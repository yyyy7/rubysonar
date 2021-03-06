package org.yinwang.rubysonar;

import org.apache.commons.io.FilenameUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.yinwang.rubysonar.ast.Call;
import org.yinwang.rubysonar.ast.Name;
import org.yinwang.rubysonar.ast.Node;
import org.yinwang.rubysonar.ast.Url;
import org.yinwang.rubysonar.types.ClassType;
import org.yinwang.rubysonar.types.FunType;
import org.yinwang.rubysonar.types.ModuleType;
import org.yinwang.rubysonar.types.Type;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;
import com.google.common.base.CaseFormat;


public class Analyzer implements Serializable {

    private static final long serialVersionUID = 1L;

    public static String MODEL_LOCATION = "org/yinwang/rubysonar/models";

    // global static instance of the analyzer itself
    public static Analyzer self;

    public String sid = _.newSessionId();
    public String cwd = null;
    public int nCalled = 0;

    public State globaltable = new State(null, State.StateType.GLOBAL);

    public Set<String> loadedFiles = new HashSet<>();
    public List<Binding> allBindings = new ArrayList<>();
    public Map<Node, List<Binding>> references = new LinkedHashMap<>();
    public Set<Name> resolved = new HashSet<>();
    public Set<Name> unresolved = new HashSet<>();

    public transient Map<String, List<Diagnostic>> semanticErrors = new HashMap<>();
    public Set<String> failedToParse = new HashSet<>();


    public List<String> path = new ArrayList<>();
    private Set<FunType> uncalled = new LinkedHashSet<>();
    private Set<Object> callStack = new HashSet<>();
    private Set<Object> importStack = new HashSet<>();

    private transient AstCache astCache;
    public Stats stats = new Stats();
    private transient Progress loadingProgress = null;

    public String projectDir;
    public String cacheDir;
    public String modelDir;

    public boolean multilineFunType = false;
    public String suffix;

    public boolean staticContext = false;

    public Map<String, Object> options;


    public Analyzer() {
        this(null);
    }


    public Analyzer(Map<String, Object> options) {
        self = this;
        if (options != null) {
            this.options = options;
        } else {
            this.options = new HashMap<>();
        }
        stats.putInt("startTime", System.currentTimeMillis());
        this.suffix = ".rb";
        addEnvPath();
        copyModels();
        createCacheDir();
        getAstCache();
    }

    // cache gems after analyzing
    public static Analyzer newCachedInstance() {
        Map<String, Object> options = new HashMap<>();
        Analyzer analyzer = new Analyzer(options);
        //Analyzer gemsCache = Analyzer.deserialize();
        //if ( gemsCache == null) {
        //    analyzer.analyze("/Users/frontier/rails");
        //    Analyzer.serialize(analyzer);
        //} else {
        //    analyzer = gemsCache;
        //    analyzer.semanticErrors = new HashMap<>();
        //    Analyzer.self = analyzer;
        //}
        return analyzer;
    }


    public boolean hasOption(String option) {
        Object op = options.get(option);
        if (op != null && op.equals(true)) {
            return true;
        } else {
            return false;
        }
    }


    private void copyModels() {
        URL resource = Thread.currentThread().getContextClassLoader().getResource(MODEL_LOCATION);
        String dest = _.locateTmp("models");
        this.modelDir = dest;

        try {
            _.copyResourcesRecursively(resource, new File(dest));
            _.msg("copied models to: " + modelDir);
        } catch (Exception e) {
            _.die("Failed to copy models. Please check permissions of writing to: " + dest);
        }
        addPath(dest);
    }


    // main entry to the analyzer
    public void analyze(String path) {
        String upath = _.unifyPath(path);
        File f = new File(upath);
        projectDir = f.isDirectory() ? f.getPath() : f.getParent();
        loadFileRecursive(upath);
    }


    // main entry to the analyzer (for JSONDump only)
    public void analyze(List<String> paths) {
        for (String path : paths) {
            loadFileRecursive(path);
        }
    }


    public void setCWD(String cd) {
        if (cd != null) {
            cwd = _.unifyPath(cd);
        }
    }


    public void addPaths(@NotNull List<String> p) {
        for (String s : p) {
            addPath(s);
        }
    }


    public void addPath(String p) {
        path.add(_.unifyPath(p));
    }


    public void setPath(@NotNull List<String> path) {
        this.path = new ArrayList<>(path.size());
        addPaths(path);
    }


    private void addEnvPath() {
        String path = System.getenv("RUBYLIB");
        if (path != null) {
            String[] segments = path.split(":");
            for (String p : segments) {
                addPath(p);
            }
        }
    }


    @NotNull
    public List<String> getLoadPath() {
        List<String> loadPath = new ArrayList<>();
        loadPath.addAll(path);
        loadPath.add("/Users/yinwang/.rvm/src/ruby-2.0.0-p247/lib");

        if (cwd != null) {
            loadPath.add(cwd);
        }
        if (projectDir != null && (new File(projectDir).isDirectory())) {
            loadPath.add(projectDir);
        }

        return loadPath;
    }


    public boolean inStack(Object f) {
        return callStack.contains(f);
    }


    public void pushStack(Object f) {
        callStack.add(f);
    }


    public void popStack(Object f) {
        callStack.remove(f);
    }


    public boolean inImportStack(Object f) {
        return importStack.contains(f);
    }


    public void pushImportStack(Object f) {
        importStack.add(f);
    }


    public void popImportStack(Object f) {
        importStack.remove(f);
    }


    @NotNull
    public List<Binding> getAllBindings() {
        return allBindings;
    }


    public List<Diagnostic> getDiagnosticsForFile(String file) {
        List<Diagnostic> errs = semanticErrors.get(file);
        if (errs != null) {
            return errs;
        }
        return new ArrayList<>();
    }


    public void putRef(@NotNull Node node, @NotNull List<Binding> bs) {
        if (!(node instanceof Url)) {
            List<Binding> bindings = references.get(node);
            if (bindings == null) {
                bindings = new ArrayList<>(1);
                references.put(node, bindings);
            }
            for (Binding b : bs) {
                if (!bindings.contains(b)) {
                    bindings.add(b);
                }
                b.addRef(node);
            }
        }
    }


    public void putRef(@NotNull Node node, @NotNull Binding b) {
        List<Binding> bs = new ArrayList<>();
        bs.add(b);
        putRef(node, bs);
    }


    @NotNull
    public Map<Node, List<Binding>> getReferences() {
        return references;
    }


    public void putProblem(@NotNull Node loc, String msg) {
        String file = loc.file;
        if (file != null) {
            addFileErr(file, loc.start, loc.end, msg);
        }
    }


    // for situations without a Node
    public void putProblem(@Nullable String file, int begin, int end, String msg) {
        if (file != null) {
            addFileErr(file, begin, end, msg);
        }
    }


    void addFileErr(String file, int begin, int end, String msg) {
        Diagnostic d = new Diagnostic(file, Diagnostic.Category.ERROR, begin, end, msg);
        getFileErrs(file, semanticErrors).add(d);
    }


    List<Diagnostic> getFileErrs(String file, @NotNull Map<String, List<Diagnostic>> map) {
        List<Diagnostic> msgs = map.get(file);
        if (msgs == null) {
            msgs = new ArrayList<>();
            map.put(file, msgs);
        }
        return msgs;
    }


    @Nullable
    public Type loadFile(String path) {
        if (loadedFiles.contains(path)) return null;
        path = _.unifyPath(path);
        File f = new File(path);

        if (!f.canRead()) {
            return null;
        }

        // detect circular import
        if (Analyzer.self.inImportStack(path)) {
            return null;
        }

        // set new CWD and save the old one on stack
        String oldcwd = cwd;
        setCWD(f.getParent());

        Analyzer.self.pushImportStack(path);
        Type type = parseAndResolve(path);

        // restore old CWD
        setCWD(oldcwd);
        Analyzer.self.popImportStack(path);

        return type;
    }


    @Nullable
    private Type parseAndResolve(String file) {
        try {
            _.msg("parse " + file + ".............");
            Node ast = getAstForFile(file);

            if (ast == null) {
                failedToParse.add(file);
                return null;
            } else {
                Type type = Node.transformExpr(ast, globaltable);
                if (!loadedFiles.contains(file)) {
                    loadedFiles.add(file);
                    loadingProgress.tick();
                }
                return type;
            }
        } catch (OutOfMemoryError | StackOverflowError e) {
            if (astCache != null) {
                astCache.clear();
            }
            System.gc();
            if(e instanceof OutOfMemoryError) {
                _.msg("Skiping for memory size limit: " + file);
            }
            if(e instanceof StackOverflowError) {
                _.msg("Skiping for stack size limit: " + file);
            }
            return null;
        }
    }


    private void createCacheDir() {
        cacheDir = _.makePathString(_.getSystemTempDir(), "rubysonar", "ast_cache");
        File f = new File(cacheDir);
        _.msg("AST cache is at: " + cacheDir);

        if (!f.exists()) {
            if (!f.mkdirs()) {
                _.die("Failed to create tmp directory: " + cacheDir +
                        ".Please check permissions");
            }
        }
    }


    private AstCache getAstCache() {
        if (astCache == null) {
            astCache = AstCache.get();
        }
        return astCache;
    }


    @Nullable
    public Node getAstForFile(String file) {
        return getAstCache().getAST(file);
    }


    public Type requireFile(String headName) {
        List<String> loadPath = getLoadPath();

        for (String p : loadPath) {
            Type t =  requireFileRecursive(p, headName);
            if (t != null) return t;
        //    String trial = _.makePathString(p, headName + suffix);
        //    if (new File(trial).exists()) {
        //        return loadFile(trial);
        //    }
        }

        return null;
    }

    public Type requireFileRecursive(String path, String baseName) {
        String trial = _.makePathString(path, baseName + suffix);
        if (new File(trial).exists()) return loadFile(trial);

        File file_or_dir = new File(path);
        if (file_or_dir.listFiles() == null) return null;
        //Arrays.stream(file_or_dir.listFiles())
        //      .filter(File::isDirectory)
        //      .forEach(file -> requireFileRecursive(file.getPath(), baseName));
        for (File file : file_or_dir.listFiles()){
            if (file.isDirectory()) {
                Type t = requireFileRecursive(file.getPath(), baseName);
                if (t != null) return t;
            }
        }
        return null;
    }


    public void loadFileRecursive(String fullname) {

        // 过滤测试目录
        if (fullname.contains("test") && fullname.contains("rails")) {
            return;
        }

        int count = countFileRecursive(fullname);
        if (loadingProgress == null) {
            loadingProgress = new Progress(count, 50);
        }

        File file_or_dir = new File(fullname);

        if (file_or_dir.isDirectory()) {
            for (File file : file_or_dir.listFiles()) {
                loadFileRecursive(file.getPath());
            }
        } else {
            if (file_or_dir.getPath().endsWith(suffix)) {
                loadFile(file_or_dir.getPath());
            }
        }
    }


    // count number of files that need processing
    public int countFileRecursive(String fullname) {
        File file_or_dir = new File(fullname);
        int sum = 0;

        if (file_or_dir.isDirectory()) {
            for (File file : file_or_dir.listFiles()) {
                sum += countFileRecursive(file.getPath());
            }
        } else {
            if (file_or_dir.getPath().endsWith(suffix)) {
                sum += 1;
            }
        }
        return sum;
    }


    public void finish() {
        _.msg("\nFinished loading files. " + nCalled + " functions were called.");
        _.msg("Analyzing uncalled functions");
        applyUncalled();

        // mark unused variables
        for (Binding b : allBindings) {
            if (!(b.type instanceof ClassType) &&
                    !(b.type instanceof FunType) &&
                    !(b.type instanceof ModuleType)
                    && b.refs.isEmpty())
            {
                Analyzer.self.putProblem(b.node, "Unused variable: " + b.node.name);
            }
        }

        _.msg(getAnalysisSummary());
    }


    public void close() {
        astCache.close();
    }


    public void addUncalled(@NotNull FunType cl) {
        if (!cl.func.called) {
            uncalled.add(cl);
        }
    }


    public void removeUncalled(FunType f) {
        uncalled.remove(f);
    }


    public void applyUncalled() {
        Progress progress = new Progress(uncalled.size(), 50);

        while (!uncalled.isEmpty()) {
            List<FunType> uncalledDup = new ArrayList<>(uncalled);

            for (FunType cl : uncalledDup) {
                progress.tick();
                Call.apply(cl, null, null, null, null, null, null);
            }
        }
    }


    @NotNull
    public String getAnalysisSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n" + _.banner("analysis summary"));

        String duration = _.formatTime(System.currentTimeMillis() - stats.getInt("startTime"));
        sb.append("\n- total time: " + duration);
        sb.append("\n- modules loaded: " + loadedFiles.size());
        sb.append("\n- semantic problems: " + semanticErrors.size());
        sb.append("\n- failed to parse: " + failedToParse.size());

        // calculate number of defs, refs, xrefs
        int nDef = 0, nXRef = 0;
        for (Binding b : getAllBindings()) {
            nDef += 1;
            nXRef += b.refs.size();
        }

        sb.append("\n- number of definitions: " + nDef);
        sb.append("\n- number of cross references: " + nXRef);
        sb.append("\n- number of references: " + getReferences().size());

        long nResolved = this.resolved.size();
        long nUnresolved = this.unresolved.size();
        sb.append("\n- resolved names: " + nResolved);
        sb.append("\n- unresolved names: " + nUnresolved);
        sb.append("\n- name resolve rate: " + _.percent(nResolved, nResolved + nUnresolved));
        sb.append("\n" + _.getGCStats());

        return sb.toString();
    }


    @NotNull
    public List<String> getLoadedFiles() {
        List<String> files = new ArrayList<>();
        for (String file : loadedFiles) {
            if (file.endsWith(suffix)) {
                files.add(file);
            }
        }
        return files;
    }

    public List<String> getLoadedConst() {
        return loadedFiles.stream()
                          .map(FilenameUtils::getBaseName)
                          .collect(Collectors.toList());
    }

    public boolean isLoadedConst(String name) {
        //String basename = FilenameUtils.getBaseName(name);
        return getLoadedConst().indexOf(name) == -1 ? false : true;
    }

    /** 
     * for analyzing rails
     */
    public void autoLoadModule(String name) {
        String underscoreName = CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, name);
        if (isLoadedConst(underscoreName)) return;
        self.requireFile(underscoreName);
    }


    public void registerBinding(@NotNull Binding b) {
        allBindings.add(b);
    }


    public void setStaticContext(boolean staticContext) {
        this.staticContext = staticContext;
    }



    public static void serialize(Analyzer analyzer) {
        try (FileOutputStream fos = new FileOutputStream("gems_cache.ser");
             ObjectOutputStream oos = new ObjectOutputStream(fos)) {
            oos.writeObject(analyzer);
        } catch (Exception e) {
            _.msg(e.getMessage());
            _.die("serialize error");
        }
    }

    public static Analyzer deserialize() {
        try (FileInputStream fis = new FileInputStream("gems_cache.ser");
             ObjectInputStream ois = new ObjectInputStream(fis)) {
            return (Analyzer)ois.readObject();
        } catch (Exception e) {
            _.msg(e.getMessage());
            //_.die("deserialize error");
            return null;
        }
    }


    @NotNull
    @Override
    public String toString() {
        return "(analyzer:" +
                "[" + allBindings.size() + " bindings] " +
                "[" + references.size() + " refs] " +
                "[" + loadedFiles.size() + " files] " +
                ")";
    }
}

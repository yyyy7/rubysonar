package org.yinwang.rubysonar.ast;

import org.jetbrains.annotations.NotNull;
import org.yinwang.rubysonar.State;
import org.yinwang.rubysonar.types.Type;
import org.yinwang.rubysonar.types.UnionType;


public class If extends Node {

    @NotNull
    public Node test;
    public Node body;
    public Node orelse;


    public If(@NotNull Node test, Node body, Node orelse, String file, int start, int end, int line, int col) {
        super(file, start,end, line, col);
        this.test = test;
        this.body = body;
        this.orelse = orelse;
        addChildren(test, body, orelse);
    }


    @NotNull
    @Override
    public Type transform(@NotNull State s) {
        Type type1, type2;
        State s1 = s.copy();
        State s2 = s.copy();

        // ignore condition for now
        transformExpr(test, s);

        if (body != null) {
            type1 = transformExpr(body, s1);
        } else {
            type1 = Type.CONT;
        }

        if (orelse != null) {
            type2 = transformExpr(orelse, s2);
        } else {
            type2 = Type.CONT;
        }

        boolean cont1 = UnionType.contains(type1, Type.CONT);
        boolean cont2 = UnionType.contains(type2, Type.CONT);

        // decide which branch affects the downstream state
        if (cont1 && cont2) {
            s.overwrite(State.merge(s1, s2));
        } else if (cont1) {
            s.overwrite(s1);
        } else if (cont2) {
            s.overwrite(s2);
        }

        return UnionType.union(type1, type2);
    }


    @NotNull
    @Override
    public String toString() {
        return "(if:" + test + ":" + body + ":" + orelse + ")";
    }


}

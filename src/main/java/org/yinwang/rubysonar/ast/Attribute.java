package org.yinwang.rubysonar.ast;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.yinwang.rubysonar.Analyzer;
import org.yinwang.rubysonar.Binding;
import org.yinwang.rubysonar.State;
import org.yinwang.rubysonar.types.*;

import java.util.List;
import java.util.Set;

import static org.yinwang.rubysonar.Binding.Kind.ATTRIBUTE;


public class Attribute extends Node {

    @Nullable
    public Node target;
    @NotNull
    public Name attr;


    public Attribute(@Nullable Node target, @NotNull Name attr, String file, int start, int end, int line, int col) {
        super(file, start,end, line, col);
        this.target = target;
        this.attr = attr;
        addChildren(target, attr);
    }


    @Nullable
    public String getAttributeName() {
        return attr.id;
    }


    @NotNull
    public Name getAttr() {
        return attr;
    }


    public void setAttr(State s, @NotNull Type v) {
        Type targetType = transformExpr(target, s);
        if (targetType instanceof UnionType) {
            Set<Type> types = ((UnionType) targetType).types;
            for (Type tp : types) {
                setAttrType(tp, v);
            }
        } else {
            setAttrType(targetType, v);
        }
    }


    private void setAttrType(@NotNull Type targetType, @NotNull Type v) {
        if (targetType.isUnknownType()) {
            Analyzer.self.putProblem(this, "Can't set attribute for UnknownType");
            return;
        }
        // new attr, mark the type as "mutated"
        if (targetType.table.lookupAttr(attr.id) == null ||
                !targetType.table.lookupAttrType(attr.id).equals(v))
        {
            targetType.setMutated(true);
        }
        targetType.table.insert(attr.id, attr, v, ATTRIBUTE);
    }


    @NotNull
    @Override
    public Type transform(State s) {
        // the form of ::A in ruby
        if (target == null) {
            return transformExpr(attr, s);
        }

        Type targetType = transformExpr(target, s);
        if (targetType instanceof UnionType) {
            Set<Type> types = ((UnionType) targetType).types;
            Type retType = Type.UNKNOWN;
            for (Type tt : types) {
                retType = UnionType.union(retType, getAttrType(tt));
            }
            return retType;
        } else {
            return getAttrType(targetType);
        }
    }


    private Type getAttrType(@NotNull Type targetType) {
        List<Binding> bs;
        if (targetType instanceof ClassType || targetType instanceof ModuleType) {
            // look for class methods only
            bs = targetType.table.lookupAttrTagged(attr.id, "class");
            if (bs == null) {
                bs = targetType.table.lookupAttr(attr.id);
            }
        } else {
            bs = targetType.table.lookupAttr(attr.id);
        }

        if (bs == null) {
            Analyzer.self.putProblem(attr, "attribute not found in type: " + targetType);
            return Type.UNKNOWN;
        } else {
            for (Binding b : bs) {
                Analyzer.self.putRef(attr, b);
                if (parent != null && (parent instanceof Call) &&
                        b.type instanceof FunType && targetType instanceof InstanceType)
                {  // method call
                    ((FunType) b.type).setSelfType(targetType);
                }
            }

            return State.makeUnion(bs);
        }
    }


    @NotNull
    @Override
    public String toString() {
        return "(" + target + "." + getAttributeName() + ")";
    }

}

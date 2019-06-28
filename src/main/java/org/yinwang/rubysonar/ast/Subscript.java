package org.yinwang.rubysonar.ast;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.yinwang.rubysonar.State;
import org.yinwang.rubysonar.types.*;


public class Subscript extends Node {

    @NotNull
    public Node value;
    @Nullable
    public Node slice;  // an NIndex or NSlice


    public Subscript(@NotNull Node value, @Nullable Node slice, String file, int start, int end, int line, int col) {
        super(file, start,end, line, col);
        this.value = value;
        this.slice = slice;
        addChildren(value, slice);
    }


    @NotNull
    @Override
    public Type transform(State s) {
        Type vt = transformExpr(value, s);
        Type st = slice == null ? null : transformExpr(slice, s);

        if (vt instanceof UnionType) {
            Type retType = Type.UNKNOWN;
            for (Type t : ((UnionType) vt).types) {
                retType = UnionType.union(retType, getSubscript(t, st, s));
            }
            return retType;
        } else {
            return getSubscript(vt, st, s);
        }
    }


    @NotNull
    private Type getSubscript(@NotNull Type vt, @Nullable Type st, State s) {
        if (vt.isUnknownType()) {
            return Type.UNKNOWN;
        } else {
            if (vt instanceof ListType) {
                return getListSubscript(vt, st, s);
            } else if (vt instanceof TupleType) {
                return getListSubscript(((TupleType) vt).toListType(), st, s);
            } else if (vt instanceof DictType) {
                DictType dt = (DictType) vt;
                if (!dt.keyType.equals(st)) {
                    addWarning("Possible KeyError (wrong type for subscript)");
                }
                return ((DictType) vt).valueType;
            } else if (vt.isStrType()) {
                if (st != null && (st instanceof ListType || st.isNumType())) {
                    return vt;
                } else {
                    addWarning("Possible KeyError (wrong type for subscript)");
                    return Type.UNKNOWN;
                }
            } else {
                return Type.UNKNOWN;
            }
        }
    }


    @NotNull
    private Type getListSubscript(@NotNull Type vt, @Nullable Type st, State s) {
        if (vt instanceof ListType) {
            if (st != null && st instanceof ListType) {
                return vt;
            } else if (st == null || st.isNumType()) {
                return ((ListType) vt).eltType;
            } else {
                addError("The type can't be subscripted: " + vt);
                return Type.UNKNOWN;
            }
        } else {
            return Type.UNKNOWN;
        }
    }


    @NotNull
    @Override
    public String toString() {
        return value + "[" + slice + "]";
    }

}

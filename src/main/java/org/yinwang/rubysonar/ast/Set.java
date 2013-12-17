package org.yinwang.rubysonar.ast;

import org.jetbrains.annotations.NotNull;
import org.yinwang.rubysonar.State;
import org.yinwang.rubysonar.types.ListType;
import org.yinwang.rubysonar.types.Type;

import java.util.List;


public class Set extends Sequence {

    public Set(List<Node> elts, int start, int end) {
        super(elts, start, end);
    }


    @NotNull
    @Override
    public Type transform(State s) {
        if (elts.size() == 0) {
            return new ListType();
        }

        ListType listType = null;
        for (Node elt : elts) {
            if (listType == null) {
                listType = new ListType(transformExpr(elt, s));
            } else {
                listType.add(transformExpr(elt, s));
            }
        }

        return listType;
    }


    @NotNull
    @Override
    public String toString() {
        return "<List:" + start + ":" + elts + ">";
    }


    @Override
    public void visit(@NotNull NodeVisitor v) {
        if (v.visit(this)) {
            visitNodes(elts, v);
        }
    }
}
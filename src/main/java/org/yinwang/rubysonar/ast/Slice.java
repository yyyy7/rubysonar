package org.yinwang.rubysonar.ast;

import org.jetbrains.annotations.NotNull;
import org.yinwang.rubysonar.State;
import org.yinwang.rubysonar.types.ListType;
import org.yinwang.rubysonar.types.Type;


public class Slice extends Node {

    public Node lower;
    public Node step;
    public Node upper;


    public Slice(Node lower, Node step, Node upper, String file, int start, int end, int line, int col) {
        super(file, start,end, line, col);
        this.lower = lower;
        this.step = step;
        this.upper = upper;
        addChildren(lower, step, upper);
    }


    @NotNull
    @Override
    public Type transform(State s) {
        if (lower != null) {
            transformExpr(lower, s);
        }
        if (step != null) {
            transformExpr(step, s);
        }
        if (upper != null) {
            transformExpr(upper, s);
        }
        return new ListType();
    }


    @NotNull
    @Override
    public String toString() {
        return "(slice:" + lower + ":" + step + ":" + upper + ")";
    }

}

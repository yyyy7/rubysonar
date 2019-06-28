package org.yinwang.rubysonar.ast;

import org.jetbrains.annotations.NotNull;
import org.yinwang.rubysonar.State;
import org.yinwang.rubysonar.types.Type;


public class Index extends Node {

    public Node value;


    public Index(Node n, String file, int start, int end, int line, int col) {
        super(file, start,end, line, col);
        this.value = n;
        addChildren(n);
    }


    @NotNull
    @Override
    public Type transform(State s) {
        return transformExpr(value, s);
    }


    @NotNull
    @Override
    public String toString() {
        return "(index:" + value + ")";
    }


}

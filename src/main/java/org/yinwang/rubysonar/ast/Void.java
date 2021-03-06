package org.yinwang.rubysonar.ast;

import org.jetbrains.annotations.NotNull;
import org.yinwang.rubysonar.State;
import org.yinwang.rubysonar.types.Type;


public class Void extends Node {

    public Void(String file, int start, int end, int line, int col) {
        super(file, start,end, line, col);
    }


    @NotNull
    @Override
    public Type transform(State s) {
        return Type.CONT;
    }


    @NotNull
    @Override
    public String toString() {
        return "(void)";
    }


}

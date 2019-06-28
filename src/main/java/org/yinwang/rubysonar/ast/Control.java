package org.yinwang.rubysonar.ast;

import org.jetbrains.annotations.NotNull;
import org.yinwang.rubysonar.State;
import org.yinwang.rubysonar.types.Type;


public class Control extends Node {

    public String command;


    public Control(String command, String file, int start, int end, int line, int col) {
        super(file, start,end, line, col);
        this.command = command;
    }


    @NotNull
    @Override
    public Type transform(State s) {
        return Type.NIL;
    }


    @NotNull
    @Override
    public String toString() {
        return "(" + command + ")";
    }
}

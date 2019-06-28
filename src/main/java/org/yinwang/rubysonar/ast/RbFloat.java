package org.yinwang.rubysonar.ast;

import org.jetbrains.annotations.NotNull;
import org.yinwang.rubysonar.State;
import org.yinwang.rubysonar.types.Type;


public class RbFloat extends Node {

    public double value;


    public RbFloat(String s, String file, int start, int end, int line, int col) {
        super(file, start,end, line, col);
        s = s.replaceAll("_", "");
        this.value = Double.parseDouble(s);
    }


    @NotNull
    @Override
    public Type transform(State s) {
        return Type.FLOAT;
    }


    @NotNull
    @Override
    public String toString() {
        return "(float:" + value + ")";
    }

}

package org.yinwang.rubysonar.ast;

import org.jetbrains.annotations.NotNull;
import org.yinwang.rubysonar.Binder;
import org.yinwang.rubysonar.State;
import org.yinwang.rubysonar.types.Type;

import java.util.List;


public class With extends Node {

    @NotNull
    public List<Withitem> items;
    public Block body;


    public With(@NotNull List<Withitem> items, Block body, int start, int end) {
        super(start, end);
        this.items = items;
        this.body = body;
        addChildren(items);
        addChildren(body);
    }


    @NotNull
    @Override
    public Type transform(@NotNull State s) {
        for (Withitem item : items) {
            Type val = transformExpr(item.context_expr, s);
            if (item.optional_vars != null) {
                Binder.bind(s, item.optional_vars, val);
            }
        }
        return transformExpr(body, s);
    }


    @NotNull
    @Override
    public String toString() {
        return "<With:" + items + ":" + body + ">";
    }


    @Override
    public void visit(@NotNull NodeVisitor v) {
        if (v.visit(this)) {
            for (Withitem item : items) {
                visitNode(item, v);
            }

            visitNode(body, v);
        }
    }
}
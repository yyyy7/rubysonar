package org.yinwang.rubysonar.ast;

import org.jetbrains.annotations.NotNull;
import org.yinwang.rubysonar.Binder;
import org.yinwang.rubysonar.Binding;
import org.yinwang.rubysonar.State;
import org.yinwang.rubysonar.types.Type;

import java.util.List;


public class Comprehension extends Node {

    public Node target;
    public Node iter;
    public List<Node> ifs;


    public Comprehension(Node target, Node iter, List<Node> ifs, int start, int end) {
        super(start, end);
        this.target = target;
        this.iter = iter;
        this.ifs = ifs;
        addChildren(target, iter);
        addChildren(ifs);
    }


    @NotNull
    @Override
    public Type transform(@NotNull State s) {
        Binder.bindIter(s, target, iter, Binding.Kind.SCOPE);
        resolveList(ifs, s);
        return transformExpr(target, s);
    }


    @NotNull
    @Override
    public String toString() {
        return "<Comprehension:" + start + ":" + target + ":" + iter + ":" + ifs + ">";
    }


    @Override
    public void visit(@NotNull NodeVisitor v) {
        if (v.visit(this)) {
            visitNode(target, v);
            visitNode(iter, v);
            visitNodes(ifs, v);
        }
    }
}
package com.example.ai.tree;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class Composite extends Behavior {
    protected List<Behavior> children = new ArrayList<>();

    public Composite(String name) {
        super(name);
    }

    public void addChildren(Behavior... children) {
        this.children.addAll(Arrays.asList(children));
    }
}
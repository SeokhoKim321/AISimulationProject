package com.example.ai.tree;

public class Selector extends Composite {
    public Selector(String name) {
        super(name);
    }

    @Override
    public Status update(Blackboard blackboard) {
        for (Behavior child : children) {
            if (child.update(blackboard) == Status.SUCCESS) {
                return Status.SUCCESS;
            }
        }
        return Status.FAILURE;
    }
}
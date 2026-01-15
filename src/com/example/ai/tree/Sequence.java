// ==========================
// Sequence.java
// ==========================

package com.example.ai.tree;

public class Sequence extends Composite {
    public Sequence(String name) {
        super(name);
    }

    @Override
    public Status update(Blackboard blackboard) {
        for (Behavior child : children) {
            if (child.update(blackboard) == Status.FAILURE) {
                return Status.FAILURE;
            }
        }
        return Status.SUCCESS;
    }
}
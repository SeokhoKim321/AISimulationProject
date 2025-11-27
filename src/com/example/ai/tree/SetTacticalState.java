package com.example.ai.tree;

public class SetTacticalState extends Behavior {
    private String stateToSet;

    public SetTacticalState(String name, String state) {
        super(name);
        this.stateToSet = state;
    }

    @Override
    public Status update(Blackboard blackboard) {
        blackboard.set("tactical_state", this.stateToSet);
        return Status.SUCCESS;
    }
}
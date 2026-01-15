// ==========================
// IsInState.java
// ==========================

package com.example.ai.tree;



public class IsInState extends Behavior {
    private final String requiredState;

    public IsInState(String name, String state){
        super(name);
        this.requiredState = state;
    }

    @Override
    public Status update(Blackboard blackboard){
        Object currentState = blackboard.get("current_state");
        if (requiredState.equals(currentState)){
            return Status.SUCCESS;

        }else{
            return Status.FAILURE;
        }
    }
}

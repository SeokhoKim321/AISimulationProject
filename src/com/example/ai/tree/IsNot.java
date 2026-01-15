// ==========================
// IsNot.java
// ==========================

package com.example.ai.tree;

public class IsNot extends Behavior{
    private final Behavior child;

    public IsNot(Behavior child){
        super("IsNot");
        this.child = child;
    }

    @Override
    public Status update(Blackboard blackboard){
        Status childStatus = child.update(blackboard);
        if(childStatus == Status.SUCCESS){
            return Status.FAILURE;
        }else{
            return Status.SUCCESS;
        }
    }
}

// src/com/example/ai/memory/MemoryCell.java

package com.example.ai.memory;

public class MemoryCell {
    String name;
    // private으로 변경하여 외부에서 직접 접근을 막습니다.
    private double activationLevel;

    // 생성자는 그대로 둡니다.
    public MemoryCell(String name) {
        this.name = name;
        this.activationLevel = 0.0;
    }

    // activationLevel의 값을 외부로 알려주는 공개(public) 메소드를 추가합니다.
    public double getActivationLevel() {
        return this.activationLevel;
    }

    // WorkingMemory가 값을 변경할 수 있도록 package-private 접근 권한의 세터(setter) 추가
    // (또는 이 클래스에 접근하는 다른 클래스들의 접근 권한을 조정할 수도 있습니다)
    void setActivationLevel(double level) {
        this.activationLevel = level;
    }


    @Override
    public String toString() {
        return String.format("<Cell: %s, Activation: %.2f>", this.name, this.activationLevel);
    }
}
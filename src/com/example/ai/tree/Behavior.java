package com.example.ai.tree;

public abstract class Behavior { // 행동트리의 모든 '규칙'노드가 상속받아야 할 "추상 클래스"를 선언
    // abstact : 추상클래스라는 의미로, 이 설계도 자체로는 실제 부품을 만들 수 없고, 이 설계도를 상속받아 완성시킨 자식클래스(예 : selector)로만
    //부품을 만들 수 있다는 뜻
    protected String name;
    // 이 규칙 노드의 이름(예 : Collision Avoidance")을 저장할 문자열 변수
    public Behavior(String name) {
        this.name = name;
    }
    // 이 설계도를 바탕으로 자식 클래스(예 : new Selector("Root"))를 만들 때 반드시 호출되는 '생성자'(초기 설정)
    // 규칙 노드를 만들 때는 반드시 name을 재료로 넣어줘야함

    public abstract Status update(Blackboard blackboard);
    //Behavior 설계도를 따르는 모든 자식 클래스가 "반드시 직접 구현해야하는 '추상 메소드'"
    //public: Agent 같은 외부 객체가 이 메소드를 호출하여 규칙을 실행시킬 수 있습니다.
    //abstract: '추상' 메소드라는 의미로, 여기서는 "이런 기능이 필요하다"고 선언만 하고, 실제 내용은 자식 클래스들이 각자 알아서 채워 넣어야 합니다.
    // (예: IsConflictDetected는 '거리'를 검사하고, Sequence는 '자식들'을 실행합니다.)
    //Status update(...): 메소드의 이름은 update이고, 실행 결과로 '성공'(SUCCESS) 또는 '실패'(FAILURE)를 의미하는 Status 값을 반환해야 합니다.
    //(Blackboard blackboard): 이 규칙을 실행하는 데 필요한 '공용 메모장'(Blackboard)을 재료로 받아야 합니다.
}
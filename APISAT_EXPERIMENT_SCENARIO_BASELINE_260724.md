# APISAT Experiment Scenario Baseline

작성일: 2026-07-24

상태: 실험 시나리오 설계 기준본. 참가자 화면 분리, 표준 audio/task
event, audio 기준 intruder timing, spawn 기준 response detector가 구현됐다.
2026-07-29 pilot 체감 확인에 따라 속도지시 범위는
`90/95/100/105/110 KIAS`로 동결했다.
2026-07-29 운용 결정에 따라 본실험 후보는 10 NM에서 시작하는 연속 접근
세션으로 정의하고, 세션 시작 입력 1회 뒤 reload 없이 6개 trial을 자동
수행한다.

## 1. 연구 질문과 해석 범위

본 실험의 핵심 질문은 다음과 같다.

> 표준화된 final approach 감시·유지 과제를 수행하는 동안 무작위 시점에 intruder가 나타났을 때, 조종사의 시선 분포와 조종 입력 반응은 객관적 사건 시점을 기준으로 어떻게 변하는가?

주요 시간 기준은 참가자 화면의 경고가 아니라 `INTRUDER_SPAWNED`이다. 다만 이 이벤트는 intruder가 생성된 객관적 노출 시작점이지, 조종사가 intruder를 의식적으로 인지한 시점이 아니다. 따라서 논문과 분석에서는 다음 용어를 구분한다.

- 사용: `intruder onset`, `threat-exposure onset`, `spawn-to-response latency`
- 사용 금지: `recognition time`, `awareness time`을 `INTRUDER_SPAWNED`와 동일시하는 표현

v33에서는 `INTRUDER_SPAWNED`와 light-assisted
`INTRUDER_VISUAL_OPPORTUNITY_ONSET`을 별도로 기록한다. 후자는 실제
인지시점이 아니라 표준화된 관측기회 시작점이다. opportunity-to-response를
주 반응지표로 사용하고 spawn-to-response는 전체 scenario 노출 지연을
보여주는 보조지표로 유지한다. 20 px `INTRUDER_VISUALLY_DETECTABLE`은
추가 진단값이다.

## 2. 현재 구현과 다음 프로토콜

| 항목 | 현재 v30 구현 | 다음 프로토콜 계획 |
|---|---|---|
| Lua | `260730_spawn_flight_context_v30` | 6-trial 자동 session state machine 추가 |
| PC 구조 | X-Plane, Java, Tobii 동일 PC | 유지 |
| UDP | `127.0.0.1:9100` | 유지 |
| intruder 대기 | 음성 시작 후 독립적인 `U(3,10초)` | 동결 |
| 표준 음성 | trial 시작 3초 후 `90~110 KIAS`, 5 KIAS 간격 WAV 중 하나를 균등 무작위 재생 | 속도 범위 동결 |
| 참가자 화면 | 최소 trial 상태와 조기 end 거부 알림만 표시 | 필요 시 문구·위치만 미세 조정 |
| 반응 검출 기준 | `INTRUDER_SPAWNED` 전 1초 평균 + 0.25초 지속 변화 | labeled pilot로 threshold 수동 검증 |
| clean 판정 | 새 spawn-response 세션은 core recording event와 선택 outcome pair를 분리 | flight-condition-valid 추가 분리 |
| 반복 방식 | 한 번의 연속 접근 중 수동으로 여러 trial | 10 NM 세션 시작 1회 뒤 reload 없이 6 trial 자동 진행 |
| 접근방향 배정 | trial별 균등 무작위 | LEFT/RIGHT/FRONT 각 2회 제약 무작위화 |
| trial 간격 | 수동 종료·재시작 | scenario closure 뒤 `U(10,15초)`와 자세 안정조건을 모두 충족하면 다음 trial |
| catch trial | 미구현 | 사용하지 않음; 결과는 예상 가능한 반복 traffic encounter로 해석 |

현재 코드와 새 설계를 혼동하지 않는다. 음성 event, 참가자 화면 분리,
음성 시작 기준 `3~10초` 지연과 실제 intruder 최초 배치 기준
`INTRUDER_SPAWNED`와 spawn 기준 response 검출은 구현됐다. 새 detector는
`PILOT_RESPONSE_BASELINE`에 baseline 품질과 `active_control_at_spawn`을
기록한다. 실제 pilot trial로 threshold의 타당성을 확인해야 한다.

v30은 raw STATE/UDP schema를 바꾸지 않고 `INTRUDER_SPAWNED` detail에
ownship의 위경도, elevation/AGL, heading/pitch/roll, angular rate,
IAS/TAS/vertical speed, 조종입력과 throttle을 기록한다. Java
session/batch analyzer는 새 event detail을 우선 사용하고, 과거 자료는
spawn과 가장 가까운 STATE row에서 같은 값을 복원한다.

## 3. 최신 pilot 세션에서 확인된 설계 문제

검토 대상:

- X-Plane/Java: `build_atc_tmp/xplane_self_tobii_20260720_142508*`
- Tobii: `logs/tobii/session_xplane_tobii_20260720_142554_gaze.csv`

중요한 단위 정정:

- STATE CSV 헤더의 `ias_mps`는 의미상 잘못된 이름이다.
- Lua가 읽는 `sim/flightmodel/position/indicated_airspeed`의 X-Plane 단위는 `kias`이다.
- 기존 CSV 호환성을 위해 즉시 헤더를 바꾸지는 않지만, 분석에서는 해당 값을 KIAS로 해석한다.
- `tas_mps`와 `vertical_speed_mps`는 이름대로 m/s이다.

최신 11-trial 세션의 spawn 시점:

- IAS: `119.8~148.9 KIAS`, 평균 `140.8 KIAS`
- heading: `323.9~325.6°`, 평균 `324.9°`
- AGL: `101~1,864 ft`
- vertical speed: `-1,014~-44 fpm`

해석:

- X-Plane `Log.txt`에서 ownship은 기본 Laminar Research `Cessna 172SP/Cessna_172SP.acf`로 확인됐다.
- STATE 궤적과 X-Plane `apt.dat`의 runway endpoint를 대조해 접근 runway는 RKSI runway 34, logged heading은 약 `325°`로 확인됐다.
- 현재 STATE schema에는 flap 상태가 없어 지난 trial의 flap configuration은 사후 확인할 수 없다.
- heading 약 `325°`는 일관되었지만 IAS와 강하율은 안정된 C172 final approach 조건으로 보기 어렵다.
- trial 1부터 11까지 AGL이 약 `1,962 ft`에서 `101 ft`로 계속 낮아졌다. 이는 동일 조건 반복이 아니라 한 번의 연속 접근에서 서로 다른 단계의 trial이다.
- 기존 strict clean은 이벤트 기록이 완결됐다는 뜻일 뿐, stabilized approach 품질이나 trial 간 조건 동일성을 보장하지 않는다.
- 이 세션은 시스템과 분석 도구를 검증한 pilot 자료로만 사용하고 개선 프로토콜의 본실험 자료와 합치지 않는다.

## 4. 표준 trial 단위

본실험에서는 `한 번의 동일 초기상황 재설정 = 한 trial`로 정의한다. 짧은 final approach 한 번에 여러 intruder trial을 연속 수행하지 않는다.

각 trial 전에 다음 조건을 같은 값으로 복원한다.

- 동일 Cessna 172 기체와 중량·연료
- 동일 runway, weather, visibility, time of day
- 동일 flap/gear configuration
- 동일 runway centerline 상의 시작 위치와 heading
- 동일 거리 또는 AGL과 3° glidepath 부근
- 동일 목표 approach IAS 부근
- 동일 cockpit view, full-screen, monitor 배치, Tobii calibration 조건

Pilot-test 시작 후보:

| 항목 | 후보값 | 확정 방법 |
|---|---:|---|
| aircraft | Laminar Research 기본 `Cessna 172SP` | X-Plane `Log.txt`로 확인 |
| runway/course | `RKSI runway 34`, 약 `325°` | STATE 궤적과 X-Plane `apt.dat`로 확인 |
| 시작 거리 | threshold 약 `3 NM` 전방 | 저장상황 재현성과 intruder 시간 확보 확인 |
| 시작 AGL | 약 `900~1,000 ft AGL` | runway elevation과 3° path에 맞춰 조정 |
| 시작 IAS | 매 trial 동일값으로 복원, `100 KIAS` 부근 후보 | 저장상황 pilot에서 최종 확인 |
| 음성 목표 IAS | `90/95/100/105/110 KIAS` | 2026-07-29 체감 pilot 후 동결 |
| glidepath | 약 `3°` | runway별 경로 확인 |
| 목표 VS | 고정 숫자 지시 안 함 | `VS_target_fpm ≈ -5.3 × groundspeed_kt`를 참고해 이동평균 평가 |

`90~110 KIAS`는 C172의 표준 최종접근 속도라고 주장하지 않는다. 본
연구에서 계기 감시와 속도조절을 유도하는 실험용 concurrent task 범위다.
각 target의 난이도는 초기 IAS에 따라 달라질 수 있으므로
`target_speed_kias`, audio-start IAS, 초기 target error를 보존한다.

기존 `Cessna Skyhawk Situation.sit` 또는 `10nm app .sit`를 덮어쓰지 않는다. 본실험용 상태는 예를 들어 `APISAT_RKSI34_C172SP_3NM.sit`라는 별도 이름으로 새로 만들고, flap·IAS·AGL·위치가 맞는지 첫 pilot 전에 확인한다.

현재 flap 상태가 로그에 없으므로 다음 구현에서 participant 입력 없이 flap handle/actual deployment를 STATE 또는 `TRIAL_START` detail에 자동 기록한다. 이 추가는 packet/CSV 계약 변경이므로 Lua, Java parser/logger/analyzer를 함께 수정한다.

## 5. 조종사 과제와 표준 음성

### 5.1 과제 선택

첫 구현은 큰 기동 명령이 아니라 final approach 유지 과제로 단순화한다.

- approach speed 유지
- runway centerline/course 유지
- stabilized glidepath 유지

제외:

- short final에서 runway 방향을 크게 벗어나는 임의 heading 지시
- 강하 중 고정 altitude 유지 지시
- 매 trial마다 임의의 숫자 vertical-speed 지시

큰 목표 변화는 지시 이행을 위한 정상 조작을 intruder 회피 반응으로 오검출할 수 있다. 첫 pilot에서는 유지 또는 작은 보정만 요구하고, spawn 순간의 목표오차와 조작 상태를 별도로 기록한다.

### 5.2 첫 음성 문구

표준 문구 형식:

> “Cessna Zero One, continue straight-in, maintain [target speed] knots, track runway centerline.”

음성은 실험자가 즉흥적으로 말하지 않고 `90`, `95`, `100`, `105`,
`110 KIAS`용 사전녹음 WAV 5개 중 하나를 균등 무작위로 재생한다. 목표는
trial 시작 시 한 번 선택해 해당 trial 동안 고정한다.

숫자 vertical speed는 음성으로 지시하지 않는다. 참가자에게는 실험 전 브리핑으로 안정된 glidepath와 과도한 상승·강하율을 피하도록 안내하고, 실제 성과는 로그로 평가한다.

## 6. 표준 event timeline

권장 순서:

1. 동일한 10 NM X-Plane 저장상황 불러오기
2. Java receiver와 Tobii logger가 기록 중인지 확인
3. 참가자가 세션 시작 command를 1회 입력
4. 준비시간과 초기 자세 안정조건 확인
5. 미리 제약 무작위화한 LEFT/RIGHT/FRONT 각 2회의 6-trial 순서 고정
6. 각 trial을 내부 `TRIAL_RESET`과 `TRIAL_START`로 자동 시작
7. `3초`의 pre-task baseline
8. `TASK_COMMAND_AUDIO_START`
9. 표준 음성 재생 및 `TASK_COMMAND_AUDIO_END`
10. audio start를 기준으로 독립적인 `U(3,10초)` 대기
11. 과제 완료 여부와 무관하게 `INTRUDER_SPAWNED`
12. 필요 시 향후 `INTRUDER_DETECTABLE_ONSET`
13. spawn 전 baseline 대비 지속 입력 변화로 `PILOT_RESPONSE_START`
14. `MIN_DISTANCE_REACHED` 및 내부 hazard 관련 이벤트
15. 시나리오가 닫히면 `TRIAL_END` 자동 기록
16. `U(10,15초)` washout과 자세 안정조건을 모두 충족하면 다음 trial
17. 6회 완료 후 세션 자동 종료

음성 과제는 audio 종료와 함께 끝나는 것이 아니라 trial 종료까지 유효한 유지 과제다. intruder spawn은 참가자의 과제 완료를 기다리지 않는다.

계획 이벤트:

- `TASK_COMMAND_AUDIO_START`
- `TASK_COMMAND_AUDIO_END`
- 필요 시 `TASK_TARGET_ENTERED`
- 필요 시 `TASK_TARGET_DEVIATED`
- 향후 `INTRUDER_DETECTABLE_ONSET`

`TASK_COMMAND_AUDIO_START/END`는 Lua v26, Java enum/parser/logger/analyzer에 구현됐다. 나머지 후보 event는 아직 구현되지 않았으며 추가 시 관련 구성요소를 같은 작업에서 함께 변경한다.

## 7. 참가자 화면 정책

본실험 참가자에게는 정상 비행에 필요한 X-Plane 정보만 보인다.

숨길 항목:

- `RANDOM CROSSING ARMED`
- `RANDOM CROSSING IN ...`
- `SCENARIO ACTIVE`
- `CROSSING INTRUDER ACTIVE`
- UDP 주소, sample/frame, 좌표, hazard 거리
- event count, 상세 trial/scenario 상태와 실험용 countdown
- `TRAFFIC ALERT`, `CHECK OUTSIDE`

유지할 항목:

- 내부 event 생성과 CSV 기록
- hazard/clear 및 minimum-distance 계산
- end-trial guard
- clean-trial 검사용 상태
- `TRIAL IDLE/RESET/ACTIVE/END` 최소 운영 확인과 조기 end 거부 알림
- 실험자용 로그 또는 별도 observer 화면

화면 draw와 내부 실험 state는 v23 이후 분리됐다. 새 no-alert Java receiver는
내부 위험창을 `HAZARD_DETECTED/CLEARED`로 기록하고 자동
`ADVISORY_SHOWN/CLEARED`를 만들지 않는다. 구형 CSV와 명시적 수동 advisory
실험을 위한 enum/parser 호환성은 유지한다.

## 8. 반응 검출과 유효 trial 기준

### 8.1 반응 검출

현재 Java는 다음 원칙으로 spawn 기준 반응을 검출한다.

- anchor: `INTRUDER_SPAWNED`
- baseline: spawn 전 약 `1초`의 안정 입력
- response: baseline 대비 threshold를 넘는 조작이 `200~300 ms` 지속되는 첫 시점
- spawn 당시 이미 음성 과제 관련 조작 중인지 `active_control_at_spawn`으로 기록
- threshold와 지속시간은 labeled pilot trial로 수동 검증 후 최종 고정

단순 threshold는 과제 이행 조작과 회피 조작을 혼동할 수 있다. 따라서 speed/course/glidepath error, 조작 축, gaze 맥락을 함께 저장하고 pilot test에서 오검출을 확인한다.

2026-07-29 fully logged v29 pilot에서 이 혼동이 실제로 확인됐다. target
`100 KIAS`, spawn IAS `108.728 KIAS`, `active_control_at_spawn=true`인
상태에서 throttle 변화가 spawn 후 `3.031초` response로 검출됐다. 이
동작은 속도 과제 수행일 가능성이 있으므로 회피반응으로 확정하지 않는다.

본실험 전 반응 판정 규칙:

- `recording_complete`와 `behavior_response_valid`를 분리한다.
- `active_control_at_spawn=true` trial은 독립 검증이 없으면 primary
  response latency에서 제외한다.
- raw control threshold 단독 대신 지속적인 aircraft state/trajectory
  변화와 predicted separation 개선을 결합한 기준을 개발한다.
- 새 기준은 수동 검토한 pilot trial과 비교해 threshold를 동결한다.

후속 행동 판정은 최소 세 범주를 사용한다.

- `SPEED_TASK_CONTROL`: throttle 중심 변화와 함께 IAS target error는
  감소하지만 heading/vertical path와 predicted separation 변화가 작음
- `AVOIDANCE_CANDIDATE`: 지속적인 roll/pitch/heading/vertical-path 변화가
  있고 predicted closest-approach separation이 개선됨
- `AMBIGUOUS`: spawn 당시 이미 조작 중이거나 두 조건이 동시에 나타남

gaze의 external/intruder AOI 전환은 보조 근거이며 단독으로 의도나 인지를
확정하지 않는다.

### 8.2 두 단계 유효성

`event-clean`과 `flight-condition-valid`를 분리한다.

`recording/event complete` 후보:

- trial start/end와 task/spawn 사건이 한 번씩 존재
- STATE/INTRUDER/Tobii timestamp가 필요한 구간을 포함
- logger 중단이나 중복 session이 없음

`flight-condition-valid` 후보:

- spawn 전 목표 IAS envelope 충족
- runway course/centerline envelope 충족
- 거리별 AGL 또는 glidepath envelope 충족
- 과도한 bank·강하율·불안정 상태가 없음

구체 허용범위는 3~5회의 pilot trial로 정한 뒤 본실험 전에 동결한다. 순간 VS가 아니라 1~2초 이동평균을 사용한다.

다음 사건은 clean 필수조건으로 두지 않는다.

- `HAZARD_DETECTED`
- `ADVISORY_SHOWN`
- `PILOT_RESPONSE_START`

조기 회피가 hazard 진입을 막을 수 있고, 반응 없음도 유효한 행동 결과일 수 있기 때문이다. 이들은 outcome category와 성능지표로 사용한다.

## 9. 주요 분석지표

현재 near-spawn protocol의 primary 후보:

- `INTRUDER_SPAWNED -> PILOT_RESPONSE_START`
- spawn 전후 instrument / outside gaze 분포
- 반응 조작 축과 최대 변화량
- minimum horizontal distance와 vertical separation
- approach speed, course, glidepath 유지오차

먼 거리/detectability 구현 후:

- primary: `INTRUDER_DETECTABLE_ONSET -> PILOT_RESPONSE_START`
- secondary: `INTRUDER_SPAWNED -> PILOT_RESPONSE_START`
- dynamic intruder AOI의 first sustained dwell
- `active_control_at_spawn`
- hazard 진입 여부와 hazard-window 길이
- left/right/front approach 조건 차이

200 ms gaze centroid는 그림을 간단히 하기 위한 평균점이지 fixation이나 인지 판정이 아니다. 실제 first dwell은 raw gaze에 별도의 지속시간 규칙을 적용해 계산한다.

## 10. 이후 단계

1단계 이후 순서는 다음과 같다.

1. NVIDIA Surround 기반 3모니터/단일 Spark feasibility test
2. 참가자 화면 분리와 표준 audio/task event 구현 완료(v26)
3. audio 기준 3~10초 독립 지연과 실제 최초 배치 기준 spawn event 구현 완료(v27)
4. 3회 timing pilot으로 v27 planned/actual delay와 음량 context 검증
5. spawn 기준 response detector와 no-alert event 의미 분리 완료
6. 실제 1회 trial로 baseline/response/no-advisory 동작 확인
7. recording-complete / flight-condition-valid / outcome 추가 분리
8. 10 NM 시작, reload 없는 6-trial 자동 세션과 washout/stability gate 구현
9. 생성 시 기존 STATE의 위치·AGL·heading·pitch·roll·IAS·VS·입력값을 trial summary로 export
10. 먼 거리 intruder와 객관적 pixel/FOV 기반 detectability event 구현
11. `RUNWAY_DYNAMIC`, `INTRUDER_DYNAMIC`, `HORIZON_OR_EXTERNAL_OTHER` 등 AOI 확장
12. pilot session으로 자세 안정 threshold와 trial-order effect 검증
13. 프로토콜 동결 후 본실험

Surround test가 실패하면 현재의 center Spark 측정을 유지하고 좌우 모니터는 `UNTRACKED_OR_OFF_DISPLAY`로 남긴다. Spark 추가 구매는 동시 스트림, 독립 calibration, IR 간섭과 timestamp 안정성을 제조사 또는 대여시험으로 확인하기 전에는 결정하지 않는다.

### 현재 3모니터 baseline

2026-07-24 로컬 확인:

- GPU: `NVIDIA GeForce RTX 2060`
- driver: `560.94`
- Windows display:
  - left `DISPLAY1`: `1920x1080`, x=`-1920`
  - center/primary `DISPLAY2`: `1920x1080`, x=`0`
  - right `DISPLAY3`: `1920x1080`, x=`1920`
- 현재는 3개 독립 display로 인식되는 extended-desktop 상태다.
- Surround 적용 후에는 한 개의 `5760x1080` 또는 bezel-corrected logical display로 보이는지 다시 확인한다.

공식 근거:

- [Tobii Pro Spark specifications](https://www.tobii.com/products/eye-trackers/screen-based/tobii-pro-spark): optimal screen size는 16:9 기준 최대 27인치이지만 tripod setup으로 더 큰 영역을 구성할 수 있다.
- [Tobii advanced screen-based setup guidance](https://www.tobii.com/resource-center/learn-articles/how-to-use-screen-based-eye-trackers): Spark의 maximum gaze angle은 35°이며 large/wide/multi-display도 실제 거리와 기하조건이 맞으면 가능하다고 설명한다.
- [NVIDIA Surround configuration](https://www.nvidia.com/content/Control-Panel-Help/vLatest/en-us/mergedProjects/3D%20Settings/NVIDIA_Surround_Configuration_.htm): Surround는 여러 display를 하나의 큰 logical display로 결합한다.
- [Tobii Pro SDK compatibility](https://developer.tobiipro.com/tobiiprosdk/eyetrackercompatibility.html): Spark는 Pro SDK 지원 대상이고 Eye Tracker 4C는 Pro SDK 미지원 대상이다.
- [Tobii Eye Tracker Manager](https://developer.tobiipro.com/eyetrackermanager/etm-installation-information.html): 여러 연결 tracker를 검색·관리할 수 있다. 다만 이것만으로 두 Spark가 한 참가자의 서로 다른 화면을 동시에 안정 추적한다고 보장되지는 않는다.

## 11. 본실험 전 미확정 항목

- flap configuration과 자동 flap-state 기록 방식
- 10 NM RKSI runway 34 저장상황의 정확한 좌표·AGL
- glidepath/VS/bank 허용범위
- response threshold와 지속시간
- `U(10,15초)` 뒤 적용할 pitch/roll/heading 자세 안정 threshold와 지속시간
- 5 px visual-opportunity guard와 20 px 보조 detectability의 실제 화면 검증
- 5개 목표속도를 6-trial 세션과 여러 세션에 걸쳐 균형화하는 방식

미확정값은 pilot test 전에 임의로 본실험 값으로 고정하지 않는다.

## 2026-08-04 반영된 v33 pilot-validation 조건

- 방향 구성: 세션당 FRONT/LEFT/RIGHT 각 2회, 순서는 매 세션 무작위
- 최초 상대방위: FRONT 0°, LEFT -32°, RIGHT +32°
- 조명: beacon/nav/strobe on, landing/taxi off
- primary anchor: `INTRUDER_VISUAL_OPPORTUNITY_ONSET`
- event 조건: 화면 내 projection, estimated span 5 px 이상, light write 성공
- event 해석: `recognition_claim=false`; 실제 발견시점으로 표현하지 않음
- primary response latency: opportunity-to-response
- secondary response latency: spawn-to-response
- 20 px detectability: 선택적 보조 진단, clean 필수조건 아님

이 조건은 코드·synthetic 검증은 끝났지만 실제 X-Plane의 LEFT/RIGHT/FRONT
위치와 점멸등 표시를 확인한 뒤 pilot-validation 값으로 확정한다.

### 2026-08-04 physical validation

- AI aircraft는 intruder용 Cessna 172 한 대만 둔다. 19대 구성에서는 내장
  ATC `GetGeoPositionAtT` assert가 발생했으며, 한 대 구성 retry는 안정적으로
  6 trial을 완료했다.
- JoinFS는 실험 중 비활성화한다.
- `session_visual_opportunity_v33_retry_20260804_134141`은 6/6 clean,
  LEFT/RIGHT/FRONT 각 2회, 전체 gaze validity 84.8%였다.
- ±32°/0° 화면위치, light write, opportunity event와 Java/Tobii merge는
  physical validation을 통과했다.
- 이 session의 순서는 RIGHT-RIGHT-FRONT-FRONT-LEFT-LEFT였고 접근고도도
  계속 감소했으므로 방향효과 자료로 사용하지 않는다.
- clean response 중 throttle trigger가 4/6이므로 recording completeness와
  avoidance-valid response를 별도 변수로 분리해야 한다.

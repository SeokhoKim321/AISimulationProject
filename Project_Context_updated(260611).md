# PROJECT_CONTEXT.md

## 연구 배경

이 프로젝트는 X-Plane 11 시뮬레이터와 Java 기반 수신/분석 프로그램을 연동하여 조종사의 위험상황 인지 및 조종 반응 데이터를 수집하는 연구이다. 최종 목표는 향후 AI 조종사 모델을 설계할 때 사용할 수 있는 조종 반응 데이터 구조, 이벤트 타임라인, 반응 지표를 구축하는 것이다.

현재 논문/실험 방향은 완성된 AI 조종사 모델 자체를 구현하는 것보다, AI 조종사 모델 개발에 필요한 pilot-in-the-loop 데이터 수집 및 분석 프레임워크를 제시하는 쪽에 맞춰져 있다.

## 현재 연구 방향

현재 핵심 시나리오는 Cessna 172/Skyhawk 기반 final approach 상황에서 예측하기 어려운 crossing intruder를 발생시키고, 조종사가 advisory와 intruder에 어떻게 반응하는지 기록하는 것이다.

주요 분석 대상:

- `INTRUDER_SPAWNED` 이후 hazard 발생까지의 시간
- `HAZARD_DETECTED`와 `ADVISORY_SHOWN` 시각
- `ADVISORY_SHOWN` 이후 `PILOT_RESPONSE_START`까지의 반응 지연
- `PILOT_RESPONSE_START` 이후 입력 안정화 또는 advisory clear까지의 반응 지속 시간
- 최소 수평 거리와 최소 수직 분리
- pitch/roll/yaw/throttle 입력 변화
- 추후 gaze/AOI 데이터와 event timeline의 병합

## 현재 시스템 구조

```text
X-Plane 11 / FlyWithLua
    -> UDP STATE / INTRUDER / EVENT packet

Java receiver / XPlaneReceiverMain
    -> logs/xplane CSV 저장
    -> 자동 hazard/advisory/pilot-response event 기록

Java analysis tools
    -> session summary
    -> trial summary
    -> clean trial CSV export

Tobii Spark / gaze logger
    -> 아직 직접 연동 전
    -> 추후 trial_id 또는 timestamp 기준으로 병합
```

현재 네트워크 설정:

- X-Plane PC IP: `100.64.0.132`
- Java receiver PC IP: `100.64.0.129`
- UDP target port: `9100`

현재 활성 Lua:

- `FLYWITHLUA_STUDY_INTEGRATED.lua`
- version: `260529_end_trial_guard_v20`
- snapshot: `FLYWITHLUA_STUDY_INTEGRATED_260529_end_trial_guard_v20.lua`

현재 Java entry point:

- receiver: `src/com/example/ai/XPlaneReceiverMain.java`
- session analysis: `src/com/example/ai/XPlaneSessionAnalysisMain.java`
- batch analysis: `src/com/example/ai/XPlaneBatchAnalysisMain.java`

## 현재 Lua 역할

`FLYWITHLUA_STUDY_INTEGRATED.lua`는 다음 기능을 담당한다.

- FlyWithLua command와 macro 등록
- trial reset/start/end 관리
- `trial_id`, `sample_index`, `study_event_counter` 관리
- ownship state sampling
- pilot control input sampling
- intruder state sampling
- UDP `STATE`, `INTRUDER`, `EVENT` 전송
- X-Plane pause 중 같은 `running_time_sec` 중복 전송 방지
- cloud deck 조건 설정
- head-on intruder 테스트 시나리오
- randomized final-approach crossing intruder 시나리오
- visual advisory 화면 표시
- 조기 `Study End Trial` 입력 guard

현재 등록된 command/macro:

- `flywithlua/study/reset_trial`
- `flywithlua/study/start_trial`
- `flywithlua/study/end_trial`
- `flywithlua/study/start_intruder_headon`
- `flywithlua/study/toggle_cloud`

현재 v18 visual advisory:

- hazard 조건: 수평 거리 `150 m` 이하 그리고 수직 분리 `30 m` 이하
- clear 조건: 수평 거리 `200 m` 이상 또는 수직 분리 `50 m` 이상
- 화면 문구: `TRAFFIC ALERT`, `CHECK OUTSIDE`
- Lua visual advisory는 화면 표시용이며, CSV event는 Java 자동 event detector가 기존 구조대로 기록한다.

v20 end-trial guard:

- `random_crossing_armed`, `crossing_stabilizing`, `visual_advisory_active` 중 하나라도 true면 `Study End Trial`이 `TRIAL_END`를 기록하지 않는다.
- crossing intruder는 `crossing_min_distance_reported == true`이면 아직 active여도 trial end를 허용한다.
- head-on intruder는 마지막 수평 거리가 clear threshold 이상이면 아직 active여도 trial end를 허용한다.
- 이 경우 `MANUAL_NOTE`에 `ignored=end_before_trial_closed`와 현재 상태 flag를 기록한다.
- 화면 상태는 `WAIT: TRIAL NOT CLOSED`로 표시한다.
- Java의 `PILOT_RESPONSE_END` 내부 상태는 Lua가 직접 알 수 없으므로, 이 guard는 intruder/advisory 진행 중 조기 종료를 줄이는 목적이다.

## 현재 Java 역할

Java receiver는 다음 역할을 담당한다.

- UDP `9100` 포트 수신
- `STATE`, `INTRUDER`, `EVENT` 패킷 파싱
- ownship state CSV 저장
- intruder CSV 저장
- event CSV 저장
- hazard 자동 판정
- advisory 자동 기록
- pilot response start/end 자동 판정
- 수동 event controller UI 제공

주요 클래스:

- `XPlaneReceiverMain`
- `XPlaneStateReceiver`
- `XPlaneStateSample`
- `XPlaneIntruderSample`
- `XPlaneEventRecord`
- `XPlaneEventType`
- `XPlaneExperimentLogger`
- `XPlaneIntruderLogger`
- `XPlaneEventLogger`
- `XPlaneAutoEventDetector`
- `XPlaneSessionAnalysisMain`
- `XPlaneBatchAnalysisMain`

## 데이터 패킷

### STATE

최신 `STATE` 패킷은 다음 정보를 포함한다.

- `trial_id`
- sample index
- X-Plane simulation time
- ownship local position
- latitude, longitude, elevation, AGL altitude
- ownship local velocity
- heading, pitch, roll
- angular rates
- indicated/true airspeed
- vertical speed
- pitch, roll, yaw, throttle input

### INTRUDER

최신 `INTRUDER` 패킷은 다음 정보를 포함한다.

- `trial_id`
- sample index
- X-Plane simulation time
- intruder local position
- intruder local velocity
- horizontal distance
- vertical separation
- ownship local position copy

### EVENT

최신 `EVENT` 패킷은 다음 정보를 포함한다.

- `trial_id`
- event index
- X-Plane simulation time
- event type
- optional detail

## 주요 이벤트 타임라인

권장 final approach crossing trial 흐름:

1. `TRIAL_RESET`
2. `TRIAL_START`
3. `FINAL_APPROACH_GATE_ENTERED`
4. randomized delay
5. `SCENARIO_SELECTED`
6. `INTRUDER_SPAWNED`
7. `HAZARD_DETECTED`
8. `ADVISORY_SHOWN`
9. `PILOT_RESPONSE_START`
10. `HAZARD_CLEARED`
11. `ADVISORY_CLEARED`
12. `PILOT_RESPONSE_END`
13. `TRIAL_END`

현재 strict clean trial 판정은 핵심 이벤트가 정확히 한 번씩, 지정된 순서로 존재해야 한다.

## CSV 출력

Java receiver가 생성하는 파일:

- `logs/xplane/xplane_session_<timestamp>.csv`
- `logs/xplane/xplane_session_<timestamp>_intruder.csv`
- `logs/xplane/xplane_session_<timestamp>_events.csv`

Batch analyzer가 생성하는 파일:

- `logs/xplane/xplane_batch_analysis_<timestamp>_sessions.csv`
- `logs/xplane/xplane_batch_analysis_<timestamp>_trials.csv`
- `logs/xplane/xplane_batch_analysis_<timestamp>_clean_trials.csv`

원본 receiver CSV는 분석 과정에서 수정하거나 병합하지 않는다.

## 최근 검증 상태

최근 기존 클래스파일 기준 batch 분석 결과:

- compatible sessions: `12`
- total trials: `79`
- strict clean trials: `19`
- incomplete trials: `60`
- clean rate: `24.1%`
- clean 평균 `spawn->hazard`: `3.551 s`
- clean 평균 `advisory->response`: `0.430 s`
- clean 평균 `hazard_window`: `1.466 s`

주의:

- `build_tmp` 재컴파일과 `logs/xplane` 신규 export가 현재 OneDrive/권한 문제로 실패할 수 있다.
- 코드 자체 분석 로직은 기존 `build_tmp` 클래스파일로 실행 가능했지만, 새 CSV export 단계에서 `AccessDeniedException`이 발생했다.

## Eye Tracking / AOI 상태

Tobii Spark 실시간 연동은 아직 구현되지 않았다. 현재는 다음 준비 단계까지 진행되어 있다.

- Spark 도입 전 X-Plane 중앙 모니터 AOI 초안 작성
- Cessna/Skyhawk cockpit 기준 AOI draft 작성
- AOI CSV: `resources/cessna_aoi_draft_260526.csv`
- 관련 문서: `XPLANE_CESSNA_AOI_DRAFT_260526.md`, `TOBII_SPARK_PREP_PROTOCOL_260521.md`

예상 gaze CSV 기본 구조:

```text
session_id,trial_id,gaze_time_s,gaze_timestamp_ms,gaze_x,gaze_y,validity,left_pupil,right_pupil,aoi
```

추후 분석 목표:

- `INTRUDER_SPAWNED` 이후 intruder 또는 outside AOI 최초 주시 시각
- `ADVISORY_SHOWN` 이후 advisory AOI 최초 주시 시각
- gaze detection latency
- response-after-gaze latency
- AOI dwell time
- AOI transition sequence

## 문서 관리 규칙

- 최신 상태는 항상 `Agents.md`, `Project_Context.md`, `Worklog.md`에 반영한다.
- 최신화 시 같은 내용을 `*_updated(YYMMDD).md` 스냅샷으로 추가 저장한다.
- 기존 스냅샷은 삭제하지 않는다.
- 코드와 문서가 다르면 코드를 먼저 확인하고 문서를 코드 기준으로 갱신한다.
- 코드나 실험 절차 변경이 끝나면 같은 작업 안에서 문서도 갱신한다.

## Lua 버전 관리 규칙

- 활성 실행 파일은 `FLYWITHLUA_STUDY_INTEGRATED.lua`로 유지한다.
- Lua를 수정할 때는 `STUDY_SCRIPT_VERSION`을 함께 갱신한다.
- Lua 업데이트 후 같은 내용을 `FLYWITHLUA_STUDY_INTEGRATED_YYMMDD_<short_description>_vNN.lua` 스냅샷으로 저장한다.
- 기존 Lua 스냅샷은 삭제하지 않는다.
- Lua의 UDP 패킷, command, event, scenario parameter를 바꾸면 Java와 문서를 같은 작업 안에서 함께 갱신한다.
- X-Plane `Scripts` 폴더에는 활성 통합 Lua 하나만 넣는다.

## 다음 작업

1. OneDrive/readonly/reparse point 때문에 발생하는 `build_tmp`, `logs/xplane` 쓰기 문제 해결
2. `AnalyzeXPlaneLogs.bat`가 현재 환경에서 안정적으로 컴파일/분석/export할 수 있게 확인
3. v18 visual advisory 위치를 실제 X-Plane 캡처 기준으로 검증
4. `ADVISORY`와 `RUNWAY_ZONE` AOI 좌표 보정
5. 같은 조건에서 clean trial 반복 수집
6. v20 end-trial guard가 clean trial 비율을 개선하는지 실제 X-Plane trial로 검증
7. gaze CSV 병합 분석 코드 설계 및 구현

## 2026-06-09 ATC Simulator Server 연동 상태

연구실 ATC Simulator Server 프로젝트:

- `Simulator Server - 20250520_DAS`

확인한 서버 통신 구조:

- 서버는 TCP socket 기반이다.
- 기본 포트는 `50000`이다.
- Java PC에서 서버 PC `172.16.150.130:50000`으로 TCP 접속이 가능함을 확인했다.
- 클라이언트는 접속 직후 `src:<type>#<id>>dst:svr>typ:reg` 형식의 등록 패킷을 보내야 한다.
- 서버가 정상 등록하면 `src:svr>dst:<type>#<id>>typ:grt` 응답을 보낸다.
- 서버 패킷 구분자는 `ICD.java` 기준으로 `&`, `>`, `:`, `#`, `@`를 사용한다.

추가한 Java 파일:

- `src/com/example/ai/AtcServerConnectionTest.java`
- `src/com/example/ai/AtcServerBridgeClient.java`
- `src/com/example/ai/AtcServerFdtSmokeTest.java`

수정한 Java 파일:

- `src/com/example/ai/XPlaneReceiverMain.java`
- `src/com/example/ai/XPlaneStateReceiver.java`

현재 연동 방향:

```text
X-Plane PC
    -> FlyWithLua UDP STATE / INTRUDER / EVENT
Java PC
    -> 기존 CSV 저장 유지
    -> 기존 hazard/advisory/pilot-response 자동 이벤트 검출 유지
    -> 옵션 사용 시 ATC Simulator Server로 ownship STATE를 FDT로 변환 송신
ATC Simulator Server PC
    -> PLT 클라이언트로 수신
```

검증된 사항:

- `AtcServerConnectionTest`로 `PLT_XP` 등록 테스트 성공
- `AtcServerFdtSmokeTest`로 `PLT_XP_SMOKE` 등록 후 synthetic X-Plane state 1개를 `FDT`로 변환 송신 성공
- `XPlaneReceiverMain`은 정상 실행 시 종료되지 않고 UDP 수신을 계속 기다리는 receiver이다. PowerShell에서 멈춘 것처럼 보이는 것이 정상 동작이다.

실행 예:

```powershell
java -cp build_atc_tmp com.example.ai.XPlaneReceiverMain 9100 logs\xplane\xplane_atc_test.csv session_atc_test --atc-host 172.16.150.130 --atc-port 50000 --atc-module PLT_XP --atc-fid xplane01 --atc-every 5
```

주의:

- 서버 PC에서 Simulator Server의 `Open` 버튼을 눌러야 `50000` 포트가 열린다.
- `Test-NetConnection`은 서버 TCP 포트 확인에는 유용하지만, 서버 코드가 accept 직후 첫 줄을 기다리므로 포트 테스트 후에는 서버를 `Close`/`Open`으로 초기화하는 것이 안전하다.
- 현재 ATC 연동은 ownship `STATE`만 `FDT`로 보낸다.
- v21부터 intruder를 서버 traffic으로 표시하기 위해 Lua가 intruder lat/lon/alt를 추가 송신하고, Java가 이를 ATC `ADS` 패킷으로 변환한다.

## 2026-06-10 ATC intruder ADS 연동 상태

A안으로 intruder 위치를 관제서버 traffic으로 보내는 최소 구현을 추가했다.

수정된 Lua:

- `FLYWITHLUA_STUDY_INTEGRATED.lua`
- version: `260610_intruder_geo_atc_v21`
- snapshot: `FLYWITHLUA_STUDY_INTEGRATED_260610_intruder_geo_atc_v21.lua`

변경된 `INTRUDER` 패킷:

- `trial_id`
- `sample_index`
- `sim_time_s`
- intruder local position: `plane1_x`, `plane1_y`, `plane1_z`
- intruder approximate geo position: `intruder_latitude_deg`, `intruder_longitude_deg`, `intruder_elevation_m`
- intruder local velocity: `plane1_vx`, `plane1_vy`, `plane1_vz`
- horizontal distance
- vertical separation
- ownship local position copy

Java 변경:

- `XPlaneIntruderSample`은 구형 INTRUDER와 v21 INTRUDER를 모두 파싱한다.
- `XPlaneIntruderLogger`의 새 CSV 헤더는 intruder 위경도/고도 컬럼을 포함한다.
- `AtcServerBridgeClient`는 ownship `STATE -> FDT`와 intruder `INTRUDER -> ADS`를 모두 지원한다.
- `XPlaneReceiverMain`은 ATC 사용 시 기본적으로 ownship용 `PLT_XP`, intruder용 `SDP_XP` 두 클라이언트를 등록한다.
- `AtcServerAdsSmokeTest`를 추가했다.

권장 실행 명령:

```powershell
java -cp build_atc_tmp com.example.ai.XPlaneReceiverMain 9100 build_atc_tmp\xplane_atc_intruder_test.csv session_atc_intruder_test --atc-host 172.16.150.130 --atc-port 50000 --atc-module PLT_XP --atc-fid xplane01 --atc-intruder-module SDP_XP --atc-intruder-fid intruder01 --atc-every 5
```

검증 포인트:

- ATC 서버 GUI에 `plt#PLT_XP`와 `sdp#SDP_XP`가 모두 등록되는지 확인한다.
- 관제 화면에 ownship `xplane01`과 intruder `intruder01`이 동시에 표시되는지 확인한다.
- 새 intruder CSV에 `intruder_latitude_deg`, `intruder_longitude_deg`, `intruder_elevation_m` 값이 `NaN`이 아닌지 확인한다.
## 2026-06-10 ATC intruder final display rule

최종 테스트에서 관제 화면에 ownship `xplane01`과 intruder `intruder01`이 함께 표시되는 실행 방식이 확인됐다.

핵심 결론:

- X-Plane/Lua intruder 생성 로직은 정상이다.
- `INTRUDER_SPAWNED`, `HAZARD_DETECTED`, intruder CSV 위경도/고도 기록이 정상 확인됐다.
- `SDP_XP` 별도 연결로 intruder를 보내면 관제 화면에 표시되지 않았다.
- `PLT_INTR` 별도 연결로 intruder를 보내도 관제 화면에 표시되지 않았다.
- 최종 성공 방식은 `PLT_XP` 한 TCP 연결에서 ownship과 intruder를 같이 보내는 방식이다.

최종 성공 구조:

```text
PLT_XP single TCP link
    ownship  -> fid:xplane01
    intruder -> fid:intruder01
```

필수 실행 옵션:

- `--atc-intruder-on-ownship-link`
- `--atc-intruder-fdt`

정상 등록 메시지:

```text
ATC bridge registered as plt#PLT_XP
```

`sdp#SDP_XP` 또는 `plt#PLT_INTR`가 같이 뜨면 최종 성공 방식이 아니다.

최종 복붙용 실행 명령은 `ATC_XPLANE_RUN_COMMANDS_260610.md`에 정리했다.

## 2026-06-11 Git update and Simulink-related classes

2026-06-11에 X-Plane/ATC 연동 작업을 Git `ver8` 브랜치에 반영했다.

반영된 원격 브랜치:

- local branch: `ver8`
- remote branch: `origin/ver8`
- commit: `c6bbb32 Add X-Plane ATC bridge integration`

이 commit에는 X-Plane/ATC receiver, analyzer, Lua v19/v20/v21, 실행 명령 문서, 최신 기준 문서, AOI draft CSV, `.gitignore` 업데이트가 포함된다.

추가로 IntelliJ에서 파란색으로 보이던 기존 Java class 수정분은 X-Plane/ATC receiver가 아니라 기존 AI simulation/Simulink bridge 쪽 변경이다. 이 변경은 별도 commit으로 `ver8`에 반영한다.

주요 내용:

- `Aircraft`에 simulator state update, velocity, target heading/altitude/speed, Simulink-controlled mode를 추가한다.
- `SimulationGUI`는 선택적으로 Simulink UDP command/state bridge를 사용할 수 있게 변경됐다.
- `SimulinkCommandSender`는 Java simulation command를 UDP `CMD` 문자열로 Simulink에 보낸다.
- `SimulinkStateReceiver`는 Simulink UDP `STATE` 문자열을 받아 aircraft state에 적용한다.
- `Agent`는 전방 시야 내 obstacle만 인식하고 command target과 target altitude/speed를 함께 갱신한다.
- `AISimulation`의 초기 위치/속도 조건이 조정됐다.

주의:

- `.class`, `build_tmp`, `build_atc_tmp`, `logs`는 `.gitignore`로 제외한다.
- IntelliJ의 빨간색 파일은 unversioned, 파란색 파일은 tracked modified 상태를 의미한다.
- `.idea/vcs.xml`의 `j6dof-temp` mapping 변경은 코드 변경이 아니므로 별도 요청 전까지 commit하지 않는다.

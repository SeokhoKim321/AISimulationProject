# PROJECT_CONTEXT.md

## 연구 배경

이 프로젝트는 X-Plane 11 시뮬레이터와 Java 기반 수신/분석 프로그램을 연동하여 위험상황 노출 전후의 조종사 시선 및 조종 반응 데이터를 수집하는 연구이다. 최종 목표는 향후 AI 조종사 모델을 설계할 때 사용할 수 있는 조종 반응 데이터 구조, 이벤트 타임라인, 반응 지표를 구축하는 것이다.

현재 논문/실험 방향은 완성된 AI 조종사 모델 자체를 구현하는 것보다, AI 조종사 모델 개발에 필요한 pilot-in-the-loop 데이터 수집 및 분석 프레임워크를 제시하는 쪽에 맞춰져 있다.

## 현재 연구 방향

현재 핵심 시나리오는 Cessna 172/Skyhawk의 표준화된 final approach 유지 과제 중 예고 없이 crossing intruder를 발생시키고, 조종사의 시선 분포와 조종 입력 반응을 객관적 사건 시각에 맞춰 기록하는 것이다.

참가자가 visual advisory를 보고 반응하는 실험이 아니라 intruder를 직접 발견하고 회피하는 실험으로 재설계한다. 따라서 다음 구현의 기준 시점은 `ADVISORY_SHOWN`이 아니라 `INTRUDER_SPAWNED`이다. 이 이벤트는 객관적인 intruder/threat-exposure onset이며 조종사의 의식적 인지시점은 아니다.

주요 분석 대상:

- `INTRUDER_SPAWNED` 이후 `PILOT_RESPONSE_START`까지의 반응 지연
- spawn 전후 계기/외부 시선 분포와 조종 입력 변화
- speed, runway course/centerline, glidepath 유지 성과
- 최소 수평 거리와 최소 수직 분리
- hazard 진입 여부와 hazard-window
- 향후 객관적인 `INTRUDER_DETECTABLE_ONSET` 이후 반응 지연

논문에서는 `spawn-to-response latency`로 표현하며 이를 recognition/awareness time으로 부르지 않는다. 현재 2026-07-20 데이터는 participant 화면에 countdown/advisory가 보이고 연속 강하 중 여러 trial을 수행한 pilot/system-validation 자료이므로 개선 프로토콜의 본실험 자료와 합치지 않는다.

먼 거리 intruder와 calibrated `INTRUDER_DETECTABLE_ONSET`을 구현한 뒤에는 detectability-to-response를 primary latency로 사용하고 spawn-to-response는 secondary exposure metric으로 유지한다.

## 현재 시스템 구조

```text
동일 X-Plane PC
    -> X-Plane 11 / FlyWithLua
    -> UDP 127.0.0.1:9100 STATE / INTRUDER / EVENT
    -> Java XPlaneReceiverMain
       -> logs/xplane CSV
       -> 현재 hazard/advisory/pilot-response 자동 event
    -> Tobii Pro Spark / Python logger
       -> logs/tobii gaze CSV
    -> PC timestamp 기반 사후 병합·AOI 분석·SVG 시각화

선택적 ATC bridge
    -> Java receiver
    -> ATC Simulator Server 172.16.150.130:50000
```

현재 네트워크 설정:

- X-Plane/Java/Tobii: 같은 PC
- UDP target: `127.0.0.1`
- UDP target port: `9100`
- ATC server: `172.16.150.130:50000`

현재 활성 Lua:

- `FLYWITHLUA_STUDY_INTEGRATED.lua`
- version: `260720_remove_placeholder_gate_v22`
- snapshot: `FLYWITHLUA_STUDY_INTEGRATED_260720_remove_placeholder_gate_v22.lua`

현재 Java entry point:

- receiver: `src/com/example/ai/XPlaneReceiverMain.java`
- session analysis: `src/com/example/ai/XPlaneSessionAnalysisMain.java`
- batch analysis: `src/com/example/ai/XPlaneBatchAnalysisMain.java`

현재 Tobii 도구:

- logger: `tools/tobii/session_xplane_tobii_logger.py`
- merge/analysis: `tools/tobii/analyze_xplane_tobii_session.py`
- visualization: `tools/tobii/visualize_tobii_gaze.py`

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
- v22부터 placeholder `FINAL_APPROACH_GATE_ENTERED` 미발생

현재 등록된 command/macro:

- `flywithlua/study/reset_trial`
- `flywithlua/study/start_trial`
- `flywithlua/study/end_trial`
- `flywithlua/study/start_intruder_headon`
- `flywithlua/study/toggle_cloud`

현재 코드에 남아 있는 visual advisory:

- hazard 조건: 수평 거리 `150 m` 이하 그리고 수직 분리 `30 m` 이하
- clear 조건: 수평 거리 `200 m` 이상 또는 수직 분리 `50 m` 이상
- 화면 문구: `TRAFFIC ALERT`, `CHECK OUTSIDE`
- Lua visual advisory는 화면 표시용이며, CSV event는 Java 자동 event detector가 기존 구조대로 기록한다.
- 직접 발견 실험의 다음 구현에서는 참가자에게 이 문구를 표시하지 않고 내부 hazard/event 기록만 유지할 계획이다.

v20에서 도입되어 v22에도 유지되는 end-trial guard:

- `random_crossing_armed`, `crossing_stabilizing`, `visual_advisory_active` 중 하나라도 true면 `Study End Trial`이 `TRIAL_END`를 기록하지 않는다.
- crossing intruder는 `crossing_min_distance_reported == true`이면 아직 active여도 trial end를 허용한다.
- head-on intruder는 마지막 수평 거리가 clear threshold 이상이면 아직 active여도 trial end를 허용한다.
- 이 경우 `MANUAL_NOTE`에 `ignored=end_before_trial_closed`와 현재 상태 flag를 기록한다.
- 화면 상태는 `WAIT: TRIAL NOT CLOSED`로 표시한다.
- Java의 `PILOT_RESPONSE_END` 내부 상태는 Lua가 직접 알 수 없으므로, 이 guard는 intruder/advisory 진행 중 조기 종료를 줄이는 목적이다.

현재 v22와 다음 설계를 구분한다.

- 현재 random crossing delay: `6~14초`
- 다음 구현 계획: 표준 음성 시작 뒤 독립적인 `3~10초`
- 현재 participant 화면: countdown, debug/status, visual advisory 표시
- 다음 구현 계획: 정상 비행정보 외 실험상태와 visual advisory 숨김

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

단위 주의:

- 기존 CSV 헤더와 Java field 이름 `ias_mps`는 호환성을 위해 유지되고 있으나, 원본 dataref `sim/flightmodel/position/indicated_airspeed`의 실제 단위는 `kias`이다.
- `tas_mps`와 `vertical_speed_mps`는 실제 m/s이다.
- 기존 `ias_mps` 값에 knot 변환계수 `1.94384`를 다시 곱하지 않는다.

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

현재 v22 final approach crossing trial 흐름:

1. `TRIAL_RESET`
2. `TRIAL_START`
3. `SCENARIO_SELECTED`
4. 6~14초 randomized delay
5. `INTRUDER_SPAWNED`
6. 조건 충족 시 `HAZARD_DETECTED`, `ADVISORY_SHOWN`
7. advisory 기준 `PILOT_RESPONSE_START`
8. `MIN_DISTANCE_REACHED`
9. `HAZARD_CLEARED`, `ADVISORY_CLEARED`
10. 필요 시 `PILOT_RESPONSE_END`
11. `TRIAL_END`

v22는 `FINAL_APPROACH_GATE_ENTERED`를 발생시키지 않으며 strict clean에서도 이를 요구하지 않는다.

다음 프로토콜의 계획 흐름:

1. 동일 final-approach 저장상황을 다시 불러온다.
2. 목표 비행 envelope를 확인한 뒤 `TRIAL_RESET`, `TRIAL_START`를 기록한다.
3. `2~5초` baseline 뒤 `TASK_COMMAND_AUDIO_START/END`를 기록한다.
4. audio start 뒤 독립적인 `3~10초`가 지나면 과제 완료 여부와 무관하게 `INTRUDER_SPAWNED`를 기록한다.
5. spawn 전 입력 baseline 대비 지속 변화로 `PILOT_RESPONSE_START`를 판정한다.
6. hazard 관련 event는 참가자 경고가 아니라 내부 severity/outcome으로 기록한다.
7. scenario closure 뒤 `TRIAL_END`를 기록한다.

다음 clean 구조는 이벤트 기록 완결성과 stabilized flight condition을 분리한다. 조기 회피가 hazard를 막거나 반응이 검출되지 않는 경우도 유효 outcome일 수 있으므로 `HAZARD_DETECTED`, `ADVISORY_SHOWN`, `PILOT_RESPONSE_START`를 recording-complete 필수 event로 두지 않는다.

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

최신 same-PC 검증 세션:

- Java prefix: `build_atc_tmp/xplane_self_tobii_20260720_142508`
- Tobii file: `logs/tobii/session_xplane_tobii_20260720_142554_gaze.csv`
- STATE/INTRUDER: 각 `1,568` row
- gaze: `19,408` row
- 정면 유효 gaze: `14,780` (`76.2%`)
- Java/Tobii overlap: `319.518 s`
- total trial: `11`
- 기존 strict clean: `8`
- incomplete: `3`
- clean 평균 advisory-to-response: `0.958 s`

해석 제한:

- trial 2는 `PILOT_RESPONSE_START`, trial 8과 11은 `TRIAL_END`가 없다.
- 8개 clean trial 모두 advisory 직전에 이미 operationally external이어서 이 세션으로 명확한 advisory 후 외부 전환을 주장할 수 없다.
- trial 5는 대표적인 시각화 예시지만, 높은 `OUTSIDE_VIEW` 비율만으로 intruder acquisition을 입증하지 않는다.
- spawn 시 IAS는 `119.8~148.9 KIAS`, AGL은 `101~1,864 ft`, vertical speed는 `-1,014~-44 fpm`이었다.
- 한 번의 연속 강하에서 trial 조건이 크게 달랐으므로 본실험의 반복 자료로 사용하지 않는다.
- 기존 strict clean은 event sequence 완결성만 의미하며 stabilized approach 품질을 보장하지 않는다.

## Eye Tracking / AOI 상태

Tobii Pro Spark는 X-Plane PC에서 SDK 인식, calibration, gaze stream 수신, `logs/tobii` CSV 저장까지 확인됐다. X-Plane/Java/Tobii는 현재 같은 PC에서 실행하며 X-Plane event와 gaze CSV를 PC timestamp로 사후 병합한다.

현재 Tobii 수집 스크립트:

- `tools/tobii/session_xplane_tobii_logger.py`
- X-Plane PC Tobii SDK `64` 폴더에 복사해 실행한다.
- 실행할 때마다 `session_xplane_tobii_YYYYMMDD_HHMMSS_gaze.csv`를 자동 생성한다.
- 기존 gaze CSV를 덮어쓰지 않도록 exclusive create mode를 사용한다.

현재 AOI 정책:

- AOI CSV: `resources/cessna_instrument_aoi_260710.csv`
- 계기 AOI는 `AIRSPEED`, `ATTITUDE`, `ALTITUDE`, `HEADING`, `VERTICAL_SPEED`, `NAV_GPS` 6개만 사용한다.
- 6개 계기 밖의 유효 gaze는 `y < 600 px`에서 `OUTSIDE_VIEW`, 그 아래에서 `PANEL_OTHER`로 분류한다.
- 정면 화면에 유효 좌표가 없는 sample은 `UNTRACKED_OR_OFF_DISPLAY`이다. 현재 데이터만으로 좌우 모니터 응시와 tracking loss를 구분하지 못한다.
- 3모니터 운용 파생값은 `OPERATIONAL_EXTERNAL = OUTSIDE_VIEW + UNTRACKED_OR_OFF_DISPLAY`이다.
- 원본 `aoi`와 파생 `operational_aoi`를 모두 보존한다.
- 시각화 배경은 프로젝트 루트 `AOI그림.png`를 사용한다.

현재 병합 분석 도구:

- `tools/tobii/analyze_xplane_tobii_session.py`
- event 전 AOI 상태, post-event entry, 200 ms 기반 지표와 clean-trial 동일가중 집계를 계산한다.
- `tools/tobii/visualize_tobii_gaze.py`는 8개 event-relative time bin, 200 ms centroid와 AOI timeline SVG를 생성한다.

현재 분석 목표:

- 다음 프로토콜에서 `INTRUDER_SPAWNED` 전후 계기/외부 scan 변화
- gaze dwell time
- spawn 및 향후 detectability onset 이후 `PILOT_RESPONSE_START`까지의 시간
- 조종 반응 전후 계기 scan / outside scan 패턴
- 향후 `RUNWAY_DYNAMIC`, `INTRUDER_DYNAMIC`, `HORIZON_OR_EXTERNAL_OTHER` AOI

200 ms centroid는 시각화용 평균점이며 fixation 또는 인지 판정이 아니다. Eye tracking은 바라본 object/region에 대한 근거를 제공하지만 그 목적이나 awareness를 직접 측정하지 않는다.

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

1. `APISAT_EXPERIMENT_SCENARIO_BASELINE_260724.md`를 기준으로 flap과 전용 RKSI runway 34 저장상황, pilot-test envelope를 확정한다.
2. 현재 Surround 상태에서 새 coarse `SIDE_EXTERNAL` 분석으로 짧은 X-Plane pilot을 수행하고, 중앙 계기 AOI와 측면 event 검출을 확인한 뒤 Surround 유지/복원을 결정한다.
3. Lua의 participant-facing countdown/debug/advisory를 숨기고 표준 WAV와 task event를 추가한다.
4. 음성 시작 뒤 독립적인 `3~10초` 지연과 spawn 기준 response detector를 구현한다.
5. 기존 strict clean을 `recording/event complete`, `flight-condition-valid`, outcome으로 분리한다.
6. 먼 거리 intruder와 pixel/FOV 기반 `INTRUDER_DETECTABLE_ONSET`을 pilot test한다.
7. runway/intruder dynamic AOI와 external-other 분류를 추가한다.
8. 3~5회 pilot trial로 target envelope와 response/dwell threshold를 수동 검증한다.
9. protocol을 동결한 뒤 새 본실험 데이터를 수집한다. 기존 2026-07-20 자료와 합치지 않는다.

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

## 2026-06-22 X-Plane/ATC 재검증 결과

실제 X-Plane/ATC 재검증을 수행했다. 첫 실행에서는 PowerShell에 예전/불완전 명령을 붙여 넣어 뒤쪽 옵션이 누락됐다.

잘못 실행된 출력:

```text
ATC intruder bridge: ... module=SDP#SDP_XP ... fdt=false ownshipLink=false
ATC bridge registered as plt#PLT_XP
ATC bridge registered as sdp#SDP_XP
```

이 상태는 예전에 실패한 `SDP_XP` 별도 연결 방식이며, 관제 화면에는 ownship `xplane01`만 보이고 intruder `intruder01`은 보이지 않는 것이 정상이다.

앞으로 Codex가 Java receiver 실행 명령을 제시할 때는 한 줄짜리 긴 명령을 기본으로 제시하지 않고, PowerShell 백틱 줄바꿈 버전을 우선 제시한다. 각 옵션이 한 줄씩 보이도록 해서 `--atc-intruder-on-ownship-link`, `--atc-intruder-fdt`, `--atc-every` 같은 뒤쪽 옵션 누락을 방지한다.

정상 실행 조건:

```text
ATC bridge registered as plt#PLT_XP
fdt=true
ownshipLink=true
```

정상 실행에서는 `sdp#SDP_XP` 또는 `plt#PLT_INTR` 등록 메시지가 뜨면 안 된다.

재검증 성공 파일:

- `build_atc_tmp/xplane_atc_intruder_onlink_260622_test2.csv`
- `build_atc_tmp/xplane_atc_intruder_onlink_260622_test2_intruder.csv`
- `build_atc_tmp/xplane_atc_intruder_onlink_260622_test2_events.csv`

수집 결과:

- STATE rows: `2051`
- INTRUDER rows: `2051`
- EVENT rows: `212`
- trial id range: `5` to `21`
- intruder 위경도/고도는 모든 row에서 정상 기록됐고 `NaN`은 없었다.
- strict clean trial: `7 / 17`
- clean trials: `9`, `10`, `11`, `16`, `18`, `19`, `20`
- clean 평균 최소 수평거리: `10.4 m`
- clean 평균 `spawn->hazard`: `6.730 s`
- clean 평균 `advisory->response`: `0.684 s`
- clean 평균 `hazard_window`: `2.444 s`

Incomplete 주요 원인:

- `PILOT_RESPONSE_START`/`PILOT_RESPONSE_END` 누락: trial `7`, `13`, `15`, `17`
- `TRIAL_END`가 `PILOT_RESPONSE_END`보다 먼저 기록: trial `12`, `14`
- `TRIAL_END` 누락: trial `21`
- intruder 발생 전 종료 또는 초기/재시작 구간이 섞인 trial: trial `6`, `8`

분석 도구 주의:

- 2026-06-22에 `XPlaneSessionAnalysisMain`을 v21 intruder CSV 헤더 기반으로 수정했다.
- 이제 `horizontal_distance`, `vertical_separation`, `sim_time_s` 컬럼을 header name으로 찾아 읽으므로 v21의 intruder geo 컬럼 추가 이후에도 거리 통계가 정상 출력된다.
- `XPlaneBatchAnalysisMain`도 `xplane_session_...csv`뿐 아니라 `xplane_atc_...csv` 같은 receiver output 파일명을 직접 받을 수 있도록 완화했다.
- receiver 재시작이나 sim time reset이 섞여 세션 전체의 첫 advisory/response 시간이 역전되는 경우, session-level response latency는 음수 대신 `n/a`로 표시한다.

수정 후 검증:

```powershell
javac --release 21 -d build_atc_tmp src\com\example\ai\XPlane*.java src\com\example\ai\AtcServer*.java
java -cp build_atc_tmp com.example.ai.XPlaneSessionAnalysisMain build_atc_tmp\xplane_atc_intruder_onlink_260622_test2.csv
java -cp build_atc_tmp com.example.ai.XPlaneBatchAnalysisMain build_atc_tmp\xplane_atc_intruder_onlink_260622_test2.csv
```

수정 후 batch 결과:

- total trials: `17`
- clean trials: `7`
- clean rate: `41.2%`
- clean 평균 `spawn->hazard`: `6.730 s`
- clean 평균 `advisory->response`: `0.684 s`
- clean 평균 `hazard_window`: `2.444 s`
- clean 평균 최소 수평거리: `10.406 m`
- clean 평균 최소 수직분리: `11.275 m`

## 2026-07-06 CWP display issue and operational application framing

ATC/CWP 화면 캡처(`xplane화면캡처.png`, `충돌상황캡처.png`, `확대화면캡처.png`)를 확인했다. 일반 배율에서는 ownship과 intruder가 가까워질 때 `xplane01`/`intruder01` 라벨과 숫자 블록이 겹쳐 가독성이 떨어졌다. 확대 화면에서는 겹침이 상당히 완화됐다.

`AISimulationProject/Simulator Server - 20250520_DAS` 폴더 전체를 검색한 결과, 해당 폴더에는 CWP 화면을 직접 그리는 렌더링 소스가 없었다.

확인된 내용:

- `src/communication/CommunicationMananger.java`, `ICD.java`에는 CWP 패킷 전달 규격과 `dst:cwp#CWP1`, `typ:fdt` 관련 주석이 있다.
- `src/gui/dialogs`에는 서버 운영 GUI(`MainWindow`, `ConnectionPanel`, `ScenarioPanel`, `LogPanel`)만 있다.
- `paintComponent`, `drawString`, `Graphics2D`, radar/CWP display panel, `Label[1]` 같은 실제 화면 라벨 렌더링 코드는 없다.
- 따라서 현재 폴더의 `Simulator Server - 20250520_DAS`는 CWP 화면 프로그램이 아니라 CWP 클라이언트로 데이터를 중계하는 서버 프로젝트로 판단한다.

근본적인 라벨 겹침 수정은 실제 CWP 클라이언트 소스에서 수행해야 한다. 찾아야 할 후보 키워드는 `CWP`, `Controller Working Position`, `Radar`, `RDR`, `ATC Client`, `paintComponent`, `drawString`, `Graphics2D`, `Label[`, `fid`이다.

현재 Java bridge에서 가능한 안전한 완화책은 관제 표시용 flight id를 짧게 쓰는 것이다.

```powershell
--atc-fid xp01 `
--atc-intruder-fid in01 `
```

라벨 겹침 완화 테스트용 실행 명령:

```powershell
java -cp build_atc_tmp com.example.ai.XPlaneReceiverMain `
  9100 `
  build_atc_tmp\xplane_atc_label_test_260624.csv `
  session_atc_label_test_260624 `
  --atc-host 172.16.150.130 `
  --atc-port 50000 `
  --atc-module PLT_XP `
  --atc-fid xp01 `
  --atc-intruder-fid in01 `
  --atc-intruder-on-ownship-link `
  --atc-intruder-fdt `
  --atc-every 5
```

CWP 확대 화면 숫자 해석:

- `xp01`, `in01`: ATC flight id
- `013`, `019`: 고도, CWP상 hundreds of feet 형태로 표시되는 것으로 판단 (`013`은 약 1300 ft)
- `108`, `074`: 속도, knot
- `1200`: squawk code, 현재 Java bridge에서 고정 송신
- `-790FPM`, `-659FPM`: 수직속도, ft/min, 음수는 하강
- `301`, `130`: heading, degree
- `Preventive!`: CWP/DAA 쪽 conflict alert 단계로 판단
- `-`, `-------`, `----`: 현재 Java bridge가 보내지 않는 flight plan/route/clearance 계열 필드로 판단

현재 연구의 적용 방향은 다음과 같이 정리한다.

```text
X-Plane 기반 조종사 반응 데이터 수집 및 ATC 연동 시뮬레이션 플랫폼
```

연구/보고용 정의:

```text
충돌위험 상황에서 조종사 시각 주의, 조종 입력, 항공기 상태, 관제 표시 정보를 동기화 수집하기 위한 human-in-the-loop flight simulation data acquisition framework
```

부대 적용 관점에서는 X-Plane 자체를 반입해 쓰는 것이 핵심이 아니라, 연구실에서 검증한 데이터 수집/분석 메커니즘을 인가된 군 훈련 시뮬레이터 환경에 이식하는 것이 핵심이다.

보안/인가 리스크:

- X-Plane, FlyWithLua, 외부 Java 프로그램, Tobii SDK, 네트워크 연동은 부대 내부망/훈련망에서 바로 사용하기 어려울 수 있다.
- 따라서 초기 보고에서는 "AI가 조종사를 평가하는 시스템"보다 "교관 디브리핑을 보조하는 데이터 기반 분석 도구"로 설명하는 것이 적절하다.

KAI 마린온 시뮬레이터 연계 가능성:

- 부대에는 KAI에서 개발한 마린온 조종사 시뮬레이터가 이미 훈련에 사용되고 있다.
- 해당 장비가 항공기 상태, 조종 입력, 이벤트, 로그 export 또는 TCP/UDP 인터페이스를 제공한다면 X-Plane/FlyWithLua 입력부를 KAI 시뮬레이터 입력부로 교체할 수 있다.
- Java 분석 파이프라인의 핵심인 trial timeline, hazard/advisory/response event, clean trial 분석, 반응시간 산출 구조는 유지 가능하다.

부대 보고용 논리:

```text
기존 시뮬레이터 훈련을 단순 시간 이수형 훈련에서 행동 데이터 기반 평가·디브리핑 훈련으로 전환한다.
```

구체적으로는 마린온 시뮬레이터 훈련 중 조종사가 특정 비정상 상황, 충돌위험 상황, 기상 악화, 관제 지시, 접근/착륙 중 판단 상황에서 언제 인지하고 어떤 조작을 했는지 기록한다. 이후 숙련 조종사 기준 모델 또는 SOP 기반 기준 모델과 비교해 훈련 후 디브리핑에 활용한다.

초기 기준 모델은 AI 모델로 단정하지 않고 다음 두 가지로 정의한다.

- 숙련 조종사 기준 모델: 교관/고경력 조종사의 반응 패턴을 기준선으로 삼는다.
- 절차 기반 기준 모델: 비행교범, SOP, 체크리스트, 교관 평가 기준을 모델화한다.

확인해야 할 KAI 시뮬레이터 인터페이스 항목:

- 위도, 경도, 고도, 속도, heading, attitude, 조종 입력 export 가능 여부
- 실시간 TCP/UDP 연동 가능 여부
- 로그 파일 저장 및 외부 분석 가능 여부
- 외부 프로그램 설치 가능 여부
- 내부망에서 별도 분석 PC 연결 가능 여부
- Tobii Spark 같은 eye tracker 반입 및 SDK 설치 가능 여부
- 보안심사 또는 장비 인가 절차

보고용 한 문장:

```text
마린온 시뮬레이터 훈련 데이터를 활용하여 조종사의 상황대응 행동을 정량화하고, 숙련 조종사 또는 SOP 기반 기준 모델과 비교함으로써 훈련 후 객관적 디브리핑을 제공하는 체계로 발전시킬 수 있다.
```

## 2026-07-08 Tobii Pro Spark SDK first connection

Tobii Pro Spark 장비가 도착했고, Eye Tracker Manager에서 사용자 calibration까지 완료했다. 현재 개발/실험 구조는 단기적으로 기존 X-Plane PC와 Java PC 분리 구조를 유지한다.

단기 운용 구조:

```text
X-Plane PC
    -> X-Plane 11 / FlyWithLua 실행
    -> Tobii Pro Spark 연결
    -> Tobii Eye Tracker Manager calibration
    -> Python 3.10 + Tobii Pro SDK gaze logger 실행
    -> gaze CSV 저장

Java PC
    -> XPlaneReceiverMain 실행
    -> X-Plane STATE / INTRUDER / EVENT UDP 수신
    -> 기존 CSV 저장
    -> ATC Simulator Server 송신
    -> 분석/문서/Git 작업
```

장기적으로는 X-Plane PC에 Java receiver까지 통합해도 무방하다. 다만 APISAT 제출 전에는 Tobii, Python SDK, gaze logger, timestamp 병합이라는 새 변수가 많으므로 기존 Java PC/X-Plane PC 분리 구조를 유지하고 gaze CSV를 사후 병합하는 방향으로 진행한다.

Tobii SDK 설치/확인 상태:

- SDK 위치: `C:\Users\ACSL-SERVER\Desktop\김석호\TobiiPro.SDK.Python.Windows_2.1.0.1`
- 사용 폴더: `...\64`
- 해당 SDK는 `.whl` 설치형이 아니라 `64` 폴더 안의 `tobii_research.py`, `tobiiresearch` package, `tobii_research_interop.pyd`를 Python import path에서 직접 사용하는 구조다.
- X-Plane PC의 Python: `py -3.10 --version` 결과 `Python 3.10.0rc2`
- `import tobii_research as tr` 성공
- `tr.find_all_eyetrackers()` 성공

SDK 장비 인식 결과:

```text
count 1
tobii-prp://TPE01-100206101311 Tobii Pro Spark TPE01-100206101311 Tobii Pro Spark
Firmware: b05c12f988
```

gaze stream 1차 결과:

- 10초 수집에서 `gaze samples: 577` 확인
- `tobii_gaze_test.csv` 저장 성공
- 첫 시도에서는 `left_gaze_x`, `right_gaze_x`, pupil 값이 `NaN`이고 validity가 `0`이라 눈 위치/추적 상태가 맞지 않았다.
- 자세/추적 상태를 조정한 뒤 `left_validity=1`, `right_validity=1`, pupil validity `1`, gaze 좌표 정상 수신을 확인했다.

정상 gaze sample 예:

```text
left_gaze_x: 0.1623
left_gaze_y: 0.7860
right_gaze_x: 0.1665
right_gaze_y: 0.7613
left_validity: 1
right_validity: 1
left_pupil_diameter: 3.43
right_pupil_diameter: 3.59
```

좌표 해석:

- gaze coordinate는 display area 기준 normalized coordinate로 판단한다.
- `x=0.0`은 화면 왼쪽, `x=1.0`은 화면 오른쪽이다.
- `y=0.0`은 화면 위쪽, `y=1.0`은 화면 아래쪽이다.
- 예시 `x≈0.16`, `y≈0.76`은 화면 왼쪽 아래 영역을 보는 상태다.

현재까지 Tobii 상태 판정:

```text
SDK에서 Spark 인식: 성공
실시간 gaze stream 수신: 성공
timestamp 포함 CSV 저장: 성공
유효 gaze 좌표 수신: 성공
X-Plane EVENT CSV와 병합: 다음 단계
```

현재 추가된 Tobii 테스트 파일:

- `tools/tobii/list_trackers.py`
- `tools/tobii/gaze_logger.py`
- `TOBII_SPARK_FIRST_TEST_260708.md`

다음 작업:

1. X-Plane PC에서 standalone gaze CSV를 30~60초 저장하며 중앙/좌/우/상/하 시선 이동이 좌표 변화와 맞는지 확인한다.
2. X-Plane 화면을 띄운 상태에서 gaze CSV를 저장해 6개 계기 AOI와 `OUTSIDE` 분류가 예상대로 동작하는지 확인한다.
3. Java PC에서 기존 `XPlaneReceiverMain`을 실행하고, X-Plane PC에서 Tobii logger를 동시에 실행한다.
4. 짧은 trial 1~3개만 수집한다.
5. Java `_events.csv`와 Tobii gaze CSV의 시간대가 겹치는지 확인한다.
6. 첫 gaze 분석은 `ADVISORY_SHOWN` 전후 계기/`OUTSIDE` 전환, `INTRUDER_SPAWNED` 이후 `OUTSIDE` gaze, gaze 이후 `PILOT_RESPONSE_START`까지의 시간만 목표로 한다.

## 2026-07-10 Tobii logger operational script

X-Plane PC에서 X-Plane trial과 동시에 장시간 gaze CSV를 기록하기 위한 기준 스크립트를 추가했다.

추가 파일:

- `tools/tobii/session_xplane_tobii_logger.py`

용도:

- X-Plane PC의 Tobii SDK `64` 폴더에 복사해 실행한다.
- 600초 동안 gaze stream을 CSV로 저장한다.
- `pc_time_sec`, `pc_time_ns`, `pc_time_iso`를 함께 저장해 Java PC의 X-Plane event CSV와 사후 병합할 수 있게 한다.
- 좌/우 눈 gaze와 validity를 저장하고, 유효한 좌표만 평균낸 `avg_gaze_x`, `avg_gaze_y`를 추가 저장한다.

기본 실행 위치:

```powershell
cd "C:\Users\ACSL-SERVER\Desktop\김석호\TobiiPro.SDK.Python.Windows_2.1.0.1\64"
py -3.10 session_xplane_tobii_logger.py
```

자동 output:

```text
session_xplane_tobii_YYYYMMDD_HHMMSS_gaze.csv
```
## 2026-07-10 X-Plane + Tobii merged analysis status

Tobii Pro Spark gaze CSV and X-Plane/Java receiver logs were successfully aligned by PC timestamp for the first integrated trial set.

Current merged test files:

- `build_atc_tmp/xplane_tobii_test_260710.csv`
- `build_atc_tmp/xplane_tobii_test_260710_intruder.csv`
- `build_atc_tmp/xplane_tobii_test_260710_events.csv`
- `build_atc_tmp/session_xplane_tobii_test_260710_gaze.csv`

Current analysis script:

- `tools/tobii/analyze_xplane_tobii_session.py`

Current analysis outputs:

- `build_atc_tmp/xplane_tobii_test_260710_gaze_aoi.csv`
- `build_atc_tmp/xplane_tobii_test_260710_trial_gaze_summary.csv`

Confirmed results:

- X-Plane state rows: `1483`
- X-Plane intruder rows: `1483`
- X-Plane event rows: `195`
- Tobii gaze rows: `22547`
- Valid gaze rows: `15647` (`69.4%`)
- Total trials in Java analysis: `15`
- Strict clean trials: `11`
- Incomplete trials: `4`
- Clean trials: `8`, `9`, `10`, `11`, `12`, `13`, `14`, `15`, `16`, `17`, `18`

AOI analysis notes:

- `resources/cessna_aoi_draft_260526.csv` is still a draft AOI file.
- AOI rows marked `exclude` in either `priority` or `status` are skipped by the Tobii analysis script.
- `DEBUG_TEXT` is now excluded from AOI classification.
- The script now includes a 200 ms dwell-based first AOI metric. This is more defensible than a single-sample first gaze hit for reporting.
- The current `ADVISORY` AOI was not detected in the first merged run. The likely next action is to refine the advisory AOI coordinates from a real X-Plane screenshot before drawing conclusions.
- `OUTSIDE_CENTER` hits should be interpreted as broad outside-view gaze, not confirmed intruder visual acquisition.

Immediate next steps:

1. Refine AOI coordinates using actual X-Plane screenshots from the same monitor and view configuration.
2. Add pre-event AOI/transition metrics to separate already-looking-outside cases from true post-advisory gaze shifts.
3. Repeat a small number of Java-only receiver + Tobii logger trials and keep file names unique for each run.

Follow-up implementation:

- `tools/tobii/draw_aoi_overlay.ps1` draws the current AOI CSV boxes on a cockpit screenshot for coordinate verification.
- `build_atc_tmp/cessna_aoi_overlay_260710.png` was generated from `resources/cessnacokpit.png`.
- `XPLANE_TOBII_AOI_MEANING_260710.md` documents what each fixed AOI coordinate label means.
- `tools/tobii/analyze_xplane_tobii_session.py` now reports pre-event AOI state and post-event AOI entry metrics.
- For gaze interpretation, prefer `advisory_to_first_<AOI>_entry_s` and dwell metrics over simple first-hit metrics.

Current AOI policy:

- The accepted fixed instrument AOIs are `AIRSPEED`, `ATTITUDE`, `ALTITUDE`, `HEADING`, `VERTICAL_SPEED`, and `NAV_GPS`.
- The current AOI file is `resources/cessna_instrument_aoi_260710.csv`.
- Valid gaze outside the six fixed instrument AOIs is classified as `OUTSIDE`.
- `RUNWAY_ZONE`, `ADVISORY`, and `OUTSIDE_CENTER` are no longer used as separate AOIs.
- X-Plane PC only needs `tools/tobii/session_xplane_tobii_logger.py` copied into the Tobii SDK `64` folder for gaze recording.
- Java PC keeps the merged analysis scripts and AOI CSV unless analysis is intentionally moved to the X-Plane PC.

Current repeated-run file naming policy:

- Tobii logger creates `session_xplane_tobii_YYYYMMDD_HHMMSS_gaze.csv` automatically on the X-Plane PC.
- Java receiver creates `logs\xplane\xplane_session_YYYYMMDD_HHMMSS*.csv` automatically when IntelliJ `Program arguments` is blank or only `9100`.
- Fixed output paths should be avoided for repeated experiments because they can append to or overwrite older runs.

## 2026-07-16 Tobii gaze visualization

Tobii gaze CSV visualization was added using only Python standard library SVG output.

Current script:

- `tools/tobii/visualize_tobii_gaze.py`

Current documentation:

- `TOBII_GAZE_VISUALIZATION_260716.md`

The script visualizes:

- all valid gaze samples as screen-space scatter points
- trial-specific gaze trajectory around `ADVISORY_SHOWN`
- trial AOI timeline with X-Plane event markers

First test command used existing 260710 data:

```powershell
py tools\tobii\visualize_tobii_gaze.py --gaze build_atc_tmp\session_xplane_tobii_test_260710_gaze.csv --events build_atc_tmp\xplane_tobii_test_260710_events.csv --aoi resources\cessna_instrument_aoi_260710.csv --background resources\cessnacokpit.png --output-prefix build_atc_tmp\xplane_tobii_test_260710_visual --trial 8
```

Generated SVG outputs:

- `build_atc_tmp\xplane_tobii_test_260710_visual_gaze_scatter.svg`
- `build_atc_tmp\xplane_tobii_test_260710_visual_trial_8_advisory_shown_trajectory.svg`
- `build_atc_tmp\xplane_tobii_test_260710_visual_trial_8_aoi_timeline.svg`

These figures are intended for quick inspection and meeting/APISAT visuals. Interpretation should still rely on dwell, entry timing, valid gaze rate, and event-aligned trial summaries.

## 2026-07-16 Same-PC X-Plane/Java/Tobii operation

- X-Plane, Java receiver, and Tobii logger are now operated on the same X-Plane PC.
- The Git-managed reference `FLYWITHLUA_STUDY_INTEGRATED.lua` uses UDP target `127.0.0.1:9100`, matching the active FlyWithLua script.
- The active script remains `260610_intruder_geo_atc_v21`; the packet schema and scenario logic were not changed.
- Run `XPlaneReceiverMain` with blank program arguments to create timestamped CSV files under `logs/xplane`.
- The Tobii session logger writes timestamped gaze CSV files directly under `logs/tobii`, even when it is launched from the SDK `64` folder.
- `AISIMULATION_PROJECT_DIR` may override the default project root when the workspace is moved.

## 2026-07-16 Same-PC integrated trial and AOI verification

- The first same-PC X-Plane/Java/Tobii trial session was successfully collected and merged.
- Java session `session_20260716_161359` contains 1,262 STATE rows, 1,262 INTRUDER rows, 10 hazards, 10 advisories, and 10 detected pilot responses.
- Trials: 10 total, 9 strict clean, 1 incomplete because trial 10 has no `TRIAL_END`.
- Tobii session `session_xplane_tobii_20260716_161408` contains 16,928 gaze rows and 12,414 valid rows (`73.3%`).
- Java event and Tobii gaze timelines overlap for `284.194 s`.
- `AOI그림.png` is the new standard visualization background. It represents the current full-screen X-Plane cockpit view and supersedes `resources/cessnacokpit.png` for new figures.
- The current six AOI rectangles were checked on `AOI그림.png` and correctly cover AIRSPEED, ATTITUDE, ALTITUDE, HEADING, VERTICAL_SPEED, and NAV_GPS.
- `OUTSIDE` means only that valid gaze was outside the six instrument AOIs; it does not prove intruder acquisition.

AOI fallback refinement:

- The former single `OUTSIDE` category is superseded by `OUTSIDE_VIEW` and `PANEL_OTHER`.
- Instrument rectangles are evaluated first.
- A remaining valid point with `y < 600 px` is `OUTSIDE_VIEW`; one with `y >= 600 px` is `PANEL_OTHER`.
- The boundary is configurable with `--panel-top-y`; default is `600` for the current full-screen cockpit view.
- Reanalysis produced `OUTSIDE_VIEW=8457` and `PANEL_OTHER=2231`.
- All 70 gaze rows in the ATTITUDE–HEADING 20 px gap were classified as `PANEL_OTHER`.

Time-panel visualization update:

- `visualize_tobii_gaze.py` now creates a fixed event-relative time-bin SVG in addition to the existing scatter, trajectory, and AOI timeline.
- The initial time-panel version used six broad bins; it is superseded by the current eight-panel rule below.
- Each panel uses one point color and an explicit time label; blue-to-red color interpretation is not required.
- Each panel prints valid/plotted sample counts, and empty pre-event panels remain visible as evidence of missing valid gaze.

Pre-advisory validity audit for the 2026-07-16 session:

- All ten trials contained the expected raw Tobii samples in the two seconds before `ADVISORY_SHOWN`; the empty panels are not caused by timestamp misalignment.
- Aggregate valid rate was `188/601 = 31.3%` for `-2~-1 s` and `273/595 = 45.9%` for `-1~0 s`.
- Trials 1, 5, and 8 had zero valid samples throughout both pre-advisory bins, with both left and right validity equal to zero.
- These intervals contain no valid front-display coordinate. The initial tracking-loss-only interpretation was later corrected after confirming the three-monitor setup.

Three-monitor interpretation correction:

- The setup uses three monitors; Tobii Pro Spark tracks only the front monitor.
- The previous statement that empty pre-advisory panels necessarily mean tracking loss is superseded.
- New outputs use `UNTRACKED_OR_OFF_DISPLAY` instead of `INVALID`.
- This category means no valid coordinate was observed on the tracked front display and may include side-monitor gaze as well as genuine tracking loss, blink, occlusion, or out-of-range posture.
- The current CSV cannot distinguish those causes, so no side-monitor gaze or tracker failure is inferred without additional evidence.
- Fixed time-bin figures display both front-monitor valid counts and `untracked/off-display` counts for every interval.

Final three-monitor reanalysis:

- Total gaze rows: `16,928`.
- Valid front-display gaze: `12,414` (`73.3%`).
- `UNTRACKED_OR_OFF_DISPLAY`: `4,514` (`26.7%`).
- Pre-advisory two-second front-display valid rates by trial: T1 `0.0%`, T2 `98.3%`, T3 `27.5%`, T4 `94.2%`, T5 `0.0%`, T6 `94.2%`, T7 `11.7%`, T8 `0.0%`, T9 `28.2%`, T10 `30.5%`.
- New outputs: `build_atc_tmp/xplane_tobii_20260716_161359_three_monitor_gaze_aoi.csv` and `..._three_monitor_trial_gaze_summary.csv`.

Hybrid time-bin update:

- The default event-relative figure now uses eight mixed-width bins: `-2~-1`, `-1~0`, `0~+0.5`, `+0.5~+1`, `+1~+2`, `+2~+3`, `+3~+4`, and `+4~+5 s`.
- The first post-advisory second is split into 0.5 s panels to preserve rapid gaze/response changes; using 2 s panels there would obscure event order.
- Trial 4 verified both post-advisory half-second bins with `30/30` front-display valid samples.
- The final `+3~+5 s` interval is split into `+3~+4` and `+4~+5 s`, producing a balanced eight-panel `4 x 2` figure.

Within-panel movement update:

- Raw points in the eight-panel figure are summarized as 200 ms binned gaze centroids.
- Discrete colors and event-relative midpoint labels show time within each panel.
- Directional arrows connect only consecutive non-empty 200 ms windows; gaps are not interpolated.
- This is not a fixation algorithm. Report the output as `200 ms binned gaze centroid` movement.
- Trial 4 verification generated 36 centroids and 28 arrows.

## 2026-07-20 AOI timeline readability update

- Dense rotated event labels were removed from the AOI timeline.
- Events are represented by staggered numbered markers and a separate two-column relative-time event list.
- AOI colors use a separate three-column legend, and the timeline axis shows `TRIAL_START`-relative seconds.
- `xplane_tobii_20260716_161359_centroids_trial_4_aoi_timeline.svg` was regenerated at `1400x592` with the clean layout.

## 2026-07-20 Operational external, trial aggregation, and Lua v22

- Three-monitor experiment policy: `OPERATIONAL_EXTERNAL = OUTSIDE_VIEW + UNTRACKED_OR_OFF_DISPLAY`.
- The raw `aoi` field is preserved; `operational_aoi` is added as a derived interpretation field.
- `analyze_xplane_tobii_session.py` now writes a clean-trial equal-weight `*_trial_aggregate_summary.csv` grouped by `all`, `front`, `left`, and `right`.
- Statistics include `n`, mean, median, standard deviation, minimum, and maximum.
- Only complete event-relative windows contained inside `TRIAL_START~TRIAL_END` are aggregated.
- Verified clean-trial count: `9`; incomplete trial 10 remains excluded.
- Overall mean `advisory_to_response_s=0.903`, mean trial operational-external rate `72.6%`.
- `8/9 = 88.9%` of clean trials were already operationally external immediately before advisory, so first external-entry latency is not used as the primary gaze result.
- New analysis files use prefix `build_atc_tmp/xplane_tobii_20260716_161359_operational`.

Lua/Java event update:

- Active Lua version is `260720_remove_placeholder_gate_v22`.
- Active and Git-managed Lua both use `127.0.0.1:9100` and have matching hashes.
- New Lua trials no longer emit placeholder `FINAL_APPROACH_GATE_ENTERED`.
- Java clean-trial requirements no longer require that event, while the enum/parser remains compatible with old CSV files containing it.
- Lua snapshot: `FLYWITHLUA_STUDY_INTEGRATED_260720_remove_placeholder_gate_v22.lua`.

## 2026-07-20 Lua v22 same-PC validation session

- Java prefix: `build_atc_tmp/xplane_self_tobii_20260720_142508`.
- Tobii file: `logs/tobii/session_xplane_tobii_20260720_142554_gaze.csv`.
- STATE/INTRUDER rows: `1,568` each; gaze rows: `19,408`; valid front-display gaze: `14,780` (`76.2%`).
- Java/Tobii timestamp overlap: `319.518 s`.
- `FINAL_APPROACH_GATE_ENTERED=0` and `SCENARIO_SELECTED=11`, confirming v22 placeholder-gate removal.
- Trials: `11` total, `8` strict clean, `3` incomplete.
- Trial 2: missing `PILOT_RESPONSE_START`; trials 8 and 11: missing `TRIAL_END`.
- Clean mean `advisory_to_response=0.958 s`; mean trial operational-external rate `78.7%`.
- All 8 clean trials were operationally external immediately before advisory.
- Clean mean operational-external rates were `98.7%` at `-1~0 s`, `98.7%` at `0~+0.5 s`, and `99.1%` at `+0.5~+1 s`.
- Under the current forced operational policy, advisory-near external rate is saturated and cannot strongly distinguish a post-advisory gaze shift.
- Component analysis clarifies the pattern: at `-1~0 s`, equal-trial means were `OUTSIDE_VIEW=31.3%` and `UNTRACKED_OR_OFF_DISPLAY=67.4%`; at `0~+0.5 s`, `63.7%` and `34.9%`; at `+0.5~+1 s`, `87.7%` and `11.4%`.
- This supports a shift from side-monitor/unobserved operational external toward the tracked front-monitor outside view, not an external-versus-instrument transition.
- Representative clean trial 5 combines `88.9%` valid gaze with `0.906 s` advisory-to-response and is visualized under prefix `build_atc_tmp/xplane_self_tobii_20260720_142508_visual`.
## 2026-07-22 Trajectory visualization interpretation

- The advisory-centered trajectory now distinguishes faint raw gaze samples from a high-contrast, arrowed path of consecutive 200 ms gaze centroids.
- Event labels are shown in a separate numbered footer instead of being overlaid at the lower-left of the cockpit image.
- A 200 ms centroid is the arithmetic mean position of all valid front-display gaze samples in that 200 ms slice; it is a visualization summary and is not a fixation classification.
- Trial 5 is genuinely dominated by `OUTSIDE_VIEW` in the advisory-centered window (`374/421`, 88.8%), but `OUTSIDE_VIEW` only identifies the forward outside-view region and does not establish intruder acquisition.

## 2026-07-24 Step 1 scenario design and research correction

The authoritative design document is `APISAT_EXPERIMENT_SCENARIO_BASELINE_260724.md`. It records approved direction and candidate pilot parameters; it does not mean the changes have already been implemented.

### Experimental unit and approach task

- One trial must start from one reproducible saved final-approach situation.
- Reload the aircraft state before every encounter instead of presenting many intruders during one uninterrupted descent.
- Freeze aircraft model, mass/fuel, flap configuration, runway/weather, view, start distance/AGL, course, IAS and glidepath after pilot validation.
- Candidate starting state is approximately 3 NM, 900~1,000 ft AGL, runway course about 325°, and a 3° glidepath.
- X-Plane `Log.txt` confirms the default Laminar Research Cessna 172SP, and the STATE path plus X-Plane `apt.dat` confirm RKSI runway 34.
- Current STATE does not log flap state, so the historical flap configuration is unknown. Add automatic flap handle/deployment logging in the next coordinated schema update.
- Create a new dedicated situation such as `APISAT_RKSI34_C172SP_3NM.sit`; do not overwrite the existing situation files.
- `70 KIAS` is a candidate only. Confirm flap/weight conditions and the C172SP POH before implementation.
- Give a standardized prerecorded maintenance instruction. The first candidate is `Cessna Zero One, continue straight-in, maintain seven zero knots, track runway centerline.`
- Do not use a large random heading change, fixed altitude during descent, or a per-trial numeric vertical-speed instruction.
- Treat vertical path as a prebriefed stabilized-approach requirement and score it with a rolling measure. A 3° reference is approximately `VS_target_fpm = -5.3 × groundspeed_kt`.

### Task and intruder timing

- Record a 2~5 s baseline after `TRIAL_START`.
- Log audio start/end automatically when the prerecorded file is played.
- Draw the intruder delay independently from 3~10 s after audio start.
- Do not wait for task completion before spawning the intruder.
- Keep the maintenance task active through the remainder of the trial.
- Record command elapsed time, task error and active-control state at spawn so task compliance is not mistaken for avoidance.

### Participant display and event interpretation

- Hide experiment countdown/status/debug and the visual `TRAFFIC ALERT`/`CHECK OUTSIDE`.
- Preserve all internal event, separation, hazard, minimum-distance and end-guard logic for experimenter analysis.
- In new no-alert trials, use `HAZARD_DETECTED/CLEARED` for the internal risk window and do not emit `ADVISORY_SHOWN/CLEARED` when no advisory was actually presented. Retain parser compatibility for historical data.
- `INTRUDER_SPAWNED` is the objective exposure anchor, not proof of detection.
- Use a future calibrated pixel/FOV event such as `INTRUDER_DETECTABLE_ONSET` when the distant-intruder design is implemented.
- Keep the current hazard trigger (`horizontal <=150 m` and `vertical <=30 m`) and clear hysteresis (`horizontal >=200 m` or `vertical >=50 m`) as internal severity/outcome measures unless separately revised.

### Response and validity redesign

- Replace the current advisory baseline with approximately one second of pre-spawn controls.
- Candidate response persistence is 200~300 ms, subject to labeled pilot validation.
- Do not require hazard, advisory or response events for recording completeness.
- Split trial evaluation into recording/event completeness, flight-condition validity and behavioral outcome.
- No detected response and early avoidance before hazard entry are valid outcomes when recording and scenario conditions are otherwise valid.
- Keep gaze validity as a reported data-quality dimension rather than silently converting every missing front coordinate into a side-monitor location.

### Three-monitor feasibility and hardware decision

- Local baseline on 2026-07-24 is an RTX 2060 with driver `560.94` and three independent `1920x1080` Windows displays at x=`-1920`, `0`, and `1920`; the center display is primary. This is extended desktop, not yet one Surround display.
- Test NVIDIA Surround with the three displays flat and co-planar, identical scaling, Spark centered, and whole-display calibration.
- Use 15 targets, five on each monitor, repeated three times.
- Record per-monitor valid rate, median and 95th-percentile error, monitor classification, dropout/recovery and center-AOI degradation.
- Proposed project go/no-go criteria are side classification >=90%, side valid rate >=70%, and center error degradation <=20%; these are research criteria, not Tobii specifications.
- Surround creates one logical coordinate space but does not itself expand the tracker's physical gaze-angle coverage.
- If the test fails, retain center-only measurement and the `UNTRACKED_OR_OFF_DISPLAY` interpretation.
- A second Spark is only a technical possibility, not a confirmed solution. Verify multi-device streams, independent display calibration, IR interference, USB bandwidth and timestamps with Tobii or a loan setup before purchase.
- Tobii 4C remains outside this research pipeline because it is not supported by the Tobii Pro SDK used here.
- Tobii's current official material lists Spark's optimal 16:9 screen size as up to 27 inches, while its advanced-setup guidance gives a 35° maximum gaze angle and describes geometry-dependent large/wide/multi-display use. This supports a feasibility test, not an assumption of success.

### Distant intruder and external gaze purpose

- A farther intruder requires separate spawn and objective detectability events.
- Estimate pixel size from range, FOV and resolution, then calibrate it with screenshots and pilot tests rather than choosing an arbitrary pixel threshold.
- Planned object/region AOIs include `RUNWAY_DYNAMIC`, `INTRUDER_DYNAMIC`, `HORIZON_OR_EXTERNAL_OTHER`, monitor-side outside regions and existing instrument AOIs.
- A sustained gaze inside an object AOI is evidence of looking toward that object, not direct evidence of awareness or intent.
- Combine AOI dwell with flight context and control response for cautious interpretation.

### Paper scope

- The APISAT full-paper deadline is 2026-09-15.
- Abstract references are `2026 APISAT/APISAT-2026_Abstract(김석호)최종.docx` and its matching PDF.
- The APISAT full paper should present an event-aligned X-Plane/Java/Tobii data-collection and preliminary-validation framework.
- Do not claim that the framework directly measures threat-recognition time or completes an AI pilot model.
- The 2026-07-20 data remains algorithm-development and system-validation material because the participant could see countdown/advisory and the repeated trials did not share the same flight condition.

## 2026-07-24 Stage 2 three-monitor validation implementation

Stage 2 now has a dedicated physical-validation pipeline:

- procedure: `TOBII_SURROUND_VALIDATION_260724.md`
- collector/analyzer: `tools/tobii/multimonitor_gaze_validation.py`
- tool version: `260724_v2`
- schema version: `2`

Verified pre-test state:

- NVIDIA RTX 2060, driver `560.94`
- three independent `1920x1080`, 100%-scale Windows displays
- virtual desktop coordinates: left `-1920..0`, center primary `0..1920`, right `1920..3840`
- one Spark, serial `TPE01-100206101311`, `60 Hz`, mode `Default`
- tracker Active Display Area: `598 x 336 mm` (`27.005 inch`)
- Python 3.10/Tk/SDK import, tracker discovery, self-test, center dry run, diagnostic wide mapping, and invalid-layout refusal all work
- synthetic end-to-end checks verified `PASS`, performance `FAIL`, and
  missing-target/one-repeat/diagnostic/identity-mismatch `INCONCLUSIVE`
  outcomes, including CSV/JSON/SVG round trips and streaming close paths

The current status is
`PRECISE WIDE AOI no-go / COARSE SIDE_EXTERNAL conditional go / X-Plane pilot pending`.

The completed baseline is
`tobii_multimonitor_center_baseline_20260727_154315`, collected at a measured
viewing distance of `750 mm`. It is `RESEARCH_VALID_COMPLETE` with `15/15`
epochs, `100.0%` condition-median sample coverage, `96.5%` equal-target center
validity, `95.6%` binocular validity, `99.9%` correct-center classification,
and `36.1 px` median target-centroid error. Target median centroid errors range
from `22.5 px` to `83.1 px`; the conservative equal-target radial sample p95 is
`75.3 px`.

This is an adequate matched center baseline. After it was collected, NVIDIA
Surround was enabled and Windows exposed one primary `5760x1080` display at
`(0,0)`. The corrected whole-wide Active Display Area was `1794 x 336 mm`; its
UCS plane was approximately `z=-60 mm`, and stored calibration data was present.
The matched eye-to-screen distance was `750 mm`, the panels were flat/co-planar,
and bezel correction was `0 mm`.

Two physical Surround runs were completed:

- `tobii_multimonitor_surround_wide_20260727_155743`
- `tobii_multimonitor_surround_wide_20260727_160333`

Both completed `45/45` epochs. The first run produced left/center/right valid
rates of `44.4% / 93.5% / 42.1%`; the retry produced
`37.5% / 96.3% / 40.1%`. Multiple outer targets on both side monitors had
`0%` valid gaze in all three repeats. Where side errors were finite, median
target-centroid errors were approximately `170~371 px` in the first run and
`297~346 px` in the retry.

The strict comparison output for each run is `INCONCLUSIVE`, because one or two
`LEFT_LL` epochs had sample coverage below the `80%` integrity gate. These
epochs had zero valid gaze and the complete event protocol was present, so the
problem is associated with loss of tracking at the outer side target rather
than a manual abort. The literal automated result must not be relabeled
`FAIL`. However, the replicated measurements are far below the project side
validity threshold of `70%`, and the worst-target median repeat-valid threshold
of `50%` is violated by complete side-target dropouts. The project
engineering/operational go/no-go decision is therefore:

```text
One center-mounted Spark is unsuitable for reliable whole-surface gaze
measurement across the tested flat three-monitor geometry.
```

This conclusion is specific to the measured setup and does not claim a
universal hardware impossibility. It remains the conclusion for precise
whole-surface coordinates and side AOIs. The experimental endpoint was then
narrowed to central-versus-side external attention, for which low side sample
validity may still be useful when measured side coordinates are combined with
sustained loss episodes.

The coarse classifier is implemented in
`tools/tobii/analyze_xplane_tobii_session.py`, analyzer version
`260727_surround_external_v1`. Reproducible target-task validation is in
`tools/tobii/validate_surround_external_classifier.py`.

Surround analysis rules:

- logical display: `5760x1080`, three `1920x1080` physical monitors
- center global range: `x=1920..3840`
- center instrument coordinate: `center_x = global_x - 1920`
- measured side: gaze remains at least `120 px` beyond a center/side seam for
  at least `200 ms`
- inferred side: bilateral invalidity lasts `500~5000 ms`, is bracketed by
  valid samples, and contains no sample gap above `100 ms`
- invalid intervals outside this rule remain `UNRESOLVED_TRACKING_LOSS`

Evidence is stored separately as `MEASURED_SIDE_LEFT`,
`MEASURED_SIDE_RIGHT`, `INFERRED_SIDE_UNKNOWN`, or
`UNRESOLVED_TRACKING_LOSS`. `OPERATIONAL_EXTERNAL` is a derived endpoint that
may combine center `OUTSIDE_VIEW` with measured and inferred side evidence.
Loss-only evidence never receives a left/right direction.

Post-hoc validation used both physical Surround sessions, `90` labeled epochs
in total. With the fixed `120 px / 200 ms / 500~5000 ms` rule, it detected
`59/60` side epochs, rejected all `30/30` center epochs, and correctly assigned
direction for `24/24` measured-direction epochs. One `LEFT_C` epoch was missed.
This is same-data, single-participant, target-task validation and must not be
reported as independent main-experiment accuracy.

The default center-only mode was regression-tested on the 2026-07-20 session.
It preserved `19,408` rows, `14,780` valid rows, every AOI count, the
operational-external count `15,694`, and the legacy trial metrics. Raw gaze and
X-Plane CSV schemas were not changed.

The tool separates configuration feasibility, data completeness, and tracker
performance:

1. `CONFIGURATION_FAILED` means Windows Surround or the Tobii whole-display
   Active Display Area could not satisfy preflight. A `*_preflight.json`
   preserves the evidence.
2. `INCONCLUSIVE` means the run pair is not research-comparable because setup,
   protocol, stream, target repetition, baseline quality, or pair matching is
   inadequate.
3. `FAIL` means comparable research-valid data exist but one or more performance
   thresholds fail.
4. `PASS` means every comparability and monitor-level threshold passes.

Target protocol:

- center baseline: five targets at 20/80% corners plus center, three repeats,
  `15` epochs
- Surround: the same five targets on each of left/center/right, three repeats,
  `45` epochs
- each epoch: center home `1.5 s`, blank `0.25 s`, target `2.5 s`, exclude the
  first `1.0 s`, analyze the final `1.5 s`, blank `0.25 s`
- the GUI uses only the active display bounds, verifies its actual Windows
  geometry, uses KeyRelease/debounce at block boundaries, and requires a live
  valid gaze stream before the first block

Integrity and persistence:

- raw gaze is continuously queued to CSV and flushed approximately once per
  second
- target events are written and flushed at occurrence
- manual `Esc`, window close, `Ctrl+C`, and Tk callback errors enter the
  incomplete-session preservation path
- raw coordinates are not clamped or interpolated
- within-run timing uses `pc_monotonic_ns`; wall time remains for audit and
  cross-system alignment
- exact event order, actual measure duration, sample coverage, target/repeat
  counts, and missing rates are explicit quality gates
- `_metadata.json` is written last and SVG reports show incomplete/diagnostic
  status without stretching the physical display aspect ratio

Temporary monitor-level project thresholds:

- left and right valid rate, separately: `>=70%`
- left and right correct-monitor classification among valid samples:
  `>=90%`
- each side's worst-target median repeat-valid rate: `>=50%`
- baseline and Surround center valid rate: `>=70%`
- center median target-centroid error degradation: `<=20%`

The comparison also requires the same non-identifying participant code,
physical setup ID, tracker serial/frequency, schema/tool
compatibility, matching target timing, panel geometry, center pixel dimensions,
and eye-to-screen distance within `20 mm`.

`bezel_mm` is defined as the total physical gap between adjacent visible
display areas, not one plastic bezel's width. Recovery output includes attempts,
successful finite recoveries, and success rate so latency is not reported
without its censoring context.

These values are project go/no-go criteria rather than Tobii specifications.
They establish only coarse monitor-level feasibility. The monitor summary now
includes coverage, binocular validity, dropout, acquisition/recovery, and a
conservative aggregate of epoch radial-sample `p95` error, but fine dynamic
runway/intruder AOI use still requires a later margin-based test.

One geometry warning remains important. With a `598 mm` panel, an outer side
target at 20% is about `1.3 panel widths` from center. At Tobii's listed maximum
operating distance of `950 mm`, this is about `39°`, beyond the advanced-setup
guidance value of `35°`. This calculation predicts that flat outer targets may
be difficult; it is not a substitute for the measured run.

Keep the current Surround setup only for a short X-Plane pilot. The pilot must
verify central instrument AOIs in the shifted coordinate system and inspect
measured/inferred/unresolved side episodes. If that pilot is inadequate,
restore extended desktop and the center `1920x1080` Tobii setup/calibration.
If it is adequate, freeze the coarse classifier before main data collection
and never reinterpret inferred loss as a precise side coordinate or intruder
fixation.

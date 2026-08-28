# AGENTS.md

## 프로젝트 개요

이 프로젝트는 X-Plane 11과 Java 수신 프로그램을 연동하여 조종사의 반응 데이터를 수집하고, 이를 기반으로 향후 AI 조종사 모델 설계에 사용할 수 있는 실험 데이터셋과 분석 파이프라인을 구축하는 것을 목표로 한다.

현재 구현은 단순한 개념 정리 단계가 아니라, FlyWithLua 기반 X-Plane 스크립트와 Java UDP 수신기를 이용해 실제 CSV 로그를 생성하고, trial 단위로 hazard, advisory, pilot response를 자동 판정하는 단계까지 진행되어 있다.

## 현재 실행 구조

- 현재 Tobii 실험은 X-Plane, FlyWithLua, Java receiver, Tobii Pro Spark logger를 같은 X-Plane PC에서 실행한다.
- FlyWithLua는 UDP `STATE`, `INTRUDER`, `EVENT` 패킷을 `127.0.0.1:9100`의 Java receiver로 보낸다.
- Java는 원본 X-Plane 데이터를 `logs/xplane` 아래 CSV로 저장한다.
- Tobii Python logger는 원본 gaze 데이터를 `logs/tobii` 아래 CSV로 저장한다.
- Java event와 Tobii gaze는 PC timestamp로 사후 병합하며, 분석·시각화 결과는 원본 CSV를 수정하지 않고 별도 파일로 생성한다.
- Java 분석 도구는 세션/트라이얼 요약, clean trial export, gaze/AOI 병합과 trial 동일가중 집계를 생성한다.
- 선택적으로 Java receiver가 연구실 ATC Simulator Server에 TCP client로 접속해 X-Plane ownship `STATE`를 서버 ICD의 `FDT` 패킷으로 동시 송신할 수 있다.

현재 네트워크 설정:

- 같은 PC UDP target: `127.0.0.1`
- Java UDP 수신 포트: `9100`
- 연구실 ATC Simulator Server PC: `172.16.150.130`
- 연구실 ATC Simulator Server TCP 포트: `50000`

현재 사용 중인 통합 Lua 파일:

- `FLYWITHLUA_STUDY_INTEGRATED.lua`
- 현재 코드 버전: `260803_visual_detectability_gate_v32`
- 보관 스냅샷: `FLYWITHLUA_STUDY_INTEGRATED_260803_visual_detectability_gate_v32.lua`

현재 Java 수신기 진입점:

- `src/com/example/ai/XPlaneReceiverMain.java`

현재 ATC Simulator Server 연동 관련 Java 파일:

- `src/com/example/ai/AtcServerConnectionTest.java`
- `src/com/example/ai/AtcServerBridgeClient.java`
- `src/com/example/ai/AtcServerFdtSmokeTest.java`

현재 Java 분석 진입점:

- `src/com/example/ai/XPlaneSessionAnalysisMain.java`
- `src/com/example/ai/XPlaneBatchAnalysisMain.java`

## 현재 구현된 기능

- X-Plane ownship 상태를 UDP `STATE` 패킷으로 전송한다.
- 조종사 입력값을 함께 전송한다.
- multiplayer/AI aircraft slot 1의 intruder 상태를 UDP `INTRUDER` 패킷으로 전송한다.
- 시나리오 이벤트를 UDP `EVENT` 패킷으로 전송한다.
- `trial_id` 기반 trial reset/start/end 구조가 구현되어 있다.
- X-Plane pause 중 같은 `sim_time_s`를 가진 중복 row 전송을 줄인다.
- active trial 없이 intruder를 시작하거나, head-on intruder를 중복 시작하면 무시 사유를 event로 기록한다.
- `sim/weather/cloud_base_msl_m[0]`, `sim/weather/cloud_tops_msl_m[0]`, `sim/weather/cloud_coverage[0]`를 이용해 cloud deck 조건을 부여한다.
- ownship 정면에 intruder를 생성하는 head-on intruder 시나리오가 있다.
- final approach 상황을 목표로 randomized crossing intruder 시나리오가 구현되어 있다.
- crossing intruder는 left/right/front 접근을 무작위로 선택하고, ownship 예상 경로 근처를 통과하도록 배치된다.
- Java는 수신 데이터를 ownship, intruder, event CSV로 분리 저장한다.
- Java는 intruder 거리 기준으로 `HAZARD_DETECTED`, `HAZARD_CLEARED`를 자동 기록한다.
- Java는 새 no-alert trial에서 `ADVISORY_SHOWN`, `ADVISORY_CLEARED`를 자동 기록하지 않는다. 이 event는 과거 자료 및 명시적 수동 advisory 실험 호환용으로 남아 있다.
- Java는 v32에서 `INTRUDER_VISUALLY_DETECTABLE` 전 1초 입력 baseline 대비 지속 변화를 이용해 `PILOT_RESPONSE_START`, `PILOT_RESPONSE_END`를 자동 기록한다. v31 이하 과거 프로토콜은 `INTRUDER_SPAWNED` anchor를 유지한다.
- Lua v18은 화면에 visual advisory 문구 `TRAFFIC ALERT`, `CHECK OUTSIDE`를 표시한다.
- v20에서 도입된 end-trial guard는 v22에도 유지된다. trial이 아직 닫히지 않은 상태에서 `Study End Trial`을 누르면 `TRIAL_END` 대신 `MANUAL_NOTE`로 조기 종료 거부 사유를 남긴다.
- v22는 placeholder `FINAL_APPROACH_GATE_ENTERED`를 더 이상 발생시키지 않으며 Java strict clean 판정도 이를 요구하지 않는다.
- v29는 trial마다 `90/95/100/105/110 KIAS` 중 하나를 균등 무작위로 선택하고, trial 시작 `3초` 후 해당 표준 계기 유지 WAV를 자동 재생하며 `TASK_COMMAND_AUDIO_START/END`를 기록한다.
- trial-level 분석과 batch 분석 CSV export가 구현되어 있다.
- 현재 gaze 분석은 `AOI그림.png`와 `resources/cessna_instrument_aoi_260710.csv`를 사용한다. center-only 분석은 6개 계기 AOI 밖의 유효 gaze를 `OUTSIDE_VIEW` 또는 `PANEL_OTHER`, 정면 좌표가 없는 sample을 `UNTRACKED_OR_OFF_DISPLAY`로 구분한다.
- 새 Surround 분석은 `OPERATIONAL_EXTERNAL`의 구성요소를 `CENTER_OUTSIDE_MEASURED`, `MEASURED_SIDE_LEFT/RIGHT`, `INFERRED_SIDE_UNKNOWN`, `UNRESOLVED_TRACKING_LOSS`로 분리해 원본 evidence를 보존한다.
- ATC 서버 연동 smoke test에서 `plt#PLT_XP_SMOKE` 등록(`REG/GRT`)과 synthetic X-Plane state 1개를 `FDT`로 변환 송신하는 데 성공했다.

## 주요 FlyWithLua 명령

현재 통합 Lua 스크립트는 다음 command와 macro를 등록한다.

- `flywithlua/study/reset_trial`
- `flywithlua/study/start_trial`
- `flywithlua/study/end_trial`
- `flywithlua/study/start_intruder_headon`
- `flywithlua/study/toggle_cloud`
- `flywithlua/study/toggle_operator_overlay`
- `flywithlua/study/test_task_audio`
- `flywithlua/study/start_auto_session`
- `flywithlua/study/stop_auto_session`

현재 작업 흐름에서는 통합 Lua 파일 하나만 사용하는 것을 원칙으로 한다. 기존의 테스트 Lua 파일을 동시에 `Scripts` 폴더에 넣으면 UDP 전송이나 화면 표시가 중복될 수 있다.

## 데이터 계약

Java 수신기는 현재 다음 세 종류의 패킷을 기대한다.

- `STATE,...`
- `INTRUDER,...`
- `EVENT,...`

현재 최신 패킷은 앞쪽에 `trial_id`를 포함한다. Java 파서는 일부 구형 패킷도 `trial_id=0`으로 처리할 수 있게 되어 있지만, 새 실험은 최신 패킷 형식을 기준으로 한다.

CSV 스키마와 UDP 컬럼 순서는 임의로 바꾸지 않는다. 패킷 형식을 바꿔야 할 경우에는 Lua 송신부, Java 파서, CSV 로거, 분석 코드를 같은 작업 안에서 함께 수정한다.

단위 주의:

- 기존 STATE header/field의 `ias_mps`는 이름과 달리 X-Plane dataref `sim/flightmodel/position/indicated_airspeed`의 원 단위인 `kias`를 담는다.
- 기존 CSV를 해석할 때 이 값에 `1.94384`를 다시 곱하지 않는다.
- 호환 스키마를 수정할 때는 구형 `ias_mps`와 새 의미표시를 parser/analyzer 전체에서 함께 처리한다.

## 연구실 ATC Simulator Server 연동

연구실 서버 프로젝트:

- `Simulator Server - 20250520_DAS`

서버 통신 방식:

- TCP socket, 기본 포트 `50000`
- 클라이언트는 접속 직후 `REG` 패킷을 한 줄 문자열로 보내고, 서버의 `GRT` 응답을 받아야 한다.
- 패킷 구분자는 서버 `ICD.java` 기준으로 `&`, `>`, `:`, `#`, `@`를 사용한다.

등록 예:

```text
src:plt#PLT_XP>dst:svr>typ:reg
```

성공 응답 예:

```text
src:svr>dst:PLT#PLT_XP>typ:grt
```

현재 연동 방향:

```text
동일 X-Plane PC
    -> FlyWithLua UDP 127.0.0.1:9100 STATE / INTRUDER / EVENT
    -> Java receiver
       -> 기존 CSV 저장과 자동 이벤트 검출 유지
       -> 옵션 사용 시 하나의 PLT_XP TCP link로 ownship/intruder FDT 동시 송신
ATC Simulator Server PC
    -> ownship fid `xplane01`, intruder fid `intruder01`로 수신
```

일회성 서버 등록 테스트:

```powershell
java -cp build_atc_tmp com.example.ai.AtcServerConnectionTest 172.16.150.130 50000 PLT_XP
```

일회성 FDT 송신 smoke test:

```powershell
java -cp build_atc_tmp com.example.ai.AtcServerFdtSmokeTest 172.16.150.130 50000 PLT_XP_SMOKE xplane01
```

X-Plane receiver와 ATC bridge 동시 실행은 `ATC_XPLANE_RUN_COMMANDS_260610.md`의 PowerShell 줄바꿈 명령을 사용한다. 최종 방식에는 `--atc-intruder-on-ownship-link`와 `--atc-intruder-fdt`가 모두 포함돼야 한다.

주의:

- `XPlaneReceiverMain`은 정상 실행되면 종료되지 않고 UDP 수신을 계속 기다리는 프로그램이다. PowerShell에서 멈춘 것처럼 보이는 것이 정상 동작이며, 종료는 `Ctrl+C`로 한다.
- 서버 PC에서 Simulator Server의 `Open` 버튼을 눌러야 `50000` 포트가 열린다.
- `Test-NetConnection`은 포트 확인에는 유용하지만, 서버 accept thread가 첫 줄을 기다리는 구조라 테스트 후 서버를 `Close`/`Open`해서 초기화하는 것이 안전하다.
- 정상 최종 방식에서는 `ATC bridge registered as plt#PLT_XP` 하나만 보이며 ownship과 intruder를 같은 link의 서로 다른 FID로 보낸다.

## 주요 이벤트

현재 Java enum에 등록된 주요 event type:

- `SESSION_START`
- `SESSION_STOP`
- `RECEIVER_START`
- `RECEIVER_STOP`
- `MANUAL_NOTE`
- `TRIAL_RESET`
- `TRIAL_START`
- `TRIAL_END`
- `SCENARIO_MARKER`
- `FINAL_APPROACH_GATE_ENTERED`
- `SCENARIO_SELECTED`
- `INTRUDER_SPAWNED`
- `ADVISORY_SHOWN`
- `ADVISORY_CLEARED`
- `HAZARD_DETECTED`
- `HAZARD_CLEARED`
- `MIN_DISTANCE_REACHED`
- `GO_AROUND_START`
- `GO_AROUND_CONFIRMED`
- `PILOT_RESPONSE_BASELINE`
- `PILOT_RESPONSE_START`
- `PILOT_RESPONSE_END`

## 저장 파일

각 Java 수신 세션은 아래 폴더에 CSV를 생성한다.

- `logs/xplane/`

대표 파일:

- `xplane_session_<timestamp>.csv`
- `xplane_session_<timestamp>_intruder.csv`
- `xplane_session_<timestamp>_events.csv`
- `xplane_batch_analysis_<timestamp>_sessions.csv`
- `xplane_batch_analysis_<timestamp>_trials.csv`
- `xplane_batch_analysis_<timestamp>_clean_trials.csv`

원본 실험 CSV 데이터는 명시 요청이 없으면 수정하지 않는다.

## Java 버전 규칙

IntelliJ 프로젝트는 Java 21 (`liberica-full-21`) 기준으로 설정되어 있다. `out/production` 안에 Java 23으로 컴파일된 `.class` 파일이 섞이면 실행 시 class version 오류가 발생할 수 있다.

문제가 생기면 X-Plane 관련 클래스를 Java 21 호환으로 다시 컴파일한다.

```powershell
javac --release 21 -d out\production\AISimulationProject src\com\example\ai\XPlane*.java
```

분석 도구 임시 컴파일은 다음 명령을 사용한다.

```powershell
javac --release 21 -d build_tmp src\com\example\ai\XPlane*.java
```

현재 OneDrive/reparse point/readonly 상태 때문에 `build_tmp` 또는 `logs/xplane` 쓰기가 막힐 수 있다. 이 경우 권한 문제를 먼저 해결하거나 OneDrive 밖 로컬 작업 폴더에서 빌드/분석한다.

## 문서 관리 규칙

- 최신 상태는 항상 기준 파일 `Agents.md`, `Project_Context.md`, `Worklog.md`에 반영한다.
- 기준 파일을 최신화할 때는 같은 내용을 날짜 suffix가 붙은 스냅샷으로도 저장한다.
- 스냅샷 파일명은 `Agents_updated(YYMMDD).md`, `Project_Context_updated(YYMMDD).md`, `Worklog_updated(YYMMDD).md` 형식을 사용한다.
- 기존 날짜 스냅샷은 삭제하지 않고 이력으로 보존한다.
- 코드나 실험 절차가 바뀌면 해당 작업이 끝난 같은 턴에서 문서도 함께 갱신한다.
- 문서가 코드와 충돌하면 현재 코드를 우선 확인하고, 문서를 최신 코드 기준으로 수정한다.

## Lua 버전 관리 규칙

- 실제 X-Plane `Scripts` 폴더에 넣어 실행할 활성 Lua 파일명은 `FLYWITHLUA_STUDY_INTEGRATED.lua`로 유지한다.
- Lua를 수정할 때는 `STUDY_SCRIPT_VERSION` 값을 날짜와 목적이 드러나는 이름으로 갱신한다.
- Lua를 업데이트한 뒤에는 같은 내용을 `FLYWITHLUA_STUDY_INTEGRATED_YYMMDD_<short_description>_vNN.lua` 형식의 스냅샷으로 저장한다.
- 기존 Lua 스냅샷은 삭제하지 않고 이력으로 보존한다.
- Lua 패킷 형식, command, event, scenario parameter가 바뀌면 Java parser/logger/analyzer와 `Agents.md`, `Project_Context.md`, `Worklog.md`를 같은 작업 안에서 함께 갱신한다.
- X-Plane FlyWithLua `Scripts` 폴더에는 원칙적으로 활성 통합 Lua 하나만 둔다.

## 작업 원칙

- 작업 시작 전 `Project_Context.md`와 `Worklog.md`를 먼저 읽는다.
- 바로 코드를 수정하지 말고 현재 코드 흐름을 먼저 분석한다.
- 변경은 작고 검증 가능한 단위로 진행한다.
- 원본 실험 CSV 데이터는 명시 요청이 없으면 수정하지 않는다.
- CSV 스키마는 명시 요청 없이 변경하지 않는다.
- 코드 주석에는 불필요한 특수 아이콘이나 이모지를 사용하지 않는다.
- 수정 후에는 어떤 파일을 왜 수정했는지 설명한다.

## 현재 알려진 이슈

- Lua v29는 participant trial 중 한 줄의 `TRIAL IDLE/RESET/ACTIVE/END` 상태만 표시한다. 종료가 guard에 거부되면 `TRIAL IN PROGRESS - END NOT READY`를 2.5초 동안 표시한다. countdown, intruder/scenario 상태, debug 좌표·거리와 `TRAFFIC ALERT`/`CHECK OUTSIDE`는 숨긴다.
- v29 표준 음성은 `Microsoft Zira` rate 1로 생성한 5개 WAV이며 trial마다 `90/95/100/105/110 KIAS` 중 하나를 균등 무작위로 지시한다.
- Lua 내부 visual-advisory 상태, UDP event, Java clean 판정, end-trial guard는 유지한다.
- 점검용 전체 overlay는 trial 밖에서만 `flywithlua/study/toggle_operator_overlay`로 켜고 끌 수 있으며, trial 시작 시 강제로 꺼지고 active trial 중 토글은 거부된다.
- Java response detector는 `INTRUDER_SPAWNED` 전 1초 입력 평균을 baseline으로 사용하고, 임계치 변화가 0.25초 이상 지속될 때 최초 통과 시점을 `PILOT_RESPONSE_START`로 기록한다.
- 새 no-alert trial에서 Java는 자동 `ADVISORY_SHOWN/CLEARED`를 만들지 않는다. 내부 위험창은 `HAZARD_DETECTED/CLEARED`로 기록하며 advisory enum/parser는 과거자료와 수동 실험 호환용으로 유지한다.
- v29 random crossing은 `TASK_COMMAND_AUDIO_START` 직후 `U(3,10초)`를 독립 추출하고 그 시간이 지난 뒤 intruder를 생성한다.
- 최신 2026-07-20 세션의 11개 trial은 한 번의 연속 강하에서 AGL 약 `1,962 ft`부터 `101 ft`까지 서로 다른 조건으로 수행됐다. 본실험은 매 trial 동일 저장상황에서 재시작해야 한다.
- STATE CSV의 `ias_mps`는 이름과 달리 X-Plane 원본 단위가 `kias`이다. 기존 스키마 호환성을 유지하면서 분석과 문서에서 KIAS로 해석해야 한다.
- 기존 strict clean은 이벤트 완결성만 뜻한다. 다음 분석에서는 `recording/event complete`, `flight-condition-valid`, behavioral outcome을 분리해야 한다.
- Spark 한 대의 3모니터 물리 validation은 완료했다. 현재 조건은 정밀 좌우 좌표/AOI 측정에는 부적합하지만, 측정된 측면 좌표와 지속 추적손실을 결합한 coarse `SIDE_EXTERNAL` 분류는 pilot test 대상으로 유지한다.
- 기존 center-only 출력의 `UNTRACKED_OR_OFF_DISPLAY`는 좌우 응시와 일반 tracking loss를 구분하지 못한다. 새 `surround-wide` 분석은 `MEASURED_SIDE_*`, `INFERRED_SIDE_UNKNOWN`, `UNRESOLVED_TRACKING_LOSS`를 분리하지만 아직 독립된 X-Plane pilot validation이 필요하다.
- 현재 intruder 시나리오는 `sim/multiplayer/position/plane1_*` dataref가 writable이라는 현재 X-Plane 11 환경에 의존한다.

## 2026-06-10 ATC intruder ADS update

- 현재 활성 Lua 버전은 `260610_intruder_geo_atc_v21`이다.
- 활성 Lua 파일은 계속 `FLYWITHLUA_STUDY_INTEGRATED.lua` 하나만 사용한다.
- v21 스냅샷은 `FLYWITHLUA_STUDY_INTEGRATED_260610_intruder_geo_atc_v21.lua`로 보존한다.
- `INTRUDER` UDP 패킷은 기존 local 좌표에 더해 `intruder_latitude_deg`, `intruder_longitude_deg`, `intruder_elevation_m`을 포함한다.
- Java `XPlaneIntruderSample`은 구버전 INTRUDER 패킷과 v21 INTRUDER 패킷을 모두 읽을 수 있다.
- 새 intruder CSV 헤더는 `intruder_z` 뒤에 `intruder_latitude_deg,intruder_longitude_deg,intruder_elevation_m`을 포함한다.
- ATC 연동 사용 시 ownship은 `PLT_XP` 클라이언트가 `FDT`로 송신하고, intruder는 `SDP_XP` 클라이언트가 `ADS`로 송신한다.
- 기본 intruder ATC flight id는 `intruder01`이다.
- ATC 서버 GUI에서는 `PLT_XP`와 `SDP_XP` 두 클라이언트가 등록되는 것이 정상이다.

현재 권장 실행 명령:

```powershell
java -cp build_atc_tmp com.example.ai.XPlaneReceiverMain 9100 build_atc_tmp\xplane_atc_intruder_test.csv session_atc_intruder_test --atc-host 172.16.150.130 --atc-port 50000 --atc-module PLT_XP --atc-fid xplane01 --atc-intruder-module SDP_XP --atc-intruder-fid intruder01 --atc-every 5
```

intruder ATC 송신만 끄고 CSV 저장/기존 이벤트 검출만 확인하려면 `--atc-no-intruder`를 추가한다.
## 2026-06-10 ATC intruder final display rule

- 최종 확인 결과, X-Plane/Lua intruder 생성 로직은 정상이다.
- `INTRUDER_SPAWNED`, `HAZARD_DETECTED`, intruder CSV 위경도/고도 기록이 정상 확인됐다.
- 관제 화면에 intruder를 표시하려면 `SDP_XP` 별도 연결이나 `PLT_INTR` 별도 연결을 쓰지 않는다.
- 최종 성공 방식은 `PLT_XP` 한 TCP 연결에서 ownship과 intruder를 같이 보내는 방식이다.
- 이때 ownship은 `fid:xplane01`, intruder는 `fid:intruder01`로 보낸다.
- 실행 옵션에는 반드시 `--atc-intruder-on-ownship-link`와 `--atc-intruder-fdt`를 포함한다.
- 정상 실행 시 ATC 등록 메시지는 `ATC bridge registered as plt#PLT_XP` 하나만 떠야 한다.
- `sdp#SDP_XP` 또는 `plt#PLT_INTR`가 같이 뜨면 최종 성공 방식이 아니다.

최종 권장 실행 명령은 `ATC_XPLANE_RUN_COMMANDS_260610.md`를 따른다.

```powershell
java -cp build_atc_tmp com.example.ai.XPlaneReceiverMain 9100 build_atc_tmp\xplane_atc_intruder_onlink_final.csv session_atc_intruder_onlink_final --atc-host 172.16.150.130 --atc-port 50000 --atc-module PLT_XP --atc-fid xplane01 --atc-intruder-fid intruder01 --atc-intruder-on-ownship-link --atc-intruder-fdt --atc-every 5
```

## 2026-06-11 Git update notes

- X-Plane/ATC 연동 작업은 local `ver8` 브랜치와 remote `origin/ver8` 브랜치에 반영됐다.
- 반영 commit은 `c6bbb32 Add X-Plane ATC bridge integration`이다.
- `.class`, `build_tmp`, `build_atc_tmp`, `logs`는 `.gitignore`로 제외한다.
- IntelliJ에서 빨간색 파일은 아직 Git에 추가되지 않은 unversioned 파일이고, 파란색 파일은 이미 Git이 추적 중인 modified 파일이다.
- 기존 AI simulation 쪽 파란색 Java class 수정분은 X-Plane/ATC receiver와 별개로 Simulink UDP bridge 관련 변경이다.
- 해당 변경에는 `AISimulation`, `Agent`, `Aircraft`, `SimulationGUI`, `SimulinkCommandSender`, `SimulinkStateReceiver`, `CalculateDWC`가 포함된다.
- `.idea/vcs.xml`의 `j6dof-temp` mapping 변경은 코드 변경이 아니므로 별도 요청 전까지 commit하지 않는다.

## 2026-06-22 X-Plane/ATC 재검증 운용 규칙

- PowerShell 실행 명령은 앞으로 한 줄 복붙 명령을 기본으로 제시하지 않는다.
- 긴 Java 실행 명령은 반드시 PowerShell 백틱 줄바꿈 버전으로 제시한다.
- 각 줄 끝의 백틱 `` ` ``이 빠지면 뒤쪽 옵션이 Java에 전달되지 않으므로, 실행 전 옵션 줄을 한 줄씩 확인한다.
- 특히 `--atc-intruder-on-ownship-link`와 `--atc-intruder-fdt`가 빠지면 최종 성공 방식이 아니다.
- 잘못 실행된 예는 `ATC intruder bridge ... module=SDP#SDP_XP ... fdt=false ownshipLink=false`와 `ATC bridge registered as sdp#SDP_XP`가 함께 뜨는 경우이다.
- 정상 실행은 `ATC bridge registered as plt#PLT_XP` 하나만 뜨고, intruder 설정에 `fdt=true ownshipLink=true`가 보여야 한다.
- ATC 서버 연결 전에는 서버 PC에서 Simulator Server를 `Close` 후 `Open`으로 초기화한다.
- `Connection refused`는 대부분 서버 PC의 `50000` 포트가 아직 열리지 않았거나 서버가 `Open` 상태가 아닌 경우이다.

2026-06-22 재검증 결과:

- 파일 prefix: `build_atc_tmp/xplane_atc_intruder_onlink_260622_test2`
- STATE rows: `2051`
- INTRUDER rows: `2051`
- EVENT rows: `212`
- intruder 위경도/고도 컬럼은 전 row에서 정상값이며 `NaN`이 없었다.
- strict clean trial은 `17`개 중 `7`개였다.
- clean trial: `9`, `10`, `11`, `16`, `18`, `19`, `20`
- clean 평균 최소 수평거리: `10.4 m`
- clean 평균 `spawn->hazard`: `6.730 s`
- clean 평균 `advisory->response`: `0.684 s`
- clean 평균 `hazard_window`: `2.444 s`
- 현재 `XPlaneSessionAnalysisMain`은 v21 intruder CSV의 추가 geo 컬럼을 반영하지 못해 거리 통계를 잘못 읽는다. event 기반 clean/incomplete 판정은 사용할 수 있지만, 거리 통계는 CSV 헤더 기준으로 수정해야 한다.

## 2026-06-22 Analyzer update rule

- `XPlaneSessionAnalysisMain`은 v21 intruder CSV의 `horizontal_distance`, `vertical_separation`, `sim_time_s`를 header name으로 찾아 읽도록 수정됐다.
- v21 intruder CSV는 geo 컬럼이 중간에 추가되므로, 앞으로 분석 코드는 intruder CSV 컬럼을 고정 index로 읽지 않는다.
- `XPlaneBatchAnalysisMain`은 `xplane_session_...csv`뿐 아니라 `xplane_atc_...csv` receiver output도 직접 분석할 수 있다.
- session-level first event latency가 sim time reset 때문에 음수가 되면 `n/a`로 표시한다.
- 2026-06-22 `test2` 재분석 기준 clean rate는 `7 / 17 = 41.2%`이고, clean 평균 최소 수평거리는 `10.406 m`이다.

## 2026-07-06 CWP display and military simulator application notes

- `xplane화면캡처.png`, `충돌상황캡처.png`, `확대화면캡처.png`를 확인했다.
- 일반 배율에서는 CWP 화면에서 ownship/intruder 라벨과 숫자 블록이 겹쳐 가독성이 낮다.
- 확대 화면에서는 겹침이 완화된다.
- `AISimulationProject/Simulator Server - 20250520_DAS` 전체를 검색했지만 CWP 화면 렌더링 소스는 없다.
- 해당 폴더에는 서버 운영 GUI와 통신 중계 코드만 있다.
- `paintComponent`, `drawString`, `Graphics2D`, radar/CWP panel, `Label[1]` 렌더링 코드는 없다.
- 근본적인 라벨 배치 수정은 실제 CWP 클라이언트 소스에서 해야 한다.
- 현재 프로젝트에서 가능한 안전한 완화책은 짧은 FID를 쓰는 것이다.

권장 라벨 완화 FID:

```powershell
--atc-fid xp01 `
--atc-intruder-fid in01 `
```

CWP 숫자 의미 추정:

- `xp01`, `in01`: flight id
- `013`, `019`: 고도, hundreds of feet 형태로 표시되는 것으로 판단
- `108`, `074`: 속도, knot
- `1200`: squawk code
- `-790FPM`, `-659FPM`: vertical rate, ft/min
- `301`, `130`: heading, degree
- `Preventive!`: conflict alert 단계
- `-`, `-------`, `----`: 현재 Java bridge가 보내지 않는 flight plan/route/clearance 계열 필드로 판단

프로젝트 정의:

```text
X-Plane 기반 조종사 반응 데이터 수집 및 ATC 연동 시뮬레이션 플랫폼
```

부대/보고용 정의:

```text
군 조종사 훈련 시뮬레이터와 연계 가능한 조종사 반응 데이터 수집·분석 체계의 프로토타입
```

부대 적용 논리:

- 부대 내부에서는 보안/인가 문제 때문에 X-Plane, FlyWithLua, 외부 Java 프로그램, Tobii SDK, 네트워크 연동을 그대로 쓰기 어려울 수 있다.
- 따라서 X-Plane 자체가 아니라 데이터 수집/분석 메커니즘을 인가된 군 훈련 시뮬레이터로 이식하는 방향으로 설명한다.
- 부대에 KAI 마린온 조종사 시뮬레이터가 있다면, 해당 시뮬레이터의 상태 데이터/조종 입력/로그 export 인터페이스를 확인한다.
- 가능하다면 X-Plane/FlyWithLua 입력부를 KAI 시뮬레이터 입력부로 대체하고, Java trial/event 분석 파이프라인은 유지한다.

보고용 핵심 문장:

```text
마린온 시뮬레이터 훈련 데이터를 활용하여 조종사의 상황대응 행동을 정량화하고, 숙련 조종사 또는 SOP 기반 기준 모델과 비교함으로써 훈련 후 객관적 디브리핑을 제공하는 체계로 발전시킬 수 있다.
```

표현상 주의:

- 초기에는 "AI가 조종사를 평가한다"보다 "교관의 디브리핑을 보조하는 데이터 기반 분석 도구"로 설명한다.
- 기준 모델은 먼저 "숙련 조종사 기준 모델" 또는 "SOP 기반 기준 모델"로 정의한다.
- 데이터가 충분히 축적된 뒤 AI 조종사 모델 또는 예측 모델로 확장한다.

## 2026-07-08 Tobii Pro Spark SDK notes

- Tobii Pro Spark 장비가 도착했고 Eye Tracker Manager에서 calibration을 완료했다.
- Tobii/Python/SDK/gaze logger는 X-Plane 화면을 기준으로 해야 하므로 X-Plane PC에 설치하고 실행한다.
- 단기적으로 Java PC와 X-Plane PC 분리 구조는 유지한다.
- APISAT 제출 전에는 Java receiver를 X-Plane PC로 옮기지 않는다. Tobii SDK/gaze CSV 안정화와 timestamp 병합을 먼저 확인한다.
- 장기적으로는 X-Plane PC 한 대에서 X-Plane, Tobii, Java receiver를 통합해도 무방하다.

단기 구조:

```text
X-Plane PC
    -> X-Plane / FlyWithLua
    -> Tobii Pro Spark
    -> Python 3.10 + Tobii Pro SDK
    -> gaze CSV

Java PC
    -> XPlaneReceiverMain
    -> STATE / INTRUDER / EVENT CSV
    -> ATC bridge
```

Tobii SDK 상태:

- SDK path on X-Plane PC: `C:\Users\ACSL-SERVER\Desktop\김석호\TobiiPro.SDK.Python.Windows_2.1.0.1`
- Use the `64` folder.
- This SDK copy has no `.whl`; use the `64` folder as the Python import path/current directory.
- Python command used: `py -3.10`
- Observed Python version: `Python 3.10.0rc2`

Verified tracker discovery:

```text
Address : tobii-prp://TPE01-100206101311
Model   : Tobii Pro Spark
Name    : Tobii Pro Spark
Serial  : TPE01-100206101311
Firmware: b05c12f988
```

Verified gaze stream:

- `py -3.10 tobii_gaze_test.py` ran successfully.
- 10 sec collection produced `gaze samples: 577`.
- `tobii_gaze_test.csv` was saved.
- Initial `NaN` gaze values with validity `0` were resolved by correcting tracking/user position.
- Valid samples showed normalized display coordinates and pupil values.

Coordinate interpretation:

- `x=0.0`: left side of display
- `x=1.0`: right side of display
- `y=0.0`: top of display
- `y=1.0`: bottom of display
- Example `x≈0.16`, `y≈0.76` means lower-left display area.

Current Tobii status:

```text
SDK import: success
Spark discovery: success
Gaze stream: success
CSV output: success
Merge with X-Plane event CSV: next step
```

Added files:

- `tools/tobii/list_trackers.py`
- `tools/tobii/gaze_logger.py`
- `TOBII_SPARK_FIRST_TEST_260708.md`

Next Tobii/X-Plane steps:

1. Run standalone gaze CSV for 30-60 sec on X-Plane PC.
2. Confirm center/left/right/up/down gaze changes match normalized coordinates.
3. Run X-Plane and verify the six fixed instrument AOIs plus the `OUTSIDE` fallback policy.
4. Run Java receiver on Java PC and gaze logger on X-Plane PC simultaneously.
5. Collect only 1-3 short trials first.
6. Verify `_events.csv` and gaze CSV overlap in time.

## 2026-07-10 Tobii logger script rule

- Use `tools/tobii/session_xplane_tobii_logger.py` as the current reference logger for simultaneous X-Plane/Tobii tests.
- Copy it to the X-Plane PC Tobii SDK `64` folder before running.
- The logger now creates a timestamped session id automatically at startup.
- Tobii output format is `session_xplane_tobii_YYYYMMDD_HHMMSS_gaze.csv`.
- The logger opens the output file in exclusive create mode, so it will not overwrite an existing CSV.
- Default duration is `600` seconds.
- It writes PC timestamps, Tobii timestamps, left/right gaze coordinates, validity, pupil diameter, and averaged valid gaze coordinates.
- It prints the first 5 rows so validity and coordinates can be checked immediately.
- It handles `Ctrl+C` by unsubscribing from the Tobii gaze stream before closing.

Run on X-Plane PC:

```powershell
cd "C:\Users\ACSL-SERVER\Desktop\김석호\TobiiPro.SDK.Python.Windows_2.1.0.1\64"
py -3.10 session_xplane_tobii_logger.py
```
## 2026-07-10 Tobii/X-Plane analysis operating notes

- For the current Tobii phase, ATC Simulator Server connection is not required unless the user explicitly asks for ATC/CWP testing.
- For repeated Java-only Tobii tests, IntelliJ `Program arguments` should be blank. `9100` is also acceptable, but blank is simpler because Java already defaults to UDP port `9100`.
- Avoid fixed Java receiver arguments such as:

```text
9100 build_atc_tmp\xplane_tobii_test_260710.csv session_xplane_tobii_test_260710
```

- Fixed output paths can append to or overwrite older runs and should not be used for repeated experiments.
- With blank Program arguments, Java creates timestamped files under `logs\xplane`, for example:

```text
logs\xplane\xplane_session_20260710_153000.csv
logs\xplane\xplane_session_20260710_153000_intruder.csv
logs\xplane\xplane_session_20260710_153000_events.csv
```

- X-Plane PC should run Tobii logging separately with `session_xplane_tobii_logger.py` from the Tobii SDK `64` folder.
- When providing PowerShell commands, avoid long one-line commands. Present commands line-by-line with PowerShell backticks so trailing options are not accidentally omitted.
- After each run, copy the timestamped Tobii gaze CSV from the X-Plane PC to the Java PC before analysis.
- Use `tools/tobii/analyze_xplane_tobii_session.py` to merge gaze and event timelines.
- The first merged output files are:
  - `build_atc_tmp/xplane_tobii_test_260710_gaze_aoi.csv`
  - `build_atc_tmp/xplane_tobii_test_260710_trial_gaze_summary.csv`
- Current AOI policy uses only six fixed instrument AOIs: `AIRSPEED`, `ATTITUDE`, `ALTITUDE`, `HEADING`, `VERTICAL_SPEED`, and `NAV_GPS`.
- Any valid gaze outside those six AOI boxes is classified as `OUTSIDE`.
- `RUNWAY_ZONE`, `ADVISORY`, and `OUTSIDE_CENTER` are no longer used as separate AOIs.
- Prefer 200 ms dwell-based AOI timing over single-sample first-hit timing when explaining results.
- Do not overinterpret `OUTSIDE` as confirmed intruder acquisition. It only means the gaze was outside the six fixed instrument AOIs.

## 2026-07-16 Tobii gaze visualization notes

- Use `tools/tobii/visualize_tobii_gaze.py` to create SVG gaze visualizations from Tobii gaze CSV and X-Plane event CSV.
- The script outputs browser-openable SVG files and does not require `matplotlib` or `PIL`.
- Standard inputs are:
  - Tobii gaze CSV from `session_xplane_tobii_logger.py`
  - X-Plane `_events.csv`
  - `resources/cessna_instrument_aoi_260710.csv`
  - optional cockpit background image `resources/cessnacokpit.png`
- Current visualization outputs are:
  - `*_gaze_scatter.svg`
  - `*_trial_<trial_id>_advisory_shown_trajectory.svg`
  - `*_trial_<trial_id>_aoi_timeline.svg`
- Treat the visualization as an inspection and presentation aid. Do not infer confirmed intruder acquisition from plotted gaze points alone.

## 2026-07-16 Same-PC integration rule

- X-Plane, `XPlaneReceiverMain`, and the Tobii logger are now intended to run on the same X-Plane PC.
- Both the Git-managed reference Lua and the active FlyWithLua script use UDP target `127.0.0.1:9100`.
- Keep IntelliJ `Program arguments` blank for repeated Tobii trials so Java creates timestamped files under `logs/xplane`.
- ATC Simulator Server connectivity is not required for the current Tobii integration test unless explicitly requested.
- Tobii raw gaze CSV files are stored under `logs/tobii`; Java receiver raw files remain under `logs/xplane`.
- The logger defaults to the current X-Plane PC project path and supports relocation through `AISIMULATION_PROJECT_DIR`.

## 2026-07-16 Same-PC trial and current visualization background

- Verified same-PC session pair: Java `session_20260716_161359` and Tobii `session_xplane_tobii_20260716_161408`.
- Results: 1,262 STATE rows, 1,262 INTRUDER rows, 16,928 gaze rows, 73.3% valid gaze, and 284.194 s timestamp overlap.
- Nine of ten trials were strict clean; trial 10 was incomplete only because `TRIAL_END` was missing.
- Use project-root `AOI그림.png` as the default cockpit background for all new Tobii gaze visualizations.
- The new image is the current full-screen cockpit view (`1918x1073`) and closely matches the `1920x1080` Tobii coordinate system.
- The six instrument AOIs were visually verified on this image. Keep `resources/cessnacokpit.png` only as a historical reference.

## 2026-07-16 AOI fallback classification rule

- The legacy single `OUTSIDE` fallback is superseded.
- Evaluate the six instrument rectangles first.
- Classify remaining valid gaze as `OUTSIDE_VIEW` when `y < 600 px` and `PANEL_OTHER` when `y >= 600 px`.
- Use `--panel-top-y` to override the boundary if the cockpit view changes.
- Do not force points in gaps between instruments into the nearest instrument AOI.
- On the verified session, all 70 points in the ATTITUDE–HEADING gap were classified as `PANEL_OTHER`.

## 2026-07-16 Fixed time-bin visualization rule

- Generate `*_trial_<trial_id>_<event>_time_bins.svg` alongside the existing gaze figures.
- The initial six-bin implementation is historical and superseded by the current eight-panel rule below.
- Use explicit panel labels and one point color instead of relying on a continuous blue-to-red time gradient.
- Keep empty panels visible and report valid/plotted sample counts; do not infer gaze location when a bin has no valid samples.

## 2026-07-16 Pre-advisory gaze validity finding

- Empty pre-advisory panels in the verified session are caused by bilateral Tobii validity loss, not missing raw samples or timestamp misalignment.
- Aggregate valid rates were `31.3%` for `-2~-1 s` and `45.9%` for `-1~0 s`.
- Trials 1, 5, and 8 had no valid gaze throughout the full two seconds before advisory.
- Do not interpolate an AOI or claim outside-view gaze for these intervals. The initial tracking-loss-only interpretation is superseded by the three-monitor rule below.

## 2026-07-16 Three-monitor Tobii interpretation correction

- The experiment uses three monitors, while Tobii Pro Spark tracks only the front monitor.
- The previous instruction to treat all bilateral invalidity as tracking loss is superseded.
- Use `UNTRACKED_OR_OFF_DISPLAY` instead of `INVALID` in new outputs.
- This means no valid coordinate was observed on the tracked front display; possible causes include side-monitor gaze, blink, occlusion, posture outside the tracking box, or tracker loss.
- Do not infer which cause occurred from the current gaze CSV alone.
- Time-bin figures must show `front valid/total` and `untracked/off-display` counts for each interval.
- Final verified-session counts are `12,414 / 16,928 = 73.3%` valid front-display gaze and `4,514 = 26.7%` `UNTRACKED_OR_OFF_DISPLAY`.

## 2026-07-16 Hybrid time-bin rule

- Use eight event-relative bins by default: `-2~-1`, `-1~0`, `0~+0.5`, `+0.5~+1`, `+1~+2`, `+2~+3`, `+3~+4`, and `+4~+5 s`.
- Preserve 0.5 s resolution during the first second after advisory; do not replace it with a 2 s panel for response-sequence analysis.
- Wider 1~2 s bins remain acceptable for pre-event context and later recovery.
- Split the former `+3~+5 s` recovery bin into `+3~+4` and `+4~+5 s`; the current standard layout is eight panels in a `4 x 2` grid.

## 2026-07-16 Within-panel centroid movement rule

- Represent within-panel movement using `200 ms binned gaze centroids` with event-relative midpoint labels.
- Apply discrete time colors within each panel and connect only consecutive non-empty windows with arrows.
- Do not interpolate missing windows or connect arrows across a missing 200 ms interval.
- Do not call these points fixations unless a separate validated fixation-detection algorithm is added.

## 2026-07-20 AOI timeline layout rule

- Do not render dense rotated event names directly on the AOI timeline.
- Use staggered numbered markers and a separate two-column list containing event number, `TRIAL_START`-relative time, and event name.
- Keep the AOI color legend in a separate three-column section and show relative-time axis ticks.
- Size the SVG height from event-list and legend content so labels do not overlap.

## 2026-07-20 Operational external and aggregation rules

- The experiment uses the policy `OPERATIONAL_EXTERNAL = OUTSIDE_VIEW + UNTRACKED_OR_OFF_DISPLAY` because both side monitors represent the outside environment.
- Preserve raw `aoi`; store this policy result separately as `operational_aoi`.
- Aggregate only strict clean trials and weight each trial equally.
- Output `all`, `front`, `left`, and `right` groups with `n`, mean, median, standard deviation, minimum, and maximum.
- Include an event-relative gaze window only when the complete window is inside that trial.
- Report the fraction already operationally external before advisory; do not overinterpret first-entry latency when this fraction is high.

## 2026-07-20 Placeholder gate removal rule

- Historical Lua version for this change: `260720_remove_placeholder_gate_v22`.
- New trials do not emit placeholder `FINAL_APPROACH_GATE_ENTERED`.
- Java strict clean-trial requirements do not require this event.
- Retain enum/parser compatibility for historical CSV files that contain the event.
- Active X-Plane and Git-managed Lua both use `127.0.0.1:9100` and must remain synchronized.

## 2026-07-20 Lua v22 validation result

- Verified session pair: Java `xplane_self_tobii_20260720_142508` and Tobii `session_xplane_tobii_20260720_142554_gaze.csv`.
- `FINAL_APPROACH_GATE_ENTERED=0`; v22 placeholder-gate removal works.
- Results: 11 total trials, 8 strict clean, 3 incomplete; 76.2% valid front-display gaze and 319.518 s timeline overlap.
- Trial 2 lacks `PILOT_RESPONSE_START`; trials 8 and 11 lack `TRIAL_END`.
- Clean mean advisory-to-response is `0.958 s`; clean mean trial operational-external rate is `78.7%`.
- All clean trials were already operationally external immediately before advisory, and advisory-near rates are about 99%; do not claim a distinct post-advisory external gaze shift from this session.
- Raw-component means changed from `OUTSIDE_VIEW/UNTRACKED_OR_OFF_DISPLAY=31.3%/67.4%` at `-1~0 s` to `63.7%/34.9%` at `0~+0.5 s` and `87.7%/11.4%` at `+0.5~+1 s`.
- Interpret this as movement from side-monitor/unobserved operational external toward the tracked front-monitor outside view, not as an instrument-to-external transition.
## 2026-07-22 Tobii trajectory display rule

- In `*_advisory_shown_trajectory.svg`, raw gaze samples are faint background points and the emphasized arrows connect consecutive 200 ms gaze centroids.
- Trajectory event names are placed in a separate numbered footer to avoid cockpit-image label overlap.
- Do not call the 200 ms centroids fixations. They are arithmetic mean gaze positions used only to simplify the visible movement path.
- A high `OUTSIDE_VIEW` proportion means gaze was measured in the forward outside-view region; it does not confirm that the intruder was detected or tracked.

## 2026-07-24 APISAT experiment redesign baseline

Design reference:

- `APISAT_EXPERIMENT_SCENARIO_BASELINE_260724.md`
- This is the Step 1 design baseline. Its planned events and parameters are not implemented in v22 yet.

Research anchor:

- The participant is intended to discover and avoid an unannounced intruder directly, without a participant-visible traffic advisory.
- Move the primary response anchor from `ADVISORY_SHOWN` to `INTRUDER_SPAWNED`.
- `INTRUDER_SPAWNED` means objective intruder/threat-exposure onset, not conscious recognition.
- Report `spawn-to-response latency`; do not label it recognition or awareness time.
- If a later pixel/FOV threshold is implemented, call it `INTRUDER_DETECTABLE_ONSET`, not recognition.
- When a farther spawn includes objectively invisible time, use detectability-to-response as the primary latency and retain spawn-to-response as a secondary exposure metric.

Participant display plan:

- Implemented in v23: hide countdown, randomized-crossing state, trial/debug coordinates, distance, event count and similar experiment status from the participant.
- Implemented in v23: hide `TRAFFIC ALERT` and `CHECK OUTSIDE` for the direct-detection experiment.
- Preserve internal events, distance/hazard calculation, minimum distance, end guard and clean-trial diagnostics.
- Separate participant-facing drawing from internal experiment state.
- In new no-alert trials, do not emit `ADVISORY_SHOWN/CLEARED` for a warning that was never presented. Use `HAZARD_DETECTED/CLEARED` for the internal risk window and retain advisory parser compatibility only for historical CSV files.

Scenario standardization:

- Use one saved final-approach initial condition per trial and reload it before every trial.
- Do not treat multiple encounters during one continuous descent as equivalent repeated trials.
- Keep the same aircraft/configuration, runway, weather, starting distance/AGL, course, target IAS and glidepath.
- Current pilot candidate is runway course about `325°`, start near `3 NM` and `900~1,000 ft AGL`, and approximately a `3°` glidepath.
- `70 KIAS` is only a pilot-test candidate. Confirm the exact C172 model, flap configuration and POH before freezing it.
- Do not derive the target IAS from the 2026-07-20 data.

Audio-task plan:

- Use one standardized prerecorded instruction rather than live operator speech.
- First candidate phrase: `Cessna Zero One, continue straight-in, maintain seven zero knots, track runway centerline.`
- Use a `2~5 s` pre-task baseline, then log audio start/end.
- Spawn the intruder after an independent uniform `3~10 s` delay from audio start, regardless of whether the participant has completed the task.
- The maintenance task remains active until trial end.
- Avoid large arbitrary heading changes, fixed-altitude commands during descent, and per-trial numeric vertical-speed commands.
- Treat stabilized glidepath/descent as a prebriefed condition and performance measure.

Planned events and response rule:

- Candidate events: `TASK_COMMAND_AUDIO_START`, `TASK_COMMAND_AUDIO_END`, optional target-state events and later `INTRUDER_DETECTABLE_ONSET`.
- Use approximately one second of pre-spawn controls as the response baseline.
- Require a threshold-crossing control change to persist for a pilot-validated `200~300 ms` candidate duration.
- Record task error and whether task-related control was already active at spawn.
- When implementing new events, update Lua, Java enum/parser/logger/analyzer and documents together.

Trial validity:

- Separate `recording/event complete`, `flight-condition-valid`, and behavioral outcome.
- Evaluate IAS, runway course/centerline, glidepath/AGL corridor, bank and rolling vertical speed for the flight-condition layer.
- Do not require `HAZARD_DETECTED`, `ADVISORY_SHOWN`, or `PILOT_RESPONSE_START` for a usable recording: successful early avoidance may prevent a hazard, and no detected response can be a valid outcome.
- Keep current hazard thresholds (`150 m` horizontal and `30 m` vertical trigger; `200 m` horizontal or `50 m` vertical clear) as internal severity/outcome measures until separately revised.

Latest pilot-data limitation:

- The 2026-07-20 session remains pilot/system-validation data and is not pooled with future main-experiment data.
- At spawn, IAS was `119.8~148.9 KIAS`, heading `323.9~325.6°`, AGL `101~1,864 ft`, and vertical speed `-1,014~-44 fpm`.
- The CSV column `ias_mps` carries KIAS because its source dataref is `sim/flightmodel/position/indicated_airspeed`.
- X-Plane `Log.txt` confirms the ownship is the default Laminar Research Cessna 172SP; STATE trajectory and X-Plane `apt.dat` identify the approach as RKSI runway 34 at about 325°.
- Current STATE does not record flap configuration. Add automatic flap-state logging with any next schema update; do not reconstruct it from the old trial.
- Strict clean in existing output only confirms the required event sequence, not a stabilized or equivalent approach.

Three-monitor roadmap:

- Local baseline on 2026-07-24: RTX 2060 driver `560.94`; Windows reports three independent `1920x1080` displays at x=`-1920`, `0`, and `1920`, with the center display primary.
- First test one Spark with NVIDIA Surround, three flat/co-planar monitors, identical scaling and whole-display calibration.
- Validate 15 fixed targets, five per monitor, for three repetitions.
- Measure per-monitor valid rate, median/95th-percentile error, left/center/right classification, dropout/recovery and central AOI degradation.
- Proposed research go/no-go values, not Tobii specifications: side classification at least `90%`, side valid rate at least `70%`, and center error no more than `20%` worse than the current baseline.
- Surround unifies the logical coordinate space but does not guarantee physical gaze coverage.
- If it fails, retain center-only Spark and `UNTRACKED_OR_OFF_DISPLAY`.
- Do not assume a second Spark is guaranteed to work. Confirm simultaneous streams, per-display calibration, IR interference, USB and timestamp behavior with Tobii or a loan test before purchase.
- Tobii 4C is excluded because it is not supported by the current Tobii Pro SDK workflow.
- Official Spark guidance gives an optimal 16:9 screen size up to 27 inches but also describes a 35° maximum gaze angle and geometry-dependent large/wide/multi-display setups; use measured validation rather than treating either statement as a guarantee.

External-view interpretation:

- Eye tracking establishes the viewed object/region, not the participant's internal purpose.
- Planned AOIs include `RUNWAY_DYNAMIC`, `INTRUDER_DYNAMIC`, `HORIZON_OR_EXTERNAL_OTHER`, monitor-side outside categories, existing instruments and unresolved samples.
- Combine dynamic AOI dwell with flight/control context, but do not claim awareness or intent from gaze alone.

APISAT paper rule:

- Full-paper deadline: `2026-09-15`.
- Abstract reference files are `2026 APISAT/APISAT-2026_Abstract(김석호)최종.docx` and the matching PDF.
- Frame the paper as an event-aligned X-Plane/Java/Tobii data-collection and preliminary-validation framework.
- Do not claim direct threat-recognition time, general pilot behavior, or a completed AI pilot model.
- Treat pre-redesign sessions as pilot/system-validation data and keep them separate from the frozen-protocol main experiment.

## 2026-07-24 Stage 2 Tobii Surround validation rule

Authoritative procedure:

- `TOBII_SURROUND_VALIDATION_260724.md`
- `tools/tobii/multimonitor_gaze_validation.py`
- tool version `260724_v2`, output schema version `2`

Current status:

```text
PRECISE WIDE AOI no-go / COARSE SIDE_EXTERNAL conditional go / X-Plane pilot pending
```

- Research-valid center baseline session: `tobii_multimonitor_center_baseline_20260727_154315`.
- Baseline result: `15/15` epochs, median coverage `100.0%`, center valid rate `96.5%`, center classification `99.9%`, and median target-centroid error `36.1 px`.
- Matched viewing distance was `750 mm`; the three panels were flat/co-planar and bezel correction was `0 mm`.
- Windows exposed one primary `5760x1080` Surround display at `(0,0)`.
- The corrected whole-wide Active Display Area was `1794 x 336 mm` with display-plane depth about `z=-60 mm`; stored calibration data was present.
- Physical Surround sessions were `tobii_multimonitor_surround_wide_20260727_155743` and retry `tobii_multimonitor_surround_wide_20260727_160333`.
- Both sessions completed all `45/45` epochs, but the strict tool result was `INCONCLUSIVE` because one or two `LEFT_LL` epochs had stream coverage below `80%`; those same epochs contained zero valid gaze rather than a user abort or missing event sequence.
- First-run valid rates were left `44.4%`, center `93.5%`, and right `42.1%`; retry valid rates were left `37.5%`, center `96.3%`, and right `40.1%`.
- In both runs, multiple outer side targets had `0%` valid gaze across all three repeats. Side median target-centroid errors, where finite, were roughly `170~371 px` in the first run and `297~346 px` in the retry.
- The automatic comparison must still be quoted as `INCONCLUSIVE`, not literal `FAIL`. Nevertheless, the repeated side-validity values are far below the project `70%` threshold and the worst-target `50%` rule is violated by complete side-target dropouts. The project engineering/operational decision is therefore that one center-mounted Spark is unsuitable for whole-surface three-monitor gaze measurement in this tested geometry.
- This is a setup-specific feasibility result, not a universal claim that Spark can never be used with any wide display.
- The experiment only needs coarse central-versus-side external attention, not precise side AOIs. For that narrower endpoint, keep Surround temporarily and evaluate the hybrid classifier before deciding whether to restore center-only operation.

Coarse external classifier:

- Analyzer: `tools/tobii/analyze_xplane_tobii_session.py`
- Analyzer version: `260728_spawn_external_v2`
- Validation tool: `tools/tobii/validate_surround_external_classifier.py`
- `--layout-mode surround-wide` maps normalized gaze to `5760x1080`; the center monitor occupies global `x=1920..3840`, and its instrument AOIs use local `x = global_x - 1920`.
- A measured side episode requires at least `200 ms` beyond a `120 px` guard band from the two center/side seams.
- A loss-only inferred episode requires continuous bilateral invalidity of `500~5000 ms`, valid samples on both sides of the loss, and no inter-sample gap above `100 ms`.
- Evidence classes are `MEASURED_SIDE_LEFT`, `MEASURED_SIDE_RIGHT`, `INFERRED_SIDE_UNKNOWN`, and `UNRESOLVED_TRACKING_LOSS`. Only the first two carry a left/right direction.
- `OPERATIONAL_EXTERNAL` may combine center `OUTSIDE_VIEW` with measured and inferred side evidence, but raw evidence must remain in separate columns for reporting and sensitivity analysis.
- Post-hoc validation on the two physical runs contained `90` labeled epochs: side `59/60` detected, center `30/30` correctly rejected, and measured direction `24/24` correct when evaluable.
- These results are single-participant and threshold-selected on the same target-task data. They justify an X-Plane pilot, not a main-experiment accuracy claim.
- Default `center` mode regression on the 2026-07-20 session preserved all `19,408` rows, `14,780` valid rows, AOI counts, operational-external count `15,694`, and legacy trial metrics.

Collection rules:

- Close X-Plane and the ordinary Tobii session logger. Select the Spark by serial `TPE01-100206101311`.
- Measure and explicitly supply panel width, panel height, eye-to-screen distance, and Surround bezel gap. Do not use an assumed viewing distance.
- `--bezel-mm` means the total gap between adjacent visible display areas, not one plastic bezel width.
- Record a non-identifying participant code, one shared physical setup ID, and a separate calibration ID for each run.
- Collect `CENTER_BASELINE` before changing NVIDIA or Tobii display setup.
- Then configure NVIDIA Surround as one logical `5760x1080` display, configure/calibrate the whole wide display in Tobii Eye Tracker Manager, and collect `SURROUND_WIDE`.
- Keep the monitors flat/co-planar, Windows scaling at 100%, bezel correction off for the first comparison, and seat/lighting/eyewear/mount conditions unchanged.
- `--allow-layout-mismatch` and `--allow-display-area-mismatch` are diagnostic-only. Outputs made with either flag cannot pass research comparison.
- If Windows or Tobii display-area configuration fails before the GUI, preserve the generated `*_preflight.json` as `CONFIGURATION_FAILED` evidence.

Data-integrity rules:

- Raw gaze is written through a queue and flushed about once per second; target events are flushed when they occur.
- `Esc`, window close, `Ctrl+C`, and Tk callback errors preserve the incomplete-session output path; use `Esc` for normal manual abort.
- Use monotonic timestamps for within-run event latency and wall-clock timestamps only for audit/merging.
- A valid collection requires exactly `15` baseline or `45` Surround epochs, three repeats per target, the complete ordered target-event sequence, actual measure duration within 90–120% of plan, per-epoch stream coverage of 80–120%, and condition median coverage of 90–110%.
- Missing epoch/target data must not be dropped from averages. It makes the comparison `INCONCLUSIVE`.
- SVG reports preserve the display aspect ratio and carry diagnostic/incomplete status.

Decision rule:

- Comparison has three states: `PASS`, `FAIL`, and `INCONCLUSIVE`.
- `INCONCLUSIVE` is used for setup, protocol, stream, baseline-quality, or pair-matching problems; `FAIL` is reserved for performance-threshold failure in comparable research-valid data.
- Left and right are assessed separately: valid rate `>=70%`, correct-monitor classification among valid samples `>=90%`, and worst-target median repeat-valid rate `>=50%`.
- Baseline and Surround center valid rate must each be `>=70%`; center median target-centroid error may degrade by no more than `20%`.
- The two runs must use the same participant code, physical setup ID, tracker/frequency, matching timing and target layout, the same panel geometry, eye-to-screen distance within `20 mm`, and matching center pixel geometry.
- `MONITOR_LEVEL_PASS` does not mean small runway/intruder AOIs are accurate. `FINE_AOI_PASS` remains unevaluated until side sample-error `p95` is compared with the intended AOI margin.

After the test:

- Do not use side-monitor gaze as a precise AOI or intruder fixation.
- Keep Surround only through a short X-Plane pilot that checks center-instrument AOIs and coarse side-external episodes.
- If the X-Plane pilot fails, restore extended desktop and the normal center-monitor Tobii setup/calibration.
- If it passes, freeze the coarse classifier before main data collection and report measured, inferred, and unresolved evidence separately.
- Historical wording that Spark “tracks only the front monitor” describes the 2026-07-16 center-only configuration; it is not an absolute hardware-limit claim.

## 2026-07-28 Surround cockpit audio-cue pilot rule

Authoritative procedure:

- `TOBII_SURROUND_COCKPIT_PILOT_260728.md`

Tools:

- cue runner: `tools/tobii/run_surround_cockpit_validation.ps1`
- cue runner version: `260728_v2_sync_speech`
- cue/gaze analyzer: `tools/tobii/analyze_surround_cockpit_validation.py`
- cue analyzer version: `260728_v1`
- shared gaze analyzer: `tools/tobii/analyze_xplane_tobii_session.py`

Current status:

```text
NATURAL DIAGNOSTIC COMPLETE / SCENARIO AND SPAWN-ANCHORED RESPONSE UPDATE NEXT
```

- Windows SAPI COM exposes `Microsoft Heami Desktop - Korean`; the cue runner selects it explicitly and falls back to the first available SAPI voice.
- The cue runner requires no participant key input and does not display an overlay over X-Plane.
- It waits `8 s`, then speaks nine instructions. Each instruction now finishes completely before the `1.5 s` settle period begins; measurement lasts `2.5 s`, followed by a `0.5 s` inter-cue gap.
- The protocol includes four side-external cues, four center-instrument cues, and one center-outside cue.
- Every cue writes `CUE_START`, `SPEECH_END`, `MEASURE_START`, `MEASURE_END`, and `CUE_END` immediately to `logs/tobii/cockpit_validation`.
- Run the normal Tobii logger before the cue runner. Java receiver is not required for this display/setup check.
- The analyzer aligns cue and gaze PC timestamps, applies the fixed Surround hybrid classifier, and writes cue results, summary JSON, classified gaze, and external episodes.
- `PILOT_PASS` requires per-cue sample coverage `>=80%`, side sensitivity `>=90%`, center specificity `>=90%`, and expected center AOI dwell success `>=80%`.
- Loss-only evidence remains `INFERRED_SIDE_UNKNOWN`; it never receives a left/right direction.
- A no-speech nine-cue dry run produced all `49` required event rows.
- Synthetic end-to-end verification mixed directly measured side cues with inferred loss-only side cues and returned side `4/4`, center specificity `5/5`, center AOI dwell `5/5`, and measured direction `2/2`.
- Do not run a main dataset with v22. The cockpit cue pilot passed, but the
  natural diagnostic showed both trials were already operationally external
  before spawn.

## 2026-07-28 X-Plane Surround FOV and cue compatibility

- NVIDIA Surround remains one logical `5760x1080` display.
- X-Plane initially retained a single-monitor lateral FOV of `60 deg`, which made the center view appear severely zoomed.
- The physical setup check selected lateral FOV `122 deg`; X-Plane preferences confirmed `FOVx_renopt=122`, `FOVy_renopt=37.377142`, and zero lateral, vertical, and roll offsets.
- The X-Plane screenshot `Cessna_172SP - 2026-07-28 14.00.18.png` was cropped to the center `1920x1080` region for comparison with `AOI그림.png`.
- The current instrument panel is approximately `30-50 px` lower than the old background, but all six instrument centers remain inside the fixed AOI rectangles. This is acceptable for the cockpit audio-cue pilot; do not change the view again before that run.
- `run_surround_cockpit_validation.ps1` now stores Korean speech strings as ASCII-safe UTF-8 Base64 and decodes them at runtime. This avoids mojibake when Windows PowerShell 5.1 reads a BOM-less UTF-8 script.
- A Windows PowerShell 5.1 no-speech regression produced nine cue starts, nine speech ends, nine measurement starts, and all `49` expected rows; the decoded first cue was `자세계를 바라보십시오.`
- The first physical run paired cue session `20260728_141201` with gaze session `20260728_140939`. It returned side `4/4`, center specificity `4/5`, center AOI dwell `3/5`, and measured direction `2/2`.
- The first run is diagnostic only. Cue 3 retained a measured left-side episode for about `1.116 s` after its measurement window began, matching the reported late response.
- Root cause: cue speech used SAPI flag `3` (`Async + Purge`) so the settle timer started before the spoken instruction finished. Runner v2 uses synchronous purge flag `2`, records `SPEECH_END`, and starts settle only afterward.
- The corrected physical run paired cue session `20260728_142411` with gaze session `20260728_142328` and returned `PILOT_PASS`.
- Corrected-run metrics: coverage minimum `93.6%`, side `4/4`, center specificity `5/5`, center target dwell `5/5`, measured direction `3/3`, and mean valid gaze rate `67.8%`.
- Three side cues had directly measured and correct left/right direction. One left cue used loss-only `INFERRED_SIDE_UNKNOWN`, so its direction remains unknown by design.
- Freeze Surround `5760x1080`, X-Plane FOV `122 deg`, zero visual offsets, the current Spark calibration, and the hybrid classifier thresholds for the next short natural intruder trial.
- This pass validates coarse `center versus side external` use for the current operator/setup only. It does not validate precise gaze coordinates on the outer monitors or universal performance across participants.

## 2026-07-28 Surround natural intruder diagnostic

- Java session: `session_surround_tobii_20260728_144151`
- X-Plane files: `logs/xplane/xplane_surround_tobii_20260728_144151*.csv`
- Tobii file: `logs/tobii/session_xplane_tobii_20260728_144218_gaze.csv`
- STATE/INTRUDER: `413` rows each; gaze: `4,965` rows; valid gaze: `2,995` (`60.3%`).
- The intended collection produced two complete trials, trial 4 (right) and trial 5 (left). Both are Java `CLEAN`.
- The analyzer also reports residual trial 3 samples before the first new `TRIAL_RESET`; this is receiver pre-roll, not a performed trial.
- Trial 4: advisory-to-response `0.648 s`, valid gaze `47.9%`, operational external `40.9%`.
- Trial 5: advisory-to-response `1.148 s`, valid gaze `63.1%`, operational external `52.7%`.
- Both trials were already `OPERATIONAL_EXTERNAL` in the `500 ms` before `INTRUDER_SPAWNED`, so near-zero spawn-to-external latency is not an acquisition latency.
- Trial 4 post-spawn `0~5 s` evidence was `22.3%` center-outside measured and `75.7%` unresolved tracking loss.
- Trial 5 post-spawn `0~5 s` evidence was `7.6%` center-outside measured and `91.0%` inferred-side unknown.
- No post-spawn measured left/right side episode occurred in either trial. Do not claim precise side direction or confirmed intruder fixation.
- `analyze_xplane_tobii_session.py` version `260728_spawn_external_v2` adds spawn-anchored external latency, before-spawn state, and eight spawn-centered time-bin rates while preserving every existing output field.
- Regression on the same session compared `104` common trial-summary fields between v1 and v2 and found `0` changed values.
- Next implementation priority: add the standardized instrument-maintenance audio task, arm an independent `3~10 s` spawn delay from audio start, and anchor pilot-response detection to `INTRUDER_SPAWNED`.

## 2026-07-28 Lua v23 participant display isolation

- Active version: `260728_participant_display_hidden_v23`.
- Snapshot: `FLYWITHLUA_STUDY_INTEGRATED_260728_participant_display_hidden_v23.lua`.
- The participant-facing overlay is off by default, so countdown, randomized-crossing state, coordinates, distance, event count, and visual advisory text are not drawn.
- Internal `visual_advisory_active`, UDP transmission, event recording, Java clean-trial logic, and the Lua end-trial guard remain unchanged.
- `flywithlua/study/toggle_operator_overlay` and the matching macro expose the former full overlay only outside an active trial.
- Starting a trial forces the overlay off, and an attempt to enable it during an active trial is ignored.
- The Git baseline and X-Plane `Scripts` copy have matching hashes. If X-Plane was already running when the file was copied, use `Plugins > FlyWithLua > Reload all Lua script files` and confirm the v23 load line in `Log.txt`.
- This change does not yet alter the `6~14 s` crossing delay, add the audio-maintenance task, or move Java pilot-response detection to `INTRUDER_SPAWNED`.

## 2026-07-28 Lua v24 minimal trial status

- Active version: `260728_minimal_trial_status_v24`.
- Snapshot: `FLYWITHLUA_STUDY_INTEGRATED_260728_minimal_trial_status_v24.lua`.
- v23 reload was confirmed in X-Plane `Log.txt` without a FlyWithLua error.
- The fully hidden v23 participant display was too restrictive for experiment operation.
- v24 always draws only one neutral line: `TRIAL IDLE`, `TRIAL RESET: <id>`, `TRIAL ACTIVE: <id>`, or `TRIAL END: <id>`.
- The line confirms reset/start/end command acceptance without revealing random delay, intruder state, scenario state, distance, coordinates, event counts, or advisory state.
- If an end request is rejected by the existing guard, the line remains `TRIAL ACTIVE`, allowing the operator to see that the trial did not close without exposing the reason.
- The full operator overlay remains available only outside an active trial; starting a trial still forces it off.

## 2026-07-28 Lua v25 rejected-end notice

- Active version: `260728_end_wait_notice_v25`.
- Snapshot: `FLYWITHLUA_STUDY_INTEGRATED_260728_end_wait_notice_v25.lua`.
- v24 reload and normal `RESET/ACTIVE/END` display operation were confirmed.
- When `Study End Trial` is pressed before the existing close guard permits termination, show `TRIAL IN PROGRESS - END NOT READY` for `2.5 s`.
- After the notice expires, return to the normal `TRIAL ACTIVE: <id>` line.
- Do not identify the intruder, hazard, visual-advisory state, or specific guard cause in the participant message.
- Pressing end with no active trial shows `NO ACTIVE TRIAL` for the same `2.5 s`.
- Accepted end behavior and all logging/analysis behavior remain unchanged.

## 2026-07-28 Lua/Java v26 standardized task audio

- Active Lua version: `260728_standard_task_audio_v26`.
- Lua snapshot: `FLYWITHLUA_STUDY_INTEGRATED_260728_standard_task_audio_v26.lua`.
- Audio asset: `resources/audio/apisat_task_maintain_70kias_centerline_en_us.wav`.
- Reproducible generator: `tools/audio/generate_standard_task_audio.ps1`.
- Actual FlyWithLua asset path: `Scripts/AISimulationProjectAssets/apisat_task_maintain_70kias_centerline_en_us.wav`.
- WAV format: mono, 16-bit PCM, `22050 Hz`, `6.493 s`; voice `Microsoft Zira Desktop - English (United States)`, rate `1`, volume `100`.
- Phrase: `Cessna zero one, continue straight in, maintain seven zero knots, track runway centerline.`
- Starting a trial records protocol `audio_task_v26`, waits a fixed `3.0 s` baseline, records `TASK_COMMAND_AUDIO_START`, and calls FlyWithLua OpenAL `play_sound`.
- `TASK_COMMAND_AUDIO_END` is recorded at the verified WAV-duration boundary using wall-clock timing. The detail preserves file name and scheduled/actual duration.
- Trial start is rejected with `TASK AUDIO NOT READY` if the WAV was not loaded.
- The end-trial guard also waits for armed/active task audio to finish.
- `flywithlua/study/test_task_audio` and `Study Test Task Audio` play the asset only outside an active trial and do not create task events.
- Java `XPlaneEventType` accepts both new events. Java batch analysis and the Tobii merge analyzer export audio timestamps and start-to-audio/audio-duration/audio-to-spawn latency.
- New `audio_task_*` trials require exactly one audio start and one audio end in the correct order; historical sessions without the protocol keep their previous clean/incomplete result.
- Static verification passed: Java 21 compile, Python AST parse, PowerShell parse, WAV format check, `git diff --check`, and historical-session clean result `2/3` unchanged.
- The current random crossing delay is still `6~14 s` from trial start. Moving it to independent `3~10 s` from audio start is the next step.

## 2026-07-28 Lua/analysis v27 audio-anchored spawn

- Active Lua version: `260728_audio_anchored_spawn_v27`.
- Lua snapshot: `FLYWITHLUA_STUDY_INTEGRATED_260728_audio_anchored_spawn_v27.lua`.
- Trial protocol is `audio_task_v27`.
- The fixed `3.0 s` trial-start baseline and the existing 6.493 s WAV are retained.
- Successful `TASK_COMMAND_AUDIO_START` playback arms the crossing scenario and independently samples `U(3,10 s)` from the audio-start simulation time.
- `SCENARIO_SELECTED` records the delay anchor, planned audio-to-spawn delay, audio-start time, and trigger time.
- `INTRUDER_SPAWNED` is now recorded when the intruder is first written into X-Plane at stabilization start. It no longer waits until the 0.35 s stabilization period has finished.
- The spawn detail records `spawn_phase=stabilization_start`, planned and actual audio-to-spawn time, and the existing approach/path parameters.
- `TRIAL_START` records X-Plane master, interior, engine, prop, environment, and radio sound-volume ratios. These are protocol-context values, not proof that the participant heard the command.
- Java session/batch analysis and `analyze_xplane_tobii_session.py` version `260728_audio_anchored_spawn_v4` export planned/actual audio-to-spawn timing, a v27 timing-valid flag, and the six sound ratios.
- Historical sessions remain backward compatible. The 2026-07-28 Surround diagnostic still returns total `3`, clean `2`, incomplete `1`; v27-only fields remain blank.
- Static verification passed: all six X-Plane 11 sound datarefs exist, Java 21 compile passed, Python AST parsing passed, `git diff --check` passed, and the active/snapshot Lua hashes match.
- Java pilot-response detection remains anchored to `ADVISORY_SHOWN`; changing it to `INTRUDER_SPAWNED` is the next implementation step.

## 2026-07-29 v27 timing pilot result

- Session: `session_audio_spawn_20260729_130859`.
- Prefix: `logs/xplane/xplane_audio_spawn_20260729_130859`.
- Final rows: STATE `768`, INTRUDER `768`, EVENT `47`.
- Receiver shutdown was clean and recorded `RECEIVER_STOP` followed by `SESSION_STOP`.
- Three physical executions produced planned/actual audio-to-spawn pairs:
  - `8.435 / 8.445 s` (`+10 ms`)
  - `9.918 / 9.938 s` (`+20 ms`)
  - `9.272 / 9.275 s` (`+3 ms`)
- All three used `spawn_phase=stabilization_start` and were within `3~10 s`.
- The second execution omitted `Study Reset Trial`, so the first two executions share `trial_id=1`. The Java trial result is therefore total `2`, clean `1`, incomplete `1` with `duplicate TRIAL_START count=2`.
- The timing evidence remains usable for v27 implementation validation, but the merged trial is not independent experimental data.
- Three draws do not validate the uniform distribution itself; they validate planned-versus-actual execution accuracy.

## 2026-07-29 spawn-anchored pilot-response detector

- `XPlaneAutoEventDetector` now opens the response window at `INTRUDER_SPAWNED`, not `ADVISORY_SHOWN`.
- New event: `PILOT_RESPONSE_BASELINE`.
- Baseline method: mean control input over the requested `1.0 s` pre-spawn window.
- Baseline validity requires at least `3` state samples; the current approximately 9 Hz stream normally provides `9~10`.
- `active_control_at_spawn=true` means at least one input's pre-spawn peak-to-peak range already exceeded its response threshold. It is a confounding/context flag, not proof of a collision-avoidance response.
- Response thresholds remain pitch `0.08`, roll `0.08`, yaw `0.05`, throttle `0.08`.
- A threshold crossing must persist for at least `0.25 s`. After confirmation, `PILOT_RESPONSE_START` is timestamped at the first threshold-crossing sample.
- Response detail records anchor, method, actual persistence, trigger axis, input deltas, baseline sample count, and active-control flag.
- Response end is recorded after the existing eight settle samples or immediately before `TRIAL_END` when a response is still active.
- The receiver calls the detector's pre-event hook so a forced `PILOT_RESPONSE_END` is ordered before the inbound `TRIAL_END`.
- New no-alert sessions no longer auto-create `ADVISORY_SHOWN/CLEARED`; internal risk severity remains `HAZARD_DETECTED/CLEARED`. Historical/manual advisory parsing remains supported.
- Spawn-response recording completeness requires reset, start, scenario, spawn, response baseline, minimum-distance marker, and trial end. Hazard/advisory/response pairs are optional outcomes, but when present must be complete and time-ordered.
- Java batch output and Tobii analyzer version `260729_spawn_response_v5` add spawn-to-response latency, baseline validity/window/sample count, active-control flag, trigger axis, method, and persistence.
- `XPlaneAutoEventDetectorSmokeTest` covers sustained response backdating, transient/non-response, active-at-spawn flagging, trial-end closure, and three clean recording-complete paths.
- Verification passed: Java 21 compile, detector smoke test, Java synthetic analysis `3/3` clean, Python synthetic analysis `3/3` clean, historical v27 timing result unchanged, historical Surround result unchanged at `2/3` clean, and `git diff --check`.

## 2026-07-29 randomized speed-maintenance task v28

- Active Lua version: `260729_random_speed_task_v28`.
- Snapshot: `FLYWITHLUA_STUDY_INTEGRATED_260729_random_speed_task_v28.lua`.
- Trial protocol: `audio_task_v28`.
- Each trial independently and uniformly selects one target from `110`, `115`,
  `120`, `125`, or `130 KIAS`; each nominal selection probability is `20%`.
- The selected speed is fixed for that trial and is recorded as
  `target_speed_kias` in `TRIAL_START`, `TASK_COMMAND_AUDIO_START`, and
  `TASK_COMMAND_AUDIO_END`. The selected WAV file is also recorded.
- The five standardized WAV files use Microsoft Zira, rate `1`, volume `100`,
  mono 16-bit PCM at `22050 Hz`. Their verified durations are respectively
  `6.583401`, `6.543447`, `6.593469`, `6.568435`, and `6.608345 s`.
- Runtime still waits `3.0 s` after trial start before audio and independently
  samples intruder spawn at `U(3,10 s)` after audio start.
- All five assets must load successfully or trial start is rejected with
  `TASK AUDIO NOT READY`.
- This speed range is an intentional experimental instrument-attention task.
  Do not describe it as a realistic C172 final-approach speed schedule.
- Java session/batch analysis adds target speed, IAS at audio start, IAS at
  spawn, pre-spawn mean absolute speed error, and pre-spawn fraction within
  `±5 KIAS`. The legacy state CSV header is `ias_mps`, but the X-Plane value in
  this pipeline is interpreted as KIAS.
- Tobii analyzer version `260729_random_speed_task_v6` preserves
  `target_speed_kias` and accepts v28 audio/spawn ordering.
- `XPlaneRandomSpeedTaskAnalysisSmokeTest` verifies v28 completeness, timing,
  target parsing, and all four speed-performance calculations.
- Verification passed: five WAV format/duration checks, Java 21 compile,
  response-detector smoke test, random-speed analysis smoke test, batch CSV
  field/value check, Python compile, and Python v28 target/timing integration.
- The Git Lua and runtime FlyWithLua Lua hashes match; all five source/runtime
  WAV hashes also match. X-Plane must reload FlyWithLua before the live v28
  trial.

## 2026-07-29 v28 live pilot result

- Java session: `session_random_speed_v28_20260729_135933`.
- X-Plane prefix:
  `logs/xplane/xplane_random_speed_v28_20260729_135933`.
- Tobii file:
  `logs/tobii/session_xplane_tobii_20260729_135834_gaze.csv`.
- Final rows: STATE `345`, INTRUDER `345`, EVENT `17`, gaze `15002`.
- Receiver shutdown was clean (`RECEIVER_STOP`, `SESSION_STOP`).
- Trial result: `1/1 CLEAN`, left approach, target `130 KIAS`.
- Planned/actual audio-to-spawn was `3.061/3.079 s`; v28 timing validation
  passed.
- Response baseline was valid (`10` samples, `0.968 s`) with
  `active_control_at_spawn=false`.
- Spawn-to-response was `2.217 s`, and the detector trigger axis was pitch.
- No automatic advisory event was generated. Hazard window was `2.977 s`;
  minimum horizontal/vertical separation was `8.629/10.976 m`.
- IAS was `111.762` at audio start and `113.502` at spawn. Pre-spawn speed
  MAE was `17.305 KIAS` and the within-`±5 KIAS` rate was `0`.
- The target instruction lasted `6.608 s`, so spawn occurred before speech
  completion. Pre-spawn speed error is therefore not a valid standalone
  compliance judgment for short-delay trials.
- IAS reached `120.770 KIAS` at minimum distance and `122.348 KIAS` at trial
  end, still outside `±5 KIAS` of the `130 KIAS` target.
- Trial gaze validity was `86.1%`. Operational-external classification was
  `31.5%`: center-outside measured `18.6%`, inferred-side unknown `9.8%`,
  and measured-side right `3.1%`.
- Last valid gaze before spawn was `AIRSPEED`; the first post-spawn
  operational-external classification occurred after `0.795 s`.
- There was no directly measured side-monitor episode after spawn. Do not
  claim confirmed left-side intruder fixation or precise acquisition time.
- The standard `visualize_tobii_gaze.py` is not yet Surround-aware. Do not
  generate or interpret its cockpit-coordinate SVG for this session until
  center-monitor remapping is added.
- Before main collection, define a fair speed-performance window and handle
  unequal target difficulty caused by differing initial IAS. Preserve target
  speed and initial speed error for stratification even if the target rule is
  unchanged.

## 2026-07-29 revised speed range v29

- The current work is pre-main-study experiment refinement and
  system/protocol validation. The single v28 physical trial is pilot evidence,
  not a main participant result.
- Active Lua version: `260729_revised_speed_task_v29`.
- Snapshot: `FLYWITHLUA_STUDY_INTEGRATED_260729_revised_speed_task_v29.lua`.
- Trial protocol: `audio_task_v29`.
- The active target set is now `90`, `95`, `100`, `105`, and `110 KIAS`, with
  equal trial-level selection probability and `5 KIAS` spacing.
- v28 assets for `115~130 KIAS` remain preserved for reproducibility but are
  not loaded or selected by v29.
- New WAV durations are `6.413379`, `6.383447`, `6.683401`, `6.663447`, and
  `6.583401 s` for `90`, `95`, `100`, `105`, and `110 KIAS`.
- The `3.0 s` trial-start audio baseline and independent `U(3,10 s)`
  audio-start-to-spawn delay are unchanged.
- Java analysis accepts v27/v28/v29. Tobii analyzer version is
  `260729_revised_speed_task_v7`.
- The v29 synthetic smoke trial uses a `100 KIAS` target and verifies target
  parsing, IAS-at-audio/spawn, speed MAE, within-`±5 KIAS` rate, timing, and
  recording completeness.
- Java 21 compile, both Java smoke tests, Python compile/integration, and v28
  physical-session regression passed.
- Git/runtime Lua and all five active WAV hashes match. Reload FlyWithLua
  before the next v29 trial.
- Narrowing the range reduces the v28 high-speed burden but does not guarantee
  identical target difficulty. Continue storing initial IAS and target error.

## 2026-07-29 v29 log-only physical check

- X-Plane successfully reloaded `260729_revised_speed_task_v29`.
- One functional trial selected `100 KIAS`.
- Planned/actual audio-to-spawn timing was `7.708/7.720 s`.
- Audio ended `1.023 s` before spawn; approach was front.
- Lua recorded `MIN_DISTANCE_REACHED` at `45.004 m` and accepted
  `TRIAL_END`.
- Java receiver and Tobii logger had already been stopped, so this run has no
  new X-Plane CSV or gaze CSV. Use it only as an audio/Lua usability check,
  not as response or gaze evidence.

## 2026-07-29 v29 speed-range freeze decision

- The operator reported the v29 `100 KIAS` trial felt appropriately demanding.
- Freeze the main-study audio target set at
  `90/95/100/105/110 KIAS`, with `5 KIAS` spacing and uniform trial-level
  selection.
- Do not change this range again unless a functional or safety problem is
  demonstrated.
- Keep `target_speed_kias`, audio-start IAS, and initial target error for
  difficulty adjustment.
- Treat `APISAT_EXPERIMENT_SCENARIO_BASELINE_260724.md` as updated to the v29
  frozen speed range.
- One fully logged v29 trial with Java and Tobii remains the final
  implementation confirmation before main collection.

## 2026-07-29 fully logged v29 trial result

- Java session: `session_revised_speed_v29_20260729_142254`.
- X-Plane prefix:
  `logs/xplane/xplane_revised_speed_v29_20260729_142254`.
- Tobii file:
  `logs/tobii/session_xplane_tobii_20260729_142157_gaze.csv`.
- Final rows: STATE `422`, INTRUDER `422`, EVENT `17`, gaze `12642`.
- Receiver shutdown was clean.
- The performed trial is trial `2`: target `100 KIAS`, left approach,
  planned/actual audio-to-spawn `7.309/7.324 s`, minimum horizontal/vertical
  separation `13.825/37.433 m`.
- Trial 2 is recording-complete. Trial 1 is receiver pre-roll and trial 3 is
  an accidental reset-only tail; do not treat the session-level `1/3` clean
  count as three attempted trials.
- Speed at audio start/spawn was `107.758/108.728 KIAS`; pre-spawn MAE was
  `9.464 KIAS` and within-`±5 KIAS` rate was zero.
- The response detector reported spawn-to-response `3.031 s` with throttle as
  trigger, but `active_control_at_spawn=true`.
- At audio end and spawn, throttle was `0.0`; at the detected response it was
  `0.173` while IAS had fallen to `104.154 KIAS`. This is plausibly speed-task
  control rather than a collision-avoidance response. Do not use `3.031 s` as
  validated avoidance latency.
- Trial gaze validity was `71.7%`, operational external `40.4%`, and gaze was
  already `OUTSIDE_VIEW` immediately before spawn.
- The first directly measured side episode after spawn was RIGHT from
  `+0.228` to `+0.977 s`, while the intruder approach was LEFT. There was no
  measured left-side episode. Do not claim intruder acquisition.
- The v29 speed range implementation is confirmed and remains frozen. The raw
  input response detector is not yet behaviorally frozen.
- Before main collection, separate recording completeness from behavioral
  response validity. At minimum, exclude `active_control_at_spawn=true` trials
  from primary response-latency analysis. Prefer a state/trajectory response
  criterion based on persistent aircraft-path change and improved predicted
  separation rather than raw throttle/pitch input alone.

## 2026-07-29 group-meeting video pilot

- Java session:
  `session_groupmeeting_demo_v29_20260729_145522`.
- X-Plane prefix:
  `logs/xplane/xplane_groupmeeting_demo_v29_20260729_145522`.
- Tobii file:
  `logs/tobii/session_xplane_tobii_20260729_145503_gaze.csv`.
- Final rows were STATE `2661`, INTRUDER `2661`, EVENT `172`, and gaze
  `30035`. The receiver and gaze logger both stopped cleanly.
- Trial `27` is receiver pre-roll. Trials `28` through `40` are 13 performed
  attempts: 11 recording-complete and 2 incomplete (`29` and `40`, both
  missing `TRIAL_END`).
- Trial `33` is the preferred presentation example. It is recording-complete,
  used a front approach and a `100 KIAS` task, and had planned/actual
  audio-to-spawn timing of `5.775/5.785 s`.
- Trial `33` IAS was `104.930 KIAS` at audio start and `100.826 KIAS` at
  spawn. Pre-spawn MAE was `2.564 KIAS`, the within-`5 KIAS` rate was
  `100%`, and `active_control_at_spawn=false`.
- Trial `33` Tobii validity was `86.5%`. Operational external viewing occupied
  `57.9%` of the trial. Gaze was on `VERTICAL_SPEED` immediately before spawn,
  with the first operational-external classification `0.546 s` after spawn.
- The raw-input detector reported a roll response `10.298 s` after spawn,
  which was `0.449 s` after `HAZARD_DETECTED`. This is more interpretable than
  an active-control trial but is still not proof of intruder acquisition.
- Trial `33` had no directly measured side-monitor episode; `7.1%` of the
  trial was inferred side viewing. Do not describe its first external-view
  transition as confirmed intruder fixation.
- Trial `40` is not suitable as a clean presentation result: it lacks
  `TRIAL_END`, had `active_control_at_spawn=true`, and gaze was already
  operational-external before spawn.
- If the video includes the full session, use the local-clock segment
  `14:58:21.511` through `14:58:43.224` for trial `33`. Video start time was
  not logged, so verify the crop against the visible reset/start/end actions.

## 2026-07-29 continuous six-trial session decision

- A session starts from the same saved final-approach condition at `10 NM`.
- One participant performs multiple sessions.
- The participant presses one session-start command only once.
- Each session runs six trials automatically without reloading the X-Plane
  situation between trials.
- Each session contains LEFT, RIGHT, and FRONT approaches exactly twice; only
  their order is randomized.
- After scenario closure, the next trial requires both a random `10~15 s`
  washout and a sustained aircraft-attitude stability gate.
- No catch trial is used. Interpret the protocol as repeated, expected traffic
  encounters with randomized onset timing, not as unexpected-intruder
  recognition.
- Preserve trial order and test for order-related pre-spawn external viewing
  and response-latency changes.
- Existing STATE logging already contains position, AGL, attitude, heading,
  airspeed, vertical speed, angular rates, and controls. Add spawn-time trial
  summary fields without changing the raw STATE CSV schema. Flap state remains
  a separate pending decision.
- The automatic session state machine, stability threshold, spawn-condition
  export, and multi-session speed-target balancing are design decisions, not
  yet implemented in v29.

## 2026-07-30 spawn flight-context logging v30

- Active Lua version is `260730_spawn_flight_context_v30`; task protocol
  remains `audio_task_v29`.
- Snapshot:
  `FLYWITHLUA_STUDY_INTEGRATED_260730_spawn_flight_context_v30.lua`.
- `INTRUDER_SPAWNED` now records exact ownship latitude/longitude,
  elevation/AGL, heading/pitch/roll, P/Q/R, IAS/TAS/vertical speed, control
  inputs, and throttle in event detail.
- Raw STATE/INTRUDER/EVENT CSV schemas are unchanged.
- `XPlaneSessionAnalysisMain` exports spawn flight context. New event detail is
  preferred; historical logs fall back to the nearest STATE row.
- `XPlaneBatchAnalysisMain` adds spawn-context columns to trial and clean-trial
  CSV exports.
- Flap state is intentionally not added.
- Java 21 compile, the v29 random-speed smoke test, historical v29 session
  regression, and batch-column verification passed.
- The repository Lua, v30 snapshot, and X-Plane runtime script hashes match.
  FlyWithLua still must be reloaded before v30 is active in X-Plane memory.
- The next behavioral classifier must separate `SPEED_TASK_CONTROL`,
  `AVOIDANCE_CANDIDATE`, and `AMBIGUOUS` using task-error direction,
  persistent trajectory change, predicted closest-approach improvement, gaze
  context, and manual video labels. Raw input threshold alone is insufficient.
- `INTRUDER_DETECTABLE_ONSET` based on calibrated screen projection/pixel size
  and dynamic `INTRUDER`, `RUNWAY`, and `EXTERNAL_OTHER` AOIs remain required
  follow-up tasks.

## 2026-08-03 v30 physical spawn-context check

- Session: `session_spawn_context_v30_20260803_150206`.
- X-Plane prefix:
  `logs/xplane/xplane_spawn_context_v30_20260803_150206`.
- FlyWithLua loaded `260730_spawn_flight_context_v30` without an error.
- One right-approach trial was `1/1 CLEAN` with STATE `290`, INTRUDER `290`,
  and EVENT `15` rows at analysis time.
- Planned/actual audio-to-spawn timing was `3.909/3.911 s`.
- The exact spawn event and Java summary agreed: AGL `565.859 m`, heading
  `325.030 deg`, pitch `-1.120 deg`, roll `-1.148 deg`, IAS `116.620 KIAS`,
  vertical speed `-0.465 m/s`, and controls/throttle were populated.
- Spawn flight-context logging is now physically confirmed. The next task is
  the automatic six-trial session state machine and stability gate.

## 2026-08-03 automatic six-trial session v31

- Active repository/runtime Lua version is
  `260803_auto_six_trial_session_v31`; task protocol is `audio_task_v31`.
- Snapshot:
  `FLYWITHLUA_STUDY_INTEGRATED_260803_auto_six_trial_session_v31.lua`.
- `Study Start Automatic Session` starts one six-trial session. The participant
  does not reset/start/end individual trials.
- Every session contains LEFT, RIGHT, and FRONT exactly twice in a newly
  shuffled order.
- The speed schedule contains 90/95/100/105/110 KIAS once each plus one
  randomly repeated target, then shuffles all six trials.
- The initial wait and each inter-trial washout are independently sampled from
  `U(10,15 s)`.
- A trial begins only after the minimum wait has elapsed and the aircraft has
  remained stable for 3 seconds: absolute roll <= 7 deg, roll range <= 3 deg,
  pitch range <= 3 deg, and heading range <= 5 deg.
- Automatic trial closure additionally waits until at least 2 seconds after
  the predicted conflict point and current horizontal separation is at least
  200 m. The intruder is then retired below the visible scene before washout.
- Participant display exposes only session/trial progress and completion; it
  does not expose direction, speed schedule, or remaining wait.
- `Study Stop Automatic Session` is recovery-only. If used during a trial, the
  current trial intentionally remains incomplete and must not be treated as a
  completed experimental trial.
- Raw STATE/INTRUDER/EVENT CSV schemas are unchanged. Session metadata is
  carried in event detail.
- Java session analysis and Tobii merge analysis recognize `audio_task_v31` as
  the current audio-anchored 3-10 second spawn protocol.
- Static verification passed: Java 21 compile, current-protocol random-speed
  smoke test, Python compile, and `git diff --check`.
- Repository Lua, v31 snapshot, and deployed X-Plane runtime Lua SHA-256:
  `92FADE00DC2BC9AC0BBD3960ED442D7064AD28CB1C8B23BCBC5AE1EC6EEEC9EE`.
- X-Plane `Log.txt` confirmed
  `[StudyIntegrated] script loaded: 260803_auto_six_trial_session_v31` with no
  FlyWithLua error after reload. The first full six-trial physical pilot passed
  on 2026-08-03; see the validation section below.

## 2026-08-03 v31 physical automatic-session validation

- Session: `session_auto_session_v31_20260803_152309`.
- Prefix: `logs/xplane/xplane_auto_session_v31_20260803_152309`.
- Receiver shutdown was clean: final EVENT rows include `RECEIVER_STOP` and
  `SESSION_STOP`, and UDP port 9100 was released.
- Automatic session duration was `212.320 s` from start marker to completion.
- Exactly six automatic reset/start/end cycles completed; all six trials were
  strict clean and each had one audio start/end, spawn, hazard detect/clear,
  response baseline/start/end, and minimum-distance event.
- Direction schedule was RIGHT, LEFT, RIGHT, LEFT, FRONT, FRONT: exactly two
  of each.
- Speed schedule was 90, 100, 105, 95, 90, 110 KIAS: all five targets once
  plus one repeated 90 KIAS target.
- Planned waits were all within 10-15 seconds. Actual waits extended when the
  stability gate required it; all recorded gate values passed their limits.
- Every automatic `TRIAL_END` occurred about 2.0 seconds after
  `MIN_DISTANCE_REACHED`, after `HAZARD_CLEARED`.
- Correct in-trial minimum horizontal distances were 24.277, 12.877, 7.549,
  8.617, 8.239, and 13.902 m.
- A post-trial metric contamination bug was found because retired-intruder
  washout rows retain the previous `trial_id`. `XPlaneSessionAnalysisMain` now
  bounds STATE/INTRUDER metrics to `TRIAL_START..TRIAL_END`, and the session
  minimum is derived from bounded trial summaries. The raw CSV is unchanged.
- `XPlaneRandomSpeedTaskAnalysisSmokeTest` now includes synthetic post-trial
  retirement rows and verifies that they do not affect sample counts or
  minimum separation. Java 21 compile, smoke test, and current-session
  reanalysis passed.
- This is a logging/state-machine pass, not yet a final experimental-design
  pass. Session AGL fell from about 534 m at automatic-session start to 75 m
  at completion; spawn AGL fell from 487.9 m in trial 1 to 59.3 m in trial 6. Trial 6
  commanded 110 KIAS while actual spawn IAS was 81.3 KIAS. Fixed random speed
  targets across the whole approach are therefore confounded with approach
  phase and require a design decision before main collection.
- Raw-input response latency also remains behaviorally unvalidated: one trial
  had `active_control_at_spawn=true`, and three reported latencies below 0.2 s,
  consistent with speed-task input rather than confirmed avoidance.
- Initial-condition interpretation correction: this first pilot did not begin
  from the saved 2100 ft scenario. It began after additional flight progress
  at about 1753 ft AGL, and the pilot reported an over-aggressive descent.
  Therefore it does not prove that the agreed 2100 ft saved start is
  insufficient. Repeat the same Java-only six-trial pilot from the exact saved
  scenario before changing altitude, speed targets, washout, or Lua logic.
- Saved-scenario diagnostic on 2026-08-03 used receiver prefix
  `xplane_auto_session_v31_2100ft_retest_20260803_154740`. The first STATE row
  was about 2045 ft AGL, confirming the saved condition is close to the stated
  2100 ft. The diagnostic unpause lasted about 8 seconds and ended near 1910
  ft AGL, so this file is preflight diagnostic only, not the actual repeat.
- Clean repeat procedure: load saved scenario, reload FlyWithLua, start Java
  receiver while paused, press `Study Start Automatic Session` while still
  paused, then unpause. Do not fly a separate pre-roll; the automatic 10-15 s
  wait and stability gate provide the required lead-in.

## 2026-08-03 exact-start v31 repeat result

- Session: `session_auto_session_v31_2100ft_clean_20260803_161422`.
- Prefix: `logs/xplane/xplane_auto_session_v31_2100ft_clean_20260803_161422`.
- Final rows: STATE `1653`, INTRUDER `1653`, EVENT `95`; shutdown recorded
  `RECEIVER_STOP` and `SESSION_STOP`.
- The automatic session completed all six trials and all six were strict clean.
- Direction order was LEFT, LEFT, RIGHT, FRONT, FRONT, RIGHT, giving two of
  each direction.
- Speed order was 95, 100, 105, 110, 90, 110 KIAS, satisfying the five-target
  plus one-repeat schedule.
- The first STATE sample was approximately `2066 ft AGL`; the sixth trial ended
  at approximately `957 ft AGL`. The previous uncontrolled dive did not recur.
- Whole-file attitude/vertical-speed bounds were pitch `-7.50..9.36 deg` and
  vertical speed approximately `-1143..1363 ft/min`, rather than the abnormal
  near-vertical dive seen in the invalid equipment/state run.
- Spawn IAS by trial was 99.970, 94.898, 98.646, 106.235, 107.849, and 91.478
  KIAS. Trials 5 and 6 were about 17.8 and 18.5 KIAS from their 90 and 110 KIAS
  targets because a 110 -> 90 -> 110 sequence was paired with only about
  5.9/5.7 seconds from audio start to spawn.
- Strict clean means event/logging completeness only. It does not mean that the
  speed task was achieved or that `PILOT_RESPONSE_START` is a valid avoidance
  onset.
- Spawn-to-response latencies were 1.413, 0.374, 7.887, 0.131, 0.043, and
  0.065 seconds. Four trials had active control at spawn, and the very short
  throttle-triggered responses remain confounded with the speed task.
- `ADVISORY_SHOWN/CLEARED` count zero is expected for the current
  intruder-spawn-anchored protocol and is not an incomplete-trial condition.
- Result: the six-trial state machine, balance, waits, stability gate, event
  pipeline, automatic close, and exact-start altitude envelope passed. Before
  main participant collection, the behavioral response criterion still needs
  to distinguish ongoing speed-task control from an avoidance maneuver.

## 2026-08-03 visual detectability gate v32

- Active Lua/version: `260803_visual_detectability_gate_v32`; protocol:
  `audio_task_v32`.
- Snapshot:
  `FLYWITHLUA_STUDY_INTEGRATED_260803_visual_detectability_gate_v32.lua`.
- Repository, snapshot, and deployed runtime Lua SHA-256:
  `7F6902AAA3480708CB0EC09523DD26B594FA5679850375AD6FCEC3C1681CEC71`.
- The validated v31 one-start/six-trial state machine, direction/speed balance,
  wait/stability gate, and automatic close are retained.
- Crossing target prediction was expanded from 320-560 m to 920-1100 m,
  configured time to conflict from 10-13 s to 16-20 s, and spawn extra path
  distance from 350 m to 500 m. Nominal intruder path-start distance is about
  1.4-1.7 km instead of about 0.9-1.0 km.
- Display calibration constants are fixed to the current X-Plane setup:
  5760x1080 and 122 deg horizontal FOV.
- Lua estimates intruder screen center and horizontal bounding span using a
  perspective proxy, ownship heading/pitch, and C172 reference dimensions
  (11.0 m wingspan, 8.3 m length). Roll correction is explicitly not yet
  applied and is logged as `roll_correction=ignored`.
- The provisional visual threshold is 20 px. It is a calibration candidate,
  not a proven human detection threshold.
- Once the intruder center is on screen and the estimated span first reaches
  20 px, Lua records exactly one `INTRUDER_VISUALLY_DETECTABLE` event with
  screen x/y, span, slant/horizontal distance, bearing/elevation, display/FOV,
  threshold, and ownship context.
- `INTRUDER_VISUALLY_DETECTABLE` is an operational detectability proxy. It must
  not be described as the participant's directly measured conscious detection.
- Java v32 response baseline/start now opens at
  `INTRUDER_VISUALLY_DETECTABLE`; hazard processing remains active from
  `INTRUDER_SPAWNED`. v31 and older spawn-anchored behavior is preserved.
- Response baseline detail now records `active_control_at_anchor` and the
  anchor-specific `active_control_at_detectability` or legacy
  `active_control_at_spawn` field. Throttle remains a valid response axis.
- v32 strict recording completeness requires exactly one detectability event
  in `INTRUDER_SPAWNED -> INTRUDER_VISUALLY_DETECTABLE -> TRIAL_END` order.
- Java session/batch analysis exports detectability geometry and
  audio/spawn/detectability/response latencies. Responses before the visual
  gate are labelled `RESPONSE_BEFORE_DETECTABILITY` instead of being used as a
  detectability-to-response latency.
- Tobii analysis v8 adds detectability-relative gaze windows and latencies.
  `visualize_tobii_gaze.py` now defaults to the detectability event; historical
  figures can still pass `--event ADVISORY_SHOWN` or `--event INTRUDER_SPAWNED`.
- Verification passed: Java 21 compile, auto-event detector smoke, v32 session
  analysis smoke, v31 backward analysis, Python compile, historical v29 Tobii
  backward analysis, batch export, and `git diff --check`.
- Physical FlyWithLua reload and LEFT/RIGHT/FRONT screen calibration are still
  required before the 20 px threshold is frozen or a six-trial v32 pilot is
  accepted.

# AGENTS.md

## 프로젝트 개요

이 프로젝트는 X-Plane 11과 Java 수신 프로그램을 연동하여 조종사의 반응 데이터를 수집하고, 이를 기반으로 향후 AI 조종사 모델 설계에 사용할 수 있는 실험 데이터셋과 분석 파이프라인을 구축하는 것을 목표로 한다.

현재 구현은 단순한 개념 정리 단계가 아니라, FlyWithLua 기반 X-Plane 스크립트와 Java UDP 수신기를 이용해 실제 CSV 로그를 생성하고, trial 단위로 hazard, advisory, pilot response를 자동 판정하는 단계까지 진행되어 있다.

## 현재 실행 구조

- X-Plane은 시뮬레이터 PC에서 실행된다.
- X-Plane 내부에서는 FlyWithLua 스크립트가 실행된다.
- Java 수신기는 별도 PC 또는 같은 네트워크의 Java 실행 PC에서 실행된다.
- FlyWithLua는 UDP 패킷을 Java 수신기로 전송한다.
- Java는 수신 데이터를 `logs/xplane` 아래 CSV로 저장한다.
- Java 분석 도구는 세션/트라이얼 단위 요약과 clean trial CSV export를 생성한다.
- 선택적으로 Java receiver가 연구실 ATC Simulator Server에 TCP client로 접속해 X-Plane ownship `STATE`를 서버 ICD의 `FDT` 패킷으로 동시 송신할 수 있다.

현재 네트워크 설정:

- X-Plane PC: `100.64.0.132`
- Java PC: `100.64.0.129`
- Java UDP 수신 포트: `9100`
- 연구실 ATC Simulator Server PC: `172.16.150.130`
- 연구실 ATC Simulator Server TCP 포트: `50000`

현재 사용 중인 통합 Lua 파일:

- `FLYWITHLUA_STUDY_INTEGRATED.lua`
- 현재 코드 버전: `260529_end_trial_guard_v20`
- 보관 스냅샷: `FLYWITHLUA_STUDY_INTEGRATED_260529_end_trial_guard_v20.lua`

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
- Java는 hazard 발생/해제에 따라 `ADVISORY_SHOWN`, `ADVISORY_CLEARED`를 자동 기록한다.
- Java는 advisory 이후 조종 입력 변화로 `PILOT_RESPONSE_START`, `PILOT_RESPONSE_END`를 자동 기록한다.
- Lua v18은 화면에 visual advisory 문구 `TRAFFIC ALERT`, `CHECK OUTSIDE`를 표시한다.
- Lua v20은 trial이 아직 닫히지 않은 상태에서 `Study End Trial`을 누르면 `TRIAL_END`를 기록하지 않고 `MANUAL_NOTE`로 조기 종료 거부 사유를 남긴다. 단, crossing intruder가 계속 active여도 `MIN_DISTANCE_REACHED`가 기록되고 visual advisory가 꺼진 상태면 종료를 허용한다.
- trial-level 분석과 batch 분석 CSV export가 구현되어 있다.
- Cessna 172/Skyhawk 중앙 모니터 기준 AOI 초안이 `resources/cessna_aoi_draft_260526.csv`에 있다.
- ATC 서버 연동 smoke test에서 `plt#PLT_XP_SMOKE` 등록(`REG/GRT`)과 synthetic X-Plane state 1개를 `FDT`로 변환 송신하는 데 성공했다.

## 주요 FlyWithLua 명령

현재 통합 Lua 스크립트는 다음 command와 macro를 등록한다.

- `flywithlua/study/reset_trial`
- `flywithlua/study/start_trial`
- `flywithlua/study/end_trial`
- `flywithlua/study/start_intruder_headon`
- `flywithlua/study/toggle_cloud`

현재 작업 흐름에서는 통합 Lua 파일 하나만 사용하는 것을 원칙으로 한다. 기존의 테스트 Lua 파일을 동시에 `Scripts` 폴더에 넣으면 UDP 전송이나 화면 표시가 중복될 수 있다.

## 데이터 계약

Java 수신기는 현재 다음 세 종류의 패킷을 기대한다.

- `STATE,...`
- `INTRUDER,...`
- `EVENT,...`

현재 최신 패킷은 앞쪽에 `trial_id`를 포함한다. Java 파서는 일부 구형 패킷도 `trial_id=0`으로 처리할 수 있게 되어 있지만, 새 실험은 최신 패킷 형식을 기준으로 한다.

CSV 스키마와 UDP 컬럼 순서는 임의로 바꾸지 않는다. 패킷 형식을 바꿔야 할 경우에는 Lua 송신부, Java 파서, CSV 로거, 분석 코드를 같은 작업 안에서 함께 수정한다.

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
X-Plane PC
    -> FlyWithLua UDP STATE / INTRUDER / EVENT
Java PC
    -> 기존 CSV 저장 유지
    -> 기존 자동 이벤트 검출 유지
    -> 옵션 사용 시 ATC Simulator Server로 STATE -> FDT 동시 송신
ATC Simulator Server PC
    -> PLT 클라이언트로 수신
```

일회성 서버 등록 테스트:

```powershell
java -cp build_atc_tmp com.example.ai.AtcServerConnectionTest 172.16.150.130 50000 PLT_XP
```

일회성 FDT 송신 smoke test:

```powershell
java -cp build_atc_tmp com.example.ai.AtcServerFdtSmokeTest 172.16.150.130 50000 PLT_XP_SMOKE xplane01
```

X-Plane receiver와 ATC bridge 동시 실행 예:

```powershell
java -cp build_atc_tmp com.example.ai.XPlaneReceiverMain 9100 logs\xplane\xplane_atc_test.csv session_atc_test --atc-host 172.16.150.130 --atc-port 50000 --atc-module PLT_XP --atc-fid xplane01 --atc-every 5
```

주의:

- `XPlaneReceiverMain`은 정상 실행되면 종료되지 않고 UDP 수신을 계속 기다리는 프로그램이다. PowerShell에서 멈춘 것처럼 보이는 것이 정상 동작이며, 종료는 `Ctrl+C`로 한다.
- 서버 PC에서 Simulator Server의 `Open` 버튼을 눌러야 `50000` 포트가 열린다.
- `Test-NetConnection`은 포트 확인에는 유용하지만, 서버 accept thread가 첫 줄을 기다리는 구조라 테스트 후 서버를 `Close`/`Open`해서 초기화하는 것이 안전하다.
- 현재 ATC 연동은 ownship `STATE`만 `FDT`로 보낸다. intruder를 서버 traffic으로 표시하려면 intruder lat/lon/alt 송신 또는 local 좌표 변환이 추가로 필요하다.

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

- `build_tmp`와 `logs/xplane`에 새 파일을 쓰는 작업이 OneDrive/권한 문제로 실패할 수 있다.
- clean trial 비율이 아직 낮다. 최근 batch 분석 기준 79개 trial 중 strict clean trial은 19개였다.
- v20에서 `Study End Trial` guard를 완화했다. 실제 X-Plane trial에서 clean trial 개선 효과는 아직 검증해야 한다.
- v18 visual advisory의 실제 화면 위치와 AOI 좌표는 실제 캡처 기준으로 재검증해야 한다.
- eye tracker/Tobii Spark 실시간 연동은 아직 구현되지 않았다.
- gaze CSV와 X-Plane/Java trial timeline 병합 분석은 다음 단계 작업이다.
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

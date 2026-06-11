# WORKLOG.md

## 현재 날짜

2026-05-29

## 현재 작업 상태

프로젝트 문서를 최신 코드 상태에 맞게 정리했다. 기존 기준 문서가 2026-05-07 상태에 머물러 있었고, 이후 진행 내용이 `*_updated(260519).md`, v18 Lua, AOI 관련 문서에 흩어져 있었다. 현재 기준 파일을 최신 v18 상태로 갱신하고, 같은 내용을 `260529` 날짜 스냅샷으로 저장하는 문서 관리 규칙을 명시했다.

갱신한 기준 파일:

- `Agents.md`
- `Project_Context.md`
- `Worklog.md`

새로 추가한 스냅샷:

- `Agents_updated(260529).md`
- `Project_Context_updated(260529).md`
- `Worklog_updated(260529).md`

## 문서 관리 규칙

앞으로 최신 상태는 항상 기준 파일 3개에 먼저 반영한다.

- `Agents.md`
- `Project_Context.md`
- `Worklog.md`

기준 파일을 갱신할 때는 같은 내용을 날짜 suffix가 붙은 스냅샷으로도 저장한다.

- `Agents_updated(YYMMDD).md`
- `Project_Context_updated(YYMMDD).md`
- `Worklog_updated(YYMMDD).md`

기존 날짜 스냅샷은 삭제하지 않는다. 코드나 실험 절차가 바뀌면 해당 작업이 끝난 같은 턴에서 문서도 갱신한다. 문서가 코드와 충돌하면 현재 코드를 먼저 확인하고, 문서를 코드 기준으로 수정한다.

Lua 파일도 같은 방식으로 관리한다. 활성 실행 파일은 `FLYWITHLUA_STUDY_INTEGRATED.lua`로 유지하고, Lua를 수정하면 `STUDY_SCRIPT_VERSION`을 갱신한 뒤 같은 내용을 `FLYWITHLUA_STUDY_INTEGRATED_YYMMDD_<short_description>_vNN.lua` 형식의 스냅샷으로 저장한다. 기존 Lua 스냅샷은 삭제하지 않는다. Lua 패킷 형식, command, event, scenario parameter가 바뀌면 Java 코드와 기준 문서도 같은 작업 안에서 함께 갱신한다.

## 현재 활성 구현

현재 활성 Lua:

- `FLYWITHLUA_STUDY_INTEGRATED.lua`
- version: `260529_end_trial_guard_v20`
- snapshot: `FLYWITHLUA_STUDY_INTEGRATED_260529_end_trial_guard_v20.lua`

현재 Java receiver:

- `src/com/example/ai/XPlaneReceiverMain.java`

현재 Java 분석 도구:

- `src/com/example/ai/XPlaneSessionAnalysisMain.java`
- `src/com/example/ai/XPlaneBatchAnalysisMain.java`

현재 AOI 초안:

- `resources/cessna_aoi_draft_260526.csv`

## 현재 구현 요약

### FlyWithLua

`FLYWITHLUA_STUDY_INTEGRATED.lua`는 다음 기능을 포함한다.

- UDP `STATE`, `INTRUDER`, `EVENT` 전송
- `trial_id` 기반 trial reset/start/end
- `sample_index`, `study_event_counter` 관리
- X-Plane pause 중 같은 `running_time_sec` 중복 전송 방지
- active trial 없는 intruder start 무시
- head-on intruder 중복 start 무시
- cloud deck 조건 toggle
- head-on intruder 테스트 시나리오
- randomized final-approach crossing intruder 시나리오
- visual advisory 화면 표시
- 조기 `Study End Trial` 입력 guard

현재 command/macro:

- `flywithlua/study/reset_trial`
- `flywithlua/study/start_trial`
- `flywithlua/study/end_trial`
- `flywithlua/study/start_intruder_headon`
- `flywithlua/study/toggle_cloud`

v18 visual advisory:

- 표시 문구: `TRAFFIC ALERT`, `CHECK OUTSIDE`
- 표시 조건: 수평 거리 `150 m` 이하 그리고 수직 분리 `30 m` 이하
- 해제 조건: 수평 거리 `200 m` 이상 또는 수직 분리 `50 m` 이상

v20 end-trial guard:

- `Study End Trial`을 눌렀을 때 randomized crossing arm, stabilization, visual advisory 중 하나라도 진행 중이면 `TRIAL_END`를 기록하지 않는다.
- crossing intruder는 `MIN_DISTANCE_REACHED`가 기록된 뒤에는 아직 active여도 trial end를 허용한다.
- head-on intruder는 마지막 수평 거리가 clear threshold 이상이면 아직 active여도 trial end를 허용한다.
- 대신 `MANUAL_NOTE`에 `ignored=end_before_trial_closed`와 상태 flag를 기록한다.
- 화면 상태는 `WAIT: TRIAL NOT CLOSED`로 표시한다.
- Java의 `PILOT_RESPONSE_END` 상태는 Lua가 직접 알 수 없으므로, 실제 clean trial 개선 효과는 X-Plane trial로 검증해야 한다.

### Java Receiver

Java receiver는 다음 기능을 포함한다.

- UDP `9100` 수신
- `STATE`, `INTRUDER`, `EVENT` parsing
- ownship state CSV 저장
- intruder CSV 저장
- event CSV 저장
- hazard 자동 판정
- advisory 자동 기록
- pilot response start/end 자동 판정
- event controller UI

### Java Analysis

`XPlaneSessionAnalysisMain`:

- session-level summary
- trial-level summary
- clean/incomplete trial 판정
- minimum distance 계산
- event timing 계산
- response latency 계산
- max pitch/roll/yaw/throttle input 계산
- first response axis 계산

`XPlaneBatchAnalysisMain`:

- 여러 Java receiver session을 한 번에 분석
- clean trial table 출력
- sessions/trials/clean_trials CSV export
- 구형 CSV 중 현재 schema와 맞지 않는 파일 제외

## 최근 검증 결과

기존 `build_tmp` 클래스파일로 batch analyzer를 실행했다.

명령:

```powershell
java -cp build_tmp com.example.ai.XPlaneBatchAnalysisMain logs\xplane
```

콘솔 요약은 정상 출력되었다.

결과:

- analyzed compatible sessions: `12`
- total trials: `79`
- strict clean trials: `19`
- incomplete trials: `60`
- clean rate: `24.1%`
- clean average `spawn->hazard`: `3.551 s`
- clean average `advisory->response`: `0.430 s`
- clean average `hazard_window`: `1.466 s`

대표 clean trial:

- `session_20260518_143658`: trial `2`, `3`, `4`
- `session_20260518_150208`: trial `2`, `5`, `8`, `10`
- `session_20260518_162806`: trial `4`, `7`, `8`, `9`, `10`, `12`
- `session_20260520_131859`: trial `3`, `4`, `7`

## 현재 환경 이슈

현재 작업 폴더가 OneDrive/reparse point/readonly 속성과 엮여 있어 새 파일 쓰기 문제가 있다.

확인된 문제:

- `javac --release 21 -d build_tmp src\com\example\ai\XPlane*.java`가 `build_tmp` 쓰기 단계에서 실패했다.
- `javac --release 21 -d C:\tmp\aisim_build_tmp ...`도 디렉터리 생성 권한 문제로 실패했다.
- `XPlaneBatchAnalysisMain`은 기존 클래스파일로 콘솔 요약까지 실행됐지만, 새 `xplane_batch_analysis_20260529_...csv` export 단계에서 `AccessDeniedException`이 발생했다.

따라서 다음 구현/분석 작업 전에 빌드 및 export 출력 위치 권한 문제를 해결해야 한다.

## 현재 알려진 실험 이슈

- clean trial 비율이 낮다.
- 많은 incomplete trial은 `Study End Trial`을 너무 빨리 눌러 `TRIAL_END`가 hazard/advisory/response closure보다 먼저 기록된 경우다.
- v20 end-trial guard는 아직 실제 X-Plane trial에서 검증하지 않았다.
- v18/v19 visual advisory의 실제 화면 위치는 X-Plane 캡처 기준으로 아직 재검증해야 한다.
- AOI `ADVISORY`, `RUNWAY_ZONE` 좌표는 실제 cockpit 캡처 기준 보정이 필요하다.
- Tobii Spark 실시간 연동과 gaze CSV 병합 분석은 아직 구현 전이다.
- `plane1_*` intruder dataref writable 동작은 현재 X-Plane 11 환경에 의존한다.

## 다음 작업 계획

1. OneDrive/readonly/reparse point로 인한 `build_tmp`, `logs/xplane` 쓰기 문제 해결
2. `AnalyzeXPlaneLogs.bat`가 현재 환경에서 Java 21 compile, batch analysis, CSV export까지 안정적으로 수행되는지 확인
3. X-Plane에서 v18 visual advisory 실제 표시 위치 캡처
4. `resources/cessna_aoi_draft_260526.csv`의 `ADVISORY`, `RUNWAY_ZONE` 좌표 보정
5. current v18 Lua로 clean trial 반복 수집
6. v20 end-trial guard가 incomplete trial을 줄이는지 검증
7. gaze CSV schema 확정
8. gaze CSV와 `STATE`/`INTRUDER`/`EVENT` CSV를 `session_id + trial_id + timestamp` 기준으로 병합하는 분석 코드 설계

## 실험 운용 메모

권장 trial 절차:

1. Java receiver 실행
2. X-Plane에서 통합 Lua가 로드됐는지 확인
3. `Study Reset Trial`
4. `Study Start Trial`
5. randomized crossing intruder 발생 대기
6. 조종 반응 수행
7. `HAZARD_CLEARED`, `ADVISORY_CLEARED`, `PILOT_RESPONSE_END`가 기록될 시간을 확보
8. `Study End Trial`
9. batch analyzer로 clean/incomplete reason 확인

`Study End Trial`을 너무 빨리 누르면 strict clean trial에서 제외될 가능성이 높다.

## 2026-05-29 Lua v19 end-trial guard update

조기 `Study End Trial` 입력으로 `TRIAL_END`가 hazard/advisory/response closure보다 먼저 기록되는 문제를 줄이기 위해 Lua guard를 추가했다.

수정 파일:

- `FLYWITHLUA_STUDY_INTEGRATED.lua`
- `FLYWITHLUA_STUDY_INTEGRATED_260529_end_trial_guard_v19.lua`

변경 내용:

- `STUDY_SCRIPT_VERSION`을 `260529_end_trial_guard_v19`로 갱신했다.
- `study_trial_can_end()`를 추가했다.
- `study_end_trial_guard_detail()`을 추가했다.
- `study_end_trial()`에서 trial 종료 가능 조건을 먼저 검사한다.
- 종료 불가 상태에서는 `TRIAL_END`를 기록하지 않고 `MANUAL_NOTE`만 기록한다.

종료를 막는 상태:

- `intruder_headon_active`
- `intruder_crossing_active`
- `random_crossing_armed`
- `crossing_stabilizing`
- `visual_advisory_active`

검증 필요:

- X-Plane에서 `Study End Trial`을 조기 입력했을 때 `WAIT: TRIAL NOT CLOSED`가 표시되는지 확인한다.
- event CSV에 `MANUAL_NOTE`와 `ignored=end_before_trial_closed`가 기록되는지 확인한다.
- intruder/advisory가 닫힌 뒤 다시 `Study End Trial`을 눌렀을 때 `TRIAL_END`가 정상 기록되는지 확인한다.

## 2026-05-29 Lua v20 end-trial guard update

v19 guard는 실제 X-Plane test에서 `TRIAL_END` 조기 기록은 막았지만, crossing intruder가 계속 active로 남아 있어 hazard/advisory/response가 모두 닫힌 뒤에도 `TRIAL_END`를 기록하지 못했다. 그 결과 대부분 trial이 `missing TRIAL_END`로 incomplete 처리되었다.

수정 파일:

- `FLYWITHLUA_STUDY_INTEGRATED.lua`
- `FLYWITHLUA_STUDY_INTEGRATED_260529_end_trial_guard_v20.lua`

변경 내용:

- `STUDY_SCRIPT_VERSION`을 `260529_end_trial_guard_v20`으로 갱신했다.
- `study_trial_can_end()`에서 `intruder_crossing_active` 자체를 종료 차단 조건으로 쓰지 않도록 변경했다.
- crossing trial은 `crossing_min_distance_reported == true`이면 아직 intruder가 active여도 종료를 허용한다.
- head-on trial은 마지막 수평 거리가 clear threshold 이상이면 종료를 허용한다.
- `random_crossing_armed`, `crossing_stabilizing`, `visual_advisory_active`는 계속 종료 차단 조건으로 유지한다.

검증 필요:

- hazard/advisory/response가 닫힌 뒤 `Study End Trial`을 누르면 `TRIAL_END`가 정상 기록되는지 확인한다.
- hazard/advisory 진행 중에는 여전히 `ignored=end_before_trial_closed`가 기록되는지 확인한다.
- analyzer에서 clean trial 비율이 v19보다 개선되는지 확인한다.

## 2026-06-09 ATC Simulator Server bridge update

연구실 ATC Simulator Server와 현재 X-Plane/Java receiver를 연동하기 위한 최소 TCP bridge를 추가했다.

분석한 서버 프로젝트:

- `Simulator Server - 20250520_DAS`

확인한 서버 구조:

- 서버는 TCP socket 기반이며 기본 포트는 `50000`이다.
- 서버 PC IP는 현재 `172.16.150.130`으로 확인했다.
- Java PC에서 `Test-NetConnection 172.16.150.130 -Port 50000` 결과 `TcpTestSucceeded : True`를 확인했다.
- 클라이언트는 접속 직후 `REG` 패킷을 보내고 `GRT` 응답을 받아야 한다.
- 서버의 패킷 구분자는 `ICD.java` 기준으로 `&`, `>`, `:`, `#`, `@`이다.

추가한 파일:

- `src/com/example/ai/AtcServerConnectionTest.java`
- `src/com/example/ai/AtcServerBridgeClient.java`
- `src/com/example/ai/AtcServerFdtSmokeTest.java`

수정한 파일:

- `src/com/example/ai/XPlaneReceiverMain.java`
- `src/com/example/ai/XPlaneStateReceiver.java`

검증 결과:

- `AtcServerConnectionTest`에서 `src:plt#PLT_XP>dst:svr>typ:reg` 송신 후 `src:svr>dst:PLT#PLT_XP>typ:grt` 응답을 받았다.
- `AtcServerFdtSmokeTest`에서 `plt#PLT_XP_SMOKE`로 등록 후 synthetic X-Plane state sample 1개를 ATC 서버 `FDT` 패킷으로 변환 송신했다.
- `AtcServerFdtSmokeTest` 결과:

```text
ATC bridge registered as plt#PLT_XP_SMOKE
Sent one FDT packet from a synthetic X-Plane state sample.
ATC FDT smoke test finished.
```

현재 구현 방향:

```text
X-Plane / FlyWithLua
    -> UDP 9100
Java XPlaneReceiverMain
    -> 기존 CSV 저장
    -> 기존 자동 이벤트 검출
    -> 옵션 사용 시 ATC Simulator Server로 STATE -> FDT 동시 송신
```

운영 실행 예:

```powershell
java -cp build_atc_tmp com.example.ai.XPlaneReceiverMain 9100 logs\xplane\xplane_atc_test.csv session_atc_test --atc-host 172.16.150.130 --atc-port 50000 --atc-module PLT_XP --atc-fid xplane01 --atc-every 5
```

주의 사항:

- `XPlaneReceiverMain`은 정상 실행되면 종료되지 않고 UDP 수신을 계속 기다린다. 콘솔이 멈춘 것처럼 보이는 것이 정상이다.
- 종료할 때는 PowerShell에서 `Ctrl+C`를 누른다.
- 서버 PC에서 Simulator Server의 `Open` 버튼을 눌러야 포트 `50000`이 열린다.
- `Test-NetConnection`은 포트 확인 후 서버 accept thread에 영향을 줄 수 있으므로, 포트 확인 뒤에는 서버를 `Close`/`Open`으로 재시작하는 것이 안전하다.
- 현재 bridge는 ownship `STATE`만 ATC `FDT`로 보낸다.
- intruder를 관제 서버 traffic으로 표시하려면 intruder lat/lon/alt 송신 또는 local 좌표 변환을 추가해야 한다.
- `out/production`에 기존 실행 중인 class 파일이 잡혀 있으면 컴파일이 실패할 수 있다. 현재 검증은 `build_atc_tmp`에서 수행했다.

다음 작업:

1. 기존 Java receiver를 종료한 뒤 `out/production`에 Java 21 target으로 재컴파일한다.
2. 실제 X-Plane trial 중 위 실행 명령으로 CSV 저장과 ATC FDT 송신이 동시에 되는지 확인한다.
3. Simulator Server GUI에서 `plt / PLT_XP` 연결과 `xplane01` 항공기 표시 여부를 확인한다.
4. ownship 표시가 안정화되면 intruder `ADS` 송신 설계를 진행한다.

## 2026-06-09 X-Plane + ATC bridge integrated test

`build_atc_tmp` 클래스파일로 `XPlaneReceiverMain`을 실행하고 실제 X-Plane/FlyWithLua UDP 데이터를 수신하면서 CSV 저장과 ATC bridge를 동시에 동작시켰다.

실행 명령:

```powershell
java -cp build_atc_tmp com.example.ai.XPlaneReceiverMain 9100 build_atc_tmp\xplane_atc_test.csv session_atc_test --atc-host 172.16.150.130 --atc-port 50000 --atc-module PLT_XP --atc-fid xplane01 --atc-every 5
```

생성 파일:

- `build_atc_tmp/xplane_atc_test.csv`
- `build_atc_tmp/xplane_atc_test_intruder.csv`
- `build_atc_tmp/xplane_atc_test_events.csv`

수집 결과:

- STATE rows: `1622`
- INTRUDER rows: `1622`
- EVENT rows: `166`
- trial count: `12`
- clean trials: `10 / 12`
- incomplete trial 3: `PILOT_RESPONSE_START`, `PILOT_RESPONSE_END` 없음
- incomplete trial 12: `TRIAL_END` 없음
- `--atc-every 5` 기준 예상 ATC FDT 송신 수: 약 `324`

판단:

- 기존 CSV 저장은 정상 동작했다.
- STATE와 INTRUDER row 수가 일치하므로 X-Plane UDP 수신/기록은 안정적으로 동작했다.
- ATC bridge는 실행 시 등록에 실패하면 receiver 시작 전에 예외로 종료되므로, 이번 통합 실행은 ATC 서버 등록 후 진행된 것으로 판단한다.
- 다만 관제 서버 화면에서 `xplane01` 표시 여부는 직접 확인하지 못했다. 다음 테스트에서는 서버 GUI의 `plt / PLT_XP` 연결 상태와 관제 화면 표시를 반드시 같이 확인해야 한다.

추가 확인:

- 같은 실행 명령으로 재실행했을 때 관제 화면에 ownship이 정상 표시됨을 확인했다.
- 재실행은 같은 `build_atc_tmp/xplane_atc_test*.csv` 파일과 같은 `session_atc_test`를 사용했기 때문에 이전 테스트 로그와 한 파일에 누적되었다.
- 재실행 구간에서는 trial을 새로 시작하지 않아 event CSV에는 `SESSION_START`, `RECEIVER_START`, `RECEIVER_STOP`, `SESSION_STOP`만 추가되었고, state CSV에는 `trial_id=0` 상태 row가 추가되었다.
- 다음 정식 테스트부터는 파일명과 session id를 `xplane_atc_test2`, `session_atc_test2`처럼 매번 바꿔서 실행해야 분석이 섞이지 않는다.

## 2026-06-10 Lua v21 + ATC intruder ADS update

A안으로 intruder를 관제서버 traffic으로 표시하기 위한 최소 구현을 진행했다. 기존 CSV 저장과 자동 event detector는 유지하고, 추가로 intruder 위경도/고도를 UDP/CSV/ATC ADS 경로에 연결했다.

수정 파일:

- `FLYWITHLUA_STUDY_INTEGRATED.lua`
- `src/com/example/ai/XPlaneIntruderSample.java`
- `src/com/example/ai/XPlaneIntruderLogger.java`
- `src/com/example/ai/AtcServerBridgeClient.java`
- `src/com/example/ai/XPlaneStateReceiver.java`
- `src/com/example/ai/XPlaneReceiverMain.java`
- `src/com/example/ai/AtcServerAdsSmokeTest.java`
- `Agents.md`
- `Project_Context.md`
- `Worklog.md`

Lua 변경:

- `STUDY_SCRIPT_VERSION`을 `260610_intruder_geo_atc_v21`로 갱신했다.
- intruder local 좌표와 ownship 위경도/고도를 이용해 근사 `intruder_latitude_deg`, `intruder_longitude_deg`, `intruder_elevation_m`을 계산한다.
- `INTRUDER` 패킷에 local position 뒤로 위경도/고도 3개 컬럼을 추가했다.
- 스냅샷 `FLYWITHLUA_STUDY_INTEGRATED_260610_intruder_geo_atc_v21.lua`를 생성했다.

Java 변경:

- `XPlaneIntruderSample`은 구형 INTRUDER와 v21 INTRUDER를 모두 파싱한다. 구형 패킷은 위경도/고도를 `NaN`으로 처리한다.
- intruder CSV 헤더와 row에 `intruder_latitude_deg`, `intruder_longitude_deg`, `intruder_elevation_m`을 추가했다.
- `AtcServerBridgeClient`에 `sendIntruder()`와 `typ:ads` 포맷 생성을 추가했다.
- `XPlaneStateReceiver`는 ownship ATC bridge와 intruder ATC bridge를 분리해서 운용한다.
- `XPlaneReceiverMain`은 ATC 옵션 사용 시 기본적으로 `PLT_XP` ownship FDT와 `SDP_XP` intruder ADS를 모두 등록한다.
- `--atc-intruder-module`, `--atc-intruder-fid`, `--atc-no-intruder` 옵션을 추가했다.
- `AtcServerAdsSmokeTest`를 추가했다.

컴파일 검증:

```powershell
javac --release 21 -d build_atc_tmp src\com\example\ai\XPlane*.java src\com\example\ai\AtcServer*.java
```

결과: 컴파일 성공.

다음 실제 X-Plane + ATC 테스트 명령:

```powershell
java -cp build_atc_tmp com.example.ai.XPlaneReceiverMain 9100 build_atc_tmp\xplane_atc_intruder_test.csv session_atc_intruder_test --atc-host 172.16.150.130 --atc-port 50000 --atc-module PLT_XP --atc-fid xplane01 --atc-intruder-module SDP_XP --atc-intruder-fid intruder01 --atc-every 5
```

테스트 전 주의:

- X-Plane FlyWithLua `Scripts` 폴더에는 최신 `FLYWITHLUA_STUDY_INTEGRATED.lua` 하나만 둔다.
- Simulator Server는 `Open` 상태여야 한다.
- 포트 확인용 `Test-NetConnection`을 실행했다면 서버를 `Close`/`Open`으로 다시 초기화한 뒤 테스트한다.
- 매 테스트마다 CSV 파일명과 session id를 새로 사용한다.

확인할 내용:

- PowerShell에 `ATC bridge registered as plt#PLT_XP`와 `ATC bridge registered as sdp#SDP_XP`가 모두 출력되는지 확인한다.
- 관제 서버 GUI에 `PLT_XP`, `SDP_XP` 두 클라이언트가 보이는지 확인한다.
- 관제 화면에 ownship `xplane01`과 intruder `intruder01`이 동시에 보이는지 확인한다.
- 새 intruder CSV의 위경도/고도 컬럼이 `NaN`이 아닌지 확인한다.

### 2026-06-10 intruder display follow-up

실제 테스트에서 `plt#PLT_XP`, `sdp#SDP_XP` 등록과 ownship `xplane01` 표시는 성공했지만, 관제 화면에 intruder `intruder01`은 표시되지 않았다.

확인 결과:

- `build_atc_tmp/xplane_atc_intruder_test_intruder.csv`에 intruder 위경도/고도는 정상 기록됐다.
- trial별 최소 수평 거리는 56 m, 13 m까지 내려간 구간이 있어 Lua/CSV 데이터 자체는 생성됐다.
- 서버 `CommunicationMananger` 기준 `ADS`는 PLT/MAP/DAS로 전달되지만, 현재 확인 중인 관제 화면이 `ADS` traffic을 직접 그리지 않을 가능성이 있다.

추가 수정:

- `AtcServerBridgeClient`에 intruder를 `ADS`와 함께 `FDT`로도 보낼 수 있는 옵션을 추가했다.
- `XPlaneReceiverMain`에 `--atc-intruder-fdt` 옵션을 추가했다.

다음 확인 명령:

```powershell
java -cp build_atc_tmp com.example.ai.XPlaneReceiverMain 9100 build_atc_tmp\xplane_atc_intruder_fdt_test.csv session_atc_intruder_fdt_test --atc-host 172.16.150.130 --atc-port 50000 --atc-module PLT_XP --atc-fid xplane01 --atc-intruder-module SDP_XP --atc-intruder-fid intruder01 --atc-intruder-fdt --atc-every 5
```

추가 확인 결과 `--atc-intruder-fdt`로도 관제 화면에 intruder가 표시되지 않았다. 서버 주석과 라우팅을 보면 관제 화면(CWP)까지 가는 정상 흐름은 `PLT -> SDP -> CWP`로 추정된다. ownship은 `PLT_XP`에서 보낸 FDT를 실제 SDP가 받아 CWP로 넘기기 때문에 보였고, intruder는 `SDP_XP`가 직접 보낸 데이터라 실제 SDP를 우회해 CWP 화면까지 가지 않은 것으로 판단된다.

추가 수정:

- `XPlaneReceiverMain`에 `--atc-intruder-source` 옵션을 추가했다.
- intruder를 별도 PLT source로 등록해 실제 SDP가 처리하도록 할 수 있다.

다음 확인 명령:

```powershell
java -cp build_atc_tmp com.example.ai.XPlaneReceiverMain 9100 build_atc_tmp\xplane_atc_intruder_plt_test.csv session_atc_intruder_plt_test --atc-host 172.16.150.130 --atc-port 50000 --atc-module PLT_XP --atc-fid xplane01 --atc-intruder-source plt --atc-intruder-module PLT_INTR --atc-intruder-fid intruder01 --atc-intruder-fdt --atc-every 5
```
## 2026-06-10 ATC intruder final display success

관제 화면에 intruder `intruder01`이 표시되는 최종 실행 방식을 확인했다.

확인 과정:

- `SDP_XP` 별도 연결 방식: `PLT_XP`, `SDP_XP` 등록은 됐지만 관제 화면에 `intruder01`은 표시되지 않았다.
- `--atc-intruder-fdt` 추가 방식: intruder를 `ADS`와 `FDT`로 같이 보내도 표시되지 않았다.
- `PLT_INTR` 별도 PLT 연결 방식: `PLT_XP`, `PLT_INTR` 등록은 됐지만 관제 화면에 `intruder01`은 표시되지 않았다.
- 같은 FID 테스트: intruder 발생 시점에 `xplane01` 항적이 재생성되는 듯한 현상이 관찰되어, intruder 데이터가 일부 표시 경로에 영향을 주는 것으로 판단했다.
- 최종 성공 방식: `PLT_XP` 한 연결에서 ownship과 intruder를 같이 송신하자 관제 화면에 `intruder01`이 표시됐다.

최종 결론:

```text
PLT_XP single TCP link
    ownship  -> fid:xplane01
    intruder -> fid:intruder01
```

필수 옵션:

- `--atc-intruder-on-ownship-link`
- `--atc-intruder-fdt`

정상 PowerShell 출력:

```text
ATC bridge registered as plt#PLT_XP
```

`sdp#SDP_XP` 또는 `plt#PLT_INTR`가 함께 등록되면 최종 성공 방식이 아니다.

복붙용 최종 실행 명령은 `ATC_XPLANE_RUN_COMMANDS_260610.md`에 별도 정리했다.

PowerShell/콘솔 인코딩 문제로 한글 설명이 깨져 보일 수 있어, 명령 복붙만 위한 ASCII 중심 문서 `ATC_XPLANE_COPYPASTE_COMMANDS_260610.md`도 추가했다.

최종 권장 명령:

```powershell
java -cp build_atc_tmp com.example.ai.XPlaneReceiverMain 9100 build_atc_tmp\xplane_atc_intruder_onlink_final.csv session_atc_intruder_onlink_final --atc-host 172.16.150.130 --atc-port 50000 --atc-module PLT_XP --atc-fid xplane01 --atc-intruder-fid intruder01 --atc-intruder-on-ownship-link --atc-intruder-fdt --atc-every 5
```

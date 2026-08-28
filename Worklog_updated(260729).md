# WORKLOG.md

## 현재 날짜

2026-07-29

## 현재 작업 상태

현재 NVIDIA Surround `5760x1080`과 Spark 한 대를 정밀 좌우 AOI가 아닌
coarse `center versus side external` 측정에 사용하는 pilot 단계이다.
표적자료 기반 hybrid classifier 구현은 완료됐고, 실제 X-Plane cockpit
위에서 수행할 무입력 음성 cue 도구와 자동 판정 분석기도 준비됐다.

현재 작업의 핵심:

- `tools/tobii/run_surround_cockpit_validation.ps1` 구현
- `tools/tobii/analyze_surround_cockpit_validation.py` 구현
- 참가자 keypress 없이 한국어 음성 cue와 정답 timestamp CSV 생성
- 중앙 계기 AOI, center outside, 좌우 external을 한 run에서 검증
- synthetic end-to-end `PILOT_PASS` 확인
- 실제 X-Plane cockpit 1차 pilot은 timing 진단용으로 보존
- 동기식 음성 runner v2 물리 pilot `PILOT_PASS`
- 짧은 natural intruder diagnostic 2 trial 완료
- spawn 기준 분석 열 추가
- Lua/Java v26 표준 task audio와 audio event 구현 완료
- Lua/분석 v27 audio 기준 `3~10초` spawn 구현 및 실측 완료
- spawn 기준 Java response detector v1 구현·synthetic 검증 완료
- Lua/분석 v29 `90~110 KIAS`, `5 KIAS` 간격 무작위 속도 과제 구현 완료

Lua는 v29로 갱신되어 최소 trial 상태 정책을 유지하면서 trial마다
`90/95/100/105/110 KIAS` 중 하나를 균등 무작위로 선택하고 trial 시작
3초 뒤 해당 계기 유지 음성을 자동 재생한다. audio start를 기준으로 독립적인
`U(3,10초)` 뒤 intruder를 생성한다. 내부 logging/guard는 유지하며,
v27 timing pilot에서 planned/actual 오차 `3~20 ms`를 확인했다.
Java response 기준도 spawn 전 1초 baseline과 0.25초 지속 변화 방식으로
변경했으며, 다음은 새 receiver를 이용한 실제 1회 trial 검증이다.

갱신한 기준 파일:

- `Agents.md`
- `Project_Context.md`
- `Worklog.md`

새로 추가한 스냅샷:

- `Agents_updated(260729).md`
- `Project_Context_updated(260729).md`
- `Worklog_updated(260729).md`

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
- version: `260729_revised_speed_task_v29`
- snapshot: `FLYWITHLUA_STUDY_INTEGRATED_260729_revised_speed_task_v29.lua`
- UDP target: `127.0.0.1:9100`

현재 Java receiver:

- `src/com/example/ai/XPlaneReceiverMain.java`

현재 Java 분석 도구:

- `src/com/example/ai/XPlaneSessionAnalysisMain.java`
- `src/com/example/ai/XPlaneBatchAnalysisMain.java`

현재 AOI 기준:

- `resources/cessna_instrument_aoi_260710.csv`
- visualization background: `AOI그림.png`

현재 Tobii 도구:

- `tools/tobii/session_xplane_tobii_logger.py`
- `tools/tobii/analyze_xplane_tobii_session.py`
- `tools/tobii/visualize_tobii_gaze.py`
- `tools/tobii/run_surround_cockpit_validation.ps1`
- `tools/tobii/analyze_surround_cockpit_validation.py`
- `tools/audio/generate_standard_task_audio.ps1`
- `tools/audio/generate_speed_task_audio_set.ps1`

현재 원본 로그 위치:

- X-Plane/Java: `logs/xplane`
- Tobii: `logs/tobii`

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
- participant 화면에는 최소 trial 상태만 표시하고 internal visual-advisory는 비표시
- 조기 `Study End Trial` 입력 guard
- placeholder `FINAL_APPROACH_GATE_ENTERED` 제거

현재 command/macro:

- `flywithlua/study/reset_trial`
- `flywithlua/study/start_trial`
- `flywithlua/study/end_trial`
- `flywithlua/study/start_intruder_headon`
- `flywithlua/study/toggle_cloud`
- `flywithlua/study/toggle_operator_overlay`
- `flywithlua/study/test_task_audio`

현재 코드에 남아 있는 internal visual advisory:

- 이전 표시 문구: `TRAFFIC ALERT`, `CHECK OUTSIDE`(v23 participant trial에서는 숨김)
- 표시 조건: 수평 거리 `150 m` 이하 그리고 수직 분리 `30 m` 이하
- 해제 조건: 수평 거리 `200 m` 이상 또는 수직 분리 `50 m` 이상

v20에서 도입되어 v23에도 유지되는 end-trial guard:

- `Study End Trial`을 눌렀을 때 randomized crossing arm, stabilization, visual advisory 중 하나라도 진행 중이면 `TRIAL_END`를 기록하지 않는다.
- crossing intruder는 `MIN_DISTANCE_REACHED`가 기록된 뒤에는 아직 active여도 trial end를 허용한다.
- head-on intruder는 마지막 수평 거리가 clear threshold 이상이면 아직 active여도 trial end를 허용한다.
- 대신 `MANUAL_NOTE`에 `ignored=end_before_trial_closed`와 상태 flag를 기록한다.
- 내부 상태 문자열은 `WAIT: TRIAL NOT CLOSED`로 유지하지만 participant trial에는 그리지 않는다.
- Java의 `PILOT_RESPONSE_END` 상태는 Lua가 직접 알 수 없으므로, 실제 clean trial 개선 효과는 X-Plane trial로 검증해야 한다.

현재 v28 구현:

- audio start 이후 독립적인 `U(3,10초)` 뒤 intruder를 생성한다.
- trial 상태와 2.5초 end 거부 알림만 남기고 audio countdown/debug/advisory는 participant 화면에서 숨긴다.
- trial마다 `90/95/100/105/110 KIAS` 중 하나를 균등 무작위로 선택하고 trial 시작 3초 뒤 해당 WAV를 재생한다.
- `TRIAL_START`와 `TASK_COMMAND_AUDIO_START/END` detail에 `target_speed_kias`와 선택 WAV를 기록한다.
- 현재 response detector는 `INTRUDER_SPAWNED` 기준이며, spawn 전 1초 평균 대비 임계치 변화가 0.25초 지속될 때 최초 통과 시점을 기록한다.
- 새 no-alert trial은 자동 `ADVISORY_SHOWN/CLEARED`를 만들지 않고 내부 위험창을 `HAZARD_DETECTED/CLEARED`로 기록한다.

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

최신 검증 pair:

- Java: `build_atc_tmp/xplane_self_tobii_20260720_142508*`
- Tobii: `logs/tobii/session_xplane_tobii_20260720_142554_gaze.csv`

수집 결과:

- STATE/INTRUDER: 각 `1,568` row
- gaze: `19,408` row
- front valid gaze: `14,780` (`76.2%`)
- timeline overlap: `319.518 s`
- total trial: `11`
- 기존 strict clean: `8`
- incomplete: `3`
- clean 평균 advisory-to-response: `0.958 s`

비행조건 재검토:

- spawn IAS: `119.8~148.9 KIAS`, 평균 `140.8 KIAS`
- spawn heading: `323.9~325.6°`, 평균 `324.9°`
- spawn AGL: `101~1,864 ft`
- spawn VS: `-1,014~-44 fpm`
- trial 1 시작 AGL은 약 `1,962 ft`, trial 11 spawn은 약 `101 ft`였다.

따라서 기존 11개 trial은 동일 조건 반복이 아니라 한 번의 연속 강하 중 서로 다른 비행상태의 pilot trial이다. strict clean은 event sequence 완결성만 의미한다.

단위 확인:

- CSV의 `ias_mps`는 실제로 KIAS이다.
- Lua source dataref `sim/flightmodel/position/indicated_airspeed`의 X-Plane 단위가 `kias`임을 로컬 `DataRefs.txt`로 확인했다.
- 이 값에는 m/s-to-knot 변환을 다시 적용하지 않는다.

## 현재 환경 이슈

과거 OneDrive/reparse point/readonly 상태에서 `build_tmp`와 export 쓰기 실패가 있었다. 최신 same-PC 실험에서는 `build_atc_tmp`, `logs/xplane`, `logs/tobii` 출력이 생성됐으므로 현재 1단계 설계의 선행 blocker는 아니다.

확인된 문제:

- `javac --release 21 -d build_tmp src\com\example\ai\XPlane*.java`가 `build_tmp` 쓰기 단계에서 실패했다.
- `javac --release 21 -d C:\tmp\aisim_build_tmp ...`도 디렉터리 생성 권한 문제로 실패했다.
- `XPlaneBatchAnalysisMain`은 기존 클래스파일로 콘솔 요약까지 실행됐지만, 새 `xplane_batch_analysis_20260529_...csv` export 단계에서 `AccessDeniedException`이 발생했다.

문제가 재발하면 원본 로그를 수정하지 말고 writable local build/output 경로를 사용한다.

## 현재 알려진 실험 이슈

- participant 화면은 최소 trial 상태만 남기도록 분리됐으며 audio/intruder countdown과 visual advisory는 숨긴다.
- current Java response detector가 `ADVISORY_SHOWN`을 기준으로 하므로 spawn 이후의 조기 회피를 놓치거나 지연값을 짧게 만든다.
- 한 번의 연속 접근 중 여러 trial을 수행해 AGL, IAS, 강하율 조건이 서로 다르다.
- 현재 `ias_mps` header 이름은 실제 KIAS 의미와 맞지 않는다.
- strict clean과 stabilized flight-condition validity가 분리되지 않았다.
- 표준 음성 유지과제와 task event는 구현됐지만 audio start 기준 3~10초 독립 지연은 아직 구현되지 않았다.
- Spark/Surround는 현재 operator/setup에서 coarse center/side external pilot만 통과했으며 참가자 간·정밀 좌표 타당성은 미확정이다.
- `UNTRACKED_OR_OFF_DISPLAY`에서 side gaze와 tracker loss를 구분할 수 없다.
- distant intruder의 객관적 detectability onset과 dynamic runway/intruder AOI는 아직 구현 전이다.
- `plane1_*` intruder dataref writable 동작은 현재 X-Plane 11 환경에 의존한다.

## 다음 작업 계획

1. flap과 전용 RKSI runway 34 저장상황을 확정하고 1단계 pilot envelope를 동결
2. NVIDIA Surround + Spark 한 대의 15점 3모니터 feasibility test
3. 표준 녹음 WAV, task event, participant 화면 숨김 구현
4. audio start 이후 3~10초 delay와 spawn 기준 response detector 구현
5. recording/event complete, flight-condition-valid, outcome 판정 분리
6. 먼 거리 intruder와 objective detectability threshold 구현
7. runway/intruder dynamic AOI 구현
8. 3~5회 pilot validation
9. protocol 동결 후 본실험

## 실험 운용 메모

현재 v22 검증용 절차:

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

다음 본실험 절차는 현재 v22 검증 절차와 다르다. 매 trial 동일 저장상황을 다시 불러오고, 표준 audio task와 독립적인 3~10초 delay를 거쳐 한 번의 intruder encounter만 수행한다. 구현 전까지 이 계획을 현재 기능으로 설명하지 않는다.

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

## 2026-06-11 Git update on ver8

세션 복구 후 최신 문서를 확인해 지금까지의 X-Plane/ATC 작업 내용을 정리했고, Git 반영을 진행했다.

먼저 `.gitignore`에 다음 generated output 제외 규칙을 추가했다.

- `*.class`
- `out/`
- `build_tmp/`
- `build_atc_tmp/`
- `logs/`
- `slprj/`
- `j6dof-temp/`
- `desktop.ini`

그다음 X-Plane/ATC 관련 파일만 선별해 local `ver8` 브랜치에 commit하고 GitHub `origin/ver8`로 push했다.

반영 commit:

```text
c6bbb32 Add X-Plane ATC bridge integration
```

포함한 주요 파일:

- `FLYWITHLUA_STUDY_INTEGRATED.lua`
- `FLYWITHLUA_STUDY_INTEGRATED_260529_end_trial_guard_v19.lua`
- `FLYWITHLUA_STUDY_INTEGRATED_260529_end_trial_guard_v20.lua`
- `FLYWITHLUA_STUDY_INTEGRATED_260610_intruder_geo_atc_v21.lua`
- `src/com/example/ai/XPlane*.java`
- `src/com/example/ai/AtcServer*.java`
- `ATC_XPLANE_RUN_COMMANDS_260610.md`
- `ATC_XPLANE_COPYPASTE_COMMANDS_260610.md`
- `Agents.md`
- `Project_Context.md`
- `Worklog.md`
- `resources/cessna_aoi_draft_260526.csv`
- `AnalyzeXPlaneLogs.bat`

포함하지 않은 항목:

- `.class` 파일
- `build_tmp`, `build_atc_tmp`, `logs`
- 논문/IRB/PowerPoint/zip/PDF 등 연구 문서 파일

## 2026-06-11 Existing AI simulation class update

IntelliJ에서 파란색으로 보이던 tracked Java class 수정분도 추가로 확인했다. 이 변경은 X-Plane/ATC receiver가 아니라 기존 AI simulation과 Simulink UDP bridge 쪽 변경이다.

추가/수정 대상:

- `src/com/example/ai/AISimulation.java`
- `src/com/example/ai/Agent.java`
- `src/com/example/ai/Aircraft.java`
- `src/com/example/ai/SimulationGUI.java`
- `src/com/example/ai/SimulinkCommandSender.java`
- `src/com/example/ai/SimulinkStateReceiver.java`
- `src/flight/analysis/CalculateDWC.java`

주요 변경 내용:

- `Aircraft`를 Simulink state 수신에 맞춰 position, velocity, attitude, target command를 보관할 수 있게 확장했다.
- `SimulationGUI`에 optional Simulink command sender/state receiver 연결 구조를 추가했다.
- `SimulinkCommandSender`는 aircraft command를 UDP `CMD` packet으로 전송한다.
- `SimulinkStateReceiver`는 UDP `STATE` packet을 받아 aircraft state를 갱신한다.
- `Agent`는 장애물을 전방 시야에 있을 때만 인식하도록 바뀌었고, command target과 target altitude/speed를 함께 갱신한다.
- `AISimulation`의 초기 aircraft 위치와 속도 조건을 조정했다.

주의:

- `.idea/vcs.xml`의 `j6dof-temp` Git mapping 변경은 코드 변경이 아니므로 이번 commit 대상에서 제외한다.
- untracked 문서/논문/실험 보조 파일은 많지만, 요청 전까지 Git에 올리지 않는다.

## 2026-06-22 X-Plane/ATC 재검증

실제 X-Plane/ATC 재검증을 진행했다.

처음 실행에서는 PowerShell 명령을 잘못 붙여 넣어 최종 옵션이 누락됐다. 실행된 명령은 `--atc-module PLT_XP`에서 끝났고, 뒤쪽의 `--atc-fid`, `--atc-intruder-fid`, `--atc-intruder-on-ownship-link`, `--atc-intruder-fdt`, `--atc-every`가 Java에 전달되지 않았다.

그 결과 출력은 다음과 같았다.

```text
ATC intruder bridge: 172.16.150.130:50000 module=SDP#SDP_XP fid=intruder01 every=5 fdt=false ownshipLink=false
ATC bridge registered as plt#PLT_XP
ATC bridge registered as sdp#SDP_XP
```

이 출력은 최종 성공 방식이 아니라 예전에 실패했던 `SDP_XP` 별도 연결 방식이다. 이 상태에서는 관제 화면에 ownship `xplane01`만 보이고 intruder `intruder01`이 표시되지 않는다.

앞으로 실행 명령 제시 규칙:

- 긴 Java receiver 명령은 한 줄 복붙 명령을 기본으로 제시하지 않는다.
- 반드시 PowerShell 백틱 줄바꿈 버전을 우선 제시한다.
- 각 옵션이 한 줄씩 보이게 하여 뒤쪽 옵션 누락을 방지한다.
- 줄 끝 백틱 `` ` ``이 빠지면 다음 줄 옵션이 실행 명령에 포함되지 않으므로 실행 전 확인한다.
- 정상 조건은 `ATC bridge registered as plt#PLT_XP` 하나만 출력되고, 설정에 `fdt=true ownshipLink=true`가 보이는 것이다.
- `sdp#SDP_XP` 또는 `plt#PLT_INTR`가 등록되면 최종 성공 방식이 아니다.
- `Connection refused`가 나오면 ATC 서버 PC에서 Simulator Server `Open` 상태와 포트 `50000`을 먼저 확인한다.

재검증 성공 실행은 `test2` 파일명으로 진행했다.

파일:

- `build_atc_tmp/xplane_atc_intruder_onlink_260622_test2.csv`
- `build_atc_tmp/xplane_atc_intruder_onlink_260622_test2_intruder.csv`
- `build_atc_tmp/xplane_atc_intruder_onlink_260622_test2_events.csv`

수집 결과:

- STATE rows: `2051`
- INTRUDER rows: `2051`
- EVENT rows: `212`
- intruder 위경도/고도: 전 row 정상, `NaN` 없음
- strict clean trial: `7 / 17`
- clean trials: `9`, `10`, `11`, `16`, `18`, `19`, `20`

clean trial 수치:

- 평균 최소 수평거리: `10.4 m`
- 평균 `spawn->hazard`: `6.730 s`
- 평균 `advisory->response`: `0.684 s`
- 평균 `hazard_window`: `2.444 s`

Incomplete 원인 요약:

- trial `7`, `13`, `15`, `17`: `PILOT_RESPONSE_START`/`PILOT_RESPONSE_END` 누락
- trial `12`, `14`: `TRIAL_END`가 `PILOT_RESPONSE_END`보다 먼저 기록
- trial `21`: `TRIAL_END` 누락
- trial `6`, `8`: 초기/재시작 또는 intruder 발생 전 구간이 섞인 trial로 판단

중요한 분석 도구 이슈:

- `XPlaneSessionAnalysisMain`은 v21 intruder CSV의 geo 컬럼 추가를 아직 반영하지 못한다.
- 현재 analyzer는 intruder CSV 컬럼을 고정 index로 읽어서 `horizontal_distance` 대신 다른 컬럼을 읽을 수 있다.
- 그래서 analyzer 공식 출력의 `Min horiz dist`가 음수로 표시되는 등 거리 통계가 잘못될 수 있다.
- event 기반 clean/incomplete 판정은 사용할 수 있지만, 거리 통계는 CSV 헤더 기준으로 읽도록 수정해야 한다.

다음 작업:

1. `XPlaneSessionAnalysisMain`의 intruder CSV parsing을 헤더 기반으로 수정한다.
2. `XPlaneBatchAnalysisMain` export 거리 통계도 v21 CSV 기준으로 검증한다.
3. 같은 방식으로 5~10 trial을 추가 수집해 clean trial 비율을 다시 확인한다.
4. Tobii Spark 도착 전까지 AOI 좌표와 visual advisory 위치를 실제 화면 캡처 기준으로 보정한다.

## 2026-06-22 Analyzer v21 intruder CSV fix

재검증 데이터 분석 중 `XPlaneSessionAnalysisMain`이 v21 intruder CSV를 잘못 읽는 문제를 수정했다.

문제:

- v21 intruder CSV는 `intruder_z` 뒤에 `intruder_latitude_deg`, `intruder_longitude_deg`, `intruder_elevation_m` 컬럼이 추가됐다.
- 기존 analyzer는 `horizontal_distance`, `vertical_separation`을 고정 index로 읽고 있었다.
- 그 결과 analyzer 공식 출력에서 `Min horiz dist`가 음수로 표시되는 등 거리 통계가 잘못 나왔다.

수정 파일:

- `src/com/example/ai/XPlaneSessionAnalysisMain.java`
- `src/com/example/ai/XPlaneBatchAnalysisMain.java`
- `Project_Context.md`
- `Worklog.md`
- `Agents.md`

변경 내용:

- `XPlaneSessionAnalysisMain`에 CSV header를 보존하는 `CsvTable` 구조를 추가했다.
- intruder 분석은 `horizontal_distance`, `vertical_separation`, `sim_time_s` 컬럼을 header name으로 찾아 읽도록 변경했다.
- header가 없는 구형 구조를 고려해 기존 index fallback도 유지했다.
- session-level response latency가 sim time reset 때문에 음수가 되면 `n/a`로 표시하도록 했다.
- `XPlaneBatchAnalysisMain`은 `xplane_session_...csv` prefix만 허용하던 제한을 완화해 `xplane_atc_...csv` 같은 receiver output도 직접 분석할 수 있게 했다.

검증 명령:

```powershell
javac --release 21 -d build_atc_tmp src\com\example\ai\XPlane*.java src\com\example\ai\AtcServer*.java
java -cp build_atc_tmp com.example.ai.XPlaneSessionAnalysisMain build_atc_tmp\xplane_atc_intruder_onlink_260622_test2.csv
java -cp build_atc_tmp com.example.ai.XPlaneBatchAnalysisMain build_atc_tmp\xplane_atc_intruder_onlink_260622_test2.csv
```

검증 결과:

- 컴파일 성공
- session analyzer의 최소 수평거리 출력이 정상값으로 변경됨
- batch analyzer가 `xplane_atc_intruder_onlink_260622_test2.csv` 단일 파일을 직접 분석함
- batch 결과: total `17`, clean `7`, incomplete `10`, clean rate `41.2%`
- clean 평균 `spawn->hazard`: `6.730 s`
- clean 평균 `advisory->response`: `0.684 s`
- clean 평균 `hazard_window`: `2.444 s`
- clean 평균 최소 수평거리: `10.406 m`
- clean 평균 최소 수직분리: `11.275 m`

다음 작업:

1. 수정된 analyzer를 Git `ver8`에 commit/push한다.
2. 새 analyzer 기준으로 다음 X-Plane/ATC 반복 trial을 분석한다.
3. pilot response 누락 trial을 줄이기 위해 response threshold와 운용 절차를 검토한다.

## 2026-07-06 CWP 라벨 이슈와 부대 적용 논의 정리

ATC/CWP 화면 캡처를 확인했다.

확인한 파일:

- `xplane화면캡처.png`
- `충돌상황캡처.png`
- `확대화면캡처.png`

일반 배율에서는 ownship과 intruder가 근접할 때 라벨과 숫자 블록이 겹쳐 잘 보이지 않았다. 확대 화면에서는 겹침이 상당히 완화됐고, `xp01`/`in01`처럼 짧은 flight id를 쓰면 가독성이 더 좋아질 수 있음을 확인했다.

`Simulator Server - 20250520_DAS` 폴더 전체를 검색했다.

검색 결과:

- 서버 운영 GUI와 통신 중계 코드는 있음
- CWP 화면 렌더링 코드는 없음
- `paintComponent`, `drawString`, `Graphics2D`, radar display panel, `Label[1]` 렌더링 코드 없음
- `CommunicationMananger.java`와 `ICD.java`에는 `cwp`, `FDT`, `ADS`, `dst:cwp#CWP1` 같은 통신 규격만 있음

결론:

- 현재 폴더 안의 서버 프로젝트에서는 라벨 겹침을 근본 수정할 수 없다.
- 근본 수정은 실제 CWP 클라이언트 소스에서 해야 한다.
- 임시 완화는 `--atc-fid xp01`, `--atc-intruder-fid in01`처럼 짧은 FID를 쓰는 방식이다.

라벨 완화 테스트용 명령을 PowerShell 백틱 줄바꿈 형식으로 정리했다. 앞으로 긴 Java 실행 명령은 계속 한 줄 복붙이 아니라 줄바꿈 형식으로 제시한다.

CWP 확대 화면 숫자 의미도 정리했다.

- `xp01`, `in01`: flight id
- `013`, `019`: 고도, 약 hundreds of feet 표시
- `108`, `074`: 속도, knot
- `1200`: squawk
- `-790FPM`, `-659FPM`: 수직속도
- `301`, `130`: heading
- `Preventive!`: conflict alert 단계
- `-`, `-------`, `----`: 현재 bridge가 보내지 않는 route/clearance 계열 필드로 판단

부대 적용 방향에 대해서도 논의했다.

현재 작업은 다음처럼 정의할 수 있다.

```text
X-Plane 기반 조종사 반응 데이터 수집 및 ATC 연동 시뮬레이션 플랫폼
```

부대 적용 관점에서는 X-Plane 자체가 목적이 아니다. 핵심은 조종사 반응 데이터 수집/분석 메커니즘을 인가된 군 훈련 시뮬레이터 환경에 이식하는 것이다.

보안상 X-Plane, FlyWithLua, 외부 Java 프로그램, Tobii SDK, 네트워크 연동은 부대 내부망에서 바로 운용하기 어려울 수 있다. 따라서 부대 보고에서는 "AI가 조종사를 평가한다"보다 "교관의 디브리핑을 보조하는 데이터 기반 분석 도구"로 설명하는 것이 적절하다.

KAI 마린온 조종사 시뮬레이터가 부대 훈련에 이미 사용되고 있다는 점은 긍정적이다. 해당 시뮬레이터가 상태 데이터, 조종 입력, 이벤트, 로그 export 또는 TCP/UDP 인터페이스를 제공한다면 X-Plane/FlyWithLua 입력부를 KAI 시뮬레이터 입력부로 대체할 수 있다.

보고 논리:

```text
기존 시뮬레이터 훈련을 단순 시간 이수형 훈련에서 행동 데이터 기반 평가·디브리핑 훈련으로 전환한다.
```

즉, 조종사가 마린온 시뮬레이터에서 훈련할 때 특정 비정상 상황, 충돌위험 상황, 기상 악화, 관제 지시, 접근/착륙 중 판단 상황에서 언제 인지하고 어떤 조작을 했는지 기록한다. 이후 숙련 조종사 기준 모델 또는 SOP 기반 기준 모델과 비교해 디브리핑 자료로 제공한다.

초기에는 AI 평가 시스템으로 표현하지 않는다. 다음 표현을 우선 사용한다.

```text
숙련 조종사 또는 SOP 기반 기준 모델과 비교하는 데이터 기반 디브리핑 보조 도구
```

다음 확인 사항:

1. KAI 마린온 시뮬레이터의 데이터 export 가능 항목 확인
2. 실시간 TCP/UDP 또는 사후 로그 분석 가능 여부 확인
3. 외부 분석 PC 연결 및 프로그램 설치 가능 여부 확인
4. Tobii Spark 반입/SDK 설치 가능성 확인
5. 부대 보안심사 및 장비 인가 절차 확인
6. CWP 클라이언트 소스 위치 확인

## 2026-07-08 Tobii Pro Spark SDK 연결 및 gaze stream 성공

Tobii Pro Spark 장비가 도착했고, Eye Tracker Manager에서 calibration을 완료했다. Java PC와 X-Plane PC를 당장 통합할지 논의했으나, APISAT 제출 전에는 새 변수를 줄이기 위해 기존 분리 구조를 유지하기로 했다.

단기 구조:

```text
X-Plane PC
    - X-Plane 11 / FlyWithLua
    - Tobii Pro Spark
    - Eye Tracker Manager
    - Python 3.10
    - Tobii Pro SDK
    - gaze CSV logger

Java PC
    - XPlaneReceiverMain
    - STATE / INTRUDER / EVENT CSV
    - ATC Server bridge
    - 분석/문서/Git
```

장기적으로는 X-Plane PC 한 대에 X-Plane, Tobii, Java receiver를 통합해도 무방하다. 통합 시 Lua `TARGET_HOST`를 Java PC IP 대신 `127.0.0.1` 또는 X-Plane PC IP로 바꾸면 된다. 다만 지금은 Tobii SDK/gaze CSV 안정화가 우선이다.

X-Plane PC SDK 상태:

- SDK 경로: `C:\Users\ACSL-SERVER\Desktop\김석호\TobiiPro.SDK.Python.Windows_2.1.0.1`
- 사용 폴더: `64`
- `.whl` 파일은 없고, `64` 폴더 자체가 Python import 경로 역할을 한다.
- `tobii_research.py`, `tobiiresearch`, `tobii_research_interop.pyd`가 포함되어 있다.
- Python은 `py -3.10`으로 실행한다.
- 현재 버전 출력은 `Python 3.10.0rc2`였다.

장비 인식 테스트:

```powershell
py -3.10 -c "import tobii_research as tr; trackers=tr.find_all_eyetrackers(); print('count', len(trackers)); [print(t.address, t.model, t.serial_number, t.device_name) for t in trackers]"
```

결과:

```text
count 1
tobii-prp://TPE01-100206101311 Tobii Pro Spark TPE01-100206101311 Tobii Pro Spark
```

추가 tracker 정보:

```text
Address : tobii-prp://TPE01-100206101311
Model   : Tobii Pro Spark
Name    : Tobii Pro Spark
Serial  : TPE01-100206101311
Firmware: b05c12f988
```

gaze stream 테스트 결과:

- `py -3.10 tobii_gaze_test.py` 실행 성공
- 10초 동안 `gaze samples: 577` 수집
- `tobii_gaze_test.csv` 저장 성공

처음에는 gaze packet은 들어왔지만 `gaze_x`, `gaze_y`, pupil 값이 `NaN`이고 validity가 `0`이었다. 이는 SDK 문제가 아니라 눈/얼굴 위치 또는 tracking 상태 문제로 판단했다. Eye Tracker Manager/user position 상태를 다시 맞춘 뒤 정상 gaze 좌표가 수신됐다.

정상 sample:

```text
left_gaze_x: 0.1623032689
left_gaze_y: 0.7860121131
right_gaze_x: 0.1665093452
right_gaze_y: 0.7612555027
left_validity: 1
right_validity: 1
left_pupil_diameter: 3.4304656982
right_pupil_diameter: 3.5915069580
left_pupil_validity: 1
right_pupil_validity: 1
```

판정:

```text
Tobii SDK import: 성공
Tobii Pro Spark discovery: 성공
gaze stream subscription: 성공
valid gaze coordinate 수신: 성공
CSV 저장: 성공
X-Plane/Java event CSV와 병합: 아직 미수행
```

추가한 로컬 파일:

- `tools/tobii/list_trackers.py`
- `tools/tobii/gaze_logger.py`
- `TOBII_SPARK_FIRST_TEST_260708.md`

다음 작업:

1. X-Plane PC에서 gaze 단독 30~60초 테스트를 한 번 더 수행한다.
2. 중앙/좌/우/상/하를 볼 때 normalized coordinate가 예상 방향으로 변하는지 확인한다.
3. X-Plane 화면을 띄우고 6개 계기 AOI와 `OUTSIDE` 분류가 예상대로 동작하는지 확인한다.
4. Java PC에서 기존 receiver를 실행하고 X-Plane PC에서 gaze logger를 동시에 실행한다.
5. 짧은 trial 1~3개만 수집한다.
6. X-Plane `_events.csv`와 Tobii gaze CSV를 timestamp 기준으로 병합 가능한지 확인한다.

## 2026-07-10 X-Plane 동시 기록용 Tobii logger 정리

X-Plane PC에서 `session_xplane_tobii_logger.py`를 수정해 장시간 gaze CSV를 저장하려는 단계로 진행했다. PowerShell heredoc/메모장 붙여넣기 과정에서 Python 들여쓰기 문제가 반복될 수 있으므로, Java PC 작업 폴더에 복사 가능한 기준 스크립트를 추가했다.

추가 파일:

- `tools/tobii/session_xplane_tobii_logger.py`

주요 내용:

- session id는 실행 시각 기준으로 자동 생성된다.
- output은 `session_xplane_tobii_YYYYMMDD_HHMMSS_gaze.csv` 형식으로 자동 생성된다.
- 같은 파일명을 덮어쓰지 않도록 exclusive create mode로 CSV를 연다.
- 기본 기록 시간: `600`초
- `pc_time_sec`, `pc_time_ns`, `pc_time_iso`, `device_time_stamp`, `system_time_stamp`를 함께 저장한다.
- 좌/우 gaze point와 validity, pupil diameter를 저장한다.
- 좌/우 눈 중 validity가 `1`인 값만 이용해 `avg_gaze_x`, `avg_gaze_y`를 계산한다.
- 처음 5개 row를 콘솔에 출력해 실시간 유효 좌표를 바로 확인한다.
- `Ctrl+C`로 조기 종료해도 unsubscribe 후 CSV를 닫도록 처리했다.

X-Plane PC에서는 이 파일 내용을 SDK `64` 폴더의 `session_xplane_tobii_logger.py`에 복사한 뒤 다음 명령으로 실행한다.

```powershell
cd "C:\Users\ACSL-SERVER\Desktop\김석호\TobiiPro.SDK.Python.Windows_2.1.0.1\64"
py -3.10 session_xplane_tobii_logger.py
```

다음 확인:

1. `left_validity`, `right_validity`가 대부분 `1`인지 확인한다.
2. `avg_gaze_x`, `avg_gaze_y`가 `NaN`이 아니라 정상 normalized coordinate인지 확인한다.
3. X-Plane trial 시작 전 logger가 이미 실행 중인지 확인한다.
4. Java PC의 X-Plane event CSV와 X-Plane PC의 gaze CSV 시간대가 겹치는지 확인한다.
## 2026-07-10 X-Plane + Tobii first merged analysis

X-Plane/Java receiver 로그와 Tobii Pro Spark gaze CSV를 같은 trial timeline 기준으로 처음 병합 분석했다.

분석 입력:

- `build_atc_tmp/xplane_tobii_test_260710.csv`
- `build_atc_tmp/xplane_tobii_test_260710_intruder.csv`
- `build_atc_tmp/xplane_tobii_test_260710_events.csv`
- `build_atc_tmp/session_xplane_tobii_test_260710_gaze.csv`
- `resources/cessna_aoi_draft_260526.csv`

수집/분석 결과:

- state rows: `1483`
- intruder rows: `1483`
- event rows: `195`
- Tobii gaze rows: `22547`
- valid gaze rows: `15647` (`69.4%`)
- X-Plane event time range and Tobii gaze time range overlap was confirmed.
- Java session analyzer result: total trials `15`, strict clean trials `11`, incomplete trials `4`.
- clean trials: `8`, `9`, `10`, `11`, `12`, `13`, `14`, `15`, `16`, `17`, `18`.
- clean trial mean `advisory->response`: about `0.97 s` in the Tobii test set.

Tobii AOI analysis tool:

- Added/updated `tools/tobii/analyze_xplane_tobii_session.py`.
- Output files:
  - `build_atc_tmp/xplane_tobii_test_260710_gaze_aoi.csv`
  - `build_atc_tmp/xplane_tobii_test_260710_trial_gaze_summary.csv`
- The script maps normalized Tobii `avg_gaze_x`, `avg_gaze_y` to pixel coordinates using the current default `1920x1080` screen size.
- AOI rows whose `priority` or `status` is `exclude` are now skipped. This prevents `DEBUG_TEXT` from being counted as a valid analysis AOI.
- A 200 ms dwell metric was added. For reporting, dwell-based AOI timing is preferred over a single-sample first hit.

Current AOI findings:

- AOI distribution after excluding `DEBUG_TEXT`:
  - `OTHER`: `6927`
  - `INVALID`: `6900`
  - `OUTSIDE_CENTER`: `6675`
  - `ATTITUDE`: `704`
  - `ALTITUDE`: `492`
  - `AIRSPEED`: `456`
  - `VERTICAL_SPEED`: `268`
  - `HEADING`: `73`
  - `NAV_GPS`: `52`
- `ADVISORY` AOI was not detected in this run. This should not yet be interpreted as "the pilot did not see the advisory"; the advisory AOI coordinates are still provisional and must be corrected using an actual X-Plane screenshot.
- `OUTSIDE_CENTER` gaze after advisory appears quickly in many trials, but this currently means only that gaze was inside the broad outside-view AOI. It does not by itself prove that the intruder was visually acquired.

Next work:

1. Use the six fixed instrument AOIs plus `OUTSIDE` policy for new analyses.
2. Keep Java IntelliJ `Program arguments` blank so Java creates timestamped files under `logs\xplane`.
3. Run the updated X-Plane PC Tobii logger so each gaze CSV is timestamped automatically.
4. Repeat 3 to 5 short trials with Java receiver and Tobii logger running before the first trial starts.
5. Use dwell-based and entry-based columns for APISAT reporting rather than single-sample first AOI hits.

Follow-up update:

- Added `tools/tobii/draw_aoi_overlay.ps1` to draw AOI boxes over a cockpit screenshot.
- Created `build_atc_tmp/cessna_aoi_overlay_260710.png` for visual AOI verification.
- Added `XPLANE_TOBII_AOI_MEANING_260710.md` to define each AOI label and interpretation rule.
- Added post-event AOI entry metrics to `tools/tobii/analyze_xplane_tobii_session.py`.
- New columns include `aoi_before_advisory`, `aoi_before_spawn`, `already_<AOI>_before_advisory`, and `advisory_to_first_<AOI>_entry_s`.
- Current result shows some trials were already in `OUTSIDE_CENTER` before `ADVISORY_SHOWN`, so those trials should not be described as post-advisory outside gaze shifts.

AOI policy update:

- The instrument AOIs were simplified to six fixed items: `AIRSPEED`, `ATTITUDE`, `ALTITUDE`, `HEADING`, `VERTICAL_SPEED`, `NAV_GPS`.
- Added `resources/cessna_instrument_aoi_260710.csv`.
- Any valid gaze point outside those six boxes is now classified as `OUTSIDE`.
- `RUNWAY_ZONE`, `ADVISORY`, and `OUTSIDE_CENTER` are no longer used as separate AOIs.
- Generated `build_atc_tmp/cessna_instrument_aoi_overlay_260710.png` to verify the six fixed AOIs.
- Added `TOBII_XPLANE_PC_FILE_PLACEMENT_260710.md` to clarify that only `session_xplane_tobii_logger.py` must be copied to the X-Plane PC for gaze recording. Analysis files stay on the Java PC unless analysis is intentionally moved to the X-Plane PC.

Automatic file naming update:

- `tools/tobii/session_xplane_tobii_logger.py` now generates a timestamped session id and output CSV at startup.
- Tobii output format is `session_xplane_tobii_YYYYMMDD_HHMMSS_gaze.csv`.
- The logger opens the output file in exclusive create mode, so it will not overwrite an existing file.
- Java `XPlaneReceiverMain` already supports automatic timestamped output if IntelliJ `Program arguments` is blank or only `9100`.
- For repeated Tobii trials, avoid fixed Java arguments such as `build_atc_tmp\xplane_tobii_test_260710.csv`; use Java automatic output under `logs\xplane`.

## 2026-07-16 Tobii gaze visualization script

기존 Tobii gaze CSV를 사용해 시간대별 시선 점과 trial별 시선 이동을 볼 수 있는 SVG 시각화 스크립트를 추가했다.

추가 파일:

- `tools/tobii/visualize_tobii_gaze.py`
- `TOBII_GAZE_VISUALIZATION_260716.md`

특징:

- 외부 plotting package 없이 Python standard library만 사용한다.
- 브라우저에서 바로 열 수 있는 SVG를 생성한다.
- cockpit background image 위에 gaze point와 6개 계기 AOI box를 표시한다.
- trial별 `ADVISORY_SHOWN` 전후 gaze trajectory를 표시한다.
- trial별 AOI timeline과 event marker를 표시한다.

기존 260710 데이터로 검증한 명령:

```powershell
py tools\tobii\visualize_tobii_gaze.py --gaze build_atc_tmp\session_xplane_tobii_test_260710_gaze.csv --events build_atc_tmp\xplane_tobii_test_260710_events.csv --aoi resources\cessna_instrument_aoi_260710.csv --background resources\cessnacokpit.png --output-prefix build_atc_tmp\xplane_tobii_test_260710_visual --trial 8
```

생성된 파일:

- `build_atc_tmp\xplane_tobii_test_260710_visual_gaze_scatter.svg`
- `build_atc_tmp\xplane_tobii_test_260710_visual_trial_8_advisory_shown_trajectory.svg`
- `build_atc_tmp\xplane_tobii_test_260710_visual_trial_8_aoi_timeline.svg`

검증:

- SVG 3개 생성 성공
- `visualize_tobii_gaze.py` syntax check 성공

주의:

- 시각화는 gaze point와 AOI transition을 보여주는 도구이다.
- intruder를 실제로 시각 획득했다는 결론은 시각화만으로 내리지 않는다.
- 보고용 해석은 `valid_gaze_rate`, dwell time, `advisory_to_first_OUTSIDE_entry_s`, `advisory_to_response_s`와 함께 사용한다.

## 2026-07-16 X-Plane/Java/Tobii 동일 PC 실행 준비

- 실제 X-Plane FlyWithLua `Scripts` 폴더의 활성 통합 Lua가 `127.0.0.1:9100`으로 Java receiver에 송신하도록 설정된 것을 확인했다.
- Git 기준본 `FLYWITHLUA_STUDY_INTEGRATED.lua`의 `TARGET_HOST`도 `100.64.0.129`에서 `127.0.0.1`로 동기화했다.
- Lua 버전, UDP 패킷 형식, command, event 및 scenario parameter는 변경하지 않았다.
- 다음 검증은 같은 PC에서 Java receiver와 Tobii logger를 먼저 실행한 뒤 1~3개 짧은 X-Plane trial을 수집하는 것이다.

## 2026-07-16 Tobii 원본 출력 폴더 통합

- `session_xplane_tobii_logger.py`가 실행 위치 대신 `AISimulationProject/logs/tobii`에 gaze CSV를 저장하도록 수정했다.
- 프로젝트가 이동한 경우 `AISIMULATION_PROJECT_DIR` 환경 변수로 project root를 재지정할 수 있다.
- 기존 최신 gaze CSV는 복사 및 해시 검증 후 `logs/tobii`에서도 사용할 수 있게 한다. SDK 폴더의 기존 원본은 즉시 삭제하지 않는다.

## 2026-07-16 동일 PC 첫 실험 분석 및 AOI 배경 갱신

- Java `session_20260716_161359`와 Tobii `session_xplane_tobii_20260716_161408`을 timestamp 기준으로 병합했다.
- STATE/INTRUDER sample은 각각 `1,262`, gaze는 `16,928` row이며 유효 gaze는 `12,414` row (`73.3%`)였다.
- 10개 trial 중 1~9는 strict clean이고, trial 10은 `TRIAL_END` 누락으로 incomplete였다.
- Java event와 Tobii gaze 시간은 `284.194 s` 동안 겹쳐 동일 PC 수집 구조가 정상임을 확인했다.
- trial 1, 5, 8의 advisory 전후 gaze trajectory와 AOI timeline을 생성했다.
- 새 전체화면 cockpit 캡처 `AOI그림.png`에 현재 6개 AOI를 overlay하여 계기 위치가 일치함을 확인했다.
- 앞으로 `visualize_tobii_gaze.py`의 기본 배경은 `AOI그림.png`를 사용한다. `resources/cessnacokpit.png`는 이전 참고 이미지로만 보존한다.

## 2026-07-16 AOI fallback 1단계 개선

- 계기 AOI 밖의 모든 유효 gaze를 `OUTSIDE`로 합치던 규칙을 폐기했다.
- 6개 계기 AOI를 먼저 판정하고, 나머지는 `y < 600 px`이면 `OUTSIDE_VIEW`, `y >= 600 px`이면 `PANEL_OTHER`로 판정한다.
- `analyze_xplane_tobii_session.py`와 `visualize_tobii_gaze.py`에 `--panel-top-y` 옵션을 추가했다.
- 동일 세션 재분석 결과는 `OUTSIDE_VIEW=8,457`, `PANEL_OTHER=2,231`이었다.
- ATTITUDE와 HEADING 사이 20 px 간격에 들어온 gaze 70개는 모두 `PANEL_OTHER`로 확인됐다.
- 다음 단계는 파랑-빨강 연속 색상 대신 event 기준 고정 시간 구간별 패널 시각화를 추가하는 것이다.

## 2026-07-16 시간 구간 시각화 2단계 개선

- `visualize_tobii_gaze.py`에 event 기준 6개 고정 시간 패널 SVG를 추가했다.
- 이 단계의 초기 6구간은 이후 8패널 혼합 시간 구간 규칙으로 대체됐다.
- 모든 점은 동일한 magenta 색을 사용하고 시간은 패널 제목으로 직접 구분한다.
- 각 패널에 valid/plotted sample 수를 표시하며, 유효 gaze가 없는 구간도 빈 패널로 남긴다.
- trial 1 검증에서 경고 전 두 구간은 valid 0이었고 이후 구간은 각각 `45`, `60`, `60`, `110`개였다.
- 기존 전체 scatter, 연속 색상 trajectory, AOI timeline은 호환성을 위해 유지한다.

## 2026-07-16 전 trial 경고 전 gaze 유효성 점검

- trial 1~10의 고정 시간 패널을 모두 생성했다.
- 모든 경고 전 구간에 약 60 Hz raw sample이 존재해 Java/Tobii timestamp 정렬 문제는 아니었다.
- `-2~-1 s`는 `601`개 중 `188`개 유효 (`31.3%`), `-1~0 s`는 `595`개 중 `273`개 유효 (`45.9%`)였다.
- trial 1, 5, 8은 경고 전 2초 동안 좌·우 validity가 모두 0이어서 점이 표시되지 않았다.
- 빈 패널에서는 gaze 위치나 AOI를 추정하지 않는다. 초기 tracking-loss-only 해석은 이후 3모니터 조건 확인에 따라 정정했다.

## 2026-07-16 3모니터 조건 반영 및 해석 정정

- 실험 환경은 모니터 3개이며 Tobii Pro Spark는 정면 모니터 1개만 추적한다.
- 기존 `INVALID` 분류를 `UNTRACKED_OR_OFF_DISPLAY`로 변경했다.
- 앞서 빈 패널을 Tobii tracking loss로 단정한 해석은 폐기한다.
- 새 분류는 정면 추적 화면에서 유효 gaze가 관측되지 않았다는 뜻이며, 좌우 모니터 응시와 실제 추적 손실을 현재 CSV만으로 구분할 수 없다.
- 빈 구간에서는 AOI, 좌우 모니터 응시, 장비 오류를 추정하지 않는다.
- 시간 구간 패널 label에 `front valid/total`과 `untracked/off-display` 수를 함께 표시하도록 수정했다.

최종 재분석 결과:

- 전체 gaze `16,928`, 정면 화면 유효 gaze `12,414` (`73.3%`), `UNTRACKED_OR_OFF_DISPLAY` `4,514` (`26.7%`).
- 경고 전 2초 정면 화면 유효률: T1 `0.0%`, T2 `98.3%`, T3 `27.5%`, T4 `94.2%`, T5 `0.0%`, T6 `94.2%`, T7 `11.7%`, T8 `0.0%`, T9 `28.2%`, T10 `30.5%`.
- `xplane_tobii_20260716_161359_three_monitor_*` 이름으로 재분석 CSV와 trial 1~10 시간 패널을 생성했다.

## 2026-07-16 혼합 시간 구간 3단계 개선

- 기존 6개 고정 구간을 최종 8개 혼합 구간으로 변경했다.
- 새 기본 구간은 `-2~-1`, `-1~0`, `0~+0.5`, `+0.5~+1`, `+1~+2`, `+2~+3`, `+3~+4`, `+4~+5 s`이다.
- 경고 직후 첫 1초는 빠른 반응 순서를 보존하기 위해 0.5초 단위로 나누고, 경고 전/회복 구간은 1~2초 폭을 유지한다.
- 대표 clean trial 4에서 두 post-advisory 0.5초 구간이 각각 `30/30` 유효 sample로 정상 생성됐다.
- 마지막 `+3~+5 s` 구간을 `+3~+4`, `+4~+5 s`로 분할해 총 8개 패널의 `4 x 2` 배치로 정리했다.

## 2026-07-16 200ms 중심점 이동 4단계 개선

- 8패널 안의 raw gaze 점을 `200 ms binned gaze centroid`로 요약했다.
- 각 중심점은 해당 200ms 구간의 유효 gaze 평균 좌표이며, 패널 내부 시간 순서에 따라 색이 단계적으로 변한다.
- 중심점 옆에 event 상대 midpoint 시간을 표시하고, 연속된 구간만 화살표로 연결한다.
- 유효 gaze가 없는 200ms 구간은 보간하지 않으며 화살표도 건너뛰어 연결하지 않는다.
- 이는 fixation 검출이 아니므로 결과 명칭에 fixation을 사용하지 않는다.
- trial 4 검증 결과 중심점 36개, 이동 화살표 28개가 생성됐다.

## 2026-07-20 AOI timeline 가독성 개선

- `xplane_tobii_20260716_161359_centroids_trial_4_aoi_timeline.svg`의 event 글씨 겹침을 수정했다.
- 회전된 event name을 timeline에서 제거하고 번호 marker만 표시했다.
- 같은 시각 또는 가까운 event marker는 여러 lane으로 엇갈려 배치한다.
- 아래쪽에 `번호, TRIAL_START 상대시간, event name`을 2열 목록으로 분리했다.
- AOI color 범례는 별도 3열 영역으로 분리하고 상대시간 axis tick을 추가했다.
- 갱신된 trial 4 SVG 크기는 `1400x592`이다.

## 2026-07-20 operational external 및 trial 평균 분석

- 3모니터 운용 규칙에 따라 `OPERATIONAL_EXTERNAL = OUTSIDE_VIEW + UNTRACKED_OR_OFF_DISPLAY` 파생 분류를 추가했다.
- 원본 `aoi`는 유지하고 병합 CSV에 `operational_aoi`를 별도 저장한다.
- strict clean trial만 동일 가중치로 집계하고 `all/front/left/right` 그룹별 `n`, 평균, 중앙값, 표준편차, 최솟값, 최댓값을 생성한다.
- event 상대 시간창은 전체 구간이 해당 trial의 `TRIAL_START~TRIAL_END` 안에 있을 때만 집계한다.
- 출력 파일 `xplane_tobii_20260716_161359_operational_trial_aggregate_summary.csv`를 추가했다.
- 기존 세션 재분석에서 clean trial `9`, incomplete trial `1`을 유지했다.
- clean 평균 advisory-to-response는 `0.903 s`, trial operational-external rate는 `72.6%`였다.
- clean trial `8/9 = 88.9%`가 advisory 직전에 이미 operational external이어서 최초 외부 진입시간은 주 지표로 사용하지 않는다.

## 2026-07-20 placeholder gate 제거

- Lua version을 `260720_remove_placeholder_gate_v22`로 갱신했다.
- `FINAL_APPROACH_GATE_ENTERED`의 `trial_start_placeholder` 발생 코드를 제거했다.
- Java strict clean event 목록에서도 해당 이벤트를 제거했다.
- 구형 CSV 호환을 위해 Java enum/parser의 이벤트 정의는 유지한다.
- Lua snapshot `FLYWITHLUA_STUDY_INTEGRATED_260720_remove_placeholder_gate_v22.lua`를 생성했다.
- Git 기준 Lua와 실제 X-Plane `Scripts` 활성 Lua의 SHA-256 일치를 확인했다.

## 2026-07-20 Lua v22 신규 실험 검증

- Java `xplane_self_tobii_20260720_142508`과 Tobii `session_xplane_tobii_20260720_142554_gaze.csv`를 병합 분석했다.
- STATE/INTRUDER 각 `1,568`, gaze `19,408`, 정면 유효 gaze `14,780` (`76.2%`), 시간 중첩 `319.518 s`였다.
- `FINAL_APPROACH_GATE_ENTERED=0`, `SCENARIO_SELECTED=11`로 placeholder gate 제거를 확인했다.
- 총 11 trial 중 strict clean 8, incomplete 3이었다.
- trial 2는 `PILOT_RESPONSE_START` 누락, trial 8과 11은 `TRIAL_END` 누락이었다.
- clean 평균 advisory-to-response `0.958 s`, trial operational-external rate `78.7%`였다.
- clean 8개 전부 advisory 직전에 이미 operational external이었다.
- `-1~0 s`, `0~+0.5 s`, `+0.5~+1 s` operational-external 평균은 각각 `98.7%`, `98.7%`, `99.1%`였다.
- 현재 강제 operational external 규칙에서는 advisory 주변 비율이 포화돼 경고 후 시선 전환 효과를 구분하기 어렵다.
- 구성요소를 분리하면 `-1~0 s`에서 `OUTSIDE_VIEW=31.3%`, `UNTRACKED_OR_OFF_DISPLAY=67.4%`, `0~+0.5 s`에서 `63.7%/34.9%`, `+0.5~+1 s`에서 `87.7%/11.4%`였다.
- 따라서 계기→외부 전환보다는 좌우 모니터/미관측 operational external에서 정면 모니터 외부 시야로 이동한 패턴으로 해석한다.
- valid gaze `88.9%`, advisory-to-response `0.906 s`인 clean trial 5를 대표 예시로 선택해 time bins, trajectory, AOI timeline을 생성했다.
## 2026-07-22 Trial 5 trajectory readability update

- Updated `tools/tobii/visualize_tobii_gaze.py` so the single-panel trajectory keeps raw gaze as faint background dots and overlays consecutive 200 ms gaze centroids with high-contrast arrows.
- Moved trajectory events out of the cockpit image into a separate footer with numbered markers and a three-column event list, preventing the lower-left labels from overlapping.
- Regenerated the four `xplane_self_tobii_20260720_142508_visual_*` SVG files from the same source CSVs.
- Trial 5 AOI verification: across the full trial, `OUTSIDE_VIEW=761/1276 (59.6%)`; within `ADVISORY_SHOWN -2 s to +5 s`, `OUTSIDE_VIEW=374/421 (88.8%)` and `UNTRACKED_OR_OFF_DISPLAY=44/421 (10.5%)`.
- The external-view concentration is therefore present in the classified source data, not introduced by SVG rendering. It does not prove that the intruder itself was visually acquired.

## 2026-07-24 APISAT experiment Step 1 scenario baseline

교수님 피드백과 후속 논의를 바탕으로 실험의 기준 질문과 시나리오를 정리했다.

추가 파일:

- `APISAT_EXPERIMENT_SCENARIO_BASELINE_260724.md`

핵심 결정:

- visual advisory 인식 반응이 아니라 예고 없이 나타난 intruder 직접 발견·회피를 측정한다.
- 다음 response anchor는 `ADVISORY_SHOWN`이 아니라 `INTRUDER_SPAWNED`이다.
- spawn은 인지시점이 아니므로 `spawn-to-response latency`라고 표현한다.
- participant 화면에서 countdown, scenario/debug 정보와 `TRAFFIC ALERT`/`CHECK OUTSIDE`를 숨긴다.
- 내부 hazard/event/minimum-distance/end-guard 기록은 유지한다.
- 새 no-alert trial에서는 실제 제시하지 않은 경고를 `ADVISORY_SHOWN/CLEARED`로 남기지 않고 내부 위험창은 `HAZARD_DETECTED/CLEARED`로 기록한다. 구형 CSV parser 호환성은 유지한다.
- random delay는 표준 음성 시작 후 독립적인 `3~10초`로 계획한다.
- 실험자가 직접 말하지 않고 자동 재생되는 동일 WAV와 audio event timestamp를 사용한다.
- 첫 과제는 speed, runway centerline/course, stabilized glidepath 유지로 제한한다.
- 큰 임의 heading 변경, 강하 중 fixed altitude, 숫자 vertical-speed 지시는 제외한다.
- 매 trial 동일 final-approach 저장상황을 다시 불러오고 한 접근당 한 encounter만 수행한다.
- 기존 strict clean을 recording/event completeness, flight-condition validity, outcome으로 분리한다.
- hazard/advisory/response 발생 자체를 recording-complete 필수조건으로 두지 않는다.

첫 pilot 후보:

- ownship: Laminar Research 기본 `Cessna 172SP`
- runway/course: `RKSI runway 34`, 약 `325°`
- threshold 약 `3 NM` 전방
- 약 `900~1,000 ft AGL`
- 약 `3°` glidepath
- target IAS `70 KIAS` 후보
- 음성: `Cessna Zero One, continue straight-in, maintain seven zero knots, track runway centerline.`

주의:

- target IAS는 현재 로그에서 가져오지 않는다.
- flap/weight 조건과 C172SP POH를 확인하고 pilot test 후 target IAS를 동결한다.
- 현재 STATE에는 flap이 없으므로 다음 schema update에서 flap handle/actual deployment를 자동 기록한다.
- 기존 situation을 덮어쓰지 않고 `APISAT_RKSI34_C172SP_3NM.sit` 같은 전용 저장상황을 새로 만든다.
- VS는 순간값이 아니라 1~2초 rolling value와 glidepath/AGL corridor로 평가한다.

### 2026-07-20 STATE 재검토

clean trial 1, 3, 4, 5, 6, 7, 9, 10을 포함해 event 인접 STATE를 다시 확인했다.

- spawn IAS: `119.8~148.9 KIAS`, 평균 `140.8 KIAS`
- spawn heading: `323.9~325.6°`, 평균 `324.9°`
- spawn AGL: `101~1,864 ft`
- spawn vertical speed: `-1,014~-44 fpm`, 평균 약 `-440 fpm`
- clean trial start AGL 평균: 약 `970 ft`, 범위 `190~1,962 ft`
- heading은 비교적 안정됐지만 IAS, AGL과 VS는 trial 간 동일하지 않았다.

단위 오류를 확인했다.

- CSV 컬럼명은 `ias_mps`이지만 Lua source dataref `sim/flightmodel/position/indicated_airspeed`는 X-Plane `DataRefs.txt` 기준 `kias`이다.
- 기존 스키마를 즉시 바꾸지는 않으며 해당 필드를 KIAS로 해석한다.
- `tas_mps`와 `vertical_speed_mps`는 m/s이다.

환경 확인:

- X-Plane `Log.txt`에서 ownship은 `Aircraft/Laminar Research/Cessna 172SP/Cessna_172SP.acf`로 확인됐다.
- STATE 궤적과 X-Plane `apt.dat` runway endpoint를 대조해 RKSI runway 34 접근임을 확인했다.
- 현재 STATE에는 flap 상태가 없어 기존 trial의 flap configuration은 사후 복원할 수 없다.

이 결과 때문에 기존 2026-07-20 자료는 시스템·알고리즘 개발용 pilot data로 분류하고 개선 프로토콜의 본실험과 합치지 않는다.

### Response detector 계획

- spawn 전 약 1초의 control baseline 사용
- threshold 변화가 `200~300 ms` 지속되는 첫 지점을 response 후보로 사용
- task error와 `active_control_at_spawn` 기록
- labeled pilot trial로 자동 검출을 수동 검증한 뒤 threshold 동결
- task 이행 조작과 회피 조작의 오분류 여부 확인

### Three-monitor feasibility 계획

- 로컬 baseline: RTX 2060, driver `560.94`, 좌/중/우 각 `1920x1080`, Windows x 좌표 `-1920/0/1920`, 가운데 `DISPLAY2`가 primary이다.
- 현재 Windows에는 3개 독립 display로 보이므로 Surround는 아직 적용되지 않은 상태다.
- NVIDIA Surround로 3개 모니터를 하나의 logical display로 설정
- 모니터를 평면·동일 높이로 정렬하고 Spark를 중앙에 설치
- 좌/중/우 각 5점, 총 15점을 3회 측정
- per-monitor valid rate, median/95th error, monitor classification, dropout/recovery, center AOI degradation 기록
- 연구용 임시 go/no-go: side classification `>=90%`, side valid `>=70%`, center error 악화 `<=20%`
- 이 값은 Tobii 공식 성능기준이 아니다.
- 실패하면 center-only Spark와 `UNTRACKED_OR_OFF_DISPLAY` 정책을 유지한다.
- Spark 두 대 동시 사용은 공식 보장으로 간주하지 않는다. 제조사 확인 또는 대여시험 전에는 구매하지 않는다.
- 4C는 현재 Tobii Pro SDK pipeline에서 제외한다.
- Tobii 공식 자료상 Spark optimal 16:9 screen은 최대 27인치이고 maximum gaze angle은 35°이다. large/wide/multi-display는 거리·기하조건에 따라 가능하므로 실제 validation으로 판단한다.

### Detectability와 external AOI 계획

- 먼 거리에서 intruder를 생성하면 spawn과 보이는 시점을 분리한다.
- 거리/FOV/해상도 기반 pixel size를 계산하고 screenshot과 pilot test로 `INTRUDER_DETECTABLE_ONSET`을 교정한다.
- 먼 거리 protocol에서는 detectability-to-response를 primary, spawn-to-response를 secondary exposure metric으로 사용한다.
- planned AOI는 `RUNWAY_DYNAMIC`, `INTRUDER_DYNAMIC`, `HORIZON_OR_EXTERNAL_OTHER`, monitor-side outside, instrument, unresolved이다.
- eye tracking으로 볼 수 있는 것은 바라본 object/region이며 목적·인지·의도 자체는 아니다.

### APISAT 논문 범위

- full-paper 제출기한은 `2026-09-15`이다.
- 참고 초록은 `2026 APISAT/APISAT-2026_Abstract(김석호)최종.docx`와 같은 이름의 PDF이다.
- full paper는 event-aligned X-Plane/Java/Tobii 수집·분석 framework와 preliminary validation으로 한정한다.
- threat-recognition time을 직접 측정했다거나 AI pilot model을 완성했다고 주장하지 않는다.
- 개선 프로토콜을 pilot test하고 동결한 뒤 새 자료만 본실험 결과로 사용한다.

## 2026-07-24 2단계 Tobii 3모니터 검증 도구 구현

### 현재 장비/화면 상태 재확인

- Windows는 NVIDIA Surround가 아니라 독립 화면 3개로 인식 중이다.
  - 좌측 `DISPLAY1`: `-1920,0`, `1920x1080`
  - 중앙 primary `DISPLAY2`: `0,0`, `1920x1080`
  - 우측 `DISPLAY3`: `1920,0`, `1920x1080`
- 세 화면은 모두 100% scaling이다.
- GPU는 RTX 2060, driver `560.94`이다.
- Spark는 1대이고 serial은 `TPE01-100206101311`, 주파수는 `60 Hz`이다.
- SDK가 반환한 현재 Active Display Area는 `598 x 336 mm`로, 27-inch 중앙 화면 한 개 설정과 일치한다.
- 현재 상태에서 “Spark는 정면 모니터만 추적한다”는 과거 설명은 당시 중앙 한 화면 설정을 뜻한다. 물리적 절대 한계라고 단정하지 않도록 문구를 수정했다.

### 전용 도구 추가

- 새 파일: `tools/tobii/multimonitor_gaze_validation.py`
- 버전: `260724_v2`
- schema: `2`
- 전용 절차서: `TOBII_SURROUND_VALIDATION_260724.md`

도구 기능:

- `CENTER_BASELINE`: 중앙 5점 x 3회 = 15 epoch
- `SURROUND_WIDE`: 좌/중/우 각 5점 x 3회 = 45 epoch
- 각 target은 해당 물리 화면의 20/80% 모서리 4점과 중앙점이다.
- home/blank/settle/measure 단계와 target event를 자동 기록한다.
- tracker serial을 명시적으로 선택한다.
- normalized gaze를 unclamped 상태로 active logical display에 매핑한다.
- epoch/target/monitor 단위 valid rate, monitor classification, error, dropout, acquisition, recovery, coverage를 산출한다.
- raw/annotated/metric/summary/metadata/SVG 총 9개 산출물을 `logs/tobii/multimonitor_validation`에 저장한다.
- raw gaze는 queue writer로 약 1초마다 flush하고 target event는 발생할 때마다 flush한다.
- ESC/창 닫기뿐 아니라 Ctrl+C와 Tk callback 예외도 incomplete-session 보존 경로로 처리한다.
- wall time과 별도로 monotonic timestamp를 저장하여 epoch 내부 latency에 사용한다.
- Windows/Tobii 설정 단계에서 실패하면 `CONFIGURATION_FAILED` preflight JSON을 남긴다.

### 연구-valid guard 보완

- CENTER baseline은 3개 독립 화면, Surround는 Windows logical display 1개를 요구한다.
- 물리 panel width/height, eye-to-screen distance, Surround bezel gap을 실측값으로 명시해야 한다.
- `bezel_mm`은 베젤 한쪽 폭이 아니라 인접한 두 visible display area 사이의 총 간격으로 정의했다.
- 익명 participant code, 공통 physical setup ID, run별 calibration ID를 기록하고 비교 시 participant/setup 일치를 자동 확인한다.
- Tobii Active Display Area의 width와 height가 예상 물리 영역과 15% 넘게 다르면 실행을 거부한다.
- `--allow-layout-mismatch`, `--allow-display-area-mismatch`는 diagnostic 전용이며 비교 PASS 대상에서 제외한다.
- 표준 timing, 3회 반복, flat/co-planar, 100% scaling, bezel correction off를 research-valid setup 조건으로 기록한다.
- 실제 GUI 창이 active display pixel bounds와 일치하는지 검사한다.
- 첫 block 시작 전 최근 gaze stream 표본과 최소 한 개의 유효 표본을 확인한다.
- block은 Space KeyRelease와 debounce로 진행하며 조기 key repeat를 막는다.

데이터 완전성 조건:

- baseline 15 epoch / 5 target, Surround 45 epoch / 15 target
- target당 정확히 3회
- 각 epoch에서 `HOME_ON`, `HOME_OFF`, `TARGET_ON`, `MEASURE_START`, `MEASURE_END`, `TARGET_OFF`가 1회씩 순서대로 존재
- 실제 measure duration이 계획의 90~120%
- 각 epoch sample coverage가 80~120%
- session median coverage가 90~110%
- 누락 epoch/target/NaN을 좋은 평균에서 제외하지 않고 `INCONCLUSIVE` 처리

### 판정 체계

- `PASS`: 비교 가능성과 monitor-level 성능 기준을 모두 만족
- `FAIL`: research-valid 비교자료는 있으나 성능 기준 미달
- `INCONCLUSIVE`: 설정, protocol, stream, baseline 품질 또는 run pair 불일치

임시 project threshold:

- 좌/우 valid rate 각각 70% 이상
- 좌/우 correct-monitor classification 각각 90% 이상
- 좌/우 worst target median repeat-valid rate 각각 50% 이상
- baseline/Surround 중앙 valid rate 각각 70% 이상
- 중앙 median target-centroid error 악화 20% 이하

동일 tracker/frequency, timing, panel geometry, center pixel geometry,
schema/tool version, participant/setup ID와 view distance 20 mm 이내 일치도 비교 조건에 포함했다.
`MONITOR_LEVEL_PASS`는 coarse monitor 분류 가능성만 뜻하며
`FINE_AOI_PASS`는 여전히 미평가 상태이다.

### 자동 검증 결과와 남은 물리 작업

- Python compile 및 deterministic self-test 통과
- 현재 extended-desktop `CENTER_BASELINE --dry-run` 통과
- 현재 상태의 정상 `SURROUND_WIDE --dry-run`은 화면 3개를 감지하고 의도대로 거부
- diagnostic wide mapping은 `5760x1080` 가상 영역에 45개 epoch를 정상 생성
- SDK import, Spark serial 선택, `60 Hz`, `Default` mode, `598 x 336 mm` display area 조회 성공
- 잘못된 `500 x 300 mm` 물리값을 넣은 실제 Spark preflight는 Active Display Area mismatch를 검출하고 `*_preflight.json`을 남긴 뒤 exit `3`으로 중단
- 현재 비-Surround 상태의 physical `SURROUND_WIDE` preflight는 display 3개를 검출하고 `CONFIGURATION_FAILED` JSON을 남긴 뒤 exit `2`로 중단
- 최종 synthetic end-to-end 검증에서 정상 pair는 `PASS(0)`, side-invalid 성능은 `FAIL(5)`, missing target/1-repeat/diagnostic/participant 또는 setup 불일치는 `INCONCLUSIVE(6)`로 분리됨
- CSV/JSON null roundtrip, SVG XML, monitor CSV field, raw/event streaming close, KeyboardInterrupt/Tk callback abort 경로를 확인
- Lua/Java/X-Plane CSV schema는 이번 2단계에서 변경하지 않았다.
- 기존 X-Plane/Tobii 원본 실험 CSV는 수정하지 않았다.
- 이 구현 시점에는 물리 `CENTER_BASELINE`과 NVIDIA Surround+ETM
  whole-display calibration 이후의 `SURROUND_WIDE`가 남아 있었으며,
  두 작업은 2026-07-27 후속 항목에서 완료·판정했다.
- 당시 실제 기준 측정 전 요구했던 눈-중앙 화면 수직거리는 이후
  `750 mm`로 실측했다.

## 2026-07-27 2단계 중앙 모니터 기준 측정

- 사용자가 평소 비행 자세의 눈-중앙 화면 거리를 약 `750 mm`로 측정했다.
- X-Plane과 기존 Python Tobii logger가 실행 중이지 않은 상태를 확인했다.
- `CENTER_BASELINE`을 중앙 `1920x1080`, 물리 영역 `598 x 336 mm`,
  viewing distance `750 mm` 조건으로 실행했다.
- session: `tobii_multimonitor_center_baseline_20260727_154315`
- 결과 status: `RESEARCH_VALID_COMPLETE`
- `15/15` epoch 및 `5 target x 3 repeat`가 정상 완료됐다.
- condition median sample coverage는 `100.0%`이고 quality reason은 없다.
- equal-target center valid rate는 `96.5%`, binocular valid rate는 `95.6%`이다.
- 유효 표본의 center-monitor classification은 `99.9%`이다.
- median target-centroid error는 `36.1 px`, target별 median 범위는
  `22.5~83.1 px`이다.
- equal-target radial sample p95는 `75.3 px`이다.
- target별 valid rate가 가장 낮은 구간은 좌상단 계열이었지만, baseline
  비교 기준인 center valid `70%`를 충분히 충족했다.
- 이 자료는 이후 `SURROUND_WIDE`의 중앙 오차 악화율과 중앙 validity를
  비교하는 기준본으로 사용 가능하다.
- 아직 NVIDIA Surround/ETM whole-display 물리 측정은 끝나지 않았으므로
  Spark 한 대의 3모니터 추적 가능 여부는 결론 내리지 않는다.
- 중앙 baseline 이후 사용자가 NVIDIA Surround `1 x 3`을 적용했다.
- Windows 자동 확인 결과 primary logical display 1개, `5760x1080`,
  origin `(0,0)`으로 정상 전환됐다.
- Tobii Pro Eye Tracker Manager를 실행했다.
- 최신 SDK 재확인에서는 Active Display Area가 아직 기존 `598 x 336 mm`로
  남아 있으므로 ETM whole-wide Display Setup/calibration은 완료되지 않았다.
- 이 상태에서는 `SURROUND_WIDE`를 실행하지 않는다.
- 사용자가 wide display size를 `1794 x 336 mm`로 변경했고 SDK 저장값과
  정확히 일치함을 확인했다.
- 다만 Active Display Area의 UCS plane depth가 baseline `z=-90 mm`에서
  `z=-740 mm`로 변경됐다.
- Spark가 실제로 중앙 모니터 아래에 부착된 상태라면 tracker가 화면에서
  약 740 mm 앞에 있다고 모델링된 것이므로 잘못된 tracker-position 설정이다.
- 사용자 eye-to-screen distance `750 mm`는 분석 metadata 값이며 custom
  Display Setup의 tracker-to-screen distance로 입력하면 안 된다.
- tracker position을 실제 center-bottom mount, 약 기존 90 mm screen offset와
  실제 pitch로 수정하고 whole-wide calibration을 완료하기 전까지
  `SURROUND_WIDE` 수집을 보류한다.

## 2026-07-27 2단계 NVIDIA Surround 물리 검증 결과

- 사용자가 custom Display Setup의 tracker position을 다시 수정했다.
- SDK 재확인 결과 Windows는 primary logical display 1개, `5760x1080`,
  origin `(0,0)`으로 유지됐다.
- Active Display Area 네 모서리는 폭 `1794 mm`, 높이 `336 mm`,
  display-plane depth 약 `z=-60 mm`를 나타냈다.
- `retrieve_calibration_data()`가 비어 있지 않아 whole-wide calibration
  저장 상태를 확인했다.
- X-Plane과 별도 Tobii logger는 실행 중이지 않았고, ETM의 표시 창을
  닫은 상태에서 물리 검증을 수행했다.

첫 번째 Surround run:

- session: `tobii_multimonitor_surround_wide_20260727_155743`
- `45/45` epoch 완료, elapsed `212.781 s`
- status: `DATA_INCOMPLETE`
- quality reason: `sample_coverage_failed_epochs=13,42`
- 두 epoch 모두 `LEFT_LL`, sample coverage `78.9%`, valid gaze `0`
- equal-target valid rate: left `44.4%`, center `93.5%`, right `42.1%`
- center median target-centroid error: `33.9 px`
- side median target-centroid error: left `185.5 px`, right `365.5 px`
- left `LEFT_LL/LEFT_UL`, right `RIGHT_LR/RIGHT_UR`은 target 단위
  mean valid rate가 `0%`였다.

동일 설정 retry:

- session: `tobii_multimonitor_surround_wide_20260727_160333`
- `45/45` epoch 완료, elapsed `211.625 s`
- status: `DATA_INCOMPLETE`
- quality reason: `sample_coverage_failed_epochs=23`
- 해당 epoch는 `LEFT_LL`, sample coverage `67.8%`, valid gaze `0`
- equal-target valid rate: left `37.5%`, center `96.3%`, right `40.1%`
- center median target-centroid error: `71.9 px`
- side median target-centroid error: left `327.4 px`, right `325.1 px`
- retry에서도 left `LEFT_LL/LEFT_UL`, right `RIGHT_LR/RIGHT_UR` target이
  세 반복 모두 mean valid rate `0%`였다.

판정:

- baseline과 두 Surround 비교의 자동 문자열은 모두 `INCONCLUSIVE`이다.
  epoch coverage integrity gate 미달 자료를 도구가 literal `FAIL`로
  바꾸지 않는 설계이므로 이 값을 그대로 보존한다.
- 그러나 두 번의 독립 실행에서 좌/우 valid rate가 project 기준
  `70%`에 크게 못 미쳤고, 동일한 바깥쪽 표적에서 반복적으로 완전
  dropout이 발생했다.
- 따라서 현재 flat/co-planar 3모니터, 눈-화면 거리 `750 mm`,
  중앙 하단 Spark 한 대 조건의 engineering/operational go/no-go는
  `부적합`으로 결정한다.
- NVIDIA Surround는 좌표계를 하나로 만드는 데 성공했지만 Spark의
  실제 추적 가능 시야를 세 화면 전체로 확장하지 못했다.
- 이 결과는 현재 기하조건에 한정하며 Spark의 모든 wide-display
  구성이 절대 불가능하다는 뜻은 아니다.
- 동일 조건 재측정은 더 수행하지 않는다. 다음 X-Plane 실험 전
  Windows extended desktop과 중앙 `1920x1080`, `598 x 336 mm`
  Tobii Display Setup/calibration을 복원한다.
- 기존 분석정책은 유지한다. 정면 화면만 정밀 AOI로 분석하고,
  좌우 화면을 본 동작은 `OPERATIONAL_EXTERNAL`로 처리하며 정확한
  좌우 좌표나 intruder fixation을 주장하지 않는다.

## 2026-07-27 Surround coarse external 분류 재정의 및 구현

사용자가 필요한 결과는 좌우 모니터의 정밀 gaze 좌표가 아니라
`중앙 계기/정면 시야`와 `좌우 외부환경` 사이의 주의 전환이다. 이에 따라
앞선 판정을 다음 두 수준으로 분리했다.

- 정밀 three-monitor gaze/AOI: 현재 물리 결과상 no-go
- coarse center-versus-side external event: conditional go, X-Plane pilot 필요

구현:

- 수정: `tools/tobii/analyze_xplane_tobii_session.py`
- analyzer version: `260727_surround_external_v1`
- 추가: `tools/tobii/validate_surround_external_classifier.py`
- validator version: `260727_v1`
- raw Tobii logger와 X-Plane CSV schema는 변경하지 않았다.

새 `--layout-mode surround-wide`:

- logical screen `5760x1080`
- physical monitor width `1920`
- center monitor global x `1920..3840`
- 기존 계기 AOI에는 `center_gaze_px = gaze_px - 1920`을 사용
- center-only 기본 모드는 기존 동작을 유지

evidence 분류:

- `MEASURED_SIDE_LEFT`
- `MEASURED_SIDE_RIGHT`
- `INFERRED_SIDE_UNKNOWN`
- `UNRESOLVED_TRACKING_LOSS`
- center의 유효 외부시야는 `CENTER_OUTSIDE_MEASURED`

기본 hybrid rule:

- side seam guard: `120 px`
- measured side dwell: `200 ms`
- inferred bilateral loss: `500~5000 ms`
- loss 전후에 유효 sample 존재
- episode 내부 최대 sample gap: `100 ms`
- 방향은 measured side에만 부여하고 loss-only는 `UNKNOWN` 유지
- `OPERATIONAL_EXTERNAL`은 center outside, measured side, inferred side의
  파생 통합값이며 evidence 원본 컬럼은 별도로 보존

두 물리 Surround run을 이용한 post-hoc target validation:

- 총 `90` epoch
- 실제 side `60`, 실제 center `30`
- TP `59`, FN `1`, FP `0`, TN `30`
- side sensitivity `98.3%`
- center specificity `100.0%`
- measured direction evaluable `24`, correct `24`
- 누락은 첫 run의 `LEFT_C` epoch 30 한 개
- 산출물:
  - `logs/tobii/multimonitor_validation/surround_coarse_external_validation_20260727_epochs.csv`
  - `logs/tobii/multimonitor_validation/surround_coarse_external_validation_20260727_summary.json`

이 값은 동일 참가자·동일 target-task 자료를 보면서 threshold를 정한
사후 검증이다. 본실험 분류 정확도로 보고하지 않고 X-Plane pilot을
진행할 근거로만 사용한다.

center-only regression:

- 기준 session: X-Plane `xplane_self_tobii_20260720_142508`, Tobii
  `session_xplane_tobii_20260720_142554_gaze.csv`
- 기존/신규 모두 gaze `19,408`, valid `14,780`,
  operational external `15,694`
- 모든 AOI count와 기존 trial 핵심 지표가 일치

다음 단계:

- Surround를 아직 해제하지 않는다.
- 새 분석모드로 짧은 X-Plane pilot을 수집한다.
- 중앙 계기 AOI 정확도와 measured/inferred/unresolved episode를 확인한다.
- pilot 실패 시 extended desktop과 center-only calibration을 복원한다.
- pilot 통과 시 threshold를 동결한 뒤 본실험에서도 evidence 구성요소를
  분리 보고한다.

## 2026-07-28 X-Plane cockpit 무입력 음성 cue 검증 도구

목적:

- 표적 화면에서 정한 coarse side-external rule을 실제 X-Plane cockpit에서
  독립적으로 확인
- 참가자 keypress 없이 cue 정답시각 확보
- 중앙 계기 AOI와 좌우 external event를 한 run에서 함께 점검

추가 파일:

- `tools/tobii/run_surround_cockpit_validation.ps1`
- `tools/tobii/analyze_surround_cockpit_validation.py`
- `TOBII_SURROUND_COCKPIT_PILOT_260728.md`

음성 환경:

- `System.Speech` 목록 조회에서는 음성이 보였지만 실제 `SelectVoice`가
  실패해 이 경로는 사용하지 않는다.
- Windows SAPI COM `SAPI.SpVoice`에서 음성 token 열거와 한국어 token
  선택 성공
- 설치 한국어 음성: `Microsoft Heami Desktop - Korean`, `ko-KR`
- 영어 fallback 음성: `Microsoft Zira Desktop`, `en-US`

cue runner:

- participant keypress 없음
- X-Plane 위 overlay 없음
- 기본 countdown `8 s`
- cue별 settle `1.5 s`
- measure `2.5 s`
- inter-cue `0.5 s`
- cue `9개`, 전체 약 `49 s`
- cue 순서:
  `ATTITUDE -> LEFT -> AIRSPEED -> RIGHT -> ALTITUDE -> LEFT ->
  CENTER_OUTSIDE -> RIGHT -> ATTITUDE`
- cue마다 `CUE_START`, `MEASURE_START`, `MEASURE_END`, `CUE_END`를 즉시
  CSV flush
- output:
  `logs/tobii/cockpit_validation/surround_cockpit_validation_<timestamp>_cues.csv`

cue/gaze analyzer:

- version `260728_v1`
- cue PC timestamp와 일반 Tobii gaze logger의 `pc_time_sec`를 병합
- 기존 `5760x1080` Surround hybrid classifier 재사용
- 산출물:
  - `*_cue_results.csv`
  - `*_summary.json`
  - `*_gaze_classified.csv`
  - `*_external_episodes.csv`
- pass gate:
  - cue sample coverage 각각 `80%` 이상
  - side sensitivity `90%` 이상
  - center specificity `90%` 이상
  - expected center AOI dwell success `80%` 이상
- status는 `PILOT_PASS`, `PILOT_FAIL`, `DATA_INCOMPLETE`로 분리

자동 검증:

- PowerShell script parse 성공
- Python compile 성공
- 무음 단축 dry-run에서 총 `40` row:
  - `CUE_START 9`
  - `MEASURE_START 9`
  - `MEASURE_END 9`
  - `CUE_END 9`
  - `SESSION_END 1`
- synthetic gaze는 direct LEFT/RIGHT와 loss-only inferred LEFT/RIGHT를
  혼합했다.
- end-to-end 결과:
  - `PILOT_PASS`
  - side `4/4`
  - center specificity `5/5`
  - center AOI dwell `5/5`
  - direction when evaluable `2/2`
  - minimum cue sample coverage 약 `99.1%`

운용:

- 실제 cockpit pilot에는 Java receiver가 필요하지 않다.
- Tobii logger를 먼저 실행하고, X-Plane을 `5760x1080` full-screen으로
  foreground에 둔 뒤 cue runner를 실행한다.
- 완료 음성 뒤 logger를 `Ctrl+C`로 중지한다.
- 실제 cue/gaze 분석이 통과하기 전에는 natural intruder 본자료를
  수집하지 않는다.
- raw X-Plane/Tobii CSV schema와 기존 실험 원본은 변경하지 않았다.

## 2026-07-28 Surround X-Plane FOV 및 실제 화면 확인

- X-Plane preference에서 Surround 해상도 `5760x1080`에 기존 단일 화면
  FOV `60 deg`가 그대로 적용된 것을 확인했다.
- 사용자가 lateral FOV를 `122 deg`로 조정했고, 저장된 값은
  `FOVx=122`, `FOVy=37.377142`, 세 방향 offset `0`이다.
- X-Plane screenshot:
  `Cessna_172SP - 2026-07-28 14.00.18.png`
- 전체 캡처에서 중앙 `x=1920..3839`를 잘라
  `build_atc_tmp/surround_fov122_center_260728.png`를 생성했다.
- 기존 `AOI그림.png`와 비교하면 현재 계기판이 약 `30-50 px` 아래에
  있지만 여섯 계기 중심은 모두 고정 AOI box 안에 남아 있다.
- 따라서 FOV/view를 더 변경하지 않고 실제 cockpit audio-cue pilot을
  진행하기로 판정했다.

PowerShell 5.1 호환 보완:

- 현재 cue runner가 BOM 없는 UTF-8 파일임을 확인했다.
- Windows PowerShell 5.1의 한글 source decoding 문제를 피하기 위해
  음성 문구를 ASCII-safe UTF-8 Base64 상수로 바꾸고 runtime decode한다.
- parse 검증 성공, script 내 non-ASCII source character `0`.
- 무음 단축 regression에서 전체 `40` row와 한글 첫 cue
  `자세계를 바라보십시오.` 복원을 확인했다.

## 2026-07-28 실제 cockpit 1차 cue 분석 및 동기식 음성 수정

입력:

- cue:
  `logs/tobii/cockpit_validation/surround_cockpit_validation_20260728_141201_cues.csv`
- gaze:
  `logs/tobii/session_xplane_tobii_20260728_140939_gaze.csv`

자동 분석:

- status `PILOT_FAIL`
- 모든 cue sample coverage `98.5%` 이상
- side detection `4/4`, sensitivity `100%`
- center specificity `4/5`, `80%`
- center AOI dwell `3/5`, `60%`
- direction evaluable `2`, correct `2`
- mean valid gaze rate `59.2%`

해석:

- 좌우 external 검출 자체는 네 번 모두 성공했다.
- cue 1 ATTITUDE와 cue 3 AIRSPEED가 dwell 실패했다.
- cue 3 측정 시작 뒤 약 `1.116 s` 동안 이전 cue의 measured LEFT가
  남아 있어 사용자가 보고한 늦은 수행과 일치한다.
- cue 9 ATTITUDE는 valid `121`개 중 `117`개가 ATTITUDE였으므로
  FOV 122에서 AOI 정렬 자체는 정상이다.
- 이 run은 Surround 실패 판정 자료가 아니라 timing 문제 진단 자료로
  보존한다.

원인 및 수정:

- SAPI `Speak(text, 3)`은 async `1`과 purge `2`의 조합이다.
- 음성 호출 직후 반환되어 음성이 끝나기 전부터 settle `1.5 s`가
  감소하고 있었다.
- runner version을 `260728_v2_sync_speech`로 올렸다.
- cue 음성은 synchronous purge flag `2`로 끝까지 재생한다.
- `SPEECH_END`를 기록한 뒤 settle `1.5 s`, measure `2.5 s`를 진행한다.
- v2 무음 regression은 `CUE_START/SPEECH_END/MEASURE_START/
  MEASURE_END/CUE_END` 각 9개, 전체 `49` row로 통과했다.
- 수정된 실제 cockpit pilot 1회를 다시 수행해야 한다.

## 2026-07-28 동기식 음성 2차 cockpit pilot 통과

입력:

- cue:
  `logs/tobii/cockpit_validation/surround_cockpit_validation_20260728_142411_cues.csv`
- gaze:
  `logs/tobii/session_xplane_tobii_20260728_142328_gaze.csv`
- runner `260728_v2_sync_speech`
- cue row `49`, `SPEECH_END` `9`

자동 분석 결과:

- status `PILOT_PASS`
- minimum sample coverage `93.6%`
- side detection `4/4`, sensitivity `100%`
- center specificity `5/5`, `100%`
- center target dwell `5/5`, `100%`
- direction evaluable `3`, correct `3`
- mean valid gaze rate `67.8%`

세부 해석:

- 모든 center cue에서 side false positive `0`
- center target의 valid sample 대비 적중률 `89.0~99.3%`
- LEFT 1회, RIGHT 2회는 measured direction이 정확했다.
- 두 번째 LEFT는 measured side coordinate 없이 qualifying loss episode로
  검출되어 `INFERRED_SIDE_UNKNOWN`이며 방향 평가에서 제외됐다.
- coarse center/side external 목적에는 통과했지만 side monitor의 정밀
  좌표 추적이 검증된 것은 아니다.

결정:

- Surround `5760x1080` 유지
- X-Plane FOV `122 deg`, visual offset `0` 유지
- 현재 Spark wide calibration 유지
- hybrid classifier threshold 고정
- 다음은 짧은 natural intruder trial 1회
- 결과 보고 시 measured/inferred/unresolved evidence를 분리

## 2026-07-28 Surround natural intruder diagnostic 분석

수집 파일:

- Java `session_surround_tobii_20260728_144151`
- X-Plane prefix
  `logs/xplane/xplane_surround_tobii_20260728_144151`
- Tobii
  `logs/tobii/session_xplane_tobii_20260728_144218_gaze.csv`

수집 상태:

- STATE/INTRUDER 각 `413` row
- event `29` row
- gaze `4,965` row
- valid gaze `2,995`, `60.3%`
- trial 4와 5는 gaze 시간에 완전히 포함
- Java `CLEAN` trial `2개`
- trial 3 incomplete 표시는 새 reset 전 old trial ID가 붙은 pre-roll sample
  `46개` 때문이며 실제 수행 trial이 아니다.

trial 4:

- approach right
- minimum horizontal distance `10.510 m`
- advisory-to-response `0.648 s`
- valid gaze `47.9%`
- operational external `40.9%`
- spawn 전 500 ms에 이미 external

trial 5:

- approach left
- minimum horizontal distance `10.221 m`
- advisory-to-response `1.148 s`
- valid gaze `63.1%`
- operational external `52.7%`
- spawn 전 500 ms에 이미 external

spawn 이후 `0~5 s` evidence:

- trial 4: center outside measured `22.3%`, unresolved loss `75.7%`
- trial 5: center outside measured `7.6%`, inferred side unknown `91.0%`
- 두 trial 모두 spawn 이후 measured LEFT/RIGHT direction은 없음

해석:

- 현재 pipeline은 Surround evidence와 X-Plane event 병합에 성공했다.
- 하지만 두 trial 모두 spawn 전에 외부를 보고 있었으므로
  spawn-to-external `0.012 s`, `0.002 s`는 인지/획득시간이 아니다.
- v22 countdown 표시와 계기 유지 과제 부재로 인한 외부 편향 문제가
  그대로 확인됐다.
- 장시간 tracking loss는 외부일 가능성이 있어도 방향이나 intruder
  fixation으로 확정할 수 없다.
- 이번 세션은 system diagnostic으로만 보존하고 본자료로 사용하지 않는다.

spawn 기준 analyzer 보완:

- `tools/tobii/analyze_xplane_tobii_session.py`
- version `260728_spawn_external_v2`
- 기존 advisory 기준 출력 유지
- `spawn_to_first_operational_external_s` 추가
- `operational_external_before_spawn` 추가
- spawn 기준 8개 time-bin rate 추가
- aggregate에도 같은 metric 추가
- v1/v2 공통 trial-summary `104개` field 비교 결과 변경 `0`
- raw X-Plane/Tobii CSV 변경 없음

다음 구현 순서:

1. audio start 뒤 독립적 `3~10 s` spawn
2. `INTRUDER_SPAWNED` 기준 pilot response detector
3. 소규모 재검증 후에만 본자료 수집

## 2026-07-28 Lua v23 participant 화면 분리

변경 파일:

- `FLYWITHLUA_STUDY_INTEGRATED.lua`
- `FLYWITHLUA_STUDY_INTEGRATED_260728_participant_display_hidden_v23.lua`

구현 내용:

- `STUDY_SCRIPT_VERSION`을 `260728_participant_display_hidden_v23`으로 갱신했다.
- `operator_overlay_visible`은 기본 `false`이다.
- overlay가 꺼져 있으면 `study_draw_status()`가 즉시 반환하므로 countdown,
  crossing 상태, 좌표/거리/event 정보와 `TRAFFIC ALERT`/`CHECK OUTSIDE`가
  참가자 화면에 그려지지 않는다.
- `study_start_trial()`이 overlay를 항상 `false`로 만들어 실험 시작 전
  점검 표시가 켜져 있었더라도 participant trial에는 남지 않는다.
- active trial 중 overlay toggle 요청은 무시한다.
- trial 밖에서는 `flywithlua/study/toggle_operator_overlay` command와
  `Study Toggle Operator Overlay` macro로 기존 전체 표시를 점검할 수 있다.
- 내부 `visual_advisory_active` 갱신, UDP packet, event 기록, Java clean 판정,
  end-trial guard는 변경하지 않았다.
- CSV schema, raw experimental CSV, crossing delay와 Java response detector는
  이번 단계에서 변경하지 않았다.

검증:

- 활성 Lua와 v23 snapshot SHA-256이 일치한다.
- `git diff --check`가 통과했다.
- Git 기준본과 실제 X-Plane `Scripts` 실행본 SHA-256이 일치한다.
- 파일 동기화 시점에도 X-Plane은 실행 중이었지만 `Log.txt` 갱신은 없었으므로
  메모리에 올라간 Lua는 아직 v23으로 재로드되지 않았다.
- X-Plane에서 `Plugins > FlyWithLua > Reload all Lua script files`를 실행한 뒤
  `script loaded: 260728_participant_display_hidden_v23`과 오류 부재를 확인한다.

## 2026-07-28 Lua v24 최소 trial 상태표시

v23 재로드 확인:

- X-Plane `Log.txt`에
  `script loaded: 260728_participant_display_hidden_v23`이 기록됐다.
- 재로드 이후 FlyWithLua stop/error는 발견되지 않았다.
- 실제 화면에서 모든 표시가 사라져 운영 확인 정보까지 함께 숨겨지는 문제가
  확인됐다.

v24 보완:

- version: `260728_minimal_trial_status_v24`
- snapshot: `FLYWITHLUA_STUDY_INTEGRATED_260728_minimal_trial_status_v24.lua`
- participant 화면에는 다음 한 줄 상태만 표시한다.
  - `TRIAL IDLE`
  - `TRIAL RESET: <id>`
  - `TRIAL ACTIVE: <id>`
  - `TRIAL END: <id>`
- reset/start/end 수락 여부만 알 수 있고 random delay, intruder/scenario 상태,
  좌표, 거리, event 수와 visual advisory는 계속 숨긴다.
- end guard가 종료를 거부하면 상태가 `TRIAL ACTIVE`로 남으므로 trial이 닫히지
  않았음을 확인할 수 있지만 거부 원인이나 intruder 정보는 노출하지 않는다.
- full operator overlay는 기존과 같이 trial 밖에서만 사용할 수 있다.
- Git 기준본, v24 snapshot, 실제 X-Plane `Scripts` 파일의 SHA-256이 일치한다.
- 현재 X-Plane 메모리에는 v23이 올라가 있으므로 active trial 종료 후 FlyWithLua
  전체 재로드가 필요하다.

## 2026-07-28 Lua v25 조기 end 거부 알림

v24 현장 확인:

- `Log.txt`에서 `script loaded: 260728_minimal_trial_status_v24`를 확인했다.
- `RESET`, `ACTIVE`, `END` 최소 상태표시는 정상 동작했다.
- trial 1과 2에서 intruder 통과 전에 end를 누르면 기존 guard가
  `ignored=end_before_trial_closed`를 정상 기록했다.
- 그러나 participant 화면은 계속 `TRIAL ACTIVE`만 보여 end 입력이 접수됐지만
  거부됐다는 점이 불명확했다.

v25 보완:

- version: `260728_end_wait_notice_v25`
- snapshot: `FLYWITHLUA_STUDY_INTEGRATED_260728_end_wait_notice_v25.lua`
- 조기 end 거부 시 `TRIAL IN PROGRESS - END NOT READY`를 `2.5초` 표시한다.
- 표시가 끝나면 자동으로 기존 `TRIAL ACTIVE: <id>`로 돌아간다.
- active trial이 없을 때 end를 누르면 `NO ACTIVE TRIAL`을 `2.5초` 표시한다.
- 알림에는 randomized delay, intruder, hazard, advisory 또는 구체적인 guard
  원인을 노출하지 않는다.
- 기존 end guard 판단, `MANUAL_NOTE` detail, 정상 `TRIAL_END`, UDP/CSV schema,
  Java 분석은 변경하지 않았다.
- Git 기준본, v25 snapshot, 실제 X-Plane `Scripts` 파일의 SHA-256이 일치한다.
- X-Plane 메모리에는 아직 v24가 올라가 있으므로 FlyWithLua 전체 재로드가 필요하다.

## 2026-07-28 표준 task audio 및 event v26

음성 자산:

- 파일:
  `resources/audio/apisat_task_maintain_70kias_centerline_en_us.wav`
- 생성기: `tools/audio/generate_standard_task_audio.ps1`
- 실제 FlyWithLua 경로:
  `Scripts/AISimulationProjectAssets/apisat_task_maintain_70kias_centerline_en_us.wav`
- voice: `Microsoft Zira Desktop - English (United States)`
- rate `1`, volume `100`
- mono, 16-bit PCM, `22050 Hz`
- duration `6.493초`
- 문구:
  `Cessna zero one, continue straight in, maintain seven zero knots, track runway centerline.`

Lua:

- version: `260728_standard_task_audio_v26`
- snapshot: `FLYWITHLUA_STUDY_INTEGRATED_260728_standard_task_audio_v26.lua`
- `TRIAL_START` detail에 `protocol=audio_task_v26` 기록
- trial 시작 뒤 고정 baseline `3.0초`
- 재생 직전 `TASK_COMMAND_AUDIO_START`
- 검증된 WAV 길이 경계에서 `TASK_COMMAND_AUDIO_END`
- end detail에 scheduled/actual wall duration 기록
- WAV 미로드 시 trial start 거부와 `TASK AUDIO NOT READY`
- audio armed/active 동안 end-trial guard 유지
- `Study Test Task Audio`는 trial 밖에서만 재생하며 task event를 만들지 않음
- audio countdown이나 상태는 participant 화면에 표시하지 않음

Java/분석:

- `XPlaneEventType`에 `TASK_COMMAND_AUDIO_START/END` 추가
- Java trial summary/batch CSV에 audio start/end, start-to-audio,
  audio duration, audio-to-spawn 추가
- `analyze_xplane_tobii_session.py` version
  `260728_task_audio_v3`
- gaze trial/aggregate에도 같은 audio timing 추가
- `audio_task_*` protocol trial은 audio start/end 각 1회와 순서를 요구
- historical session은 protocol이 없으면 audio event를 요구하지 않음

검증:

- Java 21 compile 통과
- Python AST parse 통과
- PowerShell script parse 통과
- WAV 형식/길이 확인
- `git diff --check` 통과
- historical Surround diagnostic은 기존과 동일하게 total `3`, clean `2`,
  pre-roll incomplete `1`
- Git Lua/snapshot과 실제 X-Plane Lua hash 일치
- Git WAV와 실제 FlyWithLua WAV hash 일치

아직 변경하지 않은 항목:

- crossing delay는 trial start 기준 `6~14초`
- Java pilot response는 advisory 기준
- visual advisory는 숨겼지만 Java internal advisory event는 계속 기록

다음 단계:

1. X-Plane에서 v26 reload와 `Study Test Task Audio` 청취 확인
2. 짧은 trial로 audio start/end CSV timing 확인
3. audio start 기준 독립적인 `3~10초` spawn 구현

## 2026-07-28 audio 시작 기준 intruder spawn v27

Lua:

- version: `260728_audio_anchored_spawn_v27`
- snapshot:
  `FLYWITHLUA_STUDY_INTEGRATED_260728_audio_anchored_spawn_v27.lua`
- protocol: `audio_task_v27`
- 기존 trial 시작 후 `3.0초` audio baseline과 6.493초 WAV 유지
- 음성 재생 성공 직후 `U(3,10초)`를 독립 추출하고 crossing 시나리오 arm
- `SCENARIO_SELECTED` detail에 audio-start anchor, planned delay,
  audio-start sim time, trigger sim time 기록
- trigger 시 intruder를 X-Plane에 처음 배치한 직후
  `INTRUDER_SPAWNED` 기록
- 기존 0.35초 stabilization은 이동 시작 전에 계속 적용하지만 spawn event를
  지연하지 않음
- spawn detail에 `spawn_phase=stabilization_start`, planned/actual
  audio-to-spawn과 기존 approach/path parameter 기록
- `TRIAL_START` detail에 master/interior/engine/prop/enviro/radio
  X-Plane 음량 비율 기록

Java/Python 분석:

- Java session summary와 batch CSV에 planned/actual audio-to-spawn,
  timing-valid flag, 6개 음량 비율 추가
- `analyze_xplane_tobii_session.py` version:
  `260728_audio_anchored_spawn_v4`
- gaze trial/aggregate에 같은 timing field를 추가하고 trial row에는
  6개 음량 비율을 추가
- v27 event 순서:
  `TRIAL_START < TASK_COMMAND_AUDIO_START < SCENARIO_SELECTED <
  INTRUDER_SPAWNED < TRIAL_END`
- historical protocol은 v27 필드를 비워 두며 기존 clean 판정을 유지

검증:

- 설치된 X-Plane 11 `DataRefs.txt`에서 6개 sound dataref 확인
- Java 21 compile 통과
- Python AST parse 통과
- `git diff --check` 통과
- historical Surround diagnostic은 기존과 동일하게 total `3`, clean `2`,
  incomplete `1`
- 활성 Lua와 v27 snapshot SHA-256 일치
- 현재 X-Plane `Log.txt`의 마지막 v26 trial 3은 `TRIAL_END`까지 닫힘
- UDP `9100`은 비어 있어 현재 `XPlaneReceiverMain`은 실행 중이 아님

다음 단계:

1. Git 기준 v27 Lua를 실제 FlyWithLua `Scripts` 실행본으로 복사
2. X-Plane에서 FlyWithLua 전체 reload
3. Java receiver만 새 timestamp로 실행하고 3회 짧은 timing pilot
4. planned/actual delay `3~10초`, spawn event 위치, 음량 context 검증
5. 그 뒤 Java response anchor를 `ADVISORY_SHOWN`에서
   `INTRUDER_SPAWNED`로 변경

## 2026-07-29 v27 timing pilot 실측

세션:

- session: `session_audio_spawn_20260729_130859`
- prefix: `logs/xplane/xplane_audio_spawn_20260729_130859`
- STATE `768`, INTRUDER `768`, EVENT `47`
- 종료 시 `RECEIVER_STOP`, `SESSION_STOP` 정상 기록

세 번의 실제 timing:

- planned `8.435초`, actual `8.445초`, 오차 `+10 ms`
- planned `9.918초`, actual `9.938초`, 오차 `+20 ms`
- planned `9.272초`, actual `9.275초`, 오차 `+3 ms`

모두 `3~10초` 범위이며 `spawn_phase=stabilization_start`였다.

운용 오류:

- 두 번째 실행 전에 `Study Reset Trial`을 누르지 않았다.
- 첫 번째와 두 번째 실행이 `trial_id=1`에 합쳐졌다.
- 최종 Java trial ID 판정은 total `2`, clean `1`, incomplete `1`
- incomplete reason: `duplicate TRIAL_START count=2`
- timing 구현 검증에는 세 물리 실행을 사용할 수 있지만, 병합된 두 실행은
  독립 trial 자료로 사용하지 않는다.
- 세 번의 추출만으로 균등분포 자체를 검증했다고 주장하지 않는다.

## 2026-07-29 spawn 기준 pilot response detector v1

변경 파일:

- `src/com/example/ai/XPlaneAutoEventDetector.java`
- `src/com/example/ai/XPlaneStateReceiver.java`
- `src/com/example/ai/XPlaneEventType.java`
- `src/com/example/ai/XPlaneSessionAnalysisMain.java`
- `src/com/example/ai/XPlaneBatchAnalysisMain.java`
- `src/com/example/ai/XPlaneAutoEventDetectorSmokeTest.java`
- `tools/tobii/analyze_xplane_tobii_session.py`

검출 규칙:

- 새 event: `PILOT_RESPONSE_BASELINE`
- anchor: `INTRUDER_SPAWNED`
- method: `pre_spawn_mean_sustained_delta_v1`
- spawn 전 요청 baseline window: `1.0초`
- baseline valid 최소 sample: `3`
- 현재 약 9 Hz 수집에서는 보통 `9~10` sample 예상
- threshold: pitch `0.08`, roll `0.08`, yaw `0.05`, throttle `0.08`
- persistence: `0.25초`
- persistence 확인 후에도 `PILOT_RESPONSE_START` 시각은 최초 threshold 통과 sample
- response end: 기존 8 settle sample 또는 `TRIAL_END` 직전 강제 종료
- `active_control_at_spawn`은 baseline 구간 peak-to-peak가 threshold를 넘은
  경우이며, 침입기 회피반응 확정이 아니라 교란/context flag

no-alert event 의미:

- Java 자동 advisory 생성 제거
- 새 trial의 내부 위험창은 `HAZARD_DETECTED/CLEARED`
- `ADVISORY_SHOWN/CLEARED` enum/parser와 수동 입력은 과거자료 및 명시적
  advisory 실험 호환을 위해 유지
- response detector는 hazard/advisory 상태와 독립

새 recording-complete 규칙:

- 필수: reset, start, scenario, spawn, valid response baseline,
  minimum distance, trial end
- hazard/advisory/response는 발생하지 않을 수 있는 outcome
- 선택 event pair가 발생하면 start/clear 또는 start/end가 모두 있고
  시간 순서가 맞아야 함
- historical CSV에 `PILOT_RESPONSE_BASELINE`이 없으면 기존 strict rule 유지

분석 출력:

- `spawn_to_response_s`
- response anchor/method
- baseline valid/sample count/window
- `active_control_at_spawn`
- response trigger axis
- 실제 persistence
- Tobii analyzer version:
  `260729_spawn_response_v5`

검증:

- Java 21 compile 통과
- `XPlaneAutoEventDetectorSmokeTest` 통과
- sustained response 최초시점 소급 기록 통과
- transient/no-response 처리 통과
- active-at-spawn flag 통과
- trial-end response closure 순서 통과
- synthetic Java/Python 각 `3/3` clean
- 기존 v27 timing session 결과 유지
- 기존 Surround session `2/3` clean 유지
- `git diff --check` 통과

다음 단계:

1. 새 timestamp로 Java receiver 실행
2. Lua 재로드 없이 실제 X-Plane trial 1회 수행
3. `PILOT_RESPONSE_BASELINE`, spawn-to-response, no automatic advisory 확인
4. 수동 영상/조종감각과 response threshold를 비교

## 2026-07-29 110~130 KIAS 무작위 속도 유지 과제 v28

사용자 결정:

- 속도 지시는 실험 의도대로 `110`, `115`, `120`, `125`, `130 KIAS`를
  사용한다.
- 선택 간격은 `5 KIAS`이고 trial별 균등 무작위 선택이다.
- 이 범위는 C172의 현실적인 최종접근 속도라고 주장하지 않고,
  계기 주의분산을 유도하는 실험용 concurrent speed-maintenance task로
  정의한다.

추가 자산:

- `tools/audio/generate_speed_task_audio_set.ps1`
- `resources/audio/apisat_task_maintain_110kias_centerline_en_us.wav`
- `resources/audio/apisat_task_maintain_115kias_centerline_en_us.wav`
- `resources/audio/apisat_task_maintain_120kias_centerline_en_us.wav`
- `resources/audio/apisat_task_maintain_125kias_centerline_en_us.wav`
- `resources/audio/apisat_task_maintain_130kias_centerline_en_us.wav`

WAV 공통 형식은 Microsoft Zira, rate `1`, volume `100`, mono 16-bit PCM,
`22050 Hz`이다. 검증 길이는 순서대로 `6.583401`, `6.543447`,
`6.593469`, `6.568435`, `6.608345초`이다.

Lua:

- version: `260729_random_speed_task_v28`
- snapshot: `FLYWITHLUA_STUDY_INTEGRATED_260729_random_speed_task_v28.lua`
- protocol: `audio_task_v28`
- trial 시작 시 5개 target 중 하나를 추첨하고 해당 trial 동안 고정
- `TRIAL_START`, `TASK_COMMAND_AUDIO_START/END`에
  `target_speed_kias`와 선택 WAV 기록
- 5개 WAV 중 하나라도 준비되지 않으면 trial 시작 거부
- 기존 trial start 후 `3초` audio baseline과 audio start 후 독립
  `U(3,10초)` intruder spawn 유지

분석:

- Java session/batch CSV에 목표 속도, audio start IAS, spawn IAS,
  audio-start~spawn 평균 절대 속도오차, `±5 KIAS` 유지율 추가
- `ias_mps`는 legacy CSV header를 유지하지만 실제 해석 단위는 KIAS
- Tobii analyzer version:
  `260729_random_speed_task_v6`
- Tobii trial summary에 `target_speed_kias` 보존
- 새 검증:
  `src/com/example/ai/XPlaneRandomSpeedTaskAnalysisSmokeTest.java`

검증 결과:

- 5개 WAV 형식/길이 확인
- Java 21 compile 통과
- `XPlaneAutoEventDetectorSmokeTest` 통과
- `XPlaneRandomSpeedTaskAnalysisSmokeTest` 통과
- 합성 `120 KIAS` trial에서 audio start `115`, spawn `120`,
  MAE `1.667`, ±5 KIAS 유지율 `1.000` 확인
- batch CSV field/value 정렬 확인
- Python compile 및 v28 Tobii 병합에서
  `target_speed_kias=120`, timing valid `1` 확인
- 기준 Lua/snapshot SHA-256 일치
- Git Lua와 실제 FlyWithLua Lua hash 일치
- 5개 WAV도 Git/runtime hash 모두 일치

다음 단계:

1. X-Plane에서 FlyWithLua 전체 reload
2. 새 timestamp의 Java receiver와 Tobii logger 실행
3. `Reset -> Start -> 시나리오 종료 후 End`로 실제 trial 1회 수행
4. 선택 속도 음성, target event, response baseline, spawn timing,
   speed-performance 지표를 함께 검토

## 2026-07-29 v28 실제 X-Plane/Tobii pilot

세션:

- Java session: `session_random_speed_v28_20260729_135933`
- X-Plane prefix:
  `logs/xplane/xplane_random_speed_v28_20260729_135933`
- Tobii:
  `logs/tobii/session_xplane_tobii_20260729_135834_gaze.csv`
- 종료 row: STATE `345`, INTRUDER `345`, EVENT `17`, gaze `15002`
- `RECEIVER_STOP`, `SESSION_STOP` 정상 기록

trial 결과:

- total/clean: `1/1`
- approach: left
- target: `130 KIAS`
- planned/actual audio-to-spawn: `3.061/3.079초`
- response baseline: valid, `10` samples, `0.968초`
- `active_control_at_spawn=false`
- spawn-to-response: `2.217초`
- trigger axis: pitch
- advisory: `0/0`
- hazard: `1/1`, window `2.977초`
- 최소 수평/수직 분리: `8.629/10.976 m`

속도 과제:

- audio start IAS: `111.762 KIAS`
- spawn IAS: `113.502 KIAS`
- pre-spawn MAE: `17.305 KIAS`
- pre-spawn ±5 KIAS 유지율: `0`
- minimum-distance IAS: `120.770 KIAS`
- trial-end IAS: `122.348 KIAS`
- target 음성 길이 `6.608초`보다 spawn이 먼저 발생했고, audio end는
  spawn보다 `3.529초` 뒤였다.
- 따라서 이번 pre-spawn 오차는 단독으로 지시 불이행을 뜻하지 않는다.
- 초기 IAS 대비 `130 KIAS`는 약 `+18 KIAS` 과제인 반면 `110 KIAS`
  target은 거의 즉시 만족될 수 있어 target별 난이도가 동일하지 않다.

Tobii 병합:

- analyzer: `260729_random_speed_task_v6`
- full-session valid: `83.2%`
- trial valid: `86.1%`
- trial operational external: `31.5%`
- center-outside measured: `18.6%`
- inferred-side unknown: `9.8%`
- measured-side right: `3.1%`
- spawn 직전 AOI: `AIRSPEED`
- spawn 후 첫 operational external: `0.795초`
- spawn 후 직접 측정된 side-monitor episode: 없음

해석 제한:

- `0.795초`는 coarse external transition이며 intruder 확정 발견시점이
  아니다.
- intruder는 left 접근이지만 직접 측정된 right-side episode는 spawn
  `2.202~1.683초` 전에 발생했다.
- left intruder fixation 또는 좌측 모니터에서의 정확한 gaze를 주장하지
  않는다.
- 현재 `visualize_tobii_gaze.py`는 Surround 중앙 모니터 좌표 remap이
  없으므로 이 세션에 기존 cockpit SVG를 생성하면 AOI 위치가 틀어진다.

생성된 병합 결과:

- `build_atc_tmp/xplane_random_speed_v28_20260729_135933_visual_gaze_aoi.csv`
- `build_atc_tmp/xplane_random_speed_v28_20260729_135933_visual_external_episodes.csv`
- `build_atc_tmp/xplane_random_speed_v28_20260729_135933_visual_trial_gaze_summary.csv`
- `build_atc_tmp/xplane_random_speed_v28_20260729_135933_visual_trial_aggregate_summary.csv`

본실험 전 결정할 항목:

1. 속도 수행 평가구간을 pre-spawn만 쓸지, audio end 이후 또는
   spawn~minimum-distance 구간을 함께 쓸지 결정
2. initial IAS 대비 target difficulty를 층화/공변량 처리할지,
   target 선택 규칙을 보정할지 결정
3. Surround-aware SVG 좌표 remap 추가
4. legacy `first_response_axis` 대신 새 `response_trigger_axis`를
   대표 축으로 사용

## 2026-07-29 실험 보완 목적 확인 및 속도 범위 v29 수정

현재 수행 중인 작업의 성격:

- 본실험 참가자 자료 수집 전 experiment refinement 단계
- X-Plane/Lua/Java/Tobii 기록 파이프라인 검증
- 음성 과제 난이도, intruder timing, response 기준, 시선 분류를
  물리 pilot으로 확인
- v28 1회 자료는 시스템/프로토콜 pilot이며 본실험 결과로 사용하지 않음

수정 근거:

- v28 pilot의 초기 IAS는 약 `112 KIAS`
- 무작위 target이 `130 KIAS`로 선택됨
- spawn이 audio start `3.079초` 뒤 발생하여 지시가 끝나기 전 노출됨
- trial end에도 IAS는 `122.348 KIAS`로 목표 ±5 KIAS에 들지 못함
- `110~130 KIAS`는 초기 IAS에 따라 목표별 난이도가 크게 불균형함

v29 변경:

- 활성 target: `90/95/100/105/110 KIAS`
- 간격: `5 KIAS`
- trial별 균등 무작위 선택
- version: `260729_revised_speed_task_v29`
- protocol: `audio_task_v29`
- snapshot:
  `FLYWITHLUA_STUDY_INTEGRATED_260729_revised_speed_task_v29.lua`
- 기존 audio baseline `3초`, audio-start 기준 spawn `U(3,10초)` 유지

활성 WAV:

- `90 KIAS`: `6.413379초`
- `95 KIAS`: `6.383447초`
- `100 KIAS`: `6.683401초`
- `105 KIAS`: `6.663447초`
- `110 KIAS`: `6.583401초`

과거 v28의 `115~130 KIAS` WAV는 재현성을 위해 보존하지만 v29 Lua는
로드하거나 선택하지 않는다.

분석/검증:

- Java protocol 허용 범위: v27/v28/v29
- Tobii analyzer: `260729_revised_speed_task_v7`
- v29 synthetic target: `100 KIAS`
- Java 21 compile 통과
- response detector smoke 통과
- v29 random-speed analysis smoke 통과
- Python v29 target/timing 병합 통과
- v28 실제 세션 `1/1 CLEAN` 회귀 유지
- Git/runtime Lua 및 활성 WAV 5개 SHA-256 일치

다음 단계:

1. X-Plane에서 FlyWithLua 전체 reload
2. trial 밖에서 `Study Test Task Audio` 1회로 새 범위 음성 확인
3. 필요하면 v29 짧은 trial 1회를 추가해 속도 범위 체감 확인
4. main collection 전 target difficulty와 속도 평가구간 최종 확정

## 2026-07-29 v29 reload 후 기능 확인 trial

X-Plane `Log.txt` 확인:

- loaded version: `260729_revised_speed_task_v29`
- selected target: `100 KIAS`
- audio duration planned/actual: `6.683/6.698초`
- audio-to-spawn planned/actual: `7.708/7.720초`
- audio end→spawn: `1.023초`
- approach: front
- Lua minimum distance: `45.004 m`
- `TRIAL_END` 정상

이 trial 전에는 Java receiver와 Tobii logger를 모두 종료한 상태였다.
따라서 새 `logs/xplane` 또는 `logs/tobii` CSV는 생성되지 않았으며,
이번 실행은 v29 음성 범위·Lua timing·trial closure 체감 확인으로만
보존한다. 반응시간, IAS 수행, 시선 분포 자료로 사용하지 않는다.

## 2026-07-29 v29 속도 범위 동결

사용자 체감 평가:

- v29에서 선택된 `100 KIAS` 지시의 난이도가 적당했음

동결 결정:

- target set: `90/95/100/105/110 KIAS`
- spacing: `5 KIAS`
- selection: trial별 균등 무작위
- 범위는 기능·안전 문제의 명확한 근거가 없는 한 추가 변경하지 않음
- 논문에서는 C172 표준 접근속도가 아니라 실험용 concurrent
  speed-maintenance task로 기술
- `target_speed_kias`, audio-start IAS, initial target error는 계속 저장

`APISAT_EXPERIMENT_SCENARIO_BASELINE_260724.md`도 v29 현재 구현과 동결
범위로 갱신했다.

본실험 전 남은 속도 관련 확인은 Java와 Tobii를 모두 실행한 v29 trial
1회뿐이다. 해당 trial이 정상 기록되면 속도 문구·WAV·범위·선택 규칙을
implementation-frozen 상태로 본다.

## 2026-07-29 v29 Java/Tobii 최종 기록 trial

파일:

- Java session: `session_revised_speed_v29_20260729_142254`
- X-Plane prefix:
  `logs/xplane/xplane_revised_speed_v29_20260729_142254`
- Tobii:
  `logs/tobii/session_xplane_tobii_20260729_142157_gaze.csv`
- rows: STATE `422`, INTRUDER `422`, EVENT `17`, gaze `12642`
- receiver shutdown 정상

trial 구조:

- trial 1: receiver pre-roll, trial event 없음
- trial 2: 실제 수행 trial, recording-complete
- trial 3: 종료 뒤 잘못 누른 reset-only tail
- analyzer total `3`, clean `1`, incomplete `2`를 실제 3회 수행으로
  해석하지 않는다.

trial 2:

- target: `100 KIAS`
- approach: left
- audio-to-spawn planned/actual: `7.309/7.324초`
- minimum horizontal/vertical separation: `13.825/37.433 m`
- audio-start/spawn IAS: `107.758/108.728 KIAS`
- pre-spawn MAE: `9.464 KIAS`
- pre-spawn ±5 KIAS rate: `0`
- response baseline: valid, 8 samples, `0.829초`
- `active_control_at_spawn=true`
- detector response: `3.031초`, throttle trigger

반응 검출 해석:

- audio end throttle: `0.0`
- spawn throttle: `0.0`
- detector response throttle: `0.173`
- response IAS: `104.154 KIAS`
- target `100 KIAS`로 감속하는 과제 중 throttle을 다시 조절한 동작일
  가능성이 높다.
- 따라서 `3.031초`를 intruder 회피반응시간으로 확정하지 않는다.
- event recording은 clean이지만 behavioral response validity는 confounded.

Tobii:

- full-session valid: `67.1%`
- trial valid: `71.7%`
- trial operational external: `40.4%`
- spawn 직전 AOI: `OUTSIDE_VIEW`
- spawn 시 이미 operational external
- spawn 후 measured RIGHT side: `+0.228~+0.977초`
- intruder approach: LEFT
- spawn 후 measured LEFT side: 없음
- gaze만으로 intruder 발견시점 또는 left-side fixation을 주장할 수 없음

결론:

- v29 속도 범위·음성·선택·기록 구현: 동결 유지
- raw input response detector: 아직 행동 기준 동결 불가
- 본실험 전 `recording_complete`와 `behavior_response_valid` 분리 필요
- 최소한 `active_control_at_spawn=true` trial은 primary latency에서 제외
- 다음 detector는 raw input 단독보다 aircraft state/trajectory 변화와
  predicted separation 개선을 함께 사용하고 수동 검증해야 함

생성된 결과:

- `build_atc_tmp/xplane_revised_speed_v29_20260729_142254_visual_gaze_aoi.csv`
- `build_atc_tmp/xplane_revised_speed_v29_20260729_142254_visual_external_episodes.csv`
- `build_atc_tmp/xplane_revised_speed_v29_20260729_142254_visual_trial_gaze_summary.csv`
- `build_atc_tmp/xplane_revised_speed_v29_20260729_142254_visual_trial_aggregate_summary.csv`

## 2026-07-29 group-meeting video pilot analysis

Analyzed files:

- `logs/xplane/xplane_groupmeeting_demo_v29_20260729_145522.csv`
- `logs/xplane/xplane_groupmeeting_demo_v29_20260729_145522_intruder.csv`
- `logs/xplane/xplane_groupmeeting_demo_v29_20260729_145522_events.csv`
- `logs/tobii/session_xplane_tobii_20260729_145503_gaze.csv`

Session integrity:

- STATE `2661`, INTRUDER `2661`, EVENT `172`, gaze `30035`
- full-session valid gaze `20897/30035 = 69.6%`
- receiver and gaze logger stopped cleanly
- trial `27`: receiver pre-roll
- performed attempts: trials `28` through `40`, total `13`
- recording-complete: `11/13`
- incomplete: trial `29` and trial `40`, both missing `TRIAL_END`

Preferred presentation trial: `33`

- local-clock interval: `14:58:21.511` reset to `14:58:43.224` end
- approach: front
- target: `100 KIAS`
- planned/actual audio-to-spawn: `5.775/5.785 s`
- audio-start/spawn IAS: `104.930/100.826 KIAS`
- pre-spawn MAE: `2.564 KIAS`
- pre-spawn within-`5 KIAS` rate: `100%`
- response baseline: valid, `active_control_at_spawn=false`
- detector response: roll, spawn `+10.298 s`
- hazard: spawn `+9.849 s`
- minimum sampled horizontal/vertical separation: `20.258/8.547 m`
- gaze validity: `86.5%`
- operational external-view rate: `57.9%`
- pre-spawn AOI: `VERTICAL_SPEED`
- first operational external view: spawn `+0.546 s`
- directly measured side-monitor rate: `0%`
- inferred side-view rate: `7.1%`

Interpretation:

- Trial `33` is the best presentation candidate because task performance was
  accurate, control was stable at spawn, gaze validity was high, and the gaze
  sequence changed from an instrument AOI to operational external view after
  spawn.
- The `0.546 s` value is an operational external-view transition, not a
  confirmed intruder-acquisition latency.
- The raw-input response time is more interpretable than active-control trials,
  but still requires later manual/state-trajectory validation.
- Trial `40` is incomplete and confounded by active control at spawn; do not
  use it as the clean demonstration result.

Generated analysis outputs:

- `build_atc_tmp/xplane_groupmeeting_demo_v29_20260729_145522_visual_gaze_aoi.csv`
- `build_atc_tmp/xplane_groupmeeting_demo_v29_20260729_145522_visual_external_episodes.csv`
- `build_atc_tmp/xplane_groupmeeting_demo_v29_20260729_145522_visual_trial_gaze_summary.csv`
- `build_atc_tmp/xplane_groupmeeting_demo_v29_20260729_145522_visual_trial_aggregate_summary.csv`

## 2026-07-29 연속 6-trial 세션 설계 확정

확정한 운용 방향:

- 세션 시작 위치는 활주로 약 `10 NM`
- 한 참가자는 여러 세션 수행
- 참가자는 세션 시작 command를 1회만 입력
- 한 세션은 X-Plane situation reload 없이 6 trial 자동 진행
- LEFT/RIGHT/FRONT를 각 2회 포함하고 순서만 제약 무작위화
- 각 scenario closure 뒤 `U(10,15초)` washout과 자세 안정조건을 모두
  충족하면 다음 trial 자동 시작
- 6회 완료 뒤 세션 자동 종료
- catch trial은 사용하지 않음
- trial 원자료를 보존하고 참가자별 세션 평균 산출

해석 제한:

- 참가자는 반복 후 traffic 출현을 예상할 수 있다.
- 이 프로토콜은 무작위 시점의 반복 traffic encounter 반응을 측정하며,
  unexpected-intruder recognition 실험으로 표현하지 않는다.
- 6개 trial의 평균은 무작위 변동을 줄일 수 있지만 기대효과를 제거하지
  않으므로 trial order와 spawn 전 외부시야 비율을 함께 분석한다.

자세 안정 gate 초안:

- scenario가 완전히 닫힌 뒤에만 평가
- 최소 washout은 고정값이 아니라 `U(10,15초)`
- 최근 약 3초 동안 roll 절대값, roll 변화폭, pitch 변화폭, heading
  변화폭이 모두 pilot threshold 안에 있을 때 stable
- 절대 pitch는 정상 강하자세에 따라 달라지므로 직접 제한하지 않고
  변화폭을 사용
- 최대 대기시간 안에 stable이 되지 않으면 다음 trial을 강제 시작하지
  않고 session/trial 상태를 별도로 기록
- 최종 threshold는 자동화 구현 뒤 실제 approach pilot으로 보정

생성 시 비행조건:

- 기존 STATE CSV가 latitude/longitude/elevation/AGL, heading/pitch/roll,
  angular rates, IAS/TAS/vertical speed, control input과 throttle을 이미
  저장함을 확인
- raw STATE CSV schema 변경 없이 spawn 시점 값을 trial summary에 추가 가능
- flap은 현재 STATE에 없으므로 별도 구현 여부를 결정

다음 구현 단위:

1. session start command와 6-trial Lua state machine
2. LEFT/RIGHT/FRONT 각 2회 제약 무작위 schedule
3. automatic trial end, `U(10,15초)` washout, attitude-stability gate
4. spawn-time flight-condition summary
5. 여러 세션에 걸친 5개 속도 target 균형화
6. Java/Python analyzer와 smoke/pilot 검증

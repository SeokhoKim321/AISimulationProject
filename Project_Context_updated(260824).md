# PROJECT_CONTEXT.md

## 연구 배경

이 프로젝트는 X-Plane 11 시뮬레이터와 Java 기반 수신/분석 프로그램을 연동하여 위험상황 노출 전후의 조종사 시선 및 조종 반응 데이터를 수집하는 연구이다. 최종 목표는 향후 AI 조종사 모델을 설계할 때 사용할 수 있는 조종 반응 데이터 구조, 이벤트 타임라인, 반응 지표를 구축하는 것이다.

현재 논문/실험 방향은 완성된 AI 조종사 모델 자체를 구현하는 것보다, AI 조종사 모델 개발에 필요한 pilot-in-the-loop 데이터 수집 및 분석 프레임워크를 제시하는 쪽에 맞춰져 있다.

## 현재 연구 방향

현재 핵심 시나리오는 Cessna 172/Skyhawk의 표준화된 final approach 유지 과제 중 예고 없이 crossing intruder를 발생시키고, 조종사의 시선 분포와 조종 입력 반응을 객관적 사건 시각에 맞춰 기록하는 것이다.

참가자가 visual advisory를 보고 반응하는 실험이 아니라 intruder를 직접 발견하고 회피하는 실험으로 재설계한다. `INTRUDER_SPAWNED`는 객관적인 scenario onset으로 보존한다. v37 이후에는 조명 도움을 배제한 geometry proxy에서 intruder의 추정 span이 20 px에 도달한 `INTRUDER_VISUAL_OPPORTUNITY_ONSET`을 primary operational response anchor로 사용한다. 이 event는 실제 인지시점이 아니라 표준화된 관측기회 시작점이다. 같은 threshold의 `INTRUDER_VISUALLY_DETECTABLE`은 과거 분석 호환용 보조 event로 유지한다.

주요 분석 대상:

- `INTRUDER_VISUAL_OPPORTUNITY_ONSET` 이후 `PILOT_RESPONSE_START`까지의 operational response latency
- spawn 전후 계기/외부 시선 분포와 조종 입력 변화
- speed, runway course/centerline, glidepath 유지 성과
- 최소 수평 거리와 최소 수직 분리
- hazard 진입 여부와 hazard-window
- 보조 `INTRUDER_VISUALLY_DETECTABLE` 20 px event 이후 반응 여부

논문에서는 primary를 `visual-opportunity-to-response latency`, secondary를 `spawn-to-response latency`로 표현하며 어느 것도 recognition/awareness time으로 부르지 않는다. 현재 2026-07-20 데이터는 participant 화면에 countdown/advisory가 보이고 연속 강하 중 여러 trial을 수행한 pilot/system-validation 자료이므로 개선 프로토콜의 본실험 자료와 합치지 않는다.

v33~v36의 5 px light-assisted opportunity는 역사적 검증 자료로만 유지한다. v37 이후 primary latency는 lights-off 20 px geometry opportunity-to-response이고, spawn-to-response는 secondary scenario-exposure metric이다. v34는 세션 내 방향 순서를 균형화했고, v35는 bounded speed-stability spawn gate를 시험했으며, v36 이후 raw IAS-rate를 필수 gate에서 제외하고 target-band dwell만 사용한다.

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
- version: `260811_intruder_ai_autopilot_override_v39`
- snapshot: `FLYWITHLUA_STUDY_INTEGRATED_260811_intruder_ai_autopilot_override_v39.lua`
- task protocol: `audio_task_v39`

### v39 AI autopilot override test

v38 실제 화면에서는 불빛이 남았다. 로그상 beacon/nav/strobe는 0이었지만
landing/taxi가 매 frame 1로 복구됐고, 첫 20 px opportunity 시점에 재점등 횟수는
333회였다. 따라서 모델 내장광으로 단정하기 전에 X-Plane AI autopilot이 만드는
landing/taxi 상태를 분리해야 한다.

v39은 intruder가 active인 동안 slot 1의
`override_plane_ai_autopilot[1]`만 1로 유지하고 종료 시 0으로 복원한다. user aircraft나
다른 AI slot override는 건드리지 않는다. 기존 Lua 좌표/속도 갱신, 20 px geometry,
65 m/s, six-trial 설계는 유지한다. override write/readback/value와 light 재점등 횟수를
event에 남긴다. 실제 불빛 소멸과 궤적 정상 여부는 FlyWithLua reload 후 짧은 physical
test로 판정했다.
기준본·v39 snapshot·실제 Scripts 실행본 SHA-256은
`6556B91F9E2AA568BE0C573261F1AE99BB09602505DB395206D36E1013F6BFE1`로 일치한다.

v39 single-trial physical test는 PASS했다. LEFT intruder에서 참가자가 불빛 소멸을
확인했고, spawn 및 20 px opportunity event의 override write/readback/value는
`true/true/1`, light readback은 `0/0/0/0/0`, 재점등 횟수는 0이었다.
spawn-to-opportunity는 `5.452 s`였고 `MIN_DISTANCE_REACHED`와 manual `TRIAL_END`까지
기록되어 이동/종료 흐름도 유지됐다. 이어서 balanced 6-trial로 세 방향의 동일 결과를
검증했다.

후속 balanced 6-trial 검증도 PASS했다. schedule은
`LEFT|RIGHT|FRONT|RIGHT|FRONT|LEFT`로 각 방향이 2회였으며, 6개 spawn과 opportunity
모두 override value 1, `lights_all_off=true`, `lights_reenabled_count=0`이었다.
reenabled marker와 Lua error는 없었다. spawn-to-opportunity는
`7.826,5.295,10.814,5.366,8.943,6.893 s`로 모두 `5~11 s` 범위였고,
`MIN_DISTANCE_REACHED`와 automatic `TRIAL_END`도 각각 6회, session complete도 정상
기록됐다. 따라서 v39을 조명 억제 및 intruder movement engineering PASS 활성본으로
사용한다.

### 2026-08-12 v39 integrated Java/Tobii session

v39을 Java receiver 및 Tobii logger와 동시에 실행한 첫 통합 세션은
`session_v39_integrated_20260812_155103`이다. gaze 파일은
`logs/tobii/session_xplane_tobii_20260812_155022_gaze.csv`다. STATE/INTRUDER/EVENT/gaze
row는 각각 `3247/3247/103/30140`이고 정상 종료 event도 기록됐다.

- direction: `LEFT|RIGHT|FRONT|RIGHT|FRONT|LEFT`, 각 방향 2회
- Java clean: `6/6`; spawn/opportunity/response start/response end/trial end: 각각 `6/6`
- v39 override/light: spawn 및 opportunity `6/6`에서 override readback 1,
  `lights_all_off=true`, positive reenabled count 0
- spawn-to-opportunity: `5.618~10.921 s`, 평균 `8.039 s`; 목표 `5~11 s`는 `6/6`
- initial span: `8.050~13.701 px`; provisional `7~13 px`는 `5/6`
- opportunity-to-response: `1.631~10.199 s`, 평균 `4.870 s`, 중앙값 `3.838 s`
- gaze: raw valid `70.4%`, trial valid `83.8~92.6%`, trial 동일가중 평균 `87.6%`
- trial operational-external: 평균 `49.3%`; 전체 trial 표본 기준 AIRSPEED `16.0%`,
  VERTICAL_SPEED `11.4%`

속도 생성 gate는 stable `4/6`, forced timeout `2/6`이다. trial 1은 spawn 오차가
`+14.037 KIAS`여서 primary 연구분석 제외 후보이고, trial 6은 오차가 `+3.745 KIAS`로
band 안이지만 dwell `1.357 s`가 1.5 s 기준에 미달했다. trial 5는 visual opportunity
시점에 control이 이미 active였다. 따라서 recording completeness와 연구분석 적합성을
분리하며, primary 결과는 stable trial 기준, forced trial 포함값은 sensitivity 결과로
보고한다.

behavior classifier는 `AMBIGUOUS 5 / AVOIDANCE_CANDIDATE 1`을 냈지만 이는 교수 피드백상
필수 outcome이 아니라 진단값이다. primary outcome은 표준화된 geometry opportunity 이후
최초 지속 조작 변화 시간이며, operational-external도 intruder acquisition 확정값으로
부르지 않는다. 다음 구현 단계는 정면 화면 grid에 projected intruder dynamic AOI와
runway 영역을 결합해 `INTRUDER_SEARCH_OR_TRACKING / RUNWAY_GUIDANCE / OTHER_EXTERNAL /
UNRESOLVED` evidence를 산출하는 것이다.

### 2026-08-19 외부시선 공간·객체·의미 분류 구현

마지막 핵심 분석 기능인 외부시선 context classifier를 별도 사후분석 단계로
구현했다. 도구는 `tools/tobii/classify_xplane_external_gaze.py`, 합성 검증은
`tools/tobii/classify_xplane_external_gaze_smoke_test.py`, RKSI runway geometry는
`resources/rksi_runway34_geometry_260819.csv`다. 원본 STATE/INTRUDER/EVENT/gaze와
UDP/CSV schema는 바꾸지 않는다.

분류 구조:

1. spatial: 각 monitor별 `6 x 3` grid cell
2. object: 매 gaze timestamp의 intruder projected x/y/span과 dynamic AOI, RKSI runway
   34 projected polygon
3. semantic: `INTRUDER_SEARCH_OR_TRACKING`, `RUNWAY_GUIDANCE`, `OTHER_EXTERNAL`,
   `UNRESOLVED`

기본 parameter는 surround `5760 x 1080`, HFOV `122 deg`, exterior `y<600`,
intruder/runway margin `45/45 px`, semantic dwell `200 ms`, maximum gaze gap `100 ms`,
maximum state join gap `250 ms`다. priority는 intruder dynamic overlap, runway dynamic
overlap, intruder same-grid proxy, spawn 이후 접근방향과 일치하는 measured side proxy,
residual other 순이다. direct overlap, grid search, side-direction evidence는 한 label로
숨기지 않고 별도 필드 및 trial summary에 보존한다.

v39 통합 세션 재분석에서 Lua spawn/opportunity geometry 대비 사후투영 median absolute
error는 x `0.083 px`, y `0.363 px`, span `0.026 px`다. trial 내부 external `5944`
rows 중 기본 45 px 결과는 intruder `1478 (24.9%)`, runway `298 (5.0%)`, other
`2845 (47.9%)`, unresolved `1323 (22.3%)`다. intruder 분류의 세부 근거는 direct
dynamic overlap `879`, same-grid `438`, measured side match `161` rows다.

v37 실제 세션 회귀에서도 투영오차 x/y/span `0.233/0.724/0.025 px`로 재현됐다.
Python compile, 새 synthetic smoke, 기존 gaze analyzer smoke, 기존 behavior classifier
smoke, SVG XML 검증은 모두 PASS다.

그러나 30/45/60 px sensitivity에서 semantic 비율은 달라졌다. 이는 동적 geometry
계산 오류가 아니라 gaze-to-object tolerance 선택의 영향이다. 따라서 classifier
engineering 구현은 완료됐지만 `45 px` accuracy는 화면 녹화 또는 통제된 runway,
intruder, other 표적 주시와 비교하기 전까지 provisional이다. semantic label은 실제
인지·발견·의도를 뜻하지 않으며 논문에서는 operational evidence로 제한한다.

주요 출력:

- `*_external_context_gaze.csv`
- `*_external_semantic_episodes.csv`
- `*_external_semantic_summary.csv`
- `*_external_grid_summary.csv`
- `*_projection_validation.csv`
- `*_external_context_metadata.csv`
- `*_external_context_overview.svg`

상세 절차는 `TOBII_EXTERNAL_GAZE_CLASSIFICATION_260819.md`를 따른다. 다음 즉시 단계는
새 기능 추가가 아니라 의미분류 수동 정확도 검증과 margin 동결이다.

### 2026-08-24 AOI 전이확률 행렬 및 연구실 참가자 절차

최초 벤치마크 논문 `CognitiveAgentEvaluationforSyntheticPilotTraining.pdf`의 Fig. 6과
Eq. 3~4를 다시 확인했다. 해당 attention model은 source attention zone `i`에서 다음
zone `j`로 이동할 조건부확률 `P(s_(t+1)=j | s_t=i)`을 갖는 DTMC이며 각 행의 합은
1이다. 같은 zone으로의 대각선 self-transition은 gaze persistence/duration 정보를
모델링한다. 논문은 tactical state별로 서로 다른 matrix를 만든다.

현재 자료에 같은 구조를 적용하는 별도 사후분석기
`tools/tobii/analyze_aoi_transition_matrix.py`를 구현했다. 입력은
`*_external_context_gaze.csv`와 같은 session의 `_events.csv`이며 원본 CSV schema는
변경하지 않는다. 기본 관측단위는 현재 validated dwell 기준과 맞춘 `200 ms` bin의
dominant eligible AOI다. 이것은 별도 fixation detector 결과가 아니므로 fixation으로
부르지 않는다. invalid/unresolved bin은 전이 chain을 끊는다.

matrix state:

- instrument: `AIRSPEED`, `ATTITUDE`, `ALTITUDE`, `HEADING`, `VERTICAL_SPEED`, `NAV_GPS`
- remaining panel: `PANEL_OTHER`
- external object context: `INTRUDER_AOI`, `RUNWAY_AOI`, `OTHER_EXTERNAL`

phase-specific matrix는 `FULL_TRIAL`, `BEFORE_SPAWN`, `SPAWN_TO_OPPORTUNITY`,
`AFTER_OPPORTUNITY` 네 가지다. 논문에 가까운 DTMC matrix는 self-transition을 포함하고,
교수 피드백을 직관적으로 설명하기 위한 next-switch matrix는 self-transition을 제외한다.
count/probability CSV, 개별 transition audit, dwell episode, SVG heatmap을 모두 출력한다.
여러 참가자를 결합할 때는 participant별 matrix, pooled transition matrix와
participant-equal mean probability matrix를 함께 보존하고 기본 SVG는 participant-equal
mean을 사용한다.

v39 integrated 6-trial에 적용한 결과 binned states `1745`, 전체 phase 복제 기준 DTMC
transitions `1617`, AOI switches `420`을 생성했다. `FULL_TRIAL`만 보면 eligible bins
`873`, transitions `816`, self-transitions `603 (73.9%)`, switches `213`이다. 모든
유효 probability row sum은 1이고 synthetic smoke, Python compile, SVG XML이 PASS했다.
이 값은 기존 self 1인의 engineering session 결과이므로 집단 조종사 결과가 아니다.

연구실 구성원 1인 1회 절차는 participant당 balanced automatic 6-trial session 1개다.
Tobii logger에는 backward-compatible optional `--participant-code`, `--duration-s`를
추가했다. 학위논문/APISAT 등 연구결과로 사용할 사람 데이터는 IRB 승인 또는 공식 면제
판단 이후에만 수집한다. 비조종 연구실 인원은 feasibility/novice sample로 구분하며 pilot
대표값으로 일반화하지 않는다. 상세 절차와 명령은
`AOI_TRANSITION_MATRIX_AND_LAB_PILOT_PROTOCOL_260824.md`, batch manifest 형식은
`resources/aoi_transition_session_manifest_template_260824.csv`다. 본 수집 전에는 아직
provisional인 `45 px` object margin을 통제 검증으로 동결해야 한다.

### v38 persistent intruder light suppression diagnostic

v37의 `lights_write_ok=true`는 FlyWithLua의 DataRef write 호출 성공만 뜻했다.
참가자가 실제 화면에서 점멸등이 계속 보인다고 확인했으므로, v38에서는 조명 상태를
지속 강제하고 readback하는 방식으로 진단 범위를 확장했다.

- profile: `visual_opportunity_proxy_v38`
- beacon/nav/strobe/landing/taxi DataRef를 intruder active 전 구간에서 매 frame 0으로
  설정한다.
- write 직후 readback과 다음 frame pre-force readback을 분리한다.
- event metadata: `lights_persistent_force`, `lights_readback_ok`, `lights_all_off`,
  `lights_reenabled_count`, `light_readback_beacon/nav/strobe/landing/taxi`
- X-Plane이 frame 사이에 값을 복구하면 `SCENARIO_MARKER`로 최초 상태를 기록한다.
- `override_planepath`는 AI aircraft를 제어하는 용도가 아니며 현재 위치/이동 로직을
  방해할 수 있어 사용하지 않는다.

`lights_all_off=false` 또는 reenabled marker가 나오면 simulator가 light DataRef를
되살린 것이다. 반대로 readback이 전 구간 0인데 화면 점멸이 남으면 현재 AI aircraft
모델의 내장 object lighting으로 보고 no-light AI model/custom object를 검토한다.
v38은 geometry 20 px, 65 m/s, balanced direction, six-trial 설계를 변경하지 않는
lights-only revision이다. Java/Python smoke 전체와 v37 backward reanalysis는 PASS했다.
기준본·v38 snapshot·실제 Scripts 실행본 SHA-256은
`59EF750687D4C400335C4632463C79D9089A258FDD2AA801CDDCB2D7326760DE`로 일치하며,
Scripts 폴더의 활성 Lua는 통합본 1개다.

현재 Java entry point:

- receiver: `src/com/example/ai/XPlaneReceiverMain.java`
- session analysis: `src/com/example/ai/XPlaneSessionAnalysisMain.java`
- batch analysis: `src/com/example/ai/XPlaneBatchAnalysisMain.java`

현재 Tobii 도구:

- logger: `tools/tobii/session_xplane_tobii_logger.py`
- merge/analysis: `tools/tobii/analyze_xplane_tobii_session.py`
- visualization: `tools/tobii/visualize_tobii_gaze.py`
- behavior evidence: `tools/tobii/classify_xplane_tobii_behavior.py`
- behavior smoke test: `tools/tobii/classify_xplane_tobii_behavior_smoke_test.py`
- external context: `tools/tobii/classify_xplane_external_gaze.py`
- AOI transition matrix: `tools/tobii/analyze_aoi_transition_matrix.py`
- Surround audio cue: `tools/tobii/run_surround_cockpit_validation.ps1`
- Surround cue/gaze validation: `tools/tobii/analyze_surround_cockpit_validation.py`
- procedure: `TOBII_SURROUND_COCKPIT_PILOT_260728.md`

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
- trial 시작 3초 후 표준 계기 유지 WAV 자동 재생과 audio start/end event
- internal visual-advisory 상태 계산(참가자 화면에는 기본 비표시)
- 조기 `Study End Trial` 입력 guard
- v22부터 placeholder `FINAL_APPROACH_GATE_ENTERED` 미발생
- 자동 6-trial 방향표를 두 개의 3-trial 균형 블록으로 생성
- 각 블록에 LEFT/RIGHT/FRONT를 정확히 1회씩 배정하고 블록 경계의 동일 방향 연속을 금지

## 2026-08-04 v34 균형 방향 블록 기준

- 세션당 6 trial과 방향별 2회 조건은 유지한다.
- trial 1~3은 LEFT/RIGHT/FRONT를 각 1회, trial 4~6도 각 1회 포함한다.
- 각 블록의 내부 순서는 독립적으로 무작위화하되 trial 3과 4의 방향은 같지 않게 재추첨한다.
- 가능한 방향표는 총 24개이며 어느 방향도 인접 trial에서 반복되지 않는다.
- session-start `SCENARIO_MARKER`에는 `approach_schedule_method=two_balanced_random_blocks_v1`, 두 블록과 전체 방향표를 기록한다.
- 각 `trial_ready`와 `TRIAL_START`에는 `scheduled_approach`, `approach_schedule_method`, `approach_block_index`를 기록한다.
- intruder 위치·조명·5 px operational opportunity와 20 px 보조 detectability 기준은 v33과 동일하다.
- Java/Tobii 분석기는 `audio_task_v34`를 v33과 같은 visual-opportunity protocol로 처리하며, v33 원본과 하위 protocol 호환성을 유지한다.
- 정적 설계 검증은 24개 가능한 방향표 모두에서 두 블록 균형과 인접 반복 없음으로 통과했다.
- Java 21 compile, 자동 이벤트 smoke, 속도 과제 smoke, Python compile, v33 실제 세션 Java/Tobii 회귀 분석이 통과했다.
- v33 회귀 결과는 Java `6/6 clean`, gaze `21199/17970 valid = 84.8%`로 유지됐다.
- 실제 X-Plane Scripts 실행본과 저장소 기준본 SHA-256은 `6AD4CA82B85997EEB6208824EC4ABC26405084810DDDFABC5E23897E0E0C6865`로 일치한다.
- X-Plane runtime reload에서 `260804_balanced_direction_blocks_v34`가 오류·quarantine 없이 로드됐다.
- 현장 생성 방향표는 block 1 `FRONT|LEFT|RIGHT`, block 2 `LEFT|RIGHT|FRONT`, 전체 `FRONT|LEFT|RIGHT|LEFT|RIGHT|FRONT`였다.
- 두 블록 균형과 5개 인접 경계의 비반복 조건이 모두 통과했다.
- 초기 대기 `12.692 s` 중 `9.646 s`에 stop했으며 `completed_trials=0`, `state=INITIAL_WAIT`로 정상 기록됐다.
- 따라서 v34 스케줄 생성의 정적 설계·Java/Python 회귀·FlyWithLua runtime 검증은 모두 PASS다.
- 다음 구현 단계는 speed-task correction과 intruder avoidance 반응을 분리하는 behavior-validity 분류다.

## 2026-08-04 behavior-evidence classifier v1

목적:

- `clean`은 CSV와 필수 event가 완전한지를 뜻하며 회피반응의 타당성을 뜻하지 않는다.
- 별도 classifier는 control response를 `SPEED_TASK_CORRECTION`, `AVOIDANCE_CANDIDATE`, `AMBIGUOUS`, `NO_VALID_RESPONSE`로 구분한다.
- label은 조종사의 실제 의도를 확정하지 않는 evidence-based screening 결과다.

입력과 출력:

- state CSV, intruder CSV, event CSV, `analyze_xplane_tobii_session.py`의 trial gaze summary를 입력한다.
- `tools/tobii/classify_xplane_tobii_behavior.py`가 `*_trial_behavior_summary.csv`를 생성한다.
- raw CSV schema나 Java recording clean 판정은 변경하지 않는다.

v1 사전 기준:

- response `0.5 s` 전과 `3.0 s` 후 상태를 비교한다. nearest sample gap은 최대 `0.25 s`다.
- speed-task evidence는 반응 전 목표속도 오차가 `2 kt` 이상이고 3초 뒤 절대오차가 `1 kt` 이상 감소하며, throttle trigger이면 입력 방향도 오차를 줄이는 방향이어야 한다.
- avoidance kinematic evidence는 반응 전 predicted 3-D CPA가 `60 m` 이하이고, 반응 후 predicted CPA가 `20 m` 이상이며 개선량이 `10 m` 이상일 때만 true다.
- 최종 avoidance evidence는 kinematic evidence 외에도 operational-external이 response 전에 나타나고 opportunity 당시 active control이 아니어야 한다.
- speed와 avoidance evidence가 동시에 있으면 `AMBIGUOUS`의 `DUAL_SPEED_AND_AVOIDANCE_EVIDENCE`로 남긴다.
- 이 임계값은 자기실험 1세션을 검사하기 위한 provisional heuristic이며 참가자 자료로 보정·검증하기 전에는 확정 cutoff로 사용하지 않는다.

v33 retry 적용 결과:

- 입력 session: `session_visual_opportunity_v33_retry_20260804_134141`
- output: `build_atc_tmp/xplane_visual_opportunity_v33_retry_20260804_134141_trial_behavior_summary.csv`
- `SPEED_TASK_CORRECTION`: trial 3, 6 (`2/6`)
- `AMBIGUOUS`: trial 1, 2, 4, 5 (`4/6`)
- trial 4, 5는 speed와 avoidance evidence가 모두 있어 dual-purpose ambiguity로 분류됐다.
- trial 6은 3-D CPA 개선은 있었지만 control response가 first operational-external보다 먼저여서 avoidance evidence를 인정하지 않았다.
- `AVOIDANCE_CANDIDATE`: `0/6`
- 따라서 이 세션은 recording completeness 검증에는 성공했지만 명확한 회피반응 분석 자료로 그대로 채택하기에는 부족하다.
- operational-external은 runway·일반 외부경계·intruder 목적을 구별하지 못하므로 avoidance evidence도 인지 또는 의도의 직접 측정값이 아니다.

현재 등록된 command/macro:

- `flywithlua/study/reset_trial`
- `flywithlua/study/start_trial`
- `flywithlua/study/end_trial`
- `flywithlua/study/start_intruder_headon`
- `flywithlua/study/toggle_cloud`
- `flywithlua/study/toggle_operator_overlay`
- `flywithlua/study/test_task_audio`
- `flywithlua/study/start_auto_session`
- `flywithlua/study/stop_auto_session`

현재 코드에 남아 있는 visual advisory:

- hazard 조건: 수평 거리 `150 m` 이하 그리고 수직 분리 `30 m` 이하
- clear 조건: 수평 거리 `200 m` 이상 또는 수직 분리 `50 m` 이상
- 이전 화면 문구: `TRAFFIC ALERT`, `CHECK OUTSIDE`
- v23은 이 문구와 전체 실험 overlay를 참가자에게 표시하지 않는다.
- Lua visual-advisory 상태와 CSV event는 내부 분석 및 trial guard를 위해 그대로 유지한다.

v20에서 도입되어 v22에도 유지되는 end-trial guard:

- `random_crossing_armed`, `crossing_stabilizing`, `visual_advisory_active` 중 하나라도 true면 `Study End Trial`이 `TRIAL_END`를 기록하지 않는다.
- crossing intruder는 `crossing_min_distance_reported == true`이면 아직 active여도 trial end를 허용한다.
- head-on intruder는 마지막 수평 거리가 clear threshold 이상이면 아직 active여도 trial end를 허용한다.
- 이 경우 `MANUAL_NOTE`에 `ignored=end_before_trial_closed`와 현재 상태 flag를 기록한다.
- 화면 상태는 `WAIT: TRIAL NOT CLOSED`로 표시한다.
- Java의 `PILOT_RESPONSE_END` 내부 상태는 Lua가 직접 알 수 없으므로, 이 guard는 intruder/advisory 진행 중 조기 종료를 줄이는 목적이다.

현재 v30 기준:

- 현재 random crossing delay: 표준 음성 시작 뒤 독립적인 `U(3,10초)`
- 현재 표준 음성: trial마다 `90/95/100/105/110 KIAS` 중 하나를 균등 무작위로 선택하고, trial 시작 `3초` 후 해당 WAV 자동 재생
- 현재 task event: `TASK_COMMAND_AUDIO_START`, `TASK_COMMAND_AUDIO_END`
- 현재 속도 기록: `TRIAL_START`와 audio event detail에 `target_speed_kias` 및 선택 WAV 파일명 기록
- 현재 `INTRUDER_SPAWNED`: intruder를 X-Plane에 처음 배치하는 stabilization 시작 시점
- 현재 `INTRUDER_SPAWNED` detail: 생성 순간 ownship 위경도,
  elevation/AGL, heading/pitch/roll, angular rate, IAS/TAS/vertical speed,
  pitch/roll/yaw/throttle 입력 포함
- 현재 `TRIAL_START` detail: X-Plane master/interior/engine/prop/enviro/radio 음량 비율 포함
- 현재 participant 화면: 한 줄의 `TRIAL IDLE/RESET/ACTIVE/END`만 표시
- 조기 end 거부 시 `TRIAL IN PROGRESS - END NOT READY`를 `2.5초` 표시
- countdown, intruder/scenario 상태, debug 좌표·거리와 visual advisory는 숨김
- 점검용 overlay: trial 밖에서만 수동 토글 가능하고 trial 시작 시 강제로 숨김

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

현재 v29 final approach crossing trial 흐름:

1. `TRIAL_RESET`
2. `TRIAL_START(protocol=audio_task_v29, target_speed_kias, sound ratios)`
3. trial start 3초 뒤 `TASK_COMMAND_AUDIO_START(target_speed_kias, selected WAV)`
4. audio start 직후 `SCENARIO_SELECTED(planned_audio_to_spawn_s=...)`
5. audio start 기준 독립적인 `U(3,10초)` 뒤, intruder 최초 배치와 동시에 `INTRUDER_SPAWNED`
6. WAV 종료 시 `TASK_COMMAND_AUDIO_END`(spawn과 선후가 달라질 수 있음)
7. spawn 전 1초 입력으로 `PILOT_RESPONSE_BASELINE`
8. 임계치 변화가 0.25초 지속되면 최초 통과 시점에 `PILOT_RESPONSE_START`
9. 조건 충족 시 내부 위험창 `HAZARD_DETECTED`
10. `MIN_DISTANCE_REACHED`
11. 조건 해제 시 `HAZARD_CLEARED`
12. 안정화 또는 trial 종료 시 `PILOT_RESPONSE_END`
13. `TRIAL_END`

다음 구현 대상으로 확정한 연속 세션 흐름:

1. 동일한 10 NM final-approach 저장상황에서 세션 시작
2. 참가자가 세션 시작 command를 1회 입력
3. reload 없이 6 trial 자동 수행
4. LEFT/RIGHT/FRONT를 각 2회 포함하고 순서만 제약 무작위화
5. 각 trial의 audio 이후 intruder 생성과 scenario closure까지 자동 진행
6. scenario closure 뒤 `U(10,15초)` washout과 자세 안정조건을 모두 충족
7. 다음 trial 자동 시작
8. 6 trial 완료 뒤 세션 자동 종료

catch trial은 사용하지 않는다. 따라서 참가자가 반복 traffic encounter를
예상할 수 있음을 해석에 포함하고, 결과를 unexpected-intruder recognition
자료로 표현하지 않는다. trial order `1~6`에 따른 spawn 전 외부시야 비율과
반응시간 변화는 학습·예측 가능성 점검용으로 보존한다.

현재 STATE CSV는 위치, 위경도, elevation, AGL, heading, pitch, roll,
angular rate, IAS, TAS, vertical speed, 조종 입력과 throttle을 이미
저장한다. packet/STATE CSV schema를 바꾸지 않고 각 spawn 시점 값을
trial summary에 추가했다. 새 v30 event detail을 우선 사용하고 과거 로그는
spawn과 가장 가까운 STATE row로 복원한다. flap은 사용하지 않는다.

회피/속도조절 구분의 다음 기준:

- throttle 변화와 IAS target error 감소만 나타나면 speed-task control 후보
- roll/pitch/heading/vertical-path의 지속 변화와 predicted closest-approach
  separation 개선이 함께 나타나면 avoidance candidate
- `active_control_at_spawn=true` 또는 두 유형이 겹치면 ambiguous
- gaze는 동적 intruder AOI와 외부시야 전환을 보조 근거로만 사용
- 수동 영상 label로 자동 분류를 검증하기 전에는 primary avoidance latency로
  확정하지 않음

화면상 intruder 기준의 v32 구현 이력과 v33 변경:

1. ownship/camera 대비 intruder 상대방위·고각·거리 산출
2. Surround `5760x1080`, HFOV `122°`와 실제 view geometry로 화면좌표 투영
3. 항공기 모델 크기와 거리를 이용해 screen bounding box/pixel size 계산
4. v32에서 20 px threshold의 `INTRUDER_VISUALLY_DETECTABLE`을 구현
5. v33에서 light on·화면 내·5 px guard를 만족하는
   `INTRUDER_VISUAL_OPPORTUNITY_ONSET`을 primary anchor로 변경
6. opportunity-to-response를 주 지표, spawn-to-response와 20 px event를
   보조지표로 유지

외부시야 목적 구분의 후속 구현:

- 시간가변 `INTRUDER_DYNAMIC`
- `RUNWAY_DYNAMIC`
- `HORIZON_OR_EXTERNAL_OTHER`
- 기존 instrument AOI와 `SIDE_EXTERNAL_LEFT/RIGHT/UNKNOWN`
- intruder/runway 영역이 겹치면 단일 목적을 강제하지 않고 overlap/ambiguous로
  보존
- AOI dwell은 해당 물체 방향을 본 근거이지 인지·의도를 직접 증명하지 않음

### v30 physical spawn-context confirmation

Session `session_spawn_context_v30_20260803_150206` produced one clean
right-approach trial after X-Plane loaded v30. Planned/actual audio-to-spawn
timing was `3.909/3.911 s`. The event and Java summary agreed on spawn AGL
`565.859 m`, heading `325.030 deg`, pitch `-1.120 deg`, roll `-1.148 deg`, IAS
`116.620 KIAS`, vertical speed `-0.465 m/s`, and all control values. This
physically confirms the v30 spawn-context path.

### v31 automatic six-trial session

The active design now implements the continuous session protocol rather than
requiring manual reset/start/end for every trial. One
`Study Start Automatic Session` action creates six trials. LEFT, RIGHT, and
FRONT are each scheduled exactly twice in randomized order. The speed targets
include 90/95/100/105/110 KIAS once each plus one random repeated target, and
the six resulting speed assignments are shuffled.

The state machine is `INITIAL_WAIT -> TRIAL_ACTIVE -> WASHOUT`, repeated until
six completed trials, followed by `COMPLETE`. Initial wait and every washout
are independently sampled from `U(10,15 s)`. The next trial starts only when
that minimum time has passed and a rolling 3-second attitude gate passes:
absolute roll <= 7 deg, roll range <= 3 deg, pitch range <= 3 deg, and heading
range <= 5 deg. Thus washout is a minimum recovery interval, while unstable
flight extends it rather than forcing the next trial.

Automatic closure waits for the existing trial-close conditions, at least 2
seconds beyond the predicted conflict point, and current horizontal separation
of at least 200 m. The intruder is moved outside the visible scene before the
washout. Participant text does not reveal schedules or countdowns.

The raw CSV schemas remain unchanged. Event detail includes session sequence,
session trial index/count, scheduled direction/speed, wait duration, and gate
diagnostics. Java and Tobii analyzers treat `audio_task_v31` as the current
audio-anchored 3-10 second spawn protocol. Static compile/smoke checks passed.
The repository, snapshot, and deployed runtime Lua hashes match. X-Plane
`Log.txt` confirmed v31 loaded without a FlyWithLua error. The first complete
physical six-trial pilot passed on 2026-08-03.

### v31 physical pilot result and analysis boundary fix

Session `session_auto_session_v31_20260803_152309` completed all six trials in
`212.320 s`. RIGHT/LEFT/FRONT each occurred exactly twice. The speed targets
were 90/100/105/95/90/110 KIAS, satisfying the all-five-plus-one-repeat rule.
All six trials were strict clean with complete audio, spawn, hazard,
pilot-response recording, minimum-distance, and automatic-end events. Planned
waits were within 10-15 seconds, stability diagnostics passed, and every
automatic end occurred about 2 seconds after minimum-distance passage and
after hazard clear.

Receiver closure was clean, with `RECEIVER_STOP` and `SESSION_STOP` appended
and UDP 9100 released. Final file counts were STATE 1746, INTRUDER 1746, and
EVENT 95 rows.

The first analysis exposed an important boundary issue: after trial end, Lua
retires the intruder vertically while STATE/INTRUDER packets continue with the
last trial id during washout. Unbounded trial grouping therefore produced a
false near-zero horizontal distance paired with roughly 3000 m vertical
separation. Raw data was not modified. `XPlaneSessionAnalysisMain` now limits
trial state/intruder metrics to the inclusive `TRIAL_START..TRIAL_END` window
and derives the session minimum from those bounded summaries. A regression
smoke test includes a post-trial retirement row. Correct trial minimum
horizontal distances are 24.277, 12.877, 7.549, 8.617, 8.239, and 13.902 m.

The pilot also shows that protocol completeness does not establish final
experimental suitability. AGL decreased from about 534 m at session start to
75 m at completion; spawn AGL decreased from 487.9 m to 59.3 m, and the
last trial commanded 110 KIAS at an actual 81.3 KIAS. Thus approach phase,
remaining altitude, and random target speed are confounded late in the
session. The speed schedule or usable approach envelope must be resolved
before main participant collection. Raw-input response detection remains a
separate validity issue because one trial was already actively controlling at
spawn and several detected latencies were below 0.2 seconds.

This altitude result must not yet be generalized to the saved scenario. The
pilot did not start from the saved 2100 ft condition; automatic-session start
occurred at about 1753 ft AGL after the flight had already progressed, and the
pilot reported descending too aggressively. Starting at 2100 ft adds about
347 ft; under the same observed path, the sixth spawn would be roughly 542 ft
AGL rather than 195 ft. This makes 2100 ft plausibly usable but not yet proven.
The next action is an unchanged Java-only v31 repeat from the exact saved
scenario, with no altitude/speed/Lua change before that evidence is collected.

An initial diagnostic confirmed the saved scenario near the intended altitude:
the first logged row was about 2045 ft AGL. However, the diagnostic unpause ran
for about 8 seconds and the aircraft reached roughly 1910 ft AGL, so that
receiver file is not the repeat experiment. The clean repeat order is now:
load saved scenario, reload Lua, start Java while paused, start the automatic
session while paused, and only then unpause. The existing random wait and
attitude gate replace any separate preflight flying interval.

새 no-alert trial은 실제로 제시하지 않은 `ADVISORY_SHOWN/CLEARED`를 자동 생성하지 않는다. 해당 event enum과 parser는 과거자료 및 수동 advisory 실험 호환용으로 유지한다.

v22는 `FINAL_APPROACH_GATE_ENTERED`를 발생시키지 않으며 strict clean에서도 이를 요구하지 않는다.

현재 구현과 다음 분석 보완 흐름:

1. 동일한 10 NM final-approach 저장상황에서 세션을 시작한다.
2. 세션 시작 command 1회 뒤 내부 `TRIAL_RESET`, `TRIAL_START`를 자동 기록한다.
3. 고정 `3초` baseline 뒤 `TASK_COMMAND_AUDIO_START/END`를 기록한다(v26 구현 완료).
4. audio start 뒤 독립적인 `3~10초`가 지나면 과제 완료 여부와 무관하게 intruder를 최초 배치하고 `INTRUDER_SPAWNED`를 기록한다(v27 구현 완료).
5. spawn 전 1초 입력 baseline 대비 0.25초 지속 변화로 `PILOT_RESPONSE_START`를 판정한다(구현 완료).
6. hazard 관련 event는 참가자 경고가 아니라 내부 severity/outcome으로 기록한다.
7. scenario closure 뒤 `TRIAL_END`를 자동 기록한다.
8. `U(10,15초)`와 자세 안정조건을 모두 충족한 뒤 다음 trial을 자동 시작한다.
9. reload 없이 총 6 trial을 수행하고 세션을 자동 종료한다.

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

- v33 `INTRUDER_VISUAL_OPPORTUNITY_ONSET` 전후 계기/외부 scan 변화
- gaze dwell time
- opportunity, spawn 및 보조 20 px event 이후 `PILOT_RESPONSE_START`까지의 시간
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

1. 학위논문 및 학술발표 활용범위를 포함해 IRB 신규 심의 또는 공식 면제 판단을 완료한다.
2. controlled target-gaze validation으로 intruder/runway AOI `45 px` margin을 동결한다.
3. IRB 승인 이후 연구실 구성원에게 participant code를 부여해 1인당 automatic 6-trial
   session 1개를 수집한다.
4. session별 gaze merge와 external context classifier를 실행하고 recording/event,
   geometry, gaze validity를 확인한다.
5. session exclusion 기준을 자료 확인 전에 동결하고 manifest에 포함 여부와 사유를 남긴다.
6. participant별, pooled-transition, participant-equal AOI transition matrix를 생성한다.
7. 비조종 연구실 인원 결과는 feasibility/novice로 제한하고, pilot 일반화가 필요하면
   자격·경험을 갖춘 별도 표본을 수집한다.
8. 새 participant 자료는 기존 2026-07-20 및 self engineering validation 자료와 합치지 않는다.

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

Lua/Java event update (2026-07-20 historical v22 milestone):

- Lua version for that milestone was `260720_remove_placeholder_gate_v22`.
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
`260728_spawn_external_v2`. Reproducible target-task validation is in
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

## 2026-07-28 X-Plane cockpit audio-cue pilot tooling

The physical target test was post-hoc and did not independently validate the
classifier over the actual X-Plane cockpit. A no-keypress audio-cue pilot was
therefore implemented before natural intruder trials.

Cue runner:

- file: `tools/tobii/run_surround_cockpit_validation.ps1`
- speech engine: Windows SAPI COM `SAPI.SpVoice`
- selected installed voice: `Microsoft Heami Desktop - Korean`, `ko-KR`
- output:
  `logs/tobii/cockpit_validation/surround_cockpit_validation_YYYYMMDD_HHMMSS_cues.csv`
- events are flushed immediately and carry local ISO time, Unix PC time in ms,
  and session-relative monotonic ms
- runner version: `260728_v2_sync_speech`
- events per cue: `CUE_START`, `SPEECH_END`, `MEASURE_START`, `MEASURE_END`,
  `CUE_END`
- no participant keypress and no visual overlay are required

Nine-cue protocol:

1. `ATTITUDE`
2. left external
3. `AIRSPEED`
4. right external
5. `ALTITUDE`
6. left external
7. center outside
8. right external
9. `ATTITUDE`

Default timing is `8 s` countdown, synchronous full instruction playback,
`1.5 s` settle after speech completion, `2.5 s` measure, and `0.5 s`
inter-cue. The X-Plane window remains foreground during speech.

Cue/gaze analyzer:

- file: `tools/tobii/analyze_surround_cockpit_validation.py`
- version: `260728_v1`
- inputs: cue CSV and ordinary `session_xplane_tobii_*_gaze.csv`
- outputs: `*_cue_results.csv`, `*_summary.json`,
  `*_gaze_classified.csv`, `*_external_episodes.csv`
- pass gates: every cue coverage `>=80%`, side sensitivity `>=90%`, center
  specificity `>=90%`, center expected-AOI dwell success `>=80%`
- returns `PILOT_PASS`, `PILOT_FAIL`, or `DATA_INCOMPLETE`

The cue tool passed a shortened no-speech dry run with nine complete cue event
sequences and one session end. Synthetic gaze exercised center AOIs, directly
measured left/right, inferred loss-only side looks, and center recovery. The
analyzer returned `PILOT_PASS`: side `4/4`, center specificity `5/5`, center
AOI dwell `5/5`, and measured direction `2/2`.

The first physical run was completed but is diagnostic only because the v1
runner started its settle timer while speech was still playing. Runner v2 must
be rerun before accepting or rejecting the Surround setup. Run the Tobii logger
first, keep Surround/X-Plane full-screen, run the audio cue tool, stop the
logger after the completion voice, then analyze the two timestamp-overlapping
files. Java receiver is not required until the later natural intruder trial.

## 2026-07-28 Surround cockpit projection baseline

X-Plane detected NVIDIA Surround as one `5760x1080` monitor but initially kept
the one-monitor lateral FOV at `60 deg`. That projected only a narrow portion
of the old view onto the center physical monitor. The operator selected
`122 deg` as the closest practical match to the prior cockpit view. The saved
X-Plane preference values are:

- horizontal FOV: `122.000000 deg`
- vertical FOV: `37.377142 deg`
- lateral, vertical, and roll offsets: `0 deg`

The center `1920x1080` crop of the new X-Plane screenshot was compared with
`AOI그림.png`. The current panel is about `30-50 px` lower, but the center of
every fixed instrument AOI remains inside its existing rectangle. Keep this
view fixed for the cockpit audio-cue pilot. A visualization-background update
can be versioned after the physical pilot; it is not required to run the
classifier.

Windows PowerShell on this PC is version 5.1. The cockpit cue runner therefore
uses ASCII-only UTF-8 Base64 constants for Korean speech and decodes them at
runtime. This prevents BOM-less UTF-8 source decoding from corrupting spoken
instructions. A shortened no-speech regression after the change produced all
`49` expected rows and recovered the Korean cue text correctly.

## 2026-07-28 first physical audio-cue result and timing correction

Inputs:

- cues: `surround_cockpit_validation_20260728_141201_cues.csv`
- gaze: `session_xplane_tobii_20260728_140939_gaze.csv`

The files fully overlapped in PC time and every cue had at least `98.5%`
sample coverage. The v1 result was:

- status: `PILOT_FAIL`
- side sensitivity: `4/4 = 100%`
- center specificity: `4/5 = 80%`
- expected center AOI dwell: `3/5 = 60%`
- measured direction when evaluable: `2/2`
- mean valid gaze rate: `59.2%`

This does not reject the Surround classifier. The operator reported following
some instructions late, and cue 3 retained a measured left-side episode for
about `1.116 s` after the AIRSPEED measurement window began. Cue 9 then
recorded `117/121` valid samples in ATTITUDE, confirming that the fixed
ATTITUDE AOI still aligns with the FOV 122 cockpit.

The runner used SAPI flag `3`, which combines asynchronous playback with purge.
The call returned immediately, so the `1.5 s` settle timer could expire before
a Korean instruction finished. Runner `260728_v2_sync_speech` now uses
synchronous purge flag `2`, records `SPEECH_END`, and starts the settle interval
only after audible completion. A new physical run is required.

## 2026-07-28 corrected physical cockpit pilot pass

Inputs:

- cues: `surround_cockpit_validation_20260728_142411_cues.csv`
- gaze: `session_xplane_tobii_20260728_142328_gaze.csv`
- runner: `260728_v2_sync_speech`

Result:

- status: `PILOT_PASS`
- minimum cue sample coverage: `93.6%`
- side sensitivity: `4/4 = 100%`
- center specificity: `5/5 = 100%`
- center target dwell: `5/5 = 100%`
- measured direction accuracy when evaluable: `3/3`
- mean valid gaze rate: `67.8%`

All center cues had zero side false positives. Among valid samples, the expected
center-target rates ranged from `89.0%` to `99.3%`. Three of four side cues
provided directly measured and correct direction. The second left-side cue had
no measured side coordinate but had a qualifying continuous loss episode, so
it was classified as `INFERRED_SIDE_UNKNOWN` and its direction was not scored.

Decision:

- keep NVIDIA Surround `5760x1080`
- keep X-Plane lateral FOV `122 deg` and all visual offsets at `0`
- keep the current Spark whole-wide calibration
- freeze the hybrid classifier thresholds before the next trial
- proceed to one short natural X-Plane intruder trial
- report measured, inferred, and unresolved side evidence separately

This is a setup validation for the current operator and calibration. It does
not establish precise side-monitor gaze accuracy or between-participant
validity.

## 2026-07-28 natural intruder diagnostic with Surround

Collection:

- Java session: `session_surround_tobii_20260728_144151`
- X-Plane prefix: `logs/xplane/xplane_surround_tobii_20260728_144151`
- Tobii gaze: `logs/tobii/session_xplane_tobii_20260728_144218_gaze.csv`
- STATE rows: `413`
- INTRUDER rows: `413`
- event rows: `29`
- gaze rows: `4,965`
- valid gaze: `2,995` (`60.3%`)

The gaze interval `14:42:20.383~14:43:43.904` fully covers trial 4
`14:42:46.399~14:43:08.028` and trial 5
`14:43:10.506~14:43:33.642`. The Java analyzer reports a third incomplete
trial because 46 pre-roll STATE/INTRUDER samples retained old Lua trial ID 3
before the first new `TRIAL_RESET`. This is not an attempted trial.

Trial results:

- trial 4: right approach, Java `CLEAN`, minimum horizontal distance
  `10.510 m`, advisory-to-response `0.648 s`, gaze valid `47.9%`,
  operational external `40.9%`
- trial 5: left approach, Java `CLEAN`, minimum horizontal distance
  `10.221 m`, advisory-to-response `1.148 s`, gaze valid `63.1%`,
  operational external `52.7%`

Spawn-centered interpretation:

- both trials were already operationally external during the `500 ms` before
  `INTRUDER_SPAWNED`
- the apparent spawn-to-first-external values of `0.012 s` and `0.002 s`
  therefore do not represent intruder acquisition
- trial 4 post-spawn `0~5 s`: center-outside measured `22.3%`, unresolved
  tracking loss `75.7%`, no qualifying measured/inferred side episode
- trial 5 post-spawn `0~5 s`: center-outside measured `7.6%`,
  `INFERRED_SIDE_UNKNOWN` `91.0%`, no measured side direction

The natural run confirms that the pipeline can merge the current Surround
evidence with X-Plane events. It does not solve the experimental-design
problem: the participant is still externally biased before spawn, and long
loss intervals remain ambiguous. Do not use this session as paper response
data or interpret any external episode as confirmed intruder fixation.

The shared gaze analyzer is now version `260728_spawn_external_v2`. It retains
all advisory-centered fields and adds:

- `spawn_to_first_operational_external_s`
- `operational_external_before_spawn`
- eight `spawn_operational_external_rate_*` time bins
- the same metrics in the aggregate output

Regression against the v1 output found zero changes across `104` common
trial-summary fields. Original X-Plane and Tobii CSV files were not modified.

Next implementation gate:

1. add standardized instrument-maintenance audio and explicit audio events
2. schedule intruder independently `3~10 s` after audio start
3. detect pilot input response from `INTRUDER_SPAWNED`, not only advisory
4. rerun a small pilot before collecting any main dataset

## 2026-07-28 Lua v23 participant display isolation

- Active file: `FLYWITHLUA_STUDY_INTEGRATED.lua`.
- Active version: `260728_participant_display_hidden_v23`.
- Snapshot: `FLYWITHLUA_STUDY_INTEGRATED_260728_participant_display_hidden_v23.lua`.
- No study overlay is drawn by default. This removes participant-visible countdown, crossing state, debug coordinates, distance/event counters, and `TRAFFIC ALERT`/`CHECK OUTSIDE`.
- The internal `visual_advisory_active` state still updates every frame and continues to support the existing end-trial guard. UDP packet generation, event logging, Java hazard/advisory detection, and clean-trial analysis are unchanged.
- `flywithlua/study/toggle_operator_overlay` restores the former full overlay for setup checks only when no trial is active.
- `study_start_trial()` always forces the overlay off, and the toggle command is ignored while a trial is active.
- The Git baseline and actual X-Plane `Scripts` file have matching hashes. A running X-Plane instance must reload all FlyWithLua scripts before v23 becomes active in memory.
- Remaining scenario work is the standardized audio-maintenance task, independent `3~10 s` audio-to-spawn delay, and spawn-anchored Java response detection.

## 2026-07-28 Lua v24 minimal trial-state display

The participant display now separates operational confirmation from
experiment-sensitive information.

- Active version: `260728_minimal_trial_status_v24`.
- Snapshot: `FLYWITHLUA_STUDY_INTEGRATED_260728_minimal_trial_status_v24.lua`.
- The always-visible neutral state line is limited to `TRIAL IDLE`,
  `TRIAL RESET: <id>`, `TRIAL ACTIVE: <id>`, and `TRIAL END: <id>`.
- Random countdown, crossing/intruder state, coordinates, separation,
  last-event information, and visual traffic advisory remain hidden.
- A rejected early end leaves the state at `TRIAL ACTIVE`, which confirms
  that the command did not close the trial without disclosing the guard cause.
- The operator-only diagnostic overlay still cannot be enabled during an
  active trial.
- UDP/event behavior, internal advisory state, clean-trial analysis, and CSV
  schema are unchanged.

## 2026-07-28 Lua v25 rejected-end feedback

- Active version: `260728_end_wait_notice_v25`.
- Snapshot: `FLYWITHLUA_STUDY_INTEGRATED_260728_end_wait_notice_v25.lua`.
- A premature `Study End Trial` request now replaces the normal state line
  with `TRIAL IN PROGRESS - END NOT READY` for `2.5 s`.
- The display then returns automatically to `TRIAL ACTIVE: <id>`.
- The wording intentionally does not disclose whether the cause is randomized
  waiting, stabilization, intruder passage, advisory state, or separation.
- An end request outside an active trial similarly shows `NO ACTIVE TRIAL`
  for `2.5 s`.
- The existing guard decision, `MANUAL_NOTE` detail, accepted `TRIAL_END`,
  UDP/CSV schema, and Java analysis are unchanged.

## 2026-07-28 standardized task audio v26

Implemented assets and tools:

- Lua: `FLYWITHLUA_STUDY_INTEGRATED.lua`
- version: `260728_standard_task_audio_v26`
- snapshot: `FLYWITHLUA_STUDY_INTEGRATED_260728_standard_task_audio_v26.lua`
- WAV: `resources/audio/apisat_task_maintain_70kias_centerline_en_us.wav`
- generator: `tools/audio/generate_standard_task_audio.ps1`
- FlyWithLua runtime asset directory: `Scripts/AISimulationProjectAssets`

Audio baseline:

- text: `Cessna zero one, continue straight in, maintain seven zero knots, track runway centerline.`
- voice: `Microsoft Zira Desktop - English (United States)`
- SAPI rate/volume: `1 / 100`
- format: mono, 16-bit PCM, `22050 Hz`
- verified duration: `6.493 s`

Runtime timeline:

1. `TRIAL_START` records `protocol=audio_task_v26`.
2. Lua holds a fixed `3.0 s` pre-task baseline.
3. Lua records `TASK_COMMAND_AUDIO_START` immediately before OpenAL playback.
4. Lua records `TASK_COMMAND_AUDIO_END` at the verified WAV-duration boundary
   using wall-clock timing.
5. The maintenance instruction remains behaviorally valid through trial end.

Operational safeguards:

- A missing/unloaded WAV blocks trial start with `TASK AUDIO NOT READY`.
- Armed or active task audio prevents early trial closure.
- `Study Test Task Audio` works only outside an active trial and creates no
  experimental task event.
- The participant display does not show audio countdown or audio state.

Analysis integration:

- Java enum/parser accepts both audio events.
- Java session/batch outputs include audio start/end times,
  trial-start-to-audio, audio duration, and audio-start-to-spawn.
- `analyze_xplane_tobii_session.py` version `260728_task_audio_v3` exports the
  same task timing and aggregates it across clean trials.
- Protocol-tagged trials require exactly one start and end event with
  `TRIAL_START < AUDIO_START < AUDIO_END < TRIAL_END`.
- Historical sessions without an `audio_task_*` protocol remain backward
  compatible; the 2026-07-28 Surround diagnostic retained `2` clean trials
  and one pre-roll incomplete trial.

Not yet changed:

- random crossing remains `6~14 s` from `TRIAL_START`
- pilot response remains advisory-anchored in Java
- internal Java advisory events are still produced even though no visual
  advisory is shown

The next implementation changes crossing timing to independent `U(3,10 s)`
from `TASK_COMMAND_AUDIO_START`.

## 2026-07-28 audio-anchored spawn v27

The current active source is `260728_audio_anchored_spawn_v27`, with snapshot
`FLYWITHLUA_STUDY_INTEGRATED_260728_audio_anchored_spawn_v27.lua`.

Runtime behavior:

1. `TRIAL_START` records `protocol=audio_task_v27`, the fixed audio baseline,
   and X-Plane master/interior/engine/prop/environment/radio volume ratios.
2. `TASK_COMMAND_AUDIO_START` is recorded immediately before the successful
   OpenAL playback call.
3. Successful playback independently samples `U(3,10 s)` and records
   `SCENARIO_SELECTED` with its planned delay and trigger time.
4. When the trigger is reached, Lua first writes the stationary intruder into
   X-Plane and immediately records `INTRUDER_SPAWNED`.
5. The existing 0.35 s stabilization still precedes moving traffic, but it no
   longer delays the objective exposure event.

Analysis integration:

- Java session and batch outputs include planned/actual audio-to-spawn timing,
  `task_audio_spawn_timing_valid`, and all six sound ratios.
- `tools/tobii/analyze_xplane_tobii_session.py` version
  `260728_audio_anchored_spawn_v4` exports and aggregates the same timing
  fields.
- For v27, the required order includes
  `TRIAL_START < TASK_COMMAND_AUDIO_START < SCENARIO_SELECTED <
  INTRUDER_SPAWNED < TRIAL_END`.
- Historical trials without v27 retain their previous classification and
  leave v27-only fields blank.

Verification completed before runtime deployment:

- all six sound datarefs were found in the installed X-Plane 11 `DataRefs.txt`
- Java 21 compile passed
- Python AST parse passed
- historical Surround regression remained total `3`, clean `2`, incomplete `1`
- active Lua and v27 snapshot hashes match

The next code step after the v27 timing pilot is moving the Java pilot-response
anchor from `ADVISORY_SHOWN` to `INTRUDER_SPAWNED`.

## 2026-07-29 v27 timing pilot and spawn-response v1

Timing pilot:

- session `session_audio_spawn_20260729_130859`
- prefix `logs/xplane/xplane_audio_spawn_20260729_130859`
- final rows: STATE `768`, INTRUDER `768`, EVENT `47`
- planned/actual audio-to-spawn:
  `8.435/8.445`, `9.918/9.938`, `9.272/9.275 s`
- execution errors: `+10`, `+20`, `+3 ms`
- all spawn events use `spawn_phase=stabilization_start`
- the second physical execution omitted reset and was merged into trial 1;
  the final Java result is two trial IDs, one clean and one incomplete

The three physical runs validate timing execution, not the statistical
uniformity of three random draws.

Spawn-response detector:

- new event `PILOT_RESPONSE_BASELINE`
- anchor `INTRUDER_SPAWNED`
- method `pre_spawn_mean_sustained_delta_v1`
- requested pre-spawn baseline `1.0 s`
- minimum valid baseline samples `3`
- response persistence `0.25 s`
- thresholds: pitch/roll/throttle `0.08`, yaw `0.05`
- response start timestamp is the first threshold-crossing sample after the
  persistence requirement has been confirmed
- `active_control_at_spawn` flags a pre-spawn peak-to-peak range that already
  exceeded an axis threshold; it is context/confounding evidence
- response end uses eight settled samples or is forced immediately before
  `TRIAL_END`

The new detector is independent of advisory state. New no-alert trials only
auto-record `HAZARD_DETECTED/CLEARED`; `ADVISORY_SHOWN/CLEARED` remains readable
for historical or explicitly manual advisory experiments.

New spawn-response recording completeness treats hazard, advisory, and response
pairs as optional outcomes. Core required events are reset, start, scenario,
spawn, valid response baseline, minimum distance, and trial end. Optional pairs
must be complete and time-ordered when present.

Analysis:

- Java and batch CSV include `spawn_to_response_s`, response anchor/method,
  baseline validity/sample count/window, active-at-spawn, trigger axis, and
  persistence.
- `analyze_xplane_tobii_session.py` version is
  `260729_spawn_response_v5` and exports the same fields.
- Historical trials without `PILOT_RESPONSE_BASELINE` retain the legacy clean
  rules and results.

Verification:

- Java 21 compile passed
- `XPlaneAutoEventDetectorSmokeTest` passed
- synthetic Java and Python recording-complete analyses both returned `3/3`
  clean, including a valid no-response trial
- historical v27 timing session remained `1/2` clean trial IDs
- historical Surround session remained `2/3` clean
- `git diff --check` passed

The next operational step is one live X-Plane trial with the newly compiled
receiver. Lua remains v27 and does not need another reload for this Java-only
change.

## 2026-07-29 randomized speed-maintenance task v28

The current active Lua is `260729_random_speed_task_v28`, with snapshot
`FLYWITHLUA_STUDY_INTEGRATED_260729_random_speed_task_v28.lua` and protocol
`audio_task_v28`.

Experimental task:

1. At each `TRIAL_START`, Lua uniformly selects `110`, `115`, `120`, `125`, or
   `130 KIAS`.
2. The choice is fixed within the trial and is stored in event detail as
   `target_speed_kias`, together with the selected audio file.
3. After the existing `3.0 s` baseline, Lua plays the matching standardized
   instruction.
4. Intruder timing remains independent: `U(3,10 s)` after audio start, so the
   intruder can appear before or after speech completion.

The five WAV durations are:

- `110 KIAS`: `6.583401 s`
- `115 KIAS`: `6.543447 s`
- `120 KIAS`: `6.593469 s`
- `125 KIAS`: `6.568435 s`
- `130 KIAS`: `6.608345 s`

Interpretation constraint:

- `110~130 KIAS` is intentionally retained to increase instrument-monitoring
  demand in this experiment.
- It is not presented as a realistic C172 final-approach speed prescription.
- Paper/report wording should call it an experimental speed-maintenance or
  concurrent flight-control task.

Java trial and batch outputs now include:

- `target_speed_kias`
- `ias_at_task_audio_start_kias`
- `ias_at_spawn_kias`
- `pre_spawn_mean_abs_speed_error_kias`
- `pre_spawn_within_5kias_rate`

The performance window runs from task-audio start through
`INTRUDER_SPAWNED`. These are manipulation/compliance checks and secondary
performance measures, not substitutes for spawn-to-response latency or gaze
metrics. The existing `ias_mps` state CSV header is retained for compatibility,
but its X-Plane source value is treated as KIAS in this analysis.

`analyze_xplane_tobii_session.py` version
`260729_random_speed_task_v6` exports `target_speed_kias` and treats v28 as an
audio-anchored-spawn protocol. `XPlaneRandomSpeedTaskAnalysisSmokeTest`
validated a synthetic `120 KIAS` trial with audio-start IAS `115`, spawn IAS
`120`, pre-spawn MAE `1.667 KIAS`, and within-`±5 KIAS` rate `1.000`.

The source Lua and five WAVs were copied to the actual FlyWithLua runtime and
verified by SHA-256. The next operational step supersedes the previous
Java-only note: reload FlyWithLua, then run one timestamped receiver/Tobii live
trial and confirm the selected speed, baseline, spawn timing, response event,
and speed-performance fields.

## 2026-07-29 v28 live pilot interpretation

The first v28 physical pilot completed successfully:

- session: `session_random_speed_v28_20260729_135933`
- target: `130 KIAS`
- approach: left
- recording completeness: `1/1 CLEAN`
- planned/actual audio-to-spawn: `3.061/3.079 s`
- valid response baseline: `10` samples over `0.968 s`
- spawn-to-response: `2.217 s`, pitch trigger
- hazard window: `2.977 s`
- minimum horizontal/vertical separation: `8.629/10.976 m`
- no automatic advisory events

Speed-task interpretation:

- IAS was `111.762 KIAS` at audio start and `113.502 KIAS` at spawn.
- Pre-spawn MAE was `17.305 KIAS` and within-`±5 KIAS` rate was zero.
- Spawn occurred `3.529 s` before the `6.608 s` instruction ended.
- IAS rose to `120.770 KIAS` by minimum distance and `122.348 KIAS` by trial
  end, but never entered the target's `±5 KIAS` band.
- This is not sufficient evidence of participant noncompliance because the
  short random delay exposed the intruder before the instruction finished and
  before a large `+18 KIAS` change was physically achievable.
- Uniform targets within `110~130 KIAS` do not create uniform task difficulty
  when initial IAS varies. The main analysis must retain target and initial
  speed error, and should stratify/adjust for them or revise the target
  selection rule before freezing the protocol.

Gaze interpretation:

- gaze rows: `15002`; full-session validity `83.2%`
- trial validity: `86.1%`
- trial operational external: `31.5%`
- evidence decomposition: center-outside measured `18.6%`, inferred-side
  unknown `9.8%`, measured-side right `3.1%`
- gaze immediately before spawn: `AIRSPEED`
- first post-spawn operational external: `0.795 s`
- directly measured side episode after spawn: none

The `0.795 s` value is a coarse gaze-transition latency to an operational
external class, not confirmed intruder acquisition. The intruder approached
from the left, while the only directly measured side episode was rightward and
occurred `2.202~1.683 s` before spawn. No left-side fixation claim is allowed.

The standard cockpit SVG visualizer currently multiplies normalized gaze by a
single screen width and does not apply the Surround center-monitor remapping
used by the analyzer. Therefore no standard cockpit SVG should be generated
for this session until the visualizer supports `surround-wide`.

## 2026-07-29 experiment-refinement status and v29 speed range

The current phase is experiment refinement before main participant data
collection. Its purpose is to validate and freeze:

- task difficulty and timing
- participant-visible information
- intruder exposure and response anchors
- X-Plane/Tobii synchronization
- recording-completeness rules
- analysis and visualization validity

The v28 physical trial showed that a `130 KIAS` instruction from an initial IAS
near `112 KIAS` could not be achieved before a short-delay intruder exposure.
Based on that pilot evidence, the active target set is revised to:

```text
90, 95, 100, 105, 110 KIAS
```

Implementation:

- Lua: `260729_revised_speed_task_v29`
- snapshot:
  `FLYWITHLUA_STUDY_INTEGRATED_260729_revised_speed_task_v29.lua`
- protocol: `audio_task_v29`
- equal random selection among five targets
- target fixed within each trial
- audio and event detail retain `target_speed_kias` and file name
- audio-start-to-spawn remains independent `U(3,10 s)`

The five active WAV durations are:

- `90`: `6.413379 s`
- `95`: `6.383447 s`
- `100`: `6.683401 s`
- `105`: `6.663447 s`
- `110`: `6.583401 s`

Historical v28 WAVs and protocol support remain for reproducibility.
`XPlaneSessionAnalysisMain` accepts v27/v28/v29, and Tobii analyzer
`260729_revised_speed_task_v7` accepts the same audio-anchored-spawn protocols.

This revision makes the task more achievable in the observed approach-speed
region, but target difficulty can still vary with initial IAS. Main analysis
must retain `target_speed_kias`, IAS at audio start, and initial target error.
Do not treat all five target categories as behaviorally identical without
checking those values.

### v29 functional check

After reload, a log-only physical trial selected `100 KIAS`. Planned/actual
audio-to-spawn timing was `7.708/7.720 s`, the audio ended `1.023 s` before
spawn, the intruder approach was front, and Lua recorded minimum distance
`45.004 m` followed by `TRIAL_END`.

The Java receiver and Tobii logger were not running. Therefore this trial
confirms v29 audio selection, playback timing, scenario timing, and trial
closure only. It cannot provide Java response detection, IAS task-performance
metrics, or gaze analysis.

### Speed-range freeze decision

The operator judged the selected `100 KIAS` v29 task to have appropriate
subjective difficulty. The experimental audio target set is now frozen at
`90/95/100/105/110 KIAS`, with equal trial-level selection probability.

This is an experimental concurrent speed-maintenance task, not a C172
final-approach speed recommendation. The range should not be changed again
without evidence of a functional or safety problem. Initial IAS and target
error remain required analysis context because equal target probabilities do
not guarantee equal control effort.

The remaining speed-task validation is one fully logged v29 trial with Java
and Tobii running. After it passes, speed wording, files, range, and selection
rule are considered implementation-frozen for main collection.

### Fully logged v29 confirmation

The v29 speed implementation passed a fully logged trial:

- session: `session_revised_speed_v29_20260729_142254`
- performed trial: `2`
- target: `100 KIAS`
- approach: left
- audio-to-spawn planned/actual: `7.309/7.324 s`
- minimum horizontal/vertical separation: `13.825/37.433 m`
- trial gaze validity: `71.7%`

Trial 1 contains receiver pre-roll with no trial events. Trial 3 contains only
an accidental post-run `TRIAL_RESET`. Only trial 2 is a performed trial, so
the analyzer's total `3`, clean `1`, incomplete `2` must not be reported as
three experimental attempts.

The speed range itself is implementation-confirmed and remains frozen.
However, the response detector is not behaviorally validated:

- IAS at audio start/spawn: `107.758/108.728 KIAS`
- target: `100 KIAS`
- `active_control_at_spawn=true`
- detector trigger: throttle
- reported spawn-to-response: `3.031 s`
- throttle at audio end/spawn/response: `0.0/0.0/0.173`
- IAS at response: `104.154 KIAS`

This throttle change is consistent with ongoing speed-task control and cannot
be confidently attributed to intruder avoidance. Recording completeness is
valid, but the response latency is behaviorally confounded.

Gaze does not resolve the ambiguity. Gaze was already `OUTSIDE_VIEW`
immediately before spawn. A directly measured RIGHT-side episode occurred
from `+0.228` to `+0.977 s`, while the intruder approached from LEFT. No
measured left-side episode occurred after spawn.

Before main collection:

1. Keep the frozen v29 speed range.
2. Add a separate behavioral-response-validity field.
3. Exclude `active_control_at_spawn=true` trials from primary latency unless
   independently validated.
4. Develop a response criterion using persistent aircraft-state/trajectory
   change and improvement in predicted separation, rather than raw control
   input threshold alone.
5. Validate the new criterion against manually reviewed pilot trials.

### Group-meeting video pilot

The video-recording pilot used:

- Java session `session_groupmeeting_demo_v29_20260729_145522`
- X-Plane prefix
  `logs/xplane/xplane_groupmeeting_demo_v29_20260729_145522`
- Tobii file
  `logs/tobii/session_xplane_tobii_20260729_145503_gaze.csv`

The session contains receiver pre-roll as trial `27` and 13 performed attempts
from trial `28` through `40`. Eleven attempts are recording-complete. Trials
`29` and `40` are incomplete because `TRIAL_END` is missing. Therefore the
Java report's `11/14` clean count should be described as `11/13` completed
attempts after excluding receiver pre-roll.

Trial `33` is the preferred demonstration example:

- front approach and `100 KIAS` task
- planned/actual audio-to-spawn: `5.775/5.785 s`
- IAS at audio start/spawn: `104.930/100.826 KIAS`
- pre-spawn MAE: `2.564 KIAS`
- within-`5 KIAS` rate: `100%`
- `active_control_at_spawn=false`
- gaze validity: `86.5%`
- operational external-view rate: `57.9%`
- gaze immediately before spawn: `VERTICAL_SPEED`
- first operational-external classification after spawn: `0.546 s`
- raw-input roll response after spawn: `10.298 s`
- hazard after spawn: `9.849 s`

The trial illustrates the intended sequence of instrument task, unpredictable
intruder appearance, external-view transition, and later control response.
It does not establish intruder acquisition: no directly measured side-monitor
episode was present, and the operational-external classifier includes center
external view and inferred side viewing.

Trial `40` must not be used as a clean result. It lacks `TRIAL_END`, was
already external before spawn, and had active control at spawn.

### Exact-start v31 repeat

The repeat from the saved approximately 2100 ft condition completed normally:

- session: `session_auto_session_v31_2100ft_clean_20260803_161422`
- files: `logs/xplane/xplane_auto_session_v31_2100ft_clean_20260803_161422*`
- final rows: STATE 1653, INTRUDER 1653, EVENT 95
- trials: `6/6 strict clean`
- directions: LEFT, LEFT, RIGHT, FRONT, FRONT, RIGHT
- speeds: 95, 100, 105, 110, 90, 110 KIAS
- first STATE altitude: approximately 2066 ft AGL
- sixth-trial end altitude: approximately 957 ft AGL
- whole-file pitch: `-7.50..9.36 deg`
- whole-file vertical speed: approximately `-1143..1363 ft/min`
- clean receiver shutdown was recorded

The previous near-vertical dive did not recur. Code inspection and the invalid
run showed that Java is receive-only and Lua does not write ownship controls;
the transient problem was therefore classified as an X-Plane saved-state or
control-device condition, not a Java/FlyWithLua control command.

The implementation pass does not close the behavioral-validity issue. Spawn
IAS was 99.970, 94.898, 98.646, 106.235, 107.849, and 91.478 KIAS. The last two
trials received a 110 -> 90 -> 110 command sequence and had only about 5.9/5.7
seconds before spawn, leaving approximately 17.8/18.5 KIAS target error.
Spawn-to-response latency was 1.413, 0.374, 7.887, 0.131, 0.043, and 0.065
seconds. Four trials had active control at spawn, so the sub-second events,
especially throttle-triggered events, cannot be interpreted as confirmed
avoidance onset. `Strict clean` is limited to recording/event completeness.

Current decision:

1. Accept v31 automatic six-trial execution and exact-start altitude envelope
   as functionally validated.
2. Preserve individual trial results and the speed-error context.
3. Do not use current raw-input response latency as the primary avoidance
   outcome until speed-task control and avoidance behavior are separated.
4. Treat zero advisory events as expected. This v31 result used the
   `INTRUDER_SPAWNED` response anchor; v32 below supersedes it for new runs.

### Visual detectability gate v32

Current implementation is now:

- Lua: `260803_visual_detectability_gate_v32`
- protocol: `audio_task_v32`
- snapshot:
  `FLYWITHLUA_STUDY_INTEGRATED_260803_visual_detectability_gate_v32.lua`
- deployed SHA-256:
  `7F6902AAA3480708CB0EC09523DD26B594FA5679850375AD6FCEC3C1681CEC71`

The v31 automatic six-trial workflow is retained. The scenario now creates the
crossing traffic farther away by using a 920-1100 m predicted ownship path
target, 16-20 s configured conflict horizon, and 500 m extra intruder path
distance. Nominal path-start distance is about 1.4-1.7 km; actual slant range
also depends on LEFT/RIGHT/FRONT geometry.

The Lua visual projection proxy is calibrated to the fixed current display
configuration:

- 5760x1080 Surround surface
- 122 deg horizontal FOV
- C172 reference wingspan/length: 11.0/8.3 m
- provisional detectability threshold: 20 px
- perspective method: `perspective_bbox_proxy_v1`
- ownship heading and pitch are applied; roll is currently ignored and logged

The first on-screen crossing of the provisional span threshold emits
`INTRUDER_VISUALLY_DETECTABLE`. Event detail records estimated span, screen
x/y, slant/horizontal distance, relative bearing/elevation, fixed display/FOV
parameters, threshold, time since spawn, and ownship context. This event is an
objective operational proxy for when identification may be possible, not a
direct observation of the participant's awareness.

For v32, Java keeps hazard exposure active from `INTRUDER_SPAWNED` but opens
the response baseline and sustained-control detector at
`INTRUDER_VISUALLY_DETECTABLE`. Older sessions retain their spawn anchor. The
baseline records active control at the selected anchor. Throttle remains an
allowed response axis; roll/heading/vertical-path change is not mandatory.

The analysis boundary is now:

- spawn-to-detectability: hidden/low-salience exposure interval
- detectability-to-response: primary operational response latency candidate
- response before detectability: labelled and excluded from that latency
- active control at detectability: retained as an ambiguity flag
- minimum separation and trajectory effect: outcome evidence, not direct proof
  of pilot intent

Java and Tobii analyzers require the new event only for `audio_task_v32`, so
v31 and historical sessions remain clean-compatible. The Tobii output adds
detectability-relative external-view/AOI windows. The visualization tool uses
the detectability event as its new default anchor while supporting explicit
historical anchors.

위 내용은 v32 설계 이력이다. v33에서는 20 px를 primary 기준으로 고정하지
않고 아래 light-assisted visual-opportunity 기준으로 대체한다.

## 2026-08-04 v33 현재 실험 기준

Intruder 생성 조건:

- FRONT `0°`, LEFT `-32°`, RIGHT `+32°`의 고정 최초 상대방위를 사용한다.
- 5760x1080, 수평 FOV 122°에서 예상 screen x는 FRONT `2880`, LEFT `1882`,
  RIGHT `3878 px`이다. LEFT/RIGHT는 중앙 모니터 경계에서 좌우 모니터 쪽으로
  약 38 px 넘어간 위치다.
- 지정 bearing ray에서 기존 예상 교차지점으로 수렴하는 궤적을 계산한다.
  추가 500 m path extension은 제거하고 intruder base speed `58 m/s`를
  유지한다.
- spawn 시 beacon/nav/strobe를 켜며 landing/taxi는 끈다. intruder retire 시
  light도 끈다.

Primary response anchor:

- 초기 화면 projection valid/visible, estimated span `>=5 px`, light dataref
  write 성공을 모두 만족하면 spawn과 같은 시점에
  `INTRUDER_VISUAL_OPPORTUNITY_ONSET`을 기록한다.
- method: `light_assisted_operational_proxy_v1`
- 의미: 참가자가 실제로 발견했다는 주장이 아니라, 조명된 표적을 볼 수
  있도록 한 표준화된 operational opportunity onset
- primary latency: `visual-opportunity -> PILOT_RESPONSE_START`
- secondary latency: `spawn -> response`
- diagnostic: `20 px INTRUDER_VISUALLY_DETECTABLE -> response`; event가 없을
  수도 있으며 v33 clean 필수조건이 아니다.

응답 검출 및 분석:

- Java는 opportunity 전 약 1초 입력 평균을 baseline으로 만들고 기존
  sustained-delta 규칙으로 response start/end를 판정한다.
- opportunity 후 나중에 20 px event가 발생해도 baseline/anchor를 다시 열지
  않는다.
- v33 clean은 opportunity event가 정확히 1회이고 spawn과 trial end 사이에
  순서대로 존재해야 한다.
- Java session/batch 및 Tobii v9는 opportunity의 screen 좌표, 거리, bearing,
  span, light write 결과와 opportunity-relative response/gaze 지표를 export한다.

정적 형상 검증 결과:

- 90~110 kt ownship 범위에서 FRONT 초기거리 약 `1.86~2.23 km`, span
  `7.9~9.4 px`
- LEFT/RIGHT 초기거리 약 `1.56~1.90 km`, span `12.3~14.7 px`
- 모든 계산점은 화면 내·5 px 이상 조건을 만족했다.
- 20 px는 전 궤적에서 보장되지 않았으므로 보조 지표로 둔다.

다음 단계는 실제 X-Plane에서 FlyWithLua reload 후 세 방향 위치와 light
표시를 확인하고, Java/Tobii를 함께 실행한 6-trial 짧은 validation session을
수집하는 것이다.

## 2026-08-04 v33 물리 검증 완료 상태

실험용 X-Plane traffic 구성은 ownship 외 AI aircraft Cessna 172 한 대만
사용한다. 19대 AI 상태에서 첫 physical run이 3 trial 뒤 내장 ATC
`GetGeoPositionAtT` assert로 종료됐고, AI를 한 대로 줄인 뒤 6-trial retry는
crash 없이 완료됐다. JoinFS도 실험 중 비활성화한다.

완료 세션:

```text
session_visual_opportunity_v33_retry_20260804_134141
X-Plane: logs/xplane/xplane_visual_opportunity_v33_retry_20260804_134141*.csv
Tobii:   logs/tobii/session_xplane_tobii_20260804_134103_gaze.csv
```

완결성/장치 품질:

- 6 trials, 6 clean, 0 incomplete
- RIGHT/FRONT/LEFT 각 2회
- STATE/INTRUDER `3083/3083`, EVENT `101`
- gaze `21199`, valid `17970` (`84.8%`)
- 모든 trial에서 opportunity, valid response baseline, response start/end,
  minimum distance, trial end가 존재
- 모든 opportunity에서 light write 성공 및 `recognition_claim=false`
- 실제 screen x/bearing: RIGHT 약 `3878/+32°`, FRONT `2880/0°`,
  LEFT 약 `1882/-32°`

이번 세션의 primary opportunity-to-response는 평균 `5.983 s`, 중앙값
`5.369 s`였으며 6개 값은 `12.323, 10.337, 6.166, 4.571, 1.636,
0.849 s`다. 4개 trial의 response는 보조 20 px event보다 먼저였으므로
20 px를 primary로 두지 않은 결정과 일치한다.

다만 이 값은 아직 avoidance latency 확정값이 아니다. trigger axis는
throttle 4회, pitch 2회였고 trial 6은 response가 external-view transition보다
먼저였다. 또한 이 session의 방향순서가 RIGHT-RIGHT-FRONT-FRONT-LEFT-LEFT,
spawn AGL이 약 2032 ft에서 526 ft로 감소해 trial order와 direction이
confounded됐다. response와 trial order correlation `r=-0.986`은 반복학습,
접근고도, 방향효과를 분리해야 함을 보여준다.

따라서 현재 판정은 다음과 같다.

- v33 screen geometry/light/event/data merge: physical validation PASS
- 6-trial recording completeness: PASS
- 단일 session 방향효과 추론: 불가
- 모든 자동 response를 침입기 회피로 해석: 불가
- 다음 보완: avoidance-vs-speed-task behavior validity와 dynamic object AOI

## 2026-08-05 v35 속도 안정 후 intruder 생성 기준

목적은 음성 속도지시 직후의 throttle 조작과 intruder 회피 조작이 같은 시점에
겹치는 빈도를 줄이는 것이다. 속도과제 완료를 무기한 기다리는 방식은 trial
길이와 위험 노출시점을 참가자 수행에 종속시키므로 사용하지 않는다. v35는
무작위 candidate와 제한시간을 함께 둔 bounded gate다.

동작 순서:

1. trial 시작 3초 뒤 속도 음성지시를 재생한다.
2. 음성 시작 기준 `6~10 s`에서 candidate spawn 시각을 무작위 선정한다.
3. candidate 이후 목표 속도 `±5 KIAS`, IAS 변화율 `±1 KIAS/s`를 `1.5 s`
   연속 만족한 첫 시점에 intruder를 생성한다.
4. 안정조건을 충족하지 못하면 음성 시작 `14 s`에 강제 생성한다.

이 기준은 “속도과제가 완벽히 끝난 뒤에만 생성”을 뜻하지 않는다. 생성 지연을
상한 14초로 제한하면서 대부분의 정상 조절 trial에서는 급격한 속도 조작이
진정된 뒤 intruder를 노출하려는 절충안이다. 강제 생성 trial은 버리지 않고
`spawn_forced_by_timeout=true`로 보존해 sensitivity analysis 또는 공변량으로
사용한다.

주요 기록값은 `spawn_gate_method`, `planned/candidate_audio_to_spawn_s`,
`spawn_timeout_audio_to_spawn_s`, `speed_stable_before_spawn`,
`speed_error_at_spawn_kias`, `speed_rate_at_spawn_kias_s`,
`speed_stability_duration_s`, `spawn_wait_after_candidate_s`,
`spawn_forced_by_timeout`이다. 원본 UDP/CSV 스키마는 바꾸지 않고 event detail과
분석 export 열만 확장했다.

기존 v33 retry의 STATE 로그를 근사 재생한 결과 ±3 KIAS 기준은 3/6이 timeout,
±5 KIAS 기준은 1/6이 timeout으로 예상됐다. ±5 KIAS는 기존 pre-spawn
within-tolerance 지표와도 일치한다. 단, 이전 protocol의 저주기 STATE를 사용한
후향 근사값이므로 v35 runtime 6-trial로 실제 비율을 재확인해야 한다.

검증 상태:

- Java 21 compile 및 기존 자동 event 회귀: PASS
- Java v35 안정 생성과 14초 강제 생성 판정: PASS
- Python v35 timing 판정 및 behavior classifier 회귀: PASS
- batch trial CSV header/data: `117/117` columns 일치
- 기준 Lua/snapshot/실제 Scripts 실행본 hash 일치:
  `D9604131AF8A7EDB63EE906221127860287CCA458D09FE24027151421C4B132B`
- 실제 FlyWithLua reload: PASS; X-Plane `Log.txt`에서 v35 load와 오류·quarantine
  없음 확인
- v35 물리 6-trial: 완료; 아래 first physical validation 결과 참조

## 2026-08-05 v35 first physical validation

수집 파일:

```text
session_speed_gate_v35_validation_20260805_154919
X-Plane: logs/xplane/xplane_speed_gate_v35_validation_20260805_154919*.csv
Tobii:   logs/tobii/session_xplane_tobii_20260805_154831_gaze.csv
```

데이터 완결성은 정상이다. STATE/INTRUDER/EVENT는 `3049/3049/103` rows이고,
6/6 trial이 clean이며 Java/Tobii timing validation을 통과했다. 방향표는
FRONT-LEFT-RIGHT / LEFT-FRONT-RIGHT의 두 균형 블록이다. hazard는 4/6에서
발생했고 session 최소 수평거리/그때 수직분리는 `4.713/10.220 m`였다.

v35 gate 결과는 stable 3회(trial 2,4,6), forced-timeout 3회(trial 1,3,5)다.
trial 3은 목표속도 오차 `-6.499 KIAS`, rate `+1.412 KIAS/s`로 명확한 미완료다.
trial 1과 5는 timeout 시 오차가 약 `+0.3 KIAS`였지만 1.5초 연속 rate 조건을
만족하지 못했다. 따라서 50% forced는 코드 실패가 아니라 현재 raw rate 조건의
민감도를 보여준다.

같은 pre-spawn 원본으로 수행한 sensitivity check:

- 현재 `±5 KIAS + rate ±1 KIAS/s + 1.5 s`: forced `3/6`
- rate window만 0.25에서 1.0초로 확대: forced `3/6`
- rate tolerance를 ±1.25 KIAS/s로 완화: forced `1/6`
- rate를 필수조건에서 제외하고 `±5 KIAS를 1.5 s 연속`: forced `1/6`

논문 설명 가능성과 단일 pilot에 맞춘 derivative threshold의 과적합 위험을
고려하면 마지막 규칙이 더 단순하다. IAS rate는 spawn 시 계속 기록해 사후
공변량/품질진단으로 사용할 수 있다. 이 판단에 따라 아래 v36 target-band
dwell 규칙으로 구현했고 실제 6-trial 검증까지 완료했다.

Tobii는 전체 `29030` rows 중 `21905 (75.5%)`가 valid이고 trial별 validity는
`79.2~92.6%`다. opportunity-to-response는
`10.058, 0.876, 0.752, 2.831, 0.496, 7.264 s`다. behavior evidence는
AMBIGUOUS 4, AVOIDANCE_CANDIDATE 1, SPEED_TASK_CORRECTION 1이다. 따라서 속도
gate는 timing confound를 줄이는 보조장치이지 회피 의도를 직접 판정하는
장치가 아니다. recording clean, spawn-gate status, behavior class를 분리해
보고해야 한다.

## 2026-08-05 v36 target-band dwell 기준

v35 physical validation의 목적은 gate의 작동 여부와 forced 비율을 확인하는
것이었다. v35는 정상 작동했지만 trial 1과 5가 timeout 시 목표오차 약
`+0.3 KIAS`임에도 short-window IAS-rate 조건 때문에 dwell이 reset됐다.
후향 sensitivity에서 rate 계산창을 늘리는 것만으로는 개선되지 않았고,
derivative tolerance를 새로 맞추는 것은 단일 pilot 자료에 과적합될 수 있다.

따라서 v36은 다음 규칙을 사용한다.

1. audio start 후 candidate를 `6~10 s`에서 무작위 선정한다.
2. 목표속도 `±5 KIAS` 범위를 `1.5 s` 연속 유지한다.
3. candidate와 dwell을 모두 만족한 첫 시점에 intruder를 생성한다.
4. 만족하지 못하면 audio start `14 s`에 forced spawn한다.

IAS rate는 삭제하지 않는다. `0.25 s` 간격 진단값을 계산하고 spawn event,
Java/Tobii summary, behavior summary에 보존한다. 다만 v36 timing validity를
결정하지 않는다. 이 규칙은 목표속도 과제의 성공조건을 직접 사용하므로 raw
derivative cutoff보다 단순하고 논문에서 재현하기 쉽다.

구현 식별자:

- Lua: `260805_target_band_dwell_spawn_gate_v36`
- protocol: `audio_task_v36`
- profile: `visual_opportunity_proxy_v36`
- method: `bounded_speed_band_dwell_v2`
- event flags: `speed_gate_condition=target_error_band_dwell`,
  `speed_rate_diagnostic_only=true`
- Python analyzer: `260805_target_band_dwell_spawn_gate_v11`

검증 상태:

- Java/Python v36 stable·forced·invalid metadata 분기: PASS
- v36 rate diagnostic-only와 v35 rate-required 후방 호환: PASS
- Java batch header/data: `117/117`
- 실제 v35 6-trial Java/Tobii 회귀: `6/6 clean`, timing-valid `6/6`, gaze 결과 유지
- 저장소 기준본·v36 snapshot·실제 Scripts 실행본 SHA-256:
  `1D015BF392ADF09ECDFB4AA32275B804A3F0D76AC58A8C50D7829C2B850AA3BA`
- 실제 Scripts 폴더의 Lua 파일: 활성 통합본 `1`개
- 실제 FlyWithLua v36 reload: PASS; v36 load, all scripts loaded successfully,
  quarantine Lua `0` 확인
- physical 6-trial: PASS; 아래 first physical validation 결과 참조

## 2026-08-05 v36 first physical validation 해석

동일 saved scenario에서 v36 자동 6-trial을 실행했다.

```text
Session: session_speed_gate_v36_validation_20260805_162420
X-Plane: logs/xplane/xplane_speed_gate_v36_validation_20260805_162420*.csv
Tobii: logs/tobii/session_xplane_tobii_20260805_162344_gaze.csv
```

데이터 완결성은 정상이다. STATE/INTRUDER/EVENT는 `2841/2841/105` rows이고,
6/6 trial이 recording clean 및 v36 timing-valid다. 방향은 두 block 모두
LEFT-FRONT-RIGHT로 균형을 만족했고 목표속도는 `105/95/100/90/90/110 KIAS`다.

v36 target-band dwell은 stable `4/6`, forced `2/6`이다. forced trial 4와 6은
spawn error가 `+6.893/-7.835 KIAS`이므로 실제 목표 범위 미달성이다. 반대로
stable trial 2와 3은 spawn rate가 구 v35 cutoff 바깥인 `-1.020/+1.199 KIAS/s`
였지만 오차/dwell 조건을 만족해 정상 생성됐다. 즉 v36은 raw derivative의
순간 변동 때문에 목표 범위 달성을 거부하는 문제를 제거하면서, 실제 범위
미달성은 timeout으로 남겼다. 현재 gate 규칙의 engineering 목적은 달성했다.

Tobii raw validity는 `82.4%`, trial별 `84.6~92.6%`다. 평균
visual-opportunity-to-response는 `4.402 s`이고 모든 trial에서 response 전
operational-external evidence가 있다. 다만 이것은 intruder를 보았다는 확정
증거가 아니다. behavior evidence도 AMBIGUOUS 4, AVOIDANCE_CANDIDATE 2이므로
speed gate만으로 control intent를 완전히 분리할 수 없다.

현재 5 px light-assisted opportunity 기준은 생성 당시 예상 span
`8.556~14.259 px`보다 낮아 6/6 모두 spawn과 동시에 충족됐다. 따라서 이번
session에서 visual-opportunity-to-response와 spawn-to-response는 같은 값이다.
20 px detectability는 spawn 후 `5.160~11.283 s`였지만 5/6 response가 그보다
먼저 검출됐다. 이는 20 px를 primary anchor로 바로 대체할 근거가 아니라,
response detector가 속도과제 조작도 포착할 수 있음을 함께 보여준다. 논문에서는
5 px event를 recognition time이 아닌 light-assisted operational opportunity로
제한해 표현하고, spawn과의 동시 발생률을 반드시 보고한다.

결론적으로 v36 gate는 현재 값으로 동결 가능한 engineering 후보지만, 본실험
통계에서는 stable/forced를 recording clean과 별도로 보존하고 forced trial
포함/제외 sensitivity를 제시하는 것이 적절하다.

## 2026-08-06 그룹미팅 이후 연구범위 조정

교수 피드백에 따라 조종 입력의 실제 이유를 speed correction과 intruder
avoidance로 반드시 구분하는 것은 primary research question에서 제외한다.
현재 behavior-evidence classifier는 삭제하지 않고 보조 screening으로만
유지한다. 주 분석은 표준화된 visual opportunity 이후의 조종 반응시간과
시선의 공간 분포에 둔다.

교수가 제안한 `격자`는 화면을 동일한 공간 셀로 나누어 gaze 위치·분포·이동을
객관적으로 기록하는 방법이다. 이는 `RUNWAY`, `INTRUDER`, `OTHER` 같은 의미
범주와 다른 층위다. Grid cell은 위치를 제공하지만 그 위치를 본 목적은 별도
AOI/object mapping으로 정의해야 한다.

권장 결합 구조:

1. raw spatial layer: monitor와 grid row/column을 그대로 보존한다.
2. object layer: intruder projected x/y/span의 dynamic AOI와 runway AOI를
   적용한다.
3. semantic layer: `RUNWAY_GUIDANCE`, `INTRUDER_SEARCH_OR_TRACKING`,
   `OTHER_EXTERNAL`, `UNRESOLVED`를 산출한다.

좌우 monitor는 통제된 실험 목적상 intruder search 용도로 해석할 수 있지만,
적어도 spawn 이후 intruder 방향과 일치하는 measured side episode를
`INTRUDER_SEARCH_PROXY`로 사용한다. 이 proxy도 실제 발견 또는 fixation의
직접 측정값은 아니다. 정면 monitor는 runway와 intruder가 같은 방향에 있을 수
있으므로 grid만으로 이분하지 않고 dynamic AOI와 residual `OTHER`를 남긴다.

현재 v36 intruder는 초기 `8.556~14.259 px`이며 5 px opportunity가 spawn과
동시이고 beacon/nav/strobe도 코드상 ON이다. 다음 scenario revision은 lights를
끄고 fixed distance가 아닌 initial projected span 및 목표
spawn-to-opportunity delay를 기준으로 거리·속도를 계산하는 방향이다. 이 변경은
아직 구현 전이며 짧은 3방향 calibration으로 geometry를 확인한 뒤 동결한다.

## 2026-08-11 v37 geometry-opportunity calibration 구현

v37은 위 계획의 첫 physical calibration build다. 인공적인 시각 단서를 줄이기
위해 intruder 조명을 모두 끄고, spawn 시점이 아니라 예상 화면 폭이 `20 px`에
처음 도달하는 시점을 `INTRUDER_VISUAL_OPPORTUNITY_ONSET`으로 기록한다.

구현 식별자:

- Lua: `260811_geometry_opportunity_calibration_v37`
- protocol/profile: `audio_task_v37` / `visual_opportunity_proxy_v37`
- opportunity method: `geometry_span_operational_proxy_v2`
- Python analyzer: `260811_geometry_opportunity_calibration_v12`
- Lua snapshot:
  `FLYWITHLUA_STUDY_INTEGRATED_260811_geometry_opportunity_calibration_v37.lua`

시나리오 변경:

- crossing과 head-on intruder의 beacon/nav/strobe/landing/taxi light OFF
- crossing base speed `65 m/s`로 조정
- geometry threshold `20 px`
- 교정용 initial-span 목표 범위 `7~13 px`
- 교정용 spawn-to-opportunity 목표 범위 `5~11 s`
- 생성 event에 initial span, 화면좌표, 거리, 조명 OFF 상태, 목표범위를 기록
- opportunity event에 실제 threshold 도달 span/화면좌표/거리/지연시간을 기록

`20 px`는 참가자의 실제 발견을 증명하는 생리·행동 측정값이 아니다. 동일한
화면 geometry에서 분석 시작점을 표준화하기 위한 operational visual-opportunity
proxy다. 논문에서는 `discovery time`으로 단정하지 않고
`geometry-defined visual opportunity onset`으로 표현한다.

정적/회귀 검증:

- Java 21 compile PASS
- `XPlaneAutoEventDetectorSmokeTest` PASS; opportunity가 spawn 뒤에 발생해도
  pre-opportunity baseline과 response가 정상 생성됨
- `XPlaneRandomSpeedTaskAnalysisSmokeTest` PASS; v37 target-band timing 허용
- Python analyzer/classifier compile 및 smoke PASS
- 실제 v36 6-trial 재분석: Java `6/6 clean`, Python gaze
  `20748/25185 = 82.4%` 유지
- 기준본·snapshot·Scripts 실행본 SHA-256:
  `002B1235B3C2BE6A7833435D6CCF91F876874DF7A8A575D06794705D310328F7`

아직 동결하지 않은 항목은 `20 px`, `65 m/s`, initial span `7~13 px`, delay
`5~11 s`다. 다음 단계는 FlyWithLua reload 후 자동 6-trial 전체 실험이 아니라
FRONT/LEFT/RIGHT를 포함하는 짧은 교정 세션으로 조명 OFF, 초기 span, threshold
도달 지연, 화면 내 위치와 session 소요시간을 확인하는 것이다.

2026-08-11 runtime reload는 PASS했다. `Log.txt`에서 v37 script load와
`All script files loaded successfully`를 확인했고 Lua stop/error는 없다. 실제
Scripts 폴더에는 활성 통합 Lua 1개만 있으며 `Scripts (Quarantine)`에는 Lua가
없다. reload 이전 v36 event가 같은 `Log.txt`에 남아 있으므로 runtime 판정은
마지막 reload 이후 구간의 version과 protocol을 기준으로 한다.

## 2026-08-11 v37 physical calibration interpretation

v37 자동 6-trial physical calibration은 recording 및 geometry 기준으로
성공했다. RIGHT/FRONT/LEFT가 각각 2회였고, 모든 trial에서 spawn, geometry
opportunity, detectability, response와 trial end가 한 번씩 기록돼 Java clean
`6/6`이다. 조명 event metadata는 `lights=off`, `lights_write_ok=true`가 `6/6`이며
실제 화면의 점멸 소멸 여부는 참가자 관찰을 별도로 확인해야 한다.

방향별 geometry 평균:

| Direction | Initial span | Initial range | Spawn→20 px | 20 px range |
|---|---:|---:|---:|---:|
| FRONT | 8.284 px | 2128.0 m | 10.540 s | 884.2 m |
| LEFT | 12.660 px | 1929.9 m | 7.249 s | 1221.6 m |
| RIGHT | 13.288 px | 1838.6 m | 6.216 s | 1227.6 m |

spawn-to-opportunity는 전체 `6.124~10.949 s`라 교정 목표 `5~11 s`를 `6/6`
만족한다. initial span은 `8.021~13.384 px`이며 잠정 upper bound 13 px를 넘은
3건도 최대 초과가 `0.384 px`다. 따라서 `65 m/s + 20 px` geometry는 다음
분석단계에 사용할 engineering candidate로 본다. 그러나 방향별 opportunity
지연이 동일하다는 뜻은 아니며 FRONT가 side보다 약 3~4초 늦다. primary latency는
각 방향에서 실제 20 px 도달 event 이후로 계산한다.

opportunity-to-response 평균/중앙값은 `1.800/1.936 s`이고 `6/6` 모두 event
이후 response다. trial 2는 opportunity 당시 active control이라 `0.347 s`를
새로운 회피 의도의 직접값으로 해석하지 않는다. gaze 전체 raw validity는
`69.8%`, trial별 `85.4~92.7%`다. 전체값은 session 전후 비 trial 구간을 포함하므로
본실험 품질판정에는 trial-window validity를 우선한다.

중요한 실패는 speed gate다. 모든 trial이 목표속도 band dwell을 달성하지 못하고
14초 timeout으로 생성됐다. 즉 timing-valid/recording-clean은 `6/6`이지만
speed-stable은 `0/6`이다. 목표 속도를 독립적으로 `90~110 KIAS`에서 뽑으면
연속 trial 사이 요구 변화가 너무 커질 수 있다. 다음 권장 수정은 timeout이나
tolerance를 단순 확대하는 것이 아니라 audio 시점 현재 IAS를 5-knot 단위로
반올림한 뒤 가까운 목표를 선택해 과제를 실현 가능하게 만드는 것이다. 목표와
audio 시점 IAS 차이는 계속 저장해 난이도 공변량으로 사용한다.

# Tobii 외부시선 공간·객체·의미 분류 절차

## 목적

이 단계는 X-Plane 상태, intruder 상태, event, Tobii gaze를 PC timestamp로 결합해
외부시선을 다음 세 층으로 기록한다.

1. 공간층: 모니터별 `6 x 3` grid cell
2. 객체층: 매 gaze 시각의 projected intruder dynamic AOI와 RKSI runway 34 dynamic AOI
3. 의미층: `INTRUDER_SEARCH_OR_TRACKING`, `RUNWAY_GUIDANCE`, `OTHER_EXTERNAL`,
   `UNRESOLVED`

이 분류는 시선 위치와 시나리오 geometry의 중첩에 기반한 operational proxy다.
실제 intruder 인지, 발견 또는 조종사 의도를 직접 측정한 값으로 사용하지 않는다.

## 사전 단계

먼저 기존 gaze AOI 병합을 실행한다.

```powershell
py -3.10 tools\tobii\analyze_xplane_tobii_session.py `
  --events "logs\xplane\<prefix>_events.csv" `
  --gaze "logs\tobii\<gaze>.csv" `
  --aoi "resources\cessna_instrument_aoi_260710.csv" `
  --output-prefix "build_atc_tmp\<prefix>" `
  --layout-mode surround-wide `
  --screen-width 5760 `
  --screen-height 1080 `
  --monitor-width 1920 `
  --center-monitor-index 1
```

## 외부시선 분류 실행

```powershell
py -3.10 tools\tobii\classify_xplane_external_gaze.py `
  --state "logs\xplane\<prefix>.csv" `
  --intruder "logs\xplane\<prefix>_intruder.csv" `
  --events "logs\xplane\<prefix>_events.csv" `
  --gaze-aoi "build_atc_tmp\<prefix>_gaze_aoi.csv" `
  --runway-config "resources\rksi_runway34_geometry_260819.csv" `
  --runway-id "34" `
  --output-prefix "build_atc_tmp\<prefix>"
```

## 현재 기본값

- display: `5760 x 1080`
- monitor width: `1920 px`
- horizontal FOV: `122 deg`
- exterior/panel boundary: `y=600 px`
- grid: monitor별 `6 columns x 3 rows`
- intruder AOI margin: `45 px`
- runway AOI margin: `45 px`
- semantic confirmation dwell: `200 ms`
- maximum gaze sample gap: `100 ms`
- maximum gaze-to-X-Plane state gap: `250 ms`
- intruder model: wingspan `11.0 m`, length `8.3 m`
- runway: RKSI 34, X-Plane 11 default `apt.dat` 기준

`45 px` margin은 현재 engineering candidate다. v39 단일 세션의 `30/45/60 px`
sensitivity에서 분류 비율이 변하므로 본실험 전에 수동 표적 주시 또는 화면 녹화와
비교해 동결한다. 논문에서는 기본값과 sensitivity를 함께 보고한다.

## 분류 우선순위

외부시선으로 확인된 표본에 다음 순서를 적용한다.

1. `DYNAMIC_INTRUDER_AOI_OVERLAP`
2. `DYNAMIC_RUNWAY_AOI_OVERLAP`
3. `INTRUDER_GRID_CELL_MATCH_PROXY`
4. spawn 이후 접근 방향과 일치하는 `MEASURED_SIDE_DIRECTION_MATCH_PROXY`
5. `EXTERNAL_RESIDUAL`
6. gaze tracking 또는 X-Plane time join이 불충분하면 `UNRESOLVED`

동일 semantic candidate가 `200 ms` 이상 지속되어야 확정한다. 그보다 짧은 표본은
`UNRESOLVED` 및 `TRANSIENT_LT_DWELL`로 남긴다.

## 출력

- `*_external_context_gaze.csv`
  - 매 gaze 시각의 grid, intruder screen x/y/span, AOI bounds, runway polygon,
    candidate/class/evidence
- `*_external_semantic_episodes.csv`
  - 200 ms 지속조건을 적용한 의미 episode
- `*_external_semantic_summary.csv`
  - trial별 의미 비율, 첫 intruder evidence latency, direct dynamic-overlap latency
- `*_external_grid_summary.csv`
  - trial/grid cell별 의미 분포
- `*_projection_validation.csv`
  - Lua spawn/opportunity event geometry와 사후 투영값 비교
- `*_external_context_metadata.csv`
  - classifier version과 모든 분석 parameter
- `*_external_context_overview.svg`
  - 6-trial 격자, intruder trajectory, runway polygon, 의미별 gaze 점검 그림

## v39 통합 세션 검증

입력:

- X-Plane: `xplane_v39_integrated_20260812_155103`
- gaze: `session_xplane_tobii_20260812_155022_gaze.csv`

투영 검증 median absolute error:

- screen x: `0.083 px`
- screen y: `0.363 px`
- estimated span: `0.026 px`

기본 `45 px` 결과, trial 내부 operational-external `5944` rows 중:

- `INTRUDER_SEARCH_OR_TRACKING`: `1478`, `24.9%`
- `RUNWAY_GUIDANCE`: `298`, `5.0%`
- `OTHER_EXTERNAL`: `2845`, `47.9%`
- `UNRESOLVED`: `1323`, `22.3%`

Intruder 분류 근거:

- direct dynamic AOI overlap: `879` rows
- same grid cell proxy: `438` rows
- measured side-direction proxy: `161` rows

이 결과는 동일 참가자 자료로 기본 margin을 선택하고 평가한 engineering 결과다.
본실험의 분류 정확도 또는 인간 인지 정확도로 보고하지 않는다.

## 검증 상태

- Python syntax/compile: PASS
- synthetic smoke: PASS
- 기존 gaze analyzer smoke: PASS
- 기존 behavior classifier smoke: PASS
- v39 actual projection cross-check: PASS
- v37 backward actual-session cross-check: PASS
- SVG XML structure: PASS
- 화면 녹화 또는 통제된 표적 주시와의 semantic accuracy 수동 검증: pending

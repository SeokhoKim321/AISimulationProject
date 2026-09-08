# 제3회 해병항공 전투발전 워크숍 발표자료

- 일자: 2026년 9월 22일
- 제목: AI 데이터를 활용한 조종능력 향상 방안
- 발표자: 김석호
- 기준 논문: `2026 APISAT/APISAT-2026_FullPaper_KimSeokho_merged.pdf`

## 산출물

- `제3회_해병항공_전투발전_워크숍_AI데이터_조종능력향상_김석호_260922.pptx`
- `제3회_해병항공_전투발전_워크숍_AI데이터_조종능력향상_김석호_260922.pdf`
- 본 발표 17장과 부록 3장, 총 20장
- 본 발표 발표자 노트 기준 약 19분 55초

## 핵심 메시지

현재 구현한 X-Plane·Java·Tobii 통합 프레임워크는 비행 상태, 조종 입력, 시선과 시나리오 이벤트를 같은 trial 시간축에 정렬한다. 이를 통해 교관을 대체하는 자동 채점이 아니라, 조종사가 언제 무엇을 보고 어떻게 조작했는지를 설명할 수 있는 데이터 기반 디브리핑을 지원하는 것이 발표의 중심 제안이다.

군 적용 시에는 X-Plane 자체를 도입하는 것이 아니라, 인가된 해병항공 훈련 시뮬레이터의 상태·입력·이벤트 데이터를 공통 계약으로 변환하는 어댑터를 두고 현재 분석 계층을 이식하는 구조를 제안한다.

## 현재 기술 검증 결과

- 완전 세션 6개, 총 36 trial
- trial 기준 평균 시선 유효율 약 96.3%
- 반응 검출 31/36 trial(86.1%)
- 검출된 visual-opportunity-to-response 평균 3.135초, 중앙값 2.138초
- full-trial DTMC 전이 5,942회
- 동일 AOI 자기전이율 79.9%

이 수치는 소규모 기술 검증 결과이며 숙련도 효과나 일반 조종사 집단의 특성을 입증하는 결과로 해석하지 않는다.

## 분석 개념

- `visual opportunity`: intruder가 화면에서 사전에 정한 크기 기준(현재 20 px)에 도달한 시점이다. 실제 조종사가 인지했다고 확정하는 시점은 아니다.
- 고정 계기 AOI, 격자 기반 외부환경 AOI, 동적 intruder AOI를 함께 사용한다.
- AOI 전이 Matrix(DTMC)와 next-switch Matrix를 이용해 시선 체류와 다음 관측 대상의 경향을 기술한다.

## 군 활용 제안

1. 훈련 후 event-aligned 재생과 교관 디브리핑 보조
2. 반복 훈련 간 개인별 scan·조종 변화 추적
3. 저고도·해상·공중기동 등 임무별 표준 시나리오 비교
4. 충분한 데이터 축적 후 숙련 조종사·SOP 기준 모델과의 비교 및 설명 가능한 AI 보조로 확장

## 한계와 후속 검증

- 20 px 기준은 관측 기회이지 인지 확인이 아니다.
- 현재 Matrix는 숙련 조종사 표준 모델이 아니라 기술 검증용 소규모 자료다.
- 동적 intruder AOI의 45 px 여유폭은 provisional 값으로 민감도 검증이 필요하다.
- 속도 gate는 stable 16회, timeout에 의한 forced 20회였고 110 KIAS 목표는 7/7회 forced였다.
- 해병항공 또는 마린온 시뮬레이터에서 직접 검증된 결과가 아니며, 적용 가능 구조를 제안한 단계다.

## 재생성

프로젝트 루트에서 다음 명령을 실행한다.

```powershell
& "$env:WINDIR\System32\WindowsPowerShell\v1.0\powershell.exe" `
  -NoProfile `
  -ExecutionPolicy Bypass `
  -File "tools\docs\create_marine_aviation_workshop_presentation.ps1"
```

스크립트가 사용하는 AOI 및 Matrix 이미지는 이 폴더의 `assets`에 함께 보관한다.

## 공개 근거 자료

- 방위사업청, 마린온 해병대 인도 관련 보도자료: https://www.dapa.go.kr
- KAI, MUH-1 마린온 및 훈련체계 소개: https://www.koreaaero.com
- 방위사업청, KUH-1 비행훈련시뮬레이터 관련 자료: https://www.dapa.go.kr

# ATC X-Plane 연동 실행 명령 정리 (2026-06-10)

## 최종 확인된 결론

- X-Plane/Lua intruder 생성 로직은 정상 동작한다.
- `INTRUDER_SPAWNED`, `HAZARD_DETECTED`, `ADVISORY_SHOWN` 이벤트와 intruder CSV 위경도/고도 기록이 정상 확인됐다.
- 관제 화면에 intruder를 표시하려면 `SDP_XP` 또는 별도 `PLT_INTR` 연결을 쓰면 안 된다.
- 최종 성공 방식은 `PLT_XP` 한 연결에서 ownship과 intruder를 같이 보내는 방식이다.

```text
PLT_XP single TCP link
    ownship  -> fid:xplane01
    intruder -> fid:intruder01
```

## 실행 전 준비

1. 기존 Java receiver가 실행 중이면 PowerShell에서 `Ctrl+C`로 종료한다.
2. ATC Simulator Server에서 `Close` 후 `Open`을 다시 누른다.
3. X-Plane FlyWithLua `Scripts` 폴더에는 최신 `FLYWITHLUA_STUDY_INTEGRATED.lua` 하나만 둔다.
4. Lua 버전은 `260610_intruder_geo_atc_v21`이어야 한다.

## 최종 권장 실행 명령

먼저 프로젝트 폴더로 이동한다.

```powershell
cd "C:\Users\RPAS2\OneDrive - 인하대학교\JAVA 관련\AISimulationProject"
```

그다음 아래 줄바꿈 버전을 우선 사용한다. 긴 한 줄 명령은 뒤쪽 옵션 누락 실수가 생기기 쉬우므로 기본으로 쓰지 않는다.

```powershell
java -cp build_atc_tmp com.example.ai.XPlaneReceiverMain `
  9100 `
  build_atc_tmp\xplane_atc_intruder_onlink_final.csv `
  session_atc_intruder_onlink_final `
  --atc-host 172.16.150.130 `
  --atc-port 50000 `
  --atc-module PLT_XP `
  --atc-fid xplane01 `
  --atc-intruder-fid intruder01 `
  --atc-intruder-on-ownship-link `
  --atc-intruder-fdt `
  --atc-every 5
```

각 줄 끝의 백틱 `` ` ``이 빠지면 다음 줄 옵션이 Java에 전달되지 않는다. 특히 아래 두 옵션은 반드시 포함되어야 한다.

```text
--atc-intruder-on-ownship-link
--atc-intruder-fdt
```

## 한 줄 버전

명령 누락 위험이 없을 때만 아래 한 줄 버전을 사용한다.

```powershell
java -cp build_atc_tmp com.example.ai.XPlaneReceiverMain 9100 build_atc_tmp\xplane_atc_intruder_onlink_final.csv session_atc_intruder_onlink_final --atc-host 172.16.150.130 --atc-port 50000 --atc-module PLT_XP --atc-fid xplane01 --atc-intruder-fid intruder01 --atc-intruder-on-ownship-link --atc-intruder-fdt --atc-every 5
```

## 정상 출력

최종 성공 방식에서는 ATC 등록 메시지가 하나만 떠야 한다.

```text
ATC bridge registered as plt#PLT_XP
```

설정 출력에는 아래 값이 보여야 한다.

```text
ownshipLink=true
```

`sdp#SDP_XP` 또는 `plt#PLT_INTR`가 같이 뜨면 최종 성공 방식이 아니다.

아래 출력은 잘못 실행된 상태이다.

```text
ATC intruder bridge: ... module=SDP#SDP_XP ... fdt=false ownshipLink=false
ATC bridge registered as sdp#SDP_XP
```

이 경우 receiver를 `Ctrl+C`로 끄고, ATC Simulator Server를 `Close`/`Open` 한 뒤 줄바꿈 버전으로 다시 실행한다.

## 관제 화면 확인

X-Plane trial을 실행한 뒤 관제 화면에서 다음을 확인한다.

- `xplane01` 표시
- intruder 발생 시점 이후 `intruder01` 표시

## 테스트 후 확인 파일

위 명령 기준 생성 파일:

- `build_atc_tmp\xplane_atc_intruder_onlink_final.csv`
- `build_atc_tmp\xplane_atc_intruder_onlink_final_intruder.csv`
- `build_atc_tmp\xplane_atc_intruder_onlink_final_events.csv`

재실행할 때는 파일명과 session id를 바꿔 로그가 섞이지 않게 한다.

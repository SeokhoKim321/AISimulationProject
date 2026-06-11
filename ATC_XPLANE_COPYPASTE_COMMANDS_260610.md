# ATC X-Plane Copy-Paste Commands 260610

## 1. Move To Project Folder

```powershell
cd "C:\Users\RPAS2\OneDrive - 인하대학교\JAVA 관련\AISimulationProject"
```

## 2. Final Confirmed Command

Use this command after closing the old Java receiver and reopening the ATC Simulator Server.

```powershell
java -cp build_atc_tmp com.example.ai.XPlaneReceiverMain 9100 build_atc_tmp\xplane_atc_intruder_onlink_final.csv session_atc_intruder_onlink_final --atc-host 172.16.150.130 --atc-port 50000 --atc-module PLT_XP --atc-fid xplane01 --atc-intruder-fid intruder01 --atc-intruder-on-ownship-link --atc-intruder-fdt --atc-every 5
```

## 3. Multi-Line PowerShell Version

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

## 4. Expected Output

Only one ATC registration line should appear.

```text
ATC bridge registered as plt#PLT_XP
```

This setting line should include:

```text
ownshipLink=true
```

If `sdp#SDP_XP` or `plt#PLT_INTR` appears, the command is not using the final confirmed path.

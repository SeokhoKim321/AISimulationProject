package flight.analysis;

import java.util.Hashtable;
import java.util.Vector;

import flight.Flights_Manager;
import geography.Position;
import physicalProperty.Angle;
import physicalProperty.Compound.Speed;

public class DetectAndAvoid {

	// Container
	private Vector<Object[]>	infos;
	private CalculateDWC		dwc;

	// Constructor
	public DetectAndAvoid() {
		this.dwc = new CalculateDWC();
	}

	// Calculate
	public void cal(Vector<Object[]> finfos, int phase) {
		this.infos = finfos;
		Hashtable<String, Integer> wcs = new Hashtable<String, Integer>();
		for(int o = 0; o < finfos.size(); o++) {
			Object[] own = finfos.get(o);
			if(own != null) {
				String ownfid = own[0].toString();
				if(!wcs.containsKey(ownfid)) {
					wcs.put(ownfid, (int) own[15]);
				}
				Position[] own_poss = (Position[]) own[5];
				Position own_pos = own_poss[0];				
				Speed own_gs = (Speed) own[6];
				Speed own_vr = (Speed) own[7];
				Angle own_crs = (Angle) own[8];
				for(int i = o+1; i < finfos.size(); i++) {
					Object[] itr = finfos.get(i);
					if(itr != null) {
						String itrfid = itr[0].toString();
						if(!wcs.containsKey(itrfid)) {
							wcs.put(itrfid, (int) itr[15]);
						}
						Position[] itr_poss = (Position[]) itr[5];
						Position itr_pos = itr_poss[0];
						Speed itr_gs = (Speed) itr[6];
						Speed itr_vr = (Speed) itr[7];
						Angle itr_crs = (Angle) itr[8];
						if(phase == Flights_Manager.Analysis_DWCPhase1) {
							String alert = this.dwc.calculationInPhase1(own_pos, itr_pos, own_gs, itr_gs, own_vr, itr_vr, own_crs, itr_crs);
							if(!alert.equals("-")) {
								int maxwcs_own = wcs.get(ownfid);
								int maxwcs_itr = wcs.get(itrfid);
								if(alert.equals("Preventive")) {
									if(maxwcs_own < Flights_Manager.Alert_DWC_Preventive) {
										wcs.put(ownfid, Flights_Manager.Alert_DWC_Preventive);
									}
									if(maxwcs_itr < Flights_Manager.Alert_DWC_Preventive) {
										wcs.put(itrfid, Flights_Manager.Alert_DWC_Preventive);
									}
								}else if(alert.equals("Corrective")) {
									if(maxwcs_own < Flights_Manager.Alert_DWC_Corrective) {
										wcs.put(ownfid, Flights_Manager.Alert_DWC_Corrective);
									}
									if(maxwcs_itr < Flights_Manager.Alert_DWC_Corrective) {
										wcs.put(itrfid, Flights_Manager.Alert_DWC_Corrective);
									}
								}else if(alert.equals("Warning")) {
									if(maxwcs_own < Flights_Manager.Alert_DWC_Warning) {
										wcs.put(ownfid, Flights_Manager.Alert_DWC_Warning);
									}
									if(maxwcs_itr < Flights_Manager.Alert_DWC_Warning) {
										wcs.put(itrfid, Flights_Manager.Alert_DWC_Warning);
									}
								}else if(alert.equals("LoDWC")) {
									if(maxwcs_own < Flights_Manager.Alert_DWC_LoDWC) {
										wcs.put(ownfid, Flights_Manager.Alert_DWC_LoDWC);
									}
									if(maxwcs_itr < Flights_Manager.Alert_DWC_LoDWC) {
										wcs.put(itrfid, Flights_Manager.Alert_DWC_LoDWC);
									}
								}
							}
						}else if(phase == Flights_Manager.Analysis_DWCPhase2) {
							String alert = this.dwc.calculationInPhase2(own_pos, itr_pos, own_gs, itr_gs, own_vr, itr_vr, own_crs, itr_crs);
							if(!alert.equals("-")) {
								int maxwcs_own = wcs.get(own[0].toString());
								int maxwcs_itr = wcs.get(itr[0].toString());
								if(alert.equals("Warning")) {
									if(maxwcs_own < Flights_Manager.Alert_DWC_Warning) {
										wcs.put(ownfid, Flights_Manager.Alert_DWC_Warning);
									}
									if(maxwcs_itr < Flights_Manager.Alert_DWC_Warning) {
										wcs.put(itrfid, Flights_Manager.Alert_DWC_Warning);
									}
								}else if(alert.equals("LoDWC")) {
									if(maxwcs_own < Flights_Manager.Alert_DWC_LoDWC) {
										wcs.put(ownfid, Flights_Manager.Alert_DWC_LoDWC);
									}
									if(maxwcs_itr < Flights_Manager.Alert_DWC_LoDWC) {
										wcs.put(itrfid, Flights_Manager.Alert_DWC_LoDWC);
									}
								}
							}
						}
					}
				}
			}
		}
		wcs.forEach((fid, level) -> { 
			for(Object[] info : this.infos) {
				if(info[0].toString().equals(fid)) {
					info[15] = level;
					break;
				}
			}
		});
	}

	// Getter
	public Vector<Object[]> getModFlightInfos(){
		return infos;
	}

}

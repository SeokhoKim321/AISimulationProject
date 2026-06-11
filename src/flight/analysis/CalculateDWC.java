// ==========================
// CalculateDWC.java
// ==========================

package flight.analysis;

import geography.Position;
import physicalProperty.Angle;
import physicalProperty.Length;
import physicalProperty.Units;
import physicalProperty.Compound.Speed;

public class CalculateDWC {

	/** Reference
	 * 
	 * Doc.				: RTCA DO-365A
	 * RTCA Paper No.	: 045-20/PMC-1896 
	 * Name				: Minimum Operational Performance Standards (MOPS) for Detect and Avoid (DAA) Systems
	 * Publication date : March 26, 2020
	 * Prepared by		: SC-228
	 * 
	 */

	// Phase 1 : Parameters of En-Route DWC Alerting Requirements
	private final String[]	alert_type_phase1	= new String[] {"Preventive", "Corrective", "Warning", "LoDWC"};
	private final int[]		alert_level_phase1	= new int[] {1, 2, 3, 4};
	private final int[]		tmod_star_phase1	= new int[] {35, 35, 35, 35};
	private final Length[]	hmd_star_phase1		= new Length[] {new Length(4000, Units.ft), new Length(4000, Units.ft), new Length(4000, Units.ft), new Length(4000, Units.ft)};
	private final Length[]	h_star_phase1		= new Length[] {new Length(700, Units.ft), new Length(450, Units.ft), new Length(450, Units.ft), new Length(450, Units.ft)};
	private final int[]		toa_phase1			= new int[] {55, 55, 25, 0};

	// Phase 2 : Parameters of Terminal Area DWC Alerting Requirements
	private final String[]	alert_type_phase2	= new String[] {"Warning", "LoDWC"};
	private final int[]		alert_level_phase2	= new int[] {3, 4};
	private final int[]		tmod_star_phase2	= new int[] {0, 0};
	private final Length[]	hmd_star_phase2		= new Length[] {new Length(1500, Units.ft), new Length(1500, Units.ft)};
	private final Length[]	h_star_phase2		= new Length[] {new Length(450, Units.ft), new Length(450, Units.ft)};
	private final int[]		toa_phase2			= new int[] {45, 0};

	// Constructor
	public CalculateDWC() {}

	// Calculate - *_pos: Position(X/Y/Z), *_gs: GroundSpeed, *_vr: VerticalRate, *_crs: Course(TrackAngle)
	public String calculationInPhase1(Position own_pos, Position itr_pos, Speed own_gs, Speed itr_gs, Speed own_vr, Speed itr_vr, Angle own_crs, Angle itr_crs) {
		int maxlv = 0;
		// Current status (Units: feet, degree, second)
		double own_x = own_pos.getX().get(Units.ft);
		double own_y = own_pos.getY().get(Units.ft);
		double own_z = own_pos.getZ().get(Units.ft);
		double own_vx = (own_gs.get(Units.fpm) / 60.0) * own_crs.sine();
		double own_vy = (own_gs.get(Units.fpm) / 60.0) * own_crs.cosine();
		double own_vz = (own_vr.get(Units.fpm) / 60.0);
		double itr_x = itr_pos.getX().get(Units.ft);
		double itr_y = itr_pos.getY().get(Units.ft);
		double itr_z = itr_pos.getZ().get(Units.ft);
		double itr_vx = (itr_gs.get(Units.fpm) / 60.0) * itr_crs.sine();
		double itr_vy = (itr_gs.get(Units.fpm) / 60.0) * itr_crs.cosine();
		double itr_vz = (itr_vr.get(Units.fpm) / 60.0);
		double v_rx = itr_vx - own_vx;
		double v_ry = itr_vy - own_vy;
		double rdot_now = ((itr_x - own_x) * v_rx + (itr_y - own_y) * v_ry) / Math.sqrt(Math.pow((itr_x - own_x), 2) + Math.pow((itr_y - own_y), 2));
		if(rdot_now < 0) {
			int caltimerange = 0;
			for(int toa : this.toa_phase1) {
				if(caltimerange < toa) {
					caltimerange = toa;
				}
			}
			for(int i = 0; i < caltimerange; i++) {
				// Estimated Position (Units: feet, degree, second)
				double own_x_p = own_x + (own_vx * (double) i);
				double own_y_p = own_y + (own_vy * (double) i);
				double own_z_p = own_z + (own_vz * (double) i);
				double itr_x_p = itr_x + (itr_vx * (double) i);
				double itr_y_p = itr_y + (itr_vy * (double) i);
				double itr_z_p = itr_z + (itr_vz * (double) i);
				double d_x = itr_x_p - own_x_p;
				double d_y = itr_y_p - own_y_p;
				double d_z = itr_z_p - own_z_p;
				double r = Math.sqrt(Math.pow(d_x, 2) + Math.pow(d_y, 2));
				double rdot = (d_x * v_rx + d_y * v_ry) / r;
				if(rdot < 0) {
					for(int alertindex = 0; alertindex < this.alert_level_phase1.length; alertindex++) {
						int lv = this.alert_level_phase1[alertindex];
						double tmod_star = (double) this.tmod_star_phase1[alertindex];
						double hmd_star = this.hmd_star_phase1[alertindex].get(Units.ft);
						double h_star = this.h_star_phase1[alertindex].get(Units.ft);
						int timeframe = this.toa_phase1[alertindex];
						if(maxlv < lv && i <= timeframe) {
							if(-h_star <= d_z && d_z <= h_star) {
								double tmod = Double.MAX_VALUE;
								if(rdot < 0) {
									if(r > hmd_star) {
										tmod = (Math.pow(hmd_star, 2) - Math.pow(r, 2)) / (d_x * v_rx + d_y * v_ry);
									}else {
										tmod = 0.0;
									}
								}
								if(0.0 <= tmod && tmod <= tmod_star) {
									double t_CPA = -(d_x * v_rx + d_y * v_ry) / (Math.pow(v_rx, 2) + Math.pow(v_ry, 2));
									double hmd = 0;
									if(t_CPA >= 0) {
										hmd = Math.sqrt(Math.pow(d_x + v_rx * t_CPA, 2) + Math.pow(d_y + v_ry * t_CPA, 2));
										if(hmd <= hmd_star) {
											maxlv = lv;
										}
									}
								}
							}
						}
					}
				}
			}
		}
		double d_x = itr_x - own_x;
		double d_y = itr_y - own_y;
		double d_z = itr_z - own_z;
		double r = Math.sqrt(Math.pow(d_x, 2) + Math.pow(d_y, 2));
		int lodwcindex = this.alert_level_phase1.length - 1;
		int lv = this.alert_level_phase1[lodwcindex];
		double tmod_star = (double) this.tmod_star_phase1[lodwcindex];
		double hmd_star = this.hmd_star_phase1[lodwcindex].get(Units.ft);
		double h_star = this.h_star_phase1[lodwcindex].get(Units.ft);
		if(-h_star <= d_z && d_z <= h_star) {
			double tmod = r <= hmd_star ? 0 :Double.MAX_VALUE;
			if(0.0 <= tmod && tmod <= tmod_star) {
				double t_CPA = -(d_x * v_rx + d_y * v_ry) / (Math.pow(v_rx, 2) + Math.pow(v_ry, 2));
				double hmd = 0;
				if(t_CPA >= 0) {
					hmd = Math.sqrt(Math.pow(d_x + v_rx * t_CPA, 2) + Math.pow(d_y + v_ry * t_CPA, 2));
					if(hmd <= hmd_star) {
						maxlv = lv;
					}
				}
			}
		}
		if(maxlv > 0) {
			return this.alert_type_phase1[maxlv-1];
		}else {
			return "-";
		}
	}

	public String calculationInPhase2(Position own_pos, Position itr_pos, Speed own_gs, Speed itr_gs, Speed own_vr, Speed itr_vr, Angle own_crs, Angle itr_crs) {

		int maxlv = -1;
		// Current status (Units: feet, degree, second)
		double own_x = own_pos.getX().get(Units.ft);
		double own_y = own_pos.getY().get(Units.ft);
		double own_z = own_pos.getZ().get(Units.ft);
		double own_vx = (own_gs.get(Units.fpm) / 60.0) * own_crs.sine();
		double own_vy = (own_gs.get(Units.fpm) / 60.0) * own_crs.cosine();
		double own_vz = (own_vr.get(Units.fpm) / 60.0);
		double itr_x = itr_pos.getX().get(Units.ft);
		double itr_y = itr_pos.getY().get(Units.ft);
		double itr_z = itr_pos.getZ().get(Units.ft);
		double itr_vx = (itr_gs.get(Units.fpm) / 60.0) * itr_crs.sine();
		double itr_vy = (itr_gs.get(Units.fpm) / 60.0) * itr_crs.cosine();
		double itr_vz = (itr_vr.get(Units.fpm) / 60.0);
		double v_rx = itr_vx - own_vx;
		double v_ry = itr_vy - own_vy;
		double rdot_now = ((itr_x - own_x) * v_rx + (itr_y - own_y) * v_ry) / Math.sqrt(Math.pow((itr_x - own_x), 2) + Math.pow((itr_y - own_y), 2));
		if(rdot_now < 0) {
			int caltimerange = 0;
			for(int toa : this.toa_phase2) {
				if(caltimerange < toa) {
					caltimerange = toa;
				}
			}
			for(int i = 0; i < caltimerange; i++) {
				// Estimated Position (Units: feet, degree, second)
				double own_x_p = own_x + (own_vx * (double) i);
				double own_y_p = own_y + (own_vy * (double) i);
				double own_z_p = own_z + (own_vz * (double) i);
				double itr_x_p = itr_x + (itr_vx * (double) i);
				double itr_y_p = itr_y + (itr_vy * (double) i);
				double itr_z_p = itr_z + (itr_vz * (double) i);
				double d_x = itr_x_p - own_x_p;
				double d_y = itr_y_p - own_y_p;
				double d_z = itr_z_p - own_z_p;
				double r = Math.sqrt(Math.pow(d_x, 2) + Math.pow(d_y, 2));
				double rdot = (d_x * v_rx + d_y * v_ry) / r;
				if(rdot < 0) {
					for(int alertindex = 0; alertindex < this.alert_level_phase2.length; alertindex++) {
						int lv = this.alert_level_phase2[alertindex];
						double tmod_star = (double) this.tmod_star_phase2[alertindex];
						double hmd_star = this.hmd_star_phase2[alertindex].get(Units.ft);
						double h_star = this.h_star_phase2[alertindex].get(Units.ft);
						int timeframe = this.toa_phase2[alertindex];
						if(maxlv < lv && i <= timeframe) {
							if(-h_star <= d_z && d_z <= h_star) {
								double tmod = Double.MAX_VALUE;
								if(rdot < 0) {
									if(r > hmd_star) {
										tmod = (Math.pow(hmd_star, 2) - Math.pow(r, 2)) / (d_x * v_rx + d_y * v_ry);
									}else {
										tmod = 0.0;
									}
								}
								if(0.0 <= tmod && tmod <= tmod_star) {

									double t_CPA = -(d_x * v_rx + d_y * v_ry) / (Math.pow(v_rx, 2) + Math.pow(v_ry, 2));
									double hmd = 0;
									if(t_CPA >= 0) {

										hmd = Math.sqrt(Math.pow(d_x + v_rx * t_CPA, 2) + Math.pow(d_y + v_ry * t_CPA, 2));
										if(hmd <= hmd_star) {
											maxlv = lv;
										}
									}
								}
							}
						}
					}
				}
			}
		}else {
			double d_x = itr_x - own_x;
			double d_y = itr_y - own_y;
			double d_z = itr_z - own_z;
			double r = Math.sqrt(Math.pow(d_x, 2) + Math.pow(d_y, 2));
			int lodwcindex = this.alert_level_phase2.length - 1;
			int lv = this.alert_level_phase2[lodwcindex];
			double tmod_star = (double) this.tmod_star_phase2[lodwcindex];
			double hmd_star = this.hmd_star_phase2[lodwcindex].get(Units.ft);
			double h_star = this.h_star_phase2[lodwcindex].get(Units.ft);
			if(-h_star <= d_z && d_z <= h_star) {
				double tmod = r <= hmd_star ? 0 :Double.MAX_VALUE;
				if(0.0 <= tmod && tmod <= tmod_star) {
					double t_CPA = -(d_x * v_rx + d_y * v_ry) / (Math.pow(v_rx, 2) + Math.pow(v_ry, 2));
					double hmd = 0;
					if(t_CPA >= 0) {
						hmd = Math.sqrt(Math.pow(d_x + v_rx * t_CPA, 2) + Math.pow(d_y + v_ry * t_CPA, 2));
						if(hmd <= hmd_star) {
							maxlv = lv;
						}
					}
				}
			}
		}
		if(maxlv > 0) {
			return this.alert_type_phase2[maxlv-3];
		}else {
			return "-";
		}
	}
}

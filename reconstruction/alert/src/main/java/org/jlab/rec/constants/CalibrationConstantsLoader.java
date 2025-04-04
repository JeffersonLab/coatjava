package org.jlab.rec.alert.constants;

import org.jlab.detector.calib.utils.ConstantsManager;
import org.jlab.detector.calib.utils.DatabaseConstantProvider;
import org.jlab.utils.groups.IndexedTable;

import java.util.HashMap;
import java.util.Map;
import java.lang.Integer;
import java.lang.Double;

/**
 * This class loads constants from CCDB
 * 
 * inspired by the one of the BAND subsystem
 * 
 * @author ftouchte
 */

public class CalibrationConstantsLoader {

	public CalibrationConstantsLoader() {
		// TODO Auto-generated constructor stub
	}
	public static boolean CSTLOADED = false;

	// Maps for constants from database
	// AHDC	
	public static Map<Integer, double[]> AHDC_TIME_OFFSETS           = new HashMap<Integer,double[]>(); 	///< AHDC Parameters for timing offsets
	public static Map<Integer, double[]> AHDC_TIME_TO_DISTANCE       = new HashMap<Integer,double[]>();	///< AHDC Parameters for time to distance
	
	// ATOF
	public static Map<Integer, double[]> ATOF_EFFECTIVE_VELOCITY     = new HashMap<Integer,double[]>(); 	///< ATOF Parameters for effective velocity
	public static Map<Integer, double[]> ATOF_TIME_WALK              = new HashMap<Integer,double[]>(); 	///< ATOF Parameters for time walk
	public static Map<Integer, double[]> ATOF_ATTENUATION_LENGTH     = new HashMap<Integer,double[]>(); 	///< ATOF Parameters for attenuation lenght
	public static Map<Integer, double[]> ATOF_TIME_OFFSETS         	 = new HashMap<Integer,double[]>(); 	///< ATOF Parameters for timing offsets

	public static synchronized void Load(int runno, String var, ConstantsManager manager) {

		//System.out.println("*Loading calibration constants*");
		manager.setVariation(var);

		IndexedTable  ahdc_timeOffsets    	 = manager.getConstants(runno, "/calibration/alert/ahdc/time_offsets");
		IndexedTable  ahdc_time2distance  	 = manager.getConstants(runno, "/calibration/alert/ahdc/time_to_distance");
		IndexedTable  atof_effectiveVelocity     = manager.getConstants(runno, "/calibration/alert/atof/effective_velocity");
		IndexedTable  atof_timeWalk   		 = manager.getConstants(runno, "/calibration/alert/atof/time_walk");
		IndexedTable  atof_attenuationLength     = manager.getConstants(runno, "/calibration/alert/atof/attenuation");
		IndexedTable  atof_timeOffsets           = manager.getConstants(runno, "/calibration/alert/atof/time_offsets");


		////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		// AHDC time offsets
		for( int i = 0; i < ahdc_timeOffsets.getRowCount(); i++){
			int sector      = Integer.parseInt((String)ahdc_timeOffsets.getValueAt(i, 0));
                        int layer       = Integer.parseInt((String)ahdc_timeOffsets.getValueAt(i, 1));
                        int component   = Integer.parseInt((String)ahdc_timeOffsets.getValueAt(i, 2));
			double t0       = ahdc_timeOffsets.getDoubleValue("t0",      sector, layer, component); 
			double dt0      = ahdc_timeOffsets.getDoubleValue("dt0",     sector, layer, component); 
			double extra1   = ahdc_timeOffsets.getDoubleValue("extra1",  sector, layer, component); 
			double extra2   = ahdc_timeOffsets.getDoubleValue("extra2",  sector, layer, component); 
			double chi2ndf  = ahdc_timeOffsets.getDoubleValue("chi2ndf", sector, layer, component); 
			// Put in map
			int key 	= sector*10000 + layer*100 + component;
			double params[] = {t0, dt0, extra1, extra2, chi2ndf};
			AHDC_TIME_OFFSETS.put(Integer.valueOf(key), params);
		}

		////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		// AHDC time to distance
		// Caution, this table has only one row 
		// the corresponding identifers are (sector, layer, component) == (1,1,1)
		// but it applies for all AHDC wires !
		for( int i = 0; i < ahdc_time2distance.getRowCount(); i++){
			int sector      = Integer.parseInt((String)ahdc_time2distance.getValueAt(i, 0));
                        int layer       = Integer.parseInt((String)ahdc_time2distance.getValueAt(i, 1));
                        int component   = Integer.parseInt((String)ahdc_time2distance.getValueAt(i, 2));
			double p0       = ahdc_time2distance.getDoubleValue("p0",      sector, layer, component); 
			double p1       = ahdc_time2distance.getDoubleValue("p1",      sector, layer, component); 
			double p2       = ahdc_time2distance.getDoubleValue("p2",      sector, layer, component); 
			double p3       = ahdc_time2distance.getDoubleValue("p3",      sector, layer, component); 
			double p4       = ahdc_time2distance.getDoubleValue("p4",      sector, layer, component); 
			double p5       = ahdc_time2distance.getDoubleValue("p5",      sector, layer, component); 
			double dp0      = ahdc_time2distance.getDoubleValue("dp0",      sector, layer, component); 
			double dp1      = ahdc_time2distance.getDoubleValue("dp1",     sector, layer, component); 
			double dp2      = ahdc_time2distance.getDoubleValue("dp2",     sector, layer, component); 
			double dp3      = ahdc_time2distance.getDoubleValue("dp3",     sector, layer, component); 
			double dp4      = ahdc_time2distance.getDoubleValue("dp4",     sector, layer, component); 
			double dp5      = ahdc_time2distance.getDoubleValue("dp5",     sector, layer, component); 
			double chi2ndf  = ahdc_time2distance.getDoubleValue("chi2ndf", sector, layer, component); 
			// Put in map
			int key 	= sector*10000 + layer*100 + component;
			double params[] = {p0, p1, p2, p3, p4, p5, dp0, dp1, dp2, dp3, dp4, dp5, chi2ndf};
			AHDC_TIME_TO_DISTANCE.put(Integer.valueOf(key), params);
		}
		
		////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		// ATOF effective velocity
		for( int i = 0; i < atof_effectiveVelocity.getRowCount(); i++){
			int sector      = Integer.parseInt((String)atof_effectiveVelocity.getValueAt(i, 0));
                        int layer       = Integer.parseInt((String)atof_effectiveVelocity.getValueAt(i, 1));
                        int component   = Integer.parseInt((String)atof_effectiveVelocity.getValueAt(i, 2));
			double veff     = atof_effectiveVelocity.getDoubleValue("veff",   sector, layer, component); 
			double dveff    = atof_effectiveVelocity.getDoubleValue("dveff",  sector, layer, component); 
			double extra1   = atof_effectiveVelocity.getDoubleValue("extra1", sector, layer, component); 
			double extra2   = atof_effectiveVelocity.getDoubleValue("extra2", sector, layer, component); 
			// Put in map
			int key 	= sector*10000 + layer*100 + component;
			double params[] = {veff, dveff, extra1, extra2};
			ATOF_EFFECTIVE_VELOCITY.put(Integer.valueOf(key), params);
		}

		////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		// ATOF time walk
		for( int i = 0; i < atof_timeWalk.getRowCount(); i++){
			int sector      = Integer.parseInt((String)atof_timeWalk.getValueAt(i, 0));
                        int layer       = Integer.parseInt((String)atof_timeWalk.getValueAt(i, 1));
                        int component   = Integer.parseInt((String)atof_timeWalk.getValueAt(i, 2));
			double tw0      = atof_timeWalk.getDoubleValue("tw0",     sector, layer, component); 
			double tw1      = atof_timeWalk.getDoubleValue("tw1",     sector, layer, component); 
			double tw2      = atof_timeWalk.getDoubleValue("tw2",     sector, layer, component); 
			double tw3      = atof_timeWalk.getDoubleValue("tw3",     sector, layer, component); 
			double dtw0     = atof_timeWalk.getDoubleValue("dtw0",    sector, layer, component); 
			double dtw1     = atof_timeWalk.getDoubleValue("dtw1",    sector, layer, component); 
			double dtw2     = atof_timeWalk.getDoubleValue("dtw2",    sector, layer, component); 
			double dtw3     = atof_timeWalk.getDoubleValue("dtw3",    sector, layer, component); 
			double chi2ndf  = atof_timeWalk.getDoubleValue("chi2ndf", sector, layer, component); 
			// Put in map
			int key 	= sector*10000 + layer*100 + component;
			double params[] = {tw0, tw1, tw2, tw3, dtw0, dtw1, dtw2, dtw3, chi2ndf};
			ATOF_TIME_WALK.put(Integer.valueOf(key), params);
		}

		////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		// ATOF attenuation length
		for( int i = 0; i < atof_attenuationLength.getRowCount(); i++){
			int sector      = Integer.parseInt((String)atof_attenuationLength.getValueAt(i, 0));
                        int layer       = Integer.parseInt((String)atof_attenuationLength.getValueAt(i, 1));
                        int component   = Integer.parseInt((String)atof_attenuationLength.getValueAt(i, 2));
			double attlen   = atof_attenuationLength.getDoubleValue("attlen",  sector, layer, component); 
			double dattlen  = atof_attenuationLength.getDoubleValue("dattlen", sector, layer, component); 
			double extra1   = atof_attenuationLength.getDoubleValue("extra1",  sector, layer, component); 
			double extra2   = atof_attenuationLength.getDoubleValue("extra2",  sector, layer, component); 
			// Put in map
			int key 	= sector*10000 + layer*100 + component;
			double params[] = {attlen, dattlen, extra1, extra2};
			ATOF_ATTENUATION_LENGTH.put(Integer.valueOf(key), params);
		}

		////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		// ATOF time offsets
		for( int i = 0; i < atof_timeOffsets.getRowCount(); i++){
			int sector      	   = Integer.parseInt((String)atof_timeOffsets.getValueAt(i, 0));
                        int layer       	   = Integer.parseInt((String)atof_timeOffsets.getValueAt(i, 1));
                        int component  		   = Integer.parseInt((String)atof_timeOffsets.getValueAt(i, 2));
			double t0       	   = atof_timeOffsets.getDoubleValue("t0",      	    sector, layer, component); 
			double upstream_downstream = atof_timeOffsets.getDoubleValue("upstream_downstream", sector, layer, component); 
			double wedge_bar    	   = atof_timeOffsets.getDoubleValue("wedge_bar",	    sector, layer, component); 
			double extra1   	   = atof_timeOffsets.getDoubleValue("extra1", 		    sector, layer, component); 
			double extra2              = atof_timeOffsets.getDoubleValue("extra2", 		    sector, layer, component); 
			// Put in map
			int key 	= sector*10000 + layer*100 + component;
			double params[] = {t0, upstream_downstream, wedge_bar, extra1, extra2};
			ATOF_TIME_OFFSETS.put(Integer.valueOf(key), params);
		}


		CSTLOADED = true;

	} 


	private static DatabaseConstantProvider DB;

	public static final DatabaseConstantProvider getDB() {
		return DB;
	}


	public static final void setDB(DatabaseConstantProvider dB) {
		DB = dB;
	}

	public static void main (String arg[]) {
		//	CalibrationConstantsLoader.Load(10,"default");
	}
}

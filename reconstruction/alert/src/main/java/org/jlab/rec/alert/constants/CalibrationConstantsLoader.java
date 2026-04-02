package org.jlab.rec.alert.constants;

import org.jlab.detector.calib.utils.ConstantsManager;
import org.jlab.detector.calib.utils.DatabaseConstantProvider;
import org.jlab.utils.groups.IndexedTable;

import java.util.HashMap;
import java.util.Map;

/**
 * This class loads constants from CCDB
 *
 * inspired by the one of the BAND subsystem
 *
 * @author ftouchte
 */
public class CalibrationConstantsLoader {

    public CalibrationConstantsLoader() {
        // default ctor
    }

    public static boolean CSTLOADED = false;

    // Maps for constants from database
    // AHDC
    public static Map<Integer, double[]> AHDC_TIME_OFFSETS           = new HashMap<>(); ///< {t0,dt0,extra1,extra2,chi2ndf}
	public static Map<Integer, double[]> AHDC_TIME_TO_DISTANCE_WIRE  = new HashMap<>(); ///< T2D function for every wire
    public static Map<Integer, double[]> AHDC_RAW_HIT_CUTS           = new HashMap<>(); ///< {t_min,t_max,tot_min,tot_max,adc_min,adc_max,ped_min,ped_max}
                                                                     
    // UPDATED SCHEMA: keys (sector,layer,component), columns: gainCorr,dgainCorr,extra1,extra2,extra3
    public static Map<Integer, double[]> AHDC_ADC_GAINS              = new HashMap<>(); ///< {gainCorr, dgainCorr, extra1, extra2, extra3}
                                                                     
    // NEW ToT TABLE: keys (sector,layer,component), columns: totCorr,dtotCorr,extra1,extra2,extra3
    public static Map<Integer, double[]> AHDC_TIME_OVER_THRESHOLD    = new HashMap<>(); ///< {totCorr, dtotCorr, extra1, extra2, extra3}
                                                                     
    // ATOF                                                          
    public static Map<Integer, double[]> ATOF_EFFECTIVE_VELOCITY     = new HashMap<>(); ///< {veff,dveff,extra1,extra2}
    public static Map<Integer, double[]> ATOF_TIME_WALK              = new HashMap<>(); ///< {tw0..tw3, dtw0..dtw3, chi2ndf}
    public static Map<Integer, double[]> ATOF_ATTENUATION_LENGTH     = new HashMap<>(); ///< {attlen,dattlen,extra1,extra2}
    public static Map<Integer, double[]> ATOF_TIME_OFFSETS           = new HashMap<>(); ///< {t0,upstream_downstream,wedge_bar,extra1,extra2}

    public static synchronized void Load(int runno, ConstantsManager manager) {

        if (CSTLOADED) return;

        IndexedTable ahdc_timeOffsets        = manager.getConstants(runno, "/calibration/alert/ahdc/time_offsets");
		IndexedTable ahdc_time2distanceWire  = manager.getConstants(runno, "/calibration/alert/ahdc/time_to_distance_wire");
        IndexedTable ahdc_rawHitCuts         = manager.getConstants(runno, "/calibration/alert/ahdc/raw_hit_cuts");

        // Gains table (UPDATED SCHEMA)
        IndexedTable ahdc_adcGains           = manager.getConstants(runno, "/calibration/alert/ahdc/gains");

        // NEW ToT table
        IndexedTable ahdc_timeOverThreshold  = manager.getConstants(runno, "/calibration/alert/ahdc/time_over_threshold");

        IndexedTable atof_effectiveVelocity  = manager.getConstants(runno, "/calibration/alert/atof/effective_velocity");
        IndexedTable atof_timeWalk           = manager.getConstants(runno, "/calibration/alert/atof/time_walk");
        IndexedTable atof_attenuationLength  = manager.getConstants(runno, "/calibration/alert/atof/attenuation");
        IndexedTable atof_timeOffsets        = manager.getConstants(runno, "/calibration/alert/atof/time_offsets");

        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // AHDC time offsets
        for (int i = 0; i < ahdc_timeOffsets.getRowCount(); i++) {
            int sector    = Integer.parseInt((String) ahdc_timeOffsets.getValueAt(i, 0));
            int layer     = Integer.parseInt((String) ahdc_timeOffsets.getValueAt(i, 1));
            int component = Integer.parseInt((String) ahdc_timeOffsets.getValueAt(i, 2));

            double t0      = ahdc_timeOffsets.getDoubleValue("t0",      sector, layer, component);
            double dt0     = ahdc_timeOffsets.getDoubleValue("dt0",     sector, layer, component);
            double extra1  = ahdc_timeOffsets.getDoubleValue("extra1",  sector, layer, component);
            double extra2  = ahdc_timeOffsets.getDoubleValue("extra2",  sector, layer, component);
            double chi2ndf = ahdc_timeOffsets.getDoubleValue("chi2ndf", sector, layer, component);

            int key = sector * 10000 + layer * 100 + component;
            double params[] = { t0, dt0, extra1, extra2, chi2ndf };
            AHDC_TIME_OFFSETS.put(Integer.valueOf(key), params);
        }
		
        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // AHDC time to distance per wire
		// See implementation and functional form in reconstruction/alert/src/main/java/org/jlab/rec/ahdc/Hit/HitReader.java
		if(ahdc_time2distanceWire != null){
			for (int i = 0; i < ahdc_time2distanceWire.getRowCount(); i++) {
				int sector    = Integer.parseInt((String) ahdc_time2distanceWire.getValueAt(i, 0));
				int layer     = Integer.parseInt((String) ahdc_time2distanceWire.getValueAt(i, 1));
				int component = Integer.parseInt((String) ahdc_time2distanceWire.getValueAt(i, 2));

				double p1_int   = ahdc_time2distanceWire.getDoubleValue("p1_int",   sector, layer, component);
				double p1_slope = ahdc_time2distanceWire.getDoubleValue("p1_slope", sector, layer, component);
				double p2_int   = ahdc_time2distanceWire.getDoubleValue("p2_int",   sector, layer, component);
				double p2_slope = ahdc_time2distanceWire.getDoubleValue("p2_slope", sector, layer, component);
				double p3_int   = ahdc_time2distanceWire.getDoubleValue("p3_int",   sector, layer, component);
				double p3_slope = ahdc_time2distanceWire.getDoubleValue("p3_slope", sector, layer, component);
				double t1_x0    = ahdc_time2distanceWire.getDoubleValue("t1_x0",    sector, layer, component);
				double t1_width = ahdc_time2distanceWire.getDoubleValue("t1_width", sector, layer, component);
				double t2_x0    = ahdc_time2distanceWire.getDoubleValue("t2_x0",    sector, layer, component);
				double t2_width	= ahdc_time2distanceWire.getDoubleValue("t2_width", sector, layer, component);
				double z0     	= ahdc_time2distanceWire.getDoubleValue("z0",     	sector, layer, component);
				double z1     	= ahdc_time2distanceWire.getDoubleValue("z1",     	sector, layer, component);
				double z2     	= ahdc_time2distanceWire.getDoubleValue("z2",     	sector, layer, component);
				double extra1   = ahdc_time2distanceWire.getDoubleValue("extra1",   sector, layer, component);
				double extra2   = ahdc_time2distanceWire.getDoubleValue("extra2",   sector, layer, component);
				double chi2ndf  = ahdc_time2distanceWire.getDoubleValue("chi2ndf", 	sector, layer, component);

				int key = sector * 10000 + layer * 100 + component;
				double params[] = { p1_int, p1_slope, p2_int, p2_slope, p3_int, p3_slope, t1_x0, t1_width, t2_x0, t2_width, z0, z1, z2, extra1, extra2, chi2ndf };
				AHDC_TIME_TO_DISTANCE_WIRE.put(Integer.valueOf(key), params);
			}
		}
		

        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // AHDC raw hit cuts
        for (int i = 0; i < ahdc_rawHitCuts.getRowCount(); i++) {
            int sector    = Integer.parseInt((String) ahdc_rawHitCuts.getValueAt(i, 0));
            int layer     = Integer.parseInt((String) ahdc_rawHitCuts.getValueAt(i, 1));
            int component = Integer.parseInt((String) ahdc_rawHitCuts.getValueAt(i, 2));

            double t_min   = ahdc_rawHitCuts.getDoubleValue("t_min",   sector, layer, component);
            double t_max   = ahdc_rawHitCuts.getDoubleValue("t_max",   sector, layer, component);
            double tot_min = ahdc_rawHitCuts.getDoubleValue("tot_min", sector, layer, component);
            double tot_max = ahdc_rawHitCuts.getDoubleValue("tot_max", sector, layer, component);
            double adc_min = ahdc_rawHitCuts.getDoubleValue("adc_min", sector, layer, component);
            double adc_max = ahdc_rawHitCuts.getDoubleValue("adc_max", sector, layer, component);
            double ped_min = ahdc_rawHitCuts.getDoubleValue("ped_min", sector, layer, component);
            double ped_max = ahdc_rawHitCuts.getDoubleValue("ped_max", sector, layer, component);

            int key = sector * 10000 + layer * 100 + component;
            double params[] = { t_min, t_max, tot_min, tot_max, adc_min, adc_max, ped_min, ped_max };
            AHDC_RAW_HIT_CUTS.put(Integer.valueOf(key), params);
        }

	
      // AHDC ADC gains table
     // keys   : sector, layer, component
    // cols   : gainCorr, dgainCorr, extra1, extra2, extra3

      if (ahdc_adcGains != null) {
    for (int i = 0; i < ahdc_adcGains.getRowCount(); i++) {

        int sector    = Integer.parseInt((String) ahdc_adcGains.getValueAt(i, 0));
        int layer     = Integer.parseInt((String) ahdc_adcGains.getValueAt(i, 1));   
        int component = Integer.parseInt((String) ahdc_adcGains.getValueAt(i, 2));

        double gainCorr  = ahdc_adcGains.getDoubleValue("gainCorr",  sector, layer, component);
        double dgainCorr = ahdc_adcGains.getDoubleValue("dgainCorr", sector, layer, component);

        double extra1 = 0.0, extra2 = 0.0, extra3 = 0.0;
        try { extra1 = ahdc_adcGains.getDoubleValue("extra1", sector, layer, component); } catch (Exception e) {}
        try { extra2 = ahdc_adcGains.getDoubleValue("extra2", sector, layer, component); } catch (Exception e) {}
        try { extra3 = ahdc_adcGains.getDoubleValue("extra3", sector, layer, component); } catch (Exception e) {}

        int key = sector * 10000 + layer * 100 + component;   
        AHDC_ADC_GAINS.put(key, new double[]{gainCorr, dgainCorr, extra1, extra2, extra3});
    }
}

///////////////////////////////////////////////////////////////////////////////////////////////////////////////
// AHDC time_over_threshold (NEW TABLE)
// keys   : sector, layer, component
// columns: totCorr, dtotCorr, extra1, extra2, extra3

// always clear so behavior is deterministic if Load() is ever called again
AHDC_TIME_OVER_THRESHOLD.clear();

// Table may not exist for some runs/variations -> treat as "no correction"
if (ahdc_timeOverThreshold != null) {

    for (int i = 0; i < ahdc_timeOverThreshold.getRowCount(); i++) {
        int sector    = Integer.parseInt((String) ahdc_timeOverThreshold.getValueAt(i, 0));
        int layer     = Integer.parseInt((String) ahdc_timeOverThreshold.getValueAt(i, 1));
        int component = Integer.parseInt((String) ahdc_timeOverThreshold.getValueAt(i, 2));

        double totCorr  = ahdc_timeOverThreshold.getDoubleValue("totCorr",  sector, layer, component);
        double dtotCorr = ahdc_timeOverThreshold.getDoubleValue("dtotCorr", sector, layer, component);

        double extra1 = 0.0, extra2 = 0.0, extra3 = 0.0;
        try { extra1 = ahdc_timeOverThreshold.getDoubleValue("extra1", sector, layer, component); } catch (Exception e) {}
        try { extra2 = ahdc_timeOverThreshold.getDoubleValue("extra2", sector, layer, component); } catch (Exception e) {}
        try { extra3 = ahdc_timeOverThreshold.getDoubleValue("extra3", sector, layer, component); } catch (Exception e) {}

        int key = sector * 10000 + layer * 100 + component;
        double[] params = { totCorr, dtotCorr, extra1, extra2, extra3 };
        AHDC_TIME_OVER_THRESHOLD.put(Integer.valueOf(key), params);
    }
}







	////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // AHDC time_over_threshold (NEW TABLE)
        // keys   : sector, layer, component
        // columns: totCorr, dtotCorr, extra1, extra2, extra3
//        for (int i = 0; i < ahdc_timeOverThreshold.getRowCount(); i++) {
//          int sector    = Integer.parseInt((String) ahdc_timeOverThreshold.getValueAt(i, 0));
//          int layer     = Integer.parseInt((String) ahdc_timeOverThreshold.getValueAt(i, 1));
//          int component = Integer.parseInt((String) ahdc_timeOverThreshold.getValueAt(i, 2));
//
//          double totCorr  = ahdc_timeOverThreshold.getDoubleValue("totCorr",  sector, layer, component);
//          double dtotCorr = ahdc_timeOverThreshold.getDoubleValue("dtotCorr", sector, layer, component);

//          double extra1 = 0.0, extra2 = 0.0, extra3 = 0.0;
//          try { extra1 = ahdc_timeOverThreshold.getDoubleValue("extra1", sector, layer, component); } catch (Exception e) {}
////           try { extra2 = ahdc_timeOverThreshold.getDoubleValue("extra2", sector, layer, component); } catch (Exception e) {}
//        try { extra3 = ahdc_timeOverThreshold.getDoubleValue("extra3", sector, layer, component); } catch (Exception e) {}

//          int key = sector * 10000 + layer * 100 + component;
//          double params[] = { totCorr, dtotCorr, extra1, extra2, extra3 };
//          AHDC_TIME_OVER_THRESHOLD.put(Integer.valueOf(key), params);
//      }

        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // ATOF effective velocity
        for (int i = 0; i < atof_effectiveVelocity.getRowCount(); i++) {
            int sector    = Integer.parseInt((String) atof_effectiveVelocity.getValueAt(i, 0));
            int layer     = Integer.parseInt((String) atof_effectiveVelocity.getValueAt(i, 1));
            int component = Integer.parseInt((String) atof_effectiveVelocity.getValueAt(i, 2));

            double veff   = atof_effectiveVelocity.getDoubleValue("veff",   sector, layer, component);
            double dveff  = atof_effectiveVelocity.getDoubleValue("dveff",  sector, layer, component);
            double extra1 = atof_effectiveVelocity.getDoubleValue("extra1", sector, layer, component);
            double extra2 = atof_effectiveVelocity.getDoubleValue("extra2", sector, layer, component);

            int key = sector * 10000 + layer * 1000 + component * 10;
            double params[] = { veff, dveff, extra1, extra2 };
            ATOF_EFFECTIVE_VELOCITY.put(Integer.valueOf(key), params);
        }

        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // ATOF time walk
        for (int i = 0; i < atof_timeWalk.getRowCount(); i++) {
            int sector    = Integer.parseInt((String) atof_timeWalk.getValueAt(i, 0));
            int layer     = Integer.parseInt((String) atof_timeWalk.getValueAt(i, 1));
            int component = Integer.parseInt((String) atof_timeWalk.getValueAt(i, 2));
            int order     = Integer.parseInt((String) atof_timeWalk.getValueAt(i, 3));

            double tw0     = atof_timeWalk.getDoubleValue("tw0",     sector, layer, component, order);
            double tw1     = atof_timeWalk.getDoubleValue("tw1",     sector, layer, component, order);
            double tw2     = atof_timeWalk.getDoubleValue("tw2",     sector, layer, component, order);
            double tw3     = atof_timeWalk.getDoubleValue("tw3",     sector, layer, component, order);
            double dtw0    = atof_timeWalk.getDoubleValue("dtw0",    sector, layer, component, order);
            double dtw1    = atof_timeWalk.getDoubleValue("dtw1",    sector, layer, component, order);
            double dtw2    = atof_timeWalk.getDoubleValue("dtw2",    sector, layer, component, order);
            double dtw3    = atof_timeWalk.getDoubleValue("dtw3",    sector, layer, component, order);
            double chi2ndf = atof_timeWalk.getDoubleValue("chi2ndf", sector, layer, component, order);

            int key = sector * 10000 + layer * 1000 + component * 10 + order;
            double params[] = { tw0, tw1, tw2, tw3, dtw0, dtw1, dtw2, dtw3, chi2ndf };
            ATOF_TIME_WALK.put(Integer.valueOf(key), params);
        }

        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // ATOF attenuation length
        for (int i = 0; i < atof_attenuationLength.getRowCount(); i++) {
            int sector    = Integer.parseInt((String) atof_attenuationLength.getValueAt(i, 0));
            int layer     = Integer.parseInt((String) atof_attenuationLength.getValueAt(i, 1));
            int component = Integer.parseInt((String) atof_attenuationLength.getValueAt(i, 2));

            double attlen  = atof_attenuationLength.getDoubleValue("attlen",  sector, layer, component);
            double dattlen = atof_attenuationLength.getDoubleValue("dattlen", sector, layer, component);
            double extra1  = atof_attenuationLength.getDoubleValue("extra1",  sector, layer, component);
            double extra2  = atof_attenuationLength.getDoubleValue("extra2",  sector, layer, component);

            int key = sector * 10000 + layer * 1000 + component * 10;
            double params[] = { attlen, dattlen, extra1, extra2 };
            ATOF_ATTENUATION_LENGTH.put(Integer.valueOf(key), params);
        }

        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // ATOF time offsets
        for (int i = 0; i < atof_timeOffsets.getRowCount(); i++) {
            int sector    = Integer.parseInt((String) atof_timeOffsets.getValueAt(i, 0));
            int layer     = Integer.parseInt((String) atof_timeOffsets.getValueAt(i, 1));
            int component = Integer.parseInt((String) atof_timeOffsets.getValueAt(i, 2));
            int order     = Integer.parseInt((String) atof_timeOffsets.getValueAt(i, 3)); // we have to use it here !

            double t0                  = atof_timeOffsets.getDoubleValue("t0",                  sector, layer, component, order);
            double upstream_downstream = atof_timeOffsets.getDoubleValue("upstream_downstream", sector, layer, component, order);
            double wedge_bar           = atof_timeOffsets.getDoubleValue("wedge_bar",           sector, layer, component, order);
            double extra1              = atof_timeOffsets.getDoubleValue("extra1",              sector, layer, component, order);
            double extra2              = atof_timeOffsets.getDoubleValue("extra2",              sector, layer, component, order);

            int key = sector * 10000 + layer * 1000 + component * 10 + order;
            double params[] = { t0, upstream_downstream, wedge_bar, extra1, extra2 };
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

    public static void main(String arg[]) {
    }
}

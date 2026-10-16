package org.jlab.rec.dc.timetodistance;

import org.jlab.rec.dc.Constants;
import static org.jlab.rec.dc.timetodistance.T2DFunctions.polyFcnMac;
import org.jlab.utils.groups.IndexedTable;


public class TableLoader {

    public TableLoader() {
    }
    
    private static boolean T2DLOADED = false;
    
    public static final int NBINST=2000;
    public static final double[] BfieldValues = new double[]{0.0000, 1.0000, 1.4142, 1.7321, 2.0000, 2.2361, 2.4495, 2.6458};
    public static int minBinIdxB = 0;
    public static int maxBinIdxB = BfieldValues.length-1;
    public static int minBinIdxAlpha = 0;
    public static int maxBinIdxAlpha = 5;
    private static final double[] AlphaMid = new double[6];
    private static final double[][] AlphaBounds = new double[6][2];
    public static int minBinIdxT  = 0;
    public static final int[][][][] maxBinIdxT  = new int[6][6][8][6];
    public static double[][][][][] DISTFROMTIME = new double[6][6][maxBinIdxB+1][maxBinIdxAlpha+1][NBINST]; // sector slyr alpha Bfield time bins [s][r][ibfield][icosalpha][tbin]    
    public static int maxTBin = -1;
    
        
    private static int getAlphaBin(double Alpha) {
        int bin = 0;
        for(int b =0; b<6; b++) {
            if(Alpha>=AlphaBounds[b][0] && Alpha<=AlphaBounds[b][1] )
                bin = b;
        }
        return bin;
    }
    
    private static void FillAlpha() {
        final double cos30 = Math.cos(Math.toRadians(30.));
        for(int icosalpha =0; icosalpha<maxBinIdxAlpha+1; icosalpha++) {
            double cos30minusalphaM = cos30 + (double) (icosalpha)*(1. - cos30)/5.;
            double alphaM = -(Math.toDegrees(Math.acos(cos30minusalphaM)) - 30);
            AlphaMid[icosalpha]= alphaM;
            double cos30minusalphaU = cos30 + (double) (icosalpha+0.5)*(1. - cos30)/5.;
            double alphaU = -(Math.toDegrees(Math.acos(cos30minusalphaU)) - 30);
            AlphaBounds[icosalpha][1] = alphaU;
            double cos30minusalphaL = cos30 + (double) (icosalpha-0.5)*(1. - cos30)/5.;
            double alphaL = -(Math.toDegrees(Math.acos(cos30minusalphaL)) - 30);
            AlphaBounds[icosalpha][0] = alphaL;
        }
        AlphaMid[0] = 0;
        AlphaMid[5] = 30;
        AlphaBounds[0][0] = 0;
        AlphaBounds[5][1] = 30;
    }

    public static boolean useP = true;

    private static void getConstants(IndexedTable t2dPressure, IndexedTable t2dPressRef, IndexedTable pressure){
        double p_ref = t2dPressRef.getDoubleValue("pressure", 0,0,0);
        double p = pressure.getDoubleValue("value", 0,0,3);
        double dp = p - p_ref;
        double dp2scale = 0;
        double dpscale = 1;
        
        if(!useP) 
            dpscale = 0;
        
        for(int s = 0; s<6; s++ ){ // loop over sectors
            for(int r = 0; r<6; r++ ){ //loop over slys

                long hash = IndexedTable.DEFAULT_GENERATOR.hashCode(s+1,r+1,0);
                
                // Fill constants
                FracDmaxAtMinVel[s][r] = t2dPressure.getDoubleValueByHash("c1_a0", hash)
                        +t2dPressure.getDoubleValueByHash("c1_a1", hash)*dp*dpscale;
                v0[s][r] = t2dPressure.getDoubleValueByHash("v0_a0", hash)
                        +t2dPressure.getDoubleValueByHash("v0_a1", hash)*dp*dpscale
                        +t2dPressure.getDoubleValueByHash("v0_a2", hash)*dp*dp*dp2scale;
                vmid[s][r] = t2dPressure.getDoubleValueByHash("vmid_a0", hash)
                        +t2dPressure.getDoubleValueByHash("vmid_a1", hash)*dp*dpscale
                        +t2dPressure.getDoubleValueByHash("vmid_a2", hash)*dp*dp*dp2scale;
                if(r>1 && r<4) {
                    delta_bfield_coefficient[s][r] = t2dPressure.getDoubleValueByHash("delta_bfield_a0", hash)
                            +t2dPressure.getDoubleValueByHash("delta_bfield_a1", hash)*dp*dpscale
                            +t2dPressure.getDoubleValueByHash("delta_bfield_a2", hash)*dp*dp*dp2scale;
                    b1[s][r] = t2dPressure.getDoubleValueByHash("b1_a0", hash)
                            +t2dPressure.getDoubleValueByHash("b1_a1", hash)*dp*dpscale
                            +t2dPressure.getDoubleValueByHash("b1_a2", hash)*dp*dp*dp2scale;
                    b2[s][r] = t2dPressure.getDoubleValueByHash("b2_a0", hash)
                            +t2dPressure.getDoubleValueByHash("b2_a1", hash)*dp*dpscale
                            +t2dPressure.getDoubleValueByHash("b2_a2", hash)*dp*dp*dp2scale;
                    b3[s][r] = t2dPressure.getDoubleValueByHash("b3_a0", hash)
                            +t2dPressure.getDoubleValueByHash("b3_a1", hash)*dp*dpscale
                            +t2dPressure.getDoubleValueByHash("b3_a2", hash)*dp*dp*dp2scale;
                    b4[s][r] = t2dPressure.getDoubleValueByHash("b4_a0", hash)
                            +t2dPressure.getDoubleValueByHash("b4_a1", hash)*dp*dpscale
                            +t2dPressure.getDoubleValueByHash("b4_a2", hash)*dp*dp*dp2scale;
                }
                Tmax[s][r] = t2dPressure.getDoubleValueByHash("tmax_a0", hash)
                        +t2dPressure.getDoubleValueByHash("tmax_a1", hash)*dp*dpscale
                        +t2dPressure.getDoubleValueByHash("tmax_a2", hash)*dp*dp*dp2scale;
            }
        }
    }

    public static void FillTable() {
        double stepSize = 0.00010;
        for(int s = 0; s<6; s++ ){ // loop over sectors
            for(int r = 0; r<6; r++ ){ //loop over slys
                //System.out.println("sector "+(s+1)+" sly "+(r+1)+" v0 "+v0[s][r]+" vmid "+vmid[s][r]+" R "+FracDmaxAtMinVel[s][r]);
                double dmax = 2.*Constants.getInstance().wpdist[r]; 
                //double tmax = CCDBConstants.getTMAXSUPERLAYER()[s][r];
                for(int ibfield =0; ibfield<maxBinIdxB+1; ibfield++) {
                    double bfield = BfieldValues[ibfield];
                        for(int icosalpha =0; icosalpha<maxBinIdxAlpha+1; icosalpha++) {
                            maxBinIdxT[s][r][ibfield][icosalpha] = NBINST; 
                            double cos30minusalpha = Math.cos(Math.toRadians(30.)) + (double) (icosalpha)*(1. - Math.cos(Math.toRadians(30.)))/5.;
                            double alpha = -(Math.toDegrees(Math.acos(cos30minusalpha)) - 30);
                                int nxmax = (int) (dmax*cos30minusalpha/stepSize); 
                                
                            for(int idist =0; idist<nxmax; idist++) {
                                double x = (double)(idist+1)*stepSize;
                                double timebfield = calc_Time( x,  alpha, bfield, s+1, r+1) ;
                                
                                int tbin = (int) Math.floor(timebfield/2);
                                if(tbin<0 || tbin>NBINST-1) {
                                    //System.err.println("Problem with tbin");
                                    continue;
                                } 
                                if(tbin>maxTBin) 
                                    maxTBin = tbin;
                                    //if(tbin>maxBinIdxT[s][r][ibfield][icosalpha]) {
                                    //maxBinIdxT[s][r][ibfield][icosalpha] = NBINST; 
                                    //} //LOGGER.log(Level.FINE, "tbin "+tbin+" tmax "+tmax+ "s "+s+" sl "+r );
                                    if(DISTFROMTIME[s][r][ibfield][icosalpha][tbin]==0) {
                                        // firstbin = bi
                                        // bincount = 0;				    	 
                                        DISTFROMTIME[s][r][ibfield][icosalpha][tbin]=x;
                                    } else {
                                        // test for getting center of the bin (to be validated):
                                        //double prevTime = calc_Time(x-stepSize,  alpha, bfield, s+1, r+1);
                                        //if(x>DISTFROMTIME[s][r][ibfield][icosalpha][tbin]
                                        //        && Math.abs((double)(2.*tbin+1)-timebfield)<=Math.abs((double)(2.*tbin+1)-prevTime)) {
                                        //    DISTFROMTIME[s][r][ibfield][icosalpha][tbin]=x;
                                        //}
                                        // bincount++;
                                        DISTFROMTIME[s][r][ibfield][icosalpha][tbin]+=stepSize;
                                    }
                                
                                    /* if(timebfield>timebfield_max) {
                                        DISTFROMTIME[s][r][ibfield][icosalpha][tbin]=x-stepSize*0.5;
                                        if(DISTFROMTIME[s][r][ibfield][icosalpha][tbin]>dmax)
                                            DISTFROMTIME[s][r][ibfield][icosalpha][tbin] = dmax;                                               
                                    } */
                            }
                        }
                    }
            }
        }
        
        TableLoader.fillMissingTableBins();
    }

    public static synchronized void Fill(IndexedTable t2dPressure, IndexedTable t2dPressRef, IndexedTable pressure) {
        
        //CCDBTables 0 =  "/calibration/dc/signal_generation/doca_resolution";
        //CCDBTables 1 =  "/calibration/dc/time_to_distance/t2d";
        //CCDBTables 2 =  "/calibration/dc/time_corrections/T0_correction";	
        if (T2DLOADED) return;
        System.out.println(" FILLING T2D TABLE ....");
        FillAlpha();
        getConstants(t2dPressure,  t2dPressRef,  pressure);
        FillTable();
        System.out.println(" T2D TABLE FILLED.");
        T2DLOADED = true;
     }
    
    private static synchronized void fillMissingTableBins() {
        
        for(int s = 0; s<6; s++ ){ // loop over sectors

            for(int r = 0; r<6; r++ ){ //loop over slys
                
                for(int ibfield =0; ibfield<maxBinIdxB+1; ibfield++) {
                    
                    for(int icosalpha =0; icosalpha<maxBinIdxAlpha+1; icosalpha++) {
                        
                            for(int tbin = 0; tbin<maxTBin; tbin++) {
                            if(DISTFROMTIME[s][r][ibfield][icosalpha][tbin]!=0 && DISTFROMTIME[s][r][ibfield][icosalpha][tbin+1]==0) {
                                DISTFROMTIME[s][r][ibfield][icosalpha][tbin+1] = DISTFROMTIME[s][r][ibfield][icosalpha][tbin];
                                }
                            }
                        
                        }
                    }
                }
            }
        }
    /**
     * 
     * @param x distance to wire in cm
     * @param alpha local angle in deg
     * @param bfield B field value a x in T
     * @param sector sector  
     * @param superlayer superlayer 
     * @return returns time (ns) when given inputs of distance x (cm), local angle alpha (degrees) and magnitude of bfield (Tesla).  
     */
    public static double calc_Time(double x, double alpha, double bfield, int sector, int superlayer) {
        int s = sector - 1;
        int r = superlayer - 1;
        double dmax = 2.*Constants.getInstance().wpdist[r]; 
        double tmax = Tmax[s][r];
        double delBf = delta_bfield_coefficient[s][r]; 
        double Bb1 = b1[s][r];
        double Bb2 = b2[s][r];
        double Bb3 = b3[s][r];
        double Bb4 = b4[s][r];
        if(x>dmax)
            x=dmax;
       return polyFcnMac(x, alpha, bfield, v0[s][r], vmid[s][r], FracDmaxAtMinVel[s][r], 
                tmax, dmax, delBf, Bb1, Bb2, Bb3, Bb4, superlayer) ;
       
    }    
    
    public static double[][] delta_T0 = new double[6][6];
    public static double[][] delta_bfield_coefficient = new double[6][6];
    public static double[][] deltanm = new double[6][6];
    public static double[][] vmid = new double[6][6];
    public static double[][] v0 = new double[6][6];
    public static double[][] b1 = new double[6][6];
    public static double[][] b2 = new double[6][6];
    public static double[][] b3 = new double[6][6];
    public static double[][] b4 = new double[6][6];
    public static double[][] Tmax = new double[6][6];
    public static double[][] FracDmaxAtMinVel = new double[6][6];	

        }

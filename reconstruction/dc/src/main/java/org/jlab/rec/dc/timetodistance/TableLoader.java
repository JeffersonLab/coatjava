package org.jlab.rec.dc.timetodistance;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.freehep.math.minuit.MnUserParameters;
import org.jlab.detector.base.DetectorType;
import org.jlab.detector.base.GeometryFactory;
import org.jlab.geom.base.ConstantProvider;
import org.jlab.groot.data.H1F;
import org.jlab.groot.data.H2F;
import org.jlab.groot.fitter.ParallelSliceFitter;
import org.jlab.groot.math.Func1D;
import org.jlab.groot.ui.TCanvas;
import org.jlab.rec.dc.Constants;
import static org.jlab.rec.dc.timetodistance.T2DFunctions.polyFcnMac;
import org.jlab.service.dc.DCEngine;
import org.jlab.utils.groups.IndexedTable;


public class TableLoader {

    public TableLoader() {
    }
    
    public static final Logger LOGGER = Logger.getLogger(TableLoader.class.getName());

    private static boolean T2DLOADED = false;
    
    public static final int NBINST=2000;
    public static final double[] betaValues = new double[] {0.6, 0.7, 0.8, 0.9, 1.0};
    public static final double[] BfieldValues = new double[]{0.0000, 1.0000, 1.4142, 1.7321, 2.0000, 2.2361, 2.4495, 2.6458};
    public static int minBinIdxB = 0;
    public static int maxBinIdxB = BfieldValues.length-1;
    public static int minBinIdxAlpha = 0;
    public static int maxBinIdxAlpha = 5;
    private static final double[] AlphaMid = new double[6];
    private static final double[][] AlphaBounds = new double[6][2];
    public static int minBinIdxT  = 0;
    public static final int[][][][] maxBinIdxT  = new int[6][6][8][6];
    public static double[][][][][][] DISTFROMTIME = new double[6][6][maxBinIdxB+1][maxBinIdxAlpha+1][betaValues.length][NBINST]; // sector slyr alpha Bfield time bins [s][r][ibfield][icosalpha][tbin]    
    public static int timeBinWidth = 2; //ns
    public static int maxTBin = -1;
    
    public static void main(String[] args) {
        DCEngine dce = new DCEngine("test");
        dce.setVariation("default");
        dce.LoadTables();
        ConstantProvider provider = GeometryFactory.getConstants(DetectorType.DC, 11, "default");
        for(int l=0; l<6; l++) {
            Constants.getInstance().wpdist[l] = provider.getDouble("/geometry/dc/superlayer/wpdist", l);
        }
        int run = 18331;
        TableLoader.Fill(dce.getConstantsManager().getConstants(run, Constants.T2DPRESSURE),
                dce.getConstantsManager().getConstants(run, Constants.T2DPRESSUREREF),
                dce.getConstantsManager().getConstants(run, Constants.PRESSURE)); 
        TableLoader.Fill(dce.getConstantsManager().getConstants(run, Constants.T2DPRESSURE),
                dce.getConstantsManager().getConstants(run, Constants.T2DPRESSUREREF),
                dce.getConstantsManager().getConstants(run, Constants.PRESSURE)); 
        test();
    }
    
    public static void test(){
        
        plotFcns();
        
        TimeToDistanceEstimator tde = new TimeToDistanceEstimator();
        tde.t2DPrecisionImprov=true;
        double[] beta = new double[]{0.65, 0.66, 0.67, 0.68, 0.7, 0.75, 0.8, 0.85, 0.9, 0.95, 1.0};
        double[] Bfield = new double[]{0.0, 0.5, 1.0, 1.2, 1.3, 1.4, 1.5, 2.0, 2.1, 2.15, 2.2, 2.25, 2.26, 2.27, 2.28, 2.29, 2.3};
        double[] htmax = new double[]{200,210,1050,1200,720,800};
        double[] hdmax = new double[] {0.77, 0.8, 1.1, 1.2, 1.8, 1.84};

        List<H1F> hts = new ArrayList<>(); //histo to check table and interp. from time to idistance (by interpolation)
                                           //to calculated time (from dist.to t) in seconds; as a function of time
        List<H1F> hds = new ArrayList<>(); //histo to check table and interp. from distance to calculated time (from dist.to t) 
                                           //to idistance (by interpolation) in microns; as a function of distance
        List<H2F> hd2s = new ArrayList<>();// as s function of distance   
        List<H2F> ht2d = new ArrayList<>();// time to distance from interpolation 
        for(int r = 0; r<6; r++ ){ //loop over slys
            hts.add(new H1F("ht"+(r+1), "time resolution (ns)", "Counts/0.1 ns", 400, -20.0,20.0));
            hds.add(new H1F("hd"+(r+1), "doca resolution (um)", "Counts/0.1 um", 400, -20.0,20.0));
            //public H2F(String name, String title, int bx, double xmin, double xmax, int by, double ymin, double ymax)
            hd2s.add(new H2F("hd2"+(r+1), "doca resolution (um) vs doca (cm)", (int)(hdmax[r]*100), 0, hdmax[r], 400, -20.0,20.0));
            ht2d.add(new H2F("ht2d"+(r+1), "time(time (ns) vs doca (cm)", (int)htmax[r], 0, htmax[r], (int)(hdmax[r]*100), 0, hdmax[r]));
        }
        double t1 = System.currentTimeMillis();
        int NumInterpCalls = 0;        
        for(int r = 0; r<6; r++ ){ //loop over slys
            int s=0;
            double dmax = 2.*Constants.getInstance().wpdist[r]; 
            int maxBidx = Bfield.length; 
            if(r<2 || r>3) maxBidx=1;
            for(int ibfield =0; ibfield<maxBidx; ibfield++) {
                double Bf = Bfield[ibfield]; 
                for(int icosalpha =0; icosalpha<maxBinIdxAlpha+1; icosalpha++) {
                    double alpha = -(Math.toDegrees(Math.acos(Math.cos(Math.toRadians(30.)) + (icosalpha)*(1. - Math.cos(Math.toRadians(30.)))/5.)) - 30.);
                    for(int b=0; b<beta.length; b++) {
                        for(int t =0; t<htmax[r]; t++)  {
                            double time = (double) t;
                            double idist = tde.interpolateOnGrid(Bf, alpha, beta[b], time, s, r);
                            NumInterpCalls++;
                            ht2d.get(r).fill(time, idist);
                            if(idist<dmax) { 
                                double calct = calc_Time( idist,  alpha, Bf, s+1, r+1) +
                                    getDeltaTimeBeta(idist,beta[b],distbeta[s][r],v0[s][r]);
                                double deltaT = time-calct; 
                                hts.get(r).fill(deltaT);
                            }
                        }

                        for(int d =0; d<(int)(hdmax[r]*10000); d++) {
                            double dist = (double) d/10000; 
                            double calct = calc_Time( dist,  alpha, Bf, s+1, r+1) +
                                    getDeltaTimeBeta(dist,beta[b],distbeta[s][r],v0[s][r]); 
                            double idist = tde.interpolateOnGrid(Bf, alpha, beta[b], calct, s, r); 
                            NumInterpCalls++;
                            double deltaD = dist-idist;
                            hds.get(r).fill(deltaD*10000);
                            hd2s.get(r).fill(dist, deltaD*10000);
                        } 
                    }
                }
            }
            System.out.println("Done filling histograms for superlayer "+(r+1));
        }
        System.out.println("Done");
        double t = System.currentTimeMillis() - t1;
        System.out.println("PROCESSING TIME= " +(float) (t*1000/(double) NumInterpCalls)+" ns  / T2D Interpolation");
        TCanvas T2DCan = new TCanvas("Time resolution", 1600, 1200);
        TCanvas TCan = new TCanvas("Time resolution", 1600, 1200);
        TCanvas DCan = new TCanvas("Distance resolution", 1600, 1200);
        TCanvas DCan2 = new TCanvas("Distance resolution", 1600, 1200);
        
        TCan.divide(3, 2);
        T2DCan.divide(3, 2);
        DCan.divide(3, 2);
        DCan2.divide(3, 2);
        int ci=0;
        for(int sl = 0; sl<6; sl++ ){ // loop over sectors
            T2DCan.cd(ci);
            T2DCan.getPad().getAxisZ().setLog(true);
            T2DCan.draw(ht2d.get(sl));
            TCan.cd(ci);
            TCan.draw(hts.get(sl));
            DCan.cd(ci);
            DCan.draw(hds.get(sl));
            DCan2.cd(ci);
            //DCan2.getPad().getAxisZ().setLog(true);
            DCan2.draw(hd2s.get(sl));
            
            ci++;
        }
    }
    
    private static synchronized void plotFcns(){
        TCanvas CanD2T = new TCanvas("Distance to Time Functions", 1600, 1200);
        CanD2T.divide(3, 2);
        Map<String, FitLine> fmap =  new HashMap<>();
        for (int i = 0; i < 6; i++) {
            CanD2T.cd(i);
            double[] pars = new double[11];
            pars[0] = TableLoader.v0[0][i];
            pars[1] = TableLoader.vmid[0][i];
            pars[2] = TableLoader.FracDmaxAtMinVel[0][i];
            pars[3] = TableLoader.Tmax[0][i];
            pars[4] = TableLoader.distbeta[0][i];
            pars[5] = TableLoader.delta_bfield_coefficient[0][i];
            pars[6] = TableLoader.b1[0][i];
            pars[7] = TableLoader.b2[0][i];
            pars[8] = TableLoader.b3[0][i];
            pars[9] = TableLoader.b4[0][i];
            pars[10] = 2.*Constants.getInstance().wpdist[i];//fix dmax
            
            for(int j =0; j<maxBinIdxAlpha+1; j++) {
                double cos30minusalpha = Math.cos(Math.toRadians(30.)) + (double) (j)*(1. - Math.cos(Math.toRadians(30.)))/5.;
                double alpha = -(Math.toDegrees(Math.acos(cos30minusalpha)) - 30);
                int maxBidx = BfieldValues.length;
                for(int k =0; k<maxBidx; k++) {
                    if(k>0 && (i<2 || i>3) ) continue;
                    String name = "f";
                    name+=i;
                    name+=".";
                    name+=j;
                    name+=".";
                    name+=k;
                    fmap.put(name, new FitLine(name, 0, pars[10],  0, i, 3, alpha, BfieldValues[k]));
                    fmap.get(name).setLineWidth(2);
                    fmap.get(name).setLineColor(k+1);
                    fmap.get(name).setRange(0, pars[10]);  
                    fmap.get(name).getAttributes().setTitleX("doca (cm)");
                    fmap.get(name).getAttributes().setTitleY("time (ns)");
                    CanD2T.draw(fmap.get(name), "same");
                }
            }
        }
        
    }
    
    private static int getAlphaBin(double Alpha) {
        int bin = 0;
        for(int b =0; b<6; b++) {
            if(Alpha>=AlphaBounds[b][0] && Alpha<=AlphaBounds[b][1] )
                bin = b;
        }
        return bin;
    }
    
    private static synchronized void FillAlpha() {
        for(int icosalpha =0; icosalpha<maxBinIdxAlpha+1; icosalpha++) {

            double cos30minusalphaM = Math.cos(Math.toRadians(30.)) + (double) 
                    (icosalpha)*(1. - Math.cos(Math.toRadians(30.)))/5.;
            double alphaM = -(Math.toDegrees(Math.acos(cos30minusalphaM)) - 30);
            AlphaMid[icosalpha]= alphaM;
            double cos30minusalphaU = Math.cos(Math.toRadians(30.)) + (double) 
                    (icosalpha+0.5)*(1. - Math.cos(Math.toRadians(30.)))/5.;
            double alphaU = -(Math.toDegrees(Math.acos(cos30minusalphaU)) - 30);
            AlphaBounds[icosalpha][1] = alphaU;
            double cos30minusalphaL = Math.cos(Math.toRadians(30.)) + (double) 
                    (icosalpha-0.5)*(1. - Math.cos(Math.toRadians(30.)))/5.;
            double alphaL = -(Math.toDegrees(Math.acos(cos30minusalphaL)) - 30);
            AlphaBounds[icosalpha][0] = alphaL;
        }
        AlphaMid[0] = 0;
        AlphaMid[5] = 30;
        AlphaBounds[0][0] = 0;
        AlphaBounds[5][1] = 30;
    }
    
    public static synchronized void Fill(IndexedTable t2dPressure, IndexedTable t2dPressRef, IndexedTable pressure) {
        
        //CCDBTables 0 =  "/calibration/dc/signal_generation/doca_resolution";
        //CCDBTables 1 =  "/calibration/dc/time_to_distance/t2d";
        //CCDBTables 2 =  "/calibration/dc/time_corrections/T0_correction";	
        if (T2DLOADED) return;
        
        double stepSize = 0.00010;
        FillAlpha();
        double p_ref = t2dPressRef.getDoubleValue("pressure", 0,0,0);
        double p = pressure.getDoubleValue("value", 0,0,3);
        double dp = p - p_ref;
        double dp2scale = 0;
        double dpscale = 1;
        boolean useP = true;
        if(!useP) 
            dpscale = 0;
        TimeToDistanceEstimator tde = new TimeToDistanceEstimator();
        
        for(int s = 0; s<6; s++ ){ // loop over sectors
            for(int r = 0; r<6; r++ ){ //loop over slys
                // Fill constants
                FracDmaxAtMinVel[s][r] = t2dPressure.getDoubleValue("c1_a0", s+1,r+1,0)
                        +t2dPressure.getDoubleValue("c1_a1", s+1,r+1,0)*dp*dpscale;
                v0[s][r] = t2dPressure.getDoubleValue("v0_a0", s+1,r+1,0)
                        +t2dPressure.getDoubleValue("v0_a1", s+1,r+1,0)*dp*dpscale
                        +t2dPressure.getDoubleValue("v0_a2", s+1,r+1,0)*dp*dp*dp2scale;
                vmid[s][r] = t2dPressure.getDoubleValue("vmid_a0", s+1,r+1,0)
                        +t2dPressure.getDoubleValue("vmid_a1", s+1,r+1,0)*dp*dpscale
                        +t2dPressure.getDoubleValue("vmid_a2", s+1,r+1,0)*dp*dp*dp2scale;
                distbeta[s][r] = t2dPressure.getDoubleValue("distbeta_a0", s+1,r+1,0)
                        +t2dPressure.getDoubleValue("distbeta_a1", s+1,r+1,0)*dp*dpscale
                        +t2dPressure.getDoubleValue("distbeta_a2", s+1,r+1,0)*dp*dp*dp2scale;
                if(r>1 && r<4) {
                    delta_bfield_coefficient[s][r] = t2dPressure.getDoubleValue("delta_bfield_a0", s+1,r+1,0)
                            +t2dPressure.getDoubleValue("delta_bfield_a1", s+1,r+1,0)*dp*dpscale
                            +t2dPressure.getDoubleValue("delta_bfield_a2", s+1,r+1,0)*dp*dp*dp2scale
                            +t2dPressure.getDoubleValue("delta_bfield_a1", s+1,r+1,0)*dp*dpscale
                            +t2dPressure.getDoubleValue("delta_bfield_a2", s+1,r+1,0)*dp*dp*dp2scale;
                    b1[s][r] = t2dPressure.getDoubleValue("b1_a0", s+1,r+1,0)
                            +t2dPressure.getDoubleValue("b1_a1", s+1,r+1,0)*dp*dpscale
                            +t2dPressure.getDoubleValue("b1_a2", s+1,r+1,0)*dp*dp*dp2scale;
                    b2[s][r] = t2dPressure.getDoubleValue("b2_a0", s+1,r+1,0)
                            +t2dPressure.getDoubleValue("b2_a1", s+1,r+1,0)*dp*dpscale
                            +t2dPressure.getDoubleValue("b2_a2", s+1,r+1,0)*dp*dp*dp2scale;
                    b3[s][r] = t2dPressure.getDoubleValue("b3_a0", s+1,r+1,0)
                            +t2dPressure.getDoubleValue("b3_a1", s+1,r+1,0)*dp*dpscale
                            +t2dPressure.getDoubleValue("b3_a2", s+1,r+1,0)*dp*dp*dp2scale;
                    b4[s][r] = t2dPressure.getDoubleValue("b4_a0", s+1,r+1,0)
                            +t2dPressure.getDoubleValue("b4_a1", s+1,r+1,0)*dp*dpscale
                            +t2dPressure.getDoubleValue("b4_a2", s+1,r+1,0)*dp*dp*dp2scale;
                }
                Tmax[s][r] = t2dPressure.getDoubleValue("tmax_a0", s+1,r+1,0)
                        +t2dPressure.getDoubleValue("tmax_a1", s+1,r+1,0)*dp*dpscale
                        +t2dPressure.getDoubleValue("tmax_a2", s+1,r+1,0)*dp*dp*dp2scale;
             
                // end fill constants
                //System.out.println("sector "+(s+1)+" sly "+(r+1)+" v0 "+v0[s][r]+" vmid "+vmid[s][r]+" R "+FracDmaxAtMinVel[s][r]);
                double dmax = 2.*Constants.getInstance().wpdist[r]; 
                //double tmax = CCDBConstants.getTMAXSUPERLAYER()[s][r];
                for(int ibfield =0; ibfield<maxBinIdxB+1; ibfield++) {
                    double bfield = BfieldValues[ibfield];
                    for(int ibeta=0; ibeta<betaValues.length; ibeta++) {    
                        for(int icosalpha =0; icosalpha<maxBinIdxAlpha+1; icosalpha++) {
                            maxBinIdxT[s][r][ibfield][icosalpha] = NBINST; 
                            double cos30minusalpha = Math.cos(Math.toRadians(30.)) + (double) (icosalpha)*(1. - Math.cos(Math.toRadians(30.)))/5.;
                            double alpha = -(Math.toDegrees(Math.acos(cos30minusalpha)) - 30);
                            //int nxmax = (int) (dmax*cos30minusalpha/stepSize)+1; 
                            int nxmax = (int) (dmax/stepSize)+1; 
                            double maxTime=-1;
                            double midbinclosestT=0;
                            int di=0;
                            for(int idist =0; idist<nxmax; idist++) {
                                double x = (double)(idist+1)*stepSize;
                                double timebfield = calc_Time( x,  alpha, bfield, s+1, r+1) ;
                                double deltatime_beta = getDeltaTimeBeta(x,betaValues[ibeta],distbeta[s][r],v0[s][r]);
                                timebfield+=deltatime_beta;
                                if(timebfield>maxTime) 
                                   maxTime=timebfield;
                                
                                    //System.out.println("T "+timebfield+" maxT "+maxTime+" x "+x);
                                int tbin = (int) Math.floor(timebfield/2);
                                if(tbin<0 || tbin>NBINST-1) {
                                    //System.err.println("Problem with tbin");
                                    continue;
                                } 
                                if(tbin>maxTBin) 
                                    maxTBin = tbin;
                                
                                if(timebfield<maxTime) { //fix for turning over of the function
                                    continue;
                                }
                                if(timebfield<=(double)(tbin*2)+1) {//get the value in the middle of the bin
                                    DISTFROMTIME[s][r][ibfield][icosalpha][ibeta][tbin]=x; 
                                    midbinclosestT=timebfield;
                                    di =idist;
                                } else { //if step beyond middle of the bin interpolate to get the value at the middle of the bin
                                    if(idist==di+1) {
                                        double midBinx = (((double)(tbin*2)+1) - timebfield)*(DISTFROMTIME[s][r][ibfield][icosalpha][ibeta][tbin] - x)/(midbinclosestT - timebfield) + x;
                                        DISTFROMTIME[s][r][ibfield][icosalpha][ibeta][tbin]=midBinx;
                                    }
                                }
                                
                                if(DISTFROMTIME[s][r][ibfield][icosalpha][ibeta][tbin]>dmax) {
                                    DISTFROMTIME[s][r][ibfield][icosalpha][ibeta][tbin]=dmax;
                                    idist=nxmax;
                                }
                            }
                        }
                    }
                }
            }
        }
        
        TableLoader.fillMissingTableBins();
        System.out.println(" T2D TABLE FILLED.....");
        T2DLOADED = true;
     }
    
    private static void fillMissingTableBins() {
        
        for(int s = 0; s<6; s++ ){ // loop over sectors

            for(int r = 0; r<6; r++ ){ //loop over slys
                
                for(int ibfield =0; ibfield<maxBinIdxB+1; ibfield++) {
                    
                    for(int icosalpha =0; icosalpha<maxBinIdxAlpha+1; icosalpha++) {
                        
                        for(int ibeta=0; ibeta<betaValues.length; ibeta++) {
                            
                            for(int tbin = 0; tbin<maxTBin; tbin++) {
                                
                                if(DISTFROMTIME[s][r][ibfield][icosalpha][ibeta][tbin]!=0 && DISTFROMTIME[s][r][ibfield][icosalpha][ibeta][tbin+1]==0) {
                                    DISTFROMTIME[s][r][ibfield][icosalpha][ibeta][tbin+1] = DISTFROMTIME[s][r][ibfield][icosalpha][ibeta][tbin];
                                }
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
    public static synchronized double calc_Time(double x, double alpha, double bfield, int sector, int superlayer) {
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
    
    public static synchronized double getDeltaTimeBeta(double x, double beta, double distbeta, double v_0) {
      
        double value = (0.5*Math.pow(beta*beta*distbeta,3)*x/(Math.pow(beta*beta*distbeta,3)+x*x*x))/v_0;
        
        return value;
    }
    
    
    public static double[][] delta_T0 = new double[6][6];
    public static double[][] delta_bfield_coefficient = new double[6][6];
    public static double[][] distbeta = new double[6][6];
    public static double[][] vmid = new double[6][6];
    public static double[][] v0 = new double[6][6];
    public static double[][] b1 = new double[6][6];
    public static double[][] b2 = new double[6][6];
    public static double[][] b3 = new double[6][6];
    public static double[][] b4 = new double[6][6];
    public static double[][] Tmax = new double[6][6];
    public static double[][] FracDmaxAtMinVel = new double[6][6];	

    private static class FitLine extends Func1D {
        
        public FitLine(String name, double min, double max,
                int s, int r, int ibeta, double alpha, double bfield) {
             super(name, min, max);
            this.s = s;
            this.r = r;
            this.ibeta = ibeta;
            this.alpha = alpha;
            this.bfield = bfield;
        }
        private final double alpha;
        private final double bfield;
        private final int ibeta;
        private final int s;
        private final int r;
        
        @Override
        public double evaluate(double x) { 
            double timebfield = calc_Time( x,  alpha, bfield, s+1, r+1) ;
            double deltatime_beta = getDeltaTimeBeta(x,betaValues[ibeta],distbeta[s][r],v0[s][r]);
            timebfield+=deltatime_beta;
            return timebfield;
        }
    }
}

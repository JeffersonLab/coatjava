package org.jlab.rec.dc.timetodistance;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.logging.Logger;


public class TimeToDistanceEstimator {

    public TimeToDistanceEstimator() {
    }
    
    private static final Logger LOGGER = Logger.getLogger(TimeToDistanceEstimator.class.getName());
    /**
     * 
     * @param x value on grid
     * @param xa lower x bound on grid
     * @param xb upper x bound on grid
     * @param ya lower y bound on grid
     * @param yb upper y bound on grid
     * @return y value on grid from linear interpolation between a and b evaluated at x
     */
    private double interpolateLinear(double x0, double xa, double xb, double ya, double yb) {
        double x = x0;
        if(x>xb)
            x=xb;
        if(x<xa)
            x=xa;
        double y = (ya + yb)*0.5;
        if(xb - xa == 0) 
            return y;
        
        y = ya*(xb - x)/(xb - xa) + yb*(x - xa)/(xb - xa);
        
        return y;
    }

    /**
    * 
    * @param B B field in T
    * @param alpha is the local angle in degrees
    * @param t time in ns
    * @param SlyrIdx slyr index (0...5)
    * @return the distance to the wire in cm
    */
    public double interpolateOnGrid(double Bf, double alpha, double beta, double t,  int SecIdx, int SlyrIdx) {
        
        int binlowBeta = this.getBetaIdx(beta);
        int binhighBeta = this.getBetaIdx(beta)+1; 
        if(binhighBeta>TableLoader.betaValues.length-1)
            binhighBeta=TableLoader.betaValues.length-1;
        
        double beta1 = TableLoader.betaValues[binlowBeta];
        double beta2 = TableLoader.betaValues[binhighBeta];
        
        double B = Math.abs(Bf);
        
        int binlowB  = this.getBIdx(B);
        int binhighB = binlowB + 1; 
        if(SlyrIdx<2 || SlyrIdx>3) {
            binlowB = 0;
            binhighB = 0;
        }
            
        if(binhighB > TableLoader.maxBinIdxB) {
            binhighB = TableLoader.maxBinIdxB;
        }

        double B1 = TableLoader.BfieldValues[binlowB];
        double B2 = TableLoader.BfieldValues[binhighB];

         // for alpha ranges		
        int binlowAlpha  = this.getAlphaIdx(alpha);
        int binhighAlpha = binlowAlpha + 1;
        
        
        if(binhighAlpha > TableLoader.maxBinIdxAlpha) {
            binhighAlpha = TableLoader.maxBinIdxAlpha;
        }
        //if(binhighAlpha==binlowAlpha) {
        //    binlowAlpha=binhighAlpha-1;
        //}
       
        double alpha1 = this.getAlphaFromAlphaIdx(binlowAlpha);	 
        double alpha2 = this.getAlphaFromAlphaIdx(binhighAlpha);
        //return TableLoader.DISTFROMTIME[SecIdx][SlyrIdx][binlowB][binlowAlpha][binlowBeta][this.getTimeIdx(t, SecIdx, SlyrIdx, binlowB, binlowAlpha,binlowBeta)];
        // interpolate in B:
        double f_B_alpha1_beta1_t1 = interpolateLinear(B*B, B1*B1, B2*B2, 
                    TableLoader.DISTFROMTIME[SecIdx][SlyrIdx][binlowB][binlowAlpha][binlowBeta][this.getTimeIdx(t)],
                    TableLoader.DISTFROMTIME[SecIdx][SlyrIdx][binhighB][binlowAlpha][binlowBeta][this.getTimeIdx(t)]);
        double f_B_alpha2_beta1_t1 = interpolateLinear(B*B, B1*B1, B2*B2, 
                    TableLoader.DISTFROMTIME[SecIdx][SlyrIdx][binlowB][binhighAlpha][binlowBeta][this.getTimeIdx(t)],
                    TableLoader.DISTFROMTIME[SecIdx][SlyrIdx][binhighB][binhighAlpha][binlowBeta][this.getTimeIdx(t)]);
        double f_B_alpha1_beta1_t2 = interpolateLinear(B*B, B1*B1, B2*B2, 
                    TableLoader.DISTFROMTIME[SecIdx][SlyrIdx][binlowB][binlowAlpha][binlowBeta][this.getTimeNextIdx(t)],
                    TableLoader.DISTFROMTIME[SecIdx][SlyrIdx][binhighB][binlowAlpha][binlowBeta][this.getTimeNextIdx(t)]);
        double f_B_alpha2_beta1_t2 = interpolateLinear(B*B, B1*B1, B2*B2, 
                    TableLoader.DISTFROMTIME[SecIdx][SlyrIdx][binlowB][binhighAlpha][binlowBeta][this.getTimeNextIdx(t)],
                    TableLoader.DISTFROMTIME[SecIdx][SlyrIdx][binhighB][binhighAlpha][binlowBeta][this.getTimeNextIdx(t)]);
        double f_B_alpha1_beta2_t1 = interpolateLinear(B*B, B1*B1, B2*B2, 
                    TableLoader.DISTFROMTIME[SecIdx][SlyrIdx][binlowB][binlowAlpha][binhighBeta][this.getTimeIdx(t)],
                    TableLoader.DISTFROMTIME[SecIdx][SlyrIdx][binhighB][binlowAlpha][binhighBeta][this.getTimeIdx(t)]);
        double f_B_alpha2_beta2_t1 = interpolateLinear(B*B, B1*B1, B2*B2, 
                    TableLoader.DISTFROMTIME[SecIdx][SlyrIdx][binlowB][binhighAlpha][binhighBeta][this.getTimeIdx(t)],
                    TableLoader.DISTFROMTIME[SecIdx][SlyrIdx][binhighB][binhighAlpha][binhighBeta][this.getTimeIdx(t)]);
        double f_B_alpha1_beta2_t2 = interpolateLinear(B*B, B1*B1, B2*B2, 
                    TableLoader.DISTFROMTIME[SecIdx][SlyrIdx][binlowB][binlowAlpha][binhighBeta][this.getTimeNextIdx(t)],
                    TableLoader.DISTFROMTIME[SecIdx][SlyrIdx][binhighB][binlowAlpha][binhighBeta][this.getTimeNextIdx(t)]);
        double f_B_alpha2_beta2_t2 = interpolateLinear(B*B, B1*B1, B2*B2, 
                    TableLoader.DISTFROMTIME[SecIdx][SlyrIdx][binlowB][binhighAlpha][binhighBeta][this.getTimeNextIdx(t)],
                    TableLoader.DISTFROMTIME[SecIdx][SlyrIdx][binhighB][binhighAlpha][binhighBeta][this.getTimeNextIdx(t)]);
        //interpolate for 2 values of beta:
        double tlo = this.getTimeIdx(t)*2.;
        double thi = this.getTimeNextIdx(t)*2.;
        
//        if(t<tlo) {
//            thi = tlo;
//            tlo = this.getTimeIdx(t)*2.;
//        }
//        if(t==tlo) {
//            thi = tlo;
//        }    
        double f_B_alpha1_beta1_t = interpolateLinear(t, tlo, thi, f_B_alpha1_beta1_t1, f_B_alpha1_beta1_t2);
        double f_B_alpha1_beta2_t = interpolateLinear(t, tlo, thi, f_B_alpha1_beta2_t1, f_B_alpha1_beta2_t2);
        double f_B_alpha2_beta1_t = interpolateLinear(t, tlo, thi, f_B_alpha2_beta1_t1, f_B_alpha2_beta1_t2);
        double f_B_alpha2_beta2_t = interpolateLinear(t, tlo, thi, f_B_alpha2_beta2_t1, f_B_alpha2_beta2_t2);
        double f_B_alpha1_beta = interpolateLinear(beta, beta1, beta2,f_B_alpha1_beta1_t,f_B_alpha1_beta2_t);
        double f_B_alpha2_beta = interpolateLinear(beta, beta1, beta2,f_B_alpha2_beta1_t,f_B_alpha2_beta2_t);
        // interpolate in alpha: (cos30-cosA)
//        double f_B_alpha_t = interpolateLinear((Math.cos(Math.toRadians(30.))-Math.cos(Math.toRadians(alpha))), 
//                    (Math.cos(Math.toRadians(30.))-Math.cos(Math.toRadians(alpha1))), 
//                    (Math.cos(Math.toRadians(30.))-Math.cos(Math.toRadians(alpha2))), f_B_alpha1_beta, f_B_alpha2_beta);
//        
        //System.out.println("t "+t+" tlo "+tlo+" thi "+thi+" f_B_alpha1_beta1_t1 "+f_B_alpha1_beta1_t1+" f_B_alpha1_beta1_t2 "+f_B_alpha1_beta1_t2+" f_B_alpha1_beta1_t "+f_B_alpha1_beta1_t);
        double f_B_alpha_t = interpolateLinear(Math.cos(Math.toRadians(30.-alpha)), 
                    Math.cos(Math.toRadians(30.-alpha1)), 
                    Math.cos(Math.toRadians(30.-alpha2)), f_B_alpha1_beta, f_B_alpha2_beta);
        double x = f_B_alpha_t;
        double calctime = TableLoader.calc_Time( x,  alpha, B, SecIdx+1,  SlyrIdx+1) ;
        double deltatime_beta = TableLoader.getDeltaTimeBeta(x,beta,TableLoader.distbeta[SecIdx][SlyrIdx],TableLoader.v0[SecIdx][SlyrIdx]);
        calctime+=deltatime_beta;
        if(calctime-t>2)   {                  
            //System.out.println("doca "+f_B_alpha_t+" time "+t+" calctime "+calctime);
            for(int i = 1; i<101; i++) {
                x-=0.001*i;
                calctime = TableLoader.calc_Time( x,  alpha, B, SecIdx+1,  SlyrIdx+1) ;
                deltatime_beta = TableLoader.getDeltaTimeBeta(x,beta,TableLoader.distbeta[SecIdx][SlyrIdx],TableLoader.v0[SecIdx][SlyrIdx]);
                calctime+=deltatime_beta;
                if(calctime<t) break;
                // System.out.println(i+") doca "+x+" time "+t+" calctime "+calctime);
            }
        }
        
        return x;

   
   }
    
    
    /**
     * 
     * @param binAlpha alpha parameter bin
     * @return value of alpha from alpha bin
     */
    private double getAlphaFromAlphaIdx(int binAlpha) {
        double cos30minusalpha = Math.cos(Math.toRadians(30.)) + (double) (binAlpha)*(1. - Math.cos(Math.toRadians(30.)))/5.;
        double alpha =  -(Math.toDegrees(Math.acos(cos30minusalpha)) - 30); 
        double alpha1 = 0;
        double alpha2 = 30.;
        if(alpha<alpha1) {
            alpha=alpha1;
        }
         if(alpha>alpha2) {
             alpha=alpha2;
        }	
        return alpha;
    }
    /**
     * 
     * @param t1 time value in ns
     * @param is sector index (0...5)
     * @param ir superlayer index (0...5)
     * @param ibfield bfield bin (0...7)
     * @param icosalpha cosalpha bin (0...5)
     * @return time bin
     */
    public int getTimeIdx(double t1) {
        
        DecimalFormat df = new DecimalFormat("#");
        df.setRoundingMode(RoundingMode.CEILING);
       
        int binIdx =0;
        try{
            binIdx = Integer.parseInt(df.format(t1/2.) ) -1; 
        } catch (NumberFormatException e) {
            System.out.println(" time bin error "+t1+" ");
        }
        if(binIdx<0) {
            binIdx = TableLoader.minBinIdxT;
        }
        if(binIdx>TableLoader.maxTBin) {
            binIdx = TableLoader.maxTBin ;
        }

        return binIdx;
    }
    /**
     * 
     * @param b1 bfield value in T
     * @return B field bin
     */
    public int getBIdx(double b1) {
        
//        int binIdx = (int) ((1+b1)*2) -2;
//        if(binIdx<0) {
//            binIdx = TableLoader.minBinIdxB;
//        }
//        if(binIdx>TableLoader.maxBinIdxB) {
//            binIdx = TableLoader.maxBinIdxB;
//        }
        int maxBinIdxB = TableLoader.BfieldValues.length-1;
        DecimalFormat df = new DecimalFormat("#");
        df.setRoundingMode(RoundingMode.CEILING);
       
        int binIdx =0;
        try{
            binIdx = Integer.parseInt(df.format(b1*b1) ) -1; 
        } catch (NumberFormatException e) {
            System.out.println(" field bin error "+b1+" ");
        }
        if(binIdx<0) {
            binIdx = 0;
        }
        if(binIdx>maxBinIdxB)
            binIdx = maxBinIdxB;
        
        return binIdx;
    }
    /**
     * 
     * @param alpha alpha parameter in deg
     * @return alpha bin
     */
    private int getAlphaIdx(double alpha) {
        double Ccos30minusalpha = Math.cos(Math.toRadians(30.-alpha) ) ; 
        double Cicosalpha = (Ccos30minusalpha - Math.cos(Math.toRadians(30.)))/((1. - Math.cos(Math.toRadians(30.)))/5.);
        int binIdx = (int)  Cicosalpha; 
        if(binIdx<0) {
            binIdx = TableLoader.minBinIdxAlpha;
        }
        if(binIdx>TableLoader.maxBinIdxAlpha) {
            binIdx = TableLoader.maxBinIdxAlpha;
        } 
        return binIdx;
    }
    
    private int getBetaIdx(double beta) {
        if(beta>=1.0) return TableLoader.betaValues.length-1;
        int value = TableLoader.betaValues.length-1;
        for(int i = 0; i<TableLoader.betaValues.length-1; i++) {
            if(beta>TableLoader.betaValues[i] && beta<=TableLoader.betaValues[i+1]) {
                value = i;
            }
        }
        return value;
    }
    private int getTimeNextIdx(double t) {
        int binlowT = this.getTimeIdx(t);  
        int binhighT = binlowT + 1; 

        if(binhighT>TableLoader.nBinsT-1) {
            binhighT=TableLoader.nBinsT-1;
        }
        return binhighT;
    }
    
    

}
        


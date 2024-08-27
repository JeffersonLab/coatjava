package org.jlab.rec.dc.timetodistance;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.jlab.rec.dc.Constants;
import static org.jlab.rec.dc.timetodistance.TableLoader.BfieldValues;
import static org.jlab.rec.dc.timetodistance.TableLoader.calc_Time;


public class TimeToDistanceEstimator {


    public TimeToDistanceEstimator() {
            // TODO Auto-generated constructor stub
    }
    
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
        
        if(xb - xa == 0) 
            return (ya + yb)*0.5;
        
        return  (x - xb)*(ya - yb)/(xa - xb) + yb;
        
    }
    boolean print = false;
    /**
     * 
     * @param Bf
     * @param alpha
     * @param beta
     * @param t
     * @param SecIdx
     * @param SlyrIdx
     * @return 
     */
   
    public double interpolateOnGrid(double Bf, double alpha, double beta, double t,  int SecIdx, int SlyrIdx) {
        //get the time bins
        int tBin = this.getTimeIdx(t);
        double binCenterTime = 2*this.getTimeIdx(t)+1;
        int tLo=0;
        int tHi=0;
        if(t<binCenterTime) { // if the time is to the left of the center of the bin, interpolate using the previous time bin
            tLo=tBin-1;
            if(tLo<0) tLo=0;
            tHi=tBin;
        } else { //if the time is to the right of the center of the bin, interpolate using the next time bin
            tLo=this.getTimeIdx(t);
            tHi=this.getTimeIdx(t)+1;
            if(tHi>TableLoader.NBINST-1) {
            tHi=TableLoader.NBINST-1;
            }
        }
        double timeLo=2*tLo+1;
        double timeHi=2*tHi+1;
        //get the beta bins
        int binBeta = this.getBetaIdx(beta);
        int betaLo = 0;
        int betaHi = 0;
        double betaCent = TableLoader.betaValues[binBeta];
        if(beta<betaCent) { // if the beta is to the left of the center of the bin, interpolate using the previous time bin
            betaLo=binBeta-1;
            if(betaLo<0) betaLo=0;
            betaHi=binBeta;
        } else { //if the beta is to the right of the center of the bin, interpolate using the next time bin
            betaLo=binBeta;
            betaHi=binBeta+1;
            if(betaHi>TableLoader.betaValues.length-1) {
                betaHi=TableLoader.betaValues.length-1;
            }
        }
        double betaValueLo = TableLoader.betaValues[betaLo];
        double betaValueHigh = TableLoader.betaValues[betaHi];
        
        //get the Bfield bins
        double B = Math.abs(Bf);
        int binB = this.getBIdx(B);
        double BfCen = BfieldValues[binB];
        int BfLo=0;
        int BfHi=0;
        if(B<BfCen) { // if the Bfield value is to the left of the center of the bin, interpolate using the previous time bin
            BfLo=binB-1;
            if(BfLo<0) BfLo=0;
            BfHi=binB;
        } else { //if the Bfield value is to the right of the center of the bin, interpolate using the next time bin
            BfLo=binB;
            BfHi=binB+1;
            if(BfHi>TableLoader.BfieldValues.length-1) {
                BfHi=TableLoader.BfieldValues.length-1;
            }
        }
        double BLo = BfieldValues[BfLo];
        double BHi = BfieldValues[BfHi];
         // get the alpha bins	
        int alphaBin = this.getAlphaIdx(alpha); 
        int alphaLo = 0;
        int alphaHi = 0;
        double alphaCenValue = this.getAlphaFromAlphaIdx(alphaBin);
        if(alpha<alphaCenValue) { // if the alpha is to the left of the center of the bin, interpolate using the previous time bin
            alphaLo=alphaBin-1;
            if(alphaLo<0) alphaLo=0;
            alphaHi=alphaBin;
        } else { //if the alpha is to the right of the center of the bin, interpolate using the next time bin
            alphaLo=alphaBin;
            alphaHi=alphaBin+1;
            if(alphaHi > TableLoader.maxBinIdxAlpha) {
                alphaHi = TableLoader.maxBinIdxAlpha;
            }
        }
        double alphaValueLo = this.getAlphaFromAlphaIdx(alphaLo);	 
        double alphaValueHi = this.getAlphaFromAlphaIdx(alphaHi); 
        
        // interpolate in B:
        double f_B_alpha1_beta1_t1 = interpolateLinear(B*B, BLo*BLo, BHi*BHi, 
                    TableLoader.DISTFROMTIME[SecIdx][SlyrIdx][BfLo][alphaLo][betaLo][tLo],
                    TableLoader.DISTFROMTIME[SecIdx][SlyrIdx][BfHi][alphaLo][betaLo][tLo]);
        double f_B_alpha2_beta1_t1 = interpolateLinear(B*B, BLo*BLo, BHi*BHi, 
                    TableLoader.DISTFROMTIME[SecIdx][SlyrIdx][BfLo][alphaHi][betaLo][tLo],
                    TableLoader.DISTFROMTIME[SecIdx][SlyrIdx][BfHi][alphaHi][betaLo][tLo]);
        double f_B_alpha1_beta1_t2 = interpolateLinear(B*B, BLo*BLo, BHi*BHi, 
                    TableLoader.DISTFROMTIME[SecIdx][SlyrIdx][BfLo][alphaLo][betaLo][tHi],
                    TableLoader.DISTFROMTIME[SecIdx][SlyrIdx][BfHi][alphaLo][betaLo][tHi]);
        double f_B_alpha2_beta1_t2 = interpolateLinear(B*B, BLo*BLo, BHi*BHi, 
                    TableLoader.DISTFROMTIME[SecIdx][SlyrIdx][BfLo][alphaHi][betaLo][tHi],
                    TableLoader.DISTFROMTIME[SecIdx][SlyrIdx][BfHi][alphaHi][betaLo][tHi]);
        double f_B_alpha1_beta2_t1 = interpolateLinear(B*B, BLo*BLo, BHi*BHi, 
                    TableLoader.DISTFROMTIME[SecIdx][SlyrIdx][BfLo][alphaLo][betaHi][tLo],
                    TableLoader.DISTFROMTIME[SecIdx][SlyrIdx][BfHi][alphaLo][betaHi][tLo]);
        double f_B_alpha2_beta2_t1 = interpolateLinear(B*B, BLo*BLo, BHi*BHi, 
                    TableLoader.DISTFROMTIME[SecIdx][SlyrIdx][BfLo][alphaHi][betaHi][tLo],
                    TableLoader.DISTFROMTIME[SecIdx][SlyrIdx][BfHi][alphaHi][betaHi][tLo]);
        double f_B_alpha1_beta2_t2 = interpolateLinear(B*B, BLo*BLo, BHi*BHi, 
                    TableLoader.DISTFROMTIME[SecIdx][SlyrIdx][BfLo][alphaLo][betaHi][tHi],
                    TableLoader.DISTFROMTIME[SecIdx][SlyrIdx][BfHi][alphaLo][betaHi][tHi]);
        double f_B_alpha2_beta2_t2 = interpolateLinear(B*B, BLo*BLo, BHi*BHi, 
                    TableLoader.DISTFROMTIME[SecIdx][SlyrIdx][BfLo][alphaHi][betaHi][tHi],
                    TableLoader.DISTFROMTIME[SecIdx][SlyrIdx][BfHi][alphaHi][betaHi][tHi]);
        
        //interpolate in alpha
        double f_B_alpha_beta1_t1 = interpolateLinear(alpha, alphaValueLo, alphaValueHi, f_B_alpha1_beta1_t1, f_B_alpha2_beta1_t1);
        double f_B_alpha_beta2_t1 = interpolateLinear(alpha, alphaValueLo, alphaValueHi, f_B_alpha1_beta2_t1, f_B_alpha2_beta2_t1);
        double f_B_alpha_beta1_t2 = interpolateLinear(alpha, alphaValueLo, alphaValueHi, f_B_alpha1_beta1_t2, f_B_alpha2_beta1_t2);
        double f_B_alpha_beta2_t2 = interpolateLinear(alpha, alphaValueLo, alphaValueHi, f_B_alpha1_beta2_t2, f_B_alpha2_beta2_t2);
        //interpolate in beta
        double f_B_alpha_beta_t1 = interpolateLinear(beta, betaValueLo, betaValueHigh,f_B_alpha_beta1_t1,f_B_alpha_beta2_t1);
        double f_B_alpha_beta_t2 = interpolateLinear(beta, betaValueLo, betaValueHigh,f_B_alpha_beta1_t2,f_B_alpha_beta2_t2);
        //interpolate in time
        double f_B_alpha_beta_t = interpolateLinear(t, timeLo, timeHi, f_B_alpha_beta_t1, f_B_alpha_beta_t2);
        
        double x = f_B_alpha_beta_t;
//        String st =new String();
//        st +="time "+t+" beta "+beta+" BETA: ["+betaValueLo+" "+betaCent+" "+betaValueHigh+"]"+" alpha "+alpha+"\n";
//        st +=(" f_B_alpha1_beta1_t1 "+f_B_alpha1_beta1_t1+" f_B_alpha2_beta1_t1 "+f_B_alpha2_beta1_t1+"\n");
//        st +=(" f_B_alpha1_beta2_t1 "+f_B_alpha1_beta2_t1+" f_B_alpha2_beta2_t1 "+f_B_alpha2_beta2_t1+"\n");
//        st +=(" f_B_alpha1_beta1_t2 "+f_B_alpha1_beta1_t2+" f_B_alpha2_beta1_t2 "+f_B_alpha2_beta1_t2+"\n");
//        st +=(" f_B_alpha1_beta2_t2 "+f_B_alpha1_beta2_t2+" f_B_alpha2_beta2_t2 "+f_B_alpha2_beta2_t2+"\n");       
//        st +=(" f_B_alpha_beta1_t1 "+f_B_alpha_beta1_t1+" f_B_alpha_beta2_t1 "+f_B_alpha_beta2_t1+"\n");
//        st +=(" f_B_alpha_beta1_t2 "+f_B_alpha_beta1_t2+" f_B_alpha_beta2_t2 "+f_B_alpha_beta2_t2+"\n");
//        st +=(" f_B_alpha_beta_t1 "+f_B_alpha_beta_t1+" f_B_alpha_beta_t2 "+f_B_alpha_beta_t2+"\n");
//        st +=(" f_B_alpha_t "+f_B_alpha_beta_t+"\n");
        
        double dmax = 2.*Constants.getInstance().wpdist[SlyrIdx];
//        st +=(" x "+x+" dmax "+dmax+"\n");
        
        if(x>dmax) {
//            setDebug(st);
            return dmax;
        }
        
        //Reolution improvement to compensate for non-linearity accross bin not accounted for in interpolation
        double calctime = calc_Time( x,  alpha, B, SecIdx+1,  SlyrIdx+1) ;
        double deltatime_beta = TableLoader.getDeltaTimeBeta(x,beta,TableLoader.distbeta[SecIdx][SlyrIdx],TableLoader.v0[SecIdx][SlyrIdx]);
        calctime+=deltatime_beta;
//        st +=(" t "+t+" calctime "+calctime+" deltatime_beta "+deltatime_beta+"\n");
        if(calctime>t)   {     
            double tref=0;
            for(int i = 1; i<100; i++) {
                x-=0.0001*i;
                if(x<0) return x;
                calctime = calc_Time( x,  alpha, B, SecIdx+1,  SlyrIdx+1) ;
                deltatime_beta = TableLoader.getDeltaTimeBeta(x,beta,TableLoader.distbeta[SecIdx][SlyrIdx],TableLoader.v0[SecIdx][SlyrIdx]);
                calctime+=deltatime_beta;
//                st +=(i+"] x "+x+" t "+t+" calct "+calctime+"\n");
                if(calctime<t) {
                    if(tref==0) {
                        tref=calc_Time(x+0.0001*i,  alpha, B, SecIdx+1,  SlyrIdx+1) +
                                TableLoader.getDeltaTimeBeta(x+0.0001*i,beta,TableLoader.distbeta[SecIdx][SlyrIdx],TableLoader.v0[SecIdx][SlyrIdx]);             
                    }
                    double xi=this.interpolateLinear(t, calctime, tref, x, x+0.0001*i);
//                    st +=("xi "+xi+" t "+t+" calct "+calctime+" tref "+tref+"\n");
//                    setDebug(st);
                    return xi;
                }
                tref=calctime;
            }
        }
        if(t>calctime)   {   
            double tref=0;
            for(int i = 1; i<100; i++) {
                x+=0.0001*i;
                calctime = calc_Time( x,  alpha, B, SecIdx+1,  SlyrIdx+1) ;
                deltatime_beta = TableLoader.getDeltaTimeBeta(x,beta,TableLoader.distbeta[SecIdx][SlyrIdx],TableLoader.v0[SecIdx][SlyrIdx]);
                calctime+=deltatime_beta;
//                st +=(i+"] x "+x+" t "+t+" calct "+calctime+"\n");
                if(x>dmax) {
//                   setDebug(st);
                   return dmax;
                } 
                if(t<calctime) {
                    if(tref==0) {
                        tref=calc_Time(x-0.0001*i,  alpha, B, SecIdx+1,  SlyrIdx+1) +
                                TableLoader.getDeltaTimeBeta(x-0.0001*i,beta,TableLoader.distbeta[SecIdx][SlyrIdx],TableLoader.v0[SecIdx][SlyrIdx]);             
                    }
                    double xi=this.interpolateLinear(t, tref, calctime, x-0.0001*i, x);
//                    st +=(".xi "+xi+" t "+t+" calct "+calctime+" tref "+tref+"\n");
//                    setDebug(st);
                    return xi;
                }
                tref=calctime;
            }
        }
        
        
        return x;
   }
    private String debug;
    public void setDebug(String s) {
        debug = s;
    }
    public String debug() {
        return debug;
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
     * @return time bin
     */
    public int getTimeIdx(double t1) {
        int idx=(int) Math.floor(t1 / TableLoader.timeBinWidth);
        if(idx<0) idx=0;
        return idx;
    }
    
    /**
     * 
     * @param b1 bfield value in T
     * @return B field bin
     */
    public int getBIdx(double b1) { 
        int binIdx = (int) Math.floor(b1*b1);
        int maxBinIdxB = TableLoader.BfieldValues.length-1;
        
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
            if(beta>=TableLoader.betaValues[i] && beta<TableLoader.betaValues[i+1]) {
                value = i;
            }
        }
        return value;
    }
   
    
    
}

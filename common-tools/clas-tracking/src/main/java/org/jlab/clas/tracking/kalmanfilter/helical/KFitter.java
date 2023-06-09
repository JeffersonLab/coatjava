package org.jlab.clas.tracking.kalmanfilter.helical;

import java.util.List;
import org.jlab.clas.swimtools.Swim;
import org.jlab.clas.tracking.kalmanfilter.AKFitter;
import org.jlab.clas.tracking.kalmanfilter.AMeasVecs;
import org.jlab.clas.tracking.kalmanfilter.AStateVecs;
import org.jlab.clas.tracking.kalmanfilter.AStateVecs.StateVec;
import org.jlab.clas.tracking.kalmanfilter.Surface;
import org.jlab.clas.tracking.trackrep.Helix;
import org.jlab.clas.tracking.utilities.MatrixOps.Libr;

/**
 *
 * @author ziegler
 */
public class KFitter extends AKFitter {

    private StateVecs sv = new StateVecs();
    private MeasVecs  mv = new MeasVecs();
    private StateVec finalSmoothedStateVec = null;    
    private StateVec finalTransportedStateVec = null;    
    
    
    public KFitter(boolean filter, int iterations, int dir, Swim swim, Libr mo) {
        super(filter, iterations, dir, swim, mo);
    }
        
    public final void init(Helix helix, double[][] cov, 
                           double Xb, double Yb, double Zref, 
                           List<Surface> measSurfaces, double mass) {
        finalSmoothedStateVec = null;
        finalTransportedStateVec = null; 
        this.NDF0 = -5;
        this.NDF  = -5;
        this.chi2 = Double.POSITIVE_INFINITY;
        this.numIter = 0;
        this.setFitFailed = false;
        mv.setMeasVecs(measSurfaces);
        for (int i = 0; i < mv.measurements.size(); i++) {
            if(mv.measurements.get(i).skip==false) {
                this.NDF++;
            }
        } 
        this.setXb(Xb);
        this.setYb(Yb);
        sv.init( helix, cov, Xb, Yb, Zref, mass, this.getSwimmer());
    }
    /**
     * Fail safe mode for low pt tracks that fail filtering and smoothing
     */
    public void runFitterNoFiltFailSafe() {
        int k0 = 0;
        int kf = mv.measurements.size() - 1;
        
        boolean init = this.initIter(sv, mv);
        if(!init) return;
           
        //re-init state vector and cov mat for forward 
        //this.initOneWayIter(sv, k0);
        
        // transport in forward direction
        boolean forward = this.runOneWayFitterIter(sv, mv, k0, kf);
        if (!forward) return;
        
        finalSmoothedStateVec    = this.setFinalStateVector(sv.initSV);
        finalTransportedStateVec = this.setFinalStateVector(sv.initSV);
        double chisq = 0;
        this.NDF=-5;
        for(int k = 0; k< mv.measurements.size(); k++) {
            int index   = mv.measurements.get(k).layer;
            int layer   = mv.measurements.get(k).surface.getLayer();
            int sector  = mv.measurements.get(k).surface.getSector();
            
            if(!mv.measurements.get(k).surface.passive) {
                double dh    = mv.dh(k, sv.transported().get(k));
                double error = mv.measurements.get(k).error;
                chisq += dh*dh / error/error;
                this.NDF++;
                trajPoints.put(index, new HitOnTrack(layer, sector, sv.transported().get(k), dh,dh,dh));
            } else {
                trajPoints.put(index, new HitOnTrack(layer, sector, sv.transported().get(k), -999, -999, -999));
            }
        }
        this.chi2 = chisq; 
    }    
    public void runFitter() {
        this.runFitter(sv, mv);
    }

    public double getChi2() {
        return this.getChi2(0);
    }
    
    public double getChi2(int mode) {
        if(mode==1) {
            double chi2 = 0;
            for(int k = 1; k< mv.measurements.size(); k++) {
                if(!mv.measurements.get(k).skip) {
                    double dh    = mv.dh(k, sv.smoothed().get(k));
                    double error = mv.measurements.get(k).error;
                    chi2 += dh*dh / error/error;
                }
            }
            return chi2;           
        }
        else {
            return this.chi2;
        }
    }
    
    public int getNDF() {
        return this.getNDF(0);
    }
    
    public int getNDF(int mode) {
        if(mode==1) {
            int ndf = this.NDF0;
            for(int k = 1; k< mv.measurements.size(); k++) {
                if(!mv.measurements.get(k).skip) {
                    ndf++;
                }
            }
            return ndf;           
        }
        else {
            return this.NDF;
        }
    }
    
    public StateVec getStateVec() {
        return this.getStateVec(0);
    }
    
    public StateVec getStateVec(int mode) {
        if(mode==1) {
            return finalTransportedStateVec;           
        }
        else {
            return finalSmoothedStateVec;
        }
    }
    
    public Helix getHelix() {
        Helix hlx = this.getHelix(0);
        if(hlx!=null) {
            if(Double.isNaN(hlx.getD0()) || Double.isNaN(hlx.getPhi0()) || Double.isNaN(hlx.getOmega()) 
                    || Double.isNaN(hlx.getTanL()) || Double.isNaN(hlx.getZ0())) 
                return null;
        }
        return hlx;
    }
    
    public Helix getHelix(int mode) {
        Helix helx = null;
        if(mode==1) {
            if(finalTransportedStateVec!=null)
                helx = finalTransportedStateVec.getHelix(this.getXb(), this.getYb());           
        }
        else {
            if(finalSmoothedStateVec!=null)
                helx = finalSmoothedStateVec.getHelix(this.getXb(), this.getYb()); 
        }
        return helx;
    }
    
    private StateVec setFinalStateVector(StateVec onPivot) {
        StateVec vec = sv.new StateVec(onPivot);
        vec.setPivot(this.getXb(), this.getYb(), 0);
        vec.covMat = this.sv.propagateCovMat(onPivot, vec);
        return vec;
    }
    
    @Override
    public void runFitter(AStateVecs sv, AMeasVecs mv) { 
        // System.out.println("******************************ITER "+totNumIter);
        StateVec finalSmoothedOnPivot    = null;
        StateVec finalTransportedOnPivot = null;
        
        for (int it = 0; it < totNumIter; it++) {
            this.runFitterIter(sv, mv);
            // chi2
            double newchisq = this.calc_chi2(sv, mv); 
            //System.out.println("******************************ITER "+(it+1)+" "+ newchisq+" <? "+this.chi2);
            // if curvature is 0, fit failed
            if(Double.isNaN(newchisq) ||
               sv.smoothed().get(0)==null ||
               sv.smoothed().get(0).kappa==0 || 
               Double.isNaN(sv.smoothed().get(0).kappa)) {
                this.setFitFailed = true; 
                break; 
            }            
            // if chi2 improved and curvature is non-zero, save fit results but continue iterating
            else if(newchisq < this.chi2) {
                this.chi2 = newchisq;
                finalSmoothedOnPivot    = sv.new StateVec(sv.smoothed().get(0));
                finalTransportedOnPivot = sv.new StateVec(sv.transported(false).get(0));
                this.setTrajectory(sv, mv);
            }
            // stop if chi2 got worse
            else {
                break;
            }
        }
        if(!this.setFitFailed) {
            finalSmoothedStateVec    = this.setFinalStateVector(finalSmoothedOnPivot);
            finalTransportedStateVec = this.setFinalStateVector(finalTransportedOnPivot);
        }
    }

    @Override
    public StateVec filter(int k, StateVec vec, AMeasVecs mv) {
        if (vec != null && vec.covMat != null) {
        
            StateVec fVec = sv.new StateVec(vec);
            
            if(mv.measurements.get(k).skip == false && filterOn) {

                double[] K = new double[5];
                double V = mv.measurements.get(k).error*mv.measurements.get(k).error;

                double dh = mv.dh(k, fVec);
    //            System.out.println(dh);
                //get the projector Matrix
                double[] H = mv.H(fVec, sv,  mv.measurements.get(k), this.getSwimmer());
    //            System.out.println(k + " " + mv.measurements.get(k).layer + " " + H[0] + " " + H[1] + " " + H[2] + " " + H[3] + " " + H[4] + " " +dh );
                
                double[][] CaInv =  this.getMatrixOps().filterCovMat(H, fVec.covMat, V);
                if (CaInv != null) {
                        fVec.covMat = CaInv;
                    } else {
                        return null;
                }

                for (int j = 0; j < 5; j++) {
                    // the gain matrix
                    K[j] = 0;
                    for (int i = 0; i < 5; i++) {
                        K[j] += H[i] * fVec.covMat[j][i] / V; 
                    } 
                }
                if(sv.straight) {
                        K[2] = 0;
                }

                //sv.printlnStateVec(fVec);
                if (!Double.isNaN(dh) ) {
    //                for (int j = 0; j < 5; j++) {
    //                    for (int i = 0; i < 5; i++) {
    //                        System.out.print(CaInv[j][i] + " ");
    //                    }
    //                    System.out.println();
    //                }
    //                System.out.println(k + " " + CaInv[0][0] + " " + CaInv[1][1] + " " + CaInv[2][2] + " " + CaInv[3][3] + " " + CaInv[4][4] + " " + V );
    //                System.out.println(k + " " + H[0] + " " + H[1] + " " + H[2] + " " + H[3] + " " + H[4] + " " +dh );
    //                System.out.println(k + " " + K[0] + " " + K[1] + " " + K[2] + " " + K[3] + " " + K[4] + " " +dh );
                    fVec.d_rho -= K[0] * dh;
                    fVec.phi0  -= K[1] * dh;
                    fVec.kappa -= K[2] * dh;
                    fVec.dz    -= K[3] * dh;
                    fVec.tanL  -= K[4] * dh;
                }
    
                if(this.getSwimmer()!=null && !sv.straight) fVec.rollBack(mv.rollBackAngle);
                fVec.updateFromHelix();
    // sv.printlnStateVec(fVec);
                sv.setStateVecPosAtMeasSite(fVec, mv.measurements.get(k), this.getSwimmer()); 
    // sv.printlnStateVec(fVec);
                fVec.residual = mv.dh(k, fVec); 
    //            System.out.println(dh_filt + " " + dh);
                if (Double.isNaN(fVec.residual) 
                 || Math.abs(fVec.residual) > Math.max(V, 10*Math.abs(dh))
                 || Math.abs(fVec.residual)/Math.sqrt(V)>this.getResidualsCut()) { 
                    this.NDF--;
                    mv.measurements.get(k).skip = true;
                    fVec = sv.new StateVec(vec);
                    if(this.getSwimmer()!=null && !sv.straight) fVec.rollBack(mv.rollBackAngle);
                    fVec.updateFromHelix();
                }
            }
            return fVec;
        }
        return null;
    }

    @Override
    public StateVec smooth(int k, AStateVecs sv, AMeasVecs mv) {
        //State vector recursion formula :
        //-------------------------------
        //a_n_k = a_k + A_k(a_n_kp1 - a_k_kp1);
        //where:
        //a_n_k: smoothed at k, using n measurements
        //a_k: filtered at k
        //a_n_kp1: smoothed at k+1, using n measurements
        //a_k_kp1: transport from k to k+1 using observations from 1 to k;
        //and
        //Ak = C_k FT_k (C_k_kp1)^{-1}
        //where:
        //C_k_kp1: transport covariance matrix from k to k+1 using observations from 1 to k;
        //C_k: filtered covariance matrix at k
        //FT_k: transpose of del_f/del_a_k, the propagator matrix at k
        //====================================================================================
        //Covariance matrix recursion formula:
        //C_n_k = C_k + A_k(C_n_kp1 - C_k_kp1)AT_k;
        //where:
        //C_n_k: smoothed at k, using n measurements
        //C_k: filtered at k
        //C_n_kp1: smoothed at k+1, using n measurements
        //C_k_kp1: transport from k to k+1 using observations from 1 to k.

        StateVec a_k     = sv.trackTrajF.get(k);
        StateVec a_n_k   = sv.new StateVec(a_k);
        StateVec a_n_kp1 = sv.new StateVec(sv.trackTrajS.get(k+dir));
        StateVec a_k_kp1 = sv.new StateVec(sv.trackTrajT.get(k+dir));
        double[][] FMat  = a_n_k.F;
        double[][] FMatT = this.getMatrixOps().mo.MatrixTranspose(FMat);
        double[][] C_k     = a_k.covMat;
        double[][] C_n_kp1 = a_n_kp1.covMat;
        double[][] C_k_kp1 = a_k_kp1.covMat;
        //smoothed cov matrix 
        double[][] Ak = this.getMatrixOps().smoothingCorr(C_k, FMatT, C_k_kp1);
        if(Ak==null)
            return null;
        double [][] C_n_k = this.getMatrixOps().smoothCovMat(C_n_kp1, C_k, Ak, C_k_kp1);

        double [] diffSvComps = a_k_kp1.subtractHelix(a_n_kp1);
        double[] r = new double[5];
        for(int i =0; i<5; i++) {
            for(int j =0; j<5; j++) { 
                r[i]+=Ak[i][j]*(diffSvComps[j]);
            }
            if(Double.isNaN(r[i]))
                return null;
        }
        a_n_k.d_rho   -=r[0];
        a_n_k.phi0    -=r[1];
        a_n_k.kappa   -=r[2];
        a_n_k.dz      -=r[3];
        a_n_k.tanL    -=r[4];

        if(this.getSwimmer()!=null && !sv.straight) a_n_k.rollBack(mv.rollBackAngle);
        a_n_k.updateFromHelix();

        boolean stateOK = sv.setStateVecPosAtMeasSite(a_n_k, mv.measurements.get(k), this.getSwimmer()); 
        if(!stateOK) {
            return null;
        } 

        a_n_k.covMat = C_n_k;
        return a_n_k;
    }

    @Override
    public StateVec smooth(StateVec v1, StateVec v2) {
        
        if(v1==null || v2==null) return null;
        // move pivot of first state vector to match second
        v1.setPivot(v2.x0, v2.y0, v2.z0);
        // get covariance matrices and arrays
        double[][] c1 = v1.covMat;
        double[][] c2 = v2.covMat;
        double[]   a1 = v1.getHelixArray();
        double[]   a2 = v2.getHelixArray();
        // smooth covariance matrices
        double[][] c1i = this.getMatrixOps().inverse(c1);
        double[][] c2i = this.getMatrixOps().inverse(c2);
        if(c1i == null || c2i == null) return null;
        double[][]  ci = this.getMatrixOps().mo.MatrixAddition(c1i, c2i);
        double[][]   c = this.getMatrixOps().inverse(ci);
        if(c == null) return null;
        // smooth state vectors
        double[] a = new double[a1.length];
        for(int i=0; i<a1.length; i++) {
            for(int j=0; j<a1.length; j++) {
                for(int k=0; k<a1.length; k++) {
                    a[i] += c[i][j]*(c1i[j][k]*a1[k]+c2i[j][k]*a2[k]);
                }
            }
        }
        // create averaged state vector
        StateVec vave = sv.new StateVec(v2);
        vave.d_rho = a[0];
        vave.phi0  = a[1];
        vave.kappa = a[2];
        vave.dz    = a[3];
        vave.tanL  = a[4];
        vave.covMat = c;
        if(this.getSwimmer()!=null && !sv.straight) vave.rollBack(mv.rollBackAngle);
        vave.updateFromHelix();

        boolean stateOK = sv.setStateVecPosAtMeasSite(vave, mv.measurements.get(vave.k), this.getSwimmer()); 
        if(stateOK) {
            return vave;
        }
        else {
            return null;
        }
    }



}

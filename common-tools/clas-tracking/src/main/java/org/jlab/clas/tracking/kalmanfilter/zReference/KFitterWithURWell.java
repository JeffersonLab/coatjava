package org.jlab.clas.tracking.kalmanfilter.zReference;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.logging.Level;

import org.jlab.clas.clas.math.FastMath;
import org.jlab.clas.swimtools.Swim;
import org.jlab.clas.tracking.kalmanfilter.AKFitter;
import org.jlab.clas.tracking.kalmanfilter.AMeasVecs;
import org.jlab.clas.tracking.kalmanfilter.AStateVecs;
import org.jlab.clas.tracking.kalmanfilter.Surface;
import org.jlab.clas.tracking.kalmanfilter.AStateVecs.StateVec;
import org.jlab.clas.tracking.kalmanfilter.zReference.MeasVecs;
import org.jlab.clas.tracking.kalmanfilter.zReference.StateVecs;
import org.jlab.clas.tracking.utilities.RungeKuttaDoca;
import org.jlab.clas.tracking.utilities.MatrixOps.Libr;
import org.jlab.geom.prim.Point3D;
import org.jlab.jnp.matrix.*;
import org.jlab.clas.tracking.kalmanfilter.Type;

/**
 *
 * @author Tongtong Cao
 */
public class KFitterWithURWell extends AKFitter {
    
    private static final double[] dafAnnealingFactorsTB={64, 16, 4, 1};  
    private int dafAnnealingFactorsIndex = 0;
    private double dafAnnealingFactor = 1;
    
    private static final Logger LOGGER = Logger.getLogger(KFitter.class.getName());
    
    private static final double initialCMBlowupFactor = 70;

    private StateVecs sv = new StateVecs();
    private MeasVecs mv = new MeasVecs();
    private StateVec finalSmoothedStateVec = null;
    private StateVec finalTransportedStateVec = null;

    public StateVec finalStateVec = null;
    public StateVec initialStateVec = null;
    public List<StateVec> kfStateVecsAlongTrajectory;

    private int iterNum;

    private double chi2kf = 0;
    private double KFScale = 4;

    private int svzLength;

    public int ConvStatus = 1;

    private double Z[];

    private boolean stopIteration = false;

    private boolean TBT = false;

    Matrix first_inverse = new Matrix();
    Matrix addition = new Matrix();
    Matrix result = new Matrix();
    Matrix result_inv = new Matrix();
    Matrix adj = new Matrix();

    public KFitterWithURWell(boolean filter, int iterations, int dir, Swim swim, double Z[], Libr mo) {
        super(filter, iterations, dir, swim, mo);
        this.Z = Z;
    }

    public final void init(List<Surface> measSurfaces, StateVec initSV) {
        finalSmoothedStateVec = null;
        finalTransportedStateVec = null;
        this.NDF = -5;
        this.chi2 = Double.POSITIVE_INFINITY;
        this.numIter = 0;
        this.setFitFailed = false;
        mv.setMeasVecs(measSurfaces);
        for (int i = 0; i < mv.measurements.size(); i++) {
            if (mv.measurements.get(i).skip == false) {
                if(mv.measurements.get(i).surface.type == Type.LINEDOCA)
                    this.NDF += mv.measurements.get(i).surface.getNMeas();
                else if(mv.measurements.get(i).surface.type == Type.PLANEURWELL)
                    this.NDF += 2;
            }
        }

        sv.init(initSV);
        sv.Z = Z;
    }

    public final void initFromHB(List<Surface> measSurfaces, StateVec initSV, double beta) {
        finalSmoothedStateVec = null;
        finalTransportedStateVec = null;
        this.NDF = -5;
        this.chi2 = Double.POSITIVE_INFINITY;
        this.numIter = 0;
        this.setFitFailed = false;
        mv.setMeasVecs(measSurfaces);
        for (int i = 0; i < mv.measurements.size(); i++) {
            if (mv.measurements.get(i).skip == false) {
                if(mv.measurements.get(i).surface.type == Type.LINEDOCA)
                    this.NDF++;
                else if(mv.measurements.get(i).surface.type == Type.PLANEURWELL)
                    this.NDF += 2;
            }
        }

        sv.initFromHB(initSV, beta);
        sv.Z = Z;
        TBT = true;
    }

    public void runFitter() {
        this.chi2 = Double.POSITIVE_INFINITY;
        double initChi2 = Double.POSITIVE_INFINITY;
        // this.NDF = mv.ndf;
        this.svzLength = this.mv.measurements.size();

        int sector = this.mv.measurements.get(0).sector;

        if (TBT == true) {
            this.chi2kf = 0;
            // Get the input parameters
            for (int k = 0; k < svzLength - 1; k++) {
                sv.transport(sector, k, k + 1, this.sv.trackTrajT.get(k), mv, this.getSwimmer(), true);
            }
            this.calcFinalChisqDAF(sector, true);
            this.initialStateVec = sv.trackTrajT.get(svzLength - 1);
            this.finalStateVec = sv.trackTrajT.get(svzLength - 1);
            initChi2 = this.chi2;
            if (Double.isNaN(chi2)) {
                this.setFitFailed = true;
                return;
            }
        }

        for (int i = 1; i <= totNumIter; i++) {
            iterNum = i;
            this.chi2kf = 0;

            if (i > 1) {
                if (dafAnnealingFactorsIndex < dafAnnealingFactorsTB.length) {
                    dafAnnealingFactor = dafAnnealingFactorsTB[dafAnnealingFactorsIndex];
                    dafAnnealingFactorsIndex++;
                } else {
                    dafAnnealingFactor = 1;
                    dafAnnealingFactorsIndex++;
                }
                
                for (int k = svzLength - 1; k > 0; k--) {
                    boolean forward = false;
                    if (k >= 2) {

                        // Not backward transport and filter states for the last measurement layer
                        if (k == svzLength - 1) {
                            this.sv.transported(forward).put(k, this.sv.trackTrajF.get(k));
                            this.sv.filtered(forward).put(k, this.sv.trackTrajF.get(k));
                            if (!sv.transport(sector, k, k - 2, this.sv.trackTrajF.get(k), mv, this.getSwimmer(), forward)) {
                                this.stopIteration = true;
                                break;
                            }
                        } else {
                            if (!sv.transport(sector, k, k - 2, this.sv.trackTrajB.get(k), mv, this.getSwimmer(), forward)) {
                                this.stopIteration = true;
                                break;
                            }
                        }

                        if(TBT){
                            if (!this.filter(k - 2, forward, dafAnnealingFactor)) {
                                this.stopIteration = true;
                                break;
                            }
                        }
                        else{
                            if (!this.filter(k - 2, forward)) {
                                this.stopIteration = true;
                                break;
                            }
                        }

                        if (!sv.transport(sector, k - 2, k - 1, this.sv.trackTrajB.get(k - 2), mv, this.getSwimmer(), forward)) {
                            this.stopIteration = true;
                            break;
                        }

                        if(TBT){
                            if (!this.filter(k - 1, forward, dafAnnealingFactor)) {
                                this.stopIteration = true;
                                break;
                            }
                        }
                        else{
                            if (!this.filter(k - 1, forward)) {
                                this.stopIteration = true;
                                break;
                            }
                        }
                    } else {
                        if (!sv.transport(sector, 1, 0, this.sv.trackTrajB.get(1), mv, this.getSwimmer(), forward)) {
                            this.stopIteration = true;
                            break;
                        }

                        if(TBT){
                            if (!this.filter(0, forward, dafAnnealingFactor)) {
                                this.stopIteration = true;
                                break;
                            }
                        }
                        else{
                            if (!this.filter(0, forward)) {
                                this.stopIteration = true;
                                break;
                            }
                        }
                    }
                }
            }

            if (this.stopIteration) {
                break;
            }
            
            if (dafAnnealingFactorsIndex < dafAnnealingFactorsTB.length) {
                dafAnnealingFactor = dafAnnealingFactorsTB[dafAnnealingFactorsIndex];
                dafAnnealingFactorsIndex++;
            } else {
                dafAnnealingFactor = 1;
                dafAnnealingFactorsIndex++;
            }

            for (int k = 0; k < svzLength - 1; k++) {
                boolean forward = true;

                if (iterNum == 1 && (k == 0)) {
                    if (TBT == true) {
                        this.sv.transported(true).put(0, this.sv.transported(false).get(0)); // For TBT, calcFinalChisq() is called previously.				
                    }
                }

                if (k == 0) {
                    if (i == 1) {
                        sv.filtered(forward).put(0, this.sv.trackTrajT.get(0));
                        if (!this.sv.transport(sector, 0, 1, this.sv.trackTrajT.get(0), mv, this.getSwimmer(), forward)) {
                            this.stopIteration = true;
                            break;
                        }
                    } else {
                        double c00 = this.sv.trackTrajB.get(0).CM.get(0, 0);
                        double c11 = this.sv.trackTrajB.get(0).CM.get(1, 1);
                        double c22 = this.sv.trackTrajB.get(0).CM.get(2, 2);
                        double c33 = this.sv.trackTrajB.get(0).CM.get(3, 3);
                        double c44 = this.sv.trackTrajB.get(0).CM.get(4, 4);
                        Matrix newCM = new Matrix();
                        newCM.set(c00*initialCMBlowupFactor, 0, 0, 0, 0,
                                    0, c11*initialCMBlowupFactor, 0, 0, 0,
                                    0, 0, c22*initialCMBlowupFactor, 0, 0,
                                    0, 0, 0, c33*initialCMBlowupFactor, 0,
                                    0, 0, 0, 0, c44*initialCMBlowupFactor);
                        this.sv.trackTrajB.get(0).CM = newCM;
                        
                        this.sv.transported(forward).put(0, this.sv.trackTrajB.get(0));
                        this.sv.filtered(forward).put(0, this.sv.trackTrajB.get(0));
                        
                        if (!this.sv.transport(sector, 0, 1, this.sv.trackTrajB.get(0), mv, this.getSwimmer(), forward)) {
                            this.stopIteration = true;
                            break;
                        }
                    }
                } else {
                    if (!this.sv.transport(sector, k, k + 1, this.sv.trackTrajF.get(k), mv, this.getSwimmer(), forward)) {
                        this.stopIteration = true;
                        break;
                    }

                }
                if(TBT){
                    if (!this.filter(k + 1, forward, dafAnnealingFactor)) {
                        this.stopIteration = true;
                        break;
                    }
                }
                else{
                    if (!this.filter(k + 1, forward)) {
                        this.stopIteration = true;
                        break;
                    }
                }
            }

            if (this.stopIteration) {
                break;
            }

            if (i > 1) {
                if (this.setFitFailed == true) {
                    i = totNumIter;
                }
                if (this.setFitFailed == false) {
                    if (this.finalStateVec != null) {
                        if (!TBT) {
                            if (Math.abs(sv.trackTrajF.get(svzLength - 1).Q - this.finalStateVec.Q) < 1.6e-3
                                    && Math.abs(sv.trackTrajF.get(svzLength - 1).x - this.finalStateVec.x) < 1.2e-2
                                    && Math.abs(sv.trackTrajF.get(svzLength - 1).y - this.finalStateVec.y) < 1.4e-1
                                    && Math.abs(sv.trackTrajF.get(svzLength - 1).tx - this.finalStateVec.tx) < 2.5e-4
                                    && Math.abs(sv.trackTrajF.get(svzLength - 1).ty - this.finalStateVec.ty) < 1.0e-3) {
                                i = totNumIter;
                            }
                        } else {
                            if (Math.abs(sv.trackTrajF.get(svzLength - 1).Q - this.finalStateVec.Q) < 1.1e-5
                                    && Math.abs(sv.trackTrajF.get(svzLength - 1).x - this.finalStateVec.x) < 5.5e-5
                                    && Math.abs(sv.trackTrajF.get(svzLength - 1).y - this.finalStateVec.y) < 8.0e-4
                                    && Math.abs(sv.trackTrajF.get(svzLength - 1).tx - this.finalStateVec.tx) < 2.1e-6
                                    && Math.abs(sv.trackTrajF.get(svzLength - 1).ty - this.finalStateVec.ty) < 3.5e-6) {
                                i = totNumIter;
                            }
                        }
                    }
                    this.finalStateVec = sv.trackTrajF.get(svzLength - 1);

                } else {
                    this.ConvStatus = 1; // Should be 0???
                }
            }

        }

        if (totNumIter == 1) {
            if (this.setFitFailed == false && this.stopIteration == false) {
                this.finalStateVec = sv.trackTrajF.get(svzLength - 1);
            }
        }

        if(TBT) this.calcFinalChisqDAF(sector);
        else this.calcFinalChisq(sector);

        if (Double.isNaN(chi2)) {
            this.setFitFailed = true;
        }

/*        
        if (TBT == true) {
            if (chi2 > initChi2) { // fit failed                 
                this.finalStateVec = this.initialStateVec;
                sv.trackTrajT.put(svzLength - 1, this.initialStateVec);
                this.calcFinalChisqDAF(sector, true);   
            }
        }
*/

    }
    
    private boolean filter(int k, boolean forward, double annealingFactor) {
        StateVec sVec = sv.transported(forward).get(k);
        org.jlab.clas.tracking.kalmanfilter.AMeasVecs.MeasVec mVec = mv.measurements.get(k);

        if (Double.isNaN(sVec.x) || Double.isNaN(sVec.y)
                || Double.isNaN(sVec.tx) || Double.isNaN(sVec.ty)
                || Double.isNaN(sVec.Q)) {
            this.setFitFailed = true;
            return false;
        }
        if (sVec != null && sVec.CM != null
                && k < mv.measurements.size() && mVec.skip == false) {
            
            if (mVec.surface.type == Type.LINEDOCA) {
                
                double signMeas = 1;
                double sign = 1;

                double c2 = 0;
                double x_filt = 0;
                double y_filt = 0;
                double tx_filt = 0;
                double ty_filt = 0;
                double Q_filt = 0;
                Matrix cMat = new Matrix();

                double updatedWeights_singleHit = 1;
                double[] updatedWeights_doubleHits = {0.5, 0.5};

                if (mVec.surface.doca[1] == -99) {
                    StateVec sVecPreviousFiltered = sv.filtered(!forward).get(k);
                    double daf_weight = 1;
                    if (sVecPreviousFiltered != null) {
                        daf_weight = sVecPreviousFiltered.getWeightDAF_singleHit();
                    }
                    double var = mVec.surface.unc[0] * KFScale;
                    DAFilter daf = new DAFilter(mVec.surface.doca[0], var, daf_weight);
                    daf.calc_effectiveDoca_singleHit();

                    double effectiveDoca = daf.get_EffectiveDoca();
                    double effectiveVar = daf.get_EffectiveVar();


                    double[] K = new double[5];
                    double V = effectiveVar;
                    double[] H = mv.H(sVec.x, sVec.y, mVec.surface.z, mVec.surface.wireLine[0]);
                    Matrix CaInv = this.filterCovMat(H, sVec.CM, V);
                    if (CaInv != null) {
                        Matrix5x5.copy(CaInv, cMat);
                    } else {
                        return false;
                    }

                    for (int j = 0; j < 5; j++) {
                        // the gain matrix
                        K[j] = (H[0] * cMat.get(j, 0)
                                + H[1] * cMat.get(j, 1)) / V;
                    }

                    Point3D point = new Point3D(sVec.x, sVec.y, mVec.surface.z);
                    double h = mv.hDoca(point, mVec.surface.wireLine[0]);


                    if (Math.abs(effectiveDoca) < 0.5) {
                        signMeas = Math.signum(h);
                        sign = Math.signum(h);
                    } else {
                        signMeas = Math.signum(effectiveDoca);
                        sign = Math.signum(h);
                    }

                    c2 = ((signMeas * Math.abs(effectiveDoca) - sign * Math.abs(h))
                            * (signMeas * Math.abs(effectiveDoca) - sign * Math.abs(h)) / V);

                    x_filt = sVec.x
                            + K[0] * (signMeas * Math.abs(effectiveDoca) - sign * Math.abs(h));
                    y_filt = sVec.y
                            + K[1] * (signMeas * Math.abs(effectiveDoca) - sign * Math.abs(h));
                    tx_filt = sVec.tx
                            + K[2] * (signMeas * Math.abs(effectiveDoca) - sign * Math.abs(h));
                    ty_filt = sVec.ty
                            + K[3] * (signMeas * Math.abs(effectiveDoca) - sign * Math.abs(h));
                    Q_filt = sVec.Q
                            + K[4] * (signMeas * Math.abs(effectiveDoca) - sign * Math.abs(h));

                    Point3D pointFiltered = new Point3D(x_filt, y_filt, mVec.surface.z);
                    double h0 = mv.hDoca(pointFiltered, mVec.surface.wireLine[0]);
                    if (Math.abs(effectiveDoca) < 0.5) {
                        signMeas = Math.signum(h0);
                        sign = Math.signum(h0);
                    } else {
                        signMeas = Math.signum(effectiveDoca);
                        sign = Math.signum(h0);
                    }
                    double residual = signMeas * Math.abs(effectiveDoca) - sign * Math.abs(h0);
                    updatedWeights_singleHit = daf.calc_updatedWeight_singleHit(residual, annealingFactor);                                                 
                }
                else{                                                       
                    StateVec sVecPreviousFiltered = sv.filtered(!forward).get(k);
                    double[] daf_weights = {0.5, 0.5};
                    if (sVecPreviousFiltered != null) {
                        daf_weights = sVecPreviousFiltered.getWeightDAF_doubleHits();
                    }
                    double[] vars = {mVec.surface.unc[0] * KFScale, mVec.surface.unc[1] * KFScale};
                    DAFilter daf = new DAFilter(mVec.surface.doca, vars, daf_weights, mVec.surface.wireLine);
                    daf.calc_effectiveDoca_doubleHits();

                    double effectiveDoca = daf.get_EffectiveDoca();
                    double effectiveVar = daf.get_EffectiveVar();
                    int indexReferenceWire = daf.get_IndexReferenceWire();

                    double[] K = new double[5];
                    double V = effectiveVar;
                    double[] H = mv.H(sVec.x, sVec.y, mVec.surface.z, mVec.surface.wireLine[indexReferenceWire]);
                    Matrix CaInv = this.filterCovMat(H, sVec.CM, V);
                    if (CaInv != null) {
                        Matrix5x5.copy(CaInv, cMat);
                    } else {
                        return false;
                    }

                    for (int j = 0; j < 5; j++) {
                        // the gain matrix
                        K[j] = (H[0] * cMat.get(j, 0)
                                + H[1] * cMat.get(j, 1)) / V;
                    }

                    Point3D point = new Point3D(sVec.x, sVec.y, mVec.surface.z);
                    double h = mv.hDoca(point, mVec.surface.wireLine[indexReferenceWire]);

                    signMeas = Math.signum(effectiveDoca);
                    sign = Math.signum(h);

                    c2 = ((signMeas * Math.abs(effectiveDoca) - sign * Math.abs(h))
                            * (signMeas * Math.abs(effectiveDoca) - sign * Math.abs(h)) / V);

                    x_filt = sVec.x
                            + K[0] * (signMeas * Math.abs(effectiveDoca) - sign * Math.abs(h));
                    y_filt = sVec.y
                            + K[1] * (signMeas * Math.abs(effectiveDoca) - sign * Math.abs(h));
                    tx_filt = sVec.tx
                            + K[2] * (signMeas * Math.abs(effectiveDoca) - sign * Math.abs(h));
                    ty_filt = sVec.ty
                            + K[3] * (signMeas * Math.abs(effectiveDoca) - sign * Math.abs(h));
                    Q_filt = sVec.Q
                            + K[4] * (signMeas * Math.abs(effectiveDoca) - sign * Math.abs(h));

                    Point3D pointFiltered = new Point3D(x_filt, y_filt, mVec.surface.z);
                    double h0 = mv.hDoca(pointFiltered, mVec.surface.wireLine[0]);
                    double h1 = mv.hDoca(pointFiltered, mVec.surface.wireLine[1]);
                    double[] residuals = {mVec.surface.doca[0] - h0, mVec.surface.doca[1] - h1};
                    updatedWeights_doubleHits = daf.calc_updatedWeights_doubleHits(residuals, annealingFactor);                               
                }

                chi2kf += c2;
                if (filterOn) {
                    StateVec filteredVec = sv.new StateVec(k);
                    filteredVec.x = x_filt;
                    filteredVec.y = y_filt;
                    filteredVec.tx = tx_filt;
                    filteredVec.ty = ty_filt;
                    filteredVec.Q = Q_filt;
                    filteredVec.z = sVec.z;
                    filteredVec.B = sVec.B;
                    filteredVec.deltaPath = sVec.deltaPath;

                    filteredVec.CM = cMat;
                    filteredVec.setWeightDAF_singleHit(updatedWeights_singleHit);
                    filteredVec.setWeightDAF_doubleHits(updatedWeights_doubleHits);

                    sv.filtered(forward).put(k, filteredVec);
                } else {
                    return false;
                }
            }
            
            else if (mVec.surface.type == Type.PLANEURWELL) {
                

                double V[][] = {{mVec.surface.x_err * mVec.surface.x_err, 0}, {0, mVec.surface.y_err * mVec.surface.y_err}};
                
                if(V[0][0] == 0 || V[1][1] == 0 )
                    LOGGER.log(Level.SEVERE, "Resolution for URWell is 0.");

                Matrix cMat = this.filterCovMatURWell(sVec.CM, V);
                if (cMat == null) return false;   

                double[][] K = KURWell(cMat, V);
                double [] res = {mVec.surface.x - sVec.x, mVec.surface.y - sVec.y};
                
                double filt[] = new double[5];
                for(int i = 0; i < 5; i ++){
                    for(int j = 0; j < 2; j++){
                        filt[i] += K[i][j]*res[j];
                    }
                }
                
                double c2 = res[0]*res[0]/V[0][0] + res[1]/V[1][1];
                
                //System.out.println("mVec.surface.x: " + mVec.surface.x + ";  sVec.x: " + sVec.x + " " +  "mVec.surface.y: " + mVec.surface.y + ";  sVec.y: " + sVec.y + "res[0]: " + res[0] + "; res[1]: " + res[1]);
                
                chi2kf += c2;
                if (filterOn) {
                    StateVec filteredVec = sv.new StateVec(k);
                    filteredVec.x = sVec.x + filt[0];
                    filteredVec.y = sVec.y + filt[1];
                    filteredVec.tx = sVec.tx + filt[2];
                    filteredVec.ty = sVec.ty + filt[3];
                    filteredVec.Q = sVec.Q +filt[4];
                    filteredVec.z = sVec.z;
                    filteredVec.B = sVec.B;
                    filteredVec.deltaPath = sVec.deltaPath;

                    filteredVec.CM = cMat;

                    sv.filtered(forward).put(k, filteredVec);
                    //sv.filtered(forward).put(k, sVec);
                    
                } else {
                    return false;
                }                                
            }

            return true;
        } else {
            return false;
        }
    }

    private boolean filter(int k, boolean forward) {
        StateVec sVec = sv.transported(forward).get(k);
        org.jlab.clas.tracking.kalmanfilter.AMeasVecs.MeasVec mVec = mv.measurements.get(k);

        if (Double.isNaN(sVec.x) || Double.isNaN(sVec.y)
                || Double.isNaN(sVec.tx) || Double.isNaN(sVec.ty)
                || Double.isNaN(sVec.Q)) {
            this.setFitFailed = true;
            return false;
        }
        if (sVec != null && sVec.CM != null
                && k < mv.measurements.size() && mVec.skip == false) {

            if (mVec.surface.type == Type.LINEDOCA) {

                double[] K = new double[5];
                double V = mVec.surface.unc[0] * KFScale;
                double[] H = mv.H(sVec.x, sVec.y, mVec.surface.z, mVec.surface.wireLine[0]);
                Matrix CaInv = this.filterCovMat(H, sVec.CM, V);
                Matrix cMat = new Matrix();
                if (CaInv != null) {
                    Matrix5x5.copy(CaInv, cMat);
                } else {
                    return false;
                }

                for (int j = 0; j < 5; j++) {
                    // the gain matrix
                    K[j] = (H[0] * cMat.get(j, 0)
                            + H[1] * cMat.get(j, 1)) / V;
                }

                Point3D point = new Point3D(sVec.x, sVec.y, mVec.surface.z);
                double h = mv.hDoca(point, mVec.surface.wireLine[0]);

                double signMeas = 1;
                double sign = 1;
                if (mVec.surface.doca[1] != -99
                        || !(Math.abs(mVec.surface.doca[0]) < 0.5
                        && mVec.surface.doca[1] == -99)) { // use LR only for double hits && large
                    // enough docas
                    signMeas = Math.signum(mVec.surface.doca[0]);
                    sign = Math.signum(h);
                } else {
                    signMeas = Math.signum(h);
                    sign = Math.signum(h);
                }

                double c2 = ((signMeas * Math.abs(mVec.surface.doca[0]) - sign * Math.abs(h))
                        * (signMeas * Math.abs(mVec.surface.doca[0]) - sign * Math.abs(h)) / V);

                double x_filt = sVec.x
                        + K[0] * (signMeas * Math.abs(mVec.surface.doca[0]) - sign * Math.abs(h));
                double y_filt = sVec.y
                        + K[1] * (signMeas * Math.abs(mVec.surface.doca[0]) - sign * Math.abs(h));
                double tx_filt = sVec.tx
                        + K[2] * (signMeas * Math.abs(mVec.surface.doca[0]) - sign * Math.abs(h));
                double ty_filt = sVec.ty
                        + K[3] * (signMeas * Math.abs(mVec.surface.doca[0]) - sign * Math.abs(h));
                double Q_filt = sVec.Q
                        + K[4] * (signMeas * Math.abs(mVec.surface.doca[0]) - sign * Math.abs(h));

                // USE THE DOUBLE HIT
                if (mVec.surface.doca[1] != -99) {
                    // now filter using the other Hit
                    V = mVec.surface.unc[1] * KFScale;
                    H = mv.H(x_filt, y_filt, mVec.surface.z,
                            mVec.surface.wireLine[1]);
                    CaInv = this.filterCovMat(H, cMat, V);
                    if (CaInv != null) {
                        for (int i = 0; i < 5; i++) {
                            Matrix5x5.copy(CaInv, cMat);
                        }
                    } else {
                        return false;
                    }
                    for (int j = 0; j < 5; j++) {
                        // the gain matrix
                        K[j] = (H[0] * cMat.get(j, 0)
                                + H[1] * cMat.get(j, 1)) / V;
                    }

                    Point3D point2 = new Point3D(x_filt, y_filt, mVec.surface.z);

                    h = mv.hDoca(point2, mVec.surface.wireLine[1]);

                    signMeas = Math.signum(mVec.surface.doca[1]);
                    sign = Math.signum(h);

                    x_filt += K[0] * (signMeas * Math.abs(mVec.surface.doca[1]) - sign * Math.abs(h));
                    y_filt += K[1] * (signMeas * Math.abs(mVec.surface.doca[1]) - sign * Math.abs(h));
                    tx_filt += K[2] * (signMeas * Math.abs(mVec.surface.doca[1]) - sign * Math.abs(h));
                    ty_filt += K[3] * (signMeas * Math.abs(mVec.surface.doca[1]) - sign * Math.abs(h));
                    Q_filt += K[4] * (signMeas * Math.abs(mVec.surface.doca[1]) - sign * Math.abs(h));

                    c2 += ((signMeas * Math.abs(mVec.surface.doca[1]) - sign * Math.abs(h))
                            * (signMeas * Math.abs(mVec.surface.doca[1]) - sign * Math.abs(h)) / V);
                }

                chi2kf += c2;
                if (filterOn) {
                    StateVec filteredVec = sv.new StateVec(k);
                    filteredVec.x = x_filt;
                    filteredVec.y = y_filt;
                    filteredVec.tx = tx_filt;
                    filteredVec.ty = ty_filt;
                    filteredVec.Q = Q_filt;
                    filteredVec.z = sVec.z;
                    filteredVec.B = sVec.B;
                    filteredVec.deltaPath = sVec.deltaPath;

                    filteredVec.CM = cMat;

                    sv.filtered(forward).put(k, filteredVec);
                } else {
                    return false;
                }
            }
            
            else if (mVec.surface.type == Type.PLANEURWELL) {
                

                double V[][] = {{mVec.surface.x_err * mVec.surface.x_err, 0}, {0, mVec.surface.y_err * mVec.surface.y_err}};
                
                if(V[0][0] == 0 || V[1][1] == 0 )
                    LOGGER.log(Level.SEVERE, "Resolution for URWell is 0.");

                Matrix cMat = this.filterCovMatURWell(sVec.CM, V);
                if (cMat == null) return false;   

                double[][] K = KURWell(cMat, V);
                double [] res = {mVec.surface.x - sVec.x, mVec.surface.y - sVec.y};
                
                double filt[] = new double[5];
                for(int i = 0; i < 5; i ++){
                    for(int j = 0; j < 2; j++){
                        filt[i] += K[i][j]*res[j];
                    }
                }
                
                double c2 = res[0]*res[0]/V[0][0] + res[1]/V[1][1];
                
                //System.out.println("mVec.surface.x: " + mVec.surface.x + ";  sVec.x: " + sVec.x + " " +  "mVec.surface.y: " + mVec.surface.y + ";  sVec.y: " + sVec.y + "res[0]: " + res[0] + "; res[1]: " + res[1]);
                
                chi2kf += c2;
                if (filterOn) {
                    StateVec filteredVec = sv.new StateVec(k);
                    filteredVec.x = sVec.x + filt[0];
                    filteredVec.y = sVec.y + filt[1];
                    filteredVec.tx = sVec.tx + filt[2];
                    filteredVec.ty = sVec.ty + filt[3];
                    filteredVec.Q = sVec.Q +filt[4];
                    filteredVec.z = sVec.z;
                    filteredVec.B = sVec.B;
                    filteredVec.deltaPath = sVec.deltaPath;

                    filteredVec.CM = cMat;

                    sv.filtered(forward).put(k, filteredVec);
                    //sv.filtered(forward).put(k, sVec);
                    
                } else {
                    return false;
                }                                
            }
            
            return true;

        } else {
            return false;
        }
    }
    
    public Matrix filterCovMatURWell(Matrix Ci, double[][] V) {
        double det = Matrix5x5.inverse(Ci, first_inverse, adj);
        if (Math.abs(det) < 1.e-60) {
            return null;
        }                 
        
        addition.set(
                1 / V[0][0], 0, 0, 0, 0,
                0, 1 / V[1][1], 0, 0, 0,
                0, 0, 0, 0, 0,
                0, 0, 0, 0, 0,
                0, 0, 0, 0, 0);

        Matrix5x5.add(first_inverse, addition, result);
        double det2 = Matrix5x5.inverse(result, result_inv, adj);
        if (Math.abs(det2) < 1.e-60) {
            return null;
        }

        return result_inv;        
    }
    
    public double[][] KURWell(Matrix C, double[][] V) {
        double K[][] = new double[5][2];
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 2; j++) {          
                K[i][j] = C.get(i, j) / V[j][j];
            }
        }
        return K;
    }
 
    public Matrix filterCovMat(double[] H, Matrix Ci, double V) {

        double det = Matrix5x5.inverse(Ci, first_inverse, adj);
        if (Math.abs(det) < 1.e-60) {
            return null;
        }

        addition.set(
                H[0] * H[0] / V, H[0] * H[1] / V, 0, 0, 0,
                H[0] * H[1] / V, H[1] * H[1] / V, 0, 0, 0,
                0, 0, 0, 0, 0,
                0, 0, 0, 0, 0,
                0, 0, 0, 0, 0);

        Matrix5x5.add(first_inverse, addition, result);
        double det2 = Matrix5x5.inverse(result, result_inv, adj);
        if (Math.abs(det2) < 1.e-60) {
            return null;
        }

        return result_inv;
    }

    private void calcFinalChisq(int sector) {
        calcFinalChisq(sector, false);
    }

    private void calcFinalChisq(int sector, boolean nofilter) {
        int k = svzLength - 1;
        this.chi2 = 0;
        double path = 0;
        double[] nRj = new double[4];

        StateVec sVec;

        // To be changed: to match wit the old package, we make the following codes. Could be changed when other codes for application of calcFinalChisq are changed.
        if (nofilter || (sv.trackTrajF.get(k) == null)) {
            sVec = sv.trackTrajT.get(k);
        } else {
            sVec = sv.trackTrajF.get(k);
        }

        kfStateVecsAlongTrajectory = new ArrayList<>();
        if (sVec != null && sVec.CM != null) {

            boolean forward = false;
            sv.transport(sector, k, 0, sVec, mv, this.getSwimmer(), forward);
            StateVec svc = sv.transported(forward).get(0);
            path += svc.deltaPath;
            svc.setPathLength(path);
            
            if (mv.measurements.get(0).surface.type == Type.PLANEURWELL) {
                kfStateVecsAlongTrajectory.add(svc);
                
                double x_err = mv.measurements.get(0).surface.x_err;
                double y_err = mv.measurements.get(0).surface.y_err;

                double x_res = mv.measurements.get(0).surface.x - svc.x;
                double y_res = mv.measurements.get(0).surface.y - svc.y;

                chi2 += (x_res*x_res) / (x_err*x_err);
                chi2 += (y_res*y_res) / (y_err*y_err);    

                nRj[mv.measurements.get(0).region] += x_res * x_res / (x_err*x_err);
                nRj[mv.measurements.get(0).region] += y_res * y_res / (y_err*y_err);
            }
            else if(mv.measurements.get(0).surface.type == Type.LINEDOCA) {
                double V0 = mv.measurements.get(0).surface.unc[0];

                Point3D point = new Point3D(svc.x, svc.y, mv.measurements.get(0).surface.z);
                double h0 = mv.hDoca(point, mv.measurements.get(0).surface.wireLine[0]);

                svc.setProjector(mv.measurements.get(0).surface.wireLine[0].origin().x());
                svc.setProjectorDoca(h0);
                kfStateVecsAlongTrajectory.add(svc);
                double res = (mv.measurements.get(0).surface.doca[0] - h0);
                chi2 += (mv.measurements.get(0).surface.doca[0] - h0) * (mv.measurements.get(0).surface.doca[0] - h0) / V0;
                nRj[mv.measurements.get(0).region - 1] += res * res / mv.measurements.get(0).error;
                //USE THE DOUBLE HIT
                if (mv.measurements.get(0).surface.doca[1] != -99) {
                    V0 = mv.measurements.get(0).surface.unc[1];
                    h0 = mv.hDoca(point, mv.measurements.get(0).surface.wireLine[1]);
                    res = (mv.measurements.get(0).surface.doca[1] - h0);
                    chi2 += (mv.measurements.get(0).surface.doca[1] - h0) * (mv.measurements.get(0).surface.doca[1] - h0) / V0;
                    nRj[mv.measurements.get(0).region - 1] += res * res / mv.measurements.get(0).error;

                    StateVec svc2 = sv.new StateVec(svc);
                    svc2.setProjector(mv.measurements.get(0).surface.wireLine[1].origin().x());
                    svc2.setProjectorDoca(h0);
                    kfStateVecsAlongTrajectory.add(svc2);
                }
            }            

            forward = true;
            for (int k1 = 0; k1 < k; k1++) {
                if (k1 == 0) {
                    sv.transport(sector, k1, k1 + 1, svc, mv, this.getSwimmer(), forward);
                } else {
                    sv.transport(sector, k1, k1 + 1, sv.transported(forward).get(k1), mv, this.getSwimmer(), forward);
                }

                double V = mv.measurements.get(k1 + 1).surface.unc[0];

                Point3D point = new Point3D(sv.transported(forward).get(k1 + 1).x, sv.transported(forward).get(k1 + 1).y, mv.measurements.get(k1 + 1).surface.z);

                double h = mv.hDoca(point, mv.measurements.get(k1 + 1).surface.wireLine[0]);
                svc = sv.transported(forward).get(k1 + 1);
                path += svc.deltaPath;
                svc.setPathLength(path);
                svc.setProjector(mv.measurements.get(k1 + 1).surface.wireLine[0].origin().x());
                svc.setProjectorDoca(h);
                kfStateVecsAlongTrajectory.add(svc);
                double res = (mv.measurements.get(k1 + 1).surface.doca[0] - h);
                chi2 += (mv.measurements.get(k1 + 1).surface.doca[0] - h) * (mv.measurements.get(k1 + 1).surface.doca[0] - h) / V;
                nRj[mv.measurements.get(k1 + 1).region - 1] += res * res / V;
                //USE THE DOUBLE HIT
                if (mv.measurements.get(k1 + 1).surface.doca[1] != -99) {
                    V = mv.measurements.get(k1 + 1).surface.unc[1];
                    h = mv.hDoca(point, mv.measurements.get(k1 + 1).surface.wireLine[1]);
                    res = (mv.measurements.get(k1 + 1).surface.doca[1] - h);
                    chi2 += (mv.measurements.get(k1 + 1).surface.doca[1] - h) * (mv.measurements.get(k1 + 1).surface.doca[1] - h) / V;
                    nRj[mv.measurements.get(k1 + 1).region] += res * res / V;

                    StateVec svc2 = sv.new StateVec(svc);
                    svc2.setProjector(mv.measurements.get(k1 + 1).surface.wireLine[1].origin().x());
                    svc2.setProjectorDoca(h);
                    kfStateVecsAlongTrajectory.add(svc2);
                }
            }
        }
    }
    
        private void calcFinalChisqDAF(int sector) {
        calcFinalChisqDAF(sector, false);
    }
    
    private void calcFinalChisqDAF(int sector, boolean nofilter) {
        int k = svzLength - 1;
        this.chi2 = 0;
        double path = 0;

        StateVec sVec;

        // To be changed: to match wit the old package, we make the following codes. Could be changed when other codes for application of calcFinalChisq are changed.
        if (nofilter || (sv.trackTrajF.get(k) == null)) {
            sVec = sv.trackTrajT.get(k);
        } else {
            sVec = sv.trackTrajF.get(k);
        }

        kfStateVecsAlongTrajectory = new ArrayList<>();
        if (sVec != null && sVec.CM != null) {
                        
            boolean forward = false;
            sv.transport(sector, k, 0, sVec, mv, this.getSwimmer(), forward);

            StateVec svc = sv.transported(forward).get(0);
            path += svc.deltaPath;
            svc.setPathLength(path);
            
            if(mv.measurements.get(0).surface.type == Type.PLANEURWELL){
                double x_err = mv.measurements.get(0).surface.x_err;
                double y_err = mv.measurements.get(0).surface.y_err;

                double x_res = mv.measurements.get(0).surface.x - svc.x;
                double y_res = mv.measurements.get(0).surface.y - svc.y;

                chi2 += (x_res*x_res) / (x_err*x_err);
                chi2 += (y_res*y_res) / (y_err*y_err);    
            }
            else if(mv.measurements.get(0).surface.type == Type.LINEDOCA){                          
                Point3D point = new Point3D(svc.x, svc.y, mv.measurements.get(0).surface.z);
                if(mv.measurements.get(0).surface.doca[1] == -99) {
                    StateVec sVecPreviousFiltered = sv.filtered(true).get(0);
                    double daf_weight = 1;
                    if (sVecPreviousFiltered != null) {
                        daf_weight = sVecPreviousFiltered.getWeightDAF_singleHit();
                    }
                    double V0 = mv.measurements.get(0).surface.unc[0];
                    DAFilter daf = new DAFilter(mv.measurements.get(0).surface.doca[0], V0, daf_weight);
                    daf.calc_effectiveDoca_singleHit();

                    double effectiveDoca = daf.get_EffectiveDoca();
                    double effectiveVar = daf.get_EffectiveVar();

                    double h = mv.hDoca(point, mv.measurements.get(0).surface.wireLine[0]);
                    double res = (effectiveDoca - h);
                    chi2 += res*res / effectiveVar;                               

                    svc.setProjectorDoca(h); 
                    svc.setProjector(mv.measurements.get(0).surface.wireLine[0].origin().x());
                    kfStateVecsAlongTrajectory.add(svc);
                }
                else{
                    StateVec sVecPreviousFiltered = sv.filtered(true).get(0);
                    double[] daf_weights = {0.5, 0.5};
                    if (sVecPreviousFiltered != null) {
                        daf_weights = sVecPreviousFiltered.getWeightDAF_doubleHits();
                    }
                    double[] vars = {mv.measurements.get(0).surface.unc[0], mv.measurements.get(0).surface.unc[1]};
                    DAFilter daf = new DAFilter(mv.measurements.get(0).surface.doca, vars, daf_weights, mv.measurements.get(0).surface.wireLine);
                    daf.calc_effectiveDoca_doubleHits();

                    double effectiveDoca = daf.get_EffectiveDoca();
                    double effectiveVar = daf.get_EffectiveVar();
                    int indexReferenceWire = daf.get_IndexReferenceWire(); 

                    double h = mv.hDoca(point, mv.measurements.get(0).surface.wireLine[indexReferenceWire]);
                    double res = (effectiveDoca - h);
                    chi2 += res*res / effectiveVar;

                    if(indexReferenceWire == 0) svc.setProjectorDoca(h); 
                    else svc.setProjectorDoca(-999); 
                    svc.setProjector(mv.measurements.get(0).surface.wireLine[0].origin().x());   
                    kfStateVecsAlongTrajectory.add(svc);

                    StateVec svc2 = sv.new StateVec(svc);
                    if(indexReferenceWire == 1) svc2.setProjectorDoca(h); 
                    else svc2.setProjectorDoca(-999); 
                    svc2.setProjector(mv.measurements.get(0).surface.wireLine[1].origin().x());
                    kfStateVecsAlongTrajectory.add(svc2);                  
                }
            }

            forward = true;
            for (int k1 = 0; k1 < k; k1++) {
                if (k1 == 0) {
                    sv.transport(sector, k1, k1 + 1, svc, mv, this.getSwimmer(), forward);
                } else {
                    sv.transport(sector, k1, k1 + 1, sv.transported(forward).get(k1), mv, this.getSwimmer(), forward);
                }
                
                svc = sv.transported(forward).get(k1 + 1);
                path += svc.deltaPath;
                svc.setPathLength(path);
                
                Point3D point = new Point3D(sv.transported(forward).get(k1 + 1).x, sv.transported(forward).get(k1 + 1).y, mv.measurements.get(k1 + 1).surface.z);
                if(mv.measurements.get(k1 + 1).surface.doca[1] == -99) {
                    StateVec sVecPreviousFiltered = sv.filtered(true).get(k1 + 1);
                    double daf_weight = 1;
                    if (sVecPreviousFiltered != null) {
                        daf_weight = sVecPreviousFiltered.getWeightDAF_singleHit();
                    }
                    double V0 = mv.measurements.get(k1 + 1).surface.unc[0];
                    DAFilter daf = new DAFilter(mv.measurements.get(k1 + 1).surface.doca[0], V0, daf_weight);
                    daf.calc_effectiveDoca_singleHit();

                    double effectiveDoca = daf.get_EffectiveDoca();
                    double effectiveVar = daf.get_EffectiveVar();

                    double h = mv.hDoca(point, mv.measurements.get(k1 + 1).surface.wireLine[0]);
                    double res = (effectiveDoca - h);
                    chi2 += res*res / effectiveVar;

                    svc.setProjectorDoca(h);  
                    svc.setProjector(mv.measurements.get(k1 + 1).surface.wireLine[0].origin().x());
                    kfStateVecsAlongTrajectory.add(svc);                                            
                }
                else{
                    StateVec sVecPreviousFiltered = sv.filtered(true).get(k1 + 1);
                    double[] daf_weights = {0.5, 0.5};
                    if (sVecPreviousFiltered != null) {
                        daf_weights = sVecPreviousFiltered.getWeightDAF_doubleHits();
                    }
                    double[] vars = {mv.measurements.get(k1 + 1).surface.unc[0], mv.measurements.get(k1 + 1).surface.unc[1]};
                    DAFilter daf = new DAFilter(mv.measurements.get(k1 + 1).surface.doca, vars, daf_weights, mv.measurements.get(k1 + 1).surface.wireLine);
                    daf.calc_effectiveDoca_doubleHits();

                    double effectiveDoca = daf.get_EffectiveDoca();
                    double effectiveVar = daf.get_EffectiveVar();
                    int indexReferenceWire = daf.get_IndexReferenceWire(); 

                    double h = mv.hDoca(point, mv.measurements.get(k1 + 1).surface.wireLine[indexReferenceWire]);
                    double res = (effectiveDoca - h);
                    chi2 += res*res / effectiveVar;

                    if(indexReferenceWire == 0) svc.setProjectorDoca(h); 
                    else svc.setProjectorDoca(-999); 
                    svc.setProjector(mv.measurements.get(k1 + 1).surface.wireLine[0].origin().x());
                    kfStateVecsAlongTrajectory.add(svc);       
                    
                    StateVec svc2 = sv.new StateVec(svc);
                    if(indexReferenceWire == 1) svc2.setProjectorDoca(h); 
                    else svc2.setProjectorDoca(-999); 
                    svc2.setProjector(mv.measurements.get(k1 + 1).surface.wireLine[1].origin().x());
                    kfStateVecsAlongTrajectory.add(svc2);                      
                }
            }
        }
    }

    public Matrix propagateToVtx(int sector, double Zf) {
        return sv.transport(sector, finalStateVec.k, Zf, finalStateVec, mv, this.getSwimmer());
    }

    @Override
    public void runFitter(AStateVecs sv, AMeasVecs mv) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public StateVec filter(int k, StateVec vec, AMeasVecs mv) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public StateVec smooth(int k, AStateVecs sv, AMeasVecs mv) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public StateVec smooth(StateVec v1, StateVec v2) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public MeasVecs getMeasVecs() {
        return mv;
    }

    public StateVecs getStateVecs() {
        return sv;
    }

    public void printlnMeasVecs() {
        for (int i = 0; i < mv.measurements.size(); i++) {
            org.jlab.clas.tracking.kalmanfilter.AMeasVecs.MeasVec measvec = mv.measurements.get(i);
            if(measvec.surface.type == Type.LINEDOCA){
                String s = String.format("k=%d region=%d superlayer=%d layer=%d error=%.4f", measvec.k, measvec.region, measvec.superlayer,
                        measvec.layer, measvec.error);
                s += String.format(" Surface: index=%d x=%.4f z=%.4f tilt=%.4f wireMaxSag=%.4f", measvec.surface.getIndex(),
                        measvec.surface.x, measvec.surface.z, measvec.surface.tilt, measvec.surface.wireMaxSag, measvec.surface.unc[1]);
                s += String.format(
                        " Surface line 0: doca=%.4f unc=%.4f origin_x =%.4f, origin_y =%.4f, origin_z =%.4f, end_x=%.4f, end_y=%.4f, end_z=%.4f",
                        measvec.surface.doca[0], measvec.surface.unc[0], measvec.surface.wireLine[0].origin().x(),
                        measvec.surface.wireLine[0].origin().y(), measvec.surface.wireLine[0].origin().z(),
                        measvec.surface.wireLine[0].end().x(), measvec.surface.wireLine[0].end().y(),
                        measvec.surface.wireLine[0].end().z());
                if (measvec.surface.wireLine[1] != null) {
                    s += String.format(
                            " Surface line 1: doca=%.4f unc=%.4f origin_x =%.4f, origin_y =%.4f, origin_z =%.4f, end_x=%.4f, end_y=%.4f, end_z=%.4f",
                            measvec.surface.doca[1], measvec.surface.unc[1], measvec.surface.wireLine[1].origin().x(),
                            measvec.surface.wireLine[1].origin().y(), measvec.surface.wireLine[1].origin().z(),
                            measvec.surface.wireLine[1].end().x(), measvec.surface.wireLine[1].end().y(),
                            measvec.surface.wireLine[1].end().z());
                }

                System.out.println(s);
            }
            else if(measvec.surface.type == Type.PLANEURWELL){
                String s = String.format("k=%d region=%d superlayer=%d layer=%d", measvec.k, measvec.region, measvec.superlayer,
                        measvec.layer);
                s += String.format(" Surface: index=%d x=%.4f y=%.4f z=%.4f x_err=%.4f y_err=%.4f", measvec.surface.getIndex(),
                        measvec.surface.x, measvec.surface.y, measvec.surface.z, measvec.surface.x_err, measvec.surface.y_err);
                System.out.println(s);
            }
        }
    }

}

package org.jlab.rec.muvt.track.fit;

import java.util.List;

import org.jlab.clas.swimtools.Swim;
import org.jlab.rec.muvt.track.fit.StateVecs.CovMat;
import org.jlab.rec.muvt.track.fit.StateVecs.StateVec;
import org.jlab.jnp.matrix.*;
import org.jlab.rec.muvt.MUVTCluster;
import org.jlab.rec.muvt.MUVTTrack;


/**
 * @author ziegler
 */
public class KFitter {

    public boolean setFitFailed = false;

    private StateVecs sv;
    private MeasVecs mv = new MeasVecs();

    public StateVec finalStateVec = null;
    public CovMat finalCovMat;
    public int totNumIter = 30;
    public boolean filterOn = true;
    public double chi2 = 0;
    public int NDF = 0;
    public int ConvStatus = 1;
    public int interNum = 0;

    Matrix first_inverse = new Matrix();
    Matrix addition      = new Matrix();
    Matrix result        = new Matrix();
    Matrix result_inv    = new Matrix();
    Matrix adj           = new Matrix();

    MUVTTrack track = null;
    private List<MUVTCluster> clusters;

    public KFitter(MUVTTrack track, Swim swimmer, int c) {
        sv = new StateVecs(swimmer);
        this.track = track;
        this.clusters = track.getClusters();
        this.init(clusters, track.getSector(), track.getX(), track.getY(), track.getZ(), 
                            track.getPx(), track.getPy(), track.getPz(), track.getQ(), c);
    }

    public KFitter(List<MUVTCluster> clusters, int sector, double xVtx, double yVtx, double zVtx,
            double pxVtx, double pyVtx, double pzVtx, int q, Swim swimmer, int c) {
        sv = new StateVecs(swimmer);
        this.track = new MUVTTrack(0,sector, q, xVtx, yVtx, zVtx, pxVtx, pyVtx, pzVtx, clusters);
        this.clusters = clusters;
        this.init(clusters, sector, xVtx, yVtx, zVtx, pxVtx, pyVtx, pzVtx, q, c);
    }

    private void init(List<MUVTCluster> clusters, int sector, double xVtx, double yVtx, double zVtx,
            double pxVtx, double pyVtx, double pzVtx, int q, int c) {
        // initialize measVecs
        mv.setMeasVecs(clusters);
        int mSize = mv.measurements.size();

        sv.Z = new double[mSize];

        for (int i = 0; i < mSize; i++) sv.Z[i] = mv.measurements.get(i).z;

        // initialize stateVecs
        sv.init(sector, xVtx, yVtx, zVtx, pxVtx, pyVtx, pzVtx, q, sv.Z[0], this, c);        
    }

    public void runFitter(int sector) {
        int svzLength = sv.Z.length;

        for (int i = 1; i <= totNumIter; i++) {
            interNum = i;
            this.chi2 = 0;
            if (i > 1) {
                for (int k = svzLength - 1; k > 0; k--) {
                    if (k >= 1) {
                        sv.transport(sector, k, k - 1, sv.trackTraj.get(k), sv.trackCov.get(k));
                        this.filter(k - 1);
                    }
                }
            }
            for (int k = 0; k < svzLength - 1; k++) {
                sv.transport(sector, k, k + 1, sv.trackTraj.get(k), sv.trackCov.get(k));
                this.filter(k + 1);
            }
            if (i > 1) {
                if (this.setFitFailed) i = totNumIter;
                if (!this.setFitFailed) {
                    this.finalStateVec = sv.trackTraj.get(svzLength - 1);
                    this.finalCovMat = sv.trackCov.get(svzLength - 1);
                } else {
                    this.ConvStatus = 1;
                }
            }
        }
        if (totNumIter == 1) {
            this.finalStateVec = sv.trackTraj.get(svzLength - 1);
            this.finalCovMat = sv.trackCov.get(svzLength - 1);
        }

        // Do one final pass to get the final chi^2 and the corresponding centroid residuals.
        this.chi2 = 0;
        for (int k = svzLength - 1; k > 0; --k) {
            if (k >= 1) {
                sv.transport(sector, k, k-1, sv.trackTraj.get(k), sv.trackCov.get(k));
                this.filter(k - 1);
            }
        }
        for (int k = 0; k < svzLength - 1; ++k) {
            sv.transport(sector, k, k+1, sv.trackTraj.get(k), sv.trackCov.get(k));
        }

        
        // save final trajectory points
        /*
        if(this.finalStateVec!=null) {
            for (int k = 0; k < svzLength; ++k) {
                Trajectory trj = new Trajectory(mv.measurements.get(k).layer,
                                                sv.trackTraj.get(k).x,
                                                sv.trackTraj.get(k).y,
                                                sv.trackTraj.get(k).z,
                                                sv.trackTraj.get(k).tx,
                                                sv.trackTraj.get(k).ty,
                                                0,
                                                sv.trackTraj.get(k).deltaPath);
                track.setFMTtraj(trj);
            }
        }
        */
        
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

    private void filter(int k) {
        if (sv.trackTraj.get(k) != null && sv.trackCov.get(k).covMat != null && k < sv.Z.length ) {
            double[] K = new double[5];
            double V = mv.measurements.get(k).error * mv.measurements.get(k).error;

            double[] H = mv.HMUVT(sv.trackTraj.get(k),sv);
            Matrix CaInv = this.filterCovMat(H, sv.trackCov.get(k).covMat, V);
            if (CaInv != null) sv.trackCov.get(k).covMat = CaInv;
            else return;

            Matrix cMat = new Matrix();
                    if (CaInv != null) {
                        Matrix5x5.copy(CaInv, cMat);
                    } else {
                        return;
            }
            
            // Calculate the gain matrix.
            for (int j = 0; j < 5; j++) {
                        // the gain matrix
                        K[j] = (H[0] * cMat.get(j, 0)
                                + H[1] * cMat.get(j, 1)) / V;
            }

            // Update Chi^2 and filtered state vector.            
            double res = mv.dhMUVT(sv.trackTraj.get(k));                      
            double filt[] = new double[5];
            for(int j = 0; j < 5; j ++){
                    filt[j] += K[j]*res;
            }
            
            this.chi2 += (res*res/mv.measurements.get(k).error/mv.measurements.get(k).error);

            double x_filt = sv.trackTraj.get(k).x + filt[0];
            double y_filt = sv.trackTraj.get(k).y + filt[1];
            double tx_filt = sv.trackTraj.get(k).tx + filt[2];
            double ty_filt = sv.trackTraj.get(k).ty + filt[3];
            double Q_filt = sv.trackTraj.get(k).Q + filt[4];

            if (filterOn) {
                sv.trackTraj.get(k).x = x_filt;
                sv.trackTraj.get(k).y = y_filt;
                sv.trackTraj.get(k).tx = tx_filt;
                sv.trackTraj.get(k).ty = ty_filt;
                sv.trackTraj.get(k).Q = Q_filt;
            }
        }
    }

    public Matrix propagateToVtx(int sector, double Zf) {
        return sv.transport(sector, 0, Zf, sv.trackTraj.get(0), sv.trackCov.get(0));
    }
}

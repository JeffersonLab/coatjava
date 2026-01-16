package org.jlab.clas.swimtools;

import org.jlab.geom.prim.Vector3D;
import org.jlab.geom.prim.Point3D;
import org.jlab.geom.prim.Line3D;

import cnuphys.rk4.RungeKuttaException;

import cnuphys.swim.SwimTrajectory;
import cnuphys.swim.util.Plane;

import cnuphys.swimZ.SwimZException;
import cnuphys.swimZ.SwimZResult;
import cnuphys.swimZ.SwimZStateVector;

import cnuphys.adaptiveSwim.AdaptiveSwimResult;
import cnuphys.rk4.IStopper;
import org.jlab.clas.swimtools.Stoppers.BeamLineSwimStopper;

import org.jlab.clas.swimtools.Stoppers.CylindricalBoundarySwimStopper;
import org.jlab.clas.swimtools.Stoppers.DCASwimStopper;
import org.jlab.clas.swimtools.Stoppers.LineSwimStopper;
import org.jlab.clas.swimtools.Stoppers.SphericalBoundarySwimStopper;
import org.jlab.clas.swimtools.Stoppers.ZSwimStopper;

/**
 * Class for swimming to various surfaces.  The input and output units are cm and GeV/c
 *
 * @author ziegler
 */
public class Swim extends ASwim {

    private static final int STATE_SIZE = 8;
    private static final double CM_PER_M = 100.0;
    private static final double KGCM_TO_TCM = 0.1; // divide by 10

    private SwimTrajectory swimTraj;

    public SwimTrajectory getSwimTraj() {
        return swimTraj;
    }

    public void setSwimTraj(SwimTrajectory swimTraj) {
        this.swimTraj = swimTraj;
    }

    // ------------------------------------------------------------------------
    // Common guards and helpers
    // ------------------------------------------------------------------------

    private boolean isInvalid() {
        return SwimUnPhys || _pTot < MINTRKMOM;
    }

    private double[] newState() {
        return new double[STATE_SIZE];
    }

    private void fillFromTrajectory(double[] out, SwimTrajectory traj) {
        double[] y = traj.lastElement();
        out[0] = y[0] * CM_PER_M;
        out[1] = y[1] * CM_PER_M;
        out[2] = y[2] * CM_PER_M;
        out[3] = y[3] * _pTot;
        out[4] = y[4] * _pTot;
        out[5] = y[5] * _pTot;
        out[6] = y[6] * CM_PER_M;
        out[7] = y[7] * 10.0; // kG·m → T·cm
    }

    private void fillFromZResult(double[] out, SwimZResult szr, double bdlKgCm) {
        SwimZStateVector last = szr.last();
        double[] p3 = szr.getThreeMomentum(last);
        out[0] = last.x;
        out[1] = last.y;
        out[2] = last.z;
        out[3] = p3[0];
        out[4] = p3[1];
        out[5] = p3[2];
        out[6] = szr.getPathLength();
        out[7] = bdlKgCm * KGCM_TO_TCM;
    }

    private SwimZResult tryZSwimSector(int sector, double z_cm, double[] hdata) {
        if (_pTot <= SWIMZMINMOM) return null;
        try {
            double stepSizeCM = stepSize * CM_PER_M;
            SwimZStateVector start = new SwimZStateVector(
                    _x0 * CM_PER_M, _y0 * CM_PER_M, _z0 * CM_PER_M,
                    _pTot, _theta, _phi);
            return PC.RCF_z.sectorAdaptiveRK(sector, _charge, _pTot, start, z_cm, stepSizeCM, hdata);
        } catch (SwimZException e) {
            return null;
        }
    }

    private SwimZResult tryZSwimLab(double z_cm, double[] hdata) {
        if (_pTot <= SWIMZMINMOM) return null;
        try {
            double stepSizeCM = stepSize * CM_PER_M;
            SwimZStateVector start = new SwimZStateVector(
                    _x0 * CM_PER_M, _y0 * CM_PER_M, _z0 * CM_PER_M,
                    _pTot, _theta, _phi);
            return PC.CF_z.adaptiveRK(_charge, _pTot, start, z_cm, stepSizeCM, hdata);
        } catch (SwimZException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------------
    // Plane / Z swimmers
    // ------------------------------------------------------------------------

    public double[] SwimToPlaneTiltSecSys(int sector, double z_cm) {
        if (isInvalid()) return null;

        double[] hdata = new double[3];
        double[] out = newState();

        SwimZResult szr = tryZSwimSector(sector, z_cm, hdata);
        if (szr != null) {
            double bdl = szr.sectorGetBDL(sector, PC.RCF_z.getProbe());
            fillFromZResult(out, szr, bdl);
            return out;
        }

        try {
            double z_m = z_cm / CM_PER_M;
            SwimTrajectory traj = PC.RCF.sectorSwim(
                    sector, _charge, _x0, _y0, _z0, _pTot, _theta, _phi,
                    z_m, accuracy, _rMax, _maxPathLength, stepSize,
                    cnuphys.swim.Swimmer.CLAS_Tolerance, hdata);
            if (traj == null) return null;
            traj.sectorComputeBDL(sector, PC.RCP);
            fillFromTrajectory(out, traj);
            return out;
        } catch (RungeKuttaException e) {
            return null;
        }
    }

    public double[] SwimToPlaneTiltSecSysBdlXZPlane(int sector, double z_cm) {
        if (isInvalid()) return null;

        double[] hdata = new double[3];
        double[] out = newState();

        SwimZResult szr = tryZSwimSector(sector, z_cm, hdata);
        if (szr != null) {
            double bdl = szr.sectorGetBDLXZPlane(sector, PC.RCF_z.getProbe());
            fillFromZResult(out, szr, bdl);
            return out;
        }

        try {
            double z_m = z_cm / CM_PER_M;
            SwimTrajectory traj = PC.RCF.sectorSwim(
                    sector, _charge, _x0, _y0, _z0, _pTot, _theta, _phi,
                    z_m, accuracy, _rMax, _maxPathLength, stepSize,
                    cnuphys.swim.Swimmer.CLAS_Tolerance, hdata);
            if (traj == null) return null;
            traj.sectorComputeBDL(sector, PC.RCP);
            fillFromTrajectory(out, traj);
            return out;
        } catch (RungeKuttaException e) {
            return null;
        }
    }

    @Override
    public double[] SwimToPlaneLab(double z_cm) {
        if (isInvalid()) return null;

        double[] hdata = new double[3];
        double[] out = newState();

        SwimZResult szr = tryZSwimLab(z_cm, hdata);
        if (szr != null) {
            double bdl = szr.getBDL(PC.CF_z.getProbe());
            fillFromZResult(out, szr, bdl);
            return out;
        }

        try {
            double z_m = z_cm / CM_PER_M;
            SwimTrajectory traj = PC.CF.swim(
                    _charge, _x0, _y0, _z0, _pTot, _theta, _phi,
                    z_m, accuracy, _rMax, _maxPathLength, stepSize,
                    cnuphys.swim.Swimmer.CLAS_Tolerance, hdata);
            if (traj == null) return null;
            traj.computeBDL(PC.CP);
            fillFromTrajectory(out, traj);
            return out;
        } catch (RungeKuttaException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------------
    // Geometry-based stoppers
    // ------------------------------------------------------------------------

//    @Override
    public double[] SwimToCylinder(double radius) {
        if (SwimUnPhys) return null;
        return swimWithStopper(new CylindricalBoundarySwimStopper(radius));
    }

    @Override
    public double[] SwimToSphere(double radius) {
        if (SwimUnPhys) return null;
        return swimWithStopper(new SphericalBoundarySwimStopper(radius));
    }

    @Override
    public double[] SwimToBeamLine(double xB, double yB) {
        if (SwimUnPhys) return null;
        return swimWithStopper(new BeamLineSwimStopper(xB, yB));
    }

    @Override
    public double[] SwimToLine(Line3D l) {
        if (SwimUnPhys) return null;
        return swimWithStopper(new LineSwimStopper(l));
    }

    private double[] swimWithStopper(IStopper stopper) {
        try {
            SwimTrajectory traj = PC.CF.swim(
                    _charge, _x0, _y0, _z0, _pTot, _theta, _phi,
                    stopper, _maxPathLength, stepSize, 0.0005);
              if (traj == null) return null;
            traj.computeBDL(PC.CP);
            double[] out = newState();
            fillFromTrajectory(out, traj);
            return out;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // ------------------------------------------------------------------------
    // Adaptive swimmers
    // ------------------------------------------------------------------------

    @Override
    public double[] SwimRho(double radius, double accuracy) {
        if (SwimUnPhys) return null;
        try {
            AdaptiveSwimResult r = new AdaptiveSwimResult(false);
            PC.CF.swimRho(_charge, _x0, _y0, _z0, _pTot, _theta, _phi,
                    radius / CM_PER_M, accuracy / CM_PER_M,
                    _rMax, stepSize, cnuphys.swim.Swimmer.CLAS_Tolerance, r);
            return r.getStatus() == 0 ? adaptiveOut(r) : null;
        } catch (RungeKuttaException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public double[] SwimGenCylinder(Point3D a1, Point3D a2, double radius, double accuracy) {
        if (SwimUnPhys) return null;
        try {
            AdaptiveSwimResult r = new AdaptiveSwimResult(false);
            double[] p1 = {a1.x() / CM_PER_M, a1.y() / CM_PER_M, a1.z() / CM_PER_M};
            double[] p2 = {a2.x() / CM_PER_M, a2.y() / CM_PER_M, a2.z() / CM_PER_M};
            PC.CF.swimCylinder(_charge, _x0, _y0, _z0, _pTot, _theta, _phi,
                    p1, p2, radius / CM_PER_M, accuracy / CM_PER_M,
                    _rMax, stepSize, cnuphys.swim.Swimmer.CLAS_Tolerance, r);
            return r.getStatus() == 0 ? adaptiveOut(r) : null;
        } catch (RungeKuttaException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public double[] SwimPlane(Vector3D n, Point3D p, double accuracy) {
        if (SwimUnPhys) return null;
        try {
            AdaptiveSwimResult r = new AdaptiveSwimResult(false);
            PC.CF.swimPlane(_charge, _x0, _y0, _z0, _pTot, _theta, _phi,
                    n.x(), n.y(), n.z(),
                    p.x() / CM_PER_M, p.y() / CM_PER_M, p.z() / CM_PER_M,
                    accuracy / CM_PER_M, _rMax, stepSize,
                    cnuphys.swim.Swimmer.CLAS_Tolerance, r);
            return r.getStatus() == 0 ? adaptiveOut(r) : null;
        } catch (RungeKuttaException e) {
            e.printStackTrace();
            return null;
        }
    }

    private double[] adaptiveOut(AdaptiveSwimResult r) {
        double[] out = newState();
        out[0] = r.getUf()[0] * CM_PER_M;
        out[1] = r.getUf()[1] * CM_PER_M;
        out[2] = r.getUf()[2] * CM_PER_M;
        out[3] = r.getUf()[3] * _pTot;
        out[4] = r.getUf()[4] * _pTot;
        out[5] = r.getUf()[5] * _pTot;
        out[6] = r.getFinalS() * CM_PER_M;
        out[7] = 0.0;
        return out;
    }

    // ------------------------------------------------------------------------
    // Swim to boundaries in the lab
    // ------------------------------------------------------------------------

    @Override
    public double[] SwimToPlaneBoundary(double d_cm, Vector3D n) {
        if (SwimUnPhys) return null;
        try {
            Plane plane = new Plane(n.x(), n.y(), n.z(), d_cm / CM_PER_M);
            SwimTrajectory traj = PC.CF.swim(
                    _charge, _x0, _y0, _z0, _pTot, _theta, _phi,
                    plane, accuracy, _maxPathLength, stepSize,
                    cnuphys.swim.Swimmer.CLAS_Tolerance, new double[3]);
            if (traj == null) return null;
            traj.computeBDL(PC.CP);
            double[] out = newState();
            fillFromTrajectory(out, traj);
            return out;
        } catch (RungeKuttaException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public double[] SwimToZ(double Z, int dir) {
        if (SwimUnPhys) return null;
        ZSwimStopper stopper = new ZSwimStopper(Z, dir);
        try {
            SwimTrajectory traj = PC.CF.swim(
                    _charge, _x0, _y0, _z0, _pTot, _theta, _phi,
                    stopper, _maxPathLength, stepSize, distanceBetweenSaves);
            if (traj == null) return null;
            traj.computeBDL(PC.CP);
            setSwimTraj(traj);
            double[] out = newState();
            fillFromTrajectory(out, traj);
            return out;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // ------------------------------------------------------------------------
    // Swim to track trajectory - used for detached vertexing
    // ------------------------------------------------------------------------

    public double[] SwimToDCA(SwimTrajectory trk2) {
        if (SwimUnPhys) return null;
        try {
            SwimTrajectory traj = PC.CF.swim(
                    _charge, _x0, _y0, _z0, _pTot, _theta, _phi,
                    new DCASwimStopper(trk2), _maxPathLength, stepSize, 0.0005);
            if (traj == null) return null;
            double[] y = traj.lastElement();
            return new double[]{
                    y[0] * CM_PER_M,
                    y[1] * CM_PER_M,
                    y[2] * CM_PER_M,
                    y[3] * _pTot,
                    y[4] * _pTot,
                    y[5] * _pTot
            };
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}

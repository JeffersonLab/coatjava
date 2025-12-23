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

    private SwimTrajectory swimTraj; 
    
    /**
     * @return the swimTraj
     */
    public SwimTrajectory getSwimTraj() {
        return swimTraj;
    }

    /**
     * @param swimTraj the swimTraj to set
     */
    public void setSwimTraj(SwimTrajectory swimTraj) {
        this.swimTraj = swimTraj;
    }
   
    /**
     * 
     * @param sector
     * @param z_cm
     * @return 
     */
    @Override
    public double[] SwimToPlaneTiltSecSys(int sector, double z_cm) {

        // Fiducial Cut:
        if (_pTot < MINTRKMOM || this.SwimUnPhys==true) return null;

        double[] value = new double[8];
        double hdata[] = new double[3];

        try {

            // Try to use new Z-Swimmer: 
            SwimZResult szr = null;
            if (_pTot > SWIMZMINMOM) {
                double stepSizeCM = stepSize * 100; // convert to cm
                SwimZStateVector start = new SwimZStateVector(_x0 * 100, _y0 * 100, _z0 * 100, _pTot, _theta, _phi);
                try {
                    szr = PC.RCF_z.sectorAdaptiveRK(sector, _charge, _pTot, start, z_cm, stepSizeCM, hdata);
                } catch (SwimZException e) {
                    szr = null;
                }
            }
            if (szr != null) {
                double bdl = szr.sectorGetBDL(sector, PC.RCF_z.getProbe());
                double pathLength = szr.getPathLength(); // already in cm
                SwimZStateVector last = szr.last();
                double p3[] = szr.getThreeMomentum(last);
                value[0] = last.x; // cm
                value[1] = last.y; // cm
                value[2] = last.z; // cm
                value[3] = p3[0];
                value[4] = p3[1];
                value[5] = p3[2];
                value[6] = pathLength;
                value[7] = bdl / 10; // convert from kg*cm to T*cm
            }
            
            // Use older swimmer:
            else {
                final double z = z_cm / 100; // convert to meters
                SwimTrajectory traj = PC.RCF.sectorSwim(sector, _charge, _x0, _y0, _z0, _pTot, _theta, _phi, z, accuracy, _rMax,
                                _maxPathLength, stepSize, cnuphys.swim.Swimmer.CLAS_Tolerance, hdata);
                if(traj==null) return null;
                traj.sectorComputeBDL(sector, PC.RCP);
                double lastY[] = traj.lastElement();
                value[0] = lastY[0] * 100; // convert back to cm
                value[1] = lastY[1] * 100; // convert back to cm
                value[2] = lastY[2] * 100; // convert back to cm
                value[3] = lastY[3] * _pTot;
                value[4] = lastY[4] * _pTot;
                value[5] = lastY[5] * _pTot;
                value[6] = lastY[6] * 100;
                value[7] = lastY[7] * 10;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return value;

    }

    /**
     * 
     * @param sector
     * @param z_cm
     * @return 
     */
    @Override
    public double[] SwimToPlaneTiltSecSysBdlXZPlane(int sector, double z_cm) {

        // Fiducial Cut:
        if (_pTot < MINTRKMOM || this.SwimUnPhys==true) return null;

        double hdata[] = new double[3];
        double[] value = new double[8];

        try {

            // Try to use new Z-Swimmer: 
            SwimZResult szr = null;
            if (_pTot > SWIMZMINMOM) {
                double stepSizeCM = stepSize * 100; // convert to cm
                SwimZStateVector start = new SwimZStateVector(_x0 * 100, _y0 * 100, _z0 * 100, _pTot, _theta, _phi);
                try {
                    szr = PC.RCF_z.sectorAdaptiveRK(sector, _charge, _pTot, start, z_cm, stepSizeCM, hdata);
                } catch (SwimZException e) {
                    szr = null;
                }
            }
            if (szr != null) {
                double bdl = szr.sectorGetBDLXZPlane(sector, PC.RCF_z.getProbe());
                double pathLength = szr.getPathLength(); // already in cm
                SwimZStateVector last = szr.last();
                double p3[] = szr.getThreeMomentum(last);
                value[0] = last.x; // xf in cm
                value[1] = last.y; // yz in cm
                value[2] = last.z; // zf in cm
                value[3] = p3[0];
                value[4] = p3[1];
                value[5] = p3[2];
                value[6] = pathLength;
                value[7] = bdl / 10; // convert from kg*cm to T*cm
            }
           
            // Use older swimmer:
            else {
                double z = z_cm / 100; // convert to meters
                SwimTrajectory traj = PC.RCF.sectorSwim(sector, _charge, _x0, _y0, _z0, _pTot, _theta, _phi, z, accuracy, _rMax,
                                _maxPathLength, stepSize, cnuphys.swim.Swimmer.CLAS_Tolerance, hdata);
                if (traj==null) return null;
                traj.sectorComputeBDL(sector, PC.RCP);
                double lastY[] = traj.lastElement();
                value[0] = lastY[0] * 100; // convert back to cm
                value[1] = lastY[1] * 100; // convert back to cm
                value[2] = lastY[2] * 100; // convert back to cm
                value[3] = lastY[3] * _pTot;
                value[4] = lastY[4] * _pTot;
                value[5] = lastY[5] * _pTot;
                value[6] = lastY[6] * 100;
                value[7] = lastY[7] * 10;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return value;
    }
        
    /**
     * 
     * @param z_cm
     * @return state  x,y,z,px,py,pz, pathlength, iBdl at the plane surface
     */
    @Override
    public double[] SwimToPlaneLab(double z_cm) {

        // Fiducial Cut:
        if (_pTot < MINTRKMOM || this.SwimUnPhys==true) return null;

        double hdata[] = new double[3];
        double[] value = new double[8];
        
        try {

            // Try to use new Z-Swimmer: 
            SwimZResult szr = null;
            if (_pTot > SWIMZMINMOM) {
                double stepSizeCM = stepSize * 100; // convert to cm
                SwimZStateVector start = new SwimZStateVector(_x0 * 100, _y0 * 100, _z0 * 100, _pTot, _theta, _phi);
                try {
                    szr = PC.CF_z.adaptiveRK(_charge, _pTot, start, z_cm, stepSizeCM, hdata);
                } catch (SwimZException e) {
                    szr = null;
                }
            }
            if (szr != null) {
                double bdl = szr.getBDL(PC.CF_z.getProbe());
                double pathLength = szr.getPathLength(); // already in cm
                SwimZStateVector last = szr.last();
                double p3[] = szr.getThreeMomentum(last);
                value[0] = last.x; // xf in cm
                value[1] = last.y; // yz in cm
                value[2] = last.z; // zf in cm
                value[3] = p3[0];
                value[4] = p3[1];
                value[5] = p3[2];
                value[6] = pathLength;
                value[7] = bdl / 10; // convert from kg*cm to T*cm
            }
            
            // Use older swimmer:
            else {
                double z = z_cm / 100; // the magfield method uses meters
                SwimTrajectory traj = PC.CF.swim(_charge, _x0, _y0, _z0, _pTot, _theta, _phi, z, accuracy, _rMax, _maxPathLength,
                                stepSize, cnuphys.swim.Swimmer.CLAS_Tolerance, hdata);
                if (traj==null) return null;
                traj.computeBDL(PC.CP);
                double lastY[] = traj.lastElement();
                value[0] = lastY[0] * 100; // convert back to cm
                value[1] = lastY[1] * 100; // convert back to cm
                value[2] = lastY[2] * 100; // convert back to cm
                value[3] = lastY[3] * _pTot;
                value[4] = lastY[4] * _pTot;
                value[5] = lastY[5] * _pTot;
                value[6] = lastY[6] * 100;
                value[7] = lastY[7] * 10;
            } // old swimmer

        } catch (RungeKuttaException e) {
            e.printStackTrace();
        }
        return value;
    }

    /**
     * 
     * @param Rad
     * @return state  x,y,z,px,py,pz, pathlength, iBdl at the surface 
     */
    public double[] SwimToCylinder(double Rad) {
        
        if (this.SwimUnPhys) return null;
        double[] value = new double[8];
        
        CylindricalBoundarySwimStopper stopper = new CylindricalBoundarySwimStopper(Rad);
        
        SwimTrajectory st = PC.CF.swim(_charge, _x0, _y0, _z0, _pTot, _theta, _phi,
            stopper, _maxPathLength, stepSize, 0.0005);
        if (st==null) return null;
        st.computeBDL(PC.CP);

        double[] lastY = st.lastElement();
        value[0] = lastY[0] * 100; // convert back to cm
        value[1] = lastY[1] * 100; // convert back to cm
        value[2] = lastY[2] * 100; // convert back to cm
        value[3] = lastY[3] * _pTot; // normalized values
        value[4] = lastY[4] * _pTot;
        value[5] = lastY[5] * _pTot;
        value[6] = lastY[6] * 100;
        value[7] = lastY[7] * 10; // Conversion from kG.m to T.cm
        return value;
    }

    /**
     * 
     * @param radius   in cm
     * @param accuracy in cm 
     * @return state  x,y,z,px,py,pz, pathlength, iBdl at the surface 
     */
    @Override
    public double[] SwimRho(double radius, double accuracy)  {

        if(this.SwimUnPhys) return null;
        double[] value = null;

        try {
            AdaptiveSwimResult result = new AdaptiveSwimResult(false);
            
            PC.CF.swimRho(_charge, _x0, _y0, _z0, _pTot, _theta, _phi, 
                radius/100, accuracy/100, _rMax, stepSize, cnuphys.swim.Swimmer.CLAS_Tolerance, result);

            if(result.getStatus()==0) {
                value = new double[8];   
                value[0] = result.getUf()[0] * 100; // convert back to cm
                value[1] = result.getUf()[1] * 100; // convert back to cm
                value[2] = result.getUf()[2] * 100; // convert back to cm
                value[3] = result.getUf()[3] * _pTot; // normalized values
                value[4] = result.getUf()[4] * _pTot;
                value[5] = result.getUf()[5] * _pTot;
                value[6] = result.getFinalS() * 100;
                value[7] = 0; // Conversion from kG.m to T.cm
            }
        } catch (RungeKuttaException e) {
            System.out.println(_charge + " " + _x0 + " " + _y0 + " " + _z0 + " " + _pTot + " " + _theta + " " + _phi);
            e.printStackTrace();
        }
        return value;
    }
    
    /**
     * 
     * @param axisPoint1 in cm
     * @param axisPoint2 in cm 
     * @param radius in cm 
     * @param accuracy in cm
     * @return swam trajectory to the cylinder
     */
    @Override
    public double[] SwimGenCylinder(Point3D axisPoint1, Point3D axisPoint2, double radius, double accuracy)  {

        if(this.SwimUnPhys) return null;

        double[] value = null;
        double[] p1 = new double[3];
        double[] p2 = new double[3];
        p1[0] = axisPoint1.x()/100;
        p1[1] = axisPoint1.y()/100;
        p1[2] = axisPoint1.z()/100;
        p2[0] = axisPoint2.x()/100;
        p2[1] = axisPoint2.y()/100;
        p2[2] = axisPoint2.z()/100;
        
        try {
            AdaptiveSwimResult result = new AdaptiveSwimResult(false);
            
            PC.CF.swimCylinder(_charge, _x0, _y0, _z0, _pTot, _theta, _phi, 
                    p1, p2, radius/100, accuracy/100, _rMax, stepSize, cnuphys.swim.Swimmer.CLAS_Tolerance, result);
            
            if(result.getStatus()==0) {
                value = new double[8];            
                value[0] = result.getUf()[0] * 100; // convert back to cm
                value[1] = result.getUf()[1] * 100; // convert back to cm
                value[2] = result.getUf()[2] * 100; // convert back to cm
                value[3] = result.getUf()[3] * _pTot; // normalized values
                value[4] = result.getUf()[4] * _pTot;
                value[5] = result.getUf()[5] * _pTot;
                value[6] = result.getFinalS() * 100;
                value[7] = 0; // Conversion from kG.m to T.cm
            }
        } catch (RungeKuttaException e) {
            System.out.println(_charge + " " + _x0 + " " + _y0 + " " + _z0 + " " + _pTot + " " + _theta + " " + _phi);
            e.printStackTrace();
        }
        return value;

    }

    /**
     * 
     * @param n
     * @param p
     * @param accuracy
     * @return 
     */
    @Override
    public double[] SwimPlane(Vector3D n, Point3D p, double accuracy)  {

        if (this.SwimUnPhys) return null;
        
        double[] value = null;

        try {
            AdaptiveSwimResult result = new AdaptiveSwimResult(false);
            
            PC.CF.swimPlane(_charge, _x0, _y0, _z0, _pTot, _theta, _phi, 
                            n.x(),n.y(),n.z(),p.x()/100,p.y()/100,p.z()/100, 
                            accuracy/100, _rMax, stepSize, cnuphys.swim.Swimmer.CLAS_Tolerance, result);
            
            if(result.getStatus()==0) {
                value = new double[8];   
                value[0] = result.getUf()[0] * 100; // convert back to cm
                value[1] = result.getUf()[1] * 100; // convert back to cm
                value[2] = result.getUf()[2] * 100; // convert back to cm
                value[3] = result.getUf()[3] * _pTot; // normalized values
                value[4] = result.getUf()[4] * _pTot;
                value[5] = result.getUf()[5] * _pTot;
                value[6] = result.getFinalS() * 100;
                value[7] = 0; // Conversion from kG.m to T.cm
            }
        } catch (RungeKuttaException e) {
            System.out.println(_charge + " " + _x0 + " " + _y0 + " " + _z0 + " " + _pTot + " " + _theta + " " + _phi);
            e.printStackTrace();
        }
        return value;
    }
    
    /**
     * 
     * @param Rad
     * @return state  x,y,z,px,py,pz, pathlength, iBdl at the surface 
     */
    @Override
    public double[] SwimToSphere(double Rad) {

        if (this.SwimUnPhys==true) return null;
        double[] value = new double[8];
        
        SphericalBoundarySwimStopper stopper = new SphericalBoundarySwimStopper(Rad);
            
        SwimTrajectory st = PC.CF.swim(_charge, _x0, _y0, _z0, _pTot, _theta, _phi,
            stopper, _maxPathLength, stepSize, 0.0005);
        if (st==null) return null;
        st.computeBDL(PC.CP);

        double[] lastY = st.lastElement();
        value[0] = lastY[0] * 100; // convert back to cm
        value[1] = lastY[1] * 100; // convert back to cm
        value[2] = lastY[2] * 100; // convert back to cm
        value[3] = lastY[3] * _pTot; // normalized values
        value[4] = lastY[4] * _pTot;
        value[5] = lastY[5] * _pTot;
        value[6] = lastY[6] * 100;
        value[7] = lastY[7] * 10; // Conversion from kG.m to T.cm
        return value;
    }

    /**
     * 
     * @param d_cm
     * @param n
     * @param dir
     * @return return state  x,y,z,px,py,pz, pathlength, iBdl at the plane surface in the lab frame
     */
    @Override
    public double[] SwimToPlaneBoundary(double d_cm, Vector3D n, int dir) {

        if (this.SwimUnPhys) return null;

        double[] value = new double[8];
        double hdata[] = new double[3];
        double d = d_cm / 100; // convert to meters

        Plane plane = new Plane(n.x(), n.y(), n.z(), d);
        try {

            SwimTrajectory st = PC.CF.swim(_charge, _x0, _y0, _z0, _pTot, _theta, _phi,
                plane, accuracy, _maxPathLength, stepSize,
                cnuphys.swim.Swimmer.CLAS_Tolerance, hdata);

            st.computeBDL(PC.CP);

            double[] lastY = st.lastElement();
            
            value[0] = lastY[0] * 100; // convert back to cm
            value[1] = lastY[1] * 100; // convert back to cm
            value[2] = lastY[2] * 100; // convert back to cm
            value[3] = lastY[3] * _pTot; // normalized values
            value[4] = lastY[4] * _pTot;
            value[5] = lastY[5] * _pTot;
            value[6] = lastY[6] * 100;
            value[7] = lastY[7] * 10; // Conversion from kG.m to T.cm

        } catch (RungeKuttaException e) {
            e.printStackTrace();
        }
        return value;
    }

    @Override
    public double[] SwimToBeamLine(double xB, double yB) {

        if(this.SwimUnPhys==true) return null;
        
        double[] value = new double[8];
        
        BeamLineSwimStopper stopper = new BeamLineSwimStopper(xB, yB);

        SwimTrajectory st = PC.CF.swim(_charge, _x0, _y0, _z0, _pTot, _theta, _phi,
            stopper, _maxPathLength, stepSize, 0.0005);
        if (st==null) return null;
        st.computeBDL(PC.CP);

        double[] lastY = st.lastElement();
        value[0] = lastY[0] * 100; // convert back to cm
        value[1] = lastY[1] * 100; // convert back to cm
        value[2] = lastY[2] * 100; // convert back to cm
        value[3] = lastY[3] * _pTot; // normalized values
        value[4] = lastY[4] * _pTot;
        value[5] = lastY[5] * _pTot;
        value[6] = lastY[6] * 100;
        value[7] = lastY[7] * 10; // Conversion from kG.m to T.cm
        return value;
    }
   
    @Override
    public double[] SwimToLine(Line3D l) {
        
        if (this.SwimUnPhys==true) return null;

        double[] value = new double[8];
        
        LineSwimStopper stopper = new LineSwimStopper(l);

        SwimTrajectory st = PC.CF.swim(_charge, _x0, _y0, _z0, _pTot, _theta, _phi,
            stopper, _maxPathLength, stepSize, 0.0005);
        if (st==null) return null;
        st.computeBDL(PC.CP);

        double[] lastY = st.lastElement();
        value[0] = lastY[0] * 100; // convert back to cm
        value[1] = lastY[1] * 100; // convert back to cm
        value[2] = lastY[2] * 100; // convert back to cm
        value[3] = lastY[3] * _pTot; // normalized values
        value[4] = lastY[4] * _pTot;
        value[5] = lastY[5] * _pTot;
        value[6] = lastY[6] * 100;
        value[7] = lastY[7] * 10; // Conversion from kG.m to T.cm
        return value;
    }

    /**
     * 
     * @param Z
     * @param dir
     * @return state  x,y,z,px,py,pz, pathlength, iBdl at the surface 
     */
    @Override
    public double[] SwimToZ(double Z, int dir) {
        double[] value = new double[8];
        ZSwimStopper stopper = new ZSwimStopper(Z, dir);
        SwimTrajectory st = PC.CF.swim(_charge, _x0, _y0, _z0, _pTot, _theta, _phi,
            stopper, _maxPathLength, stepSize, distanceBetweenSaves);
        if (st==null) return null;
        st.computeBDL(PC.CP);
        this.setSwimTraj(st);
        double[] lastY = st.lastElement();
        value[0] = lastY[0] * 100; // convert back to cm
        value[1] = lastY[1] * 100; // convert back to cm
        value[2] = lastY[2] * 100; // convert back to cm
        value[3] = lastY[3] * _pTot; // normalized values
        value[4] = lastY[4] * _pTot;
        value[5] = lastY[5] * _pTot;
        value[6] = lastY[6] * 100;
        value[7] = lastY[7] * 10; // Conversion from kG.m to T.cm
        return value;
    }

    @Override
    public double[] SwimToDCA(SwimTrajectory trk2) { //use for both traj to get doca for each track
        
        double[] value = new double[6];
        
        DCASwimStopper stopper = new DCASwimStopper(trk2);
        
        SwimTrajectory st = PC.CF.swim(_charge, _x0, _y0, _z0, _pTot, _theta, _phi,
            stopper, _maxPathLength, stepSize, 0.0005);
        if (st==null) return null;
       
        double[] lastY = st.lastElement();
        value[0] = lastY[0] * 100; // convert back to cm
        value[1] = lastY[1] * 100; // convert back to cm
        value[2] = lastY[2] * 100; // convert back to cm
        value[3] = lastY[3] * _pTot; // normalized values
        value[4] = lastY[4] * _pTot;
        value[5] = lastY[5] * _pTot;
        return value;
    }

}
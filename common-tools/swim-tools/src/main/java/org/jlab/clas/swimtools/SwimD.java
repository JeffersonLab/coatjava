/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jlab.clas.swimtools;

import cnuphys.adaptiveSwim.AdaptiveSwimException;
import cnuphys.rk4.IStopper;
import cnuphys.rk4.RungeKuttaException;
import cnuphys.swim.SwimTrajectory;
import cnuphys.swim.util.Plane;
import cnuphys.swimZ.SwimZException;
import cnuphys.swimZ.SwimZResult;
import cnuphys.swimZ.SwimZStateVector;
import org.jlab.geom.prim.Vector3D;
import org.jlab.geom.prim.Point3D;
import cnuphys.adaptiveSwim.AdaptiveSwimResult;
import cnuphys.adaptiveSwim.AdaptiveSwimmer;
import cnuphys.adaptiveSwim.geometry.Cylinder;
import cnuphys.adaptiveSwim.geometry.Line;
import cnuphys.adaptiveSwim.geometry.Point;
import cnuphys.adaptiveSwim.geometry.Vector;
/**
 *
 * @author ziegler
 */

public class SwimD  {
    private ProbeCollection PC;
    public SwimD(ProbeCollection pC) {
        PC = pC;
    }
    private double _x0;
    private double _y0;
    private double _z0;
    private double _phi;
    private double _theta;
    private double _pTot;
    private double _rMax = 5 + 3; // increase to allow swimming to outer
    // detectors
    private double _maxPathLength = 9;
    private boolean SwimUnPhys = false; //Flag to indicate if track is swimmable
    private int _charge;

    private double SWIMZMINMOM = 0.75; // GeV/c
    private double MINTRKMOM = 0.05; // GeV/c
    private double accuracy = 20e-6; // 20 microns
    private double tolerance = 10e-6; // 10 microns
    private double stepSize = 5.00 * 1.e-4; // 500 microns
    
    public double[] SwimToPlaneTiltSecSys(int sector, double z_cm) {
        double z = z_cm / 100; // the magfield method uses meters
        double[] value = new double[8];

        if (getpTot() < getMINTRKMOM() || this.isSwimUnPhys()==true) // fiducial cut
        {
            return null;
        }

        // use a SwimZResult instead of a trajectory (dph)
        SwimZResult szr = null;

        SwimTrajectory traj = null;
        double hdata[] = new double[3];

        try {

            if (getpTot() > getSWIMZMINMOM()) {

                // use the new z swimmer (dph)
                // NOTE THE DISTANCE, UNITS FOR swimZ are cm, NOT m like the old
                // swimmer (dph)

                double stepSizeCM = getStepSize() * 100; // convert to cm

                // create the starting SwimZ state vector
                SwimZStateVector start = new SwimZStateVector(getX0() * 100, getY0() * 100, getZ0() * 100, getpTot(), getTheta(), getPhi());

                try {
                        szr = PC.RCF_z.sectorAdaptiveRK(sector, getCharge(), getpTot(), start, z_cm, stepSizeCM, hdata);
                } catch (SwimZException e) {
                        szr = null;
                        //System.err.println("[WARNING] Tilted SwimZ Failed for p = " + _pTot);
                }
            }

            if (szr != null) {
                double bdl = szr.sectorGetBDL(sector, PC.RCF_z.getProbe());
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
            } else { // use old swimmer. Either low momentum or SwimZ failed.
                                // (dph)

                traj = PC.RCF.sectorSwim(sector, getCharge(), getX0(), getY0(), getZ0(), getpTot(), getTheta(), getPhi(), z, getAccuracy(), getrMax(), getMaxPathLength(), getStepSize(), cnuphys.swim.Swimmer.CLAS_Tolerance, hdata);

                // traj.computeBDL(sector, rprob);
                if(traj==null)
                    return null;
                
                traj.sectorComputeBDL(sector, PC.RCP);
                // traj.computeBDL(rcompositeField);

                double lastY[] = traj.lastElement();
                value[0] = lastY[0] * 100; // convert back to cm
                value[1] = lastY[1] * 100; // convert back to cm
                value[2] = lastY[2] * 100; // convert back to cm
                value[3] = lastY[3] * getpTot();
                value[4] = lastY[4] * getpTot();
                value[5] = lastY[5] * getpTot();
                value[6] = lastY[6] * 100;
                value[7] = lastY[7] * 10;
            } // use old swimmer
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
    public double[] SwimToPlaneLab(double z_cm) {
        double z = z_cm / 100; // the magfield method uses meters
        double[] value = new double[8];

        if (getpTot() < getMINTRKMOM() || this.isSwimUnPhys()==true) // fiducial cut
        {
                return null;
        }
        SwimTrajectory traj = null;
        double hdata[] = new double[3];

        // use a SwimZResult instead of a trajectory (dph)
        SwimZResult szr = null;

        try {

            if (getpTot() > getSWIMZMINMOM()) {

                // use the new z swimmer (dph)
                // NOTE THE DISTANCE, UNITS FOR swimZ are cm, NOT m like the old
                // swimmer (dph)

                double stepSizeCM = getStepSize() * 100; // convert to cm

                // create the starting SwimZ state vector
                SwimZStateVector start = new SwimZStateVector(getX0() * 100, getY0() * 100, getZ0() * 100, getpTot(), getTheta(), getPhi());

                try {
                        szr = PC.CF_z.adaptiveRK(getCharge(), getpTot(), start, z_cm, stepSizeCM, hdata);
                } catch (SwimZException e) {
                        szr = null;
                        //System.err.println("[WARNING] SwimZ Failed for p = " + _pTot);

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
            } else { // use old swimmer. Either low momentum or SwimZ failed.
                                    // (dph)
                traj = PC.CF.swim(getCharge(), getX0(), getY0(), getZ0(), getpTot(), getTheta(), getPhi(), z, getAccuracy(), getrMax(), getMaxPathLength(), getStepSize(), cnuphys.swim.Swimmer.CLAS_Tolerance, hdata);
                if(traj==null)
                    return null;
                traj.computeBDL(PC.CP);
                // traj.computeBDL(compositeField);

                double lastY[] = traj.lastElement();

                value[0] = lastY[0] * 100; // convert back to cm
                value[1] = lastY[1] * 100; // convert back to cm
                value[2] = lastY[2] * 100; // convert back to cm
                value[3] = lastY[3] * getpTot();
                value[4] = lastY[4] * getpTot();
                value[5] = lastY[5] * getpTot();
                value[6] = lastY[6] * 100;
                value[7] = lastY[7] * 10;
            } // old swimmer

        } catch (RungeKuttaException e) {
                e.printStackTrace();
        }
        return value;

    }

    
    /**
     * Cylindrical stopper
     */
    private class CylindricalBoundarySwimStopper implements IStopper {

        private double _finalPathLength = Double.NaN;

        private double _Rad;
        //boolean cutOff = false;
        // check that the track can get to R.   Stops at the track radius    
        //float[] b = new float[3];
        //double x0 = _x0*100;
        //double y0 = _y0*100;
        //double z0 = _z0*100;

        double max = -1.0;
        /**
         * A swim stopper that will stop if the boundary of a plane is crossed
         *
         * @param maxR
         *            the max radial coordinate in meters.
         */
        private CylindricalBoundarySwimStopper(double Rad) {
            // DC reconstruction units are cm. Swim units are m. Hence scale by
            // 100
            _Rad = Rad;
            // check if the track will reach the surface of the cylinder.  
            //BfieldLab(x0, y0, z0, b);            
            //double trkR = _pTot*Math.sin(Math.toRadians(_theta))/Math.abs(b[2]*LIGHTVEL);
            //double trkXc = x0 + trkR * Math.sin(Math.toRadians(_phi));
            //if(trkR<(Rad+trkXc) && Math.sqrt(x0*x0+y0*y0)<_Rad) { // check only for swimming inside out.
            //    cutOff=true;
            //}
        }

        @Override
        public boolean stopIntegration(double t, double[] y) {
            
            double r = Math.sqrt(y[0] * y[0] + y[1] * y[1]) * 100.;
//            if(r>max ) 
//                max = r;
//            else System.out.println(r + " " + max + " " + t);
            //if(cutOff) {
                return (r < max || r > _Rad); // stop intergration at closest distance to the cylinder
            //}
            //else {
            //    return (r > _Rad);
            //}
        }

        /**
         * Get the final path length in meters
         *
         * @return the final path length in meters
         */
        @Override
        public double getFinalT() {
                return _finalPathLength;
        }

        /**
         * Set the final path length in meters
         *
         * @param finalPathLength
         *            the final path length in meters
         */
        @Override
        public void setFinalT(double finalPathLength) {
                _finalPathLength = finalPathLength;
        }
    }
    //private final double LIGHTVEL     = 0.000299792458 ;
    
    /**
     * 
     * @param Rad
     * @return state  x,y,z,px,py,pz, pathlength, iBdl at the surface 
     */
    public double[] SwimToCylinder(double Rad) {
        
        double[] value = new double[8];
        if(this.isSwimUnPhys())
            return null;
        
        CylindricalBoundarySwimStopper stopper = new CylindricalBoundarySwimStopper(Rad);
        
        SwimTrajectory st = PC.CF.swim(getCharge(), getX0(), getY0(), getZ0(), getpTot(), getTheta(), getPhi(), stopper, getMaxPathLength(), getStepSize(),
                        0.0005);
        if(st==null)
                return null;
        st.computeBDL(PC.CP);
        // st.computeBDL(compositeField);

        double[] lastY = st.lastElement();

        value[0] = lastY[0] * 100; // convert back to cm
        value[1] = lastY[1] * 100; // convert back to cm
        value[2] = lastY[2] * 100; // convert back to cm
        value[3] = lastY[3] * getpTot(); // normalized values
        value[4] = lastY[4] * getpTot();
        value[5] = lastY[5] * getpTot();
        value[6] = lastY[6] * 100;
        value[7] = lastY[7] * 10; // Conversion from kG.m to T.cm

        return value;

    }

    /**
     * 
     * @param radius in cm
     * @return state  x,y,z,px,py,pz, pathlength, iBdl at the surface 
     */
    public double[] SwimRho(double radius)  {
        return SwimRho(radius, getAccuracy()*100);
    }
    
    /**
     * 
     * @param radius   in cm
     * @param accuracy in cm 
     * @return state  x,y,z,px,py,pz, pathlength, iBdl at the surface 
     */
    public double[] SwimRho(double radius, double accuracy)  {

        double[] value = null;

        // using adaptive stepsize
        if(this.isSwimUnPhys())
            return null;

        try {
        
            AdaptiveSwimResult result = new AdaptiveSwimResult(false);
            
            PC.CF.swimRho(getCharge(), getX0(), getY0(), getZ0(), getpTot(), getTheta(), getPhi(), radius/100, accuracy/100, getrMax(), getStepSize(), cnuphys.swim.Swimmer.CLAS_Tolerance, result);

            if(result.getStatus()==0) {
                value = new double[8];   
                value[0] = result.getUf()[0] * 100; // convert back to cm
                value[1] = result.getUf()[1] * 100; // convert back to cm
                value[2] = result.getUf()[2] * 100; // convert back to cm
                value[3] = result.getUf()[3] * getpTot(); // normalized values
                value[4] = result.getUf()[4] * getpTot();
                value[5] = result.getUf()[5] * getpTot();
                value[6] = result.getFinalS() * 100;
                value[7] = 0; // Conversion from kG.m to T.cm
            }
                    
        } catch (RungeKuttaException e) {
                System.out.println(getCharge() + " " + getX0() + " " + getY0() + " " + getZ0() + " " + getpTot() + " " + getTheta() + " " + getPhi());
                e.printStackTrace();
        }
        return value;

    }
    
    /**
     * 
     * @param axisPoint1 in cm
     * @param axisPoint2 in cm 
     * @param radius in cm 
     * @return swam trajectory to the cylinder
     */
    public double[] SwimGenCylinder(Point3D axisPoint1, Point3D axisPoint2, double radius)  {
        return SwimGenCylinder(axisPoint1, axisPoint2, radius, getAccuracy()*100);
    }
    
    /**
     * 
     * @param axisPoint1 in cm
     * @param axisPoint2 in cm 
     * @param radius in cm 
     * @param accuracy in cm
     * @return swam trajectory to the cylinder
     */
    public double[] SwimGenCylinder(Point3D axisPoint1, Point3D axisPoint2, double radius, double accuracy)  {

        double[] value = null;
        double[] p1 = new double[3];
        double[] p2 = new double[3];
        p1[0] = axisPoint1.x()/100;
        p1[1] = axisPoint1.y()/100;
        p1[2] = axisPoint1.z()/100;
        p2[0] = axisPoint2.x()/100;
        p2[1] = axisPoint2.y()/100;
        p2[2] = axisPoint2.z()/100;
        
        Cylinder targCyl = new Cylinder(p1, p2, radius/100);
        // using adaptive stepsize
        if(this.isSwimUnPhys())
            return null;

        try {
        
            AdaptiveSwimResult result = new AdaptiveSwimResult(false);
            
            PC.CF.swimCylinder(getCharge(), getX0(), getY0(), getZ0(), getpTot(), getTheta(), getPhi(), 
                    p1, p2, radius/100, accuracy/100, getrMax(), getStepSize(), cnuphys.swim.Swimmer.CLAS_Tolerance, result);
            
            if(result.getStatus()==0) {
                value = new double[8];            
                value[0] = result.getUf()[0] * 100; // convert back to cm
                value[1] = result.getUf()[1] * 100; // convert back to cm
                value[2] = result.getUf()[2] * 100; // convert back to cm
                value[3] = result.getUf()[3] * getpTot(); // normalized values
                value[4] = result.getUf()[4] * getpTot();
                value[5] = result.getUf()[5] * getpTot();
                value[6] = result.getFinalS() * 100;
                value[7] = 0; // Conversion from kG.m to T.cm
            }
                    
        } catch (RungeKuttaException e) {
                System.out.println(getCharge() + " " + getX0() + " " + getY0() + " " + getZ0() + " " + getpTot() + " " + getTheta() + " " + getPhi());
                e.printStackTrace();
        }
        return value;

    }

    public double[] SwimPlane(Vector3D n, Point3D p, double accuracy)  {

        double[] value = null;
        
        
        // using adaptive stepsize
        if(this.isSwimUnPhys())
            return null;

        try {
        
            AdaptiveSwimResult result = new AdaptiveSwimResult(false);
            
            PC.CF.swimPlane(getCharge(), getX0(), getY0(), getZ0(), getpTot(), getTheta(), getPhi(), 
                            n.x(),n.y(),n.z(),p.x()/100,p.y()/100,p.z()/100, 
                            accuracy/100, getrMax(), getStepSize(), cnuphys.swim.Swimmer.CLAS_Tolerance, result);
            

            if(result.getStatus()==0) {
                value = new double[8];   
                value[0] = result.getUf()[0] * 100; // convert back to cm
                value[1] = result.getUf()[1] * 100; // convert back to cm
                value[2] = result.getUf()[2] * 100; // convert back to cm
                value[3] = result.getUf()[3] * getpTot(); // normalized values
                value[4] = result.getUf()[4] * getpTot();
                value[5] = result.getUf()[5] * getpTot();
                value[6] = result.getFinalS() * 100;
                value[7] = 0; // Conversion from kG.m to T.cm
            }
                    
        } catch (RungeKuttaException e) {
                System.out.println(getCharge() + " " + getX0() + " " + getY0() + " " + getZ0() + " " + getpTot() + " " + getTheta() + " " + getPhi());
                e.printStackTrace();
        }
        return value;

    }
    
    
    private class SphericalBoundarySwimStopper implements IStopper {

        private double _finalPathLength = Double.NaN;

        private double _Rad;

        /**
         * A swim stopper that will stop if the boundary of a plane is crossed
         *
         * @param maxR
         *            the max radial coordinate in meters.
         */
        private SphericalBoundarySwimStopper(double Rad) {
                // DC reconstruction units are cm. Swim units are m. Hence scale by
                // 100
                _Rad = Rad;
        }

        @Override
        public boolean stopIntegration(double t, double[] y) {

                double r = Math.sqrt(y[0] * y[0] + y[1] * y[1] + y[2] * y[2]) * 100.;

                return (r > _Rad);

        }

        /**
         * Get the final path length in meters
         *
         * @return the final path length in meters
         */
        @Override
        public double getFinalT() {
                return _finalPathLength;
        }

        /**
         * Set the final path length in meters
         *
         * @param finalPathLength
         *            the final path length in meters
         */
        @Override
        public void setFinalT(double finalPathLength) {
                _finalPathLength = finalPathLength;
        }
    }
    /**
     * 
     * @param Rad
     * @return state  x,y,z,px,py,pz, pathlength, iBdl at the surface 
     */
    public double[] SwimToSphere(double Rad) {

        double[] value = new double[8];
        // using adaptive stepsize
        if(this.isSwimUnPhys()==true)
            return null;
        SphericalBoundarySwimStopper stopper = new SphericalBoundarySwimStopper(Rad);
            
        SwimTrajectory st = PC.CF.swim(getCharge(), getX0(), getY0(), getZ0(), getpTot(), getTheta(), getPhi(), stopper, getMaxPathLength(), getStepSize(),
                        0.0005);
        if(st==null)
            return null;
        st.computeBDL(PC.CP);
        // st.computeBDL(compositeField);

        double[] lastY = st.lastElement();

        value[0] = lastY[0] * 100; // convert back to cm
        value[1] = lastY[1] * 100; // convert back to cm
        value[2] = lastY[2] * 100; // convert back to cm
        value[3] = lastY[3] * getpTot(); // normalized values
        value[4] = lastY[4] * getpTot();
        value[5] = lastY[5] * getpTot();
        value[6] = lastY[6] * 100;
        value[7] = lastY[7] * 10; // Conversion from kG.m to T.cm

        return value;

    }

    // added for swimming to outer detectors
    private class PlaneBoundarySwimStopper implements IStopper {

        private double _finalPathLength = Double.NaN;
        private double _d;
        private Vector3D _n;
        private double _dist2plane;
        private int _dir;

        /**
         * A swim stopper that will stop if the boundary of a plane is crossed
         *
         * @param maxR
         *            the max radial coordinate in meters.
         */
        private PlaneBoundarySwimStopper(double d, Vector3D n, int dir) {
                // DC reconstruction units are cm. Swim units are m. Hence scale by
                // 100
                _d = d;
                _n = n;
                _dir = dir;
        }

        @Override
        public boolean stopIntegration(double t, double[] y) {
            double dtrk = y[0] * _n.x() + y[1] * _n.y() + y[2] * _n.z();

            double accuracy = 20e-6; // 20 microns
            // System.out.println(" dist "+dtrk*100+ " state "+y[0]*100+",
            // "+y[1]*100+" , "+y[2]*100);
            if (_dir < 0) {
                    return dtrk < _d;
            } else {
                    return dtrk > _d;
            }

        }

        @Override
        public double getFinalT() {

                return _finalPathLength;
        }

        /**
         * Set the final path length in meters
         *
         * @param finalPathLength
         *            the final path length in meters
         */
        @Override
        public void setFinalT(double finalPathLength) {
                _finalPathLength = finalPathLength;
        }
    }
    /**
     * 
     * @param d_cm
     * @param n
     * @param dir
     * @return return state  x,y,z,px,py,pz, pathlength, iBdl at the plane surface in the lab frame
     */
    public double[] SwimToPlaneBoundary(double d_cm, Vector3D n, int dir) {

        double[] value = new double[8];
        if(this.isSwimUnPhys())
            return null;
        double d = d_cm / 100;
        
        double hdata[] = new double[3];
        // using adaptive stepsize

        // the new swim to plane in swimmer
        Plane plane = new Plane(n.x(), n.y(), n.z(), d);
        SwimTrajectory st;
        try {

            st = PC.CF.swim(getCharge(), getX0(), getY0(), getZ0(), getpTot(), getTheta(), getPhi(), plane, getAccuracy(), getMaxPathLength(), getStepSize(),
                            cnuphys.swim.Swimmer.CLAS_Tolerance, hdata);

            st.computeBDL(PC.CP);

            double[] lastY = st.lastElement();
            
            value[0] = lastY[0] * 100; // convert back to cm
            value[1] = lastY[1] * 100; // convert back to cm
            value[2] = lastY[2] * 100; // convert back to cm
            value[3] = lastY[3] * getpTot(); // normalized values
            value[4] = lastY[4] * getpTot();
            value[5] = lastY[5] * getpTot();
            value[6] = lastY[6] * 100;
            value[7] = lastY[7] * 10; // Conversion from kG.m to T.cm

            // System.out.println("\nCOMPARE plane swims DIRECTION = " +
            // dir);
            // for (int i = 0; i < 8; i++) {
            // System.out.print(String.format("%-8.5f ", value[i]));
            // }

         
        } catch (RungeKuttaException e) {
                e.printStackTrace();
        }
        return value;

    }

    
    
    private class BeamLineSwimStopper implements IStopper {

        private double _finalPathLength = Double.NaN;

        private double _xB;
        private double _yB;
        double min = Double.POSITIVE_INFINITY;
        double thetaRad = Math.toRadians(getTheta());
        double phiRad = Math.toRadians(getPhi());
        double pz = getpTot() * Math.cos(thetaRad);
        private BeamLineSwimStopper(double xB, double yB) {
                // DC reconstruction units are cm. Swim units are m. Hence scale by
                // 100
                _xB = xB;
                _yB = yB;
        }

        @Override
        public boolean stopIntegration(double t, double[] y) {

                double r = Math.sqrt((_xB-y[0]* 100.) * (_xB-y[0]* 100.) + (_yB-y[1]* 100.) * (_yB-y[1]* 100.));
                if(r<min && y[2]<2.0) //start at about 2 meters before target.  Avoid inbending stopping when P dir changes
                    min = r;
                return (r > min );

        }

        /**
         * Get the final path length in meters
         *
         * @return the final path length in meters
         */
        @Override
        public double getFinalT() {
                return _finalPathLength;
        }

        /**
         * Set the final path length in meters
         *
         * @param finalPathLength
         *            the final path length in meters
         */
        @Override
        public void setFinalT(double finalPathLength) {
                _finalPathLength = finalPathLength;
        }
    }
    
    public double[] SwimToBeamLine(double xB, double yB) {

        double[] value = new double[8];
        
        if(this.isSwimUnPhys()==true)
            return null;
        BeamLineSwimStopper stopper = new BeamLineSwimStopper(xB, yB);

        SwimTrajectory st = PC.CF.swim(getCharge(), getX0(), getY0(), getZ0(), getpTot(), getTheta(), getPhi(), stopper, getMaxPathLength(), getStepSize(),
                        0.0005);
        if(st==null)
            return null;
        st.computeBDL(PC.CP);
        // st.computeBDL(compositeField);

        double[] lastY = st.lastElement();

        value[0] = lastY[0] * 100; // convert back to cm
        value[1] = lastY[1] * 100; // convert back to cm
        value[2] = lastY[2] * 100; // convert back to cm
        value[3] = lastY[3] * getpTot(); // normalized values
        value[4] = lastY[4] * getpTot();
        value[5] = lastY[5] * getpTot();
        value[6] = lastY[6] * 100;
        value[7] = lastY[7] * 10; // Conversion from kG.m to T.cm

        return value;

    }

    
    public double[] AdaptiveSwimPlane(double px, double py, double pz, double nx, double ny, double nz, double accuracy)  {
//        System.out.println("Don't use yet");

        double[] value = new double[8];
        
        Vector norm = new Vector(nx,ny,nz);
        Point point = new Point(px/100,py/100,pz/100);
        
        cnuphys.adaptiveSwim.geometry.Plane targetPlane = new cnuphys.adaptiveSwim.geometry.Plane(norm, point);

        
        // using adaptive stepsize
        if(this.isSwimUnPhys())
            return null;

        try {
        
            AdaptiveSwimResult result = new AdaptiveSwimResult(false);
            
            PC.AS.swimPlane(getCharge(), getX0(), getY0(), getZ0(), getpTot(), getTheta(), getPhi(), targetPlane,
                            accuracy/100, getrMax(), getStepSize(), cnuphys.swim.Swimmer.getEps(), result);
            
            if(result.getStatus() == AdaptiveSwimmer.SWIM_SUCCESS) {
                value[0] = result.getUf()[0] * 100; // convert back to cm
                value[1] = result.getUf()[1] * 100; // convert back to cm
                value[2] = result.getUf()[2] * 100; // convert back to cm
                value[3] = result.getUf()[3] * getpTot(); // normalized values
                value[4] = result.getUf()[4] * getpTot();
                value[5] = result.getUf()[5] * getpTot();
                value[6] = result.getFinalS() * 100;
                value[7] = 0; // Conversion from kG.m to T.cm
            }
            else {
                return null;
            }
                    
        } catch (AdaptiveSwimException e) {
                e.printStackTrace();
        }        
        return value;

    }
    
    
    public double[] AdaptiveSwimCylinder(double a1x, double a1y, double a1z, double a2x, double a2y, double a2z, double radius, double accuracy)  {
    //    System.out.println("Don't use yet");
        double[] value = new double[8];
        
        radius = radius/100;
        Point a1 = new Point(a1x/100, a1y/100, a1z/100);
        Point a2 = new Point(a2x/100, a2y/100, a2z/100);
        Line centerLine = new Line(a1, a2);
        
        cnuphys.adaptiveSwim.geometry.Cylinder targetCylinder = new cnuphys.adaptiveSwim.geometry.Cylinder(centerLine, radius);

        
        // using adaptive stepsize
        if(this.isSwimUnPhys())
            return null;

        try {
        
            AdaptiveSwimResult result = new AdaptiveSwimResult(false);
            
            PC.AS.swimCylinder(getCharge(), getX0(), getY0(), getZ0(), getpTot(), getTheta(), getPhi(), targetCylinder,
                            accuracy/100, getrMax(), getStepSize(), cnuphys.swim.Swimmer.getEps(), result);

            if(result.getStatus() == AdaptiveSwimmer.SWIM_SUCCESS) {
                value[0] = result.getUf()[0] * 100; // convert back to cm
                value[1] = result.getUf()[1] * 100; // convert back to cm
                value[2] = result.getUf()[2] * 100; // convert back to cm
                value[3] = result.getUf()[3] * getpTot(); // normalized values
                value[4] = result.getUf()[4] * getpTot();
                value[5] = result.getUf()[5] * getpTot();
                value[6] = result.getFinalS() * 100;
                value[7] = 0; // Conversion from kG.m to T.cm
            }
            else {
                return null;
            }
                    
        } catch (AdaptiveSwimException e) {
                e.printStackTrace();
        }        
        return value;

    }

    public double[] AdaptiveSwimRho(double radius, double accuracy)  {
        System.out.println("Don't use yet");

        double[] value = new double[8];

        radius = radius/100;
        // using adaptive stepsize
        if(this.isSwimUnPhys())
            return null;

        try {
        
            AdaptiveSwimResult result = new AdaptiveSwimResult(false);
            
            PC.AS.swimRho(getCharge(), getX0(), getY0(), getZ0(), getpTot(), getTheta(), getPhi(), radius, 
                          accuracy/100, getrMax(), getStepSize(), cnuphys.swim.Swimmer.getEps(), result);

            if(result.getStatus() == AdaptiveSwimmer.SWIM_SUCCESS) {
                value[0] = result.getUf()[0] * 100; // convert back to cm
                value[1] = result.getUf()[1] * 100; // convert back to cm
                value[2] = result.getUf()[2] * 100; // convert back to cm
                value[3] = result.getUf()[3] * getpTot(); // normalized values
                value[4] = result.getUf()[4] * getpTot();
                value[5] = result.getUf()[5] * getpTot();
                value[6] = result.getFinalS() * 100;
                value[7] = 0; // Conversion from kG.m to T.cm
            }
            else {
                return null;
            }
                    
        } catch (AdaptiveSwimException e) {
                e.printStackTrace();
        }
        return value;

    }

    /**
     * @return the _x0
     */
    public double getX0() {
        return _x0;
    }

    /**
     * @param _x0 the _x0 to set
     */
    public void setX0(double _x0) {
        this._x0 = _x0;
    }

    /**
     * @return the _y0
     */
    public double getY0() {
        return _y0;
    }

    /**
     * @param _y0 the _y0 to set
     */
    public void setY0(double _y0) {
        this._y0 = _y0;
    }

    /**
     * @return the _z0
     */
    public double getZ0() {
        return _z0;
    }

    /**
     * @param _z0 the _z0 to set
     */
    public void setZ0(double _z0) {
        this._z0 = _z0;
    }

    /**
     * @return the _phi
     */
    public double getPhi() {
        return _phi;
    }

    /**
     * @param _phi the _phi to set
     */
    public void setPhi(double _phi) {
        this._phi = _phi;
    }

    /**
     * @return the _theta
     */
    public double getTheta() {
        return _theta;
    }

    /**
     * @param _theta the _theta to set
     */
    public void setTheta(double _theta) {
        this._theta = _theta;
    }

    /**
     * @return the _pTot
     */
    public double getpTot() {
        return _pTot;
    }

    /**
     * @param _pTot the _pTot to set
     */
    public void setpTot(double _pTot) {
        this._pTot = _pTot;
    }

    /**
     * @return the _rMax
     */
    public double getrMax() {
        return _rMax;
    }

    /**
     * @param _rMax the _rMax to set
     */
    public void setrMax(double _rMax) {
        this._rMax = _rMax;
    }

    /**
     * @return the _maxPathLength
     */
    public double getMaxPathLength() {
        return _maxPathLength;
    }

    /**
     * @param _maxPathLength the _maxPathLength to set
     */
    public void setMaxPathLength(double _maxPathLength) {
        this._maxPathLength = _maxPathLength;
    }

    /**
     * @return the SwimUnPhys
     */
    public boolean isSwimUnPhys() {
        return SwimUnPhys;
    }

    /**
     * @param SwimUnPhys the SwimUnPhys to set
     */
    public void setSwimUnPhys(boolean SwimUnPhys) {
        this.SwimUnPhys = SwimUnPhys;
    }

    /**
     * @return the _charge
     */
    public int getCharge() {
        return _charge;
    }

    /**
     * @param _charge the _charge to set
     */
    public void setCharge(int _charge) {
        this._charge = _charge;
    }

    /**
     * @return the SWIMZMINMOM
     */
    public double getSWIMZMINMOM() {
        return SWIMZMINMOM;
    }

    /**
     * @param SWIMZMINMOM the SWIMZMINMOM to set
     */
    public void setSWIMZMINMOM(double SWIMZMINMOM) {
        this.SWIMZMINMOM = SWIMZMINMOM;
    }

    /**
     * @return the MINTRKMOM
     */
    public double getMINTRKMOM() {
        return MINTRKMOM;
    }

    /**
     * @param MINTRKMOM the MINTRKMOM to set
     */
    public void setMINTRKMOM(double MINTRKMOM) {
        this.MINTRKMOM = MINTRKMOM;
    }

    /**
     * @return the accuracy
     */
    public double getAccuracy() {
        return accuracy;
    }

    /**
     * @param accuracy the accuracy to set
     */
    public void setAccuracy(double accuracy) {
        this.accuracy = accuracy;
    }

    /**
     * @return the tolerance
     */
    public double getTolerance() {
        return tolerance;
    }

    /**
     * @param tolerance the tolerance to set
     */
    public void setTolerance(double tolerance) {
        this.tolerance = tolerance;
    }

    /**
     * @return the stepSize
     */
    public double getStepSize() {
        return stepSize;
    }

    /**
     * @param stepSize the stepSize to set
     */
    public void setStepSize(double stepSize) {
        this.stepSize = stepSize;
    }
    
     public void checkR(double _x0, double _y0, double _z0) {
        this.SwimUnPhys=false;
        if(Math.sqrt(_x0*_x0 + _y0*_y0)>this._rMax || 
                Math.sqrt(_x0*_x0 + _y0*_y0 + _z0*_z0)>this._maxPathLength)
            this.SwimUnPhys=true;
    }

}
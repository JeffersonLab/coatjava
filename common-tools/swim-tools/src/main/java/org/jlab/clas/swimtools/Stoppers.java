package org.jlab.clas.swimtools;

import cnuphys.rk4.IStopper;
import cnuphys.swim.SwimTrajectory;
import java.util.ArrayList;
import org.jlab.geom.prim.Line3D;
import org.jlab.geom.prim.Point3D;
import org.jlab.geom.prim.Vector3D;

/**
 *
 * @author ziegler
 */
public class Stoppers {
    
    public static class CylindricalBoundarySwimStopper implements IStopper {

        private double _finalPathLength = Double.NaN;
        private double _Rad;
        double max = -1.0;

        /**
         * A swim stopper that will stop if the boundary of a plane is crossed
         *
         * @param Rad the max radial coordinate in meters.
         */
        public CylindricalBoundarySwimStopper(double Rad) {
            _Rad = Rad;
        }

        @Override
        public boolean stopIntegration(double t, double[] y) {
            double r = Math.sqrt(y[0] * y[0] + y[1] * y[1]) * 100.;
            return (r < max || r > _Rad); // stop intergration at closest distance to the cylinder
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

    public static class SphericalBoundarySwimStopper implements IStopper {

        private double _finalPathLength = Double.NaN;
        private double _Rad;

        /**
         * A swim stopper that will stop if the boundary of a plane is crossed
         *
         * @param maxR the max radial coordinate in meters.
         */
        public SphericalBoundarySwimStopper(double Rad) {
            _Rad = Rad;
        }

        @Override
        public boolean stopIntegration(double t, double[] y) {
            double r = Math.sqrt(y[0] * y[0] + y[1] * y[1] + y[2] * y[2]) * 100.;
            return r > _Rad;
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

    // added for swimming to outer detectors
    public static class PlaneBoundarySwimStopper implements IStopper {

        private double _finalPathLength = Double.NaN;
        private double _d;
        private Vector3D _n;
        private double _dist2plane;
        private int _dir;

        /**
         * A swim stopper that will stop if the boundary of a plane is crossed
         *
         * @param d
         * @param dir
         * @param n
         */
        public PlaneBoundarySwimStopper(double d, Vector3D n, int dir) {
            _d = d;
            _n = n;
            _dir = dir;
        }

        @Override
        public boolean stopIntegration(double t, double[] y) {
            double dtrk = y[0] * _n.x() + y[1] * _n.y() + y[2] * _n.z();
            //double accuracy = 20e-6; // 20 microns
            return _dir < 0 ? dtrk < _d : dtrk > _d;
        }

        @Override
        public double getFinalT() {
            return _finalPathLength;
        }

        /**
         * Set the final path length in meters
         *
         * @param finalPathLength the final path length in meters
         */
        @Override
        public void setFinalT(double finalPathLength) {
            _finalPathLength = finalPathLength;
        }
    }

    public static class LineSwimStopper implements IStopper {

        private double _finalPathLength = Double.NaN;
        private Line3D _l;
        private Point3D _p;
        double min = 999;
        public LineSwimStopper(Line3D l) {
            _l =l;
            _p = new Point3D();
        }

        @Override
        public boolean stopIntegration(double t, double[] y) {
            _p.set(y[0]* 100.0, y[1]* 100.0, y[2]* 100.0);
            double doca = _l.distance(_p).length();
            if(doca<min) {
                min = doca;
            }
            return (doca > min );
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
    
    public static class ZSwimStopper implements IStopper {

        private double _finalPathLength = Double.NaN;
        private double _Z;
        private int _dir;
        
        public ZSwimStopper(double Z, int dir) {
            _Z = Z;
           _dir = dir;
        }

        @Override
        public boolean stopIntegration(double t, double[] y) {
            double z = y[2] * 100.;
            return _dir>0 ? z>_Z : z<_Z;
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

    public static class DCASwimStopper implements IStopper {

        public DCASwimStopper(SwimTrajectory swimTraj) { 
            _swimTraj = swimTraj;
            for(int i = 0; i < _swimTraj.size()-1; i++) { 
                polylines.add(new Line3D(_swimTraj.get(i)[0],_swimTraj.get(i)[1],_swimTraj.get(i)[2],
                        _swimTraj.get(i+1)[0],_swimTraj.get(i+1)[1],_swimTraj.get(i+1)[2]));
            }
        }

        private ArrayList<Line3D> polylines = new ArrayList<>();
        private SwimTrajectory _swimTraj;
        private double _finalPathLength = Double.NaN;
        private double _doca = Double.POSITIVE_INFINITY;
        
        @Override
        public boolean stopIntegration(double t, double[] y) {
           
            Point3D dcaCand = new Point3D(y[0],y[1],y[2]); 
            double maxDoca = Double.POSITIVE_INFINITY;
            
            for(Line3D l : polylines) { 
                if(l.distance(dcaCand).length()<maxDoca) {
                    maxDoca=l.distance(dcaCand).length();
                } 
            }
            if(maxDoca<_doca) {
                _doca = maxDoca; 
                return false;
            }
            return true;
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

    public static class BeamLineSwimStopper implements IStopper {

        private double _finalPathLength = Double.NaN;
        private double _xB;
        private double _yB;
        double min = Double.POSITIVE_INFINITY;
        public BeamLineSwimStopper(double xB, double yB) {
            _xB = xB;
            _yB = yB;
        }

        @Override
        public boolean stopIntegration(double t, double[] y) {

            double r = Math.sqrt((_xB-y[0]* 100.) * (_xB-y[0]* 100.) + (_yB-y[1]* 100.) * (_yB-y[1]* 100.));
            // Start at about 2 meters before target.
            // Avoid inbending stopping when P dir changes:
            if (r<min && y[2]<2.0)
                min = r;
            return r > min ;

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
    
}
package org.jlab.clas.tracking.trackrep;
import org.jlab.clas.tracking.kalmanfilter.Units;
import org.jlab.clas.tracking.kalmanfilter.helical.KFitter;
import org.jlab.geom.prim.Point3D;
import org.jlab.geom.prim.Vector3D;

/**
 *
 * @author ziegler
 */
public class Helix {

    //Setting for Negative polarity of the solenoid.
    // turningSign = -charge*polarity = + charge for nominal configuration
    
    private double _B;
    private double _d0;
    private double _phi0;
    private double _cosphi0;
    private double _sinphi0;
    private double _omega;
    private double _z0;
    private double _tanL;
    private int    _turningSign;
    private double _R;
        
    private double _xd; 
    private double _yd;
    private double _xc;
    private double _yc;
    private double _xb;
    private double _yb;
    private double _x;
    private double _y;
    private double _z;
    private double _px;
    private double _py;
    private double _pz;
    
    private Units units = Units.CM; //default
    
    public final static double LIGHTVEL = 0.0000299792458;       // velocity of light - conversion factor from radius in cm to momentum in GeV/c 
    
    public Helix() {
        
    }
    
    public Helix(double d0, double phi0, double omega, double z0, double tanL,
            int turningSign, double B, double xb, double yb, Units unit) {
        _d0          = d0;
        _phi0        = phi0;
        _cosphi0     = Math.cos(phi0);
        _sinphi0     = Math.sin(phi0);
        _omega       = omega;
        _z0          = z0;
        _tanL        = tanL;
        _turningSign = turningSign;
        _B           = B;
        _xb          = xb;
        _yb          = yb;
        units        = unit;
        this.update();
    }
    
    public Helix(double x0, double y0, double z0, double px0, double py0, double pz0,
            int q, double B, double xb, double yb, Units unit) {
        _turningSign = q; 
        _B           = B;
        units        = unit;
        double pt    = Math.sqrt(px0*px0 + py0*py0);
        _R           = pt/(B*this.getLightVelocity());
        _cosphi0     = px0/pt;
        _sinphi0     = py0/pt;
        _phi0        = Math.atan2(py0, px0);
        _tanL        = pz0/pt;
        _z0          = z0;
        _omega       = (double) KFitter.polarity*_turningSign/_R ; 
        double S = Math.sin(_phi0);
        double C = Math.cos(_phi0);
        if(Math.abs(S)>=Math.abs(C)) {
            _d0 = -(x0-xb)/S;
        } else {
            _d0 = (y0-yb)/C;
        }
        _xb = xb;
        _yb = yb;
        this.update();
    }
    
    
    public Units getUnits() {
        return this.units;
    }

    public final double getLightVelocity() {
        return LIGHTVEL*units.value();
    }    
        
    public void reset(double d0, double phi0, double omega, double z0, double tanL, double B){
        _d0          = d0;
        _phi0        = phi0;
        _cosphi0     = Math.cos(phi0);
        _sinphi0     = Math.sin(phi0);
        _omega       = omega;
        _z0          = z0;
        _tanL        = tanL;
        _B           = B;
        this.update();
    }
    
    public final void update() {
        setR(1./Math.abs(getOmega()));
        _xd = -getD0()*getSinphi0()+_xb;
        _yd =  getD0()*getCosphi0()+_yb;
        _xc = -(-(double)KFitter.polarity*_turningSign*_R + _d0)*getSinphi0()+_xb; 
        _yc =  (-(double)KFitter.polarity*_turningSign*_R + _d0)*getCosphi0()+_yb;
        _x  = getX(0);
        _y  = getY(0);
        _z  = getZ(0);
        _px = getPx(getB(), 0);
        _py = getPy(getB(), 0);
        _pz = getPz(getB()); 
    }
    
    public double getB() {
        return _B;
    }

    public double getD0() {
        return _d0;
    }

    public double getPhi0() {
        return _phi0;
    }

    public double getCosphi0() {
        return _cosphi0;
    }

    public double getSinphi0() {
        return _sinphi0;
    }

    public double getOmega() {
        return _omega;
    }

    public double getZ0() {
        return _z0;
    }

    public double getTanL() {
        return _tanL;
    }

    public int getTurningSign() {
        return _turningSign;
    }

    public double getR() {
        return _R;
    }

    public void setR(double _R) {
        this._R = _R;
    }


    public double getXc() {
        return _xc;
    }

    public double getYc() {
        return _yc;
    }

    public double getXb() {
        return _xb;
    }

    public double getYb() {
        return _yb;
    }

    public double getPhi(double l) {
        return getPhi0() + getOmega()*l;
    }
    
    public double getPt(double B) {
        return getLightVelocity() * getR() * B;
    }
    
    public double getX(double l){
        double s = (double) -KFitter.polarity; 
        return getXc() + s*getTurningSign()*getR()*Math.sin(getPhi(l)); 
    }
    
    public double getY(double l){
    double s = (double) -KFitter.polarity; 
        return getYc() - s*getTurningSign()*getR()*Math.cos(getPhi(l));
    }
    
    public double getZ(double l){
        return getZ0() -l*getTanL();
    }
    
    public double getPx(double B, double l) {
        return getPt(B) * Math.cos(getPhi(l));
    }
    
    public double getPy(double B, double l) {
        return getPt(B) * Math.sin(getPhi(l));
    }
    
    public double getPz(double B) {
        return getPt(B)*getTanL();
    }

    public double getX() {
        return this.getX(0);
    }

    public double getY() {
        return this.getY(0);
    }

    public double getZ() {
        return this.getZ(0);
    }

    public double getPx() {
        return this.getPx(this.getB(), 0);
    }

    public double getPy() {
        return this.getPy(this.getB(), 0);
    }

    public double getPz() {
        return this.getPz(this.getB());
    }

    /** Intersect the helix with the complete plane n.(r-p)=0, including nz. */
    public double getLAtPlane3D(double px, double py, double pz,
                                double nx, double ny, double nz,
                                double xNear, double yNear) {
        // Start at the point on the helix circle nearest the module. The projected trace of a
        // nearly tangential plane can miss the circle even when the full 3-D plane intersects it.
        double phi0 = Math.atan2(_yd - getYc(), _xd - getXc());
        double phiNear = Math.atan2(yNear - getYc(), xNear - getXc());
        double dphi = phiNear - phi0;
        if (dphi >  Math.PI) dphi -= 2.0 * Math.PI;
        if (dphi < -Math.PI) dphi += 2.0 * Math.PI;
        double l = dphi / getOmega();
        if (!Double.isFinite(l)) return Double.NaN;

        final double dl = 1.0e-3;
        for (int i = 0; i < 12; i++) {
            double f = nx * (getX(l) - px) + ny * (getY(l) - py)
                     + nz * (getZ(l) - pz);
            if (Math.abs(f) < 1.0e-7) return l;
            double derivative = nx * (getX(l + dl) - getX(l - dl)) / (2 * dl)
                              + ny * (getY(l + dl) - getY(l - dl)) / (2 * dl)
                              + nz * (getZ(l + dl) - getZ(l - dl)) / (2 * dl);
            if (!Double.isFinite(derivative) || Math.abs(derivative) < 1.0e-10)
                return Double.NaN;
            double step = f / derivative;
            if (Math.abs(step) > 100) step = Math.copySign(100, step);
            l -= step;
            if (!Double.isFinite(l)) return Double.NaN;
        }
        double residual = nx * (getX(l) - px) + ny * (getY(l) - py)
                        + nz * (getZ(l) - pz);
        return Math.abs(residual) < 1.0e-5 ? l : Double.NaN;
    }

    /**
     * Intersect this helix with a cylinder of radius {@code radius} about an arbitrary axis.
     * The supplied starting value normally comes from the closed-form beam-axis solution.
     *
     * @return the local root nearest {@code lStart}, or NaN when no root can be established
     */
    public double getLAtCylinder3D(double ax, double ay, double az,
                                   double ux, double uy, double uz,
                                   double radius, double lStart) {
        double un = Math.sqrt(ux * ux + uy * uy + uz * uz);
        if (!(un > 0) || !Double.isFinite(lStart)) return Double.NaN;
        ux /= un;
        uy /= un;
        uz /= un;

        double l = lStart;
        for (int i = 0; i < 12; i++) {
            double f = radialMiss(l, ax, ay, az, ux, uy, uz, radius);
            if (!Double.isFinite(f)) break;
            if (Math.abs(f) < 1.0e-7) return l;

            double derivative = radialMissDerivative(l, ax, ay, az, ux, uy, uz);
            if (!Double.isFinite(derivative) || Math.abs(derivative) < 1.0e-10) break;
            double step = f / derivative;
            if (Math.abs(step) > 100.0) step = Math.copySign(100.0, step);
            l -= step;
            if (!Double.isFinite(l)) break;
        }
        double residual = radialMiss(l, ax, ay, az, ux, uy, uz, radius);
        if (Double.isFinite(residual) && Math.abs(residual) < 1.0e-5) return l;

        // Safeguard Newton near tangencies by bracketing the first local root on each side.
        // The quarter-turn limit prevents a failure from selecting the opposite crossing.
        double maxSpan = Math.min(200.0, Math.PI / (2.0 * Math.abs(getOmega())));
        double left = lStart;
        double right = lStart;
        double fLeft = radialMiss(left, ax, ay, az, ux, uy, uz, radius);
        double fRight = fLeft;
        double span = 0.5;
        while (Double.isFinite(fLeft) && Double.isFinite(fRight)
                && Math.abs(right - lStart) < maxSpan) {
            double nextSpan = Math.min(span, maxSpan);
            double nextLeft = lStart - nextSpan;
            double nextRight = lStart + nextSpan;
            double nextFLeft = radialMiss(nextLeft, ax, ay, az, ux, uy, uz, radius);
            double nextFRight = radialMiss(nextRight, ax, ay, az, ux, uy, uz, radius);

            double rootLeft = bracketedCylinderRoot(nextLeft, left, nextFLeft, fLeft,
                    ax, ay, az, ux, uy, uz, radius);
            double rootRight = bracketedCylinderRoot(right, nextRight, fRight, nextFRight,
                    ax, ay, az, ux, uy, uz, radius);
            if (Double.isFinite(rootLeft) || Double.isFinite(rootRight)) {
                if (!Double.isFinite(rootLeft)) return rootRight;
                if (!Double.isFinite(rootRight)) return rootLeft;
                return Math.abs(rootLeft - lStart) <= Math.abs(rootRight - lStart)
                        ? rootLeft : rootRight;
            }
            left = nextLeft;
            right = nextRight;
            fLeft = nextFLeft;
            fRight = nextFRight;
            span *= 2.0;
        }
        return Double.NaN;
    }

    private double radialMissDerivative(double l, double ax, double ay, double az,
                                        double ux, double uy, double uz) {
        double wx = getX(l) - ax;
        double wy = getY(l) - ay;
        double wz = getZ(l) - az;
        double along = wx * ux + wy * uy + wz * uz;
        double qx = wx - along * ux;
        double qy = wy - along * uy;
        double qz = wz - along * uz;
        double distance = Math.sqrt(qx * qx + qy * qy + qz * qz);
        if (!(distance > 0)) return Double.NaN;

        double s = -KFitter.polarity;
        double phi = getPhi(l);
        double vx = s * getTurningSign() * getR() * getOmega() * Math.cos(phi);
        double vy = s * getTurningSign() * getR() * getOmega() * Math.sin(phi);
        double vz = -getTanL();
        return (qx * vx + qy * vy + qz * vz) / distance;
    }

    private double bracketedCylinderRoot(double lo, double hi, double flo, double fhi,
                                         double ax, double ay, double az,
                                         double ux, double uy, double uz, double radius) {
        if (!Double.isFinite(flo) || !Double.isFinite(fhi) || flo * fhi > 0) {
            return Double.NaN;
        }
        if (Math.abs(flo) < 1.0e-7) return lo;
        if (Math.abs(fhi) < 1.0e-7) return hi;
        for (int i = 0; i < 80; i++) {
            double mid = 0.5 * (lo + hi);
            double fm = radialMiss(mid, ax, ay, az, ux, uy, uz, radius);
            if (!Double.isFinite(fm)) return Double.NaN;
            if (Math.abs(fm) < 1.0e-7 || Math.abs(hi - lo) < 1.0e-9) return mid;
            if (flo * fm <= 0) {
                hi = mid;
            } else {
                lo = mid;
                flo = fm;
            }
        }
        return Double.NaN;
    }

    private double radialMiss(double l, double ax, double ay, double az,
                              double ux, double uy, double uz, double radius) {
        double wx = getX(l) - ax;
        double wy = getY(l) - ay;
        double wz = getZ(l) - az;
        double along = wx * ux + wy * uy + wz * uz;
        double dx = wx - along * ux;
        double dy = wy - along * uy;
        double dz = wz - along * uz;
        return Math.sqrt(dx * dx + dy * dy + dz * dz) - radius;
    }

    public double getLAtPlane(double X1, double Y1, double X2, double Y2, 
            double tolerance) {
        // Find the intersection of the helix circle with the module plane projection in XY which is a line
        // Plane representative line equation y = mx +d
        double X = 0;
        double Y = 0;
        if (X2 - X1 == 0) {
            X = X1;
            double y1 = getYc() + Math.sqrt(getR() * getR() - (X - getXc()) * (X - getXc()));
            double y2 = getYc() - Math.sqrt(getR() * getR() - (X - getXc()) * (X - getXc()));

            if (Math.abs(y1 - Y1) < Math.abs(Y2 - Y1)+tolerance) {
                Y = y1;
            } else {
                if (Math.abs(y2 - Y2) < Math.abs(Y2 - Y1)+tolerance) {
                    Y = y2;
                }
            }
        }
        if (Y2 - Y1 == 0) {
            Y = Y1;
            double x1 = getXc() + Math.sqrt(getR() * getR() - (Y - getYc()) * (Y - getYc()));
            double x2 = getXc() - Math.sqrt(getR() * getR() - (Y - getYc()) * (Y - getYc()));

            if (Math.abs(x1 - X1) < Math.abs(X2 - X1)+tolerance) {
                X = x1;
            } else {
                if (Math.abs(x2 - X1) < Math.abs(X2 - X1)+tolerance) {
                    X = x2;
                }
            }
        }

        if (X2 - X1 != 0 && Y2 - Y1 != 0) {
            double m = (Y2 - Y1) / (X2 - X1);
            double d = Y1 - X1 * m;

            //double del = r*r*(1+m*m) - (yc-m*xc-d)*(yc-m*xc-d);
            double del = (getXc() + (-d + getYc()) * m) * (getXc() + (-d + getYc()) * m) - 
                    (1 + m * m) * (getXc() * getXc() + (d - getYc()) * (d - getYc()) - getR()*getR());
            if (del < 0) {
                //System.err.println("Helix Plane Intersection error - Returning 0 ");
                return 0;
            } 
            double x1 = (getXc() + (-d + getYc()) * m + Math.sqrt(del)) / (1 + m * m);
            double x2 = (getXc() + (-d + getYc()) * m - Math.sqrt(del)) / (1 + m * m);

            if (Math.abs(x1 - X1) < Math.abs(X2 - X1)+tolerance) {
                X = x1;
            } else {
                if (Math.abs(x2 - X1) < Math.abs(X2 - X1)+tolerance) {
                    X = x2;
                }
            }
            double y1 = getYc() + Math.sqrt(getR() * getR() - (X - getXc()) * (X - getXc()));
            double y2 = getYc() - Math.sqrt(getR() * getR() - (X - getXc()) * (X - getXc()));

            if (Math.abs(y1 - Y1) < Math.abs(Y2 - Y1)+tolerance) {
                Y = y1;
            } else {
                if (Math.abs(y2 - Y1) < Math.abs(Y2 - Y1)+tolerance) {
                    Y = y2;
                }
            }
        }
        
        double phi1 = Math.atan2(_yd -getYc(), _xd -getXc());
        double phi2 = Math.atan2(Y -getYc(), X -getXc());
        double dphi = phi2 - phi1;
        //  put dphi in (-pi, pi)
        if (dphi > Math.PI) {
            dphi -= 2. * Math.PI;
        }
        if (dphi < -Math.PI) {
            dphi += 2. * Math.PI;
        }
        
        return dphi/getOmega();
    }
    
    
    
    public Point3D getHelixPointAtPlane(double X1, double Y1, double X2, double Y2, 
            double tolerance) {
        double l = getLAtPlane(X1, Y1, X2, Y2, tolerance);
        return new Point3D(getX(l),getY(l),getZ(l));
    }
    
    public Vector3D getMomentumAtPlane(double X1, double Y1, double X2, double Y2, 
            double tolerance) {
        double l = getLAtPlane(X1, Y1, X2, Y2, tolerance);
        return new Vector3D(getPx(getB(),l),getPy(getB(),l),getPz(getB()));
    }
    
    public double getLAtR(double r) {
        
        double x;
        double y;
        double xp = 0;
        double xm = 0;
        double yp = 0;
        double ym = 0;
        if( Math.abs(getYc()) >1.e-09) {
        double a = 0.5 * (r * r - getR() * getR() + getXc() * getXc() + getYc() * getYc()) / getYc();
            double b = -getXc() / getYc();

            double delta = a * a * b * b - (1 + b * b) * (a * a - r * r);

            xp = (-a * b + Math.sqrt(delta)) / (1 + b * b);
            xm = (-a * b - Math.sqrt(delta)) / (1 + b * b);

            yp = a + b * xp;
            ym = a + b * xm;
        } else {
            xm = (getXc()*getXc()-getR()*getR()+r*r)/(2*getXc());
            xp = xm;
            ym = Math.sqrt(r*r-xm*xm); 
            yp = Math.sqrt(r*r-xp*xp);
        }
        //double Cp = new Vector3D(xp,yp,0).asUnit().dot(new Vector3D(Math.cos(getPhi0()), Math.sin(getPhi0()),0));
        //double Cm = new Vector3D(xm,ym,0).asUnit().dot(new Vector3D(Math.cos(getPhi0()), Math.sin(getPhi0()),0));
        double Np = Math.sqrt(xp*xp+yp*yp);
        double Nm = Math.sqrt(xm*xm+ym*ym);
        double Cp = (xp*getCosphi0()+yp*getSinphi0())/Np;
        double Cm = (xm*getCosphi0()+ym*getSinphi0())/Nm;
        if(Cp > Cm) {
            x = xp;
            y = yp;
        } else {
            x = xm;
            y = ym;
        }
       
        double phi1 = Math.atan2(_yd -getYc(), _xd -getXc());
        double phi2 = Math.atan2(y -getYc(), x -getXc());
        double dphi = phi2 - phi1;
        //  put dphi in (-pi, pi)
        if (dphi > Math.PI) {
            dphi -= 2. * Math.PI;
        }
        if (dphi < -Math.PI) {
            dphi += 2. * Math.PI;
        }
        
        return dphi/getOmega();
    }
    
    public Point3D getHelixPointAtR(double r) {
        double l = getLAtR( r);
        return new Point3D(getX(l),getY(l),getZ(l));
    }
    
    public Vector3D getMomentumAtR(double r) {
        double l = getLAtR( r);
        return new Vector3D(getPx(getB(),l),getPy(getB(),l),getPz(getB()));
    }
    
    public double getLAtZ(double z) {
        return (z - getZ0())/getTanL();
    }
    
    public Point3D getHelixPointAtZ(double z) {
        double l = getLAtZ( z);
        return new Point3D(getX(l),getY(l),z);
    }
    
    public Vector3D getMomentumAtZ(double z) {
        double l = getLAtZ( z);
        return new Vector3D(getPx(getB(),l),getPy(getB(),l),getPz(getB()));
    }
    
    /**
     * Computes the path length between to points at radius rMin and rMax of the
     * helix.
     *
     * @param rMin the radius of the point from which to measure.
     * @param rMax the radius of the point to which to measure.
     *
     * helix parametrization is X(l) = xc - s*R*sin(phi0+omega*l) Y(l) = yc +
     * s*R*cos(phi0+omega*l) Z(l) = z0 - l*tanL
     *
     * d^2 = (dX/dl)^2 + (dY/dl)^2 + (dZ/dl)^2
     *
     * pathlength = integral of d(l) from l(rMin) to l(rMax) pathlength =
     * sqrt(R^2omega^2+tanL^2)*(l(rMax)-l(rMin))
     *
     * Written by pilleux
     *
     */
    public double getPathLength(double rMin, double rMax) {

        double s = (double) -KFitter.polarity * getTurningSign();

        if (rMax <= rMin) {
            System.out.print("Cannot compute path length, max radius smaller than min radius ! \n");
            return 0;
        }
        double l0 = this.getLAtR(rMin);
        double l1 = this.getLAtR(rMax);
        double term1 = this.getOmega() * s * this.getR();
        double term2 = this.getTanL();
        return Math.abs((l1 - l0) * Math.sqrt(term1 * term1 + term2 * term2));
    }
    
    @Override
    public String toString() {
        String s = String.format("    drho=%.4f phi0=%.4f radius=%.4f z0=%.4f tanL=%.4f B=%.4f\n", this._d0, this._phi0, this._R, this._z0, this._tanL, this._B);
        s       += String.format("    x0=%.4f y0=%.4f x=%.4f y=%.4f z=%.4f px=%.4f py=%.4f pz=%.4f", this._xb, this._yb, this._x, this._y, this._z, this._px, this._py, this._pz);
        return s;
    }
}

/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.jlab.rec.cvt.patternrec.fit;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.jlab.rec.cvt.cross.Cross;

/**
 *
 * @author veronique
 */
/**
 * Fits a geometric helix to a set of CVT road line segments.
 *
 * <p>The fitter uses a simple geometric helix representation internally:
 * <pre>
 *   x(l) = xc + R cos(phiRef + l/R)
 *   y(l) = yc + R sin(phiRef + l/R)
 *   z(l) = z0 + l tanLambda
 * </pre>
 *
 * <p>The fitted helix is converted to the CVT-style helix parameterization
 * in {@link FitResult}. The class also computes per-segment residuals as the
 * distance of closest approach between the fitted helix and the infinite line
 * associated with each segment.
 */
public class HelixClusterFitter {

    /* ===========================
     * Geometry primitives
     * =========================== */

    /**
     * Minimal immutable-style 3D vector utility used by the fitter.
     *
     * <p>The fields are public for speed and simplicity. Vector operations
     * return new {@code Vec3} instances and do not modify the current object.
     */
    public static class Vec3 {
        public double x, y, z;

        /**
         * Creates a 3D vector.
         *
         * @param x x coordinate
         * @param y y coordinate
         * @param z z coordinate
         */
        public Vec3(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        /**
         * Returns this vector plus another vector.
         *
         * @param o vector to add
         * @return sum vector
         */
        public Vec3 add(Vec3 o) {
            return new Vec3(x + o.x, y + o.y, z + o.z);
        }

        /**
         * Returns this vector minus another vector.
         *
         * @param o vector to subtract
         * @return difference vector
         */
        public Vec3 sub(Vec3 o) {
            return new Vec3(x - o.x, y - o.y, z - o.z);
        }

        /**
         * Returns this vector multiplied by a scalar.
         *
         * @param s scalar multiplier
         * @return scaled vector
         */
        public Vec3 scale(double s) {
            return new Vec3(s * x, s * y, s * z);
        }

        /**
         * Computes the dot product with another vector.
         *
         * @param o other vector
         * @return dot product
         */
        public double dot(Vec3 o) {
            return x * o.x + y * o.y + z * o.z;
        }

        /**
         * Computes the squared Euclidean norm.
         *
         * @return x*x + y*y + z*z
         */
        public double norm2() {
            return dot(this);
        }

        /**
         * Computes the Euclidean norm.
         *
         * @return vector magnitude
         */
        public double norm() {
            return Math.sqrt(norm2());
        }
    }

    /* ===========================
     * Line segment
     * =========================== */

    /**
     * Represents a finite 3D line segment.
     *
     * <p>The segment is parameterized as:
     * <pre>
     *   p(s) = a + s u,  s in [s0, s1]
     * </pre>
     *
     * <p>The same object can also be interpreted as an infinite line by using
     * the same {@code a} and {@code u}, but not clamping {@code s}.
     */
    public static class Segment {
        public final Vec3 a;
        public final Vec3 u;
        public final double s0, s1;

        /**
         * Creates a segment from two endpoints.
         *
         * @param p0 first endpoint
         * @param p1 second endpoint
         */
        public Segment(Vec3 p0, Vec3 p1) {
            a = p0;
            Vec3 d = p1.sub(p0);
            double L = Math.sqrt(d.norm2());
            if (L < 1e-12) {
                throw new IllegalArgumentException("Segment of ~0 length");
            }
            u = d.scale(1.0 / L);
            s0 = 0.0;
            s1 = L;
        }

        /**
         * Returns the point at segment coordinate {@code s}.
         *
         * <p>This method does not clamp {@code s}; callers decide whether the
         * segment is treated as finite or as an infinite line.
         *
         * @param s distance along the segment direction
         * @return point a + s u
         */
        public Vec3 point(double s) {
            return a.add(u.scale(s));
        }

        /**
         * Returns the midpoint of the finite segment.
         *
         * @return segment midpoint
         */
        public Vec3 midpoint() {
            return point(0.5 * (s0 + s1));
        }
    }

    /* ===========================
     * Helix parameters
     * =========================== */

    /**
     * Geometric helix representation used internally during the fit.
     */
    public static class HelixGeom {
        public double xc, yc, R;
        public double phiRef;
        public double z0, tanLambda;
        public double chi2;

        /**
         * Creates a geometric helix.
         *
         * @param xc circle center x
         * @param yc circle center y
         * @param R circle radius
         * @param phiRef reference phase in the transverse plane
         * @param z0 z intercept at l = 0
         * @param tanLambda dz/dl slope
         */
        public HelixGeom(double xc, double yc, double R,
                         double phiRef,
                         double z0, double tanLambda) {
            this.xc = xc;
            this.yc = yc;
            this.R = R;
            this.phiRef = phiRef;
            this.z0 = z0;
            this.tanLambda = tanLambda;
            this.chi2 = 0.0;
        }

        /**
         * Evaluates the helix at transverse arc-length parameter {@code l}.
         *
         * @param l transverse arc-length-like parameter
         * @return helix point at l
         */
        public Vec3 at(double l) {
            double phi = phiRef + l / R;
            return new Vec3(
                xc + R * Math.cos(phi),
                yc + R * Math.sin(phi),
                z0 + l * tanLambda
            );
        }
    }

    /* ===========================
     * Closest approach result
     * =========================== */

    /**
     * Stores the result of a closest-approach calculation.
     */
    public static class CA {
        public final double l;
        public final double s;
        public final Vec3 h;
        public final Vec3 p;

        /**
         * Creates a closest-approach result.
         *
         * @param l helix parameter at closest approach
         * @param s segment or line parameter at closest approach
         * @param h closest point on helix
         * @param p closest point on segment or line
         */
        public CA(double l, double s, Vec3 h, Vec3 p) {
            this.l = l;
            this.s = s;
            this.h = h;
            this.p = p;
        }
    }

    /**
     * Finds the closest approach between a helix and a finite segment.
     *
     * <p>The segment coordinate is clamped to {@code [s0, s1]}. This method is
     * used inside the fit because the detector object is a finite segment.
     *
     * @param h helix
     * @param seg finite segment
     * @param l0 initial guess for helix parameter
     * @return closest-approach result
     */
    public static CA closestApproach(HelixGeom h, Segment seg, double l0) {
        double bestL = l0;
        double bestD2 = Double.POSITIVE_INFINITY;

        double window = Math.max(20.0, 0.25 * Math.abs(h.R));
        int nSteps = 61;

        for (int i = 0; i < nSteps; i++) {
            double l = l0 + window * (2.0 * i / (nSteps - 1) - 1.0);
            double d2 = dist2At(h, seg, l);
            if (d2 < bestD2) {
                bestD2 = d2;
                bestL = l;
            }
        }

        double l = bestL;
        for (int it = 0; it < 8; it++) {
            double eps = Math.max(1e-3, 1e-3 * Math.abs(h.R));
            double l1 = l - eps;
            double l2 = l + eps;

            double d1 = dist2At(h, seg, l1);
            double d0 = dist2At(h, seg, l);
            double d2 = dist2At(h, seg, l2);

            double denom = (d1 - 2.0 * d0 + d2);
            if (Math.abs(denom) < 1e-12) {
                break;
            }

            double dl = 0.5 * eps * (d1 - d2) / denom;
            dl = Math.max(-0.5 * window, Math.min(0.5 * window, dl));
            l += dl;

            if (Math.abs(dl) < 1e-5) {
                break;
            }
        }

        Vec3 helixP = h.at(l);
        double s = seg.u.dot(helixP.sub(seg.a));
        s = Math.max(seg.s0, Math.min(seg.s1, s));
        Vec3 segP = seg.point(s);

        return new CA(l, s, helixP, segP);
    }

    /**
     * Computes the squared distance between a helix point and a finite segment.
     *
     * <p>The helix parameter {@code l} is fixed. The segment coordinate is
     * projected and clamped to the segment endpoints.
     *
     * @param h helix
     * @param seg finite segment
     * @param l fixed helix parameter
     * @return squared distance
     */
    private static double dist2At(HelixGeom h, Segment seg, double l) {
        Vec3 helixP = h.at(l);
        double s = seg.u.dot(helixP.sub(seg.a));
        s = Math.max(seg.s0, Math.min(seg.s1, s));
        Vec3 segP = seg.point(s);
        return helixP.sub(segP).norm2();
    }

    /**
     * Finds the closest approach between a helix and the infinite line
     * associated with a segment.
     *
     * <p>This is used for residuals. The line coordinate is not clamped, so the
     * returned distance is the helix-to-line DOCA rather than the
     * helix-to-segment distance.
     *
     * @param h helix
     * @param line segment whose point and direction define the infinite line
     * @param l0 initial guess for helix parameter
     * @return closest-approach result
     */
    public static CA closestApproachToLine(HelixGeom h, Segment line, double l0) {
        double bestL = l0;
        double bestD2 = Double.POSITIVE_INFINITY;

        double window = Math.max(20.0, 0.25 * Math.abs(h.R));
        int nSteps = 61;

        for (int i = 0; i < nSteps; i++) {
            double l = l0 + window * (2.0 * i / (nSteps - 1) - 1.0);
            double d2 = dist2AtLine(h, line, l);
            if (d2 < bestD2) {
                bestD2 = d2;
                bestL = l;
            }
        }

        double l = bestL;
        for (int it = 0; it < 8; it++) {
            double eps = Math.max(1e-3, 1e-3 * Math.abs(h.R));
            double l1 = l - eps;
            double l2 = l + eps;

            double d1 = dist2AtLine(h, line, l1);
            double d0 = dist2AtLine(h, line, l);
            double d2 = dist2AtLine(h, line, l2);

            double denom = d1 - 2.0 * d0 + d2;
            if (Math.abs(denom) < 1e-12) {
                break;
            }

            double dl = 0.5 * eps * (d1 - d2) / denom;
            dl = Math.max(-0.5 * window, Math.min(0.5 * window, dl));
            l += dl;

            if (Math.abs(dl) < 1e-5) {
                break;
            }
        }

        Vec3 helixP = h.at(l);

        // Infinite-line projection: no clamping.
        double s = line.u.dot(helixP.sub(line.a));
        Vec3 lineP = line.point(s);

        return new CA(l, s, helixP, lineP);
    }

    /**
     * Computes the squared distance between a helix point and an infinite line.
     *
     * <p>The line is defined by {@code line.a + s line.u}. The coordinate
     * {@code s} is not clamped.
     *
     * @param h helix
     * @param line segment whose point and direction define the infinite line
     * @param l fixed helix parameter
     * @return squared distance
     */
    private static double dist2AtLine(HelixGeom h, Segment line, double l) {
        Vec3 helixP = h.at(l);

        // Infinite-line projection: no clamping.
        double s = line.u.dot(helixP.sub(line.a));
        Vec3 lineP = line.point(s);

        return helixP.sub(lineP).norm2();
    }

    /* ===========================
     * Fit result
     * =========================== */

    /**
     * Result of the helix fit in both geometric and CVT-style forms.
     *
     * <p>The residual arrays are indexed in the same order as the input
     * segment list passed to {@link #fitRoadAsCVTHelix}.
     */
    public static class FitResult {
        public final double xc, yc, R;
        public final double z0, tanLambda;
        public final double chi2;
        public final int ndf;

        public final double d0, phi0, omega, tanL;
        public final int turningSign;

        /**
         * Unsigned DOCA residuals between final helix and each infinite line.
         */
        public final double[] residuals;

        /**
         * Residual vector components, defined as helix closest point minus line
         * closest point.
         */
        public final double[] residualX;
        public final double[] residualY;
        public final double[] residualZ;

        /**
         * Closest-approach parameters for debugging and downstream inspection.
         */
        public final double[] closestHelixL;
        public final double[] closestLineS;

        /**
         * Creates a fit result without residual arrays.
         *
         * <p>This constructor is kept for backward compatibility.
         *
         * @param xc circle center x
         * @param yc circle center y
         * @param R circle radius
         * @param z0 z intercept
         * @param tanLambda dz/dl slope
         * @param chi2 fit chi2
         * @param ndf number of degrees of freedom
         * @param d0 CVT d0
         * @param phi0 CVT phi0
         * @param omega CVT curvature
         * @param tanL CVT tan lambda
         * @param turningSign selected turning sign
         */
        public FitResult(double xc, double yc, double R, double z0, double tanLambda,
                         double chi2, int ndf,
                         double d0, double phi0, double omega, double tanL,
                         int turningSign) {
            this(xc, yc, R, z0, tanLambda,
                 chi2, ndf,
                 d0, phi0, omega, tanL,
                 turningSign,
                 null, null, null, null, null, null);
        }

        /**
         * Creates a fit result with residual arrays.
         *
         * @param xc circle center x
         * @param yc circle center y
         * @param R circle radius
         * @param z0 z intercept
         * @param tanLambda dz/dl slope
         * @param chi2 fit chi2
         * @param ndf number of degrees of freedom
         * @param d0 CVT d0
         * @param phi0 CVT phi0
         * @param omega CVT curvature
         * @param tanL CVT tan lambda
         * @param turningSign selected turning sign
         * @param residuals unsigned helix-to-line DOCA residuals
         * @param residualX x residual components
         * @param residualY y residual components
         * @param residualZ z residual components
         * @param closestHelixL helix closest-approach parameters
         * @param closestLineS line closest-approach parameters
         */
        public FitResult(double xc, double yc, double R, double z0, double tanLambda,
                         double chi2, int ndf,
                         double d0, double phi0, double omega, double tanL,
                         int turningSign,
                         double[] residuals,
                         double[] residualX,
                         double[] residualY,
                         double[] residualZ,
                         double[] closestHelixL,
                         double[] closestLineS) {
            this.xc = xc;
            this.yc = yc;
            this.R = R;
            this.z0 = z0;
            this.tanLambda = tanLambda;
            this.chi2 = chi2;
            this.ndf = ndf;

            this.d0 = d0;
            this.phi0 = phi0;
            this.omega = omega;
            this.tanL = tanL;
            this.turningSign = turningSign;

            this.residuals = residuals;
            this.residualX = residualX;
            this.residualY = residualY;
            this.residualZ = residualZ;
            this.closestHelixL = closestHelixL;
            this.closestLineS = closestLineS;
        }
    }

    /* ===========================
     * Weighted LM fitter
     * =========================== */

    /**
     * Levenberg-Marquardt-style fitter for the geometric helix parameters.
     */
    public static class HelixFitter {

        // Parameter order: xc, yc, R, phiRef, z0, tanLambda.
        private static final int NPAR = 6;

        /**
         * Initial damping parameter for the LM iteration.
         */
        public static double lambdaLM = 0.05;

        /**
         * Maximum number of LM iterations.
         */
        public static int maxIter = 200;

        /**
         * Effective transverse beam constraint resolution.
         */
        public static double sigmaBeamXY = 1.0;

        /**
         * Per-segment variances used in chi2.
         */
        private static class Sigmas {
            final double varXY;
            final double varZ;

            /**
             * Creates variance holder for one segment.
             *
             * @param varXY transverse variance
             * @param varZ longitudinal variance
             */
            Sigmas(double varXY, double varZ) {
                this.varXY = varXY;
                this.varZ = varZ;
            }
        }

        /**
         * Estimates transverse and longitudinal variances for each segment.
         *
         * @param segs input segments
         * @return variance objects
         */
        private static Sigmas[] getSigmas(Segment[] segs) {
            Sigmas[] s = new Sigmas[segs.length];
            for (int i = 0; i < segs.length; i++) {
                double dx = segs[i].s1 * segs[i].u.x;
                double dy = segs[i].s1 * segs[i].u.y;
                double dz = segs[i].s1 * segs[i].u.z;

                double dSq = dx * dx + dy * dy;
                double pitch = 0.16;

                double varXY = (dSq + pitch * pitch) / 12.0;
                double varZ = (dz * dz) / 12.0;
                varZ = Math.max(varZ, 1e-4);
                
                s[i] = new Sigmas(Math.max(varXY, 1e-4), varZ);
            }
            return s;
        }

        /**
         * Copies a geometric helix.
         *
         * @param h source helix
         * @return copied helix
         */
        private static HelixGeom copyHelix(HelixGeom h) {
            HelixGeom c = new HelixGeom(h.xc, h.yc, h.R, h.phiRef, h.z0, h.tanLambda);
            c.chi2 = h.chi2;
            return c;
        }

        /**
         * Estimates initial helix arc-length parameters for each segment.
         *
         * <p>The estimate is based on the segment midpoint azimuth around the
         * current circle center. The phase is unwrapped using the previous
         * segment in list order.
         *
         * @param h current helix
         * @param segs segments
         * @return initial l values
         */
        static double[] initialArcSeeds(HelixGeom h, Segment[] segs) {
            double[] ls = new double[segs.length];
            double prev = Double.NaN;

            for (int i = 0; i < segs.length; i++) {
                Vec3 mid = segs[i].midpoint();
                double phi = Math.atan2(mid.y - h.yc, mid.x - h.xc);
                double rel = phi - h.phiRef;

                if (!Double.isNaN(prev)) {
                    while (rel - prev > Math.PI) {
                        rel -= 2.0 * Math.PI;
                    }
                    while (rel - prev < -Math.PI) {
                        rel += 2.0 * Math.PI;
                    }
                }

                ls[i] = h.R * rel;
                prev = rel;
            }

            return ls;
        }

        /**
         * Adds a soft beamline constraint to the normal equations.
         *
         * <p>The constraint encourages the transverse circle to be tangent to
         * the beam point.
         *
         * @param A normal-equation matrix
         * @param b normal-equation right-hand side
         * @param h current helix
         * @param xb beam x
         * @param yb beam y
         * @param sigmaBeam beam constraint resolution
         * @param chi2Acc one-element chi2 accumulator
         */
        private static void addBeamConstraint(double[][] A, double[] b,
                                              HelixGeom h,
                                              double xb, double yb,
                                              double sigmaBeam,
                                              double[] chi2Acc) {
            double dx = h.xc - xb;
            double dy = h.yc - yb;
            double d2 = dx * dx + dy * dy;
            double d = Math.sqrt(Math.max(d2, 1e-12));

            double rBeam = d - h.R;
            double wBeam = 1.0 / (sigmaBeam * sigmaBeam);

            double[] Jb = new double[]{
                dx / d,
                dy / d,
               -1.0,
                0.0,
                0.0,
                0.0
            };

            chi2Acc[0] += wBeam * rBeam * rBeam;

            for (int i = 0; i < NPAR; i++) {
                b[i] -= wBeam * Jb[i] * rBeam;
                for (int j = 0; j < NPAR; j++) {
                    A[i][j] += wBeam * Jb[i] * Jb[j];
                }
            }
        }

        /**
         * Computes the total chi2 for a trial helix.
         *
         * <p>The input {@code l_s} array is updated with the latest closest
         * approach helix parameters.
         *
         * @param h trial helix
         * @param segs segments
         * @param zTarget target z0 for soft constraint
         * @param zHalfLength half-length used to define z0 constraint strength
         * @param xb beam x
         * @param yb beam y
         * @param l_s closest-approach seeds, updated in place
         * @return total chi2
         */
        private static double computeChi2(HelixGeom h, Segment[] segs,
                                          double zTarget, double zHalfLength,
                                          double xb, double yb,
                                          double[] l_s) {
            Sigmas[] sig = getSigmas(segs);
            double chi2 = 0.0;

            for (int idx = 0; idx < segs.length; idx++) {
                CA ca = closestApproach(h, segs[idx], l_s[idx]);
                l_s[idx] = ca.l;

                Vec3 r = ca.h.sub(ca.p);

                double wxy = 1.0 / sig[idx].varXY;
                double wz = 1.0 / sig[idx].varZ;

                chi2 += wxy * (r.x * r.x + r.y * r.y) + wz * (r.z * r.z);
            }

            double sigmaZ0 = Math.max(zHalfLength / 2.0, 1e-3);
            double wz0 = 1.0 / (sigmaZ0 * sigmaZ0);
            double dz0 = h.z0 - zTarget;
            chi2 += wz0 * dz0 * dz0;

            double dx = h.xc - xb;
            double dy = h.yc - yb;
            double d = Math.sqrt(Math.max(dx * dx + dy * dy, 1e-12));
            double rBeam = d - h.R;
            double wBeam = 1.0 / (sigmaBeamXY * sigmaBeamXY);
            chi2 += wBeam * rBeam * rBeam;

            return chi2;
        }

        /**
         * Fits a geometric helix to the supplied finite line segments.
         *
         * @param start starting helix
         * @param segs finite detector segments
         * @param zTarget target z0 for soft constraint
         * @param zHalfLength half-length used to define z0 constraint strength
         * @param xb beam x
         * @param yb beam y
         * @return fitted geometric helix
         */
        public static HelixGeom fit(HelixGeom start, Segment[] segs,
                                    double zTarget, double zHalfLength,
                                    double xb, double yb) {

            HelixGeom h = copyHelix(start);
            Sigmas[] sig = getSigmas(segs);

            double[] l_s = initialArcSeeds(h, segs);
            double[] best_ls = l_s.clone();

            double lambda = lambdaLM;
            double stepScale = 1.0;

            double bestChi2 = Double.POSITIVE_INFINITY;
            HelixGeom bestH = copyHelix(h);

            for (int iter = 0; iter < maxIter; iter++) {

                double[][] A = new double[NPAR][NPAR];
                double[] b = new double[NPAR];
                double chi2 = 0.0;

                for (int idx = 0; idx < segs.length; idx++) {
                    CA ca = closestApproach(h, segs[idx], l_s[idx]);
                    l_s[idx] = ca.l;

                    Vec3 r = ca.h.sub(ca.p);

                    double wxy = 1.0 / sig[idx].varXY;
                    double wz = 1.0 / sig[idx].varZ;
                    if(idx==0) wz = 0; //exclude the beam line z from the fit
                    chi2 += wxy * (r.x * r.x + r.y * r.y) + wz * (r.z * r.z);

                    double l = ca.l;
                    double phi = h.phiRef + l / h.R;

                    double dxdR = Math.cos(phi) + (l / h.R) * Math.sin(phi);
                    double dydR = Math.sin(phi) - (l / h.R) * Math.cos(phi);

                    // Parameter order: xc, yc, R, phiRef, z0, tanLambda.
                    double[] Jx = new double[]{
                        1.0,
                        0.0,
                        dxdR,
                        -h.R * Math.sin(phi),
                        0.0,
                        0.0
                    };

                    double[] Jy = new double[]{
                        0.0,
                        1.0,
                        dydR,
                         h.R * Math.cos(phi),
                        0.0,
                        0.0
                    };

                    double[] Jz = new double[]{
                        0.0,
                        0.0,
                        0.0,
                        0.0,
                        1.0,
                        l
                    };

                    for (int i = 0; i < NPAR; i++) {
                        double gi = wxy * (Jx[i] * r.x + Jy[i] * r.y)
                                  + wz * (Jz[i] * r.z);
                        b[i] -= gi;

                        for (int j = 0; j < NPAR; j++) {
                            double hij =
                                wxy * (Jx[i] * Jx[j] + Jy[i] * Jy[j]) +
                                wz * (Jz[i] * Jz[j]);
                            A[i][j] += hij;
                        }
                    }
                }

                // Soft z0 constraint.
                double sigmaZ0 = Math.max(zHalfLength / 2.0, 1e-3);
                double wz0 = 1.0 / (sigmaZ0 * sigmaZ0);
                double dz0 = h.z0 - zTarget;
                A[4][4] += wz0;
                b[4] -= wz0 * dz0;
                chi2 += wz0 * dz0 * dz0;

                double[] chi2Acc = new double[]{chi2};
                addBeamConstraint(A, b, h, xb, yb, sigmaBeamXY, chi2Acc);
                chi2 = chi2Acc[0];

                if (chi2 < bestChi2) {
                    bestChi2 = chi2;
                    bestH = copyHelix(h);
                    System.arraycopy(l_s, 0, best_ls, 0, l_s.length);
                }

                double[][] Atrial = new double[NPAR][NPAR];
                for (int i = 0; i < NPAR; i++) {
                    System.arraycopy(A[i], 0, Atrial[i], 0, NPAR);
                    Atrial[i][i] += lambda;
                }

                double[] dp = solve(Atrial, b.clone());

                if (norm(dp) < 1e-8) {
                    break;
                }

                HelixGeom hTry = copyHelix(h);
                hTry.xc += stepScale * dp[0];
                hTry.yc += stepScale * dp[1];
                hTry.R += stepScale * dp[2];
                hTry.phiRef += stepScale * dp[3];
                hTry.z0 += stepScale * dp[4];
                hTry.tanLambda += stepScale * dp[5];

                if (hTry.R < 1e-3) {
                    hTry.R = 1e-3;
                }

                double[] lTry = l_s.clone();
                double chi2Try = computeChi2(hTry, segs, zTarget, zHalfLength, xb, yb, lTry);

                if (chi2Try < chi2) {
                    h = hTry;
                    h.chi2 = chi2Try;
                    l_s = lTry;
                    lambda = Math.max(1e-6, lambda * 0.5);
                } else {
                    lambda = Math.min(1e6, lambda * 5.0);
                }

                if (Math.abs(chi2 - chi2Try) < 1e-6) {
                    break;
                }
               
            }
            bestH.chi2 = bestChi2;
            return bestH;
        }

        /**
         * Solves a dense linear system using Gaussian elimination with partial
         * pivoting.
         *
         * @param A square matrix, modified in place
         * @param b right-hand side, modified in place
         * @return solution vector
         */
        private static double[] solve(double[][] A, double[] b) {
            int n = b.length;
            double[] x = new double[n];

            for (int i = 0; i < n; i++) {
                int p = i;
                for (int k = i + 1; k < n; k++) {
                    if (Math.abs(A[k][i]) > Math.abs(A[p][i])) {
                        p = k;
                    }
                }

                double[] tmp = A[i];
                A[i] = A[p];
                A[p] = tmp;

                double t = b[i];
                b[i] = b[p];
                b[p] = t;

                double piv = A[i][i];
                if (Math.abs(piv) < 1e-18) {
                    return new double[n];
                }

                for (int k = i + 1; k < n; k++) {
                    double f = A[k][i] / piv;
                    for (int j = i; j < n; j++) {
                        A[k][j] -= f * A[i][j];
                    }
                    b[k] -= f * b[i];
                }
            }

            for (int i = n - 1; i >= 0; i--) {
                double s = b[i];
                for (int j = i + 1; j < n; j++) {
                    s -= A[i][j] * x[j];
                }
                x[i] = s / A[i][i];
            }

            return x;
        }

        /**
         * Computes the Euclidean norm of a vector.
         *
         * @param v input vector
         * @return vector norm
         */
        private static double norm(double[] v) {
            double s = 0.0;
            for (double x : v) {
                s += x * x;
            }
            return Math.sqrt(s);
        }
    }

    /* =============
     * Circle seed
     * ============= */

    /**
     * Fits a circle in the transverse plane using a simple algebraic method.
     *
     * @param pts 3D points whose x/y coordinates are used
     * @return array {xc, yc, R}
     */
    private static double[] fitCircleFromPoints(List<Vec3> pts) {
        double xm = 0.0;
        double ym = 0.0;
        for (Vec3 p : pts) {
            xm += p.x;
            ym += p.y;
        }
        xm /= pts.size();
        ym /= pts.size();

        double suu = 0;
        double svv = 0;
        double suv = 0;
        double suuu = 0;
        double svvv = 0;
        double suvv = 0;
        double svuu = 0;

        for (Vec3 p : pts) {
            double u = p.x - xm;
            double v = p.y - ym;
            suu += u * u;
            svv += v * v;
            suv += u * v;
            suuu += u * u * u;
            svvv += v * v * v;
            suvv += u * v * v;
            svuu += v * u * u;
        }

        double den = 2.0 * (suu * svv - suv * suv);
        if (Math.abs(den) < 1e-18) {
            return new double[]{xm, ym, 1.0};
        }

        double uc = (svv * (suuu + suvv) - suv * (svvv + svuu)) / den;
        double vc = (suu * (svvv + svuu) - suv * (suuu + suvv)) / den;

        double xc = xm + uc;
        double yc = ym + vc;
        double R = Math.hypot(uc, vc);

        if (R < 1e-6) {
            R = 1e-6;
        }

        return new double[]{xc, yc, R};
    }

    /**
     * Builds a transverse circle seed from CVT crosses.
     *
     * @param pts crosses used for seeding
     * @return array {xc, yc, R}
     */
    private static double[] fitCircle(List<Cross> pts) {
        List<Vec3> v = new ArrayList<>();
        for (Cross p : pts) {
            v.add(new Vec3(p.getPoint().x(), p.getPoint().y(), p.getPoint().z()));
        }
        return fitCircleFromPoints(v);
    }

    /**
     * Builds a transverse circle seed from segment midpoints.
     *
     * @param segs segments used for seeding
     * @return array {xc, yc, R}
     */
    private static double[] fitCircleFromSegments(List<Segment> segs) {
        List<Vec3> mids = new ArrayList<>();
        for (Segment s : segs) {
            mids.add(s.midpoint());
        }
        return fitCircleFromPoints(mids);
    }

    /**
     * Estimates the helix reference phase from the lowest-region cross.
     *
     * @param crosses input crosses
     * @param xc circle center x
     * @param yc circle center y
     * @return reference phase
     */
    private static double estimatePhiRefFromCrosses(List<Cross> crosses, double xc, double yc) {
        if (crosses == null || crosses.isEmpty()) {
            return 0.0;
        }
        Cross first = crosses.stream()
                .min(Comparator.comparingInt(Cross::getRegion))
                .orElse(crosses.get(0));
        return Math.atan2(first.getPoint().y() - yc, first.getPoint().x() - xc);
    }

    /**
     * Estimates the helix reference phase from the first segment midpoint.
     *
     * @param segments input segments
     * @param xc circle center x
     * @param yc circle center y
     * @return reference phase
     */
    private static double estimatePhiRefFromSegments(List<Segment> segments, double xc, double yc) {
        if (segments == null || segments.isEmpty()) {
            return 0.0;
        }
        Vec3 m = segments.get(0).midpoint();
        return Math.atan2(m.y - yc, m.x - xc);
    }

    /**
     * Estimates z0 and tanLambda from crosses.
     *
     * @param crosses crosses used for the z/l seed
     * @param xc circle center x
     * @param yc circle center y
     * @param R circle radius
     * @param phiRef reference phase
     * @param zTarget fallback z target
     * @return array {z0, tanLambda}
     */
    private static double[] estimateZSeed(List<Cross> crosses,
                                          double xc, double yc, double R, double phiRef,
                                          double zTarget) {
        if (crosses == null || crosses.isEmpty()) {
            return new double[]{zTarget, 0.0};
        }

        class ZPoint {
            double l;
            double z;
            int order;

            ZPoint(double l, double z, int order) {
                this.l = l;
                this.z = z;
                this.order = order;
            }
        }

        List<ZPoint> pts = new ArrayList<>();
        for (Cross c : crosses) {
            double phi = Math.atan2(c.getPoint().y() - yc, c.getPoint().x() - xc) - phiRef;
            double l = R * phi;
            pts.add(new ZPoint(l, c.getPoint().z(), c.getRegion()));
        }

        pts.sort(Comparator.comparingInt(p -> p.order));

        for (int i = 1; i < pts.size(); i++) {
            double prevPhi = pts.get(i - 1).l / R;
            double phi = pts.get(i).l / R;

            while (phi - prevPhi > Math.PI) {
                phi -= 2.0 * Math.PI;
            }
            while (phi - prevPhi < -Math.PI) {
                phi += 2.0 * Math.PI;
            }

            pts.get(i).l = R * phi;
        }

        return fitLineInZL(pts, zTarget);
    }

    /**
     * Estimates z0 and tanLambda from segment midpoints.
     *
     * @param segments segments used for the z/l seed
     * @param xc circle center x
     * @param yc circle center y
     * @param R circle radius
     * @param phiRef reference phase
     * @param zTarget fallback z target
     * @return array {z0, tanLambda}
     */
    private static double[] estimateZSeedFromSegments(List<Segment> segments,
                                                      double xc, double yc, double R, double phiRef,
                                                      double zTarget) {
        if (segments == null || segments.isEmpty()) {
            return new double[]{zTarget, 0.0};
        }

        class ZPoint {
            double l;
            double z;
            int order;

            ZPoint(double l, double z, int order) {
                this.l = l;
                this.z = z;
                this.order = order;
            }
        }

        List<ZPoint> pts = new ArrayList<>();
        for (int i = 0; i < segments.size(); i++) {
            Vec3 m = segments.get(i).midpoint();
            double phi = Math.atan2(m.y - yc, m.x - xc) - phiRef;
            double l = R * phi;
            pts.add(new ZPoint(l, m.z, i));
        }

        return fitLineInZL(pts, zTarget);
    }

    /**
     * Fits a straight line z = z0 + l tanLambda to temporary z/l points.
     *
     * <p>This method accepts local helper objects with fields named
     * {@code l} and {@code z}. Reflection is used so both local ZPoint classes
     * can share the same implementation.
     *
     * @param rawPts list of objects containing fields l and z
     * @param zTarget fallback z target
     * @return array {z0, tanLambda}
     */
    private static double[] fitLineInZL(List<?> rawPts, double zTarget) {
        @SuppressWarnings("unchecked")
        List<Object> pts = (List<Object>) rawPts;

        double sumL = 0.0;
        double sumZ = 0.0;
        double sumLL = 0.0;
        double sumLZ = 0.0;
        int n = pts.size();

        try {
            for (Object obj : pts) {
                double l = obj.getClass().getDeclaredField("l").getDouble(obj);
                double z = obj.getClass().getDeclaredField("z").getDouble(obj);
                sumL += l;
                sumZ += z;
                sumLL += l * l;
                sumLZ += l * z;
            }
        } catch (IllegalAccessException | NoSuchFieldException ex) {
            return new double[]{zTarget, 0.0};
        }

        double den = n * sumLL - sumL * sumL;
        if (Math.abs(den) < 1e-12) {
            double zMean = sumZ / Math.max(n, 1);
            return new double[]{zMean, 0.0};
        }

        double tanLambda = (n * sumLZ - sumL * sumZ) / den;
        double z0 = (sumZ - tanLambda * sumL) / n;

        return new double[]{z0, tanLambda};
    }

    /* ===========================
     * Residuals
     * =========================== */

    /**
     * Container for residual arrays computed after the final fit.
     */
    private static class ResidualResult {
        final double[] residuals;
        final double[] residualX;
        final double[] residualY;
        final double[] residualZ;
        final double[] closestHelixL;
        final double[] closestLineS;

        /**
         * Creates a residual container.
         *
         * @param residuals unsigned DOCA residuals
         * @param residualX x residual components
         * @param residualY y residual components
         * @param residualZ z residual components
         * @param closestHelixL closest helix parameters
         * @param closestLineS closest line parameters
         */
        ResidualResult(double[] residuals,
                       double[] residualX,
                       double[] residualY,
                       double[] residualZ,
                       double[] closestHelixL,
                       double[] closestLineS) {
            this.residuals = residuals;
            this.residualX = residualX;
            this.residualY = residualY;
            this.residualZ = residualZ;
            this.closestHelixL = closestHelixL;
            this.closestLineS = closestLineS;
        }
    }

    /**
     * Computes per-segment residuals as helix-to-infinite-line DOCAs.
     *
     * <p>For each input segment, the finite segment's point and direction define
     * an infinite line. The residual is the distance of closest approach between
     * the final fitted helix and that infinite line. The residual vector is
     * defined as:
     * <pre>
     *   residual vector = closest helix point - closest line point
     * </pre>
     *
     * @param hfit fitted helix
     * @param segments input segments
     * @return residual arrays
     */
    private static ResidualResult computeLineResiduals(HelixGeom hfit,
                                                       List<Segment> segments) {
        int n = segments.size();

        double[] residuals = new double[n];
        double[] residualX = new double[n];
        double[] residualY = new double[n];
        double[] residualZ = new double[n];
        double[] closestHelixL = new double[n];
        double[] closestLineS = new double[n];

        Segment[] segArray = segments.toArray(new Segment[0]);
        double[] lSeed = HelixFitter.initialArcSeeds(hfit, segArray);

        for (int i = 0; i < n; i++) {
            CA ca = closestApproachToLine(hfit, segments.get(i), lSeed[i]);

            Vec3 dr = ca.h.sub(ca.p);

            residualX[i] = dr.x;
            residualY[i] = dr.y;
            residualZ[i] = dr.z;
            residuals[i] = dr.norm();

            closestHelixL[i] = ca.l;
            closestLineS[i] = ca.s;
        }

        return new ResidualResult(
            residuals,
            residualX,
            residualY,
            residualZ,
            closestHelixL,
            closestLineS
        );
    }

    /* ===========================
     * Convert geometric -> CVT helix params
     * =========================== */

    /**
     * Converts the internal geometric helix parameters to CVT-style helix
     * parameters.
     *
     * @param h fitted geometric helix
     * @param xb beam x
     * @param yb beam y
     * @param polarity magnetic-field polarity convention
     * @param turningSign selected turning sign
     * @return fit result containing converted parameters
     */
    private static FitResult toCVTParams(HelixGeom h,
                                         double xb, double yb,
                                         int polarity, int turningSign) {

        double alpha = Math.atan2(yb - h.yc, xb - h.xc);
        double xP = h.xc + h.R * Math.cos(alpha);
        double yP = h.yc + h.R * Math.sin(alpha);

        double d0 = Math.hypot(xP - xb, yP - yb);

        double tx = -Math.sin(alpha);
        double ty = Math.cos(alpha);

        double sgn = (double) (-polarity) * turningSign;
        tx *= sgn;
        ty *= sgn;

        double phi0 = Math.atan2(ty, tx);
        double omega = (double) polarity * turningSign / h.R;
        double tanL = -h.tanLambda;

        double S = Math.sin(phi0);
        double C = Math.cos(phi0);
        if (Math.abs(S) >= Math.abs(C)) {
            d0 = -(xP - xb) / S;
        } else {
            d0 = (yP - yb) / C;
        }

        int ndf = 0;
        return new FitResult(h.xc, h.yc, h.R, h.z0, h.tanLambda,
                             h.chi2, ndf,
                             d0, phi0, omega, tanL,
                             turningSign);
    }

    /* ===========================
     * High-level fit interface
     * =========================== */

    /**
     * Fits a road as a CVT-style helix.
     *
     * <p>The method:
     * <ol>
     *   <li>Builds a transverse circle seed from crosses or segment midpoints.</li>
     *   <li>Applies a beam-compatible circle seed adjustment.</li>
     *   <li>Estimates the helix reference phase.</li>
     *   <li>Estimates the z/l seed parameters.</li>
     *   <li>Runs the weighted helix fitter.</li>
     *   <li>Converts the fitted helix to CVT parameters.</li>
     *   <li>Computes final helix-to-line residuals for each segment.</li>
     * </ol>
     *
     * @param crosses optional CVT crosses used for seeding
     * @param segments detector road segments used in the fit
     * @param zTarget target z0 for soft constraint
     * @param zHalfLength half-length used to define z0 constraint strength
     * @param xb beam x
     * @param yb beam y
     * @param polarity magnetic-field polarity convention
     * @param turningSignOrNull optional turning sign; if null, +1 is used
     * @return fit result, or null if no segments are supplied
     */
    public static FitResult fitRoadAsCVTHelix(List<Cross> crosses,
                                              List<Segment> segments,
                                              double zTarget, double zHalfLength,
                                              double xb, double yb,
                                              int polarity,
                                              Integer turningSignOrNull) {

        if (segments == null || segments.isEmpty()) {
            return null;
        }

        double[] circ;
        if (crosses != null && crosses.size() > 2) {
            circ = fitCircle(crosses);
        } else {
            circ = fitCircleFromSegments(segments);
        }

        double[] circBeam = enforceBeamOnCircleSeed(circ[0], circ[1], circ[2], xb, yb);

        double phiRef;
        if (crosses != null && !crosses.isEmpty()) {
            phiRef = estimatePhiRefFromCrosses(crosses, circBeam[0], circBeam[1]);
        } else {
            phiRef = estimatePhiRefFromSegments(segments, circBeam[0], circBeam[1]);
        }

        double[] zSeed;
        if (crosses != null && crosses.size() > 1) {
            zSeed = estimateZSeed(crosses, circBeam[0], circBeam[1], circBeam[2], phiRef, zTarget);
        } else {
            zSeed = estimateZSeedFromSegments(segments, circBeam[0], circBeam[1], circBeam[2], phiRef, zTarget);
        }

        HelixGeom seed = new HelixGeom(
            circBeam[0], circBeam[1], circBeam[2],
            phiRef,
            zSeed[0], zSeed[1]
        );

        HelixGeom hfit = HelixFitter.fit(
            seed,
            segments.toArray(new Segment[0]),
            zTarget,
            zHalfLength,
            xb, yb
        );

        int turningSign = (turningSignOrNull != null) ? turningSignOrNull : +1;

        FitResult r = toCVTParams(hfit, xb, yb, polarity, turningSign);

        ResidualResult res = computeLineResiduals(hfit, segments);

        int nSeg = segments.size();
        int ndf = Math.max(2 * nSeg - 6, 1);

        return new FitResult(r.xc, r.yc, r.R, r.z0, r.tanLambda,
                             r.chi2,
                             ndf,
                             r.d0, r.phi0, r.omega, r.tanL,
                             r.turningSign,
                             res.residuals,
                             res.residualX,
                             res.residualY,
                             res.residualZ,
                             res.closestHelixL,
                             res.closestLineS);
    }

    /**
     * Adjusts a circle seed so that the circle is tangent to the beam point.
     *
     * <p>The radius is preserved and the circle center is moved along the
     * beam-to-center direction so that the distance from beam to center equals
     * the radius.
     *
     * @param xc initial circle center x
     * @param yc initial circle center y
     * @param R circle radius
     * @param xb beam x
     * @param yb beam y
     * @return adjusted array {xc, yc, R}
     */
    private static double[] enforceBeamOnCircleSeed(double xc, double yc, double R,
                                                    double xb, double yb) {
        double dx = xc - xb;
        double dy = yc - yb;
        double d = Math.sqrt(dx * dx + dy * dy);

        if (d < 1e-9) {
            return new double[]{xc, yc, R};
        }

        double scale = R / d;
        double xc2 = xb + scale * dx;
        double yc2 = yb + scale * dy;

        return new double[]{xc2, yc2, R};
    }
    
        /**
     * Prints a compact debug summary of a fitted helix and its per-segment
     * helix-to-line residuals.
     *
     * <p>This is useful for checking whether the fit is reasonable and whether
     * any individual road segment is badly mismatched.</p>
     *
     * @param label optional label printed at the top of the debug block
     * @param fit fit result returned by fitRoadAsCVTHelix(...)
     */
    public static void debugFitResult(String label, FitResult fit) {
        System.out.println();
        System.out.println("==================================================");
        System.out.println("HelixClusterFitter debug"
                + ((label != null && !label.isEmpty()) ? " : " + label : ""));
        System.out.println("==================================================");

        if (fit == null) {
            System.out.println("FitResult is null");
            System.out.println("==================================================");
            return;
        }

        System.out.printf("Geometric helix:%n");
        System.out.printf("  xc          = %.6f%n", fit.xc);
        System.out.printf("  yc          = %.6f%n", fit.yc);
        System.out.printf("  R           = %.6f%n", fit.R);
        System.out.printf("  z0          = %.6f%n", fit.z0);
        System.out.printf("  tanLambda   = %.6f%n", fit.tanLambda);
        System.out.printf("  chi2        = %.6f%n", fit.chi2);
        System.out.printf("  ndf         = %d%n", fit.ndf);

        if (fit.ndf > 0) {
            System.out.printf("  chi2/ndf    = %.6f%n", fit.chi2 / fit.ndf);
        }

        System.out.printf("%nCVT helix parameters:%n");
        System.out.printf("  d0          = %.6f%n", fit.d0);
        System.out.printf("  phi0        = %.6f rad  %.6f deg%n",
                fit.phi0, Math.toDegrees(fit.phi0));
        System.out.printf("  omega       = %.9f%n", fit.omega);
        System.out.printf("  tanL        = %.6f%n", fit.tanL);
        System.out.printf("  turningSign = %d%n", fit.turningSign);

        if (fit.residuals == null) {
            System.out.println();
            System.out.println("No residual arrays stored in FitResult.");
            System.out.println("==================================================");
            return;
        }

        System.out.printf("%nResiduals: helix-to-infinite-line DOCA%n");
        System.out.printf("  number of residuals = %d%n", fit.residuals.length);

        double sum = 0.0;
        double sum2 = 0.0;
        double max = -1.0;
        int imax = -1;

        for (int i = 0; i < fit.residuals.length; i++) {
            double r = fit.residuals[i];

            sum += r;
            sum2 += r * r;

            if (r > max) {
                max = r;
                imax = i;
            }
        }

        int n = fit.residuals.length;
        if (n > 0) {
            double mean = sum / n;
            double rms = Math.sqrt(sum2 / n);

            System.out.printf("  mean DOCA = %.6f%n", mean);
            System.out.printf("  rms  DOCA = %.6f%n", rms);
            System.out.printf("  max  DOCA = %.6f at segment %d%n", max, imax);
        }

        System.out.println();
        System.out.println("  idx        doca          dx          dy          dz        helix_l       line_s");
        System.out.println("  ---  ----------  ----------  ----------  ----------  ------------  ------------");

        for (int i = 0; i < fit.residuals.length; i++) {
            double dx = valueAt(fit.residualX, i);
            double dy = valueAt(fit.residualY, i);
            double dz = valueAt(fit.residualZ, i);
            double l  = valueAt(fit.closestHelixL, i);
            double s  = valueAt(fit.closestLineS, i);

            System.out.printf("  %3d  %10.6f  %10.6f  %10.6f  %10.6f  %12.6f  %12.6f%n",
                    i,
                    fit.residuals[i],
                    dx,
                    dy,
                    dz,
                    l,
                    s);
        }

        System.out.println("==================================================");
    }

    /**
     * Safely returns arr[i], or NaN if the array is null or too short.
     *
     * @param arr input array
     * @param i requested index
     * @return array value or NaN
     */
    private static double valueAt(double[] arr, int i) {
        if (arr == null || i < 0 || i >= arr.length) {
            return Double.NaN;
        }
        return arr[i];
    }
    
        /**
     * Prints only residuals larger than the requested threshold.
     *
     * @param label optional debug label
     * @param fit fit result
     * @param threshold residual threshold
     */
    public static void debugLargeResiduals(String label, FitResult fit, double threshold) {
        if (fit == null || fit.residuals == null) {
            return;
        }

        boolean printedHeader = false;

        for (int i = 0; i < fit.residuals.length; i++) {
            if (fit.residuals[i] > threshold) {
                if (!printedHeader) {
                    System.out.println();
                    System.out.println("Large HelixClusterFitter residuals"
                            + ((label != null && !label.isEmpty()) ? " : " + label : ""));
                    System.out.println("threshold = " + threshold);
                    System.out.println("  idx        doca          dx          dy          dz");
                    System.out.println("  ---  ----------  ----------  ----------  ----------");
                    printedHeader = true;
                }

                System.out.printf("  %3d  %10.6f  %10.6f  %10.6f  %10.6f%n",
                        i,
                        fit.residuals[i],
                        valueAt(fit.residualX, i),
                        valueAt(fit.residualY, i),
                        valueAt(fit.residualZ, i));
            }
        }
    }
}

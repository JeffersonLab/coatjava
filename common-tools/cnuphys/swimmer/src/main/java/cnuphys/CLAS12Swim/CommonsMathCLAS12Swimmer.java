package cnuphys.CLAS12Swim;

import cnuphys.CLAS12Swim.geometry.Cylinder;
import cnuphys.CLAS12Swim.geometry.Plane;
import cnuphys.CLAS12Swim.geometry.Sphere;
import cnuphys.magfield.FieldProbe;
import cnuphys.magfield.RotatedCompositeProbe;

import org.apache.commons.math3.ode.FirstOrderDifferentialEquations;
import org.apache.commons.math3.ode.events.EventHandler;
import org.apache.commons.math3.ode.sampling.StepHandler;
import org.apache.commons.math3.ode.sampling.StepInterpolator;
import org.apache.commons.math3.ode.nonstiff.DormandPrince54Integrator;

/**
 * An {@link ICLAS12Swimmer} implementation that uses Apache Commons Math 3.6.1 ODE solvers.
 * <p>
 * This class is intended for side-by-side comparison with {@link CLAS12Swimmer} for:
 * <ul>
 *   <li>numerical agreement (final state, termination reason), and</li>
 *   <li>performance (# field evaluations, wall time).</li>
 * </ul>
 *
 * <h2>Independent variable</h2>
 * The ODE is integrated with independent variable {@code s} = path length in cm.
 *
 * <h2>State vector convention</h2>
 * The integrated state is {@code y = [x, y, z, tx, ty, tz]} where:
 * <ul>
 *   <li>{@code (x,y,z)} are in cm</li>
 *   <li>{@code (tx,ty,tz)} are direction cosines (dimensionless): {@code t = p/|p|}</li>
 * </ul>
 *
 * <h2>Magnetic field</h2>
 * The field is obtained from {@link FieldProbe#field(float, float, float, float[])}
 * with position in cm; field units must match the curvature constant used below
 * (this class mirrors the constant used by {@link CLAS12SwimODE} to keep comparisons fair).
 */
public final class CommonsMathCLAS12Swimmer implements ICLAS12Swimmer {

    /** Minimum momentum threshold (GeV/c) consistent with {@link CLAS12Swimmer}. */
    private double minMomentum = 5e-05;

    /** Minimum step size (cm). */
    private double minStepSize = 1.0e-6;

    /** Maximum step size (cm). */
    private double maxStepSize = Double.POSITIVE_INFINITY;
    

    /**
     * If true, tune tolerances to target a modest "miss" scale rather than over-solving.
     * <p>
     * For boundary-hit swims (Z/Plane/etc.), this mode uses an event convergence and
     * absolute position tolerance on the order of {@code 1e-5 cm} by default.
     * </p>
     */
    private boolean legacyComparable = false;

    private final FieldProbe probe;

    /**
     * Create a Commons-Math based swimmer using the current active field probe.
     */
    public CommonsMathCLAS12Swimmer() {
        this(FieldProbe.factory());
    }

    /**
     * Create a Commons-Math based swimmer using the given field probe.
     *
     * @param probe field probe (must not be {@code null})
     */
    public CommonsMathCLAS12Swimmer(FieldProbe probe) {
        if (probe == null) {
            throw new NullPointerException("probe");
        }
        this.probe = probe;
    }

    @Override
    public FieldProbe getProbe() {
        return probe;
    }


    /**
     * Enable/disable the "legacy comparable" tuning mode.
     *
     * @param legacyComparable true to enable comparable tuning (less over-solving)
     */
    public void setLegacyComparable(boolean legacyComparable) {
        this.legacyComparable = legacyComparable;
    }

    /**
     * Swim to a fixed target z (cm), stopping when the trajectory reaches {@code zTarget}
     * (event detection) or when the path length {@code sMax} is reached.
     *
     * <p><b>Units:</b> positions in cm, momentum in GeV/c, angles in degrees, path length in cm.</p>
     *
     * <p>
     * When {@code legacyComparable} is {@code true}, this method intentionally avoids “over-solving”
     * and targets a modest z-miss scale (~1e-5 cm) for speed comparisons.
     * </p>
     */
    @Override
    public CLAS12SwimResult swimZ(int q,
                                 double xo, double yo, double zo,
                                 double p, double theta, double phi,
                                 double zTarget, double accuracy,
                                 double sMax, double h, double tolerance) {

        final CLAS12Values ivals = new CLAS12Values(q, xo, yo, zo, p, theta, phi);
        final CLAS12ZListener listener = new CLAS12ZListener(ivals, zTarget, accuracy, sMax);

        // Momentum guard (match CLAS12Swimmer behavior)
        if (p < minMomentum) {
            listener.setStatus(CLAS12Swimmer.BELOW_MIN_MOMENTUM);
            return new CLAS12SwimResult(listener);
        }

        // Neutral shortcut
        if (q == 0 && listener.canMakeStraightLine()) {
            listener.straightLine();
            return new CLAS12SwimResult(listener);
        }

        // Initial state y = [x,y,z, tx,ty,tz]
        final double[] y = ivals.getU().clone();
        final SwimEquations ode = new SwimEquations(q, p, probe);

        // ------------------------------------------------------------------
        // legacyComparable tuning (restored without extra class fields)
        // ------------------------------------------------------------------
        final double targetMiss = legacyComparable ? 1.0e-5 : accuracy;      // cm
        final double successTol = legacyComparable ? targetMiss : accuracy;  // cm

        // Commons Math requires per-component tolerances. Interpret the provided "tolerance" knob
        // as a position absolute tolerance in cm when not in legacyComparable mode.
        final double absPos = legacyComparable ? 1.0e-5 : Math.max(1.0e-12, tolerance); // cm
        final double absDir = legacyComparable ? 1.0e-5 : 1.0e-10;                      // dimensionless
        final double rel    = legacyComparable ? 1.0e-9 : 1.0e-12;

        final double[] absTol = new double[] { absPos, absPos, absPos, absDir, absDir, absDir };
        final double[] relTol = new double[] { rel, rel, rel, rel, rel, rel };

        // Adaptive integrator: allow step-size growth up to maxStepSize (do NOT cap at h)
        final DormandPrince54Integrator integrator =
                new DormandPrince54Integrator(
                        Math.max(minStepSize, 1e-12),
                        Math.max(maxStepSize, minStepSize),
                        absTol,
                        relTol
                );

        // Use h only as an initial step-size guess
        final double h0 = Math.max(minStepSize, Math.min(Math.abs(h), maxStepSize));
        integrator.setInitialStepSize(h0);

        // Record the trajectory at each accepted step
        integrator.addStepHandler(new StepHandler() {
            @Override
            public void init(double s0, double[] y0, double sEnd) {
                // no-op
            }

            @Override
            public void handleStep(StepInterpolator interpolator, boolean isLast) {
                final double s = interpolator.getCurrentTime();
                final double[] state = interpolator.getInterpolatedState().clone();
                listener.accept(s, state);
            }
        });

        // Event: stop at zTarget
        final HitFlag hit = new HitFlag();

        final EventHandler zEvent = new EventHandler() {
            @Override
            public void init(double s0, double[] y0, double sEnd) { }

            @Override
            public double g(double s, double[] y) {
                return y[2] - zTarget;
            }

            @Override
            public Action eventOccurred(double s, double[] y, boolean increasing) {
                hit.hit = true;
                return Action.STOP;
            }

            @Override
            public void resetState(double s, double[] y) { }
        };

        final double maxCheckInterval = Math.max(0.5, h0);
        final double eventConv = Math.max(1.0e-12, targetMiss);
        integrator.addEventHandler(zEvent, maxCheckInterval, eventConv, 200);

        // Integrate
        double sFinal;
        try {
            sFinal = integrator.integrate(ode, 0.0, y, sMax, y);
        } catch (Exception ex) {
            listener.setStatus(CLAS12Swimmer.SWIM_TARGET_MISSED);
            return new CLAS12SwimResult(listener);
        }

        // Ensure final point captured even if the last step handler didn't run as expected
        listener.accept(sFinal, y.clone());

        // Status
        if (hit.hit && Math.abs(listener.getU()[2] - zTarget) <= successTol) {
            listener.setStatus(CLAS12Swimmer.SWIM_SUCCESS);
        } else {
            listener.setStatus(CLAS12Swimmer.SWIM_TARGET_MISSED);
        }

        return new CLAS12SwimResult(listener);
    }


    // -------------------------------------------------------------------------
    // The rest of the interface: skeleton placeholders
    // -------------------------------------------------------------------------

     @Override
    public CLAS12SwimResult swim(int q, double xo, double yo, double zo, double p, double theta, double phi,
                                 double sMax, double h, double tolerance) {

        final CLAS12Values ivals = new CLAS12Values(q, xo, yo, zo, p, theta, phi);
        final CLAS12Listener listener = new CLAS12Listener(ivals, sMax);

        // Momentum guard (match CLAS12Swimmer behavior)
        if (p < minMomentum) {
            listener.setStatus(CLAS12Swimmer.BELOW_MIN_MOMENTUM);
            return new CLAS12SwimResult(listener);
        }

        // Neutral shortcut: if permitted, do an exact straight-line propagation to sMax
        if (q == 0 && listener.canMakeStraightLine()) {
            listener.straightLine();
            return new CLAS12SwimResult(listener);
        }

        // Initial state y0 = [x,y,z, tx,ty,tz]
        final double[] y0 = ivals.getU();
        final double[] y = y0.clone();

        final FirstOrderDifferentialEquations ode = new SwimEquations(q, p, probe);

        // Per-component tolerances
        final double absPos = legacyComparable ? 1.0e-5 : Math.max(1.0e-12, tolerance); // cm
        final double absDir = legacyComparable ? 1.0e-5 : 1.0e-10;                      // dimensionless
        final double rel    = legacyComparable ? 1.0e-9 : 1.0e-12;

        final double[] absTol = new double[] { absPos, absPos, absPos, absDir, absDir, absDir };
        final double[] relTol = new double[] { rel, rel, rel, rel, rel, rel };

        // Adaptive integrator: allow step-size growth up to maxStepSize; use h only as initial guess.
        final DormandPrince54Integrator integrator =
                new DormandPrince54Integrator(
                        Math.max(minStepSize, 1e-12),
                        Math.max(maxStepSize, minStepSize),
                        absTol,
                        relTol
                );

        final double h0 = Math.max(minStepSize, Math.min(Math.abs(h), maxStepSize));
        integrator.setInitialStepSize(h0);

        // Record trajectory at accepted steps
        integrator.addStepHandler(new StepHandler() {
            @Override
            public void init(double t0, double[] y0, double t) {
                // listener.reset() already added the initial point
            }

            @Override
            public void handleStep(StepInterpolator interpolator, boolean isLast) {
                final double s = interpolator.getCurrentTime();
                final double[] state = interpolator.getInterpolatedState().clone();
                listener.accept(s, state);
            }
        });

        try {
            integrator.integrate(ode, 0.0, y, sMax, y);
        } catch (Exception ex) {
            listener.setStatus(CLAS12Swimmer.SWIM_TARGET_MISSED);
            return new CLAS12SwimResult(listener);
        }

        // For the basic swim, reaching sMax is considered success.
        listener.setStatus(CLAS12Swimmer.SWIM_SUCCESS);
        return new CLAS12SwimResult(listener);
    }

    @Override
    public CLAS12SwimResult swimFixed(int q, double xo, double yo, double zo, double p, double theta, double phi,
                                      double sMax, double h) {
        throw new UnsupportedOperationException("Not implemented yet (Commons Math swimmer)");
    }

    @Override
    public CLAS12SwimResult swimCylinder(int q, double xo, double yo, double zo, double p, double theta, double phi,
                                         double[] p1, double[] p2, double r, double accuracy, double sMax, double h,
                                         double tolerance) {
        throw new UnsupportedOperationException("Not implemented yet (Commons Math swimmer)");
    }

    @Override
    public CLAS12SwimResult swimCylinder(int q, double xo, double yo, double zo, double p, double theta, double phi,
                                         Cylinder targetCylinder, double accuracy, double sMax, double h,
                                         double tolerance) {
        throw new UnsupportedOperationException("Not implemented yet (Commons Math swimmer)");
    }

    @Override
    public CLAS12SwimResult swimSphere(int q, double xo, double yo, double zo, double p, double theta, double phi,
                                       double[] center, double r, double accuracy, double sMax, double h,
                                       double tolerance) {
        throw new UnsupportedOperationException("Not implemented yet (Commons Math swimmer)");
    }

    @Override
    public CLAS12SwimResult swimSphere(int q, double xo, double yo, double zo, double p, double theta, double phi,
                                       Sphere targetSphere, double accuracy, double sMax, double h,
                                       double tolerance) {
        throw new UnsupportedOperationException("Not implemented yet (Commons Math swimmer)");
    }

    @Override
    public CLAS12SwimResult swimPlane(int q, double xo, double yo, double zo, double p, double theta, double phi,
                                      double nx, double ny, double nz, double px, double py, double pz,
                                      double accuracy, double sMax, double h, double tolerance) {
        throw new UnsupportedOperationException("Not implemented yet (Commons Math swimmer)");
    }

    @Override
    public CLAS12SwimResult swimPlane(int q, double xo, double yo, double zo, double p, double theta, double phi,
                                      double[] norm, double[] point, double accuracy, double sMax, double h,
                                      double tolerance) {
        throw new UnsupportedOperationException("Not implemented yet (Commons Math swimmer)");
    }

    /**
     * Swim to a fixed plane, stopping when the trajectory intersects the plane
     * or when the path length {@code sMax} is reached.
     *
     * <p><b>Units:</b> positions in cm, momentum in GeV/c, angles in degrees.</p>
     */
    @Override
    public CLAS12SwimResult swimPlane(int q,
                                     double xo, double yo, double zo,
                                     double p, double theta, double phi,
                                     Plane plane,
                                     double accuracy,
                                     double sMax,
                                     double h,
                                     double tolerance) {

        final CLAS12Values ivals = new CLAS12Values(q, xo, yo, zo, p, theta, phi);
        final CLAS12PlaneListener listener =
                new CLAS12PlaneListener(ivals, plane, accuracy, sMax);

        // Momentum guard
        if (p < minMomentum) {
            listener.setStatus(CLAS12Swimmer.BELOW_MIN_MOMENTUM);
            return new CLAS12SwimResult(listener);
        }

        // Neutral shortcut
        if (q == 0 && listener.canMakeStraightLine()) {
            listener.straightLine();
            return new CLAS12SwimResult(listener);
        }

        // Initial state y = [x,y,z, tx,ty,tz]
        final double[] y = ivals.getU().clone();
        final SwimEquations ode = new SwimEquations(q, p, probe);

        // ------------------------------------------------------------
        // legacyComparable tuning (same philosophy as swimZ)
        // ------------------------------------------------------------
        final double targetMiss = legacyComparable ? 1.0e-5 : accuracy;
        final double successTol = legacyComparable ? targetMiss : accuracy;

        final double absPos = legacyComparable
                ? 1.0e-5
                : Math.max(1.0e-12, tolerance);

        final double absDir = legacyComparable ? 1.0e-5 : 1.0e-10;
        final double rel    = legacyComparable ? 1.0e-9 : 1.0e-12;

        final double[] absTol = {
                absPos, absPos, absPos,
                absDir, absDir, absDir
        };
        final double[] relTol = {
                rel, rel, rel, rel, rel, rel
        };

        final DormandPrince54Integrator integrator =
                new DormandPrince54Integrator(
                        Math.max(minStepSize, 1.0e-12),
                        Math.max(maxStepSize, minStepSize),
                        absTol,
                        relTol
                );

        // Initial step-size guess
        final double h0 = Math.max(minStepSize, Math.min(Math.abs(h), maxStepSize));
        integrator.setInitialStepSize(h0);

        // Record every accepted step (matches swimZ behavior)
        integrator.addStepHandler(new StepHandler() {
            @Override
            public void init(double s0, double[] y0, double sEnd) { }

            @Override
            public void handleStep(StepInterpolator interpolator, boolean isLast) {
                final double s = interpolator.getCurrentTime();
                final double[] state = interpolator.getInterpolatedState().clone();
                listener.accept(s, state);
            }
        });

        // ------------------------------------------------------------
        // Plane event: signed distance = 0
        // ------------------------------------------------------------
        final HitFlag hit = new HitFlag();

        final EventHandler planeEvent = new EventHandler() {

            @Override
            public void init(double s0, double[] y0, double sEnd) { }

            @Override
            public double g(double s, double[] y) {
                return plane.signedDistance(y[0], y[1], y[2]);
            }

            @Override
            public Action eventOccurred(double s, double[] y, boolean increasing) {
                hit.hit = true;
                return Action.STOP;
            }

            @Override
            public void resetState(double s, double[] y) { }
        };

        final double maxCheckInterval = Math.max(0.5, h0);
        final double eventConv = Math.max(1.0e-12, targetMiss);

        integrator.addEventHandler(planeEvent, maxCheckInterval, eventConv, 200);

        // ------------------------------------------------------------
        // Integrate
        // ------------------------------------------------------------
        double sFinal;
        try {
            sFinal = integrator.integrate(ode, 0.0, y, sMax, y);
        } catch (Exception ex) {
            listener.setStatus(CLAS12Swimmer.SWIM_TARGET_MISSED);
            return new CLAS12SwimResult(listener);
        }

        // Ensure final point is recorded
        listener.accept(sFinal, y.clone());

        // ------------------------------------------------------------
        // Status
        // ------------------------------------------------------------
        double dist = plane.distance(listener.getU()[0],
                                     listener.getU()[1],
                                     listener.getU()[2]);

        if (hit.hit && dist <= successTol) {
            listener.setStatus(CLAS12Swimmer.SWIM_SUCCESS);
        } else {
            listener.setStatus(CLAS12Swimmer.SWIM_TARGET_MISSED);
        }

        return new CLAS12SwimResult(listener);
    }

    /**
     * Swim to a fixed target z (cm) in a given CLAS12 sector using the RotatedComposite field.
     * <p>
     * This mirrors {@link CLAS12Swimmer#sectorSwimZ(...)} in spirit: it is only valid when the
     * active probe is a {@link RotatedCompositeProbe}. If not, it prints an error and returns {@code null}.
     * </p>
     */
    @Override
    public CLAS12SwimResult sectorSwimZ(int sector, int q,
                                       double xo, double yo, double zo,
                                       double p, double theta, double phi,
                                       double zTarget, double accuracy,
                                       double sMax, double h, double tolerance) {

        // Must use rotated field (match CLAS12Swimmer behavior)
        if (!(probe instanceof RotatedCompositeProbe)) {
            System.err.println("sectorSwimZ only valid with RotatedCompositeProbe.");
            return null;
        }

        final CLAS12Values ivals = new CLAS12Values(q, xo, yo, zo, p, theta, phi);
        final CLAS12ZListener listener = new CLAS12ZListener(ivals, zTarget, accuracy, sMax);

        if (p < minMomentum) {
            listener.setStatus(CLAS12Swimmer.BELOW_MIN_MOMENTUM);
            return new CLAS12SwimResult(listener);
        }

        if (q == 0 && listener.canMakeStraightLine()) {
            listener.straightLine();
            return new CLAS12SwimResult(listener);
        }

        final double[] y = ivals.getU().clone();

        final SectorSwimEquations ode =
                new SectorSwimEquations(sector, q, p, probe);

        // --- Same tuning logic as working swimZ ---
        final double targetMiss = legacyComparable ? 1.0e-5 : accuracy;
        final double successTol = legacyComparable ? targetMiss : accuracy;

        final double absPos = legacyComparable
                ? 1.0e-5
                : Math.max(1.0e-12, tolerance);

        final double absDir = legacyComparable ? 1.0e-5 : 1.0e-10;
        final double rel    = legacyComparable ? 1.0e-9 : 1.0e-12;

        final double[] absTol = { absPos, absPos, absPos, absDir, absDir, absDir };
        final double[] relTol = { rel, rel, rel, rel, rel, rel };

        final DormandPrince54Integrator integrator =
                new DormandPrince54Integrator(
                        Math.max(minStepSize, 1.0e-12),
                        Math.max(maxStepSize, minStepSize),
                        absTol,
                        relTol
                );

        final double h0 = Math.max(minStepSize, Math.min(Math.abs(h), maxStepSize));
        integrator.setInitialStepSize(h0);

        integrator.addStepHandler(new StepHandler() {
            @Override
            public void init(double s0, double[] y0, double sEnd) { }

            @Override
            public void handleStep(StepInterpolator interpolator, boolean isLast) {
                double s = interpolator.getCurrentTime();
                double[] state = interpolator.getInterpolatedState().clone();
                listener.accept(s, state);
            }
        });

        final HitFlag hit = new HitFlag();

        EventHandler zEvent = new EventHandler() {

            @Override
            public void init(double s0, double[] y0, double sEnd) { }

            @Override
            public double g(double s, double[] y) {
                return y[2] - zTarget;
            }

            @Override
            public Action eventOccurred(double s, double[] y, boolean increasing) {
                hit.hit = true;
                return Action.STOP;
            }

            @Override
            public void resetState(double s, double[] y) { }
        };

        double maxCheckInterval = Math.max(0.5, h0);
        double eventConv = Math.max(1.0e-12, targetMiss);

        integrator.addEventHandler(zEvent, maxCheckInterval, eventConv, 200);

        double sFinal;

        try {
            sFinal = integrator.integrate(ode, 0.0, y, sMax, y);
        } catch (Exception ex) {
            listener.setStatus(CLAS12Swimmer.SWIM_TARGET_MISSED);
            return new CLAS12SwimResult(listener);
        }

        listener.accept(sFinal, y.clone());

        if (hit.hit && Math.abs(listener.getU()[2] - zTarget) <= successTol) {
            listener.setStatus(CLAS12Swimmer.SWIM_SUCCESS);
        } else {
            listener.setStatus(CLAS12Swimmer.SWIM_TARGET_MISSED);
        }

        return new CLAS12SwimResult(listener);
    }


    @Override
    public CLAS12SwimResult swimRho(int q, double xo, double yo, double zo, double p, double theta, double phi,
                                    double rhoTarget, double accuracy, double sMax, double h, double tolerance) {

        final CLAS12Values ivals = new CLAS12Values(q, xo, yo, zo, p, theta, phi);
        final CLAS12RhoListener listener = new CLAS12RhoListener(ivals, rhoTarget, accuracy, sMax);

        // Momentum guard (match CLAS12Swimmer behavior)
        if (p < minMomentum) {
            listener.setStatus(CLAS12Swimmer.BELOW_MIN_MOMENTUM);
            return new CLAS12SwimResult(listener);
        }

        // Neutral shortcut: listener can compute exact straight-line intersection with rho target
        if (q == 0 && listener.canMakeStraightLine()) {
            listener.straightLine();
            return new CLAS12SwimResult(listener);
        }

        // Initial state y0 = [x,y,z, tx,ty,tz]
        final double[] y0 = ivals.getU();
        final double[] y = y0.clone();

        final FirstOrderDifferentialEquations ode = new SwimEquations(q, p, probe);

        // Tuning
        final double targetMiss = legacyComparable ? 1e-5 : accuracy;
        final double absPos = legacyComparable ? 1e-5 : Math.max(1e-12, tolerance);
        final double absDir = legacyComparable ? 1e-5 : 1e-10;
        final double rel    = legacyComparable ? 1e-9 : 1e-12;

        final double[] absTol = new double[] { absPos, absPos, absPos, absDir, absDir, absDir };
        final double[] relTol = new double[] { rel, rel, rel, rel, rel, rel };

        final DormandPrince54Integrator integrator =
                new DormandPrince54Integrator(
                        Math.max(minStepSize, 1e-12),
                        Math.max(maxStepSize, minStepSize),
                        absTol,
                        relTol
                );

        final double h0 = Math.max(minStepSize, Math.min(Math.abs(h), maxStepSize));
        integrator.setInitialStepSize(h0);

        // Record trajectory at accepted steps
        integrator.addStepHandler(new StepHandler() {
            @Override
            public void init(double t0, double[] y0, double t) {
                // listener.reset() already added the initial point
            }

            @Override
            public void handleStep(StepInterpolator interpolator, boolean isLast) {
                final double s = interpolator.getCurrentTime();
                final double[] state = interpolator.getInterpolatedState().clone();
                listener.accept(s, state);
            }
        });

        // Event: rho(s) - rhoTarget = 0, where rho = sqrt(x^2 + y^2)
        final HitFlag hit = new HitFlag();

        final EventHandler rhoEventHandler = new EventHandler() {
            @Override
            public void init(double t0, double[] y0, double t) {
                // nothing
            }

            @Override
            public double g(double s, double[] y) {
                final double rho = Math.hypot(y[0], y[1]);
                return rho - rhoTarget;
            }

            @Override
            public Action eventOccurred(double s, double[] y, boolean increasing) {
                hit.hit = true;
                return Action.STOP;
            }

            @Override
            public void resetState(double s, double[] y) {
                // no reset
            }
        };

        integrator.addEventHandler(
                rhoEventHandler,
                Math.max(0.5, h0),
                Math.max(1e-12, targetMiss),
                200
        );

        try {
            integrator.integrate(ode, 0.0, y, sMax, y);
        } catch (Exception ex) {
            listener.setStatus(CLAS12Swimmer.SWIM_TARGET_MISSED);
            return new CLAS12SwimResult(listener);
        }

        // Decide final status
        final double rhoFinal = Math.hypot(listener.getU()[0], listener.getU()[1]);
        if (hit.hit && Math.abs(rhoFinal - rhoTarget) <= (legacyComparable ? targetMiss : accuracy)) {
            listener.setStatus(CLAS12Swimmer.SWIM_SUCCESS);
        } else {
            listener.setStatus(CLAS12Swimmer.SWIM_TARGET_MISSED);
        }

        return new CLAS12SwimResult(listener);
    }

    @Override
    public CLAS12SwimResult swimZLine(int q, double xo, double yo, double zo, double p, double theta, double phi,
                                      double xb, double yb, double accuracy, double sMax, double h, double tolerance) {
        throw new UnsupportedOperationException("Not implemented yet (Commons Math swimmer)");
    }

    @Override
    public CLAS12SwimResult swimBeamline(int q, double xo, double yo, double zo, double p, double theta, double phi,
                                         double accuracy, double sMax, double h, double tolerance) {
        throw new UnsupportedOperationException("Not implemented yet (Commons Math swimmer)");
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * ODE system matching {@link CLAS12SwimODE} but in Apache Commons Math form.
     * Independent variable is path length s in cm.
     */
    private static final class SwimEquations implements FirstOrderDifferentialEquations {

        private final FieldProbe probe;
        private final double alpha;      // 1/(kG*cm), matches CLAS12SwimODE
        private final float[] b = new float[3];

        SwimEquations(int q, double p, FieldProbe probe) {
            this.probe = probe;
            // Mirror CLAS12SwimODE:
            // alpha = 1.0e-14 * q * C / p   (units: 1/(kG*cm))
            this.alpha = 1.0e-14 * q * CLAS12Swimmer.C / p;
        }

        @Override
        public int getDimension() {
            return 6;
        }

        @Override
        public void computeDerivatives(double s, double[] y, double[] yDot) {
            double Bx = 0.0, By = 0.0, Bz = 0.0;

            if (probe != null) {
                probe.field((float) y[0], (float) y[1], (float) y[2], b);
                Bx = b[0];
                By = b[1];
                Bz = b[2];
            }

            // dr/ds = t
            yDot[0] = y[3];
            yDot[1] = y[4];
            yDot[2] = y[5];

            // dt/ds = alpha * (t x B)
            yDot[3] = alpha * (y[4] * Bz - y[5] * By);
            yDot[4] = alpha * (y[5] * Bx - y[3] * Bz);
            yDot[5] = alpha * (y[3] * By - y[4] * Bx);
        }
    }
    
    /**
     * Sector-aware ODE system for sector-dependent swimming with a {@link RotatedCompositeProbe}.
     * <p>
     * The independent variable is the path length {@code s} in cm.
     * </p>
     *
     * <p>
     * This implementation tries to call a sector-aware method on the probe via reflection:
     * {@code field(int sector, float x, float y, float z, float[] b)}.
     * If not found (or invocation fails), it falls back to {@code probe.field(x,y,z,b)}.
     * </p>
     */
    private static final class SectorSwimEquations implements FirstOrderDifferentialEquations {

        private final int sector;
        private final FieldProbe probe;
        private final double alpha;      // 1/(kG*cm), matches CLAS12SwimODE
        private final float[] b = new float[3];

        private long fieldEvaluations = 0L;

        // Cached reflective call (lazy init)
        private transient java.lang.reflect.Method sectorFieldMethod;
        private transient boolean searched = false;

        SectorSwimEquations(int sector, int q, double p, FieldProbe probe) {
            this.sector = sector;
            this.probe = probe;
            this.alpha = 1.0e-14 * q * CLAS12Swimmer.C / p;
        }

        @Override
        public int getDimension() {
            return 6;
        }

        long getFieldEvaluations() {
            return fieldEvaluations;
        }

        @Override
        public void computeDerivatives(double s, double[] y, double[] yDot) {

            double Bx = 0.0, By = 0.0, Bz = 0.0;

            if (probe != null) {
                if (!searched) {
                    searched = true;
                    sectorFieldMethod = findSectorFieldMethod(probe.getClass());
                }

                boolean ok = false;

                if (sectorFieldMethod != null) {
                    try {
                        // signature: (int, float, float, float, float[])
                        sectorFieldMethod.invoke(probe, sector, (float) y[0], (float) y[1], (float) y[2], b);
                        ok = true;
                    } catch (Throwable t) {
                        // Disable and fall back for remainder of this swim
                        sectorFieldMethod = null;
                    }
                }

                if (!ok) {
                    probe.field((float) y[0], (float) y[1], (float) y[2], b);
                }

                fieldEvaluations++;
                Bx = b[0];
                By = b[1];
                Bz = b[2];
            }

            // dr/ds = t
            yDot[0] = y[3];
            yDot[1] = y[4];
            yDot[2] = y[5];

            // dt/ds = alpha * (t x B)
            yDot[3] = alpha * (y[4] * Bz - y[5] * By);
            yDot[4] = alpha * (y[5] * Bx - y[3] * Bz);
            yDot[5] = alpha * (y[3] * By - y[4] * Bx);
        }

        private static java.lang.reflect.Method findSectorFieldMethod(Class<?> cls) {
            try {
                return cls.getMethod("field", int.class, float.class, float.class, float.class, float[].class);
            } catch (NoSuchMethodException e) {
                return null;
            }
        }
    }


    private static final class HitFlag {
        boolean hit = false;
    }
}

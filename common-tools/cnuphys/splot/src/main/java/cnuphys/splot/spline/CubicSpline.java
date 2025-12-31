package cnuphys.splot.spline;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import org.apache.commons.math3.analysis.UnivariateFunction;
import org.apache.commons.math3.analysis.solvers.BrentSolver;
import org.apache.commons.math3.exception.NoBracketingException;

import cnuphys.splot.fit.IValueGetter;


/**
 * Natural cubic spline interpolator over tabulated data {@code (x[i], y[i])}.
 * <p>
 * This class precomputes the natural cubic spline second derivatives and can:
 * <ul>
 *   <li>Interpolate values {@link #value(double)}.</li>
 *   <li>Build an approximate derivative spline via numeric differentiation {@link #derivative()}.</li>
 *   <li>Find roots in a range using bracket-and-refine utilities {@link #findRoots(double, double)}.</li>
 *   <li>Find maxima in a range by locating zeros of the first derivative and testing the second derivative
 *       {@link #findXValsOfMaxima(double, double)}.</li>
 * </ul>
 *
 * <h2>Spline details</h2>
 * The spline is "natural": the second derivative is forced to be zero at the endpoints.
 * Between consecutive knots {@code x[i] <= x <= x[i+1]}, the interpolant is a cubic polynomial chosen so
 * that {@code y}, {@code y'} and {@code y''} are continuous across knots.
 *
 * <h2>Requirements</h2>
 * <ul>
 *   <li>{@code x[]} and {@code y[]} must be non-null, same length, and length {@code >= 2}.</li>
 *   <li>{@code x[]} must be strictly increasing (no duplicates).</li>
 * </ul>
 *
 * <h2>Out-of-range evaluation</h2>
 * If {@code x} is outside {@code [x[0], x[n-1]]}, this implementation clamps to the nearest endpoint
 * (returns {@code y[0]} or {@code y[n-1]}).
 */
public class CubicSpline implements IValueGetter {

    /** Knot x-values (strictly increasing). */
    private final double[] _x;

    /** Knot y-values. */
    private final double[] _y;

    /**
     * Precomputed second derivatives at knots for the natural cubic spline.
     * This is the classic {@code y2[]} array from Numerical Recipes' spline implementation.
     */
    private final double[] _y2;

    /**
     * Create a natural cubic spline through the provided tabulated data.
     *
     * @param x strictly increasing x-values (knots)
     * @param y y-values at the knots
     * @throws IllegalArgumentException if inputs are invalid (null, length mismatch, length &lt; 2, non-increasing x)
     */
    public CubicSpline(double[] x, double[] y) {
        if (x == null || y == null) {
            throw new IllegalArgumentException("x and y must be non-null.");
        }
        if (x.length != y.length) {
            throw new IllegalArgumentException("x and y must have the same length.");
        }
        if (x.length < 2) {
            throw new IllegalArgumentException("Need at least two points for a spline.");
        }
        // Defensive copies
        _x = Arrays.copyOf(x, x.length);
        _y = Arrays.copyOf(y, y.length);

        // Validate strict monotonicity
        for (int i = 1; i < _x.length; i++) {
            if (!(_x[i] > _x[i - 1])) {
                throw new IllegalArgumentException("x must be strictly increasing (no duplicates). Problem at i=" + i);
            }
        }

        _y2 = computeNaturalSecondDerivatives(_x, _y);
    }

    /**
     * Number of spline knots.
     *
     * @return number of x points defining the spline
     */
    public int size() {
        return (_x == null) ? 0 : _x.length;
    }

    /**
     * Minimum x value of the spline domain.
     *
     * @return minimum x, or NaN if undefined
     */
    public double xmin() {
        if (_x == null || _x.length == 0) {
            return Double.NaN;
        }
        return _x[0];
    }

    /**
     * Maximum x value of the spline domain.
     *
     * @return maximum x, or NaN if undefined
     */
    public double xmax() {
        if (_x == null || _x.length == 0) {
            return Double.NaN;
        }
        return _x[_x.length - 1];
    }

    /**
     * Whether the spline is properly initialized.
     *
     * @return true if x/y arrays are non-null and have length ≥ 2
     */
    public boolean isValid() {
        return (_x != null && _y != null && _x.length >= 2 && _x.length == _y.length);
    }

    /**
     * Evaluate the natural cubic spline interpolant.
     * <p>
     * The interval containing {@code x} is found with a binary search, then the standard cubic-spline
     * interpolation formula is used.
     *
     * @param x query x-value
     * @return interpolated y-value; clamped to endpoints if {@code x} is outside the knot range
     */
    @Override
    public double value(double x) {
        final int n = _x.length;

        // Clamp outside range
        if (x <= _x[0]) {
            return _y[0];
        }
        if (x >= _x[n - 1]) {
            return _y[n - 1];
        }

        // Binary search for the right interval: find klo such that x[klo] <= x < x[klo+1]
        int klo = 0;
        int khi = n - 1;
        while (khi - klo > 1) {
            int k = (khi + klo) >>> 1;
            if (_x[k] > x) {
                khi = k;
            } else {
                klo = k;
            }
        }

        double h = _x[khi] - _x[klo];
        // h cannot be 0 because x is strictly increasing
        double a = (_x[khi] - x) / h;
        double b = (x - _x[klo]) / h;

        // Standard cubic spline interpolation:
        // y = a*y[klo] + b*y[khi] + ((a^3-a)*y2[klo] + (b^3-b)*y2[khi])*(h^2)/6
        return a * _y[klo]
                + b * _y[khi]
                + ((a * a * a - a) * _y2[klo] + (b * b * b - b) * _y2[khi]) * (h * h) / 6.0;
    }

    /**
     * Obtain a cubic spline that approximates the derivative of this spline by numeric differentiation
     * at the knots, then spline-interpolating those derivative samples.
     * <p>
     * The derivatives are estimated using:
     * <ul>
     *   <li>Centered differences for interior points</li>
     *   <li>One-sided differences for the endpoints</li>
     * </ul>
     * This is robust and simple. If you need an analytic derivative of the spline itself, you could
     * compute it directly from the spline coefficients; this method instead builds a new spline based
     * on sampled slopes at knots.
     *
     * @return a new {@link CubicSpline} approximating {@code dy/dx}
     */
    public CubicSpline derivative() {
        int n = _x.length;
        final double[] deriv = new double[n];

        if (n == 2) {
            // Only one interval; constant slope everywhere
            double slope = (_y[1] - _y[0]) / (_x[1] - _x[0]);
            deriv[0] = slope;
            deriv[1] = slope;
            return new CubicSpline(_x, deriv);
        }

        // Endpoints: one-sided differences
        deriv[0] = (_y[1] - _y[0]) / (_x[1] - _x[0]);
        deriv[n - 1] = (_y[n - 1] - _y[n - 2]) / (_x[n - 1] - _x[n - 2]);

        // Interior: centered differences (non-uniform x spacing supported)
        for (int i = 1; i < n - 1; i++) {
            double dx = _x[i + 1] - _x[i - 1];
            deriv[i] = (_y[i + 1] - _y[i - 1]) / dx;
        }

        return new CubicSpline(_x, deriv);
    }

    /**
     * Find the roots of the interpolated function in a range.
     * <p>
     * This method:
     * <ol>
     *   <li>Uses {@link Zbrak#zbrak(UniVarRealValueFun, double, double, int)} to bracket sign changes.</li>
     *   <li>For each bracketed interval, refines the root using {@link RtSafe#rtsafe(UniVarRealValueFun, double, double, double)}.</li>
     * </ol>
     *
     * <p><b>Dependencies:</b> This assumes your project provides Numerical-Recipes-like helpers:
     * {@code UniVarRealValueFun}, {@code Zbrak}, and {@code RtSafe}.
     *
     * @param xmin the min value of the search range
     * @param xmax the max value of the search range
     * @return an array of roots (sorted ascending), or {@code null} if none are found
     * @throws IllegalArgumentException if {@code xmax <= xmin}
     */

    public double[] findRoots(double xmin, double xmax) {
        if (xmax <= xmin) {
            throw new IllegalArgumentException("xmax must be > xmin.");
        }

        // Adapter: your spline -> Commons Math function
        UnivariateFunction f = this::value;

        // Solver: Brent is robust and fast for continuous functions with a bracket
        final double relAcc = 1e-12;
        final double absAcc = 1e-10;
        BrentSolver solver = new BrentSolver(relAcc, absAcc);

        // Scan resolution (like your old zbrak(..., 100))
        final int nScan = 200;
        final double dx = (xmax - xmin) / nScan;

        List<Double> roots = new ArrayList<>();

        double x1 = xmin;
        double f1 = f.value(x1);

        for (int i = 1; i <= nScan; i++) {
            double x2 = (i == nScan) ? xmax : (xmin + i * dx);
            double f2 = f.value(x2);

            // If exactly zero at the left sample, keep it.
            if (f1 == 0.0) {
                addDedup(roots, x1, absAcc * 10);
            }

            // Sign change => bracketed root in [x1, x2]
            if (f1 * f2 < 0.0) {
                try {
                    double r = solver.solve(200, f, x1, x2);
                    addDedup(roots, r, absAcc * 100);
                } catch (NoBracketingException ex) {
                    // Should be rare since we detected a sign change; ignore if it happens
                }
            }

            x1 = x2;
            f1 = f2;
        }

        if (roots.isEmpty()) {
            return null;
        }

        double[] out = new double[roots.size()];
        for (int i = 0; i < out.length; i++) out[i] = roots.get(i);
        Arrays.sort(out);
        return out;
    }

    private static void addDedup(List<Double> roots, double x, double eps) {
        for (double r : roots) {
            if (Math.abs(r - x) <= eps) return;
        }
        roots.add(x);
    }

    /**
     * Find the {@code (x, y)} values of the maxima of the interpolated function in a given range.
     * <p>
     * Implementation strategy:
     * <ol>
     *   <li>Compute a spline approximation to the first derivative.</li>
     *   <li>Find its roots (critical points) in {@code [xmin, xmax]}.</li>
     *   <li>Compute a spline approximation to the second derivative.</li>
     *   <li>Keep only critical points where the second derivative is negative (local maxima).</li>
     *   <li>Return the corresponding {@code (x, f(x))} pairs, sorted by descending {@code y} if there
     *       is more than one.</li>
     * </ol>
     *
     * @param xmin the min value of the range
     * @param xmax the max value of the range
     * @return an array of maxima points, or {@code null} if none are found
     */
    public Point2D.Double[] findXValsOfMaxima(double xmin, double xmax) {

        // find zeroes of derivatives
        CubicSpline firstDeriv = derivative();
        double[] roots = firstDeriv.findRoots(xmin, xmax);
        if (roots == null) {
            return null;
        }

        // get second deriv
        CubicSpline secondDeriv = firstDeriv.derivative();

        // count maxima
        int count = 0;
        for (double x : roots) {
            if (secondDeriv.value(x) < 0.0) {
                count++;
            }
        }

        if (count < 1) {
            return null;
        }

        double[] xvals = new double[count];
        int ncount = 0;
        for (double x : roots) {
            if (secondDeriv.value(x) < 0.0) {
                xvals[ncount] = x;
                ncount++;
                if (ncount == count) {
                    break;
                }
            }
        }

        Point2D.Double[] results = new Point2D.Double[count];
        for (int i = 0; i < count; i++) {
            results[i] = new Point2D.Double(xvals[i], value(xvals[i]));
        }

        if (count > 1) {
            Comparator<Point2D.Double> comp = new Comparator<>() {
                @Override
                public int compare(Point2D.Double p0, Point2D.Double p1) {
                    return Double.compare(p1.y, p0.y);
                }
            };
            Arrays.sort(results, comp);
        }

        return results;
    }

    // ======================================================================
    // Internal helpers
    // ======================================================================

    /**
     * Compute natural cubic spline second derivatives at knots.
     * <p>
     * This is the standard tridiagonal solve for {@code y2[]} with boundary conditions
     * {@code y2[0]=y2[n-1]=0}.
     */
    private static double[] computeNaturalSecondDerivatives(double[] x, double[] y) {
        int n = x.length;
        double[] y2 = new double[n];
        double[] u = new double[n - 1];

        // Natural spline boundary conditions
        y2[0] = 0.0;
        u[0] = 0.0;

        for (int i = 1; i < n - 1; i++) {
            double sig = (x[i] - x[i - 1]) / (x[i + 1] - x[i - 1]);
            double p = sig * y2[i - 1] + 2.0;
            y2[i] = (sig - 1.0) / p;

            double dy1 = (y[i] - y[i - 1]) / (x[i] - x[i - 1]);
            double dy2v = (y[i + 1] - y[i]) / (x[i + 1] - x[i]);
            double dd = (6.0 * (dy2v - dy1) / (x[i + 1] - x[i - 1]) - sig * u[i - 1]) / p;
            u[i] = dd;
        }

        // Natural spline at upper boundary
        y2[n - 1] = 0.0;

        // Back substitution
        for (int k = n - 2; k >= 0; k--) {
            y2[k] = y2[k] * y2[k + 1] + u[k];
        }

        return y2;
    }

    /**
     * Insert a value into a list while de-duplicating against existing values within {@code eps}.
     * The list may be in any order; we simply avoid adding near-duplicates.
     */
    private static void addDedupSorted(List<Double> values, double v, double eps) {
        for (double existing : values) {
            if (Math.abs(existing - v) <= eps) {
                return;
            }
        }
        values.add(v);
    }
}


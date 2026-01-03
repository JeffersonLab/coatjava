package cnuphys.splot.fit;

import java.util.Arrays;
import java.util.stream.IntStream;

import org.apache.commons.math3.linear.RealMatrix;

import cnuphys.splot.plot.SmartDoubleFormatter;
import cnuphys.splot.plot.UnicodeSupport;

/**
 * Generic result for a (weighted) least-squares fit.
 * <p>
 * This class is intentionally model-agnostic: it stores parameter values and common diagnostics
 * that apply across nonlinear and linear least-squares fits (Gaussian, erf/erfc, exponential,
 * polynomial, etc.).
 */
public final class FitResult {
	
	//helpers from unicode
	public static final String CHI = UnicodeSupport.SMALL_CHI;
	public static final String CHISQ = CHI + UnicodeSupport.SUPER2;

    /** Best-fit parameters. Interpretation depends on the model. */
    public final double[] params;

    /** Gets descriptive strings from fitter */
    public IFitStringGetter stringGetter;
    
    /** Optional covariance matrix of parameters (p x p), or null if unavailable. */
    public final RealMatrix covariance;

    /**
     * Least-squares cost: sqrt(sum r_i^2) where r_i are residuals.
     * If weights are used, r_i are in the weighted residual space.
     */
    public final double cost;

    /** Chi-square: sum r_i^2 = cost^2 (weighted if weights used). */
    public final double chiSquare;

    /** Degrees of freedom: max(1, nPoints - nParameters). */
    public final int dof;

    /** Reduced chi-square: chiSquare / dof. */
    public final double chiSquareReduced;

    /** RMS of residuals (same residual space as optimizer). */
    public final double rms;

    /** Iterations used by an iterative optimizer; may be 0 for closed-form fits. */
    public final int iterations;

    /** Evaluations used by an iterative optimizer; may be 0 for closed-form fits. */
    public final int evaluations;
    
    /** "Use as a function" evaluator for the fit. */
    public Evaluator evaluator;

    /**
	 * Create a fit result.
	 *
	 * @param stringGetter    acess to descriptive strings about fitter
	 * @param params          best-fit parameters
	 * @param covariance      optional covariance matrix (may be null)
	 * @param cost            least-squares cost
	 * @param chiSquare       chi-square
	 * @param dof             degrees of freedom
	 * @param chiSquareReduced reduced chi-square
	 * @param rms             RMS of residuals
	 * @param iterations      iterations used (0 if not applicable)
	 * @param evaluations     evaluations used (0 if not applicable)
	 */
    protected FitResult(IFitStringGetter stringGetter,
                     double[] params,
                     RealMatrix covariance,
                     double cost,
                     double chiSquare,
                     int dof,
                     double chiSquareReduced,
                     double rms,
                     int iterations,
                     int evaluations) {

    	this.stringGetter = stringGetter;
        this.params = params.clone();
        this.covariance = covariance;
        this.cost = cost;
        this.chiSquare = chiSquare;
        this.dof = dof;
        this.chiSquareReduced = chiSquareReduced;
        this.rms = rms;
        this.iterations = iterations;
        this.evaluations = evaluations;
    }
    
    /**
     * Set the evaluator.
     * @param evaluator
     */
    public void setEvaluator(Evaluator evaluator) {
		this.evaluator = evaluator;
	}

    /** @return number of parameters. */
    public int nParams() {
        return params.length;
    }

    /** Convenience access to parameter i. */
    public double param(int i) {
        return params[i];
    }

    /** @return true if covariance is present. */
    public boolean hasCovariance() {
        return covariance != null;
    }

    /**
     * Standard errors for parameters computed from covariance: sqrt(cov[i,i]).
     * Returns NaN for entries where covariance is missing or diagonal is non-positive.
     */
    public double[] paramStdErrors() {
        double[] se = new double[params.length];
        if (covariance == null) {
            Arrays.fill(se, Double.NaN);
            return se;
        }

        int p = Math.min(params.length,
                Math.min(covariance.getRowDimension(), covariance.getColumnDimension()));

        Arrays.fill(se, Double.NaN);
        for (int i = 0; i < p; i++) {
            double v = covariance.getEntry(i, i);
            se[i] = (v > 0.0 && Double.isFinite(v)) ? Math.sqrt(v) : Double.NaN;
        }
        return se;
    }

    /** Standard error for parameter i (NaN if unavailable). */
    public double paramStdError(int i) {
        if (covariance == null) {
            return Double.NaN;
        }
        if (i < 0 || i >= covariance.getRowDimension() || i >= covariance.getColumnDimension()) {
            return Double.NaN;
        }
        double v = covariance.getEntry(i, i);
        return (v > 0.0 && Double.isFinite(v)) ? Math.sqrt(v) : Double.NaN;
    }

    /**
     * Correlation coefficient between parameters i and j:
     * corr(i,j) = cov(i,j) / sqrt(cov(i,i) cov(j,j)).
     * Returns NaN if unavailable.
     */
    public double correlation(int i, int j) {
        if (covariance == null) {
            return Double.NaN;
        }
        if (i < 0 || j < 0 ||
            i >= covariance.getRowDimension() || j >= covariance.getRowDimension() ||
            i >= covariance.getColumnDimension() || j >= covariance.getColumnDimension()) {
            return Double.NaN;
        }
        double cii = covariance.getEntry(i, i);
        double cjj = covariance.getEntry(j, j);
        if (!(cii > 0.0) || !(cjj > 0.0)) {
            return Double.NaN;
        }
        double cij = covariance.getEntry(i, j);
        return cij / Math.sqrt(cii * cjj);
    }

    /** Convenience: one-sigma interval [p-σ, p+σ] for parameter i (NaNs if unavailable). */
    public double[] oneSigmaInterval(int i) {
        double s = paramStdError(i);
        if (!Double.isFinite(s)) {
            return new double[] { Double.NaN, Double.NaN };
        }
        double p = param(i);
        return new double[] { p - s, p + s };
    }

    @Override
    public String toString() {
    	
    	StringBuilder sb = new StringBuilder();
    	sb.append("FitResult:\n");
    	sb.append(" Model: " + stringGetter.modelName() + "\n");
    	sb.append(" Form: " + stringGetter.functionForm() + "\n");
    	sb.append(" Parameters:\n");
  
    	IntStream.range(0, params.length)
        .forEach(i ->
            sb.append(String.format(" %s = %.3g%n", stringGetter.parameterName(i), params[i]))
        );  
      	sb.append(String.format(" %s: %.3g\n", CHISQ, chiSquare));
    	sb.append(String.format(" %s/DoF: " + doubleFormat(chiSquareReduced, 3) + "\n", CHISQ));
    	sb.append(" Degrees of Freedom: " + dof + "\n");
    	sb.append(String.format(" RMS: %.3g\n", rms));
    	sb.append(" Iterations: " + iterations + "\n");
    	sb.append(" Evaluations: " + evaluations + "\n");
    	return sb.toString();
    }
    
    // Helper to format doubles
	private String doubleFormat(double value, int sigFig) {
		return SmartDoubleFormatter.doubleFormat(value, sigFig);
	}
}

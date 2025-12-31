package cnuphys.splot.pdata;

import java.util.Objects;

/**
 * Immutable fit vectors (x, y, optional weights) used by the Apache
 * least-squares fitters.
 *
 * <p>
 * Weights are derived from the Y-error column {@code e} using
 * {@code w = 1/(e^2)}. If an error value is non-finite or {@code <= 0}, the
 * corresponding weight is set to 0, which effectively removes that point from a
 * weighted fit.
 * </p>
 *
 * <p>
 * <b>Important:</b> This class validates that X and Y have the same length, and
 * if an error column is provided, that it matches Y length as well.
 * </p>
 */
public final class FitVectors {

	/** x data (length n). */
	public final double[] x;

	/** y data (length n). */
	public final double[] y;

	/**
	 * Optional weights (length n) or null. Convention:
	 * {@code weights[i] = 1/(sigmaY[i]^2)}.
	 */
	public final double[] weights;

	/**
	 * Create fit vectors from raw arrays.
	 *
	 * @param x       the X data array (non-null)
	 * @param y       the Y data array (non-null)
	 * @param weights the weights array (may be null)
	 * @throws IllegalArgumentException if array lengths are inconsistent
	 */
	/**
	 * Create fit vectors from raw arrays.
	 *
	 * @param x       the X data array (non-null)
	 * @param y       the Y data array (non-null)
	 * @param weights the weights array (may be null)
	 * @throws IllegalArgumentException if array lengths are inconsistent
	 */
	public FitVectors(double[] x, double[] y, double[] weights) {
	    Objects.requireNonNull(x, "X array cannot be null");
	    Objects.requireNonNull(y, "Y array cannot be null");

	    if (x.length != y.length) {
	        throw new IllegalArgumentException("X and Y arrays must have the same length");
	    }
	    if (weights != null && weights.length != y.length) {
	        throw new IllegalArgumentException("Weights array length must match Y length");
	    }

	    // Defensive copies for immutability
	    this.x = x.clone();
	    this.y = y.clone();

	    if (weights == null) {
	        this.weights = null;
	    } else {
	        double[] w = weights.clone();
	        // Optional sanitation: keep weights finite and non-negative
	        for (int i = 0; i < w.length; i++) {
	            double wi = w[i];
	            if (!Double.isFinite(wi) || wi < 0.0) {
	                w[i] = 0.0;
	            }
	        }
	        this.weights = w;
	    }
	}

	/**
	 * Create fit vectors from data columns.
	 *
	 * @param xcol the X data column (non-null)
	 * @param ycol the Y data column (non-null)
	 * @param ecol the E (Y error bar) data column (may be null)
	 * @throws IllegalArgumentException if column lengths are inconsistent
	 */
	public FitVectors(DataColumn xcol, DataColumn ycol, DataColumn ecol) {
		Objects.requireNonNull(xcol, "X column cannot be null");
		Objects.requireNonNull(ycol, "Y column cannot be null");

		this.x = xcol.values();
		this.y = ycol.values();

		if (x.length != y.length) {
			throw new IllegalArgumentException("X and Y columns must have the same length");
		}

		if (ecol != null) {
			if (ecol.size() != y.length) {
				throw new IllegalArgumentException("E (error) column length must match Y length");
			}
			this.weights = new double[ecol.size()];
			for (int i = 0; i < ecol.size(); i++) {
				double e = ecol.get(i);
				// weights from errors, w = 1/(e^2); invalid/unknown errors => w = 0 (ignore)
				if (Double.isFinite(e) && e > 0.0) {
					weights[i] = 1.0 / (e * e);
				} else {
					weights[i] = 0.0;
				}
			}
		} else {
			this.weights = null;
		}
	}

	/** @return number of points. */
	public int n() {
		return x.length;
	}

	/** @return true if weights are present (array is non-null). */
	public boolean hasWeights() {
		return weights != null;
	}

	/**
	 * @return true if there is at least one strictly positive weight. Useful to
	 *         avoid degenerate weighted problems when all weights are 0.
	 */
	public boolean hasAnyPositiveWeight() {
		if (weights == null) {
			return false;
		}
		for (double w : weights) {
			if (w > 0.0 && Double.isFinite(w)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Lightweight sanity check for callers that want to “attempt a fit” without
	 * throwing.
	 *
	 * @return true if x/y exist, lengths match, and there are at least 2 points
	 */
	public boolean isUsable() {
		return x != null && y != null && x.length == y.length && x.length >= 2
				&& (weights == null || weights.length == x.length);
	}
	
	
}

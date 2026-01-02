package cnuphys.splot.plot;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility for generating "nice" axis bounds and tick spacing.
 * <p>
 * Produces a tick spacing from the set {1, 2, 5}×10^n and expands the
 * requested min/max to "nice" endpoints aligned to that spacing.
 * </p>
 */
public class NiceScale {

	private double _min;
	private double _max;
	private int _numTicks = 10; // includes ends
	private double _tickSpacing;
	private double _niceMin;
	private double _niceMax;
	private final boolean _includeZero;

	/**
	 * Convenience constructor: defaults to 10 ticks including ends, not forced to include zero.
	 *
	 * @param min minimum data value
	 * @param max maximum data value
	 */
	public NiceScale(double min, double max) {
		this(min, max, 10, false);
	}

	/**
	 * Convenience constructor: not forced to include zero.
	 *
	 * @param min      minimum data value
	 * @param max      maximum data value
	 * @param numTicks number of ticks including ends (min 2 recommended)
	 */
	public NiceScale(double min, double max, int numTicks) {
		this(min, max, numTicks, false);
	}

	/**
	 * Instantiates a new instance of the NiceScale class.
	 *
	 * @param min         the minimum data point on the axis
	 * @param max         the maximum data point on the axis
	 * @param numTicks    the number of ticks including the ends
	 * @param includeZero if true, expand min/max so that 0 is included
	 */
	public NiceScale(double min, double max, int numTicks, boolean includeZero) {
		_includeZero = includeZero;
		_numTicks = Math.max(2, numTicks);

		setMinMaxPoints(min, max); // also calculates
	}

	/**
	 * Calculate and update values for tick spacing and nice minimum and maximum
	 * data points on the axis.
	 */
	private void calculate() {

		// sanitize min/max
		if (!Double.isFinite(_min) || !Double.isFinite(_max)) {
			_min = 0;
			_max = 1;
		}

		// allow reversed input
		if (_min > _max) {
			double tmp = _min;
			_min = _max;
			_max = tmp;
		}

		// degenerate range: expand a bit
		if (_min == _max) {
			double eps = (_min == 0) ? 1.0 : Math.abs(_min) * 0.05;
			_min -= eps;
			_max += eps;
		}

		if (_includeZero) {
			_min = Math.min(_min, 0);
			_max = Math.max(_max, 0);
		}

		double rawRange = _max - _min;
		double range = niceNum(rawRange, false);

		_tickSpacing = niceNum(range / (_numTicks - 1), true);

		if (!Double.isFinite(_tickSpacing) || _tickSpacing <= 0) {
			_tickSpacing = 1.0;
		}

		_niceMin = Math.floor(_min / _tickSpacing) * _tickSpacing;
		_niceMax = Math.ceil(_max / _tickSpacing) * _tickSpacing;

		// numerical safety: if endpoints collapse due to rounding, expand by one tick
		if (_niceMin == _niceMax) {
			_niceMin -= _tickSpacing;
			_niceMax += _tickSpacing;
		}
	}

	/**
	 * Get the tick spacing.
	 *
	 * @return tick spacing
	 */
	public double getTickSpacing() {
		return _tickSpacing;
	}

	/**
	 * Get the nice plot min (expanded and aligned).
	 *
	 * @return nice min
	 */
	public double getNiceMin() {
		return _niceMin;
	}

	/**
	 * Get the nice plot max (expanded and aligned).
	 *
	 * @return nice max
	 */
	public double getNiceMax() {
		return _niceMax;
	}

	/**
	 * Get the number of ticks (including the ends).
	 *
	 * @return number of ticks
	 */
	public int getNumTicks() {
		return _numTicks;
	}

	/**
	 * Sets the minimum and maximum data points for the axis.
	 * Recomputes tick spacing and nice endpoints.
	 *
	 * @param min minimum data value
	 * @param max maximum data value
	 */
	public void setMinMaxPoints(double min, double max) {
		_min = min;
		_max = max;
		calculate();
	}

	/**
	 * Sets maximum number of tick marks we're comfortable with (including ends).
	 * Recomputes tick spacing and nice endpoints.
	 *
	 * @param maxTicks maximum number of ticks (min 2)
	 */
	public void setMaxTicks(int maxTicks) {
		_numTicks = Math.max(2, maxTicks);
		calculate();
	}

	/**
	 * Return the tick locations from niceMin to niceMax inclusive.
	 *
	 * @return array of tick values (never null)
	 */
	public double[] getTicks() {
		if (!Double.isFinite(_niceMin) || !Double.isFinite(_niceMax) || !Double.isFinite(_tickSpacing) || _tickSpacing <= 0) {
			return new double[0];
		}

		List<Double> list = new ArrayList<>();
		double v = _niceMin;

		// Add a small guard so we include the endpoint despite FP rounding.
		double guard = 0.5 * _tickSpacing;

		while (v <= _niceMax + guard) {
			list.add(v);
			v += _tickSpacing;
			// safety break in pathological cases
			if (list.size() > 10000) {
				break;
			}
		}

		double[] ticks = new double[list.size()];
		for (int i = 0; i < ticks.length; i++) {
			ticks[i] = list.get(i);
		}
		return ticks;
	}

	/**
	 * The actual number of ticks returned by {@link #getTicks()}.
	 *
	 * @return tick count
	 */
	public int getTickCount() {
		return getTicks().length;
	}

	/**
	 * Returns a "nice" number approximately equal to range.
	 * Rounds the number if round = true; takes ceiling if round = false.
	 *
	 * @param range the data range
	 * @param round whether to round the result
	 * @return a "nice" number to be used for the data range
	 */
	private double niceNum(double range, boolean round) {
		if (!Double.isFinite(range) || range <= 0) {
			return 1.0;
		}

		double exponent = Math.floor(Math.log10(range));
		double fraction = range / Math.pow(10, exponent);

		double niceFraction;
		if (round) {
			if (fraction < 1.5) {
				niceFraction = 1;
			}
			else if (fraction < 3) {
				niceFraction = 2;
			}
			else if (fraction < 7) {
				niceFraction = 5;
			}
			else {
				niceFraction = 10;
			}
		}
		else {
			if (fraction <= 1) {
				niceFraction = 1;
			}
			else if (fraction <= 2) {
				niceFraction = 2;
			}
			else if (fraction <= 5) {
				niceFraction = 5;
			}
			else {
				niceFraction = 10;
			}
		}

		return niceFraction * Math.pow(10, exponent);
	}
}

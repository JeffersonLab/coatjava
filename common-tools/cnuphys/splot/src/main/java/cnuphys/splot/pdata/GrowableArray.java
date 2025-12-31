package cnuphys.splot.pdata;

import java.util.Arrays;

/**
 * A fast, primitive {@code double} buffer that grows as needed.
 * <p>
 * This is a lightweight alternative to {@code ArrayList<Double>} that avoids boxing.
 * It also tracks the minimum and maximum values over the <em>active</em> region
 * {@code [0, size)} and keeps those values accurate across {@link #add(double)},
 * {@link #removeFirst()}, {@link #set(int, double)}, and {@link #clear()}.
 * </p>
 * <p>
 * Notes:
 * <ul>
 *   <li>The backing array may be larger than {@link #size()}.</li>
 *   <li>Only indices {@code 0 .. size-1} are considered part of the data.</li>
 *   <li>{@link #getMinimalCopy()} returns an empty array when size is 0 (never null).</li>
 * </ul>
 * </p>
 *
 * @author heddle (original)
 */
public class GrowableArray {

	/** The backing array (capacity may exceed {@link #_dataLen}). */
	protected double _data[];

	/** Current number of data values in use. */
	protected int _dataLen;

	/**
	 * Legacy fixed increment (retained for API/backward compatibility).
	 * <p>
	 * The implementation now uses geometric growth for performance, but we keep
	 * this field since existing code/config might set it.
	 * </p>
	 */
	protected int _increment;

	/** Initial capacity. */
	protected int _initCap;

	/** Min and max of active data (0..size-1). */
	protected double _minValue;
	protected double _maxValue;

	/**
	 * Creates a GrowableArray with initial capacity 100 and increment 100.
	 */
	public GrowableArray() {
		this(100, 100);
	}

	/**
	 * Create a GrowableArray.
	 *
	 * @param initCap   the initial capacity (if &lt;= 0, defaults to 16)
	 * @param increment legacy increment when the array grows (if &lt;= 0, defaults to 16)
	 */
	public GrowableArray(int initCap, int increment) {
		_initCap = (initCap > 0) ? initCap : 16;
		_increment = (increment > 0) ? increment : 16;
		clear();
	}

	/**
	 * Get the number of real data in the array, which in general is less than the
	 * capacity of the backing array.
	 *
	 * @return the number of active data values
	 */
	public int size() {
		return _dataLen;
	}

	/**
	 * True if {@link #size()} is zero.
	 *
	 * @return true if empty
	 */
	public boolean isEmpty() {
		return _dataLen == 0;
	}

	/**
	 * Get a copy of the active data region as a "just the right size" array.
	 *
	 * @return a copy of the data of length {@link #size()} (never null)
	 */
	public double[] getMinimalCopy() {
		if (_dataLen == 0) {
			return new double[0];
		}
		return Arrays.copyOf(_data, _dataLen);
	}

	/**
	 * Removes the first entry and shifts all other entries down by one.
	 * <p>
	 * This is an O(n) operation. If you do this frequently (sliding window),
	 * consider a ring buffer structure instead.
	 * </p>
	 */
	public void removeFirst() {
		if (_dataLen <= 0) {
			return;
		}

		// shift left by one
		System.arraycopy(_data, 1, _data, 0, _dataLen - 1);
		_dataLen--;

		// keep debug behavior of setting the freed slot to NaN
		if (_dataLen >= 0 && _dataLen < _data.length) {
			_data[_dataLen] = Double.NaN;
		}

		// recompute min/max accurately
		recomputeMinMax();
	}

	/**
	 * Add a value to the end of the array, growing if needed.
	 *
	 * @param val the value to add
	 */
	public void add(double val) {
		ensureCapacity(_dataLen + 1);
		_data[_dataLen] = val;
		_dataLen++;

		// Update min/max accurately (O(1))
		if (_dataLen == 1) {
			_minValue = val;
			_maxValue = val;
		} else {
			_minValue = Math.min(_minValue, val);
			_maxValue = Math.max(_maxValue, val);
		}
	}

	/**
	 * Get the minimum value over the active data.
	 *
	 * @return min value, or NaN if empty
	 */
	public double getMinValue() {
		return _minValue;
	}

	/**
	 * Get the maximum value over the active data.
	 *
	 * @return max value, or NaN if empty
	 */
	public double getMaxValue() {
		return _maxValue;
	}

	/**
	 * Reset the buffer to its initial capacity and clear contents.
	 * <p>
	 * For backward compatibility with the original class, the backing array is
	 * filled with NaNs.
	 * </p>
	 */
	public void clear() {
		_data = new double[_initCap];
		_dataLen = 0;

		// Preserve original behavior: fill with NaNs
		Arrays.fill(_data, Double.NaN);

		_minValue = Double.NaN;
		_maxValue = Double.NaN;
	}

	/**
	 * Get the value at the given index.
	 *
	 * @param index the index (0..size-1)
	 * @return the value at the index
	 * @throws IndexOutOfBoundsException if index is out of range
	 */
	public double get(int index) {
		rangeCheck(index);
		return _data[index];
	}

	/**
	 * Set the value at the given index.
	 * <p>
	 * Min/max are kept accurate. If you replace an element that currently equals
	 * the min or max, this may trigger an O(n) rescan.
	 * </p>
	 *
	 * @param index the index (0..size-1)
	 * @param val   the new value
	 * @throws IndexOutOfBoundsException if index is out of range
	 */
	public void set(int index, double val) {
		rangeCheck(index);

		double old = _data[index];
		_data[index] = val;

		if (_dataLen == 0) {
			_minValue = Double.NaN;
			_maxValue = Double.NaN;
			return;
		}

		// Fast-path updates:
		// - If we're growing min/max, update in O(1)
		// - If we overwrote the current min or max, we must rescan to stay correct
		boolean mightNeedRescan = false;

		if (!Double.isNaN(_minValue) && old == _minValue) {
			mightNeedRescan = true;
		}
		if (!Double.isNaN(_maxValue) && old == _maxValue) {
			mightNeedRescan = true;
		}

		if (mightNeedRescan) {
			recomputeMinMax();
		} else {
			_minValue = Double.isNaN(_minValue) ? val : Math.min(_minValue, val);
			_maxValue = Double.isNaN(_maxValue) ? val : Math.max(_maxValue, val);
		}
	}

	// ------------------------------------------------------------------------
	// Internal helpers
	// ------------------------------------------------------------------------

	private void rangeCheck(int index) {
		if (index < 0 || index >= _dataLen) {
			throw new IndexOutOfBoundsException(
					"index=" + index + " out of range [0," + (_dataLen - 1) + "]");
		}
	}

	/**
	 * Ensure capacity for at least {@code minCapacity} elements.
	 * <p>
	 * Uses geometric growth (roughly 1.5x) for speed, but guarantees at least
	 * {@code +_increment} growth as a lower bound to keep legacy intent.
	 * </p>
	 */
	private void ensureCapacity(int minCapacity) {
		if (_data == null) {
			_data = new double[Math.max(_initCap, minCapacity)];
			Arrays.fill(_data, Double.NaN);
			return;
		}
		if (minCapacity <= _data.length) {
			return;
		}

		int oldCap = _data.length;

		// geometric growth: newCap = oldCap + oldCap/2 + 1
		int newCap = oldCap + (oldCap >> 1) + 1;

		// also respect legacy increment as a minimum growth step
		newCap = Math.max(newCap, oldCap + _increment);

		// ensure we meet required minCapacity
		newCap = Math.max(newCap, minCapacity);

		double[] newArray = Arrays.copyOf(_data, newCap);

		// Preserve original debugging behavior: fill new region with NaN
		Arrays.fill(newArray, oldCap, newCap, Double.NaN);

		_data = newArray;
	}

	/** Recompute min and max over the active data region. */
	private void recomputeMinMax() {
		if (_dataLen <= 0) {
			_minValue = Double.NaN;
			_maxValue = Double.NaN;
			return;
		}

		double min = _data[0];
		double max = _data[0];

		for (int i = 1; i < _dataLen; i++) {
			double v = _data[i];
			if (v < min) {
				min = v;
			}
			if (v > max) {
				max = v;
			}
		}

		_minValue = min;
		_maxValue = max;
	}
}

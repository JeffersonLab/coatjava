package cnuphys.splot.pdata;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Named list of {@code Double} values with cached min/max tracking.
 *
 * <p>Min/max are updated incrementally on {@link #add(Double)} for speed.
 * If values are removed or replaced, min/max are recomputed as needed to remain correct.</p>
 */
@SuppressWarnings("serial")
public class DataList extends ArrayList<Double> {

    private double min = Double.POSITIVE_INFINITY;
    private double max = Double.NEGATIVE_INFINITY;

    /**
     * Create a data list with the given name.
     *
     */
    public DataList() {
        super();
    }

   @Override
    public boolean add(Double value) {
        if (value != null) {
            if (value < min) {
                min = value;
            }
            if (value > max) {
                max = value;
            }
        }
        return super.add(value);
    }

    @Override
    public void add(int index, Double element) {
        // ensure min/max tracking
        add(element);
        // ArrayList.add(element) appends; we need correct insertion behavior:
        // So perform actual insertion, then remove the appended element.
        // (This keeps min/max correct and avoids duplicating tracking logic.)
        if (index < size() - 1) {
            Double last = super.remove(size() - 1);
            super.add(index, last);
        }
    }

    @Override
    public boolean addAll(Collection<? extends Double> c) {
        boolean changed = false;
        for (Double d : c) {
            changed |= add(d);
        }
        return changed;
    }

    @Override
    public Double remove(int index) {
        Double removed = super.remove(index);
        if (removed != null && (removed == min || removed == max)) {
            recomputeMinMax();
        }
        return removed;
    }

    @Override
    public boolean remove(Object o) {
        boolean changed = super.remove(o);
        if (changed) {
            // conservative: if we removed something, min/max may have changed
            // (especially if duplicates exist, recompute is still safe)
            recomputeMinMax();
        }
        return changed;
    }

    @Override
    public void clear() {
        super.clear();
        min = Double.POSITIVE_INFINITY;
        max = Double.NEGATIVE_INFINITY;
    }

    @Override
    public Double set(int index, Double element) {
        Double prev = super.set(index, element);
        // prev might have been min/max; element might become min/max
        recomputeMinMax();
        return prev;
    }

    /**
     * Get the minimum value in the column.
     *
     * @return the minimum value, or +infinity if empty
     */
    public double getMin() {
        return min;
    }

    /**
     * Get the maximum value in the column.
     *
     * @return the maximum value, or -infinity if empty
     */
    public double getMax() {
        return max;
    }

    /**
     * Get the values as a primitive array.
     *
     * @return values as a new primitive array
     */
    public double[] values() {
        int n = size();
        double[] array = new double[n];
        for (int i = 0; i < n; i++) {
            Double d = get(i);
            array[i] = (d == null) ? Double.NaN : d.doubleValue();
        }
        return array;
    }

    /** Recompute min/max by scanning current contents. */
    protected void recomputeMinMax() {
        double newMin = Double.POSITIVE_INFINITY;
        double newMax = Double.NEGATIVE_INFINITY;

        for (Double d : this) {
            if (d == null) {
                continue;
            }
            if (d < newMin) newMin = d;
            if (d > newMax) newMax = d;
        }

        min = newMin;
        max = newMax;
    }
}

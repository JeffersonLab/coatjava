package cnuphys.splot.pdata;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import cnuphys.splot.fit.IValueGetter;
import cnuphys.splot.fit.CurveDrawingMethod;

/**
 * Backing data model for a strip chart / time-series plot.
 *
 * <p>This class periodically samples a provided {@link IValueGetter} and appends
 * (t, y) points into bounded X and Y columns. When capacity is reached, the oldest
 * points are discarded.</p>
 *
 * @author heddle
 */
public class StripData {

    private final String name;
    private volatile int capacity;
    private volatile IValueGetter valueGetter;
    private volatile long intervalMs;

    /** Time when sampling started (ms since epoch). */
    private volatile long startTimeMs;

    /** True while actively sampling. */
    private volatile boolean running;

    /** Series data columns. Mutated under {@link #lock}. */
    private final DataColumn xData;
    private final DataColumn yData;

    /** Lazily-created curve view over the live x/y columns. */
    private volatile Curve curve;

    /** Synchronizes mutations and snapshots. */
    private final Object lock = new Object();

    /** Optional callback invoked after a new sample is appended. */
    private volatile Runnable onSample;

    /** Scheduler for periodic sampling. */
    private final ScheduledExecutorService scheduler;
    private ScheduledFuture<?> future;

    /**
     * Create strip chart (time series) data.
     *
     * @param name curve/series name
     * @param capacity max samples retained (must be >= 2)
     * @param valueGetter supplier of y(t) values (may be null if externally driven)
     * @param intervalMs sampling interval in milliseconds
     */
    public StripData(String name, int capacity, IValueGetter valueGetter, long intervalMs) {
        this.name = Objects.requireNonNull(name, "name");
        setCapacity(capacity);
        this.valueGetter = valueGetter;
        this.intervalMs = Math.max(1, intervalMs);

        this.xData = new DataColumn("t");
        this.yData = new DataColumn(name);

        this.scheduler = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "StripData-" + name);
                t.setDaemon(true);
                return t;
            }
        });
    }

    /** @return the series name. */
    public String getName() {
        return name;
    }

    /** @return the x data column (live). Prefer {@link #snapshot()} for drawing. */
    public DataColumn getXData() {
        return xData;
    }

    /** @return the y data column (live). Prefer {@link #snapshot()} for drawing. */
    public DataColumn getYData() {
        return yData;
    }

    /**
     * Get an {@link ACurve} view over this strip chart's live (x,y) columns.
     * <p>
     * The returned curve is backed directly by the internal {@link DataColumn}s. Do not replace those
     * columns; instead, append samples via {@link #addSample(double, double)} or the sampler.
     * </p>
     *
     * @return a live {@link Curve} view of this strip data (never null)
     */
    public Curve getCurve() {
        Curve c = curve;
        if (c != null) {
            return c;
        }
        synchronized (lock) {
            if (curve == null) {
                try {
                    curve = new Curve(name, xData, yData, null);
                    // A reasonable default for strip charts; callers may override.
                    curve.setCurveDrawingMethod(CurveDrawingMethod.STAIRS);
                } catch (PlotDataException e) {
                    // Should not happen because x/y are maintained consistently under lock.
                    throw new IllegalStateException("Failed to create strip chart Curve for " + name, e);
                }
            }
            return curve;
        }
    }

    /** @return current capacity (max samples retained). */
    public int getCapacity() {
        return capacity;
    }

    /**
     * Set capacity (max retained samples).
     *
     * @param capacity must be >= 2
     */
    public void setCapacity(int capacity) {
        if (capacity < 2) {
            throw new IllegalArgumentException("capacity must be >= 2");
        }
        this.capacity = capacity;

        // Enforce immediately.
        synchronized (lock) {
            trimToCapacityLocked();
        }
    }

    /** @return sampling interval in milliseconds. */
    public long getIntervalMs() {
        return intervalMs;
    }

    /**
     * Set sampling interval.
     *
     * @param intervalMs milliseconds (coerced to >= 1)
     */
    public void setIntervalMs(long intervalMs) {
        this.intervalMs = Math.max(1, intervalMs);
        if (running) {
            restart();
        }
    }

    /** @return the value getter (may be null). */
    public IValueGetter getValueGetter() {
        return valueGetter;
    }

    /** Set the value getter (may be null for externally driven strip data). */
    public void setValueGetter(IValueGetter valueGetter) {
        this.valueGetter = valueGetter;
    }

    /**
     * Set a callback to run after a sample is appended (e.g., request repaint).
     *
     * @param onSample callback, may be null
     */
    public void setOnSample(Runnable onSample) {
        this.onSample = onSample;
    }

    /** @return true if sampling is active. */
    public boolean isRunning() {
        return running;
    }

    /** Start periodic sampling. No-op if already running. */
    public void start() {
        if (running) {
            return;
        }
        running = true;
        startTimeMs = System.currentTimeMillis();
        schedule();
    }

    /** Stop periodic sampling. No-op if not running. */
    public void stop() {
        running = false;
        if (future != null) {
            future.cancel(false);
            future = null;
        }
    }

    /** Restart sampling with current interval. */
    private void restart() {
        stop();
        start();
    }

    /** Schedule periodic sampling task. */
    private void schedule() {
        if (future != null) {
            future.cancel(false);
        }
        future = scheduler.scheduleAtFixedRate(() -> sampleOnce(), 0, intervalMs, TimeUnit.MILLISECONDS);
    }

    /** Sample once from the value getter. */
    private void sampleOnce() {
        IValueGetter vg = valueGetter;
        if (!running || vg == null) {
            return;
        }

        long nowMs = System.currentTimeMillis();
        double tSeconds = (nowMs - startTimeMs) / 1000.0;
        double y = vg.value(tSeconds);

        addSample(tSeconds, y);
    }

    /**
     * Snapshot the current data into primitive arrays for drawing without holding locks.
     *
     * @return snapshot with x and y arrays
     */
    public Snapshot snapshot() {
        synchronized (lock) {
            int n = xData.size();
            double[] x = new double[n];
            double[] y = new double[n];
            for (int i = 0; i < n; i++) {
                x[i] = xData.get(i);
                y[i] = yData.get(i);
            }
            return new Snapshot(x, y);
        }
    }

    /** Holds a snapshot of x/y data. */
    public static final class Snapshot {
        public final double[] x;
        public final double[] y;
        Snapshot(double[] x, double[] y) {
            this.x = x;
            this.y = y;
        }
        public int length() { return x.length; }
    }

    /** Trim oldest points until size <= capacity. Call under lock. */
    private void trimToCapacityLocked() {
        int over = xData.size() - capacity;
        for (int i = 0; i < over; i++) {
            xData.remove(0);
            yData.remove(0);
        }
    }

    /** Clear all data. */
    public void clear() {
        Curve c = getCurve();
        c.beginUpdate();
        try {
            synchronized (lock) {
                xData.clear();
                yData.clear();
            }
            c.dataChanged();
        } finally {
            c.endUpdate();
        }
    }

    /**
     * Manually append a sample (useful for externally-driven time series).
     * Capacity rules apply.
     *
     * @param tSeconds x value in seconds
     * @param y value
     */
    public void addSample(double tSeconds, double y) {
        // Ensure curve exists so that listeners (via PlotData) can be notified.
        Curve c = getCurve();
        c.beginUpdate();
        try {
            synchronized (lock) {
                xData.add(Double.valueOf(tSeconds));
                yData.add(Double.valueOf(y));
                trimToCapacityLocked();
            }
            c.dataChanged();
        } finally {
            c.endUpdate();
        }

        Runnable r = onSample;
        if (r != null) {
            r.run();
        }
    }

    /** Shutdown scheduler when done with this instance. */
    public void shutdown() {
        stop();
        scheduler.shutdownNow();
    }
}

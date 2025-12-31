package cnuphys.splot.pdata;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import cnuphys.splot.fit.IValueGetter;

/**
 * Backing data model for a strip chart / time-series plot.
 *
 * <p>This class periodically samples a provided {@link IValueGetter} and appends
 * (t, y) points into bounded X and Y columns. When capacity is reached, the oldest
 * samples are dropped ("scrolls off the left").</p>
 *
 * <h3>Threading</h3>
 * <p>Sampling runs on a background thread. The data columns are mutated under an
 * internal lock. Plotters should use {@link #snapshot()} to obtain a consistent
 * copy of the data for drawing without needing to synchronize on the model.</p>
 *
 * <p>If your plot is Swing-based and you want repaint on the EDT, provide an
 * {@link #setOnSample(Runnable)} callback that posts a repaint using
 * {@code SwingUtilities.invokeLater(...)}.</p>
 */
public class StripData {

    /** Name (often used for legend / series label). */
    private final String name;

    /** Maximum number of samples retained. Must be >= 2. */
    private int capacity;

    /** Produces the next value given time in seconds. */
    private final IValueGetter valueGetter;

    /** Sampling period in milliseconds. Must be > 0. */
    private long intervalMs;

    /** Time when sampling started (ms since epoch). */
    private volatile long startTimeMs;

    /** True while actively sampling. */
    private volatile boolean running;

    /** Series data columns. Mutated under {@link #lock}. */
    private final DataColumn xData;
    private final DataColumn yData;

    /** Synchronizes mutations and snapshots. */
    private final Object lock = new Object();

    /** Optional callback invoked after a new sample is appended. */
    private volatile Runnable onSample;

    /** Scheduler for periodic sampling. */
    private final ScheduledExecutorService scheduler;
    private ScheduledFuture<?> future;

    /**
     * Create strip-chart data.
     *
     * @param name        series name
     * @param xData       x data column to fill (non-null)
     * @param yData       y data column to fill (non-null)
     * @param capacity    max number of retained samples (>= 2)
     * @param valueGetter value source; called as {@code valueGetter.value(tSeconds)} (non-null)
     * @param intervalMs  update interval in milliseconds (> 0)
     */
    public StripData(String name,
                     DataColumn xData,
                     DataColumn yData,
                     int capacity,
                     IValueGetter valueGetter,
                     long intervalMs) {

        this.name = Objects.requireNonNull(name, "name");
        this.xData = Objects.requireNonNull(xData, "xData");
        this.yData = Objects.requireNonNull(yData, "yData");
        this.valueGetter = Objects.requireNonNull(valueGetter, "valueGetter");

        setCapacity(capacity);
        setIntervalMs(intervalMs);

        // Dedicated single-thread scheduler, daemon thread so app can exit cleanly.
        this.scheduler = Executors.newSingleThreadScheduledExecutor(new DaemonThreadFactory("StripData-" + name));
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

    /** @return current capacity (max samples retained). */
    public int getCapacity() {
        return capacity;
    }

    /**
     * Set the capacity (max samples retained). If the current size exceeds the new capacity,
     * oldest samples are dropped immediately.
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
     * Set the sampling interval. If currently running, restarts the schedule.
     *
     * @param intervalMs must be > 0
     */
    public void setIntervalMs(long intervalMs) {
        if (intervalMs <= 0) {
            throw new IllegalArgumentException("intervalMs must be > 0");
        }
        this.intervalMs = intervalMs;

        // If running, restart to apply the new period.
        if (running) {
            stop();
            start();
        }
    }

    /**
     * Set an optional callback invoked after each appended sample.
     * Typical usage in Swing: {@code setOnSample(() -> SwingUtilities.invokeLater(panel::repaint));}
     *
     * @param onSample callback or null
     */
    public void setOnSample(Runnable onSample) {
        this.onSample = onSample;
    }

    /** @return true if actively sampling. */
    public boolean isRunning() {
        return running;
    }

    /**
     * Start sampling. Safe to call multiple times; subsequent calls do nothing if already running.
     */
    public void start() {
        if (running) {
            return;
        }
        running = true;
        startTimeMs = System.currentTimeMillis();

        future = scheduler.scheduleAtFixedRate(this::sampleOnceSafe, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Stop sampling. Safe to call multiple times.
     *
     * <p>This cancels the scheduled task but keeps the scheduler alive so you can {@link #start()} again.</p>
     */
    public void stop() {
        running = false;
        if (future != null) {
            future.cancel(false);
            future = null;
        }
    }

    /**
     * Permanently shut down this StripData (cannot be restarted).
     * Use when the owning plot is being disposed.
     */
    public void shutdown() {
        stop();
        scheduler.shutdownNow();
    }

    /**
     * Clear all retained samples.
     */
    public void clear() {
        synchronized (lock) {
            xData.clear();
            yData.clear();
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
        synchronized (lock) {
            trimOneIfFullLocked();
            xData.add(tSeconds);
            yData.add(y);
        }
        fireOnSample();
    }

    /**
     * Obtain a consistent snapshot of the current data, suitable for plotting without locking.
     *
     * @return snapshot containing primitive arrays
     */
    public Snapshot snapshot() {
        synchronized (lock) {
            return new Snapshot(xData.values(), yData.values());
        }
    }

    // ------------------------ Internals ------------------------

    private void sampleOnceSafe() {
        try {
            if (!running) {
                return;
            }
            double tSeconds = (System.currentTimeMillis() - startTimeMs) / 1000.0;
            double y = valueGetter.value(tSeconds);

            synchronized (lock) {
                trimOneIfFullLocked();
                xData.add(tSeconds);
                yData.add(y);
            }

            fireOnSample();
        }
        catch (Throwable t) {
            // Fail soft: stop sampling on unexpected exceptions to avoid runaway logs.
            stop();
        }
    }

    private void fireOnSample() {
        Runnable r = onSample;
        if (r != null) {
            try {
                r.run();
            } catch (Throwable ignored) {
                // fail soft
            }
        }
    }

    private void trimOneIfFullLocked() {
        while (xData.size() >= capacity) {
            // DataColumn extends DataList extends ArrayList, so "remove(0)" is available.
            // This is O(n), but capacity is typically small; if you need huge series,
            // we can swap in a ring buffer.
            xData.remove(0);
            yData.remove(0);
        }
    }

    private void trimToCapacityLocked() {
        while (xData.size() > capacity) {
            xData.remove(0);
            yData.remove(0);
        }
    }

    /**
     * Immutable snapshot of strip data.
     */
    public static final class Snapshot {
        public final double[] x;
        public final double[] y;

        private Snapshot(double[] x, double[] y) {
            this.x = x;
            this.y = y;
        }

        public int length() {
            return x.length;
        }
    }

    /**
     * Simple daemon thread factory for the sampler thread.
     */
    private static final class DaemonThreadFactory implements ThreadFactory {
        private final String baseName;

        private DaemonThreadFactory(String baseName) {
            this.baseName = baseName;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, baseName);
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY);
            return t;
        }
    }
}

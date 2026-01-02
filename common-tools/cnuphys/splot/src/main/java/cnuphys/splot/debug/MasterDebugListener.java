package cnuphys.splot.debug;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.swing.SwingUtilities;

import cnuphys.splot.pdata.ACurve;
import cnuphys.splot.pdata.CurveChangeListener;
import cnuphys.splot.pdata.CurveChangeType;
import cnuphys.splot.pdata.DataChangeListener;
import cnuphys.splot.pdata.PlotData;

/**
 * Development-only listener that attaches to {@link PlotData} and all of its
 * {@link ACurve}s to log and summarize change notifications.
 *
 * <h3>Enable</h3>
 * <pre>
 * -Dsplot.debug.events=true
 * </pre>
 *
 * <h3>Optional knobs</h3>
 * <ul>
 *   <li><code>-Dsplot.debug.colors=true</code> (ANSI colors)</li>
 *   <li><code>-Dsplot.debug.summarySeconds=5</code> (periodic summary; 0 disables)</li>
 *   <li><code>-Dsplot.debug.stormRate=200</code> (events/sec threshold for warnings)</li>
 *   <li><code>-Dsplot.debug.printCurveEvents=true</code> (log each curve event)</li>
 *   <li><code>-Dsplot.debug.printPlotEvents=true</code> (log each plot event)</li>
 *   <li><code>-Dsplot.debug.stackOn=DATA</code> or STYLE or FIT (prints stack for matching curve events)</li>
 * </ul>
 */
public final class MasterDebugListener implements CurveChangeListener, DataChangeListener {

    // -----------------------------
    // Enable + configuration
    // -----------------------------

    public static final boolean ENABLED =
            Boolean.getBoolean("splot.debug.events");

    private static final boolean COLORS =
            Boolean.getBoolean("splot.debug.colors");

    private static final boolean PRINT_CURVE_EVENTS =
            getBool("splot.debug.printCurveEvents", true);

    private static final boolean PRINT_PLOT_EVENTS =
            getBool("splot.debug.printPlotEvents", true);

    /** Summary interval in seconds; 0 disables summary thread. */
    private static final int SUMMARY_SECONDS =
            getInt("splot.debug.summarySeconds", 5);

    /** Rate threshold (events/sec) beyond which we warn about storms. */
    private static final double STORM_RATE =
            getDouble("splot.debug.stormRate", 200.0);

    /** Optional: print stack traces when this curve event type is seen. */
    private static final CurveChangeType STACK_ON =
            getEnum("splot.debug.stackOn", CurveChangeType.class, null);

    // -----------------------------
    // Instance state
    // -----------------------------

    private static final SimpleDateFormat TS = new SimpleDateFormat("HH:mm:ss.SSS");

    private final PlotData plotData;

    /** Curves we’ve hooked to (identity-based). */
    private final Map<ACurve, Boolean> hookedCurves = new IdentityHashMap<>();

    /** Counters: overall, by type, by curve, etc. */
    private final EnumMap<CurveChangeType, Long> totalCurveByType = new EnumMap<>(CurveChangeType.class);
    private long totalPlotEvents;

    private final Map<ACurve, EnumMap<CurveChangeType, Long>> perCurveCounts = new IdentityHashMap<>();

    /** For rate calculations. */
    private long startNanos = System.nanoTime();
    private long lastSummaryNanos = startNanos;
    private long curveEventsSinceLastSummary;
    private long plotEventsSinceLastSummary;

    /** Summary thread (optional). */
    private final ScheduledExecutorService scheduler;

    // -----------------------------
    // Construction / attachment
    // -----------------------------

    /**
     * Attach a debug listener to a PlotData and all current curves.
     *
     * @param plotData plot data to observe
     * @return the attached listener, or null if disabled
     */
    public static MasterDebugListener attach(PlotData plotData) {
        if (!ENABLED || plotData == null) {
            return null;
        }
        MasterDebugListener dbg = new MasterDebugListener(plotData);
        dbg.hookPlotData();
        return dbg;
    }

    private MasterDebugListener(PlotData plotData) {
        this.plotData = plotData;

        for (CurveChangeType t : CurveChangeType.values()) {
            totalCurveByType.put(t, 0L);
        }

        if (SUMMARY_SECONDS > 0) {
            scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "SPlot-DebugSummary");
                t.setDaemon(true);
                return t;
            });
        } else {
            scheduler = null;
        }
    }

    /** Stop the periodic summary printer (if enabled). */
    public void shutdown() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    private void hookPlotData() {
        plotData.addDataChangeListener(this);

        for (ACurve c : plotData.getCurves()) {
            hookCurve(c);
        }

        logInfo("ATTACHED " + DebugUtil.plotDebugString(plotData));

        if (scheduler != null) {
            scheduler.scheduleAtFixedRate(this::printSummarySafe, SUMMARY_SECONDS, SUMMARY_SECONDS, TimeUnit.SECONDS);
        }
    }

    private void hookCurve(ACurve curve) {
        if (curve == null || hookedCurves.containsKey(curve)) {
            return;
        }
        curve.addCurveChangeListener(this);
        hookedCurves.put(curve, Boolean.TRUE);

        // init per-curve counters
        EnumMap<CurveChangeType, Long> m = new EnumMap<>(CurveChangeType.class);
        for (CurveChangeType t : CurveChangeType.values()) {
            m.put(t, 0L);
        }
        perCurveCounts.put(curve, m);

        logInfo("HOOK " + DebugUtil.curveDebugString(curve));
    }

    // -----------------------------
    // Listener callbacks
    // -----------------------------

    @Override
    public void curveChanged(ACurve curve, CurveChangeType type) {
        if (!ENABLED) return;

        // Ensure hook coverage even if curve appeared via “silent” model ops
        hookCurve(curve);

        // Update counters
        inc(totalCurveByType, type);
        curveEventsSinceLastSummary++;

        EnumMap<CurveChangeType, Long> m = perCurveCounts.get(curve);
        if (m != null) {
            inc(m, type);
        }

        if (PRINT_CURVE_EVENTS) {
            logCurve(type, DebugUtil.curveDebugString(curve) + threadInfo());
        }

        if (STACK_ON != null && STACK_ON == type) {
            printStack("STACK (curve " + type + ") " + DebugUtil.curveDebugString(curve));
        }
    }

    @Override
    public void dataSetChanged(PlotData pd) {
        if (!ENABLED) return;

        totalPlotEvents++;
        plotEventsSinceLastSummary++;

        if (PRINT_PLOT_EVENTS) {
            logPlot("PLOTDATA changed " + DebugUtil.plotDebugString(pd) + threadInfo());
        }

        // Safety net: hook any new curves added to the PlotData
        for (ACurve c : pd.getCurves()) {
            hookCurve(c);
        }
    }

    // -----------------------------
    // Summary + rate monitoring
    // -----------------------------

    private void printSummarySafe() {
        try {
            printSummary();
        } catch (Throwable t) {
            // Never let the debug helper crash the app
            logWarn("SUMMARY ERROR: " + t);
        }
    }

    private void printSummary() {
        long now = System.nanoTime();
        double dt = (now - lastSummaryNanos) / 1e9;
        if (dt <= 0) dt = 1e-9;

        double curveRate = curveEventsSinceLastSummary / dt;
        double plotRate = plotEventsSinceLastSummary / dt;

        String header = "SUMMARY " + DebugUtil.plotDebugString(plotData)
                + " dt=" + DebugUtil.fmt(dt) + "s"
                + " curveRate=" + DebugUtil.fmt(curveRate) + "/s"
                + " plotRate=" + DebugUtil.fmt(plotRate) + "/s";

        if (curveRate >= STORM_RATE || plotRate >= STORM_RATE) {
            logStorm(header);
        } else {
            logInfo(header);
        }

        // Totals by curve event type
        String byType = "  totals curveEvents=" + sum(totalCurveByType)
                + " [DATA=" + totalCurveByType.get(CurveChangeType.DATA)
                + " STYLE=" + totalCurveByType.get(CurveChangeType.STYLE)
                + " FIT=" + totalCurveByType.get(CurveChangeType.FIT)
                + "] plotEvents=" + totalPlotEvents;
        logInfo(byType);

        // Top “noisiest” curve in the last window (approx: use totals; good enough)
        ACurve top = null;
        long topTotal = -1;
        for (Map.Entry<ACurve, EnumMap<CurveChangeType, Long>> e : perCurveCounts.entrySet()) {
            long s = sum(e.getValue());
            if (s > topTotal) {
                topTotal = s;
                top = e.getKey();
            }
        }
        if (top != null) {
            logInfo("  noisiest (total so far): " + DebugUtil.curveDebugString(top)
                    + " events=" + topTotal);
        }

        // Reset window counters
        curveEventsSinceLastSummary = 0;
        plotEventsSinceLastSummary = 0;
        lastSummaryNanos = now;
    }

    // -----------------------------
    // Logging helpers (colors, thread)
    // -----------------------------

    private static String threadInfo() {
        boolean edt = SwingUtilities.isEventDispatchThread();
        return " thread=" + Thread.currentThread().getName() + (edt ? " (EDT)" : " (!EDT)");
    }

    private static void logInfo(String msg) {
        log(color(C.Info), msg);
    }

    private static void logWarn(String msg) {
        log(color(C.Warn), msg);
    }

    private static void logStorm(String msg) {
        log(color(C.Storm), msg);
    }

    private static void logCurve(CurveChangeType type, String msg) {
        switch (type) {
            case DATA: log(color(C.Data), "CURVE DATA " + msg); break;
            case STYLE: log(color(C.Style), "CURVE STYLE " + msg); break;
            case FIT: log(color(C.Fit), "CURVE FIT " + msg); break;
            default: log(color(C.Info), "CURVE " + type + " " + msg); break;
        }
    }

    private static void logPlot(String msg) {
        log(color(C.Plot), msg);
    }

    private static void log(String prefix, String msg) {
        System.out.println(TS.format(new Date()) + " " + prefix + msg + (COLORS ? C.Reset : ""));
    }

    private static void printStack(String header) {
        logWarn(header);
        StackTraceElement[] st = Thread.currentThread().getStackTrace();
        // Skip getStackTrace + printStack + caller frames
        for (int i = 3; i < Math.min(st.length, 40); i++) {
            System.out.println("    at " + st[i]);
        }
    }

    // -----------------------------
    // Counter helpers
    // -----------------------------

    private static void inc(EnumMap<CurveChangeType, Long> map, CurveChangeType t) {
        if (t == null) return;
        map.put(t, map.getOrDefault(t, 0L) + 1L);
    }

    private static long sum(EnumMap<CurveChangeType, Long> map) {
        long s = 0;
        for (Long v : map.values()) {
            if (v != null) s += v;
        }
        return s;
    }

    // -----------------------------
    // Config parsing
    // -----------------------------

    private static boolean getBool(String key, boolean def) {
        String v = System.getProperty(key);
        return (v == null) ? def : Boolean.parseBoolean(v);
    }

    private static int getInt(String key, int def) {
        String v = System.getProperty(key);
        if (v == null) return def;
        try { return Integer.parseInt(v.trim()); } catch (Exception e) { return def; }
    }

    private static double getDouble(String key, double def) {
        String v = System.getProperty(key);
        if (v == null) return def;
        try { return Double.parseDouble(v.trim()); } catch (Exception e) { return def; }
    }

    private static <E extends Enum<E>> E getEnum(String key, Class<E> enumType, E def) {
        String v = System.getProperty(key);
        if (v == null) return def;
        try { return Enum.valueOf(enumType, v.trim()); } catch (Exception e) { return def; }
    }

    // -----------------------------
    // ANSI colors (optional)
    // -----------------------------

    private enum C {
        Reset("\u001B[0m"),
        Info("\u001B[0m"),
        Plot("\u001B[36m"),   // cyan
        Data("\u001B[32m"),   // green
        Style("\u001B[33m"),  // yellow
        Fit("\u001B[35m"),    // magenta
        Warn("\u001B[31m"),   // red
        Storm("\u001B[1;31m");// bold red

        final String code;
        C(String code) { this.code = code; }
    }

    private static String color(C c) {
        if (!COLORS) return "";
        return c.code;
    }
}

package cnuphys.splot.debug;

import java.text.DecimalFormat;

import cnuphys.splot.fit.CurveDrawingMethod;
import cnuphys.splot.pdata.ACurve;
import cnuphys.splot.pdata.PlotData;

/**
 * Small debugging helpers for splot development.
 */
public final class DebugUtil {

    private static final DecimalFormat DF = new DecimalFormat("0.###");

    private DebugUtil() {}

    public static String id(Object o) {
        return Integer.toHexString(System.identityHashCode(o));
    }

    public static String plotDebugString(PlotData pd) {
        if (pd == null) return "PlotData<null>";
        return "PlotData@" + id(pd) + " type=" + safeType(pd) + " curves=" + pd.size();
    }

    public static String curveDebugString(ACurve c) {
        if (c == null) return "ACurve<null>";
        CurveDrawingMethod m = null;
        try { m = c.getCurveDrawingMethod(); } catch (Throwable t) { /* ignore */ }

        // Keep this robust: do not assume a curve is a Curve/HistoCurve etc.
        String name = safeName(c);
        int len = safeLen(c);
        boolean vis = safeVisible(c);

        return "Curve@" + id(c)
                + " name='" + name + "'"
                + " len=" + len
                + " method=" + (m == null ? "?" : m)
                + " visible=" + vis;
    }

    private static String safeName(ACurve c) {
        try { return c.getName(); } catch (Throwable t) { return "?"; }
    }

    private static int safeLen(ACurve c) {
        try { return c.length(); } catch (Throwable t) { return -1; }
    }

    private static boolean safeVisible(ACurve c) {
        try { return c.isVisible(); } catch (Throwable t) { return true; }
    }

    private static String safeType(PlotData pd) {
        try { return String.valueOf(pd.getType()); } catch (Throwable t) { return "?"; }
    }

    public static String fmt(double v) {
        return DF.format(v);
    }
}

package cnuphys.splot.pdata;

/**
 * Types of changes that can occur to a curve.
 * <p>
 * This is intentionally coarse-grained. Views can treat any change as a repaint,
 * while more sophisticated views may choose to optimize (e.g. only redraw a fit overlay).
 * </p>
 *
 * @author heddle
 */
public enum CurveChangeType {
    /** Underlying numeric data changed (points appended/removed, bins filled, cleared, etc.). */
    DATA,

    /** Non-data properties changed (visibility, style, drawing method, knobs, etc.). */
    STYLE,

    /** Fit-related artifacts changed (fit result updated/cleared). */
    FIT
}

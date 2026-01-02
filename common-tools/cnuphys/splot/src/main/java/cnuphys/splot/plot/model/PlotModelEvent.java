package cnuphys.splot.plot.model;

import cnuphys.splot.pdata.ACurve;

/**
 * Model change event. Kept small and "view-friendly".
 */
public final class PlotModelEvent {

	public enum Kind {
		CURVES_CHANGED,   // curves added/removed/reordered
		CURVE_DATA,       // data changed for a specific curve
		CURVE_STYLE,      // style changed for a specific curve
		CURVE_FIT         // fit changed for a specific curve
	}

	public final IPlotModel source;
	public final Kind kind;
	public final ACurve curve; // may be null for CURVES_CHANGED

	public PlotModelEvent(IPlotModel source, Kind kind, ACurve curve) {
		this.source = source;
		this.kind = kind;
		this.curve = curve;
	}
}

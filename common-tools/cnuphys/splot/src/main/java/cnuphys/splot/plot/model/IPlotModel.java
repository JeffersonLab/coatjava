package cnuphys.splot.plot.model;

import java.awt.geom.Rectangle2D;
import java.util.List;

import cnuphys.splot.pdata.ACurve;

/**
 * Curve-centric plot model: a plot is just a collection of curves (including HistoCurve).
 * The view/rendering layer must NOT assume any "dataset type".
 */
public interface IPlotModel {

	/**
	 * Get the current curves (ordering matters for draw order).
	 *
	 * @return list of curves (never null)
	 */
	List<ACurve> curves();

	/**
	 * Suggested world bounds from the model, if any.
	 * If null, the view will autoscale from curve data.
	 *
	 * @return suggested world bounds or null
	 */
	Rectangle2D getSuggestedWorld();

	/** Add a listener for model changes. */
	void addPlotModelListener(IPlotModelListener l);

	/** Remove a listener for model changes. */
	void removePlotModelListener(IPlotModelListener l);
}

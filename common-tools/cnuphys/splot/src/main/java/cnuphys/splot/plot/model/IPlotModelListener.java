package cnuphys.splot.plot.model;

import java.util.EventListener;

/**
 * Listener for plot model changes.
 */
public interface IPlotModelListener extends EventListener {

	/**
	 * Called when the model changed in a way that requires repaint and possibly rescale.
	 *
	 * @param evt details about the change
	 */
	void plotModelChanged(PlotModelEvent evt);
}

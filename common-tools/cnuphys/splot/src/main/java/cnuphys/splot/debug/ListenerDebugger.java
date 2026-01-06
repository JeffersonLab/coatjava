package cnuphys.splot.debug;

import cnuphys.splot.pdata.ACurve;
import cnuphys.splot.pdata.CurveChangeListener;
import cnuphys.splot.pdata.CurveChangeType;
import cnuphys.splot.pdata.DataChangeListener;
import cnuphys.splot.pdata.PlotData;

public class ListenerDebugger implements DataChangeListener {
	
	/** Enable or disable the listener debugger */
	public static boolean ENABLED = false;
	
	/** Singleton instance */
	public static ListenerDebugger INSTANCE;
	
	/** Singleton instance */
	private ListenerDebugger() {
	}
	
	/** Get the singleton instance
	 * @return the singleton instance
	 */
	public static ListenerDebugger getInstance() {
		if (INSTANCE == null) {
			synchronized (ListenerDebugger.class) {
				if (INSTANCE == null) {
					return new ListenerDebugger();
				}
			}
		}
		return INSTANCE;
	}
	
	
	/**
	 * Attach to plot data to listen for its changes.
	 * @param plotData the plot data
	 */
	public void attachPlotData(PlotData plotData) {
		if (ENABLED && (plotData != null)) {
			plotData.addDataChangeListener(this);
			System.out.println("ListenerDebugger attached to PlotData of type " +
			plotData.getType());
		}
	}

	@Override
	public void dataSetChanged(PlotData plotData, ACurve curve, CurveChangeType type) {
		System.out.println("ListenerDebugger: Curve on PlotData of type " +
		plotData.getType() + " changed. Curve: " + curve.name() +
		" Change type: " + type);
	}
	
	/**
	 * Enable or disable the listener debugger.
	 * @param enabled true to enable, false to disable
	 */
	public static void setEnabled(boolean enabled) {
		ENABLED = enabled;
	}
}

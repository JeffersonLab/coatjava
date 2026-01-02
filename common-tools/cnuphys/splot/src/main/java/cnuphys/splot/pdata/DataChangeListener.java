package cnuphys.splot.pdata;

import java.util.EventListener;

public interface DataChangeListener extends EventListener {

	/**
	 * A data set changed
	 * 
	 * @param plotData the dataSet that changed
	 */
	public void dataSetChanged(PlotData plotData);
}

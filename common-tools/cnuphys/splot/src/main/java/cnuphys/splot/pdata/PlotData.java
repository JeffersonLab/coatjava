package cnuphys.splot.pdata;

import java.util.ArrayList;

import javax.swing.event.EventListenerList;

/**
 * Plot data consisting of multiple curves and  histograms
 * 
 * @author heddle
 *
 */

public class PlotData {
	// the curves
	private ArrayList<Curve> curves = new ArrayList<>();
	
	// the histograms
	private ArrayList<HistoData> histograms = new ArrayList<>();
	

	/** Dataset change listeners. */
	private final EventListenerList listenerList = new EventListenerList();

	/** Dataset type. */
	private final DataSetType type;

	
	public PlotData(HistoData... histos) throws DataSetException {
		if (histos == null || histos.length < 1) {
			throw new DataSetException("Must supply at least one histogram data object.");
		}
		type = DataSetType.H1D;
		//TODO: implement
	}
	
	/**
	 * Create plot data with specified data set type and column names
	 * 
	 * @param type the data set type
	 * @param colNames the column names
	 * @throws DataSetException if there is a problem creating the data set
	 */
	public PlotData(DataSetType type, String... colNames) throws DataSetException  {
		if (type == null) {
			throw new DataSetException("DataSetType is null.");
		}
		this.type = type;

		final int colCount = (colNames == null) ? 0 : colNames.length;

		switch (type) {

		case XYXY:
			if ((colCount % 2) != 0) {
				throw new DataSetException("The number of columns " + colCount + " is not divisible by 2.");
			}
			int curveCount = colCount / 2;
			for (int i = 0; i < curveCount; i++) {
				int j = i * 2;
				DataColumn xData = new DataColumn(colNames[j]);
				DataColumn yData = new DataColumn(colNames[j + 1]);
				//use y column for curve name
				String name = yData.name();
				Curve curve = new Curve(name, xData,  yData, null);
				curves.add(curve);
			}
			break;

		case XYEXYE:
			if ((colCount % 3) != 0) {
				throw new DataSetException("The number of columns for type XYEXYE " + colCount + " is not divisible by 3.");
			}
			curveCount = colCount / 3;
			for (int i = 0; i < curveCount; i++) {
				int j = i * 3;
				DataColumn xData = new DataColumn(colNames[j]);
				DataColumn yData = new DataColumn(colNames[j + 1]);
				DataColumn eData = new DataColumn(colNames[j + 2]);
				//use y column for curve name
				String name = yData.name();
				Curve curve = new Curve(name, xData,  yData, eData);
				curves.add(curve);
			}
			break;

		case H1D:
			throw new DataSetException("Use DataSet(HistoData...) constructor for 1D histograms.");

		case STRIP:
			// handled by strip ctor
			throw new DataSetException("Use DataSet(StripData, ...) constructor for STRIP data.");
		}		
	}

	/**
	 * Get the data set type
	 * 
	 * @return the data set type
	 */
	public DataSetType getType() {
		return type;
	}
	
	/**
	 * Get the curves
	 * 
	 * @return the curves
	 */
	public ArrayList<Curve> getCurves() {
		return curves;
	}
	
	/**
	 * Get the histograms
	 * 
	 * @return the histograms
	 */
	public ArrayList<HistoData> getHistograms() {
		return histograms;
	}

}

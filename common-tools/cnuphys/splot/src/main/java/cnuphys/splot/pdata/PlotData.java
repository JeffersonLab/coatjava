package cnuphys.splot.pdata;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import javax.swing.event.EventListenerList;

import cnuphys.splot.debug.ListenerDebugger;

/**
 * A lightweight, UI-agnostic container for the data backing a plot.
 * <p>
 * {@code PlotData} owns one or more {@link ACurve} instances (including histogram-backed curves such as
 * {@link HistoCurve}, and ordinary XY curves such as {@link Curve}). Views typically register a single
 * {@link DataChangeListener} on the {@code PlotData} and repaint when notified.
 * </p>
 *
 * <h3>Notifications</h3>
 * There are two kinds of changes:
 * <ul>
 *   <li><b>Structural</b> changes to the model (curves added/removed/cleared) &mdash; fired by {@code PlotData}.</li>
 *   <li><b>Data</b> changes within a curve (points appended, histogram filled, etc.) &mdash; fired by the curve, and
 *       forwarded by {@code PlotData} so the view can listen in one place.</li>
 * </ul>
 *
 * <h3>Threading</h3>
 * {@code PlotData} is not synchronized. If curves are updated from a background sampler (e.g. {@link StripChartCurve}),
 * the view should use its existing snapshot/copy strategy when rendering.
 *
 * @author heddle
 */
public class PlotData implements CurveChangeListener {

	/** The curves owned by this plot-data instance. */
	private final ArrayList<ACurve> curves = new ArrayList<>();

	/** PlotData change listeners. */
	private final EventListenerList listenerList = new EventListenerList();

	/** Plot data set type. */
	private final PlotDataType type;

	/**
	 * Create plot data from one or more 1D histogram data objects. Each {@link HistoData}
	 * is wrapped in a {@link HistoCurve} and added to this model.
	 *
	 * @param histos one or more histogram data objects (must be non-null and contain no nulls)
	 * @throws PlotDataException if there is a problem creating the data set
	 */
	public PlotData(HistoData... histos) throws PlotDataException {
		if (histos == null || histos.length < 1) {
			throw new PlotDataException("Must supply at least one histogram data object.");
		}
		type = PlotDataType.H1D;

		for (int i = 0; i < histos.length; i++) {
			HistoData hd = histos[i];
			if (hd == null) {
				throw new PlotDataException("Histogram data object " + i + " is null.");
			}
			HistoCurve hc = new HistoCurve(hd.name(), hd);
			addCurve(hc);

		}
	}

	/**
	 * Create plot data for a strip chart (time series / streamed XY).
	 *
	 * @param stripData the strip chart backing data (non-null)
	 * @throws PlotDataException if the strip data does not provide a curve
	 */
	public PlotData(StripChartCurve stripData) throws PlotDataException {
		if (stripData == null) {
			throw new IllegalArgumentException("StripData object is null.");
		}
		type = PlotDataType.STRIP;
		addCurve(stripData);
	}

	/**
	 * Create plot data with specified data set type and column names.
	 *
	 * @param type     the data set type (non-null)
	 * @param colNames the column names (interpretation depends on {@code type})
	 * @throws PlotDataException if there is a problem creating the data set
	 */
	public PlotData(PlotDataType type, String... colNames) throws PlotDataException {
		if (type == null) {
			throw new PlotDataException("PlotDataType is null.");
		}
		this.type = type;

		final int colCount = (colNames == null) ? 0 : colNames.length;

		switch (type) {

		case XYXY: {
			if ((colCount % 2) != 0) {
				throw new PlotDataException("The number of columns " + colCount + " is not divisible by 2.");
			}
			int curveCount = colCount / 2;
			for (int i = 0; i < curveCount; i++) {
				int j = i * 2;
				DataColumn xData = new DataColumn(colNames[j]);
				DataColumn yData = new DataColumn(colNames[j + 1]);
				String name = yData.name();
				Curve curve = new Curve(name, xData, yData, null);
				addCurve(curve);
			}
			break;
		}

		case XYEXYE: {
			if ((colCount % 3) != 0) {
				throw new PlotDataException("The number of columns for type XYEXYE " + colCount + " is not divisible by 3.");
			}
			int curveCount = colCount / 3;
			for (int i = 0; i < curveCount; i++) {
				int j = i * 3;
				DataColumn xData = new DataColumn(colNames[j]);
				DataColumn yData = new DataColumn(colNames[j + 1]);
				DataColumn eData = new DataColumn(colNames[j + 2]);
				String name = yData.name();
				Curve curve = new Curve(name, xData, yData, eData);
				addCurve(curve);
			}
			break;
		}

		case H1D:
			throw new PlotDataException("Use PlotData(HistoData...) constructor for 1D histograms.");

		case STRIP:
			throw new PlotDataException("Use PlotData(StripData) constructor for STRIP data.");
		}
	}

	/** @return the plot data type. */
	public PlotDataType getType() {
		return type;
	}

	/** @return an unmodifiable view of the curves. */
	public List<ACurve> getCurves() {
		return Collections.unmodifiableList(curves);
	}

	/** @return curve count. */
	public int size() {
		return curves.size();
	}

	/** Convenience: get a curve by index. */
	public ACurve getCurve(int index) {
		return curves.get(index);
	}
	/**
	 * Determine if this is histogram plot data.
	 * @return true if histogram plot data
	 */
	public boolean isHistogramData() {
		return (type == PlotDataType.H1D);
	}

	/**
	 * Add a curve to this plot model.
	 *
	 * @param curve the curve to add (non-null)
	 * @return {@code true} if added
	 */
	public boolean addCurve(ACurve curve) {
		Objects.requireNonNull(curve, "curve");
		boolean added = curves.add(curve);
		if (added) {
			curve.addCurveChangeListener(this);
		}
		return added;
	}

	/**
	 * Find all curves whose {@link ACurve#getName()} matches the given name.
	 * Names are treated as labels and are not assumed unique.
	 */
	public List<ACurve> findCurvesByName(String name) {
		if (name == null) {
			return Collections.emptyList();
		}
		ArrayList<ACurve> matches = new ArrayList<>();
		for (ACurve c : curves) {
			if (c != null && name.equals(c.name())) {
				matches.add(c);
			}
		}
		return matches;
	}

	/** Curve-level notification (forwarded to {@link DataChangeListener}s). */
	@Override
	public void curveChanged(ACurve curve, CurveChangeType type) {
		notifyListeners(curve, type);
	}

	/** Notify {@link DataChangeListener}s that the plot model changed. */
	public void notifyListeners(ACurve curve, CurveChangeType type) {
		Object[] listeners = listenerList.getListenerList();
		for (int i = listeners.length - 2; i >= 0; i -= 2) {
			if (listeners[i] == DataChangeListener.class) {
				((DataChangeListener) listeners[i + 1]).dataSetChanged(this, curve, type);
			}
		}
	}

	/** Add a plot-data change listener. */
	public void addDataChangeListener(DataChangeListener listener) {
		if (listener != null) {
			listenerList.add(DataChangeListener.class, listener);
		}
	}

	/** Remove a plot-data change listener. */
	public void removeDataChangeListener(DataChangeListener listener) {
		if (listener != null) {
			listenerList.remove(DataChangeListener.class, listener);
		}
	}
	
	/** @return the minimum x value over all curves. */
	public double xMin() {
		if (curves.isEmpty()) {
			return Double.NaN;
		}
		double xmin = Double.POSITIVE_INFINITY;
		for (ACurve curve : curves) {
			double cxmin = curve.xMin();
			if (cxmin < xmin) {
				xmin = cxmin;
			}
		}
		return xmin;
	}
	
	/** @return the maximum x value over all curves. */
	public double xMax() {
		if (curves.isEmpty()) {
			return Double.NaN;
		}
		double xmax = Double.NEGATIVE_INFINITY;
		for (ACurve curve : curves) {
			double cxmax = curve.xMax();
			if (cxmax > xmax) {
				xmax = cxmax;
			}
		}
		return xmax;
	}	
	
	/** @return the minimum y value over all curves. */
	public double yMin() {
		if (curves.isEmpty()) {
			return Double.NaN;
		}
		double ymin = Double.POSITIVE_INFINITY;
		for (ACurve curve : curves) {
			double cymin = curve.yMin();
			if (cymin < ymin) {
				ymin = cymin;
			}
		}
		return ymin;
	}
	
	/** @return the maximum y value over all curves. */
	public double yMax() {
		if (curves.isEmpty()) {
			return Double.NaN;
		}
		double ymax = Double.NEGATIVE_INFINITY;
		for (ACurve curve : curves) {
			double cymax = curve.yMax();
			if (cymax > ymax) {
				ymax = cymax;
			}
		}
		return ymax;
	}
	
}

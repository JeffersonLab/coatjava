package cnuphys.splot.pdata;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import javax.swing.event.EventListenerList;

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
 * {@code PlotData} is not synchronized. If curves are updated from a background sampler (e.g. {@link StripData}),
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
			addCurveInternal(hc);
		}
	}

	/**
	 * Create plot data for a strip chart (time series / streamed XY).
	 *
	 * @param stripData the strip chart backing data (non-null)
	 * @throws PlotDataException if the strip data does not provide a curve
	 */
	public PlotData(StripData stripData) throws PlotDataException {
		if (stripData == null) {
			throw new IllegalArgumentException("StripData object is null.");
		}
		type = PlotDataType.STRIP;

		ACurve c = stripData.getCurve();
		if (c == null) {
			throw new PlotDataException("StripData.getCurve() returned null.");
		}
		addCurveInternal(c);
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
				addCurveInternal(curve);
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
				addCurveInternal(curve);
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
	 * Add a curve to this plot model.
	 *
	 * @param curve the curve to add (non-null)
	 * @return {@code true} if added
	 */
	public boolean addCurve(ACurve curve) {
		Objects.requireNonNull(curve, "curve");
		boolean added = curves.add(curve);
		if (added) {
			hookCurve(curve);
			notifyListeners();
		}
		return added;
	}

	/**
	 * Remove a curve from this plot model.
	 *
	 * @param curve the curve to remove (non-null)
	 * @return {@code true} if removed
	 */
	public boolean removeCurve(ACurve curve) {
		Objects.requireNonNull(curve, "curve");
		boolean removed = curves.remove(curve);
		if (removed) {
			unhookCurve(curve);
			notifyListeners();
		}
		return removed;
	}

	/** Remove all curves from this plot model. */
	public void clear() {
		if (!curves.isEmpty()) {
			for (ACurve c : curves) {
				unhookCurve(c);
			}
			curves.clear();
			notifyListeners();
		}
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
			if (c != null && name.equals(c.getName())) {
				matches.add(c);
			}
		}
		return matches;
	}

	/** Internal add that also hooks curve-level listeners. */
	private void addCurveInternal(ACurve curve) {
		curves.add(curve);
		hookCurve(curve);
	}

	/** Register to receive curve change events so we can forward them to plot listeners. */
	private void hookCurve(ACurve curve) {
		if (curve != null) {
			curve.addCurveChangeListener(this);
		}
	}

	/** Unregister from curve change events. */
	private void unhookCurve(ACurve curve) {
		if (curve != null) {
			curve.removeCurveChangeListener(this);
		}
	}

	/** Curve-level notification (forwarded to {@link DataChangeListener}s). */
	@Override
	public void curveChanged(ACurve curve, CurveChangeType type) {
		// For now, PlotData listeners only know "something changed".
		notifyListeners();
	}

	/** Notify {@link DataChangeListener}s that the plot model changed. */
	public void notifyListeners() {
		Object[] listeners = listenerList.getListenerList();
		for (int i = listeners.length - 2; i >= 0; i -= 2) {
			if (listeners[i] == DataChangeListener.class) {
				((DataChangeListener) listeners[i + 1]).dataSetChanged(this);
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
}

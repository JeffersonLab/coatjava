package cnuphys.splot.plot.model;

import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.event.EventListenerList;

import cnuphys.splot.pdata.ACurve;
import cnuphys.splot.pdata.CurveChangeListener;
import cnuphys.splot.pdata.CurveChangeType;

/**
 * A simple, concrete model that owns curves and listens to their changes.
 * You can use this directly or wrap your existing container.
 */
public class DefaultPlotModel implements IPlotModel, CurveChangeListener {

	private final List<ACurve> curves = new ArrayList<>();
	private Rectangle2D suggestedWorld;

	private final EventListenerList listeners = new EventListenerList();

	public DefaultPlotModel() {
	}

	public DefaultPlotModel(List<ACurve> initial) {
		if (initial != null) {
			for (ACurve c : initial) {
				addCurve(c);
			}
		}
	}

	@Override
	public List<ACurve> curves() {
		return Collections.unmodifiableList(curves);
	}

	@Override
	public Rectangle2D getSuggestedWorld() {
		return suggestedWorld;
	}

	public void setSuggestedWorld(Rectangle2D world) {
		this.suggestedWorld = world;
		fire(new PlotModelEvent(this, PlotModelEvent.Kind.CURVES_CHANGED, null));
	}

	public void addCurve(ACurve c) {
		if (c == null) {
			return;
		}
		curves.add(c);
		c.addCurveChangeListener(this);
		fire(new PlotModelEvent(this, PlotModelEvent.Kind.CURVES_CHANGED, null));
	}

	public void removeCurve(ACurve c) {
		if (c == null) {
			return;
		}
		if (curves.remove(c)) {
			c.removeCurveChangeListener(this);
			fire(new PlotModelEvent(this, PlotModelEvent.Kind.CURVES_CHANGED, null));
		}
	}

	public void clear() {
		for (ACurve c : curves) {
			c.removeCurveChangeListener(this);
		}
		curves.clear();
		fire(new PlotModelEvent(this, PlotModelEvent.Kind.CURVES_CHANGED, null));
	}

	@Override
	public void addPlotModelListener(IPlotModelListener l) {
		if (l != null) {
			listeners.add(IPlotModelListener.class, l);
		}
	}

	@Override
	public void removePlotModelListener(IPlotModelListener l) {
		if (l != null) {
			// Works once IPlotModelListener extends java.util.EventListener
			listeners.remove(IPlotModelListener.class, l);
		}
	}

	private void fire(PlotModelEvent evt) {
		Object[] ls = listeners.getListenerList();
		for (int i = ls.length - 2; i >= 0; i -= 2) {
			if (ls[i] == IPlotModelListener.class) {
				((IPlotModelListener) ls[i + 1]).plotModelChanged(evt);
			}
		}
	}

	@Override
	public void curveChanged(ACurve curve, CurveChangeType type) {
		if (type == null) {
			return;
		}
		PlotModelEvent.Kind k;
		switch (type) {
		case DATA:
			k = PlotModelEvent.Kind.CURVE_DATA;
			break;
		case STYLE:
			k = PlotModelEvent.Kind.CURVE_STYLE;
			break;
		case FIT:
			k = PlotModelEvent.Kind.CURVE_FIT;
			break;
		default:
			k = PlotModelEvent.Kind.CURVE_STYLE;
			break;
		}
		fire(new PlotModelEvent(this, k, curve));
	}
}

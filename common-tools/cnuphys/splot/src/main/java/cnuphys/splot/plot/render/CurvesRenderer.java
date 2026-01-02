package cnuphys.splot.plot.render;

import java.awt.Graphics2D;
import java.awt.Shape;
import java.util.ArrayList;
import java.util.List;

import cnuphys.splot.pdata.ACurve;
import cnuphys.splot.plot.PlotCanvas2;
import cnuphys.splot.plot.render.curve.HistoCurveRenderer;
import cnuphys.splot.plot.render.curve.ICurveRenderer;
import cnuphys.splot.plot.render.curve.XYCurveRenderer;

/**
 * Draws all curves using curve render strategies, clipped to the active plot rect.
 */
public class CurvesRenderer implements IPlotRenderer {

	private final List<ICurveRenderer> curveRenderers = new ArrayList<>();

	public CurvesRenderer() {
		// default strategies
		curveRenderers.add(new HistoCurveRenderer());
		curveRenderers.add(new XYCurveRenderer());
	}

	public CurvesRenderer add(ICurveRenderer r) {
		if (r != null) {
			curveRenderers.add(r);
		}
		return this;
	}

	@Override
	public void render(Graphics2D g2, PlotCanvas2 canvas, PlotTransform tx) {

		// Clip to active plot area so curves never draw into margins/labels/legend.
		Shape oldClip = g2.getClip();
		g2.clip(tx.active);

		try {
			for (ACurve c : canvas.getModel().curves()) {
				if (c == null || !c.isVisible() || c.length() < 1) {
					continue;
				}
				for (ICurveRenderer r : curveRenderers) {
					if (r.supports(c)) {
						r.draw(g2, canvas, tx, c);
						break;
					}
				}
			}
		} finally {
			g2.setClip(oldClip);
		}
	}
}

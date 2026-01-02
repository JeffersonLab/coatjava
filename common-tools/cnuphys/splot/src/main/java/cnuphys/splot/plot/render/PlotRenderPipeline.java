package cnuphys.splot.plot.render;

import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;

import cnuphys.splot.plot.PlotCanvas2;

/**
 * Ordered list of renderers. Simple, explicit, testable.
 */
public class PlotRenderPipeline {

	private final List<IPlotRenderer> renderers = new ArrayList<>();

	public PlotRenderPipeline add(IPlotRenderer r) {
		if (r != null) {
			renderers.add(r);
		}
		return this;
	}

	public void renderAll(Graphics2D g2, PlotCanvas2 canvas, PlotTransform tx) {
		for (IPlotRenderer r : renderers) {
			r.render(g2, canvas, tx);
		}
	}
}

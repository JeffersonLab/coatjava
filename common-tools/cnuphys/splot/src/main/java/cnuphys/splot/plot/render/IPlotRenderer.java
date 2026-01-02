package cnuphys.splot.plot.render;

import java.awt.Graphics2D;

import cnuphys.splot.plot.PlotCanvas2;

/**
 * One stage in the render pipeline (background, grid, axes, curves, legend, overlays...).
 */
public interface IPlotRenderer {
	void render(Graphics2D g2, PlotCanvas2 canvas, PlotTransform tx);
}

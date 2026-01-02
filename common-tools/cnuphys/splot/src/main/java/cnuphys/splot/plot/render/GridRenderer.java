package cnuphys.splot.plot.render;

import java.awt.Graphics2D;

import cnuphys.splot.plot.PlotCanvas2;
import cnuphys.splot.plot.PlotTicks2;

public class GridRenderer implements IPlotRenderer {

	@Override
	public void render(Graphics2D g2, PlotCanvas2 canvas, PlotTransform tx) {
		if (!canvas.getParams().drawGrid) {
			return;
		}
		PlotTicks2 ticks = canvas.getTicks();

		// vertical grid
		for (double xv : ticks.xTicks) {
			int xs = canvas.worldToScreenX(tx, xv);
			g2.drawLine(xs, tx.active.y, xs, tx.active.y + tx.active.height);
		}

		// horizontal grid
		for (double yv : ticks.yTicks) {
			int ys = canvas.worldToScreenY(tx, yv);
			g2.drawLine(tx.active.x, ys, tx.active.x + tx.active.width, ys);
		}
	}
}

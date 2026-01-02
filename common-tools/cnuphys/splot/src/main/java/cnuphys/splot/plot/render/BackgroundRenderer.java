package cnuphys.splot.plot.render;

import java.awt.Graphics2D;

import cnuphys.splot.plot.PlotCanvas2;

public class BackgroundRenderer implements IPlotRenderer {

	@Override
	public void render(Graphics2D g2, PlotCanvas2 canvas, PlotTransform tx) {
		g2.setColor(canvas.getParams().background);
		g2.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
	}
}

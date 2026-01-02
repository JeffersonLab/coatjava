package cnuphys.splot.plot.render.curve;

import java.awt.Graphics2D;
import java.awt.Polygon;

import cnuphys.splot.fit.IValueGetter;
import cnuphys.splot.pdata.ACurve;
import cnuphys.splot.pdata.HistoCurve;
import cnuphys.splot.pdata.HistoData;
import cnuphys.splot.style.Styled;
import cnuphys.splot.plot.PlotCanvas2;
import cnuphys.splot.plot.render.PlotTransform;

/**
 * Histogram curve renderer for {@link HistoCurve}.
 */
public class HistoCurveRenderer implements ICurveRenderer {

	@Override
	public boolean supports(ACurve c) {
		return c instanceof HistoCurve;
	}

	@Override
	public void draw(Graphics2D g2, PlotCanvas2 canvas, PlotTransform tx, ACurve c) {
		HistoCurve hc = (HistoCurve) c;
		HistoData hd = hc.getHistoData();
		Styled style = hc.getStyle();
		if (hd == null || style == null || hd.getNumberBins() < 1) {
			return;
		}

		Polygon poly = buildFillPolygon(canvas, tx, hd);

		// fill
		try {
			g2.setColor(style.getFillColor());
			g2.fillPolygon(poly);
		} catch (Exception e) {
			// ignore
		}

		// border
		try {
			g2.setColor(style.getBorderColor());
			g2.drawPolygon(poly);
		} catch (Exception e) {
			// ignore
		}

		// optional stat errors
		if (hd.drawStatisticalErrors()) {
			drawStatErrors(g2, canvas, tx, hd);
		}

		// optional fit overlay
		IValueGetter fit = hc.getFitValueGetter();
		if (fit != null) {
			// reuse XYCurveRenderer's sampling idea without introducing dependency
			drawFitFunction(g2, canvas, tx, fit, style);
		}
	}

	private Polygon buildFillPolygon(PlotCanvas2 canvas, PlotTransform tx, HistoData hd) {
		int n = hd.getNumberBins();
		double[] grid = hd.getGridCopy();
		long[] counts = hd.getCounts();

		// polygon: start at left edge at y=0, then go along top of bins, then down at right edge to y=0
		Polygon p = new Polygon();

		// baseline points
		int xLeft = canvas.worldToScreenX(tx, grid[0]);
		int yBase = canvas.worldToScreenY(tx, 0.0);
		p.addPoint(xLeft, yBase);

		for (int i = 0; i < n; i++) {
			double x0 = grid[i];
			double x1 = grid[i + 1];
			double y = Math.max(0.0, counts[i]);

			p.addPoint(canvas.worldToScreenX(tx, x0), canvas.worldToScreenY(tx, y));
			p.addPoint(canvas.worldToScreenX(tx, x1), canvas.worldToScreenY(tx, y));
		}

		int xRight = canvas.worldToScreenX(tx, grid[n]);
		p.addPoint(xRight, yBase);

		return p;
	}

	private void drawStatErrors(Graphics2D g2, PlotCanvas2 canvas, PlotTransform tx, HistoData hd) {
		int n = hd.getNumberBins();
		double[] grid = hd.getGridCopy();
		long[] counts = hd.getCounts();

		for (int i = 0; i < n; i++) {
			if (counts[i] <= 0) {
				continue;
			}
			double x = 0.5 * (grid[i] + grid[i + 1]);
			double y = counts[i];
			double err = Math.sqrt(y);

			int xs = canvas.worldToScreenX(tx, x);
			int y0 = canvas.worldToScreenY(tx, y - err);
			int y1 = canvas.worldToScreenY(tx, y + err);

			g2.drawLine(xs, y0, xs, y1);
			g2.drawLine(xs - 2, y0, xs + 2, y0);
			g2.drawLine(xs - 2, y1, xs + 2, y1);
		}
	}

	private void drawFitFunction(Graphics2D g2, PlotCanvas2 canvas, PlotTransform tx, IValueGetter f, Styled style) {
		try {
			g2.setColor(style.getFitLineColor());
		} catch (Exception e) {
			// ignore
		}

		double xmin = tx.world.getMinX();
		double xmax = tx.world.getMaxX();
		int samples = Math.max(100, tx.active.width);

		int lastX = Integer.MIN_VALUE;
		int lastY = Integer.MIN_VALUE;

		for (int i = 0; i <= samples; i++) {
			double x = xmin + (xmax - xmin) * (i / (double) samples);
			double y = f.value(x);
			int xs = canvas.worldToScreenX(tx, x);
			int ys = canvas.worldToScreenY(tx, y);

			if (i > 0) {
				g2.drawLine(lastX, lastY, xs, ys);
			}
			lastX = xs;
			lastY = ys;
		}
	}
}

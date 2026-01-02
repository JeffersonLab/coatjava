package cnuphys.splot.plot.render.curve;

import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.geom.Path2D;

import cnuphys.splot.fit.CurveDrawingMethod;
import cnuphys.splot.fit.IValueGetter;
import cnuphys.splot.pdata.ACurve;
import cnuphys.splot.pdata.Curve;
import cnuphys.splot.style.Styled;
import cnuphys.splot.plot.GraphicsUtilities;
import cnuphys.splot.plot.PlotCanvas2;
import cnuphys.splot.plot.render.PlotTransform;

/**
 * Basic XY curve drawing for {@link Curve}.
 * Uses curve drawing method: CONNECT/STAIRS/CUBICSPLINE/fits if available.
 */
public class XYCurveRenderer implements ICurveRenderer {

	@Override
	public boolean supports(ACurve c) {
		return c instanceof Curve;
	}

	@Override
	public void draw(Graphics2D g2, PlotCanvas2 canvas, PlotTransform tx, ACurve c) {
		Curve curve = (Curve) c;
		Styled style = curve.getStyle();
		if (style == null || curve.length() < 1) {
			return;
		}

		final CurveDrawingMethod method = curve.getCurveDrawingMethod();

		// set stroke/color if available
		try {
			g2.setColor(style.getFitLineColor());
		} catch (Exception e) {
			// ignore
		}
		try {
			Stroke s = GraphicsUtilities.getStroke(style.getFitLineWidth(), style.getFitLineStyle());
			if (s != null) {
				g2.setStroke(s);
			}
		} catch (Exception e) {
			// ignore
		}

		// draw "data method"
		switch (method) {
		case STAIRS:
			drawStairs(g2, canvas, tx, curve);
			break;
		case CONNECT:
		case NONE:
		default:
			drawConnect(g2, canvas, tx, curve);
			break;
		}

		// If a fit evaluator exists, draw it on top (cheap and useful).
		IValueGetter fit = curve.getFitValueGetter();
		if (fit != null) {
			drawFunction(g2, canvas, tx, fit, style);
		}
	}

	private void drawConnect(Graphics2D g2, PlotCanvas2 canvas, PlotTransform tx, Curve curve) {
		int n = curve.length();
		Path2D path = new Path2D.Double();
		for (int i = 0; i < n; i++) {
			double x = curve.xData().get(i);
			double y = curve.yData().get(i);
			int xs = canvas.worldToScreenX(tx, x);
			int ys = canvas.worldToScreenY(tx, y);
			if (i == 0) {
				path.moveTo(xs, ys);
			} else {
				path.lineTo(xs, ys);
			}
		}
		g2.draw(path);
	}

	private void drawStairs(Graphics2D g2, PlotCanvas2 canvas, PlotTransform tx, Curve curve) {
		int n = curve.length();
		if (n < 2) {
			drawConnect(g2, canvas, tx, curve);
			return;
		}
		Path2D path = new Path2D.Double();
		double x0 = curve.xData().get(0);
		double y0 = curve.yData().get(0);
		path.moveTo(canvas.worldToScreenX(tx, x0), canvas.worldToScreenY(tx, y0));

		for (int i = 1; i < n; i++) {
			double x = curve.xData().get(i);
			double y = curve.yData().get(i);
			// horizontal to new x at old y, then vertical to new y
			path.lineTo(canvas.worldToScreenX(tx, x), canvas.worldToScreenY(tx, y0));
			path.lineTo(canvas.worldToScreenX(tx, x), canvas.worldToScreenY(tx, y));
			x0 = x;
			y0 = y;
		}
		g2.draw(path);
	}

	private void drawFunction(Graphics2D g2, PlotCanvas2 canvas, PlotTransform tx, IValueGetter f, Styled style) {
		// simple sampling across visible x-range
		double xmin = tx.world.getMinX();
		double xmax = tx.world.getMaxX();
		int samples = Math.max(100, tx.active.width);

		// fit style if available
		try {
			g2.setColor(style.getFitLineColor());
		} catch (Exception e) {
			// ignore
		}

		Path2D path = new Path2D.Double();
		for (int i = 0; i <= samples; i++) {
			double x = xmin + (xmax - xmin) * (i / (double) samples);
			double y = f.value(x);
			int xs = canvas.worldToScreenX(tx, x);
			int ys = canvas.worldToScreenY(tx, y);
			if (i == 0) {
				path.moveTo(xs, ys);
			} else {
				path.lineTo(xs, ys);
			}
		}
		g2.draw(path);
	}
}

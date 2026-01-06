package cnuphys.splot.plot;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.Objects;

import cnuphys.splot.fit.CurveDrawingMethod;
import cnuphys.splot.fit.Evaluator;
import cnuphys.splot.fit.FitResult;
import cnuphys.splot.pdata.ACurve;
import cnuphys.splot.pdata.Curve;
import cnuphys.splot.pdata.HistoCurve;
import cnuphys.splot.pdata.HistoData;
import cnuphys.splot.pdata.PlotData;
import cnuphys.splot.pdata.Snapshot;
import cnuphys.splot.pdata.StripChartCurve;
import cnuphys.splot.style.IStyled;
import cnuphys.splot.style.Styled;
import cnuphys.splot.style.SymbolDraw;
import cnuphys.splot.style.SymbolType;

public class CurveDrawer {

	private static final Color _transGray = new Color(80, 80, 80, 16);
	
	protected final Object lock = new Object();

	/**
	 * Draw a curve with x and y error bars
	 * 
	 * @param g          the graphics context
	 * @param plotCanvas the plot canvas
	 * @param xcol       the x data column
	 * @param ycol       the y data column
	 * @param xerrCol    the x error bar column (often <code>null</code>)
	 * @param yerrCol    the y error bar column
	 */
	public static void drawCurve(Graphics g, PlotCanvas plotCanvas, ACurve curve) {

		Objects.requireNonNull(curve, "curve");
		Objects.requireNonNull(plotCanvas, "plotCanvas");
		
		if (!curve.isVisible()) {
			return;
		}
		
		if (curve instanceof Curve) {
			drawXYCurve(g, plotCanvas, (Curve)curve);
		}
		else if (curve instanceof StripChartCurve) {
			drawStripChart(g, plotCanvas, (StripChartCurve) curve);
		}
		else if (curve instanceof HistoCurve) {
			drawHistoCurve(g, plotCanvas, (HistoCurve) curve);
		}
		else {
			System.err.println("Unsupported curve type in drawCurve " + curve.name());
			return;
		}
		
	}



	/**
	 * Draw a standard XY(with optional errors) curve
	 * 
	 * @param g           the graphics context
	 * @param plotCanvas  the plot canvas
	 * @param curve 	the XY(E)curve to be drawn
	 */
	public static void drawXYCurve(Graphics g, PlotCanvas canvas, Curve curve) {
		if (!curve.isVisible()) {
			return;
		}
		
		if (curve.isDirty()) {
			curve.doFit(true);
		}

		Point2D.Double wp = new Point2D.Double();
		Point p0 = new Point();
		Point p1 = new Point();

		//get threadsafe copy of the data
		Snapshot snapshot = curve.snapshot();

		double x[] = snapshot.x;
		double y[] = snapshot.y;
		double ysig[] = snapshot.e; // can be null

		if ((x == null) || (x.length < 1)) {
			return;
		}
		
		//draw the fit line or basic connector lines
		drawFitOrLines(g, canvas, curve, x, y);


		// symbols?
		Styled style = curve.getStyle();

		if (style.getSymbolType() != SymbolType.NOSYMBOL) {
			for (int i = 0; i < x.length; i++) {

				//draw sigmaY error bars
				if (ysig != null) {
					double y0 = y[i] - ysig[i];
					double y1 = y[i] + ysig[i];
					wp.setLocation(x[i], y0);
					canvas.worldToLocal(p0, wp);
					wp.setLocation(x[i], y1);
					canvas.worldToLocal(p1, wp);
					g.drawLine(p0.x, p0.y, p1.x, p1.y);
				}

				wp.setLocation(x[i], y[i]);
				canvas.worldToLocal(p0, wp);
				SymbolDraw.drawSymbol(g, p0.x, p0.y, style);

			}
		}
	}

	
	/**
	 * Draw a strip chart
	 * 
	 * @param g           the graphics context
	 * @param plotCanvas  the plot canvas
	 * @param stripChartCurve the strip chart curve
	 */
	public static void drawStripChart(Graphics g, PlotCanvas canvas, StripChartCurve stripChartCurve) {
	}

	/**
	 * Draw a 1D histogram
	 * 
	 * @param g           the graphics context
	 * @param plotCanvas  the plot canvas
	 * @param histoCurve the histogram curve
	 */
	private static void drawHistoCurve(Graphics g, PlotCanvas canvas, HistoCurve histoCurve) {

		HistoData hd = histoCurve.getHistoData();

		Polygon poly = HistoData.GetPolygon(canvas, hd);
		IStyled style = histoCurve.getStyle();

		g.setColor(style.getFillColor());
		g.fillPolygon(poly);

		Color borderColor = style.getBorderColor();
		if (borderColor == null) {
			borderColor = _transGray;
		}
		else {
			// borderColor = new Color(borderColor.getRed(), borderColor.getGreen(),
			// borderColor.getBlue(), 16);
		}
		g.setColor(borderColor);
		g.drawPolygon(poly);

		long counts[] = hd.getCounts();
		int numBin = hd.getNumberBins();
		double x[] = new double[numBin];
		double y[] = new double[numBin];
		double err[] = new double[numBin];
		for (int bin = 0; bin < numBin; bin++) {
			x[bin] = hd.getBinMidValue(bin);
			y[bin] = counts[bin];
			err[bin] = Math.sqrt(y[bin]);
		}
		
		// draw the fit line
		drawFitOrLines(g, canvas, histoCurve, x, y);

		// draw statistical errors
		if (hd.drawStatisticalErrors()) {
			Point p0 = new Point();
			Point p1 = new Point();
			Point.Double wp = new Point.Double();

			for (int bin = 0; bin < numBin; bin++) {
				if (hd.getCount(bin) > 0) {
					double ymin = y[bin] - err[bin];
					double ymax = y[bin] + err[bin];
					wp.setLocation(x[bin], ymin);
					canvas.worldToLocal(p0, wp);
					wp.setLocation(x[bin], ymax);
					canvas.worldToLocal(p1, wp);
					g.drawLine(p0.x, p0.y, p1.x, p1.y);
					g.drawLine(p0.x - 2, p0.y, p0.x + 2, p0.y);
					g.drawLine(p1.x - 2, p1.y, p1.x + 2, p1.y);
				}
			}

		}

		// draw the bin borders
		Rectangle b = canvas.getActiveBounds();
		int n = poly.npoints;
		g.setColor(borderColor);
		for (int i = 0; i < n; i++) {
			g.drawLine(poly.xpoints[i], poly.ypoints[i], poly.xpoints[i], b.y + b.height);
		}

	}

	
	/**
	 * Draw the fit or basic no-fit connections for the given curve
	 * 
	 * @param g          the graphics context
	 * 
	 * @param plotCanvas the plot canvas
	 * 
	 * @param curve      the curve
	 */
	private static void drawFitOrLines(Graphics g, PlotCanvas canvas, ACurve curve, double x[], double y[]) {
		
	        
		IStyled style = curve.getStyle();
		Point2D.Double wp = new Point2D.Double();
		Point p0 = new Point();
		Point p1 = new Point();

		Graphics2D g2 = (Graphics2D) g;

		Stroke oldStroke = g2.getStroke();
		g2.setStroke(GraphicsUtilities.getStroke(style.getFitLineWidth(), style.getFitLineStyle()));

		Color fitColor = style.getFitLineColor();
		if (fitColor == null) {
			return;
		}
		g2.setColor(fitColor);
		CurveDrawingMethod drawMethod = curve.getCurveDrawingMethod();
		
		switch (drawMethod) {
		case NONE:
			break;

		case CONNECT:  // simple connections
			wp.setLocation(x[0], y[0]);
			canvas.worldToLocal(p0, wp);

			for (int i = 1; i < x.length; i++) {
				wp.setLocation(x[i], y[i]);
				canvas.worldToLocal(p1, wp);
				g2.drawLine(p0.x, p0.y, p1.x, p1.y);
				p0.setLocation(p1);
			}
			break;

		case STAIRS:
			wp.setLocation(x[0], y[0]);
			canvas.worldToLocal(p0, wp);

			Rectangle rr = canvas.getActiveBounds();
			int bottom = rr.y + rr.height;

			for (int i = 1; i < x.length; i++) {
				wp.setLocation(x[i], y[i]);
				canvas.worldToLocal(p1, wp);

				g2.setColor(style.getFillColor());
				g.fillRect(p0.x, p0.y, p1.x - p0.x, bottom - p0.y);

				g2.setColor(style.getFitLineColor());
				g2.drawLine(p0.x, p0.y, p1.x, p0.y);
				g2.drawLine(p1.x, p0.y, p1.x, p1.y);
				p0.setLocation(p1);
			}
			
			break;

		case CUBICSPLINE:
			Evaluator ivg = curve.getCubicSpline();
			if (ivg == null) {
				System.err.println("Cubic spline fit is null for curve " + curve.name());
				return;
			}
			drawEvaluator(g2, canvas, ivg);
			break;

			//all the true fits
		default:
			FitResult fr = curve.fitResult();
			if (fr == null) {
				if (curve.isDirty()) {
					System.err.println("Curve is dirty in drawFitOrLines for curve " + curve.name());
				}
				//no fit result
				System.err.println("No fit result for curve " + curve.name());
				return;
			}
			
			//this is the evaluator for the fit
			ivg = curve.getFitValueGetter();
			if (ivg == null) {
				System.err.println("Fit evaluator is null in CurveDrawer");
				return;
			}
			drawEvaluator(g2, canvas, ivg);
			break;
		}
		
		
		g2.setStroke(oldStroke);
	}

	// draw a value getter
	private static void drawEvaluator(Graphics2D g, PlotCanvas plotCanvas, Evaluator ivg) {

		Objects.requireNonNull(plotCanvas, "plotCanvas");
		Objects.requireNonNull(ivg, "evaluator");
	
		// the plot screen rectangle
		Rectangle rect = plotCanvas.getActiveBounds();
		int iy = rect.y;
		Path2D poly = null;

		Point pp = new Point();
		Point.Double wp = new Point.Double();

		PlotData plotData = plotCanvas.getPlotData();
		double ymid = 0.5 * (plotData.yMin() + plotData.yMax());

		wp.setLocation(plotData.xMin(), ymid);
		plotCanvas.worldToLocal(pp, wp);
		int xsmin = pp.x;
		wp.setLocation(plotData.xMax(), ymid);
		plotCanvas.worldToLocal(pp, wp);
		int xsmax = pp.x;

		xsmin = rect.x;
		xsmax = rect.x + rect.width;

		for (int ix = xsmin; ix <= xsmax + rect.width; ix++) {
			pp.setLocation(ix, iy);
			plotCanvas.localToWorld(pp, wp);
			wp.y = ivg.value(wp.x);
			plotCanvas.worldToLocal(pp, wp);

			if (poly == null) {
				poly = new Path2D.Float();
				poly.moveTo(pp.x, pp.y);

			}
			else {
				poly.lineTo(pp.x, pp.y);
			}
		}	
		
		Graphics2D g2 = (Graphics2D) g;
        Object oldAA = g2.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

		g.draw(poly);
				//restore
		if (oldAA != null) {
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA);
		}
	}

}

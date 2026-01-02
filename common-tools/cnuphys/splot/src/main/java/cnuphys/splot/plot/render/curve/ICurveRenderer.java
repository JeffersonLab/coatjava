package cnuphys.splot.plot.render.curve;

import java.awt.Graphics2D;

import cnuphys.splot.pdata.ACurve;
import cnuphys.splot.plot.PlotCanvas2;
import cnuphys.splot.plot.render.PlotTransform;

/**
 * Strategy for drawing a curve type (XY curve, histogram curve, strip chart, etc.).
 */
public interface ICurveRenderer {
	boolean supports(ACurve c);
	void draw(Graphics2D g2, PlotCanvas2 canvas, PlotTransform tx, ACurve c);
}

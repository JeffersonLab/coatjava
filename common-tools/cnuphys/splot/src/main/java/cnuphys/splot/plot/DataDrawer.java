package cnuphys.splot.plot;

import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.Shape;
import java.util.Collection;
import java.util.Vector;

import cnuphys.splot.pdata.ACurve;
import cnuphys.splot.pdata.PlotData;

public class DataDrawer {

	// the owner canvas
	private PlotCanvas _plotCanvas;

	/**
	 * Create a DataDrawer
	 * 
	 * @param plotCanvas the owner canvas
	 */
	public DataDrawer(PlotCanvas plotCanvas) {
		_plotCanvas = plotCanvas;
	}

	/**
	 * Draw a data set on the canvas.Draws the optional fixed lines, then the
	 * curves.
	 * 
	 * @param g  the graphics context
	 * @param plotData the PlotData to draw.
	 */
	public void draw(Graphics g, PlotData plotData) {

		//if no curves, bail
		if ((plotData == null) || plotData.size() < 1) {
			return;
		}

		//clip checks
		if (!(g.getClip().intersects(_plotCanvas.getActiveBounds()))) {
			return;
		}

		Rectangle clipRect = GraphicsUtilities.minClip(g.getClip(), _plotCanvas.getActiveBounds());
		if ((clipRect == null) || (clipRect.width == 0) || (clipRect.height == 0)) {
			return;
		}

		// save the clip, set clip to active area
		Shape oldClip = g.getClip();

		g.setClip(clipRect);

		// any fixed  horizontal or verticallines?
		Vector<PlotLine> lines = _plotCanvas.getParameters().getPlotLines();
		if (!lines.isEmpty()) {
			for (PlotLine line : lines) {
				line.draw(g);
			}
		}
		
		Collection<ACurve> curves = plotData.getCurves();
		for (ACurve curve : curves) {
			if (curve.isVisible()) {
				CurveDrawer.drawCurve(g, _plotCanvas, curve);
			}
		}


		// restore the old clip
		g.setClip(oldClip);
	}

}

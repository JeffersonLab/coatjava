package cnuphys.splot.plot;

import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.Shape;
import java.util.Vector;

import cnuphys.splot.pdata.PlotData;
import cnuphys.splot.plot.old.PlotCanvas;

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
	 * Draw a data set on the canvas
	 * 
	 * @param g  the graphics context
	 * @param plotData the PlotData to draw.
	 */
	public void draw(Graphics g, PlotData plotData) {

		if ((plotData == null) || plotData.getSize() < 1) {
			return;
		}

		if (!(g.getClip().intersects(_plotCanvas.getActiveBounds()))) {
//			System.err.println("CLIP SKIP");
			return;
		}

		Rectangle clipRect = GraphicsUtilities.minClip(g.getClip(), _plotCanvas.getActiveBounds());
		if ((clipRect == null) || (clipRect.width == 0) || (clipRect.height == 0)) {
			return;
		}

		// save the clip, set clip to active area
		Shape oldClip = g.getClip();

		g.setClip(clipRect);

		// any fixed lines?
		Vector<PlotLine> lines = _plotCanvas.getParameters().getPlotLines();
		if (!lines.isEmpty()) {
			for (PlotLine line : lines) {
				line.draw(g);
			}
		}

		switch (plotData.getType()) {
		case XYEXYE:
			for (int i = 0; i < plotData.getColumnCount() / 3; i++) {
				int j = 3 * i;
				CurveDrawer.drawCurve(g, _plotCanvas, plotData.getColumn(j), plotData.getColumn(j + 1), null, plotData.getColumn(j + 2));
			}
			break;

		case H1D:
			for (int i = 0; i < plotData.getColumnCount(); i++) {
				CurveDrawer.drawHisto1D(g, _plotCanvas, plotData.getColumn(i));
			}
			break;


		case XYXY:
			for (int i = 0; i < plotData.getColumnCount() / 2; i++) {
				int j = 2 * i;
				CurveDrawer.drawCurve(g, _plotCanvas, plotData.getColumn(j), plotData.getColumn(j + 1));
			}
			break;

		case STRIP:
			CurveDrawer.drawCurve(g, _plotCanvas, plotData.getColumn(0), plotData.getColumn(1));
			break;


		}

		// restore the old clip
		g.setClip(oldClip);
	}

}

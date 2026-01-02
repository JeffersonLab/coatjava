package cnuphys.splot.plot.render;

import java.awt.BasicStroke;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.awt.geom.Point2D;

import cnuphys.splot.plot.PlotCanvas2;
import cnuphys.splot.plot.interact.PlotInteractionState;

/**
 * Overlay renderer: crosshair + mouse coordinate readout + rubberband rectangle + hover highlight.
 */
public class OverlayRenderer implements IPlotRenderer {

	@Override
	public void render(Graphics2D g2, PlotCanvas2 canvas, PlotTransform tx) {

		PlotInteractionState s = canvas.getInteractionState();
		if (s == null) {
			return;
		}

		Rectangle a = tx.active;

		Stroke oldStroke = g2.getStroke();
		g2.setStroke(new BasicStroke(1f));

		// Crosshair + coords
		if (s.mouseInside && s.showCrosshair) {
			Point m = s.mouse;
			if (a.contains(m)) {
				g2.drawLine(a.x, m.y, a.x + a.width, m.y);
				g2.drawLine(m.x, a.y, m.x, a.y + a.height);

				if (s.showCoords) {
					Point2D w = canvas.screenToWorld(tx, m.x, m.y);
					String msg = "x=" + canvas.formatTick(w.getX(), canvas.getTicks().xSpacing)
							+ "  y=" + canvas.formatTick(w.getY(), canvas.getTicks().ySpacing);
					drawLabelBox(g2, canvas, a, m.x + 10, m.y - 10, msg);
				}
			}
		}

		// Hover highlight (nearest data point/bin)
		if (s.hover != null && s.hover.curve != null && s.hover.label != null) {
			Point hp = s.hover.screen;
			if (a.contains(hp)) {
				// marker
				int r = 4;
				g2.fillOval(hp.x - r, hp.y - r, 2 * r, 2 * r);

				// label near marker
				drawLabelBox(g2, canvas, a, hp.x + 10, hp.y + 14, s.hover.label);
			}
		}

		// Rubberband
		if (s.rubberbanding && s.rubberband.width > 0 && s.rubberband.height > 0) {
			Rectangle rb = new Rectangle(s.rubberband);
			Rectangle clipped = rb.intersection(a);
			if (clipped.width > 0 && clipped.height > 0) {
				float[] dash = { 4f, 4f };
				g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, dash, 0f));
				g2.drawRect(clipped.x, clipped.y, clipped.width, clipped.height);
			}
		}

		g2.setStroke(oldStroke);
	}

	private void drawLabelBox(Graphics2D g2, PlotCanvas2 canvas, Rectangle a, int x, int y, String msg) {
		g2.setFont(canvas.getParams().tickFont);
		FontMetrics fm = g2.getFontMetrics();

		int pad = 4;
		int tw = fm.stringWidth(msg);
		int th = fm.getHeight();

		int bx = Math.min(a.x + a.width - tw - 2 * pad, x);
		int by = Math.max(a.y + th, y);

		g2.drawRect(bx, by - th, tw + 2 * pad, th);
		g2.drawString(msg, bx + pad, by - fm.getDescent());
	}
}

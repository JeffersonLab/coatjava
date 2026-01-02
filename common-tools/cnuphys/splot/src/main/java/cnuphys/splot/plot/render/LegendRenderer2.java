package cnuphys.splot.plot.render;

import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;

import cnuphys.splot.pdata.ACurve;
import cnuphys.splot.plot.PlotCanvas2;
import cnuphys.splot.style.Styled;

/**
 * Minimal legend: curve name + sample line.
 * (Drag/drop, richer stats, and histogram RMS/sigma text can be added later.)
 */
public class LegendRenderer2 implements IPlotRenderer {

	@Override
	public void render(Graphics2D g2, PlotCanvas2 canvas, PlotTransform tx) {
		if (!canvas.getParams().drawLegend) {
			return;
		}

		int pad = 6;
		int lineH = 14;

		int x = tx.active.x + tx.active.width - 170;
		int y = tx.active.y + 10;

		int w = 160;
		int h = pad * 2 + lineH * Math.max(1, canvas.getModel().curves().size());

		Rectangle box = new Rectangle(x, y, w, h);

		if (canvas.getParams().legendBorder) {
			g2.drawRect(box.x, box.y, box.width, box.height);
		}

		g2.setFont(canvas.getParams().tickFont);
		FontMetrics fm = g2.getFontMetrics();

		int yy = y + pad + fm.getAscent();

		for (ACurve c : canvas.getModel().curves()) {
			if (c == null || !c.isVisible()) {
				continue;
			}

			Styled s = c.getStyle();
			int sx0 = x + pad;
			int sx1 = sx0 + canvas.getParams().legendSampleLength;
			int sy = yy - fm.getAscent() / 2;

			if (s != null) {
				try {
					g2.setColor(s.getFitLineColor());
				} catch (Exception e) {
					// ignore
				}
			}
			g2.drawLine(sx0, sy, sx1, sy);

			g2.setColor(java.awt.Color.black);
			g2.drawString(c.getName(), sx1 + 8, yy);

			yy += lineH;
			if (yy > y + h - pad) {
				break;
			}
		}
	}
}

package cnuphys.splot.plot.render;

import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;

import cnuphys.splot.fit.CurveDrawingMethod;
import cnuphys.splot.pdata.ACurve;
import cnuphys.splot.pdata.Curve;
import cnuphys.splot.pdata.HistoCurve;
import cnuphys.splot.pdata.HistoData;
import cnuphys.splot.plot.PlotCanvas2;
import cnuphys.splot.plot.interact.PlotInteractionState;
import cnuphys.splot.style.Styled;

/**
 * Richer legend that remains decoupled and supports hit-testing:
 * - draws a sample line
 * - curve name
 * - histogram stats (via HistoData.statStr())
 * - simple method/fit hint for XY curves
 * - records per-entry bounds in PlotInteractionState for click-to-toggle visibility
 */
public class LegendRenderer3 implements IPlotRenderer {

	@Override
	public void render(Graphics2D g2, PlotCanvas2 canvas, PlotTransform tx) {
		if (!canvas.getParams().drawLegend) {
			// still clear bounds so clicks don't toggle stale entries
			canvas.getInteractionState().legendEntries.clear();
			return;
		}

		PlotInteractionState state = canvas.getInteractionState();
		state.legendEntries.clear();

		int pad = 6;
		int lineH = 16;

		int x = tx.active.x + tx.active.width - 320;
		int y = tx.active.y + 10;

		int w = 310;

		// estimate height based on visible curves
		int visible = 0;
		for (ACurve c : canvas.getModel().curves()) {
			if (c != null && c.isVisible()) {
				visible++;
			}
		}
		int h = pad * 2 + lineH * Math.max(1, visible);

		Rectangle box = new Rectangle(x, y, w, h);

		if (canvas.getParams().legendBorder) {
			g2.drawRect(box.x, box.y, box.width, box.height);
		}

		g2.setFont(canvas.getParams().tickFont);
		FontMetrics fm = g2.getFontMetrics();

		int yy = y + pad + fm.getAscent();

		for (ACurve c : canvas.getModel().curves()) {
			if (c == null) {
				continue;
			}

			Styled s = c.getStyle();
			int sx0 = x + pad;
			int sx1 = sx0 + canvas.getParams().legendSampleLength;
			int sy = yy - fm.getAscent() / 2;

			// build legend text
			String text = c.getName();
			if (!c.isVisible()) {
				text = "(off) " + text;
			}

			if (c instanceof HistoCurve) {
				HistoData hd = ((HistoCurve) c).getHistoData();
				if (hd != null) {
					text += "   " + hd.statStr();
				}
			}
			else if (c instanceof Curve) {
				CurveDrawingMethod m = c.getCurveDrawingMethod();
				if (m != null && m != CurveDrawingMethod.NONE) {
					text += "   [" + m.name().toLowerCase() + "]";
				}
				if (((Curve) c).getFitValueGetter() != null) {
					text += " +fit";
				}
			}

			// sample line
			if (s != null) {
				try {
					g2.setColor(s.getFitLineColor());
				} catch (Exception e) {
					// ignore
				}
			}
			g2.drawLine(sx0, sy, sx1, sy);

			g2.setColor(java.awt.Color.black);
			g2.drawString(text, sx1 + 8, yy);

			// record clickable bounds for this row
			PlotInteractionState.LegendEntry entry = new PlotInteractionState.LegendEntry();
			entry.curve = c;
			entry.label = text;

			int rowH = fm.getHeight() + 2;
			entry.bounds.setBounds(x + 1, yy - fm.getAscent() - 1, w - 2, rowH);
			state.legendEntries.add(entry);

			yy += lineH;
			if (yy > y + h - pad) {
				break;
			}
		}
	}
}

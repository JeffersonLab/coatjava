package cnuphys.splot.plot.render;

import java.awt.FontMetrics;
import java.awt.Graphics2D;

import cnuphys.splot.plot.GraphicsUtilities;
import cnuphys.splot.plot.PlotCanvas2;
import cnuphys.splot.plot.PlotParameters2;

public class AxesAndTicksRenderer implements IPlotRenderer {

	@Override
	public void render(Graphics2D g2, PlotCanvas2 canvas, PlotTransform tx) {
		PlotParameters2 p = canvas.getParams();

		// active border
		g2.drawRect(tx.active.x, tx.active.y, tx.active.width, tx.active.height);

		// title
		g2.setFont(p.titleFont);
		FontMetrics fmTitle = g2.getFontMetrics();
		int tw = fmTitle.stringWidth(p.title);
		g2.drawString(p.title, Math.max(5, (canvas.getWidth() - tw) / 2), Math.max(15, p.topMargin - 5));

		// axis labels
		g2.setFont(p.labelFont);
		FontMetrics fmLabel = g2.getFontMetrics();

		// X label (centered under active rect)
		String xlab = (p.xLabel == null) ? "" : p.xLabel;
		int xw = fmLabel.stringWidth(xlab);
		int xLabelX = tx.active.x + (tx.active.width - xw) / 2;
		int xLabelY = canvas.getHeight() - 10;
		g2.drawString(xlab, xLabelX, xLabelY);

		// Y label (vertical, centered beside active rect)
		String ylab = (p.yLabel == null) ? "" : p.yLabel;
		if (!ylab.isEmpty()) {

			// We rotate -90 degrees about an anchor point (xo, yo).
			// For rotated fonts, the drawString baseline is at (0,0) after transform,
			// so we position the baseline so the text is visually centered.
			int textW = fmLabel.stringWidth(ylab); // "width" in unrotated coords -> becomes height visually
			int textH = fmLabel.getHeight();

			// Choose an anchor point slightly left of the active rect.
			// xo,yo is where baseline would be if unrotated.
			int pad = 8;
			int xo = Math.max(6, tx.active.x - pad); // keep inside component
			int yo = tx.active.y + tx.active.height / 2;

			// After -90° rotation, advancing +x in text corresponds to moving down screen.
			// To center vertically, offset baseline upward by half the unrotated string width.
			int delX = -textW / 2;

			// To place the rotated glyphs just left of the plot area, we offset in +y (unrotated),
			// which becomes a left/right shift after rotation. Use ascent to avoid clipping.
			int delY = fmLabel.getAscent() / 2;

			GraphicsUtilities.drawRotatedText(g2, ylab, p.labelFont, xo, yo, delX, delY, -90.0);
		}

		// ticks
		g2.setFont(p.tickFont);
		FontMetrics fm = g2.getFontMetrics();

		double xSpacing = canvas.getTicks().xSpacing;
		double ySpacing = canvas.getTicks().ySpacing;

		// x ticks
		for (double xv : canvas.getTicks().xTicks) {
			int xs = canvas.worldToScreenX(tx, xv);
			g2.drawLine(xs, tx.active.y + tx.active.height, xs, tx.active.y + tx.active.height + 4);

			String s = canvas.formatTick(xv, xSpacing);
			int sw = fm.stringWidth(s);
			g2.drawString(s, xs - sw / 2, tx.active.y + tx.active.height + fm.getAscent() + 6);
		}

		// y ticks
		for (double yv : canvas.getTicks().yTicks) {
			int ys = canvas.worldToScreenY(tx, yv);
			g2.drawLine(tx.active.x - 4, ys, tx.active.x, ys);

			String s = canvas.formatTick(yv, ySpacing);
			int sw = fm.stringWidth(s);
			g2.drawString(s, tx.active.x - sw - 6, ys + fm.getAscent() / 2 - 1);
		}
	}
}

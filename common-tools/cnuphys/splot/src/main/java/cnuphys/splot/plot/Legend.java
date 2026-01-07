package cnuphys.splot.plot;

import java.awt.FontMetrics;
import java.awt.Graphics;
import java.util.Collection;
import cnuphys.splot.fit.CurveDrawingMethod;
import cnuphys.splot.pdata.ACurve;
import cnuphys.splot.pdata.HistoCurve;
import cnuphys.splot.pdata.PlotData;
import cnuphys.splot.style.Styled;
import cnuphys.splot.style.SymbolDraw;
import cnuphys.splot.style.SymbolType;

@SuppressWarnings("serial")
public class Legend extends DraggableRectangle {

	// the owner plot panel
	private PlotCanvas _canvas;

	// the plot parameters
	private PlotParameters _params;

	// gap before text
	private final int HGAP = 8;

	// extra v gap
	private final int VGAP = 6;

	private int _maxStringWidth;
	private int _extra;

	private int _numVisCurves;

	/**
	 * Create a Legend rectangle
	 * 
	 * @param canvas the parent plot canvas
	 */
	public Legend(PlotCanvas canvas) {
		_canvas = canvas;
		_params = canvas.getParameters();
	}

	/**
	 * Draw the legend
	 * 
	 * @param g the graphics context
	 */
	public void draw(Graphics g) {
		// System.err.println(toString());
		PlotData plotData = _canvas.getPlotData();

		if (plotData == null) {
			return;
		}

		// are there any visible curves?
		if (plotData.getVisibleCurves().isEmpty()) {
			return;
		}

		_numVisCurves = plotData.getVisibleCurves().size();

		width = getLegendWidth();
		height = getLegendHeight();

		g.setColor(_params.getTextBackground());
		g.fillRect(x, y, width, height);

		if (_params.isLegendBorder()) {
			g.setColor(_params.getTextBorderColor());
			g.drawRect(x, y, width, height);
		}

		int yi = y + VGAP;

		Collection<ACurve> curves = plotData.getVisibleCurves();
		for (ACurve curve : curves) {
			if (curve.isVisible()) {
				yi = drawColumnLegendInfo(g, yi, curve);
			}
		}
	}

	// draw the info for the given curve
	private int drawColumnLegendInfo(Graphics g, int y, ACurve curve) {
		FontMetrics fm = _canvas.getFontMetrics(_params.getTextFont());
		g.setFont(_params.getTextFont());
		g.setColor(_params.getTextForeground());

		Styled style = curve.getStyle();
		CurveDrawingMethod cdm = curve.getCurveDrawingMethod();
		int space = verticalSpaceNeeded(curve);
		int yc = y + space / 2;

		String legStr = curve.name();
		if (legStr == null) {
			legStr = "";
		}

		if (curve.isHistogram()) {
			HistoCurve hc = (HistoCurve) curve;
			legStr += (" " + hc.getHistoData().statStr());
		}

		g.drawString(legStr, x + width - _maxStringWidth - HGAP, yc + fm.getHeight() / 2);

		if ((_numVisCurves > 0) && cdm != CurveDrawingMethod.NONE) {
			GraphicsUtilities.drawStyleLine(g, style.getLineColor(), style.getLineWidth(),
					style.getLineStyle(), x + HGAP, yc, x + HGAP + _params.getLegendLineLength(), yc);
		}

		// draw symbol if any but only for xy curves
		if (curve.isXYCurve()) {
			SymbolDraw.drawSymbol(g, x + HGAP + _extra / 2, yc, curve.getStyle());
		}

		return y + space + VGAP;
	}

	// get required width of the legend
	private int getLegendWidth() {
		FontMetrics fm = _canvas.getFontMetrics(_params.getTextFont());
		PlotData plotData = _canvas.getPlotData();

		_maxStringWidth = 0;
		_extra = 0;

		Collection<ACurve> curves = plotData.getVisibleCurves();
		for (ACurve curve : curves) {
			if (curve.isVisible()) {

				CurveDrawingMethod cdm = curve.getCurveDrawingMethod();
				if ((_numVisCurves > 0) && cdm != CurveDrawingMethod.NONE) {
					_extra = _params.getLegendLineLength();
				} else {
					Styled style = curve.getStyle();
					if (style.getSymbolType() != SymbolType.NOSYMBOL) {
						_extra = Math.max(_extra, style.getSymbolSize());
					}
				}

				String legStr = curve.name();
				if (curve.isHistogram()) {
					HistoCurve hc = (HistoCurve) curve;
					legStr += (" " + hc.getHistoData().statStr());
				}

				int sw = fm.stringWidth(legStr);

				_maxStringWidth = Math.max(_maxStringWidth, sw);
			}

		}

		return _extra + _maxStringWidth + 3 * HGAP;
	}

	// get required height of the legend based
	// on the currently visible curves
	private int getLegendHeight() {
		PlotData plotData = _canvas.getPlotData();

		int height = VGAP;

		Collection<ACurve> curves = plotData.getVisibleCurves();
		for (ACurve curve : curves) {
			if (curve.isVisible()) {
				height += (VGAP + verticalSpaceNeeded(curve));
			}
		}

		return height;
	}

	// get the vertical space needed for a curve based on symbol
	// size and font height
	private int verticalSpaceNeeded(ACurve curve) {
		FontMetrics fm = _canvas.getFontMetrics(_params.getTextFont());
		return Math.max(fm.getHeight(), curve.getStyle().getSymbolSize());
	}

}

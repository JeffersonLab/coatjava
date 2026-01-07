package cnuphys.splot.style;

import java.awt.BasicStroke;
import java.awt.Color;

import cnuphys.splot.plot.GraphicsUtilities;

public interface IStyled {

	/**
	 * Get the color used for fill the interior area.
	 * 
	 * @return the fill color.
	 */
	public Color getFillColor();

	/**
	 * Set the color used for fill the interior area.
	 * 
	 * @param fillColor the fill color.
	 */
	public void setFillColor(Color fillColor);

	/**
	 * Get the color used for symbol borders.
	 * 
	 * @return the symbol border color.
	 */
	public Color getBorderColor();

	/**
	 * Set the color used for symbol borders.
	 * 
	 * @param borderColor the border color.
	 */
	public void setBorderColor(Color borderColor);

	/**
	 * Get the color used for fits.
	 * 
	 * @return the fit line color.
	 */

	public Color getLineColor();

	/**
	 * Get the color used for auxiliary lines.
	 * 
	 * @return the auxiliary line color.
	 */

	public Color getAuxLineColor();

	/**
	 * Set the color used for the fit drawing.
	 * 
	 * @param fitColor the fit color.
	 */
	public void setLineColor(Color fitColor);

	/**
	 * Set the color used for auxiliary lines.
	 * 
	 * @param auxColor the auxiliary line color.
	 */
	public void setAuxLineColor(Color auxColor);

	/**
	 * Get the style used for drawing fits.
	 * 
	 * @return the line style for fits.
	 */
	public LineStyle getLineStyle();

	/**
	 * Get the style used for drawing fits.
	 * 
	 * @return the line style for fits.
	 */
	public LineStyle getAuxLineStyle();

	/**
	 * Set the style used for drawing fits.
	 * 
	 * @param lineStyle the fit line style.
	 */
	public void setLineStyle(LineStyle lineStyle);

	/**
	 * Set the style used for drawing auxiliary lines.
	 * 
	 * @param lineStyle the auxiliary line style.
	 */
	public void setAuxLineStyle(LineStyle lineStyle);

	/**
	 * Get the symbol used for drawing points.
	 * 
	 * @return the symbol used for drawing points.
	 */
	public SymbolType getSymbolType();

	/**
	 * Set the symbol used for drawing points.
	 * 
	 * @param symbolType the symbol used for drawing points.
	 */
	public void setSymbolType(SymbolType symbolType);

	/**
	 * Get the line width for drawing fits.
	 * 
	 * @return the fit line width in pixels.
	 */
	public float getLineWidth();

	/**
	 * Get the line width for drawing auxiliary lines.
	 * 
	 * @return the auxiliary line width in pixels.
	 */
	public float getAuxLineWidth();

	/**
	 * Set the line width for drawing fit lines.
	 * 
	 * @param lineWidth the line width in pixels.
	 */
	public void setLineWidth(float lineWidth);

	/**
	 * Set the line width for drawing auxiliary lines.
	 * 
	 * @param lineWidth the auxiliary line width in pixels.
	 */
	public void setAuxLineWidth(float lineWidth);

	/**
	 * Get the symbol size (full width) in pixels.
	 * 
	 * @return the symbol size (full width) in pixels.
	 */
	public int getSymbolSize();

	/**
	 * Set symbol size (full width) in pixels.
	 * 
	 * @param symbolSize symbol size (full width) in pixels.
	 */
	public void setSymbolSize(int symbolSize);

	/**
	 * Get the stroke used for drawing fits.
	 * 
	 * @return the stroke used for drawing fits.
	 */
	public default BasicStroke getStroke() {
		return (BasicStroke) GraphicsUtilities.getStroke(getLineWidth(), getLineStyle());
	}
}

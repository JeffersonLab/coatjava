package cnuphys.splot.plot;

import java.awt.Color;
import java.awt.Font;
import java.util.Vector;

/**
 * A centralized, mutable set of visual and behavioral parameters used by a
 * {@link PlotCanvas}.
 * <p>
 * <b>Fluent API:</b> All mutator methods (methods that change internal state)
 * return {@code this} so calls can be chained, e.g.
 *
 * <pre>
 * params.setPlotTitle("My Plot")
 *       .setXLabel("t (s)")
 *       .setYLabel("V (m/s)")
 *       .mustIncludeYZero(true)
 *       .setLegendDrawing(true);
 * </pre>
 */
public class PlotParameters {

	// for legend
	private Font _textFont = Environment.getInstance().getCommonFont(10);
	private Color _textFillColor = new Color(248, 248, 248, 224);
	private Color _textTextColor = Color.black;
	private Color _textBorderColor = Color.black;

	// for extra text
	private Font _extraFont = Environment.getInstance().getCommonFont(10);
	private Color _extraFillColor = new Color(248, 248, 248, 224);
	private Color _extraTextColor = Color.black;
	private Color _extraBorderColor = Color.black;

	// force include zero on plots?
	private boolean _includeYzero = false;
	private boolean _includeXzero = false;

	// how axis limits are determined
	private LimitsMethod _xLimitsMethod = LimitsMethod.ALGORITHMICLIMITS;
	private LimitsMethod _yLimitsMethod = LimitsMethod.ALGORITHMICLIMITS;

	// legend related
	private int _legendLineLength = 70;
	private boolean _legendBorder = true;
	private boolean _drawLegend = true;

	// extra text
	private boolean _extraBorder = true;
	private boolean _drawExtra = true;
	private String[] _extraStrings;


	private Font _titleFont = Environment.getInstance().getCommonFont(20);
	private Font _axesLabelFont = Environment.getInstance().getCommonFont(14);
	private String _plotTitle = "No Plot";
	private String _xLabel = "X Data";
	private String _yLabel = "Y Data";
	private Font _statusFont = Environment.getInstance().getCommonFont(10);

	// annotating lines
	private final Vector<PlotLine> _lines = new Vector<>();

	// the canvas
	private final PlotCanvas _canvas;

	// for tick values
	private int _numDecimalX = 2;
	private int _minExponentX = 2;
	private int _numDecimalY = 2;
	private int _minExponentY = 2;

	// if we set ranges manually
	private double _manualXmin = Double.NaN;
	private double _manualXmax = Double.NaN;

	private double _manualYmin = Double.NaN;
	private double _manualYmax = Double.NaN;

	/**
	 * Create plot parameters for a canvas.
	 *
	 * @param canvas the owning canvas (not {@code null})
	 */
	public PlotParameters(PlotCanvas canvas) {
		_canvas = canvas;
	}

	/**
	 * Force the plot to include {@code x = 0} when determining x-axis limits.
	 *
	 * @param incZero the flag
	 * @return this parameters instance (for chaining)
	 */
	public PlotParameters mustIncludeXZero(boolean incZero) {
		_includeXzero = incZero;
		return this;
	}

	/**
	 * Force the plot to include {@code y = 0} when determining y-axis limits.
	 *
	 * @param incZero the flag
	 * @return this parameters instance (for chaining)
	 */
	public PlotParameters mustIncludeYZero(boolean incZero) {
		_includeYzero = incZero;
		return this;
	}

	/**
	 * Get the extra strings for a second legend-like display.
	 *
	 * @return the extra strings (may be {@code null})
	 */
	public String[] getExtraStrings() {
		return _extraStrings;
	}

	/**
	 * Set the extra strings for a second legend-like display.
	 *
	 * @param extraStrings the new extra strings array (may be {@code null})
	 * @return this parameters instance (for chaining)
	 */
	public PlotParameters setExtraStrings(String... extraStrings) {
		_extraStrings = extraStrings;
		return this;
	}

	/**
	 * Check whether the extra text border is drawn.
	 *
	 * @return {@code true} if the extra text border is drawn
	 */
	public boolean isExtraBorder() {
		return _extraBorder;
	}

	/**
	 * Set whether the extra text border is drawn.
	 *
	 * @param extraBorder {@code true} if the extra text border is drawn
	 * @return this parameters instance (for chaining)
	 */
	public PlotParameters setExtraBorder(boolean extraBorder) {
		_extraBorder = extraBorder;
		return this;
	}

	/**
	 * Check whether the legend border is drawn.
	 *
	 * @return {@code true} if the legend border is drawn
	 */
	public boolean isLegendBorder() {
		return _legendBorder;
	}

	/**
	 * Set whether the legend border is drawn.
	 *
	 * @param legBorder {@code true} if the legend border is drawn
	 * @return this parameters instance (for chaining)
	 */
	public PlotParameters setLegendBorder(boolean legBorder) {
		_legendBorder = legBorder;
		return this;
	}

	/**
	 * Set the legend fill/background color.
	 *
	 * @param color the legend fill color
	 * @return this parameters instance (for chaining)
	 */
	public PlotParameters setTextBackground(Color color) {
		_textFillColor = color;
		return this;
	}

	/**
	 * Get the legend fill/background color.
	 *
	 * @return the legend fill color
	 */
	public Color getTextBackground() {
		return _textFillColor;
	}

	/**
	 * Set the legend text color.
	 *
	 * @param color the legend text color
	 * @return this parameters instance (for chaining)
	 */
	public PlotParameters setTextForeground(Color color) {
		_textTextColor = color;
		return this;
	}

	/**
	 * Get the legend text color.
	 *
	 * @return the legend text color
	 */
	public Color getTextForeground() {
		return _textTextColor;
	}

	/**
	 * Get the legend border color.
	 *
	 * @return the legend border color
	 */
	public Color getTextBorderColor() {
		return _textBorderColor;
	}

	/**
	 * Set the legend border color.
	 *
	 * @param color the legend border color
	 * @return this parameters instance (for chaining)
	 */
	public PlotParameters setTextBorderColor(Color color) {
		_textBorderColor = color;
		return this;
	}

	/**
	 * Set the legend font.
	 *
	 * @param font the legend font
	 * @return this parameters instance (for chaining)
	 */
	public PlotParameters setTextFont(Font font) {
		_textFont = font;
		return this;
	}

	/**
	 * Get the legend font.
	 *
	 * @return the legend font
	 */
	public Font getTextFont() {
		return _textFont;
	}

	/**
	 * Set the extra text fill/background color.
	 *
	 * @param color the extra text fill color
	 * @return this parameters instance (for chaining)
	 */
	public PlotParameters setExtraBackground(Color color) {
		_extraFillColor = color;
		return this;
	}

	/**
	 * Get the extra text fill/background color.
	 *
	 * @return the extra text fill color
	 */
	public Color getExtraBackground() {
		return _extraFillColor;
	}

	/**
	 * Set the extra text color.
	 *
	 * @param color the extra text color
	 * @return this parameters instance (for chaining)
	 */
	public PlotParameters setExtraForeground(Color color) {
		_extraTextColor = color;
		return this;
	}

	/**
	 * Get the extra text color.
	 *
	 * @return the extra text color
	 */
	public Color getExtraForeground() {
		return _extraTextColor;
	}

	/**
	 * Get the extra text border color.
	 *
	 * @return the extra text border color
	 */
	public Color getExtraBorderColor() {
		return _extraBorderColor;
	}

	/**
	 * Set the extra text border color.
	 *
	 * @param color the extra text border color
	 * @return this parameters instance (for chaining)
	 */
	public PlotParameters setExtraBorderColor(Color color) {
		_extraBorderColor = color;
		return this;
	}

	/**
	 * Set the extra text font.
	 *
	 * @param font the new extra text font
	 * @return this parameters instance (for chaining)
	 */
	public PlotParameters setExtraFont(Font font) {
		_extraFont = font;
		return this;
	}

	/**
	 * Get the extra text font.
	 *
	 * @return the extra text font
	 */
	public Font getExtraFont() {
		return _extraFont;
	}

	/**
	 * Set the title font.
	 *
	 * @param font the new title font
	 * @return this parameters instance (for chaining)
	 */
	public PlotParameters setTitleFont(Font font) {
		_canvas.remoteFirePropertyChange(PlotCanvas.TITLEFONTCHANGE, _titleFont, font);
		_titleFont = font;
		return this;
	}

	/**
	 * Get the title font.
	 *
	 * @return the title font
	 */
	public Font getTitleFont() {
		return _titleFont;
	}

	/**
	 * Set the axes label font.
	 *
	 * @param font the new axes font
	 * @return this parameters instance (for chaining)
	 */
	public PlotParameters setAxesFont(Font font) {
		_canvas.remoteFirePropertyChange(PlotCanvas.AXESFONTCHANGE, _axesLabelFont, font);
		_axesLabelFont = font;
		return this;
	}

	/**
	 * Get the axes label font.
	 *
	 * @return the axes font
	 */
	public Font getAxesFont() {
		return _axesLabelFont;
	}

	/**
	 * Set the status font.
	 *
	 * @param font the new status font
	 * @return this parameters instance (for chaining)
	 */
	public PlotParameters setStatusFont(Font font) {
		_canvas.remoteFirePropertyChange(PlotCanvas.STATUSFONTCHANGE, _statusFont, font);
		_statusFont = font;
		return this;
	}

	/**
	 * Get the status font.
	 *
	 * @return the status font
	 */
	public Font getStatusFont() {
		return _statusFont;
	}

	/**
	 * Set the legend line length.
	 *
	 * @param legLineLen the line length in pixels
	 * @return this parameters instance (for chaining)
	 */
	public PlotParameters setLegendLineLength(int legLineLen) {
		_legendLineLength = legLineLen;
		return this;
	}

	/**
	 * Get the legend line length.
	 *
	 * @return the line length in pixels
	 */
	public int getLegendLineLength() {
		return _legendLineLength;
	}

	/**
	 * Check whether to include {@code x = 0}.
	 *
	 * @return {@code true} if we should include {@code x = 0}
	 */
	public boolean includeXZero() {
		return _includeXzero;
	}

	/**
	 * Check whether to include {@code y = 0}.
	 *
	 * @return {@code true} if we should include {@code y = 0}
	 */
	public boolean includeYZero() {
		return _includeYzero;
	}

	/**
	 * Add a plot line (annotation) to the plot. If the line is already present,
	 * it is moved to the end (top) of the internal list.
	 *
	 * @param line the line to add
	 * @return this parameters instance (for chaining)
	 */
	public PlotParameters addPlotLine(PlotLine line) {
		_lines.remove(line);
		_lines.add(line);
		return this;
	}

	/**
	 * Remove a plot line (annotation) from the plot.
	 *
	 * @param line the line to remove
	 * @return this parameters instance (for chaining)
	 */
	public PlotParameters removePlotLine(PlotLine line) {
		_lines.remove(line);
		return this;
	}

	/**
	 * Get all plot lines (annotations).
	 *
	 * @return all plot lines (live collection)
	 */
	public Vector<PlotLine> getPlotLines() {
		return _lines;
	}

	/**
	 * Get the plot title.
	 *
	 * @return the plot title
	 */
	public String getPlotTitle() {
		return _plotTitle;
	}

	/**
	 * Set the plot title and notify listeners via the owning {@link PlotCanvas}.
	 *
	 * @param title the plot title
	 * @return this parameters instance (for chaining)
	 */
	public PlotParameters setPlotTitle(String title) {
		_canvas.remoteFirePropertyChange(PlotCanvas.TITLETEXTCHANGE, getPlotTitle(), title);
		_plotTitle = title;
		return this;
	}

	/**
	 * Get the label for the x axis.
	 *
	 * @return the x axis label
	 */
	public String getXLabel() {
		return _xLabel;
	}

	/**
	 * Get the label for the y axis.
	 *
	 * @return the y axis label
	 */
	public String getYLabel() {
		return _yLabel;
	}

	/**
	 * Set the x axis label and notify listeners via the owning {@link PlotCanvas}.
	 *
	 * @param label the plot x axis label
	 * @return this parameters instance (for chaining)
	 */
	public PlotParameters setXLabel(String label) {
		_canvas.remoteFirePropertyChange(PlotCanvas.XLABELTEXTCHANGE, getXLabel(), label);
		_xLabel = label;
		return this;
	}

	/**
	 * Set the y axis label and notify listeners via the owning {@link PlotCanvas}.
	 *
	 * @param label the plot y axis label
	 * @return this parameters instance (for chaining)
	 */
	public PlotParameters setYLabel(String label) {
		_canvas.remoteFirePropertyChange(PlotCanvas.YLABELTEXTCHANGE, getYLabel(), label);
		_yLabel = label;
		return this;
	}

	/**
	 * Check whether we should draw the extra text block.
	 *
	 * @return whether we should draw the extra text block
	 */
	public boolean extraDrawing() {
		return _drawExtra;
	}

	/**
	 * Set whether we should draw the extra text block.
	 *
	 * @param draw the new drawing flag
	 * @return this parameters instance (for chaining)
	 */
	public PlotParameters setExtraDrawing(boolean draw) {
		_drawExtra = draw;
		return this;
	}

	/**
	 * Check whether we should draw a legend.
	 *
	 * @return whether we should draw a legend
	 */
	public boolean isLegendDrawn() {
		return _drawLegend;
	}

	/**
	 * Set whether we should draw a legend.
	 *
	 * @param draw the new drawing flag
	 * @return this parameters instance (for chaining)
	 */
	public PlotParameters setLegendDrawing(boolean draw) {
		_drawLegend = draw;
		return this;
	}

	/**
	 * The number of decimals for tick values on the x axis.
	 *
	 * @return the number of decimals for tick values on the x axis
	 */
	public int getNumDecimalX() {
		return _numDecimalX;
	}

	/**
	 * Set the number of decimals for tick values on the x axis.
	 *
	 * @param numDecimalX the number of decimals to use on the x axis
	 * @return this parameters instance (for chaining)
	 */
	public PlotParameters setNumDecimalX(int numDecimalX) {
		_numDecimalX = numDecimalX;
		return this;
	}

	/**
	 * The exponent where we switch to scientific notation on the x axis.
	 *
	 * @return the exponent threshold for scientific notation on the x axis
	 */
	public int getMinExponentX() {
		return _minExponentX;
	}

	/**
	 * Set the exponent where we switch to scientific notation on the x axis.
	 *
	 * @param minExponentX the exponent threshold for scientific notation on the x axis
	 * @return this parameters instance (for chaining)
	 */
	public PlotParameters setMinExponentX(int minExponentX) {
		_minExponentX = minExponentX;
		return this;
	}

	/**
	 * The number of decimals for tick values on the y axis.
	 *
	 * @return the number of decimals for tick values on the y axis
	 */
	public int getNumDecimalY() {
		return _numDecimalY;
	}

	/**
	 * Set the number of decimals for tick values on the y axis.
	 *
	 * @param numDecimalY the number of decimals to use on the y axis
	 * @return this parameters instance (for chaining)
	 */
	public PlotParameters setNumDecimalY(int numDecimalY) {
		_numDecimalY = numDecimalY;
		return this;
	}

	/**
	 * The exponent where we switch to scientific notation on the y axis.
	 *
	 * @return the exponent threshold for scientific notation on the y axis
	 */
	public int getMinExponentY() {
		return _minExponentY;
	}

	/**
	 * Set the exponent where we switch to scientific notation on the y axis.
	 *
	 * @param minExponentY the exponent threshold for scientific notation on the y axis
	 * @return this parameters instance (for chaining)
	 */
	public PlotParameters setMinExponentY(int minExponentY) {
		_minExponentY = minExponentY;
		return this;
	}

	/**
	 * Manually set the x range.
	 * <p>
	 * This forces {@link #setXLimitsMethod(LimitsMethod)} to {@link LimitsMethod#MANUALLIMITS}
	 * and requests that the owning canvas recompute the world coordinate system.
	 *
	 * @param xmin the minimum x
	 * @param xmax the maximum x
	 * @return this parameters instance (for chaining)
	 */
	public PlotParameters setXRange(double xmin, double xmax) {
		setXLimitsMethod(LimitsMethod.MANUALLIMITS);
		_manualXmin = xmin;
		_manualXmax = xmax;
		_canvas.setWorldSystem();
		return this;
	}

	/**
	 * Manually set the y range.
	 * <p>
	 * This forces {@link #setYLimitsMethod(LimitsMethod)} to {@link LimitsMethod#MANUALLIMITS}
	 * and requests that the owning canvas recompute the world coordinate system.
	 *
	 * @param ymin the minimum y
	 * @param ymax the maximum y
	 * @return this parameters instance (for chaining)
	 */
	public PlotParameters setYRange(double ymin, double ymax) {
		setYLimitsMethod(LimitsMethod.MANUALLIMITS);
		_manualYmin = ymin;
		_manualYmax = ymax;
		_canvas.setWorldSystem();
		return this;
	}

	/**
	 * Get the minimum value for a manual X range.
	 *
	 * @return the minimum x (may be {@link Double#NaN} if not manually set)
	 */
	public double getManualXMin() {
		return _manualXmin;
	}

	/**
	 * Get the maximum value for a manual X range.
	 *
	 * @return the maximum x (may be {@link Double#NaN} if not manually set)
	 */
	public double getManualXMax() {
		return _manualXmax;
	}

	/**
	 * Get the minimum value for a manual Y range.
	 *
	 * @return the minimum y (may be {@link Double#NaN} if not manually set)
	 */
	public double getManualYMin() {
		return _manualYmin;
	}

	/**
	 * Get the maximum value for a manual Y range.
	 *
	 * @return the maximum y (may be {@link Double#NaN} if not manually set)
	 */
	public double getManualYMax() {
		return _manualYmax;
	}

	/**
	 * Get the limit method for the x axis.
	 *
	 * @return the limit method for the x axis
	 */
	public LimitsMethod getXLimitsMethod() {
		return _xLimitsMethod;
	}

	/**
	 * Set the limits method for the x axis.
	 *
	 * @param method the method
	 * @return this parameters instance (for chaining)
	 */
	public PlotParameters setXLimitsMethod(LimitsMethod method) {
		if (_xLimitsMethod != method) {
			_xLimitsMethod = method;
		}
		return this;
	}

	/**
	 * Get the limit method for the y axis.
	 *
	 * @return the limit method for the y axis
	 */
	public LimitsMethod getYLimitsMethod() {
		return _yLimitsMethod;
	}

	/**
	 * Set the limits method for the y axis.
	 *
	 * @param method the method
	 * @return this parameters instance (for chaining)
	 */
	public PlotParameters setYLimitsMethod(LimitsMethod method) {
		if (_yLimitsMethod != method) {
			_yLimitsMethod = method;
		}
		return this;
	}
}

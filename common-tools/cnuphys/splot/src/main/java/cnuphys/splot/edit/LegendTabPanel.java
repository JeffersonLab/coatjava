package cnuphys.splot.edit;

import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

import cnuphys.splot.plot.CommonBorder;
import cnuphys.splot.plot.PlotCanvas;
import cnuphys.splot.plot.PlotParameters;

/**
 * Tab for legend settings.
 */
@SuppressWarnings("serial")
public class LegendTabPanel extends JPanel {

	private final PlotParameters _params;

	private final JCheckBox _draw;
	private final JCheckBox _border;
	private final JSpinner _lineLen;

	private final FontSpecPanel _font;

	private final ColorLabel _fill;
	private final ColorLabel _text;
	private final ColorLabel _stroke;

	public LegendTabPanel(PlotCanvas canvas) {
		_params = canvas.getParameters();

		setBorder(new CommonBorder("Legend"));
		setLayout(new GridBagLayout());
		GridBagConstraints c = new GridBagConstraints();
		c.insets = new Insets(4, 6, 4, 6);
		c.anchor = GridBagConstraints.WEST;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.weightx = 1.0;

		_draw = new JCheckBox("Draw legend", _params.isLegendDrawn());
		_border = new JCheckBox("Draw legend border", _params.isLegendBorder());
		_lineLen = new JSpinner(new SpinnerNumberModel(_params.getLegendLineLength(), 10, 500, 5));

		_font = new FontSpecPanel("Legend font", _params.getTextFont());

		JPanel colors = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
		_fill = new ColorLabel((src, col) -> {}, _params.getTextBackground(), getFont(), "Fill");
		_text = new ColorLabel((src, col) -> {}, _params.getTextForeground(), getFont(), "Text");
		_stroke = new ColorLabel((src, col) -> {}, _params.getTextBorderColor(), getFont(), "Border");
		colors.add(_fill);
		colors.add(_text);
		colors.add(_stroke);

		int row = 0;

		c.gridx = 0; c.gridy = row++; c.gridwidth = 2; add(_draw, c);
		c.gridx = 0; c.gridy = row++; c.gridwidth = 2; add(_border, c);

		c.gridwidth = 1;
		c.gridx = 0; c.gridy = row; add(new javax.swing.JLabel("Legend line length"), c);
		c.gridx = 1; c.gridy = row++; add(_lineLen, c);

		c.gridx = 0; c.gridy = row++; c.gridwidth = 2; add(_font, c);
		c.gridx = 0; c.gridy = row++; c.gridwidth = 2; add(colors, c);
	}

	public void apply() {
		_params.setLegendDrawing(_draw.isSelected());
		_params.setLegendBorder(_border.isSelected());
		_params.setLegendLineLength(((Number) _lineLen.getValue()).intValue());

		_params.setTextFont(_font.getSelectedFont());
		_params.setTextBackground(_fill.getColor());
		_params.setTextForeground(_text.getColor());
		_params.setTextBorderColor(_stroke.getColor());
	}
}

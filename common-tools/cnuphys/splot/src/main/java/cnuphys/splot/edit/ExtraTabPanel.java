package cnuphys.splot.edit;

import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import cnuphys.splot.plot.CommonBorder;
import cnuphys.splot.plot.PlotCanvas;
import cnuphys.splot.plot.PlotParameters;

/**
 * Tab for the "extra text" block.
 */
@SuppressWarnings("serial")
public class ExtraTabPanel extends JPanel {

	private final PlotParameters _params;

	private final JCheckBox _draw;
	private final JCheckBox _border;

	private final FontSpecPanel _font;

	private final ColorLabel _fill;
	private final ColorLabel _text;
	private final ColorLabel _stroke;

	private final JTextArea _linesTA;

	public ExtraTabPanel(PlotCanvas canvas) {
		_params = canvas.getParameters();

		setBorder(new CommonBorder("Extra Text"));
		setLayout(new GridBagLayout());
		GridBagConstraints c = new GridBagConstraints();
		c.insets = new Insets(4, 6, 4, 6);
		c.anchor = GridBagConstraints.WEST;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.weightx = 1.0;

		_draw = new JCheckBox("Draw extra text block", _params.extraDrawing());
		_border = new JCheckBox("Draw extra border", _params.isExtraBorder());

		_font = new FontSpecPanel("Extra font", _params.getExtraFont());

		JPanel colors = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
		_fill = new ColorLabel((src, col) -> {}, _params.getExtraBackground(), getFont(), "Fill");
		_text = new ColorLabel((src, col) -> {}, _params.getExtraForeground(), getFont(), "Text");
		_stroke = new ColorLabel((src, col) -> {}, _params.getExtraBorderColor(), getFont(), "Border");
		colors.add(_fill);
		colors.add(_text);
		colors.add(_stroke);

		_linesTA = new JTextArea(6, 40);
		_linesTA.setLineWrap(true);
		_linesTA.setWrapStyleWord(true);

		String[] extra = _params.getExtraStrings();
		if (extra != null && extra.length > 0) {
			StringBuilder sb = new StringBuilder();
			for (String s : extra) {
				if (s != null) {
					sb.append(s).append("\n");
				}
			}
			_linesTA.setText(sb.toString());
		}

		int row = 0;

		c.gridx = 0; c.gridy = row++; c.gridwidth = 2; add(_draw, c);
		c.gridx = 0; c.gridy = row++; c.gridwidth = 2; add(_border, c);

		c.gridx = 0; c.gridy = row++; c.gridwidth = 2; add(_font, c);
		c.gridx = 0; c.gridy = row++; c.gridwidth = 2; add(colors, c);

		c.gridx = 0; c.gridy = row++; c.gridwidth = 2;
		add(new javax.swing.JLabel("Extra lines (one per line)"), c);

		c.gridx = 0; c.gridy = row++; c.gridwidth = 2;
		add(new JScrollPane(_linesTA), c);
	}

	public void apply() {
		_params.setExtraDrawing(_draw.isSelected());
		_params.setExtraBorder(_border.isSelected());

		_params.setExtraFont(_font.getSelectedFont());
		_params.setExtraBackground(_fill.getColor());
		_params.setExtraForeground(_text.getColor());
		_params.setExtraBorderColor(_stroke.getColor());

		String txt = _linesTA.getText();
		if (txt == null || txt.trim().isEmpty()) {
			_params.setExtraStrings((String[]) null);
		} else {
			String[] lines = txt.split("\\R");
			// trim and keep non-empty
			java.util.ArrayList<String> cleaned = new java.util.ArrayList<>();
			for (String s : lines) {
				if (s != null) {
					String t = s.trim();
					if (!t.isEmpty()) cleaned.add(t);
				}
			}
			_params.setExtraStrings(cleaned.toArray(new String[0]));
		}
	}
}

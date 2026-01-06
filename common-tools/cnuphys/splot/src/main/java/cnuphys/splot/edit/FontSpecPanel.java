package cnuphys.splot.edit;

import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

/**
 * Compact font editor: preserves font family, edits size and style (bold/italic).
 */
@SuppressWarnings("serial")
public class FontSpecPanel extends JPanel {

	private Font _base;
	private final JLabel _preview;
	private final JSpinner _size;
	private final JCheckBox _bold;
	private final JCheckBox _italic;

	public FontSpecPanel(String title, Font initial) {
		_base = (initial != null) ? initial : new JLabel().getFont();

		setLayout(new GridBagLayout());
		GridBagConstraints c = new GridBagConstraints();
		c.insets = new Insets(2, 2, 2, 2);
		c.anchor = GridBagConstraints.WEST;

		c.gridx = 0;
		c.gridy = 0;
		add(new JLabel(title), c);

		c.gridx = 1;
		_size = new JSpinner(new SpinnerNumberModel(_base.getSize(), 6, 72, 1));
		add(_size, c);

		c.gridx = 2;
		_bold = new JCheckBox("Bold", _base.isBold());
		add(_bold, c);

		c.gridx = 3;
		_italic = new JCheckBox("Italic", _base.isItalic());
		add(_italic, c);

		c.gridx = 0;
		c.gridy = 1;
		c.gridwidth = 4;
		_preview = new JLabel(previewText(_base));
		_preview.setFont(_base);
		add(_preview, c);

		_size.addChangeListener(e -> updatePreview());
		_bold.addActionListener(e -> updatePreview());
		_italic.addActionListener(e -> updatePreview());
	}

	private void updatePreview() {
		_preview.setFont(getSelectedFont());
		_preview.setText(previewText(getSelectedFont()));
	}

	private static String previewText(Font f) {
		return f.getFamily() + "  " + f.getSize() + "pt  " + styleText(f);
	}

	private static String styleText(Font f) {
		if (f.isBold() && f.isItalic()) return "Bold Italic";
		if (f.isBold()) return "Bold";
		if (f.isItalic()) return "Italic";
		return "Plain";
	}

	public Font getSelectedFont() {
		int style = Font.PLAIN;
		if (_bold.isSelected()) style |= Font.BOLD;
		if (_italic.isSelected()) style |= Font.ITALIC;

		int size = ((Number) _size.getValue()).intValue();
		return new Font(_base.getFamily(), style, size);
	}

	public void setBaseFont(Font f) {
		_base = (f != null) ? f : new JLabel().getFont();
	}
}

package cnuphys.splot.edit;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;

@SuppressWarnings("serial")
public class ColorLabel extends JComponent {

	// the current color
	private Color _currentColor;

	// the listener for color changes
	private IColorChangeListener _colorChangeListener;

	// the prompt label
	private final String _prompt;

	// the size of the color box
	private int _rectSize = 12;

	// padding around content (bigger hit target)
	private int _pad = 4;

	// cached preferred size
	private Dimension _prefSize;

	/**
	 * Create a clickable color label that opens a color chooser.
	 *
	 * @param listener the listener to notify on color changes (may be null)
	 * @param color    the initial color (may be null)
	 * @param font     the label font (may be null to use default)
	 * @param prompt   the prompt string (never null)
	 */
	public ColorLabel(IColorChangeListener listener, Color color, Font font, String prompt) {
		_colorChangeListener = listener;
		_currentColor = color;
		_prompt = (prompt == null) ? "" : prompt;

		if (font != null) {
			setFont(font);
		}

		setOpaque(false);
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		setToolTipText("Click to choose a color");

		recomputePreferredSize();

		addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				if (!isEnabled() || !SwingUtilities.isLeftMouseButton(e)) {
					return;
				}

				Color chosen = AlphaColorChooserDialog.showDialog(
						ColorLabel.this,
						_prompt,
						_currentColor,
						true  // allow alpha
				);

				if (chosen != null) {
					setColor(chosen);
				}
			}
		});
	}

	/**
	 * Set the color change listener.
	 *
	 * @param listener the color change listener (may be null)
	 */
	public void setColorListener(IColorChangeListener listener) {
		_colorChangeListener = listener;
	}

	/**
	 * Return the current color.
	 *
	 * @return the current color (may be null)
	 */
	public Color getColor() {
		return _currentColor;
	}

	/**
	 * Set the new color and notify the listener (if any).
	 *
	 * @param newColor the new color (may be null)
	 */
	public void setColor(Color newColor) {
		_currentColor = newColor;
		setBackground(_currentColor);

		if (_colorChangeListener != null) {
			_colorChangeListener.colorChanged(this, _currentColor);
		}
		repaint();
	}

	@Override
	public void setFont(Font font) {
		super.setFont(font);
		recomputePreferredSize();
		revalidate();
		repaint();
	}

	private void recomputePreferredSize() {
		Font f = getFont();
		if (f == null) {
			_prefSize = null;
			return;
		}

		FontMetrics fm = getFontMetrics(f);
		Insets ins = getInsets();

		int textW = fm.stringWidth(_prompt);
		int textH = fm.getHeight();

		int w = ins.left + _pad + _rectSize + 6 + textW + _pad + ins.right;
		int h = ins.top + Math.max(_rectSize, textH) + (_pad * 2) + ins.bottom;

		// Ensure a comfortable minimum hit target
		h = Math.max(h, 24);

		_prefSize = new Dimension(w, h);
	}

	@Override
	public Dimension getPreferredSize() {
		return (_prefSize != null) ? _prefSize : super.getPreferredSize();
	}

	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);

		Font f = getFont();
		if (f == null) {
			return;
		}

		boolean enabled = isEnabled();
		g.setFont(f);

		FontMetrics fm = getFontMetrics(f);
		Insets ins = getInsets();

		// layout
		int x0 = ins.left + _pad;
		int y0 = ins.top + _pad;

		// center the rect vertically within available height
		int availH = getHeight() - ins.top - ins.bottom - (_pad * 2);
		int rectY = y0 + Math.max(0, (availH - _rectSize) / 2);

		// draw color swatch
		if (_currentColor == null) {
			g.setColor(Color.red);
			g.drawLine(x0, rectY, x0 + _rectSize, rectY + _rectSize);
			g.drawLine(x0, rectY + _rectSize, x0 + _rectSize, rectY);
		} else {
			g.setColor(enabled ? _currentColor : Color.gray);
			g.fillRect(x0, rectY, _rectSize, _rectSize);
		}

		g.setColor(enabled ? Color.black : Color.gray);
		g.drawRect(x0, rectY, _rectSize, _rectSize);

		// draw prompt text baseline-aligned
		int textX = x0 + _rectSize + 6;
		int textY = y0 + Math.max(_rectSize, fm.getAscent());
		g.drawString(_prompt, textX, textY);
	}
}

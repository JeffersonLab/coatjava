package cnuphys.splot.edit;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Window;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.SwingUtilities;

/**
 * A compact color chooser dialog based on Swing's {@link JColorChooser} with:
 * <ul>
 *   <li>No preview panel</li>
 *   <li>An opacity (alpha) slider (0..255)</li>
 * </ul>
 *
 * <p>
 * The dialog is modal and returns either the selected {@link Color} (including alpha)
 * or {@code null} if the user cancels/closes the dialog.
 * </p>
 *
 * <p>
 * Typical use:
 * </p>
 *
 * <pre>
 * Color chosen = AlphaColorChooserDialog.showDialog(parent, "Choose color", currentColor, true);
 * if (chosen != null) {
 *     // user pressed OK
 * }
 * </pre>
 */
public final class AlphaColorChooserDialog {

	private AlphaColorChooserDialog() {
		// no instances
	}

	/**
	 * Show a modal chooser dialog.
	 *
	 * @param parent        a component used to find the owning window (may be null)
	 * @param title         dialog title (may be null)
	 * @param initialColor  initial color (null allowed; defaults to opaque black)
	 * @param allowAlpha    if true, show an opacity slider and preserve alpha; if false,
	 *                      behaves like a normal chooser and returns an opaque color
	 * @return the selected color (including alpha if enabled) or null if cancelled
	 */
	public static Color showDialog(Component parent, String title, Color initialColor, boolean allowAlpha) {

		final Color start = (initialColor != null) ? initialColor : new Color(0, 0, 0, 255);

		final JColorChooser chooser = new JColorChooser(new Color(start.getRed(), start.getGreen(), start.getBlue()));
		chooser.setPreviewPanel(new JPanel()); // remove preview

		// Transparency: 0 = opaque, 255 = fully transparent
		final JSlider transparencySlider =
				new JSlider(0, 255, 255 - start.getAlpha());
		transparencySlider.setPaintTicks(true);
		transparencySlider.setMajorTickSpacing(64);
		transparencySlider.setMinorTickSpacing(16);
		transparencySlider.setPaintLabels(true);

		final JLabel alphaValue = new JLabel(Integer.toString(transparencySlider.getValue()));

		final JPanel alphaPanel = new JPanel(new BorderLayout(8, 0));
		alphaPanel.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
		alphaPanel.add(new JLabel("Transparency"), BorderLayout.WEST);

		JPanel sliderWrap = new JPanel(new BorderLayout(8, 0));
		sliderWrap.add(transparencySlider, BorderLayout.CENTER);
		sliderWrap.add(alphaValue, BorderLayout.EAST);
		alphaPanel.add(sliderWrap, BorderLayout.CENTER);

		// Keep the alpha label in sync
		transparencySlider.addChangeListener(e -> alphaValue.setText(Integer.toString(transparencySlider.getValue())));

		// If alpha is enabled, ensure chooser color retains current alpha when OK is pressed.
		// (Chooser itself only manages RGB; we recombine with alpha on output.)

		final JButton ok = new JButton("OK");
		final JButton cancel = new JButton("Cancel");

		final JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
		buttons.add(ok);
		buttons.add(cancel);

		final JPanel content = new JPanel(new BorderLayout());
		content.add(chooser, BorderLayout.CENTER);
		if (allowAlpha) {
			content.add(alphaPanel, BorderLayout.SOUTH);
		}
		content.add(buttons, BorderLayout.PAGE_END);

		final Window owner = (parent != null) ? SwingUtilities.getWindowAncestor(parent) : null;
		final JDialog dialog = new JDialog(owner, (title != null) ? title : "Choose Color", JDialog.ModalityType.APPLICATION_MODAL);
		dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
		dialog.setContentPane(content);

		// Keyboard: Enter=OK, Esc=Cancel
		dialog.getRootPane().setDefaultButton(ok);

		final Color[] result = new Color[] { null };

		ok.addActionListener(e -> {
			Color rgb = chooser.getColor();
			if (rgb == null) {
				rgb = Color.black;
			}
			if (allowAlpha) {
				int a = transparencySlider.getValue();
				result[0] = new Color(rgb.getRed(), rgb.getGreen(), rgb.getBlue(), a);
			} else {
				result[0] = new Color(rgb.getRed(), rgb.getGreen(), rgb.getBlue(), 255);
			}
			dialog.dispose();
		});

		cancel.addActionListener(e -> {
			result[0] = null;
			dialog.dispose();
		});

		// If user closes window via the X, treat as cancel (result stays null)

		dialog.pack();
		dialog.setLocationRelativeTo(parent);
		dialog.setVisible(true);

		return result[0];
	}
}

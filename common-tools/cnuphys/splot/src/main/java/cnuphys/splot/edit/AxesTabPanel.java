package cnuphys.splot.edit;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

import cnuphys.splot.plot.CommonBorder;
import cnuphys.splot.plot.PlotCanvas;
import cnuphys.splot.plot.PlotParameters;

/**
 * Tab for axis behavior: limits, include-zero, tick formatting.
 */
@SuppressWarnings("serial")
public class AxesTabPanel extends JPanel {

	private final PlotCanvas _canvas;
	private final PlotParameters _params;

	private final AxesLimitsPanel _limitsPanel;

	private final JCheckBox _includeXZero;
	private final JCheckBox _includeYZero;

	private final JSpinner _decX;
	private final JSpinner _expX;
	private final JSpinner _decY;
	private final JSpinner _expY;

	// snapshot for deciding if world system must be recomputed
	private boolean _xZero0, _yZero0;
	private int _decX0, _expX0, _decY0, _expY0;

	public AxesTabPanel(PlotCanvas canvas) {
		_canvas = canvas;
		_params = canvas.getParameters();

		setBorder(new CommonBorder("Axes"));
		setLayout(new GridBagLayout());

		_xZero0 = _params.includeXZero();
		_yZero0 = _params.includeYZero();
		_decX0 = _params.getNumDecimalX();
		_expX0 = _params.getMinExponentX();
		_decY0 = _params.getNumDecimalY();
		_expY0 = _params.getMinExponentY();

		_limitsPanel = new AxesLimitsPanel(_canvas);

		_includeXZero = new JCheckBox("Include X=0 in auto limits", _xZero0);
		_includeYZero = new JCheckBox("Include Y=0 in auto limits", _yZero0);

		_decX = new JSpinner(new SpinnerNumberModel(_decX0, 0, 10, 1));
		_expX = new JSpinner(new SpinnerNumberModel(_expX0, 0, 12, 1));
		_decY = new JSpinner(new SpinnerNumberModel(_decY0, 0, 10, 1));
		_expY = new JSpinner(new SpinnerNumberModel(_expY0, 0, 12, 1));

		GridBagConstraints c = new GridBagConstraints();
		c.insets = new Insets(4, 6, 4, 6);
		c.gridx = 0;
		c.gridy = 0;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.weightx = 1.0;

		// Limits editor (your existing UI)
		c.gridwidth = 2;
		add(_limitsPanel, c);

		// Include zero
		c.gridy++;
		add(_includeXZero, c);
		c.gridy++;
		add(_includeYZero, c);

		// Tick formatting block
		c.gridy++;
		c.gridwidth = 1;
		add(new JLabel("X ticks: decimals"), c);
		c.gridx = 1;
		add(_decX, c);

		c.gridx = 0;
		c.gridy++;
		add(new JLabel("X ticks: sci exponent threshold"), c);
		c.gridx = 1;
		add(_expX, c);

		c.gridx = 0;
		c.gridy++;
		add(new JLabel("Y ticks: decimals"), c);
		c.gridx = 1;
		add(_decY, c);

		c.gridx = 0;
		c.gridy++;
		add(new JLabel("Y ticks: sci exponent threshold"), c);
		c.gridx = 1;
		add(_expY, c);
	}

	/**
	 * @return true if changes require recomputing the world system
	 */
	public boolean apply() {

		_limitsPanel.apply();

		_params.mustIncludeXZero(_includeXZero.isSelected());
		_params.mustIncludeYZero(_includeYZero.isSelected());

		_params.setNumDecimalX(((Number) _decX.getValue()).intValue());
		_params.setMinExponentX(((Number) _expX.getValue()).intValue());
		_params.setNumDecimalY(((Number) _decY.getValue()).intValue());
		_params.setMinExponentY(((Number) _expY.getValue()).intValue());

		// If include-zero toggles, world limits may change.
		boolean axisAffectsWorld =
				(_xZero0 != _includeXZero.isSelected()) ||
				(_yZero0 != _includeYZero.isSelected());

		// Tick formatting doesn’t require setWorldSystem(), just repaint.
		return axisAffectsWorld;
	}
}

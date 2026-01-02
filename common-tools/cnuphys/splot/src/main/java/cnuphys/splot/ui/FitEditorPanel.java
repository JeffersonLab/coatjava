package cnuphys.splot.ui;

import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

import javax.swing.JCheckBox;
import javax.swing.JPanel;

import cnuphys.splot.edit.VerticalFlowLayout;
import cnuphys.splot.fit.CurveDrawingMethod;
import cnuphys.splot.pdata.ACurve;
import cnuphys.splot.pdata.HistoCurve;
import cnuphys.splot.pdata.HistoData;
import cnuphys.splot.plot.CommonBorder;
import cnuphys.splot.plot.Environment;
import cnuphys.splot.plot.TextFieldSlider;
import cnuphys.splot.style.EnumComboBox;

@SuppressWarnings("serial")
public class FitEditorPanel extends JPanel {

	// properties changed
	public static final String POLYNOMIALORDERPROP = "Polynomial Order";
	public static final String GAUSSIANNUMPROP = "Number of Gaussians";
	public static final String HARMONICORDERPROP = "Harmonic Order";
	public static final String USERMSPROP = "Use RMS in Legend";
	public static final String STATERRPROP = "Show Stat Errors";

	private static final Font _font = Environment.getInstance().getCommonFont(10);
	private static final Font _font2 = Environment.getInstance().getCommonFont(9);

	// change fit style
	EnumComboBox _fitSelector;

	// polynomial order
	protected TextFieldSlider _polynomialOrderSelector;

	// number of gaussians
	protected TextFieldSlider _gaussianCountSelector;

	// harmonic order (Fourier order)
	protected TextFieldSlider _harmonicOrderSelector;

	// use rms or sigma for histo
	protected JCheckBox _rmsOrCB;
	// stat error button
	protected JCheckBox _statErrorCB;
	// panel for two checkboxes
	protected JPanel _histoCBPanel;

	/**
	 * A Fit editing panel
	 */
	public FitEditorPanel() {
		addContent();
		setBorder(new CommonBorder("Fit"));
		Environment.getInstance().commonize(this, null);
	}

	// add the components
	private void addContent() {

		setLayout(new VerticalFlowLayout());

		_fitSelector = CurveDrawingMethod.getComboBox(CurveDrawingMethod.NONE);

		createPolySelector();
		createNumGaussSelector();
		createHarmonicOrderSelector();
		createRMSOrSigmaCB();
		createStatErrorCB();

		_histoCBPanel = new JPanel();
		_histoCBPanel.setLayout(new FlowLayout(FlowLayout.LEFT, 4, 1));
		_histoCBPanel.add(_rmsOrCB);
		_histoCBPanel.add(_statErrorCB);

		JPanel sp = new JPanel();
		sp.setLayout(new FlowLayout(FlowLayout.LEFT, 0, 0));
		sp.add(_fitSelector);
		add(sp);
	}

	// create the selector for the number of polygons
	private void createPolySelector() {

		String labels[] = { "1", "2", "3", "4", "5", "6", "7", "8" };
		_polynomialOrderSelector = new TextFieldSlider(1, 8, 2, _font, 0, labels, 180, 40, "Polynomial Order") {

			@Override
			public double sliderValueToRealValue() {
				return getValue();
			}

			@Override
			public int realValueToSliderValue(double val) {
				return (int) val;
			}

			@Override
			public String valueString(double val) {
				return "" + getValue();
			}

			@Override
			public void valueChanged() {
				firePropertyChange(POLYNOMIALORDERPROP, -1, _polynomialOrderSelector.getValue());
			}

		};
	}

	private void createRMSOrSigmaCB() {
		_rmsOrCB = new JCheckBox("RMS in Legend", true);
		_rmsOrCB.setFont(_font2);

		ItemListener il = new ItemListener() {

			@Override
			public void itemStateChanged(ItemEvent e) {
				boolean selected = _rmsOrCB.isSelected();
				_rmsOrCB.firePropertyChange(USERMSPROP, !selected, selected);
			}

		};

		_rmsOrCB.addItemListener(il);
	}

	private void createStatErrorCB() {
		_statErrorCB = new JCheckBox("Statistical Errors", false);
		_statErrorCB.setFont(_font2);

		ItemListener il = new ItemListener() {

			@Override
			public void itemStateChanged(ItemEvent e) {
				boolean selected = _statErrorCB.isSelected();
				_statErrorCB.firePropertyChange(STATERRPROP, !selected, selected);
			}

		};

		_statErrorCB.addItemListener(il);
	}

	// create the selector for the number of gaussians
	private void createNumGaussSelector() {

		String labels[] = { "1", "2", "3", "4", "5", "6" };
		_gaussianCountSelector = new TextFieldSlider(1, 6, 1, _font, 1, labels, 180, 40, "Number of Gaussians") {

			@Override
			public double sliderValueToRealValue() {
				return getValue();
			}

			@Override
			public int realValueToSliderValue(double val) {
				return (int) val;
			}

			@Override
			public String valueString(double val) {
				return "" + getValue();
			}

			@Override
			public void valueChanged() {
				firePropertyChange(GAUSSIANNUMPROP, -1, _gaussianCountSelector.getValue());
			}

		};
	}

	// create the selector for harmonic order (Fourier order)
	private void createHarmonicOrderSelector() {

		// Default order used in the data model when switching to HARMONIC is typically large (e.g. 300).
		// Keep the range broad enough for typical use while still being manageable in the UI.
		String labels[] = { "1", "100", "200", "300", "400", "500" };
		_harmonicOrderSelector = new TextFieldSlider(1, 500, 300, _font, 0, labels, 180, 40, "Harmonic Order") {

			@Override
			public double sliderValueToRealValue() {
				return getValue();
			}

			@Override
			public int realValueToSliderValue(double val) {
				return (int) val;
			}

			@Override
			public String valueString(double val) {
				return "" + getValue();
			}

			@Override
			public void valueChanged() {
				firePropertyChange(HARMONICORDERPROP, -1, _harmonicOrderSelector.getValue());
			}

		};
	}

	/**
	 * Reconfigure fit widgets based on fit type
	 * 
	 * @param curve the active curve
	 */
	public void reconfigure(ACurve curve) {
		// Remove everything that is method- or curve-type-specific.
		carefulRemove(_polynomialOrderSelector);
		carefulRemove(_gaussianCountSelector);
		carefulRemove(_harmonicOrderSelector);
		carefulRemove(_histoCBPanel);

		if (curve == null) {
			revalidate();
			repaint();
			return;
		}

		// Histogram-only widgets.
		if (curve instanceof HistoCurve) {
			carefulAdd(_histoCBPanel);
		}

		// Method-specific widgets.
		switch (curve.getCurveDrawingMethod()) {
		case POLYNOMIAL:
			carefulAdd(_polynomialOrderSelector);
			break;
		case GAUSSIANS:
			carefulAdd(_gaussianCountSelector);
			break;
		case HARMONIC:
			carefulAdd(_harmonicOrderSelector);
			break;
		default:
			// none
			break;
		}

		revalidate();
		repaint();
	}

	// set components enabled
	@Override
	public void setEnabled(boolean enabled) {
		super.setEnabled(enabled);
		_fitSelector.setEnabled(enabled);
		_polynomialOrderSelector.setEnabled(enabled);
		_gaussianCountSelector.setEnabled(enabled);
		_harmonicOrderSelector.setEnabled(enabled);
		_rmsOrCB.setEnabled(enabled);
		_statErrorCB.setEnabled(enabled);
	}

	private void carefulAdd(Component comp) {
		for (Component c : this.getComponents()) {
			if (c == comp) {
				return;
			}
		}
		add(comp);
	}

	private void carefulRemove(Component comp) {
		for (Component c : this.getComponents()) {
			if (c == comp) {
				remove(comp);
				return;
			}
		}
	}

	/**
	 * FitEditorPanel Set the choices
	 * 
	 * @param curve the curve whose fit-related settings should be reflected in the UI
	 */
	public void setFit(ACurve curve) {
		if (curve == null) {
			return;
		}

		// Histogram-specific settings
		if (curve instanceof HistoCurve) {
			HistoData hd = ((HistoCurve) curve).getHistoData();
			_rmsOrCB.setSelected(hd.useRmsInHistoLegend());
			_statErrorCB.setSelected(hd.drawStatisticalErrors());
		}

		CurveDrawingMethod cmd = curve.getCurveDrawingMethod();
		if (cmd != null) {
			// EnumComboBox historically used the enum's name() string.
			_fitSelector.setSelectedItem(cmd.name());
		}

		// Per-curve knobs (now stored on ACurve)
		_polynomialOrderSelector.setValue(curve.getPolynomialDegree());
		_gaussianCountSelector.setValue(curve.getOrder());
		_harmonicOrderSelector.setValue(curve.getOrder());
	}

	/**
	 * Further enable/disable based on fit type
	 * 
	 * @param type the active curve drawing method
	 */
	public void fitSpecific(CurveDrawingMethod type) {
		switch (type) {
		case POLYNOMIAL:
			_polynomialOrderSelector.setEnabled(true);
			_gaussianCountSelector.setEnabled(false);
			_harmonicOrderSelector.setEnabled(false);
			break;

		case GAUSSIANS:
			_polynomialOrderSelector.setEnabled(false);
			_gaussianCountSelector.setEnabled(true);
			_harmonicOrderSelector.setEnabled(false);
			break;

		case HARMONIC:
			_polynomialOrderSelector.setEnabled(false);
			_gaussianCountSelector.setEnabled(false);
			_harmonicOrderSelector.setEnabled(true);
			break;

		default:
			_polynomialOrderSelector.setEnabled(false);
			_gaussianCountSelector.setEnabled(false);
			_harmonicOrderSelector.setEnabled(false);
			break;
		}
	}

	/**
	 * Get the fit selector.
	 * 
	 * @return the fit selector
	 */
	public EnumComboBox getFitSelector() {
		return _fitSelector;
	}

	/**
	 * Get the polynomial order slider.
	 * 
	 * @return polynomial order slider
	 */
	public TextFieldSlider getPolynomialOrderSelector() {
		return _polynomialOrderSelector;
	}

	/**
	 * Get the number of gaussian slider.
	 * 
	 * @return number of gaussian slider
	 */
	public TextFieldSlider getNumGaussianSelector() {
		return _gaussianCountSelector;
	}

	/**
	 * Get the harmonic order slider.
	 *
	 * @return harmonic order slider
	 */
	public TextFieldSlider getHarmonicOrderSelector() {
		return _harmonicOrderSelector;
	}

	/**
	 * Get the rms or sigma check box.
	 * 
	 * @return the rms or sigma check box
	 */
	public JCheckBox getNumRMSCheckBox() {
		return _rmsOrCB;
	}

	/**
	 * Get the draw stat error check box.
	 * 
	 * @return the draw stat error checkbox
	 */
	public JCheckBox getStatErrorCheckBox() {
		return _statErrorCB;
	}

}

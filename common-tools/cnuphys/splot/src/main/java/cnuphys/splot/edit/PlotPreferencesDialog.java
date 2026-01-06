package cnuphys.splot.edit;

import java.awt.BorderLayout;
import java.awt.Component;

import javax.swing.JTabbedPane;

import cnuphys.splot.plot.PlotCanvas;

/**
 * A more comprehensive PlotParameters editor with tabs for:
 * <ul>
 *   <li>Labels</li>
 *   <li>Axes</li>
 *   <li>Legend</li>
 *   <li>Extra</li>
 * </ul>
 */
@SuppressWarnings("serial")
public class PlotPreferencesDialog extends SimpleDialog {

	protected PlotCanvas _plotCanvas;

	// button labels
	protected static final String APPLY = "Apply";
	protected static final String CLOSE = "Close";

	private JTabbedPane _tabs;

	private LabelsTabPanel _labelsPanel;
	private AxesTabPanel _axesPanel;
	private LegendTabPanel _legendPanel;
	private ExtraTabPanel _extraPanel;

	public PlotPreferencesDialog(PlotCanvas plotCanvas) {
		super("Plot Preferences", plotCanvas, true, APPLY, CLOSE);

		_plotCanvas = plotCanvas;

		addCenter();
		pack();
	}

	@Override
	protected void prepare() {
		_plotCanvas = (PlotCanvas) _userObject;
	}

	/**
	 * Build the tabbed editor in the dialog center.
	 */
	protected Component addCenter() {
		_tabs = new JTabbedPane();

		_labelsPanel = new LabelsTabPanel(_plotCanvas);
		_axesPanel   = new AxesTabPanel(_plotCanvas);
		_legendPanel = new LegendTabPanel(_plotCanvas);
		_extraPanel  = new ExtraTabPanel(_plotCanvas);

		_tabs.addTab("Labels", _labelsPanel);
		_tabs.addTab("Axes", _axesPanel);
		_tabs.addTab("Legend", _legendPanel);
		_tabs.addTab("Extra", _extraPanel);

		add(_tabs, BorderLayout.CENTER);
		return _tabs;
	}

	@Override
	protected void handleCommand(String command) {
		if (CLOSE.equals(command)) {
			setVisible(false);
			return;
		}

		if (APPLY.equals(command)) {

			_labelsPanel.apply();
			boolean axesAffectWorld = _axesPanel.apply(); // returns true if we should recompute world
			_legendPanel.apply();
			_extraPanel.apply();

			if (axesAffectWorld) {
				_plotCanvas.setWorldSystem();
			}
			_plotCanvas.repaint();
		}
	}
}

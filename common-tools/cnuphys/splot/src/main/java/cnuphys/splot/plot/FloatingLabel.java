package cnuphys.splot.plot;

import javax.swing.JLabel;

import cnuphys.splot.plot.old.PlotCanvas;
import cnuphys.splot.plot.old.PlotParameters;

public class FloatingLabel extends JLabel {

	// the owner plot panel
	private PlotCanvas _canvas;

	// the plot parameters
	private PlotParameters _params;

	public FloatingLabel(PlotCanvas canvas) {
		_canvas = canvas;
		_params = canvas.getParameters();
		setOpaque(true);
	}

}

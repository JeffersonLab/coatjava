package cnuphys.splot.edit;

import java.awt.GridLayout;

import javax.swing.JPanel;

import cnuphys.splot.plot.CommonBorder;
import cnuphys.splot.plot.PlotCanvas;

@SuppressWarnings("serial")
public class AxesLimitsPanel extends JPanel {
	
	//the two panels for t x and y axes
	private OneAxisLimitsPanel  xPanel;
	private OneAxisLimitsPanel  yPanel;

	/**
	 * Create the panel for editing the axes limits
	 * @param canvas
	 */
	public AxesLimitsPanel(PlotCanvas canvas) {
		setLayout(new GridLayout(2, 1, 4, 4));
		
		xPanel = new OneAxisLimitsPanel(canvas, OneAxisLimitsPanel.Axis.X);
		yPanel = new OneAxisLimitsPanel(canvas, OneAxisLimitsPanel.Axis.Y);
		
		setBorder(new CommonBorder("Axes Limits"));
		
		add(xPanel);
		add(yPanel);
	}
	
	/** Apply any changes */
	public void apply() {
		xPanel.apply();
		yPanel.apply();
	}
}

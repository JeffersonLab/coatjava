package cnuphys.splot.plot;

import java.awt.Color;
import java.awt.Font;

public class PlotParameters2 {

	// margins around the active plot area inside the component
	public int leftMargin = 60;
	public int rightMargin = 20;
	public int topMargin = 25;
	public int bottomMargin = 55;

	public boolean autoscale = true;
	public boolean includeXZero = false;
	public boolean includeYZero = false;

	public String title = "Plot";
	public String xLabel = "X";
	public String yLabel = "Y";

	public Font titleFont = Environment.getInstance().getCommonFont(18);
	public Font labelFont = Environment.getInstance().getCommonFont(13);
	public Font tickFont = Environment.getInstance().getCommonFont(10);

	public boolean drawGrid = true;
	public Color background = Color.white;

	// legend
	public boolean drawLegend = true;
	public boolean legendBorder = true;
	public int legendSampleLength = 50;

	// ticks
	public int targetXTicks = 8;
	public int targetYTicks = 8;

	// padding factor applied to autoscaled min/max
	public double autoPadFraction = 0.05;
}

package cnuphys.splot.example;

import java.awt.Color;

import cnuphys.splot.fit.CurveDrawingMethod;
import cnuphys.splot.pdata.Curve;
import cnuphys.splot.pdata.PlotData;
import cnuphys.splot.pdata.PlotDataException;
import cnuphys.splot.pdata.PlotDataType;
import cnuphys.splot.plot.PlotParameters;

@SuppressWarnings("serial")
public class StraightLine extends AExample {

	static double x[] = { 3, 2.5, 3.5, 4, 5 };
	static double y[] = { 238.0, 280.0, 310, 321.0, 420.0 };
	static double sig[] = { 14.7, 12.1, 13.1, 20.0, 8.0 };

	@Override
	protected PlotData createPlotData() throws PlotDataException {
		String[] curveNames = { "Linear Fit" };
		int[] fitOrders = { 1 }; // linear fit
		return new PlotData(PlotDataType.XYEXYE, curveNames, fitOrders);
	}

	@Override
	protected String getXAxisLabel() {
		return "<html>x data  X<SUB>M</SUB><SUP>2</SUP>";
	}

	@Override
	protected String getYAxisLabel() {
		return "<html>y data  Y<SUB>Q</SUB><SUP>2</SUP>";
	}

	@Override
	protected String getPlotTitle() {
		return "<html>Sample Plot X<SUP>2</SUP> vs. Q<SUP>2</SUP>";
	}

	@Override
	public void fillData() {
		PlotData plotData = _canvas.getPlotData();
		Curve curve = (Curve) plotData.getCurve(0);
		for (int i = 0; i < x.length; i++) {
			curve.add(x[i], y[i], sig[i]);
		}
	}

	@Override
	public void setParameters() {
		PlotData plotData = _canvas.getPlotData();
		
		//symbol fill color
		plotData.getCurve(0).getStyle().setFillColor(new Color(32, 32, 32, 64));
		
		//symbol border color
		Curve curve = (Curve) plotData.getCurve(0);
		curve.getStyle().setBorderColor(Color.darkGray);
		curve.setCurveMethod(CurveDrawingMethod.POLYNOMIAL);
		PlotParameters params = _canvas.getParameters();
		params.setMinExponentY(6)
		.setNumDecimalY(2)
		.setMinExponentX(6)
		.setNumDecimalX(3);
	}

	//--------------------------------------------------------------
	public static void main(String arg[]) {
		final StraightLine example = new StraightLine();

		javax.swing.SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				example.setVisible(true);
			}
		});

	}

}

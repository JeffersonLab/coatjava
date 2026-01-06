package cnuphys.splot.example;

import java.awt.Color;

import org.apache.commons.math3.special.Erf;

import cnuphys.splot.fit.CurveDrawingMethod;
import cnuphys.splot.fit.Evaluator;
import cnuphys.splot.pdata.Curve;
import cnuphys.splot.pdata.FitVectors;
import cnuphys.splot.pdata.PlotData;
import cnuphys.splot.pdata.PlotDataException;
import cnuphys.splot.pdata.PlotDataType;
import cnuphys.splot.plot.HorizontalLine;
import cnuphys.splot.plot.PlotParameters;

@SuppressWarnings("serial")
public class ErfTest extends AExample {


	@Override
	protected PlotData createPlotData() throws PlotDataException {
	String[] curveNames = {"Erf Fit" };
	   return new PlotData(PlotDataType.XYEXYE, curveNames, null);
	}


	@Override
	protected String getXAxisLabel() {
		return "<html>DAC Threshold";
	}

	@Override
	protected String getYAxisLabel() {
		return "<html>Occupancy";
	}

	@Override
	protected String getPlotTitle() {
		return "<html>p4 U1, BCO 128ns, BLR on, low gain, 125 ns, chan 0";
	}

	@Override
	public void fillData() {
		PlotData plotData = canvas.getPlotData();
		double A = 2.0;
		double x0 = 1.0;
		double sigma = 0.5;
		double B = 0.1;
		int n = 100;

		Evaluator erfEval = (double x) -> {
			double z = (x - x0) / sigma;
			return A * Erf.erf(z) + B;
		};

		FitVectors testData = FitVectors.testData(erfEval, -4.0, 4.0, n, 3.0, 3.0);
	   	Curve curve = (Curve) plotData.getFirstCurve();
		
	   	double e[] = new double[n];
			for (int i = 0; i < n; i++) {
				//convert weight to error
		    	e[i] = 1.0 / Math.sqrt(1.0e-12 + testData.w[i]);		    	
			}
		curve.addAll(testData.x, testData.y, e);
	}

	@Override
	public void setParameters() {
		PlotData plotData = canvas.getPlotData();
		Curve curve = (Curve) plotData.getFirstCurve();
		curve.setCurveMethod(CurveDrawingMethod.ERF);
		PlotParameters params = canvas.getParameters();
		params.addPlotLine(new HorizontalLine(canvas, 0));
		params.addPlotLine(new HorizontalLine(canvas, 1));
		curve.getStyle().setFillColor(new Color(0, 0, 240, 128));
		String extra[] = { "Sample annotation string",
				"Sample annotation string",
				"This box, like the Legend, is draggable." };
		params.setExtraStrings(extra);
	}

	public static void main(String arg[]) {

		// Run the GUI codes on the Event-Dispatching thread for thread safety
		javax.swing.SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				ErfTest example = new ErfTest();
				example.setVisible(true);
			}
		});

	}
}

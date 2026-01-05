package cnuphys.splot.example;

import java.awt.Color;

import cnuphys.splot.fit.CurveDrawingMethod;
import cnuphys.splot.fit.Evaluator;
import cnuphys.splot.pdata.FitVectors;
import cnuphys.splot.pdata.PlotData;
import cnuphys.splot.pdata.PlotDataException;
import cnuphys.splot.pdata.PlotDataType;
import cnuphys.splot.plot.PlotParameters;

public class Gaussian extends AExample {

	@Override
	protected PlotData createPlotData() throws PlotDataException {
		String[] curveNames = { "Gaussian Fit" };
		return new PlotData(PlotDataType.XYEXYE, curveNames, null);
	}

	@Override
	protected String[] getColumnNames() {
		String names[] = { "X", "Y", "E" };
		return names;
	}

	@Override
	protected String getXAxisLabel() {
		return "x";
	}

	@Override
	protected String getYAxisLabel() {
		return "y";
	}

	@Override
	protected String getPlotTitle() {
		return "Sample Gaussian Fit";
	}

	@Override
	public void fillData() {
		
		final double mu = 1.2;
 		final double sigma = 0.3;
 		final double A = 2.0;
 		final double B = 0.5;
 		int n = 50;
 		
 		
 		Evaluator eval = (double x) -> {
 			double z = (x - mu) / sigma;
 			return A * Math.exp(-0.5 * z * z) + B;
 		};
 		
 		FitVectors testData = FitVectors.testData(eval, -1.0, 3.0, n, 4.0, 10.0);		
		
		for (int i = 0; i < n; i++) {
			double x = testData.x[i];
			double y = testData.y[i];
			double w = testData.w[i];
			
			//convert weight to error
	    	double e = 1.0 / Math.sqrt(1.0e-12 + w);
			_canvas.getPlotData().add(x, y, e);

		}
		
	}

	@Override
	public void setParameters() {
		PlotData plotData = _canvas.getPlotData();
		
		//symbol fill color
		plotData.getCurve(0).getStyle().setFillColor(new Color(32, 32, 32, 64));
		
		//symbol border color
		plotData.getCurve(0).getStyle().setBorderColor(Color.darkGray);
		plotData.getCurve(0).setCurveMethod(CurveDrawingMethod.GAUSSIAN);
		PlotParameters params = _canvas.getParameters();
		params.setMinExponentY(6).setNumDecimalY(2);
	}
	
	public static void main(String arg[]) {
		final Gaussian example = new Gaussian();

		javax.swing.SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				example.setVisible(true);
			}
		});

	}

}

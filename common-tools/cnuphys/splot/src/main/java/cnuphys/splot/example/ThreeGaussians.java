package cnuphys.splot.example;

import java.awt.Color;

import cnuphys.splot.fit.CurveDrawingMethod;
import cnuphys.splot.fit.Evaluator;
import cnuphys.splot.pdata.Curve;
import cnuphys.splot.pdata.FitVectors;
import cnuphys.splot.pdata.PlotData;
import cnuphys.splot.pdata.PlotDataException;
import cnuphys.splot.pdata.PlotDataType;
import cnuphys.splot.plot.PlotParameters;

@SuppressWarnings("serial")
public class ThreeGaussians extends AExample {
	
	private static final String curveName = "3 Gaussian Fit";

	@Override
	protected PlotData createPlotData() throws PlotDataException {
		String[] curveNames = {curveName };
		int[] fitOrders = {3}; // fit to 3 gaussians
		return new PlotData(PlotDataType.XYEXYE, curveNames, fitOrders);
	}


	@Override
	protected String getXAxisLabel() {
		return "<html>x <b>data</b>";
	}

	@Override
	protected String getYAxisLabel() {
		return "<html>y <b>data</b>";
	}

	@Override
	protected String getPlotTitle() {
		return "<html>Fit to Three Gaussians";
	}

	@Override
	public void fillData() {
		final double[] mu = {1.2, 3.3, 5.3};
 		final double[] sigma = {0.5, 0.5, 0.4};
 		final double[] A = {2.0, 1.5, 1.1};
 		final double B = 0.5;
 		int n = 100;
 		int numGauss = A.length;

 		Evaluator eval = (double x) -> {
 			double sum = 0;
 			for (int k = 0; k < numGauss; k++) {
 				double dx = x - mu[k];
 				double z = dx / sigma[k];
 				sum += A[k] * Math.exp(-0.5 * z * z);
 			}
 			sum += B;
 			return sum;
 		};
 		
		FitVectors testData = FitVectors.testData(eval, -1.0, 7.0, n, 4.0, 5.0);
		for (int i = 0; i < n; i++) {
			double x = testData.x[i];
			double y = testData.y[i];
			double w = testData.w[i];
			
			//convert weight to error
	    	double e = 1.0 / Math.sqrt(1.0e-12 + w);
	    	
	    	Curve curve = (Curve) canvas.getPlotData().getCurve(curveName);
	    	
	    	//since we are on the EDT thread direct add is safe
			curve.add(x, y, e);

		}
		 
	}


	@Override
	public void setParameters() {
		PlotData plotData = canvas.getPlotData();
		
		//symbol fill color
		plotData.getCurve(0).getStyle().setFillColor(new Color(32, 32, 32, 64));
		
		//symbol border color
		plotData.getCurve(0).getStyle().setBorderColor(Color.darkGray);
		plotData.getCurve(0).setCurveMethod(CurveDrawingMethod.GAUSSIANS);
		PlotParameters params = canvas.getParameters();
		params.setMinExponentY(6).setNumDecimalY(2);
		params.mustIncludeXZero(true);
		params.mustIncludeYZero(true);

		String extra[] = { "This is an extra string", "This is a longer extra string",
				"This is an even longer extra string",
				"This box, like the Legend, is draggable." };
		params.setExtraStrings(extra);

	}

	public static void main(String arg[]) {
	
		javax.swing.SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				ThreeGaussians example = new ThreeGaussians();
				example.setVisible(true);
			}
		});

	}

}

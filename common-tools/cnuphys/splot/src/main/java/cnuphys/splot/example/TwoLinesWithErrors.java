package cnuphys.splot.example;

import java.util.Collection;

import cnuphys.splot.fit.CurveDrawingMethod;
import cnuphys.splot.fit.Evaluator;
import cnuphys.splot.pdata.ACurve;
import cnuphys.splot.pdata.Curve;
import cnuphys.splot.pdata.FitVectors;
import cnuphys.splot.pdata.PlotData;
import cnuphys.splot.pdata.PlotDataException;
import cnuphys.splot.pdata.PlotDataType;
import cnuphys.splot.plot.PlotParameters;

@SuppressWarnings("serial")
public class TwoLinesWithErrors extends AExample {

	@Override
	protected PlotData createPlotData() throws PlotDataException {
		String[] curveNames = { "Line 1", "Line 2" };
		int[] fitOrders = { 1, 1 }; // linear fits
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
	
	//test data for line 1
	private FitVectors line1Data() {
		final double m = 3.3; // slope
		final double b = -0.4; // intercept
		int n = 40;

		Evaluator evaluator = new Evaluator() {
			@Override
			public double value(double x) {
				return m * x + b;
			}
		};

		// test data
		return FitVectors.testData(evaluator, 0.0, 10.0, n, 10, 20);
	}
	
	//test data for line 2
	private FitVectors line2Data() {
		final double m = -1.7; // slope
		final double b =8; // intercept
		int n = 20;

		Evaluator evaluator = new Evaluator() {
			@Override
			public double value(double x) {
				return m * x + b;
			}
		};

		// test data
		return FitVectors.testData(evaluator, 0.0, 10.0, n, 6, 11);
	}

	@Override
	public void fillData() {
		FitVectors fv1 = line1Data();
		FitVectors fv2 = line2Data();
		Curve curve1 = (Curve) _canvas.getPlotData().getCurve(0);
		Curve curve2 = (Curve) _canvas.getPlotData().getCurve(1);
		
		for (int i = 0; i < fv1.x.length; i++) {
			double e = 1.0 / Math.sqrt(1.0e-12 + fv1.w[i]);
			curve1.add(fv1.x[i], fv1.y[i], e);
		}
		
		for (int i = 0; i < fv2.x.length; i++) {
			double e = 1.0 / Math.sqrt(1.0e-12 + fv2.w[i]);
			curve2.add(fv2.x[i], fv2.y[i], e);
		}
		

	}

	@Override
	public void setParameters() {
		PlotData plotData = _canvas.getPlotData();
		Collection<ACurve> curves = plotData.getCurves();
		for (ACurve dc : curves) {
			dc.setCurveMethod(CurveDrawingMethod.POLYNOMIAL);
		}

		// many options controlled via plot parameters
		PlotParameters params = _canvas.getParameters();
		params.mustIncludeXZero(true);
		params.mustIncludeYZero(true);
	}

	public static void main(String arg[]) {
		final TwoLinesWithErrors example = new TwoLinesWithErrors();

		javax.swing.SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				example.setVisible(true);
			}
		});

	}

}

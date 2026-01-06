package cnuphys.splot.example;

import cnuphys.splot.fit.CurveDrawingMethod;
import cnuphys.splot.pdata.Curve;
import cnuphys.splot.pdata.PlotData;
import cnuphys.splot.pdata.PlotDataException;
import cnuphys.splot.pdata.PlotDataType;
import cnuphys.splot.plot.PlotParameters;
import cnuphys.splot.plot.X11Colors;

@SuppressWarnings("serial")
public class AnotherGaussian extends AExample {



	static double rawData[] = { 17200.000000, 0.000000, 0.000000, 17600.000000, 0.000000, 0.000000, 18000.000000,
			0.000000, 0.000000, 18400.000000, 0.000000, 0.000000, 18800.000000, 0.000000, 0.000000, 19200.000000,
			0.000000, 0.000000, 19600.000000, 0.000000, 0.000000, 20000.000000, 0.000000, 0.000000, 20400.000000,
			0.000000, 0.000000, 20800.000000, 0.000000, 0.000000, 21200.000000, 0.000000, 0.000000, 21600.000000,
			0.000000, 0.000000, 22000.000000, 0.000000, 0.000000, 22400.000000, 0.000000, 0.000000, 22800.000000,
			1.000000, 1.000000, 23200.000000, 2.000000, 1.414214, 23600.000000, 7.000000, 2.645751, 24000.000000,
			32.000000, 5.656854, 24400.000000, 33.000000, 5.744563, 24800.000000, 28.000000, 5.291503, 25200.000000,
			20.000000, 4.472136, 25600.000000, 3.000000, 1.732051, 26000.000000, 2.000000, 1.414214, 26400.000000,
			0.000000, 0.000000, 26800.000000, 0.000000, 0.000000, 27200.000000, 0.000000, 0.000000, 27600.000000,
			0.000000, 0.000000, 28000.000000, 0.000000, 0.000000, 28400.000000, 0.000000, 0.000000, 28800.000000,
			0.000000, 0.000000, 29200.000000, 0.000000, 0.000000, 29600.000000, 0.000000, 0.000000, 30000.000000,
			0.000000, 0.000000, 30400.000000, 0.000000, 0.000000, 30800.000000, 0.000000, 0.000000, 31200.000000,
			0.000000, 0.000000, 31600.000000, 0.000000, 0.000000, 32000.000000, 0.000000, 0.000000, 32400.000000,
			0.000000, 0.000000, 32800.000000, 0.000000, 0.000000 };

	@Override
	protected PlotData createPlotData() throws PlotDataException {
		String[] curveNames = {"Gussian" };
		return new PlotData(PlotDataType.XYEXYE, curveNames, null);
	}


	@Override
	protected String getXAxisLabel() {
		return "<html>Channel";
	}

	@Override
	protected String getYAxisLabel() {
		return "<html>Counts";
	}

	@Override
	protected String getPlotTitle() {
		return "<html>Fit to Gaussian";
	}

	@Override
	public void fillData() {
		PlotData plotData = canvas.getPlotData();
		Curve curve = (Curve) plotData.getCurve(0);

		for (int i = 0; i < rawData.length; i += 3) {
			curve.add(rawData[i], rawData[i + 1], rawData[i + 2]);
		}
	}

	@Override
	public void setParameters() {
		PlotData plotData = canvas.getPlotData();
		
		Curve curve = (Curve) plotData.getCurve(0);
		//symbol fill color
		curve.getStyle().setFillColor(X11Colors.getX11Color("dark sea green"));
		
		//symbol border color
		curve.getStyle().setBorderColor(X11Colors.getX11Color("dark red"));
		curve.setCurveMethod(CurveDrawingMethod.GAUSSIAN);
		PlotParameters params = canvas.getParameters();
		params.setMinExponentY(6).setNumDecimalY(2);

	}

	public static void main(String arg[]) {

		javax.swing.SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				AnotherGaussian example = new AnotherGaussian();
				example.setVisible(true);
			}
		});

	}

}

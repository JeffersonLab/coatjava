package cnuphys.splot.example;

import java.awt.Color;

import org.apache.commons.math3.distribution.NormalDistribution;

import cnuphys.splot.fit.CurveDrawingMethod;
import cnuphys.splot.pdata.DataSet;
import cnuphys.splot.pdata.PlotDataException;
import cnuphys.splot.plot.PlotParameters;
import cnuphys.splot.pdata.HistoData;

public class TwoHisto extends AExample {

	@Override
	protected DataSet createDataSet() throws PlotDataException {
		HistoData h1 = new HistoData("Histo 1", 0.0, 100.0, 50);
		HistoData h2 = new HistoData("Histo 2", 0.0, 150.0, 50);
		return new DataSet(h1, h2);
	}

	@Override
	protected String[] getColumnNames() {
		return null;
	}

	@Override
	protected String getXAxisLabel() {
		return "some measured value";
	}

	@Override
	protected String getYAxisLabel() {
		return "Counts";
	}

	@Override
	protected String getPlotTitle() {
		return "Sample 1D Histograms";
	}

	@Override
	public void fillData() {
		int n = 10000;
		double mu = 50.0;
		double sig = 10.0;
	    NormalDistribution normDev1 = new NormalDistribution(mu, sig);

		mu = 100.0;
		sig = 20.0;
	    NormalDistribution normDev2 = new NormalDistribution(mu, sig);

		DataSet ds = _canvas.getPlotData();
		for (int i = 0; i < n; i++) {
			double y1 = normDev1.sample();
			double y2 = normDev2.sample();
			try {
				ds.add(y1, y2);
			}
			catch (PlotDataException e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	public void setPreferences() {
		DataSet ds = _canvas.getPlotData();

		ds.getCurveStyle(0).setFillColor(new Color(196, 196, 196, 64));
		ds.getCurveStyle(0).setBorderColor(Color.black);
		ds.getCurve(0).getFit().setFitType(CurveDrawingMethod.GAUSSIANS);

		ds.getCurveStyle(1).setFillColor(new Color(196, 196, 196, 64));
		ds.getCurveStyle(1).setBorderColor(Color.red);
		ds.getCurve(1).getFit().setFitType(CurveDrawingMethod.GAUSSIANS);

		PlotParameters params = _canvas.getParameters();
		params.setMinExponentY(6);
		params.setNumDecimalY(0);
	}

	public static void main(String arg[]) {
		final TwoHisto example = new TwoHisto();

		javax.swing.SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				example.setVisible(true);
			}
		});

	}
}

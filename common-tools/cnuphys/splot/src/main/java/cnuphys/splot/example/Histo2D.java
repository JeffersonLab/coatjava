package cnuphys.splot.example;

import org.apache.commons.math3.distribution.NormalDistribution;

import cnuphys.splot.pdata.DataSet;
import cnuphys.splot.pdata.DataSetException;
import cnuphys.splot.pdata.Histo2DData;
import cnuphys.splot.plot.PlotParameters;

public class Histo2D extends AExample {
	@Override
	protected DataSet createDataSet() throws DataSetException {
		Histo2DData h1 = new Histo2DData("Histo 2D Example", "X variable", "Y variable", 0.0, 100.0, 80, 0.0, 50.0, 60);
		return new DataSet(h1);
	}

	@Override
	protected String[] getColumnNames() {
		return null;
	}

	@Override
	protected String getXAxisLabel() {
		return null;
	}

	@Override
	protected String getYAxisLabel() {
		return null;
	}

	@Override
	protected String getPlotTitle() {
		return "2D Histogram";
	}

	@Override
	public void fillData() {
		int n = 1000000;
		double mu = 50.0;
		double sig = 20.0;
	    NormalDistribution normDevX = new NormalDistribution(mu, sig);

		mu = 35;
	    NormalDistribution normDevY = new NormalDistribution(mu, sig);

		DataSet ds = _canvas.getDataSet();
		for (int i = 0; i < n; i++) {
			double x = normDevX.sample();
			double y = normDevY.sample();
			try {
				ds.add(x, y);
			}
			catch (DataSetException e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	public void setPreferences() {
		PlotParameters params = _canvas.getParameters();

		Histo2DData h2d = _canvas.getDataSet().getColumn(0).getHistoData2D();

		params.setMinExponentY(6);
		params.setNumDecimalY(0);

		params.setPlotTitle(h2d.getName());
		params.setXLabel(h2d.getXName());
		params.setYLabel(h2d.getYName());

		params.setXRange(h2d.getMinX(), h2d.getMaxX());
		params.setYRange(h2d.getMinY(), h2d.getMaxY());
		params.setLegendDrawing(false);
		params.setGradientDrawing(true);
	}

	public static void main(String arg[]) {
		final Histo2D example = new Histo2D();

		javax.swing.SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				example.setVisible(true);
			}
		});
	}

}

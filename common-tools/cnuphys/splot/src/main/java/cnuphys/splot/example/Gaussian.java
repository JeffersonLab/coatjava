package cnuphys.splot.example;

import java.awt.Color;
import java.util.Random;

import org.apache.commons.math3.distribution.NormalDistribution;

import cnuphys.splot.fit.FitType;
import cnuphys.splot.pdata.DataSet;
import cnuphys.splot.pdata.DataSetException;
import cnuphys.splot.pdata.DataSetType;
import cnuphys.splot.plot.PlotParameters;

public class Gaussian extends AExample {

	@Override
	protected DataSet createDataSet() throws DataSetException {
		return new DataSet(DataSetType.XYEXYE, getColumnNames());
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
		int n = 100;
		double mu = 1.0;
		double sig = 0.2;
	    NormalDistribution normDev = new NormalDistribution(mu, sig);

		DataSet ds = _canvas.getDataSet();
		Random rand = new Random();
		for (int i = 0; i < n; i++) {
			double x = 2.0 * rand.nextDouble();
			double y = normDev.density(x) + 0.2*(rand.nextDouble() - 0.5);
			double e = 0.2*rand.nextDouble();
		    try {
				ds.add(x, y, e);
			} catch (DataSetException e1) {
				e1.printStackTrace();
			}
		}
	}

	@Override
	public void setPreferences() {
		DataSet ds = _canvas.getDataSet();
		ds.getCurveStyle(0).setFillColor(new Color(196, 196, 196, 64));
		ds.getCurveStyle(0).setBorderColor(Color.black);
		ds.getCurve(0).getFit().setFitType(FitType.GAUSSIANS);
		PlotParameters params = _canvas.getParameters();
		params.setMinExponentY(6);
		params.setNumDecimalY(2);
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

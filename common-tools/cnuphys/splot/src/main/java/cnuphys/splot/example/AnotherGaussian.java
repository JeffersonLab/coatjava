package cnuphys.splot.example;

import java.util.Collection;

import cnuphys.splot.fit.CurveDrawingMethod;
import cnuphys.splot.pdata.OldDataColumn;
import cnuphys.splot.pdata.OldDataColumn;
import cnuphys.splot.pdata.DataSet;
import cnuphys.splot.pdata.DataSetException;
import cnuphys.splot.pdata.DataSetType;

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
		DataSet ds = _canvas.getDataSet();

		for (int i = 0; i < rawData.length; i += 3) {

			try {
				ds.add(rawData[i], rawData[i + 1], rawData[i + 2]);
			}
			catch (DataSetException e) {
				e.printStackTrace();
				System.exit(1);
			}
		}
	}

	@Override
	public void setPreferences() {
		DataSet ds = _canvas.getDataSet();
		Collection<OldDataColumn> ycols = ds.getAllColumnsByType(DataColumnType.Y);
		for (OldDataColumn dc : ycols) {
			dc.getFit().setFitType(CurveDrawingMethod.GAUSSIANS);
			dc.getFit().setNumGaussian(1);
		}

	}

	public static void main(String arg[]) {
		final AnotherGaussian example = new AnotherGaussian();

		javax.swing.SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				example.setVisible(true);
			}
		});

	}

}

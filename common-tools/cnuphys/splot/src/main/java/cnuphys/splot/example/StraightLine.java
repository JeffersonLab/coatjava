package cnuphys.splot.example;

import cnuphys.splot.pdata.PlotData;
import cnuphys.splot.pdata.PlotDataException;
import cnuphys.splot.pdata.PlotDataType;

public class StraightLine extends AExample {

	static double x[] = { 3, 4, 5 };
	static double y[] = { 238.065830, 323.394672, 409.656607 };
	static double sig[] = { 0.085087, 0.086192, 0.087027 };

	@Override
	protected PlotData createPlotData() throws PlotDataException {
		return new PlotData(PlotDataType.XYEXYE, getColumnNames());
	}

	@Override
	protected String[] getColumnNames() {
		String names[] = { "X1", "Y1", "E1" };
		return names;
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
		PlotData ds = _canvas.getPlotData();
		for (int i = 0; i < x.length; i++) {
			ds.add(x[i], y[i], sig[i]);
		}
	}

	@Override
	public void setPreferences() {
	}

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

package cnuphys.splot.example;

import java.awt.Color;
import java.util.Collection;

import cnuphys.splot.fit.CurveDrawingMethod;
import cnuphys.splot.pdata.OldDataColumn;
import cnuphys.splot.pdata.OldDataColumn;
import cnuphys.splot.pdata.DataSet;
import cnuphys.splot.pdata.PlotDataException;
import cnuphys.splot.pdata.PlotDataType;
import cnuphys.splot.plot.HorizontalLine;
import cnuphys.splot.plot.PlotParameters;
import cnuphys.splot.plot.VerticalLine;
import cnuphys.splot.style.SymbolType;

public class Scatter extends AExample {

	@Override
	protected DataSet createPlotData() throws PlotDataException {
		return new DataSet(PlotDataType.XYXY, getColumnNames());
	}

	@Override
	protected String[] getColumnNames() {
		String names[] = { "X", "Y" };
		return names;
	}

	@Override
	protected String getYAxisLabel() {
		return "Y Data";
	}

	@Override
	protected String getPlotTitle() {
		return "Scatter Plot";
	}

	@Override
	protected String getXAxisLabel() {
		return "X Data";
	}

	@Override
	public void fillData() {
		DataSet ds = _canvas.getPlotData();
		for (int i = 0; i < 1000; i++) {
			// demo that the data can be added out of order
			double x = -0.5 + Math.random();
			double y = x + 0.2 * (Math.random() - 0.5);

			try {
				ds.add(x, y);
			}
			catch (PlotDataException e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	public void setParameters() {
		Color fillColor = new Color(255, 0, 0, 96);
		DataSet ds = _canvas.getPlotData();
		Collection<OldDataColumn> ycols = ds.getAllColumnsByType(DataColumnType.Y);

		for (OldDataColumn dc : ycols) {
			dc.getFit().setFitType(CurveDrawingMethod.LINE);
			dc.getStyle().setSymbolType(SymbolType.CIRCLE);
			dc.getStyle().setSymbolSize(4);
			dc.getStyle().setFillColor(fillColor);
			dc.getStyle().setBorderColor(null);
			dc.getStyle().setFitLineColor(Color.black);
			dc.getStyle().setFitLineWidth(2.0f);
		}

		// many options controlled via plot parameters
		PlotParameters params = _canvas.getParameters();
		params.mustIncludeXZero(true);
		params.mustIncludeYZero(true);
		params.addPlotLine(new HorizontalLine(_canvas, 0));
		params.addPlotLine(new VerticalLine(_canvas, 0));
		params.setLegendDrawing(true);
	}

	public static void main(String arg[]) {
		final Scatter example = new Scatter();

		javax.swing.SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				example.setVisible(true);
			}
		});

	}

}

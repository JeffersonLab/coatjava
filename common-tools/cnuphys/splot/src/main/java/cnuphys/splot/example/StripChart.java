package cnuphys.splot.example;

import java.awt.Color;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import cnuphys.splot.fit.CurveDrawingMethod;
import cnuphys.splot.fit.Evaluator;
import cnuphys.splot.pdata.PlotData;
import cnuphys.splot.pdata.PlotDataException;
import cnuphys.splot.pdata.StripChartCurve;
import cnuphys.splot.plot.LimitsMethod;
import cnuphys.splot.plot.PlotParameters;
import cnuphys.splot.style.IStyled;
import cnuphys.splot.style.SymbolType;

@SuppressWarnings("serial")
public class StripChart extends AExample implements Evaluator {
	
	private Random random = new Random();

	@Override
	protected PlotData createPlotData() throws PlotDataException {
		int capacity = 25;
		int updateTimeMs = 1000;
		StripChartCurve sd = new StripChartCurve("Memory", capacity, this, updateTimeMs);
		return new PlotData(sd);
	}

	@Override
	protected String getXAxisLabel() {
		return "Time (ms)";
	}

	@Override
	protected String getYAxisLabel() {
		return "Heap Memory (MB)";
	}

	@Override
	protected String getPlotTitle() {
		return "Sample Strip Chart";
	}

	@Override
	public void fillData() {
	}

	@Override
	public void setParameters() {
		PlotData ds = canvas.getPlotData();
		StripChartCurve sc = (StripChartCurve) ds.getFirstCurve();
		sc.setCurveMethod(CurveDrawingMethod.STAIRS);
		IStyled style = sc.getStyle();
		style.setLineColor(Color.red);
		style.setFillColor(new Color(128, 0, 0, 48));
		style.setSymbolType(SymbolType.NOSYMBOL);
		PlotParameters params = canvas.getParameters();
		params.setMinExponentY(6);
		params.setNumDecimalY(2);
		params.setXLimitsMethod(LimitsMethod.USEDATALIMITS);
		params.mustIncludeYZero(true);
	}

	@Override
	public double value(double x) {
		// memory in mb
		
		double megaBytes = 100*(random.nextDouble() - 0.5) + (Runtime.getRuntime().totalMemory()) / 1048576.;
		return megaBytes;
	}

	public static void main(String arg[]) {

		javax.swing.SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				StripChart example = new StripChart();
				PlotData ds = example.canvas.getPlotData();
				StripChartCurve sc = (StripChartCurve) ds.getFirstCurve();
				sc.setTimeUnit(TimeUnit.SECONDS);
				PlotParameters params = example.canvas.getParameters();
				params.setXLabel("Time (" + sc.getTimeUnitShortLabel() + ")");

				example.setVisible(true);
				sc.start();
			}
		});

	}

}

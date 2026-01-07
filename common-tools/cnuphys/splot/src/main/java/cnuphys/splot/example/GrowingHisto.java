package cnuphys.splot.example;

import java.awt.Color;

import org.apache.commons.math3.distribution.NormalDistribution;

import cnuphys.splot.fit.CurveDrawingMethod;
import cnuphys.splot.pdata.PlotDataException;
import cnuphys.splot.plot.PlotCanvas;
import cnuphys.splot.plot.PlotParameters;
import cnuphys.splot.style.IStyled;
import cnuphys.splot.pdata.HistoCurve;
import cnuphys.splot.pdata.HistoData;
import cnuphys.splot.pdata.PlotData;

@SuppressWarnings("serial")
public class GrowingHisto extends AExample {

	@Override
	protected PlotData createPlotData() throws PlotDataException {
		HistoData h1 = new HistoData("Histo 1", 0.0, 100.0, 50);
		return new PlotData(h1);
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
		return "Growing Histogram Thread Test";
	}

	@Override
	public void fillData() {
		//no op, data added by background tread
	}

	@Override
	public void setParameters() {
	    HistoCurve hc = (HistoCurve) canvas.getPlotData().getCurve(0);
	    IStyled style = hc.getStyle();
		style.setFillColor(new Color(196, 196, 196, 64));
		style.setBorderColor(Color.black);
		
		//basic example, not fitting
		hc.setCurveMethod(CurveDrawingMethod.GAUSSIAN);
		PlotParameters params = canvas.getParameters();
		params.setMinExponentY(6);
		params.setNumDecimalY(0);
	}
	
	private static void addData(final PlotCanvas canvas, final long maxCount, final int increment, NormalDistribution normDev) {
	    final HistoCurve hc = (HistoCurve) canvas.getPlotData().getCurve(0);
	    
	    final double[] x = new double[increment];

		Runnable runner = new Runnable() {
			@Override
			public void run() {
				int count = 0;
				while (count < maxCount) {
					count += increment;
					for (int i = 0; i < increment; i++) {
						x[i] = normDev.sample();
					}
					hc.addAll(x);
					try {
						Thread.sleep(100);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
		};
		
		Thread sourceThread = new Thread(runner);
		sourceThread.start();
	    
	}

	public static void main(String arg[]) {

		javax.swing.SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				GrowingHisto example = new GrowingHisto();
				example.setVisible(true);
				double mu = 50.0;
				double sig = 10.0;
			    NormalDistribution normDev = new NormalDistribution(mu, sig);
				addData(example.getPlotCanvas(), 100000, 100, normDev);
			}
		});



	}
}

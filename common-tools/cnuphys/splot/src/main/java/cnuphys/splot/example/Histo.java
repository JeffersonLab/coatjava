package cnuphys.splot.example;

import java.awt.Color;

import org.apache.commons.math3.distribution.NormalDistribution;

import cnuphys.splot.fit.CurveDrawingMethod;
import cnuphys.splot.pdata.PlotDataException;
import cnuphys.splot.plot.PlotParameters;
import cnuphys.splot.style.IStyled;
import cnuphys.splot.pdata.HistoCurve;
import cnuphys.splot.pdata.HistoData;
import cnuphys.splot.pdata.PlotData;

public class Histo extends AExample {

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
		return "Sample 1D Histogram";
	}

	@Override
	public void fillData() {
		int n = 10000;
		double mu = 50.0;
		double sig = 10.0;
	    NormalDistribution normDev = new NormalDistribution(mu, sig);

	    //since this is the EDT thread, we can use add directly. If
	    //it was a background thread, we would use enqueue 
	    HistoCurve hc = (HistoCurve) canvas.getPlotData().getCurve(0);
		for (int i = 0; i < n; i++) {
			double val = normDev.sample();
			hc.add(val);
		}
	}

	@Override
	public void setParameters() {
	    HistoCurve hc = (HistoCurve) canvas.getPlotData().getCurve(0);
	    IStyled style = hc.getStyle();
		style.setFillColor(new Color(196, 196, 196, 64));
		style.setBorderColor(Color.black);
		
		//basic example, not fitting
		hc.setCurveMethod(CurveDrawingMethod.NONE);
		PlotParameters params = canvas.getParameters();
		params.setMinExponentY(6);
		params.setNumDecimalY(0);

	}

	// ---------------------------------------------------------------
	public static void main(String arg[]) {

		javax.swing.SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				Histo example = new Histo();
				example.setVisible(true);
			}
		});

	}
}

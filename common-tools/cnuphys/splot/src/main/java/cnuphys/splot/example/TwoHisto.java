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

@SuppressWarnings("serial")
public class TwoHisto extends AExample {

	@Override
	protected PlotData createPlotData() throws PlotDataException {
		HistoData h1 = new HistoData("Histo 1", 0.0, 100.0, 50);
		HistoData h2 = new HistoData("Histo 2", 0.0, 150.0, 50);
		return new PlotData(h1, h2);
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

	    HistoCurve hc1 = (HistoCurve) canvas.getPlotData().getCurve(0);
	    HistoCurve hc2 = (HistoCurve) canvas.getPlotData().getCurve(1);
	    
		for (int i = 0; i < n; i++) {
			
			double val1 = normDev1.sample();
			double val2 = normDev2.sample();
			hc1.add(val1);
			hc2.add(val2);
		}
	}

	@Override
	public void setParameters() {
	    HistoCurve hc1 = (HistoCurve) canvas.getPlotData().getCurve(0);
	    HistoCurve hc2 = (HistoCurve) canvas.getPlotData().getCurve(1);
	    IStyled style1 = hc1.getStyle();
	    IStyled style2 = hc2.getStyle();
	
		style1.setFillColor(new Color(196, 196, 196, 64));
		style1.setBorderColor(Color.black);
		hc1.setCurveMethod(CurveDrawingMethod.GAUSSIAN);

		style2.setFillColor(new Color(196, 196, 196, 64));
		style2.setLineColor(Color.red);
		style2.setBorderColor(Color.red);
		hc2.setCurveMethod(CurveDrawingMethod.GAUSSIAN);

		PlotParameters params = canvas.getParameters();
		params.setMinExponentY(6);
		params.setNumDecimalY(0);
	}

	public static void main(String arg[]) {

		javax.swing.SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				TwoHisto example = new TwoHisto();
				example.setVisible(true);
			}
		});

	}
}

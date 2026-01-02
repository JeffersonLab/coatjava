package cnuphys.splot.demo;

import java.awt.BorderLayout;
import java.util.Random;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import cnuphys.splot.pdata.Curve;
import cnuphys.splot.pdata.DataColumn;
import cnuphys.splot.pdata.HistoCurve;
import cnuphys.splot.pdata.HistoData;
import cnuphys.splot.pdata.PlotDataException;
import cnuphys.splot.plot.PlotCanvas2;
import cnuphys.splot.plot.PlotParameters2;
import cnuphys.splot.plot.interact.PanZoomController;
import cnuphys.splot.plot.model.DefaultPlotModel;

/**
 * Minimal runnable demo for the new PlotCanvas2 pipeline using the current data model.
 */
public class PlotDemoMain {

	public static void main(String[] args) {
		SwingUtilities.invokeLater(() -> {

			DefaultPlotModel model = new DefaultPlotModel();

			try {
				model.addCurve(createSineCurve());
			} catch (PlotDataException e) {
				throw new RuntimeException("Failed creating sine curve", e);
			}

			model.addCurve(createGaussianHistogram());

			PlotParameters2 params = new PlotParameters2();
			params.title = "sPlot PlotCanvas2 Demo";
			params.xLabel = "x";
			params.yLabel = "y / counts";
			params.drawLegend = true;
			params.drawGrid = true;
			params.autoscale = true;

			PlotCanvas2 canvas = new PlotCanvas2(model, params);
			new PanZoomController(canvas);

			JFrame f = new JFrame("sPlot Demo");
			f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
			f.getContentPane().setLayout(new BorderLayout());
			f.getContentPane().add(canvas, BorderLayout.CENTER);

			f.setSize(980, 680);
			f.setLocationRelativeTo(null);
			f.setVisible(true);
		});
	}

	private static Curve createSineCurve() throws PlotDataException {

		DataColumn x = new DataColumn("x");
		DataColumn y = new DataColumn("y");
		DataColumn e = null; // no errors for this demo

		Random r = new Random(0xC0FFEE);

		for (int i = 0; i < 800; i++) {
			double xx = -10.0 + 20.0 * i / 799.0;
			double yy = Math.sin(xx) + 0.15 * r.nextGaussian();

			x.add(xx); // autobox -> ArrayList<Double>
			y.add(yy);
		}

		return new Curve("Noisy sine", x, y, e);
	}

	private static HistoCurve createGaussianHistogram() {

		int bins = 80;
		double xmin = -5.0;
		double xmax = 5.0;

		HistoData hd = new HistoData("Gaussian", xmin, xmax, bins);

		Random r = new Random(1234);
		for (int i = 0; i < 20000; i++) {
			hd.add(r.nextGaussian()); // HistoData.add(double)
		}

		return new HistoCurve("Gaussian histogram", hd);
	}
}

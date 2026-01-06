package cnuphys.splot.example;

import java.awt.Color;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.SwingUtilities;

import cnuphys.splot.fit.CurveDrawingMethod;
import cnuphys.splot.pdata.Curve;
import cnuphys.splot.pdata.PlotData;
import cnuphys.splot.pdata.PlotDataException;
import cnuphys.splot.pdata.PlotDataType;
import cnuphys.splot.plot.HorizontalLine;
import cnuphys.splot.plot.PlotParameters;
import cnuphys.splot.plot.VerticalLine;
import cnuphys.splot.style.SymbolType;

/**
 * <p>
 * <b>Scatter (Thread-Safety / Data-Acquisition Stress Test)</b>
 * </p>
 *
 * <p>
 * This example intentionally simulates a <em>live data acquisition (DAQ)</em>
 * system in which data points arrive asynchronously from one or more
 * background threads while the plot is actively rendering.
 * </p>
 *
 * <p>
 * Unlike most examples, {@link #fillData()} is deliberately a no-op.
 * Instead, one or more feeder threads continuously generate and add
 * random points to a {@link Curve} over time.
 * </p>
 *
 * <p>
 * The primary purpose of this example is to:
 * </p>
 * <ul>
 *   <li>Stress-test thread safety of {@link Curve} and related model classes</li>
 *   <li>Expose race conditions between data mutation and rendering</li>
 *   <li>Simulate realistic streaming data (e.g. detectors, sensors, monitors)</li>
 * </ul>
 *
 * <p>
 * The example supports two operating modes:
 * </p>
 * <ul>
 *   <li>
 *     <b>Unsafe / stress mode</b> — background threads call {@code Curve.add()}
 *     directly. This is intentionally dangerous and may expose concurrency bugs.
 *   </li>
 *   <li>
 *     <b>Safe mode</b> — background threads enqueue data additions onto the
 *     Swing Event Dispatch Thread (EDT).
 *   </li>
 * </ul>
 *
 * <p>
 * Toggle this behavior via {@link #ADD_ON_EDT}.
 * </p>
 *
 * <p>
 * <b>Important:</b> If failures occur only in unsafe mode but not in EDT mode,
 * that is a strong indication that synchronization or buffering is needed
 * inside the data model.
 * </p>
 */
@SuppressWarnings("serial")
public class Scatter extends AExample {

	/**
	 * Controls how data points are added:
	 * <ul>
	 *   <li>{@code true}  → additions are serialized on the EDT (thread-safe)</li>
	 *   <li>{@code false} → additions occur on background threads (stress test)</li>
	 * </ul>
	 */
	private static final boolean ADD_ON_EDT = false;

	/**
	 * Number of concurrent feeder threads simulating independent
	 * data acquisition sources.
	 */
	private static final int FEEDER_COUNT = 3;

	/**
	 * Delay (milliseconds) between successive data points per feeder.
	 * Smaller values increase contention and stress.
	 */
	private static final int FEED_PERIOD_MS = 2;

	/** Running flag controlling feeder thread lifetime */
	private final AtomicBoolean _running = new AtomicBoolean(false);

	/** Active feeder threads */
	private final List<Thread> _feeders = new ArrayList<>();

	/** The curve receiving streamed data */
	private volatile Curve _curve;

	/**
	 * Create a simple XY plot with a single curve.
	 */
	@Override
	protected PlotData createPlotData() throws PlotDataException {
		String[] curveNames = { "Data" };
		int[] fitOrders = { 1 }; // linear fit
		return new PlotData(PlotDataType.XYXY, curveNames, fitOrders);
	}

	@Override
	protected String getYAxisLabel() {
		return "Y Data";
	}

	@Override
	protected String getPlotTitle() {
		return "Scatter Plot (DAQ Thread-Safety Test)";
	}

	@Override
	protected String getXAxisLabel() {
		return "X Data";
	}

	/**
	 * <p>
	 * Intentionally does nothing.
	 * </p>
	 *
	 * <p>
	 * In this example, data is not batch-loaded at startup.
	 * Instead, it is streamed asynchronously by background threads
	 * to mimic a live data acquisition system.
	 * </p>
	 */
	@Override
	public void fillData() {
		// no-op by design
	}

	/**
	 * Configure curve appearance, plot parameters, and lifecycle hooks
	 * that start and stop the data feeder threads.
	 */
	@Override
	public void setParameters() {
		Color fillColor = new Color(128, 0, 0, 96);

		PlotData plotData = _canvas.getPlotData();
		final Curve dc = (Curve) plotData.getFirstCurve();
		_curve = dc;

		dc.setCurveMethod(CurveDrawingMethod.POLYNOMIAL);
		dc.getStyle().setSymbolType(SymbolType.CIRCLE);
		dc.getStyle().setSymbolSize(4);
		dc.getStyle().setFillColor(fillColor);
		dc.getStyle().setBorderColor(null);
		dc.getStyle().setFitLineColor(Color.black);
		dc.getStyle().setFitLineWidth(2.0f);

		PlotParameters params = _canvas.getParameters();
		params.mustIncludeXZero(true);
		params.mustIncludeYZero(true);
		params.addPlotLine(new HorizontalLine(_canvas, 0));
		params.addPlotLine(new VerticalLine(_canvas, 0));
		params.setLegendDrawing(true);

		// Tie feeder lifecycle to window lifecycle
		addWindowListener(new WindowAdapter() {
			@Override
			public void windowOpened(WindowEvent e) {
				startFeeders();
			}

			@Override
			public void windowClosing(WindowEvent e) {
				stopFeeders();
			}

			@Override
			public void windowClosed(WindowEvent e) {
				stopFeeders();
			}
		});
	}

	/**
	 * Start background threads that continuously generate and feed
	 * random data points to the curve.
	 */
	private void startFeeders() {
		if (_curve == null) {
			return;
		}
		if (!_running.compareAndSet(false, true)) {
			return;
		}

		for (int i = 0; i < FEEDER_COUNT; i++) {
			final int id = i;
			Thread t = new Thread(() -> feederLoop(id), "ScatterFeeder-" + id);
			t.setDaemon(true);
			_feeders.add(t);
			t.start();
		}
	}

	/**
	 * Stop all active feeder threads.
	 */
	private void stopFeeders() {
		_running.set(false);
		for (Thread t : _feeders) {
			t.interrupt();
		}
		_feeders.clear();
	}

	/**
	 * Main loop for a single data feeder.
	 *
	 * <p>
	 * Each iteration generates one random (x,y) point and attempts
	 * to add it to the curve.
	 * </p>
	 *
	 * @param feederId identifier for debugging/logging
	 */
	private void feederLoop(int feederId) {
		ThreadLocalRandom rng = ThreadLocalRandom.current();

		while (_running.get() && !Thread.currentThread().isInterrupted()) {

			final double x = -0.5 + rng.nextDouble();
			final double y = x + 0.2 * (rng.nextDouble() - 0.5);

			if (ADD_ON_EDT) {
				// Safe mode: serialize updates on the EDT
				SwingUtilities.invokeLater(() -> {
					Curve c = _curve;
					if (c != null) {
						c.add(x, y);
					}
				});
			} else {
				// Stress mode: update directly from background thread
				Curve c = _curve;
				if (c != null) {
					c.add(x, y);
				}
			}

			try {
				Thread.sleep(FEED_PERIOD_MS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				break;
			}
		}
	}

	/**
	 * Launch the example.
	 */
	public static void main(String[] arg) {
		final Scatter example = new Scatter();
		SwingUtilities.invokeLater(() -> example.setVisible(true));
	}
}

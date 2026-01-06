package cnuphys.splot.example;

import java.awt.Color;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.SwingUtilities;
import javax.swing.Timer;

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
 * Scatter (DAQ simulation using lock-free queue + EDT drain). A Stress test.
 * </p>
 *
 * <p>
 * This example simulates a data acquisition system where one or more background
 * producer threads generate points asynchronously. Producers never touch Swing
 * or mutate the plot model directly. Instead they enqueue points into a
 * lock-free {@link ConcurrentLinkedQueue}.
 * </p>
 *
 * <p>
 * A Swing {@link Timer} (which executes on the EDT) periodically drains the queue
 * and calls {@link Curve#add(double, double)}. This ensures all plot model mutation
 * occurs on the EDT, while still allowing realistic asynchronous acquisition.
 * </p>
 */
@SuppressWarnings("serial")
public class Scatter extends AExample {

	// ----------------------------
	// DAQ / stress knobs
	// ----------------------------

	/** Number of background producer threads simulating independent acquisition sources. */
	private static final int PRODUCER_COUNT = 3;

	/** Producer period per point (ms). Smaller = more points. */
	private static final int PRODUCER_PERIOD_MS = 2;

	/**
	 * EDT drain period (ms). Smaller = lower latency but more EDT overhead.
	 * Typical values: 10–50 ms.
	 */
	private static final int DRAIN_PERIOD_MS = 20;

	/**
	 * Maximum number of points to drain per EDT tick.
	 * This prevents the EDT from getting stuck draining forever if producers outrun it.
	 */
	private static final int MAX_DRAIN_PER_TICK = 2000;

	// ----------------------------
	// State
	// ----------------------------

	private final AtomicBoolean _running = new AtomicBoolean(false);
	private final List<Thread> _producers = new ArrayList<>();

	/** Lock-free queue used to transfer points from producer threads to the EDT. */
	private final ConcurrentLinkedQueue<Point2D> _queue = new ConcurrentLinkedQueue<>();

	/** Swing timer that drains the queue on the EDT. */
	private Timer _drainTimer;

	/** The curve that receives points (mutated only on EDT). */
	private volatile Curve _curve;

	/** Simple immutable point record for queue transport. */
	private static final class Point2D {
		final double x;
		final double y;

		Point2D(double x, double y) {
			this.x = x;
			this.y = y;
		}
	}

	@Override
	protected PlotData createPlotData() throws PlotDataException {
		String[] curveNames = { "Data" };
		int[] fitOrders = { 1 };
		return new PlotData(PlotDataType.XYXY, curveNames, fitOrders);
	}

	@Override
	protected String getYAxisLabel() {
		return "Y Data";
	}

	@Override
	protected String getPlotTitle() {
		return "Scatter Plot (DAQ Queue → EDT Drain)";
	}

	@Override
	protected String getXAxisLabel() {
		return "X Data";
	}

	/**
	 * Intentionally does nothing. Data is streamed in asynchronously by producer threads.
	 */
	@Override
	public void fillData() {
		// no-op by design
	}

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

		addWindowListener(new WindowAdapter() {
			@Override
			public void windowOpened(WindowEvent e) {
				startDaq();
			}

			@Override
			public void windowClosing(WindowEvent e) {
				stopDaq();
			}

			@Override
			public void windowClosed(WindowEvent e) {
				stopDaq();
			}
		});
	}

	/**
	 * Start background producers and the EDT drain timer.
	 */
	private void startDaq() {
		if (_curve == null) {
			return;
		}
		if (!_running.compareAndSet(false, true)) {
			return;
		}

		// Start EDT drain timer first (EDT)
		SwingUtilities.invokeLater(() -> {
			if (_drainTimer != null) {
				_drainTimer.stop();
			}
			_drainTimer = new Timer(DRAIN_PERIOD_MS, e -> drainQueueOnEdt());
			_drainTimer.setCoalesce(true); // coalesce events if EDT is busy
			_drainTimer.start();
		});

		// Start producer threads
		for (int i = 0; i < PRODUCER_COUNT; i++) {
			final int id = i;
			Thread t = new Thread(() -> producerLoop(id), "ScatterProducer-" + id);
			t.setDaemon(true);
			_producers.add(t);
			t.start();
		}
	}

	/**
	 * Stop producers and stop the EDT drain timer.
	 */
	private void stopDaq() {
		_running.set(false);

		for (Thread t : _producers) {
			t.interrupt();
		}
		_producers.clear();

		SwingUtilities.invokeLater(() -> {
			if (_drainTimer != null) {
				_drainTimer.stop();
				_drainTimer = null;
			}
		});

		_queue.clear();
	}

	/**
	 * Background producer loop: generates points and offers them to the lock-free queue.
	 * Producers never touch Swing, never call curve.add, never repaint, etc.
	 */
	private void producerLoop(int producerId) {
		ThreadLocalRandom rng = ThreadLocalRandom.current();

		while (_running.get() && !Thread.currentThread().isInterrupted()) {
			double x = -0.5 + rng.nextDouble();
			double y = x + 0.2 * (rng.nextDouble() - 0.5);

			_queue.offer(new Point2D(x, y));

			try {
				Thread.sleep(PRODUCER_PERIOD_MS);
			} catch (InterruptedException ie) {
				Thread.currentThread().interrupt();
				break;
			}
		}
	}

	/**
	 * Drain the queue and add points to the curve.
	 *
	 * <p>
	 * This method must run on the EDT (it is called by a Swing Timer), so it is safe
	 * to mutate the plot model here.
	 * </p>
	 */
	private void drainQueueOnEdt() {
		// Defensive: ensure we are on EDT
		if (!SwingUtilities.isEventDispatchThread()) {
			SwingUtilities.invokeLater(this::drainQueueOnEdt);
			return;
		}

		Curve c = _curve;
		if (c == null) {
			return;
		}

		int drained = 0;
		Point2D p;

		while (drained < MAX_DRAIN_PER_TICK && (p = _queue.poll()) != null) {
			c.add(p.x, p.y);
			drained++;
		}

		// Optional: if you want faster catch-up when backlog is large,
		// you can drain more aggressively or temporarily shorten the timer interval.
	}

	public static void main(String[] arg) {
		final Scatter example = new Scatter();
		SwingUtilities.invokeLater(() -> example.setVisible(true));
	}
}

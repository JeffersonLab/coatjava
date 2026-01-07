package cnuphys.splot.example;

import java.awt.Color;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

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
 * <b>Scatter (DAQ enqueue stress test)</b>
 * </p>
 *
 * <p>
 * This example is intentionally designed as a <em>thread stress test</em> that simulates a
 * streaming data-acquisition (DAQ) system.
 * </p>
 *
 * <h2>What is being tested</h2>
 * <ul>
 *   <li>Many background producer threads concurrently call {@link Curve#enqueue(double, double)}.</li>
 *   <li>The EDT periodically drains queued points into the curve's data columns using
 *       {@link Curve#startPendingDrainTimer(int, int)} (which calls {@link Curve#drainPendingOnEDT(int)}).</li>
 *   <li>Rendering/fitting happens as usual on the EDT with a consistently-mutated model.</li>
 * </ul>
 *
 * <p>
 * This is the correct way to stress concurrency <b>without</b> reintroducing races between painting
 * and model mutation. The old “unsafe mode” (background threads calling {@code curve.add(...)})
 * is now prevented by EDT guards and should fail fast.
 * </p>
 *
 * <h2>Key design choice</h2>
 * <p>
 * {@link #fillData()} is intentionally a no-op. Data arrives over time via producers.
 * </p>
 *
 * <h2>Tuning knobs</h2>
 * <ul>
 *   <li>{@link #PRODUCER_COUNT} - number of concurrent DAQ sources</li>
 *   <li>{@link #PRODUCER_PERIOD_MS} - point production interval per source</li>
 *   <li>{@link #DRAIN_PERIOD_MS} - EDT drain frequency</li>
 *   <li>{@link #MAX_DRAIN_PER_TICK} - max points applied per drain tick</li>
 *   <li>{@link #MAX_POINTS} - max points retained in the curve (demonstrates stop enqueuing)</li>
 * </ul>
 */
@SuppressWarnings("serial")
public class Scatter extends AExample {

	// --------------------------------------------------------------------
	// Stress / DAQ knobs
	// --------------------------------------------------------------------

	/** Number of concurrent producer threads simulating independent acquisition sources. */
	private static final int PRODUCER_COUNT = 6;

	/** Per-producer delay between points (ms). Smaller means higher rate and more stress. */
	private static final int PRODUCER_PERIOD_MS = 1;

	/** How often the EDT drains queued points (ms). Typical: 10-50 ms. */
	private static final int DRAIN_PERIOD_MS = 20;

	/** Maximum number of queued points applied to the model per drain tick. */
	private static final int MAX_DRAIN_PER_TICK = 3000;
	
	/** Maximum number of points to retain in the curve (demonstrate stop enquinmg). */
	private static final int MAX_POINTS = 50000;


	// --------------------------------------------------------------------
	// State
	// --------------------------------------------------------------------

	private final AtomicBoolean running = new AtomicBoolean(false);
	private final List<Thread> producers = new ArrayList<>();
	private volatile Curve curve;
	private final AtomicInteger appliedCount = new AtomicInteger();


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
		return "Scatter Plot (DAQ thread-safety stress test)";
	}

	@Override
	protected String getXAxisLabel() {
		return "X Data";
	}

	/**
	 * No-op by design.
	 * <p>
	 * This example simulates streaming acquisition rather than batch initialization.
	 * Data arrives asynchronously via background producer threads.
	 * </p>
	 */
	@Override
	public void fillData() {
		// no-op
	}

	@Override
	public void setParameters() {
		Color fillColor = new Color(128, 0, 0, 96);

		PlotData plotData = canvas.getPlotData();
		final Curve dc = (Curve) plotData.getFirstCurve();
		curve = dc;

		dc.setCurveMethod(CurveDrawingMethod.POLYNOMIAL);
		dc.getStyle().setSymbolType(SymbolType.CIRCLE);
		dc.getStyle().setSymbolSize(4);
		dc.getStyle().setFillColor(fillColor);
		dc.getStyle().setBorderColor(null);
		dc.getStyle().setLineColor(Color.black);
		dc.getStyle().setLineWidth(2.0f);

		PlotParameters params = canvas.getParameters();
		params.mustIncludeXZero(true);
		params.mustIncludeYZero(true);
		params.addPlotLine(new HorizontalLine(canvas, 0));
		params.addPlotLine(new VerticalLine(canvas, 0));
		params.setLegendDrawing(true);

		// Lifecycle: start/stop DAQ threads with the window.
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
	 * Start the DAQ simulation:
	 * <ul>
	 *   <li>Start the curve's EDT drain timer.</li>
	 *   <li>Start background producers that continuously {@code add(x,y)}.</li>
	 * </ul>
	 */
	private void startDaq() {
		final Curve c = curve;
		if (c == null) {
			return;
		}
		if (!running.compareAndSet(false, true)) {
			return;
		}

		// Start EDT drain timer on EDT (best practice for Swing timer lifecycles).
		SwingUtilities.invokeLater(() ->
	    c.startPendingDrainTimer(DRAIN_PERIOD_MS, MAX_DRAIN_PER_TICK,
	        drained -> {
	            int total = appliedCount.addAndGet(drained);
	            if (total >= MAX_POINTS) {
	                stopDaq();
	            }
	        })
	);

		// Start producer threads (background)
		for (int i = 0; i < PRODUCER_COUNT; i++) {
			final int id = i;
			Thread t = new Thread(() -> producerLoop(id), "ScatterDAQ-" + id);
			t.setDaemon(true);
			producers.add(t);
			t.start();
		}
	}

	/**
	 * Stop DAQ simulation:
	 * <ul>
	 *   <li>Stop producers.</li>
	 *   <li>Stop drain timer.</li>
	 *   <li>Optionally clear pending queue.</li>
	 * </ul>
	 */
	private void stopDaq() {
		running.set(false);

		for (Thread t : producers) {
			t.interrupt();
		}
		producers.clear();

		final Curve c = curve;
		if (c != null) {
			SwingUtilities.invokeLater(() -> {
				c.stopPendingDrainTimer();
				// Optional: if you want “stop means stop immediately”, clear any queued points:
				// c.clearPending();
			});
		}
	}

	/**
	 * Background DAQ producer loop.
	 * <p>
	 * Generates random points and adds them. This intentionally creates contention on the
	 * lock-free queue and stresses the thread safety.
	 * </p>
	 *
	 * @param producerId producer identifier (debugging)
	 */
	private void producerLoop(int producerId) {
	    final ThreadLocalRandom rng = ThreadLocalRandom.current();
	    long startNs = System.nanoTime();
	    while (running.get() && !Thread.currentThread().isInterrupted()) {

	        double t = (System.nanoTime() - startNs) * 1e-9;   // seconds since start

	        // Slowly varying "true" model: y = m(t)*x + b(t)
	        double m = 0.2 + 1.6 * (0.5 + 0.5 * Math.sin(0.15 * t)); // ranges ~0.2..1.8
	        double b = 0.30 * Math.sin(0.07 * t);                   // small intercept drift

	        double x = -0.5 + rng.nextDouble();
	        double noise = 0.25 * (rng.nextDouble() - 0.5);         // bigger noise -> more motion
	        double y = m * x + b + noise;

	        final Curve c = curve;
	        if (c != null) {
	            c.add(x, y);
	        }

	        try {
	            Thread.sleep(PRODUCER_PERIOD_MS);
	        } catch (InterruptedException e) {
	            Thread.currentThread().interrupt();
	            break;
	        }
	    }
	}


	public static void main(String[] args) {
		javax.swing.SwingUtilities.invokeLater(() -> {
			final Scatter example = new Scatter();
			example.setVisible(true);
		});
	}

}

package cnuphys.splot.plot.interact;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

import cnuphys.splot.pdata.ACurve;
import cnuphys.splot.pdata.Curve;
import cnuphys.splot.pdata.HistoCurve;
import cnuphys.splot.pdata.HistoData;
import cnuphys.splot.plot.PlotCanvas2;
import cnuphys.splot.plot.render.PlotTransform;

/**
 * Pan/zoom + hover picking + legend click toggles.
 *
 * Controls:
 *  - Left drag: rubberband zoom (Shift locks aspect ratio)
 *  - Right drag: pan
 *  - Mouse wheel: zoom about cursor
 *  - Double-click: autoscale reset
 *
 * Keyboard:
 *  - R: autoscale reset
 *  - +/-: zoom in/out (about center)
 *  - Arrow keys: pan
 *  - Backspace: undo zoom
 *  - Shift+Backspace: redo zoom
 *  - C: toggle crosshair
 *  - X: toggle coordinate readout
 */
public class PanZoomController extends MouseAdapter implements MouseWheelListener {

	private final PlotCanvas2 canvas;
	private final PlotInteractionState state;
	private final ZoomStack zoomStack = new ZoomStack();

	private boolean panning;
	private Point lastPan = new Point();
	private Point zoomStart = new Point();

	public PanZoomController(PlotCanvas2 canvas) {
		this.canvas = canvas;
		this.state = canvas.getInteractionState();

		canvas.addMouseListener(this);
		canvas.addMouseMotionListener(this);
		canvas.addMouseWheelListener(this);

		installKeyBindings();
	}

	// ---------------- Key bindings ----------------

	private void installKeyBindings() {
		JComponent c = canvas;
		int cond = JComponent.WHEN_IN_FOCUSED_WINDOW;

		c.getInputMap(cond).put(KeyStroke.getKeyStroke('R'), "plot.reset");
		c.getActionMap().put("plot.reset", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				canvas.setAutoscale(true);
				state.clearRubberband();
				state.hover.clear();
				zoomStack.clear();
				canvas.repaint();
			}
		});

		c.getInputMap(cond).put(KeyStroke.getKeyStroke('='), "plot.zoomin");
		c.getInputMap(cond).put(KeyStroke.getKeyStroke('+'), "plot.zoomin");
		c.getActionMap().put("plot.zoomin", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				canvas.setAutoscale(false);
				pushSnapshotIfNeeded();
				zoomAboutCenter(1.0 / 1.15);
				canvas.repaint();
			}
		});

		c.getInputMap(cond).put(KeyStroke.getKeyStroke('-'), "plot.zoomout");
		c.getActionMap().put("plot.zoomout", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				canvas.setAutoscale(false);
				pushSnapshotIfNeeded();
				zoomAboutCenter(1.15);
				canvas.repaint();
			}
		});

		c.getInputMap(cond).put(KeyStroke.getKeyStroke("LEFT"), "plot.panleft");
		c.getInputMap(cond).put(KeyStroke.getKeyStroke("RIGHT"), "plot.panright");
		c.getInputMap(cond).put(KeyStroke.getKeyStroke("UP"), "plot.panup");
		c.getInputMap(cond).put(KeyStroke.getKeyStroke("DOWN"), "plot.pandown");

		c.getActionMap().put("plot.panleft", new PanAction(-1, 0));
		c.getActionMap().put("plot.panright", new PanAction(1, 0));
		c.getActionMap().put("plot.panup", new PanAction(0, 1));
		c.getActionMap().put("plot.pandown", new PanAction(0, -1));

		c.getInputMap(cond).put(KeyStroke.getKeyStroke("BACK_SPACE"), "plot.undo");
		c.getActionMap().put("plot.undo", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				Rectangle2D w = zoomStack.undo();
				if (w != null) {
					canvas.setAutoscale(false);
					canvas.setWorldRect(w);
					canvas.repaint();
				} else {
					Toolkit.getDefaultToolkit().beep();
				}
			}
		});

		c.getInputMap(cond).put(KeyStroke.getKeyStroke("shift BACK_SPACE"), "plot.redo");
		c.getActionMap().put("plot.redo", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				Rectangle2D w = zoomStack.redo();
				if (w != null) {
					canvas.setAutoscale(false);
					canvas.setWorldRect(w);
					canvas.repaint();
				} else {
					Toolkit.getDefaultToolkit().beep();
				}
			}
		});

		c.getInputMap(cond).put(KeyStroke.getKeyStroke('C'), "plot.toggleCrosshair");
		c.getActionMap().put("plot.toggleCrosshair", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				state.showCrosshair = !state.showCrosshair;
				canvas.repaint();
			}
		});

		c.getInputMap(cond).put(KeyStroke.getKeyStroke('X'), "plot.toggleCoords");
		c.getActionMap().put("plot.toggleCoords", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				state.showCoords = !state.showCoords;
				canvas.repaint();
			}
		});
	}

	@SuppressWarnings("serial")
	private final class PanAction extends AbstractAction {
		private final int dx;
		private final int dy;

		PanAction(int dx, int dy) {
			this.dx = dx;
			this.dy = dy;
		}

		@Override
		public void actionPerformed(ActionEvent e) {
			canvas.setAutoscale(false);
			pushSnapshotIfNeeded();

			Rectangle2D w = canvas.getWorldRect();
			double frac = 0.05; // 5% per keypress
			double ddx = dx * frac * w.getWidth();
			double ddy = dy * frac * w.getHeight();

			canvas.setWorldRect(new Rectangle2D.Double(w.getX() + ddx, w.getY() + ddy, w.getWidth(), w.getHeight()));
			zoomStack.push(canvas.getWorldRect());
			canvas.repaint();
		}
	}

	// ---------------- Mouse ----------------

	@Override
	public void mouseEntered(MouseEvent e) {
		state.mouseInside = true;
		updateMouse(e);
		canvas.repaint();
	}

	@Override
	public void mouseExited(MouseEvent e) {
		state.mouseInside = false;
		state.clearRubberband();
		state.hover.clear();
		canvas.repaint();
	}

	@Override
	public void mouseMoved(MouseEvent e) {
		updateMouse(e);
		updateHoverPick();
		canvas.repaint();
	}

	@Override
	public void mouseClicked(MouseEvent e) {
		// Legend click toggles visibility (left click)
		if (SwingUtilities.isLeftMouseButton(e)) {
			for (PlotInteractionState.LegendEntry le : state.legendEntries) {
				if (le != null && le.curve != null && le.bounds.contains(e.getPoint())) {
					le.curve.setVisible(!le.curve.isVisible());
					canvas.repaint();
					return;
				}
			}
		}
	}

	@Override
	public void mousePressed(MouseEvent e) {
		updateMouse(e);

		if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
			canvas.setAutoscale(true);
			state.clearRubberband();
			state.hover.clear();
			panning = false;
			zoomStack.clear();
			return;
		}

		if (SwingUtilities.isRightMouseButton(e)) {
			panning = true;
			lastPan.setLocation(e.getPoint());
			canvas.setAutoscale(false);
			pushSnapshotIfNeeded();
			state.clearRubberband();
			return;
		}

		if (SwingUtilities.isLeftMouseButton(e)) {
			canvas.setAutoscale(false);
			pushSnapshotIfNeeded();
			state.rubberbanding = true;
			zoomStart.setLocation(e.getPoint());
			state.rubberband.setBounds(zoomStart.x, zoomStart.y, 0, 0);
		}
	}

	@Override
	public void mouseDragged(MouseEvent e) {
		updateMouse(e);

		if (panning) {
			doPan(e.getPoint());
			lastPan.setLocation(e.getPoint());
			canvas.repaint();
			return;
		}

		if (state.rubberbanding) {
			updateRubberband(e.getPoint(), e.isShiftDown());
			canvas.repaint();
		}
	}

	@Override
	public void mouseReleased(MouseEvent e) {
		updateMouse(e);

		if (panning && SwingUtilities.isRightMouseButton(e)) {
			panning = false;
			zoomStack.push(canvas.getWorldRect());
			return;
		}

		if (state.rubberbanding && SwingUtilities.isLeftMouseButton(e)) {
			applyRubberbandZoom();
			state.clearRubberband();
			zoomStack.push(canvas.getWorldRect());
			canvas.repaint();
		}
	}

	@Override
	public void mouseWheelMoved(MouseWheelEvent e) {
		updateMouse(e);

		Rectangle active = canvas.getActiveScreenRect();
		if (!active.contains(e.getPoint())) {
			return;
		}

		canvas.setAutoscale(false);
		pushSnapshotIfNeeded();

		int rot = e.getWheelRotation();
		double factor = Math.pow(1.15, rot);

		PlotTransform tx = canvas.currentTransform();
		Point2D anchor = canvas.screenToWorld(tx, e.getX(), e.getY());
		zoomAbout(anchor.getX(), anchor.getY(), factor);

		zoomStack.push(canvas.getWorldRect());
		canvas.repaint();
	}

	// ---------------- Helpers ----------------

	private void updateMouse(MouseEvent e) {
		state.mouseInside = true;
		state.mouse.setLocation(e.getPoint());
	}

	private void pushSnapshotIfNeeded() {
		zoomStack.ensureSeed(canvas.getWorldRect());
		Rectangle2D cur = canvas.getWorldRect();
		Rectangle2D top = zoomStack.current();
		if (top == null || !approximatelyEqual(cur, top)) {
			zoomStack.push(cur);
		}
	}

	private boolean approximatelyEqual(Rectangle2D a, Rectangle2D b) {
		if (a == null || b == null) return false;
		double eps = 1e-12;
		return Math.abs(a.getX() - b.getX()) < eps
				&& Math.abs(a.getY() - b.getY()) < eps
				&& Math.abs(a.getWidth() - b.getWidth()) < eps
				&& Math.abs(a.getHeight() - b.getHeight()) < eps;
	}

	private void updateRubberband(Point current, boolean lockAspect) {
		int x0 = zoomStart.x;
		int y0 = zoomStart.y;
		int x1 = current.x;
		int y1 = current.y;

		int rx = Math.min(x0, x1);
		int ry = Math.min(y0, y1);
		int rw = Math.abs(x1 - x0);
		int rh = Math.abs(y1 - y0);

		if (lockAspect) {
			Rectangle a = canvas.getActiveScreenRect();
			double aspect = a.getWidth() / Math.max(1.0, a.getHeight());

			// adjust rw/rh to match aspect, preserving drag direction
			if (rh == 0) rh = 1;
			double curAspect = rw / (double) rh;

			if (curAspect > aspect) {
				// too wide -> increase height
				rh = (int) Math.round(rw / aspect);
			} else {
				// too tall -> increase width
				rw = (int) Math.round(rh * aspect);
			}

			// rebuild origin anchored at zoomStart with correct quadrant
			rx = (x1 >= x0) ? x0 : (x0 - rw);
			ry = (y1 >= y0) ? y0 : (y0 - rh);
		}

		state.rubberband.setBounds(rx, ry, rw, rh);
	}

	private void applyRubberbandZoom() {
		Rectangle rb = new Rectangle(state.rubberband);
		Rectangle active = canvas.getActiveScreenRect();
		Rectangle z = rb.intersection(active);

		if (z.width < 4 || z.height < 4) {
			return;
		}

		PlotTransform tx = canvas.currentTransform();

		Point2D w0 = canvas.screenToWorld(tx, z.x, z.y + z.height);
		Point2D w1 = canvas.screenToWorld(tx, z.x + z.width, z.y);

		double xmin = Math.min(w0.getX(), w1.getX());
		double xmax = Math.max(w0.getX(), w1.getX());
		double ymin = Math.min(w0.getY(), w1.getY());
		double ymax = Math.max(w0.getY(), w1.getY());

		canvas.setWorldRect(new Rectangle2D.Double(xmin, ymin,
				Math.max(1e-12, xmax - xmin),
				Math.max(1e-12, ymax - ymin)));
	}

	private void doPan(Point current) {
		PlotTransform tx = canvas.currentTransform();

		Point2D wLast = canvas.screenToWorld(tx, lastPan.x, lastPan.y);
		Point2D wNow = canvas.screenToWorld(tx, current.x, current.y);

		double dx = wLast.getX() - wNow.getX();
		double dy = wLast.getY() - wNow.getY();

		Rectangle2D w = canvas.getWorldRect();
		canvas.setWorldRect(new Rectangle2D.Double(w.getX() + dx, w.getY() + dy, w.getWidth(), w.getHeight()));
	}

	private void zoomAboutCenter(double factor) {
		Rectangle2D w = canvas.getWorldRect();
		zoomAbout(w.getCenterX(), w.getCenterY(), factor);
	}

	private void zoomAbout(double ax, double ay, double factor) {
		Rectangle2D w = canvas.getWorldRect();

		double xmin = w.getMinX();
		double xmax = w.getMaxX();
		double ymin = w.getMinY();
		double ymax = w.getMaxY();

		double nxmin = ax + (xmin - ax) * factor;
		double nxmax = ax + (xmax - ax) * factor;
		double nymin = ay + (ymin - ay) * factor;
		double nymax = ay + (ymax - ay) * factor;

		double newW = Math.max(1e-12, nxmax - nxmin);
		double newH = Math.max(1e-12, nymax - nymin);

		canvas.setWorldRect(new Rectangle2D.Double(nxmin, nymin, newW, newH));
	}

	// ---------------- Hover picking ----------------

	private void updateHoverPick() {

		Rectangle a = canvas.getActiveScreenRect();
		Point m = state.mouse;

		if (!state.mouseInside || !a.contains(m)) {
			state.hover.clear();
			return;
		}

		PlotTransform tx = canvas.currentTransform();
		int bestD2 = Integer.MAX_VALUE;
		ACurve bestCurve = null;
		int bestIndex = -1;
		int bestX = 0, bestY = 0;
		double bestWX = 0, bestWY = 0;
		String bestLabel = null;

		final int maxRadius = 10; // pixels
		final int maxD2 = maxRadius * maxRadius;

		for (ACurve c : canvas.getModel().curves()) {
			if (c == null || !c.isVisible() || c.length() < 1) {
				continue;
			}

			if (c instanceof Curve) {
				Curve xy = (Curve) c;
				int n = xy.length();
				int stride = Math.max(1, n / 5000);

				for (int i = 0; i < n; i += stride) {
					double x = xy.xData().get(i);
					double y = xy.yData().get(i);
					if (!Double.isFinite(x) || !Double.isFinite(y)) continue;

					int xs = canvas.worldToScreenX(tx, x);
					int ys = canvas.worldToScreenY(tx, y);
					int dx = xs - m.x;
					int dy = ys - m.y;
					int d2 = dx * dx + dy * dy;

					if (d2 < bestD2 && d2 <= maxD2) {
						bestD2 = d2;
						bestCurve = c;
						bestIndex = i;
						bestX = xs; bestY = ys;
						bestWX = x; bestWY = y;
						bestLabel = c.getName() + "  (" +
								canvas.formatTick(x, canvas.getTicks().xSpacing) + ", " +
								canvas.formatTick(y, canvas.getTicks().ySpacing) + ")";
					}
				}
			}
			else if (c instanceof HistoCurve) {
				HistoData hd = ((HistoCurve) c).getHistoData();
				if (hd == null || hd.getNumberBins() < 1) continue;

				int n = hd.getNumberBins();
				double[] grid = hd.getGridCopy();
				long[] counts = hd.getCounts();
				int stride = Math.max(1, n / 5000);

				for (int i = 0; i < n; i += stride) {
					double x = 0.5 * (grid[i] + grid[i + 1]);
					double y = counts[i];

					int xs = canvas.worldToScreenX(tx, x);
					int ys = canvas.worldToScreenY(tx, y);
					int dx = xs - m.x;
					int dy = ys - m.y;
					int d2 = dx * dx + dy * dy;

					if (d2 < bestD2 && d2 <= maxD2) {
						bestD2 = d2;
						bestCurve = c;
						bestIndex = i;
						bestX = xs; bestY = ys;
						bestWX = x; bestWY = y;
						bestLabel = c.getName() + "  bin " + i + "  x=" +
								canvas.formatTick(x, canvas.getTicks().xSpacing) + "  y=" +
								canvas.formatTick(y, canvas.getTicks().ySpacing);
					}
				}
			}
		}

		if (bestCurve == null) {
			state.hover.clear();
			return;
		}

		state.hover.curve = bestCurve;
		state.hover.index = bestIndex;
		state.hover.screen.setLocation(bestX, bestY);
		state.hover.world.setLocation(bestWX, bestWY);
		state.hover.label = bestLabel;
	}
}

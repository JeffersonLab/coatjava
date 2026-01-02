package cnuphys.splot.plot;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

import javax.swing.JComponent;

import cnuphys.splot.pdata.ACurve;
import cnuphys.splot.pdata.Curve;
import cnuphys.splot.pdata.HistoCurve;
import cnuphys.splot.plot.interact.PlotInteractionState;
import cnuphys.splot.plot.interact.ZoomStack;
import cnuphys.splot.plot.model.IPlotModel;
import cnuphys.splot.plot.model.IPlotModelListener;
import cnuphys.splot.plot.model.PlotModelEvent;
import cnuphys.splot.plot.render.AxesAndTicksRenderer;
import cnuphys.splot.plot.render.BackgroundRenderer;
import cnuphys.splot.plot.render.CurvesRenderer;
import cnuphys.splot.plot.render.GridRenderer;
import cnuphys.splot.plot.render.LegendRenderer3;
import cnuphys.splot.plot.render.OverlayRenderer;
import cnuphys.splot.plot.render.PlotRenderPipeline;
import cnuphys.splot.plot.render.PlotTransform;
import cnuphys.splot.plot.render.IPlotRenderer;

/**
 * New architecture plot canvas:
 * - consumes an {@link IPlotModel} (curves)
 * - builds transforms from world bounds
 * - runs a render pipeline
 * - event-driven repaint/autoscale (no polling timer)
 */
@SuppressWarnings("serial")
public class PlotCanvas2 extends JComponent implements IPlotModelListener {

	private final IPlotModel model;
	private final PlotParameters2 params;

	private Rectangle active = new Rectangle(0, 0, 1, 1);
	private Rectangle2D.Double world = new Rectangle2D.Double(0, 0, 1, 1);

	private PlotTicks2 ticks = new PlotTicks2(new double[0], new double[0]);

	private final PlotRenderPipeline pipeline = new PlotRenderPipeline();

	// Interaction state used by OverlayRenderer and controllers (optional).
	private PlotInteractionState interactionState = new PlotInteractionState();

	public PlotCanvas2(IPlotModel model, PlotParameters2 params) {
		this.model = model;
		this.params = (params == null) ? new PlotParameters2() : params;

		if (this.model != null) {
			this.model.addPlotModelListener(this);
		}

		// default pipeline
		pipeline.add(new BackgroundRenderer())
				.add(new GridRenderer())
				.add(new AxesAndTicksRenderer())
				.add(new CurvesRenderer())
				.add(new LegendRenderer3())
				.add(new OverlayRenderer());

		setOpaque(true);
	}

	public IPlotModel getModel() {
		return model;
	}

	public PlotParameters2 getParams() {
		return params;
	}

	public PlotTicks2 getTicks() {
		return ticks;
	}

	/** Add an extra renderer stage (typically before overlays). */
	public void addRenderer(IPlotRenderer r) {
		pipeline.add(r);
	}

	public PlotInteractionState getInteractionState() {
		return interactionState;
	}

	public void setInteractionState(PlotInteractionState state) {
		this.interactionState = (state == null) ? new PlotInteractionState() : state;
	}

	public Rectangle getActiveScreenRect() {
		return new Rectangle(active);
	}

	public Rectangle2D getWorldRect() {
		return (Rectangle2D) world.clone();
	}

	public void setAutoscale(boolean autoscale) {
		params.autoscale = autoscale;
		repaint();
	}

	public PlotTransform currentTransform() {
		AffineTransform w2s = buildWorldToScreen(active, world);
		AffineTransform s2w;
		try {
			s2w = w2s.createInverse();
		} catch (NoninvertibleTransformException e) {
			s2w = new AffineTransform();
		}
		return new PlotTransform(new Rectangle(active), (Rectangle2D) world.clone(), w2s, s2w);
	}

	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);

		recomputeActiveBounds();

		if (params.autoscale) {
			recomputeWorldFromData();
		}

		// ticks from world
		ticks = PlotTicks2.fromWorld(world, params.targetXTicks, params.targetYTicks);

		Graphics2D g2 = (Graphics2D) g.create();
		try {
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			PlotTransform tx = currentTransform();
			pipeline.renderAll(g2, this, tx);
		} finally {
			g2.dispose();
		}
	}

	public void pushWorldTo(ZoomStack zs) {
		if (zs != null) {
			zs.push(getWorldRect());
		}
	}

	private void recomputeActiveBounds() {
		int w = Math.max(1, getWidth());
		int h = Math.max(1, getHeight());

		int x = params.leftMargin;
		int y = params.topMargin;
		int aw = Math.max(1, w - params.leftMargin - params.rightMargin);
		int ah = Math.max(1, h - params.topMargin - params.bottomMargin);

		active.setBounds(x, y, aw, ah);
	}

	/**
	 * Autoscale world bounds based on visible curves.
	 * Includes small padding and optional inclusion of 0.
	 */
	private void recomputeWorldFromData() {

		Rectangle2D suggested = (model == null) ? null : model.getSuggestedWorld();
		if (suggested != null) {
			setWorldRect(suggested);
			return;
		}

		double xmin = Double.POSITIVE_INFINITY;
		double xmax = Double.NEGATIVE_INFINITY;
		double ymin = Double.POSITIVE_INFINITY;
		double ymax = Double.NEGATIVE_INFINITY;

		if (model != null) {
			for (ACurve c : model.curves()) {
				if (c == null || !c.isVisible() || c.length() < 1) {
					continue;
				}

				if (c instanceof Curve) {
					Curve xy = (Curve) c;
					int n = xy.length();
					for (int i = 0; i < n; i++) {
						double x = xy.xData().get(i);
						double y = xy.yData().get(i);
						if (!Double.isFinite(x) || !Double.isFinite(y)) {
							continue;
						}
						xmin = Math.min(xmin, x);
						xmax = Math.max(xmax, x);
						ymin = Math.min(ymin, y);
						ymax = Math.max(ymax, y);
					}
				}
				else if (c instanceof HistoCurve) {
					HistoCurve hc = (HistoCurve) c;
					xmin = Math.min(xmin, hc.getHistoData().getMinX());
					xmax = Math.max(xmax, hc.getHistoData().getMaxX());
					ymin = Math.min(ymin, hc.getHistoData().getMinY());
					ymax = Math.max(ymax, hc.getHistoData().getMaxY());
				}
			}
		}

		if (!Double.isFinite(xmin) || !Double.isFinite(xmax) || xmin == xmax) {
			xmin = 0;
			xmax = 1;
		}
		if (!Double.isFinite(ymin) || !Double.isFinite(ymax) || ymin == ymax) {
			ymin = 0;
			ymax = 1;
		}

		if (params.includeXZero) {
			xmin = Math.min(xmin, 0);
			xmax = Math.max(xmax, 0);
		}
		if (params.includeYZero) {
			ymin = Math.min(ymin, 0);
			ymax = Math.max(ymax, 0);
		}

		// padding
		double dx = xmax - xmin;
		double dy = ymax - ymin;
		double px = params.autoPadFraction * dx;
		double py = params.autoPadFraction * dy;

		setWorldRect(new Rectangle2D.Double(xmin - px, ymin - py, (xmax - xmin) + 2 * px, (ymax - ymin) + 2 * py));
	}

	public void setWorldRect(Rectangle2D r) {
		if (r == null) {
			return;
		}
		double w = Math.max(1e-12, r.getWidth());
		double h = Math.max(1e-12, r.getHeight());
		world.setRect(r.getX(), r.getY(), w, h);
	}

	// --------------------------
	// Transforms / helpers
	// --------------------------

	private static AffineTransform buildWorldToScreen(Rectangle active, Rectangle2D world) {
		// Map world (xmin..xmax, ymin..ymax) to screen with y inverted.
		double sx = active.getWidth() / world.getWidth();
		double sy = active.getHeight() / world.getHeight();

		AffineTransform at = new AffineTransform();
		at.translate(active.getX(), active.getY() + active.getHeight());
		at.scale(sx, -sy);
		at.translate(-world.getX(), -world.getY());
		return at;
	}

	public int worldToScreenX(PlotTransform tx, double x) {
		Point2D p = tx.worldToScreen.transform(new Point2D.Double(x, 0), null);
		return (int) Math.round(p.getX());
	}

	public int worldToScreenY(PlotTransform tx, double y) {
		Point2D p = tx.worldToScreen.transform(new Point2D.Double(0, y), null);
		return (int) Math.round(p.getY());
	}

	public Point2D screenToWorld(PlotTransform tx, int xs, int ys) {
		return tx.screenToWorld.transform(new Point2D.Double(xs, ys), null);
	}

	// Replace ONLY the formatTick method in PlotCanvas2 with this overload-set:

		public String formatTick(double v) {
			// backward-compatible default
			return formatTick(v, 0.0);
		}

		/**
		 * Format a tick value using spacing to choose decimals vs scientific.
		 *
		 * @param v        value
		 * @param spacing  tick spacing (0 means "unknown")
		 * @return formatted string
		 */
		public String formatTick(double v, double spacing) {

			if (!Double.isFinite(v)) {
				return "NaN";
			}

			double av = Math.abs(v);
			if (av == 0) {
				return "0";
			}

			// Switch to scientific notation for very large/small values.
			if (av >= 1e6 || av < 1e-5) {
				return String.format("%.3g", v);
			}

			int decimals = 4;
			if (Double.isFinite(spacing) && spacing > 0) {
				// Choose decimals based on spacing magnitude.
				// Example: spacing=0.1 -> 2 decimals; spacing=0.01 -> 3 decimals; spacing=10 -> 0 decimals.
				double lg = Math.log10(spacing);
				decimals = (int) Math.max(0, Math.min(8, Math.ceil(-lg) + 1));
			}

			String s = String.format("%." + decimals + "f", v);

			// trim trailing zeros
			s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
			return s;
		}

	// --------------------------
	// Model listener
	// --------------------------

	@Override
	public void plotModelChanged(PlotModelEvent evt) {
		repaint();
	}
}

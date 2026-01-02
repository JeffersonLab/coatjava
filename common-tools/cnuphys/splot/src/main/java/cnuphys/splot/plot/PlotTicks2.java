package cnuphys.splot.plot;

import java.awt.geom.Rectangle2D;

/**
 * Tick computation using {@link NiceScale}.
 * Stores tick arrays AND the tick spacing (used for formatting).
 */
public class PlotTicks2 {

	public final double[] xTicks;
	public final double[] yTicks;

	public final double xSpacing;
	public final double ySpacing;
	
	public PlotTicks2(double[] xTicks, double[] yTicks) {
	    this(xTicks, yTicks, 1.0, 1.0);
	}


	public PlotTicks2(double[] xTicks, double[] yTicks, double xSpacing, double ySpacing) {
		this.xTicks = (xTicks == null) ? new double[0] : xTicks;
		this.yTicks = (yTicks == null) ? new double[0] : yTicks;
		this.xSpacing = xSpacing;
		this.ySpacing = ySpacing;
	}

	public static PlotTicks2 fromWorld(Rectangle2D world, int xTarget, int yTarget) {
		if (world == null) {
			return new PlotTicks2(new double[0], new double[0], 1.0, 1.0);
		}

		NiceScale nx = new NiceScale(world.getMinX(), world.getMaxX(), Math.max(2, xTarget), false);
		NiceScale ny = new NiceScale(world.getMinY(), world.getMaxY(), Math.max(2, yTarget), false);

		return new PlotTicks2(nx.getTicks(), ny.getTicks(), nx.getTickSpacing(), ny.getTickSpacing());
	}
}

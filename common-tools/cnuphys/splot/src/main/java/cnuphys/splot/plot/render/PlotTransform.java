package cnuphys.splot.plot.render;

import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;

/**
 * Immutable-ish bundle of geometry for a paint pass:
 * active screen rectangle + world rectangle + transforms.
 */
public final class PlotTransform {

	public final Rectangle active;
	public final Rectangle2D world;

	public final AffineTransform worldToScreen;
	public final AffineTransform screenToWorld;

	public PlotTransform(Rectangle active, Rectangle2D world,
			AffineTransform worldToScreen, AffineTransform screenToWorld) {
		this.active = active;
		this.world = world;
		this.worldToScreen = worldToScreen;
		this.screenToWorld = screenToWorld;
	}
}

package cnuphys.splot.plot.interact;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

import cnuphys.splot.pdata.ACurve;

/**
 * Small mutable interaction state shared between controllers and overlay renderers.
 * Keeps PlotCanvas2 clean and testable.
 */
public class PlotInteractionState {

	/** Legend entry bounds (screen rects) for hit testing. */
	public static final class LegendEntry {
		public final Rectangle bounds = new Rectangle();
		public ACurve curve;
		public String label;
	}

	/** Hover information for nearest point/bin. */
	public static final class HoverInfo {
		public ACurve curve;
		public int index = -1;
		public final Point screen = new Point();
		public final Point2D.Double world = new Point2D.Double();
		public String label;

		public void clear() {
			curve = null;
			index = -1;
			screen.setLocation(0, 0);
			world.setLocation(0, 0);
			label = null;
		}
	}

	/** true if the mouse is currently inside the component */
	public boolean mouseInside;

	/** last mouse position in screen pixels */
	public final Point mouse = new Point();

	/** draw crosshair lines when mouseInside */
	public boolean showCrosshair = true;

	/** draw world coords near mouse */
	public boolean showCoords = true;

	/** rubberbanding state */
	public boolean rubberbanding;

	/** rubberband rectangle in screen coords (normalized to positive width/height) */
	public final Rectangle rubberband = new Rectangle();

	/** legend entry cache (updated by LegendRenderer3 each paint) */
	public final List<LegendEntry> legendEntries = new ArrayList<>();

	/** hover info (updated by controller) */
	public final HoverInfo hover = new HoverInfo();

	/** Clear rubberband state. */
	public void clearRubberband() {
		rubberbanding = false;
		rubberband.setBounds(0, 0, 0, 0);
	}
}

package cnuphys.splot.plot.interact;

import java.awt.geom.Rectangle2D;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Simple zoom history for world rectangles.
 * Supports push, undo, redo.
 */
public class ZoomStack {

	private final Deque<Rectangle2D> back = new ArrayDeque<>();
	private final Deque<Rectangle2D> forward = new ArrayDeque<>();

	/** Clear history. */
	public void clear() {
		back.clear();
		forward.clear();
	}

	/**
	 * Push a snapshot. Clears redo history.
	 * Stores a clone so callers can mutate their rectangles safely.
	 */
	public void push(Rectangle2D world) {
		if (world == null) {
			return;
		}
		back.push((Rectangle2D) world.clone());
		forward.clear();
	}

	public boolean canUndo() {
		return back.size() > 1;
	}

	public boolean canRedo() {
		return !forward.isEmpty();
	}

	/**
	 * Undo one step: move current to forward and return new current.
	 *
	 * @return the new current world, or null if cannot undo
	 */
	public Rectangle2D undo() {
		if (!canUndo()) {
			return null;
		}
		Rectangle2D current = back.pop();
		forward.push(current);
		return (Rectangle2D) back.peek().clone();
	}

	/**
	 * Redo one step: move from forward to back and return it.
	 *
	 * @return redone world, or null if cannot redo
	 */
	public Rectangle2D redo() {
		if (!canRedo()) {
			return null;
		}
		Rectangle2D nxt = forward.pop();
		back.push(nxt);
		return (Rectangle2D) nxt.clone();
	}

	/** Ensure there is at least one snapshot (useful on first interaction). */
	public void ensureSeed(Rectangle2D world) {
		if (back.isEmpty() && world != null) {
			back.push((Rectangle2D) world.clone());
		}
	}

	/** Current world snapshot (top of stack), or null. */
	public Rectangle2D current() {
		return back.isEmpty() ? null : (Rectangle2D) back.peek().clone();
	}
}

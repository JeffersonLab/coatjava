package cnuphys.splot.pdata;

/**
 * A snapshot of data points.
 * This is to avoid concurrent modification issues.
 * @author heddle
 *
 */
public final class Snapshot {
	public final double[] x;
	public final double[] y;

	/**
	 * Create a snapshot. Presumably the object
	 * creating the snapshot has copied the data
	 * and is locking the backing data during the copy.
	 * @param x the x data
	 * @param y the y data
	 */
	public Snapshot(double[] x, double[] y) {
		this.x = x;
		this.y = y;
	}

	/**
	 * Get the number of data points in the snapshot.
	 * @return the number of data points in the snapshot.
	 */
	public int length() {
		return x.length;
	}

}

package cnuphys.splot.pdata;

/**
 * A snapshot of data points.
 * This is to avoid concurrent modification issues.
 * @author heddle
 *
 */
public final class Snapshot {
	
	/** The x data */
	public final double[] x;
	
	/** The y data */
	public final double[] y;
	
	/** The error data (sigmaY) */
	public final double[] e;

	/**
	 * Create a snapshot. Presumably the object
	 * creating the snapshot has copied the data
	 * and is locking the backing data during the copy.
	 * @param x the x data
	 * @param y the y data
	 * @param e the error data (sigmaY) may be null
	 */
	public Snapshot(double[] x, double[] y, double [] e) {
		this.x = x;
		this.y = y;
		this.e = e;
	}

	/**
	 * Get the number of data points in the snapshot.
	 * @return the number of data points in the snapshot.
	 */
	public int length() {
		return x.length;
	}

}

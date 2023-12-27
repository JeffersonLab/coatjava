package cnuphys.adaptiveSwim.geometry;


/**
 * An INFINITE cylinder is defined by a centerline and a radius
 * @author heddle
 *
 */
public class Cylinder {

	//the centerline
	private Line _centerLine;
	
	//the radius
	public double radius;
	
	/**
	 * Create a cylinder
	 * @param centerLine the center line
	 * @param radius the radius
	 */
	public Cylinder(Line centerLine, double radius) {
		_centerLine = new Line(centerLine);
		this.radius = radius;
	}
	
	/**
	 * Create a cylinder
	 * @param p1 one point of center line as an xyz array
	 * @param p2 another point of center line as an xyz array
	 * @param radius
	 */
	public Cylinder(double[] p1, double[] p2, double radius) {
		this(new Line(p1, p2), radius);
	}


	/**
	 * Get the shortest distance between the surface of this infinite cylinder and a point.
	 * If the value is negative, we are inside the cylinder.
	 * @param p a point
	 * @return the perpendicular distance
	 */
	public double signedDistance(Point p) {
		double lineDist = _centerLine.distance(p);
		return lineDist - radius;
	}
	
    /**
	 * Set the path length of the swim
     * @deprecated Use {@link Cylinder#signedDistance} instead.
	 * @param p a point
	 * @return the perpendicular distance
     */
	public double distance(Point p) {
		double lineDist = _centerLine.distance(p);
		return lineDist - radius;
	}
	
	/**
	 * Get the shortest distance between the surface of this infinite cylinder and a point.
	 * If the value is negative, we are inside the cylinder.
	 * @param x the x coordinate
	 * @param y the y coordinate
	 * @param z the z coordinate
	 * @return the perpendicular distance
	 */
	public double signedDistance(double x, double y, double z) {
		Point p = new Point(x, y, z);
		return signedDistance(p);
	}
	
	/**
	 * Get the shortest distance between the surface of this infinite cylinder and a point.
	 * If the value is negative, we are inside the cylinder.
	 * @deprecated Use {@link Cylinder#signedDistance} instead.
	 * @param x the x coordinate
	 * @param y the y coordinate
	 * @param z the z coordinate
	 * @return the perpendicular distance
	 */
	public double distance(double x, double y, double z) {
		Point p = new Point(x, y, z);
		return distance(p);
	}
	
	/**
	 * Is the point inside the cylinder?
	 * @param x the x coordinate
	 * @param y the y coordinate		
	 * @param z the z coordinate
	 * @return <code>true</code> if the point is inside the cylinder.
	 */
	public boolean isInside(double x, double y, double z) {
		return signedDistance(x, y, z) < 0;
	}

}

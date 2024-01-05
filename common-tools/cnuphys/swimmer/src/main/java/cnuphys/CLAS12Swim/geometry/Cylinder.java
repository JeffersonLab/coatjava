package cnuphys.CLAS12Swim.geometry;


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
	 * Get the shortest absolute distance between the surface of this infinite cylinder and a point.
	 * @param x the x coordinate
	 * @param y the y coordinate
	 * @param z the z coordinate
	 * @return the perpendicular distance
	 */
	public double distance(double x, double y, double z) {
		Point p = new Point(x, y, z);
		return Math.abs(signedDistance(p));
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
	
	/**
	 * Is the cylinder centered on the z axis?
	 * @return <code>true</code> if the cylinder is centered on the z axis.
	 */
	public boolean centeredOnZ() {
		double x0 = _centerLine.getP0().x;
		double y0 = _centerLine.getP0().y;
		double x1 = _centerLine.getP1().x;
		double y1 = _centerLine.getP1().y;
		return (x0 == 0) && (y0 == 0) && (x1 == 0) && (y1 == 0);
	}

}

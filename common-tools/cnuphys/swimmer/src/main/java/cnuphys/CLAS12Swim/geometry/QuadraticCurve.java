package cnuphys.CLAS12Swim.geometry;

	public class QuadraticCurve {

		/**
		 * Get a point on a quadratic curve defined by three points.
		 * @param p0 first point of bezier curve
		 * @param p1 second point of bezier curve
		 * @param p2 third point of bezier curve
		 * @param t usual parameter. 0 <= t <= 1
		 * @return a point on the curve
		 */
	    public static void quadraticCurve(Point p0, Point p1, Point p2, double t, Point p) {
	        Point term1 = p0.scale(Math.pow(1 - t, 2));
	        Point term2 = p1.scale(2 * t * (1 - t));
	        Point term3 = p2.scale(Math.pow(t, 2));

	        p.set(term1.add(term2).add(term3));
	    }


	    /** Method to find the point on the curve closest to the line (the DOCA)
	     *
	     * @param p0 first point used to define the curve
	     * @param p1 second point used to define the curve
	     * @param p2 third point used to define the curve
	     * @param line the infinite line
	     * @param l1 second point used to define the line
	     * @return the point on the curve closest to the line
	     */
	    public static double findClosestPoint(Point p0, Point p1, Point p2, Line line, double tolerance, Point doca) {
	        double lower = 0;
	        double upper = 1;
	        double t = (lower + upper) / 2;

	        Point curvePoint1 = new Point();
	        Point curvePoint2 = new Point();

	        while (upper - lower > tolerance) {
	            double t1 = lower + (upper - lower) / 3;
	            double t2 = upper - (upper - lower) / 3;

	            quadraticCurve(p0, p1, p2, t1, curvePoint1);
	            quadraticCurve(p0, p1, p2, t2, curvePoint2);

	            double distance1 = Point.distance(curvePoint1, line.closestPointOnLine(curvePoint1));
	            double distance2 = Point.distance(curvePoint2, line.closestPointOnLine(curvePoint2));

	            if (distance1 < distance2) {
	                upper = t2;
	            } else {
	                lower = t1;
	            }

	            t = (lower + upper) / 2;
	        }

	        quadraticCurve(p0, p1, p2, t, doca);
	        return t;
	    }

	    public static void main(String[] args) {
	        Point p0 = new Point(1, 3, 1); // replace with actual values
	        Point p1 = new Point(2, 2, 2); // replace with actual values
	        Point p2 = new Point(3, 1, 3); // replace with actual values

	        Point pointOnCurve = new Point();
	        Point doca = new Point();

	        double t = 0.5; // example value of t

	        quadraticCurve(p0, p1, p2, t, pointOnCurve);
	        System.out.println("Point on Curve: (" + pointOnCurve.x + ", " + pointOnCurve.y + ", " + pointOnCurve.z + ")");

	        Line line = new Line(new Point(0, 90, 0), new Point(0, 180, 0));

	        findClosestPoint(p0, p1, p2, line, 1.0e-8, doca);
	        System.out.println("Closest Point: (" + doca.x + ", " + doca.y + ", " + doca.z + ")");
	    }

}

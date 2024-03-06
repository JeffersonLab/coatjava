package cnuphys.CLAS12Swim.geometry;

/**
 * A plane is defined by the equation (r - ro).norm = 0 Where r is an arbitrary
 * point on the plane, ro is a given point on the plane and norm is the normal
 * to the plane
 * 
 * @author heddle
 *
 */
public class Plane {
	
	/** Effectively zero */
	private static final double TINY = 1.0e-20;

	// for the form ax + by + cz = d;
	public final double a;
	public final double b;
	public final double c;
	public final double d;

	private double _denom = Double.NaN;

	/**
	 * Create a plane from a normal vector and a point on the plane
	 * 
	 * @param norm the normal vector
	 * @param p0   a point in the plane
	 * @return the plane that contains p and its normal is norm
	 */
	public Plane(Vector anorm, Point p0) {
		// lets make it a unit vector
		Vector norm = anorm.unitVector();
		a = norm.x; // A
		b = norm.y; // B
		c = norm.z; // C
		d = a * p0.x + b * p0.y + c * p0.z; // D
	}

	/**
	 * Create a plane from the coefficients of the equation ax + by + cz = d
	 * 
	 * @param a the a coefficient
	 * @param b the b coefficient
	 * @param c the c coefficient
	 * @param d the d coefficient
	 */
	public Plane(double a, double b, double c, double d) {
		this.a = a;
		this.b = b;
		this.c = c;
		this.d = d;
	}

	/**
	 * Create a plane from the normal vector in an array of doubles and a point in
	 * the plane in an array, both (x, y, z)
	 * 
	 * @param norm  the normal
	 * @param point the point in the plane
	 */
	public Plane(double norm[], double point[]) {

		this(new Vector(norm[0], norm[1], norm[2]), new Point(point[0], point[1], point[2]));
	}

	/**
	 * Create a plane from a normal vector and a point on the plane
	 * 
	 * @param nx x component of normal vector
	 * @param ny y component of normal vector
	 * @param nz z component of normal vector
	 * @param px x component of point on plane
	 * @param py y component of point on plane
	 * @param pz z component of point on plane
	 */
	public Plane(double nx, double ny, double nz, double px, double py, double pz) {

		this(new Vector(nx, ny, nz), new Point(px, py, pz));
	}

	/**
	 * Create a line from two points and then get the intersection with the plane
	 * 
	 * @param p1 one point
	 * @param p2 another point
	 * @param p  will hold the intersection, NaNs if no intersection
	 * @return the t parameter. If NaN it means the line is parallel to the plane.
	 *         If t [0,1] then the segment intersects the plane. If t outside [0, 1]
	 *         the infinite line intersects the plane, but not the segment
	 */
	public double interpolate(Point p1, Point p2, Point p) {
		Line line = new Line(p1, p2);
		return lineIntersection(line, p);
	}
	
	/**
	 * Distance from a point to the plane
	 * 
	 * @param x the x coordinate
	 * @param y the y coordinate
	 * @param z the z coordinate
	 * @return the signed distance (indicates which side you are on where norm
	 *         defines positive side)
	 */
	public double distance(double x, double y, double z) {
		return Math.abs(signedDistance(x, y, z));
	}

	/**
	 * Signed distance from a point to the plane
	 * 
	 * @param x the x coordinate
	 * @param y the y coordinate
	 * @param z the z coordinate
	 * @return the signed distance (indicates which side you are on where norm
	 *         defines positive side)
	 */
	public double signedDistance(double x, double y, double z) {
		if (Double.isNaN(_denom)) {
			_denom = Math.sqrt(a * a + b * b + c * c);
		}
		return (a * x + b * y + c * z - d) / _denom;
	}

	/**
	 * Compute the intersection of an infinite line with the plane
	 * 
	 * @param line         the line
	 * @param intersection will hold the point of intersection
	 * @return the t parameter. If NaN it means the line is parallel to the plane.
	 *         If t [0,1] then the segment intersects the plane. If t outside [0, 1]
	 *         the infinite line intersects the plane, but not the segment
	 */
	public double lineIntersection(Line line, Point intersection) {
		// Direction vector of the line
		Vector lineDir = line.getDelP();

		Point p0 = line.getP0();

		// Check if the line is parallel to the plane
		double dotProduct = a * lineDir.x + b * lineDir.y + c * lineDir.z;
		if (Math.abs(dotProduct) < TINY) {
			System.err.println("The line is parallel to the plane in Plane.findLinePlaneIntersection.");
			return Double.NaN;
		}

		// Parameter t in the parametric line equation
		double t = (d - a * p0.x - b * p0.y - c * p0.z) / dotProduct;

		line.getP(t, intersection);
		return t;
	}

	/**
	 * Get whether the point is to the left, right or (exactly) on the plane
	 * 
	 * @param x the x coordinate
	 * @param y the y coordinate
	 * @param z the z coordinate
	 * @return +1 if to the left, -1 if to the right, 0 if on the plane
	 */
	public int sign(double x, double y, double z) {
		double result = a * x + b * y + c * z;

		if (result > d) {
			return +1;
		} else if (result < d) {
			return -1;
		} else {
			return 0;
		}
	}

	/**
	 * Create a plane of constant azimuthal angle phi
	 * 
	 * @param phi the azimuthal angle in degrees
	 * @return the plane of constant phi
	 */
	public static Plane constantPhiPlane(double phi) {
		phi = Math.toRadians(phi);

		double cphi = Math.cos(phi);
		double sphi = Math.sin(phi);

		// point in the plane
		Point p = new Point(cphi, sphi, 0);

		// normal
		Vector norm = new Vector(sphi, -cphi, 0);

		return new Plane(norm, p);
	}

	@Override
	public String toString() {
		String pstr = String.format("abcd = [%10.6G, %10.6G, %10.6G, %10.6G]", a, b, c, d);
		return pstr;
	}

	// is the value essentially 0?
	private boolean tiny(double v) {
		return Math.abs(v) < TINY;
	}

	/**
	 * Find some coordinates suitable for drawing the plane as a Quad in 3D
	 * 
	 * @param scale an arbitrary big number, a couple times bigger than the drawing
	 *              extent
	 * @return the jogl coordinates for drawing a Quad
	 */
	public float[] planeQuadCoordinates(float scale) {

		int[] i1 = { -1, -1, 1, 1 };
		int[] i2 = { -1, 1, 1, -1 };

		if (tiny(a) && tiny(b) && tiny(c)) {
			return null;
		}

		float[] coords = new float[12];

		if (tiny(b) && tiny(c)) { // constant x plane
			float fx = (float) (d / a);
			for (int k = 0; k < 4; k++) {
				int j = 3 * k;

				float y = scale * i1[k];
				float z = scale * i2[k];

				coords[j] = fx;
				coords[j + 1] = y;
				coords[j + 2] = z;
			}

		} else if (tiny(a) && tiny(c)) { // constant y plane
			float fy = (float) (d / b);
			for (int k = 0; k < 4; k++) {
				int j = 3 * k;

				float x = scale * i1[k];
				float z = scale * i2[k];

				coords[j] = x;
				coords[j + 1] = fy;
				coords[j + 2] = z;
			}
		} else if (tiny(a) && tiny(b)) { // constant z plane
			float fz = (float) (d / c);
			for (int k = 0; k < 4; k++) {
				int j = 3 * k;

				float x = scale * i1[k];
				float y = scale * i2[k];

				coords[j] = x;
				coords[j + 1] = y;
				coords[j + 2] = fz;
			}
		}

		else if (tiny(a)) {
			for (int k = 0; k < 4; k++) {
				int j = 3 * k;

				float x = scale * i1[k];
				float y = scale * i2[k];
				float z = (float) ((d - b * y) / c);

				coords[j] = x;
				coords[j + 1] = y;
				coords[j + 2] = z;
			}
		}

		else if (tiny(b)) {
			for (int k = 0; k < 4; k++) {
				int j = 3 * k;

				float x = scale * i1[k];
				float y = scale * i2[k];
				float z = (float) ((d - a * x) / c);

				coords[j] = x;
				coords[j + 1] = y;
				coords[j + 2] = z;
			}

		}

		else if (tiny(c)) {
			for (int k = 0; k < 4; k++) {
				int j = 3 * k;

				float x = scale * i1[k];
				float z = scale * i2[k];
				float y = (float) ((d - a * x) / b);

				coords[j] = x;
				coords[j + 1] = y;
				coords[j + 2] = z;
			}

		}

		else { // general case, no small constants
			for (int k = 0; k < 4; k++) {
				int j = 3 * k;

				float x = scale * i1[k];
				float y = scale * i2[k];
				float z = (float) ((d - a * x - b * y) / c);

				coords[j] = x;
				coords[j + 1] = y;
				coords[j + 2] = z;
			}
		}

		return coords;
	}

}
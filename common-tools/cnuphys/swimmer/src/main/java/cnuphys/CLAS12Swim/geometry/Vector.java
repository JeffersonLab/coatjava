package cnuphys.CLAS12Swim.geometry;

/**
 * Ordinary 3D vector
 * 
 * @author heddle
 *
 */
public class Vector extends Point {
	
	/** Effectively zero */
	private static final double TINY = 1.0e-20;


	/**
	 * Create a new vector with a zero components
	 */
	public Vector() {
	}

	/**
	 * Create a Vector from a point
	 * 
	 * @param p the point
	 */
	public Vector(Point p) {
		this(p.x, p.y, p.z);
	}

	/**
	 * Create a vector
	 * 
	 * @param x the x coordinate
	 * @param y the y coordinate
	 * @param z the z coordinate
	 */
	public Vector(double x, double y, double z) {
		super(x, y, z);
	}

	/**
	 * The square of the length of the vector
	 * 
	 * @return the square of the length of the vector
	 */
	public double lengthSquared() {
		return x * x + y * y + z * z;
	}

	/**
	 * The length of the vector
	 * 
	 * @return the length of the vector
	 */
	public double length() {
		return Math.sqrt(lengthSquared());
	}

	/**
	 * The cross product of two vectors
	 * 
	 * @param a one vector
	 * @param b other vector
	 * @return c = a x b
	 */
	public static Vector cross(Vector a, Vector b) {
		Vector c = new Vector();
		cross(a, b, c);
		return c;
	}

	/**
	 * The in-place cross product of two vectors
	 * 
	 * @param a one vector
	 * @param b other vector
	 * @param c on return c = a x b
	 */
	public static void cross(Vector a, Vector b, Vector c) {
		c.x = a.y * b.z - a.z * b.y;
		c.y = a.z * b.x - a.x * b.z;
		c.z = a.x * b.y - a.y * b.x;
	}


	/**
	 * Get a unit vector in the same direction as this
	 * 
	 * @return a unit vector
	 */
	public Vector unitVector() {
		double len = length();
		if (len < TINY) {
			return null;
		}

		return new Vector(x / len, y / len, z / len);
	}


}
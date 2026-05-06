package cnuphys.magfield;

import java.io.PrintStream;
import java.util.ArrayList;

/**
 * A composition of multiple magnetic field maps. The resulting magnetic field
 * at a given point is the sum of the constituent fields at that point.
 *
 */
@SuppressWarnings("serial")
public class CompositeField extends ArrayList<IMagField> implements IMagField {

    boolean _hasTorus;
    boolean _hasSolenoid;
    
	/**
	 * Checks whether the field has been set to always return zero.
	 * 
	 * @return <code>true</code> if the field is set to return zero.
	 */
	@Override
	public final boolean isZeroField() {

		for (IMagField ifield : this) {
			if (!(ifield.isZeroField())) {
				return false;
			}
		}

		return true;
	}

	@Override
	public boolean add(IMagField field) {

		// remove(field); //prevent duplicates

		// further check, only one solenoid or one torus
		// (might have different instances for some reason)

		for (IMagField ifield : this) {
			if (ifield.getClass().equals(field.getClass())) {
				remove(ifield);
				break;
			}
		}

        _hasSolenoid = false;
        _hasTorus = false;
		if (super.add(field)) {
            for (IMagField ifield : this) {
                if (ifield instanceof Solenoid) _hasSolenoid = true;
                if (ifield instanceof Torus) _hasTorus = true;
            }
            return true;
        }
        return false;
	}

	@Override
	public String getName() {
		String s = "Composite contains: ";

		int count = 1;
		for (IMagField field : this) {
			if (count == 1) {
				s += field.getName();
			} else {
				s += " + " + field.getName();
			}
			count++;
		}

		return s;
	}

	/**
	 * Check whether we have a torus field
	 * 
	 * @return <code>true</code> if we have a torus
	 */
	public boolean hasTorus() {
        return _hasTorus;
	}

	/**
	 * Check whether we have a solenoid field
	 * 
	 * @return <code>true</code> if we have a solenoid
	 */
	public boolean hasSolenoid() {
        return _hasSolenoid;
	}
	
	/**
	 * Check whether we have a transverse solenoid field
	 * 
	 * @return <code>true</code> if we have a transverse solenoid
	 */
	public boolean hasTransverseSolenoid() {
		for (int i=0; i<this.size(); i++) {
			if (this.get(i) instanceof TransverseSolenoid) {
				return true;
			}
		}

		return false;
	}

	@Override
	public float getB1(int index) {
		float b = 0f;
		for (int i=0; i<this.size(); i++) {
			b += this.get(i).getB1(index);
		}
		return b;
	}

	@Override
	public float getB2(int index) {
		float b = 0f;
		for (int i=0; i<this.size(); i++) {
			b += this.get(i).getB2(index);
		}
		return b;
	}

	@Override
	public float getB3(int index) {
		float b = 0f;
		for (int i=0; i<this.size(); i++) {
			b += this.get(i).getB3(index);
		}
		return b;
	}

	@Override
	public float getMaxFieldMagnitude() {
		float max = 0;
		for (int i=0; i<this.size(); i++) {
			max = Math.max(max, this.get(i).getMaxFieldMagnitude());
		}
		return max;
	}

	@Override
	public double getScaleFactor() {
		return 1;
	}

	/**
	 * Print the current configuration
	 * 
	 * @param ps the print stream
	 */
	@Override
	public void printConfiguration(PrintStream ps) {
		ps.println("COMPOSITE FIELD");
		for (int i=0; i<this.size(); i++) {
			this.get(i).printConfiguration(ps);
		}
	}

	@Override
	public boolean contains(double x, double y, double z) {
		for (int i=0; i<this.size(); i++) {
			if (this.get(i).contains(x, y, z)) {
				return true;
			}
		}
		return false;
	}

}

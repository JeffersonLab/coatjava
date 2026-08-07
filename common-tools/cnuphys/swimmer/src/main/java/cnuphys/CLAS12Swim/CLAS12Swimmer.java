package cnuphys.CLAS12Swim;

import java.util.Hashtable;

/**
 * Shared CLAS12 swimming constants retained for source compatibility.
 *
 * Use {@link CommonsMathCLAS12Swimmer} as the swimming implementation. This
 * class no longer implements an integrator.
 */
public final class CLAS12Swimmer {

	public static final double C = 2.99792458e10;
	public static final int SWIM_SWIMMING = 88;
	public static final int SWIM_SUCCESS = 0;
	public static final int SWIM_TARGET_MISSED = -1;
	public static final int BELOW_MIN_MOMENTUM = -2;

	public static final Hashtable<Integer, String> resultNames = new Hashtable<>();

	static {
		resultNames.put(SWIM_SWIMMING, "SWIMMING");
		resultNames.put(SWIM_SUCCESS, "SWIM_SUCCESS");
		resultNames.put(SWIM_TARGET_MISSED, "SWIM_TARGET_MISSED");
		resultNames.put(BELOW_MIN_MOMENTUM, "BELOW_MIN_MOMENTUM");
	}

	private CLAS12Swimmer() {
	}
}

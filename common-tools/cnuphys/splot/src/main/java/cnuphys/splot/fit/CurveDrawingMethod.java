package cnuphys.splot.fit;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import cnuphys.splot.spline.CubicSpline;
import cnuphys.splot.style.EnumComboBox;

/**
 * Specifies how a curve should be drawn for a data set.
 * <p>
 * Historically this was named {@code FitType}, but that name was misleading
 * because several options are not fits at all (e.g. connect, stairs, none).
 * </p>
 * <p>
 * Some drawing methods optionally consume a {@link FitResult} (Apache Commons Math based),
 * or a {@link CubicSpline}.
 * </p>
 */
public enum CurveDrawingMethod {

	/** No curve drawn. */
	NONE,

	/** Simple line segments connecting points. */
	CONNECT,

	/** Stair-step connection between points. */
	STAIRS,

	/** Natural cubic spline interpolation (not a fit). */
	CUBICSPLINE,

	/** Polynomial least-squares fit. */
	POLYNOMIAL,

	/** Single Gaussian fit. */
	GAUSSIAN,

	/** Multiple Gaussian fit (sum of Gaussians). */
	GAUSSIANS,

	/** Error function fit. */
	ERF,

	/** Complementary error function fit. */
	ERFC;

	// ------------------------------------------------------------------------
	// Display names (immutable)
	// ------------------------------------------------------------------------

	private static final EnumMap<CurveDrawingMethod, String> DISPLAY_NAMES =
			new EnumMap<>(CurveDrawingMethod.class);

	static {
		DISPLAY_NAMES.put(CONNECT, "Simple Connect");
		DISPLAY_NAMES.put(STAIRS, "Stairs");
		DISPLAY_NAMES.put(CUBICSPLINE, "Cubic Spline");
		DISPLAY_NAMES.put(POLYNOMIAL, "Polynomial");
		DISPLAY_NAMES.put(GAUSSIAN, "Gaussian");
		DISPLAY_NAMES.put(GAUSSIANS, "Gaussians");
		DISPLAY_NAMES.put(ERF, "Erf Function");
		DISPLAY_NAMES.put(ERFC, "Erfc Function");
		DISPLAY_NAMES.put(NONE, "No Line");
	}

	/** @return unmodifiable view of display names keyed by method. */
	public static Map<CurveDrawingMethod, String> displayNames() {
		return Collections.unmodifiableMap(DISPLAY_NAMES);
	}

	/** @return the UI/display name for this method. */
	public String getDisplayName() {
		String s = DISPLAY_NAMES.get(this);
		return (s == null) ? name() : s;
	}

	/**
	 * Find a method by display name or enum name (case-insensitive).
	 *
	 * @param name the display name (preferred) or enum constant name
	 * @return matching method, or {@code null} if none
	 */
	public static CurveDrawingMethod fromDisplayName(String name) {
		if (name == null) {
			return null;
		}
		for (CurveDrawingMethod m : values()) {
			if (name.equalsIgnoreCase(m.getDisplayName())) {
				return m;
			}
			if (name.equalsIgnoreCase(m.name())) {
				return m;
			}
		}
		return null;
	}

	/**
	 * Obtain a combo box of choices.
	 *
	 * @param defaultChoice default selected method (may be {@code null})
	 * @return the combo box
	 */
	public static EnumComboBox getComboBox(CurveDrawingMethod defaultChoice) {
		return new EnumComboBox(DISPLAY_NAMES, defaultChoice);
	}
	
	/**
	 * Obtain the {@link CurveDrawingMethod} corresponding to a display string
	 * returned by an {@link EnumComboBox}.
	 *
	 * @param displayName the display string (may be {@code null})
	 * @return the corresponding {@code CurveDrawingMethod}, or {@code null} if
	 *         no match is found
	 */
	public static CurveDrawingMethod getValue(String displayName) {
		if (displayName == null) {
			return null;
		}

		for (Map.Entry<CurveDrawingMethod, String> entry : DISPLAY_NAMES.entrySet()) {
			if (displayName.equals(entry.getValue())) {
				return entry.getKey();
			}
		}

		return null;
	}


}

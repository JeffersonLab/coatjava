package cnuphys.splot.fit;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import cnuphys.splot.fit.apache.FitResult;
import cnuphys.splot.plot.DoubleFormat;
import cnuphys.splot.plot.UnicodeSupport;
import cnuphys.splot.plot.X11Colors;
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

	/** Harmonic (Fourier-like) fit. */
	HARMONIC,

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
		DISPLAY_NAMES.put(HARMONIC, "Harmonic");
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

	// ------------------------------------------------------------------------
	// Fit/draw description HTML
	// ------------------------------------------------------------------------

	private static final String _MU = UnicodeSupport.SMALL_MU;
	private static final String _SUM = UnicodeSupport.CAPITAL_SIGMA;
	private static final String _EOL = "<BR>";
	private static final String _SP = "&nbsp;";

	private static final String DARKGREEN = X11Colors.getX11ColorAsHex("Dark GREEN");

	/**
	 * Get an HTML string describing the draw method and (when applicable) the fit result.
	 * <p>
	 * Expected object types by method:
	 * </p>
	 * <ul>
	 *   <li>{@link #CUBICSPLINE}: {@link CubicSpline} (optional)</li>
	 *   <li>Fit methods: {@link FitResult} (optional)</li>
	 * </ul>
	 *
	 * @param object the associated result object (may be {@code null})
	 * @return HTML string (line breaks via {@code <BR>})
	 */
	public String getFitHtml(Object object) {
		StringBuilder sb = new StringBuilder(1024);

		switch (this) {

		case NONE:
			sb.append("No lines.");
			break;

		case CONNECT:
			sb.append("Connect the points.");
			break;

		case STAIRS:
			sb.append("Staircase connection.");
			break;

		case CUBICSPLINE:
		    sb.append(header("Cubic Spline:"));
		    sb.append("Natural cubic spline interpolation");
		    if (object instanceof CubicSpline) {
		        CubicSpline cs = (CubicSpline) object;
		        if (cs.isValid()) {
		            sb.append(_EOL);
		            sb.append(info("Knots = " + cs.size()
		                    + _SP + "Range: ["
		                    + DoubleFormat.doubleFormat(cs.xmin(), 6)
		                    + ", "
		                    + DoubleFormat.doubleFormat(cs.xmax(), 6)
		                    + "]"));
		        } else {
		            sb.append(_EOL);
		            sb.append(warning("Spline object is not valid."));
		        }
		    } else {
		        sb.append(_EOL);
		        sb.append(warning("Spline object not provided; only method description shown."));
		    }
		    break;

		case POLYNOMIAL:
			appendPolynomial(sb, object);
			break;

		case ERF:
			appendErfErfc(sb, object, false);
			break;

		case ERFC:
			appendErfErfc(sb, object, true);
			break;

		case GAUSSIAN:
			appendSingleGaussian(sb, object);
			break;

		case GAUSSIANS:
			appendMultipleGaussians(sb, object);
			break;

		case HARMONIC:
			appendHarmonic(sb, object);
			break;
		}

		return sb.toString();
	}

	// ------------------------------------------------------------------------
	// Fit-specific builders
	// ------------------------------------------------------------------------

	private static void appendPolynomial(StringBuilder sb, Object object) {
		if (!(object instanceof FitResult)) {
			sb.append(warning("fit info not available."));
			return;
		}

		FitResult fitResult = (FitResult) object;
		int degree = fitResult.nParams() - 1;

		sb.append(header("Polynomial Fit (degree " + degree + "):"));
		sb.append(descript("y = A<SUB>0</SUB> + A<SUB>1</SUB>&thinsp;x + A<SUB>2</SUB>&thinsp;x<SUP>2</SUP> + ... + A<SUB>"
				+ degree + "</SUB>&thinsp;x<SUP>" + degree + "</SUP>"));
		sb.append(info(chiSqString(fitResult.chiSquare) + _SP + "DOF = " + fitResult.dof + _SP
				+ "Reduced " + chiSqString(fitResult.chiSquareReduced)));

		sb.append(colorStr("<b>Polynomial Coefficients</b>", "blue")).append(_EOL);

		for (int i = 0; i < fitResult.nParams(); i++) {
			double val = fitResult.param(i);
			double var = varianceDiag(fitResult, i);
			sb.append(paramStr("A" + sub("", i), val, var));
		}
	}

	private static void appendErfErfc(StringBuilder sb, Object object, boolean complement) {
		if (!(object instanceof FitResult)) {
			sb.append(warning("fit info not available."));
			return;
		}

		FitResult fitResult = (FitResult) object;
		String which = complement ? "Erfc" : "Erf";

		sb.append(header(which + " Fit:"))
		  .append(descript("y = A + B&thinsp;" + which + "[(x-" + _MU + ")/S]"));
		sb.append(info(chiSqString(fitResult.chiSquare) + _SP
				+ "DOF = " + fitResult.dof + _SP
				+ "Reduced " + chiSqString(fitResult.chiSquareReduced)));

		sb.append(colorStr("<b>" + which + " Parameters</b>", "blue")).append(_EOL);

		// Expected parameterization: [A, B, mu, S]
		String[] names = { "A", "B", _MU, "S" };
		int n = Math.min(fitResult.nParams(), names.length);

		for (int i = 0; i < n; i++) {
			double val = fitResult.param(i);
			double var = varianceDiag(fitResult, i);
			sb.append(paramStr(names[i], val, var));
		}

		// If someone later adds extra parameters, show them generically
		for (int i = n; i < fitResult.nParams(); i++) {
			double val = fitResult.param(i);
			double var = varianceDiag(fitResult, i);
			sb.append(paramStr("P" + sub("", i), val, var));
		}
	}

	private static void appendSingleGaussian(StringBuilder sb, Object object) {
		if (!(object instanceof FitResult)) {
			sb.append(warning("fit info not available."));
			return;
		}

		FitResult fitResult = (FitResult) object;

		sb.append(header("Gaussian Fit:"))
		  .append(descript(" y = A&thinsp;exp{-[(x-" + _MU + ")/S]<SUP>2</SUP>}"));
		sb.append(info(chiSqString(fitResult.chiSquare) + _SP
				+ "DOF = " + fitResult.dof + _SP
				+ "Reduced " + chiSqString(fitResult.chiSquareReduced)));

		sb.append(colorStr("<b>Gaussian Parameters</b>", "blue")).append(_EOL);

		// Expected: [A, mu, S]
		String[] names = { "A", _MU, "S" };
		int n = Math.min(fitResult.nParams(), names.length);

		for (int i = 0; i < n; i++) {
			double val = fitResult.param(i);
			double var = varianceDiag(fitResult, i);
			sb.append(paramStr(names[i], val, var));
		}

		for (int i = n; i < fitResult.nParams(); i++) {
			double val = fitResult.param(i);
			double var = varianceDiag(fitResult, i);
			sb.append(paramStr("P" + sub("", i), val, var));
		}
	}

	private static void appendMultipleGaussians(StringBuilder sb, Object object) {
		if (!(object instanceof FitResult)) {
			sb.append(warning("fit info not available."));
			return;
		}

		FitResult fitResult = (FitResult) object;
		int nGauss = fitResult.nParams() / 3;

		sb.append(header("Multiple Gaussian Fit (" + nGauss + " Gaussians):"));
		sb.append(descript(" y = " + _SUM + sub(" [A", 1) + "&thinsp;exp{-[(x-" + sub("", 1)
				+ ")/" + sub("S", 1) + "]<SUP>2</SUP>}"));
		sb.append(info(chiSqString(fitResult.chiSquare) + _SP
				+ "DOF = " + fitResult.dof + _SP
				+ "Reduced " + chiSqString(fitResult.chiSquareReduced)));

		sb.append(colorStr("<b>Gaussian Parameters</b>", "blue")).append(_EOL);

		for (int i = 0; i < nGauss; i++) {
			sb.append(colorStr("<b>Gaussian " + (i + 1) + "</b>", DARKGREEN)).append(_EOL);

			double valA = fitResult.param(3 * i);
			double varA = varianceDiag(fitResult, 3 * i);
			sb.append(paramStr("A" + sub("", i + 1), valA, varA));

			double valMu = fitResult.param(3 * i + 1);
			double varMu = varianceDiag(fitResult, 3 * i + 1);
			sb.append(paramStr(_MU + sub("", i + 1), valMu, varMu));

			double valS = fitResult.param(3 * i + 2);
			double varS = varianceDiag(fitResult, 3 * i + 2);
			sb.append(paramStr("S" + sub("", i + 1), valS, varS));
		}
	}

	private static void appendHarmonic(StringBuilder sb, Object object) {
		if (!(object instanceof FitResult)) {
			sb.append(warning("fit info not available."));
			return;
		}

		FitResult fitResult = (FitResult) object;

		sb.append(header("Harmonic Fit:"))
		  .append(descript(" y = A<SUB>0</SUB> + "
				+ _SUM + sub(" [A", 1) + "&thinsp;cos(" + sub("", 1) + "&thinsp;x) + "
				+ _SUM + sub(" [B", 1) + "&thinsp;sin(" + sub("", 1) + "&thinsp;x)"));

		sb.append(info(chiSqString(fitResult.chiSquare) + _SP
				+ "DOF = " + fitResult.dof + _SP
				+ "Reduced " + chiSqString(fitResult.chiSquareReduced)));

		sb.append(colorStr("<b>Harmonic Parameters</b>", "blue")).append(_EOL);

		int nParams = fitResult.nParams();
		if (nParams <= 0) {
			sb.append(warning("No parameters returned in FitResult."));
			return;
		}

		// Typical parameterization:
		//   p0 = A0
		//   then pairs (A1,B1), (A2,B2), ... => total = 1 + 2*N
		double a0 = fitResult.param(0);
		double varA0 = varianceDiag(fitResult, 0);
		sb.append(paramStr("A" + sub("", 0), a0, varA0));

		int remaining = nParams - 1;
		int nHarm = remaining / 2;

		for (int k = 1; k <= nHarm; k++) {
			int ia = 2 * (k - 1) + 1;
			int ib = ia + 1;

			double a = fitResult.param(ia);
			double varA = varianceDiag(fitResult, ia);
			sb.append(paramStr("A" + sub("", k), a, varA));

			if (ib < nParams) {
				double b = fitResult.param(ib);
				double varB = varianceDiag(fitResult, ib);
				sb.append(paramStr("B" + sub("", k), b, varB));
			}
		}

		// If odd extras exist, show generically
		for (int i = 1 + 2 * nHarm; i < nParams; i++) {
			double v = fitResult.param(i);
			double var = varianceDiag(fitResult, i);
			sb.append(paramStr("P" + sub("", i), v, var));
		}
	}

	// ------------------------------------------------------------------------
	// Formatting helpers
	// ------------------------------------------------------------------------

	private static double varianceDiag(FitResult fitResult, int i) {
		if (fitResult == null || fitResult.covariance == null) {
			return Double.NaN;
		}
		try {
			if (i < 0 || i >= fitResult.covariance.getRowDimension()) {
				return Double.NaN;
			}
			return fitResult.covariance.getEntry(i, i);
		}
		catch (Exception e) {
			return Double.NaN;
		}
	}

	// <FONT style="BACKGROUND-COLOR: yellow">next </FONT>
	private static String colorStr(String s, String fg, String bg) {
		StringBuffer sb = new StringBuffer(512);
		sb.append("<FONT style=\"");
		if (bg != null) {
			sb.append("BACKGROUND-COLOR: " + bg + "; ");
		}
		if (fg != null) {
			sb.append("COLOR: " + fg);
		}
		sb.append("\">" + s + "</FONT>");
		return sb.toString();
	}

	private static String colorStr(String s, String fg) {
		return colorStr(s, fg, null);
	}

	private static String warning(String msg) {
		return colorStr(msg, "red") + _EOL;
	}

	private static String header(String s) {
		return "<b>" + s + "</b>" + _EOL;
	}

	private static String descript(String s) {
		return colorStr(s, "black") + _EOL;
	}

	private static String info(String s) {
		return colorStr(s, "black") + _EOL;
	}

	private static String chiSqString(double chiSq) {
		return "<i>&chi;<SUP>2</SUP></i> = " + DoubleFormat.doubleFormat(chiSq, 6);
	}

	private static String paramStr(String name, double val, double var) {
		StringBuffer sb = new StringBuffer(256);
		sb.append(colorStr(name, "black") + " = " + DoubleFormat.doubleFormat(val, 6));
		if (!Double.isNaN(var) && var >= 0.0) {
			double sigma = Math.sqrt(var);
			sb.append(_SP + colorStr("&plusmn;", "black") + _SP + DoubleFormat.doubleFormat(sigma, 6));
		}
		sb.append(_EOL);
		return sb.toString();
	}

	private static String sub(String s, int ss) {
		return s + "<SUB>" + ss + "</SUB>";
	}

	private static String sub(String s, String ss) {
		return s + "<SUB>" + ss + "</SUB>";
	}
}

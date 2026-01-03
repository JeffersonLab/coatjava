package cnuphys.splot.plot;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

public class SmartDoubleFormatter {

    public static String doubleFormat(double value, int sigDigits) {

        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return String.valueOf(value);
        }

        BigDecimal bd = new BigDecimal(value,
                new MathContext(sigDigits, RoundingMode.HALF_UP));

        String plain = bd.toPlainString();
        String scientific = bd.toString();

        // Use scientific if plain string is ugly/long
        if (plain.length() > sigDigits + 5) {
            return scientific;
        } else {
            return plain;
        }
    }
}

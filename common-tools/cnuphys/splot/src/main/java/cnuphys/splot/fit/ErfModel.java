package cnuphys.splot.fit;

import org.apache.commons.math3.analysis.ParametricUnivariateFunction;
import org.apache.commons.math3.special.Erf;

public class ErfModel implements ParametricUnivariateFunction {

    @Override
    public double value(double x, double[] p) {
        double A     = p[0];
        double x0    = p[1];
        double sigma = p[2];
        double B     = p[3];

        return A * Erf.erf((x - x0) / sigma) + B;
    }

    @Override
    public double[] gradient(double x, double[] p) {
        double A     = p[0];
        double x0    = p[1];
        double sigma = p[2];

        double z = (x - x0) / sigma;
        double exp = Math.exp(-z * z);
        double dErfDz = 2.0 / Math.sqrt(Math.PI) * exp;

        return new double[] {
            Erf.erf(z),                         // ∂/∂A
            -A * dErfDz / sigma,               // ∂/∂x0
            -A * dErfDz * z / sigma,           // ∂/∂sigma
            1.0                                // ∂/∂B
        };
    }
}


package cnuphys.splot.fit;

import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.DecompositionSolver;
import org.apache.commons.math3.linear.QRDecomposition;
import org.apache.commons.math3.linear.RealVector;

/**
 * Utility for computing a weighted linear least-squares parameter guess:
 * min sum_i w_i (A_i p - y_i)^2
 * by solving (sqrt(W)A)p = sqrt(W)y via QR.
 */
public final class LinearLeastSquaresGuesser {

    private LinearLeastSquaresGuesser() {}

    /**
     * Solve for parameters p in a linear model y ≈ A p using (optionally) weights.
     * Rows are scaled by sqrt(weight_i) for WLS.
     *
     * @param A       design matrix (n x p)
     * @param y       target vector (n)
     * @param weights optional weights (n) or null for OLS
     * @return parameter vector p (length p). Returns zeros if solve fails.
     */
    public static double[] solve(double[][] A, double[] y, double[] weights) {
        final int n = A.length;
        final int p = (n == 0) ? 0 : A[0].length;

        double[][] Aw = new double[n][p];
        double[] yw = new double[n];

        for (int i = 0; i < n; i++) {
            double scale = 1.0;
            if (weights != null) {
                double w = weights[i];
                if (Double.isFinite(w) && w > 0.0) {
                    scale = Math.sqrt(w);
                }
            }
            for (int j = 0; j < p; j++) {
                Aw[i][j] = scale * A[i][j];
            }
            yw[i] = scale * y[i];
        }

        try {
            DecompositionSolver solver =
                    new QRDecomposition(new Array2DRowRealMatrix(Aw, false)).getSolver();
            RealVector pv = solver.solve(new ArrayRealVector(yw, false));
            return pv.toArray();
        } catch (Exception e) {
            return new double[p]; // fallback zeros
        }
    }
}

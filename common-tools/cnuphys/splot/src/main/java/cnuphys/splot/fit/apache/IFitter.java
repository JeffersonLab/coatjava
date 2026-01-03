package cnuphys.splot.fit.apache;

import cnuphys.splot.fit.Evaluator;

public interface IFitter {
    FitResult fit(double[] x, double[] y);
    FitResult fit(double[] x, double[] y, double[] weights);
    Evaluator asEvaluator(FitResult fit);
}

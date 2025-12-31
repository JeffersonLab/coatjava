package cnuphys.splot.fit.apache;

import cnuphys.splot.fit.IValueGetter;

public interface IFitter {
    FitResult fit(double[] x, double[] y);
    FitResult fit(double[] x, double[] y, double[] weights);
    IValueGetter asValueGetter(FitResult fit);
}

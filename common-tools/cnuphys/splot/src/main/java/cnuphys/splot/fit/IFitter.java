package cnuphys.splot.fit;

public interface IFitter {
    FitResult fit(double[] x, double[] y);
    FitResult fit(double[] x, double[] y, double[] weights);
}

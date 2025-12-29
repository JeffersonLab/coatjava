package cnuphys.splot.fit;

/**
 * A function that can be plotted over a finite x-domain.
 * <p>
 * This is intentionally minimal so it can represent:
 * <ul>
 *   <li>fitted curves (Gaussian, erf, polynomial)</li>
 *   <li>splines</li>
 *   <li>analytic functions</li>
 * </ul>
 */
public interface IPlottableFunction {

    /** @return function evaluator */
    IValueGetter function();

    /** @return minimum x-value for plotting */
    double xmin();

    /** @return maximum x-value for plotting */
    double xmax();
}

package org.jlab.clas.tracking.utilities;

import org.apache.commons.math3.special.Gamma;

/** Chi-square probability utilities */
public class ProbChi2perNDF {

  /**
   * Returns the upper-tail probability (survival function, or p-value) of the
   * chi-square distribution: P(X &gt;= chi2) for X ~ &chi;&sup2;(ndf).
   * @param chi2 the chi-square statistic
   * @param ndf the number of degrees of freedom
   * @return the probability of observing a chi-square value at least as large as {@code chi2}, in the range [0, 1]
   */
  public static double prob(double chi2, int ndf) {
    if(ndf <= 0)
      return 0.0;
    if(chi2 <= 0.0) { // edge cases follow ROOT's `TMath::Prob` conventions
      return chi2 < 0.0 ? 0.0 : 1.0;
    }
    return Gamma.regularizedGammaQ(0.5 * ndf, 0.5 * chi2);
  }
}

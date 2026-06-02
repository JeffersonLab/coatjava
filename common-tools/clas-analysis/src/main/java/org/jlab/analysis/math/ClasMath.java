package org.jlab.analysis.math;

/**
 * 
 * @author naharrison
 */
public class ClasMath {
	
  public static boolean isWithinXPercent(double X, double val, double standard) {
    if(standard >= 0 && val > (1.0 - (X/100.0))*standard && val < (1.0 + (X/100.0))*standard) return true;
    else if(standard < 0 && val < (1.0 - (X/100.0))*standard && val > (1.0 + (X/100.0))*standard) return true;
    return false;
  }

}

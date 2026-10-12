package org.jlab.rec.dc.track.fit.basefit;

import java.util.List;

/** A least square fitting method
*   For a linear fit,f(a,b)=a+bx taking  y errors into account
*/

public class LineFitter {

	//instantiate
	private LineFitPars _linefitresult;
	
	// the constructor
	public LineFitter() {
	}

	// fit status
	public boolean fitStatus(double[] x, double[] y, double[] sigma_x, double[] sigma_y, int nbpoints) {
		boolean fitStat = false;
		
		if (nbpoints>=2) {  // must have enough points to do the fit
			// now do the fit
			// initialize weight-sum and moments
			double Sw, Sx, Sy, Sxx, Sxy;
			Sw = Sx = Sy = Sxx = Sxy =0.;
			double[] w = new double[nbpoints];
			
			
			for (int i = 0; i<nbpoints; i++) { 
				double r = sigma_y[i]*sigma_y[i]+sigma_x[i]*sigma_x[i];
				if(r==0) {
					return false;
				}
				w[i] = 1./r;
				Sw  += w[i];
				// the moments
				Sx  += x[i]*w[i];
				Sy  += y[i]*w[i];
				Sxy += x[i]*y[i]*w[i];
				Sxx += x[i]*x[i]*w[i]; 
			}
			// the determinant
			double determ = Sw*Sxx - Sx*Sx;  // the determinant; must be >0
			
			if(determ<1e-19) 
				determ=1e-19; //straight track approximation
			
			double slopeSol  = (Sw*Sxy - Sx*Sy)/determ;
			double intercSol = (Sy*Sxx - Sx*Sxy)/determ;
			// the errors on these parameters
			double slopeEr  = Math.sqrt(Sw/determ);
			double intercEr = Math.sqrt(Sxx/determ);
			double SlInCov   = -Sx/determ; 
			
			if(Math.abs(slopeSol)>=0 && Math.abs(intercSol)>=0) {
				
				// calculate the chi^2
				double chi_2 = 0.; 
				double pointchi_2[] = new double[nbpoints]; //individual chi2 for each fitted point
				for (int j = 0; j<nbpoints; j++) { 
					chi_2 += ((y[j]-(slopeSol*x[j]+intercSol))*(y[j]-(slopeSol*x[j]+intercSol)))*w[j];
					pointchi_2[j] = ((y[j]-(slopeSol*x[j]+intercSol))*(y[j]-(slopeSol*x[j]+intercSol)))*w[j];  					
				}
				// the number of degrees of freedom
				int Ndf = nbpoints - 2;
				
				// instantiate fit object to be returned;
				_linefitresult = new LineFitPars(slopeSol, intercSol, slopeEr, intercEr, SlInCov, chi_2, pointchi_2, Ndf);
				
				fitStat = true;
				}
		}
		
		// if there is a fit return true
		return fitStat;
	}

        // fit status (overload)
        public boolean fitStatus(List<Double> x, List<Double> y, List<Double> sigma_x, List<Double> sigma_y, int nbpoints) {
          return fitStatus(
              x.stream().mapToDouble(Double::doubleValue).toArray(),
              y.stream().mapToDouble(Double::doubleValue).toArray(),
              sigma_x.stream().mapToDouble(Double::doubleValue).toArray(),
              sigma_y.stream().mapToDouble(Double::doubleValue).toArray(),
              nbpoints);
        }

	// return the fit result
	public LineFitPars getFit() {
		return _linefitresult;
	}
	
	
}

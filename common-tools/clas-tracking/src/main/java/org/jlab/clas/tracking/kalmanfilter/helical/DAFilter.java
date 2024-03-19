package org.jlab.clas.tracking.kalmanfilter.helical;

/**
 * Calculate effective measurement and variance by DAF 
 * @author Tongtong
 */
public class DAFilter {
    
    private static double dafChi2Cut = 4;
         
    private double _var;
    private double _weight;

    private double _effectiveVar; 
    
    public DAFilter(double var) {
        this._var = var;
    } 
    
    public DAFilter(double var, double weight) {
        this._var = var;
        this._weight = weight;
    }          
    
    public static void setDafChi2Cut(double chi2Cut){
        dafChi2Cut = chi2Cut;
    }
    
    public double get_EffectiveVar(){
        return _var/_weight;
    }
        
    public double calc_updatedWeight(double residual, double annealingFactor){        
       double factor = 1/Math.sqrt(2 * Math.PI * annealingFactor * _var);
        
        double Chi2 = residual * residual/_var;
        double Phi = factor * Math.exp(-0.5 / annealingFactor * Chi2);
        double Lambda = factor * Math.exp(-0.5 / annealingFactor * dafChi2Cut);
        double sum = Phi + Lambda;
        double updatedWeight = Phi/sum;      
        
        return updatedWeight;
    }        
}
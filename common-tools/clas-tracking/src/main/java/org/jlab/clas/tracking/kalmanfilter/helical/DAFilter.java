package org.jlab.clas.tracking.kalmanfilter.helical;

/**
 * Calculate effective measurement and variance by DAF 
 * @author Tongtong
 */
public class DAFilter {
    
    private static double dafChi2CutTarget = 4;
    private static double dafChi2CutBST = 4;
    private static double dafChi2CutBMT = 4;
         
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
    
    public static void setDafChi2CutTarget(double chi2Cut){
        dafChi2CutTarget = chi2Cut;
    }
    
    public static void setDafChi2CutBST(double chi2Cut){
        dafChi2CutBST = chi2Cut;
    }
    
    public static void setDafChi2CutBMT(double chi2Cut){
        dafChi2CutBMT = chi2Cut;
    }
    
    public double get_EffectiveVar(){
        return _var/_weight;
    }
        
    public double calc_updatedWeight(double residual, double annealingFactor, int layerType){        
        double dafChi2Cut;        
        switch (layerType) {
            case 1:  dafChi2Cut = dafChi2CutTarget;
                     break;
            case 2:  dafChi2Cut = dafChi2CutBST;
                     break;
            case 3:  dafChi2Cut = dafChi2CutBMT;
                     break;
            default: dafChi2Cut = 4;
                     break;
        }
                
        double factor = 1/Math.sqrt(2 * Math.PI * annealingFactor * _var);        
        double Chi2 = residual * residual/_var;
        double Phi = factor * Math.exp(-0.5 / annealingFactor * Chi2);
        double Lambda = factor * Math.exp(-0.5 / annealingFactor * dafChi2Cut);
        double sum = Phi + Lambda;
        double updatedWeight = Phi/sum;      
        
        return updatedWeight;
    }        
}
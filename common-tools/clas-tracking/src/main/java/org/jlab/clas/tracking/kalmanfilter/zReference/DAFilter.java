package org.jlab.clas.tracking.kalmanfilter.zReference;
import org.jlab.clas.clas.math.FastMath;
import org.jlab.geom.prim.Point3D;
import org.jlab.geom.prim.Line3D;

/**
 * Calculate effective Doca and variance by DAF 
 * @author Tongtong
 */
public class DAFilter {
    
    private static double dafChi2Cut = 8;
    
    // For double hits
    private double[] docas_double;
    private double[] vars_double;
    private double[] weights_double;
    private Line3D[] wireLines_double;
    private Line3D verticalLine;
    private double halfWireDistance;    
    
    // For single hit
    private double doca_single;
    private double var_single;
    private double weight_single;

    // Effective doca
    private double effectiveDoca;
    private double effectiveVar;
    private int indexReferenceWire = 0;  
    
    // For uRWell
    private double[] xyVars_uRWell;
    private double weight_uRWell;
    private double[] effectiveXYVars_uRWell;
    
    
    public DAFilter(double[] docas, double[] vars,  double[] weights, Line3D[] wireLines) {
        this.docas_double = docas;
        this.vars_double = vars;
        this.weights_double = weights;
        this.wireLines_double = wireLines; 
        this.verticalLine = wireLines[0].distance(wireLines[1]);
        this.halfWireDistance = this.verticalLine.length()/2.;                
    } 
    
    public DAFilter(double doca, double var, double weight) {
        this.doca_single = doca;
        this.var_single = var;
        this.weight_single = weight;
    }
    
    public DAFilter(double[] xyVars,  double weight) {
        this.xyVars_uRWell = xyVars;
        this.weight_uRWell = weight;              
    } 
    
    public static void setDafChi2Cut(double chi2Cut){
        dafChi2Cut = chi2Cut;
    }
    
    public void calc_effectiveDoca_doubleHits(){        
        if((wireLines_double[0] == wireLines_double[1]) || ((docas_double[0] == docas_double[1]) && !(docas_double[0] == 0 && docas_double[1] == 0))) {
            effectiveVar = vars_double[0]/weights_double[0];
            effectiveDoca = docas_double[0];
            indexReferenceWire = 0;
            return;
        }        
        
        // Calculate distance between doca point to middle line with sign        
        double[] toMids = {0, 0};
                
        if(docas_double[0] < 0){
            toMids[0] = Math.abs(docas_double[0]) - halfWireDistance; 
        }
        else if(docas_double[0] > 0){
            toMids[0] = halfWireDistance - docas_double[0]; 
        }
        else{
            if(verticalLine.direction().x() > 0) toMids[0] = -halfWireDistance;
            else toMids[0] = halfWireDistance;
        }
        
        if(docas_double[1] < 0){
            toMids[1] = Math.abs(docas_double[1]) - halfWireDistance; 
        }
        else if(docas_double[1] > 0){
            toMids[1] = halfWireDistance - docas_double[1]; 
        }
        else{
            if(verticalLine.direction().x() > 0) toMids[1] = halfWireDistance;
            else toMids[1] = -halfWireDistance; 
        }
        
        // Calculate weighted averge distance to middle line
        double sumWeightedVarRec = 0; 
        double SumWeightedVarRecToMid = 0; 
        for(int i = 0; i < 2; i++){
            sumWeightedVarRec += weights_double[i] / vars_double[i];
            SumWeightedVarRecToMid += weights_double[i] / vars_double[i] * toMids[i];
        }
        effectiveVar = 1/sumWeightedVarRec;        
        double effectiveToMid = effectiveVar * SumWeightedVarRecToMid;
        
        // Calculate effective doca with reference line, which correponding doca has higher weight
        double docaLargerWeight = 0;
        if(weights_double[0] >= weights_double[1]){
            indexReferenceWire = 0;
            docaLargerWeight = docas_double[0];
        }
        else{
            indexReferenceWire = 1;
            docaLargerWeight = docas_double[1];
        }
                
        if (docaLargerWeight > 0) {
            effectiveDoca = halfWireDistance - effectiveToMid;
        } else {
            if (effectiveToMid < 0) {
                effectiveDoca = Math.abs(effectiveToMid) - halfWireDistance;
            } else {
                effectiveDoca = -effectiveToMid - halfWireDistance;
            }
        }        
    }
    
    public void calc_effectiveDoca_singleHit(){  
        effectiveVar = var_single/weight_single;
        effectiveDoca = doca_single;
    }
    
    public void calc_effectiveMeasVars_uRWell(){
        effectiveXYVars_uRWell = new double[]{10., 10.};
        for(int i = 0; i < 2; i++)
            effectiveXYVars_uRWell[i] = xyVars_uRWell[i]/weight_uRWell;
    }
    
    public double get_EffectiveDoca(){
        return effectiveDoca;
    }
    
    public double get_EffectiveVar(){
        return effectiveVar;
    }
    
    public double[] get_EffectiveXYVars_uRWell(){
        return effectiveXYVars_uRWell;
    }
    
    public int get_IndexReferenceWire(){
        return indexReferenceWire;
    }
    
    public double calc_updatedWeight_singleHit(double residual, double annealingFactor){
       double factor = 1/Math.sqrt(2 * Math.PI * annealingFactor * var_single);
        
        double Chi2 = residual * residual/var_single;
        double Phi = factor * Math.exp(-0.5 / annealingFactor * Chi2);
        double Lambda = factor * Math.exp(-0.5 / annealingFactor * dafChi2Cut);
        double sum = Phi + Lambda;
        double updatedWeight = Phi/sum;        
        
        if(updatedWeight < 1.e-100) updatedWeight = 1.e-100;        
        return updatedWeight;
    }
    
    public double[] calc_updatedWeights_doubleHits(double[] residuals, double annealingFactor){                
        double factor = 1/Math.sqrt(2 * Math.PI * annealingFactor * vars_double[0] * vars_double[1]);
        
        double[] Chi2 = new double[2];
        double[] Phi = new double[2];
        double[] Lambda = new double[2];
        double sum = 0;
        for(int i = 0; i < 2; i++){
            Chi2[i] = residuals[i] * residuals[i]/vars_double[i];
            Phi[i] = factor * Math.exp(-0.5 / annealingFactor * Chi2[i]);
            Lambda[i] = factor * Math.exp(-0.5 / annealingFactor * dafChi2Cut);
            sum += (Phi[i] + Lambda[i]);
        }        
        
        double[] updatedWeights = {0.5, 0.5};
        for(int i = 0; i < 2; i++){
            updatedWeights[i] = Phi[i]/sum;
            if(updatedWeights[i] < 1.e-100) updatedWeights[i] = 1.e-100;
        }
        
        return updatedWeights;
    }
    
    public double calc_updatedWeight_uRWell(double[] residuals, double annealingFactor){
        double factor = 1/(2 * Math.PI) / Math.sqrt( annealingFactor * xyVars_uRWell[0] * xyVars_uRWell[1]);
        
        double Chi2 = residuals[0] * residuals[0]/xyVars_uRWell[0] + residuals[1] * residuals[1]/xyVars_uRWell[1];
        double Phi = factor * Math.exp(-0.5 / annealingFactor * Chi2);
        double Lambda = factor * Math.exp(-0.5 / annealingFactor * dafChi2Cut);
        double sum = Phi + Lambda;
        double updatedWeight = Phi/sum;        
        
        if(updatedWeight < 1.e-100) updatedWeight = 1.e-100;        
        return updatedWeight;
        
    }
    
}


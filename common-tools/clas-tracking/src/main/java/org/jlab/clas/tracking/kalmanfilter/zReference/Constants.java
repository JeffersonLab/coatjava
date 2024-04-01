package org.jlab.clas.tracking.kalmanfilter.zReference;

import java.util.logging.Logger;

/**
 * Constants used in forward tracking
 * 
 * author: Tongtong
 */
public class Constants {

    // private constructor for a singleton
    private Constants() {
    }
    
    
    public static Logger LOGGER = Logger.getLogger(Constants.class.getName());
    
    // CONSTATNS for TRANSFORMATION
    public static final double ITERSTOPXHB = 1.2e-2; 
    public static final double ITERSTOPYHB = 1.4e-1;  
    public static final double ITERSTOPTXHB = 2.5e-4;  
    public static final double ITERSTOPTYHB = 1.0e-3;  
    public static final double ITERSTOPQHB = 1.6e-3;  
    
    public static final double ITERSTOPXTB = 5.5e-5; 
    public static final double ITERSTOPYTB = 8.0e-4;  
    public static final double ITERSTOPTXTB = 2.1e-6;  
    public static final double ITERSTOPTYTB = 3.5e-6;  
    public static final double ITERSTOPQTB = 1.1e-5;       

    
    public static final double ITERSTOPXHBSTR4PARAS = 6.0e-5; 
    public static final double ITERSTOPYHBSTR4PARAS = 3.9e-3;  
    public static final double ITERSTOPTXHBSTR4PARAS = 3.3e-7;  
    public static final double ITERSTOPTYHBSTR4PARAS = 2.2e-5;  
    
    public static final double ITERSTOPXTBSTR4PARAS = 3.6e-4; 
    public static final double ITERSTOPYTBSTR4PARAS = 3.4e-3;  
    public static final double ITERSTOPTXTBSTR4PARAS = 4.4e-6;  
    public static final double ITERSTOPTYTBSTR4PARAS = 4.1e-5;    
}
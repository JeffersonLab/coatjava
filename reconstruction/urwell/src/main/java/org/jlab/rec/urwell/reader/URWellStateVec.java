package org.jlab.rec.urwell.reader;

/**
 * @author Tongtong
 *
 */

public class URWellStateVec {	
    private double _x;
    private double _y;
    private double _z;
    private double _tx;
    private double _ty;
    private double _Q;
    private double _B;
    private double _PathLength;
    private double _DAFWeight = -999;
    
    /**
     * Instantiates a new  vec.
     */
    public URWellStateVec(double x, double y, double z, double tx, double ty, double Q, double B, double pathLength) {
        _x = x;
        _y = y;
        _z = z;
        _tx = tx;
        _ty = ty;
        _Q = Q;
        _B = B;
        _PathLength = pathLength;
    }

    public double getB() {
            return _B;
    }
    
    public double setB(double B) {
            return this._B = B;
    }

    public double getPathLength() {
        return _PathLength;
    }

    public void setPathLength(double _PathLength) {
        this._PathLength = _PathLength;
    }
    
    
    public double getDAFWeight() {
        return _DAFWeight;
    }

    public void setDAFWeight(double weight) {
        this._DAFWeight = weight;
    } 
    


    /**
     * Description of x().
     *
     * @return the x component
     */
    public double x() {
            return _x;
    }

    /**
     * Description of y().
     *
     * @return the y component
     */ 

    public double y() {
            return _y;
    }
    
    /**
     * Description of z().
     *
     * @return z
     */
    public double z() {
            return _z;
    }

    /**
     * Description of tanThetaX().
     *
     * @return the tanThetaX component
     */ 
    public double tx() {
            return _tx;
    }

    /**
     * Description of tanThetaY().
     *
     * @return the tanThetaY component
     */ 
    public double ty() {
            return _ty;
    }
    
    /**
     * Description of Q().
     *
     * @return the Q component
     */ 
    public double Q() {
            return _Q;
    }    


    public void printInfo() {
            System.out.println("StateVec [ "+this.x()+", "+this.y()+", "+this.tx()+", "+this.ty()+", "+this.Q()+" ] ");
    }

}

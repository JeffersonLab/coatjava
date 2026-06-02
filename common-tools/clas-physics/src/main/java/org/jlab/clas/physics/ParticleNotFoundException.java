package org.jlab.clas.physics;

/**
 *
 * @author gavalian
 */
public class ParticleNotFoundException extends Exception {
    String message = "";
    
    public ParticleNotFoundException(String msg){
        super(msg);
        this.message = msg;
    }
    
    public ParticleNotFoundException(Throwable cause){
        super(cause);
    }
    
    @Override
    public String toString(){
        return message;
    }
    
    @Override
    public String getMessage(){
        return message;
    }
}

/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.jlab.rec.rvt;

import java.util.ArrayList;
import java.util.List;
import org.jlab.clas.swimtools.Swim;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;


/**
 *
 * @author ziegler
 */
public class PixelLinker {
    public double width = 20; //cm
    double R = 257.512698;
    double n = 809;
    double angle = 2*Math.PI/(double)n;
    
    public List<Pixel> simulatePixels(DataEvent event, Swim swim) {
        if (event.hasBank("MC::Particle") == false) {
            return null;
        }
        List<Pixel> pixels = new ArrayList<>();
        DataBank bank = event.getBank("MC::Particle");
        for(int i =0; i<bank.rows(); i++) {
            Pixel pix = this.simulatePixel(event, swim, i);
            if(pix!=null)
                pixels.add(pix);
        }
        return pixels;
    }
    
    public Pixel simulatePixel(DataEvent event, Swim swim, 
            int mcRow) {
        Pixel pix = new Pixel();
        
        if (event.hasBank("MC::Particle") == false) {
            return null;
        }
        DataBank bank = event.getBank("MC::Particle");
        
        // fills the arrays corresponding to the variables
        if(bank!=null) {
            int q = this.getCharge(bank.getInt("pid", mcRow));
            double x = (double) bank.getFloat("vx", mcRow);
            double y = (double) bank.getFloat("vy", mcRow);
            double z = (double) bank.getFloat("vz", mcRow);
            double px = (double) bank.getFloat("px", mcRow);
            double py = (double) bank.getFloat("py", mcRow);
            double pz = (double) bank.getFloat("pz", mcRow);
            swim.SetSwimParameters(x, y, z, px, py, pz, q);
        
            double[] swpars = swim.SwimRho(R);
            double xf = swpars[0]*10;
            double yf = swpars[1]*10;
            double zf = swpars[2]*10;
            
            double min=Double.POSITIVE_INFINITY;
            for(int i =0; i<n; i++) {
                double angl_i = angle/2+i*angle;
                double xp = R*Math.cos(angl_i);
                double yp = R*Math.sin(angl_i);
                double D2=(xf-xp)*(xf-xp)+(yf-yp)*(yf-yp);

                if(D2<min) {
                    min=D2;
                    pix.x = xp;
                    pix.y = yp;
                    pix.z = zf;
                } 
            }
        }
        return pix;
    }

    private int getCharge(int pid) {
        if((int)Math.abs(pid/100)==0) {
            return (int)-Math.signum(pid);
        }   else {
            return (int)Math.signum(pid);
        }
    }
    public class Pixel {
        public final double e = width/Math.sqrt(12);
        public final double w = width;
        public double x;
        public double y;
        public double z;
    }
}

package org.jlab.rec.atof.Hit;

import org.jlab.detector.calib.utils.DatabaseConstantProvider;
import org.jlab.geom.base.Detector;
import org.jlab.geom.detector.alert.ATOF.AlertTOFFactory;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.io.hipo.HipoDataSource;
import java.util.ArrayList;

/**
 *
 * @author npilleux
 */
public class BarHit {
    
    //A bar hit is the combination of a downstream and upstream hits
    private AtofHit hit_up, hit_down;
    private double x,y,z, time, energy;
    int sector, layer;
    
    public AtofHit getHitUp() {
        return hit_up;
    }

    public void setHitUp(AtofHit hit_up) {
        this.hit_up = hit_up;
    }

    public AtofHit getHitDown() {
        return hit_down;
    }

    public void setHitDown(AtofHit hit_down) {
        this.hit_down = hit_down;
    }

    public double getX() {
        return x;
    }

    public void setX(double x) {
        this.x = x;
    }

    public double getY() {
        return y;
    }

    public void setY(double y) {
        this.y = y;
    }

    public double getZ() {
        return z;
    }

    public void setZ(double z) {
        this.z = z;
    }
    
    public void computeZ() {
        double some_calibration = 10; //Here read the calibration DB
        this.z = some_calibration * (hit_up.getTime() - hit_down.getTime());
    }
    
    public void computeTime() {
        double some_calibration = 10; //Here read the calibration DB
        this.time = some_calibration + ((hit_up.getTime() + hit_down.getTime())/2.);
    }
    
    public void computeEnergy() {
        this.energy = (hit_up.getEnergy() + hit_down.getEnergy());
    }

    public double getTime() {
        return time;
    }

    public void setTime(double time) {
        this.time = time;
    }

    public double getEnergy() {
        return energy;
    }

    public void setEnergy(double energy) {
        this.energy = energy;
    }

    public int getSector() {
        return sector;
    }

    public void setSector(int sector) {
        this.sector = sector;
    }

    public int getLayer() {
        return layer;
    }

    public void setLayer(int layer) {
        this.layer = layer;
    }
    
    public BarHit(AtofHit hit_down, AtofHit hit_up) 
	{
            boolean hits_match = hit_down.barmatch(hit_up);
            if(!hits_match) throw new UnsupportedOperationException("Hits do not match \n");
            
            this.hit_up = hit_up;
            this.hit_down = hit_down;
            this.layer = hit_up.getLayer();
            this.sector = hit_up.getSector();
            this.x = hit_up.getX();
            this.y = hit_up.getY(); 
            this.computeZ(); 
            this.computeTime();
            this.computeEnergy();
	}
    
    /**
     * @param args the command line arguments
     */
        public static void main(String[] args) {
        // TODO code application logic here
        AlertTOFFactory factory = new AlertTOFFactory();
        DatabaseConstantProvider cp = new DatabaseConstantProvider(11, "default");
        Detector atof = factory.createDetectorCLAS(cp);
        
        //Input to be read
        String input = "/Users/npilleux/Desktop/alert/atof-reconstruction/coatjava/reconstruction/alert/src/main/java/org/jlab/rec/atof/Hit/test_tdc_atof.hipo";
        HipoDataSource reader = new HipoDataSource();
        reader.open(input);

        int event_number = 0;
        while (reader.hasEvent()) {
            DataEvent event = (DataEvent) reader.getNextEvent();
            event_number++;
            DataBank bank = event.getBank("ATOF::tdc");
            int nt = bank.rows(); // number of tracks 
            ArrayList<AtofHit> hit_up = new ArrayList<>();  
            ArrayList<AtofHit> hit_down = new ArrayList<>();  
            ArrayList<AtofHit> hit_wedge = new ArrayList<>();  
            for (int i = 0; i < nt; i++) {
                int sector = bank.getInt("sector", i);
                int layer = bank.getInt("layer", i);
                int component = bank.getInt("component", i);
                int order = bank.getInt("order", i);
                int tdc = bank.getInt("TDC", i);
                int tot = bank.getInt("ToT", i);
                AtofHit hit = new AtofHit(sector, layer, component, order, tdc, tot, atof);
                if(null == hit.getType()) System.out.print("Undefined hit type \n");
                else switch (hit.getType()) {
                    case "bar up" -> hit_up.add(hit);
                    case "bar down" -> hit_down.add(hit);
                    case "wedge" -> hit_wedge.add(hit);
                    default -> System.out.print("Undefined hit type \n");
                }
            }
            ArrayList<BarHit> bar_hits = new ArrayList<BarHit>(); 
            for(int i_up=0; i_up<hit_up.size();i_up++)
            {
                AtofHit this_hit_up = hit_up.get(i_up);
                for(int i_down=0; i_down<hit_down.size();i_down++)
                {
                   AtofHit this_hit_down = hit_down.get(i_down); 
                   if(this_hit_up.barmatch(this_hit_down)) 
                   {
                       BarHit this_bar_hit = new BarHit(this_hit_up, this_hit_down);
                       bar_hits.add(this_bar_hit);
                   }
                }
            }
            }
        }
    }

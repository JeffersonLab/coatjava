package org.jlab.rec.atof.Hit;

import java.util.ArrayList;
import org.jlab.detector.calib.utils.DatabaseConstantProvider;
import org.jlab.geom.base.Detector;
import org.jlab.geom.detector.alert.ATOF.AlertTOFFactory;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.io.hipo.HipoDataSource;
/**
 *
 * @author npilleux
 */
public class HitFinder {
    
    private ArrayList<BarHit> bar_hits;
    private ArrayList<AtofHit> wedge_hits;
    
    public HitFinder() 
	{
		this.bar_hits = new ArrayList<>();  
		this.wedge_hits = new ArrayList<>();  
        }
    
    // Getter and Setter for bar_hits
    public ArrayList<BarHit> getBarHits() {
        return bar_hits;
    }

    public void setBarHits(ArrayList<BarHit> bar_hits) {
        this.bar_hits = bar_hits;
    }

    // Getter and Setter for wedge_hits
    public ArrayList<AtofHit> getWedgeHits() {
        return wedge_hits;
    }

    public void setWedgeHits(ArrayList<AtofHit> wedge_hits) {
        this.wedge_hits = wedge_hits;
    }
    
    public void FindHits(DataEvent event, Detector atof) {

            DataBank bank = event.getBank("ATOF::tdc");
            int nt = bank.rows(); // number of hits
            ArrayList<AtofHit> hit_up = new ArrayList<>();  
            ArrayList<AtofHit> hit_down = new ArrayList<>();
 
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
                    case "wedge" -> this.wedge_hits.add(hit);
                    default -> System.out.print("Undefined hit type \n");
                }
            }
            for(int i_up=0; i_up<hit_up.size();i_up++)
            {
                AtofHit this_hit_up = hit_up.get(i_up);
                for(int i_down=0; i_down<hit_down.size();i_down++)
                {
                   AtofHit this_hit_down = hit_down.get(i_down); 
                   if(this_hit_up.barmatch(this_hit_down)) 
                   {
                       BarHit this_bar_hit = new BarHit(this_hit_up, this_hit_down);
                       this.bar_hits.add(this_bar_hit);
                   }
                }
            }
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
        
        HitFinder hitfinder = new HitFinder();

        int event_number = 0;
        while (reader.hasEvent()) {
            DataEvent event = (DataEvent) reader.getNextEvent();
            event_number++;
            hitfinder.FindHits(event, atof);
        }
    }
}
   
package org.jlab.rec.atof.Hit;

import cnuphys.magfield.MagneticFields;
import java.util.ArrayList;
import java.util.Collections;
import org.jlab.clas.swimtools.Swim;
import org.jlab.detector.calib.utils.DatabaseConstantProvider;
import org.jlab.geom.base.Detector;
import org.jlab.geom.detector.alert.ATOF.AlertTOFFactory;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.io.hipo.HipoDataSource;
import org.jlab.rec.atof.TrackMatch.TrackProjector;
import org.jlab.utils.CLASResources;
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
    
    public void FindHits(DataEvent event, Detector atof, TrackProjector track_projector) {

            //For each event a list of bar hits and a list of wedge hits are filled
            this.bar_hits.clear();
            this.wedge_hits.clear();
            //They are read from the ATOF TDC bank
            DataBank bank = event.getBank("ATOF::tdc");
            int nt = bank.rows(); // number of hits
            //Hits in the bar downstream and upstream will be matched
            ArrayList<AtofHit> hit_up = new ArrayList<>();  
            ArrayList<AtofHit> hit_down = new ArrayList<>();
            //Looping through all hits
            for (int i = 0; i < nt; i++) {
                //Getting their properties
                int sector = bank.getInt("sector", i);
                int layer = bank.getInt("layer", i);
                int component = bank.getInt("component", i);
                int order = bank.getInt("order", i);
                int tdc = bank.getInt("TDC", i);
                int tot = bank.getInt("ToT", i);
                //Building a Hit
                AtofHit hit = new AtofHit(sector, layer, component, order, tdc, tot, atof);
                if(hit.getEnergy() < 0.1 )continue; //energy threshold
                //Sorting the hits into wedge, upstream and downstream bar hits
                //Lists are built for up/down bar to match them after
                //Wedge hits are mayched to ahdc tracks and listed 
                if(null == hit.getType()) System.out.print("Undefined hit type \n");
                else switch (hit.getType()) {
                    case "bar up" -> hit_up.add(hit);
                    case "bar down" -> hit_down.add(hit);
                    case "wedge" -> {hit.MatchTrack(track_projector); this.wedge_hits.add(hit);}
                    default -> System.out.print("Undefined hit type \n");
                }
            }//End loop through all hits
            
            //Starting loop through up hits in the bar
            for(int i_up=0; i_up<hit_up.size();i_up++)
            {
                AtofHit this_hit_up = hit_up.get(i_up);
                //Starting loop through down hits in the bar
                for(int i_down=0; i_down<hit_down.size();i_down++)
                {
                   AtofHit this_hit_down = hit_down.get(i_down); 
                   //Matching the hits: if same module and different order, they make up a bar hit
                   if(this_hit_up.barmatch(this_hit_down)) 
                   {
                       //Bar hits are matched to ahdc tracks and listed
                       BarHit this_bar_hit = new BarHit(this_hit_up, this_hit_down);
                       this_bar_hit.MatchTrack(track_projector);
                       this.bar_hits.add(this_bar_hit);
                   }
                }
            }
            //Once all has been listed, hits are sorted by energy
            Collections.sort(this.bar_hits, (hit1, hit2) -> Double.compare(hit1.getEnergy(), hit2.getEnergy()));
            Collections.sort(this.wedge_hits, (hit1, hit2) -> Double.compare(hit1.getEnergy(), hit2.getEnergy()));
        }
    

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        
        //Building ALERT geometry
        AlertTOFFactory factory = new AlertTOFFactory();
        DatabaseConstantProvider cp = new DatabaseConstantProvider(11, "default");
        Detector atof = factory.createDetectorCLAS(cp);

        //READING MAG FIELD MAP
        System.setProperty("CLAS12DIR", "../../");
        String mapDir = CLASResources.getResourcePath("etc") + "/data/magfield";
        try {
            MagneticFields.getInstance().initializeMagneticFields(mapDir,
                    "Symm_torus_r2501_phi16_z251_24Apr2018.dat", "Symm_solenoid_r601_phi1_z1201_13June2018.dat");
        } catch (Exception e) {
            e.printStackTrace();
        }
        float[] b = new float[3];
        Swim swimmer = new Swim();
        swimmer.BfieldLab(0, 0, 0, b);
        double B = Math.abs(b[2]);

        //Track Projector Initialisation with B field
        TrackProjector projector = new TrackProjector();
        projector.setB(B);
        
        //Hit finder init
        HitFinder hitfinder = new HitFinder();
        
        //Input to be read
        String input = "/Users/npilleux/Desktop/alert/atof-reconstruction/coatjava/reconstruction/alert/src/main/java/org/jlab/rec/atof/Hit/update_protons_to_test_with_tracks.hipo";
        HipoDataSource reader = new HipoDataSource();
        reader.open(input);

        int event_number = 0;
        while (reader.hasEvent()) {
            DataEvent event = (DataEvent) reader.getNextEvent();
            
            event_number++;

            projector.ProjectTracks(event);
            hitfinder.FindHits(event, atof, projector);
        }

        System.out.print("Read " + event_number + " events");
    }
}
   
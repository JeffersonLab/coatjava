package org.jlab.rec.atof.hit;

import java.util.ArrayList;
import java.util.Collections;
import org.jlab.geom.base.Detector;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;

/**
 * The {@code HitFinder} class finds hits in the atof.
 *
 * <p>
 * Uses atof tdc bank information
 *
 * Creates a {@link ArrayList} of {@link BarHit} for bar hits read. Creates a
 * {@link ArrayList} of {@link ATOFHit} for wedge hits read.
 *
 * </p>
 *
 * @author pilleux
 */
public class HitFinder {

    /**
     * list of bar hits
     */
    private ArrayList<BarHit> barHits;
    /**
     * list of wedge hits
     */
    private ArrayList<ATOFHit> wedgeHits;

    /**
     * Default constructor that initializes the list of hits as new empty lists.
     */
    public HitFinder() {
        this.barHits = new ArrayList<>();
        this.wedgeHits = new ArrayList<>();
    }

    // Getter and Setter for barHits
    public ArrayList<BarHit> getBarHits() {
        return barHits;
    }

    public void setBarHits(ArrayList<BarHit> bar_hits) {
        this.barHits = bar_hits;
    }

    public ArrayList<ATOFHit> getWedgeHits() {
        return wedgeHits;
    }

    public void setWedgeHits(ArrayList<ATOFHit> wedge_hits) {
        this.wedgeHits = wedge_hits;
    }

    /**
     * Find hits in the event, matches them to tracks found in the ahdc and
     * build their properties.
     *
     * @param event the {@link DataEvent} containing hits.
     * @param atof the {@link Detector} representing the atof geometry to match
     * the sector/layer/component to x/y/z.
     */
    public void findHits(DataEvent event, Detector atof, Float startTime) {
        //For each event a list of bar hits and a list of wedge hits are filled
        this.barHits.clear();
        this.wedgeHits.clear();
        //They are read from the ATOF TDC bank
        DataBank bank = event.getBank("ATOF::tdc");
        //Check that the event start time is defined are done in the engine
        //if (event.hasBank("REC::Event") && 
        //        event.getBank("REC::Event").getFloat("startTime", 0)!=-1000)
        
        int nt = bank.rows(); // number of hits
        //Hits in the bar downstream and upstream will be matched
        ArrayList<ATOFHit> hit_up = new ArrayList<>();
        ArrayList<ATOFHit> hit_down = new ArrayList<>();
        
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
            ATOFHit hit = new ATOFHit(sector, layer, component, order, tdc, tot, startTime, atof);
            if (hit.getEnergy() < 0.01) {
                continue; //energy threshold
            }

            //Sorting the hits into wedge, upstream and downstream bar hits
            //Lists are built for up/down bar to match them after
            //Wedge hits are mayched to ahdc tracks and listed 
            if (null == hit.getType()) {
                System.out.print("Undefined hit type \n");
            } else {
                switch (hit.getType()) {
                    case "bar up" ->
                        hit_up.add(hit);
                    case "bar down" ->
                        hit_down.add(hit);
                    case "wedge" -> {
                        this.wedgeHits.add(hit);
                    }
                    default ->
                        System.out.print("Undefined hit type \n");
                }
            }
        }//End loop through all hits

        //Starting loop through up hits in the bar
        for (int i_up = 0; i_up < hit_up.size(); i_up++) {
            ATOFHit this_hit_up = hit_up.get(i_up);
            int countMatches = 0;
            //Starting loop through down hits in the bar
            for (int i_down = 0; i_down < hit_down.size(); i_down++) {
                ATOFHit this_hit_down = hit_down.get(i_down);
                //Matching the hits: if same module and different order, they make up a bar hit
                if (this_hit_up.matchBar(this_hit_down)) {
                    //Bar hits are matched to ahdc tracks and listed
                    BarHit this_bar_hit = new BarHit(this_hit_down, this_hit_up);
                    //Only add bar hits for which the time sum is in time
                    if(!this_bar_hit.isInTime()) continue;
                    this.barHits.add(this_bar_hit);
                    countMatches++;
                }
            }
        }
        //Once all has been listed, hits are sorted by energy
        Collections.sort(this.barHits, (hit1, hit2) -> Double.compare(hit2.getEnergy(), hit1.getEnergy()));
        Collections.sort(this.wedgeHits, (hit1, hit2) -> Double.compare(hit2.getEnergy(), hit1.getEnergy()));
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
    }
}

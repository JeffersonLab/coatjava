package org.jlab.rec.recoiltof.hit;

import java.util.ArrayList;
import java.util.Collections;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;

/**
 * The {@code HitFinder} class finds hits in the recoil tof.
 *
 * <p>
 * Uses recoil tof tdc bank information
 *
 * Creates a {@link ArrayList} of {@link BarHit} for bar hits read.
 *
 * </p>
 *
 * @author pilleux, Nilanga Wickramaarachchi 
 */
public class HitFinder {

    /**
     * list of bar hits
     */
    private ArrayList<BarHit> barHits;

    /**
     * Default constructor that initializes the list of hits as new empty lists.
     */
    public HitFinder() {
        this.barHits = new ArrayList<>();
    }

    // Getter and Setter for barHits
    public ArrayList<BarHit> getBarHits() {
        return barHits;
    }

    public void setBarHits(ArrayList<BarHit> bar_hits) {
        this.barHits = bar_hits;
    }

    /**
     *
     * @param event the {@link DataEvent} containing hits.
     *
     */
    public void findHits(DataEvent event) {
        //For each event a list of bar hits is filled
        this.barHits.clear();
        //They are read from the RECOILTOF TDC bank
        DataBank bank = event.getBank("RECOILTOF::tdc");
        int nt = bank.rows(); // number of hits
        //Hits in the bar downstream and upstream will be matched
        ArrayList<RECOILTOFHit> hit_up = new ArrayList<>();
        ArrayList<RECOILTOFHit> hit_down = new ArrayList<>();
        
        //Looping through all hits
        for (int i = 0; i < nt; i++) {
            //Getting their properties
            int sector = bank.getInt("sector", i);
            int row = bank.getInt("row", i);
            int column = bank.getInt("column", i);
            int order = bank.getInt("order", i);
            int tdc = bank.getInt("TDC", i);
            int tot = bank.getInt("ToT", i);

            //Building a Hit
            RECOILTOFHit hit = new RECOILTOFHit(sector, row, column, order, tdc, tot);
            if (hit.getEnergy() < 0.01) {
                continue; //energy threshold
            }

            //Sorting the hits into upstream and downstream bar hits
            //Lists are built for up/down bar to match them after
            if (null == hit.getType()) {
                System.out.print("Undefined hit type \n");
            } else {
                switch (hit.getType()) {
                    case "bar up" ->
                        hit_up.add(hit);
                    case "bar down" ->
                        hit_down.add(hit);
                    default ->
                        System.out.print("Undefined hit type \n");
                }
            }
        }//End loop through all hits

        //Starting loop through up hits in the bar
        for (int i_up = 0; i_up < hit_up.size(); i_up++) {
            RECOILTOFHit this_hit_up = hit_up.get(i_up);
            int countMatches = 0;
            //Starting loop through down hits in the bar
            for (int i_down = 0; i_down < hit_down.size(); i_down++) {
                RECOILTOFHit this_hit_down = hit_down.get(i_down);
                //Matching the hits: if same bar and different order, they make up a bar hit
                if (this_hit_up.matchBar(this_hit_down)) {
                    if (countMatches > 0) {
                        //If the up hit was already involved in a match, do not make an additionnal match
                        //Chosing to ignore double matches for now because it happened for <1% of events in cosmic runs
                        continue;
                    }
                    BarHit this_bar_hit = new BarHit(this_hit_down, this_hit_up);
                    this.barHits.add(this_bar_hit);
                    countMatches++;
                }
            }
        }
        //Once all has been listed, hits are sorted by energy
        Collections.sort(this.barHits, (hit1, hit2) -> Double.compare(hit2.getEnergy(), hit1.getEnergy()));
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
    }
}

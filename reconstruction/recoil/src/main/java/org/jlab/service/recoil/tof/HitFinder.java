package org.jlab.rec.recoil.tof;

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
 * Creates a {@link ArrayList} of {@link RTOFHit} for rtof hits read.
 *
 * </p>
 *
 * @author pilleux, Nilanga Wickramaarachchi 
 */
public class HitFinder {

    /**
     * list of rtof hits
     */
    private ArrayList<RTOFHit> rtofHits;

    /**
     * Default constructor that initializes the list of hits as new empty lists.
     */
    public HitFinder() {
        this.rtofHits = new ArrayList<>();
    }

    // Getter and Setter for rtofHits
    public ArrayList<RTOFHit> getRTOFHits() {
        return rtofHits;
    }

    public void setRTOFHits(ArrayList<RTOFHit> rtof_hits) {
        this.rtofHits = rtof_hits;
    }

    /**
     *
     * @param event the {@link DataEvent} containing hits.
     *
     */
    public void findHits(DataEvent event) {
        //For each event a list of rtof hits is filled
        this.rtofHits.clear();
        //They are read from the RTOF TDC bank
        DataBank bank = event.getBank("RTOF::tdc");
        int nt = bank.rows(); // number of hits
        //Hits in the bar downstream and upstream will be matched
        ArrayList<RTOFRawHit> hit_up = new ArrayList<>();
        ArrayList<RTOFRawHit> hit_down = new ArrayList<>();
        
        //Looping through all hits
        for (int i = 0; i < nt; i++) {
            //Getting their properties
            int sector = bank.getByte("sector", i);
            int layer = bank.getByte("layer", i);
            int component = bank.getByte("component", i);
            int order = bank.getShort("order", i);
            int tdc = bank.getShort("TDC", i);
            int tot = bank.getShort("ToT", i);

            //Building a Hit
            RTOFRawHit hit = new RTOFRawHit(sector, layer, component, order, tdc, tot);
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
            RTOFRawHit this_hit_up = hit_up.get(i_up);
            int countMatches = 0;
            //Starting loop through down hits in the bar
            for (int i_down = 0; i_down < hit_down.size(); i_down++) {
                RTOFRawHit this_hit_down = hit_down.get(i_down);
                //Matching the hits: if same bar and different order, they make up a rtof hit
                if (this_hit_up.matchBar(this_hit_down)) {
                    if (countMatches > 0) {
                        //If the up hit was already involved in a match, do not make an additionnal match
                        //Chosing to ignore double matches for now because it happened for <1% of events in cosmic runs
                        continue;
                    }
                    RTOFHit this_rtof_hit = new RTOFHit(this_hit_down, this_hit_up);
                    this.rtofHits.add(this_rtof_hit);
                    countMatches++;
                }
            }
        }
        //Once all has been listed, hits are sorted by energy
        Collections.sort(this.rtofHits, (hit1, hit2) -> Double.compare(hit2.getEnergy(), hit1.getEnergy()));
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
    }
}

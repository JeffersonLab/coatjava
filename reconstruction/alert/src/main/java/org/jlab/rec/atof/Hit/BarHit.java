package org.jlab.rec.atof.hit;

import org.jlab.detector.calib.utils.DatabaseConstantProvider;
import org.jlab.geom.base.Detector;
import org.jlab.geom.detector.alert.ATOF.AlertTOFFactory;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.io.hipo.HipoDataSource;
import java.util.ArrayList;
import org.jlab.rec.atof.constants.Parameters;

/**
 *
 * Represents a hit in the atof bar. Extends class AtofHit. Is further defined
 * by the two hits upstream and downstream composing a full bar hit. z position,
 * time and energy are defined from the up/down hits.
 *
 * @author npilleux
 */
public class BarHit extends AtofHit {

    //A bar hit is the combination of a downstream and upstream hits
    private AtofHit hit_up, hit_down;

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

    /**
     * Computes bar hit z coordinate from up/downstream hit times.
     * 
     */
    public final void computeZ() {
        this.setZ(Parameters.VEFF/2. * (hit_up.getTime() - hit_down.getTime()));
    }

    /**
     * Computes bar hit time from up/downstream hit times.
     * The time is set as the time of the most energetic hit.
     * It is corrected for propagation time.
     * 
     */
    public final void computeTime() {
        //We pick the most energetic signal as the timing signal
        double time_at_sipm, distance_to_sipm;
        if(this.hit_down.getEnergy() > this.hit_up.getEnergy()) {
            time_at_sipm = this.hit_down.getTime();
            distance_to_sipm = Parameters.LENGTH_ATOF/2. - this.getZ();
        }
        else {
            time_at_sipm = this.hit_up.getTime();
            distance_to_sipm = Parameters.LENGTH_ATOF/2. + this.getZ();
        }
        this.setTime(time_at_sipm - distance_to_sipm/Parameters.VEFF);
    }

    /**
     * Computes bar hit energy from up/downstream hits.
     * The energy of the up/downstream hits is corrected for attenuation now that z is known.
     * The energy of the bar hit is the sum of the energy of the up/downstream hits.
     * 
     */
    public final void computeEnergy() {
        this.computeZ();
        double distance_hit_to_sipm_up = Parameters.LENGTH_ATOF / 2. + this.getZ();
        double distance_hit_to_sipm_down = Parameters.LENGTH_ATOF / 2. - this.getZ();
        double Edep_up = hit_up.getEnergy() * Math.exp(distance_hit_to_sipm_up / Parameters.ATT_L);
        double Edep_down = hit_down.getEnergy() * Math.exp(distance_hit_to_sipm_down / Parameters.ATT_L);
        this.setEnergy(Edep_up + Edep_down);
    }

    public BarHit(AtofHit hit_down, AtofHit hit_up) {
        boolean hits_match = hit_down.matchBar(hit_up);
        if (!hits_match) {
            throw new UnsupportedOperationException("Hits do not match \n");
        }
        this.setType("bar");
        this.setOrder(2);//Fake order for bar hits
        this.hit_up = hit_up;
        this.hit_down = hit_down;
        this.setLayer(hit_up.getLayer());
        this.setSector(hit_up.getSector());
        this.setComponent(10);
        this.setX(hit_up.getX());
        this.setY(hit_up.getY());
        this.computeZ();
        this.computeTime();
        this.computeEnergy();
    }

    public BarHit() {
        super();  // Call AtofHit constructor
        //Sets some parameters to make a bar type hit
        this.setType("bar");
        this.setOrder(2);
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
    }
}

package org.jlab.rec.atof.hit;

import org.jlab.rec.atof.constants.Parameters;

/**
 *
 * Represents a hit in the atof bar. Extends class ATOFHit. Is further defined
 * by the two hits upstream and downstream composing a full bar hit. z position,
 * time and energy are defined from the up/down hits.
 *
 * @author npilleux
 */
public class BarHit extends ATOFHit {

    //A bar hit is the combination of a downstream and upstream hits
    private ATOFHit hitUp, hitDown;

    public ATOFHit getHitUp() {
        return hitUp;
    }

    public void setHitUp(ATOFHit hit_up) {
        this.hitUp = hit_up;
    }

    public ATOFHit getHitDown() {
        return hitDown;
    }

    public void setHitDown(ATOFHit hit_down) {
        this.hitDown = hit_down;
    }

    /**
     * Computes bar hit z coordinate from up/downstream hit times.
     * 
     */
    public final void computeZ() {
        this.setZ(Parameters.VEFF/2. * (hitUp.getTime() - hitDown.getTime()));
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
        if(this.hitDown.getEnergy() > this.hitUp.getEnergy()) {
            time_at_sipm = this.hitDown.getTime();
            distance_to_sipm = Parameters.LENGTH_ATOF/2. - this.getZ();
        }
        else {
            time_at_sipm = this.hitUp.getTime();
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
        double Edep_up = hitUp.getEnergy() * Math.exp(distance_hit_to_sipm_up / Parameters.ATT_L);
        double Edep_down = hitDown.getEnergy() * Math.exp(distance_hit_to_sipm_down / Parameters.ATT_L);
        this.setEnergy(Edep_up + Edep_down);
    }

    public BarHit(ATOFHit hit_down, ATOFHit hit_up) {
        boolean hits_match = hit_down.matchBar(hit_up);
        if (!hits_match) {
            throw new UnsupportedOperationException("Hits do not match \n");
        }
        this.setType("bar");
        this.setOrder(2);//Fake order for bar hits
        this.hitUp = hit_up;
        this.hitDown = hit_down;
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
        super();
        this.setType("bar");
        this.setOrder(2);//Fake order for bar hits
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
    }
}

package org.jlab.rec.recoiltof.hit;

import org.jlab.rec.recoiltof.constants.Parameters;

/**
 *
 * Represents a hit in the recoil tof bar. Extends class RECOILTOFHit. Is further defined
 * by the two hits upstream and downstream composing a full bar hit. y position,
 * time and energy are defined from the up/down hits.
 *
 * @author npilleux, Nilanga Wickramaarachchi 
 */
public class BarHit extends RECOILTOFHit {

    //A bar hit is the combination of a downstream and upstream hits
    private RECOILTOFHit hitUp, hitDown;

    public RECOILTOFHit getHitUp() {
        return hitUp;
    }

    public void setHitUp(RECOILTOFHit hit_up) {
        this.hitUp = hit_up;
    }

    public RECOILTOFHit getHitDown() {
        return hitDown;
    }

    public void setHitDown(RECOILTOFHit hit_down) {
        this.hitDown = hit_down;
    }

    /**
     * Computes bar hit y coordinate from up/downstream hit times.
     * 
     */
    public final void computeY() {
        this.setY(Parameters.VEFF/2. * (hitUp.getTime() - hitDown.getTime()));
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
	    if(this.hitDown.getRow() == 3) distance_to_sipm = Parameters.SHORT_BAR_LENGTH/2. - this.getY();
	    else distance_to_sipm = Parameters.LONG_BAR_LENGTH/2. - this.getY();
        }
        else {
            time_at_sipm = this.hitUp.getTime();
	    if(this.hitUp.getRow() == 3) distance_to_sipm = Parameters.SHORT_BAR_LENGTH/2. + this.getY();
            else distance_to_sipm = Parameters.LONG_BAR_LENGTH/2. + this.getY();
        }
        this.setTime(time_at_sipm - distance_to_sipm/Parameters.VEFF);
    }

    /**
     * Computes bar hit energy from up/downstream hits.
     * The energy of the up/downstream hits is corrected for attenuation now that y is known.
     * The energy of the bar hit is the sum of the energy of the up/downstream hits.
     * 
     */
    public final void computeEnergy() {
        this.computeY();
        double distance_hit_to_sipm_up, distance_hit_to_sipm_down;
	
	if (hitUp.getRow() == 3) distance_hit_to_sipm_up = Parameters.SHORT_BAR_LENGTH / 2. + this.getY();
	else distance_hit_to_sipm_up = Parameters.LONG_BAR_LENGTH / 2. + this.getY();
	
	if (hitDown.getRow() == 3) distance_hit_to_sipm_down = Parameters.SHORT_BAR_LENGTH / 2. - this.getY();
	else distance_hit_to_sipm_down = Parameters.LONG_BAR_LENGTH / 2. - this.getY();
	
        double Edep_up = hitUp.getEnergy() * Math.exp(distance_hit_to_sipm_up / Parameters.ATT_L);
        double Edep_down = hitDown.getEnergy() * Math.exp(distance_hit_to_sipm_down / Parameters.ATT_L);
        this.setEnergy(Edep_up + Edep_down);
    }

    public BarHit(RECOILTOFHit hit_down, RECOILTOFHit hit_up) {
        boolean hits_match = hit_down.matchBar(hit_up);
        if (!hits_match) {
            throw new UnsupportedOperationException("Hits do not match \n");
        }
        this.setType("bar");
        this.setOrder(2);//Fake order for bar hits
        this.hitUp = hit_up;
        this.hitDown = hit_down;
	this.setSector(hit_up.getSector());
        this.setRow(hit_up.getRow());
        this.setColumn(hit_up.getColumn());
        this.setX(hit_up.getX());
        this.setZ(hit_up.getZ());
        this.computeY();
        this.computeTime();
        this.computeEnergy();
        this.setTdc((hit_down.getTdc() + hit_up.getTdc())/2);
        this.setTot((hit_down.getTot() + hit_up.getTot()));
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

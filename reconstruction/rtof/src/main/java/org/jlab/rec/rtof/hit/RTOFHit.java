package org.jlab.rec.rtof.hit;

import org.jlab.rec.rtof.constants.Parameters;
import org.jlab.detector.geant4.v2.rtof.RTOFConstants;

/**
 *
 * Represents a hit in the recoil tof bar. Extends class RTOFRawHit. Is further defined
 * by the two hits upstream and downstream composing a full rtof hit. y position,
 * time and energy are defined from the up/down hits.
 *
 * @author npilleux, Nilanga Wickramaarachchi 
 */
public class RTOFHit extends RTOFRawHit {

    //A rtof hit is the combination of a downstream and upstream hits
    private RTOFRawHit hitUp, hitDown;

    public RTOFRawHit getHitUp() {
        return hitUp;
    }

    public void setHitUp(RTOFRawHit hit_up) {
        this.hitUp = hit_up;
    }

    public RTOFRawHit getHitDown() {
        return hitDown;
    }

    public void setHitDown(RTOFRawHit hit_down) {
        this.hitDown = hit_down;
    }

    /**
     * Computes rtof hit y local coordinate from up/downstream hit times.
     * 
     */
    public final void computeLocalY() {
        this.setLocalY(Parameters.VEFF/2. * (hitUp.getTime() - hitDown.getTime()));
    }

    /**
     * Computes rtof hit y coordinate in the global coordinate system. 
     *
     */
    public final void computeGlobalY() {
	double localY = this.getLocalY();
    
	int nRows = RTOFConstants.NROWS;
	double y_start = -(RTOFConstants.LENGTH - RTOFConstants.LONG_BAR_LENGTH)/2;  // Starting Y position
	double dy_long = RTOFConstants.LONG_BAR_LENGTH;
	double dy_short = RTOFConstants.SHORT_BAR_LENGTH;

        double y_pos; // y coordinate of the center of bar wrt to the global coordinate system
	if(hitUp.getRow()-1 < (nRows - 1)/2)
	    {
		y_pos = y_start + ((hitUp.getRow()-1) * dy_long);
	    }
	else if (hitUp.getRow()-1 == (nRows-1) / 2) // middle row
	    {  
		y_pos = 0;
	    }
	else
	    {
		y_pos = y_start + (hitUp.getRow()-2) * dy_long + dy_short;
	    }

	this.setY(y_pos + localY);
    }

    
    /**
     * Computes rtof hit time from up/downstream hit times.
     * The time is set as the time of the most energetic hit.
     * It is corrected for propagation time.
     * 
     */
    public final void computeTime() {
        //We pick the most energetic signal as the timing signal
        double time_at_sipm, distance_to_sipm;
        if(this.hitDown.getEnergy() > this.hitUp.getEnergy()) {
            time_at_sipm = this.hitDown.getTime();
	    if(this.hitDown.getRow() == 3) distance_to_sipm = RTOFConstants.SHORT_BAR_LENGTH/2. - this.getLocalY();
	    else distance_to_sipm = RTOFConstants.LONG_BAR_LENGTH/2. - this.getLocalY();
        }
        else {
            time_at_sipm = this.hitUp.getTime();
	    if(this.hitUp.getRow() == 3) distance_to_sipm = RTOFConstants.SHORT_BAR_LENGTH/2. + this.getLocalY();
            else distance_to_sipm = RTOFConstants.LONG_BAR_LENGTH/2. + this.getLocalY();
        }
        this.setTime(time_at_sipm - distance_to_sipm/Parameters.VEFF);
    }

    /**
     * Computes rtof hit energy from up/downstream hits.
     * The energy of the up/downstream hits is corrected for attenuation now that y is known.
     * The energy of the rtof hit is the sum of the energy of the up/downstream hits.
     * 
     */
    public final void computeEnergy() {
        this.computeLocalY();
        double distance_hit_to_sipm_up, distance_hit_to_sipm_down;
	
	if (hitUp.getRow() == 3) distance_hit_to_sipm_up = RTOFConstants.SHORT_BAR_LENGTH / 2. + this.getLocalY();
	else distance_hit_to_sipm_up = RTOFConstants.LONG_BAR_LENGTH / 2. + this.getLocalY();
	
	if (hitDown.getRow() == 3) distance_hit_to_sipm_down = RTOFConstants.SHORT_BAR_LENGTH / 2. - this.getLocalY();
	else distance_hit_to_sipm_down = RTOFConstants.LONG_BAR_LENGTH / 2. - this.getLocalY();
	
        double Edep_up = hitUp.getEnergy() * Math.exp(distance_hit_to_sipm_up / Parameters.ATT_L);
        double Edep_down = hitDown.getEnergy() * Math.exp(distance_hit_to_sipm_down / Parameters.ATT_L);
        this.setEnergy(Edep_up + Edep_down);
    }

    public RTOFHit(RTOFRawHit hit_down, RTOFRawHit hit_up) {
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
        this.computeLocalY();
	this.computeGlobalY();
        this.computeTime();
        this.computeEnergy();
        this.setTdc((hit_down.getTdc() + hit_up.getTdc())/2);
        this.setTot((hit_down.getTot() + hit_up.getTot()));
    }

    public RTOFHit() {
        super();
        this.setType("bar");
        this.setOrder(2);//Fake order for rtof hits
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
    }
}

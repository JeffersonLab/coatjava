package org.jlab.rec.atof.hit;

import org.jlab.rec.atof.constants.Parameters;
import org.jlab.utils.groups.IndexedTable;

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
    //Effective velocity read from CCDB
    double vEff;

    public ATOFHit getHitUp() {
        return hitUp;
    }
    
    /**
     * Computes bar time sum and check if it is around 
     * the value the hits were aligned to. For now,
     * 40ns cut. When calibrations are final, it should
     * be refined to reflect the resolution.
     * 
     */
    public boolean isInTime() {
        //Undefined start time is when useStartTime option is false in the yaml
        //for example for usage with simulations
        //-> we keep all the hits
        if(this.hitUp.getStartTime() == null) return true;
        double timeShift = 0;
        //TO DO: make this more robust
        //For FT electron for which the startTime is set at -1000
        //We need to shift where the cut is applied
        //2180 = 2*1090 = 1000+90 for FD start time around 90ns
        //if the data start time is not around 90, this will be a problem
        if(this.hitUp.getStartTime()<0) timeShift = 2180;
        if(Math.abs(this.hitUp.getTime()+this.hitDown.getTime()
                        -timeShift)<40)
            return true;
        return false;
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
        this.setZ(this.vEff/2. * (hitUp.getTime() - hitDown.getTime()));
    }

    /**
     * Computes bar hit time from up/downstream hit times.
     * The time is set as the time of the most energetic hit.
     * It is corrected for propagation time.
     * 
     */
    public final void computeTime() {
    //Select the most energetic hit as the timing reference
    final boolean useDownstream = hitDown.getEnergy() > hitUp.getEnergy();
    final double sipmTime   = useDownstream ? hitDown.getTime() : hitUp.getTime();
    //veff correction
    //t0 has already been removed.
    //distance to SiPM is L/2+-z, part of it is absorbed into the t0 as:
    //t0 = 2*offset+L/veff
    //t_hit = t_u/d - 2*offset/2 -/+ tud/2 - 1/veff(L/2 -/+ z)
    //t_hit = t_u/d - (t0)/2 -/+ tud/2 +/- z/veff
    //Only the z part remains
    final double zDirection = useDownstream ? +this.getZ() : -this.getZ();
    final double correctedTime = sipmTime + zDirection / vEff;
    this.setTime(correctedTime);
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

    public BarHit(ATOFHit hit_down, ATOFHit hit_up, IndexedTable atofEffectiveVelocityTable) {
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

        //CCDB readout for the effective velocity
        this.vEff = atofEffectiveVelocityTable.getDoubleValue("veff", this.getSector(), this.getLayer(), this.getComponent());
        this.computeZ();
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

package org.jlab.rec.atof.cluster;

import java.util.ArrayList;
import org.jlab.geom.prim.Point3D;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.rec.atof.constants.Parameters;
import org.jlab.rec.atof.hit.ATOFHit;
import org.jlab.rec.atof.hit.BarHit;

/**
 * The {@code ATOFCluster} represents clusters in the atof
 *
 * <p>
 * Create clusters and compute their basic properties from the hits composing
 * them.
 * </p>
 *
 * @author pilleux
 */
public class ATOFCluster {

    /**
     * list of hits in the bars.
     */
    ArrayList<BarHit> barHits;
    /**
     * list of hits in the wedges.
     */
    ArrayList<ATOFHit> wedgeHits;
    /**
     * cluster properties:position [cm], time [ns], energy[MeV], path length
     * [cm] and length through the atof [cm], type of the maximum hit (to set
     * resolutions).
     */
    double x, y, z, time, energy;
    double pathLength, inPathLength;
    String typeMaxHit;

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

    public double getX() {
        return x;
    }

    public void setX(double x) {
        this.x = x;
    }

    public double getY() {
        return y;
    }

    public void setY(double y) {
        this.y = y;
    }

    public double getZ() {
        return z;
    }

    public void setZ(double z) {
        this.z = z;
    }

    public double getTime() {
        return time;
    }

    public void setTime(double time) {
        this.time = time;
    }

    public double getEnergy() {
        return energy;
    }

    public void setEnergy(double energy) {
        this.energy = energy;
    }

    public double getPathLength() {
        return pathLength;
    }

    public void setPathLength(double pathLength) {
        this.pathLength = pathLength;
    }

    public double getInPathLength() {
        return inPathLength;
    }

    public void setInPathLength(double inPathLength) {
        this.inPathLength = inPathLength;
    }
    
    public String getTypeMaxHit() {
        return typeMaxHit;
    }

    public void setTypeMaxHit(String typeMaxHit) {
        this.typeMaxHit = typeMaxHit;
    }  
    
    /**
     * Compute the cluster properties.
     *
     * Cluster coordinates and time are defined as the coordinates and time of
     * the max energy hit.
     *
     * TO DO: Test other choices for the definitions.
     *
     */
    public final void computeClusterProperties() {
        this.energy = 0;
        double max_energy = -1;
        ATOFHit max_energy_hit = new ATOFHit();

        for (int i_wedge = 0; i_wedge < this.wedgeHits.size(); i_wedge++) {
            ATOFHit this_wedge_hit = this.wedgeHits.get(i_wedge);
            double this_energy = this_wedge_hit.getEnergy();
            this.energy += this_energy;
            if (this_energy > max_energy) {
                max_energy_hit = this_wedge_hit;
                max_energy = this_energy;
            }
        }

        for (int i_bar = 0; i_bar < this.barHits.size(); i_bar++) {
            BarHit this_bar_hit = this.barHits.get(i_bar);
            double this_energy = this_bar_hit.getEnergy();
            this.energy += this_energy;
            if (this_energy > max_energy) {
                max_energy_hit = this_bar_hit;
                max_energy = this_energy;
            }
        }
        
        this.time = max_energy_hit.getTime();
        this.x = max_energy_hit.getX();
        this.y = max_energy_hit.getY();
        this.z = max_energy_hit.getZ();
        this.typeMaxHit = max_energy_hit.getType();
    }
    
    /**
     * Matches the current track with ahdc tracks projections that have been written to the banks.
     * Calculates the match by comparing the hit's azimuthal angle and longitudinal position
     * (z) with the track projection. If a match is found within defined
     * tolerances for phi and z, the path length of the matched hit is updated.
     *
     * @param event a @link{DataEvent} in which the track projections bank has been written.
     *
     */
    public int matchTrack(DataEvent event) {
        String track_bank_name = "ALERT::Projections";
        if (event == null) { // check if there is an event
            //System.out.print(" no event \n");
            return 1;
        } else if (event.hasBank(track_bank_name) == false) {
            // check if there are ahdc tracks in the event
            //System.out.print("no tracks \n");
            return 1;    
        } else {
            DataBank track_bank = event.getBank(track_bank_name);
            int nt = track_bank.rows(); // number of tracks
            double sigma_phi = 0;
            double sigma_z = 0;

            //Looping through all tracks
            for (int i = 0; i < nt; i++) {
                Float xt = null, yt = null, zt = null, path = null, inpath = null;
                if (null == this.getTypeMaxHit()) {
                    System.out.print("Impossible to match track and hit; hit type is null \n");
                } else {
                    switch (this.getTypeMaxHit()) {
                        case "wedge" -> {
                            sigma_phi = Parameters.SIGMA_PHI_TRACK_MATCHING_WEDGE;
                            sigma_z = Parameters.SIGMA_Z_TRACK_MATCHING_WEDGE;
                            xt = track_bank.getFloat("x_at_wedge", i);
                            yt = track_bank.getFloat("y_at_wedge", i);
                            zt = track_bank.getFloat("z_at_wedge", i);
                            path = track_bank.getFloat("L_at_wedge", i);
                            //A wedge hit traveled through the whole bar and then through a portion of the wedge
                            inpath = track_bank.getFloat("L_in_wedge", i) + track_bank.getFloat("L_at_wedge", i) - track_bank.getFloat("L_at_bar", i); 
                        }
                        case "bar" -> {
                            sigma_phi = Parameters.SIGMA_PHI_TRACK_MATCHING_BAR;
                            sigma_z = Parameters.SIGMA_Z_TRACK_MATCHING_BAR;
                            xt = track_bank.getFloat("x_at_bar", i);
                            yt = track_bank.getFloat("y_at_bar", i);
                            zt = track_bank.getFloat("z_at_bar", i);
                            path = track_bank.getFloat("L_at_bar", i);
                            inpath = track_bank.getFloat("L_in_bar", i);
                        }
                        case "bar up", "bar down" -> {
                            System.out.print("Impossible to match track and hit; hit type is a single up or down bar hit. \n");
                        }
                        default ->
                            System.out.print("Impossible to match track and hit; hit type is undefined \n");
                    }
                }
                Point3D projection_point = new Point3D(xt, yt, zt);
                double delta_phi = Math.abs(this.getPhi() - projection_point.toVector3D().phi());
                if(delta_phi > Math.PI) delta_phi = Math.PI - delta_phi;
                if (delta_phi < sigma_phi) {
                    if (Math.abs(this.getZ() - projection_point.z()) < sigma_z) {
                        this.setPathLength(path);
                        this.setInPathLength(inpath);
                    }      
                }
            }   
        }
        return 0;
    }

    public double getEdepWedge() {
        double energy = 0;
        for (int i = 0; i < this.wedgeHits.size(); i++) {
            ATOFHit this_hit = this.wedgeHits.get(i);
            energy += this_hit.getEnergy();
        }
        return energy;
    }

    public double getEdepBar() {
        double energy = 0;
        for (int i = 0; i < this.barHits.size(); i++) {
            ATOFHit this_hit = this.barHits.get(i);
            energy += this_hit.getEnergy();
        }
        return energy;
    }

    /**
     * Compute the cluster phi angle in radians.
     *
     * @return a double that is angle in radians
     * 
     */
    public double getPhi() {
        return Math.atan2(this.y, this.x);
    }

    /**
     * Compute the cluster beta from the path length and time.
     *
     * @return a double that is beta
     * 
     * - TO DO: Change to non-hardcoded value for c
     * 
     */
    public double getBeta() {
        //Need to change to non hardcoded value
        return (this.pathLength / this.time) / (2.9979 * Math.pow(10, 2));
    }

    /**
     * Constructor that initializes the list of bar hits and list of wedge hits
     * and computes the cluster properties.
     * 
     * @param bar_hits a {@link ArrayList} of {@link BarHit}.
     * @param wedge_hits a {@link ArrayList} of {@link ATOFHit}.
     * 
     */
    public ATOFCluster(ArrayList<BarHit> bar_hits, ArrayList<ATOFHit> wedge_hits) {
        this.barHits = bar_hits;
        this.wedgeHits = wedge_hits;
        this.computeClusterProperties();
    }
    
    /**
     * Constructor that initializes the list of bar hits and list of wedge hits
     * and computes the cluster properties.
     * 
     * @param bar_hits a {@link ArrayList} of {@link BarHit}.
     * @param wedge_hits a {@link ArrayList} of {@link ATOFHit}.
     * 
     */
    public ATOFCluster(ArrayList<BarHit> bar_hits, ArrayList<ATOFHit> wedge_hits, DataEvent event) {
        this.barHits = bar_hits;
        this.wedgeHits = wedge_hits;
        this.computeClusterProperties();
        this.matchTrack(event);
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
    }

}

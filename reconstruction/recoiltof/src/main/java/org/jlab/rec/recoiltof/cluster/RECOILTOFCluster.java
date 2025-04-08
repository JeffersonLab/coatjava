package org.jlab.rec.recoiltof.cluster;

import java.util.ArrayList;
import org.jlab.geom.prim.Line3D;
import org.jlab.geom.prim.Point3D;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.rec.recoiltof.constants.Parameters;
import org.jlab.rec.recoiltof.hit.RECOILTOFHit;
import org.jlab.rec.recoiltof.hit.BarHit;

/**
 * The {@code RECOILTOFCluster} represents clusters in the recoil tof
 *
 * <p>
 * Create clusters and compute their basic properties from the hits composing
 * them.
 * </p>
 *
 * @author pilleux, Nilanga Wickramaarachchi 
 */
public class RECOILTOFCluster {

    /**
     * list of hits in the bars.
     */
    ArrayList<BarHit> barHits;
    /**
     * cluster properties:position [cm], time [ns], energy[MeV], 
     * type of the maximum hit (to set resolutions) and index and sector of the maximum hit.
     */
    double x, y, z, time, energy;
    String typeMaxHit;
    int indexMaxHit, sectorMaxHit;

    public ArrayList<BarHit> getBarHits() {
        return barHits;
    }

    public void setBarHits(ArrayList<BarHit> bar_hits) {
        this.barHits = bar_hits;
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


    public String getTypeMaxHit() {
        return typeMaxHit;
    }

    public void setTypeMaxHit(String typeMaxHit) {
        this.typeMaxHit = typeMaxHit;
    }

    public int getIndexMaxHit() {
        return indexMaxHit;
    }

    public void setIndexMaxHit(int indexMaxHit) {
        this.indexMaxHit = indexMaxHit;
    }

    public int getSectorMaxHit() {
        return sectorMaxHit;
    }

    public void setSectorMaxHit(int sectorMaxHit) {
        this.sectorMaxHit = sectorMaxHit;
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
        RECOILTOFHit max_energy_hit = new RECOILTOFHit();

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
	this.sectorMaxHit = max_energy_hit.getSector();
    }

    
    /**
     * Computes the energy deposited in the bars.
     *
     * @return the energy deposited in the bars.
     *
     */
    public double getEdepBar() {
        double energy = 0;
        for (int i = 0; i < this.barHits.size(); i++) {
            RECOILTOFHit this_hit = this.barHits.get(i);
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
     * Retrieve the hit with maximal energy in the cluster. It must have been
     * computed previously.
     *
     * @return a RECOILTOFHit that is the maximal energy hit in the cluster
     *
     */
    public final RECOILTOFHit getMaxHit() {
        if (this.typeMaxHit == null) {
            System.out.print("You did not compute the maximal hit! \n");
            return null;
        }
        if (null == this.typeMaxHit) {
            System.out.print("Unrecognized type! \n");
            return null;
        } else {
            switch (this.typeMaxHit) {
                case "bar" -> {
                    return this.barHits.get(this.indexMaxHit);
                }
                default -> {
                    System.out.print("Unrecognized type! \n");
                    return null;
                }
            }
        }
    }

    /**
     * Computes the sum of TOT in the cluster.
     *
     * @return an int representing the summed TOT
     *
     */
    public int getTot() {
        int tot = 0;
        for (int i = 0; i < this.barHits.size(); i++) {
            BarHit this_hit = this.barHits.get(i);
            tot += this_hit.getTot();
        }
        return tot;
    }

    /**
     * Returns the TDC of the maximal hit in the cluster.
     *
     * @return an int representing the TDC of the maximal hit.
     *
     */
    public int getTdc() {
        return this.getMaxHit().getTdc();
    }

    /**
     * Constructor that initializes the list of bar hits
     * and computes the cluster properties.
     *
     * @param bar_hits a {@link ArrayList} of {@link BarHit}.
     *
     */
    public RECOILTOFCluster(ArrayList<BarHit> bar_hits) {
        this.barHits = bar_hits;
        this.computeClusterProperties();
    }

    /**
     * Constructor that initializes the list of bar hits
     * and computes the cluster properties.
     *
     * @param bar_hits a {@link ArrayList} of {@link BarHit}.
     * @param event a {@link DataEvent} with which track matching will be done.
     *
     */
    public RECOILTOFCluster(ArrayList<BarHit> bar_hits, DataEvent event) {
        this.barHits = bar_hits;
        this.computeClusterProperties();
    }


    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
    }

}

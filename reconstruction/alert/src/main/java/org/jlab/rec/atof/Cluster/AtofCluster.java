package org.jlab.rec.atof.cluster;

import java.util.ArrayList;
import org.jlab.rec.atof.hit.AtofHit;
import org.jlab.rec.atof.hit.BarHit;

/**
 *
 * @author npilleux
 */
public class AtofCluster {
    
    ArrayList<BarHit> barHits;
    ArrayList<AtofHit> wedgeHits;
    double x,y,z,time,energy;
    double pathLength, inPathLength;
    
    public ArrayList<BarHit> getBarHits() {
        return barHits;
    }

    public void setBarHits(ArrayList<BarHit> bar_hits) {
        this.barHits = bar_hits;
    }

    public ArrayList<AtofHit> getWedgeHits() {
        return wedgeHits;
    }

    public void setWedgeHits(ArrayList<AtofHit> wedge_hits) {
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
    
    //Cluster coordinates and time are defined as the coordinates and time of the max energy hit
    //Can be changed later
    public final void computeClusterProperties() {
        this.energy=0;
        double max_energy = -1;
        AtofHit max_energy_hit = new AtofHit();
        BarHit max_energy_barhit = new BarHit();

        for(int i_wedge = 0; i_wedge<this.wedgeHits.size(); i_wedge++)
        {
            AtofHit this_wedge_hit = this.wedgeHits.get(i_wedge);
            double this_energy = this_wedge_hit.getEnergy();
            this.energy+=this_energy;
            if(this_energy>max_energy){max_energy_hit = this_wedge_hit; max_energy = this_energy;}
        }
        
        for(int i_bar = 0; i_bar<this.barHits.size(); i_bar++)
        {
            BarHit this_bar_hit = this.barHits.get(i_bar);
            double this_energy = this_bar_hit.getEnergy();
            this.energy+=this_energy;
            if(this_energy>max_energy){max_energy_barhit = this_bar_hit; max_energy = this_energy;}
        }
        
        if(max_energy_hit.getEnergy() > max_energy_barhit.getEnergy())
        {
            this.time = max_energy_hit.getTime();
            this.x = max_energy_hit.getX();
            this.y = max_energy_hit.getY();
            this.z = max_energy_hit.getZ();
            this.pathLength = max_energy_hit.getPathLength();
            this.inPathLength = max_energy_hit.getInPathLength();
        }
        else
        {
            this.time = max_energy_barhit.getTime();
            this.x = max_energy_barhit.getX();
            this.y = max_energy_barhit.getY();
            this.z = max_energy_barhit.getZ();
            this.pathLength = max_energy_barhit.getPathLength();
            this.inPathLength = max_energy_barhit.getInPathLength();
        }
    }
    
    public double getPhi()
    {
        return Math.atan2(this.y, this.x);
    }
    
    public double getBeta()
    {
        return (this.pathLength / this.time) / (2.9979 * Math.pow(10, 2));//to do: Change to non-hardcoded value for c
    }
    
    public AtofCluster(ArrayList<BarHit> bar_hits, ArrayList<AtofHit> wedge_hits) 
	{
		this.barHits = bar_hits;  
		this.wedgeHits = wedge_hits;  
                this.computeClusterProperties();
        }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
    }
    
}

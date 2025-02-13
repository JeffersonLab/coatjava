package org.jlab.rec.ahdc.AI;

import org.jlab.rec.ahdc.Hit.Hit;
import org.jlab.rec.ahdc.PreCluster.PreCluster;

import java.util.ArrayList;

public class PreclusterSuperlayer {
    private final double x;
    private final double y;
    private ArrayList<PreCluster> preclusters = new ArrayList<>();


 ;   public PreclusterSuperlayer(ArrayList<PreCluster> preclusters_) {
        this.preclusters = preclusters_;
        double x_ = 0;
        double y_ = 0;

        for (PreCluster p : this.preclusters) {
            x_ += p.get_X();
            y_ += p.get_Y();
        }
        this.x = x_ / this.preclusters.size();
        this.y = y_ / this.preclusters.size();



    }

    public ArrayList<PreCluster> getPreclusters() {
        return preclusters;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }


    public String toString() {
        return "PreCluster{" + "X: " + this.x + " Y: " + this.y + " phi: " + Math.atan2(this.y, this.x) + "}\n";
    }
}

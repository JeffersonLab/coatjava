package org.jlab.rec.atof.cluster;

import java.util.ArrayList;
import org.jlab.geom.prim.Line3D;
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
     * resolutions) and index of the maximum hit.
     */
    double x, y, z, time, energy;
    double pathLength, inPathLength;
    String typeMaxHit;
    int indexMaxHit;
    int iProj;

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

    public int getIProj() {
        return iProj;
    }

    public void setIProj(int iProj) {
        this.iProj = iProj;
    }

    public int getIndexMaxHit() {
        return indexMaxHit;
    }

    public void setIndexMaxHit(int indexMaxHit) {
        this.indexMaxHit = indexMaxHit;
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
     * Matches the current track with ahdc tracks projections that have been
     * written to the banks. Calculates the match by comparing the hit's
     * azimuthal angle and longitudinal position (z) with the track projection.
     * If a match is found within defined tolerances for phi and z, the path
     * length of the matched hit is updated.
     *
     * @param event a @link{DataEvent} in which the track projections bank has
     * been written.
     *
     */
    public int matchTrack(DataEvent event) {

        //This cluster point in space
        Point3D cluster = new Point3D(this.x, this.y, this.z);
        //Bank to be read
        String track_bank_name = "ALERT::projections";
        //Checking if there is an event
        if (event == null) {
            return 1;
        } else if (event.hasBank(track_bank_name) == false) {
            //Check if there are tracks in the event
            //If it is not the case, assign a straight track
            this.makeStraightTrack();
            return 1;
        } else {
            //There are tracks in the event
            DataBank track_bank = event.getBank(track_bank_name);
            int nt = track_bank.rows(); // number of tracks
            double sigma_phi = 0;
            double sigma_z = 0;

            //This will help decide which track to consider 
            //when more than one are matched to the same cluster
            double distanceMinBetweenTrackAndCluster = 999;

            //Check if a track was matched
            Boolean foundMatch = false;
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
                            path = track_bank.getFloat("l_at_wedge", i);
                            //A wedge hit traveled through the whole bar and then through a portion of the wedge
                            inpath = track_bank.getFloat("l_in_wedge", i) + track_bank.getFloat("l_at_wedge", i) - track_bank.getFloat("l_at_bar", i);
                        }
                        case "bar" -> {
                            sigma_phi = Parameters.SIGMA_PHI_TRACK_MATCHING_BAR;
                            sigma_z = Parameters.SIGMA_Z_TRACK_MATCHING_BAR;
                            xt = track_bank.getFloat("x_at_bar", i);
                            yt = track_bank.getFloat("y_at_bar", i);
                            zt = track_bank.getFloat("z_at_bar", i);
                            path = track_bank.getFloat("l_at_bar", i);
                            inpath = track_bank.getFloat("l_in_bar", i);
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
                if (delta_phi > Math.PI) {
                    delta_phi = Math.PI - delta_phi;
                }
                //Geometrical match
                if (delta_phi < sigma_phi && Math.abs(this.getZ() - projection_point.z()) < sigma_z) {
                    foundMatch = true;
                    //If a track is matched, we look at the distance between it and the cluster
                    double distance = cluster.distance(projection_point);
                    //We only consider the minimal distance track
                    if (distance < distanceMinBetweenTrackAndCluster) {
                        distanceMinBetweenTrackAndCluster = distance;
                        this.setPathLength(path);
                        this.setInPathLength(inpath);
                        this.setIProj(track_bank.getInt("id", i));
                    }
                }
            }
            if (!foundMatch) {
                //If no track is matched, assign a straight track
                this.makeStraightTrack();
            }
        }
        return 0;
    }

    /**
     * Build a straight track from the vertex to this cluster.
     *
     * Sets the cluster path length and length through the atof from it.
     *
     */
    public void makeStraightTrack() {
        double vx = 0, vy = 0, vz = 0;//Here we should read vertex info
        Line3D vertexToCluster = new Line3D(vx, vy, vz, this.x, this.y, this.z);
        double straightPath = vertexToCluster.length();
        this.setPathLength(straightPath);
        this.setInPathLength(getDistanceStraightInATOF(vx, vy, vz));
        //ID indicating no track was matched
        this.setIProj(-1);
    }

    /**
     * Computes the distance a straight track goes through the ATOF. The
     * intersection point between a straight track from the vertex to the
     * cluster and the ATOF inner cylinder is computed to that end.
     *
     * @param vx, the x coordinate of the vertex
     * @param vy, the y coordinate of the vertex
     * @param vz, the z coordinate of the vertex
     *
     * @return the distance the straight track went through in the ATOF
     *
     */
    public double getDistanceStraightInATOF(double vx, double vy, double vz) {
        //Solving the equation (X,Y,Z) = t*((x-vx),(y-vy),(z-vz)) + (vx,vy,vz)
        //Such as sqr(X)+sqr(Y)=sqr(R)
        double a = Math.pow((this.x - vx), 2) + Math.pow((this.y - vy), 2);
        double b = 2 * (vx * (x - vx) + vy * (y - vy));
        double c = vx * vx + vy * vy - Parameters.BAR_INNER_RADIUS * Parameters.BAR_INNER_RADIUS;
        double d = b * b - 4 * a * c;
        double t1 = (-b - Math.sqrt(d)) / (2 * a);
        double t2 = (-b + Math.sqrt(d)) / (2 * a);
        //Intersection points between the line and the inner surface of the ATOF
        double X1 = t1 * (this.x - vx) + vx;
        double Y1 = t1 * (this.y - vy) + vy;
        double Z1 = t1 * (this.z - vz) + vz;
        double X2 = t2 * (this.x - vx) + vx;
        double Y2 = t2 * (this.y - vy) + vy;
        double Z2 = t2 * (this.z - vz) + vz;
        //Distance between these and the cluster is the length through the detector
        Point3D p1 = new Point3D(X1, Y1, Z1);
        Point3D p2 = new Point3D(X2, Y2, Z2);
        Point3D cluster = new Point3D(this.x, this.y, this.z);
        double d1 = cluster.distance(p1);
        double d2 = cluster.distance(p2);
        //Returning the smallest distance, the other one is the opposite side
        if (d1 > d2) {
            return d2;
        } else {
            return d1;
        }
    }

    /**
     * Computes the energy deposited in the wedges.
     *
     * @return the energy deposited in the wedges.
     *
     */
    public double getEdepWedge() {
        double energy = 0;
        for (int i = 0; i < this.wedgeHits.size(); i++) {
            ATOFHit this_hit = this.wedgeHits.get(i);
            energy += this_hit.getEnergy();
        }
        return energy;
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
     * Retrieve the hit with maximal energy in the cluster. It must have been
     * computed previously.
     *
     * @return a ATOFHit that is the maximal energy hit in the cluster
     *
     */
    public final ATOFHit getMaxHit() {
        if (this.typeMaxHit == null) {
            System.out.print("You did not compute the maximal hit! \n");
            return null;
        }
        if (null == this.typeMaxHit) {
            System.out.print("Unrecognized type! \n");
            return null;
        } else {
            switch (this.typeMaxHit) {
                case "wedge" -> {
                    return this.wedgeHits.get(this.indexMaxHit);
                }
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
        for (int i = 0; i < this.wedgeHits.size(); i++) {
            ATOFHit this_hit = this.wedgeHits.get(i);
            tot += this_hit.getTot();
        }
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
     * @param event a {@link DataEvent} with which track matching will be done.
     *
     */
    public ATOFCluster(ArrayList<BarHit> bar_hits, ArrayList<ATOFHit> wedge_hits, DataEvent event) {
        this.barHits = bar_hits;
        this.wedgeHits = wedge_hits;
        this.computeClusterProperties();
        this.matchTrack(event);
    }

    /**
     * Computes the wedge hit with maximal energy in the cluster.
     *
     * @return a ATOFHit that is the maximal energy hit in the wedges in the
     * cluster.
     *
     */
    public final ATOFHit getMaxWedgeHit() {
        double max_energy = -1;
        ATOFHit max_energy_hit = new ATOFHit();

        for (int i_wedge = 0; i_wedge < this.wedgeHits.size(); i_wedge++) {
            ATOFHit this_wedge_hit = this.wedgeHits.get(i_wedge);
            double this_energy = this_wedge_hit.getEnergy();
            if (this_energy > max_energy) {
                max_energy_hit = this_wedge_hit;
                max_energy = this_energy;
            }
        }
        return max_energy_hit;
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
    }

}

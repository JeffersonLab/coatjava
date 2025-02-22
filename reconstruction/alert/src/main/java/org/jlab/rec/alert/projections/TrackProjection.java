package org.jlab.rec.alert.projections;

import org.jlab.geom.prim.Point3D;

/**
 * The {@code TrackProjection} class holds ahdc track information relevant for atof analysis
 * i.e projected to the surfaces of the bar and wedges
 * @author pilleux
 */

public class TrackProjection {

    /** 
     * Intersection point of the track with the middle surface of the bar.
     */
    private Point3D barIntersect = new Point3D(); 
    
    /** 
     * Intersection point of the track with the middle surface of the wedges.
     */
    private Point3D wedgeIntersect = new Point3D(); 
    
    /** 
     * Path length of the track from the DOCA to the beam line 
     * to the entrance surface of the bar. 
     */
    Float barPathLength; 
    
    /** 
     * Path length of the track from the DOCA to the beam line 
     * to the entrance surface of the wedges.
     */
    Float wedgePathLength; 
    
    /** 
     * Path length inside the bar. 
     */
    Float barInPathLength;
    
    /** 
     * Path length inside the wedge. 
     */
    Float wedgeInPathLength;
    
    int trackID;
    

    /**
     * Default constructor that initializes the intersection points and path lengths to {@code NaN}.
     */
    public TrackProjection() {
        barIntersect = new Point3D(Double.NaN, Double.NaN, Double.NaN);
        wedgeIntersect = new Point3D(Double.NaN, Double.NaN, Double.NaN);
        barPathLength = Float.NaN;
        wedgePathLength = Float.NaN;
        barInPathLength = Float.NaN;
        wedgeInPathLength = Float.NaN;
    }

    /**
     * Gets the intersection point of the track with the middle surface of the bar.
     * 
     * @return {@link Point3D} bar's intersection point.
     */
    public Point3D getBarIntersect() {
        return barIntersect;
    }

    /**
     * Gets the intersection point of the track with the middle surface of the wedges.
     * 
     * @return {@link Point3D} wedge's intersection point.
     */
    public Point3D getWedgeIntersect() {
        return wedgeIntersect;
    }

    /**
     * Gets the path length of the track from the DOCA to the beam line to the inner surface of the bar.
     * 
     * @return {@code Float} path length to the bar's middle surface.
     */
    public Float getBarPathLength() {
        return barPathLength;
    }
    
    /**
     * Gets the path length of the track from the inner surface of the bar
     * to its middle surface.
     * 
     * @return {@code Float} path length inside the bar.
     */
    public Float getBarInPathLength() {
        return barInPathLength;
    }

    /**
     * Gets the path length of the track from the DOCA to the beam line to the inner surface of the wedges.
     * 
     * @return {@code Float} path length to the wedge's middle surface.
     */
    public Float getWedgePathLength() {
        return wedgePathLength;
    }
    
    /**
     * Gets the path length of the track from the the inner surface of the wedge
     * to its middle surface.
     * 
     * @return {@code Float} path length inside the wedge.
     */
    public Float getWedgeInPathLength() {
        return wedgeInPathLength;
    }

    /**
     * Sets the intersection point of the track with the middle surface of the bar.
     * 
     * @param BarIntersect {@link Point3D} intersection with the bar.
     */
    public void setBarIntersect(Point3D BarIntersect) {
        this.barIntersect = BarIntersect;
    }

    /**
     * Sets the intersection point of the track with the middle surface of the wedges.
     * 
     * @param WedgeIntersect {@link Point3D} intersection with the wedge.
     */
    public void setWedgeIntersect(Point3D WedgeIntersect) {
        this.wedgeIntersect = WedgeIntersect;
    }

    /**
     * Sets the path length of the track from the DOCA to the beam line to the inner surface of the bar.
     * 
     * @param BarPathLength {@code Float} path length to the bar inner surface.
     */
    public void setBarPathLength(Float BarPathLength) {
        this.barPathLength = BarPathLength;
    }

    /**
     * Sets the path length of the track from the DOCA to the beam line to the inner surface of the wedges.
     * 
     * @param WedgePathLength {@code Float} path length to the wedge inner surface.
     */
    public void setWedgePathLength(Float WedgePathLength) {
        this.wedgePathLength = WedgePathLength;
    }
    
    /**
     * Sets the path length of the track inside the bar.
     * 
     * @param BarInPathLength {@code Float} path length inside the bar.
     */
    public void setBarInPathLength(Float BarInPathLength) {
        this.barInPathLength = BarInPathLength;
    }

    /**
     * Sets the path length of the track inside the wedges.
     * 
     * @param WedgeInPathLength {@code Float} path length inside the wedge.
     */
    public void setWedgeInPathLength(Float WedgeInPathLength) {
        this.wedgeInPathLength = WedgeInPathLength;
    }

    public void setTrackID(int trackID) {
        this.trackID = trackID;
    }
    
    public int getTrackID() {
        return trackID;
    }

    /**
     * testing purposes.
     *
     * @param arg command-line arguments.
     */
    public static void main(String arg[]) {
    }
}
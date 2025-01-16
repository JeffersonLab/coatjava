package org.jlab.rec.atof.trackMatch;

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
    private Point3D _BarIntersect = new Point3D(); 
    
    /** 
     * Intersection point of the track with the middle surface of the wedges.
     */
    private Point3D _WedgeIntersect = new Point3D(); 
    
    /** 
     * Path length of the track from the DOCA to the beam line 
     * to the entrance surface of the bar. 
     */
    Float _BarPathLength; 
    
    /** 
     * Path length of the track from the DOCA to the beam line 
     * to the entrance surface of the wedges.
     */
    Float _WedgePathLength; 
    
    /** 
     * Path length inside the bar. 
     */
    Float _BarInPathLength;
    
    /** 
     * Path length inside the wedge. 
     */
    Float _WedgeInPathLength;
    

    /**
     * Default constructor that initializes the intersection points and path lengths to {@code NaN}.
     */
    public TrackProjection() {
        _BarIntersect = new Point3D(Double.NaN, Double.NaN, Double.NaN);
        _WedgeIntersect = new Point3D(Double.NaN, Double.NaN, Double.NaN);
        _BarPathLength = Float.NaN;
        _WedgePathLength = Float.NaN;
        _BarInPathLength = Float.NaN;
        _WedgeInPathLength = Float.NaN;
    }

    /**
     * Gets the intersection point of the track with the middle surface of the bar.
     * 
     * @return {@link Point3D} bar's intersection point.
     */
    public Point3D get_BarIntersect() {
        return _BarIntersect;
    }

    /**
     * Gets the intersection point of the track with the middle surface of the wedges.
     * 
     * @return {@link Point3D} wedge's intersection point.
     */
    public Point3D get_WedgeIntersect() {
        return _WedgeIntersect;
    }

    /**
     * Gets the path length of the track from the DOCA to the beam line to the inner surface of the bar.
     * 
     * @return {@code Float} path length to the bar's middle surface.
     */
    public Float get_BarPathLength() {
        return _BarPathLength;
    }
    
    /**
     * Gets the path length of the track from the inner surface of the bar
     * to its middle surface.
     * 
     * @return {@code Float} path length inside the bar.
     */
    public Float get_BarInPathLength() {
        return _BarInPathLength;
    }

    /**
     * Gets the path length of the track from the DOCA to the beam line to the inner surface of the wedges.
     * 
     * @return {@code Float} path length to the wedge's middle surface.
     */
    public Float get_WedgePathLength() {
        return _WedgePathLength;
    }
    
    /**
     * Gets the path length of the track from the the inner surface of the wedge
     * to its middle surface.
     * 
     * @return {@code Float} path length inside the wedge.
     */
    public Float get_WedgeInPathLength() {
        return _WedgeInPathLength;
    }

    /**
     * Sets the intersection point of the track with the middle surface of the bar.
     * 
     * @param BarIntersect {@link Point3D} intersection with the bar.
     */
    public void set_BarIntersect(Point3D BarIntersect) {
        this._BarIntersect = BarIntersect;
    }

    /**
     * Sets the intersection point of the track with the middle surface of the wedges.
     * 
     * @param WedgeIntersect {@link Point3D} intersection with the wedge.
     */
    public void set_WedgeIntersect(Point3D WedgeIntersect) {
        this._WedgeIntersect = WedgeIntersect;
    }

    /**
     * Sets the path length of the track from the DOCA to the beam line to the inner surface of the bar.
     * 
     * @param BarPathLength {@code Float} path length to the bar inner surface.
     */
    public void set_BarPathLength(Float BarPathLength) {
        this._BarPathLength = BarPathLength;
    }

    /**
     * Sets the path length of the track from the DOCA to the beam line to the inner surface of the wedges.
     * 
     * @param WedgePathLength {@code Float} path length to the wedge inner surface.
     */
    public void set_WedgePathLength(Float WedgePathLength) {
        this._WedgePathLength = WedgePathLength;
    }
    
    /**
     * Sets the path length of the track inside the bar.
     * 
     * @param BarInPathLength {@code Float} path length inside the bar.
     */
    public void set_BarInPathLength(Float BarInPathLength) {
        this._BarInPathLength = BarInPathLength;
    }

    /**
     * Sets the path length of the track inside the wedges.
     * 
     * @param WedgeInPathLength {@code Float} path length inside the wedge.
     */
    public void set_WedgeInPathLength(Float WedgeInPathLength) {
        this._WedgeInPathLength = WedgeInPathLength;
    }
    

    /**
     * testing purposes.
     *
     * @param arg command-line arguments.
     */
    public static void main(String arg[]) {
    }
}
package org.jlab.rec.ahdc.DocaCluster;

/**
 * DocaCluster is a "refined" cluster space point built from
 * the DOCA information of hits in the two PreClusters that
 * compose a standard Cluster.
 *
 * One original Cluster can produce one or several DocaCluster
 * objects, each with its own (x, y, z, weight).
 */
public class DocaCluster {

    // Refined space point
    private double _X;
    private double _Y;
    private double _Z;

    // Weight for helix fit (relative weight of this point)
    private double _Weight = 1.0;

    // Pattern of hit multiplicity: 11, 12, 21, 22, or 0 for "others"
    private int _Pattern = 0;

    // Index of the original Cluster in the AHDC_Clusters list
    private int _ClusterIndex = -1;

    public DocaCluster(double x, double y, double z,
                       double weight, int pattern, int clusterIndex) {
        this._X = x;
        this._Y = y;
        this._Z = z;
        this._Weight = weight;
        this._Pattern = pattern;
        this._ClusterIndex = clusterIndex;
    }

    public double get_X()      { return _X; }
    public double get_Y()      { return _Y; }
    public double get_Z()      { return _Z; }
    public double get_Weight() { return _Weight; }

    public int get_Pattern()       { return _Pattern; }
    public int get_ClusterIndex()  { return _ClusterIndex; }

    public void set_X(double x)           { this._X = x; }
    public void set_Y(double y)           { this._Y = y; }
    public void set_Z(double z)           { this._Z = z; }
    public void set_Weight(double weight) { this._Weight = weight; }
    public void set_Pattern(int pattern)  { this._Pattern = pattern; }
    public void set_ClusterIndex(int idx) { this._ClusterIndex = idx; }

    @Override
    public String toString() {
        return String.format(
            "DocaCluster{X=%.3f, Y=%.3f, Z=%.3f, w=%.3f, pattern=%d, idx=%d}",
            _X, _Y, _Z, _Weight, _Pattern, _ClusterIndex
        );
    }
}

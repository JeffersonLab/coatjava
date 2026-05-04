package org.jlab.rec.ahdc.Cluster;

import org.jlab.rec.ahdc.Hit.Hit;
import org.jlab.rec.ahdc.PreCluster.PreCluster;

import java.util.ArrayList;
import org.jlab.geom.detector.alert.AHDC.AlertDCFactory;
import org.jlab.geom.prim.Line3D;
import org.jlab.geom.prim.Point3D;

/**
 * Cluster are compose by 2 PreCluster on layer with a different stereo angle
 */
public class Cluster {

	private int                   _trackId = -1;
	private double                _Radius;
	private double                _Phi;
	private double                _Z;
	private boolean               _Used = false;
	private int                   _Num_wire;
	private double                _X;
	private double                _Y;
	private double                _U;
	private double                _V;
	private ArrayList<PreCluster> _PreClusters_list;

	private static Line3D representativeLine(PreCluster pc) {
		if (pc == null || pc.get_hits_list() == null || pc.get_hits_list().isEmpty()) {
			return null;
		}
		Hit h = pc.get_hits_list().get(0);
		return h.getLine();
	}
	private static double wrapPi(double a) {
		while (a > Math.PI)  a -= 2.0 * Math.PI;
		while (a < -Math.PI) a += 2.0 * Math.PI;
		return a;
	}
	private static double stereoTwistFromLine(Line3D line) {
		if (line == null) {
			return 0.0;
		}

		Point3D p0 = line.origin();
		Point3D p1 = line.end();

		double phi0 = Math.atan2(p0.y(), p0.x());
		double phi1 = Math.atan2(p1.y(), p1.x());

		return wrapPi(phi1 - phi0);
	}
	public Cluster(PreCluster precluster, PreCluster other_precluster) {
		this._PreClusters_list = new ArrayList<>();
		_PreClusters_list.add(precluster);
		_PreClusters_list.add(other_precluster);
		this._Radius = (precluster.get_Radius() + other_precluster.get_Radius()) / 2;

		Line3D line1 = representativeLine(precluster);
		Line3D line2 = representativeLine(other_precluster);
		Point3D end1 = line1.end();
		Point3D start1 = line1.origin();
		double DeltaZ = end1.z()-start1.z();
		double Zref = end1.z();
		double StereoAnglep = stereoTwistFromLine(line1);
		double StereoAngleo = stereoTwistFromLine(line2);
		this._Z      = ((precluster.get_Phi() - other_precluster.get_Phi()) / (StereoAnglep - StereoAngleo)) * DeltaZ + Zref;

		double x1     = -precluster.get_Radius() * Math.sin(precluster.get_Phi());
		double y1     = -precluster.get_Radius() * Math.cos(precluster.get_Phi());
		double x2     = -other_precluster.get_Radius() * Math.sin(other_precluster.get_Phi());
		double y2     = -other_precluster.get_Radius() * Math.cos(other_precluster.get_Phi());
		double x_mean = (x1 + x2) / 2;
		double y_mean = (y1 + y2) / 2;
		this._Phi      = mod(-Math.PI / 2 - Math.atan2(y_mean, x_mean), (2 * Math.PI));
		this._Num_wire = (int) (precluster.get_Num_wire() + other_precluster.get_Num_wire()) / 2;
		this._X        = -this._Radius * Math.sin(this._Phi);
		this._Y        = -this._Radius * Math.cos(this._Phi);
		this._U        = this._X / (this._X * this._X + this._Y * this._Y);
		this._V        = this._Y / (this._X * this._X + this._Y * this._Y);
	}

	public Cluster(double X, double Y, double Z) {
		this._X = X;
		this._Y = Y;
		this._Z = Z;
	}

	/** Build a Cluster from a single PreCluster (one layer of a superlayer).
	 *  Used by the GNN path when a track covers a superlayer on only one
	 *  stereo layer — no stereo pair is available, so Z is taken from the
	 *  average wire-midpoint z of the PreCluster's hits rather than from a
	 *  stereo-angle computation. DocaClusterRefiner falls back to a degenerate
	 *  DocaCluster when {@code get_PreClusters_list().size() != 2}, so
	 *  downstream is unaffected. */
	public Cluster(PreCluster precluster) {
		this._PreClusters_list = new ArrayList<>();
		_PreClusters_list.add(precluster);
		this._Radius   = precluster.get_Radius();
		this._Phi      = precluster.get_Phi();
		this._X        = precluster.get_X();
		this._Y        = precluster.get_Y();
		this._Num_wire = (int) precluster.get_Num_wire();
		double r2 = this._X * this._X + this._Y * this._Y;
		if (r2 > 0.0) {
			this._U = this._X / r2;
			this._V = this._Y / r2;
		}
		double zSum = 0.0;
		int    zCount = 0;
		for (Hit h : precluster.get_hits_list()) {
			Line3D line = h.getLine();
			if (line != null) { zSum += line.midpoint().z(); zCount++; }
		}
		this._Z = (zCount > 0) ? zSum / zCount : 0.0;
	}

	@Override
	public String toString() {
		return "Cluster{" + "_X=" + _X + ", _Y=" + _Y + ", _Z=" + _Z + '}';
	}

	public ArrayList<PreCluster> get_PreClusters_list() {
		return _PreClusters_list;
	}

	double mod(double a, double b) {
		return a - b * Math.floor(a / b);
	}

	public double get_Radius() {
		return _Radius;
	}

	public void set_Radius(double _Radius) {
		this._Radius = _Radius;
	}

	public double get_Phi() {
		return _Phi;
	}

	public void set_Phi(double _Phi) {
		this._Phi = _Phi;
	}

	public double get_Z() {
		return _Z;
	}

	public void set_Z(double _Z) {
		this._Z = _Z;
	}

	public boolean is_Used() {
		return _Used;
	}

	public void set_Used(boolean _Used) {
		this._Used = _Used;
	}

	public int get_Num_wire() {
		return _Num_wire;
	}

	public void set_Num_wire(int _Num_wire) {
		this._Num_wire = _Num_wire;
	}

	public double get_X() {
		return _X;
	}

	public void set_X(double _X) {
		this._X = _X;
	}

	public double get_Y() {
		return _Y;
	}

	public void set_Y(double _Y) {
		this._Y = _Y;
	}

	public double get_U() {
		return _U;
	}

	public void set_U(double _U) {
		this._U = _U;
	}

	public double get_V() {
		return _V;
	}

	public void set_V(double _V) {
		this._V = _V;
	}

	public int get_trackId() {
		return _trackId;
	}

	public void set_trackId(int trackId) {
		this._trackId = trackId;
	}
}

package org.jlab.clas.tracking.kalmanfilter;

import java.util.ArrayList;
import java.util.List;
import org.jlab.clas.tracking.objects.Strip;
import org.jlab.geom.prim.Plane3D;
import org.jlab.geom.prim.Point3D;
import org.jlab.geom.prim.Arc3D;
import org.jlab.geom.prim.Cylindrical3D;
import org.jlab.geom.prim.Line3D;
import org.jlab.geom.prim.Transformation3D;
import org.jlab.geom.prim.Vector3D;

/**
 *
 * @author ziegler
 * @author Tongtong Cao
 */
public class Surface implements Comparable<Surface> {
    
    public Type type;
    public Plane3D plane;
    public Point3D measPoint;
    public Point3D measPoint_err;
    // Todo: make a line to replace two end points
    public Point3D lineEndPoint1;
    public Point3D lineEndPoint2;
    public Point3D finitePlaneCorner1;
    public Point3D finitePlaneCorner2;
    public Cylindrical3D cylinder;
    public Cylindrical3D lineVolume;
    private Transformation3D toGlobal = new Transformation3D();
    private Transformation3D toLocal  = new Transformation3D();
    public Arc3D arc;
    public Strip strip;
    private double error;
    private int index;
    private int layer;
    private int sector;
    private List<Material> materials = new ArrayList<>();
    // this is for swimming
    public double swimAccuracy;
    public boolean passive = false;
    public double hemisphere = 1;
    
    public double[] unc = new double[2];
    public double[] doca = new double[2];
    public Line3D[] wireLine = new Line3D[2];
    public int region;
    public int superlayer;
    public int nMeas = 1;
    
        
    public void setNMeas(int n) {
    	nMeas = n;
    }
    
    public int getNMeas() {
    	return nMeas;
    }
    
    // For URWell
    public Surface(int sector, double x, double y, double z, double x_err, double y_err) {
        type = Type.PLANEWITHPOINT;
        this.sector = sector;
        Point3D point = new Point3D(x, y, z);
        measPoint = point;
        Point3D point_err = new Point3D(x_err, y_err, 0);
        measPoint_err = point_err;
        Material material_air = new Material("air", 0, 0, 0, 30400, 0, Units.CM);
        Material material_argon = new Material("argon", 0, 0, 0, 14, 0, Units.CM);
        materials.add(material_air);
        materials.add(material_argon);
    }
    
    // For DC
    public Surface(int region, double[] doca, double[] unc, double error, Line3D[] wireLine) {
    	type = Type.LINEDOCA;
    	this.region = region;
        this.doca = doca;
        this.unc = unc;
        this.error = error;
        this.wireLine = wireLine;
        Point3D point = new Point3D(0, 0, wireLine[0].origin().z());
        this.measPoint = point;
        Material material_air = new Material("air", 0, 0, 0, 30400, 0, Units.CM);
        Material material_argon = new Material("argon", 0, 0, 0, 14, 0, Units.CM);
        materials.add(material_air);
        materials.add(material_argon);
    }
    
    public Surface(Plane3D plane3d, Point3D refrPoint, Point3D c1, Point3D c2, double accuracy) {
        type = Type.PLANEWITHPOINT;
        plane = plane3d;
        measPoint = refrPoint;
        finitePlaneCorner1 = c1;
        finitePlaneCorner2 = c2;
        swimAccuracy = accuracy;
    }
    public Surface(Plane3D plane3d, Point3D endPoint1, Point3D endPoint2, Point3D c1, Point3D c2, double accuracy) {
        type = Type.PLANEWITHLINE;
        plane = plane3d;
        lineEndPoint1 = endPoint1;
        lineEndPoint2 = endPoint2;
        finitePlaneCorner1 = c1;
        finitePlaneCorner2 = c2;
        swimAccuracy = accuracy;
    }
    public Surface(Plane3D plane3d, Strip strp, Point3D c1, Point3D c2, double accuracy) {
        type = Type.PLANEWITHSTRIP;
        plane = plane3d;
        strip = strp;
        finitePlaneCorner1 = c1;
        finitePlaneCorner2 = c2;
        lineEndPoint1 = new Point3D(strip.getX(), strip.getY(), strip.getZ());
        lineEndPoint2 = new Point3D(strip.getX()+strip.getLength()*strip.getUx(), 
                strip.getY()+strip.getLength()*strip.getUy(), 
                strip.getZ()+strip.getLength()*strip.getUz());
        swimAccuracy = accuracy;
    }
    public Surface(Cylindrical3D cylinder3d, Strip strp, double accuracy) {
        type = Type.CYLINDERWITHSTRIP;
        cylinder = cylinder3d;
        strip = strp;
        lineEndPoint1 = new Point3D(strip.getX(), strip.getY(), strip.getZ());
        lineEndPoint2 = new Point3D(strip.getX()+strip.getLength()*strip.getUx(), 
                strip.getY()+strip.getLength()*strip.getUy(), 
                strip.getZ()+strip.getLength()*strip.getUz());
        swimAccuracy = accuracy;
    }
    public Surface(Cylindrical3D cylinder3d, Point3D refrPoint, double accuracy) {
        type = Type.CYLINDERWITHPOINT;
        cylinder = cylinder3d;
        measPoint = refrPoint;
        swimAccuracy = accuracy;
    }
    public Surface(Cylindrical3D cylinder3d, Point3D endPoint1, Point3D endPoint2, double accuracy) {
        type = Type.CYLINDERWITHLINE;
        cylinder = cylinder3d;
        lineEndPoint1 = endPoint1;
        lineEndPoint2 = endPoint2;
        swimAccuracy = accuracy;
    }

    public Surface(Cylindrical3D cylinder3d, Arc3D refArc, Point3D endPoint1, Point3D endPoint2, double accuracy) {
        type = Type.CYLINDERWITHARC;
        cylinder = cylinder3d;
        arc = refArc;
        if(endPoint1 == null) {
            lineEndPoint1 = arc.origin();
        }
        if(endPoint2 == null) {
            lineEndPoint2 = arc.end();
        }
        swimAccuracy = accuracy;
    }

    public Surface(Point3D endPoint1, Point3D endPoint2, double accuracy) {
        type = Type.LINE;
        lineEndPoint1 = endPoint1;
        lineEndPoint2 = endPoint2;
        swimAccuracy = accuracy;
    }

    public Surface(Point3D endPoint1, Point3D endPoint2, Cylindrical3D volume, double accuracy) {
        type = Type.LINE;
        lineEndPoint1 = endPoint1;
        lineEndPoint2 = endPoint2;
        lineVolume = volume;
        swimAccuracy = accuracy;
    }
    
    @Override
    public String toString() {
        String s = "Surface: ";
        s = s + String.format("Type=%s Index=%d  Layer=%d  Sector=%d  Emisphere=%.1f thickness=%.4f X0=%.4f Z/A=%.4f Error=%.4f Passive=%b",
                               this.type.name(), this.getIndex(),this.getLayer(),this.getSector(),this.hemisphere,this.getThickness(),this.getToverX0(),
                               this.getZoverA(),this.getError(), this.passive);
        if(type==Type.PLANEWITHSTRIP) {
            s = s + "\n\t" + this.plane.toString();
            s = s + "\n\t" + this.finitePlaneCorner1.toString();
            s = s + "\n\t" + this.finitePlaneCorner2.toString();
            s = s + "\n\t" + this.strip.toString();
        }
        else if(type==Type.CYLINDERWITHSTRIP) {
            s = s + "\n\t" + this.cylinder.toString();
            s = s + "\n\t" + this.strip.toString();
        }
        else if(type==Type.CYLINDERWITHLINE) {
            s = s + "\n\t" + this.cylinder.toString();
            s = s + "\n\t" + this.lineEndPoint1.toString();
            s = s + "\n\t" + this.lineEndPoint2.toString();
        }
        else if(type==Type.LINE) {
            s = s + "\n\t" + this.lineEndPoint1.toString();
            s = s + "\n\t" + this.lineEndPoint2.toString();
            if(this.lineVolume!=null) s = s + "\n\t" + this.lineVolume.toString();
        }
        return s;
    }
    /**
     * @return the error
     */
    public double getError() {
        return error;
    }

    /**
     * @param error the error to set
     */
    public void setError(double error) {
        this.error = error;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    /**
     * @return the layer
     */
    public int getLayer() {
        return layer;
    }

    /**
     * @param layer the layer to set
     */
    public void setLayer(int layer) {
        this.layer = layer;
    }
    
    /**
     * @return the superlayer
     */
    public int getSuperLayer() {
        return superlayer;
    }

    /**
     * @param superlayer the superlayer to set
     */
    public void setSuperLayer(int superlayer) {
        this.superlayer = superlayer;
    }

    /**
     * @return the sector
     */
    public int getSector() {
        return sector;
    }

    /**
     * @param sector the sector to set
     */
    public void setSector(int sector) {
        this.sector = sector;
    }

    public List<Material> getMaterials() {
        return materials;
    }

    public void addMaterial(Material m) {
        this.materials.add(m);
    }
    
    public void addMaterial(String name, double thickness, double density, double ZoverA, double X0, double IeV, Units unit) {
        this.materials.add(new Material(name, thickness, density, ZoverA, X0, IeV, unit));
    }
    
    public double getRadius() {
        if(this.cylinder!=null)
            return this.cylinder.baseArc().radius();
        else if(this.lineVolume!=null) 
            return this.lineVolume.baseArc().radius();
        else
            return 0;
    }
    
    public double getLength() {
        return this.cylinder.height();
    }
    
    public double getThickness() {
        double t = 0;
        for(Material m : this.materials) {
            t += m.getThickness();
        }
        return t;
    }

    public double getToverX0() {
        double lX0 = 0;
        for(Material m : this.materials) {
            lX0 += m.getThickness()/m.getX0();
        }
        return lX0;
    }

    public double getZoverA() {
        double ZA   = 0;
        double RhoX = 0;
        for(Material m : this.materials) {
            ZA += m.getThickness()*m.getDensity()*m.getZoverA();
            RhoX += m.getThickness()*m.getDensity();
        }
        return ZA/RhoX;
    }
    
    public double getTrackLength(Point3D pos, Vector3D dir) {
        return this.getTrackLength(pos, dir, 0);
    }
    
    public double getTrackLength(Point3D pos, Vector3D dir, int materialIndex) {
        if(this.type!=Type.PLANEWITHSTRIP && 
           this.type!=Type.CYLINDERWITHSTRIP && 
           this.type!=Type.LINE) 
           return this.getMaterials().get(materialIndex).getThickness();
        else {
            if(this.type==Type.PLANEWITHSTRIP) {
                Vector3D norm = this.plane.normal();
                return this.getMaterials().get(materialIndex).getThickness()/Math.abs(norm.dot(dir));
            }
            else if(this.type==Type.CYLINDERWITHSTRIP) {
                Vector3D axis = this.cylinder.getAxis().direction().asUnit();
                dir.sub(dir.projection(axis));
                return this.getMaterials().get(materialIndex).getThickness()/Math.abs(dir.mag());
            }
            else if(this.type==Type.LINE) {
                if(this.lineVolume==null) {
                    Vector3D line = this.lineEndPoint1.vectorTo(this.lineEndPoint2).asUnit();
                    dir.sub(dir.projection(line));
                    return this.getMaterials().get(materialIndex).getThickness()/Math.abs(dir.mag());
                }
                else {  
                    Line3D track = new Line3D(pos, dir);
                    List<Point3D> intersections = new ArrayList<>();
                    double trackLength = this.lineVolume.intersectionLength(track, intersections);
                    if(materialIndex==0) { 
                        return trackLength;
                    }
                    else {
                        for(Point3D p : intersections) {
                            if(this.lineVolume.isOnSurface(p)){
                                Vector3D axis = this.lineVolume.getAxis().direction().asUnit();
                                dir.sub(dir.projection(axis));
                                return this.getMaterials().get(materialIndex).getThickness()/Math.abs(dir.mag());
                            }
                        }
                        return 0;
                    }
                }
            }
            return 0;
        }
    }    
    
    public double getEloss(double p, double mass) {
        double dE = 0;
        for(int im=0; im<this.getMaterials().size(); im++) {
            Material m = this.getMaterials().get(im);
            dE += m.getEloss(p, mass);
        }    
        return dE;
    }
    
    public double getEloss(Point3D pos, Vector3D mom, double mass) {
        double dE = 0;
        double p = mom.mag();
        for(int im=0; im<this.getMaterials().size(); im++) {
            Material m = this.getMaterials().get(im);
            dE += m.getEloss(p, mass)*this.getTrackLength(pos, mom.asUnit(), im)/m.getThickness();
        }    
        return dE;
    }
     
    public double getElossScale(Point3D pos, Vector3D mom, double mass, int dir) {
        double scale = 0;
        double dE = -dir*this.getEloss(pos, mom, mass);
        double Ecorr = Math.sqrt(mom.mag2() + mass*mass) + dE;
        if(Ecorr>mass) scale = Math.sqrt(Ecorr*Ecorr - mass*mass)/mom.mag();
        mom.scale(scale);
        return scale;
    }
    
    public double getDx(Point3D pos, Vector3D mom) {
        double dx = 0;
        for(int im=0; im<this.getMaterials().size(); im ++) {
            Material m = this.getMaterials().get(im);
            dx += this.getTrackLength(pos, mom.asUnit(), im);
        }
        return dx;
    }
    
    public double getThetaMS(double p, double mass, double cosEntranceAngle) {
        double ToverX0 = this.getToverX0()/cosEntranceAngle;
        double beta = p / Math.sqrt(p * p + mass * mass);
        double thetaMS = 0;
        if(ToverX0>0) thetaMS = (0.0136/(beta*p))*Math.sqrt(ToverX0)*(1 + 0.038 * Math.log(ToverX0));
        return thetaMS;
    }
        
    public Transformation3D toGlobal() {
        return toGlobal;
    }

    public Transformation3D toLocal() {
        return toLocal;
    }

    public void setTransformation(Transformation3D transform) {
        this.toGlobal = transform;
        this.toLocal  = transform.inverse();
    }

    @Override
    public int compareTo(Surface o) {
       if (this.index > o.index) {
            return 1;
        } else {
            return -1;
        }
    }

}

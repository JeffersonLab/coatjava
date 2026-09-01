package org.jlab.rec.cvt.measurement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jlab.clas.tracking.kalmanfilter.Material;
import org.jlab.clas.tracking.kalmanfilter.Surface;
import org.jlab.clas.tracking.objects.Strip;
import org.jlab.detector.base.DetectorType;
import org.jlab.geom.prim.Arc3D;
import org.jlab.geom.prim.Cylindrical3D;
import org.jlab.geom.prim.Line3D;
import org.jlab.geom.prim.Plane3D;
import org.jlab.geom.prim.Point3D;
import org.jlab.geom.prim.Vector3D;
import org.jlab.geometry.prim.Line3d;
import org.jlab.rec.cvt.Constants;
import org.jlab.rec.cvt.Geometry;
import org.jlab.rec.cvt.bmt.BMTGeometry;
import org.jlab.rec.cvt.bmt.BMTType;
import org.jlab.rec.cvt.cluster.Cluster;
import org.jlab.rec.cvt.svt.SVTGeometry;
import org.jlab.rec.cvt.track.Seed;
import org.jlab.rec.cvt.track.StraightTrack;
import org.jlab.rec.cvt.trajectory.Helix;
import org.jlab.rec.cvt.trajectory.Ray;
import org.jlab.rec.cvt.trajectory.TrajectoryFinder;

/**
 *
 * @author devita
 */
public class Measurements {
    
    private static final int NSURFACES = SVTGeometry.NPASSIVE + 
                                         SVTGeometry.NLAYERS  +
                                         BMTGeometry.NPASSIVE +
                                         BMTGeometry.NLAYERS;
    private boolean cosmic   = false;
    private Surface[] cvtSurfaces;
    private boolean debug = false;
    private Seed _seed;
    public Measurements(double xbeam, double ybeam, boolean beamSpot) {
        this.initTargetSurfaces(xbeam, ybeam, beamSpot);
    }
    
    public Measurements(boolean cosmic) {
        this.cosmic  = cosmic;
        this.initCosmicSurfaces();
    }
    
    private void initTargetSurfaces(double xbeam, double ybeam, boolean beamSpot) {
        cvtSurfaces = new Surface[NSURFACES+2];
        this.add(MLayer.TARGET.getIndex(),       this.getTarget(xbeam, ybeam, beamSpot));
        this.add(MLayer.SCHAMBER.getIndex(),     Geometry.getInstance().getScatteringChamber());
        this.add(MLayer.SHIELD.getIndex(),       Geometry.getInstance().getTargetShield());
        this.add(MLayer.INNERSVTCAGE.getIndex(), Geometry.getInstance().getSVT().getFaradayCageSurfaces(0));
        this.add(MLayer.OUTERSVTCAGE.getIndex(), Geometry.getInstance().getSVT().getFaradayCageSurfaces(1)); 
        this.add(MLayer.BMTINNERTUBE.getIndex(), Geometry.getInstance().getBMT().getInnerTube()); 
        this.add(MLayer.BMTOUTERTUBE.getIndex(), Geometry.getInstance().getBMT().getOuterTube()); 
    }
    
    private void initCosmicSurfaces() {
        cvtSurfaces = new Surface[NSURFACES*2+3];
        this.add(MLayer.COSMICPLANE.getIndex(1),   this.getCosmicPlane());
        this.add(MLayer.SCHAMBER.getIndex(1),      Geometry.getInstance().getScatteringChamber(),1);
        this.add(MLayer.SHIELD.getIndex(1),        Geometry.getInstance().getTargetShield(),1);
        this.add(MLayer.INNERSVTCAGE.getIndex(1),  Geometry.getInstance().getSVT().getFaradayCageSurfaces(0),1);
        this.add(MLayer.OUTERSVTCAGE.getIndex(1),  Geometry.getInstance().getSVT().getFaradayCageSurfaces(1),1);       
        this.add(MLayer.BMTINNERTUBE.getIndex(1),  Geometry.getInstance().getBMT().getInnerTube(),1); 
        this.add(MLayer.BMTOUTERTUBE.getIndex(1),  Geometry.getInstance().getBMT().getOuterTube(),1); 
        this.add(MLayer.SCHAMBER.getIndex(-1),     Geometry.getInstance().getScatteringChamber(),-1);
        this.add(MLayer.SHIELD.getIndex(-1),       Geometry.getInstance().getTargetShield(), -1);
        this.add(MLayer.INNERSVTCAGE.getIndex(-1), Geometry.getInstance().getSVT().getFaradayCageSurfaces(0), -1);
        this.add(MLayer.OUTERSVTCAGE.getIndex(-1), Geometry.getInstance().getSVT().getFaradayCageSurfaces(1), -1);       
        this.add(MLayer.BMTINNERTUBE.getIndex(-1), Geometry.getInstance().getBMT().getInnerTube(),-1);        
        this.add(MLayer.BMTOUTERTUBE.getIndex(-1), Geometry.getInstance().getBMT().getOuterTube(),-1); 
    }
    
    private void add(int index, Surface surface) {
        if(!(0<=index && index<cvtSurfaces.length))
            throw new IllegalArgumentException("Error: invalid index ="+index);
        if(debug) System.out.println("adding at index " + index + " surface for layer/sector " + surface.getLayer() + "/" + surface.getSector() + " with type " + surface.type.name()+" hemisphere "+surface.hemisphere);
        surface.setIndex(index);
        cvtSurfaces[index] = surface;
    }
 
    private void add(int index, Surface surface, int hemisphere) {
        surface.hemisphere = hemisphere;
        this.add(index, surface);
    }
 
    private int getIndex(MLayer id, int hemisphere) {
        if(!cosmic)
            return id.getIndex(0);
        else
            return id.getIndex(hemisphere);
    }

    private Surface getTarget(double xbeam, double ybeam, boolean beamSpot) {
        Vector3D u = new Vector3D(0,0,1);
        Point3D  p = new Point3D(xbeam, ybeam, 0);
        Line3D   l = new Line3D(p, u);
        Surface target =  new Surface(l.origin(), l.end(), Constants.DEFAULTSWIMACC);
        if(Geometry.getInstance().getTargetCellSurface()!=null &&
           Geometry.getInstance().getTargetCellSurface().lineVolume!=null) {
            target.lineVolume = Geometry.getInstance().getTargetCellSurface().lineVolume;
        }
        for(Material m : Geometry.getInstance().getTargetMaterials())
            target.addMaterial(m);
        target.setError(Constants.getInstance().getBeamRadius());
        if(beamSpot)
            target.passive = false;
        else
            target.passive = true;
        return target;
    }    
    
    private static Surface getCTOF() {
        
        double radius    = Geometry.getInstance().getCTOF().getRadius(1);
        double thickness = Geometry.getInstance().getCTOF().getThickness(1);
        Line3d lineZ     = Geometry.getInstance().getCTOF().getPaddle(1).getLineZ();
        
        Point3D  center = new Point3D(        0, 0, lineZ.origin().z*10);
        Point3D  origin = new Point3D(radius*10, 0, lineZ.origin().z*10);
        Vector3D axis   = new Vector3D(0,0,1);
        Arc3D base = new Arc3D(origin, center, axis, 2*Math.PI);
        Cylindrical3D barrel = new Cylindrical3D(base, (lineZ.end().z -lineZ.origin().z)*10);
        
        Surface ctof = new Surface(barrel, new Strip(0, 0, 0), Constants.DEFAULTSWIMACC);
        ctof.addMaterial(Geometry.SCINTILLATOR.clone(thickness*10));
        ctof.setIndex(DetectorType.CTOF.getDetectorId());
        ctof.setLayer(1);
        ctof.setSector(1);
        ctof.passive=true;
        return ctof;
    }
    
    private static List<Surface> getCND() {
        List<Surface> surfaces = new ArrayList<>();
        
        Vector3D axis   = new Vector3D(0,0,1);
        
        for(int ilayer=0; ilayer<Geometry.getInstance().getCND().getSector(0).getSuperlayer(0).getNumLayers(); ilayer++) {
            
            Point3D paddle = Geometry.getInstance().getCND().getSector(0).getSuperlayer(0).getLayer(ilayer).getComponent(0).getMidpoint();
            
            double radius    = Math.sqrt(paddle.x()*paddle.x()+paddle.y()*paddle.y());
            double thickness = Geometry.getInstance().getCND().getSector(0).getSuperlayer(0).getLayer(ilayer).getComponent(0).getVolumeEdge(1).length();
            double length    = Geometry.getInstance().getCND().getSector(0).getSuperlayer(0).getLayer(ilayer).getComponent(0).getLength();
            Point3D       center = new Point3D(        0, 0, (paddle.z() - length/2)*10);
            Point3D       origin = new Point3D(radius*10, 0, (paddle.z() - length/2)*10);
            Arc3D           base = new Arc3D(origin, center, axis, 2*Math.PI);
            Cylindrical3D barrel = new Cylindrical3D(base, length*10);
            
            Surface cnd = new Surface(barrel, new Strip(0, 0, 0), Constants.DEFAULTSWIMACC);
            cnd.addMaterial(Geometry.SCINTILLATOR.clone(thickness*10));
            cnd.setIndex(DetectorType.CND.getDetectorId());
            cnd.setLayer(ilayer+1);
            cnd.setSector(1);
            cnd.passive=true;
            surfaces.add(cnd);
        }
        return surfaces;
    }
    
    public static List<Surface> getOuters() {
        List<Surface> surfaces = new ArrayList<>();
        surfaces.add(getCTOF());
        surfaces.addAll(getCND());
        return surfaces;
    }
    
    private Surface getCosmicPlane() {
        Point3D point = new Point3D(0,0,0);
        Point3D   ep1 = new Point3D(-300,0,0);
        Point3D   ep2 = new Point3D( 300,0,0);
        Vector3D  dir = new Vector3D(0,1,0); 
        Plane3D plane = new Plane3D(point, dir);
        Surface cosmic = new Surface(plane, point,ep1, ep2,Constants.DEFAULTSWIMACC);
        cosmic.addMaterial(Geometry.VACUUM);
        cosmic.setError(1);
        cosmic.hemisphere = 1;
        int inters=plane.intersection(new Line3D(ep1, ep2), cosmic.rayInterc);
        cosmic.passive = true;
        return cosmic;
    }
    
    public List<Surface> getMeasurements(Seed seed) {
        this.reset();
        this.addClusters(seed);
        this.addMissing(seed);
        List<Surface> surfaces = new ArrayList<>();
        for(Surface surf : cvtSurfaces) {
            if(surf!=null) {
                if(debug) System.out.println("USING SURFACE "+surf.toString());
                surfaces.add(surf);
            }
        }
        return surfaces;               
    }
    
    public List<Surface> getActiveMeasurements(Seed seed) {
        List<Surface> surfaces = this.getMeasurements(seed);
        List<Surface> active = new ArrayList<>();
        for(Surface surf : surfaces) {
            if(surf.passive && surf.getIndex()!=0) continue;
            active.add(surf);
            if(debug) System.out.println("ACTIVE SURFACE "+surf.toString());
        }
        return active;
    }

    
    public double getELoss(double p, double mass) {
        double pcorr = p;
        for(int i=0; i<cvtSurfaces.length; i++) {
            Surface surf = cvtSurfaces[i];
            if(surf==null) break;
            double E  = Math.sqrt(pcorr*pcorr + mass*mass);
            double dE = surf.getEloss(pcorr, mass);
            double Ecorr = E + dE;
            pcorr = Math.sqrt(Ecorr*Ecorr-mass*mass); 
            if(debug) System.out.println(p + " " + pcorr + "\n" + surf.toString());
        }
        return pcorr;
    }

    public List<Surface> getMeasurements(StraightTrack cosmic) {
        this.reset();
        this.addClusters(cosmic);
        this.addMissing(cosmic);
        List<Surface> surfaces ;
        Map<Integer, Surface> sMap = new HashMap<>();
        for(Surface surf : cvtSurfaces) {
            if(surf!=null) {
                
                if(surf.passive && surf.getIndex()!=0) {
                    surf.hemisphere = this.getHemisphere((int)surf.hemisphere, cosmic.getRay(), surf); 
                    
                    if(!this.isCrossed(surf)) {
                        if(debug) System.out.println("Removing surface "+surf.toString()+" " + surf.passive );
                        continue;
                    }
                }
                
                sMap.put(surf.getIndex(), surf);
                //surfaces.add(surf);
            }
        }
        surfaces = new ArrayList<>(sMap.values());
         if(debug) 
            for(Surface surf : surfaces) {
                System.out.println("USED "+surf.toString());
            }
        return surfaces;
    }
    
    public List<Surface> getActiveMeasurements(StraightTrack cosmic) {
        List<Surface> surfaces = this.getMeasurements(cosmic);
        List<Surface> active = new ArrayList<>();
        for(Surface surf : surfaces) {
            if(surf.passive && surf.getIndex()!=0) continue;
            active.add(surf);
        }
        return active;
    }

    private void addClusters(Seed seed) {
        this.addClusters(DetectorType.BST, this.getClusterSurfaces(DetectorType.BST, seed.getClusters(), seed));
        this.addClusters(DetectorType.BMT, this.getClusterSurfaces(DetectorType.BMT, seed.getClusters(), seed));
    }
    
    private void addClusters(StraightTrack cosmic) {
        this.addClusters(DetectorType.BST, this.getClusterSurfaces(DetectorType.BST, cosmic.getClusters(), cosmic.getRay()));
        this.addClusters(DetectorType.BMT, this.getClusterSurfaces(DetectorType.BMT, cosmic.getClusters(), cosmic.getRay()));
    }

    private void addClusters(DetectorType type, List<Surface> clusters) {
        for(Surface cluster : clusters) {
            int hemisphere = 0;
            if(cosmic) hemisphere = (int) cluster.hemisphere;
            int index = MLayer.getType(type, cluster.getLayer()).getIndex(hemisphere);
            this.add(index, cluster);
        }
    }
        
//    private List<Surface> getClusterSurfaces(DetectorType type, List<Cluster> clusters, int hemisphere) {
//        
//        List<Surface> surfaces = this.getClusterSurfaces(type, clusters);        
//        for(Surface surf : surfaces) {
//            surf.hemisphere = hemisphere;
//        }
//        return surfaces;
//    }
        
    private List<Surface> getClusterSurfaces(DetectorType type, List<Cluster> clusters, Ray ray) {
       List<Surface> surfaces = new ArrayList<>();         
       for(Cluster cluster : clusters) {
            Surface surf = this.getClusterSurface(type, cluster);
            if(surf!=null) {
                surf.hemisphere = this.getHemisphere(cluster, ray, surf); 
                surfaces.add(surf);
            }
       }
        return surfaces;
    }
    
    private List<Surface> getClusterSurfaces(DetectorType type, List<Cluster> clusters, Seed seed) {
        
        List<Surface> surfaces = new ArrayList<>();        
        for(Cluster cluster : clusters) {
            Surface surf = this.getClusterSurface(type, cluster);
            if(surf!=null) {
                surf.hemisphere = this.getHemisphere(cluster, seed, surf);
                surfaces.add(surf);
            }
        }
        return surfaces;
    }
    private Surface getClusterSurface(DetectorType type, Cluster cluster) {
        if(cluster.getDetector()!=type) return null;
        int layer = MLayer.getType(type, cluster.getLayer()).getCVTLayer();
        Surface surface = cluster.measurement();
        if((int)Constants.getInstance().getUsedLayers().get(layer)<1)
            surface.passive=true;
        return surface;
    }
    
    private void addMissing(Seed seed) {
        for(int i=0; i<cvtSurfaces.length; i++) {
            if(cvtSurfaces[i]==null) {
                int id = MLayer.getId(i, 0);
                int layer = MLayer.getType(id).getLayer();
                if(layer>0) {
                    DetectorType type = MLayer.getDetectorType(id);
                    Surface surface = this.getDetectorSurface(seed, type, layer, 0);
                    if(surface == null) continue;
                    surface.passive=true;
                    if(debug) System.out.println("Generating surface for missing index " + i + " detector " + type.getName() + " layer " + layer + " sector " + surface.getSector());
                    this.add(i, surface);
                }
            }
        }
    }
                
    private void addMissing(StraightTrack ray) {
        for(int i=0; i<cvtSurfaces.length; i++) {
            if(cvtSurfaces[i]==null) {
                int hemisphere = MLayer.getHemisphere(i);
                int id = MLayer.getId(i, hemisphere);
                int layer = MLayer.getType(id).getLayer();
                if(layer>0) {
                    DetectorType type = MLayer.getDetectorType(id);
                    Surface surface = this.getDetectorSurface(ray, type, layer, hemisphere);
                    if(surface == null) continue;
                    surface.hemisphere = this.getHemisphere(hemisphere, ray.getRay(), surface); //resets hemisphere for shallow angle tracks
                    surface.passive=true;
                    if(debug) System.out.println("Generating surface for missing index " + i +" id "+id+ " detector " + type.getName() + " layer " + layer + " sector " + surface.getSector()+" hemisphere "+
                            surface.hemisphere+" y "+surface.rayInterc);
                    this.add(i, surface);
                }
            }
        }
    }
                
    private Surface getDetectorSurface(Seed seed, DetectorType type, int layer, int hemisphere) {
        int sector = this.getSector(seed, type, layer, hemisphere);
        if(sector>0)
            return this.getDetectorSurface(type, layer, sector);
        return null;
    }

    
    private Surface getDetectorSurface(StraightTrack ray, DetectorType type, int layer, int hemisphere) {
        int sector = this.getSector(ray, type, layer, hemisphere);
        if(sector>0)
            return this.getDetectorSurface(type, layer, sector);
        return null;
    }

    private Surface getDetectorSurface(DetectorType type, int layer, int sector) {
        Surface surface = null;
        if(type==DetectorType.BST)
            surface = Geometry.getInstance().getSVT().getSurface(layer, sector, new Strip(0, 0, 0));
        else if(type==DetectorType.BMT)
            surface = Geometry.getInstance().getBMT().getSurface(layer, sector, new Strip(0, 0, 0));
        return surface;
    }

    
    private int getSector(Seed seed, DetectorType type, int layer, int hemisphere) {
        Helix helix = seed.getHelix();
        if(helix==null)
            return 0;
        if(type==DetectorType.BST) { 
            int twinLayer = Geometry.getInstance().getSVT().getTwinLayer(layer);
            int twinIndex = MLayer.getType(DetectorType.BST, twinLayer).getIndex(hemisphere);
            if(cvtSurfaces[twinIndex]!=null) 
                return cvtSurfaces[twinIndex].getSector();
            Point3D traj = helix.getPointAtRadius(Geometry.getInstance().getSVT().getLayerRadius(layer));
            if(traj!=null && !Double.isNaN(traj.z())) 
                return Geometry.getInstance().getSVT().getSector(layer, traj);
        }
        else if(type==DetectorType.BMT) {
            Point3D traj = seed.getHelix().getPointAtRadius(Geometry.getInstance().getBMT().getRadius(layer));
            if(traj!=null && !Double.isNaN(traj.z())) 
                return Geometry.getInstance().getBMT().getSector(0, traj);
            else if(layer>1) {
                int twinIndex = MLayer.getType(DetectorType.BMT, layer-1).getIndex(hemisphere);
                if(cvtSurfaces[twinIndex]!=null)
                    return cvtSurfaces[twinIndex].getSector();
            }
        }
        return 0;
    }
    
    private int getSector(StraightTrack cosmic, DetectorType type, int layer, int hemisphere) {

        if(type==DetectorType.BST) {   
            int twinLayer = Geometry.getInstance().getSVT().getTwinLayer(layer);
            int twinIndex = MLayer.getType(DetectorType.BST, twinLayer).getIndex(hemisphere);
            if(cvtSurfaces[twinIndex]!=null)
                return cvtSurfaces[twinIndex].getSector();
           
            double[][][] trajs = TrajectoryFinder.calcTrackIntersSVT(cosmic.getRay());
            for(int i=0; i<SVTGeometry.NSECTORS[layer-1]; i++) {
                if(trajs[layer-1][i][0]!=-999 && hemisphere==(int) Math.signum(trajs[layer-1][i][1])) {                       
                    return i+1;
                }
            }
        }
        else if(type==DetectorType.BMT) {
            double[][][] trajs = TrajectoryFinder.calcTrackIntersBMT(cosmic.getRay(), 1);
            double x = trajs[layer-1][(hemisphere+1)/2][0];
            double y = trajs[layer-1][(hemisphere+1)/2][1];
            double z = trajs[layer-1][(hemisphere+1)/2][2];
            return Geometry.getInstance().getBMT().getSector(layer, Math.atan2(y, x));
        }
        return 0;
        
    }
    
    
    
    private Point3D getRayInters(Cluster cluster, Ray ray, Surface surface) {
        if (ray == null || surface == null) {
            return new Point3D(Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY);
        }

        Line3D line = ray.toLine(); // Shared line for both cylinder and plane
        if (surface.cylinder != null) {
            return handleCylinderIntersection(cluster, surface.cylinder, line);
        }

        if (surface.plane != null) {
            return handlePlaneIntersection(line, surface.plane);
        }

        return new Point3D(Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY); // Default if no surface type matched
    }

    private Point3D handleCylinderIntersection(Cluster cluster, Cylindrical3D cylinder, Line3D line) {
        if (cluster.getType() != BMTType.C) {
            return cluster.center(); // For non-C type clusters, return the center 
        }

        List<Point3D> intersections = new ArrayList<>();
        int intersectionCount = cylinder.intersection(line, intersections);

        switch (intersectionCount) {
            case 0:
                return new Point3D(Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY);
            case 1:
                return intersections.get(0);
            case 2:
                // Choose the intersection closest to the cluster center on the z-axis
                Point3D closest = getClosestIntersectionToClusterZ(cluster, intersections);
                return closest;
            default:
                return new Point3D(Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY);
        }
    }

    private Point3D getRayInters(int h, Ray ray, Surface surface) {
        if (ray == null || surface == null) {
            return new Point3D(Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY);
        }

        Line3D line = ray.toLine(); // Shared line for both cylinder and plane
        if (surface.cylinder != null) {
            return handleCylinderIntersection(h, surface.cylinder, line);
        }

        if (surface.plane != null) {
            return handlePlaneIntersection(line, surface.plane);
        }

        return new Point3D(Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY); // Default if no surface type matched
    }
    
    private Point3D handleCylinderIntersection(int h, Cylindrical3D cylinder, Line3D line) {
        
        List<Point3D> intersections = new ArrayList<>();
        int intersectionCount = cylinder.intersection(line, intersections);

        switch (intersectionCount) {
            case 0:
                return new Point3D(Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY);
            case 1:
                return intersections.get(0);
            case 2:
                // Choose 
                Point3D yFirst = intersections.get(0);
                Point3D ySecond =intersections.get(1);

                if(Math.signum(yFirst.y())==h) {
                    return yFirst ; 
                } else {
                    return ySecond;
                }
            
            default:
                return new Point3D(Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY);
        }
    }
    
    private Point3D handlePlaneIntersection(Line3D line, Plane3D plane) {
        Point3D intersection = new Point3D();
        if (plane.intersection(line, intersection) != 0) {
            return intersection;
        }
        return new Point3D(Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY);
    }

    private Point3D getClosestIntersectionToClusterZ(Cluster cluster, List<Point3D> intersections) {
        Point3D first = intersections.get(0);
        Point3D second = intersections.get(1);

        double distanceToFirst = Math.abs(cluster.center().z() - first.z());
        double distanceToSecond = Math.abs(cluster.center().z() - second.z());

        return distanceToFirst < distanceToSecond ? first : second;
    }

    private int getHemisphere(Cluster cluster, Ray ray, Surface surface){
        Point3D Y = this.getRayInters(cluster, ray, surface);
        if(Y.y() == Double.POSITIVE_INFINITY) return 0;
        surface.rayInterc=Y;
        return (int) Math.signum(Y.y());
        
    }
    
    private int getHemisphere(int h, Ray ray, Surface surface){
        Point3D Y = this.getRayInters(h, ray, surface);
        if(Y.y() == Double.POSITIVE_INFINITY) return 0;
        surface.rayInterc=Y;
        return (int) Math.signum(Y.y());
        
    }
    
    private boolean isCrossed(Surface surface) {
        if (surface.cylinder == null) {
            return false;
        }
        //is Crossed is called after getHemisphere which computes the intersection of the ray with the cylinder
        // so there is no need to recompute it and it can just use the hemisphere to determine if the intersection was valid
        if(surface.hemisphere==0) {
            return false;
        } else {
            return true;
        }
        
    }
    //functionality to find out if a surface is crossed for surfaces where the hemisphere has not been set yet (not used at present
    private boolean isCrossed(Cluster cluster, Ray ray, Surface surface) {
        int h = this.getHemisphere(cluster, ray, surface);
        if(h==0) {
            return false;
        } else {
            return true;
        }
    }
    private int getHemisphere(Cluster cluster, Seed seed, Surface surface) {
        if(surface.cylinder==null)
            return 0;
        if(cluster.getType()==BMTType.C) {
            if(seed==null)
                return 0;
            double r = cluster.getRadius();
            Point3D vAtR = seed.getHelix().getPointAtRadius(r);
            return (int) Math.signum(vAtR.y());
        } else {
            return (int) Math.signum(cluster.center().y()); 
        }
    }
    private void reset() {
        for(int i=0; i<cvtSurfaces.length; i++) {
            int hemisphere = MLayer.getHemisphere(i);
            int id = MLayer.getId(i, hemisphere);
            if(!cosmic) id= MLayer.getId(i, 0);
            DetectorType type = MLayer.getDetectorType(id);
            if(type==DetectorType.BST || type==DetectorType.BMT) {
                cvtSurfaces[i] = null;
                if(debug) System.out.println("Resetting surface with index " + i + " in hemisphere " + hemisphere + " with id " + id + " and DetectorType " + type.getName());
            }
        }
    }

    
}

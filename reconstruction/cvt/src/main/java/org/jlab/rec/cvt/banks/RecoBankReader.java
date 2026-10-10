package org.jlab.rec.cvt.banks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jlab.detector.base.DetectorType;
import org.jlab.geom.prim.Arc3D;
import org.jlab.geom.prim.Line3D;
import org.jlab.geom.prim.Point3D;
import org.jlab.geom.prim.Vector3D;
import org.jlab.io.banks.BMT_Clusters;
import org.jlab.io.banks.BMT_Crosses;
import org.jlab.io.banks.BMT_Hits;
import org.jlab.io.banks.BST_Clusters;
import org.jlab.io.banks.BST_Crosses;
import org.jlab.io.banks.BST_Hits;
import org.jlab.io.banks.CVT_Seeds;
import org.jlab.io.banks.CVT_Tracks;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.rec.cvt.Constants;
import org.jlab.rec.cvt.Geometry;
import org.jlab.rec.cvt.bmt.BMTGeometry;
import org.jlab.rec.cvt.bmt.BMTType;
import org.jlab.rec.cvt.cluster.Cluster;
import org.jlab.rec.cvt.cross.Cross;
import org.jlab.rec.cvt.hit.Hit;
import org.jlab.rec.cvt.hit.Strip;
import org.jlab.rec.cvt.svt.SVTGeometry;
import org.jlab.rec.cvt.track.Seed;
import org.jlab.rec.cvt.trajectory.Helix;

/**
 *
 * @author devita
 */
public class RecoBankReader {
    
       
    public static List<Hit> readBSTHitBank(DataEvent event) {
        
        if(!event.hasBank("BST::Hits"))
            return null;
        else {
            List<Hit> hits = new ArrayList<>();        
            
            DataBank bank = event.getBank("BST::Hits");
            for(int i = 0; i < bank.rows(); i++) {
                int id     = bank.getShort(BST_Hits.ID, i);
                int sector = bank.getByte(BST_Hits.sector, i);
                int layer  = bank.getByte(BST_Hits.layer, i);
                int strip  = bank.getShort(BST_Hits.strip, i);
                double energy      = bank.getFloat(BST_Hits.energy, i);
                double time        = bank.getFloat(BST_Hits.time, i);
                double fitResidual = bank.getFloat(BST_Hits.fitResidual, i)*10;
                int clusterId  = bank.getShort(BST_Hits.clusterID, i);
                int trackId    = bank.getShort(BST_Hits.trkID, i);
                int trkStatus  = bank.getByte(BST_Hits.trkingStat, i);
                int status     = bank.getByte(BST_Hits.status, i);
                Hit hit = new Hit(DetectorType.BST, BMTType.UNDEFINED, sector, layer, new Strip(strip, energy, time));
                hit.getStrip().setLine(Geometry.getInstance().getSVT().getStrip(layer, sector, strip));
                hit.getStrip().setModule(Geometry.getInstance().getSVT().getModule(layer, sector));
                hit.getStrip().setNormal(Geometry.getInstance().getSVT().getNormal(layer, sector));
                hit.getStrip().setPitch(SVTGeometry.getPitch());
                hit.getStrip().setStatus(status);
                hit.setId(id);
                hit.setAssociatedClusterID(clusterId);
                hit.setAssociatedTrackID(trackId);
                hit.setTrkgStatus(trkStatus);
                hit.setdocaToTrk(fitResidual);
                hits.add(hit);
            }
            return hits;
        }
    }
            
    public static List<Hit>  readBMTHitBank(DataEvent event) {
        
        if(!event.hasBank("BMT::Hits"))
            return null;
        else {
            List<Hit> hits = new ArrayList<>();        
            
            DataBank bank = event.getBank("BMT::Hits");
            for(int i = 0; i < bank.rows(); i++) {
                int id     = bank.getShort(BMT_Hits.ID, i);
                int sector = bank.getByte(BMT_Hits.sector, i);
                int layer  = bank.getByte(BMT_Hits.layer, i);
                int strip  = bank.getShort(BMT_Hits.strip, i);
                double energy      = bank.getFloat(BMT_Hits.energy, i);
                double time        = bank.getFloat(BMT_Hits.time, i);
                double fitResidual = bank.getFloat(BMT_Hits.fitResidual, i)*10;
                int clusterId  = bank.getShort(BMT_Hits.clusterID, i);
                int trackId    = bank.getShort(BMT_Hits.trkID, i);
                int trkStatus  = bank.getByte(BMT_Hits.trkingStat, i);
                int status     = bank.getByte(BMT_Hits.status, i);
                Hit hit = new Hit(DetectorType.BMT, BMTGeometry.getDetectorType(layer), sector, layer, new Strip(strip, energy, time));
                hit.getStrip().setStatus(status);
                hit.setId(id);
                hit.setAssociatedClusterID(clusterId);
                hit.setAssociatedTrackID(trackId);
                hit.setTrkgStatus(trkStatus);
                hit.setdocaToTrk(fitResidual);
                hits.add(hit);
            }
            return hits;
        }
    }
                        
    public static Map<Integer, Cluster> readBSTClusterBank(DataEvent event, List<Hit> svthits) {
        
        if(!event.hasBank("BST::Clusters"))
            return null;
        else {
            Map<Integer, Cluster> clusters = new HashMap<>();        
            
            DataBank bank = event.getBank("BST::Clusters");
            for(int i = 0; i < bank.rows(); i++) {
                int id     = bank.getShort(BST_Clusters.ID, i);
                int tid    = bank.getShort(BST_Clusters.trkID, i);
                int sector = bank.getByte(BST_Clusters.sector, i);
                int layer  = bank.getByte(BST_Clusters.layer, i);
                double etot          = bank.getFloat(BST_Clusters.ETot, i);
                double time          = bank.getFloat(BST_Clusters.time, i);
                double centroid      = bank.getFloat(BST_Clusters.centroid, i);
                double resolution    = bank.getFloat(BST_Clusters.e, i)*10;
                double x1 = bank.getFloat(BST_Clusters.x1,   i)*10;
                double y1 = bank.getFloat(BST_Clusters.y1,   i)*10;
                double z1 = bank.getFloat(BST_Clusters.z1,   i)*10;
                double x2 = bank.getFloat(BST_Clusters.x2,   i)*10;
                double y2 = bank.getFloat(BST_Clusters.y2,   i)*10;
                double z2 = bank.getFloat(BST_Clusters.z2,   i)*10;
                double cx = bank.getFloat(BST_Clusters.cx,   i)*10;
                double cy = bank.getFloat(BST_Clusters.cy,   i)*10;
                double cz = bank.getFloat(BST_Clusters.cz,   i)*10;
                double lx = bank.getFloat(BST_Clusters.lx,   i);
                double ly = bank.getFloat(BST_Clusters.ly,   i);
                double lz = bank.getFloat(BST_Clusters.lz,   i);
                double nx = bank.getFloat(BST_Clusters.nx,   i);
                double ny = bank.getFloat(BST_Clusters.ny,   i);
                double nz = bank.getFloat(BST_Clusters.nz,   i);
                double sx = bank.getFloat(BST_Clusters.sx,   i);
                double sy = bank.getFloat(BST_Clusters.sy,   i);
                double sz = bank.getFloat(BST_Clusters.sz,   i);

                Cluster cls = new Cluster(DetectorType.BST, BMTType.UNDEFINED, sector, layer, id);
                cls.setAssociatedTrackID(tid);         
                cls.setLine(new Line3D(x1,y1,z1,x2,y2,z2));
                cls.setTotalEnergy(etot);
                cls.setTime(time);
                cls.setCentroid(centroid);
                cls.setCentroidError(resolution/(SVTGeometry.getPitch()/Math.sqrt(12)));
                cls.setResolution(resolution);
                cls.setL(new Vector3D(lx,ly,lz));
                cls.setN(new Vector3D(nx,ny,nz));
                cls.setS(new Vector3D(sx,sy,sz));
                cls.setPhi(Math.atan2(cy,cx));
                cls.setPhi0(Math.atan2(cy,cx));
                Hit seedHit = null;
                double seedE = -1;
                for(Hit h : svthits) {
                    if(h.getAssociatedClusterID()==id) {
                        cls.add(h);
                        if(h.getStrip().getEdep()>seedE) {
                            seedE = h.getStrip().getEdep();
                            seedHit = h;
                        }
                    }
                }
                cls.setSeed(seedHit);
                clusters.put(id, cls);
            }
            return clusters;
        }
    }
        
        
    public static Map<Integer, Cluster> readBMTClusterBank(DataEvent event, List<Hit> bmthits) {
        
        if(!event.hasBank("BMT::Clusters"))
            return null;
        else {
            Map<Integer, Cluster> clusters = new HashMap<>();       
            
            DataBank bank = event.getBank("BMT::Clusters");
            for(int i = 0; i < bank.rows(); i++) {
                int id     = bank.getShort(BMT_Clusters.ID, i);
                int tid    = bank.getShort(BMT_Clusters.trkID, i);
                int sector = bank.getByte(BMT_Clusters.sector, i);
                int layer  = bank.getByte(BMT_Clusters.layer, i);
                double etot          = bank.getFloat(BMT_Clusters.ETot, i);
                double time          = bank.getFloat(BMT_Clusters.time, i);
                double centroid      = bank.getFloat(BMT_Clusters.centroid, i);
                double centroidValue = bank.getFloat(BMT_Clusters.centroidValue, i);
                double centroidError = bank.getFloat(BMT_Clusters.centroidError, i);
                double resolution    = bank.getFloat(BMT_Clusters.e, i)*10;
                double x1 = bank.getFloat(BMT_Clusters.x1,   i)*10;
                double y1 = bank.getFloat(BMT_Clusters.y1,   i)*10;
                double z1 = bank.getFloat(BMT_Clusters.z1,   i)*10;
                double x2 = bank.getFloat(BMT_Clusters.x2,   i)*10;
                double y2 = bank.getFloat(BMT_Clusters.y2,   i)*10;
                double z2 = bank.getFloat(BMT_Clusters.z2,   i)*10;
                double cx = bank.getFloat(BMT_Clusters.cx,   i)*10;
                double cy = bank.getFloat(BMT_Clusters.cy,   i)*10;
                double ax1 = bank.getFloat(BMT_Clusters.ax1,   i)*10;
                double ay1 = bank.getFloat(BMT_Clusters.ay1,   i)*10;
                double az1 = bank.getFloat(BMT_Clusters.az1,   i)*10;
                double ax2 = bank.getFloat(BMT_Clusters.ax2,   i)*10;
                double ay2 = bank.getFloat(BMT_Clusters.ay2,   i)*10;
                double az2 = bank.getFloat(BMT_Clusters.az2,   i)*10;
                double cz = bank.getFloat(BMT_Clusters.cz,   i)*10;
                double lx = bank.getFloat(BMT_Clusters.lx,   i);
                double ly = bank.getFloat(BMT_Clusters.ly,   i);
                double lz = bank.getFloat(BMT_Clusters.lz,   i);
                double nx = bank.getFloat(BMT_Clusters.nx,   i);
                double ny = bank.getFloat(BMT_Clusters.ny,   i);
                double nz = bank.getFloat(BMT_Clusters.nz,   i);
                double sx = bank.getFloat(BMT_Clusters.sx,   i);
                double sy = bank.getFloat(BMT_Clusters.sy,   i);
                double sz = bank.getFloat(BMT_Clusters.sz,   i);   
                // cluster
                Cluster cls = new Cluster(DetectorType.BMT, BMTGeometry.getDetectorType(layer), sector, layer, id);
                if(cls.getType()==BMTType.C) { 
                    double   theta  = bank.getFloat("theta",   i);
                    Line3D ln = new Line3D(ax1,ay1,az1, ax2,ay2,az2);
                    Point3D  origin = new Point3D(x1,y1,z1);
                    Point3D  center = ln.distance(origin).origin();
                    Vector3D normal = ln.direction();
                    Arc3D arc = new Arc3D(origin, center, normal, theta);
                    cls.setArc(arc);
                    cls.setCentroidValue(centroidValue*10);
                    cls.setCentroidError(centroidError*10);
                } else {
                    Line3D ln = new Line3D(x1,y1,z1, x2,y2,z2);
                    cls.setLine(ln);
                    cls.setCentroidValue(centroidValue);
                    cls.setCentroidError(centroidError);
                    cls.setPhi(Math.atan2(cy,cx));
                    cls.setPhi0(Math.atan2(cy,cx));
                }
                cls.setAssociatedTrackID(tid);
                cls.setTotalEnergy(etot);
                cls.setTime(time);
                cls.setCentroid(centroid);
                cls.setResolution(resolution);
                cls.setL(new Vector3D(lx,ly,lz));
                cls.setN(new Vector3D(nx,ny,nz));
                cls.setS(new Vector3D(sx,sy,sz));
                Hit seedHit = null;
                double seedE = -1;
                for(Hit h : bmthits) {
                    if(h.getAssociatedClusterID()==id) {
                        cls.add(h);
                        if(h.getStrip().getEdep()>seedE) {
                            seedE = h.getStrip().getEdep();
                            seedHit = h;
                        }
                    }
                }
                cls.setSeed(seedHit);
                clusters.put(id, cls);
            }
            return clusters;
        }
    }

    
    
    public static Map<Integer, Cross> readBSTCrossBank(DataEvent event, Map<Integer, Cluster> bstClusters) {
        
        if(!event.hasBank("BST::Crosses"))
            return null;
        else {
            Map<Integer, Cross> crosses = new HashMap<>();        
    
            DataBank bank = event.getBank("BST::Crosses");        
            for(int i = 0; i < bank.rows(); i++) {
                int id     = bank.getShort(BST_Crosses.ID, i);
                int tid    = bank.getShort(BST_Crosses.trkID, i);
                int sector = bank.getByte(BST_Crosses.sector, i);
                int region = bank.getByte(BST_Crosses.region, i);
                double x   = bank.getFloat(BST_Crosses.x, i)*10;
                double y   = bank.getFloat(BST_Crosses.y, i)*10;
                double z   = bank.getFloat(BST_Crosses.z, i)*10;
                double err_x = bank.getFloat(BST_Crosses.err_x, i)*10;
                double err_y = bank.getFloat(BST_Crosses.err_y, i)*10;
                double err_z = bank.getFloat(BST_Crosses.err_z, i)*10;
                double x0  = bank.getFloat(BST_Crosses.x0, i)*10;
                double y0  = bank.getFloat(BST_Crosses.y0, i)*10;
                double z0  = bank.getFloat(BST_Crosses.z0, i)*10;
                double err_x0 = bank.getFloat(BST_Crosses.err_x0, i)*10;
                double err_y0 = bank.getFloat(BST_Crosses.err_y0, i)*10;
                double err_z0 = bank.getFloat(BST_Crosses.err_z0, i)*10;
                double ux = bank.getFloat(BST_Crosses.ux, i);
                double uy = bank.getFloat(BST_Crosses.uy, i);
                double uz = bank.getFloat(BST_Crosses.uz, i);
                int clid1 = bank.getShort(BST_Crosses.Cluster1_ID, i);
                int clid2 = bank.getShort(BST_Crosses.Cluster2_ID, i);
                Cross cr = new Cross(DetectorType.BST, BMTType.UNDEFINED, sector, region, id);
                cr.setAssociatedTrackID(tid); 
                cr.isInSeed=true;
                cr.setDir(new Vector3D(ux,uy,uz));
                cr.setPoint(new Point3D(x,y,z));
                cr.setPointErr(new Point3D(err_x,err_y,err_z));
                cr.setPoint0(new Point3D(x0,y0,z0));
                cr.setPointErr0(new Point3D(err_x0,err_y0,err_z0));
                cr.setOrderedRegion(region);
                cr.setCluster1(bstClusters.get(clid1));
                cr.setCluster2(bstClusters.get(clid2));
                crosses.put(id,cr);
            }
            return crosses;
        }
    }
        
    public static Map<Integer, Cross> readBMTCrossBank(DataEvent event, Map<Integer, Cluster> bmtClusters) {
        
        if(!event.hasBank("BMT::Crosses"))
            return null;
        else {
            Map<Integer, Cross> crosses = new HashMap<>();         
    
            DataBank bank = event.getBank("BMT::Crosses");
            for(int i = 0; i < bank.rows(); i++) {
                int id     = bank.getShort(BMT_Crosses.ID, i);
                int tid    = bank.getShort(BMT_Crosses.trkID, i);
                int sector = bank.getByte(BMT_Crosses.sector, i);
                int region = bank.getByte(BMT_Crosses.region, i);
                int layer  = bank.getByte(BMT_Crosses.layer, i);
                double x   = bank.getFloat(BMT_Crosses.x, i)*10;
                double y   = bank.getFloat(BMT_Crosses.y, i)*10;
                double z   = bank.getFloat(BMT_Crosses.z, i)*10;
                double err_x = bank.getFloat(BMT_Crosses.err_x, i)*10;
                double err_y = bank.getFloat(BMT_Crosses.err_y, i)*10;
                double err_z = bank.getFloat(BMT_Crosses.err_z, i)*10;
                double x0  = bank.getFloat(BMT_Crosses.x0, i)*10;
                double y0  = bank.getFloat(BMT_Crosses.y0, i)*10;
                double z0  = bank.getFloat(BMT_Crosses.z0, i)*10;
                double err_x0 = bank.getFloat(BMT_Crosses.err_x0, i)*10;
                double err_y0 = bank.getFloat(BMT_Crosses.err_y0, i)*10;
                double err_z0 = bank.getFloat(BMT_Crosses.err_z0, i)*10;
                double ux = bank.getFloat(BMT_Crosses.ux, i);
                double uy = bank.getFloat(BMT_Crosses.uy, i);
                double uz = bank.getFloat(BMT_Crosses.uz, i);
                int clid1 = bank.getShort(BMT_Crosses.Cluster1_ID, i);
                if(layer==0) continue;
                Cross cr = new Cross(DetectorType.BMT, BMTGeometry.getDetectorType(layer), sector, region, id);
                cr.setAssociatedTrackID(tid); 
                cr.isInSeed=true;
                cr.setDir(new Vector3D(ux,uy,uz));
                cr.setPoint(new Point3D(x,y,z));
                cr.setPointErr(new Point3D(err_x,err_y,err_z));
                cr.setPoint0(new Point3D(x0,y0,z0));
                cr.setPointErr0(new Point3D(err_x0,err_y0,err_z0));
                cr.setOrderedRegion(Geometry.getInstance().getBMT().getLayer(region, cr.getType())+SVTGeometry.NREGIONS); // RDV check if is used and fix definition here and in CrossMaker
                cr.setCluster1(bmtClusters.get(clid1));
                crosses.put(id, cr);
            }
            return crosses;
        }
    }
    //sets seeds from first pass tracks
    public static Map<Integer, Seed> readCVTSeedsBank(DataEvent event, double xb, double yb, Map<Integer, Cross> svtCrosses, Map<Integer, Cross> bmtCrosses) {
        
        if(!event.hasBank("CVT::Seeds") || svtCrosses==null)
            return null;
        else {
            Map<Integer, Seed> seeds = new HashMap<>();        
    
            DataBank bank = event.getBank("CVT::Seeds");
            for(int i = 0; i < bank.rows(); i++) {
                int    tid    = bank.getShort(CVT_Seeds.ID, i);
                double pt     = bank.getFloat(CVT_Seeds.pt, i);
                double phi0   = bank.getFloat(CVT_Seeds.phi0, i);
                double tandip = bank.getFloat(CVT_Seeds.tandip, i);
                double z0     = bank.getFloat(CVT_Seeds.z0, i)*10;
                double d0     = bank.getFloat(CVT_Seeds.d0, i)*10;
                int    q      = bank.getByte(CVT_Seeds.q, i);
                int    type   = bank.getByte(CVT_Seeds.fittingMethod, i);
//                double xb     = bank.getFloat(CVT_Seeds.xb, i);
//                double yb     = bank.getFloat(CVT_Seeds.yb, i);
                Helix helix = new Helix( pt, d0, phi0, z0, tandip, q, xb, yb);
                double[][] covmatrix = new double[5][5];
                covmatrix[0][0] = bank.getFloat(CVT_Seeds.cov_d02, i)*10*10;
                covmatrix[0][1] = bank.getFloat(CVT_Seeds.cov_d0phi0, i)*10 ;
                covmatrix[0][2] = bank.getFloat(CVT_Seeds.cov_d0rho, i);
                covmatrix[1][0] = bank.getFloat(CVT_Seeds.cov_d0phi0, i)*10 ;
                covmatrix[1][1] = bank.getFloat(CVT_Seeds.cov_phi02, i);
                covmatrix[1][2] = bank.getFloat(CVT_Seeds.cov_phi0rho, i)/10 ;
                covmatrix[2][0] = bank.getFloat(CVT_Seeds.cov_d0rho, i);
                covmatrix[2][1] = bank.getFloat(CVT_Seeds.cov_phi0rho, i)/10 ;
                covmatrix[2][2] = bank.getFloat(CVT_Seeds.cov_rho2, i)/10/10;
                covmatrix[3][3] = bank.getFloat(CVT_Seeds.cov_z02, i)*10*10;
                covmatrix[3][4] = bank.getFloat(CVT_Seeds.cov_z0tandip, i)*10;
                covmatrix[4][3] = bank.getFloat(CVT_Seeds.cov_z0tandip, i)*10;
                covmatrix[4][4] = bank.getFloat(CVT_Seeds.cov_tandip2, i);
                double circleChi2 = bank.getFloat(CVT_Seeds.circlefit_chi2_per_ndf, i);
                double lineChi2   = bank.getFloat(CVT_Seeds.linefit_chi2_per_ndf, i);
                double chi2       = bank.getFloat(CVT_Seeds.chi2, i);
                int    ndf        = bank.getShort(CVT_Seeds.ndf, i);
                Seed seed = new Seed();
                seed.setId(tid);
                seed.setHelix(helix);
                seed.getHelix().setCovMatrix(covmatrix);
                seed.setStatus(type);
                seed.setCircleFitChi2PerNDF(circleChi2);
                seed.setLineFitChi2PerNDF(lineChi2);
                seed.setChi2(chi2);
                seed.setNDF(ndf);
                seed.percentTruthMatch = bank.getFloat(CVT_Seeds.fracmctru, i);
                seed.totpercentTruthMatch = bank.getFloat(CVT_Seeds.fracmcmatch, i);
                
                List<Cross> crossesOnTrk = new ArrayList<>();
                for (int j = 0; j < 9; j++) {
                    String hitStrg = "Cross";
                    hitStrg += (j + 1);
                    hitStrg += "_ID";  
                    int cid = (int) bank.getShort(hitStrg, i);
                    if(svtCrosses.containsKey(cid)) { 
                        crossesOnTrk.add(svtCrosses.get(cid));
                        
                    }
                    if(bmtCrosses!=null) {
                        if(bmtCrosses.containsKey(cid)) { 
                            crossesOnTrk.add(bmtCrosses.get(cid));
                        }        
                    }
                }
                
                seed.setCrosses(crossesOnTrk);
               
                seeds.put(tid, seed);
            }
            return seeds;
        }
    }    
    
    public static Map<Integer, Seed> readCVTTracksBank(DataEvent event, double xb, double yb, Map<Integer, Seed> cvtSeeds,
            Map<Integer, Cross> svtCrosses, Map<Integer, Cross> bmtCrosses) {
        
        if(!event.hasBank("CVT::Tracks"))
            return null;
        else {
            Map<Integer, Seed> seeds = new HashMap<>();          
    
            DataBank bank = event.getBank("CVT::Tracks");
            for(int i = 0; i < bank.rows(); i++) {
                int    tid    = bank.getShort(CVT_Tracks.ID, i);
                double pt     = bank.getFloat(CVT_Tracks.pt, i);
                double phi0   = bank.getFloat(CVT_Tracks.phi0, i);
                double tandip = bank.getFloat(CVT_Tracks.tandip, i);
                double z0     = bank.getFloat(CVT_Tracks.z0, i)*10;
                double d0     = bank.getFloat(CVT_Tracks.d0, i)*10;
                int    q      = bank.getByte(CVT_Tracks.q, i);
//                double xb     = bank.getFloat(CVT_Tracks.xb, i);
//                double yb     = bank.getFloat(CVT_Tracks.yb, i);
                Helix helix = new Helix( pt, d0, phi0, z0, tandip, q, xb, yb);
                double[][] covmatrix = new double[5][5];
                covmatrix[0][0] = bank.getFloat(CVT_Tracks.cov_d02, i)*10*10;
                covmatrix[0][1] = bank.getFloat(CVT_Tracks.cov_d0phi0, i)*10 ;
                covmatrix[0][2] = bank.getFloat(CVT_Tracks.cov_d0rho, i);
                covmatrix[1][0] = bank.getFloat(CVT_Tracks.cov_d0phi0, i)*10 ;
                covmatrix[1][1] = bank.getFloat(CVT_Tracks.cov_phi02, i);
                covmatrix[1][2] = bank.getFloat(CVT_Tracks.cov_phi0rho, i)/10 ;
                covmatrix[2][0] = bank.getFloat(CVT_Tracks.cov_d0rho, i);
                covmatrix[2][1] = bank.getFloat(CVT_Tracks.cov_phi0rho, i)/10 ;
                covmatrix[2][2] = bank.getFloat(CVT_Tracks.cov_rho2, i)/10/10;
                covmatrix[3][3] = bank.getFloat(CVT_Tracks.cov_z02, i)*10*10;
                covmatrix[3][4] = bank.getFloat(CVT_Tracks.cov_z0tandip, i)*10;
                covmatrix[4][3] = bank.getFloat(CVT_Tracks.cov_z0tandip, i)*10;
                covmatrix[4][4] = bank.getFloat(CVT_Tracks.cov_tandip2, i);
            //    int    status   = bank.getShort(CVT_Tracks.status, i);
            //    double chi2     = bank.getFloat(CVT_Tracks.chi2, i);
            //    int    ndf      = bank.getShort(CVT_Tracks.ndf, i);
            //    int    pid      = bank.getInt(CVT_Tracks.pid, i);
                int    seedId   = bank.getShort(CVT_Tracks.seedID, i);
                int    status   = Math.abs(bank.getShort(CVT_Tracks.status, i));
                int type = status - (int) (status/10) * 10; 
                Seed seed = cvtSeeds.get(seedId);
                seed.setId(tid);
                //seed.setHelix(seed.getHelix());
                //seed.getHelix().setCovMatrix(seed.getHelix().getCovMatrix());
                seed.setHelix(helix);
                seed.getHelix().setCovMatrix(covmatrix);
                seed.setStatus(type);
                seed.FirstPassIdx = i;
                List<Cross> crossesOnTrk = new ArrayList<>();
                for (int j = 0; j < 9; j++) {
                    String hitStrg = "Cross";
                    hitStrg += (j + 1);
                    hitStrg += "_ID";  
                    int cid = (int) bank.getShort(hitStrg, i);
                    if(svtCrosses.containsKey(cid)) { 
                        crossesOnTrk.add(svtCrosses.get(cid));
                        
                    }
                    if(bmtCrosses!=null) {
                        if(bmtCrosses.containsKey(cid)) { 
                            crossesOnTrk.add(bmtCrosses.get(cid));
                        }        
                    }
                }
                
                seed.setCrosses(crossesOnTrk);
                if(Constants.getInstance().seedingDebugMode) 
                    System.out.println("Recompose seed "+seed.toString());
                seeds.put(tid, seed);
            }
            return seeds;
        }
    }
    
}

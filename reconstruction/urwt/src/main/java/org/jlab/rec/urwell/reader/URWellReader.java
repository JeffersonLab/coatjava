package org.jlab.rec.urwell.reader;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jlab.geom.prim.Point3D;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;

/**
 *
 * @author Tongtong Cao
 */
public class URWellReader{
   
    private final List<URWellHit>       urHits     = new ArrayList<>();
    private final List<URWellCluster>   urClusters = new ArrayList<>();
    private final List<URWellCross>     urCrosses  = new ArrayList<>(); 
      
    private static List<Integer> pickedURWellRegions = new ArrayList<>(Arrays.asList(1, 2));
        
    public URWellReader(DataEvent event) {
        
        if(event.hasBank("URWT::hits"))
            this.readHits(event.getBank("URWT::hits"));
        if(event.hasBank("URWT::clusters"))
            this.readClusters(event.getBank("URWT::clusters"));
        if(event.hasBank("URWT::crosses"))
            this.readCrosses(event.getBank("URWT::crosses"));
    }
    
    public URWellReader(DataEvent event, String type) {
        if(event.hasBank("URWT::clusters"))
                this.readClusters(event.getBank("URWT::clusters"));
        
        
        if(type == "HB"){            
            if(event.hasBank("URWT::crosses"))
                this.readCrosses(event.getBank("URWT::crosses"));
        }
        else if(type == "TB"){ 
            if(event.hasBank("HitBasedTrkg::HBURWellCrosses"))
                this.readHBCrosses(event.getBank("HitBasedTrkg::HBURWellCrosses"));
        }
        else if(type == "AI"){   
            if(event.hasBank("HitBasedTrkg::AIURWellCrosses"))
                this.readHBCrosses(event.getBank("HitBasedTrkg::AIURWellCrosses"));
        }
    }
    
    public List<URWellHit> getUrwellHits() {
        return urHits;
    }

    public List<URWellCluster> getUrwellClusters() {
        return urClusters;
    }

    public List<URWellCross> getUrwellCrosses() {
        return urCrosses;
    } 
        
    public final void readHits(DataBank bank) {

        for(int i=0; i<bank.rows(); i++) {
            int    sector = bank.getByte("sector", i);
            int    layer  = bank.getByte("layer", i);
            int    strip  = bank.getShort("strip", i);
            double energy = bank.getFloat("energy", i);
            double time   = bank.getFloat("time", i);
            URWellHit hit = new URWellHit(sector, layer, strip, energy, time);
            urHits.add(hit);
        }
    }
    
    public final void readClusters(DataBank bank) {

        for(int i=0; i<bank.rows(); i++) {
            int    id = bank.getShort("id", i);
            int    sector = bank.getByte("sector", i);
            int    layer  = bank.getByte("layer", i);
            int    strip  = bank.getShort("strip", i);
            int    size   = bank.getShort("size", i);
            double energy = bank.getFloat("energy", i);
            double time   = bank.getFloat("time", i);
            double xo = bank.getFloat("xo", i);
            double yo = bank.getFloat("yo", i);
            double zo = bank.getFloat("zo", i);
            double xe = bank.getFloat("xe", i);
            double ye = bank.getFloat("ye", i);
            double ze = bank.getFloat("ze", i);
            Point3D pointOrigin = new Point3D(xo,yo,zo);
            Point3D pointEnd = new Point3D(xe,ye,ze);
            URWellCluster cluster = new URWellCluster(id, sector, layer, strip, size, energy, time, pointOrigin, pointEnd);
            urClusters.add(cluster);
        }
    }
    
    // Crosses with energy and time coincidence for their clusters are stored
    public final void readCrosses(DataBank bank) {

        for(int i=0; i<bank.rows(); i++) {
            int    region = bank.getByte("region", i);
            if(pickedURWellRegions.contains(region)){
                int id = bank.getShort("id", i);
                int    sector = bank.getByte("sector", i);            
                double x      = bank.getFloat("x", i);
                double y      = bank.getFloat("y", i);
                double z      = bank.getFloat("z", i);                        
                double energy = bank.getFloat("energy", i);
                double time   = bank.getFloat("time", i);
                int  cluster1 = bank.getShort("cluster1", i);
                int  cluster2 = bank.getShort("cluster2", i); 
                int status = bank.getShort("status", i); 
                URWellCross cross = new URWellCross(id, sector, region, x, y, z, energy, time, cluster1, cluster2, status);
                cross.setClusterIndex1(cluster1);
                cross.setClusterIndex2(cluster2);
                cross.setCluster1(urClusters);
                cross.setCluster2(urClusters);           
                if(cluster1<=urClusters.size()) urClusters.get(cluster1-1).setCrossIndex(i);
                if(cluster2<=urClusters.size()) urClusters.get(cluster2-1).setCrossIndex(i);           
                if(status == 0)            
                    urCrosses.add(cross);
            }
        }
    } 
    
        // Crosses with energy and time coincidence for their clusters are stored
    public final void readHBCrosses(DataBank bank) {

        for(int i=0; i<bank.rows(); i++) {
            int    region = bank.getByte("region", i);
            if(pickedURWellRegions.contains(region)){
                int id = bank.getShort("id", i);
                int tid = bank.getShort("tid", i);    
                int    sector = bank.getByte("sector", i);
                double x      = bank.getFloat("x", i);
                double y      = bank.getFloat("y", i);
                double z      = bank.getFloat("z", i);
                double x_local = bank.getFloat("x_local", i);
                double y_local = bank.getFloat("y_local", i);
                double z_local = bank.getFloat("z_local", i);  
                double energy = bank.getFloat("energy", i);
                double time   = bank.getFloat("time", i);
                int  cluster1 = bank.getShort("cluster1", i);
                int  cluster2 = bank.getShort("cluster2", i); 
                int status = bank.getShort("status", i); 
                URWellCross cross = new URWellCross(id, tid, sector, region, x, y, z, x_local, y_local, z_local, energy, time, cluster1, cluster2, status);
                cross.setClusterIndex1(cluster1);
                cross.setClusterIndex2(cluster2);
                cross.setCluster1(urClusters);
                cross.setCluster2(urClusters);
                if(cluster1<=urClusters.size()) urClusters.get(cluster1-1).setCrossIndex(i);
                if(cluster2<=urClusters.size()) urClusters.get(cluster2-1).setCrossIndex(i); 
                if(status == 0) 
                    urCrosses.add(cross);
            }
        }
    }
    
    public static void setPickedURWellRegions(String str){
        pickedURWellRegions.clear();
        if(str.equals( "R1")) pickedURWellRegions.add(1);
        else if(str.equals("R2")) pickedURWellRegions.add(2);
        else if(str.equals("R1R2")) {
            pickedURWellRegions.add(1);
            pickedURWellRegions.add(2);
        }
    }
}

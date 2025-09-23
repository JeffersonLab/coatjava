package org.jlab.rec.cvt.track;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jlab.geom.prim.Point3D;
import org.jlab.rec.cvt.Constants;
import org.jlab.rec.cvt.Geometry;
import org.jlab.rec.cvt.bmt.BMTConstants;
import org.jlab.rec.cvt.bmt.BMTGeometry;
import org.jlab.rec.cvt.cross.Cross;

public class TrackSeederRZ {
    
    private List<ArrayList<ArrayList<Cross>>> sortedCrosses;

    public boolean unUsedHitsOnly = false;
    private Map <Integer, Map <Integer, List<Cross>>> bmtcrs;
    BMTGeometry bmtGeom;
   
    public TrackSeederRZ(BMTGeometry bmtGeo) {
        bmtGeom = bmtGeo;
        bmtcrs= new HashMap<>();
        //init lists for scan
        sortedCrosses = new ArrayList<>();
        for(int i =0; i<3; i++) {
            sortedCrosses.add(i, new ArrayList<>() );
            for(int l =0; l<3; l++) {
                sortedCrosses.get(i).add(l,new ArrayList<>() );
            }
        }
      
    }
    
    
    public  List<ArrayList<Cross>> getSeeds(List<Cross> crosses,
            Map <Integer, Map <Integer, List<Cross>>> svtcrs) {
        List<ArrayList<Cross>> result = new ArrayList<>();
        bmtcrs.clear();
        //sort the crosses in lists
        for(Cross c : crosses) { 
            if(!svtcrs.containsKey(c.getSector()))
                    continue;
            
            if(bmtcrs.containsKey(c.getSector())) {  
                if(bmtcrs.get(c.getSector()).containsKey(c.getRegion())) {
                    bmtcrs.get(c.getSector()).get(c.getRegion()).add(c);
                } else {
                    bmtcrs.get(c.getSector()).put(c.getRegion(), new ArrayList<>());
                    bmtcrs.get(c.getSector()).get(c.getRegion()).add(c);
                }
            } else {
                bmtcrs.put(c.getSector(), new HashMap<>());
                bmtcrs.get(c.getSector()).put(c.getRegion(), new ArrayList<>());
                bmtcrs.get(c.getSector()).get(c.getRegion()).add(c);
            }
        }
        Map<Integer, ArrayList<Cross>> seeds = new HashMap<>();
        for(int sector =1; sector<4; sector++) { //loop over sectors
            if(!bmtcrs.containsKey(sector))
                continue;
            int[] N = new int[]{1,1,1}; //number of crosses in each region
            for(int creg =1; creg<4; creg++) { //loop over regions
                if(bmtcrs.get(sector).containsKey(creg)) { 
                    bmtcrs.get(sector).get(creg).sort(Comparator.comparing(Cross::getZ));
                    N[creg-1] = bmtcrs.get(sector).get(creg).size(); 
                }
            }
            
            List<Cross> seed = new ArrayList<>();
            List<Cross> seedij = new ArrayList<>();
            for(int i1 = 0; i1<N[0]; i1++) {
                for(int i2 = 0; i2<N[1]; i2++) {
                    for(int i3 = 0; i3<N[2]; i3++) {
                        
                        seed.clear();
                        if(bmtcrs.get(sector).containsKey(1))
                            seed.add(bmtcrs.get(sector).get(1).get(i1));
                        if(bmtcrs.get(sector).containsKey(2))
                            seed.add(bmtcrs.get(sector).get(2).get(i2));
                        if(bmtcrs.get(sector).containsKey(3))
                            seed.add(bmtcrs.get(sector).get(3).get(i3));
                        
                        if(seed.size()==1) 
                            continue;
                        if(seed.size()==2) 
                            if(this.interceptOK(seed.get(0), seed.get(1))){ 
                                seedij = new ArrayList<>();
                                seedij.add(seed.get(0));
                                seedij.add(seed.get(1));
                                int key = (seed.get(0).getId()-900)*1000+(seed.get(1).getId()-900);
                                if(!seeds.containsKey(key))
                                    seeds.put(key,(ArrayList<Cross>) seedij);
                            }
                        if(seed.size()==3) { 
                            if(this.line3OK(seed.get(0), seed.get(1), seed.get(2))) {
                                seedij = new ArrayList<>();
                                seedij.add(seed.get(0));
                                seedij.add(seed.get(1));
                                seedij.add(seed.get(2));
                                int key = (seed.get(0).getId()-900)*1000000+(seed.get(1).getId()-900)*1000+(seed.get(2).getId()-900);
                                if(!seeds.containsKey(key))
                                    seeds.put(key,(ArrayList<Cross>) seedij);
                            } else { 
                                if(this.interceptOK(seed.get(0), seed.get(1))) {
                                    seedij = new ArrayList<>();
                                    seedij.add(seed.get(0));
                                    seedij.add(seed.get(1));
                                    int key = (seed.get(0).getId()-900)*1000+(seed.get(1).getId()-900);
                                    if(!seeds.containsKey(key))
                                        seeds.put(key,(ArrayList<Cross>) seedij);
                                }
                                if(this.interceptOK(seed.get(0), seed.get(2))) {
                                    seedij = new ArrayList<>();
                                    seedij.add(seed.get(0));
                                    seedij.add(seed.get(2));
                                    int key = (seed.get(0).getId()-900)*1000+(seed.get(2).getId()-900);
                                    if(!seeds.containsKey(key))
                                        seeds.put(key,(ArrayList<Cross>) seedij);
                                }
                                if(this.interceptOK(seed.get(1), seed.get(2))) {
                                    seedij = new ArrayList<>();
                                    seedij.add(seed.get(1));
                                    seedij.add(seed.get(2));
                                    int key = (seed.get(1).getId()-900)*1000+(seed.get(2).getId()-900);
                                    if(!seeds.containsKey(key))
                                        seeds.put(key,(ArrayList<Cross>) seedij);
                                }
                            }
                        }
                    }
                }
            }
        }
        
        seeds.forEach((key,value) -> result.add(value));
        if(Constants.getInstance().seedingDebugMode) {
            System.out.println("ALL RZ SEEDS");
            for(List<Cross> s : result) {
                System.out.println("RZ SEED:");
                for(Cross c : s)
                    System.out.println(c.printInfo());
            }
        }
        removeCompleteZROverlaps(result);
        
        result.removeIf(s -> status(s) == 0);
        
        return result;
    }
    

    private void removeCompleteZROverlaps(List<ArrayList<Cross>> zrtracks) {
        List<ArrayList<Cross>>twoCros = new ArrayList<>();
        List<ArrayList<Cross>> threeCros = new ArrayList<>();
        List<ArrayList<Cross>> rmCros = new ArrayList<>();
        for(List<Cross> zrtrk : zrtracks) {
            if(zrtrk.size()==3) {
                threeCros.add((ArrayList<Cross>) zrtrk);
            }
            if(zrtrk.size()==2) 
                twoCros.add((ArrayList<Cross>) zrtrk);
        }
        boolean rm = false;
        for(List<Cross> zrtrk3 : threeCros) {
            for(List<Cross> zrtrk2 : twoCros) {
                rm = false;
                if(zrtrk2.get(0).getId()==zrtrk3.get(0).getId() && zrtrk2.get(1).getId()==zrtrk3.get(1).getId())   
                    rm = true;
                if(zrtrk2.get(0).getId()==zrtrk3.get(0).getId() && zrtrk2.get(1).getId()==zrtrk3.get(2).getId())   
                    rm = true;
                if(zrtrk2.get(0).getId()==zrtrk3.get(1).getId() && zrtrk2.get(1).getId()==zrtrk3.get(2).getId())   
                    rm = true;
                if(rm == true) {
                    rmCros.add((ArrayList<Cross>) zrtrk2);
                }
            }
        }
            
        zrtracks.removeAll(rmCros);
    }
    private boolean interceptOK(Cross c1, Cross c2) { 
        if(Constants.getInstance().seedingDebugMode) {
            System.out.println("RZ Condidering (2):");
            System.out.println(c1.printInfo());
            System.out.println(c2.printInfo());
        }
        boolean value = false;
        double sl = (c1.getPoint().z() - c2.getPoint().z())/(c1.getPoint().toVector3D().rho() - c2.getPoint().toVector3D().rho());
        double in = -sl*c1.getPoint().toVector3D().rho()+c1.getPoint().z();
        double targetCen = Geometry.getInstance().getTargetZOffset();
        double targetLen = Geometry.getInstance().getTargetHalfLength();
        
        if(Math.abs(targetCen-in)<targetLen+Constants.getInstance().getZRANGE())
           value = true;
        if(Constants.getInstance().seedingDebugMode) 
            System.out.println("Passing : "+in+" is "+ value+" for target at center z = "+targetCen+" and target length "+targetLen);
        return value;
    }

    private boolean line3OK(Cross c1, Cross c2, Cross c3) {
        if(Constants.getInstance().seedingDebugMode) {
            System.out.println("RZ Condidering (3):");
            System.out.println(c1.printInfo());
            System.out.println(c2.printInfo());
            System.out.println(c3.printInfo());
        }
        boolean value = false;
        if(interceptOK(c1,c3)==false)
            return value;
        double sl = (c1.getPoint().z() - c3.getPoint().z())/(c1.getPoint().toVector3D().rho() - c3.getPoint().toVector3D().rho());
        double in = -sl*c1.getPoint().toVector3D().rho()+c1.getPoint().z();
        
        double Rm = c2.getPoint().toVector3D().rho();
        double Zm = c2.getPoint().z();
        double Zc = sl*Rm +in;
        double Zerr = c2.getPointErr().z(); 
        if(Math.abs(Zc-Zm)<Zerr*5) {
            value = true;  
        } 
        if(Constants.getInstance().seedingDebugMode) 
            System.out.println("Passing: "+ value);
        return value;
    }

    private boolean missingCrossOK(Cross c1, Cross c3) {
        if(Constants.getInstance().seedingDebugMode) {
            System.out.println("RZ Search:");
            System.out.println(c1.printInfo());
            System.out.println(c3.printInfo());
        }
        boolean value = false;
        if(interceptOK(c1,c3)==false)
            return value;
        double sl = (c1.getPoint().z() - c3.getPoint().z())/(c1.getPoint().toVector3D().rho() - c3.getPoint().toVector3D().rho());
        double in = -sl*c1.getPoint().toVector3D().rho()+c1.getPoint().z();
        
        int region = findMissingRegion(c1, c3);
        double Rm =BMTConstants.getCRCRADIUS()[region-1];
        double Zc = sl*Rm +in;
        int expectedStrip = bmtGeom.getCstrip(region, new Point3D(0,0,Zc));
        if(expectedStrip==0) { 
            value = true;
        } else {
            value = false;
        }
        
        if(Constants.getInstance().seedingDebugMode) 
            System.out.println("Passing: "+ value);
        return value;
    }
    
    private int status (List<Cross> crosses) {
        int n = crosses.size();
        if(n==3) {
            return 1;
        } else {
            if(missingCrossOK(crosses.get(0), crosses.get(1))) {
                return 1;
            } else {
                return 0;
            }
        }
    }
    
    private int findMissingRegion(Cross c1, Cross c2) {
        // available layers
        Set<Integer> available = new HashSet<>(Arrays.asList(1, 2, 3));

        // remove the ones already used
        available.remove(c1.getRegion());
        available.remove(c2.getRegion());

        // now only the missing one remains
        return available.iterator().next();
    }

}

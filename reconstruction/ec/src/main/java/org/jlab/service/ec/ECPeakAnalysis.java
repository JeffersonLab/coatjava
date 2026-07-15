package org.jlab.service.ec;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jlab.geom.prim.Line3D;
import org.jlab.service.ec.ECPeakSplitter.ECPeakSplitterMargin;
import org.jlab.service.ec.ECPeakSplitter.ECPeakSplitterOriginal;

/**
 *
 * @author gavalian
 * @modified lcsmith 
 * @re-modified gavalian
 * 
 * Added a new functionality to split only peaks after constructing a cluster
 * then checking if the other views in the cluster need splitting. Hopefully 
 * this will work. This is the way.
 */

public class ECPeakAnalysis {
    
    static final int[][] otherLayers = new int[][]{
        {0,0},{2,3},{1,3},{1,2},{5,6},{4,6},{4,5},{8,9},{7,9},{7,8}
    };
    
    public static int[] getPeakSplitIndex(List<ECPeak> peaks){ 
    	int [] split = {-1,-1,-1}; int[] zero = {-1,-1,-1}; 
        for(int i = 0; i < peaks.size(); i++){
            split[0] = peaks.get(i).getSplitIndex(ECCommon.splitMethod); //index of strip used to split peak
            split[1] = peaks.get(i).getSplitStrip(); //strip used to split peak
            split[2] = i;
            if(split[0]>=0) return split; //index of peak tagged to be split
        }
        return zero;
    }
    
    public static void splitPeaks(List<ECPeak> peaks){
        while(true){ //repeat processing all peaks until no split found
        	if(ECCommon.debugSplit) System.out.println(" ");
            int[] split = getPeakSplitIndex(peaks);
        	if(ECCommon.debugSplit) System.out.println("New Iteration "+split[0]+" "+split[1]);
            if(split[2]<0){
                return; // no split was found in any peak.  Exit.
            } else {
                ECPeak  peak = peaks.get(split[2]); //retrieve tagged peak with split candidate
                peaks.remove(split[2]); //tagged peak removed from list 
                peaks.addAll(peak.splitPeak(split[0])); //two split peaks returned to list
            }
        }
    }   
    
    /**
     * This part is added to acommodate for tricky algorithm to check if the 
     * splitting makes sense. Implemented on 02/20/2023
     * @param peak
     * @param allPeaks
     * @return 
     */
    public static int[] getPeakSplitIndex(ECPeak peak, List<ECPeak> allPeaks){
        int [] split = {-1,-1,-1};
        split[0] = peak.getSplitIndex(ECCommon.splitMethod);
        split[1] = peak.getSplitStrip(); //strip used to split peak
        split[2] = 0;
        if(split[0]>=0) {
            
            int layer = peak.getDescriptor().getLayer();
            if(layer==1||layer==2||layer==3) return split;
            
            List<ECPeak>  others = ECPeakAnalysis.getMatchingPeaks(peak, allPeaks);
            if(others.size()!=2) return split;

            int splitIndexOne = others.get(0).getSplitIndex(ECCommon.splitMethod);
            int splitIndexTwo = others.get(1).getSplitIndex(ECCommon.splitMethod);
            if(splitIndexOne>=0||splitIndexTwo>=0) return split;
            
        }
        return new int[]{-1,-1,-1};
    }
    
    public static List<ECPeak>  getListForSectorLayer(List<ECPeak> peaks, int sector, int layer){
        List<ECPeak> list = new ArrayList<>();
        for(ECPeak p : peaks)
            if(p.getDescriptor().getSector()==sector&&p.getDescriptor().getLayer()==layer) list.add(p);
        return list;
    }
    
    
    public static int[] getBestCluster(ECPeak p, List<ECPeak> viewOne, List<ECPeak> viewTwo){
        double distance = 25.0; 
        int[]  indexMatch = new int[]{-1,-1};
        for(int ione = 0; ione < viewOne.size(); ione++){
            for(int itwo = 0; itwo < viewTwo.size(); itwo++){
                Line3D line = ECCluster.getClusterGeometry(p, 
                        viewOne.get(ione),viewTwo.get(itwo));
                if(line.length()<distance){
                    distance = line.length();
                    indexMatch[0] = ione; indexMatch[1] = itwo;
                }
            }
        }
        return indexMatch;
    }
    
    public static List<ECPeak>  getMatchingPeaks(ECPeak p, List<ECPeak> peaks){
        int sector = p.getDescriptor().getSector();
        int  layer = p.getDescriptor().getLayer();
        int[] layers = ECPeakAnalysis.otherLayers[layer];
        
        List<ECPeak> one = ECPeakAnalysis.getListForSectorLayer(peaks, sector, layers[0]);
        List<ECPeak> two = ECPeakAnalysis.getListForSectorLayer(peaks, sector, layers[1]);
        int[] cluster = ECPeakAnalysis.getBestCluster(p, one, two);
        if(cluster[0]>=0&&cluster[1]>=0) return Arrays.asList(one.get(cluster[0]),two.get(cluster[1]));
        return new ArrayList<>();
    }
    
    public static void splitPeaksAlternative(List<ECPeak> peaks){
        
        List<ECPeak> current = new ArrayList<>();
        current.addAll(peaks);
        
        peaks.clear();
        
        while(!current.isEmpty()){
            ECPeak peak = current.get(0); current.remove(0);
            int[] split = ECPeakAnalysis.getPeakSplitIndex(peak, current);
            if(split[0]>=0){
                List<ECPeak> result = peak.splitPeak(split[0]);
                double en1 = result.get(0).getMaxECStrip().getEnergy();
                double en2 = result.get(1).getMaxECStrip().getEnergy();
                peaks.addAll(peak.splitPeak(split[0]));
            } else {
                peaks.add(peak);
            }
        }
    }

    public static void splitPeaksAlternative2(List<ECPeak> peaks){
        
        ECPeakSplitterMargin m = new ECPeakSplitterMargin();
        
        List<ECPeak> current = new ArrayList<>();
        List<ECPeak>   whole = new ArrayList<>();
        
        current.addAll(peaks);
        whole.addAll(peaks);        
        peaks.clear();
        
        while(!current.isEmpty()){
            ECPeak peak = current.get(0); current.remove(0);
            
            List<ECPeak> splitPeaks = m.split(peak);
            if(splitPeaks.size()==2){
                
                int sector   = peak.getDescriptor().getSector();
                int  layer   = peak.getDescriptor().getLayer();
                int[] layers = ECPeakAnalysis.otherLayers[layer];
                
                List<ECPeak> one = ECPeakAnalysis.getListForSectorLayer(whole, sector, layers[0]);
                List<ECPeak> two = ECPeakAnalysis.getListForSectorLayer(whole, sector, layers[1]);
                List<ECPeak> others = ECPeakAnalysis.getMatchingPeaks(peak, whole);
                
                if(others.size()==2){
                    List<ECPeak>   oneView  = m.split(others.get(0));
                    List<ECPeak>   twoView  = m.split(others.get(1));
                    if(oneView.size()==1&&twoView.size()==1){
                        if(one.size()>1&&two.size()>1){
                            peaks.addAll(splitPeaks); 
                        } else { peaks.add(peak);}
                    } else {
                        peaks.addAll(splitPeaks);
                    }
                } else {
                    peaks.addAll(splitPeaks);
                }
                
            } else {
                peaks.add(peak);
            }
        }
    }
    
    public static void splitPeaksAlternative5(List<ECPeak> peaks){
        
        ECPeakSplitterOriginal mo = new ECPeakSplitterOriginal();
        
        List<ECPeak> current = new ArrayList<>();
        List<ECPeak>   whole = new ArrayList<>();
        
        current.addAll(peaks);
        whole.addAll(peaks);        
        peaks.clear();
        
        while(!current.isEmpty()){
            ECPeak peak = current.get(0); current.remove(0);            
            List<ECPeak> splitPeaks = mo.split(peak);
            peaks.addAll(splitPeaks);
        }
    }
    
    public static void splitPeaksAlternative4(List<ECPeak> peaks){
        
        ECPeakSplitterOriginal mo = new ECPeakSplitterOriginal();
        
        List<ECPeak> current = new ArrayList<>();
        List<ECPeak>   whole = new ArrayList<>();
        
        current.addAll(peaks);
        whole.addAll(peaks);        
        peaks.clear();
        
        while(!current.isEmpty()){
            ECPeak peak = current.get(0); current.remove(0);
            
            List<ECPeak> splitPeaks = mo.split(peak);
            
            if(splitPeaks.size()==2){
                
                int sector   = peak.getDescriptor().getSector();
                int  layer   = peak.getDescriptor().getLayer();
                int[] layers = ECPeakAnalysis.otherLayers[layer];
                
                List<ECPeak> one = ECPeakAnalysis.getListForSectorLayer(whole, sector, layers[0]);
                List<ECPeak> two = ECPeakAnalysis.getListForSectorLayer(whole, sector, layers[1]);
                
                List<ECPeak> others = ECPeakAnalysis.getMatchingPeaks(peak, whole);
                
                if(others.size()==2){
                    
                    List<ECPeak>   oneView  = mo.split(others.get(0));
                    List<ECPeak>   twoView  = mo.split(others.get(1));
                    int is_s_1 = oneView.size();
                    int is_s_2 = twoView.size();
                    
                    int n_1 = one.size();
                    int n_2 = two.size();
                    
                    double dist123 = ECCluster.getDistance(peak, others.get(0),others.get(1));
                    
                    double dist_12 = ECCluster.getDistance(splitPeaks.get(0), others.get(0),others.get(1));
                    double dist_13 = ECCluster.getDistance(splitPeaks.get(1) ,others.get(0),others.get(1));
                    double best_dist_23 = Math.min(Math.abs(dist_13), Math.abs(dist_12));
                    
                    double best_dist = Math.min(Math.abs(dist123), best_dist_23);
                    double best_ratio = best_dist/Math.abs(dist123);
                    double best_ratio_23 = best_dist_23/Math.abs(dist123);
                    
                    int should_split = 0;
                    
                    if(is_s_1==1&&is_s_2==1){
                        if(n_1==1&&n_2==1){
                           if(best_ratio<0.8)  should_split = 1;
                        } else {
                            if(best_ratio_23<1.5) should_split = 1;
                        }
                    } else {
                        if(n_1==1&&n_2==1){
                            if(best_ratio<0.9) should_split = 1;
                        } else {
                            if(best_ratio_23<1.5) should_split = 1;
                        }
                    }
                    
                    if(should_split>0){
                        peaks.addAll(splitPeaks);
                    } else {
                        peaks.add(peak);
                    }
                    
                } else {
                    peaks.addAll(splitPeaks);
                }
                
            } else {
                peaks.add(peak);
            }
        }
    }
    
    public static int  findMatchForCluster(ECCluster c, List<ECCluster> list){
        for(int i = 0; i < list.size(); i++){
            ECCluster l = list.get(i);
            if(l.getPeak(0).getMaxStrip()==c.getPeak(0).getMaxStrip()
                    &&l.getPeak(1).getMaxStrip()==c.getPeak(1).getMaxStrip()) return i;
            
            if(l.getPeak(0).getMaxStrip()==c.getPeak(0).getMaxStrip()
                    &&l.getPeak(2).getMaxStrip()==c.getPeak(2).getMaxStrip()) return i;
                
            if(l.getPeak(1).getMaxStrip()==c.getPeak(1).getMaxStrip()
                    &&l.getPeak(2).getMaxStrip()==c.getPeak(2).getMaxStrip()) return i;
        }
        return -1;
    }
    
    public static void doClusterCleanup(List<ECCluster> clusters){
        List<ECCluster> tmp = new ArrayList<>();
        tmp.addAll(clusters);
        
        clusters.clear();
        
        while(!tmp.isEmpty()){
            ECCluster c = tmp.get(0);
            tmp.remove(0);
            int index = ECPeakAnalysis.findMatchForCluster(c, tmp);
            if(index<0){
                clusters.add(c);
            } else {
                ECCluster l = tmp.get(index);
                tmp.remove(index);
                l.getClusterGeometry();
                c.getClusterGeometry();
                if(c.getClusterSize()<l.getClusterSize()){
                    clusters.add(c);
                } else {
                    clusters.add(l);
                }
            }
        }
    }
    
    public static void doPeakCleanup(List<ECPeak> peaks){
        ECPeakSplitterMargin m = new ECPeakSplitterMargin();
        for(int i = 0; i < peaks.size(); i++){
            m.split(peaks.get(i));
        }
    }
}
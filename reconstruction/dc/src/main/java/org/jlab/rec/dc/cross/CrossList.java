package org.jlab.rec.dc.cross;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.jlab.rec.dc.segment.Segment;
/**
 * List of DC crosses used to fit a track candidate
 */
public class CrossList extends ArrayList<List<Cross>> {

    /**
     * the serial version ID (automatically generated in Eclipse)
     */
    private static final long serialVersionUID = 8509791607282273163L;
    public int trackID;
    
    public void removeDuplicates(CrossList crosslist) {
        Map<String, ArrayList<Cross>> crosslistUniq = new HashMap<>();
        for (List<Cross> thisList : this) {
            for(int j = 0; j< crosslist.size(); j++) {
                List<Cross> otherList = crosslist.get(j);
                if(!(thisList.get(0).get_Id()==otherList.get(0).get_Id() &&
                        thisList.get(1).get_Id()==otherList.get(1).get_Id() &&
                        thisList.get(2).get_Id()==otherList.get(2).get_Id())) {
                    String s = "";
                    s+=thisList.get(0).get_Id()+thisList.get(1).get_Id()+thisList.get(2).get_Id();
                    crosslistUniq.put(s, (ArrayList<Cross>) thisList);
                }
            }
        }
        this.clear();
        Iterator<Map.Entry<String, ArrayList<Cross>>> itr = crosslistUniq.entrySet().iterator(); 
        while(itr.hasNext()) {
            Map.Entry<String, ArrayList<Cross>> entry = itr.next(); 
            List<Cross> addedList = entry.getValue();
            this.add(addedList); 
        }
    }

    public List<Segment> getSegments() {
        List<Segment> segments = new ArrayList<>();
        List<Cross> crosses = this.getCrosses();
        if(crosses.isEmpty()) return segments;

        for(Cross c : crosses) {
            if(c.get_Segment1().get_Id()!=-1) 
                segments.add(c.get_Segment1());
            if(c.get_Segment2().get_Id()!=-1) 
                segments.add(c.get_Segment2());
        }
        return segments;
    }
    public List<Cross> getCrosses() {
        List<Cross> crosses = new ArrayList<>();
        for (List<Cross> thisList : this) {
            crosses.addAll(thisList);
        };
        return crosses;
    }
    
}

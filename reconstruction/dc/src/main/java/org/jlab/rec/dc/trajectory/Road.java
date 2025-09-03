package org.jlab.rec.dc.trajectory;

import java.util.List;
import java.util.ArrayList;
import org.jlab.rec.dc.segment.Segment;
import org.jlab.rec.urwell.reader.URWellCross;

/**
 *
 * @author ziegler
 */
public class Road extends ArrayList<Segment>{

    public int id;
    public double[] a = new double[3]; // fit params
    
    private List<URWellCross> uRWellCrosses = new ArrayList();
    
    public void setURWellCrosses(List<URWellCross> crses){
        this.uRWellCrosses.clear();
        this.uRWellCrosses.addAll(crses);
    }
    
    public List<URWellCross> getURWellCrosses(){
        return uRWellCrosses;
    }
}

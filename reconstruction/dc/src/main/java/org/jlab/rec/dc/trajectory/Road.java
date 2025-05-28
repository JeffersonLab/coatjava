package org.jlab.rec.dc.trajectory;

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
    
    private URWellCross uRWellCross= null;
    
    public void setURWellCross(URWellCross crs){
        uRWellCross = crs;
    }
    
    public URWellCross getURWellCross(){
        return uRWellCross;
    }
}

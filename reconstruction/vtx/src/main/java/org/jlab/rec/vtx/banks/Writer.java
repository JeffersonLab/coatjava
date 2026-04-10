package org.jlab.rec.vtx.banks;

import java.util.List;
import org.jlab.io.banks.REC__VertDoca;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.rec.vtx.Vertex;

/**
 *
 * @author ziegler
 */
public class Writer {
    
    public  DataBank VtxBank(DataEvent event, List<Vertex> vtx) { 
        DataBank bank = event.createBank("REC::VertDoca", vtx.size());
       
        for (int i = 0; i < vtx.size(); i++) {
            bank.setShort(REC__VertDoca.index1, i, (short) vtx.get(i).getP1().getIndex());
            bank.setShort(REC__VertDoca.index2, i, (short) vtx.get(i).getP2().getIndex());
            bank.setFloat(REC__VertDoca.x, i, (float) vtx.get(i).getP0().getVx());
            bank.setFloat(REC__VertDoca.y, i, (float) vtx.get(i).getP0().getVy());
            bank.setFloat(REC__VertDoca.z, i, (float) vtx.get(i).getP0().getVz());
            bank.setFloat(REC__VertDoca.x1, i, (float) vtx.get(i).getP1().getVx());
            bank.setFloat(REC__VertDoca.y1, i, (float) vtx.get(i).getP1().getVy());
            bank.setFloat(REC__VertDoca.z1, i, (float) vtx.get(i).getP1().getVz());
            bank.setFloat(REC__VertDoca.cx1, i, (float) vtx.get(i).getP1().getPx());
            bank.setFloat(REC__VertDoca.cy1, i, (float) vtx.get(i).getP1().getPy());
            bank.setFloat(REC__VertDoca.cz1, i, (float) vtx.get(i).getP1().getPz());
            bank.setFloat(REC__VertDoca.x2, i, (float) vtx.get(i).getP2().getVx());
            bank.setFloat(REC__VertDoca.y2, i, (float) vtx.get(i).getP2().getVy());
            bank.setFloat(REC__VertDoca.z2, i, (float) vtx.get(i).getP2().getVz());
            bank.setFloat(REC__VertDoca.cx2, i, (float) vtx.get(i).getP2().getPx());
            bank.setFloat(REC__VertDoca.cy2, i, (float) vtx.get(i).getP2().getPy());
            bank.setFloat(REC__VertDoca.cz2, i, (float) vtx.get(i).getP2().getPz());
            bank.setFloat(REC__VertDoca.r, i, (float) vtx.get(i).getR()); 
        }
        
        return bank;
    }
}

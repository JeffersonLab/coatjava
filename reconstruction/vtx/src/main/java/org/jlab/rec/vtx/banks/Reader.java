package org.jlab.rec.vtx.banks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jlab.io.banks.REC__Particle;
import org.jlab.io.banks.REC__Track;
import org.jlab.io.banks.REC__UTrack;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.rec.vtx.Particle;
/**
 * 
 * @author ziegler
 */
public class Reader {

    public Reader() {
            // TODO Auto-generated constructor stub
    }

    /**
     * @return the _particles
     */
    public List<Particle> getParticles() {
        return _particles;
    }

    /**
     * @param _particles the _particles to set
     */
    public void setParticles(List<Particle> _particles) {
        this._particles = _particles;
    }
    private boolean updateWithUTrack = true;
    
    private List<Particle> _particles;
   
    public void readDataBanks(DataEvent event) {
        if(_particles!=null) {
            _particles.clear();
        } else {
            _particles = new ArrayList<>();
        }
        DataBank recBankEB = null;
        DataBank trkBankEB = null;
        DataBank utrkBankEB = null;
        
        if(event.hasBank("REC::Particle")) recBankEB = event.getBank("REC::Particle");
        if(event.hasBank("REC::Track")) trkBankEB = event.getBank("REC::Track");
        if(event.hasBank("REC::UTrack")) utrkBankEB = event.getBank("REC::UTrack");
        
        Map<Integer, double[]> uTrkMap = new HashMap<>();
        Map<Integer, double[]> pTrkMap = new HashMap<>();
        Map<Integer, Integer> qMap = new HashMap<>();
        
        if(utrkBankEB!=null) {
            int nrows2 = utrkBankEB.rows();
            for(int loop = 0; loop < nrows2; loop++){
                int uindex = utrkBankEB.getInt(REC__UTrack.index, loop);
                double px = utrkBankEB.getFloat(REC__UTrack.px, loop);
                double py = utrkBankEB.getFloat(REC__UTrack.py, loop);
                double pz = utrkBankEB.getFloat(REC__UTrack.pz, loop);
                double vx = utrkBankEB.getFloat(REC__UTrack.vx, loop);
                double vy = utrkBankEB.getFloat(REC__UTrack.vy, loop);
                double vz = utrkBankEB.getFloat(REC__UTrack.vz, loop);
                double[] t = new double[]{px,py,pz,vx,vy,vz};
                uTrkMap.put(uindex, t);
            }
        }
        if(recBankEB!=null) {
            int nrows = recBankEB.rows();
            for(int loop = 0; loop < nrows; loop++){
                double px = recBankEB.getFloat(REC__Particle.px, loop);
                double py = recBankEB.getFloat(REC__Particle.py, loop);
                double pz = recBankEB.getFloat(REC__Particle.pz, loop);
                double vx = recBankEB.getFloat(REC__Particle.vx, loop);
                double vy = recBankEB.getFloat(REC__Particle.vy, loop);
                double vz = recBankEB.getFloat(REC__Particle.vz, loop);
                double[] t = new double[]{px,py,pz,vx,vy,vz};
                pTrkMap.put(loop, t);
            }
        }
        if(trkBankEB!=null) {
            int nrows2 = trkBankEB.rows();
            for(int loop = 0; loop < nrows2; loop++){
                int pindex = trkBankEB.getInt(REC__Track.pindex, loop);
                int detector = trkBankEB.getInt(REC__Track.detector, loop);
                qMap.put(pindex, trkBankEB.getInt(REC__Track.q, loop));
                
                if(detector!=5) 
                    continue;
                int index = trkBankEB.getInt(REC__Track.index, loop);
                if(uTrkMap.containsKey(index) && pTrkMap.containsKey(pindex)) {
                    pTrkMap.put(pindex, uTrkMap.get(index));
                }
            }
        }
        if(recBankEB!=null && trkBankEB!=null) {
            int nrows = recBankEB.rows();
            for(int loop = 0; loop < nrows; loop++){
                int pid = recBankEB.getInt(REC__Particle.pid, loop);
                int q = 0;
                if(qMap.containsKey(loop))
                    q = qMap.get(loop);
                if(q==0) continue;
                double px = recBankEB.getFloat(REC__Particle.px, loop);
                double py = recBankEB.getFloat(REC__Particle.py, loop);
                double pz = recBankEB.getFloat(REC__Particle.pz, loop);
                double vx = recBankEB.getFloat(REC__Particle.vx, loop);
                double vy = recBankEB.getFloat(REC__Particle.vy, loop);
                double vz = recBankEB.getFloat(REC__Particle.vz, loop);
                if(this.updateWithUTrack && pTrkMap.containsKey(loop)) {
                    double[] t = pTrkMap.get(loop);
                    px = t[0];
                    py = t[1];
                    pz = t[2];
                    vx = t[3];
                    vy = t[4];
                    vz = t[5];
                } 
                 _particles.add(new Particle(loop,pid, vx, vy, vz, px, py, pz, q));
            }
        }
    }
	
} // end class

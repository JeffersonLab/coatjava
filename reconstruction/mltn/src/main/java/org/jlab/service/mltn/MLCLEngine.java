/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.jlab.service.mltn;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.jlab.clas.reco.ReconstructionEngine;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.io.hipo.HipoDataBank;
import org.jlab.io.hipo.HipoDataEvent;
import org.jlab.jnp.hipo4.data.DataType;
import org.jlab.jnp.hipo4.data.Node;

/**
 *
 * @author gavalian
 */
public class MLCLEngine extends ReconstructionEngine {
    private String   inputBank = "HitBasedTrkg::Clusters";
    private String   trackBank = "TimeBasedTrkg::TBTracks";
    private String clusterBank = "TimeBasedTrkg::TBClusters";
    
    
    public MLCLEngine(){
        super("MLCL","gavalian","1.0");
    }
    
    @Override
    public boolean processDataEvent(DataEvent de) {
        if(de.hasBank(inputBank)==true){
            DataBank bank = de.getBank(inputBank);

            int nrows = bank.rows();
            byte[] na = new byte[nrows*3];
            for(int row = 0; row < nrows; row++){
                int offset = row*3;
                int sector =  bank.getInt("sector",row);
                int   supl =  bank.getInt("superlayer",row);
                float   wire =  bank.getFloat("avgWire",row);
                na[offset] = (byte) (sector*10+supl);
                na[offset+1] = this.getUpper(wire);
                na[offset+2] = this.getLower(wire);
            }
            
            
            DataBank mltrc = de.createBank("MLTR::Clusters", na.length);
            for(int k = 0; k < na.length; k++) mltrc.setByte("byte", k, na[k]);
            
            de.appendBank(mltrc);
            if(de.hasBank(trackBank)==true&&de.hasBank(clusterBank)){
                HipoDataBank tr = (HipoDataBank) de.getBank(trackBank);
                HipoDataBank cl = (HipoDataBank) de.getBank(clusterBank);
                int rows = tr.rows();
                short[] tracks = new short[rows*6];
                String[] entries = tr.getDescriptor().getEntryList();
                
                int order = this.getEntryIndex("Cluster1_ID", entries);
                //System.out.println("******");
                //System.out.println(Arrays.toString(entries));
                //System.out.println("   >> order =  " + order);
                if(order>=0){
                    for(int r = 0; r < rows; r++){
                        int offset = r*6;
                        for(int s = 0; s < 6; s++){
                            int cid = tr.getBank().getInt(order+s, r);
                            int index = this.getIndex(cl, cid);
                            if(index<0){
                                tracks[offset+s] = -1;
                            } else {
                                double wire = cl.getFloat("avgWire", index);
                                tracks[offset+s] = (short) Math.floor(wire*100);
                            }
                        }
                    }
                    
                    DataBank mltrt = de.createBank("MLTR::Tracks", tracks.length);
                    for(int k = 0; k < tracks.length; k++) mltrt.setShort("short", k, tracks[k]);
                    de.appendBank(mltrt);
                    //Node nodetr = new Node(32100, 22, tracks);
                    //((HipoDataEvent) de).getHipoEvent().write(nodetr);
                }
            }
            
            //Node node = new Node(32100, 21, DataType.BYTE, na.length);
            //for(int k = 0; k < na.length; k++) node.setByte(k, na[k]);           
            //((HipoDataEvent) de).getHipoEvent().write(node);
        }
        return true;
    }
    
    protected int getEntryIndex(String entry, String[] list){
        for(int k = 0; k < list.length; k++) if(list[k].compareTo(entry)==0) return k;
        return -1;
    }
    private int getIndex(DataBank b, int cid){
        int rows = b.rows();
        for(int r = 0; r < rows; r++){
            int id = b.getInt("id", r);
            if(id==cid) return r;
        }
        return -1;
    }
    private byte getUpper(float f){
        return (byte) Math.floor(f);
    }
    
    private byte getLower(float f){
        int n =  (int) Math.floor((f - Math.floor(f))*100);
        return (byte) n;
    }
    
    @Override
    public boolean init() {
        System.out.println("[MLCL Engine] initialization Success....");
        return true;
    }
    
}

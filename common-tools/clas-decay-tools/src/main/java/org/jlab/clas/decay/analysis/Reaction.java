/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.jlab.clas.decay.analysis;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author ziegler
 */
public class Reaction {
    
    List<Integer> parent;
    List<Integer> dau1;
    List<Integer> dau2;
    List<Integer> dau3;
    List<Double> lowBound;
    List<Double> highBound;
    

    public void setDecays(String decays) {
        parent      = new ArrayList<>();
        dau1        = new ArrayList<>();
        dau2        = new ArrayList<>();
        dau3        = new ArrayList<>();
        lowBound    = new ArrayList<>();
        highBound   = new ArrayList<>();
        
        if(decays!=null) {
            String[] chain = decays.split(";");
            for(int i =0; i< chain.length; i++) {
                String[] decprod = chain[i].split(":");
                System.out.println("...................................................LOOKING FOR DECAY "+decprod[0]+" --> "+decprod[1]+" + "+decprod[2]);
                parent.add(Integer.parseInt(decprod[0]));
                dau1.add(Integer.parseInt(decprod[1]));
                dau2.add(Integer.parseInt(decprod[2]));
                dau3.add(Integer.parseInt(decprod[3]));
                lowBound.add(Double.parseDouble(decprod[4]));
                highBound.add(Double.parseDouble(decprod[5]));
            }
        } else {
            System.out.println("!!!!!!!!!!!!!DECAYS ARE NULL!!!!!!!!!!!!!!!!!!!");
        }
    }
    private boolean reactionsLoaded;
    public synchronized void initialize(String engine, 
            String decays) {
        if(!reactionsLoaded) {
            this.setDecays(decays);
            reactionsLoaded = true;
        }
    }
    /**
     * @return the parent
     */
    public List<Integer> getParent() {
        return parent;
    }

    /**
     * @param parent the parent to set
     */
    public void setParent(List<Integer> parent) {
        this.parent = parent;
    }

    /**
     * @return the dau1
     */
    public List<Integer> getDau1() {
        return dau1;
    }

    /**
     * @param dau1 the dau1 to set
     */
    public void setDau1(List<Integer> dau1) {
        this.dau1 = dau1;
    }

    /**
     * @return the dau2
     */
    public List<Integer> getDau2() {
        return dau2;
    }

    /**
     * @param dau2 the dau2 to set
     */
    public void setDau2(List<Integer> dau2) {
        this.dau2 = dau2;
    }

    /**
     * @return the dau3
     */
    public List<Integer> getDau3() {
        return dau3;
    }

    /**
     * @param dau3 the dau3 to set
     */
    public void setDau3(List<Integer> dau3) {
        this.dau3 = dau3;
    }

    /**
     * @return the lowBound
     */
    public List<Double> getLowBound() {
        return lowBound;
    }

    /**
     * @param lowBound the lowBound to set
     */
    public void setLowBound(List<Double> lowBound) {
        this.lowBound = lowBound;
    }

    /**
     * @return the highBound
     */
    public List<Double> getHighBound() {
        return highBound;
    }

    /**
     * @param highBound the highBound to set
     */
    public void setHighBound(List<Double> highBound) {
        this.highBound = highBound;
    }

}

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jlab.clas.physics;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author gavalian
 */
public class ParticleList {
    
    /**
     * Array containing particles.
     */
    private List<Particle>  particles = new ArrayList<Particle>();
    
    /**
     * creates an empty list of particles.
     */
    public ParticleList(){
        
    }
    
    
    /**
     * adds particle to the list.
     * @param p new Particle to add to the list.
     */
    public void add(Particle p){
        this.particles.add(p);
    }
    
    /**
     * clears the list. resulting list will have zero elements.
     */
    public void clear(){ particles.clear();}
    
    /**
     * returns number of the particles in the list.
     * @return particle count in the list.
     */
    public int count(){ return particles.size();}

    /**
     * returns number of particles of given charge
     * @param charge charge of the particle
     * @return count
     */
    public int countByCharge(int charge){
        int count = 0;
        for(Particle p : particles){
            if(p.charge()==charge) count++;
        }
        return count;
    }
    /**
     * returns number of particles with given particle id (LUND id)
     * @param pid particle id
     * @return count number of particles with given pid
     */
    public int countByPid(int pid){
        int count = 0;
        for(Particle p : particles){
            if(p.pid()==pid) count++;
        }
        return count;
    }
    /**
     * returns particle with id = pid and skips 'skip' particles
     * @param pid
     * @param skip
     * @return Particle class
     */
    public Particle getByPid(int pid, int skip){
        int skipped = 0;
        for(int i = 0; i < particles.size();i++){
            if(particles.get(i).pid()==pid){
                if(skipped==skip){
                    return particles.get(i);
                } else {
                    skipped++;
                }
            }
        }
        return null;
    }
    /**
     * returns particles by charge skipping skip particles.
     * @param charge
     * @param skip
     * @return Particle class
     */
    public Particle getByCharge(int charge, int skip){
        int skipped = 0;
        for(int i = 0; i < particles.size();i++){
            if(particles.get(i).charge()==charge){
                if(skipped==skip){
                    return particles.get(i);
                } else {
                    skipped++;
                }
            }
        }
        return null;
    }
    
    /**
     * returns LUND representation string of the list. For each particle
     * toLundString() method is called.
     * @return string representation of the event.
     */
    public String toLundString(){
         StringBuilder str = new StringBuilder();
        for(int loop = 0; loop < particles.size(); loop++){
            str.append(String.format("%5d", loop+1));
            str.append(particles.get(loop).toLundString());
            str.append("\n");
        }
        /*
        str.append(String.format("%10d %9.5f %9.5f\n",eventParticles.size(),eventBeam.vector().e(),
                eventTarget.vector().mass()));
        for(int loop = 0; loop < eventParticles.size(); loop++){
            str.append(eventParticles.get(loop).toString());
            str.append("\n");
        }*/
        return str.toString();
    }
}

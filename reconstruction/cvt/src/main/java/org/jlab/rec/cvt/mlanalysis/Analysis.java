/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.jlab.rec.cvt.mlanalysis;

/**
 *
 * @author ziegler
 */
public class Analysis {
    private int nbins;
    
    
    public int[][] truePositives  ;  //is on track, is reconstructed on-track; [0]=BST;[1]=BMT
    public int[][] falsePositives ;  //is off track, is reconstructed on-track; [0]=BST;[1]=BMT
    public int[][] trueNegatives  ;  //is off track, is reconstructed off-track; [0]=BST;[1]=BMT
    public int[][] falseNegatives ;  //is on track, is reconstructed off-track; [0]=BST;[1]=BMT
    
    public int[][] allOnTrack  ;  //truePositives+falseNegatives; [0]=BST;[1]=BMT
    public int[][] allOffTrack ;  //falsePositives+trueNegatives; [0]=BST;[1]=BMT
    
    public void init(int bins) {
        nbins = bins;
        truePositives  = new int[2][nbins];  
        falsePositives = new int[2][nbins];  
        trueNegatives  = new int[2][nbins];  
        falseNegatives = new int[2][nbins];  

        allOnTrack = new int[2][nbins];  
        allOffTrack = new int[2][nbins]; 
    }
}

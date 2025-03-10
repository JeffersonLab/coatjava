/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.jlab.rec.dc.nn;

import java.util.Objects;

/**
 *
 * @author ziegler
 */
public class TrackInfo implements Comparable<TrackInfo> {
    private final int[] ids; // To store c1-c6 values
    private final double[] tPars;
    private final double prob;
    private final int trkId ;

    public TrackInfo(int[] ids, double[] tPars) {
        // Create defensive copies of the arrays to ensure immutability
        this.ids   = ids.clone(); 
        this.tPars = tPars.clone();
        this.prob  = tPars[4]; // prob is at index 4
        this.trkId = (int)tPars[3];
    }

    public int[] getIds() {
        return ids.clone(); // Return a copy to prevent external modification
    }

    public double[] getTPars() {
        return tPars.clone(); // Return a copy to prevent external modification
    }

    public double getProb() {
        return prob;
    }

    /**
     * @return the trkId
     */
    public int getTrkId() {
        return trkId;
    }

    @Override
    public int compareTo(TrackInfo other) {
        int[] c = new int[this.ids.length];
        for (int i = 0; i < this.ids.length; i++) {
            c[i] = this.ids[i] < other.ids[i] ? -1 : this.ids[i] == other.ids[0] ? 0 : 1;
        }

        int return_val = ((c[0] == 0) ? c[1] : c[0]);
        for (int i = 1; i < this.ids.length; i++) {
            return_val = ((c[i] == 0) ? return_val : c[i]);
        }
        return return_val;
    }
}

// Pair class implementation 
 class Pair<T, U> {
    private final T first;
    private final U second;

    public Pair(T first, U second) {
        this.first = first;
        this.second = second;
    }

    public T getFirst() {
        return first;
    }

    public U getSecond() {
        return second;
    }

    @Override
    public int hashCode() {
        return Objects.hash(first, second);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Pair<?, ?> pair = (Pair<?, ?>) o;
        return Objects.equals(first, pair.first) && Objects.equals(second, pair.second);
    }
}

//Not used 
// Triplet class implementation 
class Triplet<T, U, V> {
    private final T first;
    private final U second;
    private final V third;

    public Triplet(T first, U second, V third) {
        this.first  = first;
        this.second = second;
        this.third  = third;
    }

    public T getFirst() {
        return first;
    }

    public U getSecond() {
        return second;
    }

    public V getThird() {
        return third;
    }

    @Override
    public int hashCode() {
        return Objects.hash(first, second, third);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Triplet<?, ?, ?> pair = (Triplet<?, ?, ?>) o;
        return Objects.equals(first, pair.first) 
                && Objects.equals(second, pair.second)
                && Objects.equals(third, pair.third);
    }
}
    


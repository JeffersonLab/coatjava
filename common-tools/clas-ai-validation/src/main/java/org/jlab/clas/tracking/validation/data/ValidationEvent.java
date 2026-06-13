/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt 
 * to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java 
 * to edit this template
 */
package org.jlab.clas.tracking.validation.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jlab.clas.tracking.validation.HitKey;
import org.jlab.clas.tracking.validation.TrackingObjectType;
import org.jlab.clas.tracking.validation.data.ValidationObject.ObjectKey;

/** 
 * 
 * Generic bank-backed tracking and MC information for one event. 
 * 
 * @author veronique
 */

public final class ValidationEvent {

    private final int run;
    private final int event;

    private final List<Hit> hits = new ArrayList<>();
    private final List<Cluster> clusters = new ArrayList<>();
    private final List<Segment> segments = new ArrayList<>();
    private final List<Cross> crosses = new ArrayList<>();
    private final List<Seed> seeds = new ArrayList<>();
    private final List<Track> tracks = new ArrayList<>();

    private final Map<HitKey, Hit> hitMap = new HashMap<>();
    private final Map<ObjectKey, Cluster> clusterMap = new HashMap<>();
    private final Map<ObjectKey, Segment> segmentMap = new HashMap<>();
    private final Map<ObjectKey, Cross> crossMap = new HashMap<>();
    private final Map<ObjectKey, Seed> seedMap = new HashMap<>();
    private final Map<ObjectKey, Track> trackMap = new HashMap<>();

    private final List<Particle> particles = new ArrayList<>();
    private final Map<Integer, Particle> particleMap = new HashMap<>();
    private final List<McTruthAssociation> truthAssociations = new ArrayList<>();
    private final Map<TruthHitKey, McTruthAssociation> truthAssociationMap = new HashMap<>();
    
    private final List<AiTrackSuggestion> suggestions =
        new ArrayList<>();

    private final Map<ObjectKey, AiTrackSuggestion> suggestionMap =
        new LinkedHashMap<>();

    public ValidationEvent(int run, int event) {
        this.run = run;
        this.event = event;
    }

    public int getRun() { return run; }
    public int getEvent() { return event; }

    public void addHit(Hit hit) {
        Objects.requireNonNull(hit, "hit must not be null");
        HitKey key = hit.key();
        Hit previous = hitMap.put(key, hit);
        if (previous != null) {
            hits.remove(previous);
        }
        hits.add(hit);
    }

    public void addCluster(Cluster cluster) {
        Objects.requireNonNull(cluster, "cluster must not be null");
        ObjectKey key = cluster.key();
        if (clusterMap.put(key, cluster) != null) {
            throw new IllegalArgumentException("Duplicate cluster key: " + key);
        }
        clusters.add(cluster);
    }

    public void addSegment(Segment segment) {
        Objects.requireNonNull(segment, "segment must not be null");
        ObjectKey key = segment.key();
        if (segmentMap.put(key, segment) != null) {
            throw new IllegalArgumentException("Duplicate segment key: " + key);
        }
        segments.add(segment);
    }

    public void addCross(Cross cross) {
        Objects.requireNonNull(cross, "cross must not be null");
        ObjectKey key = cross.key();
        if (crossMap.put(key, cross) != null) {
            throw new IllegalArgumentException("Duplicate cross key: " + key);
        }
        crosses.add(cross);
    }

    public void addSeed(Seed seed) {
        Objects.requireNonNull(seed, "seed must not be null");
        ObjectKey key = seed.key();
        if (seedMap.put(key, seed) != null) {
            throw new IllegalArgumentException("Duplicate seed key: " + key);
        }
        seeds.add(seed);
    }

    public void addTrack(Track track) {
        Objects.requireNonNull(track, "track must not be null");
        ObjectKey key = track.key();
        if (trackMap.put(key, track) != null) {
            throw new IllegalArgumentException("Duplicate track key: " + key);
        }
        tracks.add(track);
    }
     
    public void addSuggestion(
            AiTrackSuggestion suggestion) {

        if (suggestion == null) {
            return;
        }

        ObjectKey key = suggestion.key();

        if (suggestionMap.containsKey(key)) {
            throw new IllegalArgumentException(
                    "Duplicate AI suggestion key: " + key);
        }

        suggestions.add(suggestion);
        suggestionMap.put(key, suggestion);
    }
    
    public void addParticle(Particle particle) {
        Objects.requireNonNull(particle, "particle must not be null");
        int trackId = particle.getTrackId();
        if (particleMap.put(trackId, particle) != null) {
            throw new IllegalArgumentException("Duplicate MC particle track ID: " + trackId);
        }
        particles.add(particle);
    }

    public void addTruthAssociation(McTruthAssociation association) {
        Objects.requireNonNull(association, "association must not be null");
        TruthHitKey key = association.key();
        McTruthAssociation previous = truthAssociationMap.put(key, association);
        if (previous != null) {
            truthAssociations.remove(previous);
        }
        truthAssociations.add(association);
    }

    public Hit getHit(HitKey key) { return hitMap.get(key); }
    public Cluster getCluster(ObjectKey key) { return clusterMap.get(key); }
    public Segment getSegment(ObjectKey key) { return segmentMap.get(key); }
    public Cross getCross(ObjectKey key) { return crossMap.get(key); }
    public Seed getSeed(ObjectKey key) { return seedMap.get(key); }
    public Track getTrack(ObjectKey key) { return trackMap.get(key); }
    public Particle getParticle(int truthTrackId) { return particleMap.get(truthTrackId); }

    public McTruthAssociation getTruthAssociation(int detector, int truthHitId) {
        return truthAssociationMap.get(new TruthHitKey(detector, truthHitId));
    }

    public int getTruthTrackId(int detector, int truthHitId) {
        McTruthAssociation association = getTruthAssociation(detector, truthHitId);
        return association == null ? Hit.NO_TRUTH : association.getTruthTrackId();
    }

    public List<Hit> getHits() { 
        return Collections.unmodifiableList(hits); 
    }
    public List<Cluster> getClusters() { 
        return Collections.unmodifiableList(clusters); 
    }
    public List<Segment> getSegments() { 
        return Collections.unmodifiableList(segments); 
    }
    public List<Cross> getCrosses() { 
        return Collections.unmodifiableList(crosses); 
    }
    public List<Seed> getSeeds() { 
        return Collections.unmodifiableList(seeds); 
    }
    public List<Track> getTracks() { 
        return Collections.unmodifiableList(tracks); 
    }
    public List<Particle> getParticles() { 
        return Collections.unmodifiableList(particles); 
    }
    public List<McTruthAssociation> getTruthAssociations() {
        return Collections.unmodifiableList(truthAssociations);
    }
    
    public List<AiTrackSuggestion> getSuggestions() {
        return Collections.unmodifiableList(suggestions);
    }
    
    public List<HitKey> resolveHitKeysFromSegments(
            String algorithm,
            int detector,
            List<Integer> segmentIds) {

        List<HitKey> resolved = new ArrayList<>();
        if (segmentIds == null) {
            return resolved;
        }

        for (int segmentId : segmentIds) {
            ObjectKey key = new ObjectKey(
                    algorithm,
                    detector,
                    TrackingObjectType.SEGMENT,
                    segmentId);
            Segment segment = segmentMap.get(key);
            if (segment != null) {
                resolved.addAll(segment.getHitKeys());
            }
        }
        return new ArrayList<>(new LinkedHashSet<>(resolved));
    }

    public List<HitKey> resolveHitKeysFromClusters(
            String algorithm,
            int detector,
            List<Integer> clusterIds) {

        List<HitKey> resolved = new ArrayList<>();
        if (clusterIds == null) {
            return resolved;
        }

        for (int clusterId : clusterIds) {
            ObjectKey key = new ObjectKey(
                    algorithm,
                    detector,
                    TrackingObjectType.CLUSTER,
                    clusterId);
            Cluster cluster = clusterMap.get(key);
            if (cluster != null) {
                resolved.addAll(cluster.getHitKeys());
            }
        }
        return new ArrayList<>(new LinkedHashSet<>(resolved));
    }
    
    public void markAiSelected(
            HitKey hitKey,
            double score) {

        Hit hit = hitMap.get(hitKey);

        if (hit != null) {
            hit.markAiSelected(score);
        }
    }
   
}

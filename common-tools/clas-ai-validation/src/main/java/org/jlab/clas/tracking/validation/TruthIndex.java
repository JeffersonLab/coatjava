/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.jlab.clas.tracking.validation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jlab.clas.tracking.validation.data.Hit;

/**
 * Event-local immutable lookup of reconstructed hits and their MC truth
 * ownership.
 *
 * @author veronique
 */
public final class TruthIndex {
    /**
     * Reconstructed hits indexed by their unique detector hit key.
     */
    private final Map<HitKey, Hit> hitsByKey =
            new LinkedHashMap<>();
    
    /**
     * Unique reconstructed hit keys grouped by MC truth-track identifier.
     */
    private final Map<Integer, Set<HitKey>> hitsByTruthTrack =
            new LinkedHashMap<>();
    
    /** Key by detector surface*/
    public record SectorLayerKey(
            int detectorId,
            int sector,
            int layer) {
    }
     /**
     * Unique reconstructed hit SectorLayer keys grouped by MC truth-track identifier.
     */
    private final Map<Integer, Map<SectorLayerKey, Set<HitKey>>>
        hitsByTruthTrackSectorLayer =
        new LinkedHashMap<>();
    
    /**
     * Builds the event-local truth index.
     *
     * @param hits reconstructed hits for one event
     */
    public TruthIndex(Collection<Hit> hits) {

        if (hits == null) {
            throw new IllegalArgumentException(
                    "hits must not be null");
        }

        for (Hit hit : hits) {

            if (hit == null) {
                continue;
            }

            HitKey key = hit.key();

            if (key == null) {
                throw new IllegalArgumentException(
                        "Hit has a null HitKey: " + hit);
            }

            hitsByKey.put(key, hit);

            int truthTrackId = hit.getTruthTrackId();

            if (truthTrackId <= 0) {
                continue;
            }

            hitsByTruthTrack
                    .computeIfAbsent(
                            truthTrackId,
                            id -> new LinkedHashSet<>())
                    .add(key);

            SectorLayerKey sectorLayerKey =
                    new SectorLayerKey(
                            key.getDetectorId(),
                            key.getSector(),
                            key.getLayer());

            hitsByTruthTrackSectorLayer
                    .computeIfAbsent(
                            truthTrackId,
                            id -> new LinkedHashMap<>())
                    .computeIfAbsent(
                            sectorLayerKey,
                            id -> new LinkedHashSet<>())
                    .add(key);
        }
    }

    public Hit get(HitKey key) {
        return hitsByKey.get(key);
    }

    public Collection<Hit> getHits() {
        return Collections.unmodifiableCollection(
                hitsByKey.values());
    }

    /**
     * Returns all positive MC truth-track identifiers represented by the
     * reconstructed hits.
     *
     * @return unmodifiable set of truth-track identifiers
     */
    public Set<Integer> getTruthTrackIds() {
        return Collections.unmodifiableSet(
                hitsByTruthTrack.keySet());
    }

    /**
     * Returns the reconstructed-hit keys associated with one MC truth track.
     *
     * @param truthTrackId MC truth-track identifier
     * @return unmodifiable set of hit keys
     */
    public Set<HitKey> getTruthHits(
            int truthTrackId) {

        Set<HitKey> keys =
                hitsByTruthTrack.get(
                        truthTrackId);

        if (keys == null) {
            return Collections.emptySet();
        }

        return Collections.unmodifiableSet(keys);
    }

    /**
     * Returns the reconstructed hits associated with one MC truth track.
     *
     * @param truthTrackId MC truth-track identifier
     * @return unmodifiable list of matching hits
     */
    public List<Hit> getHitsForTruthTrack(
            int truthTrackId) {

        List<Hit> result =
                new ArrayList<>();

        for (HitKey key :
                getTruthHits(truthTrackId)) {

            Hit hit =
                    hitsByKey.get(key);

            if (hit != null) {
                result.add(hit);
            }
        }

        return Collections.unmodifiableList(result);
    }

    /**
     * @param truthTrackId
     * @param detectorId
     * @param sector
     * @param layer
     * @return truth hits by sector/laye
     */
    public Set<HitKey> getTruthHitsSectorLayer(
            int truthTrackId,
            int detectorId,
            int sector,
            int layer) {

        Map<SectorLayerKey, Set<HitKey>> bySectorLayer =
                hitsByTruthTrackSectorLayer.get(
                        truthTrackId);

        if (bySectorLayer == null) {
            return Collections.emptySet();
        }

        Set<HitKey> keys =
                bySectorLayer.get(
                        new SectorLayerKey(
                                detectorId,
                                sector,
                                layer));

        return keys == null
                ? Collections.emptySet()
                : Collections.unmodifiableSet(keys);
    }
    
    /**
     * @param truthTrackId
     * @return all sector/layer groups for one MC track
     */
    public Map<SectorLayerKey, Set<HitKey>>
            getTruthHitsBySectorLayer(
                    int truthTrackId) {

        Map<SectorLayerKey, Set<HitKey>> source =
                hitsByTruthTrackSectorLayer.get(
                        truthTrackId);

        if (source == null) {
            return Collections.emptyMap();
        }

        Map<SectorLayerKey, Set<HitKey>> result =
                new LinkedHashMap<>();

        source.forEach(
                (key, value) ->
                        result.put(
                                key,
                                Collections.unmodifiableSet(value)));

        return Collections.unmodifiableMap(result);
    }
            
    /**
     * Returns the number of unique reconstructed hits in the index.
     */
    public int size() {
        return hitsByKey.size();
    }

    /**
     * Returns true when the index contains no reconstructed hits.
     */
    public boolean isEmpty() {
        return hitsByKey.isEmpty();
    }
}
/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.jlab.clas.tracking.validation.io;

import java.util.HashMap;
import java.util.Map;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;

/**
 *
 * @author veronique
 */

/** Reads MC::True into a detector+hit-number to MC-track-id lookup. */
public final class McTruthLookup {
    private final Map<Long, Integer> truthByDetectorHit = new HashMap<>();

    public static McTruthLookup from(DataEvent event, boolean primaryOnly) {
        McTruthLookup lookup = new McTruthLookup();
        if (!event.hasBank("MC::True")) return lookup;
        DataBank bank = event.getBank("MC::True");
        for (int row = 0; row < bank.rows(); row++) {
            if (primaryOnly && bank.getInt("mtid", row) != 0) continue;
            int detectorId = bank.getByte("detector", row);
            int hitId = bank.getInt("hitn", row);
            int truthTrackId = bank.getInt("tid", row);
            if (truthTrackId > 0) lookup.truthByDetectorHit.put(key(detectorId, hitId), truthTrackId);
        }
        return lookup;
    }

    public int truthTrackId(int detectorId, int hitId) {
        Integer value = truthByDetectorHit.get(key(detectorId, hitId));
        return value == null ? -1 : value;
    }

    private static long key(int detectorId, int hitId) {
        return (((long) detectorId) << 32) ^ (hitId & 0xffffffffL);
    }
}

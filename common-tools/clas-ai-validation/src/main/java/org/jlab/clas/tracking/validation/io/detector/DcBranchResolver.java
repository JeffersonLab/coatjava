/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.jlab.clas.tracking.validation.io.detector;

import org.jlab.io.base.DataEvent;

/**
 *
 * @author veronique
 */

public final class DcBranchResolver {

    private DcBranchResolver() {
    }

    public static boolean hasAiSuggestions(DataEvent event) {
        return event != null
                && event.hasBank("ai::tracks");
    }

    public static boolean hasSeparateAiBranch(DataEvent event) {
        return event != null
                && (event.hasBank("HitBasedTrkg::AITracks")
                || event.hasBank("TimeBasedTrkg::AITracks")
                || event.hasBank("HitBasedTrkg::AIHits")
                || event.hasBank("TimeBasedTrkg::AIHits"));
    }

    /**
     * Returns true when the standard HB/TB banks belong to an AI-assisted
     * reconstruction run rather than a conventional branch.
     */
    public static boolean standardBanksAreAiAssisted(
            DataEvent event) {

        return hasAiSuggestions(event)
                && !hasSeparateAiBranch(event);
    }

    public static String aiHitBasedPrefix(
            DataEvent event) {

        if (hasSeparateAiBranch(event)) {
            return "HitBasedTrkg::AI";
        }

        if (standardBanksAreAiAssisted(event)) {
            return "HitBasedTrkg::HB";
        }

        return null;
    }

    public static String aiTimeBasedPrefix(
            DataEvent event) {

        if (hasSeparateAiBranch(event)) {
            return "TimeBasedTrkg::AI";
        }

        if (standardBanksAreAiAssisted(event)) {
            return "TimeBasedTrkg::TB";
        }

        return null;
    }
}
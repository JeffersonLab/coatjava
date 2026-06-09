package org.jlab.rec.alert.Track;

/** Specialization of a {@link TrackCandidate}. The type a track finder assigns
 *  to a candidate dictates how the candidate is fitted downstream.
 *
 *  <ul>
 *  <li>{@code AHDC_ONLY}   — AHDC hits only (MLP / Distance / Hough, and GNN
 *      tracks that include no ATOF graph nodes).</li>
 *  <li>{@code AHDC_ATOF}   — AHDC hits plus attached ATOF hits (GNN tracks whose
 *      connected component includes ATOF graph nodes).</li>
 *  <li>{@code AHDC_VERTEX} — reserved for future use (AHDC hits + a vertex
 *      constraint).</li>
 *  </ul>
 */
public enum CandidateType {
    AHDC_ONLY,
    AHDC_ATOF,
    AHDC_VERTEX
}

/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.jlab.clas.tracking.validation.io;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jlab.clas.tracking.validation.data.ValidationEvent;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;

/** 
 * Coordinates all bank readers in dependency order for one event.
 * 
 * @author veronique
 */

public final class ValidationEventReader {

    private final List<TrackingBankReader> readers = new ArrayList<>();

    public ValidationEventReader() { }

    public ValidationEventReader(List<TrackingBankReader> readers) {
        this.readers.addAll(Objects.requireNonNull(readers, "readers must not be null"));
    }

    public void addReader(TrackingBankReader reader) {
        readers.add(Objects.requireNonNull(reader, "reader must not be null"));
    }

    public ValidationEvent readEvent(DataEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        ValidationEvent output = new ValidationEvent(
                readRunNumber(event),
                readEventNumber(event));

        runPhase(event, output, Phase.TRUTH);
        runPhase(event, output, Phase.HITS);
        runPhase(event, output, Phase.CLUSTERS);
        runPhase(event, output, Phase.SEGMENTS);
        runPhase(event, output, Phase.CROSSES);
        runPhase(event, output, Phase.SEEDS);
        runPhase(event, output, Phase.TRACKS);
        return output;
    }

    private void runPhase(
            DataEvent event,
            ValidationEvent output,
            Phase phase) {

        for (TrackingBankReader bankReader : readers) {

            boolean applicable =
                    bankReader.isApplicable(event);

            if (!applicable) {
                continue;
            }

            switch (phase) {
                case TRUTH:
                    bankReader.readTruth(event, output);
                    break;

                case HITS:
                    bankReader.readHits(event, output);
                    break;

                case CLUSTERS:
                    bankReader.readClusters(event, output);
                    break;

                case SEGMENTS:
                    bankReader.readSegments(event, output);
                    break;

                case CROSSES:
                    bankReader.readCrosses(event, output);
                    break;

                case SEEDS:
                    bankReader.readSeeds(event, output);
                    break;

                case TRACKS:
                    bankReader.readTracks(event, output);
                    break;
            }
        }
    }

    private static int readRunNumber(DataEvent event) {
        if (!event.hasBank("RUN::config")) return -1;
        DataBank bank = event.getBank("RUN::config");
        return bank.rows() == 0 ? -1 : bank.getInt("run", 0);
    }

    private static int readEventNumber(DataEvent event) {
        if (!event.hasBank("RUN::config")) return -1;
        DataBank bank = event.getBank("RUN::config");
        return bank.rows() == 0 ? -1 : bank.getInt("event", 0);
    }

    private enum Phase {
        TRUTH, HITS, CLUSTERS, SEGMENTS, CROSSES, SEEDS, TRACKS
    }
}

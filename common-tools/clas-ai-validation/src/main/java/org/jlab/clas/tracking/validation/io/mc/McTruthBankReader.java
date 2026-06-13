/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

package org.jlab.clas.tracking.validation.io.mc;

import java.util.logging.Logger;
import org.jlab.clas.tracking.validation.data.McTruthAssociation;
import org.jlab.clas.tracking.validation.data.ValidationEvent;
import org.jlab.clas.tracking.validation.data.Particle;
import org.jlab.clas.tracking.validation.io.TrackingBankReader;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;

/**
 * Reads Monte Carlo particles and detector-hit truth associations.
 *
 * MC::Particle supplies generated-particle information.
 * MC::True supplies the association between simulated detector hits and
 * generated particles.
 *
 * This reader does not read reconstructed hits, clusters, crosses, seeds,
 * or tracks.
 *
 * @author veronique
 */
public final class McTruthBankReader
        implements TrackingBankReader {

    private static final Logger LOGGER =
            Logger.getLogger(McTruthBankReader.class.getName());

    private static final String PARTICLE_BANK = "MC::Particle";
    private static final String TRUE_BANK = "MC::True";

    @Override
    public boolean isApplicable(DataEvent event) {
        return event != null
                && (event.hasBank(PARTICLE_BANK)
                || event.hasBank(TRUE_BANK));
    }

    @Override
    public void readTruth(
            DataEvent event,
            ValidationEvent output) {

        if (event == null) {
            throw new IllegalArgumentException(
                    "DataEvent must not be null");
        }

        if (output == null) {
            throw new IllegalArgumentException(
                    "ValidationEvent must not be null");
        }

        if (event.hasBank(PARTICLE_BANK)) {
            readParticles(event.getBank(PARTICLE_BANK), output);
        }

        if (event.hasBank(TRUE_BANK)) {
            readTruthAssociations(
                    event.getBank(TRUE_BANK),
                    output);
        }
    }

    /**
     * Reads generated particles from MC::Particle.
     */
    private void readParticles(
            DataBank bank,
            ValidationEvent output) {
        
        for (int row = 0; row < bank.rows(); row++) {

            /*
             * The MC particle identifier used by MC::True is usually the
             * row-based track identifier. Keep it separate from the PID.
             *
             * Use row + 1 because CLAS12 truth track identifiers are commonly
             * one-based. This should be checked against the exact MC::True
             * schema used in the input files.
             */
            int trackId = row + 1;

            int pid = getInt(bank, "pid", row, 0);

            double px = getFloat(bank, "px", row, 0.0);
            double py = getFloat(bank, "py", row, 0.0);
            double pz = getFloat(bank, "pz", row, 0.0);

            double vx = getFloat(bank, "vx", row, 0.0);
            double vy = getFloat(bank, "vy", row, 0.0);
            double vz = getFloat(bank, "vz", row, 0.0);

            double time = firstFloat(
                    bank,
                    row,
                    0.0,
                    "vt",
                    "time");

            Particle particle =
                    new Particle(
                            trackId,
                            pid,
                            px,
                            py,
                            pz,
                            vx,
                            vy,
                            vz,
                            time);

            output.addParticle(particle);
        }
    }

    /**
     * Reads simulated-hit to MC-particle associations from MC::True.
     *
     * The exact MC::True schema can differ among COATJAVA versions. The
     * helper methods therefore accept several common column names.
     */
    private void readTruthAssociations(
            DataBank bank,
            ValidationEvent output) {

        for (int row = 0; row < bank.rows(); row++) {

            if (!hasColumn(bank, "detector")
                    || !hasColumn(bank, "hitn")) {
                continue;
            }

            int detector = bank.getByte("detector", row);
            int truthHitId = bank.getInt("hitn", row);

            /*
             * MC::True.otid is the one-based index of the original generated
             * particle in MC::Particle. It is therefore the appropriate stable
             * owner for detector-hit purity and efficiency studies.
             */
            int truthTrackId = hasColumn(bank, "otid")
                    ? bank.getInt("otid", row)
                    : firstInt(bank, row, -1, "tid", "trackID");

            int pid = hasColumn(bank, "pid")
                    ? bank.getInt("pid", row)
                    : 0;

            double x = firstFloat(bank, row, Double.NaN, "avgX", "x");
            double y = firstFloat(bank, row, Double.NaN, "avgY", "y");
            double z = firstFloat(bank, row, Double.NaN, "avgZ", "z");
            double time = firstFloat(bank, row, Double.NaN, "avgT", "time", "t");
            double energyDeposit = firstFloat(
                    bank, row, 0.0, "totEdep", "energy", "edep");

            if (detector < 0 || truthHitId < 0 || truthTrackId <= 0) {
                LOGGER.fine("Skipping invalid MC::True row " + row);
                continue;
            }

            output.addTruthAssociation(new McTruthAssociation(
                    detector,
                    truthHitId,
                    truthTrackId,
                    pid,
                    x,
                    y,
                    z,
                    time,
                    energyDeposit));
        }
    }

    private static boolean hasColumn(
            DataBank bank,
            String name) {

        return bank.getDescriptor() != null
                && bank.getDescriptor().hasEntry(name);
    }

    private static int getInt(
            DataBank bank,
            String name,
            int row,
            int defaultValue) {

        if (!hasColumn(bank, name)) {
            return defaultValue;
        }

        return bank.getInt(name, row);
    }

    private static double getFloat(
            DataBank bank,
            String name,
            int row,
            double defaultValue) {

        if (!hasColumn(bank, name)) {
            return defaultValue;
        }

        return bank.getFloat(name, row);
    }

    private static int firstInt(
            DataBank bank,
            int row,
            int defaultValue,
            String... names) {

        for (String name : names) {
            if (hasColumn(bank, name)) {
                return bank.getInt(name, row);
            }
        }

        return defaultValue;
    }

    private static double firstFloat(
            DataBank bank,
            int row,
            double defaultValue,
            String... names) {

        for (String name : names) {
            if (hasColumn(bank, name)) {
                return bank.getFloat(name, row);
            }
        }

        return defaultValue;
    }
}
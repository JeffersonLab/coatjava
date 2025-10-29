package org.jlab.io.utils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jlab.coda.jevio.EventWriter;
import org.jlab.coda.jevio.EvioException;
import org.jlab.coda.jevio.EvioReader;
import org.jlab.logging.DefaultLogger;

/**
 *
 * @author gavalian
 */
public class EvioCure {

    private static final Logger LOGGER = Logger.getLogger(EvioCure.class.getName());

    public static void main(String[] args) {
        DefaultLogger.debug();

        String inputFile = args[0];
        String outputFile = args[1];

        int icounter = 0;
        EventWriter evioWriter = null;
        try {
            EvioReader reader = new EvioReader(inputFile, false, false);
            LOGGER.log(Level.INFO, " READER OPENED {0}", reader.getEventCount());
            LOGGER.log(Level.INFO, " ENDIANNESS : {0}", reader.getByteOrder());
            evioWriter = new EventWriter(outputFile, false, reader.getByteOrder());
            reader.rewind();
            for (int i = 1; i < reader.getEventCount(); i++) {
                ByteBuffer buffer = reader.getEventBuffer(i);
                buffer.order(reader.getByteOrder());
                evioWriter.writeEvent(buffer);
                icounter++;
            }
            LOGGER.log(Level.INFO, " RECOVERED EVENT {0}", icounter);
            evioWriter.close();
        } catch (EvioException ex) {
            LOGGER.log(Level.WARNING, " RECOVERED EVENT (EVIO exception) " + icounter, ex);
            ex.printStackTrace();
            evioWriter.close();
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, " RECOVERED EVENT (IO Exception) " + icounter, ex);
            evioWriter.close();
        }
    }
}

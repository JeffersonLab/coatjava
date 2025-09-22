package org.jlab.io.util;

import java.util.logging.*;

public class StdOutErrLogHandler {
    public static void configureLogger(Logger logger) {
        logger.setUseParentHandlers(false);
        Handler infoHandler = new StreamHandler(System.out, new SimpleFormatter()) {
            @Override
            public synchronized void publish(LogRecord record) {
                if (record.getLevel().intValue() <= Level.INFO.intValue()) {
                    super.publish(record);
                    flush();
                }
            }
        };
        Handler errorHandler = new StreamHandler(System.err, new SimpleFormatter()) {
            @Override
            public synchronized void publish(LogRecord record) {
                if (record.getLevel().intValue() >= Level.WARNING.intValue()) {
                    super.publish(record);
                    flush();
                }
            }
        };
        logger.addHandler(infoHandler);
        logger.addHandler(errorHandler);
    }
}

package org.jlab.io.util;

import java.util.logging.*;

public class StdOutErrLogHandler {
    public static void configureLogger(Logger logger) {
        logger.setUseParentHandlers(false);
        SimpleFormatter formatter = new SimpleFormatter() {
            private static final String format = "%5$s%6$s%n";
            @Override
            public synchronized String format(LogRecord lr) {
                return String.format(format,
                        lr.getSourceClassName(),
                        lr.getSourceMethodName(),
                        lr.getLoggerName(),
                        lr.getLevel().getLocalizedName(),
                        lr.getMessage(),
                        lr.getThrown() == null ? "" : lr.getThrown());
            }
        };
        Handler infoHandler = new StreamHandler(System.out, formatter) {
            @Override
            public synchronized void publish(LogRecord record) {
                if (record.getLevel().intValue() <= Level.INFO.intValue()) {
                    super.publish(record);
                    flush();
                }
            }
        };
        Handler errorHandler = new StreamHandler(System.err, formatter) {
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

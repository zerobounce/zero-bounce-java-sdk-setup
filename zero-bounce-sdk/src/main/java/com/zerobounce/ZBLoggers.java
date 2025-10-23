package com.zerobounce;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Helper factory methods for adapting common logger implementations to {@link ZBLogger}.
 */
public final class ZBLoggers {
    private ZBLoggers() {
    }

    public static ZBLogger jul(Logger logger) {
        return new ZBLogger() {
            @Override
            public void debug(String msg) {
                if (logger.isLoggable(Level.FINE)) {
                    logger.fine(msg);
                }
            }

            @Override
            public void info(String msg) {
                logger.info(msg);
            }

            @Override
            public void warn(String msg) {
                logger.warning(msg);
            }

            @Override
            public void error(String msg, Throwable t) {
                logger.log(Level.SEVERE, msg, t);
            }
        };
    }
}

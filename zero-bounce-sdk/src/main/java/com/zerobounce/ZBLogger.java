package com.zerobounce;

/**
 * Minimal logging abstraction used by the ZeroBounce SDK.
 */
public interface ZBLogger {
    default void debug(String msg) {}
    default void info(String msg) {}
    default void warn(String msg) {}
    default void error(String msg, Throwable t) {}

    static ZBLogger noop() {
        return new ZBLogger() {
        };
    }
}

package com.zerobounce;

/**
 * A custom interface that acts as a [Runnable] but whose run method can throw exceptions.
 */
@FunctionalInterface
interface ThrowingRunnable {
    /**
     * The run method, which is permitted to throw a general checked Exception.
     */
    void run() throws Exception;
}
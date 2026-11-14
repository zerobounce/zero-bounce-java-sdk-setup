package com.zerobounce;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Finding S8 (low): a logging binding that throws an Error leaves SLF4J ONGOING for good, so
 * every call would wait out the whole SLF4J wait and then be refused. This test holds SLF4J in
 * that state by setting its initialisation flag, which the other suites never do, and restores
 * it afterwards. The first call waits and is refused without a request; later calls are refused
 * at once; once SLF4J is bound again, a call goes through.
 */
class J2TransportSlf4jWaitTest {

    private static final String KEY = "j2-slf4j-key-0123456789";

    @AfterEach
    void clearStuckState() {
        SdkHttp.loggerBindingStuck = false;
    }

    private static Field initialisationState() throws Exception {
        Field field = LoggerFactory.class.getDeclaredField("INITIALIZATION_STATE");
        field.setAccessible(true);
        return field;
    }

    private static int ongoingInitialisation() throws Exception {
        Field field = LoggerFactory.class.getDeclaredField("ONGOING_INITIALIZATION");
        field.setAccessible(true);
        return field.getInt(null);
    }

    private static SdkHttp.Result call(J1TransportServer server) throws IOException {
        return SdkHttp.exchange("GET", URI.create("http://127.0.0.1:" + server.port() + "/v2/getcredits?api_key=" + KEY),
                null, null, null, 5_000);
    }

    private static J1TransportServer.Responder ok() {
        return (request, out) -> J1TransportServer.keepAlive(out, 200, "OK", "application/json",
                J1TransportServer.CREDITS);
    }

    @Test
    void aStuckSlf4jWaitsOnceThenRefusesWithoutSendingAndRecoversWhenBound() throws Exception {
        Field state = initialisationState();
        int bound = state.getInt(null);
        state.setInt(null, ongoingInitialisation());
        try (J1TransportServer server = J1TransportServer.plain(ok(), 60_000)) {
            long start = System.nanoTime();
            IOException first = assertThrows(IOException.class, () -> call(server));
            long firstMillis = (System.nanoTime() - start) / 1_000_000;
            assertEquals(SdkHttp.KEY_LOGGING_UNCHECKED, first.getMessage());
            assertTrue(firstMillis >= 9_000, "the first call did not wait for SLF4J: " + firstMillis + " ms");

            start = System.nanoTime();
            IOException second = assertThrows(IOException.class, () -> call(server));
            long secondMillis = (System.nanoTime() - start) / 1_000_000;
            assertEquals(SdkHttp.KEY_LOGGING_UNCHECKED, second.getMessage());
            assertTrue(secondMillis < 2_000, "a later call waited again: " + secondMillis + " ms");

            assertTrue(server.requests().isEmpty(), "a call was sent while SLF4J was unchecked: " + server.requests());
        } finally {
            state.setInt(null, bound);
        }

        try (J1TransportServer server = J1TransportServer.plain(ok(), 60_000)) {
            assertEquals(200, call(server).status, "a call was refused after SLF4J was bound again");
            assertEquals(1, server.requests().size());
            assertFalse(SdkHttp.loggerBindingStuck, "the stuck state was not cleared once SLF4J was bound");
        }
    }
}

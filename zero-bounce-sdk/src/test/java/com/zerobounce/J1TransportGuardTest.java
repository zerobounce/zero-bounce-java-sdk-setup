package com.zerobounce;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The key-logging guard on the per-call client path: a call with a timeout that
 * the shared cache has no room for is sent through a client of its own, and must be
 * refused by the same guard as a call through a shared client.
 */
class J1TransportGuardTest {

    /** Strong references: java.util.logging forgets the level of a collected logger. */
    private static final Map<String, Logger> LOGGERS = new ConcurrentHashMap<>();

    /** slf4j-jdk14 maps DEBUG to FINE and TRACE to FINEST. */
    private static void level(String name, Level level) {
        LOGGERS.computeIfAbsent(name, Logger::getLogger).setLevel(level);
    }
    private static final String KEY = "j1-guard-key-0123456789";

    @AfterEach
    void restoreLevels() {
        SdkHttp.closeSharedClients();
        level("org.apache.hc", null);
        for (String name : SdkHttp.KEY_LOGGERS) {
            level(name, null);
        }
    }

    /** Fills the shared cache with eight timeouts, so a ninth call gets a client of its own. */
    private static void fillSharedCache() {
        SdkHttp.closeSharedClients();
        for (int i = 0; i < 8; i++) {
            assertTrue(SdkHttp.sharedClient(1_000 + i) != null, "shared client " + i);
        }
        assertEquals(null, SdkHttp.sharedClient(9_000), "a ninth timeout must not get a shared client");
    }

    /**
     * G6: while a key logger is at DEBUG or TRACE, a call on the per-call client is
     * refused before it is sent. With the level back at INFO, the same call goes out,
     * which shows the refusal is the guard's doing and not a broken client.
     */
    @Test
    void perCallClientRefusesWhileAKeyLoggerIsAtDebugOrTrace() throws Exception {
        for (String logger : SdkHttp.KEY_LOGGERS) {
            for (Level level : List.of(Level.FINE, Level.FINEST)) {
                fillSharedCache();
                level(logger, level);
                try (J1TransportServer server = J1TransportServer.plain(keepAliveOk(), 60_000)) {
                    IOException refused = assertThrows(IOException.class, () -> call(server));
                    assertEquals(SdkHttp.KEY_LOGGING_REFUSED, refused.getMessage(), logger + " at " + level);
                    assertTrue(server.requests().isEmpty(), logger + " at " + level + " sent " + server.requests());
                }
                level(logger, Level.INFO);
                try (J1TransportServer server = J1TransportServer.plain(keepAliveOk(), 60_000)) {
                    assertEquals(200, call(server).status, logger + " at INFO");
                    assertEquals(1, server.requests().size(), logger + " at INFO");
                }
                level(logger, null);
            }
        }
    }

    private static J1TransportServer.Responder keepAliveOk() {
        return (request, out) -> J1TransportServer.keepAlive(out, 200, "OK", "application/json",
                J1TransportServer.CREDITS);
    }

    /** One call through the ninth timeout, so the per-call client. */
    private static SdkHttp.Result call(J1TransportServer server) throws IOException {
        return SdkHttp.exchange("GET", URI.create("http://127.0.0.1:" + server.port() + "/v2/getcredits?api_key=" + KEY),
                null, null, null, 9_000);
    }
}

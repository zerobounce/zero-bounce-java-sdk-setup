package com.zerobounce;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Finding S8: the loggers whose DEBUG output carries server-sent text are guarded like the
 * key loggers. Each one is shown to echo the key at DEBUG when nothing guards it, and a
 * call is refused while it is at DEBUG or TRACE.
 */
class J1TransportServerTextGuardTest {

    /** Strong references: java.util.logging forgets the level of a collected logger. */
    private static final Map<String, Logger> LOGGERS = new ConcurrentHashMap<>();

    /** slf4j-jdk14 maps DEBUG to FINE and TRACE to FINEST. */
    private static void level(String name, Level level) {
        LOGGERS.computeIfAbsent(name, Logger::getLogger).setLevel(level);
    }

    /** Collects every record that reaches the root logger. */
    private static final class Capture extends Handler {
        final List<LogRecord> list = Collections.synchronizedList(new ArrayList<>());

        Capture() {
            setLevel(Level.ALL);
        }

        @Override
        public void publish(LogRecord record) {
            list.add(record);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }
    private static final String PARSER = "org.apache.hc.client5.http.impl.io.LenientHttpResponseParser";
    private static final String BASIC = "org.apache.hc.client5.http.impl.auth.BasicScheme";
    private static final String KEY = "j1-text-key-0123456789";

    @AfterEach
    void restoreLevels() {
        SdkHttp.closeSharedClients();
        level("org.apache.hc", null);
        for (String name : SdkHttp.SERVER_TEXT_LOGGERS) {
            level(name, null);
        }
    }

    private static URI uri(J1TransportServer server) {
        return URI.create("http://127.0.0.1:" + server.port() + "/v2/getcredits?api_key=" + KEY);
    }

    private static List<LogRecord> capture(Capture log) {
        return new ArrayList<>(log.list);
    }

    private static Capture attach() {
        Capture log = new Capture();
        Logger.getLogger("").addHandler(log);
        return log;
    }

    private static void detach(Capture log) {
        Logger.getLogger("").removeHandler(log);
    }

    /** Each server-text logger at DEBUG or TRACE refuses the call before a request is sent. */
    @Test
    void serverTextLoggersRefuseWhileAtDebugOrTrace() throws Exception {
        for (String logger : SdkHttp.SERVER_TEXT_LOGGERS) {
            for (Level level : List.of(Level.FINE, Level.FINEST)) {
                level(logger, level);
                try (J1TransportServer server = J1TransportServer.plain((request, out) ->
                        J1TransportServer.keepAlive(out, 200, "OK", "application/json", J1TransportServer.CREDITS), 5_000)) {
                    IOException refused = assertThrows(IOException.class, () ->
                            SdkHttp.exchange("GET", uri(server), null, null, null, 5_000));
                    assertEquals(SdkHttp.KEY_LOGGING_REFUSED, refused.getMessage(), logger + " at " + level);
                    assertTrue(server.requests().isEmpty(), logger + " at " + level);
                }
                level(logger, null);
            }
        }
    }

    /**
     * Proof the parser writes the echo: the garbage status line the server sends is logged
     * at DEBUG when the call goes through an unguarded client.
     */
    @Test
    void theParserLogsAServerEchoAtDebugWhenNothingGuardsIt() throws Exception {
        level(PARSER, Level.FINE);
        Capture log = attach();
        try (J1TransportServer server = J1TransportServer.plain((request, out) -> {
            out.write(("garbage echo " + KEY + "\r\n\r\n").getBytes(StandardCharsets.ISO_8859_1));
            return false;
        }, 5_000); CloseableHttpClient client = SdkHttp.newClient(5_000, false)) {
            try {
                client.execute(new HttpGet(uri(server)), response -> response.getCode());
            } catch (IOException expected) {
                // the garbage status line is a protocol error
            }
        } finally {
            detach(log);
        }
        List<String> echoes = capture(log).stream()
                .filter(e -> e.getLoggerName().equals(PARSER) && e.getMessage().contains(KEY))
                .map(LogRecord::getMessage)
                .collect(Collectors.toList());
        assertFalse(echoes.isEmpty(), "the parser wrote no echo at DEBUG");
    }

    /** The server's realm echo, through an unguarded client, is logged by BasicScheme at DEBUG. */
    @Test
    void basicSchemeLogsAServerEchoAtDebugWhenNothingGuardsIt() throws Exception {
        level(BASIC, Level.FINE);
        Capture log = attach();
        try (J1TransportServer server = J1TransportServer.plain((request, out) ->
                J1TransportServer.keepAlive(out, 401, "Unauthorized", "text/plain", new byte[0],
                        "WWW-Authenticate: Basic realm=\"" + KEY + "\""), 5_000);
             CloseableHttpClient client = SdkHttp.newClient(5_000, false)) {
            client.execute(new HttpGet(uri(server)), response -> response.getCode());
        } finally {
            detach(log);
        }
        List<String> echoes = capture(log).stream()
                .filter(e -> e.getLoggerName().equals(BASIC) && e.getMessage().contains(KEY))
                .map(LogRecord::getMessage)
                .collect(Collectors.toList());
        assertFalse(echoes.isEmpty(), "BasicScheme wrote no echo at DEBUG");
    }

    /** A Basic challenge's realm is refused by the guard when BasicScheme is at DEBUG. */
    @Test
    void basicSchemeAtDebugRefusesTheCall() throws Exception {
        level(BASIC, Level.FINE);
        try (J1TransportServer server = J1TransportServer.plain((request, out) ->
                J1TransportServer.keepAlive(out, 401, "Unauthorized", "text/plain", new byte[0],
                        "WWW-Authenticate: Basic realm=\"" + KEY + "\""), 5_000)) {
            IOException refused = assertThrows(IOException.class, () ->
                    SdkHttp.exchange("GET", uri(server), null, null, null, 5_000));
            assertEquals(SdkHttp.KEY_LOGGING_REFUSED, refused.getMessage());
            assertTrue(server.requests().isEmpty());
        }
    }
}

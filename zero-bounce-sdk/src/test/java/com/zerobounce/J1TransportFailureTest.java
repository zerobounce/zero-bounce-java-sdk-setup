package com.zerobounce;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

/**
 * Failures of the transport that must surface as a status 0 error and never as a
 * success: a connection refused, and a chunked body cut short mid-stream.
 */
class J1TransportFailureTest {

    private static final String KEY = "j1-failure-key-0123456789";

    @AfterEach
    void closeClients() {
        SdkHttp.closeSharedClients();
    }

    /** A port nothing listens on: a server that was started and closed. */
    private static int closedPort() throws IOException {
        try (J1TransportServer server = J1TransportServer.plain((request, out) -> false, 1_000)) {
            return server.port();
        }
    }

    @Test
    void aRefusedConnectionIsAnErrorThatDoesNotCarryTheKey() throws Exception {
        URI uri = URI.create("http://127.0.0.1:" + closedPort() + "/v2/getcredits?api_key=" + KEY);
        IOException error = assertThrows(IOException.class, () ->
                SdkHttp.exchange("GET", uri, null, null, null, 5_000));
        assertTrue(error.getMessage().contains("Connection refused"), error.getMessage());
        assertFalse(error.toString().contains(KEY), error.toString());
    }

    /** The SDK reports a refused connection as status 0 with no success. */
    @Test
    void theSdkReportsARefusedConnectionAsStatusZero() throws Exception {
        LoopbackServer gone = new LoopbackServer(LoopbackServer.answer(200, "application/json", "{}".getBytes(StandardCharsets.UTF_8)));
        gone.close();
        ZeroBounceSDK sdk = gone.sdk();
        sdk.initialize(KEY);
        List<ZBCreditsResponse> successes = new ArrayList<>();
        List<ErrorResponse> errors = new ArrayList<>();
        sdk.getCredits(successes::add, errors::add);
        assertTrue(successes.isEmpty(), "a refused connection reported success");
        assertEquals(1, errors.size());
        assertEquals(0, errors.get(0).getStatusCode());
        assertFalse(errors.get(0).getErrors().toString().contains(KEY), errors.get(0).getErrors().toString());
    }

    /**
     * Bodies cut short: mid-chunk, between chunks with no zero-length last chunk, and a
     * malformed chunk size. Each must fail the exchange.
     */
    @Test
    void aChunkedBodyCutShortIsATransportFailure() throws Exception {
        String[] bodies = {
                "1a\r\n{\"Credits\": \"5",
                "10\r\n{\"Credits\": \"5\"}\r\n",
                "zz\r\n{\"Credits\": \"5\"}\r\n0\r\n\r\n"};
        for (String chunks : bodies) {
            chunkedFailure(chunks);
        }
    }

    private static void chunkedFailure(String chunks) throws Exception {
        byte[] head = ("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nTransfer-Encoding: chunked\r\n\r\n")
                .getBytes(StandardCharsets.ISO_8859_1);
        byte[] body = chunks.getBytes(StandardCharsets.ISO_8859_1);
        try (J1TransportServer server = J1TransportServer.plain((request, out) -> {
            out.write(head);
            out.write(body);
            return false;
        }, 5_000)) {
            URI uri = URI.create("http://127.0.0.1:" + server.port() + "/v2/getcredits?api_key=" + KEY);
            IOException error = assertThrows(IOException.class, () ->
                    SdkHttp.exchange("GET", uri, null, null, null, 5_000), chunks);
            assertFalse(error.toString().contains(KEY), error.toString());
        }
    }

    @Test
    void theSdkReportsAChunkedBodyCutShortAsStatusZeroWithNoSuccess() throws Exception {
        byte[] head = ("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nTransfer-Encoding: chunked\r\n\r\n")
                .getBytes(StandardCharsets.ISO_8859_1);
        byte[] body = "1a\r\n{\"Credits\": \"5".getBytes(StandardCharsets.ISO_8859_1);
        try (J1TransportServer server = J1TransportServer.plain((request, out) -> {
            out.write(head);
            out.write(body);
            return false;
        }, 5_000)) {
            ZeroBounceSDK sdk = spy(server.port());
            sdk.initialize(KEY);
            List<ZBCreditsResponse> successes = new ArrayList<>();
            List<ErrorResponse> errors = new ArrayList<>();
            sdk.getCredits(successes::add, errors::add);
            assertTrue(successes.isEmpty(), "a truncated body reported success");
            assertEquals(1, errors.size());
            assertEquals(0, errors.get(0).getStatusCode());
        }
    }

    private static ZeroBounceSDK spy(int port) throws Exception {
        ZeroBounceSDK sdk = Mockito.spy(new ZeroBounceSDK());
        doAnswer(invocation -> URI.create("http://127.0.0.1:" + port + invocation.getArgument(0, String.class)
                .replaceFirst("^https?://[^/]*", "")).toURL()).when(sdk).createUrlFrom(anyString());
        return sdk;
    }
}

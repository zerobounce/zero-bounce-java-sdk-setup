package com.zerobounce;

import org.apache.hc.core5.util.TimeValue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Request and response shape on the real client: the JSON Accept header (S24), the keep-alive
 * setting (S10), and content compression, which the client does not advertise or decode (L8).
 */
class J2TransportRequestTest {

    private static final String KEY = "j2-request-key-0123456789";

    @AfterEach
    void closeClients() {
        SdkHttp.closeSharedClients();
    }

    private static URI uri(J1TransportServer server) {
        return URI.create("http://127.0.0.1:" + server.port() + "/v2/getcredits?api_key=" + KEY);
    }

    /** Every JSON call asks for JSON, on GET and on POST with a body. */
    @Test
    void jsonCallsSendAcceptApplicationJson() throws Exception {
        try (J1TransportServer server = J1TransportServer.plain((request, out) ->
                J1TransportServer.keepAlive(out, 200, "OK", "application/json", J1TransportServer.CREDITS), 60_000)) {
            assertEquals(200, SdkHttp.exchange("GET", uri(server), null, null, null, 5_000).status);
            assertEquals(200, SdkHttp.exchange("POST", uri(server), "application/json",
                    "{}".getBytes(StandardCharsets.UTF_8), null, 5_000).status);
            assertEquals(2, server.requests().size());
            for (J1TransportServer.Req request : server.requests()) {
                assertEquals("application/json", request.headers.get("accept"),
                        request.method + " sent Accept " + request.headers.get("accept"));
            }
        }
    }

    /** The idle keep-alive for a connection the server does not time out is five seconds. */
    @Test
    void theRequestConfigKeepsIdleConnectionsForFiveSeconds() {
        assertEquals(TimeValue.ofSeconds(5), SdkHttp.requestConfig(9_000).getConnectionKeepAlive());
    }

    /**
     * The client sends no Accept-Encoding and does not decode, so a gzip body the server sends
     * anyway comes back exactly as sent: a 16 MiB zero-filled body inflated to memory would be
     * the gzip-bomb case, and it does not happen.
     */
    @Test
    void noAcceptEncodingIsSentAndAGzipBodyIsKeptAsSent() throws Exception {
        byte[] plain = new byte[16 << 20];
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(buffer)) {
            gzip.write(plain);
        }
        byte[] zipped = buffer.toByteArray();
        assertTrue(zipped.length < 1 << 20, "the fixture is not compressed: " + zipped.length);

        try (J1TransportServer server = J1TransportServer.plain((request, out) ->
                J1TransportServer.keepAlive(out, 200, "OK", "application/json", zipped,
                        "Content-Encoding: gzip"), 60_000)) {
            SdkHttp.Result result = SdkHttp.exchange("GET", uri(server), null, null, null, 10_000);
            assertEquals(200, result.status);
            assertEquals(1, server.requests().size());
            assertFalse(server.requests().get(0).headers.containsKey("accept-encoding"),
                    "sent Accept-Encoding: " + server.requests().get(0).headers.get("accept-encoding"));
            assertArrayEquals(zipped, result.body, "the gzip body was decoded or changed");
        }
    }
}

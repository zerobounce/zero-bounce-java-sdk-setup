package com.zerobounce;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The real pooled client, against loopback servers that keep connections open:
 * stale-connection reuse, the idle evictor, the per-call client, the per-route
 * limit and cookies.
 */
class J1TransportPoolTest {

    private static final byte[] OK = J1TransportServer.CREDITS;

    @AfterEach
    void closeClients() {
        SdkHttp.closeSharedClients();
    }

    private static URI uri(J1TransportServer server, String path) {
        return URI.create("http://127.0.0.1:" + server.port() + path);
    }

    private static SdkHttp.Result get(J1TransportServer server, int timeoutMillis) throws IOException {
        return SdkHttp.exchange("GET", uri(server, "/v2/getcredits?api_key=pool-key"), null, null, null, timeoutMillis);
    }

    private static J1TransportServer.Responder keepAliveOk() {
        return (request, out) -> J1TransportServer.keepAlive(out, 200, "OK", "application/json", OK);
    }

    /**
     * Finding T2: the server closes an idle connection 200 ms after the last answer.
     * Calls 400 ms apart reuse the pooled connection, which is then dead, and must
     * still all succeed (automatic retries are off, so a stale reuse is a failure).
     */
    @Test
    void callsSpacedPastTheServersIdleCloseAllSucceed() throws Exception {
        try (J1TransportServer server = J1TransportServer.plain(keepAliveOk(), 200)) {
            List<String> failures = new ArrayList<>();
            for (int call = 1; call <= 5; call++) {
                Thread.sleep(400);
                try {
                    SdkHttp.Result result = get(server, 5_000);
                    if (result.status != 200) {
                        failures.add("call " + call + " status " + result.status);
                    }
                } catch (IOException e) {
                    failures.add("call " + call + " threw " + e);
                }
            }
            assertTrue(failures.isEmpty(), failures.toString());
            assertTrue(server.serverIdleClosed() >= 1, "the server never closed an idle connection");
            assertEquals(5, server.requests().size());
        }
    }

    /**
     * Finding T2 with the default timeout, through the shared client the SDK
     * methods use: the connection a finished call left idle is reused after the
     * server has closed it.
     */
    @Test
    void sharedClientReusesNoDeadConnectionAfterTheServerClosesIt() throws Exception {
        try (J1TransportServer server = J1TransportServer.plain(keepAliveOk(), 150)) {
            for (int call = 1; call <= 4; call++) {
                Thread.sleep(300);
                assertEquals(200, get(server, 120_000).status, "call " + call);
            }
            assertEquals(4, server.requests().size());
        }
    }

    /**
     * M12, M19: an idle connection in the shared pool is closed by the evictor once
     * it has been idle for MAX_IDLE (5 s), so the server sees the client close it.
     */
    @Test
    void idleConnectionInTheSharedPoolIsClosedByTheClient() throws Exception {
        try (J1TransportServer server = J1TransportServer.plain(keepAliveOk(), 60_000)) {
            assertEquals(200, get(server, 5_000).status);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
            while (server.peerClosed() == 0 && System.nanoTime() < deadline) {
                Thread.sleep(100);
            }
            assertEquals(1, server.peerClosed(), "the idle connection was never closed by the client");
        }
    }

    /**
     * M13: a client built for a ninth distinct timeout is used for one call and must be
     * closed after it, so its pooled connection is closed at once.
     */
    @Test
    void perCallClientClosesItsConnectionAfterTheCall() throws Exception {
        for (int i = 0; i < 8; i++) {
            assertTrue(SdkHttp.sharedClient(1_000 + i) != null, "shared client " + i);
        }
        try (J1TransportServer server = J1TransportServer.plain(keepAliveOk(), 60_000)) {
            assertEquals(200, get(server, 9_000).status);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (server.peerClosed() == 0 && System.nanoTime() < deadline) {
                Thread.sleep(20);
            }
            assertEquals(1, server.peerClosed(), "the per-call client left its connection open");
        }
    }

    /**
     * M15: six calls at once must all reach the server together. The server holds
     * each answer until six requests have arrived, so a per-route limit of five
     * would leave the sixth waiting and the others unanswered.
     */
    @Test
    void sixConcurrentCallsToOneHostAreAllInFlight() throws Exception {
        CountDownLatch sixArrived = new CountDownLatch(6);
        J1TransportServer.Responder barrier = (request, out) -> {
            sixArrived.countDown();
            boolean all;
            try {
                all = sixArrived.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                all = false;
            }
            return J1TransportServer.keepAlive(out, all ? 200 : 500, all ? "OK" : "Not Concurrent",
                    "application/json", OK);
        };
        try (J1TransportServer server = J1TransportServer.plain(barrier, 60_000)) {
            ExecutorService pool = Executors.newFixedThreadPool(6);
            try {
                List<Future<Integer>> results = new ArrayList<>();
                for (int i = 0; i < 6; i++) {
                    results.add(pool.submit(() -> get(server, 20_000).status));
                }
                for (Future<Integer> result : results) {
                    assertEquals(200, result.get(30, TimeUnit.SECONDS).intValue());
                }
            } finally {
                pool.shutdownNow();
            }
        }
    }

    /**
     * M16: the SDK does not keep cookies. The second call must not send the cookie the
     * first answer set.
     */
    @Test
    void cookiesFromOneAnswerAreNotSentOnTheNextCall() throws Exception {
        try (J1TransportServer server = J1TransportServer.plain((request, out) ->
                J1TransportServer.keepAlive(out, 200, "OK", "application/json", OK,
                        "Set-Cookie: session=abc123; Path=/"), 60_000)) {
            assertEquals(200, get(server, 5_000).status);
            assertEquals(200, get(server, 5_000).status);
            List<J1TransportServer.Req> requests = server.requests();
            assertEquals(2, requests.size());
            assertFalse(requests.get(1).headers.containsKey("cookie"),
                    "second call sent cookies: " + requests.get(1).headers.get("cookie"));
        }
    }
}

package com.zerobounce;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Finding T (M): the shared client must not cap concurrent calls. The total connection limit is
 * Integer.MAX_VALUE, as HttpURLConnection had none; the pool's default of 25 would hold the 26th
 * call until one of the first 25 finished.
 */
class J2TransportConcurrencyTest {

    private static final int CALLERS = 40;

    @AfterEach
    void closeClients() {
        SdkHttp.closeSharedClients();
    }

    /**
     * Forty calls on one shared client are all in flight at once: each answer waits until all
     * forty requests have arrived, or ten seconds pass (then 500).
     */
    @Test
    void fortyConcurrentCallsOnOneSharedClientAreAllInFlight() throws Exception {
        CountDownLatch arrived = new CountDownLatch(CALLERS);
        J1TransportServer.Responder barrier = (request, out) -> {
            arrived.countDown();
            boolean all;
            try {
                all = arrived.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                all = false;
            }
            return J1TransportServer.keepAlive(out, all ? 200 : 500, all ? "OK" : "Not Concurrent",
                    "application/json", J1TransportServer.CREDITS);
        };
        try (J1TransportServer server = J1TransportServer.plain(barrier, 60_000)) {
            URI uri = URI.create("http://127.0.0.1:" + server.port() + "/v2/getcredits?api_key=j2-concurrent-key");
            ExecutorService pool = Executors.newFixedThreadPool(CALLERS);
            try {
                List<Future<Integer>> results = new ArrayList<>();
                for (int i = 0; i < CALLERS; i++) {
                    results.add(pool.submit(() -> SdkHttp.exchange("GET", uri, null, null, null, 30_000).status));
                }
                for (Future<Integer> result : results) {
                    assertEquals(200, result.get(60, TimeUnit.SECONDS).intValue());
                }
                assertEquals(CALLERS, server.requests().size());
            } finally {
                pool.shutdownNow();
            }
        }
    }
}

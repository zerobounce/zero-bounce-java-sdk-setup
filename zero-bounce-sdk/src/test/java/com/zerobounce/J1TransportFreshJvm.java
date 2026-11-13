package com.zerobounce;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Run in a child JVM by {@link J1TransportFreshJvmTest}: the first sixteen calls of a
 * process, made at once, against a loopback server, with SLF4J not yet initialised.
 * Prints one line per call outcome, then {@code SUMMARY ok=<n> failed=<n> requests=<n>}.
 * Helper only, not a test class.
 */
public final class J1TransportFreshJvm {

    private J1TransportFreshJvm() {
    }

    public static void main(String[] args) throws Exception {
        int calls = 16;
        try (J1TransportServer server = J1TransportServer.plain(
                (request, out) -> J1TransportServer.keepAlive(out, 200, "OK", "application/json",
                        J1TransportServer.CREDITS), 5_000)) {
            URI uri = URI.create("http://127.0.0.1:" + server.port() + "/v2/getcredits?api_key=fresh-key");
            ExecutorService pool = Executors.newFixedThreadPool(calls);
            CountDownLatch go = new CountDownLatch(1);
            List<Future<String>> outcomes = new ArrayList<>();
            for (int i = 0; i < calls; i++) {
                outcomes.add(pool.submit(() -> {
                    go.await();
                    try {
                        return "OK " + SdkHttp.exchange("GET", uri, null, null, null, 5_000).status;
                    } catch (Exception e) {
                        return "FAILED " + e.getMessage();
                    }
                }));
            }
            Thread.sleep(500);
            go.countDown();
            int ok = 0;
            int failed = 0;
            for (Future<String> outcome : outcomes) {
                String line = outcome.get(60, TimeUnit.SECONDS);
                System.out.println("CALL " + line);
                if (line.equals("OK 200")) {
                    ok++;
                } else {
                    failed++;
                }
            }
            pool.shutdownNow();
            System.out.println("SUMMARY ok=" + ok + " failed=" + failed + " requests=" + server.requests().size());
        }
    }
}

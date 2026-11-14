package com.zerobounce;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.text.Format;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The client's API key is a transient field, so no serialiser prints it, and
 * getApiUsage formats its dates without a shared, non-thread-safe formatter:
 * concurrent calls each send the dates they were given.
 */
class J2SdkApiUsageDateTest {

    private static final Pattern START = Pattern.compile("start_date=([0-9-]+)");
    private static final Pattern END = Pattern.compile("end_date=([0-9-]+)");

    @Test
    void theApiKeyFieldIsTransient() throws Exception {
        Field key = ZeroBounceSDK.class.getDeclaredField("apiKey");
        assertTrue(Modifier.isTransient(key.getModifiers()), "apiKey is not transient, so a serialiser prints it");
    }

    @Test
    void noFieldOfTheClientIsAShareableDateFormat() {
        List<String> shared = new ArrayList<>();
        for (Field field : ZeroBounceSDK.class.getDeclaredFields()) {
            if (Format.class.isAssignableFrom(field.getType())) {
                shared.add(field.getName());
            }
        }
        assertEquals(List.of(), shared, "a shared date formatter is a field of the client");
    }

    @Test
    void concurrentGetApiUsageCallsSendTheDatesTheyWereGiven() throws Exception {
        List<String[]> sent = Collections.synchronizedList(new ArrayList<>());
        ZeroBounceSDK sdk = new ZeroBounceSDK();
        sdk.transport = request -> {
            Matcher start = START.matcher(request.url);
            Matcher end = END.matcher(request.url);
            if (start.find() && end.find()) {
                sent.add(new String[]{start.group(1), end.group(1)});
            }
            return new SdkHttp.Result(200, "application/json", "{}".getBytes(StandardCharsets.UTF_8));
        };
        sdk.initialize("usage-date-key");

        int threads = 16;
        int callsPerThread = 150;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            int thread = t;
            futures.add(pool.submit(() -> {
                go.await();
                for (int i = 0; i < callsPerThread; i++) {
                    Date start = dayOffset(thread * callsPerThread + i);
                    Date end = dayOffset(thread * callsPerThread + i + 1);
                    sdk.getApiUsage(start, end, ok -> { }, err -> { });
                }
                return null;
            }));
        }
        go.countDown();
        for (Future<?> future : futures) {
            future.get();
        }
        pool.shutdown();

        assertEquals(threads * callsPerThread, sent.size());
        List<String> wrong = new ArrayList<>();
        for (String[] pair : sent) {
            LocalDate start = LocalDate.parse(pair[0]);
            LocalDate end = LocalDate.parse(pair[1]);
            if (!end.equals(start.plusDays(1))) {
                wrong.add(pair[0] + ".." + pair[1]);
            }
        }
        assertEquals(List.of(), wrong, "a call sent an end date that is not the day after its start date");
    }

    private static Date dayOffset(int days) {
        return new Date(Instant.parse("2026-01-01T12:00:00Z").toEpochMilli() + days * 86_400_000L);
    }
}

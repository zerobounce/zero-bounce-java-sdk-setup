package com.zerobounce;

import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GsonDateDeserializer: decodes are correct under concurrency (the old shared
 * SimpleDateFormat was not thread-safe), and parsing is strict (no rolled-over
 * fields, no trailing text).
 */
class J1ParsingDateDeserializerTest {

    private static final GsonDateDeserializer DESERIALIZER = new GsonDateDeserializer();

    private static Date decode(String text) {
        return DESERIALIZER.deserialize(new JsonPrimitive(text), Date.class, null);
    }

    /** Each input with the instant it must decode to. */
    private static List<Object[]> cases() {
        List<Object[]> cases = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            LocalDateTime local = LocalDateTime.of(2001 + i % 30, 1 + i % 12, 1 + i % 28, i % 24, (i * 7) % 60, (i * 13) % 60, (i * 37 % 1000) * 1_000_000);
            ZonedDateTime zoned = local.atZone(ZoneId.systemDefault());
            cases.add(new Object[]{String.format("%04d-%02d-%02d %02d:%02d:%02d.%03d",
                    local.getYear(), local.getMonthValue(), local.getDayOfMonth(), local.getHour(),
                    local.getMinute(), local.getSecond(), local.getNano() / 1_000_000), zoned.toInstant()});
            cases.add(new Object[]{String.format("%02d/%02d/%04d %d:%02d:%02d %s",
                    local.getMonthValue(), local.getDayOfMonth(), local.getYear(),
                    local.getHour() % 12 == 0 ? 12 : local.getHour() % 12, local.getMinute(), local.getSecond(),
                    local.getHour() < 12 ? "AM" : "PM"), local.atZone(ZoneId.systemDefault()).toInstant().minusNanos(local.getNano())});
            cases.add(new Object[]{String.format("%04d-%02d-%02dT%02d:%02d:%02d.%03dZ",
                    local.getYear(), local.getMonthValue(), local.getDayOfMonth(), local.getHour(),
                    local.getMinute(), local.getSecond(), local.getNano() / 1_000_000),
                    local.atOffset(java.time.ZoneOffset.UTC).toInstant()});
            cases.add(new Object[]{String.format("%04d-%02d-%02dT%02d:%02d:%02d+01:00",
                    local.getYear(), local.getMonthValue(), local.getDayOfMonth(), local.getHour(),
                    local.getMinute(), local.getSecond()),
                    local.atOffset(java.time.ZoneOffset.ofHours(1)).toInstant().minusNanos(local.getNano())});
        }
        return cases;
    }

    @Test
    void eightThreadsDecodeEveryFormatCorrectly() throws Exception {
        List<Object[]> cases = cases();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<List<String>>> futures = new ArrayList<>();
            for (int t = 0; t < 8; t++) {
                futures.add(pool.submit((Callable<List<String>>) () -> {
                    List<String> failures = new ArrayList<>();
                    for (int round = 0; round < 400; round++) {
                        for (Object[] c : cases) {
                            String text = (String) c[0];
                            Instant expected = (Instant) c[1];
                            try {
                                Date actual = decode(text);
                                if (actual == null || !actual.toInstant().equals(expected)) {
                                    failures.add(text + " -> " + actual);
                                }
                            } catch (RuntimeException e) {
                                failures.add(text + " threw " + e);
                            }
                        }
                    }
                    return failures;
                }));
            }
            List<String> failures = new ArrayList<>();
            for (Future<List<String>> f : futures) {
                failures.addAll(f.get());
            }
            assertEquals(List.of(), failures.subList(0, Math.min(5, failures.size())), failures.size() + " wrong decodes");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void theIsoOffsetsAndFractionsAreAcceptedInEveryShape() {
        Instant expected = Instant.parse("2020-01-01T12:00:00Z");
        String[] shapes = {
                "2020-01-01T12:00:00Z",
                "2020-01-01T12:00:00.000Z",
                "2020-01-01T13:00:00+01:00",
                "2020-01-01T13:00:00+0100",
                "2020-01-01T13:00:00+01",
                "2020-01-01T13:00:00.000+01:00",
        };
        for (String shape : shapes) {
            assertEquals(expected, decode(shape).toInstant(), shape);
        }
        assertEquals(Instant.parse("2020-01-01T12:00:00.500Z"), decode("2020-01-01T12:00:00.5Z").toInstant(), "one-digit fraction");
        ZonedDateTime zoned = ZonedDateTime.of(2020, 1, 1, 12, 0, 0, 0, ZoneId.systemDefault());
        assertEquals(zoned.toInstant(), decode(zoned.format(java.time.format.DateTimeFormatter.ISO_DATE_TIME)).toInstant(),
                "zone-suffixed ISO text");
    }

    @Test
    void outOfRangeOrRolledOverDatesAreRejected() {
        String[] bad = {
                "2026-13-45 99:99:99.999",
                "2020-02-30 12:00:00.000",
                "2020-01-32 12:00:00.000",
                "2020-01-01 24:00:00.000",
                "2020-01-01 12:60:00.000",
                "2020-01-01 12:00:60.000",
                "13/01/2020 1:00:00 AM",
                "02/30/2020 1:00:00 AM",
                "01/01/2020 0:00:00 PM",
                "01/01/2020 13:00:00 PM",
                "2020-01-01T12:00:00+25:00",
                "2021-02-29T12:00:00Z",
                "2020-1-01 12:00:00.000",
                "not a date",
        };
        for (String text : bad) {
            assertThrows(JsonParseException.class, () -> decode(text), text);
        }
    }

    @Test
    void trailingOrLeadingJunkIsRejected() {
        String[] bad = {
                "2020-01-01 12:00:00.000x",
                "2020-01-01 12:00:00.000 extra",
                "2020-01-01T12:00:00Zjunk",
                "2020-01-01T12:00:00Z ",
                "x2020-01-01 12:00:00.000",
                "01/01/2020 12:00:00 PM and more",
        };
        for (String text : bad) {
            assertThrows(JsonParseException.class, () -> decode(text), "[" + text + "]");
        }
    }

    @Test
    void aBlankDateIsNullAsBefore() {
        assertNull(decode(""));
        assertNull(decode("   "));
    }

    @Test
    void aBadDateIsAnErrorNotAWrongDate() {
        // The message names the text, so a caller can see which date was refused.
        JsonParseException e = assertThrows(JsonParseException.class, () -> decode("2026-13-45 99:99:99.999"));
        assertTrue(e.getMessage().contains("2026-13-45"), e.getMessage());
    }
}

package com.zerobounce;

import com.google.gson.*;
import org.junit.Test;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class GsonDateDeserializerTest {

    /**
     * Test date deserialization with no timezone dates
     */
    @Test
    public void dateFormatDeserializationNoTimeZone() {
        Date verificationDate = Date.from(LocalDateTime.of(2020, 1, 1, 12, 0, 0, 0).atZone(ZoneId.systemDefault()).toInstant());
        final Gson gson = new GsonBuilder().registerTypeAdapter(Date.class, new GsonDateDeserializer()).create();
        try {
            assertEquals(verificationDate, gson.fromJson("\"2020-01-01 12:00:00.000\"", Date.class));
            assertEquals(verificationDate, gson.fromJson("\"01/01/2020 12:00:00 PM\"", Date.class));
        } catch (JsonParseException e) {
            fail("Failed to deserialize date");
        }
    }

    /**
     * Test date deserialization with ISO 8601 format and UTC (Zulu) timezone.
     */
    @Test
    public void dateFormatDeserializationZulu() {
        Date verificationDateZulu = Date.from(ZonedDateTime.of(2020, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC).toInstant());
        final Gson gson = new GsonBuilder().registerTypeAdapter(Date.class, new GsonDateDeserializer()).create();
        try {
            assertEquals(verificationDateZulu, gson.fromJson("\"2020-01-01T12:00:00.000Z\"", Date.class));
            assertEquals(verificationDateZulu, gson.fromJson("\"2020-01-01T12:00:00Z\"", Date.class));
        } catch (JsonParseException e) {
            fail("Failed to deserialize date");
        }
    }

    /**
     * Test date deserialization with ISO 8601 format local timezone.
     */
    @Test
    public void dateFormatDeserializationLocalTimezone() {
        ZonedDateTime zonedDateTime = ZonedDateTime.of(2020, 1, 1, 12, 0, 0, 0, ZoneId.systemDefault());
        Date verificationDate = Date.from(zonedDateTime.toInstant());

        final Gson gson = new GsonBuilder().registerTypeAdapter(Date.class, new GsonDateDeserializer()).create();
        try {
            assertEquals(verificationDate, gson.fromJson("\"" + zonedDateTime.format(DateTimeFormatter.ISO_DATE_TIME) + "\"", Date.class));
        } catch (JsonParseException e) {
            fail("Failed to deserialize date");
        }
    }
}
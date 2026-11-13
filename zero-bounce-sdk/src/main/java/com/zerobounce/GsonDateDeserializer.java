package com.zerobounce;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;

import java.lang.reflect.Type;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * A custom deserializer for transforming date Strings into Date objects.
 *
 * <p>Parsing uses {@link java.time} formatters, which are immutable and safe to
 * share between threads (a shared {@code SimpleDateFormat} is not). Each format
 * is strict: the whole string must match, and a field out of range (month 13,
 * day 30 of February, hour 99) is an error rather than a rolled-over date.
 */
public class GsonDateDeserializer implements JsonDeserializer<Date> {

    /** How a parsed value becomes an instant. */
    private enum Kind {
        /** No zone in the text: the system default zone applies, as before. */
        SYSTEM_ZONE,
        /** An offset (Z, +hh:mm, +hhmm or +hh) is in the text. */
        OFFSET
    }

    private static final class Format {
        final DateTimeFormatter formatter;
        final Kind kind;

        Format(DateTimeFormatter formatter, Kind kind) {
            this.formatter = formatter;
            this.kind = kind;
        }
    }

    private static final List<Format> FORMATS = List.of(
            new Format(strict("uuuu-MM-dd HH:mm:ss.SSS"), Kind.SYSTEM_ZONE),
            new Format(strict("MM/dd/uuuu h:mm:ss a"), Kind.SYSTEM_ZONE),
            // ISO 8601 (standard json), with or without fractional seconds, and
            // the offset written as Z, +hh:mm, +hhmm or +hh. An optional
            // [Region/Id] suffix is accepted, as ZonedDateTime.toString writes it.
            new Format(isoFormat("+HH:MM"), Kind.OFFSET),
            new Format(isoFormat("+HHMM"), Kind.OFFSET),
            new Format(isoFormat("+HH"), Kind.OFFSET));

    private static DateTimeFormatter strict(String pattern) {
        return new DateTimeFormatterBuilder()
                .appendPattern(pattern)
                .toFormatter(Locale.ROOT)
                .withResolverStyle(ResolverStyle.STRICT);
    }

    private static DateTimeFormatter isoFormat(String offsetPattern) {
        return new DateTimeFormatterBuilder()
                .appendPattern("uuuu-MM-dd'T'HH:mm:ss")
                .optionalStart()
                .appendFraction(ChronoField.NANO_OF_SECOND, 1, 9, true)
                .optionalEnd()
                .appendOffset(offsetPattern, "Z")
                .optionalStart()
                .appendLiteral('[')
                .parseCaseSensitive()
                .appendZoneRegionId()
                .appendLiteral(']')
                .optionalEnd()
                .toFormatter(Locale.ROOT)
                .withResolverStyle(ResolverStyle.STRICT);
    }

    @Override
    public Date deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        try {
            final String dateString = json.getAsJsonPrimitive().getAsString();
            return parseDate(dateString);
        } catch (DateTimeParseException e) {
            throw new JsonParseException(e.getMessage(), e);
        }
    }

    /**
     * Tries to parse the given [dateString] into a [Date] object.
     *
     * @param dateString a date string
     * @return a [Date] object or null
     */
    private Date parseDate(String dateString) throws DateTimeParseException {
        if (dateString == null || dateString.trim().isEmpty()) {
            return null;
        }

        for (Format format : FORMATS) {
            try {
                TemporalAccessor parsed = format.formatter.parse(dateString);
                Instant instant = format.kind == Kind.SYSTEM_ZONE
                        ? LocalDateTime.from(parsed).atZone(ZoneId.systemDefault()).toInstant()
                        : OffsetDateTime.from(parsed).toInstant();
                return Date.from(instant);
            } catch (DateTimeException pe) {
                // try next format
            }
        }
        throw new DateTimeParseException("Unparseable date: \"" + dateString + "\"", dateString, 0);
    }
}

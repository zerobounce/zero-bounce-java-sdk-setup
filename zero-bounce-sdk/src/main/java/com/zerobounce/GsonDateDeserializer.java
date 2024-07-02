package com.zerobounce;

import com.google.gson.*;

import java.lang.reflect.Type;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * A custom deserializer for transforming date Strings into Date objects.
 */
public class GsonDateDeserializer implements JsonDeserializer<Date> {

    private static final List<SimpleDateFormat> DATE_FORMATS = Arrays.asList(
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT),
            new SimpleDateFormat("MM/dd/yyyy h:mm:ss a", Locale.ROOT),
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX", Locale.ROOT), // ISO 8601 support (standard json)
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX", Locale.ROOT)); // ISO 8601 support with milliseconds


    @Override
    public Date deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        try {
            final String dateString = json.getAsJsonPrimitive().getAsString();
            return parseDate(dateString);
        } catch (ParseException e) {
            throw new JsonParseException(e.getMessage(), e);
        }
    }

    /**
     * Tries to parse the given [dateString] into a [Date] object.
     *
     * @param dateString a date string
     * @return a [Date] object or null
     */
    private Date parseDate(String dateString) throws ParseException {
        if (dateString == null || dateString.trim().isEmpty()) {
            return null;
        }

        for (SimpleDateFormat format : DATE_FORMATS) {
            try {
                return format.parse(dateString);
            } catch (ParseException pe) {
                // try next format
            }
        }
        throw new ParseException("Unparseable date: \"" + dateString + "\"", 0);
    }
}

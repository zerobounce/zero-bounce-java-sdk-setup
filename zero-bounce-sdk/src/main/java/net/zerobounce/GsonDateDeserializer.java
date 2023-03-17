package net.zerobounce;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;

import java.lang.reflect.Type;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * A custom deserializer for transforming date Strings into Date objects.
 */
public class GsonDateDeserializer implements JsonDeserializer<Date> {

    private final SimpleDateFormat format1 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT);
    private final SimpleDateFormat format2 = new SimpleDateFormat("MM/dd/yyyy h:mm:ss a", Locale.ROOT);

    @Override
    public Date deserialize(
            JsonElement json,
            Type typeOfT,
            JsonDeserializationContext context
    ) throws JsonParseException {
        try {
            String dateString = json.getAsJsonPrimitive().getAsString();
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
        if (dateString != null && dateString.trim().length() > 0) {
            try {
                return format1.parse(dateString);
            } catch (ParseException pe) {
                return format2.parse(dateString);
            }
        } else {
            return null;
        }
    }
}

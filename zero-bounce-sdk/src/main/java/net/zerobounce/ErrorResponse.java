package net.zerobounce;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The model used for when a request throws an error. This model was introduced in order to provide
 * a standardized way of handling the error responses that a request can return.
 * <p>
 * If the error JSON received from the server includes the words "error" or "message", then the
 * values of those keys will be added to the [errors] array. If the error is not a JSON dictionary,
 * then JSON String will be added to the [errors] array.
 * <p>
 * If any type of messages are received, then they will be added according to the same rule above
 * after the errors found above.
 */
public class ErrorResponse {

    @Nullable
    private Boolean success = null;

    @NotNull
    private ArrayList<String> errors = new ArrayList<>();

    public @Nullable Boolean getSuccess() {
        return success;
    }

    public void setSuccess(@Nullable Boolean success) {
        this.success = success;
    }

    public @NotNull ArrayList<String> getErrors() {
        return errors;
    }

    public void setErrors(@NotNull ArrayList<String> errors) {
        this.errors = errors;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ErrorResponse that = (ErrorResponse) o;
        return Objects.equals(success, that.success) && Objects.equals(errors, that.errors);
    }

    @Override
    public int hashCode() {
        return Objects.hash(success, errors);
    }

    /**
     * Tries to parse the given [error] String into a LinkedHashMap (i.e.: a dictionary structure) in order to provide
     * a standardized way of handling the various error responses.
     * <p>
     * If the [error] cannot be parsed, then the error will be added as is in the [errors] array.
     *
     * @param error the error that will be parsed
     * @return an [ErrorResponse] object
     */
    protected static ErrorResponse parseError(@Nullable String error) {
        ErrorResponse response = new ErrorResponse();
        if (error == null) return response;

        ArrayList<String> errors = new ArrayList<>();
        ArrayList<String> otherMessages = new ArrayList<>();

        Type typeToken = new TypeToken<LinkedHashMap<String, Object>>() {}.getType();

        try {
            LinkedHashMap<String, Object> hashMap = new Gson().fromJson(error, typeToken);
            for (Map.Entry<String, Object> entry : hashMap.entrySet()) {
                if (entry.getKey().contains("error") || entry.getKey().contains("message")) {
                    if (entry.getValue() instanceof ArrayList) {
                        ArrayList<String> values = (ArrayList<String>) entry.getValue();
                        errors.addAll(values);
                    } else if (entry.getValue() != null) {
                        errors.add(entry.getValue().toString());
                    }
                } else {
                    if (entry.getKey().equals("success") && entry.getValue() instanceof Boolean) {
                        response.success = (Boolean) entry.getValue();
                    } else if (entry.getValue() instanceof ArrayList) {
                        ArrayList<String> values = (ArrayList<String>) entry.getValue();
                        otherMessages.addAll(values);
                    } else if (entry.getValue() != null) {
                        otherMessages.add(entry.getValue().toString());
                    }
                }

            }
            errors.addAll(otherMessages);
        } catch (Throwable t) {
            // If the error couldn't be parsed as a JSON, then we'll show the actual error we received
            errors.add(error);
        }

        response.errors = errors;
        return response;
    }
}

package com.zerobounce;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Turns HTTP failures, HTML pages, and empty or non-JSON bodies into a short message.
 */
final class HttpMessages {

    private HttpMessages() {
    }

    static boolean looksLikeHtml(String body, String contentType) {
        if (contentType != null && contentType.toLowerCase().contains("text/html")) {
            return true;
        }
        if (body == null) {
            return false;
        }
        String sample = body.substring(0, Math.min(body.length(), 2000)).toLowerCase();
        return sample.contains("<!doctype html")
                || sample.contains("<html")
                || sample.contains("<head")
                || sample.contains("<body");
    }

    /**
     * @return a short error for a JSON API call, or null when the body is usable JSON
     */
    static String jsonCallFailure(int status, String body, String contentType) {
        String trimmed = body == null ? "" : body.trim();
        if (trimmed.isEmpty()) {
            return "Empty response from ZeroBounce (HTTP " + status + ")";
        }
        if (looksLikeHtml(body, contentType)) {
            return htmlMessage(body);
        }
        if (status > 299) {
            return messageFromBody(trimmed, status);
        }
        if (trimmed.charAt(0) != '{' && trimmed.charAt(0) != '[') {
            return "ZeroBounce returned a response that is not valid JSON.";
        }
        try {
            JsonParser.parseString(trimmed);
            return null;
        } catch (RuntimeException ignored) {
            return "ZeroBounce returned a response that is not valid JSON.";
        }
    }

    /**
     * @return a short error for a file download, or null when the bytes should be saved
     */
    static String downloadFailure(int status, byte[] body, String contentType) {
        String text = body == null ? "" : new String(body, java.nio.charset.StandardCharsets.UTF_8);
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return "Empty response from ZeroBounce (HTTP " + status + ")";
        }
        if (looksLikeHtml(text, contentType)) {
            return htmlMessage(text);
        }
        if (status > 299) {
            return messageFromBody(trimmed, status);
        }
        return null;
    }

    /**
     * Account-level batch error text, or null when the payload is a normal or partial result.
     */
    static String batchAccountError(String body) {
        try {
            JsonElement parsed = JsonParser.parseString(body);
            if (!parsed.isJsonObject()) {
                return null;
            }
            JsonObject object = parsed.getAsJsonObject();
            if (!object.has("errors") || !object.get("errors").isJsonArray()) {
                return null;
            }
            for (JsonElement item : object.getAsJsonArray("errors")) {
                if (!item.isJsonObject()) {
                    continue;
                }
                JsonObject error = item.getAsJsonObject();
                String address = text(error.get("email_address"));
                if ("all".equalsIgnoreCase(address)) {
                    String message = text(error.get("error"));
                    return message == null || message.isBlank()
                            ? "Invalid API Key or your account ran out of credits"
                            : stripPrefix(message);
                }
            }
        } catch (RuntimeException ignored) {
            return null;
        }
        return null;
    }

    /**
     * @return a short message when a 2xx JSON object is an API error, or null to decode it
     */
    static String successPayloadError(String body) {
        try {
            JsonElement parsed = JsonParser.parseString(body);
            if (!parsed.isJsonObject()) {
                return null;
            }
            JsonObject object = parsed.getAsJsonObject();
            if (object.has("email_batch")) {
                return batchAccountError(body);
            }
            if (object.has("success") && object.get("success").isJsonPrimitive()
                    && !object.get("success").getAsBoolean()) {
                return firstText(object, "message", "error", "error_message");
            }
            if (object.has("error") && !object.get("error").isJsonNull()) {
                String message = text(object.get("error"));
                if (message != null && !message.isBlank()) {
                    return stripPrefix(message);
                }
            }
            if (object.has("error_message")) {
                String message = text(object.get("error_message"));
                if (message != null && !message.isBlank()) {
                    return stripPrefix(message);
                }
            }
        } catch (RuntimeException ignored) {
            return null;
        }
        return null;
    }

    private static String firstText(JsonObject object, String... keys) {
        for (String key : keys) {
            String message = text(object.get(key));
            if (message != null && !message.isBlank()) {
                return stripPrefix(message);
            }
        }
        return "Request not processed successfully.";
    }

    private static String text(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (element.isJsonPrimitive()) {
            return element.getAsString();
        }
        if (element.isJsonArray() && !element.getAsJsonArray().isEmpty()) {
            JsonElement first = element.getAsJsonArray().get(0);
            if (first.isJsonPrimitive()) {
                return first.getAsString();
            }
        }
        return element.toString();
    }

    private static String htmlMessage(String body) {
        String title = htmlTitle(body);
        if (title.isEmpty()) {
            return "ZeroBounce returned an HTML error page instead of an API response.";
        }
        return "ZeroBounce returned an HTML error page (\"" + title + "\") instead of an API response.";
    }

    private static String htmlTitle(String body) {
        String lower = body.toLowerCase();
        int start = lower.indexOf("<title");
        if (start < 0) {
            return "";
        }
        int openEnd = lower.indexOf('>', start);
        if (openEnd < 0) {
            return "";
        }
        int close = lower.indexOf("</title>", openEnd);
        if (close < 0) {
            return "";
        }
        return body.substring(openEnd + 1, close).trim();
    }

    private static String messageFromBody(String trimmed, int status) {
        if (trimmed.charAt(0) == '{' || trimmed.charAt(0) == '[') {
            try {
                JsonElement parsed = JsonParser.parseString(trimmed);
                if (parsed.isJsonObject()) {
                    JsonObject object = parsed.getAsJsonObject();
                    if (object.has("title") && object.get("title").isJsonPrimitive()) {
                        String title = object.get("title").getAsString();
                        boolean accessDenied = title.contains("Access denied") || title.contains("1020");
                        boolean cloudflare = object.has("cloudflare_error")
                                && object.get("cloudflare_error").isJsonPrimitive()
                                && object.get("cloudflare_error").getAsBoolean();
                        if (accessDenied && (cloudflare || !hasErrorOrMessageKey(object))) {
                            return title;
                        }
                    }
                    String apiError = successPayloadError(trimmed);
                    if (apiError != null) {
                        return apiError;
                    }
                }
            } catch (RuntimeException ignored) {
                return "ZeroBounce returned a response that is not valid JSON.";
            }
            if (trimmed.length() > 1000) {
                return "ZeroBounce returned a response that is not valid JSON.";
            }
            return stripPrefix(trimmed);
        }
        if (trimmed.length() > 1000) {
            return "ZeroBounce returned a response that is not valid JSON.";
        }
        String message = stripPrefix(trimmed);
        if (message.isEmpty()) {
            return "Empty response from ZeroBounce (HTTP " + status + ")";
        }
        return message;
    }

    private static boolean hasErrorOrMessageKey(JsonObject object) {
        for (String key : object.keySet()) {
            if (key.contains("error") || key.contains("message")) {
                return true;
            }
        }
        return false;
    }

    static boolean preferShortMessage(String body) {
        if (body == null) {
            return true;
        }
        String trimmed = body.trim();
        if (trimmed.isEmpty() || looksLikeHtml(trimmed, null) || trimmed.length() > 1000) {
            return true;
        }
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            return true;
        }
        try {
            JsonElement parsed = JsonParser.parseString(trimmed);
            if (!parsed.isJsonObject()) {
                return false;
            }
            JsonObject object = parsed.getAsJsonObject();
            if (object.has("cloudflare_error") && object.get("cloudflare_error").isJsonPrimitive()
                    && object.get("cloudflare_error").getAsBoolean()) {
                return true;
            }
            if (object.has("title") && object.get("title").isJsonPrimitive()
                    && object.get("title").getAsString().contains("1020")) {
                return true;
            }
            return false;
        } catch (RuntimeException ignored) {
            return true;
        }
    }

    static String stripPrefix(String message) {
        String current = message == null ? "" : message.trim();
        while (current.startsWith("Error:") || current.startsWith("[Error]:")) {
            current = current.substring(current.startsWith("[Error]:") ? 8 : 6).trim();
        }
        return current;
    }
}

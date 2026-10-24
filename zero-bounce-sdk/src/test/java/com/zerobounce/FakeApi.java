package com.zerobounce;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * In-process ZeroBounce API. Records each call and never opens a socket.
 */
final class FakeApi implements SdkHttp.Transport {

    static final class RecordedRequest {
        final String method;
        final String host;
        final String path;
        final String apiKey;
        final String contentType;
        final String url;
        final String rawBody;
        final Map<String, String> fields;
        final boolean followRedirects;

        RecordedRequest(
                String method,
                String host,
                String path,
                String apiKey,
                String contentType,
                String url,
                String rawBody,
                Map<String, String> fields,
                boolean followRedirects
        ) {
            this.method = method;
            this.host = host;
            this.path = path;
            this.apiKey = apiKey;
            this.contentType = contentType;
            this.url = url;
            this.rawBody = rawBody;
            this.fields = fields;
            this.followRedirects = followRedirects;
        }

        String field(String name) {
            return fields.get(name);
        }
    }

    private final List<RecordedRequest> records = Collections.synchronizedList(new ArrayList<>());

    List<RecordedRequest> records() {
        synchronized (records) {
            return List.copyOf(records);
        }
    }

    void clear() {
        records.clear();
    }

    @Override
    public SdkHttp.Result exchange(SdkHttp call) throws IOException {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextInt(5, 20));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
        RecordedRequest recorded = record(call);
        records.add(recorded);
        String key = recorded.apiKey == null ? "" : recorded.apiKey;
        if (key.startsWith("html403")) {
            return html(403, key);
        }
        if (key.startsWith("html200")) {
            return html(200, key);
        }
        if (key.startsWith("empty")) {
            return new SdkHttp.Result(200, "application/json", new byte[0]);
        }
        if (key.startsWith("malformed")) {
            return text(200, "application/json", "not-json{{{");
        }
        if (key.startsWith("errprefix") || key.startsWith("double-error-")) {
            return text(400, "text/plain", "Error: Error: boom");
        }
        if (key.startsWith("cf-json-") || key.startsWith("cfjson")) {
            return text(403, "application/json", """
                    {"type":"https://developers.cloudflare.com/support/troubleshooting/http-status-codes/cloudflare-1xxx-errors/error-1020/","title":"Error 1020: Access denied","status":403,"detail":"The request was blocked by a Cloudflare firewall rule configured by the site owner.","error_code":1020,"cloudflare_error":true,"what_you_should_do":"Do not retry. This block is intentional."}
                    """);
        }
        if (key.startsWith("batchall") || (key.startsWith("badkey") && recorded.path.contains("validatebatch"))) {
            return json(batchAll());
        }
        if (key.startsWith("batchitem")) {
            return json(batchItem());
        }
        if (key.startsWith("badkey")) {
            return json("{\"error\":\"Invalid API Key or your account ran out of credits\"}");
        }
        if (key.startsWith("nocredits")) {
            return json("{\"error\":\"Your account ran out of credits\"}");
        }
        return success(recorded);
    }

    private SdkHttp.Result success(RecordedRequest recorded) {
        String path = recorded.path;
        if (path.endsWith("/getfile")) {
            String csv = "email,status\nok@example.com,valid\n";
            return text(200, "text/csv", csv);
        }
        if (path.endsWith("/listS3files")) {
            return json("[{\"file_name\":\"emails.csv\",\"size\":12}]");
        }
        if (path.contains("/filters/")) {
            return json("[]");
        }
        if (path.endsWith("/getcredits")) {
            return json("{\"Credits\":" + quote(recorded.apiKey) + "}");
        }
        if (path.endsWith("/scoring")) {
            return json("{\"email\":\"person@example.com\",\"score\":9}");
        }
        if (path.endsWith("/validatebatch")) {
            return json("""
                    {"email_batch":[{"address":"ok@example.com","status":"valid"}],"errors":[]}
                    """);
        }
        if (path.endsWith("/validate")) {
            return json("{\"address\":\"person@example.com\",\"status\":\"valid\"}");
        }
        if (path.contains("/sendfile") || path.contains("/deletefile") || path.contains("S3file")
                || path.contains("listevaluator") || path.contains("send-evaluated-file")) {
            return json("{\"success\":true,\"message\":\"ok\",\"file_id\":\"fid\",\"file_name\":\"f.csv\"}");
        }
        if (path.endsWith("/filestatus")) {
            return json("{\"success\":true,\"file_id\":\"fid\",\"file_status\":\"Complete\",\"complete_percentage\":\"100\"}");
        }
        return json("{\"success\":true}");
    }

    private static String batchAll() {
        return """
                {"email_batch":[],"errors":[{"email_address":"all","error":"Invalid API Key or your account ran out of credits"}]}
                """;
    }

    private static String batchItem() {
        return """
                {"email_batch":[{"address":"ok@example.com","status":"valid"},{"address":"bad@example.com","status":"invalid"}],"errors":[{"email_address":"bad@example.com","error":"mailbox_not_found"}]}
                """;
    }

    private static SdkHttp.Result json(String body) {
        return text(200, "application/json", body);
    }

    private static SdkHttp.Result text(int status, String contentType, String body) {
        return new SdkHttp.Result(status, contentType, body.getBytes(StandardCharsets.UTF_8));
    }

    private static SdkHttp.Result html(int status, String apiKey) {
        return new SdkHttp.Result(status, "text/html; charset=UTF-8", accessDeniedPage(apiKey));
    }

    private static byte[] accessDeniedPage(String apiKey) {
        StringBuilder page = new StringBuilder(1_500_000);
        page.append("<!DOCTYPE html><html lang=\"en-US\"><head><title>Access denied | zerobounce.net | Zero Bounce</title></head><body>");
        page.append("<!--").append(apiKey).append("-->");
        String block = "<p>blocked</p>";
        while (page.length() < 1_400_000) {
            page.append(block);
        }
        page.append("</body></html>");
        return page.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String quote(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static RecordedRequest record(SdkHttp call) {
        ParsedUrl parsed = parseUrl(call.url);
        Map<String, String> fields = new LinkedHashMap<>(parsed.query);
        String raw = new String(call.body, StandardCharsets.UTF_8);
        if (call.fields != null && !call.fields.isEmpty()) {
            fields.putAll(call.fields);
        } else if (!raw.isBlank() && raw.trim().charAt(0) == '{') {
            flattenJson(raw.trim(), fields);
        } else if (raw.contains("=")) {
            parseQuery(raw, fields);
        }
        String apiKey = fields.get("api_key");
        return new RecordedRequest(
                call.method,
                parsed.host,
                parsed.path,
                apiKey,
                call.contentType,
                call.url,
                raw,
                Collections.unmodifiableMap(fields),
                call.followRedirects
        );
    }

    private static void flattenJson(String raw, Map<String, String> fields) {
        com.google.gson.JsonObject object = com.google.gson.JsonParser.parseString(raw).getAsJsonObject();
        for (Map.Entry<String, com.google.gson.JsonElement> entry : object.entrySet()) {
            com.google.gson.JsonElement value = entry.getValue();
            if (value.isJsonNull()) {
                fields.put(entry.getKey(), "null");
            } else if (value.isJsonPrimitive()) {
                fields.put(entry.getKey(), value.getAsString());
            } else {
                fields.put(entry.getKey(), value.toString());
            }
        }
    }

    private static final class ParsedUrl {
        String host = "";
        String path = "/";
        Map<String, String> query = new LinkedHashMap<>();
    }

    private static ParsedUrl parseUrl(String url) {
        ParsedUrl parsed = new ParsedUrl();
        int hostStart = 0;
        int scheme = url.indexOf("://");
        if (scheme >= 0) {
            hostStart = scheme + 3;
        }
        int pathStart = url.indexOf('/', hostStart);
        int queryStart = url.indexOf('?', hostStart);
        if (pathStart < 0) {
            parsed.host = queryStart < 0 ? url.substring(hostStart) : url.substring(hostStart, queryStart);
        } else {
            parsed.host = url.substring(hostStart, pathStart);
            parsed.path = queryStart < 0 ? url.substring(pathStart) : url.substring(pathStart, queryStart);
        }
        if (queryStart >= 0) {
            parseQuery(url.substring(queryStart + 1), parsed.query);
        }
        return parsed;
    }

    private static void parseQuery(String query, Map<String, String> fields) {
        int start = 0;
        while (start < query.length()) {
            int amp = query.indexOf('&', start);
            if (amp < 0) {
                amp = query.length();
            }
            String pair = query.substring(start, amp);
            int eq = pair.indexOf('=');
            if (eq < 0) {
                fields.put(decode(pair), "");
            } else {
                fields.put(decode(pair.substring(0, eq)), decode(pair.substring(eq + 1)));
            }
            start = amp + 1;
        }
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}

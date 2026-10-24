package com.zerobounce;

import com.google.gson.JsonObject;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Offline tests for the Warmup API client. Every request is answered by a
 * mocked {@link HttpURLConnection}; nothing here reaches the live API. The
 * assertions are on what actually went on the wire.
 */
@ExtendWith(MockitoExtension.class)
public class OfflineWarmupTest {

    @Spy
    private ZeroBounceWarmup warmup;

    private static final String API_KEY = "warmup-key";

    /** What one mocked call recorded. */
    private static class Recorded {
        String url;
        String method;
        String body;
        final Map<String, String> headers = new LinkedHashMap<>();

        String path() {
            int mark = url.indexOf('?');
            return mark < 0 ? url : url.substring(0, mark);
        }

        Map<String, String> query() {
            Map<String, String> params = new LinkedHashMap<>();
            int mark = url.indexOf('?');
            if (mark < 0) return params;
            for (String pair : url.substring(mark + 1).split("&")) {
                int split = pair.indexOf('=');
                if (split < 0) continue;
                params.put(
                        URLDecoder.decode(pair.substring(0, split), StandardCharsets.UTF_8),
                        URLDecoder.decode(pair.substring(split + 1), StandardCharsets.UTF_8)
                );
            }
            return params;
        }

        JsonObject json() {
            return com.google.gson.JsonParser.parseString(body).getAsJsonObject();
        }
    }

    private final List<Recorded> recorded = new ArrayList<>();
    private final List<String> errors = new ArrayList<>();
    private int lastStatus;

    @BeforeEach
    public void setUp() {
        warmup.initialize(API_KEY);
        recorded.clear();
        errors.clear();
        lastStatus = -1;
    }

    private void mockAll(int status, @NotNull String responseJson) throws IOException {
        mockAll(status, responseJson, "application/json");
    }

    private void mockAll(int status, @NotNull String responseJson, @NotNull String contentType)
            throws IOException {
        ByteArrayOutputStream sentBody = new ByteArrayOutputStream();

        URL url = mock(URL.class,
                withSettings().useConstructor("https://warmup-api.zerobounce.net/api/v1/warmup/seeds"));
        HttpURLConnection con = mock(HttpURLConnection.class);
        lenient().when(url.openConnection()).thenReturn(con);
        lenient().when(con.getResponseCode()).thenReturn(status);
        lenient().when(con.getHeaderField("Content-Type")).thenReturn(contentType);
        lenient().when(con.getOutputStream()).thenReturn(sentBody);
        lenient().when(con.getInputStream())
                .thenAnswer(i -> new ByteArrayInputStream(responseJson.getBytes(StandardCharsets.UTF_8)));
        lenient().when(con.getErrorStream())
                .thenAnswer(i -> new ByteArrayInputStream(responseJson.getBytes(StandardCharsets.UTF_8)));

        lenient().doAnswer(invocation -> {
            Recorded record = new Recorded();
            record.url = invocation.getArgument(0);
            recorded.add(record);
            sentBody.reset();
            return url;
        }).when(warmup).createUrlFrom(anyString());

        lenient().doAnswer(invocation -> {
            if (!recorded.isEmpty()) {
                recorded.get(recorded.size() - 1).method = invocation.getArgument(0);
            }
            return null;
        }).when(con).setRequestMethod(anyString());

        lenient().doAnswer(invocation -> {
            if (!recorded.isEmpty()) {
                recorded.get(recorded.size() - 1).headers
                        .put(invocation.getArgument(0), invocation.getArgument(1));
            }
            return null;
        }).when(con).setRequestProperty(anyString(), anyString());

        // The body is written after setRequestMethod, so read it back lazily.
        lenient().doAnswer(invocation -> {
            if (!recorded.isEmpty()) {
                recorded.get(recorded.size() - 1).body = sentBody.toString(StandardCharsets.UTF_8);
            }
            return null;
        }).when(con).disconnect();
    }

    private Recorded only() {
        assertEquals(1, recorded.size(), "expected exactly one request");
        Recorded record = recorded.get(0);
        recorded.clear();
        return record;
    }

    private final ZeroBounceSDK.OnSuccessCallback<JsonObject> succeed = r -> {
    };
    private final ZeroBounceSDK.OnSuccessCallback<String> succeedText = r -> {
    };

    private void failOnError(ErrorResponse error) {
        fail(error.getErrors().toString());
    }

    /** Collects an error, and asserts it stayed short. */
    private final ZeroBounceSDK.OnErrorCallback collect = error -> {
        errors.addAll(error.getErrors());
        lastStatus = error.getStatusCode();
        assertTrue(error.getErrors().toString().length() < 500, error.getErrors().toString());
    };

    private final ZeroBounceSDK.OnSuccessCallback<JsonObject> failOnSuccess = r -> fail("success");

    // -- construction --------------------------------------------------------

    @Test
    public void theKeyTravelsInTheApiKeyHeader() throws IOException {
        mockAll(200, "{\"ok\":true}");

        warmup.getSeedCount(succeed, this::failOnError);
        Recorded record = only();
        assertEquals("https://warmup-api.zerobounce.net/api/v1/warmup/seed-count", record.path());
        assertEquals(API_KEY, record.headers.get("API-Key"));
        // The key must never reach the query string, where it would be logged.
        assertTrue(record.query().isEmpty(), record.url);
    }

    @Test
    public void aKeyThatIsEmptyOrPaddedIsRejected() throws IOException {
        mockAll(200, "{}");
        for (String bad : new String[]{"", " key ", "key\n", "key\tx"}) {
            warmup.initialize(bad);
            errors.clear();
            warmup.getSeeds(failOnSuccess, collect);
            assertTrue(errors.toString().contains("api_key"), errors.toString());
        }
        assertTrue(recorded.isEmpty());
    }

    @Test
    public void aPlainHttpBaseUrlIsRejected() throws IOException {
        mockAll(200, "{}");
        warmup.initialize(API_KEY, "http://warmup-api.zerobounce.net");
        warmup.getSeeds(failOnSuccess, collect);
        assertTrue(errors.toString().contains("https"), errors.toString());
        assertTrue(recorded.isEmpty());
    }

    @Test
    public void aCustomHttpsBaseUrlIsUsed() throws IOException {
        mockAll(200, "{}");
        warmup.initialize(API_KEY, "https://warmup.example.com/");
        warmup.getSeeds(succeed, this::failOnError);
        assertEquals("https://warmup.example.com/api/v1/warmup/seeds", only().path());
    }

    // -- paths and verbs -----------------------------------------------------

    @Test
    public void everyEndpointUsesItsDocumentedPathAndVerb() throws IOException {
        mockAll(200, "{\"ok\":true}");
        String root = "https://warmup-api.zerobounce.net/api/v1/warmup";

        warmup.listSendingAccounts(succeed, this::failOnError);
        assertEquals(root + "/sending-accounts", only().path());
        warmup.getSendingAccount("a@b.com", succeed, this::failOnError);
        assertEquals(root + "/sending-account", only().path());
        warmup.listFromAddresses(null, null, null, succeed, this::failOnError);
        assertEquals(root + "/from-addresses", only().path());
        warmup.getEngagementRules(succeed, this::failOnError);
        assertEquals(root + "/engagement-rules", only().path());
        warmup.getCustomEngagement("a@b.com", succeed, this::failOnError);
        assertEquals(root + "/custom-engagement/a%40b.com", only().path());
        warmup.getSeeds(succeed, this::failOnError);
        assertEquals(root + "/seeds", only().path());
        warmup.getProgressToday(succeed, this::failOnError);
        assertEquals(root + "/progress-today", only().path());
        warmup.getSentToday(succeed, this::failOnError);
        assertEquals(root + "/sent-today", only().path());
        warmup.getEmailsPerDay(succeed, this::failOnError);
        assertEquals(root + "/emails-per-day", only().path());
        warmup.getStatistics("a@b.com", succeed, this::failOnError);
        assertEquals(root + "/statistics", only().path());
        warmup.getStatisticsDetailed("a@b.com", succeed, this::failOnError);
        assertEquals(root + "/statistics-detailed", only().path());
        warmup.getFromAddressStatistics("a@b.com", succeed, this::failOnError);
        assertEquals(root + "/from-address-statistics", only().path());
        warmup.checkDomainDmarc("a@b.com", null, succeed, this::failOnError);
        assertEquals(root + "/check-domain-dmarc", only().path());

        warmup.addSendingAccount("a@b.com", "self_managed",
                new ZeroBounceWarmup.AddSendingAccountOptions().setEngagementRuleId(0),
                succeed, this::failOnError);
        Recorded added = only();
        assertEquals("POST", added.method);
        assertEquals(root + "/sending-accounts", added.path());

        warmup.updateSendingAccount("a@b.com", 1, 4, true, succeed, this::failOnError);
        assertEquals("PUT", only().method);
        warmup.deleteSendingAccount("a@b.com", succeed, this::failOnError);
        assertEquals("DELETE", only().method);
        warmup.changeFromAddress("a@b.com", "c@d.com", succeed, this::failOnError);
        Recorded changed = only();
        assertEquals("PUT", changed.method);
        assertEquals(root + "/smtp-credentials", changed.path());
        warmup.setEngagementRule("a@b.com", 2, succeed, this::failOnError);
        Recorded rule = only();
        assertEquals("PUT", rule.method);
        assertEquals(root + "/engagement-rule", rule.path());
        warmup.createCustomEngagement("a@b.com", 10, 10, 5, succeed, this::failOnError);
        Recorded created = only();
        assertEquals("POST", created.method);
        assertEquals(root + "/custom-engagement", created.path());
        warmup.updateCustomEngagement("a@b.com", 10, 10, 5, succeed, this::failOnError);
        assertEquals("PUT", only().method);
        warmup.deleteCustomEngagement("a@b.com", 1, succeed, this::failOnError);
        assertEquals("DELETE", only().method);
        warmup.testSmtpConfiguration("a@b.com", "u", "p", "smtp.example.com", 587,
                succeed, this::failOnError);
        Recorded smtp = only();
        assertEquals("POST", smtp.method);
        assertEquals(root + "/test-smtp-configuration", smtp.path());
    }

    @Test
    public void statisticsPerDaySendsBothDates() throws IOException {
        mockAll(200, "{}");
        warmup.getStatisticsPerDay("a@b.com", "2026-01-02", "2026-01-09", succeed, this::failOnError);
        Recorded record = only();
        assertEquals("2026-01-02", record.query().get("start_date"));
        assertEquals("2026-01-09", record.query().get("end_date"));
    }

    // -- argument validation -------------------------------------------------

    @Test
    public void requiredArgumentsAreCheckedBeforeAnyRequest() throws IOException {
        mockAll(200, "{}");
        warmup.getSendingAccount("", failOnSuccess, collect);
        warmup.deleteSendingAccount(" ", failOnSuccess, collect);
        warmup.changeFromAddress("", "b@c.com", failOnSuccess, collect);
        warmup.changeFromAddress("a@b.com", "", failOnSuccess, collect);
        warmup.setEngagementRule("", 1, failOnSuccess, collect);
        warmup.getStatistics("", failOnSuccess, collect);
        warmup.checkDomainDmarc("", null, failOnSuccess, collect);
        assertTrue(recorded.isEmpty(), "nothing should reach the network");
    }

    @Test
    public void engagementRuleIdsAreRangeChecked() throws IOException {
        mockAll(200, "{}");
        for (int bad : new int[]{-1, 5}) {
            errors.clear();
            warmup.setEngagementRule("a@b.com", bad, failOnSuccess, collect);
            assertTrue(errors.toString().contains("rule_id"), errors.toString());
        }
        assertTrue(recorded.isEmpty());

        for (int good = 0; good <= 4; good++) {
            warmup.setEngagementRule("a@b.com", good, succeed, this::failOnError);
            assertEquals(good, only().json().get("rule_id").getAsInt());
        }
    }

    @Test
    public void customEngagementPercentagesAreRangeChecked() throws IOException {
        mockAll(200, "{}");
        int[][] bad = {
                {4, 10, 5},    // reply_pct below 5
                {46, 10, 5},   // reply_pct above 45
                {10, 4, 5},    // important_pct below 5
                {10, 41, 5},   // important_pct above 40
                {10, 10, 71},  // click_pct above 70
                {10, 10, 18},  // click_pct above important_pct x 1.75
        };
        for (int[] values : bad) {
            warmup.createCustomEngagement("a@b.com", values[0], values[1], values[2],
                    failOnSuccess, collect);
        }
        assertTrue(recorded.isEmpty());

        warmup.createCustomEngagement("a@b.com", 20, 20, 35, succeed, this::failOnError);
        JsonObject body = only().json();
        assertEquals(20, body.get("reply_pct").getAsInt());
        assertEquals(35, body.get("click_pct").getAsInt());
    }

    @Test
    public void deleteCustomEngagementOnlyFallsBackToAStandardRule() throws IOException {
        mockAll(200, "{}");
        warmup.deleteCustomEngagement("a@b.com", 0, failOnSuccess, collect);
        assertTrue(errors.toString().contains("engagement_rule_id"), errors.toString());
        assertTrue(recorded.isEmpty());

        warmup.deleteCustomEngagement("a@b.com", 3, succeed, this::failOnError);
        assertEquals(3, only().json().get("engagement_rule_id").getAsInt());
    }

    @Test
    public void updateSendingAccountChecksItsRanges() throws IOException {
        mockAll(200, "{}");
        warmup.updateSendingAccount("a@b.com", 5, 4, true, failOnSuccess, collect);
        warmup.updateSendingAccount("a@b.com", 1, 9, true, failOnSuccess, collect);
        assertTrue(recorded.isEmpty());

        warmup.updateSendingAccount("a@b.com", 1, 5, false, succeed, this::failOnError);
        JsonObject body = only().json();
        assertEquals(1, body.get("team_id").getAsInt());
        assertEquals(5, body.get("status_id").getAsInt());
        assertFalse(body.get("auto_enable").getAsBoolean());
    }

    @Test
    public void addSendingAccountChecksTheOnboardingModel() throws IOException {
        mockAll(200, "{}");
        warmup.addSendingAccount("a@b.com", "managed", null, failOnSuccess, collect);
        assertTrue(errors.toString().contains("self_managed"), errors.toString());

        // self_managed needs its engagement rule and takes no ZB-managed fields.
        warmup.addSendingAccount("a@b.com", "self_managed", null, failOnSuccess, collect);
        warmup.addSendingAccount("a@b.com", "self_managed",
                new ZeroBounceWarmup.AddSendingAccountOptions()
                        .setEngagementRuleId(0)
                        .setEmailContent(new ZeroBounceWarmup.EmailContent()),
                failOnSuccess, collect);
        assertTrue(recorded.isEmpty());

        warmup.addSendingAccount("a@b.com", "self_managed",
                new ZeroBounceWarmup.AddSendingAccountOptions()
                        .setEngagementRuleId(2)
                        .setIgnoreConflict(true),
                succeed, this::failOnError);
        JsonObject body = only().json();
        assertEquals("self_managed", body.get("onboarding_model").getAsString());
        assertEquals(2, body.get("engagement_rule_id").getAsInt());
        assertTrue(body.get("ignore_conflict").getAsBoolean());
    }

    @Test
    public void addSendingAccountChecksTheZbManagedPayload() throws IOException {
        mockAll(200, "{}");
        warmup.addSendingAccount("a@b.com", "zb_managed",
                new ZeroBounceWarmup.AddSendingAccountOptions()
                        .setEmailContent(new ZeroBounceWarmup.EmailContent()
                                .setTypesOfEmails(List.of("promotional"))),
                failOnSuccess, collect);
        warmup.addSendingAccount("a@b.com", "zb_managed",
                new ZeroBounceWarmup.AddSendingAccountOptions()
                        .setManagedSending(new ZeroBounceWarmup.ManagedSending()
                                .setTimezoneOffset(15)),
                failOnSuccess, collect);
        warmup.addSendingAccount("a@b.com", "zb_managed",
                new ZeroBounceWarmup.AddSendingAccountOptions()
                        .setManagedSending(new ZeroBounceWarmup.ManagedSending()
                                .setWarmupStartDate("02/01/2026")),
                failOnSuccess, collect);
        assertTrue(recorded.isEmpty());

        warmup.addSendingAccount("a@b.com", "zb_managed",
                new ZeroBounceWarmup.AddSendingAccountOptions()
                        .setEmailContent(new ZeroBounceWarmup.EmailContent()
                                .setTypesOfEmails(List.of("newsletter", "marketing")))
                        .setManagedSending(new ZeroBounceWarmup.ManagedSending()
                                .setTimezoneOffset(-5)
                                .setWarmupStartDate("2026-03-04")),
                succeed, this::failOnError);
        JsonObject body = only().json();
        assertEquals("zb_managed", body.get("onboarding_model").getAsString());
        assertEquals("marketing",
                body.getAsJsonObject("email_content").getAsJsonArray("types_of_emails")
                        .get(1).getAsString());
        assertEquals("2026-03-04",
                body.getAsJsonObject("managed_sending").get("warmup_start_date").getAsString());
    }

    @Test
    public void listFromAddressesChecksItsSearchAndSendsPagingAsStrings() throws IOException {
        mockAll(200, "{}");
        warmup.listFromAddresses("bad search!", null, null, failOnSuccess, collect);
        warmup.listFromAddresses(null, -1, null, failOnSuccess, collect);
        assertTrue(recorded.isEmpty());

        warmup.listFromAddresses("a.b-c_d@e+f", 0, 50, succeed, this::failOnError);
        Map<String, String> query = only().query();
        assertEquals("a.b-c_d@e+f", query.get("search_from"));
        assertEquals("0", query.get("offset"));
        assertEquals("50", query.get("limit"));
    }

    @Test
    public void statisticsPerDayRejectsABackwardsRangeAndBadDates() throws IOException {
        mockAll(200, "{}");
        warmup.getStatisticsPerDay("a@b.com", "2026-02-02", "2026-01-01", failOnSuccess, collect);
        warmup.getStatisticsPerDay("a@b.com", "01/02/2026", "2026-01-01", failOnSuccess, collect);
        assertTrue(recorded.isEmpty());
    }

    @Test
    public void checkDomainDmarcRequiresAnEmailAddressNotADomain() throws IOException {
        mockAll(200, "{}");
        // LIVE: the API answers a bare domain with "Please provide a valid
        // email address", although the docs describe a domain.
        warmup.checkDomainDmarc("example.com", null, failOnSuccess, collect);
        assertTrue(errors.toString().contains("email address"), errors.toString());
        assertTrue(recorded.isEmpty());

        warmup.checkDomainDmarc("sender@example.com", "zb", succeed, this::failOnError);
        assertEquals("zb", only().query().get("dkim_selector"));
    }

    @Test
    public void customEngagementPathIdsCannotTraverse() throws IOException {
        mockAll(200, "{}");
        warmup.getCustomEngagement(".", failOnSuccess, collect);
        warmup.getCustomEngagement("..", failOnSuccess, collect);
        assertTrue(recorded.isEmpty());
    }

    @Test
    public void theSmtpTestChecksItsPort() throws IOException {
        mockAll(200, "{}");
        for (int bad : new int[]{0, 65536}) {
            errors.clear();
            warmup.testSmtpConfiguration("a@b.com", "u", "p", "s", bad, failOnSuccess, collect);
            assertTrue(errors.toString().contains("port"), errors.toString());
        }
        assertTrue(recorded.isEmpty());
    }

    // -- responses -----------------------------------------------------------

    @Test
    public void anEmptySuccessBecomesNull() throws IOException {
        mockAll(204, "");
        List<JsonObject> seen = new ArrayList<>();
        warmup.deleteSendingAccount("a@b.com", seen::add, this::failOnError);
        assertEquals(1, seen.size());
        assertNull(seen.get(0));

        recorded.clear();
        mockAll(200, "");
        seen.clear();
        warmup.getSeedCount(seen::add, this::failOnError);
        assertEquals(1, seen.size());
        assertNull(seen.get(0));
    }

    @Test
    public void aListPayloadComesBackUnderData() throws IOException {
        mockAll(200, "[{\"seed\":\"a@b.com\"},{\"seed\":\"c@d.com\"}]");
        List<JsonObject> seen = new ArrayList<>();
        warmup.getSeeds(seen::add, this::failOnError);
        assertEquals(2, seen.get(0).getAsJsonArray("data").size());
    }

    @Test
    public void csvDownloadsComeBackAsText() throws IOException {
        mockAll(200, "seed,provider\na@b.com,gmail\n", "text/csv");
        List<String> seen = new ArrayList<>();
        warmup.downloadSeeds(seen::add, this::failOnError);
        assertEquals("seed,provider" + "a@b.com,gmail", seen.get(0));

        recorded.clear();
        mockAll(200, "day,sent", "text/csv");
        warmup.downloadStatistics("a@b.com", succeedText, this::failOnError);
        assertEquals("a@b.com", only().query().get("c_from"));
    }

    @Test
    public void aJsonErrorInPlaceOfACsvIsAnError() throws IOException {
        mockAll(200, "{\"message\":\"No statistics for this address.\"}");
        warmup.downloadSeeds(r -> fail("success"), collect);
        assertTrue(errors.toString().contains("No statistics for this address."), errors.toString());
    }

    @Test
    public void theHttpStatusIsOnTheError() throws IOException {
        mockAll(429, "{\"message\":\"Custom rule was updated less than 7 days ago.\"}");
        warmup.updateCustomEngagement("a@b.com", 10, 10, 5, failOnSuccess, collect);
        assertEquals(429, lastStatus);
        assertTrue(errors.toString().contains("Custom rule was updated"), errors.toString());
    }

    @Test
    public void aValidationErrorListBecomesOneShortMessage() throws IOException {
        mockAll(422, "{\"detail\":[{\"loc\":[\"body\",\"reply_pct\"],\"msg\":\"ensure this value is <= 45\"}]}");
        warmup.createCustomEngagement("a@b.com", 10, 10, 5, failOnSuccess, collect);
        assertTrue(errors.toString().contains("reply_pct: ensure this value is <= 45"),
                errors.toString());
    }

    @Test
    public void aTopLevelStringListBecomesOneShortMessage() throws IOException {
        mockAll(400, "[\"Sending account already exists.\"]");
        warmup.addSendingAccount("a@b.com", "self_managed",
                new ZeroBounceWarmup.AddSendingAccountOptions().setEngagementRuleId(0),
                failOnSuccess, collect);
        assertTrue(errors.toString().contains("Sending account already exists."), errors.toString());
    }

    @Test
    public void anHtmlErrorPageIsNotPastedIntoTheMessage() throws IOException {
        mockAll(403, "<!DOCTYPE html><html><head><title>Access denied</title></head><body>"
                + "<p>blocked</p>".repeat(2000) + "</body></html>", "text/html");
        warmup.getSeeds(failOnSuccess, collect);
        assertTrue(errors.toString().contains("HTML error page"), errors.toString());
        assertEquals(403, lastStatus);
    }

    @Test
    public void aRedirectIsRefusedRatherThanFollowed() throws IOException {
        mockAll(302, "");
        warmup.getSeeds(failOnSuccess, collect);
        assertTrue(errors.toString().contains("redirect"), errors.toString());
        assertEquals(302, lastStatus);
    }

    @Test
    public void theKeyNeverAppearsInAnError() throws IOException {
        mockAll(401, "{\"message\":\"Invalid key " + API_KEY + "\"}");
        warmup.getSeeds(failOnSuccess, collect);
        assertFalse(errors.toString().contains(API_KEY), errors.toString());
        assertTrue(errors.toString().contains("***"), errors.toString());
    }

    @Test
    public void smtpCredentialsNeverAppearInAnError() throws IOException {
        mockAll(500, "{\"message\":\"login failed for user bob with s3cret\"}");
        warmup.testSmtpConfiguration("a@b.com", "bob", "s3cret", "smtp.example.com", 587,
                failOnSuccess, collect);
        assertFalse(errors.toString().contains("s3cret"), errors.toString());
        assertFalse(errors.toString().contains("bob"), errors.toString());
        // LIVE: a failed SMTP login comes back as a 500, not a 4xx.
        assertEquals(500, lastStatus);
    }

    @Test
    public void aMalformedBodyDoesNotLeakAParserDump() throws IOException {
        mockAll(200, "<<<not-json>>>" + "x".repeat(5000));
        warmup.getSeeds(failOnSuccess, collect);
        assertTrue(errors.toString().contains("not valid JSON"), errors.toString());
    }
}

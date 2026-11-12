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
    public void aClientCanBeConstructedAndKeepsItsOwnKey() throws Exception {
        // The notes say "call ZeroBounceWarmup.getInstance() or construct one".
        assertTrue(java.lang.reflect.Modifier.isPublic(
                ZeroBounceWarmup.class.getConstructor().getModifiers()));

        ZeroBounceWarmup other = spy(new ZeroBounceWarmup());
        // Nothing below may reach a network: the first call must fail before
        // it builds a URL, and the second one never runs on this client.
        lenient().doThrow(new AssertionError("no request expected"))
                .when(other).createUrlFrom(anyString());
        assertNotSame(ZeroBounceWarmup.getInstance(), other);
        other.getSeedCount(r -> fail("success"), collect);
        assertTrue(errors.toString().contains("not initialized"), errors.toString());

        other.initialize("other-team-key");
        mockAll(200, "{\"ok\":true}");
        warmup.getSeedCount(succeed, this::failOnError);
        assertEquals(API_KEY, only().headers.get("API-Key"));
    }

    @Test
    public void aFailureWithNoBodyKeepsItsStatus() throws IOException {
        // HttpURLConnection has no error stream for a failed response with no body.
        mockAll(500, "");
        HttpURLConnection con = (HttpURLConnection) warmup.createUrlFrom("https://x").openConnection();
        recorded.clear();
        when(con.getErrorStream()).thenReturn(null);
        warmup.getSeedCount(r -> fail("success"), collect);
        assertEquals(List.of("Empty response from ZeroBounce (HTTP 500)"), errors);
        assertEquals(500, lastStatus);
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

    /**
     * The exact JSON bodies of testSmtpConfiguration and changeFromAddress, so a
     * renamed or dropped wire field fails (X3).
     */
    @Test
    public void smtpAndFromAddressBodiesUseTheDocumentedFieldNames() throws IOException {
        mockAll(200, "{}");
        warmup.testSmtpConfiguration("a@b.com", "bob", "pw", "smtp.example.com", 587,
                succeed, this::failOnError);
        assertEquals(com.google.gson.JsonParser.parseString("{\"from_address\":\"a@b.com\",\"username\":\"bob\","
                + "\"password\":\"pw\",\"server\":\"smtp.example.com\",\"port\":587}"), only().json());

        warmup.changeFromAddress("old@b.com", "new@b.com", succeed, this::failOnError);
        assertEquals(com.google.gson.JsonParser.parseString("{\"team_id\":1,\"old_from_address\":\"old@b.com\","
                + "\"new_from_address\":\"new@b.com\"}"), only().json());
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

    /**
     * The zb_managed body has exactly the documented fields
     * (https://www.zerobounce.net/docs/warmup-api/adding-a-sending-account).
     */
    @Test
    public void addSendingAccountSendsTheDocumentedZbManagedBody() throws IOException {
        mockAll(200, "{}");
        warmup.addSendingAccount("a@b.com", "zb_managed",
                new ZeroBounceWarmup.AddSendingAccountOptions()
                        .setEmailContent(new ZeroBounceWarmup.EmailContent()
                                .setCompanyName("Acme")
                                .setCompanyWebsite("https://acme.example")
                                .setCompanyDescription("B2B email tools")
                                .setTypesOfEmails(List.of("marketing", "newsletter"))
                                .setExampleEmail("Hi, short intro to Acme."))
                        .setManagedSending(new ZeroBounceWarmup.ManagedSending()
                                .setWeekdaysOnly(true)
                                .setTimezone("Asia/Kolkata")
                                .setTimezoneOffset(5.5)
                                .setWarmupStartDate("2026-03-04")),
                succeed, this::failOnError);
        assertEquals("{\"from_address\":\"a@b.com\",\"onboarding_model\":\"zb_managed\","
                + "\"email_content\":{\"company_name\":\"Acme\",\"company_website\":\"https://acme.example\","
                + "\"company_description\":\"B2B email tools\",\"types_of_emails\":[\"marketing\",\"newsletter\"],"
                + "\"example_email\":\"Hi, short intro to Acme.\"},"
                + "\"managed_sending\":{\"weekdays_only\":true,\"timezone\":\"Asia/Kolkata\","
                + "\"timezone_offset\":5.5,\"warmup_start_date\":\"2026-03-04\"}}", only().body);

        warmup.addSendingAccount("a@b.com", "zb_managed",
                new ZeroBounceWarmup.AddSendingAccountOptions()
                        .setManagedSending(new ZeroBounceWarmup.ManagedSending().setWeekdaysOnly(false)),
                succeed, this::failOnError);
        assertEquals("{\"from_address\":\"a@b.com\",\"onboarding_model\":\"zb_managed\","
                + "\"managed_sending\":{\"weekdays_only\":false}}", only().body);
    }

    /** Whole and fractional hours from -11 to 14 go out as numbers; anything else is refused. */
    @Test
    public void addSendingAccountTakesFractionalTimezoneOffsets() throws IOException {
        mockAll(200, "{}");
        for (double offset : new double[]{5.5, 3.0, -11, 14, -3.5, 5.75}) {
            warmup.addSendingAccount("a@b.com", "zb_managed",
                    new ZeroBounceWarmup.AddSendingAccountOptions()
                            .setManagedSending(new ZeroBounceWarmup.ManagedSending().setTimezoneOffset(offset)),
                    succeed, this::failOnError);
            com.google.gson.JsonElement sent = only().json().getAsJsonObject("managed_sending").get("timezone_offset");
            assertEquals(offset, sent.getAsDouble(), 0.0);
        }
        // An int literal still compiles, and goes out as that number of hours.
        warmup.addSendingAccount("a@b.com", "zb_managed",
                new ZeroBounceWarmup.AddSendingAccountOptions()
                        .setManagedSending(new ZeroBounceWarmup.ManagedSending().setTimezoneOffset(3)),
                succeed, this::failOnError);
        assertTrue(only().body.contains("\"timezone_offset\":3.0"));

        for (double offset : new double[]{-11.5, 14.5, -12, 15, Double.NaN, Double.POSITIVE_INFINITY}) {
            errors.clear();
            warmup.addSendingAccount("a@b.com", "zb_managed",
                    new ZeroBounceWarmup.AddSendingAccountOptions()
                            .setManagedSending(new ZeroBounceWarmup.ManagedSending().setTimezoneOffset(offset)),
                    failOnSuccess, collect);
            assertEquals(List.of("timezone_offset must be -11 to 14"), errors, "offset " + offset);
        }
        assertTrue(recorded.isEmpty(), "a refused offset sends nothing");
    }

    /**
     * Callers of 3.0.1's fields still compile: website goes out as
     * company_website (company_website wins when both are set), and industry and
     * daily_limit, which the API does not read, are left out with a warning.
     */
    @Test
    @SuppressWarnings("deprecation")
    public void addSendingAccountMapsTheOldFieldsToTheDocumentedOnes() throws IOException {
        List<String> warnings = new ArrayList<>();
        ZeroBounceSDK.setLogger(new ZBLogger() {
            @Override
            public void warn(String msg) {
                warnings.add(msg);
            }
        });
        try {
            mockAll(200, "{}");
            warmup.addSendingAccount("a@b.com", "zb_managed",
                    new ZeroBounceWarmup.AddSendingAccountOptions()
                            .setEmailContent(new ZeroBounceWarmup.EmailContent()
                                    .setIndustry("Software")
                                    .setCompanyName("Acme")
                                    .setWebsite("https://old.example"))
                            .setManagedSending(new ZeroBounceWarmup.ManagedSending()
                                    .setTimezoneOffset(Integer.valueOf(-5))
                                    .setDailyLimit(50)),
                    succeed, this::failOnError);
            assertEquals("{\"from_address\":\"a@b.com\",\"onboarding_model\":\"zb_managed\","
                    + "\"email_content\":{\"company_name\":\"Acme\",\"company_website\":\"https://old.example\"},"
                    + "\"managed_sending\":{\"timezone_offset\":-5.0}}", only().body);
            assertEquals(2, warnings.size(), warnings.toString());
            assertTrue(warnings.get(0).contains("industry"), warnings.toString());
            assertTrue(warnings.get(1).contains("daily_limit"), warnings.toString());

            warmup.addSendingAccount("a@b.com", "zb_managed",
                    new ZeroBounceWarmup.AddSendingAccountOptions()
                            .setEmailContent(new ZeroBounceWarmup.EmailContent()
                                    .setWebsite("https://old.example")
                                    .setCompanyWebsite("https://new.example")),
                    succeed, this::failOnError);
            assertEquals("https://new.example",
                    only().json().getAsJsonObject("email_content").get("company_website").getAsString());
        } finally {
            ZeroBounceSDK.setLogger(null);
        }
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
        // The body exactly as served, line breaks included.
        assertEquals("seed,provider\na@b.com,gmail\n", seen.get(0));

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

    /**
     * An HTML page served on a 200 (a proxy's or Cloudflare's block page) is an
     * error with status 200 from both CSV downloads, by its content type or by
     * its look, never a CSV; the page's title is redacted like any body text.
     */
    @Test
    public void anHtmlPageInPlaceOfACsvIsAnError() throws IOException {
        String page = "<!DOCTYPE html><html><head><title>Denied for " + API_KEY + "</title></head>"
                + "<body>blocked</body></html>";
        String[][] answers = {
                {page, "text/html; charset=utf-8"},
                {page, "text/csv"},
                {"<html><body>blocked</body></html>", "application/octet-stream"},
                {"seed,provider", "text/html"},
        };
        for (String[] answer : answers) {
            for (int download = 0; download < 2; download++) {
                errors.clear();
                lastStatus = -1;
                mockAll(200, answer[0], answer[1]);
                ZeroBounceSDK.OnSuccessCallback<String> unexpected = r -> fail("success: " + r);
                if (download == 0) {
                    warmup.downloadSeeds(unexpected, collect);
                } else {
                    warmup.downloadStatistics("a@b.com", unexpected, collect);
                }
                assertEquals(200, lastStatus, answer[1] + " " + errors);
                assertTrue(errors.toString().contains("HTML"), errors.toString());
                assertFalse(errors.toString().contains(API_KEY), errors.toString());
            }
        }
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
    @Test
    public void anExceptionFromTheSuccessCallbackPropagates() throws IOException {
        IllegalStateException thrown = new IllegalStateException("from the caller's success callback");
        mockAll(200, "{\"seeds\":[]}");
        RuntimeException caught = assertThrows(RuntimeException.class, () -> warmup.getSeeds(r -> {
            throw thrown;
        }, collect));
        assertSame(thrown, caught);
        mockAll(200, "[1, 2]");
        caught = assertThrows(RuntimeException.class, () -> warmup.getSeeds(r -> {
            throw thrown;
        }, collect));
        assertSame(thrown, caught);
        mockAll(200, "email\nseed@example.com\n", "text/csv");
        caught = assertThrows(RuntimeException.class, () -> warmup.downloadSeeds(r -> {
            throw thrown;
        }, collect));
        assertSame(thrown, caught);
        assertTrue(errors.isEmpty(), "the caller's exception reached the error callback: " + errors);
    }

    @Test
    public void anExceptionFromTheErrorCallbackPropagatesOnce() throws IOException {
        IllegalStateException thrown = new IllegalStateException("from the caller's error callback");
        for (int status : new int[]{200, 302, 500}) {
            mockAll(status, status == 200 ? "<<<not-json>>>" : "{\"message\":\"failed\"}");
            java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
            RuntimeException caught = assertThrows(RuntimeException.class, () -> warmup.getSeeds(failOnSuccess, e -> {
                calls.incrementAndGet();
                throw thrown;
            }));
            assertSame(thrown, caught);
            assertEquals(1, calls.get(), "HTTP " + status);
        }
    }
}

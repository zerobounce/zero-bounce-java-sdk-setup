package com.zerobounce.live;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import com.zerobounce.ErrorResponse;
import com.zerobounce.ZBBulkFileJobOptions;
import com.zerobounce.ZBValidateBatchData;
import com.zerobounce.ZBValidateOptions;
import com.zerobounce.ZeroBounceSDK;
import com.zerobounce.ZeroBounceWarmup;

import java.io.File;
import java.io.FileWriter;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Opt-in live API suite: calls every SDK method against the real API to check
 * payloads and response shapes.
 * <p>
 * It lives outside the Maven module's source root, so {@code mvn test} and CI
 * never compile or run it. Compile and run it by hand:
 *
 * <pre>
 * mvn -f zero-bounce-sdk/pom.xml dependency:build-classpath -Dmdep.outputFile=/tmp/cp.txt
 * CP="zero-bounce-sdk/target/classes:$(cat /tmp/cp.txt)"
 * javac -cp "$CP" -d live/out live/LiveApi.java
 * export ZEROBOUNCE_API_KEY=...          # validation API (skipped without it)
 * export ZEROBOUNCE_WARMUP_API_KEY=...   # Warmup API (skipped without it)
 * java -cp "$CP:live/out" com.zerobounce.live.LiveApi
 * </pre>
 * <p>
 * By default only free calls run: sandbox addresses (no credits), reads, and
 * requests expected to fail (unknown file IDs, S3/SFTP without an integration)
 * that still prove the API accepts the payload. The free tier checks that the
 * credit balance did not change. Two flags opt in to more:
 * <ul>
 *   <li>{@code ZEROBOUNCE_LIVE_SPEND=1} spends credits: scoring, activity,
 *       email finder, and a bulk round trip whose job is deleted again.</li>
 *   <li>{@code ZEROBOUNCE_LIVE_CHANGES=1} reversible account changes, each
 *       undone in cleanup even when a test fails: a test filter, and a
 *       self-managed Warmup sending account.</li>
 * </ul>
 * <p>
 * Never called, because the API cannot undo them: creating an Inbox Placement
 * test (it cannot be deleted while running), setting the Inbox Placement
 * webhook (it cannot be cleared), and ZB-managed Warmup onboarding (it starts
 * mailbox provisioning).
 * <p>
 * No key is ever written to a file or printed. Keys come from the environment.
 */
public final class LiveApi {

    /**
     * Well-formed but matching no file: a malformed ID can be
     * Cloudflare-blocked instead of answered.
     */
    private static final String UNKNOWN_ID = "0d1b7a4e-1111-2222-3333-444455556666";
    private static final String CALLBACK = "https://example.com/zerobounce-sdk-live-test";
    private static final String SANDBOX = "valid@example.com";

    private static int passed;
    private static int failed;
    private static int skipped;
    private static final List<String> failures = new ArrayList<>();

    private static String env(String name) {
        String value = System.getenv(name);
        return value == null ? "" : value.trim();
    }

    private static String apiKey() {
        return env("ZEROBOUNCE_API_KEY");
    }

    private static String warmupKey() {
        return env("ZEROBOUNCE_WARMUP_API_KEY");
    }

    private static boolean flag(String name) {
        return "1".equals(env(name));
    }

    private static String redact(String text) {
        String value = text == null ? "" : text;
        for (String secret : Arrays.asList(apiKey(), warmupKey())) {
            if (!secret.isEmpty()) {
                value = value.replace(secret, "***");
            }
        }
        return value;
    }

    /** A check either passes, or records why it did not. */
    private static void check(String name, Supplier<String> body) {
        String problem;
        try {
            problem = body.get();
        } catch (RuntimeException e) {
            problem = redact(String.valueOf(e.getMessage()));
        }
        if (problem == null || problem.isEmpty()) {
            passed++;
            System.out.println("  + " + name);
        } else {
            failed++;
            failures.add(name + ": " + redact(problem));
            System.out.println("  X " + name + "\n      " + redact(problem));
        }
    }

    private static void skip(String name, String reason) {
        skipped++;
        System.out.println("  - SKIP " + name + " (" + reason + ")");
    }

    /** Runs a call and reports what it produced: null on success. */
    private static String run(BiConsumer<Runnable, ZeroBounceSDK.OnErrorCallback> call) {
        AtomicReference<String> problem = new AtomicReference<>("no response");
        call.accept(() -> problem.set(null),
                error -> problem.set(redact(error.getErrors().toString())));
        return problem.get();
    }

    /** Runs a call that must fail, and hands back the error's text. */
    private static String expectFailure(
            String what, BiConsumer<Runnable, ZeroBounceSDK.OnErrorCallback> call) {
        AtomicReference<String> message = new AtomicReference<>(null);
        call.accept(() -> message.set(null),
                error -> message.set(redact(error.getErrors().toString())));
        return message.get() == null ? what + " unexpectedly succeeded" : null;
    }

    private static ZeroBounceSDK sdk() {
        ZeroBounceSDK zb = ZeroBounceSDK.getInstance();
        zb.initialize(apiKey());
        return zb;
    }

    public static void main(String[] args) {
        String key = apiKey();
        String warmup = warmupKey();
        boolean spend = flag("ZEROBOUNCE_LIVE_SPEND");
        boolean changes = flag("ZEROBOUNCE_LIVE_CHANGES");

        System.out.println("ZeroBounce live API suite");
        System.out.println("  validation key: " + (key.isEmpty() ? "MISSING" : "set"));
        System.out.println("  warmup key:     " + (warmup.isEmpty() ? "MISSING" : "set"));
        System.out.println("  flags:          SPEND=" + (spend ? 1 : 0)
                + " CHANGES=" + (changes ? 1 : 0) + "\n");

        if (!key.isEmpty()) {
            validationFree();
            if (spend) {
                validationSpend();
            }
            if (changes) {
                validationChanges();
            }
        }
        if (!warmup.isEmpty()) {
            warmupFree();
            if (changes) {
                warmupChanges();
            }
        }

        System.out.println("\n" + passed + " passed, " + failed + " failed, " + skipped + " skipped");
        if (!failures.isEmpty()) {
            System.out.println("\nFailures:");
            for (String line : failures) {
                System.out.println("  - " + line);
            }
        }
        System.exit(failed == 0 ? 0 : 1);
    }

    // -- validation API, free -------------------------------------------------

    private static void validationFree() {
        System.out.println("Validation API (free calls)");
        ZeroBounceSDK zb = sdk();
        AtomicReference<String> before = new AtomicReference<>(null);

        check("getCredits returns a balance", () -> run((ok, err) ->
                zb.getCredits(response -> {
                    before.set(response.getCredits());
                    ok.run();
                }, err)));

        check("getApiUsage parses the documented date formats", () -> run((ok, err) -> {
            long day = 24L * 3600 * 1000;
            zb.getApiUsage(new Date(System.currentTimeMillis() - 7 * day), new Date(),
                    response -> ok.run(), err);
        }));

        check("validate on a sandbox address spends nothing", () -> {
            AtomicReference<String> problem = new AtomicReference<>("no response");
            zb.validate(SANDBOX, null, response -> problem.set(
                            SANDBOX.equals(response.getAddress()) ? null : "address did not echo"),
                    error -> problem.set(redact(error.getErrors().toString())));
            return problem.get();
        });

        check("validate works over POST as well as GET", () -> run((ok, err) -> {
            // No billable add-on here: activity_data, verify_plus and
            // domain_info are charged even on a sandbox address, so they live
            // in the SPEND tier.
            ZBValidateOptions options = new ZBValidateOptions().setUsePost(true).setTimeout(30);
            zb.validate(SANDBOX, null, options, response -> ok.run(), err);
        }));

        check("LIVE: validateBatch accepts timeout as a string", () -> {
            // Sent as a JSON number the API rejects the whole batch with the
            // account-level "Invalid API Key or your account ran out of credits".
            AtomicReference<String> problem = new AtomicReference<>("no response");
            zb.validateBatch(List.of(new ZBValidateBatchData(SANDBOX, null)),
                    new ZBValidateOptions().setTimeout(30),
                    response -> {
                        problem.set(null);
                        if (response.getErrors() != null) {
                            response.getErrors().forEach(error -> {
                                Object address = error.get("email_address");
                                if ("all".equalsIgnoreCase(String.valueOf(address))) {
                                    problem.set("account-level error: " + error.get("error"));
                                }
                            });
                        }
                    },
                    error -> problem.set(redact(error.getErrors().toString())));
            return problem.get();
        });

        check("an unknown file id is a short, clean error", () ->
                expectFailure("fileStatus", (ok, err) ->
                        zb.fileStatus(UNKNOWN_ID, response -> ok.run(), err)));

        check("listS3Files answers (empty without an S3 integration)", () -> {
            AtomicReference<String> problem = new AtomicReference<>(null);
            zb.listS3Files(null, null, files -> {
            }, error -> {
                // No S3 integration configured is an expected, documented failure.
                if (error.getErrors().toString().length() >= 500) {
                    problem.set("message too long");
                }
            });
            return problem.get();
        });

        check("LIVE: S3/SFTP jobs reject a payload without return_url before sending", () -> {
            AtomicReference<String> message = new AtomicReference<>(null);
            zb.emailFinderS3File(new ZBBulkFileJobOptions().setFileName("f.csv").setHasHeaderRow(true),
                    response -> message.set("unexpectedly succeeded"),
                    error -> message.set(error.getErrors().toString().contains("return_url")
                            ? null : "unexpected message: " + redact(error.getErrors().toString())));
            return message.get();
        });

        check("LIVE: a complete S3 job payload is accepted by the API", () -> {
            // Without an S3 integration this fails on the integration, not the payload.
            AtomicReference<String> problem = new AtomicReference<>(null);
            zb.emailFinderS3File(new ZBBulkFileJobOptions()
                            .setFileName("f.csv").setHasHeaderRow(true).setReturnUrl(CALLBACK),
                    response -> {
                    },
                    error -> {
                        String message = redact(error.getErrors().toString());
                        if (message.contains("return URL is not permitted")
                                || message.contains("This field is required")) {
                            problem.set("payload rejected: " + message);
                        }
                    });
            return problem.get();
        });

        check("filters list", () -> run((ok, err) ->
                zb.listFilters(false, response -> ok.run(), err)));

        check("LIVE: the Inbox Placement Tester accepts a Bearer key", () -> run((ok, err) ->
                zb.getInboxPlacementProviders(response -> ok.run(), err)));

        check("inbox placement test types", () -> run((ok, err) ->
                zb.getInboxPlacementTestTypes(response -> ok.run(), err)));

        check("LIVE: inbox placement tests are listed from /tests", () -> {
            // The listing is GET /tests (plural); /test is the single-test path
            // and answers Method "GET" not allowed.
            String problem = run((ok, err) -> zb.listInboxPlacementTests(
                    new ZeroBounceSDK.ListInboxPlacementTestsOptions().setPage(1).setPageSize(10),
                    response -> ok.run(), err));
            if (problem != null) {
                return problem;
            }
            return run((ok, err) -> zb.getInboxPlacementWebhook(response -> ok.run(), err));
        });

        check("list evaluator result for an unknown id fails cleanly", () ->
                expectFailure("getListEvaluatorResult", (ok, err) ->
                        zb.getListEvaluatorResult(UNKNOWN_ID, response -> ok.run(), err)));

        check("LIVE: sendEvaluatedFile posts the file id as a form field", () -> {
            AtomicReference<String> message = new AtomicReference<>("unexpectedly succeeded");
            zb.sendEvaluatedFile(UNKNOWN_ID, response -> message.set("unexpectedly succeeded"),
                    error -> {
                        String text = redact(error.getErrors().toString());
                        message.set(text.contains("not allowed") ? "wrong method or path: " + text : null);
                    });
            return message.get();
        });

        check("the free calls did not change the credit balance", () -> {
            AtomicReference<String> problem = new AtomicReference<>("no response");
            sdk().getCredits(response -> problem.set(
                            String.valueOf(before.get()).equals(response.getCredits())
                                    ? null
                                    : "credits went from " + before.get() + " to " + response.getCredits()),
                    error -> problem.set(redact(error.getErrors().toString())));
            return problem.get();
        });
    }

    // -- validation API, spends credits ---------------------------------------

    private static void validationSpend() {
        System.out.println("\nValidation API (ZEROBOUNCE_LIVE_SPEND=1)");
        ZeroBounceSDK zb = sdk();

        check("validate accepts every documented option", () -> run((ok, err) -> {
            // LIVE: activity_data, verify_plus and domain_info are billable
            // add-ons and are charged even for a sandbox address.
            ZBValidateOptions options = new ZBValidateOptions()
                    .setTimeout(30)
                    .setActivityData(true)
                    .setVerifyPlus(true)
                    .setDomainInfo(true)
                    .setGreylistProcessing(true)
                    .setCallbackUrl(CALLBACK);
            zb.validate(SANDBOX, null, options, response -> ok.run(), err);
        }));

        check("scoreEmail", () -> run((ok, err) ->
                zb.scoreEmail(SANDBOX, response -> ok.run(), err)));
        check("scoreEmail over POST", () -> run((ok, err) ->
                zb.scoreEmail(SANDBOX, true, response -> ok.run(), err)));
        check("getActivityData", () -> run((ok, err) ->
                zb.getActivityData(SANDBOX, response -> ok.run(), err)));
        check("email finder", () -> run((ok, err) ->
                zb.findEmail("John", "example.com", null, null, "Doe", response -> ok.run(), err)));
        check("domain search", () -> run((ok, err) ->
                zb.findDomain("example.com", null, response -> ok.run(), err)));

        AtomicReference<String> fileId = new AtomicReference<>(null);
        check("bulk validation round trip: upload, status, delete", () -> {
            File file = new File("zb-sdk-live.csv");
            try (FileWriter out = new FileWriter(file)) {
                out.write("email\nvalid@example.com\ninvalid@example.com\n");
            } catch (Exception e) {
                return "could not write the test file: " + e.getMessage();
            }
            AtomicReference<String> problem = new AtomicReference<>("no response");
            try {
                zb.sendFile(file, 1, null, response -> {
                    if (response.getFileId() == null || response.getFileId().isEmpty()) {
                        problem.set("no file_id in the upload response");
                        return;
                    }
                    fileId.set(response.getFileId());
                    problem.set(null);
                }, error -> problem.set(redact(error.getErrors().toString())));
            } catch (com.zerobounce.ZBException e) {
                problem.set(redact(e.getMessage()));
            }
            file.delete();
            if (problem.get() != null) {
                return problem.get();
            }
            problem.set("no status response");
            zb.fileStatus(fileId.get(), status -> problem.set(
                            fileId.get().equals(status.getFileId()) ? null : "status did not echo the file id"),
                    error -> problem.set(redact(error.getErrors().toString())));
            return problem.get();
        });

        if (fileId.get() == null) {
            skip("the uploaded validation job is deleted again", "upload did not produce a file id");
        } else {
            check("the uploaded validation job is deleted again", () -> {
                // LIVE: a just-accepted job answers "File cannot be found" for
                // a short while, so the delete is retried rather than leaving
                // the job behind.
                AtomicReference<String> last = new AtomicReference<>("");
                for (int attempt = 0; attempt < 12; attempt++) {
                    AtomicReference<Boolean> done = new AtomicReference<>(false);
                    zb.deleteFile(fileId.get(), response -> done.set(true),
                            error -> last.set(redact(error.getErrors().toString())));
                    if (done.get()) {
                        return null;
                    }
                    try {
                        Thread.sleep(10_000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                return "could not delete " + fileId.get() + " after retrying: " + last.get();
            });
        }
    }

    // -- validation API, reversible account changes ---------------------------

    private static void validationChanges() {
        System.out.println("\nValidation API (ZEROBOUNCE_LIVE_CHANGES=1)");
        ZeroBounceSDK zb = sdk();
        String value = "zb-sdk-live-" + System.currentTimeMillis() + ".example.com";
        AtomicReference<Boolean> added = new AtomicReference<>(false);

        check("addFilter creates a reversible allow rule", () -> run((ok, err) ->
                zb.addFilter("allow", "domain", value, response -> {
                    added.set(true);
                    ok.run();
                }, err)));

        if (!added.get()) {
            skip("the test filter is removed again", "filter was not created");
            return;
        }
        check("the test filter is removed again", () -> run((ok, err) ->
                zb.deleteFilter("allow", "domain", value, response -> ok.run(), err)));
    }

    // -- Warmup API -----------------------------------------------------------

    private static ZeroBounceWarmup warmupClient() {
        ZeroBounceWarmup zb = ZeroBounceWarmup.getInstance();
        zb.initialize(warmupKey());
        return zb;
    }

    private static void warmupFree() {
        System.out.println("\nWarmup API (reads)");
        ZeroBounceWarmup zb = warmupClient();

        check("listSendingAccounts", () -> run((ok, err) ->
                zb.listSendingAccounts(response -> ok.run(), err)));
        check("listFromAddresses", () -> run((ok, err) ->
                zb.listFromAddresses(null, null, null, response -> ok.run(), err)));
        check("getEngagementRules", () -> run((ok, err) ->
                zb.getEngagementRules(response -> ok.run(), err)));
        check("getSeeds", () -> run((ok, err) -> zb.getSeeds(response -> ok.run(), err)));
        check("getSeedCount", () -> run((ok, err) -> zb.getSeedCount(response -> ok.run(), err)));
        check("getProgressToday", () -> run((ok, err) ->
                zb.getProgressToday(response -> ok.run(), err)));
        check("getSentToday", () -> run((ok, err) -> zb.getSentToday(response -> ok.run(), err)));
        check("getEmailsPerDay", () -> run((ok, err) ->
                zb.getEmailsPerDay(response -> ok.run(), err)));

        check("downloadSeeds returns CSV text", () -> {
            AtomicReference<String> problem = new AtomicReference<>("no response");
            zb.downloadSeeds(csv -> problem.set(
                            csv != null && !csv.trim().startsWith("{") ? null : "got JSON instead of CSV"),
                    error -> problem.set(redact(error.getErrors().toString())));
            return problem.get();
        });

        check("LIVE: checkDomainDmarc rejects a bare domain before sending", () -> {
            AtomicReference<String> message = new AtomicReference<>("unexpectedly succeeded");
            zb.checkDomainDmarc("example.com", null,
                    response -> message.set("unexpectedly succeeded"),
                    error -> {
                        String text = error.getErrors().toString();
                        message.set(text.contains("email address") ? null : "unexpected message: " + text);
                    });
            return message.get();
        });

        check("LIVE: checkDomainDmarc accepts an email address", () -> run((ok, err) ->
                zb.checkDomainDmarc("sender@example.com", null, response -> ok.run(), err)));

        // The statistics endpoints need a from-address this team actually has;
        // the API answers "Sending account ... was not found" for anything else.
        AtomicReference<String> known = new AtomicReference<>(null);
        zb.listSendingAccounts(response -> {
            if (response == null) {
                return;
            }
            JsonElement list = response.has("data") ? response.get("data") : response;
            if (!list.isJsonArray() || list.getAsJsonArray().isEmpty()) {
                return;
            }
            JsonObject first = list.getAsJsonArray().get(0).getAsJsonObject();
            for (String field : new String[]{"from_address", "c_from", "fromAddress"}) {
                if (first.has(field) && first.get(field).isJsonPrimitive()) {
                    known.set(first.get(field).getAsString());
                    return;
                }
            }
        }, error -> {
        });

        if (known.get() == null) {
            for (String name : new String[]{
                    "statistics for a registered from-address",
                    "statistics per day over a short range",
                    "detailed statistics and the CSV download for that address",
                    "from-address statistics"}) {
                skip(name, "this team has no sending account");
            }
        } else {
            String address = known.get();
            check("statistics for a registered from-address", () -> run((ok, err) ->
                    zb.getStatistics(address, response -> ok.run(), err)));
            check("statistics per day over a short range", () -> run((ok, err) ->
                    zb.getStatisticsPerDay(address,
                            LocalDate.now().minusDays(6).toString(), LocalDate.now().toString(),
                            response -> ok.run(), err)));
            check("detailed statistics and the CSV download for that address", () -> {
                String problem = run((ok, err) ->
                        zb.getStatisticsDetailed(address, response -> ok.run(), err));
                if (problem != null) {
                    return problem;
                }
                return run((ok, err) -> zb.downloadStatistics(address, csv -> ok.run(), err));
            });
            check("from-address statistics", () -> run((ok, err) ->
                    zb.getFromAddressStatistics(address, response -> ok.run(), err)));
        }

        check("an unknown Warmup resource fails with a status code", () -> {
            AtomicReference<String> problem = new AtomicReference<>(null);
            zb.getCustomEngagement("no-such-address@example.com", response -> {
            }, error -> {
                if (error.getStatusCode() == 0) {
                    problem.set("no status code on the error");
                } else if (error.getErrors().toString().contains(warmupKey())) {
                    problem.set("the key leaked into the message");
                }
            });
            return problem.get();
        });
    }

    private static void warmupChanges() {
        System.out.println("\nWarmup API (ZEROBOUNCE_LIVE_CHANGES=1)");
        ZeroBounceWarmup zb = warmupClient();
        String address = "zb-sdk-live-" + System.currentTimeMillis() + "@example.com";
        AtomicReference<Boolean> added = new AtomicReference<>(false);

        check("a self-managed sending account can be added", () -> run((ok, err) ->
                zb.addSendingAccount(address, "self_managed",
                        new ZeroBounceWarmup.AddSendingAccountOptions()
                                .setEngagementRuleId(0)
                                .setIgnoreConflict(true),
                        response -> {
                            added.set(true);
                            ok.run();
                        }, err)));

        if (!added.get()) {
            skip("the test sending account is removed again", "account was not created");
            return;
        }
        check("the test sending account is removed again", () -> run((ok, err) ->
                zb.deleteSendingAccount(address, response -> ok.run(), err)));
    }

    private LiveApi() {
    }
}

package com.zerobounce;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The public request methods of {@link ZeroBounceSDK} and {@link ZeroBounceWarmup},
 * found by reflection (every public instance method that takes an
 * {@link ZeroBounceSDK.OnErrorCallback}), with arguments that make each one send a
 * request. The sweeps built on it are complete by construction:
 * {@link #assertComplete()} fails when a public request method has no arguments
 * here, or when an entry no longer matches a method, so a new request path cannot
 * skip a sweep.
 */
final class RequestSweep {

    /**
     * The SMTP password the sweep sends to testSmtpConfiguration. JSON escaping
     * changes its quote, slash and backslash, Gson's HTML-safe escaping its
     * {@code = < > & '}, and URL encoding all of those, its {@code +} and
     * {@code %}, and its space (as {@code +} or {@code %20}), so every spelling
     * of it differs.
     */
    static final String SMTP_PASSWORD = "smtp\"pa/ss w=<>&'\\+%";

    private final Path tempDir;
    private final AtomicInteger files = new AtomicInteger();
    /** The download path handed to the most recent call, or null. */
    String lastOut;
    /** When set, each download path already holds a file with this text before the call. */
    String existingOut;

    /** Signature, as {@link #signature(Method)} prints it, to the arguments before the callbacks. */
    final Map<String, Supplier<Object[]>> sdkArgs = new LinkedHashMap<>();
    final Map<String, Supplier<Object[]>> warmupArgs = new LinkedHashMap<>();

    RequestSweep(Path tempDir) {
        this.tempDir = tempDir;
        declareSdkArgs();
        declareWarmupArgs();
    }

    private File csv() {
        try {
            File file = tempDir.resolve("in-" + files.incrementAndGet() + ".csv").toFile();
            Files.writeString(file.toPath(), "email\na@example.com\n");
            return file;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static ByteArrayInputStream stream() {
        return new ByteArrayInputStream("email\na@example.com\n".getBytes(StandardCharsets.UTF_8));
    }

    private String out() {
        lastOut = tempDir.resolve("out-" + files.incrementAndGet() + ".csv").toString();
        if (existingOut != null) {
            try {
                Files.writeString(Path.of(lastOut), existingOut);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
        return lastOut;
    }

    private static ZBBulkFileJobOptions job() {
        return new ZBBulkFileJobOptions().setFileName("a.csv").setReturnUrl("https://example.com/done");
    }

    private void args(String signature, Supplier<Object[]> args) {
        sdkArgs.put(signature, args);
    }

    private void warmup(String signature, Supplier<Object[]> args) {
        warmupArgs.put(signature, args);
    }

    private void declareSdkArgs() {
        String email = "a@example.com";
        args("validate(String,String)", () -> new Object[]{email, null});
        args("validate(String,String,ZBValidateOptions)", () -> new Object[]{email, null, new ZBValidateOptions()});
        args("validateWithOptions(String,ZBValidateRequestOptions)", () -> new Object[]{email, null});
        args("validateBatch(List)", () -> new Object[]{List.of(new ZBValidateBatchData(email, null))});
        args("validateBatch(List,ZBValidateOptions)", () -> new Object[]{List.of(new ZBValidateBatchData(email, null)), null});
        args("validateBatchWithOptions(List,ZBValidateBatchOptions)", () -> new Object[]{List.of(new ZBValidateBatchData(email, null)), null});
        args("guessFormat(String,String,String,String)", () -> new Object[]{"example.com", "Ann", null, null});
        args("findEmail(String,String,String,String,String)", () -> new Object[]{"Ann", "example.com", null, null, null});
        args("findDomain(String,String)", () -> new Object[]{"example.com", null});
        args("getApiUsage(Date,Date)", () -> new Object[]{new Date(0), new Date(86_400_000L)});
        args("getCredits()", () -> new Object[]{});
        args("getActivityData(String)", () -> new Object[]{email});
        args("scoreEmail(String)", () -> new Object[]{email});
        args("scoreEmail(String,boolean)", () -> new Object[]{email, true});
        args("scoreEmailPost(String)", () -> new Object[]{email});
        // bulk validation and scoring
        args("sendFile(File,int,SendFileOptions)", () -> new Object[]{csv(), 1, null});
        args("sendFile(InputStream,String,int,SendFileOptions)", () -> new Object[]{stream(), "in.csv", 1, null});
        args("fileStatus(String)", () -> new Object[]{"fid"});
        args("getFile(String,String)", () -> new Object[]{"fid", out()});
        args("getFile(String,String,ZBGetFileOptions)", () -> new Object[]{"fid", out(), new ZBGetFileOptions()});
        args("deleteFile(String)", () -> new Object[]{"fid"});
        args("scoringSendFile(File,int,ScoringSendFileOptions)", () -> new Object[]{csv(), 1, null});
        args("scoringSendFile(InputStream,String,int,ScoringSendFileOptions)", () -> new Object[]{stream(), "in.csv", 1, null});
        args("scoringFileStatus(String)", () -> new Object[]{"fid"});
        args("scoringGetFile(String,String)", () -> new Object[]{"fid", out()});
        args("scoringGetFile(String,String,ZBGetFileOptions)", () -> new Object[]{"fid", out(), new ZBGetFileOptions()});
        args("scoringDeleteFile(String)", () -> new Object[]{"fid"});
        // email finder and domain search files
        args("sendEmailFinderFile(File,int,EmailFinderSendFileOptions)", () -> new Object[]{
                csv(), 1, new ZeroBounceSDK.EmailFinderSendFileOptions().setFirstNameColumn(2)});
        args("sendEmailFinderFileStream(InputStream,String,int,EmailFinderSendFileOptions)", () -> new Object[]{
                stream(), "in.csv", 1, new ZeroBounceSDK.EmailFinderSendFileOptions().setFirstNameColumn(2)});
        args("emailFinderFileStatus(String)", () -> new Object[]{"fid"});
        args("emailFinderGetFile(String,String)", () -> new Object[]{"fid", out()});
        args("emailFinderDeleteFile(String)", () -> new Object[]{"fid"});
        args("sendDomainSearchFile(File,int,DomainSearchSendFileOptions)", () -> new Object[]{csv(), 1, null});
        args("sendDomainSearchFileStream(InputStream,String,int,DomainSearchSendFileOptions)", () -> new Object[]{stream(), "in.csv", 1, null});
        args("domainSearchFileStatus(String)", () -> new Object[]{"fid"});
        args("domainSearchGetFile(String,String)", () -> new Object[]{"fid", out()});
        args("domainSearchDeleteFile(String)", () -> new Object[]{"fid"});
        // list evaluator
        args("sendListEvaluatorFile(File,int)", () -> new Object[]{csv(), 1});
        args("sendListEvaluatorFileStream(InputStream,String,int)", () -> new Object[]{stream(), "in.csv", 1});
        args("getListEvaluatorResult(String)", () -> new Object[]{"fid"});
        args("sendEvaluatedFile(String)", () -> new Object[]{"fid"});
        // filters
        args("addFilter(String,String,String)", () -> new Object[]{"allow", "email", email});
        args("deleteFilter(String,String,String)", () -> new Object[]{"allow", "email", email});
        args("listFilters(boolean)", () -> new Object[]{false});
        // S3 and SFTP jobs
        args("listS3Files(String,String)", () -> new Object[]{"bucket", null});
        args("validateS3File(ValidateS3FileOptions)", () -> new Object[]{
                new ZeroBounceSDK.ValidateS3FileOptions().setFileName("a.csv").setReturnUrl("https://example.com/done")});
        args("scoreS3File(ScoreS3FileOptions)", () -> new Object[]{
                new ZeroBounceSDK.ScoreS3FileOptions().setFileName("a.csv").setReturnUrl("https://example.com/done")});
        args("validateS3File(ZBBulkFileJobOptions)", () -> new Object[]{job()});
        args("scoreS3File(ZBBulkFileJobOptions)", () -> new Object[]{job()});
        args("emailFinderS3File(ZBBulkFileJobOptions)", () -> new Object[]{job()});
        args("domainSearchS3File(ZBBulkFileJobOptions)", () -> new Object[]{job()});
        args("exportS3File(String,String)", () -> new Object[]{"fid", "bucket"});
        args("validateSFTPFile(ZBBulkFileJobOptions)", () -> new Object[]{job()});
        args("scoreSFTPFile(ZBBulkFileJobOptions)", () -> new Object[]{job()});
        args("emailFinderSFTPFile(ZBBulkFileJobOptions)", () -> new Object[]{job()});
        args("domainSearchSFTPFile(ZBBulkFileJobOptions)", () -> new Object[]{job()});
        args("exportSFTPFile(String,String)", () -> new Object[]{"fid", "/out"});
        // Inbox Placement Tester
        args("getInboxPlacementProviders()", () -> new Object[]{});
        args("getInboxPlacementTestTypes()", () -> new Object[]{});
        args("createInboxPlacementTest(String,CreateInboxPlacementTestOptions)", () -> new Object[]{"Launch", null});
        args("getInboxPlacementTest(String)", () -> new Object[]{"t1"});
        args("listInboxPlacementTests(ListInboxPlacementTestsOptions)", () -> new Object[]{null});
        args("deleteInboxPlacementTest(String)", () -> new Object[]{"t1"});
        args("getInboxPlacementWebhook()", () -> new Object[]{});
        args("setInboxPlacementWebhook(String)", () -> new Object[]{"https://example.com/ipt"});
    }

    private void declareWarmupArgs() {
        String from = "sender@example.com";
        warmup("listSendingAccounts()", () -> new Object[]{});
        warmup("getSendingAccount(String)", () -> new Object[]{from});
        warmup("addSendingAccount(String,String,AddSendingAccountOptions)", () -> new Object[]{from, "zb_managed", null});
        warmup("updateSendingAccount(String,int,int,boolean)", () -> new Object[]{from, 0, 4, true});
        warmup("deleteSendingAccount(String)", () -> new Object[]{from});
        warmup("changeFromAddress(String,String)", () -> new Object[]{from, "new@example.com"});
        warmup("listFromAddresses(String,Integer,Integer)", () -> new Object[]{null, null, null});
        warmup("getEngagementRules()", () -> new Object[]{});
        warmup("setEngagementRule(String,int)", () -> new Object[]{from, 1});
        warmup("getCustomEngagement(String)", () -> new Object[]{from});
        warmup("createCustomEngagement(String,int,int,int)", () -> new Object[]{from, 10, 10, 10});
        warmup("updateCustomEngagement(String,int,int,int)", () -> new Object[]{from, 10, 10, 10});
        warmup("deleteCustomEngagement(String,int)", () -> new Object[]{from, 1});
        warmup("getSeeds()", () -> new Object[]{});
        warmup("downloadSeeds()", () -> new Object[]{});
        warmup("getSeedCount()", () -> new Object[]{});
        warmup("getProgressToday()", () -> new Object[]{});
        warmup("getSentToday()", () -> new Object[]{});
        warmup("getEmailsPerDay()", () -> new Object[]{});
        warmup("getStatistics(String)", () -> new Object[]{from});
        warmup("getStatisticsPerDay(String,String,String)", () -> new Object[]{from, "2026-01-01", "2026-01-31"});
        warmup("getStatisticsDetailed(String)", () -> new Object[]{from});
        warmup("downloadStatistics(String)", () -> new Object[]{from});
        warmup("getFromAddressStatistics(String)", () -> new Object[]{from});
        warmup("checkDomainDmarc(String,String)", () -> new Object[]{from, null});
        warmup("testSmtpConfiguration(String,String,String,String,int)", () -> new Object[]{
                from, "smtp-user", SMTP_PASSWORD, "smtp.example.com", 587});
    }

    private static boolean isCallback(Class<?> type) {
        return type == ZeroBounceSDK.OnSuccessCallback.class || type == ZeroBounceSDK.OnErrorCallback.class;
    }

    /** The public request methods of a client: public instance methods that take an error callback. */
    static List<Method> requestMethods(Class<?> client) {
        return Arrays.stream(client.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()) && !Modifier.isStatic(m.getModifiers()))
                .filter(m -> !m.isSynthetic() && !m.isBridge())
                .filter(m -> Arrays.asList(m.getParameterTypes()).contains(ZeroBounceSDK.OnErrorCallback.class))
                .sorted(java.util.Comparator.comparing(RequestSweep::signature))
                .collect(Collectors.toList());
    }

    /** {@code name(Type,Type)} with simple type names, callbacks left out. */
    static String signature(Method method) {
        return method.getName() + Arrays.stream(method.getParameterTypes())
                .filter(type -> !isCallback(type))
                .map(Class::getSimpleName)
                .collect(Collectors.joining(",", "(", ")"));
    }

    private Map<String, Supplier<Object[]>> argsFor(Class<?> client) {
        return client == ZeroBounceWarmup.class ? warmupArgs : sdkArgs;
    }

    /** Every public request method has arguments here, and every entry matches a method. */
    void assertComplete() {
        assertComplete(ZeroBounceSDK.class, 60);
        assertComplete(ZeroBounceWarmup.class, 25);
    }

    private void assertComplete(Class<?> client, int atLeast) {
        Map<String, Supplier<Object[]>> args = argsFor(client);
        TreeSet<String> reflected = requestMethods(client).stream()
                .map(RequestSweep::signature)
                .collect(Collectors.toCollection(TreeSet::new));
        String name = client.getSimpleName();
        assertTrue(reflected.size() >= atLeast,
                "found only " + reflected.size() + " " + name + " request methods: " + reflected);
        TreeSet<String> missing = new TreeSet<>(reflected);
        missing.removeAll(args.keySet());
        assertTrue(missing.isEmpty(), name + " public request methods missing from the sweep: " + missing);
        TreeSet<String> stale = new TreeSet<>(args.keySet());
        stale.removeAll(reflected);
        assertTrue(stale.isEmpty(), name + " sweep entries that match no public request method: " + stale);
    }

    /** What one swept call reported. */
    static final class Outcome {
        final String signature;
        final List<Object> successes = new ArrayList<>();
        final List<ErrorResponse> errors = new ArrayList<>();
        /** The download path handed to the call, or null when it downloads nothing. */
        String out;
        Throwable thrown;

        Outcome(String signature) {
            this.signature = signature;
        }

        @Override
        public String toString() {
            return signature + ": successes=" + successes + " errors="
                    + errors.stream().map(e -> e.getStatusCode() + " " + e.getErrors()).collect(Collectors.toList())
                    + (thrown == null ? "" : " threw " + thrown);
        }
    }

    /**
     * Calls one public request method on {@code client} with its sweep arguments
     * and callbacks that record what came back. A method missing from the sweep
     * is reported by {@link #assertComplete()}; here it is an error.
     */
    Outcome invoke(Object client, Method method) {
        String signature = signature(method);
        Supplier<Object[]> supplier = argsFor(method.getDeclaringClass()).get(signature);
        if (supplier == null) {
            throw new IllegalStateException(signature + " is not in the sweep");
        }
        Outcome outcome = new Outcome(signature);
        lastOut = null;
        Object[] before = supplier.get();
        outcome.out = lastOut;
        Class<?>[] types = method.getParameterTypes();
        Object[] args = new Object[types.length];
        int next = 0;
        for (int i = 0; i < types.length; i++) {
            if (types[i] == ZeroBounceSDK.OnSuccessCallback.class) {
                args[i] = (ZeroBounceSDK.OnSuccessCallback<Object>) outcome.successes::add;
            } else if (types[i] == ZeroBounceSDK.OnErrorCallback.class) {
                args[i] = (ZeroBounceSDK.OnErrorCallback) outcome.errors::add;
            } else {
                args[i] = before[next++];
            }
        }
        assertEquals(before.length, next, signature + ": argument count");
        try {
            method.invoke(client, args);
        } catch (InvocationTargetException e) {
            outcome.thrown = e.getCause();
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
        return outcome;
    }

    /** Every public request method of the SDK, then of Warmup. */
    static List<Method> allRequestMethods() {
        List<Method> all = new ArrayList<>(requestMethods(ZeroBounceSDK.class));
        all.addAll(requestMethods(ZeroBounceWarmup.class));
        return all;
    }
}

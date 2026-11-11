package com.zerobounce;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every public request method sends over HTTPS, on the SDK's own default base URLs.
 * <p>
 * The methods are found by reflection (every public instance method of
 * {@link ZeroBounceSDK} that takes an {@link ZeroBounceSDK.OnErrorCallback}) and invoked
 * by reflection, overload by overload. {@link #ARGS} gives each one the arguments
 * before its callbacks; a public request method missing from it, or an entry that no
 * longer matches a method, fails {@link #everyPublicRequestMethodIsInTheSweep()}, so a
 * new request path cannot skip the sweep.
 */
class OfflineHttpsSweepTest {

    @TempDir
    Path tempDir;

    /** Signature, as {@link #signature(Method)} prints it, to the arguments before the callbacks. */
    private final Map<String, Supplier<Object[]>> ARGS = new LinkedHashMap<>();

    private File csv() {
        try {
            File file = tempDir.resolve("in.csv").toFile();
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
        return tempDir.resolve("out.csv").toString();
    }

    private static ZBBulkFileJobOptions job() {
        return new ZBBulkFileJobOptions().setFileName("a.csv").setReturnUrl("https://example.com/done");
    }

    private void args(String signature, Supplier<Object[]> args) {
        ARGS.put(signature, args);
    }

    private void declareArgs() {
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

    private static boolean isCallback(Class<?> type) {
        return type == ZeroBounceSDK.OnSuccessCallback.class || type == ZeroBounceSDK.OnErrorCallback.class;
    }

    /** The public request methods: public instance methods that take an error callback. */
    private static List<Method> requestMethods() {
        return Arrays.stream(ZeroBounceSDK.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()) && !Modifier.isStatic(m.getModifiers()))
                .filter(m -> !m.isSynthetic() && !m.isBridge())
                .filter(m -> Arrays.asList(m.getParameterTypes()).contains(ZeroBounceSDK.OnErrorCallback.class))
                .collect(Collectors.toList());
    }

    /** {@code name(Type,Type)} with simple type names, callbacks left out. */
    private static String signature(Method method) {
        return method.getName() + Arrays.stream(method.getParameterTypes())
                .filter(type -> !isCallback(type))
                .map(Class::getSimpleName)
                .collect(Collectors.joining(",", "(", ")"));
    }

    @Test
    void everyPublicRequestMethodIsInTheSweep() {
        declareArgs();
        TreeSet<String> reflected = requestMethods().stream()
                .map(OfflineHttpsSweepTest::signature)
                .collect(Collectors.toCollection(TreeSet::new));
        assertTrue(reflected.size() >= 60, "found only " + reflected.size() + " request methods: " + reflected);
        TreeSet<String> missing = new TreeSet<>(reflected);
        missing.removeAll(ARGS.keySet());
        assertTrue(missing.isEmpty(), "public request methods missing from the HTTPS sweep: " + missing);
        TreeSet<String> stale = new TreeSet<>(ARGS.keySet());
        stale.removeAll(reflected);
        assertTrue(stale.isEmpty(), "sweep entries that match no public request method: " + stale);
    }

    @Test
    void everyPublicRequestMethodSendsOverHttps() throws Exception {
        declareArgs();
        FakeApi api = new FakeApi();
        ZeroBounceSDK sdk = new ZeroBounceSDK();
        sdk.transport = api;
        sdk.initialize("https-sweep-key");

        List<String> failures = new ArrayList<>();
        int swept = 0;
        for (Method method : requestMethods()) {
            String signature = signature(method);
            Supplier<Object[]> supplier = ARGS.get(signature);
            if (supplier == null) {
                continue; // reported by everyPublicRequestMethodIsInTheSweep
            }
            Object[] before = supplier.get();
            Class<?>[] types = method.getParameterTypes();
            Object[] args = new Object[types.length];
            List<String> errors = new ArrayList<>();
            int next = 0;
            for (int i = 0; i < types.length; i++) {
                if (types[i] == ZeroBounceSDK.OnSuccessCallback.class) {
                    args[i] = (ZeroBounceSDK.OnSuccessCallback<Object>) response -> {
                    };
                } else if (types[i] == ZeroBounceSDK.OnErrorCallback.class) {
                    args[i] = (ZeroBounceSDK.OnErrorCallback) error -> errors.add(String.valueOf(error));
                } else {
                    args[i] = before[next++];
                }
            }
            assertEquals(before.length, next, signature + ": argument count");

            api.clear();
            try {
                method.invoke(sdk, args);
            } catch (InvocationTargetException e) {
                failures.add(signature + " threw " + e.getCause());
                continue;
            }
            List<FakeApi.RecordedRequest> requests = api.records();
            if (requests.isEmpty()) {
                failures.add(signature + " sent no request" + (errors.isEmpty() ? "" : ": " + errors));
            }
            for (FakeApi.RecordedRequest request : requests) {
                if (!request.url.startsWith("https://")) {
                    failures.add(signature + " sent over plain http: " + request.url);
                }
            }
            swept++;
        }
        assertTrue(failures.isEmpty(), String.join("\n", failures));
        assertFalse(swept == 0, "nothing was swept");
        assertEquals(ARGS.size(), swept, "every sweep entry was invoked");
    }
}

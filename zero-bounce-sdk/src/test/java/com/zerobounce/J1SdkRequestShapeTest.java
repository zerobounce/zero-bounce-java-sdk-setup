package com.zerobounce;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Request shape: validate omits an ip_address that is not set; listS3Files
 * never sends a blank bucket; allow_phase_2 is never sent on a scoring sendfile.
 */
class J1SdkRequestShapeTest {

    private static final byte[] EMPTY_LIST = "{\"success\": true, \"files\": []}".getBytes(StandardCharsets.UTF_8);

    private static String capture(java.util.function.Consumer<ZeroBounceSDK> call, List<String> urls) {
        ZeroBounceSDK sdk = new ZeroBounceSDK();
        sdk.transport = request -> {
            urls.add(request.url);
            return new SdkHttp.Result(200, "application/json", EMPTY_LIST);
        };
        sdk.initialize("shape-key");
        call.accept(sdk);
        assertEquals(1, urls.size(), "requests sent");
        return urls.get(0);
    }

    @Test
    void validateOmitsAnUnsetIpAddress() {
        for (String ip : new String[]{null, "", "   "}) {
            List<String> urls = new ArrayList<>();
            String url = capture(sdk -> sdk.validate("person@example.com", ip,
                    value -> { }, e -> { }), urls);
            assertFalse(url.contains("ip_address"), "ip " + ip + " -> " + url);
        }
    }

    @Test
    void validateSendsAnIpAddressThatIsSet() {
        List<String> urls = new ArrayList<>();
        String url = capture(sdk -> sdk.validate("person@example.com", "203.0.113.5",
                value -> { }, e -> { }), urls);
        assertTrue(url.contains("ip_address=203.0.113.5"), url);
    }

    @Test
    void aBlankS3BucketIsNeverSent() {
        for (String bucket : new String[]{null, "", "   ", "\t"}) {
            List<String> urls = new ArrayList<>();
            String url = capture(sdk -> sdk.listS3Files(bucket, null, value -> { }, e -> { }), urls);
            assertFalse(url.contains("bucket="), "bucket " + bucket + " -> " + url);
        }
        List<String> urls = new ArrayList<>();
        String url = capture(sdk -> sdk.listS3Files("my-bucket", null, value -> { }, e -> { }), urls);
        assertTrue(url.contains("bucket=my-bucket"), url);
    }

    @Test
    void allowPhase2IsNeverSentOnAScoringSendFile() throws Exception {
        ZeroBounceSDK sdk = new ZeroBounceSDK();
        Method fields = ZeroBounceSDK.class.getDeclaredMethod("sendFileMultipartFields",
                boolean.class, int.class, ZeroBounceSDK.SendFileOptions.class, String.class);
        fields.setAccessible(true);
        ZeroBounceSDK.SendFileOptions options = new ZeroBounceSDK.SendFileOptions().setAllowPhase2(true);

        @SuppressWarnings("unchecked")
        Map<String, String> scoring = (Map<String, String>) fields.invoke(sdk, true, 1, options, "a.csv");
        assertFalse(scoring.containsKey("allow_phase_2"), scoring.toString());

        @SuppressWarnings("unchecked")
        Map<String, String> validation = (Map<String, String>) fields.invoke(sdk, false, 1, options, "a.csv");
        assertEquals("true", validation.get("allow_phase_2"), validation.toString());
    }
}

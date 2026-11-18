package com.zerobounce;

import java.lang.management.ManagementFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CVE-2026-114938 (Java2Shell) is an out-of-bounds write in the JVM's x86-64
 * Base64 intrinsic. A patched runtime is safe. Any other runtime that still
 * has the intrinsic must have been started with {@code -XX:-UseBASE64Intrinsics}.
 */
class Java2ShellRuntimeTest {

    @Test
    void patchedBuildsAreRecognized() {
        assertTrue(runtimeIsPatched("21.0.13.1"));
        assertTrue(runtimeIsPatched("21.0.13.1.2"));
        assertTrue(runtimeIsPatched("21.0.14"));
        assertFalse(runtimeIsPatched("21.0.13"));
        assertFalse(runtimeIsPatched("21.0.13+9"));
        assertFalse(runtimeIsPatched("21.0.10"));
        assertTrue(runtimeIsPatched("17.0.5"));
        assertFalse(runtimeIsPatched("17.0.6"));
        assertTrue(runtimeIsPatched("17.0.21.1"));
        assertFalse(runtimeIsPatched("17.0.21"));
        assertTrue(runtimeIsPatched("11.0.33"));
        assertTrue(runtimeIsPatched("25.0.5.1"));
        assertFalse(runtimeIsPatched("25.0.5"));
        assertTrue(runtimeIsPatched("27.0.1.1"));
        assertFalse(runtimeIsPatched("27.0.1"));
    }

    @Test
    void thisJvmDisablesTheVulnerableDecoderOrIsPatched() {
        boolean intrinsicOff = ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
                .anyMatch("-XX:-UseBASE64Intrinsics"::equals);
        assertTrue(
                intrinsicOff || runtimeIsPatched(System.getProperty("java.version")),
                "CVE-2026-114938: this JVM is " + System.getProperty("java.version")
                        + ". Use 21.0.13.1 / 25.0.5.1 or newer, or start it with -XX:-UseBASE64Intrinsics.");
    }

    static boolean runtimeIsPatched(String version) {
        if (version == null || version.isBlank()) {
            return false;
        }
        String[] parts = version.split("\\+")[0].split("\\.");
        if (parts.length < 3) {
            return false;
        }
        int feature = number(parts[0]);
        int update = number(parts[2]);
        int patch = parts.length > 3 ? number(parts[3]) : 0;
        if (feature < 0 || update < 0 || patch < 0) {
            return false;
        }
        if (feature < 17) {
            return true;
        }
        return switch (feature) {
            case 17 -> update < 6 || update > 21 || (update == 21 && patch >= 1);
            case 21 -> update > 13 || (update == 13 && patch >= 1);
            case 25 -> update > 5 || (update == 5 && patch >= 1);
            case 27 -> update > 1 || (update == 1 && patch >= 1);
            default -> false;
        };
    }

    private static int number(String text) {
        if (text.isEmpty() || !text.chars().allMatch(Character::isDigit)) {
            return -1;
        }
        return Integer.parseInt(text);
    }
}

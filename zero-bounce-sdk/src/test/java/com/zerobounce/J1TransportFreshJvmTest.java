package com.zerobounce;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Finding S (guard vs SLF4J initialisation): in a fresh JVM, the first concurrent
 * calls must not be refused while SLF4J binds its loggers. The suite's own JVM has
 * SLF4J initialised long before, so the calls run in a child JVM. Nothing is stubbed:
 * the child makes real calls to a loopback server.
 */
class J1TransportFreshJvmTest {

    @Test
    void firstConcurrentCallsInAFreshJvmAllSucceedAtInfo() throws Exception {
        String java = Paths.get(System.getProperty("java.home"), "bin", "java").toString();
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        Process child = new ProcessBuilder(java, "-cp", classpath, J1TransportFreshJvm.class.getName())
                .redirectErrorStream(true)
                .start();
        List<String> lines;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(child.getInputStream(), StandardCharsets.UTF_8))) {
            lines = reader.lines().toList();
        }
        assertTrue(child.waitFor(120, TimeUnit.SECONDS), "child JVM did not finish");
        assertEquals(0, child.exitValue(), String.join("\n", lines));
        assertTrue(lines.contains("SUMMARY ok=16 failed=0 requests=16"), String.join("\n", lines));
    }
}

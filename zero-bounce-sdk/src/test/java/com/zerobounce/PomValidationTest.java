package com.zerobounce;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validates POM plugin/dependency versions that are easy to misversion (e.g. copy-paste).
 * Fails during {@code mvn test} so issues are caught locally before CI.
 */
class PomValidationTest {

    private static final Pattern JACOCO_VERSION = Pattern.compile("0\\.8\\.\\d+");

    @Test
    void jacocoMavenPluginVersionMustBe08x() throws Exception {
        File pom = findPom();
        assertTrue(Files.isRegularFile(pom.toPath()), "pom.xml not found at " + pom.getAbsolutePath());

        String content = Files.readString(pom.toPath());
        // Find jacoco-maven-plugin and its version (next <version> after artifactId jacoco-maven-plugin)
        int pluginIdx = content.indexOf("<artifactId>jacoco-maven-plugin</artifactId>");
        assertTrue(pluginIdx > 0, "jacoco-maven-plugin not found in pom.xml");

        int versionStart = content.indexOf("<version>", pluginIdx);
        assertTrue(versionStart > pluginIdx, "version not found for jacoco-maven-plugin");
        versionStart += "<version>".length();
        int versionEnd = content.indexOf("</version>", versionStart);
        assertTrue(versionEnd > versionStart, "version end tag not found");
        String version = content.substring(versionStart, versionEnd).trim();

        assertTrue(
                JACOCO_VERSION.matcher(version).matches(),
                "jacoco-maven-plugin version must be 0.8.x (e.g. 0.8.14). Found: " + version
                        + ". Version 2.0.10 does not exist on Maven Central."
        );
    }

    private static File findPom() {
        Path dir = Path.of(System.getProperty("user.dir", "."));
        File pom = dir.resolve("pom.xml").toFile();
        if (pom.isFile()) {
            return pom;
        }
        // Fallback for IDE runs that might use module root differently
        Path parent = dir.getParent();
        if (parent != null) {
            File parentPom = parent.resolve("pom.xml").toFile();
            if (parentPom.isFile()) {
                return parentPom;
            }
        }
        return pom;
    }
}

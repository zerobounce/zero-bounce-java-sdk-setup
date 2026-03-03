package com.zerobounce;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validates POM plugin/dependency versions that are easy to misversion (e.g. copy-paste).
 * Fails during {@code mvn test} so issues are caught locally before CI.
 * (Pattern from Android SDK: project/publish version is separate from plugin/dependency versions.)
 */
class PomValidationTest {

    private static final Pattern JACOCO_VERSION = Pattern.compile("0\\.8\\.\\d+");

    @Test
    void noPluginOrDependencyVersionMustEqualProjectVersion() throws Exception {
        File pom = findPom();
        assertTrue(Files.isRegularFile(pom.toPath()), "pom.xml not found at " + pom.getAbsolutePath());
        String content = Files.readString(pom.toPath());

        // Project version: root <version> (may be ${sdk.version}); resolve from properties if needed.
        Matcher rootVersion = Pattern.compile("<version>([^<]+)</version>").matcher(content);
        assertTrue(rootVersion.find(), "No <version> found in pom.xml");
        String projectVersion = rootVersion.group(1).trim();
        if (projectVersion.startsWith("${") && projectVersion.endsWith("}")) {
            String propName = projectVersion.substring(2, projectVersion.length() - 1);
            Matcher prop = Pattern.compile("<" + Pattern.quote(propName) + ">([^<]+)</" + Pattern.quote(propName) + ">").matcher(content);
            assertTrue(prop.find(), "Property " + propName + " not found in pom.xml");
            projectVersion = prop.group(1).trim();
        }

        // Collect any <version> (other than root) that equals the project version (copy-paste mistake).
        List<String> mistaken = new ArrayList<>();
        Matcher allVersions = Pattern.compile("<version>([^<]+)</version>").matcher(content);
        boolean first = true;
        while (allVersions.find()) {
            String v = allVersions.group(1).trim();
            if (first) {
                first = false;
                continue; // skip root version
            }
            if (projectVersion.equals(v)) {
                mistaken.add(v);
            }
        }
        assertTrue(
                mistaken.isEmpty(),
                "Plugin/dependency version must not equal project version (" + projectVersion + "). "
                        + "That copy-paste causes PluginResolutionException (e.g. maven-surefire-plugin/jacoco have no such version on Maven Central). "
                        + "Found " + mistaken.size() + " occurrence(s). Use explicit versions per artifact; only bump <sdk.version> in <properties>."
        );
    }

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

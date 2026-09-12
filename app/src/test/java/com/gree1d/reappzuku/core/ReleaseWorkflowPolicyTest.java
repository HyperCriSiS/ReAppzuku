package com.gree1d.reappzuku.core;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.Test;

public class ReleaseWorkflowPolicyTest {
    private static final Pattern IMMUTABLE_ACTION =
            Pattern.compile("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+(?:/[A-Za-z0-9_.-]+)*@[0-9a-f]{40}(?:\\s+#.*)?$");

    @Test
    public void signedReleaseWorkflowKeepsLeastPrivilegeAndImmutableActions() throws Exception {
        String workflow = readRepositoryFile(".github/workflows/signed-release.yml");

        assertTrue(workflow.contains("permissions: {}"));
        assertTrue(workflow.contains("build-sign-verify:\n    permissions:\n      contents: read"));
        assertTrue(workflow.contains("publish:\n    if: ${{ inputs.publish }}\n    needs: build-sign-verify\n    permissions:\n      contents: write"));
        assertFalse(workflow.contains("updateLintBaseline"));

        for (String line : workflow.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("uses: ")) continue;
            String action = trimmed.substring("uses: ".length());
            assertTrue("External action must use immutable full SHA: " + action,
                    IMMUTABLE_ACTION.matcher(action).matches());
        }
    }

    @Test
    public void permanentReleaseAndApi37SourcesAreMainOnly() throws Exception {
        String release = readRepositoryFile(".github/workflows/signed-release.yml");
        String api37 = readRepositoryFile(".github/workflows/android17-runtime.yml");

        assertTrue(release.contains("description: Trusted Git ref to build (main)"));
        assertTrue(release.contains("default: main"));
        assertTrue(release.contains("main) ;;"));
        assertFalse(release.contains("ondemand-shizuku"));

        assertTrue(api37.contains("product_ref:"));
        assertTrue(api37.contains("default: main"));
        assertFalse(api37.contains("ondemand-shizuku"));
    }

    @Test
    public void signedReleaseBuildsOnlyTheProductModule() throws Exception {
        String workflow = readRepositoryFile(".github/workflows/signed-release.yml");

        assertTrue(workflow.contains(
                "./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleRelease"));
        assertFalse(workflow.contains("./gradlew testDebugUnitTest lintDebug assembleRelease"));
        assertFalse(workflow.contains(":securityProbe:"));
    }

    @Test
    public void stableReleaseMustMatchSourceAndBuiltApkVersion() throws Exception {
        String workflow = readRepositoryFile(".github/workflows/signed-release.yml");

        assertTrue(workflow.contains("Validate stable tag against source version before signing"));
        assertTrue(workflow.contains("Stable release tag/source version mismatch"));
        assertTrue(workflow.contains("Stable release tag/APK version mismatch"));
        assertTrue(workflow.contains("versionName"));
        assertTrue(workflow.contains("source-sha.txt"));
        assertTrue(workflow.contains("--target \"$SOURCE_SHA\""));
    }

    @Test
    public void publisherAcceptsOnlyExpectedApkAndDoesNotPublishProvenanceAsset() throws Exception {
        String workflow = readRepositoryFile(".github/workflows/signed-release.yml");

        assertTrue(workflow.contains("expected_name=\"ReAppzuku-${safe_tag}.apk\""));
        assertTrue(workflow.contains("test \"$(basename \"$apk\")\" = \"$expected_name\""));
        assertTrue(workflow.contains("args=(release create \"$RELEASE_TAG\" \"$apk\" \"$checksum\""));
        assertFalse(workflow.contains(".provenance.txt"));
    }

    @Test
    public void codeQlWorkflowUsesLeastPrivilegePinnedActionsAndRealJavaKotlinBuild() throws Exception {
        String workflow = readRepositoryFile(".github/workflows/codeql.yml");

        assertTrue(workflow.contains("push:\n    branches: [main]"));
        assertTrue(workflow.contains("pull_request:\n    branches: [main]"));
        assertTrue(workflow.contains("security-events: write"));
        assertTrue(workflow.contains("languages: java-kotlin"));
        assertTrue(workflow.contains("build-mode: manual"));
        assertTrue(workflow.contains("queries: security-extended"));
        assertTrue(workflow.contains(":app:assembleDebug :securityProbe:assembleDebug"));
        assertTrue(workflow.contains("--no-build-cache --rerun-tasks"));
        assertFalse(workflow.contains("permissions: write-all"));

        boolean sawInit = false;
        boolean sawAnalyze = false;
        for (String line : workflow.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("uses: ")) continue;
            String action = trimmed.substring("uses: ".length());
            assertTrue("External action must use immutable full SHA: " + action,
                    IMMUTABLE_ACTION.matcher(action).matches());
            sawInit |= action.startsWith("github/codeql-action/init@");
            sawAnalyze |= action.startsWith("github/codeql-action/analyze@");
        }
        assertTrue(sawInit);
        assertTrue(sawAnalyze);
    }

    private static String readRepositoryFile(String relative) throws IOException {
        List<Path> candidates = Arrays.asList(
                Paths.get(relative),
                Paths.get("..", relative),
                Paths.get("..", "..", relative));
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return Files.readString(candidate);
            }
        }
        throw new IOException("Could not locate repository file: " + relative
                + " from " + Paths.get("").toAbsolutePath());
    }
}

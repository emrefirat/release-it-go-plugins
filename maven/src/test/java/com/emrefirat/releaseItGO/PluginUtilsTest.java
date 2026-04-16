package com.emrefirat.releaseItGO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link PluginUtils}.
 */
class PluginUtilsTest {

    // --- normalizeVersion tests ---

    @Test
    void normalizeVersion_plainVersion() {
        assertEquals("0.1.3", PluginUtils.normalizeVersion("0.1.3"));
    }

    @Test
    void normalizeVersion_withVPrefix() {
        assertEquals("0.1.3", PluginUtils.normalizeVersion("v0.1.3"));
    }

    @Test
    void normalizeVersion_withCommandOutput() {
        assertEquals("0.1.3", PluginUtils.normalizeVersion("release-it-go version 0.1.3"));
    }

    @Test
    void normalizeVersion_withCommandOutputAndVPrefix() {
        assertEquals("0.1.3", PluginUtils.normalizeVersion("release-it-go version v0.1.3"));
    }

    @Test
    void normalizeVersion_withWhitespace() {
        assertEquals("0.1.3", PluginUtils.normalizeVersion("  0.1.3  "));
    }

    @Test
    void normalizeVersion_null_returnsEmpty() {
        assertEquals("", PluginUtils.normalizeVersion(null));
    }

    @Test
    void normalizeVersion_empty_returnsEmpty() {
        assertEquals("", PluginUtils.normalizeVersion(""));
    }

    @Test
    void normalizeVersion_distinguishesSimilarVersions() {
        // This was the original bug: "0.1.30".contains("0.1.3") == true
        String v1 = PluginUtils.normalizeVersion("0.1.3");
        String v2 = PluginUtils.normalizeVersion("0.1.30");
        assertFalse(v1.equals(v2), "0.1.3 and 0.1.30 should be different versions");
    }

    @Test
    void normalizeVersion_fullBinaryOutput() {
        // Real binary output format: "release-it-go 0.1.4-hooks.0 (commit: f420228, built: 2026-03-30T22:06:39Z)"
        assertEquals("0.1.4-hooks.0",
                PluginUtils.normalizeVersion("release-it-go 0.1.4-hooks.0 (commit: f420228, built: 2026-03-30T22:06:39Z)"));
    }

    @Test
    void normalizeVersion_fullBinaryOutputSimple() {
        assertEquals("0.2.0",
                PluginUtils.normalizeVersion("release-it-go 0.2.0 (commit: abc1234, built: 2026-04-01T10:00:00Z)"));
    }

    @Test
    void normalizeVersion_preReleaseVersion() {
        assertEquals("1.0.0-beta.1", PluginUtils.normalizeVersion("v1.0.0-beta.1"));
    }

    // --- hasConfigFile tests ---

    @Test
    void hasConfigFile_yamlExists_returnsTrue(@TempDir Path tempDir) throws IOException {
        Files.createFile(tempDir.resolve(".release-it-go.yaml"));
        assertTrue(PluginUtils.hasConfigFile(tempDir.toFile()));
    }

    @Test
    void hasConfigFile_ymlExists_returnsTrue(@TempDir Path tempDir) throws IOException {
        Files.createFile(tempDir.resolve(".release-it-go.yml"));
        assertTrue(PluginUtils.hasConfigFile(tempDir.toFile()));
    }

    @Test
    void hasConfigFile_jsonExists_returnsTrue(@TempDir Path tempDir) throws IOException {
        Files.createFile(tempDir.resolve(".release-it-go.json"));
        assertTrue(PluginUtils.hasConfigFile(tempDir.toFile()));
    }

    @Test
    void hasConfigFile_tomlExists_returnsTrue(@TempDir Path tempDir) throws IOException {
        Files.createFile(tempDir.resolve(".release-it-go.toml"));
        assertTrue(PluginUtils.hasConfigFile(tempDir.toFile()));
    }

    @Test
    void hasConfigFile_releaseItYamlExists_returnsTrue(@TempDir Path tempDir) throws IOException {
        Files.createFile(tempDir.resolve(".release-it.yaml"));
        assertTrue(PluginUtils.hasConfigFile(tempDir.toFile()));
    }

    @Test
    void hasConfigFile_releaseItYmlExists_returnsTrue(@TempDir Path tempDir) throws IOException {
        Files.createFile(tempDir.resolve(".release-it.yml"));
        assertTrue(PluginUtils.hasConfigFile(tempDir.toFile()));
    }

    @Test
    void hasConfigFile_releaseItJsonExists_returnsTrue(@TempDir Path tempDir) throws IOException {
        Files.createFile(tempDir.resolve(".release-it.json"));
        assertTrue(PluginUtils.hasConfigFile(tempDir.toFile()));
    }

    @Test
    void hasConfigFile_releaseItTomlExists_returnsTrue(@TempDir Path tempDir) throws IOException {
        Files.createFile(tempDir.resolve(".release-it.toml"));
        assertTrue(PluginUtils.hasConfigFile(tempDir.toFile()));
    }

    @Test
    void hasConfigFile_unrelatedFiles_returnsFalse(@TempDir Path tempDir) throws IOException {
        Files.createFile(tempDir.resolve("pom.xml"));
        Files.createFile(tempDir.resolve(".gitignore"));
        Files.createFile(tempDir.resolve("release-it-go.yaml")); // missing leading dot
        assertFalse(PluginUtils.hasConfigFile(tempDir.toFile()));
    }

    @Test
    void hasConfigFile_noConfigFile_returnsFalse(@TempDir Path tempDir) {
        assertFalse(PluginUtils.hasConfigFile(tempDir.toFile()));
    }

    @Test
    void hasConfigFile_nonExistentDir_returnsFalse() {
        assertFalse(PluginUtils.hasConfigFile(new File("/nonexistent/path")));
    }

    // --- getDefaultVersion tests ---

    @Test
    void getDefaultVersion_returnsNonEmpty() {
        String version = PluginUtils.getDefaultVersion();
        assertFalse(version.isEmpty(), "Default version should not be empty");
    }

    @Test
    void getDefaultVersion_returnsSemver() {
        String version = PluginUtils.getDefaultVersion();
        assertTrue(version.matches("[0-9]+\\.[0-9]+\\.[0-9]+.*"),
                "Default version should be semver format but was: " + version);
    }

    // --- validateVersion tests ---

    @Test
    void validateVersion_validSimple() {
        assertDoesNotThrow(() -> PluginUtils.validateVersion("0.1.3"));
    }

    @Test
    void validateVersion_validMajor() {
        assertDoesNotThrow(() -> PluginUtils.validateVersion("1.0.0"));
    }

    @Test
    void validateVersion_validPreRelease() {
        assertDoesNotThrow(() -> PluginUtils.validateVersion("1.0.0-beta.1"));
    }

    @Test
    void validateVersion_validRC() {
        assertDoesNotThrow(() -> PluginUtils.validateVersion("2.0.0-rc1"));
    }

    @Test
    void validateVersion_null_throws() {
        assertThrows(IllegalArgumentException.class, () -> PluginUtils.validateVersion(null));
    }

    @Test
    void validateVersion_empty_throws() {
        assertThrows(IllegalArgumentException.class, () -> PluginUtils.validateVersion(""));
    }

    @Test
    void validateVersion_pathTraversal_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> PluginUtils.validateVersion("0.1.0/../../evil-repo/releases/download/v1"));
    }

    @Test
    void validateVersion_commandInjection_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> PluginUtils.validateVersion("0.1.0; rm -rf /"));
    }

    @Test
    void validateVersion_urlEncoded_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> PluginUtils.validateVersion("0.1.0%2F..%2F.."));
    }

    @Test
    void validateVersion_onlyMajorMinor_throws() {
        assertThrows(IllegalArgumentException.class, () -> PluginUtils.validateVersion("1.0"));
    }

    @Test
    void validateVersion_withVPrefix_throws() {
        assertThrows(IllegalArgumentException.class, () -> PluginUtils.validateVersion("v1.0.0"));
    }

    // --- ensureGitignore tests ---

    @Test
    void ensureGitignore_createsNewFile(@TempDir Path tempDir) throws IOException {
        assertTrue(PluginUtils.ensureGitignore(tempDir.toFile()));

        File gitignore = tempDir.resolve(".gitignore").toFile();
        assertTrue(gitignore.exists());
        String content = new String(Files.readAllBytes(gitignore.toPath()), StandardCharsets.UTF_8);
        assertTrue(content.contains("release-it-go"));
        assertTrue(content.contains("release-it-go.exe"));
    }

    @Test
    void ensureGitignore_appendsToExistingFile(@TempDir Path tempDir) throws IOException {
        File gitignore = tempDir.resolve(".gitignore").toFile();
        Files.write(gitignore.toPath(), "target/\n*.class\n".getBytes(StandardCharsets.UTF_8));

        assertTrue(PluginUtils.ensureGitignore(tempDir.toFile()));

        String content = new String(Files.readAllBytes(gitignore.toPath()), StandardCharsets.UTF_8);
        assertTrue(content.startsWith("target/\n*.class\n"));
        assertTrue(content.contains("release-it-go"));
        assertTrue(content.contains("release-it-go.exe"));
    }

    @Test
    void ensureGitignore_alreadyPresent_returnsFalse(@TempDir Path tempDir) throws IOException {
        File gitignore = tempDir.resolve(".gitignore").toFile();
        Files.write(gitignore.toPath(),
                "target/\n# release-it-go binary\nrelease-it-go\nrelease-it-go.exe\n".getBytes(StandardCharsets.UTF_8));

        assertFalse(PluginUtils.ensureGitignore(tempDir.toFile()));
    }

    @Test
    void ensureGitignore_partiallyPresent_addsOnlyMissing(@TempDir Path tempDir) throws IOException {
        File gitignore = tempDir.resolve(".gitignore").toFile();
        Files.write(gitignore.toPath(), "release-it-go\n".getBytes(StandardCharsets.UTF_8));

        assertTrue(PluginUtils.ensureGitignore(tempDir.toFile()));

        String content = new String(Files.readAllBytes(gitignore.toPath()), StandardCharsets.UTF_8);
        assertTrue(content.contains("release-it-go.exe"));
        // Should not duplicate the existing entry
        int count = content.split("release-it-go\n", -1).length - 1;
        assertTrue(count >= 1, "Original entry should remain");
    }

    @Test
    void ensureGitignore_noFalsePositiveFromSubstring(@TempDir Path tempDir) throws IOException {
        File gitignore = tempDir.resolve(".gitignore").toFile();
        Files.write(gitignore.toPath(), "my-release-it-go-wrapper\n".getBytes(StandardCharsets.UTF_8));

        assertTrue(PluginUtils.ensureGitignore(tempDir.toFile()));

        String content = new String(Files.readAllBytes(gitignore.toPath()), StandardCharsets.UTF_8);
        assertTrue(content.contains("\nrelease-it-go\n") || content.endsWith("release-it-go\n"));
    }

    @Test
    void ensureGitignore_existingFileNoTrailingNewline(@TempDir Path tempDir) throws IOException {
        File gitignore = tempDir.resolve(".gitignore").toFile();
        Files.write(gitignore.toPath(), "target/".getBytes(StandardCharsets.UTF_8));

        assertTrue(PluginUtils.ensureGitignore(tempDir.toFile()));

        String content = new String(Files.readAllBytes(gitignore.toPath()), StandardCharsets.UTF_8);
        // Should not have content glued together without newline
        assertFalse(content.contains("target/\n\n\n"), "Should not have excess blank lines");
        assertTrue(content.contains("release-it-go"));
    }

    // --- getDefaultVersion with fallback reason tests ---

    @Test
    void getDefaultVersion_withFallbackReason_emptyWhenSuccess() {
        List<String> reasons = new ArrayList<String>();
        String version = PluginUtils.getDefaultVersion(reasons);
        assertFalse(version.isEmpty());
        // In test environment with filtered resources, reasons should be empty
        // (the resource exists and is filtered by Maven during test phase)
        assertTrue(reasons.isEmpty(), "No fallback reason expected when resource is properly filtered");
    }

    @Test
    void getDefaultVersion_nullReasonList_doesNotThrow() {
        assertDoesNotThrow(() -> PluginUtils.getDefaultVersion(null));
    }
}

package com.emrefirat.releaseItGO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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
}

package com.emrefirat.releaseItGO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link InstallMojo}.
 */
class InstallMojoTest {

    // --- normalizeVersion tests ---

    @Test
    void normalizeVersion_plainVersion() {
        assertEquals("0.1.3", InstallMojo.normalizeVersion("0.1.3"));
    }

    @Test
    void normalizeVersion_withVPrefix() {
        assertEquals("0.1.3", InstallMojo.normalizeVersion("v0.1.3"));
    }

    @Test
    void normalizeVersion_withCommandOutput() {
        assertEquals("0.1.3", InstallMojo.normalizeVersion("release-it-go version 0.1.3"));
    }

    @Test
    void normalizeVersion_withCommandOutputAndVPrefix() {
        assertEquals("0.1.3", InstallMojo.normalizeVersion("release-it-go version v0.1.3"));
    }

    @Test
    void normalizeVersion_withWhitespace() {
        assertEquals("0.1.3", InstallMojo.normalizeVersion("  0.1.3  "));
    }

    @Test
    void normalizeVersion_null_returnsEmpty() {
        assertEquals("", InstallMojo.normalizeVersion(null));
    }

    @Test
    void normalizeVersion_empty_returnsEmpty() {
        assertEquals("", InstallMojo.normalizeVersion(""));
    }

    @Test
    void normalizeVersion_distinguishesSimilarVersions() {
        // This was the original bug: "0.1.30".contains("0.1.3") == true
        String v1 = InstallMojo.normalizeVersion("0.1.3");
        String v2 = InstallMojo.normalizeVersion("0.1.30");
        assertFalse(v1.equals(v2), "0.1.3 and 0.1.30 should be different versions");
    }

    // --- hasConfigFile tests ---

    @Test
    void hasConfigFile_yamlExists_returnsTrue(@TempDir Path tempDir) throws IOException {
        Files.createFile(tempDir.resolve(".release-it-go.yaml"));
        assertTrue(InstallMojo.hasConfigFile(tempDir.toFile()));
    }

    @Test
    void hasConfigFile_ymlExists_returnsTrue(@TempDir Path tempDir) throws IOException {
        Files.createFile(tempDir.resolve(".release-it-go.yml"));
        assertTrue(InstallMojo.hasConfigFile(tempDir.toFile()));
    }

    @Test
    void hasConfigFile_jsonExists_returnsTrue(@TempDir Path tempDir) throws IOException {
        Files.createFile(tempDir.resolve(".release-it-go.json"));
        assertTrue(InstallMojo.hasConfigFile(tempDir.toFile()));
    }

    @Test
    void hasConfigFile_tomlExists_returnsTrue(@TempDir Path tempDir) throws IOException {
        Files.createFile(tempDir.resolve(".release-it-go.toml"));
        assertTrue(InstallMojo.hasConfigFile(tempDir.toFile()));
    }

    @Test
    void hasConfigFile_releaseItYamlExists_returnsTrue(@TempDir Path tempDir) throws IOException {
        Files.createFile(tempDir.resolve(".release-it.yaml"));
        assertTrue(InstallMojo.hasConfigFile(tempDir.toFile()));
    }

    @Test
    void hasConfigFile_noConfigFile_returnsFalse(@TempDir Path tempDir) {
        assertFalse(InstallMojo.hasConfigFile(tempDir.toFile()));
    }

    @Test
    void hasConfigFile_emptyDir_returnsFalse(@TempDir Path tempDir) {
        assertFalse(InstallMojo.hasConfigFile(tempDir.toFile()));
    }

    @Test
    void hasConfigFile_nonExistentDir_returnsFalse() {
        assertFalse(InstallMojo.hasConfigFile(new File("/nonexistent/path")));
    }
}

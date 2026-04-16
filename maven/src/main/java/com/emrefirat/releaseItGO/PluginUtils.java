package com.emrefirat.releaseItGO;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * Shared utility methods for the release-it-go Maven plugin.
 */
final class PluginUtils {

    /** Allowed version format: major.minor.patch with optional pre-release suffix. */
    private static final Pattern VERSION_PATTERN =
            Pattern.compile("^[0-9]+\\.[0-9]+\\.[0-9]+(-[a-zA-Z0-9.]+)?$");

    private static final String DEFAULTS_RESOURCE = "release-it-go-defaults.properties";
    private static final String FALLBACK_VERSION = "0.2.0";

    private static final String[] GITIGNORE_ENTRIES = {
            "release-it-go",
            "release-it-go.exe",
            ".hooks/"
    };

    private static final String[] CONFIG_FILES = {
            ".release-it-go.yaml", ".release-it-go.yml", ".release-it-go.json", ".release-it-go.toml",
            ".release-it.yaml", ".release-it.yml", ".release-it.json", ".release-it.toml"
    };

    private PluginUtils() {
        // utility class
    }

    /**
     * Checks if a release-it-go config file exists in the given directory.
     */
    static boolean hasConfigFile(File baseDir) {
        for (String name : CONFIG_FILES) {
            if (new File(baseDir, name).exists()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the default release-it-go version bundled with this plugin build.
     * The value is injected by Maven resource filtering at build time.
     * Falls back to FALLBACK_VERSION if the resource is missing or unfiltered,
     * and returns the reason via the provided list (empty list = no fallback).
     */
    static String getDefaultVersion() {
        return getDefaultVersion(null);
    }

    /**
     * Returns the default release-it-go version bundled with this plugin build.
     *
     * @param fallbackReason if non-null, the reason for falling back is added to this list
     */
    static String getDefaultVersion(java.util.List<String> fallbackReason) {
        try (InputStream is = PluginUtils.class.getClassLoader().getResourceAsStream(DEFAULTS_RESOURCE)) {
            if (is == null) {
                if (fallbackReason != null) {
                    fallbackReason.add("Defaults resource not found: " + DEFAULTS_RESOURCE);
                }
                return FALLBACK_VERSION;
            }
            Properties props = new Properties();
            props.load(is);
            String version = props.getProperty("version", "").trim();
            if (!version.isEmpty() && !version.contains("$")) {
                return version;
            }
            if (fallbackReason != null) {
                fallbackReason.add("Version property is empty or unfiltered: '" + version + "'");
            }
        } catch (IOException e) {
            if (fallbackReason != null) {
                fallbackReason.add("Failed to read defaults resource: " + e.getMessage());
            }
        }
        return FALLBACK_VERSION;
    }

    /**
     * Validates that a version string matches the expected semver format.
     * Prevents path traversal and URL injection via malicious version values.
     *
     * @throws IllegalArgumentException if the version format is invalid
     */
    static void validateVersion(String version) {
        if (version == null || !VERSION_PATTERN.matcher(version).matches()) {
            throw new IllegalArgumentException(
                    "Invalid version format: '" + version + "'. Expected semver (e.g. 0.1.3 or 1.0.0-beta.1)");
        }
    }

    /** Matches a semver-like version string (e.g. 0.1.3, 1.0.0-beta.1, 0.1.4-hooks.0). */
    private static final Pattern SEMVER_EXTRACT_PATTERN =
            Pattern.compile("v?([0-9]+\\.[0-9]+\\.[0-9]+(?:-[a-zA-Z0-9.]+)?)");

    /**
     * Extracts a clean version string from various formats:
     * <ul>
     *   <li>"0.1.3" → "0.1.3"</li>
     *   <li>"v0.1.3" → "0.1.3"</li>
     *   <li>"release-it-go version 0.1.3" → "0.1.3"</li>
     *   <li>"release-it-go 0.1.4-hooks.0 (commit: abc, built: ...)" → "0.1.4-hooks.0"</li>
     * </ul>
     */
    static String normalizeVersion(String raw) {
        if (raw == null) {
            return "";
        }
        java.util.regex.Matcher matcher = SEMVER_EXTRACT_PATTERN.matcher(raw.trim());
        if (matcher.find()) {
            return matcher.group(1);
        }
        return raw.trim();
    }

    /**
     * Ensures all platform binary names are listed in .gitignore.
     * Adds both release-it-go and release-it-go.exe for cross-platform team support.
     * Uses line-based matching to avoid false positives from substring contains.
     *
     * @param baseDir the project base directory containing .gitignore
     * @return true if .gitignore was modified, false if already up to date
     * @throws IOException if the file cannot be read or written
     */
    static boolean ensureGitignore(File baseDir) throws IOException {
        File gitignore = new File(baseDir, ".gitignore");

        java.util.Set<String> existingEntries = new java.util.HashSet<String>();
        String existingContent = "";

        if (gitignore.exists()) {
            existingContent = new String(Files.readAllBytes(gitignore.toPath()), StandardCharsets.UTF_8);
            for (String line : existingContent.split("\\r?\\n")) {
                existingEntries.add(line.trim());
            }
        }

        java.util.List<String> missing = new java.util.ArrayList<String>();
        for (String entry : GITIGNORE_ENTRIES) {
            if (!existingEntries.contains(entry)) {
                missing.add(entry);
            }
        }

        if (missing.isEmpty()) {
            return false;
        }

        StringBuilder block = new StringBuilder();
        if (gitignore.exists()) {
            // Ensure we start on a new line
            if (!existingContent.isEmpty() && !existingContent.endsWith("\n")) {
                block.append("\n");
            }
        }
        block.append("\n# release-it-go binary\n");
        for (String entry : missing) {
            block.append(entry).append("\n");
        }

        if (gitignore.exists()) {
            Files.write(gitignore.toPath(),
                    block.toString().getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.APPEND);
        } else {
            // Remove leading newline for new file
            String content = block.toString();
            if (content.startsWith("\n")) {
                content = content.substring(1);
            }
            Files.write(gitignore.toPath(),
                    content.getBytes(StandardCharsets.UTF_8));
        }

        return true;
    }
}

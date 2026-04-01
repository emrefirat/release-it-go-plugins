package com.emrefirat.releaseItGO;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
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
    private static final String FALLBACK_VERSION = "0.1.3";

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
     */
    static String getDefaultVersion() {
        try (InputStream is = PluginUtils.class.getClassLoader().getResourceAsStream(DEFAULTS_RESOURCE)) {
            if (is != null) {
                Properties props = new Properties();
                props.load(is);
                String version = props.getProperty("version", "").trim();
                if (!version.isEmpty() && !version.contains("$")) {
                    return version;
                }
            }
        } catch (IOException e) {
            // fall through to fallback
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

    /**
     * Extracts a clean version string by stripping any "v" prefix and non-version text.
     * For example: "release-it-go version 0.1.3" → "0.1.3", "v0.1.3" → "0.1.3"
     */
    static String normalizeVersion(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        // Handle output like "release-it-go version 0.1.3"
        int lastSpace = trimmed.lastIndexOf(' ');
        if (lastSpace >= 0) {
            trimmed = trimmed.substring(lastSpace + 1);
        }
        // Strip leading "v"
        if (trimmed.startsWith("v")) {
            trimmed = trimmed.substring(1);
        }
        return trimmed;
    }
}

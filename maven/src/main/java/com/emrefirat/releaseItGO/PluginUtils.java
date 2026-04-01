package com.emrefirat.releaseItGO;

import java.io.File;

/**
 * Shared utility methods for the release-it-go Maven plugin.
 */
final class PluginUtils {

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

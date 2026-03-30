package com.emrefirat.releaseIt;

import java.util.Locale;

/**
 * Detects the current operating system and CPU architecture.
 * Maps Java system property values to the naming convention used
 * by release-it-go GitHub Releases (Go-style GOOS/GOARCH).
 */
public final class PlatformDetector {

    private PlatformDetector() {
        // utility class
    }

    /**
     * Detects the operating system and returns the Go-style GOOS value.
     *
     * @return "darwin", "linux", or "windows"
     * @throws IllegalStateException if the OS is not recognized
     */
    public static String detectOS() {
        return detectOS(System.getProperty("os.name", ""));
    }

    /**
     * Detects the operating system from the given os.name property value.
     *
     * @param osName the value of the os.name system property
     * @return "darwin", "linux", or "windows"
     * @throws IllegalStateException if the OS is not recognized
     */
    static String detectOS(String osName) {
        String name = osName.toLowerCase(Locale.ENGLISH);
        if (name.contains("mac") || name.contains("darwin")) {
            return "darwin";
        }
        if (name.contains("linux")) {
            return "linux";
        }
        if (name.contains("windows") || name.contains("win")) {
            return "windows";
        }
        throw new IllegalStateException("Unsupported operating system: " + osName);
    }

    /**
     * Detects the CPU architecture and returns the Go-style GOARCH value.
     *
     * @return "amd64" or "arm64"
     * @throws IllegalStateException if the architecture is not recognized
     */
    public static String detectArch() {
        return detectArch(System.getProperty("os.arch", ""));
    }

    /**
     * Detects the CPU architecture from the given os.arch property value.
     *
     * @param osArch the value of the os.arch system property
     * @return "amd64" or "arm64"
     * @throws IllegalStateException if the architecture is not recognized
     */
    static String detectArch(String osArch) {
        String arch = osArch.toLowerCase(Locale.ENGLISH);
        if (arch.equals("x86_64") || arch.equals("amd64")) {
            return "amd64";
        }
        if (arch.equals("aarch64") || arch.equals("arm64")) {
            return "arm64";
        }
        throw new IllegalStateException("Unsupported architecture: " + osArch);
    }

    /**
     * Returns the binary file name for the current platform.
     * On Windows the binary has a .exe extension.
     *
     * @return the binary file name
     */
    public static String binaryName() {
        if ("windows".equals(detectOS())) {
            return "release-it-go.exe";
        }
        return "release-it-go";
    }
}

package com.emrefirat.releaseItGO;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link PlatformDetector}.
 */
class PlatformDetectorTest {

    // --- detectOS tests ---

    @Test
    void detectOS_macOSX_returnsDarwin() {
        assertEquals("darwin", PlatformDetector.detectOS("Mac OS X"));
    }

    @Test
    void detectOS_macOS_returnsDarwin() {
        assertEquals("darwin", PlatformDetector.detectOS("macOS"));
    }

    @Test
    void detectOS_darwin_returnsDarwin() {
        assertEquals("darwin", PlatformDetector.detectOS("Darwin"));
    }

    @Test
    void detectOS_linux_returnsLinux() {
        assertEquals("linux", PlatformDetector.detectOS("Linux"));
    }

    @Test
    void detectOS_gnuLinux_returnsLinux() {
        assertEquals("linux", PlatformDetector.detectOS("GNU/Linux"));
    }

    @Test
    void detectOS_windows10_returnsWindows() {
        assertEquals("windows", PlatformDetector.detectOS("Windows 10"));
    }

    @Test
    void detectOS_windowsServer_returnsWindows() {
        assertEquals("windows", PlatformDetector.detectOS("Windows Server 2019"));
    }

    @Test
    void detectOS_caseInsensitive_linux() {
        assertEquals("linux", PlatformDetector.detectOS("LINUX"));
    }

    @Test
    void detectOS_caseInsensitive_windows() {
        assertEquals("windows", PlatformDetector.detectOS("WINDOWS 11"));
    }

    @Test
    void detectOS_unsupported_throwsException() {
        assertThrows(IllegalStateException.class, () -> PlatformDetector.detectOS("FreeBSD"));
    }

    @Test
    void detectOS_solaris_throwsException() {
        assertThrows(IllegalStateException.class, () -> PlatformDetector.detectOS("SunOS"));
    }

    @Test
    void detectOS_empty_throwsException() {
        assertThrows(IllegalStateException.class, () -> PlatformDetector.detectOS(""));
    }

    // --- detectArch tests ---

    @Test
    void detectArch_x86_64_returnsAmd64() {
        assertEquals("amd64", PlatformDetector.detectArch("x86_64"));
    }

    @Test
    void detectArch_amd64_returnsAmd64() {
        assertEquals("amd64", PlatformDetector.detectArch("amd64"));
    }

    @Test
    void detectArch_aarch64_returnsArm64() {
        assertEquals("arm64", PlatformDetector.detectArch("aarch64"));
    }

    @Test
    void detectArch_arm64_returnsArm64() {
        assertEquals("arm64", PlatformDetector.detectArch("arm64"));
    }

    @Test
    void detectArch_unsupported_throwsException() {
        assertThrows(IllegalStateException.class, () -> PlatformDetector.detectArch("i386"));
    }

    @Test
    void detectArch_empty_throwsException() {
        assertThrows(IllegalStateException.class, () -> PlatformDetector.detectArch(""));
    }

    // --- Live platform tests (run on current JVM) ---

    @Test
    void detectOS_currentPlatform_returnsValidValue() {
        String os = PlatformDetector.detectOS();
        assertNotNull(os, "OS should not be null");
        assertTrue(
                os.equals("darwin") || os.equals("linux") || os.equals("windows"),
                "OS should be darwin, linux, or windows but was: " + os
        );
    }

    @Test
    void detectArch_currentPlatform_returnsValidValue() {
        String arch = PlatformDetector.detectArch();
        assertNotNull(arch, "Arch should not be null");
        assertTrue(
                arch.equals("amd64") || arch.equals("arm64"),
                "Arch should be amd64 or arm64 but was: " + arch
        );
    }

    @Test
    void binaryName_returnsNonEmptyString() {
        String name = PlatformDetector.binaryName();
        assertNotNull(name, "Binary name should not be null");
        assertTrue(name.startsWith("release-it-go"), "Binary name should start with release-it-go");
    }

    @Test
    void binaryName_windowsHasExeExtension() {
        String name = PlatformDetector.binaryName();
        String os = PlatformDetector.detectOS();
        if ("windows".equals(os)) {
            assertTrue(name.endsWith(".exe"), "Windows binary should end with .exe");
        } else {
            assertEquals("release-it-go", name, "Non-Windows binary should be release-it-go");
        }
    }
}

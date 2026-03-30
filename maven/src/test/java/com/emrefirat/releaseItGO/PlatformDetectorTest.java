package com.emrefirat.releaseItGO;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests for {@link PlatformDetector}.
 */
public class PlatformDetectorTest {

    // --- detectOS tests ---

    @Test
    public void detectOS_macOSX_returnsDarwin() {
        assertEquals("darwin", PlatformDetector.detectOS("Mac OS X"));
    }

    @Test
    public void detectOS_macOS_returnsDarwin() {
        assertEquals("darwin", PlatformDetector.detectOS("macOS"));
    }

    @Test
    public void detectOS_darwin_returnsDarwin() {
        assertEquals("darwin", PlatformDetector.detectOS("Darwin"));
    }

    @Test
    public void detectOS_linux_returnsLinux() {
        assertEquals("linux", PlatformDetector.detectOS("Linux"));
    }

    @Test
    public void detectOS_gnuLinux_returnsLinux() {
        assertEquals("linux", PlatformDetector.detectOS("GNU/Linux"));
    }

    @Test
    public void detectOS_windows10_returnsWindows() {
        assertEquals("windows", PlatformDetector.detectOS("Windows 10"));
    }

    @Test
    public void detectOS_windowsServer_returnsWindows() {
        assertEquals("windows", PlatformDetector.detectOS("Windows Server 2019"));
    }

    @Test(expected = IllegalStateException.class)
    public void detectOS_unsupported_throwsException() {
        PlatformDetector.detectOS("FreeBSD");
    }

    @Test(expected = IllegalStateException.class)
    public void detectOS_empty_throwsException() {
        PlatformDetector.detectOS("");
    }

    // --- detectArch tests ---

    @Test
    public void detectArch_x86_64_returnsAmd64() {
        assertEquals("amd64", PlatformDetector.detectArch("x86_64"));
    }

    @Test
    public void detectArch_amd64_returnsAmd64() {
        assertEquals("amd64", PlatformDetector.detectArch("amd64"));
    }

    @Test
    public void detectArch_aarch64_returnsArm64() {
        assertEquals("arm64", PlatformDetector.detectArch("aarch64"));
    }

    @Test
    public void detectArch_arm64_returnsArm64() {
        assertEquals("arm64", PlatformDetector.detectArch("arm64"));
    }

    @Test(expected = IllegalStateException.class)
    public void detectArch_unsupported_throwsException() {
        PlatformDetector.detectArch("i386");
    }

    @Test(expected = IllegalStateException.class)
    public void detectArch_empty_throwsException() {
        PlatformDetector.detectArch("");
    }

    // --- Live platform tests (run on current JVM) ---

    @Test
    public void detectOS_currentPlatform_returnsValidValue() {
        String os = PlatformDetector.detectOS();
        assertNotNull("OS should not be null", os);
        assertTrue(
                "OS should be darwin, linux, or windows but was: " + os,
                os.equals("darwin") || os.equals("linux") || os.equals("windows")
        );
    }

    @Test
    public void detectArch_currentPlatform_returnsValidValue() {
        String arch = PlatformDetector.detectArch();
        assertNotNull("Arch should not be null", arch);
        assertTrue(
                "Arch should be amd64 or arm64 but was: " + arch,
                arch.equals("amd64") || arch.equals("arm64")
        );
    }

    @Test
    public void binaryName_returnsNonEmptyString() {
        String name = PlatformDetector.binaryName();
        assertNotNull("Binary name should not be null", name);
        assertTrue("Binary name should start with release-it-go", name.startsWith("release-it-go"));
    }

    @Test
    public void binaryName_windowsHasExeExtension() {
        // We test the logic indirectly: on Windows it should end with .exe,
        // on other platforms it should not
        String name = PlatformDetector.binaryName();
        String os = PlatformDetector.detectOS();
        if ("windows".equals(os)) {
            assertTrue("Windows binary should end with .exe", name.endsWith(".exe"));
        } else {
            assertEquals("Non-Windows binary should be release-it-go", "release-it-go", name);
        }
    }
}

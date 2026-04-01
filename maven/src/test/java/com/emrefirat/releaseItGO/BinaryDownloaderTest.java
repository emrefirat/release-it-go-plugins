package com.emrefirat.releaseItGO;

import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link BinaryDownloader}.
 */
class BinaryDownloaderTest {

    private final Log log = new SystemStreamLog();

    // --- extractZip tests ---

    @Test
    void extractZip_findsBinaryAtRoot(@TempDir Path tempDir) throws IOException {
        String binaryName = PlatformDetector.binaryName();
        byte[] content = "fake-binary-content".getBytes(StandardCharsets.UTF_8);
        File zipFile = createZipWithEntry(tempDir, binaryName, content);

        BinaryDownloader downloader = new BinaryDownloader(log, "0.1.0", tempDir.toFile(), null);
        downloader.extractZip(zipFile, tempDir.toFile());

        File extracted = new File(tempDir.toFile(), binaryName);
        assertTrue(extracted.exists(), "Binary should be extracted");
        assertEquals("fake-binary-content", new String(Files.readAllBytes(extracted.toPath()), StandardCharsets.UTF_8));
    }

    @Test
    void extractZip_findsBinaryInSubdirectory(@TempDir Path tempDir) throws IOException {
        String binaryName = PlatformDetector.binaryName();
        byte[] content = "nested-binary".getBytes(StandardCharsets.UTF_8);
        File zipFile = createZipWithEntry(tempDir, "release-it-go_0.1.0_windows_amd64/" + binaryName, content);

        BinaryDownloader downloader = new BinaryDownloader(log, "0.1.0", tempDir.toFile(), null);
        downloader.extractZip(zipFile, tempDir.toFile());

        File extracted = new File(tempDir.toFile(), binaryName);
        assertTrue(extracted.exists(), "Binary should be extracted from subdirectory");
        assertEquals("nested-binary", new String(Files.readAllBytes(extracted.toPath()), StandardCharsets.UTF_8));
    }

    @Test
    void extractZip_binaryNotInArchive_throwsIOException(@TempDir Path tempDir) throws IOException {
        File zipFile = createZipWithEntry(tempDir, "some-other-file.txt", "hello".getBytes(StandardCharsets.UTF_8));

        BinaryDownloader downloader = new BinaryDownloader(log, "0.1.0", tempDir.toFile(), null);

        IOException ex = assertThrows(IOException.class, () ->
                downloader.extractZip(zipFile, tempDir.toFile())
        );
        assertTrue(ex.getMessage().contains("not found in archive"));
    }

    @Test
    void extractZip_emptyArchive_throwsIOException(@TempDir Path tempDir) throws IOException {
        File zipFile = tempDir.resolve("empty.zip").toFile();
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            // empty zip
        }

        BinaryDownloader downloader = new BinaryDownloader(log, "0.1.0", tempDir.toFile(), null);

        assertThrows(IOException.class, () ->
                downloader.extractZip(zipFile, tempDir.toFile())
        );
    }

    // --- extractTarGz tests ---

    @Test
    void extractTarGz_findsBinaryAtRoot(@TempDir Path tempDir) throws IOException {
        String binaryName = PlatformDetector.binaryName();
        File tarFile = createTarGzWithFile(tempDir, binaryName, "tar-binary-content");

        BinaryDownloader downloader = new BinaryDownloader(log, "0.1.0", tempDir.toFile(), null);
        downloader.extractTarGz(tarFile, tempDir.toFile());

        File extracted = new File(tempDir.toFile(), binaryName);
        assertTrue(extracted.exists(), "Binary should be extracted from tar.gz");
        assertEquals("tar-binary-content", new String(Files.readAllBytes(extracted.toPath()), StandardCharsets.UTF_8));
    }

    @Test
    void extractTarGz_binaryNotInArchive_throwsIOException(@TempDir Path tempDir) throws IOException {
        File tarFile = createTarGzWithFile(tempDir, "some-other-file.txt", "hello");

        BinaryDownloader downloader = new BinaryDownloader(log, "0.1.0", tempDir.toFile(), null);

        assertThrows(IOException.class, () ->
                downloader.extractTarGz(tarFile, tempDir.toFile())
        );
    }

    // --- Helper methods ---

    private File createZipWithEntry(Path dir, String entryName, byte[] content) throws IOException {
        File zipFile = dir.resolve("test.zip").toFile();
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            zos.putNextEntry(new ZipEntry(entryName));
            zos.write(content);
            zos.closeEntry();
        }
        return zipFile;
    }

    private File createTarGzWithFile(Path tempDir, String fileName, String content) throws IOException {
        File contentFile = tempDir.resolve(fileName).toFile();
        Files.write(contentFile.toPath(), content.getBytes(StandardCharsets.UTF_8));

        File tarFile = tempDir.resolve("test.tar.gz").toFile();
        ProcessBuilder pb = new ProcessBuilder(
                "tar", "-czf", tarFile.getAbsolutePath(), "-C", tempDir.toString(), fileName
        );
        pb.redirectErrorStream(true);
        try {
            Process process = pb.start();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException("tar creation failed with exit code " + exitCode);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("tar creation interrupted", e);
        } finally {
            contentFile.delete();
        }
        return tarFile;
    }
}

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

    // --- ZIP security tests ---

    @Test
    void extractZip_pathTraversal_throwsIOException(@TempDir Path tempDir) throws IOException {
        String binaryName = PlatformDetector.binaryName();
        File zipFile = createZipWithEntry(tempDir, "../../" + binaryName, "evil".getBytes(StandardCharsets.UTF_8));

        BinaryDownloader downloader = new BinaryDownloader(log, "0.1.0", tempDir.toFile(), null);

        IOException ex = assertThrows(IOException.class, () ->
                downloader.extractZip(zipFile, tempDir.toFile())
        );
        assertTrue(ex.getMessage().contains("path traversal"));
    }

    @Test
    void extractZip_pathTraversalInSubdir_throwsIOException(@TempDir Path tempDir) throws IOException {
        String binaryName = PlatformDetector.binaryName();
        File zipFile = createZipWithEntry(tempDir,
                "subdir/../../../tmp/" + binaryName, "evil".getBytes(StandardCharsets.UTF_8));

        BinaryDownloader downloader = new BinaryDownloader(log, "0.1.0", tempDir.toFile(), null);

        IOException ex = assertThrows(IOException.class, () ->
                downloader.extractZip(zipFile, tempDir.toFile())
        );
        assertTrue(ex.getMessage().contains("path traversal"));
    }

    // --- SHA256 tests ---

    @Test
    void computeSHA256_knownValue(@TempDir Path tempDir) throws IOException {
        // SHA256 of "hello\n" (with newline, as Files.write adds)
        File testFile = tempDir.resolve("test.txt").toFile();
        Files.write(testFile.toPath(), "hello".getBytes(StandardCharsets.UTF_8));

        String hash = BinaryDownloader.computeSHA256(testFile);

        // SHA256("hello") = 2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824
        assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824", hash);
    }

    @Test
    void computeSHA256_emptyFile(@TempDir Path tempDir) throws IOException {
        File testFile = tempDir.resolve("empty.txt").toFile();
        Files.write(testFile.toPath(), new byte[0]);

        String hash = BinaryDownloader.computeSHA256(testFile);

        // SHA256("") = e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", hash);
    }

    @Test
    void computeSHA256_differentContentsDifferentHash(@TempDir Path tempDir) throws IOException {
        File file1 = tempDir.resolve("file1.txt").toFile();
        File file2 = tempDir.resolve("file2.txt").toFile();
        Files.write(file1.toPath(), "content-a".getBytes(StandardCharsets.UTF_8));
        Files.write(file2.toPath(), "content-b".getBytes(StandardCharsets.UTF_8));

        String hash1 = BinaryDownloader.computeSHA256(file1);
        String hash2 = BinaryDownloader.computeSHA256(file2);

        assertTrue(!hash1.equals(hash2), "Different contents should produce different hashes");
        assertEquals(64, hash1.length(), "SHA256 hex string should be 64 chars");
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

package com.emrefirat.releaseItGO;

import org.apache.maven.plugin.logging.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Downloads the release-it-go binary from GitHub Releases.
 * Handles platform detection, archive extraction, and caching.
 */
public class BinaryDownloader {

    private static final String DOWNLOAD_URL_TEMPLATE =
            "https://github.com/emrefirat/release-it-GO/releases/download/v%s/release-it-go_%s_%s_%s";

    private static final String CHECKSUMS_URL_TEMPLATE =
            "https://github.com/emrefirat/release-it-GO/releases/download/v%s/checksums.txt";

    private static final int CONNECT_TIMEOUT_MS = 30_000;
    private static final int READ_TIMEOUT_MS = 120_000;
    private static final int BUFFER_SIZE = 8192;
    private static final int MAX_REDIRECTS = 5;

    private final Log logger;
    private final String version;
    private final File binDir;
    private final String token;
    private final boolean strictChecksum;

    /**
     * Creates a new BinaryDownloader.
     *
     * @param logger          Maven logger for output
     * @param version         the release-it-go version to download (e.g. "0.1.0")
     * @param binDir          the directory where the binary will be stored
     * @param token           GitHub token for private repo access (nullable)
     * @param strictChecksum  when true, fail if checksum cannot be verified
     */
    public BinaryDownloader(Log logger, String version, File binDir, String token, boolean strictChecksum) {
        this.logger = logger;
        this.version = version;
        this.binDir = binDir;
        this.token = token;
        this.strictChecksum = strictChecksum;
    }

    /**
     * Downloads and extracts the release-it-go binary for the current platform.
     * If the binary already exists at the target path, the download is skipped.
     *
     * @return the File pointing to the extracted binary
     * @throws IOException if the download or extraction fails
     */
    public File download() throws IOException {
        String os = PlatformDetector.detectOS();
        String arch = PlatformDetector.detectArch();
        String binaryName = PlatformDetector.binaryName();

        File binaryFile = new File(binDir, binaryName);

        // Remove old binary if exists (caller handles version check)
        if (binaryFile.exists() && !binaryFile.delete()) {
            throw new IOException("Failed to delete existing binary: " + binaryFile.getName());
        }

        if (!binDir.exists() && !binDir.mkdirs()) {
            throw new IOException("Failed to create binary directory");
        }

        String ext = "windows".equals(os) ? ".zip" : ".tar.gz";
        String downloadUrl = String.format(DOWNLOAD_URL_TEMPLATE + ext, version, version, os, arch);
        logger.info("Downloading release-it-go v" + version + " for " + os + "/" + arch);
        logger.info("URL: " + downloadUrl);

        File archiveFile = new File(binDir, "release-it-go" + ext);
        downloadFile(downloadUrl, archiveFile);

        // Verify archive integrity via SHA256 checksum
        verifyChecksum(archiveFile, version, os, arch, ext);

        logger.info("Extracting archive...");
        if ("windows".equals(os)) {
            extractZip(archiveFile, binDir);
        } else {
            extractTarGz(archiveFile, binDir);
        }

        if (!archiveFile.delete()) {
            logger.warn("Failed to delete archive: " + archiveFile.getName());
        }

        if (!"windows".equals(os)) {
            // Owner-only executable to prevent other users from running or replacing
            if (!binaryFile.setExecutable(true, true)) {
                logger.warn("Failed to set executable permission on binary");
            }
        }

        if (!binaryFile.exists()) {
            throw new IOException("Binary not found after extraction: " + binaryFile.getName());
        }

        logger.info("Binary installed: " + binaryFile.getName());
        return binaryFile;
    }

    /**
     * Downloads a file from the given URL to the target file.
     * Follows HTTP redirects manually with a depth limit.
     * Authorization header is only sent to the original host to prevent token leakage on redirects.
     */
    private void downloadFile(String urlString, File target) throws IOException {
        String originalHost = null;
        String currentUrl = urlString;

        for (int redirectCount = 0; ; redirectCount++) {
            if (redirectCount > MAX_REDIRECTS) {
                throw new IOException("Too many redirects (max " + MAX_REDIRECTS + ") from: " + urlString);
            }

            URL url;
            try {
                url = new URI(currentUrl).toURL();
            } catch (URISyntaxException e) {
                throw new IOException("Invalid URL: " + currentUrl, e);
            }

            if (originalHost == null) {
                originalHost = url.getHost();
            }

            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestProperty("Accept", "application/octet-stream");

            // Only send auth token to the original host to prevent leakage on redirects
            if (token != null && !token.isEmpty() && url.getHost().equals(originalHost)) {
                connection.setRequestProperty("Authorization", "Bearer " + token);
            }

            try {
                int responseCode = connection.getResponseCode();

                if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP
                        || responseCode == HttpURLConnection.HTTP_MOVED_PERM
                        || responseCode == 307
                        || responseCode == 308) {
                    String redirectUrl = connection.getHeaderField("Location");
                    if (redirectUrl == null || redirectUrl.isEmpty()) {
                        throw new IOException("Redirect with no Location header from: " + currentUrl);
                    }
                    logger.debug("Following redirect to: " + maskUrl(redirectUrl));
                    currentUrl = redirectUrl;
                    continue;
                }

                if (responseCode != HttpURLConnection.HTTP_OK) {
                    throw new IOException("HTTP " + responseCode + " when downloading " + currentUrl);
                }

                try (InputStream in = connection.getInputStream();
                     OutputStream out = new FileOutputStream(target)) {
                    byte[] buffer = new byte[BUFFER_SIZE];
                    int bytesRead;
                    long totalBytes = 0;
                    while ((bytesRead = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                        totalBytes += bytesRead;
                    }
                    logger.info("Downloaded " + (totalBytes / 1024) + " KB");
                }
                return;
            } finally {
                connection.disconnect();
            }
        }
    }

    /**
     * Extracts only the binary from a .tar.gz archive to the given target directory.
     * Uses the system tar command which is available on all supported platforms
     * (Unix natively, Windows 10+ built-in).
     */
    void extractTarGz(File archive, File targetDir) throws IOException {
        String binaryName = PlatformDetector.binaryName();
        ProcessBuilder pb = new ProcessBuilder(
                "tar", "-xzf", archive.getAbsolutePath(), "-C", targetDir.getAbsolutePath(), binaryName
        );
        pb.redirectErrorStream(true);

        Process process = pb.start();
        try {
            // Read output to prevent process from blocking
            StringBuilder output = new StringBuilder();
            try (InputStream is = process.getInputStream()) {
                byte[] buffer = new byte[BUFFER_SIZE];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    output.append(new String(buffer, 0, bytesRead, StandardCharsets.UTF_8));
                }
            }

            int exitCode;
            try {
                exitCode = process.waitFor();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Extraction interrupted", e);
            }

            if (exitCode != 0) {
                throw new IOException("tar extraction failed (exit code " + exitCode + "): " + output);
            }
        } finally {
            process.destroyForcibly();
        }
    }

    /**
     * Extracts only the binary from a .zip archive to the given target directory.
     * Uses Java's built-in ZipInputStream for cross-platform compatibility.
     */
    void extractZip(File archive, File targetDir) throws IOException {
        String binaryName = PlatformDetector.binaryName();
        boolean found = false;

        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName();
                // Reject entries with path traversal sequences
                if (entryName.contains("..")) {
                    throw new IOException("Zip entry contains path traversal: " + entryName);
                }
                if (entryName.equals(binaryName) || entryName.endsWith("/" + binaryName)) {
                    File outFile = new File(targetDir, binaryName);
                    try (OutputStream out = new FileOutputStream(outFile)) {
                        byte[] buffer = new byte[BUFFER_SIZE];
                        int bytesRead;
                        while ((bytesRead = zis.read(buffer)) != -1) {
                            out.write(buffer, 0, bytesRead);
                        }
                    }
                    found = true;
                    break;
                }
                zis.closeEntry();
            }
        }

        if (!found) {
            throw new IOException("Binary '" + binaryName + "' not found in archive: " + archive.getName());
        }
    }

    /**
     * Downloads the checksums file and verifies the archive's SHA256 hash.
     * If the checksums file is not available (e.g. older releases), logs a warning and continues.
     */
    void verifyChecksum(File archiveFile, String ver, String os, String arch, String ext)
            throws IOException {
        String archiveName = String.format("release-it-go_%s_%s_%s%s", ver, os, arch, ext);
        String checksumsUrl = String.format(CHECKSUMS_URL_TEMPLATE, ver);

        File checksumsFile = new File(binDir, "checksums.txt");
        try {
            downloadFile(checksumsUrl, checksumsFile);
        } catch (IOException e) {
            if (strictChecksum) {
                throw new IOException(
                        "SECURITY: Checksums file not available and strict checksum mode is enabled. "
                        + "Cannot verify binary integrity. Aborting download.", e);
            }
            logger.warn("Checksums file not available (" + e.getMessage()
                    + "). Skipping integrity verification — consider upgrading release-it-go "
                    + "or enabling strictChecksum mode.");
            return;
        }

        try {
            String expectedHash = null;
            for (String line : java.nio.file.Files.readAllLines(checksumsFile.toPath(), StandardCharsets.UTF_8)) {
                // Format: "sha256hash  filename" (two spaces between hash and name)
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                String[] parts = trimmed.split("\\s+", 2);
                if (parts.length == 2 && parts[1].trim().equals(archiveName)) {
                    expectedHash = parts[0].toLowerCase();
                    break;
                }
            }

            if (expectedHash == null) {
                if (strictChecksum) {
                    throw new IOException(
                            "SECURITY: No checksum entry found for " + archiveName
                            + " in checksums.txt. Strict checksum mode requires verification. Aborting.");
                }
                logger.warn("No checksum entry found for " + archiveName + " in checksums.txt. Skipping verification.");
                return;
            }

            String actualHash = computeSHA256(archiveFile);

            if (!expectedHash.equals(actualHash)) {
                // Delete the compromised archive immediately
                archiveFile.delete();
                throw new IOException(
                        "SECURITY: SHA256 checksum mismatch for " + archiveName + "!\n"
                        + "  Expected: " + expectedHash + "\n"
                        + "  Actual:   " + actualHash + "\n"
                        + "The downloaded file may have been tampered with. "
                        + "Aborting to prevent execution of potentially malicious code.");
            }

            logger.info("SHA256 checksum verified: " + actualHash);
        } finally {
            checksumsFile.delete();
        }
    }

    /**
     * Computes the SHA256 hash of a file.
     */
    static String computeSHA256(File file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 algorithm not available", e);
        }

        try (InputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
        }

        byte[] hashBytes = digest.digest();
        StringBuilder hex = new StringBuilder(hashBytes.length * 2);
        for (byte b : hashBytes) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    /**
     * Masks query string parameters in a URL to prevent leaking signed tokens in logs.
     */
    static String maskUrl(String url) {
        int queryStart = url.indexOf('?');
        if (queryStart < 0) {
            return url;
        }
        return url.substring(0, queryStart) + "?[MASKED]";
    }
}

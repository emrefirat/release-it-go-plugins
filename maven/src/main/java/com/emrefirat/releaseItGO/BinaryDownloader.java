package com.emrefirat.releaseItGO;

import org.apache.maven.plugin.logging.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

/**
 * Downloads the release-it-go binary from GitHub Releases.
 * Handles platform detection, archive extraction, and caching.
 */
public class BinaryDownloader {

    private static final String DOWNLOAD_URL_TEMPLATE =
            "https://github.com/emrefirat/release-it-GO/releases/download/v%s/release-it-go_%s_%s_%s";

    private static final int CONNECT_TIMEOUT_MS = 30_000;
    private static final int READ_TIMEOUT_MS = 120_000;
    private static final int BUFFER_SIZE = 8192;
    private static final int MAX_REDIRECTS = 5;

    private final Log logger;
    private final String version;
    private final File binDir;
    private final String token;

    /**
     * Creates a new BinaryDownloader.
     *
     * @param logger  Maven logger for output
     * @param version the release-it-go version to download (e.g. "0.1.0")
     * @param binDir  the directory where the binary will be stored
     * @param token   GitHub token for private repo access (nullable)
     */
    public BinaryDownloader(Log logger, String version, File binDir, String token) {
        this.logger = logger;
        this.version = version;
        this.binDir = binDir;
        this.token = token;
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
            throw new IOException("Failed to delete existing binary: " + binaryFile.getAbsolutePath());
        }

        if (!binDir.exists() && !binDir.mkdirs()) {
            throw new IOException("Failed to create directory: " + binDir.getAbsolutePath());
        }

        String ext = "windows".equals(os) ? ".zip" : ".tar.gz";
        String downloadUrl = String.format(DOWNLOAD_URL_TEMPLATE + ext, version, version, os, arch);
        logger.info("Downloading release-it-go v" + version + " for " + os + "/" + arch);
        logger.info("URL: " + downloadUrl);

        File archiveFile = new File(binDir, "release-it-go" + ext);
        downloadFile(downloadUrl, archiveFile);

        logger.info("Extracting archive...");
        if ("windows".equals(os)) {
            extractZip(archiveFile, binDir);
        } else {
            extractTarGz(archiveFile, binDir);
        }

        if (!archiveFile.delete()) {
            logger.warn("Failed to delete archive: " + archiveFile.getAbsolutePath());
        }

        if (!"windows".equals(os)) {
            if (!binaryFile.setExecutable(true)) {
                logger.warn("Failed to set executable permission on: " + binaryFile.getAbsolutePath());
            }
        }

        if (!binaryFile.exists()) {
            throw new IOException("Binary not found after extraction: " + binaryFile.getAbsolutePath());
        }

        logger.info("Binary installed: " + binaryFile.getAbsolutePath());
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
                        || responseCode == 307) {
                    String redirectUrl = connection.getHeaderField("Location");
                    connection.disconnect();
                    if (redirectUrl == null || redirectUrl.isEmpty()) {
                        throw new IOException("Redirect with no Location header from: " + currentUrl);
                    }
                    logger.debug("Following redirect to: " + redirectUrl);
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
    private void extractTarGz(File archive, File targetDir) throws IOException {
        String binaryName = PlatformDetector.binaryName();
        ProcessBuilder pb = new ProcessBuilder(
                "tar", "-xzf", archive.getAbsolutePath(), "-C", targetDir.getAbsolutePath(), binaryName
        );
        pb.redirectErrorStream(true);

        Process process = pb.start();

        // Read output to prevent process from blocking
        StringBuilder output = new StringBuilder();
        try (InputStream is = process.getInputStream()) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                output.append(new String(buffer, 0, bytesRead));
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
    }

    /**
     * Extracts only the binary from a .zip archive to the given target directory.
     * Uses Java's built-in ZipInputStream for cross-platform compatibility.
     */
    private void extractZip(File archive, File targetDir) throws IOException {
        String binaryName = PlatformDetector.binaryName();
        boolean found = false;

        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(
                new java.io.FileInputStream(archive))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().equals(binaryName) || entry.getName().endsWith("/" + binaryName)) {
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
            throw new IOException("Binary '" + binaryName + "' not found in archive: " + archive.getAbsolutePath());
        }
    }
}

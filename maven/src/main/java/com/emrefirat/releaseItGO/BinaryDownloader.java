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
            "https://github.com/emrefirat/release-it-GO/releases/download/v%s/release-it-go_%s_%s.tar.gz";

    private static final int CONNECT_TIMEOUT_MS = 30_000;
    private static final int READ_TIMEOUT_MS = 120_000;
    private static final int BUFFER_SIZE = 8192;

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

        if (binaryFile.exists() && binaryFile.canExecute()) {
            logger.info("Binary already exists: " + binaryFile.getAbsolutePath());
            return binaryFile;
        }

        if (!binDir.exists() && !binDir.mkdirs()) {
            throw new IOException("Failed to create directory: " + binDir.getAbsolutePath());
        }

        String downloadUrl = String.format(DOWNLOAD_URL_TEMPLATE, version, os, arch);
        logger.info("Downloading release-it-go v" + version + " for " + os + "/" + arch);
        logger.info("URL: " + downloadUrl);

        File archiveFile = new File(binDir, "release-it-go.tar.gz");
        downloadFile(downloadUrl, archiveFile);

        logger.info("Extracting archive...");
        extractTarGz(archiveFile, binDir);

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
     * Follows HTTP redirects automatically.
     */
    private void downloadFile(String urlString, File target) throws IOException {
        URL url;
        try {
            url = new URI(urlString).toURL();
        } catch (URISyntaxException e) {
            throw new IOException("Invalid URL: " + urlString, e);
        }
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setInstanceFollowRedirects(true);
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setRequestProperty("Accept", "application/octet-stream");
        if (token != null && !token.isEmpty()) {
            connection.setRequestProperty("Authorization", "Bearer " + token);
        }

        try {
            int responseCode = connection.getResponseCode();

            // Handle GitHub redirects (302 -> S3)
            if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP
                    || responseCode == HttpURLConnection.HTTP_MOVED_PERM
                    || responseCode == 307) {
                String redirectUrl = connection.getHeaderField("Location");
                connection.disconnect();
                if (redirectUrl == null || redirectUrl.isEmpty()) {
                    throw new IOException("Redirect with no Location header from: " + urlString);
                }
                logger.debug("Following redirect to: " + redirectUrl);
                downloadFile(redirectUrl, target);
                return;
            }

            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP " + responseCode + " when downloading " + urlString);
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
        } finally {
            connection.disconnect();
        }
    }

    /**
     * Extracts a .tar.gz archive to the given target directory.
     * Uses the system tar command which is available on all supported platforms
     * (Unix natively, Windows 10+ built-in).
     */
    private void extractTarGz(File archive, File targetDir) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(
                "tar", "-xzf", archive.getAbsolutePath(), "-C", targetDir.getAbsolutePath()
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
}

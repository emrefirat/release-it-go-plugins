package com.emrefirat.releaseItGO;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;

/**
 * Downloads the release-it-go binary from GitHub Releases and runs {@code hooks install}
 * in the project directory.
 *
 * <p>Usage in pom.xml:</p>
 * <pre>{@code
 * <plugin>
 *     <groupId>com.emrefirat</groupId>
 *     <artifactId>release-it-go-maven-plugin</artifactId>
 *     <version>1.2.0</version>
 *     <configuration>
 *         <version>0.1.0</version>
 *     </configuration>
 *     <executions>
 *         <execution>
 *             <goals><goal>install</goal></goals>
 *         </execution>
 *     </executions>
 * </plugin>
 * }</pre>
 */
@Mojo(name = "install", defaultPhase = LifecyclePhase.INITIALIZE, threadSafe = true)
public class InstallMojo extends AbstractMojo {

    /**
     * The release-it-go version to download.
     * Defaults to the version bundled with this plugin build.
     * Override in pom.xml or with -DreleaseItGo.version=X.Y.Z.
     */
    @Parameter(property = "releaseItGo.version")
    private String version;

    /**
     * Skip execution of this plugin.
     */
    @Parameter(property = "releaseItGo.skip", defaultValue = "false")
    private boolean skip;

    /**
     * GitHub token for private repo access. Reads from GITHUB_TOKEN env var if not set.
     */
    @Parameter(property = "releaseItGo.token")
    private String token;

    /**
     * When true, fail the build if SHA256 checksum cannot be verified.
     * Recommended for CI/CD environments to prevent checksum bypass attacks.
     */
    @Parameter(property = "releaseItGo.strictChecksum", defaultValue = "false")
    private boolean strictChecksum;

    @Parameter(defaultValue = "${project.basedir}", readonly = true)
    private File baseDir;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("release-it-go plugin execution skipped");
            return;
        }

        // Auto-skip in CI environments — hooks install is only needed locally
        if (PluginUtils.isCiEnvironment()) {
            getLog().info("CI environment detected (CI=true), skipping hook installation");
            return;
        }

        // Resolve version: user-specified or build-time default (local var to avoid field mutation)
        String resolvedVersion;
        if (version != null && !version.isEmpty()) {
            resolvedVersion = version;
        } else {
            java.util.List<String> fallbackReason = new java.util.ArrayList<String>();
            resolvedVersion = PluginUtils.getDefaultVersion(fallbackReason);
            if (!fallbackReason.isEmpty()) {
                getLog().warn("Using fallback version " + resolvedVersion + ": " + fallbackReason.get(0));
            } else {
                getLog().info("Using bundled default version: " + resolvedVersion);
            }
        }

        // Validate version format to prevent URL injection
        try {
            PluginUtils.validateVersion(resolvedVersion);
        } catch (IllegalArgumentException e) {
            throw new MojoFailureException(e.getMessage());
        }

        // Check for config file
        if (!PluginUtils.hasConfigFile(baseDir)) {
            throw new MojoFailureException(
                    "No .release-it-go.yaml (or .json/.toml) config file found in project directory.\n"
                    + "Create one with: ./release-it-go init\n"
                    + "Or manually create .release-it-go.yaml with your hooks configuration."
            );
        }

        // Ensure binary is in .gitignore
        ensureGitignore(baseDir);

        File binaryFile = new File(baseDir, PlatformDetector.binaryName());

        // Download binary if missing or version mismatch
        boolean needsDownload = !binaryFile.exists();
        if (!needsDownload) {
            String currentVersion = getInstalledVersion(binaryFile);
            if (currentVersion == null) {
                getLog().warn("Could not determine installed binary version, re-downloading to be safe");
                needsDownload = true;
            } else if (!PluginUtils.normalizeVersion(currentVersion).equals(PluginUtils.normalizeVersion(resolvedVersion))) {
                getLog().info("Version mismatch: installed=" + currentVersion + ", required=" + resolvedVersion);
                needsDownload = true;
            } else {
                getLog().info("Binary up to date: " + binaryFile.getName());
            }
        }

        if (needsDownload) {
            getLog().info("Downloading release-it-go v" + resolvedVersion + "...");
            try {
                String resolvedToken = resolveToken();
                BinaryDownloader downloader = new BinaryDownloader(getLog(), resolvedVersion, baseDir, resolvedToken, strictChecksum);
                binaryFile = downloader.download();
            } catch (IllegalStateException e) {
                throw new MojoFailureException("Unsupported platform: " + e.getMessage(), e);
            } catch (IOException e) {
                throw new MojoExecutionException("Failed to download release-it-go binary: " + e.getMessage(), e);
            }
        }

        // Verify binary hasn't been swapped between download and execution (TOCTOU defense)
        String preExecHash;
        try {
            preExecHash = BinaryDownloader.computeSHA256(binaryFile);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to compute binary hash before execution: " + e.getMessage(), e);
        }

        // Run hooks install
        getLog().info("Running: " + binaryFile.getName() + " hooks install");
        runHooksInstall(binaryFile);

        // Verify binary wasn't modified during execution
        try {
            String postExecHash = BinaryDownloader.computeSHA256(binaryFile);
            if (!preExecHash.equals(postExecHash)) {
                String message = "SECURITY: Binary hash changed during execution! "
                        + "Pre: " + preExecHash + ", Post: " + postExecHash;
                if (strictChecksum) {
                    throw new MojoExecutionException(message);
                }
                getLog().warn(message);
            }
        } catch (MojoExecutionException e) {
            throw e;
        } catch (IOException e) {
            getLog().debug("Could not verify post-execution binary hash: " + e.getMessage());
        }
    }

    private static final long HOOKS_INSTALL_TIMEOUT_SECONDS = 60;
    private static final long VERSION_CHECK_TIMEOUT_SECONDS = 10;

    /**
     * Executes the release-it-go binary with "hooks install" arguments.
     * Redirects output to a temp file to avoid blocking I/O before timeout.
     * Throws on non-zero exit code or timeout.
     */
    private void runHooksInstall(File binary) throws MojoExecutionException {
        File outputFile = null;
        Process process = null;
        try {
            outputFile = File.createTempFile("release-it-go-hooks-", ".log");
            ProcessBuilder pb = new ProcessBuilder(
                    binary.getAbsolutePath(), "hooks", "install"
            );
            pb.directory(baseDir);
            pb.redirectErrorStream(true);
            pb.redirectOutput(outputFile);

            process = pb.start();

            boolean finished = process.waitFor(HOOKS_INSTALL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new MojoExecutionException(
                        "release-it-go hooks install timed out after " + HOOKS_INSTALL_TIMEOUT_SECONDS + " seconds"
                );
            }

            // Log output after process completes
            for (String line : Files.readAllLines(outputFile.toPath(), StandardCharsets.UTF_8)) {
                getLog().info("[release-it-go] " + line);
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                throw new MojoExecutionException(
                        "release-it-go hooks install failed with exit code " + exitCode
                );
            }

            getLog().info("release-it-go hooks install completed successfully");
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to execute release-it-go: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MojoExecutionException("Execution interrupted", e);
        } finally {
            if (process != null) {
                process.destroyForcibly();
            }
            if (outputFile != null) {
                outputFile.delete();
            }
        }
    }

    /**
     * Gets the version of the installed binary by running "release-it-go version".
     * Redirects output to a temp file to avoid blocking I/O before timeout.
     * Returns null if the binary cannot be executed.
     */
    private String getInstalledVersion(File binary) {
        File outputFile = null;
        Process process = null;
        try {
            outputFile = File.createTempFile("release-it-go-version-", ".log");
            ProcessBuilder pb = new ProcessBuilder(binary.getAbsolutePath(), "version");
            pb.redirectErrorStream(true);
            pb.redirectOutput(outputFile);

            process = pb.start();

            if (!process.waitFor(VERSION_CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                getLog().warn("Version check timed out after " + VERSION_CHECK_TIMEOUT_SECONDS + " seconds");
                return null;
            }

            java.util.List<String> lines = Files.readAllLines(outputFile.toPath(), StandardCharsets.UTF_8);
            if (lines.isEmpty()) {
                return null;
            }
            return lines.get(0);
        } catch (Exception e) {
            getLog().debug("Could not determine installed version: " + e.getMessage());
            return null;
        } finally {
            if (process != null) {
                process.destroyForcibly();
            }
            if (outputFile != null) {
                outputFile.delete();
            }
        }
    }

    /**
     * Ensures binary names are listed in .gitignore.
     * Delegates to PluginUtils for testability.
     */
    private void ensureGitignore(File baseDir) {
        synchronized (InstallMojo.class) {
            try {
                if (PluginUtils.ensureGitignore(baseDir)) {
                    getLog().info("Updated .gitignore with release-it-go binary entries");
                } else {
                    getLog().debug("Binary entries already present in .gitignore");
                }
            } catch (IOException e) {
                getLog().warn("Could not update .gitignore: " + e.getMessage());
            }
        }
    }

    /**
     * Resolves the GitHub token from config or GITHUB_TOKEN environment variable.
     */
    private String resolveToken() {
        if (token != null && !token.isEmpty()) {
            getLog().warn("SECURITY: GitHub token is set in plugin configuration. "
                    + "This may end up in version control. "
                    + "Prefer using the GITHUB_TOKEN environment variable instead.");
            return token;
        }
        String envToken = System.getenv("GITHUB_TOKEN");
        if (envToken != null && !envToken.isEmpty()) {
            getLog().debug("Using token from GITHUB_TOKEN environment variable");
            return envToken;
        }
        return null;
    }
}

package com.emrefirat.releaseItGO;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Downloads the release-it-go binary from GitHub Releases and runs {@code hooks install}
 * in the project directory.
 *
 * <p>Usage in pom.xml:</p>
 * <pre>{@code
 * <plugin>
 *     <groupId>com.emrefirat</groupId>
 *     <artifactId>release-it-go-maven-plugin</artifactId>
 *     <version>1.0.0</version>
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
     * Defaults to the version bundled with this plugin release.
     * Override only if you need a specific version.
     */
    @Parameter(property = "releaseItGo.version", defaultValue = "0.1.3")
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

    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject project;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("release-it-go plugin execution skipped");
            return;
        }

        File baseDir = project.getBasedir();

        // Check for config file
        if (!hasConfigFile(baseDir)) {
            throw new MojoFailureException(
                    "No .release-it-go.yaml (or .json/.toml) config file found in " + baseDir.getAbsolutePath() + "\n"
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
            if (currentVersion != null && !currentVersion.contains(version)) {
                getLog().info("Version mismatch: installed=" + currentVersion + ", required=" + version);
                needsDownload = true;
            } else {
                getLog().info("Binary up to date: " + binaryFile.getAbsolutePath());
            }
        }

        if (needsDownload) {
            getLog().info("Downloading release-it-go v" + version + "...");
            try {
                String resolvedToken = resolveToken();
                BinaryDownloader downloader = new BinaryDownloader(getLog(), version, baseDir, resolvedToken);
                binaryFile = downloader.download();
            } catch (IllegalStateException e) {
                throw new MojoFailureException("Unsupported platform: " + e.getMessage(), e);
            } catch (IOException e) {
                throw new MojoExecutionException("Failed to download release-it-go binary: " + e.getMessage(), e);
            }
        }

        // Run hooks install
        getLog().info("Running: " + binaryFile.getAbsolutePath() + " hooks install");
        runHooksInstall(binaryFile);
    }

    /**
     * Executes the release-it-go binary with "hooks install" arguments.
     * Captures and logs both stdout and stderr. Throws on non-zero exit code.
     */
    private void runHooksInstall(File binary) throws MojoExecutionException {
        ProcessBuilder pb = new ProcessBuilder(
                binary.getAbsolutePath(), "hooks", "install"
        );
        pb.directory(project.getBasedir());
        pb.redirectErrorStream(true);

        try {
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    getLog().info("[release-it-go] " + line);
                }
            }

            int exitCode = process.waitFor();
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
        }
    }

    /**
     * Gets the version of the installed binary by running "release-it-go version".
     * Returns null if the binary cannot be executed.
     */
    private String getInstalledVersion(File binary) {
        try {
            ProcessBuilder pb = new ProcessBuilder(binary.getAbsolutePath(), "version");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                process.waitFor();
                return line;
            }
        } catch (Exception e) {
            getLog().debug("Could not determine installed version: " + e.getMessage());
            return null;
        }
    }

    /**
     * Checks if a release-it-go config file exists in the project directory.
     */
    private boolean hasConfigFile(File baseDir) {
        String[] configFiles = {
                ".release-it-go.yaml", ".release-it-go.yml", ".release-it-go.json", ".release-it-go.toml",
                ".release-it.yaml", ".release-it.yml", ".release-it.json", ".release-it.toml"
        };
        for (String name : configFiles) {
            if (new File(baseDir, name).exists()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Ensures the binary name is listed in .gitignore.
     */
    private void ensureGitignore(File baseDir) {
        String binaryName = PlatformDetector.binaryName();
        File gitignore = new File(baseDir, ".gitignore");

        try {
            if (gitignore.exists()) {
                String content = new String(java.nio.file.Files.readAllBytes(gitignore.toPath()));
                if (content.contains(binaryName)) {
                    return; // Already in .gitignore
                }
                // Append to existing .gitignore
                java.nio.file.Files.write(gitignore.toPath(),
                        ("\n# release-it-go binary\n" + binaryName + "\n").getBytes(),
                        java.nio.file.StandardOpenOption.APPEND);
            } else {
                // Create .gitignore
                java.nio.file.Files.write(gitignore.toPath(),
                        ("# release-it-go binary\n" + binaryName + "\n").getBytes());
            }
            getLog().info("Added " + binaryName + " to .gitignore");
        } catch (IOException e) {
            getLog().warn("Could not update .gitignore: " + e.getMessage());
        }
    }

    /**
     * Resolves the GitHub token from config or GITHUB_TOKEN environment variable.
     */
    private String resolveToken() {
        if (token != null && !token.isEmpty()) {
            getLog().debug("Using token from plugin configuration");
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

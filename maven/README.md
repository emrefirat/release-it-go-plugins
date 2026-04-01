# release-it-go-maven-plugin

Maven plugin that automatically downloads the [release-it-go](https://github.com/emrefirat/release-it-GO) binary from GitHub Releases and runs `hooks install` during the build.

## Usage

Add the plugin to your `pom.xml`:

```xml
<plugin>
    <groupId>com.emrefirat</groupId>
    <artifactId>release-it-go-maven-plugin</artifactId>
    <version>1.1.0</version>
    <!-- version is optional — defaults to the bundled version (currently 0.2.0) -->
    <!-- <configuration><version>0.2.0</version></configuration> -->
    <executions>
        <execution>
            <goals><goal>install</goal></goals>
        </execution>
    </executions>
</plugin>
```

## Configuration

| Parameter | Property | Default | Description |
|-----------|----------|---------|-------------|
| `version` | `releaseItGo.version` | *(bundled at build time)* | The release-it-go version to download |
| `skip` | `releaseItGo.skip` | `false` | Skip plugin execution |
| `token` | `releaseItGo.token` | `$GITHUB_TOKEN` | GitHub token for private repo access |
| `strictChecksum` | `releaseItGo.strictChecksum` | `false` | Fail build if SHA256 checksum cannot be verified. Recommended for CI/CD |

## How It Works

1. During the `initialize` phase, the plugin checks if the binary already exists in the project root.
2. If found, it verifies the version matches — downloads a new one if mismatched.
3. If not found, it downloads the correct platform-specific archive from GitHub Releases.
4. The archive's SHA256 checksum is verified against `checksums.txt` from the release.
5. The archive is extracted (`.tar.gz` on Unix, `.zip` on Windows) and the binary is made executable (on Unix).
6. The binary hash is recorded and the plugin runs `release-it-go hooks install` in the project directory.
7. After execution, the binary hash is re-verified to detect tampering.

## Skipping Execution

```bash
mvn install -DreleaseItGo.skip=true
```

## Private Repositories

For private repos, set `GITHUB_TOKEN` environment variable or configure token in pom.xml:

```bash
export GITHUB_TOKEN=ghp_your_token_here
mvn initialize
```

## Building with a Custom Default Version

When building the plugin, you can specify which release-it-go version to bundle as the default:

```bash
# Bundle with default version (0.2.0)
mvn package

# Bundle with a specific version
mvn package -DreleaseItGo.default.version=0.3.0

# Deploy with a specific version
mvn deploy -DreleaseItGo.default.version=0.3.0
```

Users of the plugin can still override the version in their own `pom.xml` or with `-DreleaseItGo.version=X.Y.Z`.

## Requirements

- Java 8+
- Maven 3.9+
- `tar` command available on PATH (built-in on Unix and Windows 10+)

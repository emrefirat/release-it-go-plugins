# release-it-go-maven-plugin

Maven plugin that automatically downloads the [release-it-go](https://github.com/emrefirat/release-it-GO) binary from GitHub Releases and runs `hooks install` during the build.

## Usage

Add the plugin to your `pom.xml`:

```xml
<plugin>
    <groupId>com.emrefirat</groupId>
    <artifactId>release-it-go-maven-plugin</artifactId>
    <version>1.0.0</version>
    <configuration>
        <version>0.1.3</version> <!-- or omit to use the default -->
    </configuration>
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
| `version` | `releaseItGo.version` | `0.1.3` | The release-it-go version to download |
| `skip` | `releaseItGo.skip` | `false` | Skip plugin execution |
| `token` | `releaseItGo.token` | `$GITHUB_TOKEN` | GitHub token for private repo access |

## How It Works

1. During the `initialize` phase, the plugin checks if the binary already exists in the project root.
2. If found, it verifies the version matches — downloads a new one if mismatched.
3. If not found, it downloads the correct platform-specific archive from GitHub Releases.
4. The archive is extracted (`.tar.gz` on Unix, `.zip` on Windows) and the binary is made executable (on Unix).
5. The plugin runs `release-it-go hooks install` in the project directory.

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

## Requirements

- Java 8+
- Maven 3.9+
- `tar` command available on PATH (built-in on Unix and Windows 10+)

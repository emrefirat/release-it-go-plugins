# PRD: release-it-go Maven Plugin

## Genel Bakis

Maven plugin (`com.emrefirat:release-it-go-maven-plugin`) Java/Maven projelerinde release-it-go Go binary'sini otomatik indirip git hook kurulumu yapan aractir. Developer `mvn compile` calistirir, plugin binary'yi indirir ve `release-it-go hooks install` calistirir. Developer hicbir ek kurulum yapmaz.

## Problem

- Go binary'lerini Java projelerine entegre etmek manuel islem gerektirir
- Her developer binary'yi elle indirmeli, dogru platforma uygun versiyonu secmeli
- Binary versiyonu ile plugin versiyonu senkron tutulmali
- Git hook'lari her developer'in makinesinde ayri ayri kurulmali

## Cozum

Maven `initialize` phase'inde calisan plugin:
1. Platform tespit eder (OS + arch)
2. GitHub Releases'dan dogru binary'yi indirir
3. SHA256 checksum ile dogrular
4. `release-it-go hooks install` calistirarak git hook'larini kurar

## Mimari

```
com.emrefirat.releaseItGO
├── InstallMojo.java        — Ana Mojo (@Mojo, initialize phase)
├── BinaryDownloader.java   — Download, extract, checksum dogrulama
├── PlatformDetector.java   — OS/arch tespit (darwin/linux/windows × amd64/arm64)
└── PluginUtils.java        — Versiyon yonetimi, config kontrolu, yardimci metodlar
```

## Teknik Ozellikler

| Ozellik | Deger |
|---------|-------|
| Java uyumluluk | 1.8+ |
| Maven uyumluluk | 3.9+ |
| External dependency | Yok (Maven API + Java stdlib) |
| Packaging | maven-plugin |
| Default phase | initialize |
| Goal | install |
| Goal prefix | release-it-go |

## Konfigurasyon

| Parametre | Property | Default | Aciklama |
|-----------|----------|---------|----------|
| version | releaseItGo.version | Build-time bundled | release-it-go binary versiyonu |
| skip | releaseItGo.skip | false | Plugin calismasini atla |
| token | releaseItGo.token | $GITHUB_TOKEN | Private repo icin GitHub token |
| strictChecksum | releaseItGo.strictChecksum | false | Checksum dogrulanamaz ise build fail |

## Pipeline Akisi

```
mvn compile
  │
  ├─ 1. Skip kontrolu
  ├─ 2. Versiyon cozumleme (user config veya bundled default)
  ├─ 3. Versiyon format dogrulama (URL injection engelleme)
  ├─ 4. Config dosyasi kontrolu (.release-it-go.yaml)
  ├─ 5. .gitignore guncelleme (binary ekleme)
  ├─ 6. Binary versiyon kontrolu → download gerekli mi?
  ├─ 7. SHA256 checksum dogrulama (checksums.txt)
  ├─ 8. Arsiv extract (tar.gz Unix, zip Windows)
  ├─ 9. Calisma izni ayarlama (chmod +x)
  ├─ 10. Pre-execution hash hesaplama
  ├─ 11. release-it-go hooks install calistirma
  └─ 12. Post-execution hash dogrulama (TOCTOU savunma)
```

## Guvenlik Onlemleri

### Binary Butunluk
- SHA256 checksum dogrulama (checksums.txt ile)
- `strictChecksum` modu: checksum dogrulanamaz ise build fail
- TOCTOU savunma: binary hash'i calistirilmadan once ve sonra karsilastirilir

### Token Guvenligi
- Auth token sadece orijinal host'a gonderilir (redirect'lerde gonderilmez)
- URL'ler log'larda maskelenir (query parametreleri gizlenir)
- Config'de token yazildiginda guvenlik uyarisi verilir
- GITHUB_TOKEN env var tercih edilir

### Input Dogrulama
- Versiyon formati regex ile dogrulanir (path traversal engelleme)
- Zip entry'lerde `..` kontrolu (path traversal engelleme)
- Process execution timeout limitleri (60s hooks install, 10s version check)

### Dosya Izinleri
- Binary sadece owner icin executable (`setExecutable(true, true)`)
- .gitignore synchronized yazma (race condition onleme)

## Download URL Formati

GitHub Releases goreleaser convention'i:
```
https://github.com/emrefirat/release-it-GO/releases/download/v{version}/release-it-go_{version}_{os}_{arch}.tar.gz
https://github.com/emrefirat/release-it-GO/releases/download/v{version}/release-it-go_{version}_{os}_{arch}.zip      (Windows)
https://github.com/emrefirat/release-it-GO/releases/download/v{version}/checksums.txt
```

## Platform Destegi

| OS | Arch | Binary | Arsiv |
|----|------|--------|-------|
| darwin (macOS) | amd64 | release-it-go | .tar.gz |
| darwin (macOS) | arm64 | release-it-go | .tar.gz |
| linux | amd64 | release-it-go | .tar.gz |
| linux | arm64 | release-it-go | .tar.gz |
| windows | amd64 | release-it-go.exe | .zip |

## Versiyon Yonetimi

- Plugin her build'de default release-it-go versiyonunu gomulur (`releaseItGo.default.version` property)
- Kullanici override edebilir: `<version>X.Y.Z</version>` veya `-DreleaseItGo.version=X.Y.Z`
- Mevcut binary varsa versiyon kontrol edilir, uyusmaz ise yeniden indirilir
- Versiyon normalize edilir: "release-it-go 0.1.3 (commit: abc)" → "0.1.3"

## Test Stratejisi

- JUnit 5 unit testler
- JaCoCo code coverage raporlama
- PlatformDetector: OS/arch tespit testleri (tum desteklenen platformlar + edge case'ler)
- PluginUtils: versiyon dogrulama, normalize, config dosyasi kontrolu
- GitHub Actions CI: tag push ile test + publish

## Dagitim

| Kanal | Durum |
|-------|-------|
| GitHub Packages | Aktif |
| Maven Central | Planli |
| JitPack | Alternatif |

## Kullanim

### Temel (default versiyon)
```xml
<plugin>
    <groupId>com.emrefirat</groupId>
    <artifactId>release-it-go-maven-plugin</artifactId>
    <version>1.2.2</version>
    <executions>
        <execution>
            <goals><goal>install</goal></goals>
        </execution>
    </executions>
</plugin>
```

### Versiyon override
```xml
<configuration>
    <version>0.2.0</version>
</configuration>
```

### Strict checksum (CI/CD icin)
```xml
<configuration>
    <strictChecksum>true</strictChecksum>
</configuration>
```

### Atlama
```bash
mvn compile -DreleaseItGo.skip=true
```

## Gelecek Gelistirmeler

- [ ] Maven Central publish
- [ ] Proxy desteği (corporate environment)
- [ ] Offline mod (cached binary, version check atla)
- [ ] release-it-go hooks remove support (uninstall goal)
- [ ] Configurable hooks command (install yerine custom args)

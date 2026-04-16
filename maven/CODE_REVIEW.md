# Code Review Raporu

> **Tarih:** 2026-04-16
> **Reviewer:** Senior Developer Engineer
> **Kapsam:** Tum maven plugin kaynak kodu, testler, CI/CD

---

## Kritik — Guvenlik

### K1. `extractTarGz` — timeout yok

- **Dosya:** `BinaryDownloader.java`
- **Durum:** [x] Cozuldu

`process.waitFor()` timeout olmadan cagriliyordu. 60 saniye timeout eklendi. `EXTRACT_TIMEOUT_SECONDS` sabiti tanimlandi.

---

### K2. `extractTarGz` — path traversal korumasi yok

- **Dosya:** `BinaryDownloader.java`
- **Durum:** [x] Cozuldu

Extract sonrasi `canonicalPath` kontrolu eklendi. Binary hedef dizin disindaysa silinir ve `IOException` firlatilir.

---

### K3. TOCTOU algilama sadece warn

- **Dosya:** `InstallMojo.java`
- **Durum:** [x] Cozuldu

`strictChecksum=true` ise `MojoExecutionException` firlatilir. Default davranis (warn) korundu.

---

### K4. `getInstalledVersion` — readLine() timeout'tan once bloklanabilir

- **Dosya:** `InstallMojo.java`
- **Durum:** [x] Cozuldu

`runHooksInstall` ve `getInstalledVersion` metodlari `ProcessBuilder.redirectOutput(File)` ile yeniden yazildi. Process output temp file'a yonlendirilir, `waitFor(timeout)` cagrilir, sonra dosya okunur. Bloklayici I/O sorunu ortadan kaldirildi.

---

### K5. Download basarisiz olursa mevcut binary kayboluyor

- **Dosya:** `BinaryDownloader.java`
- **Durum:** [x] Cozuldu

Eski binary indirmeden once siliniyordu. Download, checksum veya extract basarisiz olursa calisan binary kayboluyordu. Backup-restore mekanizmasi eklendi:
- Mevcut binary `.bak` olarak yedeklenir
- Basarili ise yedek silinir
- Basarisiz ise yedekten geri yuklenir
- `.bak` dosyasi her durumda temizlenir (`deleteOnExit` fallback)

---

## Yuksek — Dogruluk

### Y1. `version` field'i mutasyona ugriyor

- **Dosya:** `InstallMojo.java`
- **Durum:** [x] Cozuldu

`this.version` field'i artik degistirilmiyor. Local `resolvedVersion` degiskeni kullaniliyor.

---

### Y2. `getDefaultVersion` — IOException sessizce yutulur

- **Dosya:** `PluginUtils.java`
- **Durum:** [x] Cozuldu

`getDefaultVersion(List<String> fallbackReason)` overload'u eklendi. Fallback sebebi list'e yazilir. `InstallMojo` fallback durumunda `warn` logu yaziyor.

---

### Y3. `verifyChecksum` testleri aslinda checksum dogrulamayi test etmiyor

- **Dosya:** `BinaryDownloaderTest.java`
- **Durum:** [x] Cozuldu

`parseExpectedHash()` static metodu cikarildi. 10 yeni test eklendi: matching, not-found, empty lines, case normalization, tab separator, Windows line endings, partial match, null inputs.

---

## Orta — Saglamlik

### O1. `ensureGitignore` — platform-specific entry

- **Dosya:** `PluginUtils.java` (tasinmis)
- **Durum:** [x] Cozuldu

Her iki binary adi (`release-it-go` ve `release-it-go.exe`) ekleniyor. Cross-platform takim destegi saglandi.

---

### O2. `ensureGitignore` — `content.contains()` false positive

- **Dosya:** `PluginUtils.java` (tasinmis)
- **Durum:** [x] Cozuldu

Satir bazli kontrol: `split("\\r?\\n")` ile satirlara bolunup `trim().equals()` karsilastirmasi yapiliyor.

---

### O3. `detectOS` — `"win"` cok genis eslesir

- **Dosya:** `PlatformDetector.java`
- **Durum:** [x] Cozuldu

`name.contains("win")` kaldirildi, sadece `name.contains("windows")` kullaniliyor. Test eklendi.

---

### O4. `checksums.txt` silme garantisi yok

- **Dosya:** `BinaryDownloader.java`
- **Durum:** [x] Cozuldu

`delete()` basarisiz olursa `deleteOnExit()` fallback + warn logu eklendi.

---

### O5. `ensureGitignore` — test yok

- **Dosya:** `PluginUtils.java` + `PluginUtilsTest.java`
- **Durum:** [x] Cozuldu

Mantik `PluginUtils.ensureGitignore()` static metoduna tasindi. 6 yeni test eklendi: yeni dosya olusturma, mevcut dosyaya ekleme, zaten mevcut, kismi mevcut, false positive kontrolu, trailing newline.

---

## Dusuk — Test Eksiklikleri

### D1. Test coverage bosluklari

- **Durum:** Kismi cozum

| Metod | Dosya | Test Durumu |
|-------|-------|-------------|
| `ensureGitignore` | PluginUtils | [x] 6 test eklendi |
| `parseExpectedHash` | BinaryDownloader | [x] 10 test eklendi |
| `getDefaultVersion` (fallback) | PluginUtils | [x] 2 test eklendi |
| `resolveToken` | InstallMojo | [ ] Private — integration test gerektirir |
| `runHooksInstall` | InstallMojo | [ ] Private — integration test gerektirir |
| `getInstalledVersion` | InstallMojo | [ ] Private — integration test gerektirir |
| `downloadFile` | BinaryDownloader | [ ] Network gerektirir |

> **Not:** Kalan 4 metod `private` ve Maven plugin context'i veya network erisimi gerektiriyor.
> Integration test altyapisi (mock Maven context veya embedded HTTP server) kuruldugunda bu testler eklenebilir.

---

## CI/CD

### C1. Tag-pom versiyon uyumsuzlugu kontrolu yok

- **Dosya:** `.github/workflows/publish.yml`
- **Durum:** [x] Cozuldu

Publish step'ten once versiyon dogrulama step'i eklendi. `GITHUB_REF` shell env var olarak okunur (injection-safe), `mvn help:evaluate` ile pom versiyonu karsilastirilir. Uyusmaz ise build fail olur.

---

### C2. Platform matrix testi yok

- **Dosya:** `.github/workflows/publish.yml`
- **Durum:** [x] Cozuldu

Test job'ina OS matrix eklendi: `ubuntu-latest`, `macos-latest`, `windows-latest`. Tum platformlarda testler calistirilir.

---

## Ozet

| Kategori | Toplam | Cozuldu | Acik |
|----------|--------|---------|------|
| Kritik (K) | 5 | 5 | 0 |
| Yuksek (Y) | 3 | 3 | 0 |
| Orta (O) | 5 | 5 | 0 |
| Dusuk (D) | 1 | kismi | 4 metod integration test bekliyor |
| CI/CD (C) | 2 | 2 | 0 |

**Test sayisi:** 76 → 93 (+17 yeni test)
**Build durumu:** SUCCESS

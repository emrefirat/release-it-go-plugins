# release-it-go-plugins

> release-it-go icin build tool entegrasyon plugin'leri. Her plugin kendi dizininde bagimsiz yasam dongusune sahip.

## Proje Yapisi

```
release-it-go-plugins/
├── maven/                          ← Maven plugin (Java)
│   ├── pom.xml                     # Build config, dependency, dagitim
│   ├── PRD.md                      # Detayli urun gereksinimleri
│   ├── README.md                   # Kullanim dokumantasyonu
│   └── src/
│       ├── main/java/com/emrefirat/releaseItGO/
│       │   ├── InstallMojo.java        # Ana Mojo (initialize phase)
│       │   ├── BinaryDownloader.java   # Download + extract + checksum
│       │   ├── PlatformDetector.java   # OS/arch tespit
│       │   └── PluginUtils.java        # Versiyon, config, yardimci
│       ├── main/resources/
│       │   └── release-it-go-defaults.properties  # Build-time versiyon
│       └── test/java/com/emrefirat/releaseItGO/
│           └── PlatformDetectorTest.java
├── npm/                            ← Gelecek: npm package
├── gradle/                         ← Gelecek: Gradle plugin
├── .github/workflows/
│   └── publish.yml                 # Tag push → test → GitHub Packages
├── CLAUDE.md                       # Bu dosya
├── README.md
└── .gitignore
```

## Build Komutlari

```bash
cd maven && mvn clean test          # Testleri calistir
cd maven && mvn clean verify        # Test + JaCoCo coverage
cd maven && mvn clean install       # Local .m2'ye kur
cd maven && mvn deploy              # GitHub Packages'a publish
```

## Zorunlu Kurallar

### Java Kod Kurallari

1. **Java 8 uyumluluk zorunlu** — `var`, modules, records, text blocks, switch expressions KULLANMA
2. **External dependency YASAK** — sadece Maven Plugin API + Java stdlib
3. **Her yeni fonksiyon icin test zorunlu** — JUnit 5
4. **Error handling** — checked exception'lar her zaman handle edilmeli, sessiz yutma yok
5. **Utility class'lar** — private constructor, final class
6. **Process execution** — her zaman timeout ile (`waitFor(timeout, unit)`)
7. **File I/O** — her zaman try-with-resources, charset belirt (UTF-8)

### Guvenlik Kurallari

1. **SHA256 checksum dogrulama** — indirilen her binary dogrulanmali
2. **Auth token redirect'te gonderilmez** — sadece orijinal host'a
3. **URL maskeleme** — log'larda query parametreleri gizlenmeli
4. **TOCTOU savunma** — binary hash calistirilmadan once ve sonra karsilastirilmali
5. **Zip path traversal** — entry'lerde `..` kontrolu
6. **Versiyon format dogrulama** — regex ile URL injection engelleme
7. **Token config'de yazilmaz** — env var tercih edilmeli, config'de uyari ver
8. **Sensitive bilgi loglanmaz** — token, sifre, imzali URL

### Commit Kurallari

1. Conventional commits formati: `feat:`, `fix:`, `test:`, `docs:`, `chore:`
2. **Co-Authored-By satiri ASLA eklenmez**
3. Commit oncesi `mvn clean test` basarili olmali
4. Versiyon degisikligi icin pom.xml guncellenmeli

### Test Kurallari

1. JUnit 5 kullan
2. JaCoCo coverage raporlama aktif
3. Static method'lar icin overloaded test-friendly versiyonlar yaz (orn: `detectOS(String)`)
4. Edge case'ler: null input, bos string, bilinmeyen platform

## Onemli Dosyalar

| Dosya | Aciklama |
|-------|----------|
| `maven/pom.xml` | Build config, versiyon, dependency, dagitim |
| `maven/PRD.md` | Detayli urun gereksinimleri ve teknik dokumantasyon |
| `maven/README.md` | Kullanici dokumantasyonu |
| `maven/src/main/resources/release-it-go-defaults.properties` | Build-time gomulu default versiyon |
| `.github/workflows/publish.yml` | CI/CD: test + GitHub Packages publish |

## release-it-go Binary Bilgileri

- GitHub repo: `github.com/emrefirat/release-it-GO`
- Binary isimlendirme: goreleaser convention — `release-it-go_{version}_{os}_{arch}.tar.gz`
- Windows: `.zip` uzantisi
- Checksums: `checksums.txt` (SHA256)
- Default versiyon: `pom.xml` icindeki `releaseItGo.default.version` property'si
- Build-time override: `mvn package -DreleaseItGo.default.version=X.Y.Z`

## Plugin Nasil Calisir

```
Developer: mvn compile
  │
  ├─ initialize phase tetiklenir
  ├─ InstallMojo.execute()
  │   ├─ Config dosyasi var mi? (.release-it-go.yaml)
  │   ├─ .gitignore'a binary eklenmis mi?
  │   ├─ Binary var mi? Versiyon dogru mu?
  │   │   └─ Hayir → BinaryDownloader.download()
  │   │       ├─ PlatformDetector.detectOS/Arch()
  │   │       ├─ GitHub Releases'dan indir
  │   │       ├─ SHA256 checksum dogrula
  │   │       └─ Extract (tar.gz/zip)
  │   ├─ Pre-execution hash hesapla
  │   ├─ release-it-go hooks install calistir
  │   └─ Post-execution hash dogrula
  │
  └─ .hooks/ dizinine git hook'lari kurulur
```

## Yeni Plugin Eklerken (npm, gradle)

1. Root dizinde yeni klasor olustur (`npm/`, `gradle/`)
2. Kendi build config'ini ekle (`package.json`, `build.gradle`)
3. Ayni pattern: binary indir → hooks install calistir
4. `.github/workflows/` altina CI/CD ekle
5. Root `README.md`'yi guncelle

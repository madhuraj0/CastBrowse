# CastBrowse

A lightweight, native Android web browser designed with robust privacy-hardening measures, tabbed web browsing, dynamic HTML5 video extraction, and local network casting capability (supporting the open-source FCast protocol).

---

## 👥 Authors & Collaborators

This project was built collaboratively as a pair-programming project:

* **User (Product Director & Lead Architect)**:
  * Conceived the core application concept and casting flows.
  * Designed the visual interface, Chrome-style Economical Redesign, and overall UX aesthetics.
  * Architected dynamic UI layouts, theme options (Light, Dark, AMOLED, and dynamic Material You).
  * Diagnosed and troublesought critical receiver playback bottlenecks, including network sniffing / container mimetype mapping.
* **Antigravity (AI Software Engineer)**:
  * Partnered with the Lead Architect to implement the Kotlin/Compose codebases.
  * Implemented the local proxy server to bypass header restrictions and cookie authentication issues.
  * Created custom adaptive vector layouts and designs for the launcher icons.
  * Developed the raw TCP socket integration for the FCast protocol, network service discovery (SSDP/NSD), and security sanitization checks.

---

## 💎 Credits & Attributions

We would like to express our gratitude to the creators of the libraries and tools that made this project possible:

* **[FCast Protocol & Receiver](https://fcast.org/)**: The outstanding open-source protocol and receiver client that allows for lightweight, cross-platform media casting without proprietary locks.
* **Google Android Team**: For **Jetpack Compose** (UI declarative engine), **ExoPlayer/Media3** (multimedia player framework), and **ML Kit** (Barcode QR scanning api).
* **Square**: For **OkHttp** (networking client).
* **Kotlin Team**: For Kotlin Coroutines and Serialization utilities.

---

## 🔒 Privacy & Security Hardening

CastBrowse is built with privacy and security compliance at its core:

1. **Privacy-Rigid Web Browser (`SecureWebView`)**:
   - Restricts persistent cookies, DOM storage, and disables local file access schemes (`file://`, `content://`).
   - Automatically wipes cookies, DOM storage, local databases, and temporary caches between actions.
   - **Panic Button**: Instantly clears cache/history/cookies, resets session states, and terminates the application process (`System.exit(0)`) to clear memory.

2. **Security Compliance**:
   - **Anti-Screen Capture**: Enforces `WindowManager.LayoutParams.FLAG_SECURE` to block screenshot utilities, screen recorders, and background projection.
   - **Disables Device Backup**: Formulated with `android:allowBackup="false"` to prevent local database or session extracts via ADB backup.
   - **Encrypted Preferences**: Integrates `EncryptedSharedPreferences` backed by the Android Keystore system.
   - **AMOLED Dark Mode**: Supports true black backgrounds (`#000000`) for absolute contrast and low-light eye comfort.

3. **Dynamic Video Extractor (`MediaExtractorClient`)**:
   - **Network Request Sniffer**: Inspects ongoing network calls inside the WebView client to capture active media links (`.mp4`, `.webm`, `.m3u8`, `.m3u`, `.mpd`).
   - Displays a dynamic Float Action Button (FAB) listing discovered streams.
   - **Stream Details Panel**: Displays stream URLs, size, resolution, container type, and provides copy-to-clipboard options.

---

## 🏗️ Project Structure

```
CastBrowse/
├── gradle/
│   └── libs.versions.toml             # Declares dependencies and versions catalog
├── app/
│   ├── build.gradle.kts               # Module level dependencies and compile SDKs
│   ├── proguard-rules.pro             # Proguard optimization configs
│   └── src/
│       └── main/
│           ├── AndroidManifest.xml    # App configurations & permissions
│           ├── res/
│           │   ├── drawable/          # Vector launcher backgrounds/foregrounds
│           │   ├── mipmap-anydpi-v26/ # Adaptive icons support
│           │   ├── values/            # Base styles and text values
│           │   └── xml/               # Network security configs (cleartext control)
│           └── java/com/castbrowse/app/
│               ├── MainActivity.kt    # Compose UI, search routing & dialogs
│               ├── SecureWebView.kt   # Hardened web engine subclass
│               ├── MediaExtractorClient.kt # Network sniffer & JS interfaces
│               ├── SsdpDiscoveryService.kt # SSDP & NSD network scanners
│               ├── FCastClient.kt     # Raw TCP FCast protocols
│               └── EncryptedStorage.kt # Secure SharedPreferences wrapper
├── build.gradle.kts                   # Project root build configurations
├── settings.gradle.kts                # Core repositories & modules definition
└── local.properties                   # Path to local Android SDK (automatically configured)
```

---

## 🛠️ How to Build & Run

Ensure you have Java 17 and Android SDK installed on your machine.

### 1. Configure Release Signing (Anonymous)
To compile a fully signed release build, create a file named `app/secrets.properties` in your local directory (this file is excluded from version control in `.gitignore`):
```properties
RELEASE_STORE_FILE=castbrowse-release.jks
RELEASE_STORE_PASSWORD=castbrowse123
RELEASE_KEY_ALIAS=castbrowse-key
RELEASE_KEY_PASSWORD=castbrowse123
```
Then generate your local anonymous release keystore:
```bash
keytool -genkeypair -v -keystore app/castbrowse-release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias castbrowse-key -storepass castbrowse123 -keypass castbrowse123 -dname "CN=CastBrowse Developer, OU=Mobile, O=CastBrowse Open Source, L=Internet, S=Global, C=US"
```

### 2. Build Release APK
To compile resources and generate a fully signed release APK:
```bash
./gradlew assembleRelease
```
The compiled APK will be located at:
`app/build/outputs/apk/release/app-release.apk`

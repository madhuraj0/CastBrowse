<p align="center">
  <img src="logo.svg" alt="CastBrowse Logo" width="120" height="120">
</p>

# CastBrowse

A lightweight Android web browser with privacy-hardening, tabbed browsing, media extraction, and casting support for the open-source [FCast protocol](https://fcast.org/).

> ⚠️ **Disclaimer**: The entire code of this project was generated using Antigravity (AI coding assistant) and is a completely **vibecoded** project. Inspect the code before building or installing.

---

## 🔒 Privacy & Security Features

* **Privacy Browser**: Disables persistent cookies, DOM storage, and local file access schemes. Wipes cache and state on close.
* **Ad Blocking**: Logarithmic O(d) right-to-left blocker powered by [Steven Black's Hosts](https://github.com/StevenBlack/hosts) (auto-updated weekly in the background, or manually in-app).
* **1-Tap Session Clear**: Instantly clears browser data, resets session state, and terminates the application process.
* **Anti-Screen Capture**: Enforces `FLAG_SECURE` to block screenshots and recording.
* **No Backups**: `allowBackup="false"` prevents local database extracts via ADB.
* **AMOLED Dark Mode**: True black backgrounds (`#000000`) for low-light comfort.
* **Stream Details Panel**: Displays type, resolution, size, and URL of detected streams with copy-to-clipboard actions.

---

## 🛠️ Build & Run

### 1. Configure Signing Credentials
Create `app/secrets.properties`:
```properties
RELEASE_STORE_FILE=castbrowse-release.jks
RELEASE_STORE_PASSWORD=castbrowse123
RELEASE_KEY_ALIAS=castbrowse-key
RELEASE_KEY_PASSWORD=castbrowse123
```

Generate keystore:
```bash
keytool -genkeypair -v -keystore app/castbrowse-release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias castbrowse-key -storepass castbrowse123 -keypass castbrowse123 -dname "CN=CastBrowse Developer, OU=Mobile, O=CastBrowse Open Source, L=Internet, S=Global, C=US"
```

### 2. Build Release APK
```bash
./gradlew assembleRelease
```
Output: `app/build/outputs/apk/release/app-release.apk`

---

## 💎 Credits & Attributions

* **[FCast Protocol & Receiver](https://fcast.org/)**: Open-source lightweight casting framework.
* **[Steven Black's Hosts](https://github.com/StevenBlack/hosts)**: Extensible host-based adblocking list.
* **Google Android Team**: Jetpack Compose, ExoPlayer/Media3, and ML Kit Barcode scanning.

---

## 👥 Collaborators

* **Antigravity** (AI Assistant): Coding, Asset creation
* **[madhuraj0](https://github.com/madhuraj0)**: Idea, Design direction, Testing

---

## 📄 License

Licensed under the [MIT License](LICENSE).

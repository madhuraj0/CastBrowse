# CastBrowse Project Roadmap

This document outlines the planned architectural milestones, system features, and enhancements for upcoming releases of CastBrowse.

---

## 🧭 Roadmap Milestones

### 1. System Integration & Browser Intent
- **System Browser Association**: Register CastBrowse with `<intent-filter>` for `http` and `https` schemes (`android.intent.action.VIEW` with categories `DEFAULT` and `BROWSABLE`) so users can set CastBrowse as their default browser or choose it from the "Open With..." Android app disambiguation menu.
- **Link & Text Selection Context Menu**: Implement `android.intent.action.PROCESS_TEXT` so when a user highlights any URL in any app (Chrome, WhatsApp, Telegram, etc.), a system context action menu option **"Cast with CastBrowse"** appears to immediately fetch and cast the stream to the TV.
- **Quick Settings (QS) Tile**:
  - Add a Quick Settings tile in Android system shade (`TileService`).
  - Tapping reads the current clipboard URL: if a link is present, automatically extracts media and prompts to cast; otherwise, launches CastBrowse directly to the Streams detection hub.

---

### 2. Universal Protocol Casting
- **Proprietary & Standard Casting Protocols**: Expand beyond FCast to support:
  - **Google Cast (Chromecast)**: Google Cast SDK integration for direct casting to Chromecast and Nest Hub devices.
  - **DLNA / UPnP**: Universal plug-and-play casting supported natively by Samsung Tizen, LG webOS, and older smart TVs without companion apps.
  - **Apple AirPlay**: Support AirPlay 1/2 streaming to Apple TVs and AirPlay-enabled receivers.
  - **DIAL (Discovery and Launch)**: Direct second-screen launch support for YouTube and Smart TV apps.

---

### 3. Remote Controller & Media Experience
- **Advanced Playback Controller**:
  - "Start from Beginning" (seek to 0:00).
  - Fast Forward / Rewind with customizable jump steps (10s, 30s, 60s).
  - "Play in Loop" toggle.
  - In-controller "Download to Device" button.
  - Save stream state / stream bookmarks with active session headers (Referer, Cookies, User-Agent) preserved.
- **Subtitle & Caption Injection**: Support external subtitles (`.srt`, `.vtt` sidecars) either detected from web pages or loaded from device storage, multiplexed into the cast stream.
- **Aspect Ratio Control**: Remote aspect ratio adjustment (Fit, Fill, Zoom, 16:9, 4:3, 21:9 anamorphic) sent to receiver.
- **System Media Notification & Lock Screen Controls**: Integrate `androidx.media3.session.MediaSession` and `MediaNotificationProvider` to provide standard Android lock screen and notification shade media controls (Play/Pause, Seek bar, Next/Prev).

---

### 4. Offline Audio & Photo Casting
- **Offline Music / Audio Hub**:
  - Dedicated "Music / Audio" sub-tab in Streams screen scanning offline audio files (`.mp3`, `.flac`, `.aac`, `.wav`, `.m4a`).
  - Casts audio streams to receivers while rendering an **absolute pitch-black screen** (`#000000`) on the TV to protect OLED displays and save energy.
- **Photo Slideshow Casting**:
  - Image gallery picker (folders or multi-photo selection).
  - Cast high-resolution local photo slideshows with configurable transition intervals.

---

### 5. UI/UX Polish & Design Consistency
- **Design Language System**: Harmonize typography, border radii, tactile pressed states, haptic feedback, and dynamic color/AMOLED dark themes across all activities and dialogs.
- **Responsive Tablet & Foldable Layout**: Split-screen two-pane layout for tablets, foldables, and Chromebooks (browser on left, streams list on right).

---

### 6. Engineering Quality & Verification
- **Automated Testing Suite**:
  - Unit tests for stream extraction regex, HLS manifest rewriting, and adblock logarithmic parser.
  - Integration tests for `LocalMediaProxy` HTTP Range requests (206 Partial Content) and headers propagation.
  - Instrumented UI tests verifying WebView tab state preservation and navigation.

---

### 7. Licensing, Compliance & Release Readiness
- **Dependency Audit**: Full audit of third-party open-source licenses and compliance with MIT, Apache 2.0, and GPL guidelines.
- **Production Readiness**: Proguard/R8 optimization review, reproducible builds, automated GitHub Actions CI/CD release pipeline, and store metadata preparation.

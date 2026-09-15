# CastBrowse Roadmap

### 1. System Integration
- [x] **Default Browser Handler**: Register `VIEW` intent for `http`/`https` links.
- [x] **Link Context Menu**: System text selection action (`PROCESS_TEXT`) to "Cast with CastBrowse".
- [x] **Quick Settings Tile**: Status bar tile to cast clipboard link or open Streams hub.

### 2. Universal Protocols & Connectivity
- [x] **Universal Protocols**: Native AirPlay 1, DLNA / UPnP, DIAL multiscreen, and Google Cast discovery.
- [x] **HTML5 Web Receiver (`/tv`)**: Built-in web player served via local proxy for TVs without app stores (LG webOS, Samsung Tizen, PlayStation/Xbox).
- [x] **Hotspot / Travel Mode**: Cast via phone's Wi-Fi hotspot without an external router (auto-bind to `192.168.43.1`).
- [x] **Network Diagnostics**: 1-tap TCP reachability check with latency (ms); VPN detection and split-tunneling warnings.

### 3. Playback & Remote Control
- [x] **Extended Controls**: Jump steps (10s/30s), restart (0:00), loop, and aspect ratio toggles (16:9, Fill, Zoom, Original).
- [x] **Queue / Play Next**: Continuous playlist playback for binge-watching.
- [x] **Resume Playback**: Save and restore timestamp across sessions.
- [x] **A/V & Subtitle Sync**: Audio delay and subtitle offset sliders (+/- 500ms and +/- 2000ms).
- [x] **Audio Track Selector**: Multi-audio stream selection for dual-language/commentary media.
- [x] **External Subtitles**: Inject local `.srt`/`.vtt` files into cast streams.
- [x] **System Media Session**: Android lock screen and notification media controls (`MediaSession`).

### 4. Stream Extraction Intelligence
- [x] **Deep iFrame Scraping**: Detect streams within nested cross-origin sandbox iframes.
- [x] **User-Agent Presets**: 1-tap presets (iPad Safari, Smart TV) to bypass anti-mobile stream blocks.
- [x] **DRM Detection**: Graceful notification for Widevine-protected content that cannot be cast.

### 5. Media Hub
- [x] **Offline Audio Mode**: Music player tab that casts audio while displaying a pure black screen (`#000000`) on TV.
- [x] **Photo Slideshows**: Cast local folders or selected photos with transition timers.

### 6. UI Polish & Aesthetics
- [ ] **App-Wide Glassmorphic Design Language**: Extend a unified frosted-glass aesthetic throughout the entire application—applying translucent acrylic surfaces, specular rim gradients, soft ambient depth, and tactile micro-interactions to headers, cards, dialogs, and controller panels.
- [ ] **Floating Glassmorphic Bottom Bar**: Floating translucent capsule with specular rim gradient, liquid selection indicator, and automatic system inset handling.
- [ ] **Home & New Tab Bookmarks / Speed Dial**: Pin, add, and manage favorite streaming site shortcuts and bookmarks on the home / new tab page.
- [ ] **Fluid Physics & Micro-Animations**: Smooth spring-based transitions, liquid page shifts, sheet expands, and tactile touch feedback across all screens.
- [ ] **Desktop Site Icon**: Replace gear icon with monitor/desktop icon.
- [ ] **Dismiss Individual Items**: Delete single stream or device video from cards.

### 7. Code Quality, Testing & CI/CD
- [ ] **Unit & Integration Tests**: Test suite for proxy partial content (206), HLS rewrites, and adblock.
- [ ] **Licensing & Release CI/CD**: License audit, ProGuard optimization, and automated GitHub Actions release workflow.

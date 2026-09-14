# CastBrowse Roadmap

### 1. System Integration
- **Default Browser Handler**: Register `VIEW` intent for `http`/`https` links.
- **Link Context Menu**: System text selection action (`PROCESS_TEXT`) to "Cast link to TV".
- **Quick Settings Tile**: Status bar tile to cast clipboard link or open Streams hub.

### 2. Universal Protocols & Connectivity
- **Additional Protocols**: Chromecast (Google Cast), DLNA / UPnP, AirPlay, and DIAL.
- **HTML5 Web Receiver (`/tv`)**: Built-in web player served via local proxy for TVs without app stores (LG webOS, Samsung Tizen, PlayStation/Xbox).
- **Hotspot / Travel Mode**: Cast via phone's Wi-Fi hotspot without an external router (auto-bind to `192.168.43.1`).
- **Network Diagnostics**: 1-tap ping and port reachability check; VPN split-tunneling warning.

### 3. Playback & Remote Control
- **Extended Controls**: Jump steps (10s/30s), restart (0:00), loop, and aspect ratio toggles (16:9, Fill, Zoom).
- **Queue / Play Next**: Continuous playlist playback for binge-watching.
- **Resume Playback**: Save and restore timestamp across sessions.
- **A/V & Subtitle Sync**: Audio delay and subtitle offset sliders (+/- 500ms).
- **Audio Track Selector**: Multi-audio stream selection for dual-language/commentary media.
- **External Subtitles**: Inject local `.srt`/`.vtt` files into cast streams.
- **System Media Session**: Android lock screen and notification media controls (`MediaSession`).

### 4. Stream Extraction Intelligence
- **Deep iFrame Scraping**: Detect streams within nested cross-origin sandbox iframes.
- **User-Agent Presets**: 1-tap presets (iPad Safari, Smart TV) to bypass anti-mobile stream blocks.
- **DRM Detection**: Graceful notification for Widevine-protected content that cannot be cast.

### 5. Media Hub
- **Offline Audio Mode**: Music player tab that casts audio while displaying a pure black screen (`#000000`) on TV.
- **Photo Slideshows**: Cast local folders or selected photos with transition timers.

### 6. UI Polish & Quality
- **App-Wide Glassmorphic Design Language**: Extend a unified frosted-glass aesthetic throughout the entire application—applying translucent acrylic surfaces, specular rim gradients, soft ambient depth, and tactile micro-interactions to headers, cards, dialogs, and controller panels (not limited to the navbar).
- **Floating Glassmorphic Bottom Bar**: Floating translucent capsule with specular rim gradient, liquid selection indicator, and automatic system inset handling.
- **Desktop Site Icon**: Replace gear icon with monitor/desktop icon.
- **Dismiss Individual Items**: Delete single stream or device video from cards.
- **Unit & Integration Tests**: Test suite for proxy partial content (206), HLS rewrites, and adblock.
- **Licensing & Release CI/CD**: License audit, ProGuard optimization, and automated GitHub Actions release workflow.

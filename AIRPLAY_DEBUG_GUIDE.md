# AirPlay Casting Handoff & Debugging Guide for agy CLI Agent

This document is intended for the `agy` CLI agent running on the local PC to troubleshoot and finalize AirPlay video casting in **CastBrowse**.

---

## 1. Context & Symptoms

### What works:
- **Device Discovery**: AirPlay receivers (both macOS and Android AirPlay server apps) are discovered via mDNS (`_airplay._tcp.`).
- **Handshake & Session Setup**: The modern AirPlay binary plist (`application/x-apple-binary-plist`), session ID (`X-Apple-Session-ID`), and pre-flight checks are implemented.
- **Initial Connection Notification**: When casting starts, the target AirPlay receiver accepts the connection (macOS prompt or Android AirPlay server "connected" notification).
- **CastBrowse State**: The app transitions to "Playing" state and initiates playback polling.

### What fails (The "Blink and Idle" Bug):
- Immediately after the receiver accepts the connection, the target screen blinks or briefly shows a loading indicator, but **no video or audio plays**.
- The receiver exits playback mode or returns to the home screen, while CastBrowse continues showing "Playing".

---

## 2. Recent Code Changes Committed

1. **`AirPlayClient.kt`**:
   - Modern binary plist payload generation (`createPlayBinaryPlist`) containing `Content-Location` (string) and `Start-Position` (float 0.0) required by macOS Monterey+ and tvOS 10.2+.
   - Automatic pre-flight wake-up (`GET /info` or `GET /server-info`).
   - Explicit `POST /rate?value=1.0` sent immediately following `/play` to force the receiver from buffering/paused to playing state.
   - Polling fallback to `/playback-info` when `/scrub` is rejected or deprecated with HTTP 404/500 by modern AirPlay targets.
   - Disconnect tolerance increased to 20 consecutive seconds of failure before tearing down the session.

2. **`MainActivity.kt`**:
   - For AirPlay targets, public web video streams without restrictive cookies or referers are now sent **directly** (`videoUrl`) to the receiver rather than passing through `LocalMediaProxy`. This allows the receiver to fetch and decode directly using native hardware and complies with Apple ATS (App Transport Security) requirements.

3. **`LocalMediaProxy.kt`**:
   - Added `Accept-Ranges: bytes` to proxy HTTP responses (essential for Apple AVPlayer and ExoPlayer seeking/metadata probes).
   - Added network capability inspection to bind upstream connections to the active Wi-Fi interface.

---

## 3. Step-by-Step Troubleshooting Plan (Simple to Advanced)

> [!IMPORTANT]
> **Rule for the Local Agent**: Start from Phase 1 and test systematically. Do NOT introduce complex networking or proxy hacks before verifying the simpler fundamental causes.

### Phase 1: Simple & Direct Verifications (Zero-Code)

1. **Test with Tailscale / VPN completely disconnected on the phone**:
   - *Why*: When Tailscale is active on Android, it sets the system DNS to `100.100.100.100` (`PrivateDnsBroken`). If any proxying occurs or background sockets resolve hostnames, DNS queries may fail with `UnknownHostException`.
   - *Action*: Disconnect Tailscale on the phone. Ensure the phone, PC, and AirPlay target (Android tablet / Mac) are on the same local Wi-Fi network (e.g. `192.168.1.0/24`). Test casting.

2. **Test with a Known-Good Standard MP4 Video**:
   - *Why*: AirPlay receivers (especially macOS AVPlayer) strictly reject formats like WebM, VP8, VP9, AVI, or non-H.264/AAC containers. If an unsupported codec is sent, the receiver blinks and immediately dismisses the player without an error dialog. Also note that some older sample URLs (like Google's old BigBuckBunny GCS bucket) now return HTTP 403 Forbidden.
   - *Action*: Cast this verified public test stream:
     `https://vjs.zencdn.net/v/oceans.mp4`

3. **Use the "Android AirPlay" receiver for rapid iteration**:
   - As requested by the user, test primarily with the Android AirPlay server app on the tablet, as it does not require interactive confirmation prompts on every connection.

---

### Phase 2: Inspect Receiver Error Logs

1. **On macOS AirPlay Receiver**:
   - Run this in macOS Terminal while triggering playback:
     ```bash
     log stream --predicate 'subsystem contains "AirPlay" or process contains "AirPlay"' --level debug
     ```
   - Look for:
     - `AVPlayerItem` status changes or failed reasons.
     - `ATS block` (App Transport Security blocking plain unencrypted `http://` URLs).
     - Format / codec errors (`CoreMedia` or `VideoToolbox` errors).
     - SSL handshake or certificate validation errors.

2. **On Phone Logcat**:
   - Monitor the CastBrowse logs:
     ```bash
     adb logcat -s AirPlayClient,LocalMediaProxy,MainActivity,CastSessionManager
     ```
   - Check the exact URL being sent in:
     `AirPlay: Playing '<title>' (<url>) on <ip>:<port>`

---

### Phase 3: Direct Stream vs Proxy Stream Behavior

1. **Determine whether the URL is Direct or Proxied**:
   - Look at `MainActivity.kt` around line 849:
     ```kotlin
     val proxiedUrl = if (videoUrl.contains("/local?id=")) {
         videoUrl
     } else if (device.protocol == CastProtocol.AIRPLAY && (headers["Cookie"].isNullOrEmpty() && headers["Referer"].isNullOrEmpty())) {
         videoUrl
     } else {
         LocalMediaProxy.getProxyUrl(videoUrl, headers, device.ipAddress)
     }
     ```
   - If `proxiedUrl` starts with `http://192.168.1.x:8085/proxy?url=...`:
     - From your local PC or receiver, verify the proxy is reachable:
       ```bash
       curl -I "http://<phone-ip>:8085/proxy?url=<encoded-url>"
       ```
     - Ensure the proxy responds with `HTTP/1.1 200 OK` or `206 Partial Content` and contains `Accept-Ranges: bytes`.
   - If Apple AVPlayer blocks unencrypted `http://` proxy URLs due to ATS:
     - Note that modern Apple AirPlay receivers enforce ATS and will refuse to play `http://` streams from local proxies unless served over HTTPS or if direct `https://` URLs are sent.

---

### Phase 4: Advanced AirPlay Protocol Variations

If the target receiver is an Android AirPlay server app that expects legacy protocol nuances:
1. **Binary plist vs text/parameters**:
   - In `AirPlayClient.kt`, step 2 sends binary plist and step 3 falls back to `text/parameters`.
   - Some third-party Android AirPlay servers respond with HTTP 200 to binary plist even if they don't actually parse it, then get stuck.
   - If Android AirPlay is failing, test sending `text/parameters` first:
     ```http
     POST /play HTTP/1.1
     Content-Type: text/parameters
     Content-Length: ...
     User-Agent: MediaControl/1.0
     X-Apple-Session-ID: <uuid>

     Content-Location: <url>
     Start-Position: 0.0
     ```
2. **User-Agent header**:
   - `MediaControl/1.0` is standard. Some receivers expect `AirPlay/320.20` or `AppleTV/6.2`.

---

## 4. Useful ADB & Network Commands

- **Check Phone IP on Wi-Fi**:
  `adb shell ip addr show wlan0`
- **Rebuild and Install Signed Release APK**:
  ```bash
  ./gradlew assembleRelease --no-daemon
  adb install -r app/build/outputs/apk/release/app-release.apk
  ```

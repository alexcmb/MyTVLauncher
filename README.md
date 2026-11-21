<div align="center">

<img src="docs/logo.svg" alt="My TV Launcher Logo" width="128">

# My TV Launcher

**A lightweight, elegant, and privacy-respecting home screen for Android TV.**

[![Latest Release](https://img.shields.io/github/v/release/alexcmb/MyTVLauncher?color=blue&style=flat-square)](https://github.com/alexcmb/MyTVLauncher/releases/latest)
[![Android](https://img.shields.io/badge/Android-7.0%2B%20(API%2024%2B)-brightgreen?style=flat-square&logo=android)](https://developer.android.com/)
[![CI Status](https://img.shields.io/github/actions/workflow/status/alexcmb/MyTVLauncher/ci.yml?branch=main&label=CI&style=flat-square)](https://github.com/alexcmb/MyTVLauncher/actions/workflows/ci.yml)
[![CodeQL](https://img.shields.io/github/actions/workflow/status/alexcmb/MyTVLauncher/codeql.yml?branch=main&label=CodeQL&style=flat-square)](https://github.com/alexcmb/MyTVLauncher/actions/workflows/codeql.yml)
[![License: GPL-3.0](https://img.shields.io/badge/License-GPL--3.0-orange?style=flat-square)](LICENSE.md)
[![Privacy First](https://img.shields.io/badge/Privacy-100%25%20Offline%20%7C%20Zero%20Tracking-purple?style=flat-square)](#privacy--security)

<p align="center">
  <b>No ads. No tracking. No telemetry. No account required. Fully functional offline.</b>
</p>

---

</div>

## ✨ Highlights

My TV Launcher gives your TV screen back to you. The interface sits discreetly in the bottom third of the screen, leaving the rest open for your wallpapers, ambient visuals, or media art.

* 🪟 **Glassmorphic Design**: Refined glass textures, fluid transitions, and the clean **Outfit** typography.
* 🎨 **Adaptive Accent Colors**: Choose from vibrant presets or let **Auto** extract the dominant accent directly from the focused app's banner (via AndroidX Palette).
* 🌌 **Rich Wallpaper Gallery**:
  * **Looping Videos** (MP4 / WebM): Hardware-accelerated, silent, and gapless loops powered by AndroidX Media3 ExoPlayer.
  * **Day / Night Dynamic Wallpapers** (Apple HEIC): Cross-fades throughout the day based on local clock or real astronomical sun position computed for your city.
  * **Live Procedural Visuals**: Drifting aurora, subtle gradients, or focused app art.
  * **Still Images**: Custom scaling (fill, fit, stretch, centre, span).
* 📂 **Retractable Side Rail**:
  * A sleek glass strip that expands into a navigation panel upon focus.
  * Dedicated tabs for custom categories, sorted by usage frequency, alphabetical order, or custom manual ordering.
  * Quick-access tab for physical TV inputs (HDMI, AV).
* 🧩 **Tuckable Widgets**: Host up to three standard Android widgets with configurable sizes and positions. Press **Up** from the dock to reveal them; press **Down** to tuck them away.
* 💤 **Ambient Screensaver (Daydream)**: Sleep mode integrates with Android's system screensaver, featuring a floating bounce-clock and live "Now Playing" cover art.
* ⚡ **Fast & Responsive**: Instant home startup using intelligent app list caching and asynchronous widget initialization.
* 🌐 **Bilingual**: Fully localized in English and French.

---

## 🔒 Privacy & Security

* **Zero Data Collection**: No analytics SDKs, no trackers, no third-party network requests.
* **Offline by Design**: The only internet permission requested is strictly used to check for updates against GitHub Releases when you manually click **Check for updates**.
* **Integrity Enforcement**: Update packages are verified against the author's signing key before installation.

---

## 📥 Installation

1. Download the latest APK from the **[Releases](https://github.com/alexcmb/MyTVLauncher/releases/latest)** page.
2. Sideload it onto your Android TV device via ADB:

```bash
adb install -r MyTvLauncher-v0.14.apk
```

> **Requirements**:
> * Android 7.0 (API 24) or newer.
> * Dynamic HEIC day/night wallpapers require Android 9.0 (API 28) or newer.

3. Press the **Home** button on your remote and select **My TV Launcher** as your default launcher.

---

## ⚙️ Widget Permission (One-Time ADB Setup)

Standard Android TV distributions omit the system widget authorization UI. To allow My TV Launcher to host widgets on most Android TV devices, grant the `BIND_APPWIDGET` permission once via ADB:

```bash
adb shell appwidget grantbind --package alexcmb.mytvlauncher
```

> [!NOTE]
> Devices that provide their own widget consent screen or picker handle this automatically and do not require this command.

---

## 🛠️ Development & Building

Built with **Jetpack Compose for TV**, **Kotlin 2.4**, and **Android Gradle Plugin 9**.

### Prerequisites
* JDK 17 or higher
* Android SDK (API 35 & 37 build tools)

### Build Commands

```bash
# Run unit tests
./gradlew testDebugUnitTest

# Build debug APK (installs as alexcmb.mytvlauncher.dev)
./gradlew assembleDebug

# Build release APK (requires signing credentials)
./gradlew assembleRelease
```

> [!TIP]
> The debug build uses the applicationId suffix `.dev` (`alexcmb.mytvlauncher.dev`), allowing you to test changes on your TV side-by-side without replacing your daily launcher.

---

## 🛡️ CI / CD Pipeline

Every push and pull request runs an automated security and compliance suite:
* **Unit Tests & Debug Build**
* **Android Lint** (strict error checks)
* **Semgrep** (static analysis rules)
* **Gitleaks** (secret detection)
* **OSV-Scanner** (dependency vulnerability audits)
* **CodeQL** (GitHub semantic security analysis for Java/Kotlin)

Releases are published automatically upon pushing an annotated semantic tag `vMAJOR.MINOR[.PATCH]`.

---

## 📜 License

This project is licensed under the **GNU General Public License v3.0** — see the [LICENSE.md](LICENSE.md) file for details.

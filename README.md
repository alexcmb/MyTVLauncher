<div align="center">

<img src="docs/logo.svg" alt="My TV Launcher Logo" width="100">

# My TV Launcher

**A clean, fast, and modern home screen for Android TV.**  
No ads. No tracking. No clutter. Just your apps and your wallpapers.

[![Latest Release](https://img.shields.io/github/v/release/alexcmb/MyTVLauncher?color=blue&style=flat-square)](https://github.com/alexcmb/MyTVLauncher/releases/latest)
[![Android](https://img.shields.io/badge/Android-7.0%2B-brightgreen?style=flat-square&logo=android)](https://developer.android.com/)
[![License: GPL-3.0](https://img.shields.io/badge/License-GPL--3.0-orange?style=flat-square)](LICENSE.md)
[![Privacy First](https://img.shields.io/badge/Privacy-100%25%20Offline-purple?style=flat-square)](#highlights)

<br>

<img src="docs/home.png" alt="My TV Launcher Interface" width="800">

<br><br>

[**Download APK (v0.15.2)**](https://github.com/alexcmb/MyTVLauncher/releases/latest) • [**Highlights**](#highlights) • [**Installation**](#installation)

</div>

---

## Highlights

Most TV launchers are cluttered with ads, recommendations, and sponsored content. My TV Launcher keeps things simple and gives you control over your screen.

- **Clean layout:** Your wallpaper takes center stage, with apps, favorites, and playback controls neatly arranged at the bottom.
- **Dynamic wallpapers:** Choose from looping videos, day/night wallpapers, aurora effects, or your own photos.
- **Modern design:** Smooth animations, glass effects, and accent colors that adapt to the focused app.
- **Side rail:** Organize apps into categories, switch TV inputs (HDMI/AV), and access settings.
- **Widgets & Gallery:** Manage up to 3 widgets in a full-screen visual gallery. Enjoy predesigned TV widgets (Weather & forecast, Storage & network monitor, Sun ephemeris & clock) or sideloaded app widgets. Press Up to show them and Down to hide them.
- **Screensaver:** A floating clock and Now Playing artwork appear when your TV is idle.
- **Privacy:** No accounts, no tracking, no telemetry, and no data collection. Works entirely offline.

<br>

<div align="center">
  <img src="docs/navigation.gif" alt="Navigation and adaptive accents" width="700">
  <br><br>
  <img src="docs/wallpapers.gif" alt="Dynamic wallpapers gallery" width="700">
</div>

---

## Installation

1. Download **`MyTvLauncher-v0.15.2.apk`** from [**Releases**](https://github.com/alexcmb/MyTVLauncher/releases/latest).
2. Install it on your Android TV (using a USB drive, an app like *Send Files to TV*, or ADB):
   ```bash
   adb install -r MyTvLauncher-v0.15.2.apk
   ```
3. Press **Home** on your remote and select **My TV Launcher** as your default launcher.

*(Requires Android 7.0 or newer. Dynamic day/night HEIC wallpapers require Android 9+)*

---

<details>
<summary><b>One-time setup for Android TV (Widgets & Now Playing)</b></summary>

<br>

Android TV builds do not include the standard system screens to grant widget hosting or notification access. You can grant both once over ADB:

**1. Enable widgets:**
```bash
adb shell appwidget grantbind --package alexcmb.mytvlauncher
```

**2. Enable Now Playing card:**
```bash
adb shell cmd notification allow_listener alexcmb.mytvlauncher/.media.NowPlayingListenerService
```

Once granted, widgets and the Now Playing card work automatically without any further setup.
</details>

<details>
<summary><b>For developers: building from source</b></summary>

<br>

Built with **Jetpack Compose for TV**, **Kotlin 2.4**, and **Android Gradle Plugin 9** (JDK 17+ required).

```bash
# Run unit tests
./gradlew testDebugUnitTest

# Build debug APK (installs side-by-side as alexcmb.mytvlauncher.dev)
./gradlew assembleDebug
```

Every PR is audited automatically via GitHub Actions (Tests, Android Lint, Semgrep, Gitleaks, OSV-Scanner, and CodeQL).
</details>

---

## Languages

Available in English and French.

## License

Free and open source under the [GPL-3.0 License](LICENSE.md).

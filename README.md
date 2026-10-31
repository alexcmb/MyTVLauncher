<div align="center">

<img src="docs/logo.svg" alt="" width="112">

# My TV Launcher

**A simple, modern home screen for Android TV.**

No ads. No tracking. No account. Works offline.

</div>

## What it does

**A home that leaves room for your background.** The top of the screen is left to it; everything
sits in the bottom third: the media playing, the focused app's name and a dock of favourites
over a soft gradient. Widgets wait out of sight: press Up from the dock and they come in at the top
corners, press Down and they tuck away again.

**A side rail.** Tabs and settings live in one rail down the left edge: a slim strip of glass
at rest, a panel with names when you move onto it. OK switches tab; the settings open in that
same panel. Each category of apps is a tab with a full grid, sorted by usage, by name or in
your own order; the TV's inputs (HDMI, AV) get a tab of their own.

**Backgrounds, in a gallery.** Keep as many as you like and pick them from cards, filtered by
kind:

- **Looping videos** (MP4/WebM), silent, hardware-decoded.
- **Day/night dynamic wallpapers** (HEIC). They follow the clock, or the real
  position of the sun over your city, cross-fading from dawn to night.
- **Still images**, with fill, fit, stretch, centre or span framing.
- **Generated**: a drifting aurora, the focused app's banner, an accent gradient, plain.

**Apps, organised.** Long-press any app to pin it to the dock, move it to another category
(or a new one), hide it, uninstall it or open its system info. Categories can be renamed,
reordered, hidden or deleted, and you choose which tab opens at start.

**Widgets.** Host up to three; each gets one of five sizes and a zone (left, centre, right).

**The rest.** A glass look throughout, the Outfit typeface, an accent colour (presets, or
**Auto**, taken from the focused app's banner), a clock with optional seconds, date and
greeting, and a sleep mode, also offered as the TV's screensaver: the background darkened,
with the time and what's playing (with its cover) drifting across the screen and bouncing off
its edges. English and French.

**Updates itself** from GitHub Releases, but only when you ask (Settings → Check for updates),
and only installs an APK signed with the same key. That is the only time it touches the
network.

## Install

Grab the APK from the [latest release](https://github.com/alexcmb/MyTVLauncher/releases/latest)
and sideload it:

```
adb install -r MyTvLauncher-v0.11.apk
```

Android 6.0 (API 23) or newer; dynamic HEIC wallpapers need Android 9. Then press Home and
pick My TV Launcher.

## Widgets need a one-time permission

Android reserves `BIND_APPWIDGET` for privileged apps and expects a launcher to be
white-listed through a Settings screen that the Android TV build doesn't ship. So on most TV
devices the permission has to be granted once, over adb:

```
adb shell appwidget grantbind --package alexcmb.mytvlauncher
```

Then add the widget again. Devices that do ship the consent screen, or a system widget picker,
are used automatically and need none of this.

## Build

```
./gradlew assembleDebug
```

JDK 17 or newer. The debug build installs as `alexcmb.mytvlauncher.dev`, next to the release
one, so you can try changes without touching your launcher.

Every push and pull request runs the unit tests, Android Lint, Semgrep, gitleaks, an
OSV-Scanner audit of the shipped dependencies and CodeQL. Pushing a `vMAJOR.MINOR[.PATCH]`
tag builds the release APK, signs it with the key held in the repository's secrets, and
publishes a GitHub Release whose text is the tag's message.

## License

[GPL-3.0](LICENSE.md)

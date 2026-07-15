# My TV Launcher
Simple Leanback style TV launcher.

* No ads.
* No tracking.
* Works fully offline — the internet is only used when you manually pick
  "Check for updates", which fetches the latest APK from GitHub Releases.

## Widgets

One app widget can be hosted in a band above the rows, via Settings → Add widget.

Android reserves `BIND_APPWIDGET` for privileged apps and expects a launcher to be
white-listed through a Settings screen that the Android TV build doesn't ship. So on
most TV devices the permission has to be granted once, over adb:

```
adb shell appwidget grantbind --package alexcmb.mytvlauncher
```

Then add the widget again. Devices that do ship the consent screen, or a system
widget picker, are used automatically and need none of this.

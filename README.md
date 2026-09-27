# Wolfy Browser (magisk-browser)

A **100% native Android** web browser — Kotlin + Android SDK, system WebView —
with an optional **Magisk module** that installs it as a privileged system app
and applies root-level browser configuration at boot. Built for Wolfy.

No Electron. No React Native. No JavaScript UI frameworks. The app is a
regular `Activity` with a `WebView`, and the module is plain POSIX shell.

## Components

| Piece | Path | What it is |
|---|---|---|
| Browser app | `app/` | Kotlin WebView browser: tabs, incognito tabs, bookmarks, history, downloads, per-app privacy defaults, settings UI. Works fine unrooted as a normal APK. |
| Magisk module | `magisk/` | `module.prop`, `customize.sh`, `service.sh`, `uninstall.sh`, priv-app permission whitelist, default `browser.conf`. |
| Packaging | `tools/make-module.sh` | Builds the flashable `dist/magisk-browser-vX.Y.Z.zip`. |

## What root actually adds

The app runs standalone with zero privileges. Installing the module adds:

- **Priv-app install** — `system/priv-app/WolfyBrowser/` via Magic Mount, plus a
  `privapp-permissions` whitelist granting `WRITE_SECURE_SETTINGS`.
- **Boot-time config** — `service.sh` reads `/data/adb/magisk-browser/browser.conf`:
  - `set_default_browser=1` → grants the `android.app.role.BROWSER` role (API 29+)
  - `global_http_proxy=host:port` → `settings put global http_proxy`
  - `webview_provider=<pkg>` → `cmd webviewupdate set-webview-implementation` (best-effort; see [limitations](docs/LIMITATIONS.md))
  - `hosts_blocklist=1` → systemless `/system/etc/hosts` overlay from your `hosts.block` list
- **In-app root editing** — Settings → Root edits the config live over `su`.

Read the honest constraint list before flashing: **[docs/LIMITATIONS.md](docs/LIMITATIONS.md)**.

## Build

Requirements: JDK 17, Android SDK 34 (or any `compileSdk` you adjust to).

```bash
./gradlew :app:assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest    # JVM unit tests
./tools/make-module.sh              # dist/magisk-browser-v0.1.0.zip
```

## Install

- **Unrooted**: `adb install app-debug.apk` — it's just a browser.
- **Rooted**: flash the module zip in Magisk → reboot → open Wolfy Browser →
  Settings → Root.

Full guide: **[docs/INSTALL.md](docs/INSTALL.md)** · architecture:
**[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)** · config keys:
**[docs/ROOT-SETTINGS.md](docs/ROOT-SETTINGS.md)**.

## Security defaults

TLS errors are never bypassed, `file:`/`content:`/`data:` URLs are blocked at
top level, mixed content is blocked, third-party cookies are off, geolocation
prompts per site, `su` is only ever used for the documented config paths, and
the privileged whitelist contains exactly one permission.

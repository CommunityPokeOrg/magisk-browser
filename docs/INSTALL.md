# Install & uninstall

## Requirements

| | |
|---|---|
| Android | 8.0+ (API 26); role-based default browser needs 10+ (API 29) |
| Magisk | v20.4+ (any recent release; module uses only stable interfaces) |
| For building | JDK 17 + Android SDK 34 |

## Option A — unrooted (app only)

```bash
./gradlew :app:assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

Everything browser-side works; the Root section in settings just reports
"not available". No system changes are made.

## Option B — Magisk module (root)

```bash
./gradlew :app:assembleDebug
./tools/make-module.sh                      # dist/magisk-browser-vX.Y.Z.zip
adb push dist/magisk-browser-vX.Y.Z.zip /sdcard/
```

Then either:
- Magisk app → Modules → *Install from storage* → pick the zip → **Reboot**, or
- `adb shell su -c 'magisk --install-module /sdcard/magisk-browser-vX.Y.Z.zip'` → reboot.

After reboot, verify:

```bash
adb shell pm path org.communitypoke.magiskbrowser     # should show /system/priv-app path
adb shell su -c 'ls /data/adb/magisk-browser'         # browser.conf, hosts.block, service.log
```

**If the app was previously sideloaded**, uninstall the user copy first —
the system and user copies can't coexist on some builds:
`adb uninstall org.communitypoke.magiskbrowser`.

## Configure

Settings → **Root** in the app (uses `su`), or edit
`/data/adb/magisk-browser/browser.conf` directly. Changes apply at **next
boot** (hosts changes are additionally attempted live via bind-mount).

## Uninstall

1. Magisk app → Modules → remove **Wolfy Browser** → reboot.
   The module's overlay disappears; `uninstall.sh` removes
   `/data/adb/magisk-browser`.
2. If it was the default browser, pick a new one in
   Settings → Apps → Default apps.

**Rollback if something goes wrong at boot**: modules are removable without
booting Android — in Magisk use *Safe Mode* (reboot with volume-down held on
many devices) or remove `/data/adb/modules/magisk-browser` from recovery /
`adb wait-for-device shell` early in boot. The module never modifies the
real `/system` partition, so a stock boot is always recoverable.

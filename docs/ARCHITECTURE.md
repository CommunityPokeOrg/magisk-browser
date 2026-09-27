# Architecture

```
┌──────────────────────────┐         ┌────────────────────────────────┐
│  Wolfy Browser APK       │  su(1)  │  /data/adb/magisk-browser/     │
│  (org.communitypoke.     │◄───────►│   browser.conf  hosts.block    │
│   magiskbrowser)         │         └───────────────▲────────────────┘
│  WebView + settings UI   │                         │ read once per boot
└──────────────────────────┘                         │
                                                     │
┌────────────────────────────────────────────────────┴────────────────┐
│  Magisk module  (id: magisk-browser)                                 │
│                                                                     │
│  customize.sh    → stage APK → system/priv-app/WolfyBrowser/        │
│                    create /data/adb/magisk-browser/ (mode 0700)     │
│  service.sh      → late_start, waits for boot_completed, applies    │
│                    browser.conf (role / proxy / provider / hosts)   │
│  uninstall.sh    → rm -rf /data/adb/magisk-browser                  │
│  system/etc/permissions/privapp-permissions-*.xml                   │
│                    → whitelist WRITE_SECURE_SETTINGS                │
└─────────────────────────────────────────────────────────────────────┘
```

## Design decisions

**Priv-app overlay, not a su-daemon.**
A persistent root daemon would add a socket, IPC, and an attack surface for
zero gain here — every customization maps cleanly onto an existing system
interface (`cmd role`, `settings`, `cmd webviewupdate`, hosts file). A
`service.sh` boot script is smaller, auditable, and Magisk-idiomatic.

**Config file as the contract.**
`/data/adb/magisk-browser/browser.conf` is the single integration point:
the shell side reads it at boot, the app edits it over `su`. No binder
service, no IPC. Format is flat `key=value` parseable by `grep`/`cut` —
deliberately simple so `service.sh` stays POSIX-sh.

**No bundled WebView.**
Shipping/owning a WebView provider (Chromium fork) would be a multi-GB
build and a security nightmare to maintain. The app uses the system WebView
and can *optionally* switch providers where the OS allows it. See
LIMITATIONS.md for why universal replacement isn't promised.

**No libsu dependency.**
Root access is `su -c <cmd>` via `ProcessBuilder` with timeouts
(`root/RootShell.kt`). Fewer dependencies, easier audit. Root is probed
once and cached; every root path degrades gracefully without it.

## Data flow

- **App → system**: edits `browser.conf` via `su` (base64 → `base64 -d`,
  avoids quoting bugs). Optionally uses `WRITE_SECURE_SETTINGS` when the
  priv-app whitelist granted it.
- **Module → system**: `service.sh` applies the conf at boot; hosts overlay
  is bind-mounted immediately *and* picked up by Magic Mount next boot.
- **System → app**: none — the app re-reads the file when you ask it to.

## Trust boundaries

- `browser.conf` lives in `/data/adb` (0700, root:root). Only root can
  modify it; the module only ever executes `settings`/`cmd`/`mount` with
  values from it — values are never `eval`'d as shell.
- The privileged whitelist grants exactly one permission
  (`WRITE_SECURE_SETTINGS`); if an OEM ignores the whitelist, the app
  detects the missing grant and falls back to `su` paths.

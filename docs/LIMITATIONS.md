# Limitations — read before flashing

This project deliberately does **not** overclaim. What follows is what the
module can and cannot do, verified against how Android actually behaves.

## WebView provider

- **You cannot universally replace the system WebView.** Since Android 7,
  WebView is provided by an installed package from a build-time allowlist
  (`config_webViewPackageProvider` / `webviewprovider` framework config).
  `cmd webviewupdate set-webview-implementation` only accepts packages the
  OS considers valid providers, and on many production builds the switch is
  rejected or silently reverted. Android 10+ tightened this further.
- The `webview_provider` key is therefore **best-effort**: it must name an
  installed, valid provider package (e.g. `com.google.android.webview`,
  `com.android.webview`, a bromite/mulch-style provider if your ROM allows
  it). Check `cmd webviewupdate` / `dumpsys webviewupdate` on your device
  first.
- The browser app itself always uses whatever WebView the system gives it —
  like every WebView app. It does not embed its own engine.

## Priv-app / permissions

- The `privapp-permissions` XML only whitelists `WRITE_SECURE_SETTINGS`.
  OEMs may ignore or strip the whitelist; the app then reports "Not granted"
  and falls back to `su`. Declared-but-ungranted privileged permissions are
  ignored by the OS — this is normal, not a bug.
- Being a priv-app does **not** make the browser immune to Play Protect
  warnings, change its signature, or grant other signature permissions that
  aren't whitelisted.

## hosts blocking

- Only applies while the module is enabled and mounted. Apps using
  **Private DNS (DoT)** or their own resolver bypass `/etc/hosts`.
- The live bind-mount attempt can fail on some kernels; the overlay is then
  effective on the **next** boot.
- Overly broad lists can break legitimate sites (everything maps to 0.0.0.0).

## Default browser role

- `cmd role add-role-holder` exists on API 29+. On older Android the module
  logs a skip; set the default browser manually in system settings.
- Some OEM skins add extra default-app confirmation dialogs.

## "Incognito" tabs

- No history/bookmark writes. Cookies, HTTP cache, and DOM storage are
  **process-wide** in Android WebView — incognito tabs share them. This is
  a platform limitation; real site isolation would need a separate process
  or engine.

## General

- The module modifies `/system` only via Magisk Magic Mount (systemless):
  uninstalling restores stock. OTA updates can still wipe modules.
- `su` prompts come from your su manager (Magisk) — approve
  `org.communitypoke.magiskbrowser` there to use in-app root editing.
- `global_http_proxy` is honored by apps that read the global proxy
  setting, not by apps with hardcoded connectivity or QUIC.
- minSdk 26: older devices aren't supported (WebView feature matrix +
  Magisk support both get unreliable below that).

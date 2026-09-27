# Root settings — browser.conf reference

`/data/adb/magisk-browser/browser.conf` is a flat `key=value` file read once
per boot by `service.sh` (after `sys.boot_completed`). `#` starts a comment.
Unknown keys are ignored, missing keys take defaults.

| Key | Values | Applies at | Effect |
|---|---|---|---|
| `set_default_browser` | `0`/`1` | boot | `cmd role add-role-holder android.app.role.BROWSER org.communitypoke.magiskbrowser`. API 29+ only. Makes Wolfy Browser the default handler for http/https links. |
| `global_http_proxy` | `host:port` or empty | boot | `settings put global http_proxy <value>`. Affects apps that honor the global proxy (system WebView does). Empty leaves the setting untouched; use `:0` to actively clear. |
| `webview_provider` | package name or empty | boot | `cmd webviewupdate set-webview-implementation <pkg>` — only if that package is installed and is a valid provider. Best-effort; many builds refuse. See LIMITATIONS.md. |
| `hosts_blocklist` | `0`/`1` | boot (+ live bind attempt) | Generates `system/etc/hosts` inside the module overlay: localhost entries + every non-comment line of `/data/adb/magisk-browser/hosts.block` mapped to `0.0.0.0`. |

`hosts.block` format: one domain per line, `#` comments allowed. Example:

```
ads.example.com
tracker.example.net
```

## Editing

- In-app: Settings → Root → *Edit browser.conf* / *Edit hosts blocklist*
  (writes via `su`).
- Shell: `su -c 'vi /data/adb/magisk-browser/browser.conf'` (or any editor).
- Changes are boot-applied; nothing re-reads the file at runtime.

## Logging

`service.sh` appends to `/data/adb/magisk-browser/service.log` — check it
first when a setting "didn't apply":

```bash
su -c 'cat /data/adb/magisk-browser/service.log'
```

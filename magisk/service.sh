#!/system/bin/sh
# late_start service: runs once per boot, as root, after /data is mounted.
# Applies /data/adb/magisk-browser/browser.conf.
# POSIX-sh only (executed by mksh); keep it dependency-free.

MODDIR=${0%/*}
CONF_DIR="/data/adb/magisk-browser"
CONF="$CONF_DIR/browser.conf"
LOG="$CONF_DIR/service.log"
PKG="org.communitypoke.magiskbrowser"

mkdir -p "$CONF_DIR"
exec >>"$LOG" 2>&1
echo "=== magisk-browser service.sh $(date) ==="

get_conf() {
  grep -E "^$1=" "$CONF" 2>/dev/null | head -n1 | cut -d= -f2-
}

[ -f "$CONF" ] || { echo "no browser.conf; nothing to do"; exit 0; }

# The commands below need a mostly-booted system.
until [ "$(getprop sys.boot_completed)" = "1" ]; do sleep 2; done
sleep 5

# --- 1. Default-browser role (Android 10+/API29+) --------------------------
if [ "$(get_conf set_default_browser)" = "1" ]; then
  if [ "$(getprop ro.build.version.sdk)" -ge 29 ]; then
    if cmd role add-role-holder android.app.role.BROWSER "$PKG"; then
      echo "role: $PKG is now a holder of android.app.role.BROWSER"
    else
      echo "role: add-role-holder failed"
    fi
  else
    echo "role: skipped (API < 29 has no role manager)"
  fi
fi

# --- 2. Global HTTP proxy ---------------------------------------------------
PROXY=$(get_conf global_http_proxy)
if [ -n "$PROXY" ]; then
  settings put global http_proxy "$PROXY" && echo "http_proxy=$PROXY"
fi

# --- 3. WebView provider switch ---------------------------------------------
# Only a valid, installed, WebView-provider package can be selected, and OEM /
# OS rules may still refuse or revert the choice. See docs/LIMITATIONS.md.
PROVIDER=$(get_conf webview_provider)
if [ -n "$PROVIDER" ]; then
  if pm path "$PROVIDER" >/dev/null 2>&1; then
    cmd webviewupdate set-webview-implementation "$PROVIDER" && \
      echo "webview provider set to $PROVIDER (reboot recommended)"
  else
    echo "webview provider '$PROVIDER' is not installed; skipped"
  fi
fi

# --- 4. Systemless hosts blocking -------------------------------------------
if [ "$(get_conf hosts_blocklist)" = "1" ]; then
  HOSTS_OUT="$MODDIR/system/etc/hosts"
  mkdir -p "$(dirname "$HOSTS_OUT")"
  {
    echo "127.0.0.1 localhost"
    echo "::1 ip6-localhost"
    grep -vE '^[[:space:]]*(#|$)' "$CONF_DIR/hosts.block" 2>/dev/null \
      | sed 's/^[[:space:]]*//; s/[[:space:]]*$//; s/^/0.0.0.0 /'
  } > "$HOSTS_OUT.tmp"
  chmod 0644 "$HOSTS_OUT.tmp"
  mv "$HOSTS_OUT.tmp" "$HOSTS_OUT"
  # Magic mounts are set up before late_start — bind now AND on next boot.
  mount --bind "$HOSTS_OUT" /system/etc/hosts 2>/dev/null \
    && echo "hosts overlay mounted ($(wc -l < "$HOSTS_OUT") lines)" \
    || echo "hosts overlay staged; effective after next boot"
fi

echo "=== done ==="
exit 0

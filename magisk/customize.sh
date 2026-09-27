#!/system/bin/sh
# Magisk install-time script. Runs as root inside the Magisk installer with
# $MODPATH, $API, $ARCH etc. set. Keep POSIX-sh compatible (mksh on device).

SKIPUNZIP=0

MIN_API=26
if [ "$API" -lt "$MIN_API" ]; then
  abort "! Wolfy Browser requires Android 8.0 (API $MIN_API) or newer; this device reports API $API"
fi

ui_print "- Installing Wolfy Browser as a system priv-app"

# Stage the bundled APK into the module's systemless overlay. The APK is a
# plain JVM app, so no ABI split is needed.
APK_STAGED="$MODPATH/apk/WolfyBrowser.apk"
PRIVAPP_DIR="$MODPATH/system/priv-app/WolfyBrowser"

[ -f "$APK_STAGED" ] || abort "! Bundled WolfyBrowser.apk missing — corrupt module zip?"

mkdir -p "$PRIVAPP_DIR"
cp -f "$APK_STAGED" "$PRIVAPP_DIR/WolfyBrowser.apk"
rm -rf "$MODPATH/apk"
set_perm_recursive "$PRIVAPP_DIR" 0 0 0755 0644

# User-editable config dir. Created once; upgrades must NOT clobber it.
CONF_DIR="/data/adb/magisk-browser"
if [ ! -d "$CONF_DIR" ]; then
  mkdir -p "$CONF_DIR"
  cp -f "$MODPATH/config/browser.conf" "$CONF_DIR/browser.conf"
  touch "$CONF_DIR/hosts.block"
fi
set_perm_recursive "$CONF_DIR" 0 0 0700 0600

ui_print "- Config:  $CONF_DIR/browser.conf"
ui_print "- Hosts:   $CONF_DIR/hosts.block"
ui_print "- Reboot to activate the module"

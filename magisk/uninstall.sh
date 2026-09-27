#!/system/bin/sh
# Runs when the module is removed from Magisk (before the removing reboot).
# Cleans up runtime state; the systemless priv-app overlay disappears with the
# module itself.

rm -rf /data/adb/magisk-browser

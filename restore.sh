#!/bin/bash
# Undo everything debloat.sh and the custom launcher did, returning the TV to
# the state captured in backup/ (default: the backup named in backup/LATEST).
# Usage: ./restore.sh [backup-dir]
set -u
. "$(dirname "$0")/common.sh"
B="${1:-$(cat backup/LATEST)}"
LAUNCHER=com.minimal.tvhome
adb get-state >/dev/null || { echo "TV not reachable at $ANDROID_SERIAL"; exit 1; }
echo "Restoring from $B"

# 1. Custom launcher: drop its Home-button accessibility hook, then remove it.
svc=$(adb shell settings get secure enabled_accessibility_services | tr -d '\r')
new=$(echo "$svc" | tr ':' '\n' | grep -v "^$LAUNCHER/" | paste -sd: -)
orig_svc=$(grep '^enabled_accessibility_services=' "$B/settings-secure.txt" | cut -d= -f2-)
if [ -n "$orig_svc" ]; then adb shell settings put secure enabled_accessibility_services "$orig_svc"
elif [ -z "$new" ] || [ "$new" = null ]; then adb shell settings delete secure enabled_accessibility_services
else adb shell settings put secure enabled_accessibility_services "$new"; fi
orig_acc=$(grep '^accessibility_enabled=' "$B/settings-secure.txt" | cut -d= -f2-)
[ -n "$orig_acc" ] && adb shell settings put secure accessibility_enabled "$orig_acc"
adb shell cmd notification disallow_listener $LAUNCHER/$LAUNCHER.MediaListener >/dev/null 2>&1
adb shell pm path $LAUNCHER >/dev/null 2>&1 && adb uninstall $LAUNCHER

# 2. Re-enable every package that is disabled now but was enabled at backup time.
was_disabled=$(cut -d: -f2 "$B/packages-disabled.txt")
for pkg in $(adb shell pm list packages -d | cut -d: -f2 | tr -d '\r'); do
  echo "$was_disabled" | grep -qx "$pkg" && continue
  adb shell pm enable "$pkg" | tr -d '\r'
done
: > state/disabled-by-us.txt 2>/dev/null

# 2b. Lift background restrictions put on protected packages.
for pkg in $(cat state/restricted-by-us.txt 2>/dev/null); do
  adb shell cmd appops reset "$pkg" && echo "Package $pkg background restriction lifted"
done
: > state/restricted-by-us.txt 2>/dev/null

# 3. Put the settings we touched back to their backed-up values.
for key in limit_ad_tracking amz_limit_ad_tracking non_amz_limit_ad_tracking \
           usage_metrics_marketing_enabled pact_usage_collection_enabled \
           screensaver_enabled screensaver_components screensaver_default_component; do
  val=$(grep "^$key=" "$B/settings-secure.txt" | cut -d= -f2-)
  if [ -n "$val" ]; then adb shell settings put secure "$key" "'$val'"
  else adb shell settings delete secure "$key" >/dev/null; fi
done

# 4. Back to Amazon's home screen.
adb shell input keyevent KEYCODE_HOME
echo "Done. Disabled now: $(adb shell pm list packages -d | wc -l | tr -d ' ') packages (backup had $(grep -c . "$B/packages-disabled.txt"))."
echo "A reboot is recommended: adb -s $ANDROID_SERIAL reboot"

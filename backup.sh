#!/bin/bash
# Snapshot the TV before changing anything: package states, all settings,
# appops for installed apps, and copies of your installed APKs.
# Writes backup/<timestamp>/ and records it in backup/LATEST for restore.sh.
# Usage: ./backup.sh            (run once, before debloat.sh)
set -u
. "$(dirname "$0")/common.sh"
B=backup/$(date +%Y%m%d-%H%M%S); mkdir -p "$B"
adb shell pm list packages -f -U > "$B/packages-all.txt"
adb shell pm list packages -s > "$B/packages-system.txt"
adb shell pm list packages -3 > "$B/packages-user.txt"
adb shell pm list packages -d > "$B/packages-disabled.txt"
adb shell pm list packages -e > "$B/packages-enabled.txt"
for ns in global secure system; do adb shell settings list $ns > "$B/settings-$ns.txt"; done
adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME > "$B/home-activity.txt"
adb shell getprop > "$B/getprop.txt"
adb shell 'for p in $(pm list packages -3 | cut -d: -f2); do echo "== $p"; cmd appops get $p; done' > "$B/appops-user.txt"
mkdir -p "$B/user-apks"
for p in $(cut -d: -f2 "$B/packages-user.txt" | tr -d '\r'); do
  for a in $(adb shell pm path "$p" | cut -d: -f2 | tr -d '\r'); do
    adb pull -q "$a" "$B/user-apks/$p--$(basename "$a")" 2>/dev/null
  done
done
shasum -a 256 "$B"/*.txt > "$B/SHA256SUMS"
echo "$B" > backup/LATEST
echo "Backup saved to $B ($(du -sh "$B" | cut -f1)). Keep it private: it holds your device details."

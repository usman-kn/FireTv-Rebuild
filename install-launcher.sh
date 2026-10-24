#!/bin/bash
# Install the minimal home screen and route the Home button to it.
# Undo with ./restore.sh
set -eu
. "$(dirname "$0")/common.sh"
[ -f launcher/build/home.apk ] || launcher/build.sh
adb install -r launcher/build/home.apk
SVC=com.minimal.tvhome/com.minimal.tvhome.HomeKeyService
cur=$(adb shell settings get secure enabled_accessibility_services | tr -d '\r')
case "$cur" in *"$SVC"*) ;; null|"") cur="$SVC" ;; *) cur="$cur:$SVC" ;; esac
adb shell settings put secure enabled_accessibility_services "$cur"
adb shell settings put secure accessibility_enabled 1
# Mini player: lets the home screen read what Spotify (or any app) is playing
adb shell cmd notification allow_listener com.minimal.tvhome/com.minimal.tvhome.MediaListener
adb shell am start -n com.minimal.tvhome/.HomeActivity >/dev/null
echo "Minimal home installed; Home button now opens it."

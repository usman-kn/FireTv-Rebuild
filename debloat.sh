#!/bin/bash
# Disable Fire TV bloat listed in packages.txt (plus packages.local.txt, if you
# keep one). Reversible: packages are disabled, never uninstalled; see restore.sh.
# Usage: ./debloat.sh all | <tier> [tier ...]
# Safe to re-run after a Fire OS update re-enables things.
set -u
. "$(dirname "$0")/common.sh"
mkdir -p state
log=state/disabled-by-us.txt; rlog=state/restricted-by-us.txt; touch "$log" "$rlog"
[ -f backup/LATEST ] || { echo "No backup yet: run ./backup.sh first." >&2; exit 1; }

lists="packages.txt"; [ -f packages.local.txt ] && lists="$lists packages.local.txt"
if [ $# -eq 0 ]; then
  echo "Usage: ./debloat.sh all | <tier> [tier ...]"
  echo "Tiers: $(cat $lists | grep -vE '^\s*(#|$)' | awk '{print $1}' | uniq | paste -sd' ' -) settings"
  exit 1
fi
tiers=("$@")
want() { for t in "${tiers[@]}"; do [ "$t" = all ] || [ "$t" = "$1" ] && return 0; done; return 1; }

cat $lists | grep -vE '^\s*(#|$)' | while read -r tier pkg; do
  want "$tier" || continue
  if ! adb shell pm path "$pkg" </dev/null >/dev/null 2>&1; then echo "skip  $pkg (not present)"; continue; fi
  out=$(adb shell pm disable-user --user 0 "$pkg" </dev/null 2>&1)
  if echo "$out" | grep -q 'disabled-user'; then
    echo "off   $pkg"; grep -qx "$pkg" "$log" || echo "$pkg" >> "$log"
  elif echo "$out" | grep -q 'protected package'; then
    # Fire OS won't disable it; stop it running in the background instead.
    adb shell cmd appops set "$pkg" RUN_ANY_IN_BACKGROUND ignore </dev/null
    adb shell am force-stop "$pkg" </dev/null >/dev/null 2>&1   # usually refused too; harmless
    echo "bg-   $pkg (protected; background-restricted)"
    grep -qx "$pkg" "$rlog" || echo "$pkg" >> "$rlog"
  else
    echo "FAIL  $pkg: $(echo "$out" | grep -m1 -oE '[A-Za-z]+Exception[^[:cntrl:]]*')"
  fi
done

if want settings; then
  # Privacy / ad toggles (originals are in backup/*/settings-secure.txt)
  adb shell settings put secure limit_ad_tracking 1
  adb shell settings put secure amz_limit_ad_tracking 1
  adb shell settings put secure non_amz_limit_ad_tracking 1
  adb shell settings put secure usage_metrics_marketing_enabled 0
  adb shell settings put secure pact_usage_collection_enabled 0
  adb shell settings put secure screensaver_enabled 0
  echo "settings applied"
fi

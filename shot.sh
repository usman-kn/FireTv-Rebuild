#!/bin/bash
# Grab a screenshot from the TV: ./shot.sh <name>  -> shots/<name>.png
# Retries because screencap returns 0 bytes mid-transition.
. "$(dirname "$0")/common.sh"
mkdir -p shots
for i in 1 2 3 4 5; do
  adb exec-out screencap -p > "shots/$1.png"
  [ -s "shots/$1.png" ] && { echo "shots/$1.png"; exit 0; }
  sleep 2
done
echo "screencap failed: shots/$1.png is empty" >&2; exit 1

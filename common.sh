# Sourced by every script: loads config.sh and points adb at the TV.
cd "$(dirname "$0")"
[ -f config.sh ] && . ./config.sh
if [ -z "${ANDROID_SERIAL:-}" ]; then
  if [ -z "${TV_IP:-}" ]; then
    echo "Set TV_IP first:  cp config.example.sh config.sh  and edit it." >&2
    exit 1
  fi
  export ANDROID_SERIAL="$TV_IP:5555"
fi
adb connect "$ANDROID_SERIAL" >/dev/null

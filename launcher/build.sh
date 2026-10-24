#!/bin/bash
# Build home.apk with plain SDK tools (no Gradle). Output: build/home.apk
set -euo pipefail
cd "$(dirname "$0")"
# JDK 17: $JAVA_HOME if set, else a Temurin unpacked in ~/.local, else whatever is on PATH
if [ -z "${JAVA_HOME:-}" ]; then
  for j in "$HOME"/.local/jdk-17*/Contents/Home "$HOME"/.local/jdk-17*; do [ -x "$j/bin/javac" ] && JAVA_HOME="$j" && break; done
fi
[ -n "${JAVA_HOME:-}" ] && export JAVA_HOME PATH="$JAVA_HOME/bin:$PATH"
SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
BT="$SDK/build-tools/34.0.0"
JAR="$SDK/platforms/android-30/android.jar"

rm -rf build && mkdir -p build/res build/gen build/classes build/dex
"$BT/aapt2" compile --dir res -o build/res/res.zip
# Prompt name from ../config.sh, layered over res/values/strings.xml
[ -f ../config.sh ] && . ../config.sh
mkdir -p build/overlay/values
cat > build/overlay/values/prompt.xml <<XML
<resources>
    <string name="prompt_user">${PROMPT_USER:-me}</string>
    <string name="prompt_host">${PROMPT_HOST:-firetv}</string>
</resources>
XML
"$BT/aapt2" compile build/overlay/values/prompt.xml -o build/res/
"$BT/aapt2" link -I "$JAR" --manifest AndroidManifest.xml --java build/gen \
  -o build/unsigned.apk build/res/res.zip -R build/res/values_prompt.arsc.flat \
  --min-sdk-version 26 --target-sdk-version 30
javac -source 8 -target 8 -nowarn -Xlint:-options -bootclasspath "$JAR:$BT/core-lambda-stubs.jar" -d build/classes \
  $(find src build/gen -name '*.java')
"$BT/d8" --release --min-api 26 --lib "$JAR" --output build/dex $(find build/classes -name '*.class')
(cd build/dex && zip -q ../unsigned.apk classes.dex)
"$BT/zipalign" -f 4 build/unsigned.apk build/aligned.apk

KS=debug.keystore
[ -f $KS ] || keytool -genkeypair -keystore $KS -storepass android -keypass android \
  -alias home -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Minimal TV Home" >/dev/null 2>&1
"$BT/apksigner" sign --ks $KS --ks-pass pass:android --key-pass pass:android \
  --out build/home.apk build/aligned.apk
ls -la build/home.apk

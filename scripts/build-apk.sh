#!/usr/bin/env bash
# Offline Android packaging. See README.md for tool setup and signing-key handling.
set -euo pipefail
cd "$(dirname "$0")/.."
JAVA="${JAVA:-$PWD/.tools/jre11/bin/java}"
JAVAC_JAR="${JAVAC_JAR:-$PWD/.tools/javac/package/tools.jar}"
ANDROID_JAR="${ANDROID_JAR:-$PWD/.tools/android.jar}"
AAPT2="${AAPT2:-$PWD/.tools/aapt/package/bin/x64/linux/aapt2}"
D8_JAR="${D8_JAR:-$PWD/.tools/minapk/package/tools/d8.jar}"
APKSIGNER_JAR="${APKSIGNER_JAR:-$PWD/.tools/minapk/package/tools/apksigner.jar}"
KEYSTORE="${KEYSTORE:-$PWD/.tools/little-star-local.p12}"
KEY_ALIAS="${KEY_ALIAS:-littlestar}"
: "${KEYSTORE_PASSWORD:?Set KEYSTORE_PASSWORD to sign the local APK}"
export KEYSTORE_PASSWORD
for tool in "$JAVA" "$JAVAC_JAR" "$ANDROID_JAR" "$AAPT2" "$D8_JAR" "$APKSIGNER_JAR" "$KEYSTORE"; do
  test -f "$tool" || { echo "Missing build tool: $tool"; exit 1; }
done
npm run build
node --check dist/sw.js
rm -rf android/build
mkdir -p android/build/{classes,dex,assets/www} releases
cp -R dist/. android/build/assets/www/
"$JAVA" -cp "$JAVAC_JAR" com.sun.tools.javac.Main -encoding UTF-8 -source 8 -target 8 -bootclasspath "$ANDROID_JAR" -d android/build/classes android/app/src/main/java/ir/setareh/adventure/MainActivity.java
"$JAVA" -cp "$D8_JAR" com.android.tools.r8.D8 --lib "$ANDROID_JAR" --min-api 26 --output android/build/dex $(find android/build/classes -name '*.class')
"$AAPT2" compile --dir android/app/src/main/res -o android/build/resources.zip
"$AAPT2" link -o android/build/unsigned.apk --manifest android/app/src/main/AndroidManifest.xml -I "$ANDROID_JAR" -A android/build/assets --min-sdk-version 26 --target-sdk-version 35 android/build/resources.zip
python3 - <<'PY'
# Android 11+ requires resources.arsc to be STORED and four-byte aligned.
import zipfile, struct
from pathlib import Path
apk=Path('android/build/unsigned.apk')
with zipfile.ZipFile(apk) as src, zipfile.ZipFile('android/build/packed.apk','w',compression=zipfile.ZIP_DEFLATED) as dst:
    for item in src.infolist():
        if item.filename == 'resources.arsc':
            info=zipfile.ZipInfo(item.filename)
            info.compress_type=zipfile.ZIP_STORED
            padding=(-(dst.fp.tell()+30+len(info.filename.encode('utf-8'))))%4
            info.extra=struct.pack('<HH',0xFFFF,padding)+b'\0'*padding
            dst.writestr(info,src.read(item.filename))
        else:
            dst.writestr(item.filename,src.read(item.filename),compress_type=zipfile.ZIP_DEFLATED)
    dst.write('android/build/dex/classes.dex','classes.dex')
PY
"$JAVA" -jar "$APKSIGNER_JAR" sign --ks "$KEYSTORE" --ks-key-alias "$KEY_ALIAS" --ks-pass env:KEYSTORE_PASSWORD --key-pass env:KEYSTORE_PASSWORD --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true --v4-signing-enabled false --out releases/Setareh-Koochooloo-v1.0.0.apk android/build/packed.apk
"$JAVA" -jar "$APKSIGNER_JAR" verify --verbose --print-certs releases/Setareh-Koochooloo-v1.0.0.apk
"$AAPT2" dump badging releases/Setareh-Koochooloo-v1.0.0.apk
sha256sum releases/Setareh-Koochooloo-v1.0.0.apk > releases/SHA256SUMS.txt

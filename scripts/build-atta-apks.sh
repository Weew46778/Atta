#!/usr/bin/env bash
# ساختِ پنج APK آتا v3 (بدون نیاز به Gradle — تولچین سبک .tools)
# full / explorer / toddler / mother / father
set -euo pipefail
cd "$(dirname "$0")/.."
JAVA="$PWD/.tools/jre11/bin/java"
KEYTOOL="$PWD/.tools/jre11/bin/keytool"
JAVAC="$PWD/.tools/javac/package/tools.jar"
ANDROID="$PWD/.tools/android.jar"
AAPT="$PWD/.tools/aapt/package/bin/x64/linux/aapt2"
D8="$PWD/.tools/minapk/package/tools/d8.jar"
SIGNER="$PWD/.tools/minapk/package/tools/apksigner.jar"
KEYSTORE="${KEYSTORE:-$PWD/.tools/atta-v3.p12}"
: "${KEYSTORE_PASSWORD:?Provide KEYSTORE_PASSWORD through the environment}"
export KEYSTORE_PASSWORD
if [ ! -f "$KEYSTORE" ]; then
  "$KEYTOOL" -genkeypair -keystore "$KEYSTORE" -storetype PKCS12 -storepass:env KEYSTORE_PASSWORD -keypass:env KEYSTORE_PASSWORD -alias atta -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=Atta Family, O=Atta, C=IR'
fi

# اعتبارسنجیِ محتوای وب پیش از بسته‌بندی
for f in app/js/*.js; do node --check "$f"; done
node --check app/sw.js
OUT="$PWD/releases/atta-v3"
mkdir -p "$OUT"

build_one () {
  local EDITION="$1" PKG="$2" LABEL="$3" ED_PARAM="$4" START_PARAM="$5"
  local BUILD="$PWD/android/build/atta-$EDITION"
  rm -rf "$BUILD"
  mkdir -p "$BUILD"/{classes,dex,assets/www,src}
  cp -R app/. "$BUILD/assets/www/"
  sed "s/package ir.atta.app;/package $PKG;/; s/__EDITION__/$ED_PARAM/; s/__START__/$START_PARAM/" android/atta/MainActivity.java > "$BUILD/src/MainActivity.java"
  mkdir -p "$BUILD/res"
  cp -R android/atta/res/. "$BUILD/res/"
  cat > "$BUILD/AndroidManifest.xml" <<MANIFEST
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="$PKG" android:versionCode="300" android:versionName="3.0.0">
<uses-sdk android:minSdkVersion="26" android:targetSdkVersion="35"/>
<uses-permission android:name="android.permission.INTERNET"/>
<uses-permission android:name="android.permission.RECORD_AUDIO"/>
<uses-feature android:name="android.hardware.microphone" android:required="false"/>
<application android:label="$LABEL" android:icon="@mipmap/ic_launcher" android:theme="@android:style/Theme.Material.NoActionBar" android:allowBackup="false" android:supportsRtl="true" android:usesCleartextTraffic="false">
<activity android:name=".MainActivity" android:exported="true" android:configChanges="orientation|screenSize|keyboardHidden" android:windowSoftInputMode="adjustResize"><intent-filter><action android:name="android.intent.action.MAIN"/><category android:name="android.intent.category.LAUNCHER"/></intent-filter></activity>
</application></manifest>
MANIFEST
  "$JAVA" -cp "$JAVAC" com.sun.tools.javac.Main -encoding UTF-8 -source 8 -target 8 -bootclasspath "$ANDROID" -d "$BUILD/classes" "$BUILD/src/MainActivity.java"
  "$JAVA" -cp "$D8" com.android.tools.r8.D8 --lib "$ANDROID" --min-api 26 --output "$BUILD/dex" $(find "$BUILD/classes" -name '*.class')
  "$AAPT" compile --dir "$BUILD/res" -o "$BUILD/resources.zip"
  "$AAPT" link -o "$BUILD/unsigned.apk" --manifest "$BUILD/AndroidManifest.xml" -I "$ANDROID" -A "$BUILD/assets" --min-sdk-version 26 --target-sdk-version 35 "$BUILD/resources.zip"
  BUILD="$BUILD" python3 - <<'PY'
import os, zipfile, struct
b = os.environ['BUILD']
with zipfile.ZipFile(b + '/unsigned.apk') as src, zipfile.ZipFile(b + '/packed.apk', 'w', compression=zipfile.ZIP_DEFLATED) as dst:
    for i in src.infolist():
        if i.filename == 'resources.arsc':
            info = zipfile.ZipInfo(i.filename); info.compress_type = zipfile.ZIP_STORED
            padding = (-(dst.fp.tell() + 30 + len(info.filename.encode()))) % 4
            info.extra = struct.pack('<HH', 0xffff, padding) + b'\0' * padding
            dst.writestr(info, src.read(i.filename))
        else:
            dst.writestr(i.filename, src.read(i.filename), compress_type=zipfile.ZIP_DEFLATED)
    dst.write(b + '/dex/classes.dex', 'classes.dex')
PY
  local APK="$OUT/Atta-$EDITION-v3.0.0.apk"
  "$JAVA" -jar "$SIGNER" sign --ks "$KEYSTORE" --ks-key-alias atta --ks-pass env:KEYSTORE_PASSWORD --key-pass env:KEYSTORE_PASSWORD --v2-signing-enabled true --v3-signing-enabled true --v4-signing-enabled false --out "$APK" "$BUILD/packed.apk"
  "$JAVA" -jar "$SIGNER" verify "$APK" && echo "SIGNED: $APK"
  "$AAPT" dump badging "$APK" | head -6
  rm -rf "$BUILD"
}

build_one full    ir.atta.full    'آتا — همه‌کاره'        explorer home
build_one explorer ir.atta.explorer 'آتا — کاوشگر'         explorer home
build_one toddler  ir.atta.toddler  'آتا — خردسال'         toddler  home
build_one mother   ir.atta.mother   'آتا — همراهِ مادر'     explorer parent
build_one father   ir.atta.father   'آتا — همراهِ پدر'      explorer parent

sha256sum "$OUT"/*.apk > "$OUT/SHA256SUMS.txt"
python3 - <<'PY'
import zipfile, pathlib
out = pathlib.Path('releases/atta-v3')
with zipfile.ZipFile('releases/Atta-Family-v3.zip', 'w', compression=zipfile.ZIP_DEFLATED) as z:
    for p in sorted(out.iterdir()):
        if p.is_file(): z.write(p, p.name)
print('bundle: releases/Atta-Family-v3.zip')
PY
ls -la "$OUT"

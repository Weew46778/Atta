#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
JAVA="$PWD/.tools/jre11/bin/java"
KEYTOOL="$PWD/.tools/jre11/bin/keytool"
JAVAC="$PWD/.tools/javac/package/tools.jar"
ANDROID="$PWD/.tools/android.jar"
AAPT="$PWD/.tools/aapt/package/bin/x64/linux/aapt2"
D8="$PWD/.tools/minapk/package/tools/d8.jar"
SIGNER="$PWD/.tools/minapk/package/tools/apksigner.jar"
KEYSTORE="${KEYSTORE:-$PWD/.tools/bagh-family-v2.p12}"
: "${KEYSTORE_PASSWORD:?Provide KEYSTORE_PASSWORD through the environment}"
export KEYSTORE_PASSWORD
if [ ! -f "$KEYSTORE" ]; then
 "$KEYTOOL" -genkeypair -keystore "$KEYSTORE" -storetype PKCS12 -storepass:env KEYSTORE_PASSWORD -keypass:env KEYSTORE_PASSWORD -alias family -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=Bagh Family Preview, O=Bagh Family, C=IR'
fi
npm run build
node --check dist/sw.js
mkdir -p releases/family-v2
for EDITION in explorer toddler mother father; do
 case "$EDITION" in
 explorer) LABEL='باغ قصه‌ها — کاوشگر';;
 toddler) LABEL='باغ قصه‌ها — جوانه';;
 mother) LABEL='باغ قصه‌ها — همراه مادر';;
 father) LABEL='باغ قصه‌ها — همراه پدر';;
 esac
 BUILD="$PWD/android/build/family-$EDITION"
 rm -rf "$BUILD"
 mkdir -p "$BUILD"/{classes,dex,assets/www,res/drawable,src}
 cp -R dist/. "$BUILD/assets/www/"
 # Do not package the retired app entry; the four v2 editions share the v2 content bundle.
 rm -f "$BUILD/assets/www/legacy.html"
 cp android/app/src/main/res/drawable/app_icon.xml "$BUILD/res/drawable/app_icon.xml"
 sed "s/package ir.setareh.adventure;/package ir.bagh.family.$EDITION;/; s/__EDITION__/$EDITION/" android/family/MainActivity.java > "$BUILD/src/MainActivity.java"
 cat > "$BUILD/AndroidManifest.xml" <<MANIFEST
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="ir.bagh.family.$EDITION" android:versionCode="200" android:versionName="2.0.0-preview">
<uses-sdk android:minSdkVersion="26" android:targetSdkVersion="35"/>
<uses-permission android:name="android.permission.INTERNET"/>
<uses-permission android:name="android.permission.RECORD_AUDIO"/>
<uses-feature android:name="android.hardware.microphone" android:required="false"/>
<application android:label="$LABEL" android:icon="@drawable/app_icon" android:theme="@android:style/Theme.Material.Light.NoActionBar" android:allowBackup="false" android:supportsRtl="true" android:usesCleartextTraffic="false">
<activity android:name=".MainActivity" android:exported="true" android:configChanges="orientation|screenSize|keyboardHidden" android:windowSoftInputMode="adjustResize"><intent-filter><action android:name="android.intent.action.MAIN"/><category android:name="android.intent.category.LAUNCHER"/></intent-filter></activity>
</application></manifest>
MANIFEST
 "$JAVA" -cp "$JAVAC" com.sun.tools.javac.Main -encoding UTF-8 -source 8 -target 8 -bootclasspath "$ANDROID" -d "$BUILD/classes" "$BUILD/src/MainActivity.java"
 "$JAVA" -cp "$D8" com.android.tools.r8.D8 --lib "$ANDROID" --min-api 26 --output "$BUILD/dex" $(find "$BUILD/classes" -name '*.class')
 "$AAPT" compile --dir "$BUILD/res" -o "$BUILD/resources.zip"
 "$AAPT" link -o "$BUILD/unsigned.apk" --manifest "$BUILD/AndroidManifest.xml" -I "$ANDROID" -A "$BUILD/assets" --min-sdk-version 26 --target-sdk-version 35 "$BUILD/resources.zip"
 BUILD="$BUILD" python3 - <<'PY'
import os,zipfile,struct
b=os.environ['BUILD']
with zipfile.ZipFile(b+'/unsigned.apk') as src,zipfile.ZipFile(b+'/packed.apk','w',compression=zipfile.ZIP_DEFLATED) as dst:
 for i in src.infolist():
  if i.filename=='resources.arsc':
   info=zipfile.ZipInfo(i.filename);info.compress_type=zipfile.ZIP_STORED
   padding=(-(dst.fp.tell()+30+len(info.filename.encode())))%4
   info.extra=struct.pack('<HH',0xffff,padding)+b'\0'*padding
   dst.writestr(info,src.read(i.filename))
  else: dst.writestr(i.filename,src.read(i.filename),compress_type=zipfile.ZIP_DEFLATED)
 dst.write(b+'/dex/classes.dex','classes.dex')
PY
 APK="$PWD/releases/family-v2/Bagh-$EDITION-v2.0.0.apk"
 "$JAVA" -jar "$SIGNER" sign --ks "$KEYSTORE" --ks-key-alias family --ks-pass env:KEYSTORE_PASSWORD --key-pass env:KEYSTORE_PASSWORD --v2-signing-enabled true --v3-signing-enabled true --v4-signing-enabled false --out "$APK" "$BUILD/packed.apk"
 "$JAVA" -jar "$SIGNER" verify --verbose "$APK"
 "$AAPT" dump badging "$APK" | head -15
 done
sha256sum releases/family-v2/*.apk > releases/family-v2/SHA256SUMS.txt
python3 - <<'PY'
import zipfile,pathlib
with zipfile.ZipFile('releases/Bagh-Family-v2.zip','w',compression=zipfile.ZIP_DEFLATED) as z:
 for p in pathlib.Path('releases/family-v2').iterdir():
  if p.is_file(): z.write(p,p.name)
PY

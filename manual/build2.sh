#!/usr/bin/env bash
# AttaPanel v2 — full build with aapt2 (icon/label resources) + compatdx + apksigner
set -eu
T=${ATTA_TOOLS:-/home/user/Atta/.attatools}
M=/home/user/Atta/manual
J=$T/jre/bin/java
JAVAC=$T/dl/package/tools.jar
ANDROID_JAR=$T/ap/jars/stubs/android-29/android.jar
AAPT2=$T/aapt2
DEX=$T/r8/compatdx-master.jar
SIGN=$T/wb/libs/apksigner/apksigner.jar
KS=$M/keystore/ks.jks
OUT=${1:-/home/user/Atta/Atta-2.0.1.apk}
W=$(mktemp -d /tmp/attab2-XXXXXX)
cd "$M"
echo "== resources =="
rm -rf res/xml res/values res/values-*
mkdir -p res/xml res/values
cp -f res-src/xml/*.xml res/xml/
cp -f res-src/values/*.xml res/values/ 2>/dev/null || true
"$AAPT2" compile --dir res -o "$W/res.zip" 2>&1 | tail -3
echo "== link =="
"$AAPT2" link -o "$W/base.apk" -I "$ANDROID_JAR" \
  --manifest AndroidManifest.xml \
  --min-sdk-version 26 --target-sdk-version 28 \
  --version-code ${VCODE:-30} --version-name ${VNAME:-2.0.0} \
  "$W/res.zip" 2>&1 | tail -5
echo "== javac =="
find "$M/java-src" -name '*.java' > "$W/sources.txt"
mkdir -p "$W/classes"
"$J" -cp "$JAVAC" com.sun.tools.javac.Main -source 1.8 -target 1.8 -encoding UTF-8 -nowarn \
  -bootclasspath "$ANDROID_JAR" -d "$W/classes" @"$W/sources.txt" 2>&1 | grep -vE 'Note|deprecat' | head -10
echo "compile ok ($(wc -l < "$W/sources.txt") files)"
echo "== dex =="
find "$W/classes" -name '*.class' > "$W/cls.txt"
"$J" -cp "$DEX" com.android.tools.r8.compatdx.CompatDx --dex --min-sdk-version=26 --output="$W/classes.dex" $(cat "$W/cls.txt") 2>&1 | head -5
echo "== assemble =="
python3 - "$W/base.apk" "$W/classes.dex" "$W/out.apk" <<'PY'
import sys, zipfile
base, dex, out = sys.argv[1], sys.argv[2], sys.argv[3]
z = zipfile.ZipFile(base)
dz = open(dex, 'rb').read()
zo = zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED)
# ۱) AndroidManifest، ۲) classes.dex (الزامی — aapt2 آن را نمی‌سازد)، ۳) بقیه
names = list(z.namelist())
def write(name, data):
    zi = zipfile.ZipInfo(name, date_time=(2026,9,9,0,0,0))
    zi.compress_type = zipfile.ZIP_DEFLATED
    zo.writestr(zi, data)
if 'AndroidManifest.xml' in names:
    write('AndroidManifest.xml', z.read('AndroidManifest.xml'))
write('classes.dex', dz)
for i in z.infolist():
    if i.filename == 'AndroidManifest.xml': continue
    write(i.filename, z.read(i.filename))
zo.close(); z.close()
print('assembled dex=%d bytes files=%d' % (len(dz), len(names) + 1))
PY
echo "== sign =="
"$J" -jar "$SIGN" sign --ks "$KS" --ks-key-alias atta --ks-pass pass:atta123 \
  --v1-signing-enabled true --v2-signing-enabled true --out "$OUT" "$W/out.apk" 2>&1 | head -5
"$J" -jar "$SIGN" verify --print-certs "$OUT" 2>/dev/null | grep -iE 'Verifies|sha-256' | head -2
"$AAPT2" dump badging "$OUT" 2>/dev/null | head -4
ls -la "$OUT"
rm -rf "$W"

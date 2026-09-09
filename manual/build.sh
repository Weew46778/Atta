#!/usr/bin/env bash
# ============================================================
# Atta — ساخت APK بدون Gradle / Android Studio
# زنجیره: javac -> dx (dex) -> aapt2 -> zip -> zipalign -> apksigner
#
# نیازمندی‌ها: JDK17 (javac/keytool)، android.jar، aapt2، dx، zipalign، apksigner
# همهٔ ابزارها در $TOOLS (پیش‌فرض ~/.cache/atta) باید موجود باشند.
# ============================================================
set -euo pipefail

cd "$(dirname "$0")"
TOOLS="${TOOLS:-$HOME/.cache/atta}"
JDK="${JDK:-$TOOLS/jdk17}"
ANDROID_JAR="${ANDROID_JAR:-$TOOLS/android-29.jar}"
AAPT2="${AAPT2:-$TOOLS/aapt2}"
DX="${DX:-$TOOLS/dx20.jar}"
APKSIGNER="${APKSIGNER:-$TOOLS/apksigner-warren.jar}"
ZIPALIGN="${ZIPALIGN:-$TOOLS/zipalign}"
LIBCPP="${LIBCPP:-$TOOLS}"
KS="$TOOLS/ks.jks"
KS_ALIAS=atta
KS_PASS=atta123

OUT_APK="${1:-$TOOLS/../Atta-1.0.0.apk}"
OUT_APK="$(cd "$(dirname "$OUT_APK")" && pwd)/$(basename "$OUT_APK")"

BIN="$JDK/bin"
export LD_LIBRARY_PATH="$LIBCPP${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"

echo "==> ابزارها"
"$BIN/java" -version 2>&1 | head -1
"$AAPT2" version
ls -la "$ANDROID_JAR" >/dev/null && echo "android.jar ok"

BUILD="$(mktemp -d /tmp/atta-build-XXXXXX)"
trap 'rm -rf "$BUILD"' EXIT
echo "==> ساخت در $BUILD"

# ۱) آیکون‌ها
python3 gen-icons.py
echo "==> آیکون‌ها ساخته شد"

# ۱ب) منابع xml (مسیر پایدار در ریپو؛ res/ در .gitignore است)
mkdir -p res/xml res/values
cp -f res-src/xml/*.xml res/xml/
cp -f res-src/values/*.xml res/values/
echo "==> منابع xml کپی شد"

# ۲) کامپایل جاوا
mkdir -p "$BUILD/classes"
find java-src -name '*.java' > "$BUILD/sources.txt"
echo "==> javac ($(wc -l < "$BUILD/sources.txt") فایل)"
"$BIN/javac" -encoding UTF-8 --release 8 -nowarn -cp "$ANDROID_JAR" \
    -d "$BUILD/classes" @"$BUILD/sources.txt"

# ۳) دکس
echo "==> dx"
"$BIN/java" -jar "$DX" --dex --min-sdk-version=26 --output="$BUILD/classes.dex" "$BUILD/classes"

# ۴) منابع با aapt2
"$AAPT2" compile --dir res -o "$BUILD/res.zip"
"$AAPT2" link -o "$BUILD/base.apk" -I "$ANDROID_JAR" \
    --manifest AndroidManifest.xml \
    --min-sdk-version 26 --target-sdk-version 28 \
    --version-code 25 --version-name 1.7.13 \
    -A assets \
    "$BUILD/res.zip"
echo "==> aapt2 ok"

# ۵) افزودن dex
python3 - "$BUILD" <<'EOF'
import sys, zipfile
build = sys.argv[1]
z = zipfile.ZipFile(build + '/base.apk', 'a', compression=zipfile.ZIP_DEFLATED)
z.write(build + '/classes.dex', 'classes.dex')
z.close()
print('dex added')
EOF

# ۶) کلید و امضا
if [ ! -f "$KS" ]; then
  "$BIN/keytool" -genkeypair -keystore "$KS" -alias "$KS_ALIAS" \
      -keyalg RSA -keysize 2048 -validity 10000 \
      -storepass "$KS_PASS" -dname "CN=Atta, O=Atta, C=IR" 2>/dev/null
fi
"$ZIPALIGN" -f 4 "$BUILD/base.apk" "$BUILD/aligned.apk"
"$BIN/java" -jar "$APKSIGNER" sign --ks "$KS" --ks-key-alias "$KS_ALIAS" \
    --ks-pass "pass:$KS_PASS" --key-pass "pass:$KS_PASS" \
    --out "$OUT_APK" "$BUILD/aligned.apk"
echo "==> امضا شد"

"$AAPT2" dump badging "$OUT_APK" 2>/dev/null | head -5
"$BIN/java" -jar "$APKSIGNER" verify "$OUT_APK"
echo ""
echo "✅ APK ساخته شد: $OUT_APK"
ls -la "$OUT_APK"

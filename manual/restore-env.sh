#!/usr/bin/env bash
# بازسازی خودکار محیط بیلد (بعد از هر بار پاک‌شدن sandbox):
#   JRE (jdk4py) + tools.jar (javac) + aapt2 + android.jar + compatdx + apksigner + آیکون‌ها
# استفاده:  bash manual/restore-env.sh
set -eu
T=${ATTA_TOOLS:-/home/user/Atta/.attatools}
M=/home/user/Atta/manual
mkdir -p "$T"

echo "== [1/6] JRE (jdk4py 17) =="
if [ ! -x "$T/jre/bin/java" ]; then
  pip install --quiet --break-system-packages jdk4py==17.0.9.2
  cp -r "$(python3 -c 'import jdk4py;print(jdk4py.JAVA_HOME)')" "$T/jre"
fi
"$T/jre/bin/java" -version 2>&1 | head -1

echo "== [2/6] tools.jar (javac) =="
if [ ! -f "$T/dl/package/tools.jar" ]; then
  mkdir -p "$T/npm1" && cd "$T/npm1"
  npm install --silent dataslope-tools-jar
  mkdir -p "$T/dl/package" "$T/dl/pkg/package"
  cp "$T/npm1/node_modules/dataslope-tools-jar/tools.jar" "$T/dl/package/tools.jar"
  cp "$T/dl/package/tools.jar" "$T/dl/pkg/package/tools.jar"
fi
ls -la "$T/dl/package/tools.jar"

echo "== [3/6] aapt2 =="
if [ ! -x "$T/aapt2" ]; then
  mkdir -p "$T/npm2" && cd "$T/npm2"
  npm install --silent aaptjs3
  cp "$T/npm2/node_modules/aaptjs3/bin/x64/linux/aapt2" "$T/aapt2"
  chmod +x "$T/aapt2"
fi
"$T/aapt2" version 2>&1 | head -1

echo "== [4/6] android.jar (API 29) =="
if [ ! -f "$T/ap/jars/stubs/android-29/android.jar" ]; then
  rm -rf "$T/ap"
  git clone --quiet --depth 1 --filter=blob:none --sparse https://github.com/JordanSamhi/Android-platforms "$T/ap"
  (cd "$T/ap" && git sparse-checkout set jars/stubs/android-29)
fi
ls -la "$T/ap/jars/stubs/android-29/android.jar"

echo "== [5/6] r8/compatdx + apksigner =="
if [ ! -f "$T/r8/compatdx-master.jar" ]; then
  rm -rf "$T/r8"
  git clone --quiet --depth 1 https://github.com/LineageOS/android_prebuilts_r8 "$T/r8"
fi
if [ ! -f "$T/wb/libs/apksigner/apksigner.jar" ]; then
  rm -rf "$T/wb"
  git clone --quiet --depth 1 https://github.com/warren-bank/print-apk-signature "$T/wb"
fi
ls "$T/r8/compatdx-master.jar" "$T/wb/libs/apksigner/apksigner.jar"

echo "== [6/6] آیکون‌ها (res از icon-512.png — بدون PIL) =="
cd "$M"
python3 icondownscale.py
echo "== محیط آماده است =="

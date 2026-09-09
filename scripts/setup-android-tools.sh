#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p .tools/{jre11,javac,minapk,aapt}
# Build-only dependencies; none of these binaries is shipped in the APK.
# Temurin JRE 11.0.22 mirror and Android 35 platform, pinned to repository commits.
gh api 'repos/ojeker/pava-jre/contents/v11/OpenJDK11U-jre_x64_linux_hotspot_11.0.22_7.tar.gz?ref=2b115ca01e2039ca5f956a0ab97c9e028517b1b7' -H 'Accept: application/vnd.github.raw+json' > .tools/jre11.tar.gz
gh api 'repos/Sable/android-platforms/contents/android-35/android.jar?ref=1e98db1a199e8f7f85541af26bfc27019501b132' -H 'Accept: application/vnd.github.raw+json' > .tools/android.jar
npm pack @drxiaozhi/minapk@0.3.0 --pack-destination .tools --silent
npm pack aaptjs3@2.0.2 --pack-destination .tools --silent
npm pack dataslope-tools-jar@1.0.0 --pack-destination .tools --silent
sha256sum --check <<'HASHES'
3a0fec1b9ef38d6abd86cf11f6001772b086096b6ec2588d2a02f1fa86b2b1de  .tools/jre11.tar.gz
4566663c3876e022b4fa4ced8c8697c4ab1688267f090114fd92d027b32e619b  .tools/android.jar
1d447e3b0b9d0102ce9ae38aad05ef6052625608c5face3112d4a6b7711acd31  .tools/drxiaozhi-minapk-0.3.0.tgz
26031bd1acb577edce91675ae92daf63cf85795d4e2399333edeb994dd9197e9  .tools/aaptjs3-2.0.2.tgz
a1833319f9750ad285331e1c67a56915957a0659dc1b7f4e6f1b341e061158df  .tools/dataslope-tools-jar-1.0.0.tgz
HASHES
tar -xzf .tools/jre11.tar.gz -C .tools/jre11 --strip-components=1
tar -xzf .tools/drxiaozhi-minapk-0.3.0.tgz -C .tools/minapk
tar -xzf .tools/aaptjs3-2.0.2.tgz -C .tools/aapt
tar -xzf .tools/dataslope-tools-jar-1.0.0.tgz -C .tools/javac
chmod +x .tools/aapt/package/bin/x64/linux/aapt2
.tools/jre11/bin/java -version
.tools/aapt/package/bin/x64/linux/aapt2 version

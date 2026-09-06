# ساخت و آماده‌سازی APK — MineAva

## ۱. پیش‌نیازها

- **Java (JDK) نسخه ۱۷ یا بالاتر**
- **Android SDK** (پلتفرم `android-35` و `build-tools;35.0.0`)
- (**اختیاری** ولی پیشنهادی) Gradle CLI یا `gradle wrapper`

اگر این‌ها موجود باشد، فقط کافی است از ریشهٔ ریپو اجرا کنید:

```bash
bash tools/build_apk.sh                 # debug
BUILD_TYPE=release bash tools/build_apk.sh   # release (unsigned)
```

خروجی:

```
app/build/outputs/apk/debug/app-debug.apk
app/build/outputs/apk/release/app-release-unsigned.apk
```

## ۲. اگر Gradle wrapper ندارید

این ریپو فعلاً بدون `gradle-wrapper.jar` است؛ بنابراین برای ساخت محلی،
یک‌بار Gradle را نصب کنید (یا از Android Studio استفاده کنید) و سپس:

```bash
gradle wrapper --gradle-version 8.7
./gradlew assembleDebug
```

## ۳. راه‌اندازی دستی Android SDK

روی Ubuntu:

```bash
sudo apt-get update
sudo apt-get install -y openjdk-17-jdk unzip wget
mkdir -p "$HOME/Android/Sdk/cmdline-tools"
cd "$HOME/Android/Sdk/cmdline-tools"
wget https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
unzip commandlinetools-linux-11076708_latest.zip
mv cmdline-tools latest
export ANDROID_HOME="$HOME/Android/Sdk"
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" \
  "platform-tools" "platforms;android-35" "build-tools;35.0.0"
yes | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --licenses
```

و برای اینکه `gradle` محل SDK را پیدا کند، در ریشهٔ ریپو:

```bash
echo "sdk.dir=$HOME/Android/Sdk" > local.properties
```

## ۴. ساخت خودکار در GitHub (پیشنهادی)

`.github/workflows/android-debug-build.yml` یک گردشکار CI دارد که:

1. JDK 17، Gradle و Android SDK را نصب می‌کند.
2. `tools/static_check.py` را اجرا می‌کند (بدون نیاز به SDK).
3. `assembleDebug` را اجرا و APK را به‌عنوان artifact آپلود می‌کند.

پس از هر `push`/`pull_request` می‌توانید APK debug را از
**Actions → Android debug build → Upload debug APK** دانلود کنید.

## ۵. چک سلامت بدون JDK

بسیاری از خطاهای ساخت بدیهی (عدم تعادل `{}`، JSON خراب، Activity بدون ثبت در
Manifest) را می‌توان بدون JDK/SDK پیدا کرد:

```bash
python3 tools/static_check.py
```

خروجی موفق:

```
HEALTH CHECK OK: 38 Kotlin files, assets + manifest valid.
```

## ۶. امضای Release

فایل‌های `release` ساخته‌شده `unsigned` هستند. برای انتشار باید APK را با
کلید اختصاصی امضا کنید (مثلاً در Android Studio یا با `apksigner`).

## ۷. تست دستی

- نصب APK debug روی گوشی: `adb install app/build/outputs/apk/debug/app-debug.apk`
- «اولین اجرا»: راهنمای صوتی + مجوزها.
- بخش صوت: «🎁 ساخت بستهٔ نمونهٔ داخل اپ» و سپس «🎤 صدای داخل اپ: ON» را تست کنید.
- بخش سرور: از «پنل حرفه‌ای»، «مدیریت سرور» و «🌐 لاگ و کنسول ابری» استفاده کنید.

# ستاره‌کوچولو 🌟

یک برنامهٔ فارسی و راست‌به‌چپ برای بازی و یادگیری کودکان ۸ تا ۱۰ ساله. نسخهٔ اول، آفلاین، بدون حساب کاربری، تبلیغات، خرید یا چت.

## فایل نصب

**[دریافت APK نسخهٔ ۱.۰.۰](releases/Setareh-Koochooloo-v1.0.0.apk)**

- اندروید ۸ یا جدیدتر، به همراه **Android System WebView به‌روز** (Chromium 107+ توصیه می‌شود).
- فایل APK امضاشدهٔ مستقل، بدون درخواست مجوز اینترنت، دوربین، میکروفن یا دسترسی عمومی به حافظه.
- خروجی با کلید محلی این پروژه امضا شده؛ نسخهٔ فروشگاهی نیست.
- فایل را روی گوشی باز کنید. اگر اندروید درخواست کرد، اجازهٔ نصب همین فایل را فقط برای مرورگر/فایل‌منیجر مورد استفاده بدهید و پس از نصب آن اجازه را خاموش کنید. حفاظت Play Protect را خاموش نکنید.
- پیشرفت روی دستگاه ذخیره می‌شود؛ حذف برنامه یا پاک‌کردن داده‌های آن، پیشرفت و نقاشی‌ها را حذف می‌کند.
- **نصب روی گوشی یا شبیه‌ساز اندروید هنوز تست نشده است.** امضای APK، ساختار فایل، منابع بسته‌بندی‌شده و رفتار برنامه در مرورگر تست شده‌اند؛ گزارش دقیق در [docs/TEST-REPORT.md](docs/TEST-REPORT.md).

## چه چیزهایی ساخته شده؟

- ۱۰ سیاره، هر سیاره ۲۰ مرحله در ۴ سطح: مجموع **۲۰۰ مرحله و ۸۰۰ فعالیت**.
- ۱۲ نوع فعالیت: دقت دیداری، حافظه، ریاضی، الگو، تشخیص تعداد آوا، صبر، داستان و انتخاب، دانستنی‌ها، مرتب‌سازی، نفس آرام، نقاشی و شناخت احساسات.
- بازشدن پیاپی مرحله‌ها، امتیاز تلاش، ۲۰۰ یادگاری با نام مجزا و ۱۰ نشان پایان سیاره.
- ذخیرهٔ پیشرفت پس از هر فعالیت و ادامهٔ مرحلهٔ نیمه‌کاره؛ تمرین دوباره بدون ستارهٔ تکراری.
- بازی آزاد بدون قفل و امتیاز، گالری ۱۲ نقاشی آخر، حالت بی‌صدا و راهنمای دیداری جایگزین شنیدن.
- پنل والدین، گزارش متنی، تنظیم استراحت بعد از ۱۵/۲۰/۳۰ دقیقهٔ بازی و وقفهٔ یک‌دقیقه‌ای.
- در موبایل برای پنل والدین، تصویر کاربر در بالای صفحه ← «تنظیمات و پنل والدین» را بزنید.
- سؤال حساب قبل از پنل والدین صرفاً مانع سادهٔ ورود است، نه رمز یا حفاظت امنیتی.

### مرزهای نسخهٔ اول

۲۰۰ مرحله دارای ترتیب و یادگاری مجزا هستند، اما ۲۰۰ بستهٔ محتوایی کاملاً مستقل نیستند. ۱۲ سازوکار بازی، ۲۰ سؤال دانستنی، ۱۰ داستان اخلاقی و ۵ موقعیت احساسی در مسیر ترکیب و مرور می‌شوند. عددها، الگوها، چیدمان‌ها و بعضی چالش‌ها بر اساس مرحله تغییر می‌کنند. زمان واقعی هر مرحله به کودک بستگی دارد.

داستان‌ها صحنه‌های متحرک درون‌برنامه‌ای‌اند؛ **ویدیوی مستقل، کارتون طولانی یا گویندگی فارسی ضبط‌شده نداریم**. صداها آواهای سادهٔ تعاملی‌اند. آموزش ارزشی عمومی شامل همدلی، صداقت، مسئولیت‌پذیری، قدردانی و احترام به تفاوت‌هاست؛ آموزش مذهبی اختصاصی نیست.

برنامه ابزار تمرین و سرگرمی است، نه آزمون هوش، تشخیص شخصیت، درمان اضطراب یا تضمین رشد روان‌شناختی. بازبینی تخصصی محتوای کودک و آزمون کاربردپذیری با کودکان پیش از انتشار عمومی توصیه می‌شود.

## اجرا و تست برای توسعه‌دهنده

```bash
npm ci
npm run dev -- --port 5173  # binds to 0.0.0.0; preview hosts allowed
npm test                    # seven content/invariant tests
npm run build               # dist/ + complete offline service-worker cache
```

نسخهٔ وب پس از یک بار بارگذاری کامل خروجی production روی HTTPS یا localhost، آفلاین کار می‌کند. سرور توسعه عمداً service worker ثبت نمی‌کند. APK تمام محتوا را از همان نصب اول همراه خود دارد.

```bash
node scripts/prepare-browser.mjs
npm run test:e2e             # full traversal of all 800 activities + integration tests
node tests/distribution.mjs # production offline and packed Android-origin tests
```

Playwright از Chromium همراه بستهٔ npm استفاده می‌کند؛ دانلود مرورگر از CDN لازم نیست. تست‌ها با زمان مجازی و reduced motion اجرا می‌شوند؛ این جایگزین ارزیابی شنیداری انسانی نیست. تست distribution به `dist/` و `android/build/assets/www/` حاصل ساخت APK نیاز دارد.

## ساخت دوبارهٔ APK

ساخت این پروژه عمداً سبک است: یک Activity جاوا و WebView محلی؛ بدون Gradle، Capacitor، سرویس خارجی یا native library. همهٔ درخواست‌های WebView به مبدأ داخلی محدودند و از Android assets پاسخ داده می‌شوند. خروجی گزارش با document picker سیستم و بدون مجوز عمومی حافظه ذخیره می‌شود.

ابزارها داخل `.tools/` قرار گرفته‌اند و در گیت نیستند. [scripts/setup-android-tools.sh](scripts/setup-android-tools.sh) نسخه‌های استفاده‌شده را از npm و GitHub API با بررسی SHA-256 آماده می‌کند. احراز هویت `gh` لازم است.

```bash
bash scripts/setup-android-tools.sh
.tools/jre11/bin/keytool -genkeypair -keystore .tools/little-star-local.p12 \
  -storetype PKCS12 -alias littlestar -keyalg RSA -keysize 2048 -validity 10000 \
  -dname 'CN=Little Star Local Build, O=Little Star, C=IR'
# keytool رمز را تعاملی دریافت می‌کند. همان رمز را برای ساخت وارد کنید:
read -s -p 'Keystore password: ' KEYSTORE_PASSWORD; echo
export KEYSTORE_PASSWORD
bash scripts/build-apk.sh
unset KEYSTORE_PASSWORD
```

کلید امضا را خارج از گیت و در محل امن نگه دارید؛ برای به‌روزرسانی روی نصب قبلی **همان کلید قبلی** لازم است. کلید جدید امکان آپدیت APK فعلی را ندارد و حذف نصب قدیمی، داده‌های آن را پاک می‌کند. متغیرهای `JAVA`, `JAVAC_JAR`, `ANDROID_JAR`, `AAPT2`, `D8_JAR`, `APKSIGNER_JAR`, `KEYSTORE` و `KEY_ALIAS` قابل تنظیم‌اند.

## ساختار

```
src/content.js           200-stage curriculum and curated banks
src/main.js              UI, all game mechanics, storage, parent panel
src/style.css            RTL responsive design and reduced-motion support
public/assets/           offline fonts, selected emoji, generated hero art
android/app/src/main/    offline-only Android wrapper and launcher icon
scripts/                 offline cache, Android build and test setup
tests/                   unit, browser, production-distribution tests
releases/                signed APK and checksum
```

مجوز و انتساب قلم وزیرمتن و گرافیک‌های Twemoji در `public/assets/THIRD-PARTY-NOTICES.txt` آمده است. تصویر فضانورد برای این پروژه با هوش مصنوعی تولید شده است.

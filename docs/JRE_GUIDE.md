# راهنمای JRE موبایل برای سرور Java روی گوشی

MineAva می‌تواند یک **سرور Java واقعی روی گوشی** اجرا کند
(`OnDeviceJavaServerManager` + `ProcessBuilder`). برای این کار دو چیز لازم است:

1. **JRE موبایل** (ZIP حاوی `bin/java` و `lib/...`) — همان runtime جاوا.
2. **server.jar** — سرور Vanilla / Paper / Purpur/ ... که در خود اپ وارد می‌شود.

> **چرا این فایل داخل ریپو نیست؟** Android خودش JVM همراه ندارد و یک JRE کامل هم
> چند ده مگابایت و معمولاً لایسنس/توزیع خاص دارد. بنابراین نمی‌توانم یک JRE
> عمومی و قابل‌تضمین را بدون مجوز داخل APK بگذارم. در عوض، مسیر کامل و ابزار بسته‌بندی
> و نصب آن را فراهم کرده‌ام.

---

## ۱. گرفتن JRE موبایل

یک JRE/JDK سازگار با **معماری گوشی (arm64/aarch64)** و Android پیدا کنید:

- یک build پورت‌شدهٔ OpenJDK برای Android (مثلاً JVMهایی که لانچرهای
  PojavLauncher/Android-Java پروژه‌ها استفاده می‌کنند).
- یا یک runtime جاوا برای Linux ARM که داخل همان Android محیط اجرا شود.

نکته: فقط بسته‌ای را استفاده کنید که `bin/java` واقعاً قابل اجرا باشد
(برای shell/ProcessBuilder). نسخه‌های desktop-class Windows/Linux x86 برای گوشی کار
نمی‌کنند.

## ۲. بسته‌بندی ZIP

JRE را از حالت فشرده خارج کنید و سپس:

```bash
python3 tools/make_jre_pack.py \
  --jre-dir /path/to/mobile-jre \
  --output artifacts/mineava-jre-arm64.zip
```

خروجی باید ساختار:

```
mineava-jre-arm64.zip
├── bin/java
├── lib/
├── conf/
├── legal/
└── release
```

داشته باشد. سپس در اپ:

```
منوی اصلی → مدیریت سرور → (اگر نسخه Java/Hybrid باشد)
→ 📂 نصب JRE موبایل (ZIP)
```

اگر فایل `bin/java` وجود نداشته باشد، اپ همان‌جا خطای دقیق فارسی می‌دهد و هیچ‌چیز
ناقصی ذخیره نمی‌کند.

## ۳. گرفتن server.jar

- Vanilla: از `https://www.minecraft.net/download/server` و نسخهٔ مناسب.
- Paper: صفحهٔ رسمی PaperMC.
- Purpur: صفحهٔ رسمی PurpurMC.

اگر ZIP باشد (مثلاً از وب‌سایت‌های بسته)، اپ فایل `*.jar` داخل آن را خودش پیدا و به
`server.jar` تبدیل می‌کند:

```
📦 نصب server.jar سرور
```

## ۴. اجرا

```
▶ شروع سرور Java
```

پیام خروجی دو حالت دارد:

- `ON-DEVICE JAVA: RUNNING (PID ...)` → واقعاً اجرا می‌شود.
- `ON-DEVICE JAVA: JRE نصب نشده...` یا `server.jar نصب نشده...` → فقط پیش‌نیاز را
  یادآوری می‌کند، نه ادعای اجرا.

## ۵. تنظیمات مفید

- در `server.properties`، `online-mode` روی `false` گذاشته شده تا اتصال از
  جاوا/بدراک (با Geyser) راحت‌تر باشد.
- RCON هم اگر در اپ فعال کنید، `enable-rcon=true` و پورت/رمز RCON در همان فایل
  نوشته می‌شود؛ سپس «پنل حرفه‌ای» می‌تواند فرمان‌ها را مستقیم به سرور بفرستد.

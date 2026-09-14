// آتا — موتور گفتار: صدای زن فارسی همراه اپ (بدون وابستگی به موتور گوشی) + شناسایی گفتار
// فایل‌های صوتی در public/assets/atta/voices قرار دارند و همراه نسخهٔ اندروید بسته‌بندی می‌شوند.
// برای متن‌های پویا (خارج از فهرست ضبط‌شده) از موتور محلی مرورگر فقط به‌عنوان جایگزین استفاده می‌شود.

export const NARRATOR = Object.freeze({
  id: 'ava',
  name: 'آوا',
  desc: 'صدای زن فارسی، همراهِ بستهٔ نصب آتا؛ آهسته، شمرده و بدون تکیهٔ خارج از فارسی ایران',
});

// فهرست گفتارهای ضبط‌شده — هر صفحهٔ اپ یک راهنمای صوتی اختصاصی دارد.
export const VOICE_CLIPS = Object.freeze({
  home: { file: 'home.mp3', text: 'به آتا خوش آمدی. من آوا هستم، دستیار صوتی تو. از اینجا سرور ماینکرافت بساز، سرورهایت را مدیریت کن و داخل بازی با اورلای آتا دستور بده. برای شروع، دکمهٔ ساخت سرور را بزن، یا بگو: راهنمایی.' },
  hosts: { file: 'hosts.mp3', text: 'اینجا یازده میزبان سرور ماینکرافت می‌بینی؛ از اترنوس رایگان و وی‌پی‌اس اختصاصی اترنوس تا میزبان‌های حرفه‌ای جهانی. هر کارت را که خواستی انتخاب کن؛ بقیهٔ مراحل را من قدم به قدم از تو می‌پرسم و خودکار انجام می‌دهم.' },
  wizard: { file: 'wizard.mp3', text: 'حالا قدم به قدم سرور را می‌سازیم. من یکی یکی از تو می‌پرسم: اسم سرور، نسخهٔ بازی، حالت، تعداد بازیکن، رم و منطقهٔ دیتاسنتر. هر جا فقط یک گزینه را انتخاب کن و دکمهٔ بعدی را بزن.' },
  servers: { file: 'servers.mp3', text: 'این فهرست سرورهای توست. روی هر سرور بزن تا پنل کامل مدیریت باز شود: کنسول، بازیکن‌ها، تنظیمات دقیق، بکاپ و کنترل روشن و خاموش. دکمهٔ ورود به بازی هم همین‌جاست.' },
  panel: { file: 'panel.mp3', text: 'به پنل مدیریت سرور خوش آمدی. زبانهٔ نمای کلی برای روشن و خاموش‌کردن، زبانهٔ کنسول برای دیدن لاگ و اجرای دستور، زبانهٔ بازیکن‌ها برای اوپ و وایت‌لیست، زبانهٔ تنظیمات برای تمام گزینه‌های دقیق سرور، و زبانهٔ بکاپ برای پشتیبان‌گیری است.' },
  connect: { file: 'connect.mp3', text: 'آدرس و پورت سرور را برایت کپی کردم. دکمهٔ بازکردن ماینکرافت را بزن تا بازی خودکار بالا بیاید. در بازی، بخش پلی و سپس افزودن سرور را بزن و آدرس را جایگذاری کن. اگر لانچر بوت یا پوجاو داری، دکمهٔ همان را بزن.' },
  overlay: { file: 'overlay.mp3', text: 'در حالت بازی، انگشتت را از لبهٔ چپ صفحه به داخل بکش تا پنل اورلای آتا باز شود. هر زبانه رنگ خودش را دارد و با لمس، صفحهٔ همان زبانه کنارش سر می‌خورد. بقیهٔ صفحه برای بازی آزاد است. اگر بگویی آتا، پنل با صدا باز می‌شود؛ اسم زبانه را بگو تا باز شود، و متن هر دکمه را بگو تا اجرا شود. بعد از سی ثانیه، پنل خودبه‌خود بسته می‌شود.' },
  support: { file: 'support.mp3', text: 'این مرکز پشتیبانی آتاست. سؤال‌های پرتکرار را ببین، یا در گفتگو با پشتیبان آتا بنویس یا حرف بزن تا جواب بگیری. اگر مشکل حل نشد، دکمهٔ تیکت به میزبان را بزن.' },
  settings: { file: 'settings.mp3', text: 'در تنظیمات می‌توانی گفتار من را روشن یا خاموش کنی، کنترل صوتی را فعال کنی و کلمهٔ بیداری را تغییر بدهی. با روشن‌بودن کنترل صوتی، در بازی فقط با گفتن آتا اورلای باز می‌شود.' },
  deploy: { file: 'deploy.mp3', text: 'در حال ساخت سرور هستم. سفارش ثبت شد، منابع اختصاص پیدا کرد، هستهٔ سرور دانلود شد و تنظیمات دقیق نوشته شد. چند لحظه صبر کن تا سرور روشن شود.' },
});

export function createVoice({ createAudio = () => new Audio(), synth = null, onCaption = () => {} } = {}) {
  let current = null;      // عنصر در حال پخش
  let captionTimer = null;
  const synthRef = synth ?? (typeof speechSynthesis !== 'undefined' ? speechSynthesis : null);

  function stop() {
    if (current) { current.onended = null; current.onerror = null; current.pause(); current.removeAttribute('src'); current.load(); current = null; }
    if (synthRef) { try { synthRef.cancel(); } catch { /* noop */ } }
    if (captionTimer) { clearTimeout(captionTimer); captionTimer = null; }
  }

  function caption(text) {
    onCaption(text);
    if (captionTimer) clearTimeout(captionTimer);
    captionTimer = setTimeout(() => onCaption(''), 9000);
  }

  // پخش گفتار: اول فایل همراه اپ؛ اگر نبود و موتور جایگزین فعال بود، خوانش محلی.
  async function speak(id, { allowFallback = true, base = './' } = {}) {
    stop();
    const clip = VOICE_CLIPS[id];
    if (clip) {
      caption(clip.text);
      const audio = createAudio();
      current = audio;
      audio.preload = 'auto';
      audio.src = `${base}assets/atta/voices/${clip.file}`;
      try { await audio.play(); return 'bundled'; }
      catch { /* ادامه به جایگزین */ }
      current = null;
      if (!allowFallback) return 'failed';
    }
    return speakText(clip ? clip.text : id, { captioned: !!clip });
  }

  function speakText(text, { captioned = false } = {}) {
    if (!text) return 'skipped';
    if (!captioned) caption(text);
    if (synthRef && typeof SpeechSynthesisUtterance !== 'undefined') {
      try {
        const u = new SpeechSynthesisUtterance(text);
        u.lang = 'fa-IR';
        u.rate = 0.95; u.pitch = 1.05;
        const voices = synthRef.getVoices ? synthRef.getVoices() : [];
        const fa = voices.find(v => v.lang && v.lang.toLowerCase().startsWith('fa') && /female|zan|woman/i.test(v.name))
          || voices.find(v => v.lang && v.lang.toLowerCase().startsWith('fa'));
        if (fa) u.voice = fa;
        synthRef.speak(u);
        return 'fallback';
      } catch { /* noop */ }
    }
    return 'text-only';
  }

  return { speak, speakText, stop, NARRATOR };
}

// ———————————————————————————————— شناسایی گفتار ————————————————————————————————
export function createRecognizer({ recognitionFactory = defaultRecognitionFactory, onState = () => {} } = {}) {
  let rec = null, listening = false, finalCallback = null, timer = null;

  function available() { return !!recognitionFactory(); }

  function listen({ timeoutMs = 8000, onResult, lang = 'fa-IR' }) {
    stop();
    const factory = recognitionFactory();
    if (!factory) { onState('unsupported'); return false; }
    rec = factory();
    rec.lang = lang;
    rec.interimResults = false;
    rec.maxAlternatives = 3;
    finalCallback = onResult;
    rec.onresult = (e) => {
      const alt = e.results && e.results[0] ? Array.from(e.results[0]).map(r => r.transcript) : [];
      onResult(alt[0] || '', alt);
    };
    rec.onerror = (e) => { onState(e && e.error ? `error:${e.error}` : 'error'); cleanup(); };
    rec.onend = () => { onState('idle'); cleanup(); };
    try { rec.start(); listening = true; onState('listening'); }
    catch { onState('error'); cleanup(); return false; }
    timer = setTimeout(() => { if (listening) { onState('timeout'); stop(); } }, timeoutMs);
    return true;
  }

  function cleanup() { listening = false; if (timer) { clearTimeout(timer); timer = null; } }
  function stop() { cleanup(); if (rec) { try { rec.stop(); } catch { /* noop */ } rec = null; } }
  return { available, listen, stop, get listening() { return listening; }, finalCallback };
}

function defaultRecognitionFactory() {
  const SR = (typeof window !== 'undefined') && (window.SpeechRecognition || window.webkitSpeechRecognition);
  if (!SR) return null;
  return () => new SR();
}

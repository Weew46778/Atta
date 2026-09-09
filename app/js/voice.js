/* آتا — موتور گفتار: گویندگی (TTS) و شنیدن گفتار کودک (STT)
   سه پس‌زمینه: پل بومی اندروید (AttaVoice) ← Web Speech ← بی‌صدا (فقط زیرنویس) */
(function () {
  'use strict';
  const AT = (window.AT = window.AT || {});
  const U = AT.util;

  const V = {
    mode: 'none',          // 'native' | 'web' | 'none'
    hasPersian: false,
    voiceName: '',
    enabled: true,
    _talkListeners: [],
    _queue: Promise.resolve(),
    _id: 0,
  };

  /* ---------- رویداد لالهٔ حرف‌زدن (برای انیمیشن شخصیت) ---------- */
  V.onTalk = (cb) => { V._talkListeners.push(cb); };
  function talkState(on) { V._talkListeners.forEach((cb) => { try { cb(on); } catch {} }); }

  /* ---------- راه‌اندازی ---------- */
  const native = () => window.AttaVoice && typeof window.AttaVoice.speak === 'function';
  function init() {
    if (native()) {
      V.mode = 'native';
      try { V.hasPersian = !!window.AttaVoice.hasPersian(); } catch { V.hasPersian = true; }
      return;
    }
    if ('speechSynthesis' in window) {
      V.mode = 'web';
      refreshVoices();
      if (window.speechSynthesis.onvoiceschanged !== undefined)
        window.speechSynthesis.onvoiceschanged = refreshVoices;
      // برخی مرورگرها با تأخیر صداها را می‌سازند
      setTimeout(refreshVoices, 700); setTimeout(refreshVoices, 2500);
    } else V.mode = 'none';
  }
  let webVoice = null;
  function refreshVoices() {
    if (V.mode !== 'web') return;
    const voices = window.speechSynthesis.getVoices() || [];
    const fa = voices.find((v) => /^fa/i.test(v.lang)) ||
      voices.find((v) => /persian|farsi/i.test(v.name));
    if (fa) { webVoice = fa; V.hasPersian = true; V.voiceName = fa.name; }
    else {
      webVoice = null; V.hasPersian = false;
      V.voiceName = voices.length ? voices[0].name : '';
    }
  }

  /* ---------- گویندگی ---------- */
  // speak(text, {rate:0.6..1.4 (نسبت), pitch, force}) → Promise<boolean> (true = واقعاً خوانده شد)
  V.speak = function (text, opts = {}) {
    const s = U.load().settings;
    if (!s.voice && !opts.force) return Promise.resolve(false);
    text = String(text || '').replace(/\s+/g, ' ').trim();
    if (!text) return Promise.resolve(false);
    const rate = (opts.rate || 1) * (s.rate || 1);
    const pitch = opts.pitch || 1;
    V._id++;
    const id = V._id;
    const job = V._queue.then(() => speakNow(text, rate, pitch, id));
    V._queue = job.catch(() => {});
    return job;
  };

  function speakNow(text, rate, pitch, id) {
    if (V.mode === 'none') return false;
    const startedAt = Date.now();
    return new Promise((resolve) => {
      let settled = false;
      const done = (ok) => {
        if (settled) return; settled = true; talkState(false); resolve(ok);
      };
      talkState(true);
      if (V.mode === 'native') {
        window.AttaVoiceEvents = window.AttaVoiceEvents || {};
        window.AttaVoiceEvents.onDone = (rid) => { if (+rid === id) done(true); };
        window.AttaVoiceEvents.onError = (rid) => { if (+rid === id) done(false); };
        try {
          window.AttaVoice.speak(String(id), text, clampRate(rate), pitch);
        } catch { done(false); }
        // شبکهٔ ایمنی: اگر ۳۰ ثانیه پاسخ نیامد
        setTimeout(() => done(false), 30000 + text.length * 90);
        return;
      }
      // Web Speech
      try {
        const u = new SpeechSynthesisUtterance(text);
        u.lang = webVoice ? webVoice.lang : 'fa-IR';
        if (webVoice) u.voice = webVoice;
        u.rate = clampRate(rate); u.pitch = pitch;
        u.onend = () => done(true);
        u.onerror = () => done(false);
        window.speechSynthesis.cancel();
        window.speechSynthesis.speak(u);
        setTimeout(() => done(false), 30000 + text.length * 110);
      } catch { done(false); }
    });
  }
  function clampRate(r) { return Math.min(1.6, Math.max(0.4, r)); }

  V.warmup = function () { // آماده‌سازی موتور صدا با اولین لمس (بی‌صدا)
    if (V._warmed) return;
    V._warmed = true;
    if (V.mode === 'web') {
      try { const u = new SpeechSynthesisUtterance(' '); u.volume = 0; window.speechSynthesis.speak(u); } catch {}
    }
  };

  V.stop = function () {
    try {
      if (V.mode === 'native' && native()) window.AttaVoice.stop();
      else if (V.mode === 'web') window.speechSynthesis.cancel();
    } catch {}
    talkState(false);
  };

  V.preview = function (text, opts) { // با تنظیمات بی‌اعتنای خاموشی؟ نه — با force
    const s = U.load().settings; const oldVoice = s.voice; s.voice = true;
    return V.speak(text, Object.assign({ force: true }, opts)).then((r) => { s.voice = oldVoice; return r; });
  };

  /* ---------- شنیدن گفتار کودک ---------- */
  V.sttAvailable = function () {
    if (native() && typeof window.AttaVoice.startListen === 'function') return true;
    return !!(window.SpeechRecognition || window.webkitSpeechRecognition);
  };

  // listen() → Promise<{ok, text, reason}>
  V.listen = function (opts = {}) {
    if (!V.sttAvailable()) return Promise.resolve({ ok: false, reason: 'unsupported' });
    return new Promise((resolve) => {
      let settled = false;
      const done = (res) => { if (settled) return; settled = true; resolve(res); };
      const timeout = setTimeout(() => {
        if (native()) { try { window.AttaVoice.stopListen(); } catch {} }
        done({ ok: false, reason: 'timeout' });
      }, opts.maxWait || 7000);
      if (native()) {
        window.AttaVoiceEvents = window.AttaVoiceEvents || {};
        window.AttaVoiceEvents.onSpeech = (text) => { clearTimeout(timeout); try { window.AttaVoice.stopListen(); } catch {} done({ ok: true, text: String(text || '') }); };
        window.AttaVoiceEvents.onSpeechError = (code) => { clearTimeout(timeout); try { window.AttaVoice.stopListen(); } catch {} done({ ok: false, reason: String(code || 'error') }); };
        try { window.AttaVoice.startListen(); } catch { clearTimeout(timeout); done({ ok: false, reason: 'error' }); }
        return;
      }
      try {
        const SR = window.SpeechRecognition || window.webkitSpeechRecognition;
        const r = new SR();
        r.lang = 'fa-IR'; r.interimResults = false; r.maxAlternatives = 3;
        r.onresult = (e) => {
          clearTimeout(timeout);
          const alts = Array.from(e.results[0] || []).map((x) => x.transcript);
          done({ ok: true, text: alts[0] || '', alts });
        };
        r.onerror = (e) => { clearTimeout(timeout); done({ ok: false, reason: e.error || 'error' }); };
        r.onend = () => { clearTimeout(timeout); done({ ok: false, reason: 'no-speech' }); };
        r.start();
      } catch { clearTimeout(timeout); done({ ok: false, reason: 'error' }); }
    });
  };

  /* ---------- شبیه‌سازی و تطبیق متن گفتار ---------- */
  const norm = (s) => String(s || '')
    .replace(/[\u064A\u0649]/g, 'ی').replace(/\u0643/g, 'ک')
    .replace(/[\u200c\u200f\u200e\s\u064B-\u0652.,!?؟،؛:«»"'()\-]/g, '')
    .toLowerCase();
  V.heard = function (heard, target) {
    const h = norm(heard), t = norm(target);
    if (!h || !t) return false;
    if (h === t || h.includes(t) || t.includes(h)) return true;
    // فاصلهٔ ویرایش برای تحمل خطای تلفظ
    return editDistance(h, t) <= Math.max(1, Math.floor(t.length / 5));
  };
  function editDistance(a, b) {
    const m = a.length, n = b.length;
    if (Math.abs(m - n) > 4) return 99;
    const dp = Array.from({ length: m + 1 }, (_, i) => [i, ...Array(n).fill(0)]);
    for (let j = 0; j <= n; j++) dp[0][j] = j;
    for (let i = 1; i <= m; i++)
      for (let j = 1; j <= n; j++)
        dp[i][j] = Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1, dp[i - 1][j - 1] + (a[i - 1] === b[j - 1] ? 0 : 1));
    return dp[m][n];
  }

  init();
  AT.voice = V;
})();

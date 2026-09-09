/* آتا — هستهٔ ابزارها: اعداد فارسی، تصادفِ قطعی، DOM، ذخیره‌سازی، افکت‌های صوتی و کاغذرنگی */
(function () {
  'use strict';
  const AT = (window.AT = window.AT || {});

  /* ---------- اعداد فارسی ---------- */
  const FA_DIGITS = '۰۱۲۳۴۵۶۷۸۹';
  const fa = (n) => String(n).replace(/\d/g, (d) => FA_DIGITS[+d]);

  /* ---------- تصادفِ قطعی (mulberry32) ---------- */
  function rng(seed) {
    let a = (seed >>> 0) || 1;
    return function () {
      a |= 0; a = (a + 0x6d2b79f5) | 0;
      let t = Math.imul(a ^ (a >>> 15), 1 | a);
      t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
      return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    };
  }
  function shuffled(items, seed) {
    const a = Array.from(items);
    const r = rng(seed);
    for (let i = a.length - 1; i > 0; i--) {
      const j = Math.floor(r() * (i + 1));
      [a[i], a[j]] = [a[j], a[i]];
    }
    return a;
  }
  const pick = (arr, r) => arr[Math.floor((r ? r() : Math.random()) * arr.length)];

  /* ---------- DOM ---------- */
  const esc = (s) =>
    String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const $ = (sel, root) => (root || document).querySelector(sel);
  const $$ = (sel, root) => Array.from((root || document).querySelectorAll(sel));
  function el(html) {
    const t = document.createElement('template');
    t.innerHTML = String(html).trim();
    return t.content.firstElementChild;
  }
  function speakable(html) { // متنِ خالص برای گویندگی از یک HTML
    const d = document.createElement('div');
    d.innerHTML = html;
    return (d.textContent || '').replace(/\s+/g, ' ').trim();
  }

  /* ---------- تاریخ ---------- */
  const today = () => {
    const d = new Date();
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  };
  const FA_WEEK = ['یکشنبه', 'دوشنبه', 'سه‌شنبه', 'چهارشنبه', 'پنجشنبه', 'جمعه', 'شنبه'];
  const faWeekday = (d) => FA_WEEK[typeof d === 'number' ? d : (d || new Date()).getDay()];

  /* ---------- ذخیره‌سازی ---------- */
  const KEY = 'atta-v3';
  const DEFAULT_STATE = {
    v: 3,
    role: 'explorer',
    edition: null,
    profiles: {},
    explorer: {
      name: 'کاوشگر', avatar: '🦅',
      done: [], stars: {}, coins: 0, gems: 0,
      streak: 0, lastDay: '', days: [],
      skills: {}, freePlay: {}, favorites: [], interestPicks: [],
      badges: [], treasures: {}, art: [], drafts: {},
      missed: [], hints: 0, minutes: 0, sessions: 0,
      dailyBest: {}, speakConfident: 0,
    },
    toddler: {
      name: 'جوانه', avatar: '🐣',
      sessionsDone: [], words: {}, saidWords: {}, wordLog: [],
      favorites: [], minutes: 0,
    },
    mother: { name: 'مادر', lessonsDone: [], checklists: {}, calmDone: [], minutes: 0, moodLog: [] },
    father: { name: 'پدر', lessonsDone: [], cycleStart: '', rituals: {}, checklists: {}, minutes: 0 },
    settings: { voice: true, rate: 1, sfx: true, breakMin: 25, textSize: 'md' },
    events: [],
  };
  let state = null;
  function load() {
    if (state) return state;
    const clone = (o) => (typeof structuredClone === 'function' ? structuredClone(o) : JSON.parse(JSON.stringify(o)));
    try {
      const raw = JSON.parse(localStorage.getItem(KEY) || 'null');
      state = deepMerge(clone(DEFAULT_STATE), raw);
    } catch { state = clone(DEFAULT_STATE); }
    if (!Array.isArray(state.explorer.done)) state.explorer.done = [];
    return state;
  }
  function deepMerge(base, over) {
    if (over == null || typeof over !== 'object') return base;
    for (const k of Object.keys(over)) {
      if (over[k] && typeof over[k] === 'object' && !Array.isArray(over[k]) && base[k] && typeof base[k] === 'object' && !Array.isArray(base[k]))
        deepMerge(base[k], over[k]);
      else base[k] = over[k];
    }
    return base;
  }
  let saveTimer = null;
  function save() {
    clearTimeout(saveTimer);
    saveTimer = setTimeout(() => {
      try { localStorage.setItem(KEY, JSON.stringify(state)); }
      catch { /* فضای پر — بی‌صدا رد می‌شود */ }
    }, 120);
  }
  function logEvent(kind, data) {
    state.events.push({ t: Date.now(), role: state.role, kind, data: data || {} });
    if (state.events.length > 600) state.events = state.events.slice(-500);
    save();
  }

  /* ---------- افکت‌های صوتی (WebAudio) ---------- */
  const sfx = (() => {
    let ctx = null;
    function ac() {
      if (!ctx) {
        try { ctx = new (window.AudioContext || window.webkitAudioContext)(); } catch { ctx = null; }
      }
      if (ctx && ctx.state === 'suspended') ctx.resume().catch(() => {});
      return ctx;
    }
    function tone(freq, dur, type = 'sine', vol = 0.18, when = 0, slide = 0) {
      const c = ac(); if (!c) return;
      const t0 = c.currentTime + when;
      const o = c.createOscillator(), g = c.createGain();
      o.type = type; o.frequency.setValueAtTime(freq, t0);
      if (slide) o.frequency.exponentialRampToValueAtTime(Math.max(40, freq + slide), t0 + dur);
      g.gain.setValueAtTime(0.0001, t0);
      g.gain.exponentialRampToValueAtTime(vol, t0 + 0.02);
      g.gain.exponentialRampToValueAtTime(0.0001, t0 + dur);
      o.connect(g); g.connect(c.destination);
      o.start(t0); o.stop(t0 + dur + 0.05);
    }
    function melody(notes, step = 0.12, type = 'sine', vol = 0.16) {
      notes.forEach((n, i) => { if (n) tone(n, step * 1.8, type, vol, i * step); });
    }
    const S = {
      enabled: true,
      unlock() { ac(); },
      tap() { if (S.enabled) tone(600, 0.06, 'sine', 0.08); },
      pop() { if (S.enabled) tone(880, 0.08, 'triangle', 0.14, 0, -300); },
      correct() { if (S.enabled) melody([523, 659, 784], 0.09, 'sine', 0.16); },
      bigwin() { if (S.enabled) melody([523, 659, 784, 1047, 784, 1047], 0.11, 'triangle', 0.16); },
      coin() { if (S.enabled) melody([988, 1319], 0.07, 'square', 0.07); },
      wrong() { if (S.enabled) tone(220, 0.22, 'sawtooth', 0.07, 0, -60); },
      whoosh() { if (S.enabled) tone(300, 0.25, 'sine', 0.06, 0, 500); },
      tick() { if (S.enabled) tone(1000, 0.03, 'square', 0.05); },
      star(i = 0) { if (S.enabled) melody([784, 988, 1175].slice(0, i + 1), 0.1, 'sine', 0.15); },
      unlock() { if (S.enabled) melody([392, 523, 659, 784], 0.1, 'triangle', 0.15); },
      boss() { if (S.enabled) melody([196, 185, 196, 233], 0.16, 'sawtooth', 0.08); },
      note(i) { const scale = [262, 294, 330, 349, 392, 440, 494, 523]; if (S.enabled) tone(scale[i % scale.length], 0.28, 'sine', 0.2); },
      beat(strong) { if (S.enabled) tone(strong ? 180 : 140, strong ? 0.16 : 0.1, 'sine', strong ? 0.3 : 0.2); },
    };
    return S;
  })();

  function vibrate(ms) { try { navigator.vibrate && navigator.vibrate(ms); } catch {} }

  /* ---------- کاغذرنگی ---------- */
  function confetti(host, opts = {}) {
    const canvas = el(`<canvas class="confetti-canvas" aria-hidden="true"></canvas>`);
    host.appendChild(canvas);
    const cs = getComputedStyle(canvas);
    const W = canvas.width = Math.max(300, parseInt(cs.width) || innerWidth);
    const H = canvas.height = Math.max(300, parseInt(cs.height) || innerHeight);
    const ctx = canvas.getContext('2d');
    const colors = opts.colors || ['#ffd166', '#ef476f', '#06d6a0', '#118ab2', '#c77dff', '#fb8500', '#fff'];
    const N = opts.count || 130;
    const parts = [];
    for (let i = 0; i < N; i++) {
      parts.push({
        x: Math.random() * W, y: -20 - Math.random() * H * 0.4,
        w: 6 + Math.random() * 7, h: 8 + Math.random() * 8,
        vx: (Math.random() - 0.5) * 2.6, vy: 2 + Math.random() * 3.2,
        rot: Math.random() * Math.PI, vr: (Math.random() - 0.5) * 0.25,
        c: colors[(Math.random() * colors.length) | 0],
        shape: Math.random() < 0.3 ? 'circle' : 'rect',
      });
    }
    const t0 = performance.now();
    const dur = opts.dur || 2600;
    function frame(t) {
      const dt = (t - t0);
      ctx.clearRect(0, 0, W, H);
      const fade = dt > dur - 600 ? Math.max(0, (dur - dt) / 600) : 1;
      for (const p of parts) {
        p.x += p.vx; p.y += p.vy; p.vy += 0.035; p.rot += p.vr;
        ctx.save(); ctx.globalAlpha = fade; ctx.translate(p.x, p.y); ctx.rotate(p.rot);
        ctx.fillStyle = p.c;
        if (p.shape === 'circle') { ctx.beginPath(); ctx.arc(0, 0, p.w / 2, 0, 7); ctx.fill(); }
        else ctx.fillRect(-p.w / 2, -p.h / 2, p.w, p.h);
        ctx.restore();
      }
      if (dt < dur) requestAnimationFrame(frame);
      else canvas.remove();
    }
    requestAnimationFrame(frame);
    return () => canvas.remove();
  }

  /* ---------- شمارش مهارت‌ها ---------- */
  const SKILLS = {
    memory: { name: 'حافظه', emoji: '🧠', color: '#c77dff' },
    focus: { name: 'تمرکز', emoji: '🎯', color: '#ffb703' },
    logic: { name: 'منطق و ریاضی', emoji: '🔢', color: '#48cae4' },
    words: { name: 'زبان و واژه', emoji: '📖', color: '#f28482' },
    knowledge: { name: 'دانستنی‌ها', emoji: '🔭', color: '#80ed99' },
    ethics: { name: 'اخلاق و رفتار', emoji: '💚', color: '#90be6d' },
    calm: { name: 'آرامش و هیجان', emoji: '🌊', color: '#8ecae6' },
    creativity: { name: 'خلاقیت', emoji: '🎨', color: '#f4a261' },
    speech: { name: 'گفتار و اعتماد', emoji: '🎤', color: '#ff8fab' },
  };
  function bumpSkill(skill, hit, tries) {
    const s = load().explorer;
    s.skills[skill] = s.skills[skill] || { hit: 0, try: 0 };
    s.skills[skill].hit += hit; s.skills[skill].try += tries;
    save();
  }

  /* ---------- اشتراک‌گذاری دادهٔ نقش‌ها ---------- */
  /* ---------- اعلان کوتاه ---------- */
  function toast(msg, ms) {
    const old = U.$('#toast'); if (old) old.remove();
    const t = el(`<div id="toast" role="status">${esc(msg)}</div>`);
    document.body.appendChild(t);
    setTimeout(() => t.classList.add('show'), 20);
    setTimeout(() => { t.classList.remove('show'); setTimeout(() => t.remove(), 350); }, ms || 2200);
  }

  AT.util = { fa, rng, shuffled, pick, esc, $, $$, el, speakable, today, faWeekday, load, save, logEvent, sfx, confetti, vibrate, SKILLS, bumpSkill, DEFAULT_STATE, toast };
})();

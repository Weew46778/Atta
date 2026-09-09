/* آتا — هستهٔ برنامه: بوت، مسیریابی، تب‌بار، راوی، یادآورِ استراحت */
(function () {
  'use strict';
  const AT = (window.AT = window.AT || {});
  const U = AT.util;
  const app = (AT.app = { screen: 'home' });

  /* ================= مسیریابی ================= */
  const TABS = {
    explorer: [
      { id: 'home', ico: '🏠', label: 'خانه' },
      { id: 'radar', ico: '📊', label: 'رادارِ من' },
      { id: 'free', ico: '🎮', label: 'بازیِ آزاد' },
      { id: 'parent', ico: '👨‍👩‍👧', label: 'والدین' },
    ],
    toddler: [
      { id: 'home', ico: '🏠', label: 'خانه' },
      { id: 'words', ico: '🔤', label: 'واژه‌ها' },
      { id: 'songs', ico: '🎵', label: 'آهنگ‌ها' },
      { id: 'parent', ico: '👨‍👩‍👧', label: 'والدین' },
    ],
  };
  app.go = function (screen) {
    app.screen = screen;
    const st = U.load();
    const edition = st.edition === 'toddler' ? 'toddler' : 'explorer';
    const root = U.$('#app');
    renderTabbar(edition, screen);
    window.scrollTo(0, 0);
    try { AT.voice.stop(); } catch {}
    switch (screen) {
      case 'home': (edition === 'toddler' ? AT.toddlerUI.home : AT.explorer.home)(root); break;
      case 'radar': AT.explorer.radar(root); break;
      case 'free': AT.explorer.free(root); break;
      case 'words': AT.toddlerUI.words(root); break;
      case 'songs': AT.toddlerUI.songs(root); break;
      case 'parent': AT.parentUI.home(root); break;
      default: AT.explorer.home(root);
    }
  };
  function renderTabbar(edition, active) {
    const bar = U.$('#tabbar');
    bar.innerHTML = '';
    TABS[edition].forEach((t) => {
      const b = U.el(`<button class="${t.id === active ? 'on' : ''}" aria-label="${t.label}"><span class="t-ico">${t.ico}</span><span>${t.label}</span></button>`);
      b.onclick = () => { U.sfx.tap(); app.go(t.id); };
      bar.appendChild(b);
    });
  }

  /* ================= روزهای پیوسته ================= */
  function updateStreak() {
    const st = U.load();
    const today = U.today();
    const ex = st.explorer;
    if (ex.lastDay === today) return;
    const yest = new Date(Date.now() - 864e5);
    const yKey = `${yest.getFullYear()}-${String(yest.getMonth() + 1).padStart(2, '0')}-${String(yest.getDate()).padStart(2, '0')}`;
    ex.streak = ex.lastDay === yKey ? ex.streak + 1 : 1;
    ex.lastDay = today;
    ex.days = ex.days || [];
    if (!ex.days.includes(today)) ex.days.push(today);
    if (ex.days.length > 90) ex.days = ex.days.slice(-90);
    ex.sessions++;
    U.save();
    if (ex.streak >= 2) {
      AT.explorer.checkBadges();
      setTimeout(() => U.toast(`🔥 ${U.fa(ex.streak)} روز پیوسته! ادامه بده!`), 1500);
    }
  }

  /* ================= یادآورِ استراحت ================= */
  function breakReminder() {
    const st = U.load();
    let activeMs = 0, last = Date.now(), shown = 0;
    setInterval(() => {
      if (document.hidden) { last = Date.now(); return; }
      const now = Date.now();
      activeMs += now - last;
      last = now;
      const limit = st.settings.breakMin * 60000;
      if (activeMs - shown > limit) {
        shown = activeMs;
        U.sfx.unlock();
        AT.ui.openModal(`
          <div style="text-align:center">
            <div style="font-size:3em" class="floaty">👀✨</div>
            <h3>وقتِ استراحتِ چشم‌ها!</h3>
            <p class="dim" style="line-height:2">قهرمانِ خوبی بوده‌ای! حالا:<br>۲۰ ثانیه به دوردست نگاه کن 🌄<br>چند قدم برو و آب بخور 💧<br>بعد با انرژیِ تازه برمی‌گردیم!</p>
            <button class="btn mint" id="br-ok" style="width:100%;margin-top:8px">چشم‌هایم را استراحت دادم! ✅</button>
          </div>`);
        U.$('#br-ok').onclick = () => AT.ui.closeModal();
        AT.voice.speak('وقتِ استراحتِ چشم‌ها! کمی به دوردست نگاه کن و آب بخور!', { rate: .95 }).catch(() => {});
      }
    }, 15000);
  }

  /* ================= بوت ================= */
  app.boot = function () {
    const st = U.load();
    /* پوستهٔ برنامه */
    document.body.innerHTML = `
      <div id="app" class="shell"></div>
      <nav id="tabbar" class="tabbar" aria-label="منوی اصلی"></nav>
      <div id="modal-root"></div>`;
    AT.ui.starfield();
    /* تنظیمات */
    document.documentElement.dataset.text = st.settings.textSize || 'md';
    U.sfx.enabled = st.settings.sfx !== false;
    /* راوی: اولین لمس، قفلِ صدا را باز می‌کند */
    const unlock = () => {
      U.sfx.unlock();
      AT.voice.warmup();
      removeEventListener('pointerdown', unlock);
    };
    addEventListener('pointerdown', unlock);
    /* دکمه‌های «بخون برام» */
    document.addEventListener('click', (e) => {
      const b = e.target.closest('[data-read]');
      if (b && !b.disabled) {
        e.preventDefault();
        AT.voice.speak(b.dataset.read, { rate: .95 }).catch(() => {});
        b.classList.add('playing');
        setTimeout(() => b.classList.remove('playing'), 900);
      }
    });
    /* قطع صدا هنگام مخفی‌شدن */
    document.addEventListener('visibilitychange', () => { if (document.hidden) try { AT.voice.stop(); } catch {} });
    /* خطاهای نرم */
    addEventListener('error', (e) => {
      console.warn('atta:', e.message);
    });
    updateStreak();
    breakReminder();
    /* پارامترهای نسخهٔ APK: ?edition=toddler|explorer و ?start=parent */
    try {
      const qp = new URLSearchParams(location.search);
      const ed = qp.get('edition');
      if ((ed === 'toddler' || ed === 'explorer') && !st.settings.editionChosen) {
        st.edition = ed === 'toddler' ? 'toddler' : null;
        U.save();
      }
      app.go(qp.get('start') === 'parent' ? 'parent' : 'home');
    } catch { app.go('home'); }
    /* پیشنهاد اولیهٔ پروفایل */
    if (!st.events.some((x) => x.kind === 'profile')) {
      setTimeout(() => {
        if (app.screen === 'home') {
          AT.voice.speak(st.edition === 'toddler'
            ? 'سلام! من مینام، طوطیِ مهربون! لمس کن تا با هم بازی کنیم!'
            : 'سلام! من آتام؛ سیمرغِ کوچولوی راهنمای تو! روی «ادامهٔ سفر» بزن تا ماجراجویی شروع شود!', { rate: .95 }).catch(() => {});
        }
      }, 900);
    }
    /* PWA */
    if ('serviceWorker' in navigator && location.protocol.startsWith('http')) {
      addEventListener('load', () => navigator.serviceWorker.register('sw.js').catch(() => {}));
    }
  };

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', app.boot);
  else app.boot();
})();

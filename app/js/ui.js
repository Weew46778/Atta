/* آتا — اجزای رابط مشترک: ماسکوت‌ها، مودال، جشنِ پایان مرحله، ستاره‌ها */
(function () {
  'use strict';
  const AT = (window.AT = window.AT || {});
  const U = AT.util;

  /* ---------- سیمرغ کوچولوی «آتا» (راهنمای کاوشگر) ---------- */
  function simorghSVG(size = 90) {
    return `<svg class="mascot" width="${size}" height="${size}" viewBox="0 0 120 120" aria-hidden="true">
      <defs>
        <radialGradient id="attaBody" cx="38%" cy="30%"><stop offset="0%" stop-color="#ffe9a8"/><stop offset="55%" stop-color="#ffc44d"/><stop offset="100%" stop-color="#f59e2d"/></radialGradient>
        <linearGradient id="attaTail" x1="0" y1="0" x2="1" y2="0"><stop offset="0%" stop-color="#06d6a0"/><stop offset="50%" stop-color="#4cc9f0"/><stop offset="100%" stop-color="#c77dff"/></linearGradient>
      </defs>
      <g class="body">
        <path class="tail" d="M34 78 C10 88 8 104 22 108 C34 112 44 100 46 88 Z" fill="url(#attaTail)" opacity=".9"/>
        <path class="tail" d="M32 66 C6 68 -2 82 10 90 C22 97 34 86 38 76 Z" fill="url(#attaTail)" opacity=".75" transform="rotate(8 32 66)"/>
        <ellipse cx="62" cy="66" rx="34" ry="30" fill="url(#attaBody)"/>
        <path d="M52 36 C56 24 68 24 72 36 C68 32 56 32 52 36Z" fill="#f59e2d"/>
        <circle cx="62" cy="34" r="4" fill="#ef476f"/>
        <ellipse cx="50" cy="52" rx="9" ry="11" fill="#fff" class="eye"/>
        <ellipse cx="74" cy="52" rx="9" ry="11" fill="#fff" class="eye"/>
        <circle cx="51" cy="54" r="4.4" fill="#2b2140"/><circle cx="75" cy="54" r="4.4" fill="#2b2140"/>
        <circle cx="52.4" cy="52.6" r="1.5" fill="#fff"/><circle cx="76.4" cy="52.6" r="1.5" fill="#fff"/>
        <path d="M60 62 L66 68 L60 74 L54 68 Z" fill="#ef476f"/>
        <ellipse class="mouth" cx="62" cy="79" rx="7" ry="4.5" fill="#b3541e"/>
        <circle cx="44" cy="70" r="5" fill="#ffb2a5" opacity=".7"/><circle cx="82" cy="70" r="5" fill="#ffb2a5" opacity=".7"/>
        <path d="M88 56 C104 48 110 58 104 68 C100 74 92 70 88 64Z" fill="#ffd166"/>
        <path d="M36 56 C22 48 14 58 20 68 C24 74 32 70 36 64Z" fill="#ffd166"/>
      </g>
    </svg>`;
  }

  /* ---------- طوطی «مینا» (همراهِ خردسال) ---------- */
  function parrotSVG(size = 90) {
    return `<svg class="mascot" width="${size}" height="${size}" viewBox="0 0 120 120" aria-hidden="true">
      <defs>
        <radialGradient id="minaBody" cx="38%" cy="28%"><stop offset="0%" stop-color="#b7f7c8"/><stop offset="55%" stop-color="#38c96e"/><stop offset="100%" stop-color="#159a4a"/></radialGradient>
      </defs>
      <g class="body">
        <ellipse cx="62" cy="70" rx="32" ry="28" fill="url(#minaBody)"/>
        <path d="M30 70 C14 78 16 92 28 92 C38 92 42 82 42 74Z" fill="#06d6a0"/>
        <ellipse cx="62" cy="46" rx="26" ry="24" fill="#38c96e"/>
        <path d="M46 32 C50 16 74 16 78 32 C70 26 54 26 46 32Z" fill="#ef476f"/>
        <ellipse cx="52" cy="44" rx="8.5" ry="10" fill="#fff" class="eye"/>
        <ellipse cx="72" cy="44" rx="8.5" ry="10" fill="#fff" class="eye"/>
        <circle cx="53" cy="46" r="4" fill="#2b2140"/><circle cx="73" cy="46" r="4" fill="#2b2140"/>
        <circle cx="54.2" cy="44.8" r="1.4" fill="#fff"/><circle cx="74.2" cy="44.8" r="1.4" fill="#fff"/>
        <path class="mouth" d="M58 56 L74 56 L66 70 Z" fill="#fb8500"/>
        <path d="M58 56 L74 56 L70 62 L62 62 Z" fill="#ffd166"/>
        <circle cx="44" cy="52" r="4.6" fill="#ffb2a5" opacity=".65"/><circle cx="80" cy="52" r="4.6" fill="#ffb2a5" opacity=".65"/>
        <path d="M90 62 C106 54 112 66 104 74 C98 80 92 74 90 68Z" fill="#4cc9f0"/>
        <path d="M62 96 C58 106 66 112 74 108 C80 104 76 96 70 95Z" fill="#c77dff"/>
      </g>
    </svg>`;
  }

  /* ---------- شخصیت راهنما ---------- */
  const MASCOTS = {
    explorer: { svg: simorghSVG, name: 'آتا', hello: 'سلام! من آتام؛ سیمرغِ کوچولوی راهنمای تو! با هم کهکشان دانایی را می‌گردیم!' },
    toddler: { svg: parrotSVG, name: 'مینا', hello: 'سلام! من مینام؛ طوطیِ مهربون! با هم حرف می‌زنیم و بازی می‌کنیم!' },
  };

  /* ---------- مودال ---------- */
  let modalCleanup = null;
  function openModal(html, opts = {}) {
    closeModal();
    const root = U.$('#modal-root');
    root.innerHTML = `<div class="overlay"><section class="modal ${opts.wide ? 'wide' : ''}" role="dialog" aria-modal="true">${html}</section></div>`;
    document.body.classList.add('modal-open');
    const overlay = U.$('.overlay', root);
    overlay.addEventListener('click', (e) => { if (e.target === overlay && !opts.locked) closeModal(); });
    return U.$('.modal', root);
  }
  function closeModal() {
    AT.voice && AT.voice.stop();
    if (modalCleanup) { try { modalCleanup(); } catch {} modalCleanup = null; }
    U.$('#modal-root').innerHTML = '';
    document.body.classList.remove('modal-open');
  }
  function onModalClose(fn) { modalCleanup = fn; }

  /* ---------- ستاره‌ها ---------- */
  function stars(n, of = 3, size = 1) {
    let s = '';
    for (let i = 0; i < of; i++) s += `<span class="${i < n ? '' : 'off'}">${i < n ? '⭐' : '✩'}</span>`;
    return `<span class="stars" style="font-size:${size}em">${s}</span>`;
  }

  /* ---------- جعبهٔ گویندگی ---------- */
  function readBtn(text, label) {
    return `<button class="read-btn" data-read="${U.esc(text)}" aria-label="${U.esc(label || 'بشنو')}">🔊 <span>${U.esc(label || 'برام بخون')}</span></button>`;
  }

  /* ---------- جشنِ پایان مرحله ---------- */
  function celebrate(host, { coins, starCount, treasure, title, subtitle, badges }) {
    U.sfx.bigwin(); U.confetti(document.body, { dur: 3200 });
    const T = treasure ? treasure : null;
    host.innerHTML = `
      <div style="text-align:center" class="pop-in">
        <div style="font-size:3.2em" class="floaty">🏆</div>
        <h2 style="margin:.1em 0">${U.esc(title)}</h2>
        <p class="dim" style="margin:.2em 0 10px">${U.esc(subtitle || '')}</p>
        <div class="ceremony-stars" style="font-size:2.2em;margin:8px 0">${stars(0, 3)}</div>
        ${T ? `<div class="treasure-card glass-strong">
          <div style="font-size:2.6em" class="bounce-in">${T.emoji}</div>
          <b>گنجِ این مرحله: ${U.esc(T.name)}</b>
          <p style="font-size:.9em;color:var(--ink-dim);margin:.4em 0 0">💎 ${U.esc(T.fact)}</p>
        </div>` : ''}
        <div class="reward-row" style="display:flex;gap:8px;justify-content:center;flex-wrap:wrap;margin:12px 0">
          <span class="chip pill-gold">🪙 ${U.fa(coins)} سکهٔ طلا</span>
          ${badges && badges.length ? badges.map((b) => `<span class="chip" style="border-color:var(--sun)">${b.emoji} نشانِ ${U.esc(b.name)}</span>`).join('') : ''}
        </div>
        <button class="btn big" id="ceremony-done">بزن بریم! ✨</button>
      </div>`;
    // آشکارسازی ستاره‌ها یکی‌یکی
    const starEls = U.$$('.ceremony-stars span', host);
    starEls.forEach((el, i) => {
      setTimeout(() => {
        if (i < starCount) { el.textContent = '⭐'; U.sfx.star(i); el.classList.add('bounce-in'); }
      }, 700 + i * 550);
    });
    setTimeout(() => { AT.voice.speak(`آفرین قهرمان! ${starCount} ستاره گرفتی! ${treasure ? 'گنج این مرحله: ' + treasure.name + '. ' + treasure.fact : ''}`, { rate: .95 }).catch(() => {}); }, 900);
    return new Promise((res) => {
      const btn = U.$('#ceremony-done', host);
      btn.onclick = () => { U.sfx.pop(); res(); };
    });
  }

  /* ---------- حبابِ گفتار ماسکوت ---------- */
  function mascotSay(mascot, text, opts = {}) {
    if (typeof mascot === 'string') mascot = MASCOTS[mascot] || MASCOTS.explorer;
    const size = typeof opts.size === 'number' ? opts.size : 64;
    return `<div class="mascot-bubble glass-strong" style="display:flex;gap:10px;align-items:flex-start;padding:10px 12px;border-radius:18px;margin:8px 0">
      <div style="flex:none">${mascot.svg(size)}</div>
      <div style="align-self:center">
        <b style="color:var(--sun)">${U.esc(mascot.name)}</b>
        <p style="margin:.15em 0;font-size:.95em;line-height:1.9">${U.esc(text)}</p>
      </div>
    </div>`;
  }

  /* ---------- زمینهٔ ستاره‌ای ---------- */
  function starfield() {
    const cv = document.createElement('canvas');
    cv.id = 'starfield';
    document.body.prepend(cv);
    const ctx = cv.getContext('2d');
    let W, H, stars = [], running = true;
    function resize() {
      W = cv.width = innerWidth; H = cv.height = innerHeight;
      stars = Array.from({ length: Math.min(90, (W * H) / 16000 | 0) }, () => ({
        x: Math.random() * W, y: Math.random() * H, r: Math.random() * 1.5 + .4,
        p: Math.random() * Math.PI * 2, s: .008 + Math.random() * .02,
      }));
    }
    resize();
    addEventListener('resize', resize);
    document.addEventListener('visibilitychange', () => { running = !document.hidden; if (running) requestAnimationFrame(frame); });
    function frame() {
      if (!running) return;
      ctx.clearRect(0, 0, W, H);
      for (const st of stars) {
        st.p += st.s;
        const a = .25 + Math.abs(Math.sin(st.p)) * .6;
        ctx.globalAlpha = a;
        ctx.fillStyle = '#fff';
        ctx.beginPath(); ctx.arc(st.x, st.y, st.r, 0, 7); ctx.fill();
      }
      ctx.globalAlpha = 1;
      requestAnimationFrame(frame);
    }
    requestAnimationFrame(frame);
  }

  AT.ui = { simorghSVG, parrotSVG, MASCOTS, openModal, closeModal, onModalClose, stars, readBtn, celebrate, mascotSay, starfield };
})();

/* آتا — گوشهٔ والدین: درس‌نامه‌های مادر و پدر، آرامش فوری، جمله‌های داغ، برنامهٔ روز، پیشرفت کودک، تنظیمات */
(function () {
  'use strict';
  const AT = (window.AT = window.AT || {});
  const U = AT.util;
  const P = () => AT.parent;
  const UI = (AT.parentUI = {});
  const state = () => U.load();

  /* ================= خانهٔ والدین ================= */
  UI.home = function (root) {
    const s = state();
    const wk = U.faWeekday(new Date());
    root.innerHTML = `
      <header class="topbar">
        <button class="btn round ghost" id="pt-back" aria-label="بازگشت">⟶</button>
        <div style="flex:1;text-align:center"><b>👨‍👩‍👧 گوشهٔ والدین</b><br><small class="dim">${wk} — همراهِ آگاه، کودکِ شاداب</small></div>
        <span style="width:56px"></span>
      </header>
      <div class="card glass-strong glow" id="pt-calm" style="cursor:pointer">
        <div style="display:flex;align-items:center;gap:12px">
          <span style="font-size:2.4em" class="floaty">🫧</span>
          <div style="flex:1"><b>آرامشِ فوریِ من</b><br><small class="dim">وقتی عصبانی یا خسته‌ای؛ ۱ دقیقه نفس</small></div>
          <span style="font-size:1.4em">▶️</span>
        </div>
      </div>
      <div class="parent-grid" style="margin-top:10px">
        <button class="card tight" id="pt-mother" style="cursor:pointer;text-align:center;padding:16px 8px">
          <div style="font-size:2em">👩</div><b>درس‌نامهٔ مادر</b>
          <small class="dim" style="display:block">${U.fa(s.mother.lessonsDone.length)}/${U.fa(P().mother.length)} درس</small>
        </button>
        <button class="card tight" id="pt-father" style="cursor:pointer;text-align:center;padding:16px 8px">
          <div style="font-size:2em">👨</div><b>درس‌نامهٔ پدر</b>
          <small class="dim" style="display:block">${U.fa(s.father.lessonsDone.length)}/${U.fa(P().father.length)} درس</small>
        </button>
        <button class="card tight" id="pt-hot" style="cursor:pointer;text-align:center;padding:16px 8px">
          <div style="font-size:2em">🔥</div><b>جمله‌های داغ</b>
          <small class="dim" style="display:block">${U.fa(P().hotScripts.length)} موقعیتِ پرتنش</small>
        </button>
        <button class="card tight" id="pt-day" style="cursor:pointer;text-align:center;padding:16px 8px">
          <div style="font-size:2em">📅</div><b>برنامهٔ روز</b>
          <small class="dim" style="display:block">چهار ایستگاهِ روزانه</small>
        </button>
      </div>
      <button class="card glass-strong" id="pt-child" style="cursor:pointer;width:100%;margin-top:10px;text-align:right">
        <div style="display:flex;align-items:center;gap:12px">
          <span style="font-size:2.2em">🌱</span>
          <div style="flex:1"><b>پیشرفتِ کودک</b><br><small class="dim">گزارشِ بازی‌ها، مهارت‌ها و گنج‌ها</small></div>
          <span style="font-size:1.4em">📊</span>
        </div>
      </button>
      <button class="card" id="pt-settings" style="cursor:pointer;width:100%;margin-top:10px;text-align:right">
        <div style="display:flex;align-items:center;gap:12px">
          <span style="font-size:2.2em">⚙️</span>
          <div style="flex:1"><b>تنظیمات و حالت‌ها</b><br><small class="dim">صدا، متن، تعویضِ نسخهٔ کودک</small></div>
        </div>
      </button>
      <p class="dim" style="text-align:center;font-size:.75em;margin-top:12px;line-height:1.9">
        این درس‌نامه‌ها آموزشی و عمومی‌اند و جایگزینِ مشاورهٔ تخصصی نیستند.<br>ساختهٔ خانوادهٔ آتا — نسخهٔ ۳
      </p>`;
    U.$('#pt-back', root).onclick = () => AT.app.go('home');
    U.$('#pt-calm', root).onclick = () => UI.calm(root);
    U.$('#pt-mother', root).onclick = () => UI.lessons(root, 'mother');
    U.$('#pt-father', root).onclick = () => UI.lessons(root, 'father');
    U.$('#pt-hot', root).onclick = () => UI.hot(root);
    U.$('#pt-day', root).onclick = () => UI.dayPlan(root);
    U.$('#pt-child', root).onclick = () => UI.childProgress(root);
    U.$('#pt-settings', root).onclick = () => UI.settings(root);
  };

  /* ================= آرامشِ فوری ================= */
  UI.calm = function (root) {
    const list = P().parentCalm;
    root.innerHTML = `
      <header class="topbar">
        <button class="btn round ghost" id="pc-back">⟶</button>
        <div style="flex:1;text-align:center"><b>🫧 آرامشِ فوریِ من</b></div>
        <span style="width:56px"></span>
      </header>
      <div id="pc-list" style="display:flex;flex-direction:column;gap:8px"></div>`;
    U.$('#pc-back', root).onclick = () => UI.home(root);
    list.forEach((c, i) => {
      const s = state();
      const done = s.mother.calmDone.includes(i);
      const b = U.el(`<button class="card glass-strong" style="cursor:pointer;width:100%;text-align:right">
        <div style="display:flex;align-items:center;gap:12px">
          <span style="font-size:1.9em">${done ? '✅' : '🫧'}</span>
          <div style="flex:1"><b>${U.esc(c.t)}</b><br><small class="dim">${U.fa(c.steps.length)} قدم</small></div>
          <span>▶️</span>
        </div>
      </button>`);
      b.onclick = () => UI.calmPlay(root, i);
      U.$('#pc-list', root).appendChild(b);
    });
  };
  UI.calmPlay = function (root, i) {
    const c = P().parentCalm[i];
    let k = 0;
    root.innerHTML = `
      <header class="topbar">
        <button class="btn round ghost" id="cp-back">⟶</button>
        <div style="flex:1;text-align:center"><b>🫧 ${U.esc(c.t)}</b></div>
        <span style="width:56px"></span>
      </header>
      <div class="breath-circle" id="cp-circle">آماده…</div>
      <div id="cp-steps" style="text-align:center;font-weight:700;line-height:2.2;min-height:2.4em"></div>
      <div style="text-align:center;margin-top:10px"><button class="btn ghost small" id="cp-skip">رد کردن صدا 🔇</button></div>`;
    U.$('#cp-back', root).onclick = () => { AT.voice.stop(); UI.calm(root); };
    let mute = false;
    U.$('#cp-skip', root).onclick = () => { mute = true; AT.voice.stop(); };
    const circle = U.$('#cp-circle', root);
    async function run() {
      if (k >= c.steps.length) {
        const s = state();
        if (!s.mother.calmDone.includes(i)) { s.mother.calmDone.push(i); s.mother.minutes += 2; U.save(); }
        U.sfx.bigwin();
        root.appendChild(U.el(`<div class="feedback good"><span class="f-emoji">🫧</span><div><b>آرام‌تر شدی؟</b><br>هر وقت لازم شد، اینجا هستیم. بچه‌ها آرامش را از ما یاد می‌گیرند.</div></div>
          <button class="btn mint" id="cp-done" style="width:100%;margin-top:8px">ممنون، برگردم ⟶</button>`));
        U.$('#cp-done', root).onclick = () => UI.calm(root);
        return;
      }
      U.$('#cp-steps', root).innerHTML = `قدمِ ${U.fa(k + 1)} از ${U.fa(c.steps.length)}: ${U.esc(c.steps[k])}`;
      if (/دم|نگه|بازدم/.test(c.steps[k])) {
        circle.classList.add('inhale');
        if (!mute) await AT.voice.speak(c.steps[k], { rate: .75 }).catch(() => {});
        circle.classList.remove('inhale');
      } else if (!mute) await AT.voice.speak(c.steps[k], { rate: .85 }).catch(() => {});
      k++;
      setTimeout(run, 400);
    }
    run();
  };

  /* ================= درس‌نامه‌ها ================= */
  UI.lessons = function (root, who) {
    const list = P()[who];
    const s = state()[who];
    const isMother = who === 'mother';
    root.innerHTML = `
      <header class="topbar">
        <button class="btn round ghost" id="ls-back">⟶</button>
        <div style="flex:1;text-align:center"><b>${isMother ? '👩 درس‌نامهٔ مادر' : '👨 درس‌نامهٔ پدر'}</b><br>
          <small class="dim">${U.fa(s.lessonsDone.length)} از ${U.fa(list.length)} درس کامل شد</small>
        </div>
        <span style="width:56px"></span>
      </header>
      <div class="progress" style="margin-bottom:10px"><div class="progress-fill" style="width:${(s.lessonsDone.length / list.length) * 100}%"></div></div>
      ${!isMother ? `<div class="card glass-strong" id="fa-cycle" style="cursor:pointer">
        <div style="display:flex;align-items:center;gap:12px">
          <span style="font-size:2em" class="floaty">🔄</span>
          <div style="flex:1" id="fa-cycle-info"></div>
          <span style="font-size:1.4em">▶️</span>
        </div>
      </div>` : ''}
      <div id="ls-list" style="display:flex;flex-direction:column;gap:8px;margin-top:8px"></div>`;
    U.$('#ls-back', root).onclick = () => UI.home(root);
    if (!isMother) {
      const info = U.$('#fa-cycle-info', root);
      const fa = state().father;
      if (fa.cycleStart) {
        const days = Math.floor((Date.now() - new Date(fa.cycleStart + 'T00:00:00').getTime()) / 864e5) + 1;
        info.innerHTML = `<b>چرخهٔ ۱۰ روزهٔ حضور</b><br><small class="dim">روزِ ${U.fa(Math.min(10, days))} از ۱۰ ${days > 10 ? '— چرخه تمام شد، دوباره شروع کن!' : ''}</small>`;
      } else info.innerHTML = `<b>چرخهٔ ۱۰ روزهٔ حضور</b><br><small class="dim">هنوز شروع نشده — از امروز!</small>`;
      U.$('#fa-cycle', root).onclick = () => UI.fatherCycle(root);
    }
    const wrap = U.$('#ls-list', root);
    list.forEach((l, i) => {
      const done = s.lessonsDone.includes(i);
      const b = U.el(`<button class="card ${done ? '' : 'glass-strong'}" style="cursor:pointer;width:100%;text-align:right">
        <div style="display:flex;align-items:center;gap:12px">
          <span style="font-size:1.9em">${done ? '✅' : l.emoji}</span>
          <div style="flex:1"><b>${U.esc(l.t)}</b><br><small class="dim">⏱️ ${U.fa(l.min)} دقیقه • ${U.esc(l.body[0][0] || '').slice(0, 46)}…</small></div>
          <span style="font-size:1.3em">${done ? '🔁' : '▶️'}</span>
        </div>
      </button>`);
      b.onclick = () => UI.lesson(root, who, i);
      wrap.appendChild(b);
    });
  };
  UI.lesson = function (root, who, i) {
    const l = P()[who][i];
    const s = state()[who];
    const checks = s.checklists[i] || l.checklist.map(() => false);
    const bodyHTML = l.body.map((sec) => {
      const isH = sec.length === 1;
      return isH
        ? `<h4 class="lesson-h">${U.esc(sec[0])}</h4>`
        : `<p class="lesson-p">${U.esc(sec[0]).replace(/\n/g, '<br>')}</p>`;
    }).join('');
    root.innerHTML = `
      <header class="topbar">
        <button class="btn round ghost" id="le-back">⟶</button>
        <div style="flex:1;text-align:center"><b>${l.emoji} ${U.esc(l.t)}</b><br><small class="dim">⏱️ ${U.fa(l.min)} دقیقه</small></div>
        <span style="width:56px"></span>
      </header>
      <div class="card glass-strong lesson-body">
        ${bodyHTML}
        ${l.practice.length ? `<h4 class="lesson-h">🎯 تمرینِ این درس</h4>
          <ul class="lesson-list">${l.practice.map((p) => `<li>${U.esc(p)}</li>`).join('')}</ul>` : ''}
        ${l.checklist.length ? `<h4 class="lesson-h">✅ چک‌لیستِ عملی</h4>
          <div id="le-checks" style="display:flex;flex-direction:column;gap:6px"></div>` : ''}
      </div>
      <button class="btn mint big" id="le-done" style="width:100%;margin-top:10px">این درس را کامل کردم! ✅</button>`;
    U.$('#le-back', root).onclick = () => UI.lessons(root, who);
    const cw = U.$('#le-checks', root);
    l.checklist.forEach((c, j) => {
      const b = U.el(`<button class="check-row ${checks[j] ? 'on' : ''}" data-j="${j}">
        <span class="check-box">${checks[j] ? '✔️' : ''}</span><span>${U.esc(c)}</span>
      </button>`);
      b.onclick = () => {
        checks[j] = !checks[j];
        b.classList.toggle('on', checks[j]);
        U.$('.check-box', b).textContent = checks[j] ? '✔️' : '';
        U.sfx.tap();
        const st = state();
        st[who].checklists[i] = checks;
        U.save();
      };
      cw.appendChild(b);
    });
    U.$('#le-done', root).onclick = () => {
      const st = state();
      if (!st[who].lessonsDone.includes(i)) {
        st[who].lessonsDone.push(i);
        st[who].minutes += l.min;
        U.save();
        U.logEvent('parent-lesson', { who, i });
        U.sfx.bigwin();
        U.toast('آفرین! درس ثبت شد 🌟');
      } else U.toast('قبلاً کامل شده بود؛ دوباره خواندی، بهتر!');
      UI.lessons(root, who);
    };
  };

  /* ================= چرخهٔ ۱۰ روزهٔ پدر ================= */
  UI.fatherCycle = function (root) {
    const s = state().father;
    const today = U.today();
    const todayMin = s.rituals[today] || 0;
    let days = 0;
    if (s.cycleStart) days = Math.floor((Date.now() - new Date(s.cycleStart + 'T00:00:00').getTime()) / 864e5) + 1;
    root.innerHTML = `
      <header class="topbar">
        <button class="btn round ghost" id="fc-back">⟶</button>
        <div style="flex:1;text-align:center"><b>🔄 چرخهٔ ۱۰ روزهٔ حضور</b></div>
        <span style="width:56px"></span>
      </header>
      <div class="card glass-strong">
        <p class="lesson-p">ده روزِ پیوسته، هر روز فقط <b>۲۰ دقیقه</b> حضورِ واقعی: بدون گوشی، با چشم‌درچشم. این چرخه، پیوندِ پدر و کودک را عمیق می‌کند.</p>
        <div style="display:grid;grid-template-columns:repeat(5,1fr);gap:6px;margin:10px 0" id="fc-days"></div>
        <div style="text-align:center;font-weight:800">${s.cycleStart ? `روزِ ${U.fa(Math.min(10, days))} از ۱۰` : 'چرخه شروع نشده'}</div>
      </div>
      <div class="card glass-strong">
        <b>🎯 آیینِ امروز (${U.faWeekday(new Date())})</b>
        <p class="lesson-p">امروز <b>${U.fa(todayMin)} دقیقه</b> حضورِ واقعی ثبت شده است.</p>
        <div style="display:flex;gap:8px">
          <button class="btn sky" id="fc-add10" style="flex:1">۱۰ دقیقه اضافه کن ⏱️</button>
          <button class="btn mint" id="fc-add20" style="flex:1">۲۰ دقیقه کامل شد ✅</button>
        </div>
      </div>
      <button class="btn ${s.cycleStart ? 'ghost' : 'coral'} big" id="fc-start" style="width:100%;margin-top:10px">
        ${s.cycleStart ? 'چرخه را از امروز دوباره شروع کن 🔄' : 'چرخه را از امروز شروع کن! ▶️'}
      </button>`;
    U.$('#fc-back', root).onclick = () => UI.lessons(root, 'father');
    const grid = U.$('#fc-days', root);
    for (let d = 1; d <= 10; d++) {
      const filled = s.cycleStart && d <= Math.min(10, days);
      grid.appendChild(U.el(`<span class="cycle-day ${filled ? 'on' : ''}">${U.fa(d)}</span>`));
    }
    U.$('#fc-add10', root).onclick = () => addRitual(10);
    U.$('#fc-add20', root).onclick = () => addRitual(20);
    function addRitual(min) {
      const st = state();
      st.father.rituals[today] = (st.father.rituals[today] || 0) + min;
      st.father.minutes += min;
      if (!st.father.cycleStart) st.father.cycleStart = today;
      U.save();
      U.sfx.coin();
      U.toast(`${U.fa(min)} دقیقهٔ حضور ثبت شد! 🌟`);
      UI.fatherCycle(root);
    }
    U.$('#fc-start', root).onclick = () => {
      const st = state();
      st.father.cycleStart = today;
      U.save();
      U.sfx.unlock();
      U.toast('چرخهٔ تازه شروع شد! روز اول، روزِ بهترین‌هاست!');
      UI.fatherCycle(root);
    };
  };

  /* ================= جمله‌های داغ ================= */
  UI.hot = function (root) {
    root.innerHTML = `
      <header class="topbar">
        <button class="btn round ghost" id="ht-back">⟶</button>
        <div style="flex:1;text-align:center"><b>🔥 جمله‌های داغ</b><br><small class="dim">راه‌حلِ آماده برای لحظه‌های بحرانی</small></div>
        <span style="width:56px"></span>
      </header>
      <div id="ht-list" style="display:flex;flex-direction:column;gap:8px"></div>`;
    U.$('#ht-back', root).onclick = () => UI.home(root);
    P().hotScripts.forEach((h) => {
      const card = U.el(`<div class="card glass-strong">
        <button class="hot-head" style="width:100%;text-align:right;cursor:pointer">
          <div style="display:flex;align-items:center;gap:10px">
            <span style="font-size:1.8em">${h.emoji}</span>
            <b style="flex:1;text-align:right">${U.esc(h.t)}</b>
            <span class="hot-arrow">▾</span>
          </div>
        </button>
        <div class="hot-body" style="display:none;margin-top:10px"></div>
      </div>`);
      const body = U.$('.hot-body', card);
      U.$('.hot-head', card).onclick = () => {
        const open = body.style.display !== 'none';
        body.style.display = open ? 'none' : '';
        U.$('.hot-arrow', card).textContent = open ? '▾' : '▴';
        if (!open) {
          body.innerHTML = `<ol class="lesson-list hot-lines">${h.lines.map((l) => `<li>${U.esc(l)}</li>`).join('')}</ol>
            <button class="read-btn" data-read="${U.esc(h.t + '. ' + h.lines.join(' '))}">🔊 بخون برام</button>`;
        }
      };
      U.$('#ht-list', root).appendChild(card);
    });
  };

  /* ================= برنامهٔ روز ================= */
  UI.dayPlan = function (root) {
    root.innerHTML = `
      <header class="topbar">
        <button class="btn round ghost" id="dp-back">⟶</button>
        <div style="flex:1;text-align:center"><b>📅 برنامهٔ روزِ خانواده</b></div>
        <span style="width:56px"></span>
      </header>
      <p class="dim" style="text-align:center;font-size:.85em">چهار ایستگاهِ هر روز؛ برای خردسال و بزرگ‌تر جداگانه</p>
      <div id="dp-list" style="display:flex;flex-direction:column;gap:10px"></div>`;
    U.$('#dp-back', root).onclick = () => UI.home(root);
    P().dailyBlocks.forEach((b) => {
      U.$('#dp-list', root).appendChild(U.el(`<div class="card glass-strong">
        <div style="display:flex;align-items:center;gap:10px;margin-bottom:6px">
          <span style="font-size:1.8em">${b.emoji}</span>
          <b style="font-size:1.1em">${U.esc(b.h)}</b>
        </div>
        <p class="lesson-p">💡 ${U.esc(b.tip)}</p>
        <div class="day-kids">
          <div class="day-kid"><small class="dim">🐣 خردسال</small><span>${U.esc(b.kids[0]).replace(/^خردسال:\s*/, '')}</span></div>
          <div class="day-kid"><small class="dim">🦅 بزرگ‌تر</small><span>${U.esc(b.kids[1] || b.kids[0]).replace(/^بزرگ‌تر:\s*/, '')}</span></div>
        </div>
      </div>`));
    });
  };

  /* ================= پیشرفتِ کودک ================= */
  UI.childProgress = function (root) {
    const s = state();
    const ex = s.explorer, td = s.toddler;
    const skills = Object.entries(U.SKILLS).map(([k, v]) => [v, Math.min(100, Math.round(ex.skills[k] || 0))]);
    const topSkills = skills.sort((a, b) => b[1] - a[1]).slice(0, 3);
    const trCount = Object.values(ex.treasures).filter(Boolean).length;
    root.innerHTML = `
      <header class="topbar">
        <button class="btn round ghost" id="ch-back">⟶</button>
        <div style="flex:1;text-align:center"><b>🌱 پیشرفتِ کودک</b></div>
        <span style="width:56px"></span>
      </header>
      <div class="stats-grid">
        <div class="stat-cell"><b>${U.fa(ex.done.length)}</b><small>مرحلهٔ ماجراجویی</small></div>
        <div class="stat-cell"><b>${U.fa(Object.values(ex.stars).reduce((a, b) => a + b, 0))}</b><small>مجموع ستاره‌ها</small></div>
        <div class="stat-cell"><b>${U.fa(trCount)}</b><small>گنجِ دانستنی</small></div>
        <div class="stat-cell"><b>${U.fa(ex.badges.length)}</b><small>نشانِ افتخار</small></div>
        <div class="stat-cell"><b>${U.fa(ex.minutes + td.minutes)}</b><small>دقیقهٔ بازیِ هدفمند</small></div>
        <div class="stat-cell"><b>${U.fa(ex.speakConfident)}</b><small>تمرینِ گفتارِ شجاع</small></div>
        <div class="stat-cell"><b>${U.fa(td.sessionsDone.length)}</b><small>جلسهٔ گفتاریِ خردسال</small></div>
        <div class="stat-cell"><b>${U.fa(Object.keys(td.words).length)}</b><small>واژهٔ شنیده/گفته‌شده</small></div>
      </div>
      ${ex.art.length ? `<h3 class="section-title">🖼️ آخرین نقاشی‌ها</h3>
        <div class="art-grid">${ex.art.slice(-4).reverse().map((a) => `<div class="art-cell"><img src="${a.d}" alt="نقاشی" loading="lazy"></div>`).join('')}</div>` : ''}
      <h3 class="section-title">💪 نقاطِ قوتِ فعلی</h3>
      <div style="display:flex;gap:6px;flex-wrap:wrap">
        ${topSkills.length ? topSkills.map(([v, n]) => `<span class="chip" style="border-color:${v.color}66">${v.emoji} ${v.name}: ${U.fa(n)}</span>`).join('') : '<span class="dim">هنوز دادهٔ کافی نیست؛ چند مرحله بازی کنید!</span>'}
      </div>
      <h3 class="section-title">🗓️ حضور در بازی</h3>
      <div style="display:flex;gap:4px;flex-wrap:wrap;justify-content:center" id="ch-days"></div>
      <p class="dim" style="text-align:center;font-size:.8em;margin-top:8px">🔥 روزهای پیوسته: ${U.fa(ex.streak)} — بلندترین حضورِ ثبت‌شده</p>`;
    U.$('#ch-back', root).onclick = () => UI.home(root);
    /* ۲۸ روزِ اخیر */
    const daysWrap = U.$('#ch-days', root);
    for (let i = 27; i >= 0; i--) {
      const d = new Date(Date.now() - i * 864e5);
      const key = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
      const on = (ex.days || []).includes(key);
      daysWrap.appendChild(U.el(`<span class="day-dot ${on ? 'on' : ''}" title="${key}"></span>`));
    }
  };

  /* ================= تنظیمات ================= */
  UI.settings = function (root) {
    const s = state();
    root.innerHTML = `
      <header class="topbar">
        <button class="btn round ghost" id="se-back">⟶</button>
        <div style="flex:1;text-align:center"><b>⚙️ تنظیمات</b></div>
        <span style="width:56px"></span>
      </header>
      <div class="card glass-strong">
        <b>🐣 حالتِ کودک</b>
        <p class="lesson-p">اپ دو نسخه دارد: <b>کاوشگر</b> (۴ تا ۱۰ سال: ماجراجویی و مهارت‌ها) و <b>خردسال</b> (۲ تا ۳ سال: جلساتِ گفتاری با مینا).</p>
        <div style="display:flex;gap:8px">
          <button class="btn ${s.edition !== 'toddler' ? 'mint' : 'ghost'}" id="se-explorer" style="flex:1">🦅 کاوشگر</button>
          <button class="btn ${s.edition === 'toddler' ? 'mint' : 'ghost'}" id="se-toddler" style="flex:1">🐣 خردسال</button>
        </div>
      </div>
      <div class="card glass-strong" style="margin-top:10px">
        <b>🔊 صدا و راوی</b>
        <div class="set-row"><span>صدای راوی (خواندنِ متن‌ها)</span><button class="btn small ${s.settings.voice ? 'mint' : 'ghost'}" id="se-voice">${s.settings.voice ? 'روشن' : 'خاموش'}</button></div>
        <div class="set-row"><span>جلوه‌های صوتیِ بازی</span><button class="btn small ${s.settings.sfx ? 'mint' : 'ghost'}" id="se-sfx">${s.settings.sfx ? 'روشن' : 'خاموش'}</button></div>
        <div class="set-row"><span>سرعتِ خواندن</span>
          <div style="display:flex;gap:4px;align-items:center">
            <button class="btn small ghost" id="se-slower">−</button>
            <b>${U.fa(Math.round(s.settings.rate * 100))}٪</b>
            <button class="btn small ghost" id="se-faster">+</button>
          </div>
        </div>
        <button class="btn ghost small" id="se-test" style="width:100%;margin-top:8px">🔊 صدای راوی را بشنو</button>
      </div>
      <div class="card glass-strong" style="margin-top:10px">
        <b>👁️ اندازهٔ متن</b>
        <div style="display:flex;gap:8px;margin-top:8px">
          ${['sm', 'md', 'lg'].map((t) => `<button class="btn small ${s.settings.textSize === t ? 'mint' : 'ghost'}" data-ts="${t}" style="flex:1">${t === 'sm' ? 'کوچک' : t === 'md' ? 'متوسط' : 'بزرگ'}</button>`).join('')}
        </div>
        <div class="set-row" style="margin-top:10px"><span>یادآورِ استراحت (دقیقه)</span>
          <div style="display:flex;gap:4px;align-items:center">
            <button class="btn small ghost" id="se-break-minus">−</button>
            <b>${U.fa(s.settings.breakMin)}</b>
            <button class="btn small ghost" id="se-break-plus">+</button>
          </div>
        </div>
      </div>
      <div class="card" style="margin-top:10px">
        <b>♻️ داده‌ها</b>
        <p class="lesson-p">تمام پیشرفت روی همین دستگاه ذخیره می‌شود؛ بدون اینترنت و بدون حسابِ کاربری.</p>
        <button class="btn coral small" id="se-reset" style="width:100%">پاک‌کردنِ همهٔ پیشرفت‌ها</button>
      </div>`;
    U.$('#se-back', root).onclick = () => UI.home(root);
    U.$('#se-explorer', root).onclick = () => setEdition(null);
    U.$('#se-toddler', root).onclick = () => setEdition('toddler');
    function setEdition(v) {
      const st = state();
      st.edition = v;
      st.settings.editionChosen = true; /* از این پس پارامترِ APK دیگر تحمیل نمی‌شود */
      U.save();
      U.sfx.pop();
      U.toast(v === 'toddler' ? 'حالتِ خردسال فعال شد — با مینا! 🐣' : 'حالتِ کاوشگر فعال شد — با آتا! 🦅');
      AT.app.go('home');
    }
    U.$('#se-voice', root).onclick = () => {
      const st = state(); st.settings.voice = !st.settings.voice;
      AT.voice.enabled = st.settings.voice;
      U.save(); UI.settings(root);
    };
    U.$('#se-sfx', root).onclick = () => {
      const st = state(); st.settings.sfx = !st.settings.sfx;
      U.sfx.enabled = st.settings.sfx;
      U.save(); UI.settings(root);
    };
    U.$('#se-slower', root).onclick = () => adjustRate(-.1);
    U.$('#se-faster', root).onclick = () => adjustRate(.1);
    function adjustRate(d) {
      const st = state();
      st.settings.rate = Math.min(1.3, Math.max(.6, Math.round((st.settings.rate + d) * 10) / 10));
      U.save(); UI.settings(root);
    }
    U.$('#se-test', root).onclick = () => AT.voice.speak('سلام! من راویِ قصه‌های آتا هستم. این صدای من است!', { rate: state().settings.rate });
    root.querySelectorAll('[data-ts]').forEach((b) => {
      b.onclick = () => {
        const st = state();
        st.settings.textSize = b.dataset.ts;
        document.documentElement.dataset.text = b.dataset.ts;
        U.save(); UI.settings(root);
      };
    });
    U.$('#se-break-minus', root).onclick = () => adjustBreak(-5);
    U.$('#se-break-plus', root).onclick = () => adjustBreak(5);
    function adjustBreak(d) {
      const st = state();
      st.settings.breakMin = Math.min(60, Math.max(10, st.settings.breakMin + d));
      U.save(); UI.settings(root);
    }
    U.$('#se-reset', root).onclick = () => {
      AT.ui.openModal(`
        <h3>⚠️ همه‌چیز پاک شود؟</h3>
        <p class="dim">مرحله‌ها، ستاره‌ها، گنج‌ها، نقاشی‌ها و پیشرفتِ درس‌نامه‌ها برای همیشه حذف می‌شوند.</p>
        <div style="display:flex;gap:8px">
          <button class="btn ghost" id="rs-no" style="flex:1">نه، پاک نکن</button>
          <button class="btn coral" id="rs-yes" style="flex:1">بله، پاک کن</button>
        </div>`, { locked: true });
      U.$('#rs-no').onclick = () => AT.ui.closeModal();
      U.$('#rs-yes').onclick = () => {
        try { localStorage.removeItem('atta-v3'); } catch {}
        location.reload();
      };
    };
  };
})();

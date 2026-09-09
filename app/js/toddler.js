/* آتا — بخشِ خردسال (۲-۳ سال): مینا، جلساتِ هدایت‌شده، واژه‌ها، آهنگ‌ها، قصه‌ها */
(function () {
  'use strict';
  const AT = (window.AT = window.AT || {});
  const U = AT.util;
  const T = () => AT.toddler;
  const UI = (AT.toddlerUI = {});
  const ST = () => U.load().toddler;

  const say = (t, opts) => AT.voice.speak(t, Object.assign({ rate: .82, pitch: 1.15 }, opts)).catch(() => {});
  const M = () => AT.ui.MASCOTS.toddler;

  /* بانک‌های کوچکِ بازی‌ها */
  const SOUNDS = [
    ['🐱', 'گربه', 'میو میو'], ['🐶', 'سگ', 'هاپ هاپ'], ['🐮', 'گاو', 'قُر قُر'], ['🐔', 'مرغ', 'قُق‌ری‌قو'],
    ['🦆', 'اردک', 'کواک کواک'], ['🐑', 'گوسفند', 'بععع'], ['🦁', 'شیر', 'رااار'], ['🐸', 'قورباغه', 'غار غار'],
  ];
  const BODY = [['👁️', 'چشم'], ['👂', 'گوش'], ['👃', 'بینی'], ['👄', 'دهان'], ['✋', 'دست'], ['🦶', 'پا'], ['🦵', 'زانو'], ['💪', 'بازو']];
  const ACTS = [
    ['🐰', 'مثل خرگوش بپر! بپر بپر!'], ['🐻', 'مثل خرس راه برو! سنگین سنگین!'],
    ['🐦', 'مثل پرنده با دستت پرواز کن!'], ['🐸', 'مثل قورباغه بشین و بلند شو!'],
    ['🐘', 'مثل فیل با پایت کوب! بوم بوم!'], ['🐟', 'مثل ماهی شنا کن!'],
    ['⭐', 'بالا بپر و ستاره را بگیر!'], ['🦋', 'مثل پروانه آرام آرام بچرخ!'],
  ];

  /* ================= خانه ================= */
  UI.home = function (root) {
    const st = ST();
    const dayIdx = ((Date.now() / 864e5) | 0) % 20;
    const done = st.sessionsDone.includes(dayIdx);
    const name = U.esc(st.name);
    root.innerHTML = `
      <header class="topbar">
        <span style="width:56px"></span>
        <div style="flex:1;text-align:center"><b>🐣 ${name}</b><br><small class="dim">نسخهٔ خردسال — ${U.fa(st.sessionsDone.length)} جلسه انجام شد</small></div>
        <button class="btn round ghost" id="td-help" aria-label="راهنما">❓</button>
      </header>
      ${AT.ui.mascotSay('toddler', `سلام! من مینام، طوطیِ مهربون! ${done ? 'جلسهٔ امروز رو انجام دادی، آفرین! یه بازیِ دیگه می‌کنیم؟' : 'امروز با هم یه جلسهٔ قشنگ داریم!'}`, { size: 76 })}
      <button class="card glass-strong ${done ? '' : 'glow'}" id="td-session" style="cursor:pointer;width:100%;text-align:right">
        <div style="display:flex;align-items:center;gap:12px">
          <span style="font-size:2.8em" class="floaty">${done ? '⭐' : T().sessions[dayIdx].emoji}</span>
          <div style="flex:1">
            <small class="dim">جلسهٔ امروز</small>
            <div style="font-weight:900;font-size:1.2em">${U.esc(T().sessions[dayIdx].t)}</div>
            <small class="dim">قصه + بازی + آهنگ</small>
          </div>
          <span style="font-size:1.6em">${done ? '🔁' : '▶️'}</span>
        </div>
      </button>
      <div class="toddler-grid" style="margin-top:10px">
        <button class="card tight" id="td-words" style="cursor:pointer;text-align:center;padding:16px 8px">
          <div style="font-size:2em">🔤</div><b>واژه‌ها</b><small class="dim" style="display:block">لمس کن و بشنو</small>
        </button>
        <button class="card tight" id="td-songs" style="cursor:pointer;text-align:center;padding:16px 8px">
          <div style="font-size:2em">🎵</div><b>آهنگ‌ها</b><small class="dim" style="display:block">با حرکت بخوان</small>
        </button>
        <button class="card tight" id="td-stories" style="cursor:pointer;text-align:center;padding:16px 8px">
          <div style="font-size:2em">📖</div><b>قصه‌ها</b><small class="dim" style="display:block">کوتاه و شاد</small>
        </button>
        <button class="card tight" id="td-games" style="cursor:pointer;text-align:center;padding:16px 8px">
          <div style="font-size:2em">🎈</div><b>بازی‌ها</b><small class="dim" style="display:block">بازیِ آزاد</small>
        </button>
      </div>
      <div style="display:flex;gap:6px;flex-wrap:wrap;justify-content:center;margin-top:10px">
        ${T().sessions.map((s, i) => `<span class="chip" style="font-size:.75em;cursor:pointer;${st.sessionsDone.includes(i) ? 'border-color:var(--mint);background:rgba(6,214,160,.15)' : ''}" data-s="${i}">${st.sessionsDone.includes(i) ? '✅' : '○'} ${U.fa(i + 1)}</span>`).join('')}
      </div>`;
    U.$('#td-session', root).onclick = () => UI.session(root, dayIdx);
    U.$('#td-words', root).onclick = () => UI.words(root);
    U.$('#td-songs', root).onclick = () => UI.songs(root);
    U.$('#td-stories', root).onclick = () => UI.stories(root);
    U.$('#td-games', root).onclick = () => UI.playGame(root, null, null);
    U.$('#td-help', root).onclick = () => AT.ui.openModal(`
      <h3>راهنمای والدین 🐣</h3>
      <p style="line-height:2.1;font-size:.92em">این بخش برای کودکِ <b>۲ تا ۳ سال</b> طراحی شده است:<br>
      • هر جلسه ۵ تا ۷ دقیقه است؛ کمتر از این هم کافی است.<br>
      • کنار کودک بنشینید و واژه‌ها را با هم تکرار کنید.<br>
      • اگر کودک فقط نگاه کرد، اشکالی ندارد؛ تکرارِ شاد مهم است.<br>
      • در پایان هر جلسه، یک نکتهٔ تخصصیِ گفتاردرمانی برای شما نمایش داده می‌شود.</p>
      <button class="btn mint" id="th-ok" style="width:100%">متوجه شدم!</button>`);
    U.$('#th-ok') && (U.$('#th-ok').onclick = () => AT.ui.closeModal());
    root.querySelectorAll('[data-s]').forEach((c) => {
      c.onclick = () => UI.session(root, +c.dataset.s);
    });
    say('سلام! من مینام! با هم بازی کنیم؟', {});
  };

  /* ================= جریانِ جلسه ================= */
  UI.session = async function (root, idx) {
    const s = T().sessions[idx];
    const cat = s.cat;
    const words = T().words[cat] || T().words['حیوانات'];
    const story = T().stories[s.story] || T().stories[0];
    const song = T().songs[s.song] || T().songs[0];
    const st = ST();
    let step = 0;
    const steps = ['hello', 'words', 'story', 'game', 'song', 'game2', 'done'];

    root.innerHTML = `
      <div class="level-screen">
        <header class="topbar">
          <button class="btn round ghost" id="ss-exit" aria-label="خروج">✕</button>
          <div style="flex:1;text-align:center"><b>${s.emoji} ${U.esc(s.t)}</b>
            <div class="progress" style="margin-top:4px"><div class="progress-fill" id="ss-prog" style="width:0%"></div></div>
          </div>
          <span style="width:56px"></span>
        </header>
        <div id="ss-host"></div>
      </div>`;
    U.$('#ss-exit', root).onclick = () => { AT.voice.stop(); UI.home(root); };
    const host = U.$('#ss-host', root);
    const prog = () => { U.$('#ss-prog', root).style.width = `${Math.round((step / (steps.length - 1)) * 100)}%`; };

    /* قدم ۱: سلام مینا */
    prog();
    await screen_hello();
    /* قدم ۲: سه واژهٔ تازه */
    step = 1; prog();
    await screen_words(words.slice(0, 3), cat);
    /* قدم ۳: قصه */
    step = 2; prog();
    await screen_story(story);
    /* قدم ۴: بازی */
    step = 3; prog();
    await playGameInner(host, s.game, words);
    /* قدم ۵: آهنگ */
    step = 4; prog();
    await screen_song(song);
    /* قدم ۶: بازیِ دوم (نوعِ دیگر) */
    step = 5; prog();
    const game2 = { find: 'sound', sound: 'pick', pick: 'act', act: 'count', count: 'body', body: 'find' }[s.game] || 'find';
    await playGameInner(host, game2, words);
    /* قدم ۷: پایان */
    step = 6; prog();
    if (!st.sessionsDone.includes(idx)) {
      st.sessionsDone.push(idx);
      st.minutes += 6;
      U.save();
      U.logEvent('toddler-session', { idx });
    }
    U.sfx.bigwin();
    host.innerHTML = `
      <div class="card glass-strong pop-in" style="text-align:center">
        <div style="font-size:3em" class="floaty">⭐</div>
        <b style="font-size:1.2em">آفرین ${U.esc(st.name)}! جلسه تمام شد!</b>
        <div class="big-btn-row" style="margin-top:10px">
          <button class="btn mint big" id="ss-again">دوباره! 🔁</button>
          <button class="btn ghost big" id="ss-home">تمام ✋</button>
        </div>
      </div>`;
    say('آفرین! جلسهٔ امروز عالی بود!', {});
    /* نکتهٔ والد */
    setTimeout(() => {
      AT.ui.openModal(`
        <h3>💛 نکتهٔ امروز برای والدین</h3>
        <p style="line-height:2.1">${U.esc(s.tip)}</p>
        <button class="btn mint" id="tip-ok" style="width:100%">ممنون مینا!</button>`);
      U.$('#tip-ok').onclick = () => AT.ui.closeModal();
    }, 1400);
    U.$('#ss-again', host).onclick = () => UI.session(root, idx);
    U.$('#ss-home', host).onclick = () => { AT.voice.stop(); UI.home(root); };

    /* ---------- صفحه‌ها ---------- */
    function screen_hello() {
      return new Promise((res) => {
        host.innerHTML = `
          ${AT.ui.mascotSay('toddler', `سلامِ ${U.esc(st.name)} عزیز! امروز دربارهٔ «${U.esc(cat)}» یاد می‌گیریم!`, { size: 76 })}
          <button class="btn sky big" id="sh-go" style="width:100%">سلام مینا! 👋</button>`;
        U.$('#sh-go', host).onclick = () => { U.sfx.pop(); res(); };
      });
    }
    function screen_words(list, catName) {
      return new Promise((res) => {
        host.innerHTML = `
          <h3 class="section-title">واژه‌های امروز — ${U.esc(catName)}</h3>
          <p class="dim" style="text-align:center;font-size:.85em">روی هر تصویر بزن تا مینا بگوید!</p>
          <div class="toddler-words" id="sw-list"></div>
          <button class="btn mint big" id="sw-next" style="width:100%">یاد گرفتم! ▶️</button>`;
        const listEl = U.$('#sw-list', host);
        list.forEach((w) => {
          const b = U.el(`<button class="toddler-wordcard pop-in">
            <span class="tw-emoji">${w[1]}</span>
            <b class="tw-word">${U.esc(w[0])}</b>
            <small class="dim">${U.esc(w[2])}</small>
          </button>`);
          b.onclick = () => {
            U.sfx.pop();
            say(w[0] + '! ' + w[2].replace(/-/g, ' ') + '! ' + w[3], {});
            b.classList.add('bounce-in');
            setTimeout(() => b.classList.remove('bounce-in'), 600);
            const ts = ST();
            ts.words[w[0]] = (ts.words[w[0]] || 0) + 1;
            ts.wordLog.push({ w: w[0], t: Date.now() });
            if (ts.wordLog.length > 400) ts.wordLog = ts.wordLog.slice(-300);
            U.save();
          };
          listEl.appendChild(b);
        });
        say('این واژه‌ها را ببین! روی هر تصویر بزن تا با هم بگوییم!', {});
        U.$('#sw-next', host).onclick = () => { U.sfx.coin(); res(); };
      });
    }
    function screen_story(story2) {
      return new Promise((res) => {
        let i = 0;
        host.innerHTML = `
          <h3 class="section-title">📖 ${U.esc(story2.t)}</h3>
          <div class="card glass-strong" id="ts-card" style="min-height:190px;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:8px;cursor:pointer">
            <span id="ts-emoji" style="font-size:4em" class="floaty"></span>
            <b id="ts-line" style="font-size:1.25em;text-align:center;line-height:2"></b>
            <small class="dim">برای ادامه لمس کن 👆</small>
          </div>`;
        const card = U.$('#ts-card', host);
        function show() {
          if (i >= story2.lines.length) return end();
          U.$('#ts-emoji', host).textContent = story2.emoji;
          U.$('#ts-line', host).textContent = story2.lines[i];
          say(story2.lines[i], { rate: .8 });
          i++;
        }
        card.onclick = () => { U.sfx.tap(); show(); };
        say('حالا قصهٔ ' + story2.t, { rate: .85 }).then(show, show);
        function end() {
          U.sfx.correct();
          say('قصه تمام شد! چه قشنگ!', {});
          host.appendChild(U.el(`<button class="btn mint big" id="ts-next" style="width:100%;margin-top:8px">قصه تموم شد! ▶️</button>`));
          U.$('#ts-next', host).onclick = () => res();
        }
      });
    }
    function screen_song(song2) {
      return new Promise((res) => {
        let i = 0;
        host.innerHTML = `
          <h3 class="section-title">🎵 ${U.esc(song2.t)}</h3>
          <div class="card glass-strong" id="sg-card" style="min-height:170px;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:6px">
            <span style="font-size:2.8em" class="floaty">🎶</span>
            <b id="sg-line" style="font-size:1.2em;text-align:center;line-height:2"></b>
            <small class="dim">با هم بخوانیم!</small>
          </div>
          <div style="display:flex;gap:8px;margin-top:8px">
            <button class="btn sky big" id="sg-next" style="flex:1">بعدی 🎵</button>
            <button class="btn ghost big" id="sg-done" style="flex:1">خودمون خوندیم! ▶️</button>
          </div>`;
        function sing() {
          if (i >= song2.lines.length) i = 0;
          U.$('#sg-line', host).textContent = song2.lines[i];
          U.sfx.note(i % 5);
          say(song2.lines[i], { rate: .78, pitch: 1.25 });
          i++;
        }
        say('حالا آهنگ می‌خوانیم! ' + song2.t, { rate: .85 }).then(sing, sing);
        U.$('#sg-next', host).onclick = () => { U.sfx.pop(); sing(); };
        U.$('#sg-done', host).onclick = () => { U.sfx.coin(); res(); };
      });
    }
  };

  /* ================= بازی‌های خردسال ================= */
  function playGameInner(host, game, words) {
    return new Promise((res) => {
      const wrap = U.el('<div id="tg-wrap"></div>');
      host.appendChild(wrap);
      runGame(wrap, game, words, () => { wrap.remove(); res(); });
    });
  }
  UI.playGame = function (root, game, words) {
    if (!game) game = U.pick(['find', 'sound', 'pick', 'body', 'count', 'act']);
    if (!words) {
      const cats = Object.keys(T().words);
      words = T().words[U.pick(cats)];
    }
    root.innerHTML = `
      <header class="topbar">
        <button class="btn round ghost" id="tg-back" aria-label="بازگشت">⟶</button>
        <div style="flex:1;text-align:center"><b>🎈 بازی با مینا</b></div>
        <span style="width:56px"></span>
      </header>
      ${AT.ui.mascotSay('toddler', 'بازی! آماده‌ای؟', {})}
      <div id="tg-host"></div>`;
    U.$('#tg-back', root).onclick = () => { AT.voice.stop(); UI.home(root); };
    runGame(U.$('#tg-host', root), game, words, () => UI.playGame(root, null, null));
  };
  function runGame(host, game, words, done) {
    const rounds = 3;
    let round = 0, hits = 0;
    next();
    function next() {
      if (round >= rounds) {
        U.sfx.bigwin();
        say('آفرین! تو قهرمانِ بازی هستی!', {});
        host.appendChild(U.el(`<div class="card glass-strong pop-in" style="text-align:center">
          <div style="font-size:2.6em">🎉</div><b>آفرین! ${U.fa(hits)} تا درست!</b>
          <button class="btn mint big" id="tg-again" style="width:100%;margin-top:8px">دوباره بازی کنم! 🔁</button>
        </div>`));
        U.$('#tg-again', host).onclick = () => done();
        return;
      }
      round++;
      const r = U.rng((Date.now() & 0xffff) + round * 77);
      if (game === 'find') find(host, words, r, cb);
      else if (game === 'sound') sound(host, r, cb);
      else if (game === 'pick') pick(host, words, r, cb);
      else if (game === 'body') body(host, r, cb);
      else if (game === 'count') count(host, words, r, cb);
      else act(host, r, cb);
      function cb(ok) { if (ok) hits++; setTimeout(next, 700); }
    }
  }
  /* پیدا کن */
  function find(host, words, r, cb) {
    const opts = U.shuffled(words, (r() * 1e4) | 0).slice(0, 3);
    const target = opts[0];
    host.innerHTML = `
      <div class="toddler-q">👀 <b>${target[1]}</b> کجاست؟ لمسش کن!</div>
      <div class="toddler-opts" id="fd-opts"></div>`;
    say(target[0] + ' کجاست؟', {});
    const el = U.$('#fd-opts', host);
    opts.forEach((w) => {
      const b = U.el(`<button class="toddler-bigopt"><span style="font-size:2.6em">${w[1]}</span></button>`);
      b.onclick = () => {
        if (w === target) { U.sfx.correct(); say('آفرین! این ' + target[0] + ' است!', {}); markWord(target); cb(true); }
        else { U.sfx.tap(); say('این ' + w[0] + ' است. ' + target[0] + ' کجاست؟', {}); b.classList.add('wiggle'); setTimeout(() => b.classList.remove('wiggle'), 600); }
      };
      el.appendChild(b);
    });
  }
  /* صدای حیوان */
  function sound(host, r, cb) {
    const opts = U.shuffled(SOUNDS, (r() * 1e4) | 0).slice(0, 3);
    const target = opts[0];
    host.innerHTML = `
      <div class="toddler-q">👂 این صدای کیست؟ «${target[2]}»</div>
      <div class="toddler-opts" id="sd-opts"></div>`;
    say('این صدای کیست؟ ' + target[2], { rate: .75 });
    const el = U.$('#sd-opts', host);
    opts.forEach((w) => {
      const b = U.el(`<button class="toddler-bigopt"><span style="font-size:2.6em">${w[0]}</span></button>`);
      b.onclick = () => {
        if (w === target) { U.sfx.correct(); say('آفرین! صدای ' + target[1] + '!', {}); cb(true); }
        else { U.sfx.tap(); say('این ' + w[1] + ' است. گوش کن: ' + target[2], {}); b.classList.add('wiggle'); setTimeout(() => b.classList.remove('wiggle'), 600); }
      };
      el.appendChild(b);
    });
  }
  /* کدام واژه */
  function pick(host, words, r, cb) {
    const opts = U.shuffled(words, (r() * 1e4) | 0).slice(0, 3);
    const target = opts[0];
    host.innerHTML = `
      <div class="toddler-q">🔤 کدام «${target[1]}» است؟</div>
      <div class="toddler-opts" id="pk-opts"></div>`;
    say('کدام ' + target[0] + ' است؟', {});
    const el = U.$('#pk-opts', host);
    opts.forEach((w) => {
      const b = U.el(`<button class="toddler-bigopt"><b style="font-size:1.5em">${U.esc(w[0])}</b></button>`);
      b.onclick = () => {
        if (w === target) { U.sfx.correct(); say('درسته! ' + target[0] + '!', {}); markWord(target); cb(true); }
        else { U.sfx.tap(); say('این ' + w[0] + ' است.', {}); b.classList.add('wiggle'); setTimeout(() => b.classList.remove('wiggle'), 600); }
      };
      el.appendChild(b);
    });
  }
  /* اعضای بدن */
  function body(host, r, cb) {
    const opts = U.shuffled(BODY, (r() * 1e4) | 0).slice(0, 3);
    const target = opts[0];
    host.innerHTML = `
      <div class="toddler-q">🖐️ <b>${target[0]}</b> کجاست؟ نشان بده!</div>
      <div class="toddler-opts" id="bd-opts"></div>`;
    say(target[1] + ' کجاست؟ نشان بده!', {});
    const el = U.$('#bd-opts', host);
    opts.forEach((w) => {
      const b = U.el(`<button class="toddler-bigopt"><span style="font-size:2.6em">${w[0]}</span></button>`);
      b.onclick = () => {
        if (w === target) { U.sfx.correct(); say('آفرین! این ' + target[1] + ' توست!', {}); cb(true); }
        else { U.sfx.tap(); say('این ' + w[1] + ' است. ' + target[1] + ' کجاست؟', {}); b.classList.add('wiggle'); setTimeout(() => b.classList.remove('wiggle'), 600); }
      };
      el.appendChild(b);
    });
  }
  /* شمردن تا ۳ */
  function count(host, words, r, cb) {
    const n = 1 + ((r() * 3) | 0);
    const item = words[(r() * words.length) | 0][1];
    const numWords = ['', 'یک', 'دو', 'سه'];
    host.innerHTML = `
      <div class="toddler-q">🔢 چند تا <b>${item}</b> هست؟</div>
      <div class="card glass-strong" style="display:flex;justify-content:center;flex-wrap:wrap;gap:4px;padding:18px;margin:8px 0;font-size:2.4em">${item.repeat(n)}</div>
      <div class="toddler-opts" id="ct-opts"></div>`;
    say('چند تا ' + item + ' هست؟ با هم بشماریم!', {});
    const el = U.$('#ct-opts', host);
    U.shuffled([1, 2, 3], (r() * 1e4) | 0).forEach((k) => {
      const b = U.el(`<button class="toddler-bigopt"><b style="font-size:1.8em">${U.fa(k)}</b><small>${numWords[k]}</small></button>`);
      b.onclick = () => {
        if (k === n) { U.sfx.correct(); say('آفرین! ' + numWords[n] + ' تا بود!', {}); cb(true); }
        else { U.sfx.tap(); say('بیا با هم بشماریم: ' + Array.from({ length: n }, (_, i) => numWords[i + 1]).join('، ') + '!', {}); b.classList.add('wiggle'); setTimeout(() => b.classList.remove('wiggle'), 600); }
      };
      el.appendChild(b);
    });
  }
  /* قلدری و حرکت */
  function act(host, r, cb) {
    const a = ACTS[(r() * ACTS.length) | 0];
    host.innerHTML = `
      <div class="toddler-q">🎭 ${U.esc(a[1])}</div>
      <div class="card glass-strong" style="text-align:center;padding:22px;margin:8px 0">
        <span style="font-size:4em" class="floaty">${a[0]}</span>
      </div>
      <button class="btn mint big" id="ac-done" style="width:100%">انجامش دادم! ✅</button>`;
    say(a[1], { rate: .8 });
    U.$('#ac-done', host).onclick = () => { U.sfx.correct(); say('آفرین! چه حرکتِ قشنگی!', {}); cb(true); };
  }
  function markWord(w) {
    const st = ST();
    st.saidWords[w[0]] = (st.saidWords[w[0]] || 0) + 1;
    U.save();
  }

  /* ================= واژه‌ها ================= */
  UI.words = function (root) {
    const cats = Object.keys(T().words);
    root.innerHTML = `
      <header class="topbar">
        <button class="btn round ghost" id="tw-back" aria-label="بازگشت">⟶</button>
        <div style="flex:1;text-align:center"><b>🔤 واژه‌ها</b><br><small class="dim">لمس کن، بشنو، تکرار کن!</small></div>
        <span style="width:56px"></span>
      </header>
      <div class="toddler-grid" id="tw-cats"></div>`;
    U.$('#tw-back', root).onclick = () => UI.home(root);
    const grid = U.$('#tw-cats', root);
    cats.forEach((c) => {
      const emoji = T().words[c][0][1];
      const b = U.el(`<button class="card tight" style="cursor:pointer;text-align:center;padding:18px 8px">
        <span style="font-size:2em">${emoji}</span><b style="display:block;font-size:.95em">${U.esc(c)}</b>
        <small class="dim">${U.fa(T().words[c].length)} واژه</small>
      </button>`);
      b.onclick = () => UI.wordList(root, c);
      grid.appendChild(b);
    });
  };
  UI.wordList = function (root, cat) {
    const list = T().words[cat];
    root.innerHTML = `
      <header class="topbar">
        <button class="btn round ghost" id="wl-back" aria-label="بازگشت">⟶</button>
        <div style="flex:1;text-align:center"><b>🔤 ${U.esc(cat)}</b></div>
        <span style="width:56px"></span>
      </header>
      <div class="toddler-words" id="wl-list"></div>`;
    U.$('#wl-back', root).onclick = () => UI.words(root);
    const grid = U.$('#wl-list', root);
    list.forEach((w) => {
      const st = ST();
      const seen = st.words[w[0]] || 0;
      const b = U.el(`<button class="toddler-wordcard ${seen ? 'seen' : ''}">
        <span class="tw-emoji">${w[1]}</span>
        <b class="tw-word">${U.esc(w[0])}</b>
        <small class="dim">${U.esc(w[2])}</small>
        ${seen ? `<span class="tw-badge">${'⭐'.repeat(Math.min(3, seen))}</span>` : ''}
      </button>`);
      b.onclick = () => {
        U.sfx.pop();
        say(w[0] + '! ' + w[3], {});
        b.classList.add('bounce-in');
        setTimeout(() => b.classList.remove('bounce-in'), 600);
        const ts = ST();
        ts.words[w[0]] = (ts.words[w[0]] || 0) + 1;
        ts.wordLog.push({ w: w[0], t: Date.now() });
        if (ts.wordLog.length > 400) ts.wordLog = ts.wordLog.slice(-300);
        U.save();
      };
      grid.appendChild(b);
    });
  };

  /* ================= آهنگ‌ها ================= */
  UI.songs = function (root) {
    root.innerHTML = `
      <header class="topbar">
        <button class="btn round ghost" id="so-back" aria-label="بازگشت">⟶</button>
        <div style="flex:1;text-align:center"><b>🎵 آهنگ‌ها</b><br><small class="dim">با حرکت بخوان!</small></div>
        <span style="width:56px"></span>
      </header>
      <div id="so-list" style="display:flex;flex-direction:column;gap:8px"></div>`;
    U.$('#so-back', root).onclick = () => UI.home(root);
    T().songs.forEach((s, i) => {
      const b = U.el(`<button class="card glass-strong" style="cursor:pointer;width:100%;text-align:right">
        <div style="display:flex;align-items:center;gap:12px">
          <span style="font-size:2em" class="floaty">🎶</span>
          <div style="flex:1"><b>${U.esc(s.t)}</b><br><small class="dim">${U.esc(s.lines[0])}</small></div>
          <span>▶️</span>
        </div>
      </button>`);
      b.onclick = () => UI.playSong(root, i);
      U.$('#so-list', root).appendChild(b);
    });
  };
  UI.playSong = function (root, i) {
    const s = T().songs[i];
    let li = 0;
    root.innerHTML = `
      <header class="topbar">
        <button class="btn round ghost" id="ps-back" aria-label="بازگشت">⟶</button>
        <div style="flex:1;text-align:center"><b>🎵 ${U.esc(s.t)}</b></div>
        <span style="width:56px"></span>
      </header>
      <div class="card glass-strong" id="ps-card" style="min-height:200px;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:8px">
        <span style="font-size:2.8em" class="floaty">🎶</span>
        <b id="ps-line" style="font-size:1.3em;text-align:center;line-height:2.1"></b>
      </div>
      <div style="display:flex;gap:8px;margin-top:8px">
        <button class="btn sky big" id="ps-next" style="flex:1">این خط را بگو! 🎤</button>
        <button class="btn ghost big" id="ps-done" style="flex:1">بازگشت ⟶</button>
      </div>`;
    U.$('#ps-back', root).onclick = U.$('#ps-done', root).onclick = () => { AT.voice.stop(); UI.songs(root); };
    function sing() {
      if (li >= s.lines.length) li = 0;
      U.$('#ps-line', root).textContent = s.lines[li];
      U.sfx.note(li % 5);
      say(s.lines[li], { rate: .78, pitch: 1.25 });
      li++;
    }
    say('آهنگِ ' + s.t + '! با هم می‌خوانیم!', { rate: .85 }).then(sing, sing);
    U.$('#ps-next', root).onclick = () => { U.sfx.pop(); sing(); };
  };

  /* ================= قصه‌ها ================= */
  UI.stories = function (root) {
    root.innerHTML = `
      <header class="topbar">
        <button class="btn round ghost" id="st-back" aria-label="بازگشت">⟶</button>
        <div style="flex:1;text-align:center"><b>📖 قصه‌ها</b><br><small class="dim">کوتاه و شاد</small></div>
        <span style="width:56px"></span>
      </header>
      <div class="toddler-grid" id="st-list"></div>`;
    U.$('#st-back', root).onclick = () => UI.home(root);
    T().stories.forEach((s, i) => {
      const b = U.el(`<button class="card tight" style="cursor:pointer;text-align:center;padding:16px 8px">
        <span style="font-size:2em">${s.emoji}</span><b style="display:block;font-size:.9em">${U.esc(s.t)}</b>
        <small class="dim">${U.fa(s.lines.length)} جمله</small>
      </button>`);
      b.onclick = () => UI.playStory(root, i);
      U.$('#st-list', root).appendChild(b);
    });
  };
  UI.playStory = function (root, i) {
    const s = T().stories[i];
    let li = 0;
    root.innerHTML = `
      <header class="topbar">
        <button class="btn round ghost" id="pb-back" aria-label="بازگشت">⟶</button>
        <div style="flex:1;text-align:center"><b>📖 ${U.esc(s.t)}</b></div>
        <span style="width:56px"></span>
      </header>
      <div class="card glass-strong" id="pb-card" style="min-height:210px;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:8px;cursor:pointer">
        <span id="pb-emoji" style="font-size:4em" class="floaty">${s.emoji}</span>
        <b id="pb-line" style="font-size:1.25em;text-align:center;line-height:2.1"></b>
        <small class="dim">برای ادامه لمس کن 👆</small>
      </div>
      <div style="display:flex;gap:8px;margin-top:8px">
        <button class="btn sky big" id="pb-replay" style="flex:1">دوباره بگو 🔊</button>
        <button class="btn ghost big" id="pb-done" style="flex:1">قصهٔ بعدی ⟶</button>
      </div>`;
    U.$('#pb-back', root).onclick = () => { AT.voice.stop(); UI.stories(root); };
    function show() {
      if (li >= s.lines.length) {
        U.sfx.correct();
        say('قصه تمام شد! چه قشنگ!', {});
        U.$('#pb-line', root).textContent = '⭐ قصه تمام شد!';
        return;
      }
      U.$('#pb-line', root).textContent = s.lines[li];
      say(s.lines[li], { rate: .8 });
      li++;
    }
    U.$('#pb-card', root).onclick = () => { U.sfx.tap(); show(); };
    U.$('#pb-replay', root).onclick = () => { li = Math.max(0, li - 1); show(); };
    U.$('#pb-done', root).onclick = () => UI.playStory(root, (i + 1) % T().stories.length);
    say('قصهٔ ' + s.t, { rate: .85 }).then(show, show);
  };
})();

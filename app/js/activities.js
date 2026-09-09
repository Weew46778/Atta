/* آتا — موتورهای فعالیت: ۳۷ نوع بازیِ تعاملی
   هر موتور: async (host, spec, ctx) => {hit, tries}
   ctx: {t سختی، timer، world، level، seed}
   spec: {type, item, idx, seed, hint?} — توسط composer در app.js ساخته می‌شود */
(function () {
  'use strict';
  const AT = (window.AT = window.AT || {});
  const U = AT.util;
  const A = (AT.activities = {});

  /* ================= کمکی‌های مشترک ================= */
  const LETTERS = ['الف', 'ب', 'ج', 'د'];
  const PRAISES = ['آفرین قهرمان!', 'چه هوشیار!', 'عالی بود!', 'درخشیدی!', 'ذهنت مثل برق!', 'ایول!', 'چه دقیق!', 'خیلی خوب بود!'];
  const NUDGE = ['نزدیک بود! یک بار دیگر فکر کن.', 'همین‌طور ادامه بده!', 'راهِ دیگری امتحان کن!'];
  const isEmoji = (s) => /^[\u{1F000}-\u{1FAFF}\u{2600}-\u{27BF}\u{2B00}-\u{2BFF}\u{1F1E6}-\u{1F1FF}]/u.test(String(s));

  function pickPraise() { return U.pick(PRAISES); }
  function narrate(text, opts) { return AT.voice.speak(text, opts).catch(() => {}); }

  function head(spec, ctx) {
    const meta = AT.data.TYPE_META[spec.type] || { title: 'فعالیت', emoji: '✨', coins: 10 };
    return `<div class="activity-head">
      <div class="activity-badge">${meta.emoji}</div>
      <div><div class="activity-kind">${U.esc(meta.title)}${ctx && ctx.timer ? ' • سرعت بالا!' : ''}</div>
      <div style="font-weight:700;font-size:.85em;color:var(--ink-faint)">جایزه: 🪙 ${U.fa(meta.coins)}</div></div>
    </div>`;
  }

  /* گزینه‌های استاندارد: تلاش دوباره + بازخورد + تایمرِ باس */
  function choices(host, { question, options, answer, explain, emoji, timer, rate }) {
    return new Promise((resolve) => {
      let tries = 0, hit = false, timerInt = null, timeLeft = timer || 0;
      const emojiMode = options.every(isEmoji);
      host.innerHTML = head(host._spec || { type: 'q' }, host._ctx || {}) + `
        ${emoji ? `<div class="pop-in" style="text-align:center;font-size:2.8em;margin:2px 0">${emoji}</div>` : ''}
        <div class="q-text">${U.esc(question)}</div>
        <div style="display:flex;gap:8px;flex-wrap:wrap;margin:6px 0 2px">
          <button class="read-btn" id="q-read">🔊 بخون برام</button>
          ${timer ? `<span class="timer-chip" id="q-timer">⏳ ${U.fa(timeLeft)}</span>` : ''}
        </div>
        <div class="opts ${options.length === 3 ? 'c3' : 'c2'}" id="q-opts"></div>
        <div id="q-feedback"></div>`;
      const optsEl = U.$('#q-opts', host);
      options.forEach((op, i) => {
        optsEl.appendChild(U.el(
          emojiMode
            ? `<button class="opt emoji-opt" data-i="${i}"><span class="opt-emoji">${op}</span></button>`
            : `<button class="opt" data-i="${i}"><span class="opt-letter">${LETTERS[i]}</span><span>${U.esc(op)}</span></button>`
        ));
      });
      narrate(question, { rate });
      U.$('#q-read', host).onclick = () => narrate(question, { rate });
      if (timer) {
        const chip = U.$('#q-timer', host);
        timerInt = setInterval(() => {
          timeLeft--;
          chip.textContent = `⏳ ${U.fa(Math.max(0, timeLeft))}`;
          chip.classList.toggle('urgent', timeLeft <= 5);
          if (timeLeft <= 5 && timeLeft > 0) U.sfx.tick();
          if (timeLeft <= 0) { clearInterval(timerInt); finish(false, true); }
        }, 1000);
      }
      optsEl.onclick = (e) => {
        const b = e.target.closest('.opt'); if (!b) return;
        const i = +b.dataset.i;
        if (i === answer) finish(true);
        else {
          tries++; U.sfx.wrong(); U.vibrate(80);
          b.classList.add('wrong');
          setTimeout(() => b.classList.add('dim'), 450);
          narrate(NUDGE[Math.min(tries - 1, 2)], { rate: .95 });
          if (tries >= 2) finish(false);
        }
      };
      function finish(ok, expired) {
        clearInterval(timerInt);
        U.$$('.opt', optsEl).forEach((b, i) => { if (i === answer) b.classList.add('correct'); else b.classList.add('dim'); });
        const good = ok && !expired;
        U.sfx[good ? 'correct' : 'unlock']();
        const fb = U.$('#q-feedback', host);
        fb.innerHTML = `<div class="feedback ${good ? 'good' : 'bad'}">
          <span class="f-emoji">${good ? '🎉' : expired ? '⏰' : '💡'}</span>
          <div><b>${good ? pickPraise() : expired ? 'وقت تمام شد! جوابِ درست روشن شد.' : 'نزدیک بود! جوابِ درست این بود.'}</b><br>${U.esc(explain || '')}</div></div>
          <button class="btn small" id="q-next" style="margin-top:8px">ادامه ⟵</button>`;
        if (good) narrate(pickPraise() + ' ' + (explain || ''), { rate: 1.02 });
        else narrate(expired ? 'وقت تمام شد.' : (explain || 'دفعه بعد حتماً می‌شود!'), { rate: .95 });
        U.$('#q-next', fb).onclick = () => resolve({ hit: good, tries: Math.max(1, tries) });
      }
    });
  }
  /* هاست را با مشخصاتِ فعالیت مجهز کن تا head() درست کار کند */
  function gear(host, spec, ctx) { host._spec = spec; host._ctx = ctx || {}; return host; }

  /* بانک احساس‌ها (اختصاصیِ موتور) */
  const EMOTIONS = [
    ['در مدرسه نقاشی‌ات را روی دیوار نصب کردند و همه تماشا کردند.', 'افتخار', ['خوشحالی', 'افتخار', 'خجالت'], 'وقتی از کارِ خودمان حسِ خوب و بزرگی می‌کنیم، احساسِ افتخار داریم.'],
    ['دوستت قول داد ساعت ۵ بیاید، اما ساعت ۷ شد و نیامد.', 'ناراحتی', ['خشم', 'ناراحتی', 'شادی'], 'وقتی انتظاری برآورده نمی‌شود، دلمان می‌گیرد؛ این ناراحتی است و طبیعی است.'],
    ['برادر کوچک‌ت اسباب‌بازی‌ات را بدون اجازه برد و شکست.', 'خشم', ['خشم', 'ترس', 'تعجب'], 'وقتی چیزیِ ما آسیب می‌بیند، خشمِ سالم در ما بالا می‌آید؛ می‌توانیم با حرف زدن آن را آرام کنیم.'],
    ['شبِ تنها در اتاق، صدای عجیبی از آشپزخانه شنیدی.', 'ترس', ['ترس', 'شادی', 'غرور'], 'ترس، زنگِ خطرِ بدن است تا مراقب باشیم؛ با روشن کردن چراغ و گفت‌وگو با بزرگ‌ترها کم می‌شود.'],
    ['یک هفته منتظرِ سفرِ مادربزرگ بودی و بالاخره امروز رسید.', 'ذوق', ['غصه', 'ذوق', 'خستگی'], 'انتظارِ چیزِ خوب و رسیدنِ آن، حسِ ذوق و هیجان می‌سازد.'],
    ['مسئلهٔ سختی را با تلاشِ زیاد حل کردی.', 'غرور و رضایت', ['غرور و رضایت', 'حسادت', 'تنهایی'], 'وقتی با تلاشِ خودمان به نتیجه می‌رسیم، حسِ رضایت و غرورِ سالم داریم.'],
    ['رفیقت با بچهٔ دیگری خیلی بازی و خندید و تو کنار ماندی.', 'حسادت', ['حسادت', 'غم', 'آرامش'], 'حسادت یعنی دوست داریم آنچه دیگری دارد را داشته باشیم؛ با گفتنِ حسِمان به او، کوچک‌تر می‌شود.'],
    ['اولین بار بود بدونِ والدینت به خانهٔ خاله می‌رفتی.', 'دلهره', ['دلهره', 'شادی', 'کینه'], 'دلهره، ترسِ بامزهِ چیزِ تازه است؛ وقتی کارِ تازه شروع شود، کم‌کم جای خود را به آرامش می‌دهد.'],
    ['حيوانِ خانگی‌ات بعدِ بیماری بهتر شد.', 'آرامش و خوشحالی', ['آرامش و خوشحالی', 'خشم', 'ترس'], 'وقتی نگرانی تمام می‌شود، بدن ما آرام می‌شود و قلبمان شاد می‌شود.'],
    ['در مسابقهٔ دوی مدرسه اول شدی.', 'شادی و غرور', ['شادی و غرور', 'حسرت', 'خجالت'], 'کوشش و پیروزی، حسِ شادی و غرورِ خوشایند می‌دهد؛ می‌توانی با فریادِ «آفرین به خودم!» جشن بگیری!'],
    ['اشتباهی لیوان را انداختی و شکست، در حالی که تلاش کردی محکم بگیریش.', 'خجالت', ['خجالت', 'افتخار', 'شادی'], 'خجالت وقتی است که فکر می‌کنیم دیگران ما را قضاوت می‌کنند؛ اشتباه، بخشِ یادگیری است و می‌گذرد.'],
    ['همهٔ دوستانت در بازی گروهی با تو بودند و با هم خندیدید.', 'تعلق و شادی', ['تعلق و شادی', 'تنهایی', 'خستگی'], 'حسِ تعلق یعنی احساسِ بخشِ یک گروه بودن؛ این حس با همبستگی و خنده قوی‌تر می‌شود.'],
  ];

  /* ================= کوئیزهای بانکی ================= */
  function bankOf(type) {
    switch (type) {
      case 'quiziran': return AT.quiz.iran;
      case 'quizscience': return AT.quiz.science.concat(AT.quiz.nature);
      case 'quizethics': return AT.quiz.values;
      case 'quizart': return AT.quiz.art;
      case 'quizsafe': return AT.quiz.safety.concat(AT.quiz.health);
      default: return AT.quiz.science;
    }
  }
  function bankQuiz(host, spec, ctx) {
    gear(host, spec, ctx);
    const q = spec.item; // [پرسش، [گزینه‌ها]، ایندکس، توضیح]
    let options = q[1].slice();
    if (ctx.t > .5 && options.length === 3) {
      const other = bankOf(spec.type)[(spec.idx + 7) % bankOf(spec.type).length][1];
      const extra = other.find((o) => !options.includes(o));
      if (extra) options.push(extra);
    }
    options = U.shuffled(options, spec.seed);
    return choices(host, {
      question: q[0], options, answer: options.indexOf(q[1][q[2]]), explain: q[3],
      timer: ctx.timer ? Math.max(12, 20 - Math.floor(ctx.t * 6)) : 0,
    });
  }
  A.quiziran = A.quizscience = A.quizethics = A.quizart = A.quizsafe = bankQuiz;

  /* کوئیز ادبی: کامل کردن بیت */
  A.quizlang = function (host, spec, ctx) {
    gear(host, spec, ctx);
    const p = spec.item; // [مصرع۱، مصرع۲، سراینده]
    const decoys = U.shuffled(AT.word.poems.filter((x) => x[1] !== p[1]), spec.seed).slice(0, 2).map((x) => x[1]);
    const options = U.shuffled([p[1], ...decoys], spec.seed + 3);
    return choices(host, {
      question: `مصرعِ اول: «${p[0]}» — از سرودهٔ ${p[2]}. مصرعِ دوم کدام است؟`,
      options, answer: options.indexOf(p[1]),
      explain: `«${p[0]} / ${p[1]}» — سرودهٔ ${p[2]}.`, timer: ctx.timer ? 16 : 0,
    });
  };

  /* درست/نادرستِ سریع از گنج‌ها */
  A.quizsmart = function (host, spec, ctx) {
    gear(host, spec, ctx);
    const fact = spec.item;
    const flip = spec.seed % 2 === 0;
    const statement = flip ? fact : twistFact(fact, spec.seed);
    return choices(host, {
      question: statement, options: ['درست است ✔️', 'نادرست است ❌'], answer: flip ? 0 : 1,
      explain: 'حقیقتِ درست: ' + fact, emoji: '🧠', timer: ctx.timer ? 12 : 0,
    });
  };
  function twistFact(fact, seed) {
    const swaps = [['زیاد', 'کم'], ['بیشتر', 'کمتر'], ['بزرگ‌ترین', 'کوچک‌ترین'], ['گرم', 'سرد'], ['روشن', 'تاریک'], ['بالا', 'پایین'], ['قوی', 'ضعیف'], ['سریع', 'کند'], ['همیشه', 'هرگز']];
    let changed = false;
    const out = fact.split(' ').map((w) => {
      for (const [a, b] of swaps) {
        if (seed % 3 !== 2 && w.includes(a)) { changed = true; return w.replace(a, b); }
        if (seed % 3 === 2 && w.includes(b)) { changed = true; return w.replace(b, a); }
      }
      return w;
    });
    if (!changed) out.unshift('نه،');
    return out.join(' ');
  }

  /* جای خالی عدد */
  A.quizmath = function (host, spec, ctx) {
    gear(host, spec, ctx);
    const r = U.rng(spec.seed), t = ctx.t;
    let a, b, ans, q, explain;
    if (t < .4) { a = 2 + (r() * 8 | 0); b = 2 + (r() * 8 | 0); ans = a + b; q = `${U.fa(a)} + ؟ = ${U.fa(a + b)}`; explain = `؟ = ${U.fa(ans)} − ${U.fa(a)} = ${U.fa(b)}؛ از جواب، عددِ دیگر را کم کن.`; }
    else if (t < .7) { a = 8 + (r() * 12 | 0); b = 2 + (r() * (a - 2) | 0); ans = a - b; q = `${U.fa(a)} − ؟ = ${U.fa(b)}`; explain = `؟ = ${U.fa(a)} − ${U.fa(b)} = ${U.fa(ans)}؛ عقب‌شماری کن!`; }
    else { a = 2 + (r() * 7 | 0); b = 2 + (r() * 6 | 0); ans = b; q = `${U.fa(a)} × ؟ = ${U.fa(a * b)}`; explain = `؟ = ${U.fa(a * b)} ÷ ${U.fa(a)} = ${U.fa(b)}؛ ضرب را برعکس با تقسیم حل کن.`; }
    const opts = U.shuffled([ans, ans + 1 + (r() * 2 | 0), Math.max(1, ans - 1 - (r() * 2 | 0)), ans + 3 + (r() * 3 | 0)], spec.seed).map((x) => U.fa(x));
    return choices(host, {
      question: q + '  «؟» کدام عدد است؟', options: opts, answer: opts.indexOf(U.fa(ans)),
      explain, emoji: '🧮', timer: ctx.timer ? 15 : 0,
    });
  };

  /* ================= قصهٔ اقلیم ================= */
  A.story = function (host, spec, ctx) {
    gear(host, spec, ctx);
    const s = spec.item; // {t,e,cast,w,p,q,lesson}
    return new Promise((resolve) => {
      let qi = 0, hits = 0, tries = 0, i = 0, cancelled = false;
      host.innerHTML = head(spec, ctx) + `
        <div style="display:flex;align-items:center;gap:10px;margin-bottom:4px">
          <span style="font-size:2.2em">${s.e}</span>
          <div><b style="font-size:1.1em">قصه: ${U.esc(s.t)}</b><br><small class="dim">با صدای راوی گوش کن؛ متن هم نشان داده می‌شود</small></div>
        </div>
        <div class="story-stage">${s.cast.map((c) => `<span class="actor">${c}</span>`).join('')}</div>
        <div id="st-body"></div>
        <div style="margin-top:8px"><button class="btn small ghost" id="st-mute">بی‌صدا بخونم 🔇</button></div>`;
      const body = U.$('#st-body', host);
      async function nextPara() {
        if (i >= s.p.length) return askQ();
        body.querySelectorAll('.story-p').forEach((p, j) => { p.classList.toggle('now', j === i); });
        body.appendChild(U.el(`<p class="story-p now">${U.esc(s.p[i])}</p>`));
        if (!cancelled) await narrate(s.p[i], { rate: .98 });
        i++;
        setTimeout(nextPara, 250);
      }
      U.$('#st-mute', host).onclick = () => { cancelled = true; AT.voice.stop(); };
      narrate('قصهٔ ' + s.t, { rate: .95 }).then(nextPara, nextPara);
      function askQ() {
        if (qi >= s.q.length) {
          host.appendChild(U.el(`<div class="feedback good"><span class="f-emoji">🌱</span><div><b>درسِ این قصه:</b><br>${U.esc(s.lesson)}</div></div>`));
          narrate('درس این قصه: ' + s.lesson, { rate: .95 });
          setTimeout(() => resolve({ hit: hits > 0, tries: Math.max(1, tries) }), Math.min(9000, 2500 + s.lesson.length * 60));
          return;
        }
        const q = s.q[qi];
        const qbox = U.el('<div class="slide-up" id="st-q"></div>');
        host.appendChild(qbox);
        choices(qbox, { question: q[0], options: q[1], answer: q[2], explain: q[3], rate: .98 }).then((r2) => {
          hits += r2.hit ? 1 : 0; tries += r2.tries; qi++;
          qbox.remove();
          askQ();
        });
      }
    });
  };

  /* ================= انیمیشنِ کوچک ================= */
  A.toon = function (host, spec, ctx) {
    gear(host, spec, ctx);
    const s = spec.item; // {t,emoji,w,scenes,q,lesson}
    return new Promise((resolve) => {
      let i = 0;
      host.innerHTML = head(spec, ctx) + `
        <div style="display:flex;align-items:center;gap:10px">
          <span style="font-size:2em">🎬</span>
          <div><b>انیمیشنِ کوچک: ${U.esc(s.t)}</b><br><small class="dim">صحنه‌ها را تماشا کن و گوش بده</small></div>
        </div>
        <div class="card glass-strong" style="margin-top:8px;padding:14px;background:linear-gradient(180deg,rgba(76,201,240,.14),rgba(199,125,255,.1))">
          <div class="story-stage" id="tn-stage" style="min-height:86px;font-size:2.6em"></div>
          <p id="tn-text" style="min-height:3.8em;margin:6px 0 0;text-align:center;font-weight:700;line-height:1.9"></p>
        </div>
        <div style="display:flex;gap:8px;justify-content:center;margin-top:8px">
          <button class="btn small ghost" id="tn-next">صحنهٔ بعد ⏭</button>
        </div>`;
      const canSTT = AT.voice.sttAvailable();
      async function playScene() {
        if (i >= s.scenes.length) return reflect();
        const sc = s.scenes[i];
        U.$('#tn-stage', host).innerHTML = sc.a.map((a, k) => `<span class="actor" style="animation-delay:${k * .3}s">${a}</span>`).join('');
        U.$('#tn-text', host).textContent = sc.text;
        U.sfx.whoosh();
        await narrate(sc.text, { rate: .98 });
        i++;
        if (i < s.scenes.length) setTimeout(playScene, 600); else setTimeout(reflect, 400);
      }
      U.$('#tn-next', host).onclick = () => { AT.voice.stop(); i++; i >= s.scenes.length ? reflect() : playScene(); };
      narrate('انیمیشنِ کوچک: ' + s.t, { rate: .95 }).then(playScene, playScene);
      /* پرسشِ گفت‌وگو: با صدای خودت جواب بده، بعد درس را بشنو */
      function reflect() {
        const btn = U.$('#tn-next', host);
        if (btn) btn.remove();
        host.appendChild(U.el(`<div class="slide-up" id="tn-reflect">
          <div class="q-text">🎤 حالا نوبتِ توست: <b>${U.esc(s.q)}</b></div>
          <div class="mic-panel">
            ${canSTT ? `<button class="mic-btn" id="tn-mic" aria-label="ضبط">🎤</button><p class="dim" style="font-size:.85em">دکمه را بزن و با صدای بلند جواب بده!</p>` : `<p class="dim" style="font-size:.9em">جواب را با صدای بلند بگو، بعد دکمه را بزن!</p>`}
            <div class="mic-fallback"><button class="btn mint small" id="tn-said">جواب دادم! ✅</button></div>
          </div>
        </div>`));
        narrate('حالا نوبت توست: ' + s.q + ' با صدای بلند جواب بده!', { rate: .95 });
        U.$('#tn-said', host).onclick = finish;
        if (canSTT) {
          U.$('#tn-mic', host).onclick = async () => {
            const mic = U.$('#tn-mic', host);
            mic.classList.add('listening'); U.sfx.pop();
            narrate('بگو!', { rate: .9 }).then(async () => {
              const res = await AT.voice.listen({ maxWait: 9000 });
              mic.classList.remove('listening');
              finish(res.ok && res.text ? 'شنیدمت! «' + res.text.slice(0, 60) + '»' : null);
            });
          };
        }
        function finish(heard) {
          U.sfx.correct();
          host.appendChild(U.el(`<div class="feedback good"><span class="f-emoji">🌱</span><div><b>${heard ? 'جوابِ قشنگی بود!' : 'چه خوب که فکر کردی!'}</b><br><b>پیامِ انیمیشن:</b> ${U.esc(s.lesson)}</div></div>`));
          narrate((heard || 'چه خوب که فکر کردی!') + ' پیامِ این انیمیشن: ' + s.lesson, { rate: .95 });
          setTimeout(() => resolve({ hit: true, tries: 1 }), Math.min(9000, 2600 + s.lesson.length * 60));
        }
      }
    });
  };

  /* ================= معما ================= */
  A.riddle = function (host, spec, ctx) {
    gear(host, spec, ctx);
    const rd = spec.item; // [معما، جواب، راهنما، فریب۱، فریب۲، فریب۳]
    return new Promise((resolve) => {
      let tries = 0;
      host.innerHTML = head(spec, ctx) + `
        <div class="q-text">🧩 ${U.esc(rd[0])}</div>
        <div style="display:flex;gap:8px;margin:8px 0;flex-wrap:wrap">
          <button class="read-btn" id="rd-read">🔊 بخون برام</button>
          <button class="hint-btn" id="rd-hint">💡 راهنما می‌خوام</button>
        </div>
        <div id="rd-hintbox"></div>
        <div class="opts c2" id="rd-opts"></div>
        <div id="rd-fb"></div>`;
      const options = U.shuffled([rd[1], rd[3], rd[4], rd[5]], spec.seed);
      const optsEl = U.$('#rd-opts', host);
      options.forEach((op, i) => optsEl.appendChild(U.el(`<button class="opt" data-i="${i}"><span class="opt-letter">${LETTERS[i]}</span><span>${U.esc(op)}</span></button>`)));
      narrate('معما: ' + rd[0], { rate: .92 });
      U.$('#rd-read', host).onclick = () => narrate(rd[0], { rate: .92 });
      U.$('#rd-hint', host).onclick = (e) => {
        e.preventDefault(); U.sfx.pop();
        U.$('#rd-hintbox', host).innerHTML = `<div class="feedback good" style="padding:8px 12px"><span class="f-emoji">💡</span><div>${U.esc(rd[2])}</div></div>`;
        narrate('راهنما: ' + rd[2], { rate: .92 });
        const st = U.load(); st.explorer.hints++; U.save();
        e.target.closest('button').disabled = true;
      };
      optsEl.onclick = (e) => {
        const b = e.target.closest('.opt'); if (!b) return;
        const i = +b.dataset.i;
        if (options[i] === rd[1]) {
          U.sfx.correct(); b.classList.add('correct');
          U.$$('.opt', optsEl).forEach((x, j) => { if (j !== i) x.classList.add('dim'); });
          narrate(pickPraise() + ' جواب درست: ' + rd[1], {});
          U.$('#rd-fb', host).innerHTML = `<div class="feedback good"><span class="f-emoji">🎉</span><div><b>${pickPraise()}</b><br>جواب: <b>${U.esc(rd[1])}</b></div></div><button class="btn small" id="rd-next">ادامه ⟵</button>`;
          U.$('#rd-next', host).onclick = () => resolve({ hit: tries === 0, tries: Math.max(1, tries) });
        } else {
          tries++; U.sfx.wrong(); b.classList.add('wrong');
          setTimeout(() => b.classList.add('dim'), 400);
          narrate(NUDGE[Math.min(tries - 1, 2)], {});
          if (tries >= 2) {
            U.$$('.opt', optsEl).forEach((x, j) => { if (options[j] === rd[1]) x.classList.add('correct'); else x.classList.add('dim'); });
            U.$('#rd-fb', host).innerHTML = `<div class="feedback bad"><span class="f-emoji">💡</span><div>جوابِ درست: <b>${U.esc(rd[1])}</b></div></div><button class="btn small" id="rd-next">ادامه ⟵</button>`;
            U.$('#rd-next', host).onclick = () => resolve({ hit: false, tries });
          }
        }
      };
    });
  };

  /* ================= کلمه‌سازی ================= */
  A.wordbuild = function (host, spec, ctx) {
    gear(host, spec, ctx);
    const item = spec.item; // [واژه، ایموجی، راهنما]
    const word = item[0], emoji = item[1];
    const letters = word.replace(/\u200c/g, '').split('');
    let scrambled = U.shuffled(letters, spec.seed);
    if (scrambled.join('') === letters.join('') && letters.length > 2) scrambled.push(scrambled.shift());
    return new Promise((resolve) => {
      let built = [], tries = 0;
      host.innerHTML = head(spec, ctx) + `
        <div class="floaty" style="text-align:center;font-size:3.2em;margin:2px 0">${emoji}</div>
        <div class="q-text" style="text-align:center">واژهٔ مربوط با تصویر را با حروف بساز!</div>
        ${item[2] ? `<p class="dim" style="text-align:center;font-size:.85em">راهنما: از دستهٔ «${U.esc(item[2])}»</p>` : ''}
        <div id="wb-slots" style="display:flex;gap:6px;justify-content:center;flex-wrap:wrap;margin:12px 0;direction:rtl"></div>
        <div id="wb-letters" style="display:flex;gap:8px;justify-content:center;flex-wrap:wrap;min-height:56px"></div>
        <div style="display:flex;gap:8px;justify-content:center;margin-top:10px">
          <button class="btn small ghost" id="wb-read">🔊 بشنویم</button>
          <button class="btn small ghost" id="wb-undo">↩ یکی بردار</button>
        </div>
        <div id="wb-fb"></div>`;
      const slots = U.$('#wb-slots', host), pool = U.$('#wb-letters', host);
      function render() {
        slots.innerHTML = '';
        letters.forEach((_, i) => slots.appendChild(U.el(`<div class="wb-slot ${built[i] ? 'filled' : ''}">${U.esc(built[i] || '')}</div>`)));
        const remaining = letters.slice();
        built.forEach((b) => { const k = remaining.indexOf(b); if (k > -1) remaining.splice(k, 1); });
        pool.innerHTML = '';
        remaining.forEach((ch) => pool.appendChild(U.el(`<button class="wb-tile" data-ch="${U.esc(ch)}">${U.esc(ch)}</button>`)));
      }
      render();
      narrate('حروفِ جابه‌جاشده را به ترتیب بچین تا واژه ساخته شود!', { rate: .9 });
      U.$('#wb-read', host).onclick = () => narrate('واژه: ' + word, { rate: .8 });
      U.$('#wb-undo', host).onclick = () => { if (built.length) { built.pop(); render(); U.sfx.tap(); } };
      pool.onclick = (e) => {
        const b = e.target.closest('.wb-tile'); if (!b) return;
        if (built.length >= letters.length) return;
        built.push(b.dataset.ch); U.sfx.tap(); render();
        if (built.length === letters.length) {
          setTimeout(() => {
            if (built.join('') === letters.join('')) {
              U.sfx.correct(); U.confetti(host, { count: 40, dur: 1400 });
              narrate(pickPraise() + ' درست بود: ' + word, {});
              U.$('#wb-fb', host).innerHTML = `<div class="feedback good"><span class="f-emoji">🎉</span><div><b>${pickPraise()}</b><br>واژه: <b>${U.esc(word)}</b></div></div><button class="btn small" id="wb-next">ادامه ⟵</button>`;
              U.$('#wb-next', host).onclick = () => resolve({ hit: tries === 0, tries: Math.max(1, tries) });
            } else {
              tries++; U.sfx.wrong(); U.vibrate(70);
              narrate('ترتیبِ حروف عوض شد؛ دوباره امتحان کن!', {});
              setTimeout(() => { built = []; render(); }, 800);
              if (tries >= 3) {
                built = letters.slice(); render();
                U.$('#wb-fb', host).innerHTML = `<div class="feedback bad"><span class="f-emoji">💡</span><div>این هم جواب: <b>${U.esc(word)}</b></div></div><button class="btn small" id="wb-next">ادامه ⟵</button>`;
                U.$('#wb-next', host).onclick = () => resolve({ hit: false, tries });
              }
            }
          }, 350);
        }
      };
    });
  };

  /* ================= مترادف/متضاد ================= */
  A.opposites = function (host, spec, ctx) {
    gear(host, spec, ctx);
    const isSyn = spec.sub === 'syn';
    const bank = isSyn ? AT.word.synonyms : AT.word.opposites;
    const n = ctx.t > .5 ? 4 : 3;
    const pairs = [];
    for (let i = 0; i < n; i++) pairs.push(bank[(spec.idx + i * 2) % bank.length]);
    const rights = U.shuffled(pairs.map((p) => p[1]), spec.seed + 1);
    return new Promise((resolve) => {
      let matched = 0, tries = 0, sel = null;
      host.innerHTML = head(spec, ctx) + `
        <div class="q-text" style="text-align:center">${isSyn ? 'واژه‌های هم‌معنی را جفت کن!' : 'متضادها را به هم وصل کن!'}</div>
        <p class="dim" style="text-align:center;font-size:.85em">اول یک واژه از ستونِ راست انتخاب کن، بعد جفتش را از ستونِ چپ بزن</p>
        <div style="display:grid;grid-template-columns:1fr 1fr;gap:10px;margin-top:10px">
          <div id="op-left" style="display:flex;flex-direction:column;gap:8px"></div>
          <div id="op-right" style="display:flex;flex-direction:column;gap:8px"></div>
        </div>
        <div id="op-fb"></div>`;
      const L = U.$('#op-left', host), R = U.$('#op-right', host);
      rights.forEach((w) => L.appendChild(U.el(`<button class="opt" data-w="${U.esc(w)}" style="justify-content:center">${U.esc(w)}</button>`)));
      pairs.forEach((p) => R.appendChild(U.el(`<button class="opt" data-w="${U.esc(p[0])}" style="justify-content:center">${U.esc(p[0])}</button>`)));
      narrate(isSyn ? 'واژه‌های هم‌معنی را جفت کن!' : 'متضاد یعنی برعکس؛ آن‌ها را به هم وصل کن!', {});
      R.onclick = (e) => {
        const b = e.target.closest('.opt'); if (!b || b.disabled) return;
        R.querySelectorAll('.opt').forEach((x) => x.style.borderColor = '');
        b.style.borderColor = 'var(--sun)'; sel = b; U.sfx.tap();
      };
      L.onclick = (e) => {
        const b = e.target.closest('.opt'); if (!b || !sel || b.disabled) return;
        tries++;
        const target = pairs.find((p) => p[0] === sel.dataset.w);
        if (target && b.dataset.w === target[1]) {
          U.sfx.correct();
          b.classList.add('correct'); sel.classList.add('correct');
          b.disabled = sel.disabled = true; sel = null; matched++;
          if (matched === pairs.length) {
            narrate(pickPraise() + ' همه را جفت کردی!', {});
            U.$('#op-fb', host).innerHTML = `<div class="feedback good"><span class="f-emoji">🎉</span><div><b>${pickPraise()}</b><br>${isSyn ? 'همهٔ هم‌معنی‌ها پیدا شد!' : 'همهٔ متضادها پیدا شد!'}</div></div><button class="btn small" id="op-next">ادامه ⟵</button>`;
            U.$('#op-next', host).onclick = () => resolve({ hit: tries === pairs.length, tries: Math.max(1, tries) });
          }
        } else { U.sfx.wrong(); b.classList.add('wrong'); setTimeout(() => b.classList.remove('wrong'), 500); }
      };
    });
  };

  /* ================= گوشِ شنوا ================= */
  A.listen = function (host, spec, ctx) {
    gear(host, spec, ctx);
    const L = spec.item; // [متن شامل پرسش، [گزینه‌ها]، ایندکس]
    const qSentence = (L[0].split(/[.!؟؟]\s*/).filter((x) => x.includes('؟')).pop()) || L[0].slice(-90);
    return new Promise((resolve) => {
      host.innerHTML = head(spec, ctx) + `
        <div class="q-text">👂 خوب گوش کن؛ متن نشان داده نمی‌شود!</div>
        <div style="text-align:center;margin:12px 0">
          <button class="mic-btn" id="ls-play" style="background:linear-gradient(180deg,#a5e6ff,#118ab2);box-shadow:0 6px 0 #0d5a73,0 16px 30px rgba(0,0,0,.4)" aria-label="پخش">▶️</button>
          <p class="dim" style="font-size:.85em">دکمهٔ آبی را بزن تا دوباره بشنوی</p>
        </div>
        <div id="ls-q"></div>`;
      const play = U.$('#ls-play', host);
      play.onclick = () => { narrate(L[0], { rate: .9 }); play.classList.add('listening'); setTimeout(() => play.classList.remove('listening'), 1200); };
      narrate(L[0], { rate: .9 });
      setTimeout(() => {
        gear(U.$('#ls-q', host), spec, ctx);
        choices(U.$('#ls-q', host), { question: qSentence, options: L[1], answer: L[2], emoji: '❓', rate: .95 })
          .then(resolve);
      }, Math.min(7000, 2600 + L[0].length * 55));
    });
  };

  /* ================= میکروفنِ قهرمان ================= */
  A.speak = function (host, spec, ctx) {
    gear(host, spec, ctx);
    const text = String(spec.item).replace(/\{name\}/g, U.load().explorer.name || 'من');
    const canSTT = AT.voice.sttAvailable();
    return new Promise((resolve) => {
      host.innerHTML = head(spec, ctx) + `
        <div class="q-text" style="text-align:center">🎤 این جمله را با صدای بلند و شمرده بگو:</div>
        <div class="card glass-strong" style="text-align:center;font-size:1.2em;font-weight:800;padding:16px;margin:10px 0;line-height:2">«${U.esc(text)}»</div>
        <div style="text-align:center;margin:8px 0"><button class="btn small ghost" id="sp-read">🔊 نمونه را بشنو</button></div>
        <div class="mic-panel">
          ${canSTT ? `<button class="mic-btn" id="sp-mic" aria-label="ضبط">🎤</button><p class="dim" style="font-size:.85em">دکمه را بزن، اجازهٔ میکروفن را بده و جمله را بگو!</p>` : `<p class="dim" style="font-size:.9em">میکروفنِ این دستگاه در دسترس نیست؛ بعد از گفتنِ جمله، دکمهٔ پایین را بزن!</p>`}
          <div class="mic-fallback"><button class="btn mint small" id="sp-said">گفتم! ✅</button></div>
        </div>
        <div id="sp-fb"></div>`;
      narrate('این جمله را با صدای بلند بگو: ' + text, { rate: .9 });
      U.$('#sp-read', host).onclick = () => narrate(text, { rate: .88 });
      U.$('#sp-said', host).onclick = () => finish('خودت تأیید کردی! تمرینِ گفتار یعنی همین شجاعت!');
      if (canSTT) {
        U.$('#sp-mic', host).onclick = () => {
          const mic = U.$('#sp-mic', host);
          mic.classList.add('listening'); U.sfx.pop();
          narrate('بگو!', { rate: .9 }).then(async () => {
            const res = await AT.voice.listen({ maxWait: 9000 });
            mic.classList.remove('listening');
            if (res.ok && AT.voice.heard(res.text, text)) finish('شنیدمت! دقیق و قشنگ گفتی! 🎉');
            else if (res.ok && res.text) finish('صدات را شنیدم: «' + res.text.slice(0, 60) + '» — دفعهٔ بعد شمرده‌تر بگو تا قشنگ‌تر شود!');
            else finish('صدایت واضح نرسید؛ ولی مهم این است که تلاش کردی!');
          });
        };
      }
      function finish(msg) {
        U.sfx.correct();
        const st = U.load(); st.explorer.speakConfident++; U.save();
        U.bumpSkill('speech', 1, 1);
        U.$('#sp-fb', host).innerHTML = `<div class="feedback good"><span class="f-emoji">🎤</span><div><b>آفرین، صدای شجاع!</b><br>${U.esc(msg)}</div></div><button class="btn small" id="sp-next">ادامه ⟵</button>`;
        narrate(msg, { rate: .95 });
        U.$('#sp-next', host).onclick = () => resolve({ hit: true, tries: 1 });
      }
    });
  };

  /* ================= مثل‌های ایرانی ================= */
  A.proverb = function (host, spec, ctx) {
    gear(host, spec, ctx);
    const p = spec.item; // [نیمهٔ اول، نیمهٔ دوم، معنا]
    const decoys = U.shuffled(AT.word.proverbs.filter((x) => x[1] !== p[1]), spec.seed).slice(0, 2).map((x) => x[1]);
    const options = U.shuffled([p[1], ...decoys], spec.seed + 5);
    return choices(host, {
      question: `این مثلِ ایرانی را کامل کن: «${p[0]} …»`, options,
      answer: options.indexOf(p[1]),
      explain: `«${p[0]} ${p[1]}» یعنی: ${p[2]}`, emoji: '💬',
    });
  };

  /* ================= کارآگاهِ جمله‌ها ================= */
  A.typo = function (host, spec, ctx) {
    gear(host, spec, ctx);
    const [sent, wrong, right] = spec.item;
    const decoys = U.shuffled(['چمن', 'پنجره', 'خورشید', 'مداد', 'پرنده', 'ماشین', 'چراغ', 'کوه'].filter((o) => o !== right), spec.seed).slice(0, 2);
    return new Promise((resolve) => {
      host.innerHTML = head(spec, ctx) + `
        <div class="q-text">🕵️ یک واژه در این جمله اشتباه است؛ اول روی آن بزن:</div>
        <div class="card glass-strong" style="margin:10px 0;padding:14px;line-height:2.6;font-size:1.05em;text-align:center" id="tp-sent"></div>
        <div id="tp-step2"></div>`;
      const sentEl = U.$('#tp-sent', host);
      narrate('جمله را گوش کن: ' + sent + '. یک واژه اشتباه است؛ روی آن بزن!', { rate: .95 });
      sent.split(' ').forEach((w) => {
        const b = U.el(`<button class="word-chip">${U.esc(w)}</button>`);
        b.onclick = () => {
          if (w === wrong) {
            U.sfx.correct();
            b.style.background = 'rgba(239,71,111,.3)'; b.style.borderColor = 'var(--coral)';
            sentEl.querySelectorAll('button').forEach((x) => x.disabled = true);
            const step2 = U.$('#tp-step2', host);
            gear(step2, spec, ctx);
            const options = U.shuffled([right, ...decoys], spec.seed + 9);
            choices(step2, {
              question: `«${wrong}» غلط است؛ واژهٔ درست کدام است؟`,
              options, answer: options.indexOf(right),
              explain: `جملهٔ درست: «${sent.replace(wrong, right)}»`,
            }).then(resolve);
          } else { U.sfx.wrong(); b.style.animation = 'shake .4s'; setTimeout(() => b.style.animation = '', 450); }
        };
        sentEl.appendChild(b);
      });
    });
  };

  /* ================= ماشینِ حساب ================= */
  A.mathops = function (host, spec, ctx) {
    gear(host, spec, ctx);
    const r = U.rng(spec.seed), t = ctx.t;
    let a, b, q, ans, op, explain;
    if (t < .35) {
      a = 2 + (r() * 7 | 0); b = 2 + (r() * 7 | 0); ans = a + b; op = '+';
      q = `${U.fa(a)} تا 🍎  و  ${U.fa(b)} تا 🍌 با هم چند تا می‌شوند؟`;
      explain = `${U.fa(a)} و ${U.fa(b)} با هم ${U.fa(ans)} می‌شوند؛ می‌توانی گروه‌ها را یکی‌یکی شمردی.`;
    } else if (t < .6) {
      a = 6 + (r() * (10 + t * 30) | 0); b = 3 + (r() * 8 | 0); ans = a + b; op = '+';
      q = `${U.fa(a)} + ${U.fa(b)} = ؟`;
      explain = `عددِ بزرگ را نگه دار و کوچک را قدم‌به‌قدم اضافه کن: ${U.fa(a)}، ${U.fa(a + 1)}… تا ${U.fa(ans)}.`;
    } else if (t < .8) {
      a = 14 + (r() * (20 + t * 50) | 0); b = 4 + (r() * 12 | 0); ans = a - b; op = '-';
      q = `${U.fa(a)} − ${U.fa(b)} = ؟`;
      explain = `از ${U.fa(a)}، ${U.fa(b)} تا کم کن؛ عقب‌شماری هم می‌شود.`;
    } else {
      a = 2 + (r() * 6 | 0); b = 2 + (r() * 5 | 0); ans = a * b; op = '×';
      q = `${U.fa(a)} × ${U.fa(b)} = ؟`;
      explain = `ضرب یعنی ${U.fa(a)} گروهِ ${U.fa(b)}تایی؛ گروه‌ها را بشمار!`;
    }
    const opts = U.shuffled([ans, ans + 2, Math.max(1, ans - 1), ans + 5 + (r() * 4 | 0)], spec.seed).map((x) => U.fa(x));
    return choices(host, {
      question: q, options: opts, answer: opts.indexOf(U.fa(ans)), explain,
      emoji: op === '+' ? '➕' : op === '-' ? '➖' : '✖️',
    });
  };

  /* ================= مسئلهٔ قصه‌دار ================= */
  A.wordproblem = function (host, spec, ctx) {
    gear(host, spec, ctx);
    const [tmpl, kind, lesson] = spec.item;
    const r = U.rng(spec.seed), t = ctx.t;
    const scale = 2 + Math.floor(t * 10);
    let a, b, ans;
    if (kind === 'sub') { b = 1 + (r() * (scale - 1) | 0); a = b + 1 + (r() * scale | 0); }
    else if (kind === 'div') { b = 2 + (r() * 3 | 0); ans = 2 + (r() * scale | 0); a = ans * b; }
    else { a = 2 + (r() * scale | 0); b = 2 + (r() * scale | 0); }
    if (kind === 'add') ans = a + b;
    else if (kind === 'sub') ans = a - b;
    else if (kind === 'mul') ans = a * b;
    else if (kind !== 'div') ans = a;
    const q = tmpl.replace(/\{a\}/g, U.fa(a)).replace(/\{b\}/g, U.fa(b));
    const opts = U.shuffled([ans, ans + 2, Math.max(1, ans - 1), ans + 4 + (r() * 4 | 0)], spec.seed).map((x) => U.fa(x));
    return choices(host, {
      question: q, options: opts, answer: opts.indexOf(U.fa(ans)),
      explain: lesson + ` (پاسخ: ${U.fa(ans)})`, emoji: '📝', rate: .92,
    });
  };

  /* ================= قطارِ الگوها ================= */
  A.pattern = function (host, spec, ctx) {
    gear(host, spec, ctx);
    const r = U.rng(spec.seed), t = ctx.t;
    const sets = [['🟢', '🔵'], ['⭐', '🌙'], ['🍎', '🍏'], ['🚗', '🚕', '🚙'], ['🌟', '✨']];
    const useNums = t > .45;
    let seq = [], ans, q, explain, options;
    if (useNums) {
      const step = 2 + (r() * (2 + Math.floor(t * 6)) | 0);
      const start = 1 + (r() * 9 | 0);
      for (let i = 0; i < 4; i++) seq.push(start + i * step);
      ans = start + 4 * step;
      q = 'عددِ بعدیِ این قطار کدام است؟ ' + seq.map(U.fa).join(' ، ') + ' ، ؟';
      explain = `هر بار به اندازهٔ ${U.fa(step)} زیاد می‌شود؛ این قانونِ الگوست.`;
      options = U.shuffled([ans, ans + step, ans + 1, ans - 1], spec.seed).map((x) => U.fa(x));
    } else {
      const s = sets[spec.seed % sets.length];
      for (let i = 0; i < 5; i++) seq.push(s[i % s.length]);
      ans = s[5 % s.length];
      q = 'شکلِ بعدیِ این قطار کدام است؟ ' + seq.join(' ') + ' …';
      explain = 'الگو را ببین: چه چیزی تکرار می‌شود؟ قانونِ تکرار را پیدا کن.';
      const all = ['⭐', '🌙', '🍎', '🍏', '🟢', '🔵', '🚗', '🚕', '🚙', '🌟', '✨'].filter((x) => x !== ans);
      options = U.shuffled([ans, ...U.shuffled(all, spec.seed).slice(0, 3)], spec.seed + 2);
    }
    return choices(host, {
      question: q, options, answer: options.indexOf(useNums ? U.fa(ans) : ans), explain, emoji: '🚂',
    });
  };

  /* ================= ترازوی منطق ================= */
  A.balance = function (host, spec, ctx) {
    gear(host, spec, ctx);
    const r = U.rng(spec.seed), t = ctx.t;
    const item = ['🐘', '🐈', '🍎', '⚽', '🐟', '🐼'][spec.seed % 6];
    const a = 1 + (r() * (3 + Math.floor(t * 6)) | 0);
    let b = 1 + (r() * (3 + Math.floor(t * 6)) | 0);
    if (b === a) b += 1;
    const q = 'کدام کفهٔ ترازو سنگین‌تر است؟';
    const opts = ['کفهٔ چپ ⚖️', 'کفهٔ راست ⚖️'];
    const ans = a > b ? 0 : 1;
    return new Promise((resolve) => {
      let tries = 0;
      host.innerHTML = head(spec, ctx) + `
        <div class="q-text" style="text-align:center">${q}</div>
        <div class="card glass-strong" style="display:flex;align-items:center;justify-content:center;gap:14px;padding:16px;margin:10px 0;font-size:1.9em">
          <div style="text-align:center;line-height:1.15">${item.repeat(a).split(item).join(item + '<br>')}<b style="font-size:.55em;display:block">(${U.fa(a)} تا)</b></div>
          <div style="font-size:1.6em">${a > b ? '⬇️' : ''}⚖️${b > a ? '⬇️' : ''}</div>
          <div style="text-align:center;line-height:1.15">${item.repeat(b).split(item).join(item + '<br>')}<b style="font-size:.55em;display:block">(${U.fa(b)} تا)</b></div>
        </div>
        <div class="opts c2" id="bl-opts"></div>
        <div id="bl-fb"></div>`;
      narrate(q + ' کفهٔ چپ ' + a + ' تا و کفهٔ راست ' + b + ' تا دارد.', {});
      const optsEl = U.$('#bl-opts', host);
      opts.forEach((op, i) => optsEl.appendChild(U.el(`<button class="opt" data-i="${i}" style="justify-content:center">${op}</button>`)));
      optsEl.onclick = (e) => {
        const btn = e.target.closest('.opt'); if (!btn) return;
        if (+btn.dataset.i === ans) {
          U.sfx.correct(); btn.classList.add('correct');
          optsEl.querySelectorAll('.opt').forEach((x, i) => { if (i !== ans) x.classList.add('dim'); });
          const why = `چون ${U.fa(Math.max(a, b))} از ${U.fa(Math.min(a, b))} بیشتر است؛ کفهٔ سنگین‌تر پایین می‌رود.`;
          U.$('#bl-fb', host).innerHTML = `<div class="feedback good"><span class="f-emoji">🎉</span><div><b>${pickPraise()}</b><br>${why}</div></div>`;
          narrate(pickPraise() + ' ' + why, {});
          setTimeout(() => resolve({ hit: tries === 0, tries: Math.max(1, tries) }), 2200);
        } else {
          tries++; U.sfx.wrong(); btn.classList.add('wrong');
          setTimeout(() => { btn.classList.remove('wrong'); btn.classList.add('dim'); }, 500);
          narrate(NUDGE[0], {});
        }
      };
    });
  };

  /* ================= حافظهٔ اعداد ================= */
  A.numbermemory = function (host, spec, ctx) {
    gear(host, spec, ctx);
    const r = U.rng(spec.seed), t = ctx.t;
    const len = 3 + Math.floor(t * 4);
    const num = Array.from({ length: len }, () => (r() * 10 | 0)).join('');
    return new Promise((resolve) => {
      host.innerHTML = head(spec, ctx) + `
        <div class="q-text" style="text-align:center">🔢 این عدد را خوب به خاطر بسپار!</div>
        <div class="card glass-strong" id="nm-num" style="text-align:center;font-size:2.6em;font-weight:800;letter-spacing:6px;padding:20px;margin:10px 0;direction:ltr">${num}</div>
        <p class="dim" style="text-align:center" id="nm-status">به‌خاطر بسپار…</p>
        <div id="nm-q"></div>`;
      narrate('این عدد را به خاطر بسپار: ' + num.split('').join(' ، '), { rate: .85 });
      setTimeout(() => {
        U.$('#nm-num', host).textContent = '🔒';
        U.$('#nm-status', host).textContent = 'حالا جواب بده!';
        const similar = (n) => {
          const idx = (U.rng(spec.seed + n)() * len) | 0;
          const arr = num.split(''); arr[idx] = String((+arr[idx] + 1 + n) % 10);
          return arr.join('');
        };
        const options = U.shuffled([num, similar(1), similar(2), similar(3)], spec.seed);
        gear(U.$('#nm-q', host), spec, ctx);
        choices(U.$('#nm-q', host), {
          question: 'کدام عدد دیدی؟', options, answer: options.indexOf(num),
          explain: `عددِ درست: ${num}`, emoji: '🔢',
        }).then(resolve);
      }, 2200 + len * 400);
    });
  };

  /* ================= کارت‌های جادویی ================= */
  A.memorycards = function (host, spec, ctx) {
    gear(host, spec, ctx);
    const t = ctx.t;
    const pairsN = t < .3 ? 4 : t < .55 ? 5 : t < .8 ? 6 : 8;
    const emojis = U.shuffled(['🌟', '🚀', '🐬', '🦋', '🌈', '🍉', '🦊', '🦉', '🎈', '🐢', '🐝', '🌸'], spec.seed).slice(0, pairsN);
    const cards = U.shuffled([...emojis, ...emojis], spec.seed + 1);
    return new Promise((resolve) => {
      let open = [], matched = 0, mistakes = 0, lock = false;
      host.innerHTML = head(spec, ctx) + `
        <div class="q-text" style="text-align:center">🃏 جفت‌های یکسان را پیدا کن!</div>
        <div class="mem-grid" id="mc-grid"></div>`;
      narrate('جفت‌های یکسان را پیدا کن! اول یکی را باز کن، بعد جفتش را حدس بزن.', {});
      const grid = U.$('#mc-grid', host);
      cards.forEach((c) => {
        const card = U.el(`<button class="mem-card" aria-label="کارت"><span class="face">${c}</span><span class="back">؟</span></button>`);
        card.dataset.c = c;
        card.onclick = () => {
          if (lock || card.classList.contains('open') || card.classList.contains('matched')) return;
          card.classList.add('open'); U.sfx.tap();
          open.push(card);
          if (open.length === 2) {
            lock = true;
            const [x, y] = open;
            if (x.dataset.c === y.dataset.c) {
              setTimeout(() => {
                x.classList.add('matched'); y.classList.add('matched');
                U.sfx.coin(); matched++; open = []; lock = false;
                if (matched === pairsN) {
                  U.sfx.bigwin();
                  const good = mistakes <= 2;
                  host.appendChild(U.el(`<div class="feedback ${good ? 'good' : 'bad'}"><span class="f-emoji">🧠</span><div><b>${good ? pickPraise() : 'تمام شد!'}</b><br>همهٔ ${U.fa(pairsN)} جفت پیدا شد${good ? '!' : ` با ${U.fa(mistakes)} خطا — دفعهٔ بعد با تمرکزِ بیشتر!`}</div></div>`));
                  narrate(good ? pickPraise() + ' همه را پیدا کردی!' : 'همه را پیدا کردی؛ دفعهٔ بعد تمرکز بیشتر!', {});
                  setTimeout(() => resolve({ hit: good, tries: mistakes + 1 }), 2200);
                }
              }, 450);
            } else {
              mistakes++; U.sfx.wrong();
              setTimeout(() => { x.classList.remove('open'); y.classList.remove('open'); open = []; lock = false; }, 850);
            }
          }
        };
        grid.appendChild(card);
      });
    });
  };

  /* ================= حافظهٔ واژه‌ها ================= */
  A.memorywords = function (host, spec, ctx) {
    gear(host, spec, ctx);
    const [topic, words] = spec.item;
    const n = ctx.t > .5 ? 5 : 4;
    const list = U.shuffled(words, spec.seed).slice(0, n);
    return new Promise((resolve) => {
      host.innerHTML = head(spec, ctx) + `
        <div class="q-text" style="text-align:center">🧠 این واژه‌های «${U.esc(topic)}» را به‌خاطر بسپار:</div>
        <div class="card glass-strong" id="mw-list" style="display:flex;flex-wrap:wrap;gap:8px;justify-content:center;font-size:1.2em;font-weight:800;padding:16px;margin:10px 0"></div>
        <p class="dim" style="text-align:center" id="mw-status">به‌خاطر بسپار…</p>
        <div id="mw-q"></div>`;
      const listEl = U.$('#mw-list', host);
      list.forEach((w) => listEl.appendChild(U.el(`<span class="chip" style="font-size:1em">${U.esc(w)}</span>`)));
      narrate('این واژه‌ها را به خاطر بسپار: ' + list.join(' ، '), { rate: .9 });
      setTimeout(() => {
        listEl.innerHTML = ''; U.$('#mw-status', host).textContent = 'حالا جواب بده!';
        const target = list[0];
        const others = U.shuffled(words.filter((w) => !list.includes(w)), spec.seed + 2).slice(0, 3);
        const options = U.shuffled([target, ...others], spec.seed + 4);
        gear(U.$('#mw-q', host), spec, ctx);
        choices(U.$('#mw-q', host), {
          question: 'کدام واژه در فهرست بود؟', options, answer: options.indexOf(target),
          explain: `«${target}» در فهرستِ ${topic} بود. ترفند: برای هر واژه یک تصویرِ ذهنی بساز!`, emoji: '💭',
        }).then(resolve);
      }, 3200 + n * 500);
    });
  };

  /* ================= سمعکِ رنگین (سایمون) ================= */
  A.simon = function (host, spec, ctx) {
    gear(host, spec, ctx);
    const rounds = Math.min(6, 3 + Math.floor(ctx.t * 4));
    const pads = ['#ef476f', '#06d6a0', '#4cc9f0', '#ffd166'];
    return new Promise((resolve) => {
      let round = 1, seq = [], inputIdx = 0, canInput = false;
      host.innerHTML = head(spec, ctx) + `
        <div class="q-text" style="text-align:center">🎵 دنبالهٔ رنگ‌ها را با چشم و گوش یاد بگیر و تکرار کن!</div>
        <div class="simon-grid" id="sm-grid"></div>
        <p class="dim" style="text-align:center;font-weight:700" id="sm-status">دقت کن…</p>`;
      narrate('دنبالهٔ رنگ‌ها را با چشم و گوش یاد بگیر و همان را تکرار کن!', {});
      const grid = U.$('#sm-grid', host);
      const padEls = pads.map((c, n) => {
        const el = U.el(`<button class="simon-pad" style="background:${c}" aria-label="دکمهٔ رنگی"></button>`);
        el.dataset.n = n;
        grid.appendChild(el);
        return el;
      });
      function lit(el) {
        el.classList.add('lit'); U.sfx.note(+el.dataset.n);
        setTimeout(() => el.classList.remove('lit'), 380);
      }
      const wait = (ms) => new Promise((res) => setTimeout(res, ms));
      async function playSeq() {
        canInput = false; inputIdx = 0;
        U.$('#sm-status', host).textContent = `دورِ ${U.fa(round)} از ${U.fa(rounds)} — دقت کن…`;
        await wait(600);
        for (const n of seq) { lit(padEls[n]); await wait(520); }
        U.$('#sm-status', host).textContent = 'حالا نوبتِ توست!';
        canInput = true;
      }
      function next() {
        if (round > rounds) {
          U.sfx.bigwin();
          host.appendChild(U.el(`<div class="feedback good"><span class="f-emoji">🎵</span><div><b>استادِ سمعکِ رنگین!</b><br>${U.fa(rounds)} دورِ کامل را تکرار کردی.</div></div>`));
          narrate('استاد سمعک رنگین! همه را دقیق تکرار کردی!', {});
          setTimeout(() => resolve({ hit: true, tries: rounds }), 2200);
          return;
        }
        seq.push((U.rng(spec.seed + round * 13)() * 4) | 0);
        playSeq();
      }
      grid.onclick = (e) => {
        const el = e.target.closest('.simon-pad'); if (!el) return;
        lit(el);
        if (!canInput) return;
        const n = +el.dataset.n;
        if (n === seq[inputIdx]) {
          inputIdx++;
          if (inputIdx === seq.length) { round++; U.sfx.coin(); setTimeout(next, 700); }
        } else {
          canInput = false;
          U.sfx.wrong();
          const good = round - 1 >= Math.ceil(rounds / 2);
          host.appendChild(U.el(`<div class="feedback ${good ? 'good' : 'bad'}"><span class="f-emoji">🎵</span><div><b>${good ? 'آفرین!' : 'تمام شد!'}</b><br>تا دورِ ${U.fa(Math.max(0, round - 1))} درست تکرار کردی. گوشِ شنوا با تمرین تیزتر می‌شود!</div></div>`));
          narrate(good ? 'آفرین! چند دورِ کامل را تکرار کردی!' : 'تمام شد؛ گوشِ شنوا با تمرین تیزتر می‌شود!', {});
          setTimeout(() => resolve({ hit: good, tries: rounds }), 2300);
        }
      };
      next();
    });
  };

  /* ================= چشمِ تیزبین ================= */
  A.focus = function (host, spec, ctx) {
    gear(host, spec, ctx);
    const total = 12;
    const stim = Array.from({ length: total }, (_, i) => (U.rng(spec.seed + i * 7)() < .65 ? '⭐' : '☁️'));
    return new Promise((resolve) => {
      let i = 0, score = 0, wrong = 0, showing = false, current = null;
      host.innerHTML = head(spec, ctx) + `
        <div class="q-text" style="text-align:center">🎯 فقط وقتی <b>⭐ ستاره</b> دیدی، سریع بزن!<br>وقتی ☁️ ابر است، دست نزن!</div>
        <div class="card glass-strong" id="fc-stage" style="height:180px;display:grid;place-items:center;font-size:4.5em;margin:10px 0;cursor:pointer"><span class="dim">آماده…</span></div>
        <div style="display:flex;justify-content:space-between;font-size:.9em" class="dim"><span>درست: <b id="fc-ok" style="color:var(--mint)">۰</b></span><span>خطا: <b id="fc-bad" style="color:var(--coral)">۰</b></span></div>`;
      const stage = U.$('#fc-stage', host);
      narrate('فقط وقتی ستاره دیدی سریع بزن؛ وقتی ابر است دست نزن!', {});
      stage.onclick = () => {
        if (!showing || !current) return;
        showing = false;
        if (current === '⭐') { score++; U.sfx.pop(); } else { wrong++; U.sfx.wrong(); }
        update();
      };
      function update() {
        const ok = U.$('#fc-ok', host), bad = U.$('#fc-bad', host);
        if (ok) ok.textContent = U.fa(score);
        if (bad) bad.textContent = U.fa(wrong);
      }
      function next() {
        if (i >= total) {
          const good = wrong <= 2 && score >= total * .5;
          host.appendChild(U.el(`<div class="feedback ${good ? 'good' : 'bad'}"><span class="f-emoji">🎯</span><div><b>${good ? pickPraise() : 'تمام شد!'}</b><br>${U.fa(score)} ستارهٔ درست${wrong ? ` و ${U.fa(wrong)} خطا` : ' بدون خطا!'}.</div></div>`));
          narrate(good ? pickPraise() + ' تمرکزت عالی بود!' : 'تمرکز مثل عضله است؛ با تمرین قوی‌تر می‌شود!', {});
          setTimeout(() => resolve({ hit: good, tries: Math.max(1, wrong + 1) }), 2200);
          return;
        }
        current = stim[i]; showing = true;
        stage.innerHTML = `<span class="pop-in">${current}</span>`;
        if (current === '⭐') U.sfx.note(4);
        i++;
        setTimeout(() => { showing = false; stage.innerHTML = '<span class="dim">…</span>'; }, 900);
        setTimeout(next, 1300 + (U.rng(spec.seed + i)() * 400 | 0));
      }
      setTimeout(next, 1600);
    });
  };

  /* ================= شمردنِ برق‌آسا ================= */
  A.count = function (host, spec, ctx) {
    gear(host, spec, ctx);
    const r = U.rng(spec.seed), t = ctx.t;
    const n = 4 + Math.floor(r() * (6 + t * 14));
    const item = ['⭐', '🐟', '🎈', '🍓', '🦋'][spec.seed % 5];
    return new Promise((resolve) => {
      host.innerHTML = head(spec, ctx) + `
        <div class="q-text" style="text-align:center">⚡ چشم‌ها تیز باشد: چند تا <b>${item}</b> خواهد بود؟</div>
        <div class="card glass-strong" id="ct-stage" style="height:170px;display:flex;flex-wrap:wrap;align-content:center;justify-content:center;font-size:2.2em;gap:2px;margin:10px 0;overflow:hidden"></div>
        <div id="ct-q"></div>`;
      const stage = U.$('#ct-stage', host);
      for (let k = 0; k < n; k++) {
        stage.appendChild(U.el(`<span style="padding:2px 6px;transform:translate(${(U.rng(spec.seed + k)() * 40 - 20) | 0}px,${(U.rng(spec.seed + k * 3)() * 30 - 15) | 0}px)">${item}</span>`));
      }
      narrate('آماده باش! تعداد را سریع بشمار!', {});
      setTimeout(() => {
        stage.innerHTML = '<span class="dim" style="font-size:1em">پنهان شد! چند تا بود؟</span>';
        const options = U.shuffled([n, n + 1, Math.max(1, n - 1), n + 2], spec.seed).map((x) => U.fa(x));
        gear(U.$('#ct-q', host), spec, ctx);
        choices(U.$('#ct-q', host), {
          question: `چند تا ${item} دیدی؟`, options, answer: options.indexOf(U.fa(n)),
          explain: `درست: ${U.fa(n)} تا. ترفند: گروه‌های دوتایی و سه‌تایی را یک‌جا ببین!`, emoji: item,
        }).then(resolve);
      }, 1500 + Math.min(1800, n * 90));
    });
  };

  /* ================= ترتیبِ دانایی ================= */
  A.order = function (host, spec, ctx) {
    gear(host, spec, ctx);
    const [topic, items, reason] = spec.item;
    return new Promise((resolve) => {
      let placed = [], tries = 0;
      host.innerHTML = head(spec, ctx) + `
        <div class="q-text" style="text-align:center">🪜 به ترتیبِ درست بزن: <b>${U.esc(topic)}</b></div>
        <p class="dim" style="text-align:center;font-size:.85em">${U.esc(reason)}</p>
        <div id="od-slots" style="display:flex;gap:6px;justify-content:center;margin:10px 0;flex-wrap:wrap"></div>
        <div id="od-pool" style="display:flex;gap:8px;justify-content:center;flex-wrap:wrap;margin:8px 0;min-height:52px"></div>
        <div id="od-fb"></div>`;
      narrate('به ترتیبِ درست بزن: ' + topic + '. ' + reason, { rate: .95 });
      const slots = U.$('#od-slots', host), pool = U.$('#od-pool', host);
      function render() {
        slots.innerHTML = items.map((_, i) => `<span class="chip" style="min-width:70px;justify-content:center">${placed[i] ? U.esc(placed[i]) : U.fa(i + 1) + '؟'}</span>`).join('');
        pool.innerHTML = '';
        U.shuffled(items, spec.seed).filter((x) => !placed.includes(x)).forEach((w) => {
          const b = U.el(`<button class="opt" style="min-height:46px;padding:6px 12px;font-size:.9em">${U.esc(w)}</button>`);
          b.dataset.w = w;
          pool.appendChild(b);
        });
      }
      render();
      pool.onclick = (e) => {
        const b = e.target.closest('.opt'); if (!b) return;
        const w = b.dataset.w;
        if (w === items[placed.length]) {
          U.sfx.pop(); placed.push(w); render();
          if (placed.length === items.length) {
            U.sfx.bigwin();
            U.$('#od-fb', host).innerHTML = `<div class="feedback good"><span class="f-emoji">🎉</span><div><b>${pickPraise()}</b><br>ترتیبِ درست: ${items.map(U.esc).join(' ← ')}</div></div><button class="btn small" id="od-next">ادامه ⟵</button>`;
            narrate(pickPraise(), {});
            U.$('#od-next', host).onclick = () => resolve({ hit: tries === 0, tries: Math.max(1, tries) });
          }
        } else {
          tries++; U.sfx.wrong(); b.classList.add('wrong');
          narrate(NUDGE[0], {});
          setTimeout(() => b.classList.remove('wrong'), 500);
          if (tries >= 4) {
            placed = items.slice(); render();
            U.$('#od-fb', host).innerHTML = `<div class="feedback bad"><span class="f-emoji">💡</span><div>ترتیبِ درست: ${items.map(U.esc).join(' ← ')}</div></div><button class="btn small" id="od-next">ادامه ⟵</button>`;
            U.$('#od-next', host).onclick = () => resolve({ hit: false, tries });
          }
        }
      };
    });
  };

  /* ================= دسته‌بندیِ هوشمند ================= */
  A.sortcat = function (host, spec, ctx) {
    gear(host, spec, ctx);
    const [title, buckets, items] = spec.item;
    return new Promise((resolve) => {
      let doneN = 0, tries = 0, sel = null;
      host.innerHTML = head(spec, ctx) + `
        <div class="q-text" style="text-align:center">🗃️ ${U.esc(title)}</div>
        <p class="dim" style="text-align:center;font-size:.85em">اول یک مورد را انتخاب کن، بعد سبدِ درستش را بزن</p>
        <div id="sc-items" style="display:flex;gap:8px;justify-content:center;flex-wrap:wrap;margin:10px 0;min-height:54px"></div>
        <div id="sc-buckets" style="display:grid;grid-template-columns:repeat(${buckets.length},1fr);gap:8px;margin-top:6px"></div>
        <div id="sc-fb"></div>`;
      narrate(title + '. اول یک مورد را انتخاب کن، بعد سبدِ درستش را بزن!', {});
      const itemsEl = U.$('#sc-items', host), bEl = U.$('#sc-buckets', host);
      U.shuffled(items, spec.seed).forEach(([name, emoji, bidx]) => {
        const b = U.el(`<button class="opt" style="min-height:48px;padding:4px 10px;font-size:.9em;gap:6px"><span class="opt-emoji" style="font-size:1.3em">${emoji}</span>${U.esc(name)}</button>`);
        b.dataset.b = bidx;
        itemsEl.appendChild(b);
      });
      buckets.forEach((bt, i) => {
        const b = U.el(`<div class="card tight sc-bucket" style="text-align:center;padding:10px 6px;min-height:110px"><b style="font-size:.9em">${U.esc(bt)}</b><div class="sc-in" style="display:flex;flex-wrap:wrap;gap:4px;margin-top:8px;font-size:1.5em;min-height:34px;justify-content:center"></div></div>`);
        b.dataset.i = i;
        bEl.appendChild(b);
      });
      itemsEl.onclick = (e) => {
        const b = e.target.closest('.opt'); if (!b || b.disabled) return;
        itemsEl.querySelectorAll('.opt').forEach((x) => x.style.borderColor = '');
        b.style.borderColor = 'var(--sun)'; sel = b; U.sfx.tap();
      };
      bEl.onclick = (e) => {
        const box = e.target.closest('.sc-bucket'); if (!box || !sel) return;
        tries++;
        if (+box.dataset.i === +sel.dataset.b) {
          U.sfx.pop();
          box.querySelector('.sc-in').appendChild(U.el(`<span class="pop-in">${sel.querySelector('.opt-emoji').textContent}</span>`));
          sel.remove(); sel = null; doneN++;
          if (doneN === items.length) {
            U.sfx.bigwin();
            U.$('#sc-fb', host).innerHTML = `<div class="feedback good"><span class="f-emoji">🎉</span><div><b>${pickPraise()}</b><br>همه در جای درست قرار گرفت!</div></div><button class="btn small" id="sc-next">ادامه ⟵</button>`;
            narrate(pickPraise() + ' همه را درست دسته‌بندی کردی!', {});
            U.$('#sc-next', host).onclick = () => resolve({ hit: tries === items.length, tries: Math.max(1, tries) });
          }
        } else { U.sfx.wrong(); U.vibrate(60); narrate('سبدِ درست این نیست؛ یک بار دیگر فکر کن!', {}); }
      };
    });
  };

  /* ================= متفاوت کدام است؟ ================= */
  A.odd = function (host, spec, ctx) {
    gear(host, spec, ctx);
    const [items, oddIdx, why] = spec.item; // items: [[نام، ایموجی]]
    const options = items.map((x) => x[1]);
    return choices(host, {
      question: 'کدام با بقیه فرق دارد؟', options, answer: oddIdx, explain: why, rate: .95,
    });
  };

  /* ================= نقشهٔ ایران ================= */
  A.mapiran = function (host, spec, ctx) {
    gear(host, spec, ctx);
    const pts = AT.ethics.mapPoints; // [نام، ایموجی، x، y]
    const target = spec.item;
    const others = U.shuffled(pts.filter((p) => p[0] !== target[0]), spec.seed).slice(0, 3);
    const cands = U.shuffled([target, ...others], spec.seed + 4);
    return new Promise((resolve) => {
      host.innerHTML = head(spec, ctx) + `
        <div class="q-text" style="text-align:center">🗺️ روی نقشهٔ ایران، <b>${target[1]} ${U.esc(target[0])}</b> کدام نقطه است؟</div>
        <div style="position:relative;background:radial-gradient(circle at 50% 40%,rgba(80,237,153,.08),transparent 70%),rgba(255,255,255,.04);border-radius:20px;padding:6px" id="mi-wrap"></div>
        <div id="mi-fb"></div>`;
      U.$('#mi-wrap', host).innerHTML = iranMapSVG(cands);
      narrate('روی نقشهٔ ایران، ' + target[0] + ' کدام نقطه است؟', {});
      U.$$('#mi-wrap .mi-dot', host).forEach((dot) => {
        dot.onclick = () => {
          if (dot.dataset.n === target[0]) {
            U.sfx.correct(); dot.classList.add('correct');
            U.$$('#mi-wrap .mi-dot', host).forEach((d) => { if (d.dataset.n !== target[0]) d.classList.add('dim'); });
            U.$('#mi-fb', host).innerHTML = `<div class="feedback good"><span class="f-emoji">🗺️</span><div><b>${pickPraise()}</b><br>${U.esc(target[0])} در این جای نقشه است.</div></div><button class="btn small" id="mi-next">ادامه ⟵</button>`;
            narrate(pickPraise(), {});
            U.$('#mi-next', host).onclick = () => resolve({ hit: true, tries: 1 });
          } else {
            U.sfx.wrong(); dot.classList.add('wrong'); setTimeout(() => dot.classList.remove('wrong'), 500);
          }
        };
      });
    });
  };
  function iranMapSVG(cands) {
    const path = 'M5,8 L12,4 L20,9 L27,6 L34,10 L40,8 L47,11 L55,8 L62,12 L70,10 L78,13 L86,11 L93,15 L95,22 L88,28 L90,36 L96,44 L99,58 L97,74 L99,92 L88,95 L76,90 L68,82 L60,84 L52,88 L42,80 L37,70 L27,66 L22,56 L14,52 L12,42 L6,36 L9,26 L4,18 Z';
    const dots = cands.map((c, i) =>
      `<g class="mi-dot" data-n="${U.esc(c[0])}">
        <circle cx="${c[2]}" cy="${c[3]}" r="6.5" fill="#ffd166" stroke="#7c3f00" stroke-width="1.6"/>
        <text x="${c[2]}" y="${c[3] + 3.6}" text-anchor="middle" font-size="8" font-weight="800" fill="#7c3f00">${LETTERS[i]}</text>
      </g>`).join('');
    return `<svg viewBox="0 0 100 100" style="width:100%;max-width:420px;display:block;margin:0 auto" aria-label="نقشهٔ سادهٔ ایران">
      <path d="${path}" fill="rgba(128,237,153,.25)" stroke="#80ed99" stroke-width="1.4" stroke-linejoin="round"/>
      ${dots}
    </svg>`;
  }

  /* ================= انتخابِ قهرمان ================= */
  A.scenario = function (host, spec, ctx) {
    gear(host, spec, ctx);
    const s = spec.item; // [موقعیت، [گزینه‌ها]، ایندکس، دلیل]
    return choices(host, { question: '🧭 ' + s[0], options: s[1], answer: s[2], explain: s[3], rate: .95 });
  };

  /* ================= نفسِ آرامش ================= */
  A.calm = function (host, spec, ctx) {
    gear(host, spec, ctx);
    const c = spec.item; // [عنوان، [قدم‌ها]]
    return new Promise((resolve) => {
      let i = 0;
      host.innerHTML = head(spec, ctx) + `
        <div class="q-text" style="text-align:center">🫧 تمرینِ آرامش: <b>${U.esc(c[0])}</b></div>
        <div class="breath-circle" id="cl-circle">آماده…</div>
        <div id="cl-steps" style="text-align:center;font-weight:700;line-height:2.1;min-height:2.2em"></div>`;
      const circle = U.$('#cl-circle', host);
      narrate('تمرینِ آرامش: ' + c[0], { rate: .9 }).then(run, run);
      async function run() {
        if (i >= c[1].length) {
          U.sfx.bigwin();
          host.appendChild(U.el(`<div>
            <div class="feedback good"><span class="f-emoji">🫧</span><div><b>چه آرام و قشنگ!</b><br>این تمرین را هر وقت خواستی تکرار کن.</div></div>
            <button class="btn small" id="cl-next">ادامه ⟵</button>
          </div>`));
          narrate('چه آرام و قشنگ! هر وقت خواستی این تمرین را دوباره انجام بده.', {});
          U.$('#cl-next', host).onclick = () => resolve({ hit: true, tries: 1 });
          return;
        }
        const st = c[1][i];
        U.$('#cl-steps', host).innerHTML = `قدمِ ${U.fa(i + 1)} از ${U.fa(c[1].length)}: ${U.esc(st)}`;
        if (/دم|نگه داری|بازدم|شکم/.test(st)) {
          circle.classList.add('inhale');
          await narrate(st, { rate: .8 });
          circle.classList.remove('inhale');
        } else {
          circle.textContent = '🫧';
          await narrate(st, { rate: .88 });
        }
        i++;
        setTimeout(run, 500);
      }
    });
  };

  /* ================= شناختِ احساس ================= */
  A.emotion = function (host, spec, ctx) {
    gear(host, spec, ctx);
    const E = spec.item && spec.item.length ? spec.item : EMOTIONS[spec.idx % EMOTIONS.length];
    return choices(host, {
      question: E[0], options: E[2], answer: E[2].indexOf(E[1]), explain: E[3], emoji: '💛', rate: .95,
    });
  };
  A.emotionBank = EMOTIONS;

  /* ================= صدای باور ================= */
  A.affirm = function (host, spec, ctx) {
    gear(host, spec, ctx);
    return new Promise((resolve) => {
      host.innerHTML = head(spec, ctx) + `
        <div class="q-text" style="text-align:center">🌟 این جملهٔ باور را با صدای بلند و شمرده بگو:</div>
        <div class="card glass-strong" style="text-align:center;font-size:1.25em;font-weight:800;padding:18px;margin:10px 0;line-height:2">«${U.esc(spec.item)}»</div>
        <div style="text-align:center"><button class="btn mint" id="af-done">با صدای بلند گفتم! 🎤</button></div>`;
      narrate('این جمله را با صدای بلند و با اعتماد بگو: ' + spec.item, { rate: .85 });
      U.$('#af-done', host).onclick = () => {
        U.sfx.bigwin(); U.confetti(host, { count: 50, dur: 1800 });
        const st = U.load(); st.explorer.speakConfident++; U.save();
        U.bumpSkill('speech', 1, 1);
        host.appendChild(U.el(`<div class="feedback good"><span class="f-emoji">🌟</span><div><b>صدایت پر از قدرت بود!</b><br>هر روز یک جملهٔ باور، ذهنِ قهرمان می‌سازد.</div></div>`));
        narrate('صدایت پر از قدرت بود! آفرین!', {});
        setTimeout(() => resolve({ hit: true, tries: 1 }), 2300);
      };
    });
  };

  /* ================= آینهٔ خودشناسی ================= */
  A.selfcheck = function (host, spec, ctx) {
    gear(host, spec, ctx);
    const [q, opts] = spec.item;
    return new Promise((resolve) => {
      host.innerHTML = head(spec, ctx) + `
        <div class="q-text" style="text-align:center">🪞 ${U.esc(q)}</div>
        <p class="dim" style="text-align:center;font-size:.85em">پاسخِ درست و غلط ندارد؛ فقط خودت را بهتر بشناس!</p>
        <div class="opts c2" id="sl-opts"></div>`;
      narrate(q, {});
      const optsEl = U.$('#sl-opts', host);
      opts.forEach((o, i) => optsEl.appendChild(U.el(`<button class="opt" data-i="${i}"><span class="opt-letter">${LETTERS[i]}</span><span>${U.esc(o)}</span></button>`)));
      optsEl.onclick = (e) => {
        const b = e.target.closest('.opt'); if (!b) return;
        U.sfx.correct();
        b.classList.add('correct');
        const st = U.load();
        st.explorer.interestPicks.push({ q: spec.idx, o: +b.dataset.i, t: Date.now() });
        if (st.explorer.interestPicks.length > 80) st.explorer.interestPicks = st.explorer.interestPicks.slice(-60);
        U.save();
        narrate('ثبت شد! آفرین که خودت را شناختی.', {});
        setTimeout(() => resolve({ hit: true, tries: 1 }), 1400);
      };
    });
  };

  /* ================= کارگاهِ نقاشی ================= */
  A.draw = function (host, spec, ctx) {
    gear(host, spec, ctx);
    const colors = ['#ef476f', '#ffd166', '#06d6a0', '#4cc9f0', '#c77dff', '#fb8500', '#ffffff', '#2b2140'];
    return new Promise((resolve) => {
      host.innerHTML = head(spec, ctx) + `
        <div class="q-text" style="text-align:center">🎨 ${U.esc(spec.item)}</div>
        <canvas id="dw-canvas" width="640" height="420" style="width:100%;background:#fffdf6;border-radius:16px;border:2px solid var(--stroke-strong);touch-action:none;cursor:crosshair"></canvas>
        <div style="display:flex;gap:6px;justify-content:center;flex-wrap:wrap;margin:8px 0">
          ${colors.map((c) => `<button class="dw-color" data-c="${c}" style="width:34px;height:34px;border-radius:50%;background:${c};border:3px solid rgba(255,255,255,.5);box-shadow:0 2px 6px rgba(0,0,0,.3)"></button>`).join('')}
          <button class="btn small ghost" id="dw-clear">🧽 پاک‌کن</button>
        </div>
        <div style="text-align:center"><button class="btn mint" id="dw-save">تمومه! ذخیره کن 🖼️</button></div>`;
      const cv = U.$('#dw-canvas', host);
      const g = cv.getContext('2d');
      g.lineWidth = 7; g.lineCap = 'round'; g.lineJoin = 'round';
      let color = '#ef476f', drawing = false, last = null;
      const pos = (e) => {
        const r = cv.getBoundingClientRect();
        return { x: (e.clientX - r.left) * (cv.width / r.width), y: (e.clientY - r.top) * (cv.height / r.height) };
      };
      cv.addEventListener('pointerdown', (e) => { drawing = true; last = pos(e); e.preventDefault(); });
      cv.addEventListener('pointermove', (e) => {
        if (!drawing) return;
        const p = pos(e);
        g.strokeStyle = color;
        g.beginPath(); g.moveTo(last.x, last.y); g.lineTo(p.x, p.y); g.stroke();
        last = p; e.preventDefault();
      });
      cv.addEventListener('pointerup', () => drawing = false);
      cv.addEventListener('pointerleave', () => drawing = false);
      host.querySelectorAll('.dw-color').forEach((b) => {
        b.onclick = () => {
          color = b.dataset.c;
          host.querySelectorAll('.dw-color').forEach((x) => x.style.outline = '');
          b.style.outline = '3px solid #ffd166';
          U.sfx.tap();
        };
      });
      U.$('#dw-clear', host).onclick = () => { g.clearRect(0, 0, cv.width, cv.height); U.sfx.whoosh(); };
      U.$('#dw-save', host).onclick = () => {
        try {
          const st = U.load();
          st.explorer.art.push({ d: cv.toDataURL('image/png'), p: spec.item, t: Date.now() });
          if (st.explorer.art.length > 12) st.explorer.art = st.explorer.art.slice(-12);
          U.save();
        } catch {}
        U.sfx.bigwin(); U.confetti(host, { count: 60, dur: 2000 });
        narrate('چه نقاشیِ قشنگی! در گنجینهٔ آثار ثبت شد.', {});
        host.appendChild(U.el(`<div class="feedback good"><span class="f-emoji">🖼️</span><div><b>اثرِ شما در گنجینه ثبت شد!</b><br>هنرِ تو، امضای دنیای توست.</div></div>`));
        setTimeout(() => resolve({ hit: true, tries: 1 }), 2100);
      };
      narrate('کارگاهِ نقاشی! ' + spec.item + ' با رنگ‌ها خیالت را آزاد بگذار!', { rate: .95 });
    });
  };

  /* ================= ریتمِ موسیقی ================= */
  A.rhythm = function (host, spec, ctx) {
    gear(host, spec, ctx);
    const r = U.rng(spec.seed), t = ctx.t;
    const len = 5 + Math.floor(t * 3);
    const pattern = Array.from({ length: len }, () => r() < .5);
    const strongN = pattern.filter(Boolean).length;
    return new Promise((resolve) => {
      let taps = 0, tries = 0, phase = 'listen';
      host.innerHTML = head(spec, ctx) + `
        <div class="q-text" style="text-align:center">🥁 به ریتم گوش کن؛ <b>به اندازهٔ ضربه‌های قویِ بلند</b> بکوب!</div>
        <div style="text-align:center;margin:12px 0">
          <button class="mic-btn" id="rh-pad" style="background:linear-gradient(180deg,#ff9bb2,#ef476f);box-shadow:0 6px 0 #a11d3f" aria-label="طبل">🥁</button>
        </div>
        <p class="dim" style="text-align:center;font-weight:700" id="rh-status">اول گوش کن…</p>
        <div style="display:flex;gap:4px;justify-content:center" id="rh-pips"></div>
        <div style="text-align:center;margin-top:10px">
          <button class="btn small ghost" id="rh-replay">🔁 دوباره بشنوم</button>
          <button class="btn mint small" id="rh-done" style="display:none">بس است، تمام! ✋</button>
        </div>`;
      const pad = U.$('#rh-pad', host), status = U.$('#rh-status', host), pips = U.$('#rh-pips', host);
      const wait = (ms) => new Promise((res) => setTimeout(res, ms));
      function renderPips() {
        pips.innerHTML = pattern.map((s, i) =>
          `<span style="width:14px;height:14px;border-radius:50%;border:1px solid var(--stroke);display:inline-block;background:${phase === 'listen' && i < shown ? (s ? 'var(--coral)' : 'var(--sky)') : i < taps ? 'var(--coral)' : 'var(--card-strong)'}"></span>`).join('');
      }
      let shown = 0;
      renderPips();
      async function play() {
        phase = 'listen'; shown = 0; taps = 0; renderPips();
        status.textContent = 'گوش کن…';
        U.$('#rh-done', host).style.display = 'none';
        await wait(700);
        for (let i = 0; i < pattern.length; i++) {
          U.sfx.beat(pattern[i]);
          pad.style.transform = pattern[i] ? 'scale(1.12)' : 'scale(.96)';
          setTimeout(() => pad.style.transform = '', 200);
          shown = i + 1; renderPips();
          await wait(650);
        }
        phase = 'repeat';
        status.textContent = 'حالا به اندازهٔ ضربه‌های قوی بکوب!';
        U.$('#rh-done', host).style.display = '';
        narrate('حالا تو! به اندازهٔ ضربه‌های قوی بکوب و بعد دکمهٔ بس است را بزن!', {});
      }
      pad.onclick = () => {
        if (phase !== 'repeat') return;
        U.sfx.beat(true);
        taps++;
        renderPips();
        status.textContent = `${U.fa(taps)} ضربه کوبیدی!`;
      };
      U.$('#rh-replay', host).onclick = () => { if (phase === 'repeat') { tries++; play(); } };
      U.$('#rh-done', host).onclick = () => {
        if (phase !== 'repeat') return;
        phase = 'done';
        if (taps === strongN && tries === 0) {
          U.sfx.bigwin();
          host.appendChild(U.el(`<div class="feedback good"><span class="f-emoji">🥁</span><div><b>${pickPraise()}</b><br>دقیقاً ${U.fa(strongN)} ضربهٔ قوی! حسِ ریتمت عالی است.</div></div>`));
          narrate(pickPraise() + ' حسِ ریتمت عالی است!', {});
          setTimeout(() => resolve({ hit: true, tries: 1 }), 2200);
        } else if (tries < 1 && taps !== strongN) {
          tries++;
          status.textContent = `${U.fa(taps)} ضربه کوبیدی، اما ${U.fa(strongN)} ضربهٔ قوی داشتیم! یک بار دیگر…`;
          narrate('هنوز کامل نشد! یک بار دیگر گوش کن و بشمار.', {});
          setTimeout(play, 1800);
        } else {
          const good = taps === strongN;
          U.sfx[good ? 'correct' : 'unlock']();
          host.appendChild(U.el(`<div class="feedback ${good ? 'good' : 'bad'}"><span class="f-emoji">🥁</span><div><b>${good ? pickPraise() : 'تمام شد!'}</b><br>${U.fa(strongN)} ضربهٔ قوی داشتیم؛ تو ${U.fa(taps)} بار کوبیدی. ریتم با تمرین در دلت می‌نشیند!</div></div>`));
          narrate(good ? pickPraise() : 'ریتم با تمرین در دلت می‌نشیند!', {});
          setTimeout(() => resolve({ hit: good, tries: Math.max(1, tries + 1) }), 2300);
        }
      };
      narrate('به ریتم گوش کن و به اندازهٔ ضربه‌های قوی بکوب!', {});
      setTimeout(play, 1400);
    });
  };

  /* ================= خریدِ هوشمند ================= */
  A.shopmarket = function (host, spec, ctx) {
    gear(host, spec, ctx);
    const r = U.rng(spec.seed), t = ctx.t;
    const goods = [['🍦 بستنی', '🍦'], ['🧸 اسباب‌بازی', '🧸'], ['📚 کتاب', '📚'], ['⚽ توپ', '⚽'], ['🎨 مدادرنگی', '🎨'], ['🔔 زنگ دوچرخه', '🔔']];
    const pick = U.shuffled(goods, spec.seed).slice(0, 3);
    const prices = pick.map((_, i) => 3 + (U.rng(spec.seed + i * 3)() * (5 + t * 12) | 0));
    const wallet = prices[0] + prices[1] + 1 + (r() * 6 | 0);
    const mode = spec.seed % 2;
    let q, ans, explain, options;
    if (mode === 0) {
      q = `در جیبِ تو ${U.fa(wallet)} سکهٔ طلا است. کدام را می‌توانی بخری؟`;
      ans = prices.findIndex((p) => p <= wallet);
      if (ans < 0) ans = 0;
      explain = `با ${U.fa(wallet)} سکه می‌توانی ${pick[ans][0]} را بخری (قیمت: ${U.fa(prices[ans])} سکه).`;
      options = pick.map((p, i) => `${p[0]} — ${U.fa(prices[i])} 🪙`);
    } else {
      const p = prices[0];
      q = `${pick[0][0]} ${U.fa(p)} سکه است و تو ${U.fa(wallet)} سکه داری. اگر آن را بخری، چند سکه اضافه می‌ماند؟`;
      ans = wallet - p;
      explain = `${U.fa(wallet)} − ${U.fa(p)} = ${U.fa(ans)} سکه باقی می‌ماند.`;
      options = U.shuffled([ans, ans + 2, Math.max(0, ans - 1), ans + 4], spec.seed).map((x) => U.fa(x));
    }
    return choices(host, {
      question: q, options, answer: mode === 0 ? ans : options.indexOf(U.fa(ans)),
      explain, emoji: '🛒', rate: .95,
    });
  };
})();

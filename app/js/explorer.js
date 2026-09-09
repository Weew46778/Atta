/* آتا — بخشِ کاوشگر: خانه، نقشهٔ اقلیم‌ها، جریانِ مرحله، رادار، بازیِ آزاد، چالشِ روزانه */
(function () {
  'use strict';
  const AT = (window.AT = window.AT || {});
  const U = AT.util;
  const E = (AT.explorer = {});
  const D = () => AT.data;

  /* ================= ترکیب‌گر spec فعالیت ================= */
  function bankFor(type, wk) {
    switch (type) {
      case 'quiziran': return AT.quiz.iran;
      case 'quizscience': return AT.quiz.science.concat(AT.quiz.nature);
      case 'quizethics': return AT.quiz.values;
      case 'quizart': return AT.quiz.art;
      case 'quizsafe': return AT.quiz.safety.concat(AT.quiz.health);
      case 'quizsmart': return AT.facts[wk] || AT.facts.science;
      default: return null;
    }
  }
  function buildSpec(type, lvl, k) {
    const w = D().WORLDS[lvl.world];
    const wk = w.key;
    const seed = (lvl.seed + k * 131) >>> 0;
    const salt = k + lvl.local;
    const spec = { type, seed, idx: 0, item: null, sub: null, hint: null };
    const pull = (bank) => {
      if (!bank || !bank.length) return null;
      spec.idx = D().bankIndex(bank.length, lvl.world, lvl.local, salt);
      return bank[spec.idx];
    };
    switch (type) {
      case 'story': {
        const bank = AT.stories.filter((s) => s.w === wk);
        spec.item = pull(bank.length ? bank : AT.stories);
        break;
      }
      case 'toon': {
        const bank = AT.toons.filter((s) => s.w === wk);
        spec.item = pull(bank.length ? bank : AT.toons);
        break;
      }
      case 'quizlang': spec.item = pull(AT.word.poems); break;
      case 'quizmath': case 'mathops': case 'pattern': case 'balance':
      case 'numbermemory': case 'memorycards': case 'simon': case 'focus':
      case 'count': case 'rhythm': case 'shopmarket':
        break; /* تولیدی */
      case 'riddle': spec.item = pull(AT.word.riddles); break;
      case 'wordbuild': spec.item = pull(AT.word.anagrams); break;
      case 'opposites': spec.sub = seed % 2 ? 'syn' : 'ant'; spec.idx = D().bankIndex(40, lvl.world, lvl.local, salt); break;
      case 'listen': spec.item = pull(AT.word.listens); break;
      case 'speak': { const d = pull(AT.word.speakDrills); spec.item = d ? d[0] : 'سلام! من قهرمانِ قصه‌ها هستم.'; break; }
      case 'proverb': spec.item = pull(AT.word.proverbs); break;
      case 'typo': spec.item = pull(AT.word.typos); break;
      case 'wordproblem': spec.item = pull(AT.ethics.wordProblems); break;
      case 'memorywords': spec.item = pull(AT.word.wordLists); break;
      case 'order': spec.item = pull(AT.ethics.orders); break;
      case 'sortcat': spec.item = pull(AT.ethics.sorts); break;
      case 'odd': spec.item = pull(AT.ethics.odds); break;
      case 'mapiran': spec.item = pull(AT.ethics.mapPoints); break;
      case 'scenario': spec.item = pull(AT.ethics.scenario); break;
      case 'calm': spec.item = pull(AT.ethics.calms); break;
      case 'emotion': spec.item = AT.activities.emotionBank[D().bankIndex(AT.activities.emotionBank.length, lvl.world, lvl.local, salt)]; break;
      case 'affirm': spec.item = pull(AT.ethics.affirms); break;
      case 'selfcheck': spec.item = pull(AT.ethics.selfchecks); break;
      case 'draw': spec.item = pull(AT.ethics.drawPrompts); break;
      default:
        if (type.startsWith('quiz')) spec.item = pull(bankFor(type, wk));
    }
    return spec;
  }
  E._buildSpec = buildSpec; /* برای تست */

  /* ================= وضعیت‌ها ================= */
  const S = () => U.load().explorer;
  function starOf(id) { return S().stars[id] || 0; }
  function worldUnlocked(w) { return w === 0 || S().done.includes(w * 20); }
  function treasureCount() { return Object.values(S().treasures).filter(Boolean).length; }

  /* ================= نشان‌ها ================= */
  function checkBadges() {
    const st = S();
    const has = (id) => st.badges.includes(id);
    const add = [];
    const cond = {
      first: st.done.length >= 1, ten: st.done.length >= 10, fifty: st.done.length >= 50,
      hundred: st.done.length >= 100, all: st.done.length >= 200,
      streak3: st.streak >= 3, streak7: st.streak >= 7, streak30: st.streak >= 30,
      coins300: st.coins >= 300, story10: countType('story') >= 10, riddle20: countType('riddle') >= 20,
      calm10: countType('calm') >= 10, speak10: st.speakConfident >= 10, draw5: st.art.length >= 5,
      perfect10: Object.values(st.stars).filter((x) => x === 3).length >= 10,
      daily5: Object.keys(st.dailyBest).length >= 5,
      kind5: (U.load().events.filter((e) => e.kind === 'kind-pick').length) >= 5,
      free10: Object.values(st.freePlay).reduce((a, b) => a + b, 0) >= 10,
    };
    D().WORLDS.forEach((w, i) => { cond['world' + i] = st.done.includes(i * 20 + 20); });
    for (const b of D().BADGES) if (cond[b.id] && !has(b.id)) { st.badges.push(b.id); add.push(b); }
    if (add.length) { U.save(); U.logEvent('badges', { ids: add.map((b) => b.id) }); }
    return add;
  }
  let typeCounts = null;
  function countType(type) {
    if (!typeCounts) {
      typeCounts = {};
      U.load().events.forEach((e) => { if (e.kind === 'act') typeCounts[e.data.type] = (typeCounts[e.data.type] || 0) + 1; });
    }
    return typeCounts[type] || 0;
  }

  /* ================= خانه ================= */
  E.home = function (root) {
    const st = S();
    const nl = D().nextLevel(U.load());
    const nlv = D().LEVELS[nl - 1];
    const nw = D().WORLDS[nlv.world];
    const dailyDone = !!st.dailyBest[U.today()];
    root.innerHTML = `
      <header class="topbar">
        <button class="btn round ghost" id="hm-profile" aria-label="پروفایل">${st.avatar}</button>
        <div style="flex:1;text-align:center">
          <b id="hm-name">${U.esc(st.name)}</b>
          <div class="pill-row" style="justify-content:center;margin-top:2px">
            <span class="chip">🪙 ${U.fa(st.coins)}</span>
            <span class="chip">💎 ${U.fa(st.gems)}</span>
            <span class="chip">🔥 ${U.fa(st.streak)}</span>
          </div>
        </div>
        <button class="btn round ghost" id="hm-radar" aria-label="رادار">📊</button>
      </header>
      ${AT.ui.mascotSay('explorer', greet(st))}
      <div class="card glass-strong" id="hm-continue" style="cursor:pointer;background:linear-gradient(135deg,${nw.color}33,${nw.color2}44)">
        <div style="display:flex;align-items:center;gap:12px">
          <span style="font-size:2.6em" class="floaty">${nw.emoji}</span>
          <div style="flex:1">
            <small class="dim">ادامهٔ سفر — ${U.esc(nw.name)}</small>
            <div style="font-weight:900;font-size:1.15em">مرحلهٔ ${U.fa(nl)} ${nlv.boss ? '👑 آزمونِ بزرگ' : nlv.festival ? '🎉 جشنِ اقلیم' : ''}</div>
          </div>
          <span style="font-size:1.6em">▶️</span>
        </div>
      </div>
      <div style="display:grid;grid-template-columns:1fr 1fr;gap:10px;margin-top:10px">
        <button class="card tight ${dailyDone ? '' : 'glow'}" id="hm-daily" style="cursor:pointer;text-align:center">
          <div style="font-size:1.8em">${dailyDone ? '✅' : '📅'}</div>
          <b style="font-size:.92em">چالشِ روزانه</b>
          <div class="dim" style="font-size:.78em">${dailyDone ? 'امروز انجام شد!' : '۳ بازی + جایزهٔ ویژه'}</div>
        </button>
        <button class="card tight" id="hm-free" style="cursor:pointer;text-align:center">
          <div style="font-size:1.8em">🎮</div>
          <b style="font-size:.92em">بازیِ آزاد</b>
          <div class="dim" style="font-size:.78em">هر بازی که دوست داری</div>
        </button>
      </div>
      <h3 class="section-title">اقلیم‌های سفر</h3>
      <div class="worlds-grid" id="hm-worlds"></div>`;
    const grid = U.$('#hm-worlds', root);
    D().WORLDS.forEach((w, i) => {
      const unlocked = worldUnlocked(i);
      const doneInWorld = w ? 0 : 0;
      const doneN = st.done.filter((id) => id > i * 20 && id <= i * 20 + 20).length;
      const card = U.el(`<button class="world-card ${unlocked ? '' : 'locked'}" style="--wc:${w.color};--wc2:${w.color2}">
        <span class="wc-emoji">${unlocked ? w.emoji : '🔒'}</span>
        <b>${U.esc(w.name)}</b>
        <small class="dim">${U.esc(w.sub)}</small>
        <span class="wc-progress">${U.fa(doneN)}/${U.fa(20)}</span>
      </button>`);
      card.onclick = () => {
        if (!unlocked) { U.toast('اول اقلیمِ قبلی را کامل کن! 🔒'); U.sfx.wrong(); return; }
        E.world(root, i);
      };
      grid.appendChild(card);
    });
    U.$('#hm-continue', root).onclick = () => E.playLevel(root, nl);
    U.$('#hm-daily', root).onclick = () => E.daily(root);
    U.$('#hm-free', root).onclick = () => E.free(root);
    U.$('#hm-radar', root).onclick = () => E.radar(root);
    U.$('#hm-profile', root).onclick = () => E.profile(root);
  };
  function greet(st) {
    const h = new Date().getHours();
    const t = h < 5 ? 'شب‌زنده‌داریِ قشنگ!' : h < 12 ? 'صبح‌بخیر قهرمان!' : h < 17 ? 'ظهرِ شما بخیر!' : h < 21 ? 'عصر بخیر رفیق!' : 'شبِ شما بخیر!';
    const extra = st.streak >= 3 ? ` آفرین، ${U.fa(st.streak)} روز پیوسته اومدی! 🔥` : '';
    const next = st.done.length ? ' بریم ادامهٔ ماجراجویی؟' : ' امروز شروعِ سفرِ بزرگِ ماست! از شهرِ کلمه‌ها شروع می‌کنیم.';
    return t + extra + next;
  }

  /* ================= پروفایل ================= */
  E.profile = function (root) {
    const st = S();
    const names = ['کاوشگر', 'قهرمان', 'شاهین', 'رها', 'آسمان', 'نیما', 'سارا', 'امیر', 'درنا', 'ستاره'];
    AT.ui.openModal(`
      <h3 style="margin:0 0 10px">پروفایلِ کاوشگر</h3>
      <label style="display:block;font-weight:800;font-size:.9em;margin-bottom:4px">اسم تو چیست؟</label>
      <input id="pf-name" class="text-input" maxlength="14" value="${U.esc(st.name)}" placeholder="اسم قشنگت...">
      <label style="display:block;font-weight:800;font-size:.9em;margin:10px 0 4px">چهرهٔ تو</label>
      <div style="display:grid;grid-template-columns:repeat(6,1fr);gap:6px" id="pf-avatars"></div>
      <div class="stats-mini" style="margin-top:12px;display:grid;grid-template-columns:1fr 1fr;gap:6px;font-size:.85em">
        <span class="chip">📖 ${U.fa(st.done.length)} مرحله</span>
        <span class="chip">🏆 ${U.fa(treasureCount())} گنج</span>
        <span class="chip">🎖️ ${U.fa(st.badges.length)} نشان</span>
        <span class="chip">⏱️ ${U.fa(st.minutes)} دقیقه بازی</span>
      </div>
      <button class="btn mint" id="pf-save" style="width:100%;margin-top:12px">ذخیره کن ✅</button>`);
    const av = ['🦅', '🐯', '🦊', '🐼', '🦉', '🐬', '🦄', '🐢', '🦁', '🐙', '🦋', '🐳'];
    const avGrid = U.$('#pf-avatars');
    let pick = st.avatar;
    av.forEach((a) => {
      const b = U.el(`<button class="opt emoji-opt ${a === pick ? 'correct' : ''}" style="padding:4px"><span class="opt-emoji">${a}</span></button>`);
      b.onclick = () => { pick = a; avGrid.querySelectorAll('.opt').forEach((x) => x.classList.remove('correct')); b.classList.add('correct'); };
      avGrid.appendChild(b);
    });
    U.$('#pf-save').onclick = () => {
      const nm = U.$('#pf-name').value.trim() || 'کاوشگر';
      st.name = nm.slice(0, 14); st.avatar = pick; U.save();
      U.logEvent('profile', {});
      AT.ui.closeModal();
      U.toast('ذخیره شد! ' + pick);
      E.home(root);
    };
  };

  /* ================= نقشهٔ یک اقلیم ================= */
  E.world = function (root, wi) {
    const w = D().WORLDS[wi];
    const st = S();
    let nodes = '';
    for (let i = 1; i <= 20; i++) {
      const id = wi * 20 + i;
      const lvl = D().LEVELS[id - 1];
      const unlocked = D().isUnlocked(U.load(), id);
      const done = st.done.includes(id);
      const stars = starOf(id);
      const col = (i - 1) % 4;
      const row = Math.floor((i - 1) / 4);
      const flip = row % 2 === 1;
      const order = flip ? 3 - col : col;
      nodes += `<button class="map-node ${done ? 'done' : ''} ${unlocked && !done ? 'next' : ''} ${unlocked ? '' : 'locked'}"
        data-id="${id}" style="grid-column:${order + 1};grid-row:${row + 1};--wc:${w.color}">
        ${!unlocked ? '🔒' : lvl.festival ? '🎉' : lvl.boss ? '👑' : U.fa(i)}
        ${done ? `<span class="node-stars">${'⭐'.repeat(stars) || '✅'}</span>` : ''}
      </button>`;
    }
    root.innerHTML = `
      <header class="topbar">
        <button class="btn round ghost" id="wd-back" aria-label="بازگشت">⟶</button>
        <div style="flex:1;text-align:center"><b>${w.emoji} ${U.esc(w.name)}</b><br><small class="dim">${U.esc(w.desc)}</small></div>
        <span style="width:56px"></span>
      </header>
      <div class="path-map" style="--wc:${w.color};--wc2:${w.color2}">${nodes}</div>
      <p class="dim" style="text-align:center;font-size:.8em;margin-top:6px">راهنما: ${U.esc(w.guide)} • 👙 آزمونِ بزرگ هر ۵ مرحله • 🎉 جشنِ پایانِ اقلیم</p>`;
    U.$('#wd-back', root).onclick = () => E.home(root);
    root.querySelectorAll('.map-node').forEach((n) => {
      n.onclick = () => {
        const id = +n.dataset.id;
        if (!D().isUnlocked(U.load(), id)) { U.toast('اول مرحلهٔ قبلی را تمام کن! 🔒'); U.sfx.wrong(); return; }
        E.playLevel(root, id);
      };
    });
    AT.voice.speak(w.name, {}).catch(() => {});
  };

  /* ================= جریانِ بازیِ مرحله ================= */
  E.playLevel = async function (root, id) {
    const lvl = D().LEVELS[id - 1];
    const w = D().WORLDS[lvl.world];
    const types = D().pickTypes(lvl, lvl.seed);
    const specs = types.map((t, k) => buildSpec(t, lvl, k));
    let act = 0, hits = 0, coinsEarned = 0;
    root.innerHTML = `
      <div class="level-screen">
        <header class="topbar">
          <button class="btn round ghost" id="lv-exit" aria-label="خروج">✕</button>
          <div style="flex:1;text-align:center">
            <b>${lvl.boss ? '👑 ' : lvl.festival ? '🎉 ' : ''}${U.esc(w.name)} — مرحلهٔ ${U.fa(lvl.local)}</b>
            <div class="progress" style="margin-top:4px"><div class="progress-fill" id="lv-prog" style="width:0%"></div></div>
          </div>
          <span class="chip" id="lv-coins">🪙 ۰</span>
        </header>
        <div id="act-host"></div>
      </div>`;
    const host = U.$('#act-host', root);
    let exited = false;
    U.$('#lv-exit', root).onclick = () => {
      exited = true;
      AT.ui.openModal(`
        <h3>از بازی بیرون می‌روی؟</h3>
        <p class="dim">اگر الان بیرون بروی، پیشرفتِ این مرحله ذخیره نمی‌شود.</p>
        <div style="display:flex;gap:8px">
          <button class="btn ghost" id="lx-stay" style="flex:1">ادامهٔ بازی</button>
          <button class="btn coral" id="lx-quit" style="flex:1">بیرون می‌روم</button>
        </div>`, { locked: true });
      U.$('#lx-stay').onclick = () => { exited = false; AT.ui.closeModal(); };
      U.$('#lx-quit').onclick = () => { AT.ui.closeModal(); AT.voice.stop(); E.world(root, lvl.world); };
    };
    narrateLevelIntro(lvl, w);
    for (const spec of specs) {
      if (exited) return;
      const engine = AT.activities[spec.type];
      if (!engine) continue;
      AT.explorer._current = spec; /* قلابِ دیباگ/تست */
      U.$('#lv-prog', root).style.width = `${Math.round((act / specs.length) * 100)}%`;
      let res = { hit: false, tries: 1 };
      try {
        host.innerHTML = '';
        res = await engine(host, spec, { t: lvl.t, timer: !!lvl.boss, world: lvl.world, level: lvl, seed: spec.seed });
      } catch (err) { console.warn('engine error', spec.type, err); }
      host.innerHTML = '';
      if (exited) return;
      act++;
      hits += res.hit ? 1 : 0;
      const meta = D().TYPE_META[spec.type] || { coins: 10, skills: [] };
      if (res.hit) {
        coinsEarned += meta.coins;
        (meta.skills || []).forEach((sk) => U.bumpSkill(sk, 1, 2));
      } else (meta.skills || []).forEach((sk) => U.bumpSkill(sk, .4, 1));
      U.logEvent('act', { type: spec.type, hit: res.hit, level: id });
      if (spec.type === 'scenario' && res.hit) U.logEvent('kind-pick', { level: id });
      U.$('#lv-coins', root).textContent = `🪙 ${U.fa(coinsEarned)}`;
    }
    if (exited) return;
    U.$('#lv-prog', root).style.width = '100%';
    await finishLevel(root, lvl, w, { acts: specs.length, hits, coinsEarned });
  };
  function narrateLevelIntro(lvl, w) {
    let intro = `${w.name}، مرحلهٔ ${lvl.local}.`;
    if (lvl.boss) intro = 'آزمونِ بزرگ! سرعت و دقت مهم است. آماده‌ای؟';
    if (lvl.festival) intro = 'جشنِ اقلیم! چهار بازیِ شاد و جایزهٔ ویژه در انتظار توست!';
    AT.voice.speak(intro, { rate: .98 }).catch(() => {});
  }

  async function finishLevel(root, lvl, w, r) {
    const st = S();
    const ratio = r.acts ? r.hits / r.acts : 0;
    const stars = ratio >= .85 ? 3 : ratio >= .55 ? 2 : 1;
    const first = !st.done.includes(lvl.id);
    const reward = D().levelReward(lvl);
    let coins = r.coinsEarned + (first ? reward : Math.round(reward * .3));
    const gems = lvl.boss && first ? 1 : lvl.festival && first ? 2 : 0;
    /* گنج: در پایانِ هر مرحلهٔ تازه یک گنج از گنجینهٔ اقلیم */
    let treasure = null;
    if (first) {
      const bank = AT.facts[w.key] || [];
      if (bank.length) {
        const key = w.key + ':' + lvl.local;
        if (!st.treasures[key]) {
          st.treasures[key] = bank[(lvl.local - 1) % bank.length];
          treasure = { emoji: w.emoji, name: 'دانستنیِ ' + w.name, fact: st.treasures[key] };
        }
      }
      st.done.push(lvl.id);
      st.minutes += Math.max(2, Math.round(r.acts * 1.5));
    }
    st.coins += coins; st.gems += gems;
    st.stars[lvl.id] = Math.max(starOf(lvl.id), stars);
    U.save();
    U.logEvent('level', { id: lvl.id, stars, coins });
    const badges = checkBadges();
    AT.ui.celebrate(root, {
      coins, starCount: stars, treasure, title: lvl.festival ? 'جشنِ اقلیم! 🎉' : 'مرحله تمام شد!',
      subtitle: `${U.esc(w.name)} • مرحلهٔ ${U.fa(lvl.local)} • ${U.fa(r.hits)} از ${U.fa(r.acts)} بازی درست`,
      badges,
    }).then(() => {
      const nextId = lvl.id < 200 ? lvl.id + 1 : null;
      showNextOffer(root, lvl, nextId);
    });
  }
  function showNextOffer(root, lvl, nextId) {
    const w = D().WORLDS[lvl.world];
    let html = `
      <div class="card glass-strong pop-in" style="text-align:center">
        <div style="font-size:2.4em" class="floaty">${w.emoji}</div>
        <b style="font-size:1.1em">${lvl.festival ? 'اقلیم فتح شد!' : 'آفرین قهرمان!'}</b>
        <p class="dim" style="font-size:.9em">${nextId ? 'مرحلهٔ بعدی آماده است!' : 'کل سفر را تمام کردی! تو افسانهٔ آتایی! 👑'}</p>
        <div style="display:flex;gap:8px;justify-content:center;flex-wrap:wrap;margin-top:8px">
          ${nextId ? '<button class="btn mint" id="no-next">مرحلهٔ بعد ▶️</button>' : ''}
          <button class="btn ghost" id="no-map">نقشهٔ اقلیم 🗺️</button>
          <button class="btn ghost" id="no-home">خانه 🏠</button>
        </div>
      </div>`;
    root.innerHTML = html;
    const nx = U.$('#no-next', root);
    if (nx) nx.onclick = () => E.playLevel(root, nextId);
    U.$('#no-map', root).onclick = () => E.world(root, lvl.world);
    U.$('#no-home', root).onclick = () => E.home(root);
  }

  /* ================= رادارِ مهارت و گنج‌ها ================= */
  E.radar = function (root) {
    const st = S();
    const skills = U.SKILLS;
    const keys = Object.keys(skills);
    const vals = keys.map((k) => Math.min(100, (st.skills[k] || 0)));
    root.innerHTML = `
      <header class="topbar">
        <button class="btn round ghost" id="rd-back" aria-label="بازگشت">⟶</button>
        <div style="flex:1;text-align:center"><b>📊 رادارِ قهرمانی</b></div>
        <span style="width:56px"></span>
      </header>
      <div class="radar-box">${radarSVG(keys, vals)}</div>
      <div style="display:grid;grid-template-columns:1fr 1fr;gap:6px;font-size:.82em;margin-top:6px">
        ${keys.map((k) => `<span class="chip" style="border-color:${skills[k].color}66">${skills[k].emoji} ${skills[k].name}: ${U.fa(Math.round(vals[keys.indexOf(k)]))}</span>`).join('')}
      </div>
      <h3 class="section-title">🎖️ نشان‌ها (${U.fa(st.badges.length)}/${U.fa(D().BADGES.length)})</h3>
      <div class="badge-grid" id="rd-badges"></div>
      <h3 class="section-title">🏆 گنجینه (${U.fa(treasureCount())} از ${U.fa(200)})</h3>
      <p class="dim" style="font-size:.82em;text-align:center">با تمام‌کردنِ هر مرحله، یک گنجِ دانستنی باز می‌شود!</p>
      <div class="treasure-grid" id="rd-treasures"></div>
      ${st.art.length ? `<h3 class="section-title">🖼️ نگارخانهٔ تو (${U.fa(st.art.length)})</h3><div class="art-grid" id="rd-art"></div>` : ''}`;
    const bg = U.$('#rd-badges', root);
    D().BADGES.forEach((b) => {
      const earned = st.badges.includes(b.id);
      bg.appendChild(U.el(`<div class="badge-cell ${earned ? 'earned' : ''}" title="${U.esc(b.desc)}">
        <span style="font-size:1.7em">${earned ? b.emoji : '🔒'}</span>
        <b style="font-size:.72em">${U.esc(b.name)}</b>
      </div>`));
    });
    const tg = U.$('#rd-treasures', root);
    const trs = Object.entries(st.treasures).filter(([, v]) => v);
    if (!trs.length) tg.appendChild(U.el('<p class="dim" style="text-align:center">هنوز گنجی باز نشده؛ اولین مرحله را برو! 🏴‍☠️</p>'));
    else trs.slice(-12).reverse().forEach(([k, v]) => {
      const wk = k.split(':')[0];
      const w = D().WORLDS.find((x) => x.key === wk) || D().WORLDS[0];
      tg.appendChild(U.el(`<div class="treasure-cell" style="--wc:${w.color}"><span>${w.emoji}</span><small>${U.esc(String(v).slice(0, 90))}${String(v).length > 90 ? '…' : ''}</small></div>`));
    });
    if (st.art.length) {
      const ag = U.$('#rd-art', root);
      st.art.slice().reverse().forEach((a) => {
        ag.appendChild(U.el(`<div class="art-cell"><img src="${a.d}" alt="نقاشی" loading="lazy"><small class="dim">${U.esc(String(a.p || '').slice(0, 40))}</small></div>`));
      });
    }
    U.$('#rd-back', root).onclick = () => E.home(root);
  };
  function radarSVG(keys, vals) {
    const n = keys.length, cx = 100, cy = 100, R = 78;
    const pt = (i, r) => {
      const a = -Math.PI / 2 + (i * 2 * Math.PI) / n;
      return [cx + r * Math.cos(a), cy + r * Math.sin(a)];
    };
    let rings = '';
    for (let ring = 1; ring <= 4; ring++) {
      const r = (R * ring) / 4;
      rings += `<polygon points="${keys.map((_, i) => pt(i, r).map((x) => x.toFixed(1)).join(',')).join(' ')}" fill="none" stroke="rgba(255,255,255,.12)" stroke-width="1"/>`;
    }
    const poly = keys.map((_, i) => pt(i, (R * Math.max(6, vals[i])) / 100).map((x) => x.toFixed(1)).join(',')).join(' ');
    const labels = keys.map((k, i) => {
      const [x, y] = pt(i, R + 11);
      return `<text x="${x.toFixed(1)}" y="${(y + 3).toFixed(1)}" text-anchor="middle" font-size="11">${U.SKILLS[k].emoji}</text>`;
    }).join('');
    return `<svg viewBox="0 0 200 200">
      ${rings}
      ${keys.map((_, i) => { const [x, y] = pt(i, R); return `<line x1="${cx}" y1="${cy}" x2="${x.toFixed(1)}" y2="${y.toFixed(1)}" stroke="rgba(255,255,255,.1)"/>`; }).join('')}
      <polygon points="${poly}" fill="rgba(255,209,102,.3)" stroke="#ffd166" stroke-width="2.5" stroke-linejoin="round"/>
      ${keys.map((_, i) => { const [x, y] = pt(i, (R * Math.max(6, vals[i])) / 100); return `<circle cx="${x.toFixed(1)}" cy="${y.toFixed(1)}" r="3.4" fill="#ffd166"/>`; }).join('')}
      ${labels}
    </svg>`;
  }

  /* ================= بازیِ آزاد ================= */
  const FREE_PICK = ['riddle', 'memorycards', 'simon', 'wordbuild', 'mathops', 'pattern', 'count', 'balance', 'draw', 'rhythm', 'shopmarket', 'numbermemory', 'focus', 'mapiran', 'proverb', 'quizscience', 'quiziran', 'quizsafe', 'quizart', 'odd', 'sortcat', 'order', 'listen', 'typo', 'wordproblem', 'memorywords', 'quizmath', 'quizsmart', 'scenario', 'emotion'];
  E.free = function (root) {
    root.innerHTML = `
      <header class="topbar">
        <button class="btn round ghost" id="fr-back" aria-label="بازگشت">⟶</button>
        <div style="flex:1;text-align:center"><b>🎮 بازیِ آزاد</b><br><small class="dim">هر بازی که دوست داری، هر چند بار!</small></div>
        <span style="width:56px"></span>
      </header>
      <div class="free-grid" id="fr-grid"></div>`;
    const grid = U.$('#fr-grid', root);
    FREE_PICK.forEach((type) => {
      const meta = D().TYPE_META[type] || { title: type, emoji: '🎮' };
      const n = S().freePlay[type] || 0;
      const b = U.el(`<button class="card tight free-cell" style="cursor:pointer;text-align:center">
        <span style="font-size:1.9em">${meta.emoji}</span>
        <b style="font-size:.85em;display:block">${U.esc(meta.title)}</b>
        ${n ? `<small class="dim" style="font-size:.72em">${U.fa(n)} بار بازی شده</small>` : '<small class="dim" style="font-size:.72em">تازه!</small>'}
      </button>`);
      b.onclick = () => runFree(root, type);
      grid.appendChild(b);
    });
    U.$('#fr-back', root).onclick = () => E.home(root);
  };
  async function runFree(root, type) {
    const st = S();
    const seed = (Date.now() & 0xffff) >>> 0;
    const fakeLevel = { world: 0, local: (seed % 20) + 1, t: .3 + (seed % 50) / 100, boss: false, festival: false, seed, id: 0, acts: 1 };
    const spec = buildSpec(type, fakeLevel, 0);
    root.innerHTML = `
      <div class="level-screen">
        <header class="topbar">
          <button class="btn round ghost" id="fv-back" aria-label="بازگشت">⟶</button>
          <div style="flex:1;text-align:center"><b>🎮 ${U.esc((D().TYPE_META[type] || {}).title || '')}</b></div>
          <span class="chip">🪙 ${U.fa(st.coins)}</span>
        </header>
        <div id="act-host"></div>
      </div>`;
    U.$('#fv-back', root).onclick = () => { AT.voice.stop(); E.free(root); };
    const host = U.$('#act-host', root);
    let res = { hit: false, tries: 1 };
    try { res = await AT.activities[type](host, spec, { t: fakeLevel.t, timer: false, world: 0, seed }); }
    catch (err) { console.warn(err); }
    st.freePlay[type] = (st.freePlay[type] || 0) + 1;
    const meta = D().TYPE_META[type] || { coins: 10, skills: [] };
    if (res.hit) {
      st.coins += Math.ceil(meta.coins / 2);
      (meta.skills || []).forEach((sk) => U.bumpSkill(sk, .5, 1));
    }
    U.save();
    U.logEvent('act', { type, hit: res.hit, free: true });
    const badges = checkBadges();
    await AT.ui.celebrate(root, {
      coins: res.hit ? Math.ceil(meta.coins / 2) : 2, starCount: res.hit ? 3 : 1, treasure: null,
      title: res.hit ? 'آفرین!' : 'دوباره امتحان می‌کنی؟', subtitle: 'بازیِ آزاد', badges,
    });
    E.free(root);
  }

  /* ================= چالشِ روزانه ================= */
  E.daily = async function (root) {
    const st = S();
    const today = U.today();
    if (st.dailyBest[today]) {
      root.innerHTML = `
        <header class="topbar"><button class="btn round ghost" id="dl-back">⟶</button><div style="flex:1;text-align:center"><b>📅 چالشِ روزانه</b></div><span style="width:56px"></span></header>
        <div class="card glass-strong pop-in" style="text-align:center">
          <div style="font-size:2.6em">✅</div>
          <b>چالشِ امروز انجام شد!</b>
          <p class="dim" style="font-size:.9em">امشب خوب بخواب؛ فردا چالشِ تازه می‌آید! ${U.faWeekday(new Date())} هم یادت باشد.</p>
          <button class="btn mint" id="dl-ok" style="margin-top:8px">آفرین!</button>
        </div>`;
      U.$('#dl-back', root).onclick = U.$('#dl-ok', root).onclick = () => E.home(root);
      return;
    }
    /* سه فعالیتِ ثابت برای امروز: seed از تاریخ */
    const d = new Date();
    const dseed = (+`${d.getFullYear()}${String(d.getMonth() + 1).padStart(2, '0')}${String(d.getDate()).padStart(2, '0')}` % 100000) + 3000;
    const pools = ['riddle', 'mathops', 'memorycards', 'quizscience', 'wordbuild', 'pattern', 'proverb', 'quiziran', 'simon', 'count', 'odd', 'quizsafe'];
    const types = [0, 1, 2].map((i) => pools[(dseed + i * 7) % pools.length]);
    let idx = 0, hits = 0, coins = 0;
    root.innerHTML = `
      <header class="topbar">
        <button class="btn round ghost" id="dl-exit" aria-label="خروج">✕</button>
        <div style="flex:1;text-align:center"><b>📅 چالشِ روزانه</b>
          <div class="progress" style="margin-top:4px"><div class="progress-fill" id="dl-prog" style="width:0%"></div></div>
        </div>
        <span class="chip">⭐ ${U.fa(hits)}/${U.fa(3)}</span>
      </header>
      <div id="act-host"></div>`;
    U.$('#dl-exit', root).onclick = () => { AT.voice.stop(); E.home(root); };
    AT.voice.speak('چالشِ روزانه! سه بازیِ امروز؛ همه را درست بزنی، جایزهٔ ویژه داری!', {}).catch(() => {});
    const host = U.$('#act-host', root);
    for (const type of types) {
      const fakeLevel = { world: (dseed % 10), local: (dseed % 18) + 1, t: .25 + (dseed % 45) / 100, boss: false, festival: false, seed: dseed, id: 0, acts: 3 };
      const spec = buildSpec(type, fakeLevel, idx);
      let res = { hit: false, tries: 1 };
      try { host.innerHTML = ''; res = await AT.activities[type](host, spec, { t: fakeLevel.t, timer: false, world: fakeLevel.world, seed: dseed }); }
      catch (err) { console.warn(err); }
      host.innerHTML = '';
      idx++;
      if (res.hit) { hits++; coins += (D().TYPE_META[type] || { coins: 10 }).coins; }
      U.$('#dl-prog', root).style.width = `${(idx / 3) * 100}%`;
      U.logEvent('act', { type, hit: res.hit, daily: true });
    }
    const perfect = hits === 3;
    const bonus = perfect ? 40 : hits === 2 ? 20 : 8;
    coins += bonus;
    st.dailyBest[today] = hits;
    st.coins += coins;
    st.days = st.days || [];
    if (!st.days.includes(today)) st.days.push(today);
    U.save();
    U.logEvent('daily', { hits, coins });
    const badges = checkBadges();
    await AT.ui.celebrate(root, {
      coins, starCount: hits, treasure: null,
      title: perfect ? 'چالشِ کامل! 🌟' : 'چالشِ روزانه تمام شد!',
      subtitle: `${U.fa(hits)} از ${U.fa(3)} بازی درست ${perfect ? '+ جایزهٔ ویژه!' : ''}`,
      badges,
    });
    E.home(root);
  };
})();

// آتا — اپ اصلی: صفحه‌ها، جادوگر ساخت، پنل مدیریت، ورود به بازی، حالت بازی با اورلای و دستیار صوتی
import './style.css';
import {
  APP_NAME, HOSTS, EDITIONS, VERSIONS, GAMEMODES, DIFFICULTIES, RAM_OPTIONS, REGIONS,
  OVERLAY_TABS, QUICK_ACTIONS, WORLD_ACTIONS, PLAYER_ACTIONS, ITEM_GROUPS, POWER_ACTIONS,
  MOB_GROUPS, MOB_ACTIONS, PROPERTY_GROUPS, SUPPORT_TOPICS, faNum, faDate, faTime,
} from './data.js';
import {
  loadState, saveState, buildServer, deployStages, bootConsoleLog, randomConsoleHint,
  executeCommand, createBackup, matchVoiceCommand, normalizeFa,
} from './engine.js';
import { createVoice, createRecognizer, VOICE_CLIPS, NARRATOR } from './voice.js';
import { createOverlay } from './overlay.js';
import { createGameWorld, applyEvents, drawWorld } from './game.js';
import { createPterodactylClient, PTERO_KEY_HELP } from './real/pterodactyl.js';
import { createVpsAgentClient, agentInstallOneLiner } from './real/vps-agent.js';
import { normalizePanelUrl } from './real/transport.js';

const $ = (s, el = document) => el.querySelector(s);
const $$ = (s, el = document) => [...el.querySelectorAll(s)];
const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

// ———————————————————————————————— وضعیت ————————————————————————————————
let state = loadState(typeof localStorage !== 'undefined' ? localStorage : null);
const save = () => { if (!saveState(typeof localStorage !== 'undefined' ? localStorage : null, state)) toast('ذخیره ممکن نشد؛ حافظهٔ دستگاه پر است.'); };

let page = 'home';
let selectedHostId = null;
let wizardStep = 0;
let draft = {};
let currentServerId = null;
let panelTab = 'overview';
let world = null;             // دنیای تک‌نفره / شبیه‌ساز
let overlay = null;
let gameRaf = 0, hintTimer = 0;
let voice = null, recognizer = null, listeningLoop = false;
let deployTimers = [];
let pteroClient = null, vpsClient = null;   // کلاینت‌های اتصال واقعی
let realConsoleHandle = null, realPollTimer = 0;

const PAGE_META = {
  home: { title: 'خانه', clip: 'home', help: 'این خانهٔ آتاست. سرور بساز، سرورهایت را مدیریت کن یا وارد بازی شو.' },
  hosts: { title: 'میزبان‌ها', clip: 'hosts', help: 'یازده میزبان سرور ماینکرافت. یکی را انتخاب کن تا ساخت شروع شود.' },
  wizard: { title: 'ساخت سرور', clip: 'wizard', help: 'قدم به قدم پیش می‌رویم؛ هر صفحه فقط یک سؤال است.' },
  servers: { title: 'سرورهای من', clip: 'servers', help: 'فهرست سرورهای تو. برای پنل مدیریت روی هر سرور بزن.' },
  panel: { title: 'پنل مدیریت', clip: 'panel', help: 'پنل کامل مدیریت سرور: کنسول، بازیکن‌ها، تنظیمات و بکاپ.' },
  connect: { title: 'ورود به بازی', clip: 'connect', help: 'اطلاعات اتصال منتقل شد؛ ماینکرافت را باز کن و وصل شو.' },
  real: { title: 'اتصال واقعی', clip: null, help: 'اینجا به میزبان واقعی وصل می‌شوی: کلید پنل پتروداکتیل یا عامل وی‌پی‌اس را وارد کن تا سرور واقعی‌ات را مدیریت کنم.' },
  game: { title: 'حالت بازی', clip: 'overlay', help: 'از لبهٔ چپ بکش تا اورلای باز شود؛ بقیهٔ صفحه برای بازی آزاد است.' },
  support: { title: 'پشتیبانی', clip: 'support', help: 'مرکز کمک آتا: سؤال‌های پرتکرار و گفتگو با پشتیبان.' },
  settings: { title: 'تنظیمات', clip: 'settings', help: 'تنظیم گفتار، کنترل صوتی و کلمهٔ بیداری.' },
};

// ———————————————————————————————— ابزارهای رابط ————————————————————————————————
function toast(text) {
  const el = $('#toast');
  el.textContent = text;
  el.classList.add('visible');
  clearTimeout(toast.timer);
  toast.timer = setTimeout(() => el.classList.remove('visible'), 3800);
}

function caption(text) {
  const el = $('#assistant-caption');
  if (el) { el.textContent = text; el.classList.toggle('active', !!text); }
}

function speakClip(id) {
  if (!voice) return;
  if (!state.settings.narration) { caption(VOICE_CLIPS[id]?.text || ''); return; }
  voice.speak(id, { base: './' });
}

function say(text) {
  if (!voice) return;
  if (!state.settings.narration) { caption(text); return; }
  if (VOICE_CLIPS[text]) speakClip(text);
  else voice.speakText(text);
}

function go(next, opts = {}) {
  stopGameLoops();
  stopRealWatch();
  page = next;
  if (typeof location !== 'undefined') history.replaceState(null, '', `#${next}`);
  if (opts.keep !== true) stopListeningOnce();
  render();
}

function stopRealWatch() {
  if (realConsoleHandle) { try { realConsoleHandle.close(); } catch { /* noop */ } realConsoleHandle = null; }
  if (realPollTimer) { clearInterval(realPollTimer); realPollTimer = 0; }
}

// ———————————————————————————————— رندر صفحه‌ها ————————————————————————————————
function shell(content) {
  const meta = PAGE_META[page] || PAGE_META.home;
  const micOn = state.settings.voiceControl;
  return `
  <header class="top">
    <div class="brand"><span class="brand-mark">⛏️</span><div><strong>${APP_NAME}</strong><small>${meta.title}</small></div></div>
    <div class="top-actions">
      <button class="chip ${state.settings.narration ? 'on' : ''}" data-action="toggle-narration" aria-label="گفتار راهنما">${state.settings.narration ? '🔊 گفتار روشن' : '🔇 گفتار خاموش'}</button>
      <button class="chip ${micOn ? 'on' : ''}" data-action="toggle-voice-control" aria-label="کنترل صوتی">${micOn ? '🎤 صوتی روشن' : '🎤 صوتی خاموش'}</button>
    </div>
  </header>
  <main class="screen" data-page="${page}">${content}</main>
  <footer class="assistant">
    <button class="assist-btn" data-action="speak-help" aria-label="راهنمای صوتی این صفحه">🔊<small>راهنما</small></button>
    <div class="assist-caption" id="assistant-caption" aria-live="polite">دستیار ${NARRATOR.name} آماده است…</div>
    <button class="assist-btn mic ${listeningLoop ? 'listening' : ''}" data-action="mic" aria-label="صحبت با آتا">🎤<small>${listeningLoop ? 'می‌شنوم…' : 'بگو'}</small></button>
  </footer>`;
}

function render() {
  const view = {
    home: homePage, hosts: hostsPage, wizard: wizardPage, servers: serversPage,
    panel: panelPage, connect: connectPage, real: realPage, game: () => '', support: supportPage, settings: settingsPage,
  }[page];
  $('#app').innerHTML = shell(view ? view() : '');
  if (page === 'game') { startGamePage(); }
  if (page === 'real') { refreshRealLive(); }
  if (page === 'panel' && String(currentServerId).startsWith('real:')) {
    if (panelTab === 'console') startRealConsoleWatch();
    if (panelTab === 'backups') loadRealBackups();
  }
  const meta = PAGE_META[page];
  if (meta && state.settings.autoplay && state.settings.narration) {
    if (meta.clip) speakClip(meta.clip); else say(meta.help);
  } else if (meta) caption(meta.help);
}

function handleAction(action, data, el) {
  if (action.startsWith('wiz-')) { wizardAction(action, data); return; }
  switch (action) {
    case 'go':
      if (data.host) { selectedHostId = data.host; draft = { slots: 10, ram: 4 }; wizardStep = 0; }
      go(data.to);
      break;
    case 'speak-help': speakClip(PAGE_META[page].clip); break;
    case 'toggle-narration':
      state.settings.narration = !state.settings.narration;
      if (!state.settings.narration) voice?.stop();
      save(); render(); break;
    case 'toggle-voice-control':
      state.settings.voiceControl = !state.settings.voiceControl;
      save(); render();
      toast(state.settings.voiceControl ? 'کنترل صوتی روشن شد؛ کلمهٔ بیداری: «آتا»' : 'کنترل صوتی خاموش شد.');
      break;
    case 'toggle-autoplay': state.settings.autoplay = !state.settings.autoplay; save(); render(); break;
    case 'test-voice': speakClip('home'); break;
    case 'mic': micOnce(); break;
    case 'copy': copyText(data.value, data.label); break;
    case 'launch': launchGame(data.target); break;
    case 'open-panel': currentServerId = data.id; panelTab = 'overview'; go('panel'); break;
    case 'panel-tab': panelTab = data.tab; render(); break;
    case 'power': togglePower(); break;
    case 'restart': restartServer(); break;
    case 'go-connect': go('connect'); break;
    case 'srv-op': srvCmd(`/op ${data.who}`); break;
    case 'srv-kick': srvCmd(`/kick ${data.who}`); break;
    case 'srv-ban': srvCmd(`/ban ${data.who}`); break;
    case 'srv-pardon': srvCmd(`/pardon ${data.who}`); break;
    case 'srv-whitelist': srvCmd(`/whitelist add ${data.who}`); break;
    case 'prop-bool': togglePropBool(data.key); break;
    case 'backup-now': backupNow(); break;
    case 'backup-restore': backupRestore(data.id); break;
    case 'support-read': say(data.text); break;
    case 'reset-all':
      if (confirm('همهٔ سرورها و داده‌ها پاک شوند؟')) { localStorage.removeItem('atta-console-v1'); state = loadState(localStorage); currentServerId = null; go('home'); }
      break;
    case 'srv-power': togglePower(); break;
    case 'srv-restart': restartServer(); break;
    case 'srv-backup': backupNow(); break;
    case 'srv-whitelist-on': runCommand('/whitelist on'); break;
    case 'srv-whitelist-off': runCommand('/whitelist off'); break;
    case 'srv-say': runCommand('/say سرور تا لحظاتی دیگر ری‌استارت می‌شود'); break;
    case 'srv-gc': runCommand('/gc'); break;
    case 'srv-save': runCommand('/save-all'); break;
    case 'support-topic': {
      const t = SUPPORT_TOPICS.find(x => x.id === data.payload);
      if (t && world) { world.chat = [...world.chat.slice(-5), { who: 'پشتیبان آتا', text: t.a }]; renderChat(); say(t.a); }
      break;
    }
    // ————— اتصال واقعی —————
    case 'real-open': currentServerId = `real:ptero:${data.id}`; panelTab = 'overview'; go('panel'); break;
    case 'real-open-vps': currentServerId = 'real:vps'; panelTab = 'overview'; go('panel'); break;
    case 'real-select': setActiveReal('ptero', data.id); break;
    case 'real-select-vps': setActiveReal('vps'); break;
    case 'real-power': realPower(data.signal); break;
    case 'real-cmd': realCmdDirect(data.cmd); break;
    case 'real-backup': realBackupNow(); break;
    case 'ptero-disconnect': state.real.ptero = null; state.real.activeReal = null; pteroClient = null; save(); render(); toast('اتصال پنل قطع شد.'); break;
    case 'vps-disconnect': state.real.vps = null; if (state.real.activeReal?.kind === 'vps') state.real.activeReal = null; vpsClient = null; save(); render(); toast('اتصال عامل قطع شد.'); break;
    case 'vps-create': vpsCreateReal(); break;
    case 'native-overlay': startNativeOverlay(); break;
    case 'launch-web': if (el?.tagName === 'BUTTON') window.open(data.url, '_blank', 'noopener'); break;
  }
}

async function realCmdDirect(cmd) {
  const target = state.real.activeReal;
  const send = async () => {
    try {
      if (String(currentServerId).startsWith('real:ptero:')) await ptero()?.sendCommand(currentServerId.slice('real:ptero:'.length), cmd);
      else if (String(currentServerId).startsWith('real:vps')) await vps()?.sendCommand(cmd);
      else if (target?.kind === 'ptero') await ptero()?.sendCommand(target.id, cmd);
      else if (target?.kind === 'vps') await vps()?.sendCommand(cmd);
      else throw new Error('اول یک سرور واقعی را هدف اورلای کن');
      toast(`به سرور واقعی ارسال شد ✅ ${cmd}`);
      say('دستور به سرور واقعی ارسال شد.');
    } catch (e) { toast(`نشد: ${e.message}`); }
  };
  await send();
}

async function realBackupNow() {
  if (!String(currentServerId).startsWith('real:ptero:') || !ptero()) { toast('بکاپ واقعی فقط برای سرورهای پنل میزبان است.'); return; }
  try {
    toast('در حال ساخت بکاپ واقعی در پنل میزبان…');
    await ptero().createBackup(currentServerId.slice('real:ptero:'.length));
    say('بکاپ واقعی در پنل میزبان ساخته شد.');
    setTimeout(loadRealBackups, 2500);
  } catch (e) { toast(`بکاپ نشد: ${e.message}`); }
}

function startNativeOverlay() {
  if (typeof AndroidBridge === 'undefined' || !AndroidBridge.startOverlay) {
    toast('اورلای واقعی روی بازی فقط در اپ اندروید فعال است؛ اینجا شبیه‌ساز را بزن.');
    return;
  }
  if (!state.real.activeReal) toast('نکته: برای تأثیر واقعی، اول از فهرست سرورها «هدف اورلای» را انتخاب کن.');
  AndroidBridge.startOverlay();
  say('اورلای اپراتور باز شد. داخل بازی، از لبهٔ چپ بکش.');
}

function copyText(value, label = '') {
  try { navigator.clipboard?.writeText(value); toast(`${label || 'متن'} کپی شد 📋`); }
  catch { toast('کپی خودکار نشد؛ نشانی را دستی انتخاب کن.'); }
}

// ———————————————————————————————— خانه ————————————————————————————————
function homePage() {
  const serversCount = state.servers.length;
  return `
  <section class="hero">
    <div class="hero-art" aria-hidden="true">🏰</div>
    <div>
      <h1>سلام ${esc(state.profile.operator)}! 👋</h1>
      <p>سرور ماینکرافت بساز، مدیریت کن و داخل بازی با اورلای آتا کنترلش کن — همه با صدای ${NARRATOR.name}.</p>
    </div>
  </section>
  <nav class="home-grid">
    <button class="tile big accent" data-action="go" data-to="real">🔌<div><strong>اتصال به میزبان واقعی</strong><small>پنل پتروداکتیل یا وی‌پی‌اس — مدیریت و اورلای واقعی</small></div></button>
    <button class="tile big" data-action="go" data-to="hosts">🏗️<div><strong>ساخت سرور جدید</strong><small>با ۱۱ میزبان، قدم‌به‌قدم</small></div></button>
    <button class="tile" data-action="go" data-to="servers">🗂️<div><strong>سرورهای من</strong><small>${faNum(serversCount)} سرور · پنل کامل مدیریت</small></div></button>
    <button class="tile" data-action="go" data-to="game">🎮<div><strong>حالت بازی و اورلای</strong><small>اورلای اپراتور داخل بازی</small></div></button>
    <button class="tile" data-action="go" data-to="support">🆘<div><strong>پشتیبانی</strong><small>راهنما و کمک فوری</small></div></button>
    <button class="tile" data-action="go" data-to="settings">⚙️<div><strong>تنظیمات و صدا</strong><small>گفتار ${NARRATOR.name} و کنترل صوتی</small></div></button>
  </nav>`;
}

// ———————————————————————————————— میزبان‌ها ————————————————————————————————
function hostsPage() {
  return `
  <div class="page-head"><h2>یک میزبان انتخاب کن</h2><p>همهٔ مراحل بعدی را خودم انجام می‌دهم؛ فقط انتخاب کن.</p></div>
  <div class="host-grid">
    ${HOSTS.map(h => `
      <button class="host-card ${h.free ? 'free' : ''}" data-action="go" data-to="wizard" data-host="${h.id}">
        <span class="host-badge">${h.badge}</span>
        <strong>${h.name}</strong><small class="latin">${h.latin}</small>
        <p>${h.tagline}</p>
        <div class="host-meta">
          <span>💰 ${h.price}</span><span>🧠 رم: ${h.ram.map(r => faNum(r)).join('، ')}</span>
          <span>📶 پینگ: ${h.ping}</span><span>🌍 ${h.locations.join('، ')}</span>
        </div>
        <div class="host-tags">${h.bedrock ? '<i>بدراک ✓</i>' : ''}${h.java ? '<i>جاوا ✓</i>' : ''}${h.crossplay ? '<i>کراس‌پلی ✓</i>' : ''}<i>${h.panel}</i></div>
      </button>`).join('')}
  </div>`;
}

// ———————————————————————————————— جادوگر ساخت ————————————————————————————————
const WIZARD_STEPS = ['name', 'edition', 'version', 'gamemode', 'difficulty', 'slots', 'ram', 'region', 'review', 'deploy'];

function wizardPage() {
  const step = WIZARD_STEPS[wizardStep];
  const host = HOSTS.find(h => h.id === selectedHostId);
  const progress = `<div class="wiz-progress" aria-label="پیشرفت ساخت">${WIZARD_STEPS.map((s, i) => `<i class="${i < wizardStep ? 'done' : ''} ${i === wizardStep ? 'now' : ''}"></i>`).join('')}</div>`;
  const back = wizardStep > 0 && step !== 'deploy' ? `<button class="f-btn ghost" data-action="wiz-back">→ قبلی</button>` : '';
  const nav = (next, label = 'بعدی', disabled = false) => `<div class="wiz-nav">${back}<button class="f-btn primary" data-action="${next}" ${disabled ? 'disabled' : ''}>${label} ←</button></div>`;

  if (step === 'name') return progress + `
    <div class="wiz-step">
      <h2>۱) اسم سرورت چیست؟</h2><p>این اسم در فهرست بازیکن‌ها دیده می‌شود.</p>
      <input id="wiz-name" class="big-input" dir="auto" maxlength="24" placeholder="مثلاً: سرور دوستان" value="${esc(draft.name || '')}" autofocus>
      ${nav('wiz-next', 'بعدی')}
    </div>`;

  if (step === 'edition') return progress + optionsStep('۲) کدام نسخهٔ بازی؟', EDITIONS.map(e => ({ id: e.id, icon: e.icon, name: e.name, desc: e.desc })), draft.edition, 'wiz-edition');
  if (step === 'version') return progress + optionsStep('۳) کدام نسخهٔ سرور؟', VERSIONS.map(v => ({ id: v.id, icon: '🧩', name: v.name, desc: v.latest ? 'پیشنهاد آتا' : '' })), draft.version, 'wiz-version');
  if (step === 'gamemode') return progress + optionsStep('۴) حالت پیش‌فرض بازی؟', GAMEMODES.map(g => ({ id: g.id, icon: g.icon, name: g.name, desc: g.desc })), draft.gamemode, 'wiz-gamemode');
  if (step === 'difficulty') return progress + optionsStep('۵) سختی بازی؟', DIFFICULTIES.map(d => ({ id: d.id, icon: d.icon, name: d.name, desc: d.desc })), draft.difficulty, 'wiz-difficulty');

  if (step === 'slots') return progress + `
    <div class="wiz-step">
      <h2>۶) چند نفر هم‌زمان بازی کنند؟</h2><p>پیشنهاد آتا برای شروع: ۱۰ نفر</p>
      <div class="stepper">
        <button class="step-btn" data-action="wiz-slots" data-delta="-1">−</button>
        <span class="step-value" id="wiz-slots">${faNum(draft.slots || 10)}</span>
        <button class="step-btn" data-action="wiz-slots" data-delta="1">+</button>
      </div>
      ${nav('wiz-next')}
    </div>`;

  if (step === 'ram') return progress + optionsStep('۷) چقدر رم؟', RAM_OPTIONS
    .filter(r => !host || host.ram.includes(r))
    .map(r => ({ id: String(r), icon: '🧠', name: `${faNum(r)} گیگابایت`, desc: r <= 2 ? 'تا ۵ بازیکن' : r <= 4 ? 'تا ۱۲ بازیکن' : r <= 8 ? 'تا ۳۰ بازیکن' : 'سرور بزرگ و مادپک' })), String(draft.ram || ''), 'wiz-ram');

  if (step === 'region') return progress + optionsStep('۸) دیتاسنتر کجا باشد؟', REGIONS.map(r => ({ id: r.id, icon: r.icon, name: r.name, desc: r.ping })), draft.region, 'wiz-region');

  if (step === 'review') return progress + `
    <div class="wiz-step">
      <h2>۹) همه‌چیز درست است؟</h2>
      <div class="review-card">
        <div><span>نام</span><b>${esc(draft.name)}</b></div>
        <div><span>میزبان</span><b>${host ? host.name : '—'}</b></div>
        <div><span>نسخه</span><b>${EDITIONS.find(e => e.id === draft.edition)?.name || ''} · ${draft.version}</b></div>
        <div><span>حالت و سختی</span><b>${GAMEMODES.find(g => g.id === draft.gamemode)?.name || ''} · ${DIFFICULTIES.find(d => d.id === draft.difficulty)?.name || ''}</b></div>
        <div><span>بازیکن / رم</span><b>${faNum(draft.slots)} نفر · ${faNum(draft.ram)} گیگ</b></div>
        <div><span>دیتاسنتر</span><b>${REGIONS.find(r => r.id === draft.region)?.name || ''}</b></div>
        <div><span>هزینه</span><b>${host ? host.price : ''}</b></div>
      </div>
      ${nav('wiz-deploy', '🚀 بساز و راه‌اندازی کن')}
    </div>`;

  if (step === 'deploy') return progress + `
    <div class="wiz-step deploy-step">
      <h2>در حال ساخت روی ${esc(host?.name || '')}…</h2>
      <div class="deploy-list" id="deploy-list"></div>
      <button class="f-btn ghost" data-action="go" data-to="servers">دیدن سرورها در پس‌زمینه</button>
    </div>`;

  return '';
}

function optionsStep(title, options, selected, action) {
  return `
    <div class="wiz-step">
      <h2>${title}</h2>
      <div class="opt-grid ${options.length > 4 ? 'many' : ''}">
        ${options.map(o => `
          <button class="opt ${selected === o.id ? 'selected' : ''}" data-action="${action}" data-id="${o.id}" aria-pressed="${selected === o.id}">
            <span class="opt-icon">${o.icon}</span><strong>${o.name}</strong><small>${o.desc || ''}</small>
          </button>`).join('')}
      </div>
      <div class="wiz-nav"><button class="f-btn ghost" data-action="wiz-back">→ قبلی</button></div>
    </div>`;
}

function wizardAction(action, data) {
  const step = WIZARD_STEPS[wizardStep];
  if (action === 'wiz-back') { wizardStep = Math.max(0, wizardStep - 1); render(); return; }
  if (action === 'wiz-slots') {
    draft.slots = Math.min(100, Math.max(2, (draft.slots || 10) + Number(data.delta)));
    $('#wiz-slots').textContent = faNum(draft.slots); return;
  }
  if (action === 'wiz-edition') draft.edition = data.id;
  if (action === 'wiz-version') draft.version = data.id;
  if (action === 'wiz-gamemode') draft.gamemode = data.id;
  if (action === 'wiz-difficulty') draft.difficulty = data.id;
  if (action === 'wiz-ram') draft.ram = Number(data.id);
  if (action === 'wiz-region') draft.region = data.id;
  if (['wiz-edition', 'wiz-version', 'wiz-gamemode', 'wiz-difficulty', 'wiz-ram', 'wiz-region'].includes(action)) {
    wizardStep += 1; render(); return;
  }
  if (action === 'wiz-next') {
    if (step === 'name') {
      draft.name = ($('#wiz-name')?.value || '').trim();
      if (!draft.name) { toast('اول یک اسم برای سرور بنویس.'); say('اول یک اسم برای سرور بنویس.'); return; }
    }
    wizardStep += 1; render(); return;
  }
  if (action === 'wiz-deploy') { wizardStep = WIZARD_STEPS.indexOf('deploy'); render(); runDeploy(); }
}

function runDeploy() {
  const host = HOSTS.find(h => h.id === selectedHostId);
  const built = buildServer({ ...draft, host, profile: state.profile.operator });
  if (!built.ok) { toast(built.errors[0]); wizardStep = 0; render(); return; }
  const server = built.server;
  const stages = deployStages(server);
  const list = $('#deploy-list');
  if (list) list.innerHTML = stages.map(s => `<div class="deploy-row" id="stage-${s.id}"><span class="d-icon">${s.icon}</span><span class="d-label">${s.label}</span><span class="d-state">در صف</span></div>`).join('');
  speakClip('deploy');
  let i = 0;
  const stepFn = () => {
    if (i >= stages.length) return finishDeploy(server);
    const s = stages[i];
    const row = $(`#stage-${s.id}`);
    if (row) { row.classList.add('active'); $('.d-state', row).textContent = s.skip ? 'رد شد' : 'در حال انجام…'; }
    deployTimers.push(setTimeout(() => {
      if (row) { row.classList.remove('active'); row.classList.add('done'); $('.d-state', row).textContent = s.skip ? 'رد شد ✓' : 'انجام شد ✓'; }
      i += 1; stepFn();
    }, s.skip ? 200 : s.ms));
  };
  stepFn();
}

function finishDeploy(server) {
  server.status = 'online';
  server.deployedAt = Date.now();
  server.consoleLog = bootConsoleLog(server, state.profile.operator);
  server.fakePlayers = ['Aria_MC', 'Kimia_Gamer'];
  state.servers = [server, ...state.servers];
  currentServerId = server.id;
  save();
  toast(`سرور «${server.name}» ساخته و روشن شد 🎉`);
  say(`سرور ${server.name} ساخته شد و روشن است. آدرس آن ${server.address} است. حالا می‌توانی وارد بازی شوی.`);
  wizardStep = 0; draft = {}; selectedHostId = null;
  go('connect');
}

// ———————————————————————————————— سرورهای من ————————————————————————————————
function realServersSection() {
  const ptero = state.real.ptero;
  const vps = state.real.vps;
  if (!ptero && !vps) return '';
  return `
  <div class="real-section">
    <h3>🔌 سرورهای واقعی (متصل به میزبان)</h3>
    ${ptero?.servers?.length ? ptero.servers.map(s => `
      <div class="server-card real">
        <span class="status-dot ${s.isOnline === 'online' ? 'online' : s.isOnline === 'offline' ? '' : 'starting'}"></span>
        <div class="sc-main">
          <strong>${esc(s.name)} <b class="real-badge">واقعی</b></strong>
          <small>${esc(ptero.panelUrl.replace(/^https?:\/\//, ''))} · گره: ${esc(s.node || '—')}</small>
          <small class="addr" dir="ltr">${esc(s.address || '—')}</small>
        </div>
        <div class="sc-side">
          <span class="sc-status ${s.isOnline === 'online' ? 'online' : ''}">${s.isOnline === 'online' ? 'آنلاین' : s.isOnline === 'offline' ? 'خاموش' : s.isOnline || 'نامشخص'}</span>
          <span class="row-btns">
            <button class="mini" data-action="real-open" data-id="${s.id}">پنل</button>
            <button class="mini ${state.real.activeReal?.kind === 'ptero' && state.real.activeReal?.id === s.id ? 'on' : ''}" data-action="real-select" data-id="${s.id}">هدف اورلای</button>
          </span>
        </div>
      </div>`).join('') : ''}
    ${vps?.info ? `
      <div class="server-card real">
        <span class="status-dot ${vps.info.running ? 'online' : ''}"></span>
        <div class="sc-main">
          <strong>سرور وی‌پی‌اس <b class="real-badge">واقعی</b></strong>
          <small>${esc(vps.agentUrl)} · ${esc(vps.info.edition || 'بدون نسخه')} ${esc(vps.info.version || '')}</small>
          <small class="addr" dir="ltr">پورت: ${faNum(vps.info.port || 19132)}</small>
        </div>
        <div class="sc-side">
          <span class="sc-status ${vps.info.running ? 'online' : ''}">${vps.info.running ? 'روشن' : 'خاموش'}</span>
          <span class="row-btns">
            <button class="mini" data-action="real-open-vps">پنل</button>
            <button class="mini ${state.real.activeReal?.kind === 'vps' ? 'on' : ''}" data-action="real-select-vps">هدف اورلای</button>
          </span>
        </div>
      </div>` : ''}
  </div>`;
}

function serversPage() {
  const online = state.servers.filter(s => s.status === 'online').length;
  const hasReal = state.real.ptero || state.real.vps;
  return `
  <div class="page-head"><h2>سرورهای من <b class="count">${faNum(state.servers.length)}</b></h2><p>${hasReal ? 'سرورهای واقعی بالای فهرست‌اند؛ ' : ''}برای پنل کامل روی هر سرور بزن.</p></div>
  ${realServersSection()}
  ${state.servers.length === 0 && !hasReal ? `
    <div class="empty">
      <span class="empty-art">🏗️</span><h3>هنوز سروری نداری</h3>
      <p>با یکی از ۱۱ میزبان، در کمتر از یک دقیقه سرور خودت را بساز.</p>
      <button class="f-btn primary" data-action="go" data-to="hosts">🏗️ ساخت اولین سرور</button>
    </div>` : `
    <div class="server-list">
      ${state.servers.map(s => `
        <button class="server-card" data-action="open-panel" data-id="${s.id}">
          <span class="status-dot ${s.status}"></span>
          <div class="sc-main">
            <strong>${esc(s.name)}</strong>
            <small>${s.hostName} · ${s.edition === 'bedrock' ? 'بدراک' : s.edition === 'java' ? 'جاوا' : 'کراس‌پلی'} · ${s.version}</small>
            <small class="addr" dir="ltr">${s.address}:${s.port}</small>
          </div>
          <div class="sc-side">
            <span class="sc-status ${s.status}">${s.status === 'online' ? 'آنلاین' : s.status === 'starting' ? 'در حال روشن‌شدن' : s.status === 'stopping' ? 'در حال خاموش‌شدن' : 'خاموش'}</span>
            <small>${faNum(s.fakePlayers?.length || 0)}/${faNum(s.slots)} بازیکن</small>
          </div>
        </button>`).join('')}
    </div>
    <div class="wiz-nav"><button class="f-btn primary" data-action="go" data-to="hosts">+ ساخت سرور جدید</button></div>`}`;
}

function currentServer() { return state.servers.find(s => s.id === currentServerId) || state.servers[0] || null; }

function panelPage() {
  if (String(currentServerId).startsWith('real:')) return realPanelPage();
  const s = currentServer();
  if (!s) return `<div class="empty"><span class="empty-art">🗂️</span><h3>سروری نیست</h3><button class="f-btn primary" data-action="go" data-to="hosts">ساخت سرور</button></div>`;
  const tabs = [
    ['overview', 'نمای کلی', '📊'], ['console', 'کنسول', '💻'], ['players', 'بازیکن‌ها', '🧑‍🤝‍🧑'],
    ['settings', 'تنظیمات دقیق', '🎛️'], ['backups', 'بکاپ', '💾'], ['connect', 'اتصال', '🎮'],
  ];
  return `
  <div class="panel-head">
    <div>
      <h2>${esc(s.name)} <span class="status-dot ${s.status}"></span></h2>
      <small>${s.hostName} · ${s.region} · آدرس: <bdi dir="ltr">${s.address}:${s.port}</bdi></small>
    </div>
    <div class="panel-quick">
      <button class="f-btn small ${s.status === 'online' ? 'danger' : 'primary'}" data-action="power" aria-label="روشن/خاموش">${s.status === 'online' ? '⏹ خاموش' : '▶ روشن'}</button>
      <button class="f-btn small ghost" data-action="restart">🔄 ری‌استارت</button>
    </div>
  </div>
  <div class="panel-tabs" role="tablist">
    ${tabs.map(([id, label, icon]) => `<button class="ptab ${panelTab === id ? 'active' : ''}" data-action="panel-tab" data-tab="${id}" role="tab" aria-selected="${panelTab === id}">${icon} ${label}</button>`).join('')}
  </div>
  <div class="panel-body">${panelBody(s)}</div>`;
}

function panelBody(s) {
  if (panelTab === 'overview') return `
    <div class="ov-cards">
      <div class="stat"><span>وضعیت</span><b class="${s.status}">${s.status === 'online' ? '🟢 آنلاین' : s.status === 'offline' ? '⚫ خاموش' : '🟡 در حال تغییر'}</b></div>
      <div class="stat"><span>تی‌پی‌اس</span><b>${s.status === 'online' ? faNum(s.tps) : '—'}</b></div>
      <div class="stat"><span>بازیکن</span><b>${faNum(s.fakePlayers?.length || 0)} / ${faNum(s.slots)}</b></div>
      <div class="stat"><span>رم</span><b>${faNum(s.ram)} گیگ</b></div>
      <div class="stat"><span>نسخه</span><b>${s.version}</b></div>
      <div class="stat"><span>آپ‌تایم</span><b>${s.deployedAt ? faNum(Math.max(1, Math.round((Date.now() - s.deployedAt) / 3600000))) + ' ساعت' : '—'}</b></div>
    </div>
    <div class="wiz-nav center">
      <button class="f-btn primary" data-action="go-connect">🎮 ورود به بازی با این سرور</button>
      <button class="f-btn ghost" data-action="panel-tab" data-tab="console">💻 رفتن به کنسول</button>
    </div>`;

  if (panelTab === 'console') return `
    <div class="console" id="console-view">${s.consoleLog.slice(-40).map(l => `<div class="c-line ${l.kind}"><small>${faTime(l.at)}</small> ${esc(l.text)}</div>`).join('')}</div>
    <form class="console-input" data-form="console-cmd">
      <input name="cmd" dir="ltr" placeholder="/ دستور را بنویس… مثلاً /time set day" autocomplete="off">
      <button class="f-btn small primary">اجرا</button>
    </form>`;

  if (panelTab === 'players') return `
    <div class="players-box">
      <div class="player-row head"><span>بازیکن</span><span>دسترسی‌ها</span></div>
      ${[state.profile.operator, ...(s.fakePlayers || [])].map(p => `
        <div class="player-row">
          <span>${esc(p)} ${s.status === 'online' ? '🟢' : '⚪'}</span>
          <span class="row-btns">
            <button class="mini ${s.ops.includes(p) ? 'on' : ''}" data-action="srv-op" data-who="${esc(p)}">${s.ops.includes(p) ? '⭐ اوپ' : 'اوپ'}</button>
            <button class="mini" data-action="srv-whitelist" data-who="${esc(p)}">لیست سفید</button>
            <button class="mini warn" data-action="srv-kick" data-who="${esc(p)}">بیرون</button>
            <button class="mini danger" data-action="srv-ban" data-who="${esc(p)}">بن</button>
          </span>
        </div>`).join('')}
      ${s.banned.length ? `<p class="banned">مسدودها: ${s.banned.map(b => `<button class="mini" data-action="srv-pardon" data-who="${esc(b)}">${esc(b)} ✕</button>`).join(' ')}</p>` : ''}
    </div>`;

  if (panelTab === 'settings') return `
    <div class="props">
      ${PROPERTY_GROUPS.map(g => `
        <details class="prop-group" ${g.id === 'general' ? 'open' : ''}>
          <summary>${g.icon} ${g.name}</summary>
          <div class="prop-grid">
            ${g.props.map(p => propField(s, p)).join('')}
          </div>
        </details>`).join('')}
    </div>`;

  if (panelTab === 'backups') return `
    <div class="wiz-nav center"><button class="f-btn primary" data-action="backup-now">💾 پشتیبان‌گیری همین حالا</button></div>
    <div class="backup-list">
      ${s.backups.length === 0 ? '<p class="muted">هنوز پشتیبانی گرفته نشده؛ با یک لمس همین حالا بساز.</p>' : s.backups.map(b => `
        <div class="backup-row"><span>📦 ${faDate(b.at)} · ${faTime(b.at)} · ${faNum(b.sizeMb)} مگابایت</span>
          <span><button class="mini" data-action="backup-restore" data-id="${b.id}">بازگردانی</button></span></div>`).join('')}
    </div>`;

  if (panelTab === 'connect') return connectCardBody(s);
  return '';
}

function propField(s, p) {
  const val = s.props[p.key] ?? '';
  if (p.type === 'bool') return `
    <label class="prop bool"><span>${p.label}</span>
      <button class="toggle ${val ? 'on' : ''}" data-action="prop-bool" data-key="${p.key}" role="switch" aria-checked="${!!val}">${val ? 'روشن' : 'خاموش'}</button>
    </label>`;
  if (p.type === 'select') return `
    <label class="prop"><span>${p.label}</span>
      <select data-prop="${p.key}">${p.options.map(o => `<option value="${o}" ${val === o ? 'selected' : ''}>${o}</option>`).join('')}</select>
    </label>`;
  return `
    <label class="prop"><span>${p.label}${p.hint ? `<i>${p.hint}</i>` : ''}</span>
      <input data-prop="${p.key}" type="${p.type === 'number' ? 'number' : 'text'}" dir="ltr" value="${esc(val)}">
    </label>`;
}

// ———————————————————————————————— اتصال واقعی ————————————————————————————————
function ptero() {
  const c = state.real.ptero;
  if (!c) return null;
  pteroClient = pteroClient || createPterodactylClient({ panelUrl: c.panelUrl, apiKey: c.apiKey });
  return pteroClient;
}
function vps() {
  const c = state.real.vps;
  if (!c) return null;
  vpsClient = vpsClient || createVpsAgentClient({ agentUrl: c.agentUrl, token: c.token });
  return vpsClient;
}

function realPage() {
  const p = state.real.ptero;
  const v = state.real.vps;
  const android = typeof AndroidBridge !== 'undefined';
  return `
  <div class="page-head"><h2>اتصال به میزبان واقعی 🔌</h2><p>اینجا واقعاً وصل می‌شوی: پنل میزبان یا وی‌پی‌اس خودت. دستورهای اورلای مستقیم به سرور واقعی می‌روند.</p></div>
  <div class="real-grid">
    <div class="conn-card">
      <h3>۱) پنل میزبان (پتروداکتیل)</h3>
      <p class="muted">PebbleHost، FalixNodes، GodLike، Bisect و بیشتر میزبان‌ها پنل پتروداکتیل دارند. ${PTERO_KEY_HELP}</p>
      <form data-form="ptero-connect" class="real-form">
        <input name="panelUrl" dir="ltr" placeholder="https://panel.pebblehost.com" value="${esc(p?.panelUrl || '')}">
        <input name="apiKey" dir="ltr" type="password" placeholder="ptlc_xxxxxxxxxxxxxxxx" value="${esc(p?.apiKey || '')}">
        <button class="f-btn primary small">🔌 اتصال و دریافت سرورهای واقعی</button>
      </form>
      <div id="ptero-status" class="real-status">${p ? `متصل به ${esc(p.panelUrl)} — ${faNum(p.servers?.length || 0)} سرور پیدا شد` : ''}</div>
      ${p ? '<button class="f-btn small ghost danger-ghost" data-action="ptero-disconnect">قطع اتصال پنل</button>' : ''}
    </div>
    <div class="conn-card">
      <h3>۲) وی‌پی‌اس خودت (اترنوس یا هر وی‌پی‌اس)</h3>
      <p class="muted">«عامل آتا» را یک‌بار روی وی‌پی‌اس اجرا کن؛ از آن پس اپ سرور واقعی را می‌سازد و کامل مدیریت می‌کند.</p>
      <form data-form="vps-connect" class="real-form">
        <input name="agentUrl" dir="ltr" placeholder="http://5.160.10.10:8790" value="${esc(v?.agentUrl || '')}">
        <input name="token" dir="ltr" type="password" placeholder="توکن عامل (همان --token)" value="${esc(v?.token || '')}">
        <button class="f-btn primary small">🔌 اتصال به عامل</button>
      </form>
      <div id="vps-status" class="real-status">${v ? (v.info ? `متصل — سرور ${v.info.running ? 'روشن' : 'خاموش'} (${esc(v.info.edition || '؟')} ${esc(v.info.version || '')})` : 'متصل') : ''}</div>
      <details class="support-item"><summary>نصب عامل روی وی‌پی‌اس (یک خط)</summary>
        <p class="muted">در ترمینال وی‌پی‌اس اجرا کن؛ توکن طولانی انتخاب کن و در فایروال پورت را فقط برای آی‌پی خودت باز کن.</p>
        <div class="addr-box" dir="ltr"><b style="font-size:.72rem">${esc(agentInstallOneLiner())}</b></div>
        <button class="mini" data-action="copy" data-value="${esc(agentInstallOneLiner())}" data-label="دستور نصب">📋 کپی دستور نصب</button>
      </details>
      ${v ? `<div class="row-btns">
        <button class="f-btn small ghost" data-action="vps-create">🏗️ ساخت سرور واقعی روی وی‌پی‌اس</button>
        <button class="f-btn small ghost danger-ghost" data-action="vps-disconnect">قطع</button>
      </div>` : ''}
    </div>
    <div class="conn-card">
      <h3>۳) اورلای واقعی روی بازی</h3>
      <p class="muted">بعد از اتصال، دکمهٔ زیر اورلای اپراتور را «روی خود ماینکرافت» باز می‌کند؛ هر لمس، دستور را به سرور واقعیِ انتخاب‌شده می‌فرستد. اول از تنظیمات اندروید اجازهٔ «نمایش روی برنامه‌ها» بده.</p>
      <div class="row-btns">
        <button class="f-btn small primary" data-action="native-overlay">🪟 بازکردن اورلای روی بازی</button>
        <button class="f-btn small ghost" data-action="go" data-to="game">🕹️ تمرین در شبیه‌ساز</button>
      </div>
      <div class="real-status">${android ? '' : 'نکته: این بخش در نسخهٔ اندروید فعال است؛ در وب، شبیه‌ساز را ببین.'}</div>
      <details class="support-item"><summary>چرا اترنوس رایگان (Aternos) خودکار نیست؟</summary>
        <p class="muted">اترنوس هیچ API رسمی ندارد و طبق قوانینش، استفاده از ابزار غیررسمی باعث حذف سرور و مسدودشدن حساب می‌شود. برای همین آتا به‌جای ریسک، تو را مستقیم به پنل خودش می‌برد.</p>
        <button class="f-btn small ghost" data-action="launch-web" data-url="https://aternos.org/server/">بازکردن پنل اترنوس</button>
      </details>
    </div>
  </div>`;
}

async function pteroConnect(data) {
  const panelUrl = normalizePanelUrl(data.panelUrl);
  const apiKey = (data.apiKey || '').trim();
  if (!panelUrl) { toast('نشانی پنل را درست بنویس؛ مثلاً https://panel.pebblehost.com'); return; }
  if (!apiKey) { toast('کلید API را از پنل میزبانت بساز و اینجا بگذار.'); return; }
  const el = $('#ptero-status');
  if (el) el.textContent = 'در حال اتصال واقعی به پنل…';
  const client = createPterodactylClient({ panelUrl, apiKey });
  try {
    const servers = await client.listServers();
    pteroClient = client;
    state.real.ptero = { panelUrl, apiKey, servers };
    save();
    toast(servers.length ? `${faNum(servers.length)} سرور واقعی از پنل گرفتم ✅` : 'اتصال موفق بود ولی سروری در این حساب نیست.');
    say(servers.length ? `اتصال واقعی برقرار شد و ${servers.length} سرور پیدا شد.` : 'اتصال برقرار شد ولی سروری پیدا نشد.');
    refreshRealLive();
    render();
  } catch (e) {
    if (el) el.textContent = `خطا: ${e.message}`;
    toast(`اتصال نشد: ${e.message}`);
  }
}

async function refreshRealLive() {
  // وضعیت زندهٔ سرورهای واقعی
  if (state.real.ptero?.servers?.length && ptero()) {
    for (const s of state.real.ptero.servers) {
      ptero().resources(s.id).then(r => { s.isOnline = r.state; save(); if (page === 'real' || page === 'servers') { const el = document.querySelector(`[data-id="${s.id}"] .sc-status`); if (el) el.textContent = r.state === 'online' ? 'آنلاین' : r.state === 'offline' ? 'خاموش' : r.state; } }).catch(() => { s.isOnline = 'نامشخص'; });
    }
  }
  if (state.real.vps && vps()) {
    try {
      const info = await vps().status();
      state.real.vps.info = info; save();
      if (page === 'real') { const el = $('#vps-status'); if (el) el.textContent = `متصل — سرور ${info.running ? 'روشن' : 'خاموش'} (${info.edition || '؟'} ${info.version || ''})`; }
      if (page === 'panel' && String(currentServerId).startsWith('real:vps')) render();
    } catch (e) {
      if (page === 'real') { const el = $('#vps-status'); if (el) el.textContent = `خطا: ${e.message}`; }
    }
  }
}

async function vpsConnect(data) {
  const agentUrl = (data.agentUrl || '').trim();
  const token = (data.token || '').trim();
  if (!agentUrl || !token) { toast('نشانی عامل و توکن را وارد کن.'); return; }
  const el = $('#vps-status');
  if (el) el.textContent = 'در حال اتصال به عامل روی وی‌پی‌اس…';
  const client = createVpsAgentClient({ agentUrl, token });
  try {
    const info = await client.status();
    vpsClient = client;
    state.real.vps = { agentUrl, token, info };
    save();
    toast('به عامل وی‌پی‌اس وصل شدم ✅');
    say('اتصال به وی پی اس برقرار شد. حالا می‌توانی سرور واقعی بسازی یا مدیریت کنی.');
    render();
  } catch (e) {
    if (el) el.textContent = `خطا: ${e.message}`;
    toast(`اتصال نشد: ${e.message}`);
  }
}

async function vpsCreateReal() {
  if (!vps()) { toast('اول به عامل وصل شو.'); return; }
  const edition = 'bedrock'; // بدراک پیش‌فرض است؛ جاوا هم پشتیبانی می‌شود
  try {
    toast('در حال ساخت واقعی سرور روی وی‌پی‌اس (دانلود هسته)…');
    const res = await vps().createServer({ name: 'Atta Server', edition, version: '1.21.111' });
    toast(`سرور واقعی ساخته شد ✅ ${res.message || ''}`);
    say('سرور واقعی روی وی پی اس ساخته شد و آمادهٔ روشن‌شدن است.');
    const info = await vps().status();
    state.real.vps.info = info; save(); render();
  } catch (e) { toast(`ساخت نشد: ${e.message}`); }
}

// هدف اورلای: سرور واقعی‌ای که دستورهای یک‌لمسی به آن می‌روند
function setActiveReal(kind, id = null) {
  state.real.activeReal = kind === 'none' ? null : (kind === 'ptero' ? { kind, id } : { kind: 'vps' });
  save(); render();
  const on = state.real.activeReal;
  toast(on ? 'هدف اورلای تنظیم شد؛ از این پس دکمه‌ها روی همین سرور واقعی اعمال می‌شوند.' : 'هدف اورلای برداشته شد.');
  if (on) say('هدف اورلای تنظیم شد. داخل بازی، یک لمس کافی است.');
}

// ارسال واقعی دستور به سرور واقعی — قلب «تأثیر با یک کلیک»
async function sendRealCommand(cmd) {
  const target = state.real.activeReal;
  if (!target) return { sent: false, reason: 'no-target' };
  try {
    if (target.kind === 'ptero') {
      if (!ptero()) return { sent: false, reason: 'no-connection' };
      await ptero().sendCommand(target.id, cmd);
    } else if (target.kind === 'vps') {
      if (!vps()) return { sent: false, reason: 'no-connection' };
      await vps().sendCommand(cmd);
    } else return { sent: false, reason: 'no-target' };
    return { sent: true };
  } catch (e) { return { sent: false, reason: e.message }; }
}

async function realPower(signal) {
  const target = state.real.activeReal;
  if (!target) { toast('اول از فهرست سرورها، «هدف اورلای» را انتخاب کن.'); return; }
  try {
    if (target.kind === 'ptero') await ptero().power(target.id, signal);
    else await vps().power(signal);
    const fa = { start: 'سرور واقعی روشن شد ▶', stop: 'سرور واقعی خاموش شد ⏹', restart: 'ری‌استارت واقعی آغاز شد 🔄', kill: 'سرور متوقف شد' }[signal];
    toast(fa); say(fa);
    setTimeout(refreshRealLive, 1500);
  } catch (e) { toast(`انجام نشد: ${e.message}`); }
}

// ———————————————————————————————— پنل سرور واقعی ————————————————————————————————
function realPanelPage() {
  const isPtero = String(currentServerId).startsWith('real:ptero:');
  const srv = isPtero ? state.real.ptero?.servers?.find(s => s.id === currentServerId.slice('real:ptero:'.length)) : null;
  const info = !isPtero ? state.real.vps?.info : null;
  const name = isPtero ? (srv?.name || 'سرور واقعی') : 'سرور وی‌پی‌اس';
  const addr = isPtero ? (srv?.address || '—') : `${state.real.vps?.agentUrl || ''} پورت ${info?.port || ''}`;
  const online = isPtero ? srv?.isOnline === 'online' : !!info?.running;
  const tabs = [
    ['overview', 'نمای کلی', '📊'], ['console', 'کنسول زنده', '💻'],
    isPtero ? ['backups', 'بکاپ واقعی', '💾'] : ['commands', 'دستورها', '⚡'],
    ['overlay', 'اورلای', '🪟'],
  ];
  return `
  <div class="panel-head">
    <div>
      <h2>${esc(name)} <b class="real-badge">واقعی</b> <span class="status-dot ${online ? 'online' : ''}"></span></h2>
      <small>${isPtero ? esc(state.real.ptero?.panelUrl || '') : esc(state.real.vps?.agentUrl || '')} · <bdi dir="ltr">${esc(addr)}</bdi></small>
    </div>
    <div class="panel-quick">
      <button class="f-btn small primary" data-action="real-power" data-signal="start">▶ روشن</button>
      <button class="f-btn small danger" data-action="real-power" data-signal="stop">⏹ خاموش</button>
      <button class="f-btn small ghost" data-action="real-power" data-signal="restart">🔄</button>
    </div>
  </div>
  <div class="panel-tabs" role="tablist">
    ${tabs.map(([id, label, icon]) => `<button class="ptab ${panelTab === id ? 'active' : ''}" data-action="panel-tab" data-tab="${id}" role="tab">${icon} ${label}</button>`).join('')}
  </div>
  <div class="panel-body">${realPanelBody(isPtero, srv, info)}</div>`;
}

function realPanelBody(isPtero, srv, info) {
  if (panelTab === 'overview') {
    if (!isPtero && info) return `
      <div class="ov-cards">
        <div class="stat"><span>وضعیت</span><b class="${info.running ? 'online' : ''}">${info.running ? '🟢 روشن' : '⚫ خاموش'}</b></div>
        <div class="stat"><span>نسخه</span><b>${esc(info.edition || '—')} ${esc(info.version || '')}</b></div>
        <div class="stat"><span>پورت</span><b>${faNum(info.port || 19132)}</b></div>
        <div class="stat"><span>آپ‌تایم</span><b>${faNum(info.uptimeSec || 0)} ثانیه</b></div>
      </div>
      <div class="wiz-nav center"><button class="f-btn primary" data-action="native-overlay">🪟 بازکردن اورلای روی بازی</button></div>`;
    return `
      <div class="ov-cards">
        <div class="stat"><span>وضعیت</span><b class="${srv?.isOnline === 'online' ? 'online' : ''}">${srv?.isOnline || '…'}</b></div>
        <div class="stat"><span>نشانی</span><b dir="ltr" style="font-size:.85rem">${esc(srv?.address || '—')}</b></div>
        <div class="stat"><span>گره</span><b>${esc(srv?.node || '—')}</b></div>
      </div>
      <div class="wiz-nav center">
        <button class="f-btn primary" data-action="real-select" data-id="${srv?.id}">🎯 هدف اورلای کردن این سرور</button>
        <button class="f-btn ghost" data-action="panel-tab" data-tab="console">💻 کنسول زنده</button>
      </div>`;
  }
  if (panelTab === 'console') return `
    <div class="console" id="console-view"><div class="c-line atta">در حال اتصال به کنسول زندهٔ سرور واقعی…</div></div>
    <form class="console-input" data-form="real-console-cmd">
      <input name="cmd" dir="ltr" placeholder="دستور واقعی… مثلاً time set day" autocomplete="off">
      <button class="f-btn small primary">اجرا روی سرور واقعی</button>
    </form>`;
  if (panelTab === 'backups') return `
    <div class="wiz-nav center"><button class="f-btn primary" data-action="real-backup">💾 ساخت بکاپ واقعی در پنل میزبان</button></div>
    <div class="backup-list" id="real-backup-list"><p class="muted">در حال گرفتن فهرست بکاپ‌های واقعی…</p></div>`;
  if (panelTab === 'commands') return `
    <div class="ov-grid">${QUICK_ACTIONS.map(a => `<button class="f-btn small" data-action="real-cmd" data-cmd="${esc(a.cmd)}">${a.icon} ${esc(a.label)}</button>`).join('')}</div>
    <form class="console-input" data-form="real-console-cmd">
      <input name="cmd" dir="ltr" placeholder="دستور دلخواه…" autocomplete="off">
      <button class="f-btn small primary">اجرا</button>
    </form>`;
  if (panelTab === 'overlay') return `
    <div class="wiz-nav center">
      <button class="f-btn primary" data-action="real-select" data-id="${isPtero ? srv?.id : ''}">🎯 این سرور هدف اورلای شود</button>
      <button class="f-btn primary" data-action="native-overlay">🪟 بازکردن اورلای روی بازی</button>
      <button class="f-btn ghost" data-action="go" data-to="game">🕹️ شبیه‌ساز داخل اپ</button>
    </div>
    <p class="muted">وقتی اورلای روی بازی باز است، هر دکمهٔ آن همین دستورهای آماده را به سرور واقعیِ هدف می‌فرستد؛ بدون خروج از بازی.</p>`;
  return '';
}

function startRealConsoleWatch() {
  stopRealWatch();
  const view = $('#console-view');
  if (!view) return;
  const append = (text, kind = 'sys') => {
    const div = document.createElement('div');
    div.className = `c-line ${kind}`;
    div.textContent = text;
    view.appendChild(div);
    while (view.children.length > 150) view.removeChild(view.firstChild);
    view.scrollTop = view.scrollHeight;
  };
  if (String(currentServerId).startsWith('real:ptero:')) {
    const id = currentServerId.slice('real:ptero:'.length);
    if (!ptero()) return;
    view.innerHTML = '';
    realConsoleHandle = ptero().openConsole(id, {
      onLine: (l) => append(l, 'chat'),
      onState: (s) => { if (s === 'connected') append('— اتصال زندهٔ وب‌سوکت برقرار شد —', 'atta'); },
    });
  } else if (String(currentServerId).startsWith('real:vps')) {
    if (!vps()) return;
    const poll = async () => {
      try { const r = await vps().consoleTail(40); view.innerHTML = ''; r.forEach(l => append(l, 'chat')); } catch { /* noop */ }
    };
    poll();
    realPollTimer = setInterval(poll, 4000);
  }
}

async function loadRealBackups() {
  const list = $('#real-backup-list');
  const id = currentServerId.slice('real:ptero:'.length);
  if (!ptero() || !list) return;
  try {
    const bks = await ptero().backups(id);
    list.innerHTML = bks.length ? bks.map(b => `<div class="backup-row"><span>📦 ${esc(b.name || b.uuid)} · ${faNum(Math.round((b.bytes || 0) / 1048576))} مگ · ${esc((b.at || '').slice(0, 10))}</span>${b.completed ? '<span class="sc-status online">موفق</span>' : '<span class="sc-status">در حال ساخت</span>'}</div>`).join('') : '<p class="muted">هنوز بکاپی در پنل میزبان نیست؛ با دکمهٔ بالا بساز.</p>';
  } catch { list.innerHTML = '<p class="muted">فهرست بکاپ‌ها گرفته نشد.</p>'; }
}

// ———————————————————————————————— ورود به بازی ————————————————————————————————
function connectPage() {
  const s = currentServer();
  if (!s) return `<div class="empty"><span class="empty-art">🎮</span><h3>اول یک سرور بساز</h3><button class="f-btn primary" data-action="go" data-to="hosts">ساخت سرور</button></div>`;
  return `
  <div class="page-head"><h2>ورود به بازی «${esc(s.name)}»</h2><p>اطلاعات سرور را منتقل کردم؛ فقط وصل شو.</p></div>
  <div class="connect-grid">
    <div class="conn-card">
      <h3>۱) نشانی اتصال</h3>
      <div class="addr-box" dir="ltr"><b>${s.address}</b><span>پورت: ${faNum(s.port)}</span></div>
      <div class="wiz-nav center">
        <button class="f-btn small primary" data-action="copy" data-value="${s.address}" data-label="نشانی">📋 کپی نشانی</button>
        <button class="f-btn small ghost" data-action="copy" data-value="${s.address}:${s.port}" data-label="نشانی و پورت">📋 نشانی:پورت</button>
      </div>
      <p class="muted">در بازی: Play ← افزودن سرور ← جایگذاری نشانی. آتا قبلاً آن را کپی کرده است.</p>
    </div>
    <div class="conn-card">
      <h3>۲) بازی را باز کن</h3>
      ${connectCardBody(s)}
    </div>
    <div class="conn-card">
      <h3>۳) بعد از ورود</h3>
      <p class="muted">داخل بازی، انگشتت را از لبهٔ چپ به داخل بکش تا اورلای اپراتور آتا باز شود؛ یا بگو «آتا».</p>
      <button class="f-btn primary" data-action="go" data-to="game">🎮 تمرین اورلای در حالت بازیِ آتا</button>
    </div>
  </div>`;
}

function connectCardBody(s) {
  return `
    <div class="launch-grid">
      <button class="launch-btn bedrock" data-action="launch" data-target="bedrock"><span>📱</span>ماینکرافت بدراک</button>
      <button class="launch-btn boat" data-action="launch" data-target="pojav"><span>🚤</span>لانچر پوجا‌و (جاوا)</button>
      <button class="launch-btn sim" data-action="go" data-to="game"><span>🕹️</span>شبیه‌ساز بازی آتا</button>
    </div>`;
}

function launchGame(target) {
  const s = currentServer();
  if (s) copyText(s.address, 'نشانی سرور');
  if (typeof AndroidBridge !== 'undefined' && (AndroidBridge.launchExternal || AndroidBridge.openLauncherPackage)) {
    if (target === 'pojav' && AndroidBridge.openLauncherPackage) AndroidBridge.openLauncherPackage('net.kdt.pojavlaunch');
    else if (AndroidBridge.launchExternal) AndroidBridge.launchExternal(target === 'pojav' ? 'market://details?id=net.kdt.pojavlaunch' : 'minecraft://');
    say(target === 'pojav' ? 'لانچر پوجاو را باز کردم؛ نشانی سرور کپی شده است.' : 'ماینکرافت بدراک را باز کردم؛ نشانی سرور کپی شده است. در بازی، افزودن سرور را بزن و جایگذاری کن.');
    return;
  }
  if (target === 'bedrock') location.href = 'minecraft://';
  toast(s ? 'اگر بازی نصب باشد باز می‌شود؛ نشانی سرور کپی شد. در وب، دکمهٔ شبیه‌ساز را بزن.' : 'شبیه‌ساز بازی آتا باز شد.');
}

// ———————————————————————————————— حالت بازی + اورلای ————————————————————————————————
function startGamePage() {
  const app = $('#app');
  const s = currentServer();
  world = world || createGameWorld(s ? s.name : 'دنیای تک‌نفره');
  if (s && s.status === 'online' && s.fakePlayers) world.chat = [{ who: 'آتا', text: `به سرور ${s.name} وصل شدی (${s.address}:${s.port}).` }];
  app.innerHTML = `
    <div class="game-screen" data-page="game">
      <canvas id="game-canvas"></canvas>
      <div class="game-hud" dir="rtl">
        <div class="hud-row">
          <span class="hud-chip">🎮 ${world.name}</span>
          <span class="hud-chip" id="hud-mode">حالت: ${world.player.mode}</span>
          <span class="hud-chip" id="hud-pos" dir="ltr">x:${faNum(Math.round(world.player.x))} y:${faNum(Math.round(world.player.y))} z:${faNum(Math.round(world.player.z))}</span>
        </div>
        <div class="hud-row">
          <span class="hud-chip" id="hud-hp">${'❤️'.repeat(10)}</span>
          <span class="hud-chip" id="hud-fx"></span>
        </div>
      </div>
      <div class="game-chat" id="game-chat" dir="rtl"></div>
      <div class="hotbar" id="hotbar" dir="ltr"></div>
      <div class="game-exit">
        <button class="f-btn small ghost" data-action="mic" aria-label="فرمان صوتی">🎤 فرمان صوتی</button>
        <button class="f-btn small ghost" data-action="go" data-to="home">⏏ خروج از بازی</button>
      </div>
      <div class="orientation-hint" id="orientation-hint">📱 برای بهترین تجربه گوشی را افقی بگیر — کشیدن از لبهٔ چپ، اورلای را باز می‌کند</div>
    </div>`;
  const gs = $('.game-screen');
  const testTimeout = Number(new URLSearchParams(typeof location !== 'undefined' ? location.search : '').get('overlayTimeout'));
  overlay = createOverlay({
    mount: gs,
    tabContent: overlayTabContent,
    onAction: (act, tabId) => runOverlayAction(act, tabId),
    onClose: () => {},
    onTabOpen: (id) => { const t = OVERLAY_TABS.find(x => x.id === id); say(`زبانهٔ ${t.name} باز شد.`); },
    ...(Number.isFinite(testTimeout) && testTimeout > 0 ? { autoCloseMs: testTimeout } : {}),
  });
  renderChat(); renderHotbar(); renderFxHud();
  const canvas = $('#game-canvas');
  const ctx = canvas.getContext('2d');
  const resize = () => { canvas.width = gs.clientWidth; canvas.height = gs.clientHeight; };
  resize(); window.addEventListener('resize', resize);
  const loop = () => {
    if (!world.timeFrozen) world.timeTick = (world.timeTick + 2) % 24000;
    drawWorld(ctx, world, canvas.width, canvas.height);
    updateHud();
    gameRaf = requestAnimationFrame(loop);
  };
  gameRaf = requestAnimationFrame(loop);
  if (typeof AndroidBridge !== 'undefined' && AndroidBridge.fullscreen) { try { AndroidBridge.fullscreen(true); } catch { /* noop */ } }
  say('حالت بازی روشن است. از لبهٔ چپ صفحه به داخل بکش تا اورلای آتا باز شود.');
}

function stopGameLoops() {
  if (gameRaf) cancelAnimationFrame(gameRaf);
  gameRaf = 0;
  if (hintTimer) clearInterval(hintTimer);
  if (overlay) { overlay.destroy(); overlay = null; }
  if (typeof AndroidBridge !== 'undefined' && AndroidBridge.fullscreen) { try { AndroidBridge.fullscreen(false); } catch { /* noop */ } }
}

function updateHud() {
  const mode = $('#hud-mode'); if (mode) mode.textContent = `حالت: ${world.player.mode}${world.player.flying ? ' · پرواز' : ''}`;
  const pos = $('#hud-pos'); if (pos) pos.textContent = `x:${Math.round(world.player.x)} y:${Math.round(world.player.y)} z:${Math.round(world.player.z)}`;
  const hp = $('#hud-hp'); if (hp) hp.textContent = '❤️'.repeat(Math.ceil(world.player.hp / 2)) + '🖤'.repeat(10 - Math.ceil(world.player.hp / 2));
}

function renderChat() {
  const el = $('#game-chat');
  if (!el) return;
  el.innerHTML = world.chat.slice(-4).map(c => `<div class="chat-line ${c.announce ? 'announce' : ''}"><b>${esc(c.who)}:</b> ${esc(c.text)}</div>`).join('');
}

function renderHotbar() {
  const el = $('#hotbar');
  if (!el) return;
  el.innerHTML = Array.from({ length: 9 }, (_, i) => {
    const item = world.hotbar[i];
    return `<div class="slot ${item ? 'full' : ''}">${item ? `${item.id.replaceAll('_', ' ')}` : ''}${item ? `<i>×${faNum(item.count)}</i>` : ''}</div>`;
  }).join('');
}

function renderFxHud() {
  const el = $('#hud-fx');
  if (!el) return;
  world.player.effects = world.player.effects.filter(f => f.until > Date.now());
  el.textContent = world.player.effects.map(f => `✨${f.fx}`).join(' ') || '';
}

function runCommand(cmd) {
  const s = currentServer();
  // اگر سرور واقعی هدف اورلای باشد، دستور اول به سرور واقعی می‌رود — تأثیر واقعی با یک کلیک
  if (state.real.activeReal) {
    sendRealCommand(cmd).then(r => {
      if (r.sent) {
        world.chat = [...world.chat.slice(-5), { who: 'آتا', text: `دستور به سرور واقعی ارسال شد ✅ ${cmd}` }];
        renderChat();
        toast(`به سرور واقعی ارسال شد ✅`);
      } else {
        world.chat = [...world.chat.slice(-5), { who: 'خطا', text: `به سرور واقعی نرسید (${r.reason}); روی شبیه‌ساز اعمال شد.` }];
        renderChat();
      }
    });
  }
  const res = executeCommand(s || { props: {}, ops: [], whitelist: [], banned: [] }, world, cmd, state.profile.operator);
  if (s) {
    s.consoleLog = [...(s.consoleLog || []).slice(-80), { at: Date.now(), kind: res.ok ? 'ok' : 'err', text: `[${state.profile.operator}] ${cmd} → ${res.message}` }];
    save();
  }
  const notices = applyEvents(world, res.events, state.profile.operator);
  world.chat = [...world.chat.slice(-5), { who: state.profile.operator, text: cmd }];
  if (res.message) world.chat = [...world.chat.slice(-5), { who: res.ok ? 'آتا' : 'خطا', text: res.message, announce: !res.ok }];
  renderChat(); renderHotbar(); renderFxHud();
  if (res.events.some(e => e.type === 'stop') && s) { s.status = 'offline'; save(); }
  if (res.events.some(e => e.type === 'restart') && s) { s.status = 'online'; save(); }
  toast(res.message);
  if (state.settings.narration) say(res.message);
  return res;
}

function runOverlayAction(act, tabId) {
  if (act.id === 'free-command' && (act.data?.cmd || act.cmd)) {
    const c = act.data?.cmd || act.cmd;
    runCommand(c.startsWith('/') ? c : `/${c}`);
    return;
  }
  if (act.id === 'teleport' && act.data) { runCommand(`/tp @s ${Number(act.data.x) || 0} ${Number(act.data.y) || 70} ${Number(act.data.z) || 0}`); return; }
  if (act.id === 'give-item') { const [item, count] = (act.payload || '').split(':'); runCommand(`/give @s ${item} ${count || 1}`); return; }
  if (act.id === 'summon-mob') { runCommand(`/summon ${act.payload}`); return; }
  if (act.id === 'support-ask' && act.data?.q) { supportAnswer(act.data.q); return; }
  if (act.id === 'support-topic') { handleAction('support-topic', { payload: act.payload }); return; }
  if (act.id === 'srv-power') { togglePower(); return; }
  if (act.id === 'srv-restart') { restartServer(); return; }
  if (act.id === 'srv-backup') { backupNow(); return; }
  if (act.cmd) runCommand(act.cmd);
}

function overlayTabContent(tabId) {
  const grid = actions => `<div class="ov-grid">${actions.map(a => `
    <button class="ov-btn" data-act="${a.id}" data-label="${esc(a.label)}" data-cmd="${esc(a.cmd)}">
      <span class="ov-btn-icon">${a.icon}</span><span>${esc(a.label)}</span>
    </button>`).join('')}</div>`;

  switch (tabId) {
    case 'quick': return grid(QUICK_ACTIONS);
    case 'world': return grid(WORLD_ACTIONS);
    case 'player': return grid(PLAYER_ACTIONS) + `
      <form class="ov-form" data-ovform="teleport" data-label="تله‌پورت با مختصات">
        <input name="x" inputmode="numeric" placeholder="X" dir="ltr"><input name="y" inputmode="numeric" placeholder="Y" dir="ltr"><input name="z" inputmode="numeric" placeholder="Z" dir="ltr">
        <button class="ov-btn"><span class="ov-btn-icon">🚀</span><span>برو به این مختصات</span></button>
      </form>`;
    case 'items': return ITEM_GROUPS.map(g => `
      <div class="ov-group"><h4>${g.icon} ${g.name}</h4><div class="ov-grid small">
        ${g.items.map(it => `<button class="ov-btn" data-act="give-item" data-label="${esc(it.label)}" data-payload="${it.id}:${it.count || 1}"><span class="ov-btn-icon">${it.icon}</span><span>${esc(it.label)}</span></button>`).join('')}
      </div></div>`).join('');
    case 'powers': return grid(POWER_ACTIONS);
    case 'mobs': return MOB_GROUPS.map(g => `
      <div class="ov-group"><h4>${g.icon} ${g.name}</h4><div class="ov-grid small">
        ${g.mobs.map(m => `<button class="ov-btn" data-act="summon-mob" data-label="${esc(m.label)}" data-payload="${m.id}"><span class="ov-btn-icon">${m.icon}</span><span>${esc(m.label)}</span></button>`).join('')}
      </div></div>`).join('') + `<div class="ov-group"><h4>⚔️ کنترل</h4><div class="ov-grid small">${MOB_ACTIONS.map(a => `<button class="ov-btn" data-act="${a.id}" data-label="${esc(a.label)}" data-cmd="${esc(a.cmd)}"><span class="ov-btn-icon">${a.icon}</span><span>${esc(a.label)}</span></button>`).join('')}</div></div>`;
    case 'command': return `
      <form class="ov-form wide" data-ovform="free-command" data-label="اجرای دستور دلخواه">
        <input name="cmd" dir="ltr" placeholder="/say سلام به همه…" autocomplete="off">
        <button class="ov-btn"><span class="ov-btn-icon">⚡</span><span>اجرای دستور</span></button>
      </form>
      <div class="ov-grid small">${['/help', '/list', '/seed', '/save-all', '/gc', '/difficulty hard', '/gamerule doImmediateRespawn true', '/say سرور آتا بهترین است!'].map(c => `<button class="ov-btn" data-act="free-command" data-label="${esc(c)}" data-cmd="${esc(c)}" data-payload=""><span class="ov-btn-icon">💬</span><span dir="ltr">${c}</span></button>`).join('')}</div>`;
    case 'admin': {
      const s = currentServer();
      return `<div class="ov-grid">${[
        { id: 'srv-power', label: s?.status === 'online' ? 'خاموش‌کردن سرور' : 'روشن‌کردن سرور', icon: '⏻', payload: '' },
        { id: 'srv-restart', label: 'ری‌استارت سرور', icon: '🔄' },
        { id: 'srv-backup', label: 'بکاپ فوری', icon: '💾' },
        { id: 'srv-save', label: 'ذخیرهٔ جهان', icon: '💽', cmd: '/save-all' },
        { id: 'srv-whitelist-on', label: 'وایت‌لیست روشن', icon: '🔒', cmd: '/whitelist on' },
        { id: 'srv-whitelist-off', label: 'وایت‌لیست خاموش', icon: '🔓', cmd: '/whitelist off' },
        { id: 'srv-say', label: 'اعلان در سرور', icon: '📢', cmd: '/say سرور تا لحظاتی دیگر ری‌استارت می‌شود' },
        { id: 'srv-gc', label: 'بهینه‌سازی حافظه', icon: '⚙️', cmd: '/gc' },
      ].map(a => `<button class="ov-btn" data-act="${a.id}" data-label="${esc(a.label)}" data-cmd="${esc(a.cmd || '')}"><span class="ov-btn-icon">${a.icon}</span><span>${esc(a.label)}</span></button>`).join('')}</div>`;
    }
    case 'support': return `
      <div class="ov-support">
        <form class="ov-form wide" data-ovform="support-ask" data-label="پرسش از پشتیبانی">
          <input name="q" placeholder="مشکلت را بنویس یا بگو…" autocomplete="off">
          <button class="ov-btn"><span class="ov-btn-icon">🆘</span><span>پرسش از پشتیبان آتا</span></button>
        </form>
        <div class="ov-grid small">${SUPPORT_TOPICS.slice(0, 6).map(t => `<button class="ov-btn" data-act="support-topic" data-label="${esc(t.q)}" data-payload="${t.id}"><span class="ov-btn-icon">❓</span><span>${esc(t.q)}</span></button>`).join('')}</div>
      </div>`;
    default: return '';
  }
}

function supportAnswer(q) {
  const norm = normalizeFa(q);
  let topic = SUPPORT_TOPICS.find(t => norm.includes(normalizeFa(t.q).split(' ').slice(0, 2).join(' ')));
  if (!topic) {
    if (/(لگ|کند|تیک|افت)/.test(norm)) topic = SUPPORT_TOPICS.find(t => t.id === 'lag');
    else if (/(وصل|ورود|وارد)/.test(norm)) topic = SUPPORT_TOPICS.find(t => t.id === 'connect');
    else if (/(پشتیبان|بکاپ)/.test(norm)) topic = SUPPORT_TOPICS.find(t => t.id === 'backup');
    else if (/(اوپ|اپراتور|دسترسی)/.test(norm)) topic = SUPPORT_TOPICS.find(t => t.id === 'op');
    else if (/(صدا|میکروفن|گفتار|صوتی)/.test(norm)) topic = SUPPORT_TOPICS.find(t => t.id === 'voice');
  }
  const a = topic ? topic.a : 'سؤالت را ثبت کردم. کارشناس‌های میزبان معمولاً در چند دقیقه جواب می‌دهند. تا آن موقع، سؤال‌های پرتکرار را ببین.';
  world.chat = [...world.chat.slice(-5), { who: 'پشتیبان آتا', text: a }];
  renderChat();
  say(a);
  state.supportLog = [...state.supportLog.slice(-30), { at: Date.now(), q, a }];
  save();
}

// ———————————————————————————————— پشتیبانی ————————————————————————————————
function supportPage() {
  return `
  <div class="page-head"><h2>مرکز پشتیبانی آتا</h2><p>پاسخ سریع برای سؤال‌های رایج؛ با صدا هم می‌توانی بپرسی.</p></div>
  <div class="support-grid">
    <div class="support-topics">
      ${SUPPORT_TOPICS.map(t => `
        <details class="support-item"><summary>${t.q}</summary><p>${t.a}</p>
          <button class="mini" data-action="support-read" data-text="${esc(t.a)}">🔊 بخوان</button>
        </details>`).join('')}
    </div>
    <form class="support-chat" data-form="support-form">
      <h3>گفتگو با پشتیبان آتا 💬</h3>
      <div class="support-log" id="support-log">${state.supportLog.slice(-4).map(l => `<div class="sl-q">🙋 ${esc(l.q)}</div><div class="sl-a">🤖 ${esc(l.a)}</div>`).join('')}</div>
      <div class="chat-input-row">
        <input name="q" placeholder="مثلاً: سرور لگ دارد…" autocomplete="off">
        <button class="f-btn small primary">پرسش</button>
      </div>
    </form>
  </div>`;
}

// ———————————————————————————————— تنظیمات ————————————————————————————————
function settingsPage() {
  const st = state.settings;
  return `
  <div class="page-head"><h2>تنظیمات و صدا</h2><p>صدای ${NARRATOR.name}: ${NARRATOR.desc}</p></div>
  <div class="settings-grid">
    <div class="set-card">
      <h3>🔊 گفتار همراه</h3>
      <label class="set-row"><span>راهنمای صوتی همهٔ صفحه‌ها</span><button class="toggle ${st.narration ? 'on' : ''}" data-action="toggle-narration">روشن</button></label>
      <label class="set-row"><span>پخش خودکار هنگام ورود به هر صفحه</span><button class="toggle ${st.autoplay ? 'on' : ''}" data-action="toggle-autoplay">روشن</button></label>
      <button class="f-btn small ghost" data-action="test-voice">🎧 آزمایش صدای ${NARRATOR.name}</button>
    </div>
    <div class="set-card">
      <h3>🎤 کنترل صوتی</h3>
      <label class="set-row"><span>کنترل با صدا و کلمهٔ بیداری «${st.wakeWord}»</span><button class="toggle ${st.voiceControl ? 'on' : ''}" data-action="toggle-voice-control">روشن</button></label>
      <p class="muted">در بازی: بگو «${st.wakeWord}» تا اورلای باز شود، اسم زبانه را بگو تا باز شود، متن دکمه را بگو تا اجرا شود.</p>
    </div>
    <div class="set-card">
      <h3>👤 پروفایل اپراتور</h3>
      <form data-form="profile-form" class="set-row">
        <input name="operator" value="${esc(state.profile.operator)}" maxlength="16" placeholder="نام اپراتور">
        <button class="f-btn small primary">ذخیره</button>
      </form>
      <button class="f-btn small ghost danger-ghost" data-action="reset-all">🗑 حذف همهٔ داده‌ها و شروع دوباره</button>
    </div>
  </div>`;
}

// ———————————————————————————————— رویدادهای سراسری ————————————————————————————————
document.addEventListener('click', (e) => {
  const el = e.target.closest('[data-action]');
  if (!el) return;
  handleAction(el.dataset.action, el.dataset, el);
});

document.addEventListener('change', (e) => {
  const el = e.target.closest('[data-prop]');
  if (!el) return;
  const s = currentServer();
  if (!s) return;
  const key = el.dataset.prop;
  s.props[key] = el.type === 'number' ? Number(el.value) : el.value;
  save();
  toast(`«${key}» ذخیره شد ✓`);
});

document.addEventListener('keydown', (e) => {
  if (e.key !== 'Enter') return;
  if (page === 'wizard' && WIZARD_STEPS[wizardStep] === 'name' && e.target?.id === 'wiz-name') {
    e.preventDefault();
    wizardAction('wiz-next', {});
  }
});

document.addEventListener('submit', (e) => {
  const form = e.target.closest('[data-form]');
  if (!form) return;
  e.preventDefault();
  const kind = form.dataset.form;
  const data = Object.fromEntries(new FormData(form).entries());
  if (kind === 'console-cmd' && data.cmd?.trim()) {
    const s = currentServer();
    if (s) {
      const res = executeCommand(s, world || createGameWorld(), data.cmd, state.profile.operator);
      s.consoleLog = [...(s.consoleLog || []).slice(-80), { at: Date.now(), kind: res.ok ? 'ok' : 'err', text: `[${state.profile.operator}] ${data.cmd} → ${res.message}` }];
      save();
      toast(res.message);
      render();
      setTimeout(() => $('#console-view')?.scrollTo({ top: 9e9 }), 30);
    }
    form.reset();
  }
  if (kind === 'support-form' && data.q?.trim()) {
    const norm = normalizeFa(data.q);
    let topic = SUPPORT_TOPICS.find(t => norm.includes(normalizeFa(t.q).split(' ').slice(0, 2).join(' ')));
    if (!topic) {
      if (/(لگ|کند)/.test(norm)) topic = SUPPORT_TOPICS.find(t => t.id === 'lag');
      else if (/(وصل|ورود)/.test(norm)) topic = SUPPORT_TOPICS.find(t => t.id === 'connect');
      else if (/(بکاپ|پشتیبان)/.test(norm)) topic = SUPPORT_TOPICS.find(t => t.id === 'backup');
    }
    const a = topic?.a || 'سؤالت ثبت شد؛ پشتیبان‌های میزبان به‌زودی جواب می‌دهند. سؤال‌های پرتکرار را هم ببین.';
    state.supportLog = [...state.supportLog.slice(-30), { at: Date.now(), q: data.q, a }];
    save(); say(a); render();
  }
  if (kind === 'profile-form') {
    state.profile.operator = (data.operator || '').trim() || 'اپراتور';
    save(); toast(`نام اپراتور: ${state.profile.operator}`); render();
  }
  if (kind === 'ptero-connect') pteroConnect(data);
  if (kind === 'vps-connect') vpsConnect(data);
  if (kind === 'real-console-cmd' && data.cmd?.trim()) {
    realCmdDirect(data.cmd);
    const input = form.querySelector('input[name="cmd"]');
    if (input) input.value = '';
    const view = $('#console-view');
    if (view) { const d = document.createElement('div'); d.className = 'c-line ok'; d.textContent = `[${state.profile.operator}] ${data.cmd}`; view.appendChild(d); view.scrollTop = view.scrollHeight; }
  }
});

// ———————————————————————————————— عملیات سرور ————————————————————————————————
function togglePower() {
  const s = currentServer();
  if (!s) return;
  const wasOnline = s.status === 'online';
  if (wasOnline) {
    s.status = 'stopping';
    s.consoleLog = [...(s.consoleLog || []), { at: Date.now(), kind: 'sys', text: 'فرمان خاموش‌شدن از پنل آتا دریافت شد…' }];
    setTimeout(() => { s.status = 'offline'; s.fakePlayers = []; save(); if (page === 'panel') render(); }, 900);
    toast('سرور در حال خاموش‌شدن… ⏹');
  } else {
    s.status = 'starting';
    setTimeout(() => {
      s.status = 'online';
      s.deployedAt = s.deployedAt || Date.now();
      s.fakePlayers = ['Aria_MC', 'Kimia_Gamer'];
      s.consoleLog = [...(s.consoleLog || []), ...bootConsoleLog(s, state.profile.operator)];
      save(); if (page === 'panel') render();
    }, 1200);
    toast('سرور در حال روشن‌شدن… ▶');
  }
  save(); if (page === 'panel') render();
}

function restartServer() {
  const s = currentServer();
  if (!s) return;
  s.status = 'starting'; save(); render();
  setTimeout(() => { s.status = 'online'; s.consoleLog = [...(s.consoleLog || []), { at: Date.now(), kind: 'ok', text: 'سرور دوباره راه‌اندازی شد ✅' }]; save(); if (page === 'panel') render(); }, 1400);
  toast('راه‌اندازی دوباره آغاز شد 🔄');
}

function srvCmd(cmd) {
  const s = currentServer();
  if (!s) return;
  const res = executeCommand(s, world || createGameWorld(), cmd, state.profile.operator);
  s.consoleLog = [...(s.consoleLog || []).slice(-80), { at: Date.now(), kind: res.ok ? 'ok' : 'err', text: `[پنل] ${cmd} → ${res.message}` }];
  save(); toast(res.message); if (state.settings.narration) say(res.message); render();
}

function togglePropBool(key) {
  const s = currentServer();
  if (!s) return;
  s.props[key] = !s.props[key];
  save(); toast(`«${key}» → ${s.props[key] ? 'روشن' : 'خاموش'}`); render();
}

function backupNow() {
  const s = currentServer();
  if (!s) return;
  const b = createBackup(s);
  save(); toast(`پشتیبان ساخته شد (${faNum(b.sizeMb)} مگابایت) 💾`);
  say('نسخهٔ پشتیبان با موفقیت ساخته شد.');
  render();
}

function backupRestore(id) {
  const s = currentServer();
  const b = s?.backups.find(x => x.id === id);
  if (!b) return;
  toast('بازگردانی پشتیبان آغاز شد…');
  setTimeout(() => { toast('جهان از پشتیبان بازیابی شد ✅'); say('جهان از نسخهٔ پشتیبان بازیابی شد.'); }, 1200);
}

// شبیه‌ساز زندهٔ کنسول برای سرورهای آنلاین
setInterval(() => {
  let changed = false;
  for (const s of state.servers) {
    if (s.status === 'online') {
      s.consoleLog = [...(s.consoleLog || []).slice(-80), randomConsoleHint(s, state.profile.operator)];
      s.tps = Math.round((19.4 + Math.random() * 0.6) * 10) / 10;
      changed = true;
    }
  }
  if (changed) save();
  if (page === 'panel' && panelTab === 'console') render();
}, 15000);

// ———————————————————————————————— کنترل صوتی ————————————————————————————————
function micOnce() {
  recognizer = recognizer || createRecognizer({ onState: () => {} });
  if (!recognizer.available()) {
    toast('شناسایی گفتار در این مرورگر نیست؛ در اپ اندروید آتا فعال است.');
    const text = prompt('می‌توانی فرمان را بنویسی (مثلاً: آتا، زبانه آیتم‌ها):');
    if (text) handleVoiceText(text);
    return;
  }
  recognizer.listen({
    onResult: (text) => { toast(`شنیدم: ${text}`); handleVoiceText(text); },
  });
}

function stopListeningOnce() { recognizer?.stop(); listeningLoop = false; }

function handleVoiceText(text) {
  const said = normalizeFa(text);
  const wake = normalizeFa(state.settings.wakeWord || 'آتا');
  // ناوبری صفحه‌ها
  const navCandidates = [
    { id: 'home', phrases: ['خانه', 'منو', 'اصلی'] },
    { id: 'hosts', phrases: ['میزبان', 'میزبان ها', 'هاست'] },
    { id: 'wizard', phrases: ['ساخت سرور', 'سرور جدید', 'سرور بساز'] },
    { id: 'servers', phrases: ['سرورهای من', 'سرورها', 'فهرست سرور'] },
    { id: 'support', phrases: ['پشتیبانی', 'کمک'] },
    { id: 'settings', phrases: ['تنظیمات'] },
    { id: 'game', phrases: ['حالت بازی', 'بازی', 'ورود به بازی'] },
    { id: 'real', phrases: ['اتصال واقعی', 'اتصال', 'میزبان واقعی'] },
  ];
  if (page === 'game' && overlay) {
    if (said.includes(normalizeFa('ببند')) && (overlay.isOpen() || overlay.getActiveTab())) { overlay.close(); say('پنل بسته شد.'); return; }
    if (said.includes(wake) && !overlay.isOpen()) { overlay.open(); say('اورلای آتا باز شد. کدام زبانه؟'); return; }
    const tabId = matchVoiceCommand(text, OVERLAY_TABS.map(t => ({ id: t.id, phrases: [t.name, ...t.voice] })));
    if (tabId && overlay.isOpen()) { overlay.toggleTab(tabId); return; }
    // دکمه‌های زبانهٔ باز
    const active = overlay.getActiveTab();
    if (active) {
      const actions = overlayActionsFor(active);
      const actId = matchVoiceCommand(text, actions.map(a => ({ id: a.id, phrases: [a.label] })));
      if (actId) {
        const a = actions.find(x => x.id === actId);
        runOverlayAction({ id: a.id, label: a.label, cmd: a.cmd || null, payload: a.payload || null }, active);
        return;
      }
    }
  }
  const nav = matchVoiceCommand(text, navCandidates);
  if (nav) { go(nav); return; }
  say('متوجه نشدم. بگو: خانه، سرورها، ساخت سرور، یا در بازی بگو آتا.');
}

function overlayActionsFor(tabId) {
  switch (tabId) {
    case 'quick': return QUICK_ACTIONS;
    case 'world': return WORLD_ACTIONS;
    case 'player': return PLAYER_ACTIONS;
    case 'powers': return POWER_ACTIONS;
    case 'items': return ITEM_GROUPS.flatMap(g => g.items.map(it => ({ id: 'give-item', label: it.label, payload: `${it.id}:${it.count || 1}` })));
    case 'mobs': return MOB_GROUPS.flatMap(g => g.mobs.map(m => ({ id: 'summon-mob', label: m.label, payload: m.id })));
    default: return [];
  }
}

// ———————————————————————————————— راه‌اندازی ————————————————————————————————
voice = createVoice({ onCaption: caption });
const hash = (typeof location !== 'undefined' && location.hash || '').replace('#', '');
if (PAGE_META[hash]) page = hash;
render();

// برای آزمون‌ها و ابزارهای بیرونی
window.__atta = {
  get state() { return state; },
  go, handleVoiceText, runCommand,
  openOverlay: () => overlay?.open(),
  toggleTab: (id) => overlay?.toggleTab(id),
  closeOverlay: () => overlay?.close(),
  overlayOpen: () => overlay?.isOpen(),
  activeTab: () => overlay?.getActiveTab(),
  sendRealCommand, realPower, setActiveReal,
  setAutoClose: undefined,
};

// پل اورلای بومی اندروید: دستورهای لمس‌شده روی خود بازی از اینجا وارد اپ می‌شوند
async function bridgeExec(cmd) {
  if (!cmd) return { sent: false, reason: 'empty' };
  if (cmd.startsWith('@power:')) { await realPower(cmd.slice(7)); return { sent: true }; }
  if (cmd === '@backup') {
    const t = state.real.activeReal;
    try {
      if (t?.kind === 'ptero') await ptero().createBackup(t.id);
      else if (t?.kind === 'vps') await vps().backup();
      else return { sent: false, reason: 'no-target' };
      return { sent: true };
    } catch (e) { return { sent: false, reason: e.message }; }
  }
  // چند دستور با «;» پشت‌سرهم
  let last = { sent: false, reason: 'empty' };
  for (const part of String(cmd).split(';')) {
    if (part.trim()) last = await sendRealCommand(part.trim());
  }
  return last;
}

window.__attaBridge = {
  receive(payload) {
    let d = payload;
    if (typeof payload === 'string') { try { d = JSON.parse(payload); } catch { return; } }
    if (!d || !d.cmd) return;
    bridgeExec(d.cmd).then(r => {
      if (typeof AndroidBridge !== 'undefined' && AndroidBridge.bridgeResult) {
        AndroidBridge.bridgeResult(r.sent ? `✓ «${d.label || d.cmd}» به سرور واقعی رسید` : `✗ به سرور واقعی نرسید: ${r.reason}`);
      }
    });
  },
};

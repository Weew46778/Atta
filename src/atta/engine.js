// آتا — موتور اپ: ذخیره‌سازی، چرخهٔ عمر سرور، اجرای دستورها و شبیه‌ساز کنسول
// همهٔ منطق به‌صورت تابع خالص؛ رابط کاربری و آزمون‌ها هر دو از همین ماژول استفاده می‌کنند.

import { DEPLOY_STAGES, DEFAULT_PROPERTIES, SERVER_CONSOLE_HINTS, OVERLAY_TABS, faNum } from './data.js';

const STORAGE_KEY = 'atta-console-v1';

// ———————————————————————————————— ذخیره‌سازی ————————————————————————————————
export function defaultState() {
  return {
    profile: { operator: 'اپراتور' },
    settings: {
      narration: true,        // گفتار راهنما در همهٔ صفحه‌ها
      autoplay: true,         // پخش خودکار راهنما هنگام ورود به هر صفحه
      voiceControl: false,    // کنترل صوتی و کلمهٔ بیداری
      wakeWord: 'آتا',
      ttsEngine: 'bundled',   // bundled = صدای زن فارسی همراه اپ، بدون وابستگی به گوشی
    },
    servers: [],
    supportLog: [],
    // اتصال‌های واقعی: پنل پتروداکتیل میزبان‌ها و عامل وی‌پی‌اس
    real: {
      ptero: null,            // {panelUrl, apiKey, servers:[...]}
      vps: null,              // {agentUrl, token, info}
      activeReal: null,       // {kind:'ptero', id} یا {kind:'vps'} — هدف دستورهای اورلای
    },
  };
}

export function loadState(storage) {
  const base = defaultState();
  if (!storage) return base;
  try {
    const raw = storage.getItem(STORAGE_KEY);
    if (!raw) return base;
    const parsed = JSON.parse(raw);
    return {
      ...base, ...parsed,
      profile: { ...base.profile, ...(parsed.profile || {}) },
      settings: { ...base.settings, ...(parsed.settings || {}) },
      servers: Array.isArray(parsed.servers) ? parsed.servers : [],
      supportLog: Array.isArray(parsed.supportLog) ? parsed.supportLog : [],
      real: { ...base.real, ...(parsed.real || {}) },
    };
  } catch { return base; }
}

export function saveState(storage, state) {
  if (!storage) return true;
  try { storage.setItem(STORAGE_KEY, JSON.stringify(state)); return true; }
  catch { return false; }
}

// ———————————————————————————————— ساخت سرور ————————————————————————————————
let idCounter = 0;
export function newServerId() {
  idCounter += 1;
  return `srv-${Date.now().toString(36)}-${idCounter}-${Math.random().toString(36).slice(2, 6)}`;
}

export function buildServer(config) {
  const name = String(config.name || '').trim();
  const errors = validateServerConfig(config);
  if (errors.length) return { ok: false, errors };
  const host = config.host;
  const port = config.edition === 'java' ? 25565 : 19132;
  const address = config.host.id === 'eternos-vps'
    ? `${slug(name)}.eternos.ir`
    : `${slug(name)}.${host.latin.toLowerCase().replace(/[^a-z]/g, '')}.host`;
  const server = {
    id: newServerId(),
    name,
    hostId: host.id,
    hostName: host.name,
    edition: config.edition,
    version: config.version,
    gamemode: config.gamemode,
    difficulty: config.difficulty,
    slots: config.slots,
    ram: config.ram,
    region: config.region,
    address,
    port,
    javaPort: config.edition !== 'bedrock' ? 25565 : null,
    status: 'offline',
    tps: 20, playersOnline: 0,
    motd: 'به سرور آتا خوش آمدید! 🌟',
    createdAt: Date.now(),
    deployedAt: null,
    props: { ...DEFAULT_PROPERTIES, 'server-name': name, 'server-port': port, 'max-players': config.slots, gamemode: config.gamemode, difficulty: config.difficulty, 'level-seed': config.seed || '' },
    ops: [config.profile || 'اپراتور'],
    whitelist: [],
    banned: [],
    backups: [],
    consoleLog: [],
  };
  return { ok: true, server };
}

export function validateServerConfig(config) {
  const errors = [];
  const name = String(config.name || '').trim();
  if (!name) errors.push('نام سرور را بنویس؛ مثلاً «سرور دوستان».');
  else if (name.length > 24) errors.push('نام سرور باید کوتاه‌تر از ۲۴ حرف باشد.');
  if (!config.host) errors.push('یکی از میزبان‌ها را انتخاب کن.');
  if (!['bedrock', 'java', 'crossplay'].includes(config.edition)) errors.push('نسخهٔ بازی را انتخاب کن.');
  if (!config.version) errors.push('نسخهٔ سرور را انتخاب کن.');
  if (!Number.isFinite(config.slots) || config.slots < 2 || config.slots > 100) errors.push('تعداد بازیکن باید بین ۲ تا ۱۰۰ باشد.');
  if (!Number.isFinite(config.ram) || config.ram < 1) errors.push('مقدار رم را انتخاب کن.');
  if (!config.region) errors.push('منطقهٔ دیتاسنتر را انتخاب کن.');
  return errors;
}

export function slug(name) {
  const latin = String(name).toLowerCase()
    .replace(/[^a-z0-9]+/g, '')
    .slice(0, 12);
  return latin || `srv${Math.floor(Math.random() * 900 + 100)}`;
}

// ———————————————————————————————— استقرار ————————————————————————————————
export function deployStages(server) {
  return DEPLOY_STAGES.map(s => ({
    ...s,
    label: s.id === 'download'
      ? (server.edition === 'java' ? 'دانلود هستهٔ سرور جاوا (Paper)' : server.edition === 'crossplay' ? 'دانلود هستهٔ جاوا + پل گیسر' : 'دانلود هستهٔ سرور بدراک (BDS)')
      : s.id === 'geyser' && server.edition === 'bedrock'
        ? 'رد شدن از پل کراس‌پلی (نیازی نیست)' : s.label,
    skip: s.id === 'geyser' && server.edition === 'bedrock',
  }));
}

export function consoleLine(server, text, kind = 'info') {
  return { at: Date.now(), kind, text };
}

export function bootConsoleLog(server, operator) {
  return [
    consoleLine(server, `[آتا] اتصال به پنل میزبان «${server.hostName}» برقرار شد`, 'atta'),
    consoleLine(server, `[${server.address}:${server.port}] راه‌اندازی هستهٔ ${server.edition === 'java' ? 'Paper' : 'Bedrock Dedicated Server'} نسخهٔ ${server.version}`, 'sys'),
    consoleLine(server, 'بارگذاری جهان «' + (server.props['level-name'] || 'world') + '» …', 'sys'),
    consoleLine(server, 'تنظیمات دقیق از پنل آتا اعمال شد (server.properties)', 'atta'),
    consoleLine(server, `سرور روی ${server.address}:${server.port} آماده است ✅`, 'ok'),
    consoleLine(server, `${operator} به‌عنوان اپراتور وارد شد`, 'join'),
  ];
}

export function randomConsoleHint(server, operator) {
  const hint = SERVER_CONSOLE_HINTS[Math.floor(Math.random() * SERVER_CONSOLE_HINTS.length)];
  return consoleLine(server, hint.replaceAll('Operator_Joined', operator), 'chat');
}

// ———————————————————————————————— اجرای دستورها ————————————————————————————————
// خروجی: {ok, message, events} — events برای شبیه‌ساز بازی و کنسول مصرف می‌شود.
export function executeCommand(server, world, raw, operator = 'اپراتور') {
  const cmd = String(raw || '').trim();
  if (!cmd) return { ok: false, message: 'دستوری نوشته نشده است.', events: [] };
  const parts = cmd.replace(/^\//, '').split(/\s+/);
  const name = parts[0].toLowerCase();
  const arg = i => parts[i];
  const events = [];
  const done = (message, extra = []) => ({ ok: true, message, events: [...events, ...extra] });

  switch (name) {
    case 'gamemode': {
      const modes = { survival: 'بقا', creative: 'خلاقانه', adventure: 'ماجراجویی', spectator: 'تماشاگر', s: 'بقا', c: 'خلاقانه', a: 'ماجراجویی', sp: 'تماشاگر', '0': 'بقا', '1': 'خلاقانه', '2': 'ماجراجویی', '3': 'تماشاگر' };
      const m = modes[(arg(1) || '').toLowerCase()];
      if (!m) return { ok: false, message: 'حالت بازی نامعتبر است؛ مثال: /gamemode creative', events };
      events.push({ type: 'gamemode', value: m });
      return done(`حالت بازی به «${m}» تغییر کرد.`, events);
    }
    case 'time': {
      const values = { day: 1000, noon: 6000, sunset: 12000, night: 13000, midnight: 18000, sunrise: 23000 };
      if (arg(1) === 'set') {
        const v = values[arg(2)] ?? Number(arg(2));
        if (!Number.isFinite(v)) return { ok: false, message: 'زمان نامعتبر است؛ مثال: /time set day', events };
        events.push({ type: 'time', value: arg(2), tick: v });
        return done(`زمان جهان تنظیم شد: ${arg(2)}`, events);
      }
      return { ok: false, message: 'کاربرد: /time set <day|night|noon|...>', events };
    }
    case 'weather': {
      const w = ['clear', 'rain', 'thunder'].includes(arg(1)) ? arg(1) : null;
      if (!w) return { ok: false, message: 'کاربرد: /weather <clear|rain|thunder>', events };
      events.push({ type: 'weather', value: w });
      return done(w === 'clear' ? 'آسمان صاف شد ☀️' : w === 'rain' ? 'باران شروع شد 🌧️' : 'رعد و برق آغاز شد ⛈️', events);
    }
    case 'gamerule': {
      const rule = arg(1), value = arg(2);
      if (!rule) return { ok: false, message: 'کاربرد: /gamerule <نام قانون> <true|false>', events };
      if (value !== undefined && !['true', 'false'].includes(value)) return { ok: false, message: 'مقدار قانون باید true یا false باشد.', events };
      if (server.props) server.props[`gamerule-${rule}`] = value ?? 'true';
      events.push({ type: 'gamerule', rule, value: value ?? 'true' });
      return done(`قانون ${rule} = ${value ?? 'true'} اعمال شد.`, events);
    }
    case 'give': {
      const item = arg(2) || arg(1);
      if (!item) return { ok: false, message: 'کاربرد: /give @s <آیتم> [تعداد]', events };
      const count = Number(arg(3)) || 1;
      events.push({ type: 'give', item, count });
      return done(`«${item}» ×${faNum(count)} به کیف شما اضافه شد 🎒`, events);
    }
    case 'effect': {
      if (arg(2) === 'clear') { events.push({ type: 'effects-clear' }); return done('همهٔ افکت‌ها پاک شد 🧼', events); }
      const fx = arg(2);
      if (!fx) return { ok: false, message: 'کاربرد: /effect @s <افکت> <ثانیه> <قدرت>', events };
      const dur = Number(arg(3)) || 30, amp = Number(arg(4)) || 1;
      events.push({ type: 'effect', fx, dur, amp });
      return done(`افکت «${fx}» به مدت ${faNum(dur)} ثانیه فعال شد ✨`, events);
    }
    case 'tp': case 'teleport': {
      let coords = null, who = null;
      if (['@s', '@p', '@a', '@r'].includes(arg(1)) || (arg(1) || '').startsWith('@')) {
        coords = [Number(arg(2)), Number(arg(3)), Number(arg(4))];
        if (coords.some(Number.isNaN)) coords = null;
      } else if (Number.isFinite(Number(arg(1)))) {
        coords = [Number(arg(1)), Number(arg(2)), Number(arg(3))];
        if (coords.some(n => !Number.isFinite(n))) coords = null;
        if (coords === null && Number.isFinite(Number(arg(1)))) return { ok: false, message: 'مختصات کامل بنویس؛ مثال: /tp @s 100 70 -200', events };
      } else {
        who = arg(1);
      }
      if (!coords && !who) return { ok: false, message: 'کاربرد: /tp @s <x> <y> <z> یا /tp <بازیکن>', events };
      if (coords) {
        const [x, y, z] = coords;
        events.push({ type: 'tp', x, y, z });
        return done(`به مختصات ${faNum(x)}، ${faNum(y)}، ${faNum(z)} منتقل شدی 🚀`, events);
      }
      events.push({ type: 'tp', to: who });
      return done(`به «${who}» منتقل شدی 🚀`, events);
    }
    case 'heal': { events.push({ type: 'heal' }); return done('جان شما کامل شد ❤️', events); }
    case 'feed': { events.push({ type: 'feed' }); return done('گرسنگی برطرف شد 🍖', events); }
    case 'clear': { events.push({ type: 'clear-inv' }); return done('کیف شما خالی شد 🧹', events); }
    case 'xp': {
      const lv = Number(String(arg(1)).replace(/L?$/i, ''));
      if (!Number.isFinite(lv)) return { ok: false, message: 'کاربرد: /xp <تعداد> @s', events };
      events.push({ type: 'xp', levels: lv });
      return done(`${faNum(lv)} سطح تجربه اضافه شد 🟢`, events);
    }
    case 'ability': {
      const ab = arg(2), val = arg(3);
      if (!ab) return { ok: false, message: 'کاربرد: /ability @s <mayfly|invulnerable|mute> <true|false>', events };
      events.push({ type: 'ability', ability: ab, value: val !== 'false' });
      return done(`توانایی «${ab}» ${val !== 'false' ? 'فعال' : 'غیرفعال'} شد.`, events);
    }
    case 'summon': {
      const mob = arg(1);
      if (!mob) return { ok: false, message: 'کاربرد: /summon <نام ماب>', events };
      events.push({ type: 'summon', mob });
      return done(`«${mob}» همین‌جا احضار شد 🐾`, events);
    }
    case 'kill': {
      const sel = arg(1) || '@s';
      if (sel.startsWith('@e')) { events.push({ type: 'killall' }); return done('ماب‌های انتخاب‌شده حذف شدند 🧨', events); }
      events.push({ type: 'kill' }); return done('حذف انجام شد.', events);
    }
    case 'op': {
      const who = String(arg(1) || operator).replace(/^@s$/, operator);
      if (!server.ops.includes(who)) server.ops.push(who);
      events.push({ type: 'op', who });
      return done(`«${who}» اپراتور شد ⭐`, events);
    }
    case 'deop': {
      const who = String(arg(1) || operator).replace(/^@s$/, operator);
      server.ops = server.ops.filter(o => o !== who);
      events.push({ type: 'deop', who });
      return done(`«${who}» دیگر اپراتور نیست.`, events);
    }
    case 'kick': {
      const who = arg(1);
      if (!who) return { ok: false, message: 'کاربرد: /kick <نام بازیکن>', events };
      events.push({ type: 'kick', who });
      return done(`«${who}» از سرور بیرون انداخته شد.`, events);
    }
    case 'ban': {
      const who = arg(1);
      if (!who) return { ok: false, message: 'کاربرد: /ban <نام بازیکن>', events };
      if (!server.banned.includes(who)) server.banned.push(who);
      events.push({ type: 'ban', who });
      return done(`«${who}» مسدود شد 🔨`, events);
    }
    case 'pardon': {
      const who = arg(1);
      server.banned = server.banned.filter(b => b !== who);
      return done(`«${who}» از مسدودی خارج شد.`, events);
    }
    case 'whitelist': {
      const mode = arg(1), who = arg(2);
      if (mode === 'add' && who) { if (!server.whitelist.includes(who)) server.whitelist.push(who); return done(`«${who}» به وایت‌لیست اضافه شد ✅`, events); }
      if (mode === 'remove' && who) { server.whitelist = server.whitelist.filter(w => w !== who); return done(`«${who}» از وایت‌لیست حذف شد.`, events); }
      if (mode === 'on') { server.props['white-list'] = true; return done('وایت‌لیست روشن شد؛ فقط افراد مجاز وارد می‌شوند.', events); }
      if (mode === 'off') { server.props['white-list'] = false; return done('وایت‌لیست خاموش شد.', events); }
      return { ok: false, message: 'کاربرد: /whitelist <add|remove|on|off>', events };
    }
    case 'say': case 'announce': {
      const msg = parts.slice(1).join(' ');
      if (!msg) return { ok: false, message: 'پیامی بنویس؛ مثال: /say سلام به همه', events };
      events.push({ type: 'say', msg });
      return done(`پیام شما در کل سرور پخش شد 📢: «${msg}»`, events);
    }
    case 'difficulty': {
      const map = { peaceful: 'صلح‌آمیز', easy: 'آسان', normal: 'معمولی', hard: 'سخت', '0': 'صلح‌آمیز', '1': 'آسان', '2': 'معمولی', '3': 'سخت' };
      const d = map[(arg(1) || '').toLowerCase()];
      if (!d) return { ok: false, message: 'کاربرد: /difficulty <peaceful|easy|normal|hard>', events };
      server.props.difficulty = d === 'صلح‌آمیز' ? 'peaceful' : d === 'آسان' ? 'easy' : d === 'معمولی' ? 'normal' : 'hard';
      events.push({ type: 'difficulty', value: d });
      return done(`سختی بازی: ${d}`, events);
    }
    case 'seed': { events.push({ type: 'seed' }); return done(`سید جهان: ${server.props['level-seed'] || '4242 Atta 1370'}`, events); }
    case 'list': { events.push({ type: 'list' }); return done(`بازیکن‌های آنلاین: شما (${operator})${server.fakePlayers?.length ? '، ' + server.fakePlayers.join('، ') : ''} — ${faNum(1 + (server.fakePlayers?.length || 0))} نفر`, events); }
    case 'save-all': { events.push({ type: 'save' }); return done('جهان با موفقیت ذخیره شد 💾', events); }
    case 'stop': { events.push({ type: 'stop' }); return done('سرور با فرمان شما خاموش می‌شود… 🔴', events); }
    case 'restart': { events.push({ type: 'restart' }); return done('راه‌اندازی دوبارهٔ سرور آغاز شد 🔄', events); }
    case 'gc': { events.push({ type: 'gc' }); return done('حافظه آزاد شد؛ تی‌پی‌اس بهبود یافت ⚙️', events); }
    case 'spawnpoint': { events.push({ type: 'spawnpoint' }); return done('نقطهٔ اسپاون همین‌جا ثبت شد 🧭', events); }
    case 'help': {
      const tabs = OVERLAY_TABS.map(t => t.name).join('، ');
      return done(`راهنمای آتا: زبانه‌های اورلای — ${tabs}. هر دکمه یک دستور آماده دارد؛ در زبانهٔ «دستور» هر فرمان دلخواه را بنویس.`, events);
    }
    default:
      return { ok: false, message: `دستور «/${name}» شناخته نشد. /help را ببین یا از دکمه‌های آمادهٔ اورلای استفاده کن.`, events };
  }
}

// ———————————————————————————————— پشتیبان‌گیری ————————————————————————————————
export function createBackup(server) {
  const backup = { id: `bk-${Date.now().toString(36)}`, at: Date.now(), sizeMb: Math.round((server.ram * 90 + Math.random() * 200) * 10) / 10 };
  server.backups = [backup, ...server.backups].slice(0, 10);
  return backup;
}

// ———————————————————————————————— تطبیق گفتار با اکشن ————————————————————————————————
// برای کنترل صوتی: نرمال‌سازی متن فارسی و پیدا کردن بهترین تطبیق.
export function normalizeFa(text) {
  return String(text || '')
    .toLowerCase()
    .replace(/[\u200c\u200f\u200e]/g, ' ')
    .replace(/[يی]/g, 'ی').replace(/[كک]/g, 'ک').replace(/[ۀة]/g, 'ه')
    .replace(/[۰-۹]/g, d => String('۰۱۲۳۴۵۶۷۸۹'.indexOf(d)))
    .replace(/[أإآٱٲٳٵ]/g, 'ا')
    .replace(/[^\u0600-\u06FFa-z0-9\s.:/@_-]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

export function matchVoiceCommand(utterance, candidates) {
  // candidates: [{id, phrases:[...]}] — phrases از قبل با حروف کوچک و بدون نشانه باشند بهتر است
  const said = normalizeFa(utterance);
  if (!said) return null;
  let best = null, bestScore = 0;
  for (const cand of candidates) {
    for (const phrase of cand.phrases) {
      const p = normalizeFa(phrase);
      if (!p) continue;
      let score = 0;
      if (said === p) score = p.length * 3 + 10;
      else if (said.includes(p)) score = p.length * 2;
      else if (p.includes(said) && said.length >= 2) score = said.length * 2 - 1;
      if (score > bestScore) { bestScore = score; best = cand.id; }
    }
  }
  return bestScore >= 3 ? best : null;
}

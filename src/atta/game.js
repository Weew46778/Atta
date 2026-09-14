// آتا — شبیه‌ساز صحنهٔ بازی: دنیای بلوکی ساده که به دستورها واکنش نشان می‌دهد
// در «تک‌نفره» همین صحنه زمین بازی است؛ روی سرور واقعی، همین دستورها به کنسول سرور می‌روند.

import { faNum } from './data.js';

export function createGameWorld(serverName = 'دنیای آتا') {
  return {
    name: serverName,
    timeTick: 1000,           // 0..24000 مثل ماینکرافت
    timeFrozen: false,
    weather: 'clear',
    lightning: 0,
    player: { x: 0, y: 64, z: 0, hp: 20, food: 20, mode: 'بقا', flying: false, effects: [], gamemodeId: 'survival' },
    hotbar: [],
    mobs: [],
    chat: [{ who: 'آتا', text: 'دنیای تک‌نفره آماده است. از لبهٔ چپ بکش تا اورلای باز شود.' }],
    hud: { op: true },
  };
}

const MODE_MAP = { بقا: 'survival', خلاقانه: 'creative', ماجراجویی: 'adventure', تماشاگر: 'spectator' };
export const MOB_ICON = {
  horse: '🐎', donkey: '🫏', cat: '🐈', wolf: '🐕', cow: '🐄', sheep: '🐑', chicken: '🐔', fox: '🦊',
  parrot: '🦜', villager: '🧑‍🌾', iron_golem: '🤖', allay: '🧚', zombie: '🧟', skeleton: '💀', creeper: '🟢',
  spider: '🕷️', enderman: '🌌', witch: '🧙', pillager: '🏹', blaze: '🔥', ghast: '👻', warden: '🕳️',
  ender_dragon: '🐉', wither: '☠️', elder_guardian: '🐡', ravager: '🐗',
};

export function applyEvents(world, events, operator = 'اپراتور') {
  const notices = [];
  for (const ev of events || []) {
    switch (ev.type) {
      case 'time': {
        if (typeof ev.tick === 'number') world.timeTick = ev.tick % 24000;
        break;
      }
      case 'weather': world.weather = ev.value; if (ev.value === 'thunder') world.lightning = 3; break;
      case 'gamemode': {
        world.player.mode = ev.value;
        world.player.gamemodeId = MODE_MAP[ev.value] || 'survival';
        world.player.flying = ev.value === 'خلاقانه' || ev.value === 'تماشاگر';
        notices.push(`حالت بازی: ${ev.value}`);
        break;
      }
      case 'heal': world.player.hp = 20; notices.push('جان کامل شد ❤️'); break;
      case 'feed': world.player.food = 20; notices.push('سیر شدی 🍖'); break;
      case 'clear-inv': world.hotbar = []; notices.push('کیف خالی شد'); break;
      case 'give': {
        world.hotbar = [...world.hotbar.slice(-8), { id: ev.item, count: ev.count || 1 }];
        notices.push(`${ev.item} ×${faNum(ev.count || 1)} دریافت شد`);
        break;
      }
      case 'effect': {
        world.player.effects = [...world.player.effects.filter(f => f.fx !== ev.fx), { fx: ev.fx, until: Date.now() + ev.dur * 1000, amp: ev.amp }];
        break;
      }
      case 'effects-clear': world.player.effects = []; break;
      case 'tp': {
        if (Number.isFinite(ev.x)) { world.player.x = ev.x; world.player.y = ev.y; world.player.z = ev.z; }
        else { world.player.x = 0; world.player.y = 70; world.player.z = 0; }
        break;
      }
      case 'ability': {
        if (ev.ability === 'mayfly') world.player.flying = ev.value;
        if (ev.ability === 'invulnerable') { world.player.hp = 20; notices.push(ev.value ? 'رویین‌تن شدی 🛡️' : 'حالت محافظ خاموش شد'); }
        break;
      }
      case 'xp': notices.push(`${faNum(ev.levels)} سطح تجربه گرفتی`); break;
      case 'summon': {
        world.mobs = [...world.mobs.slice(-14), { id: `${ev.mob}-${Date.now() % 10000}`, type: ev.mob, icon: MOB_ICON[ev.mob] || '🐾', x: Math.round(Math.random() * 240 + 60), y: Math.round(Math.random() * 60 + 120) }];
        break;
      }
      case 'killall': world.mobs = []; notices.push('ماب‌ها حذف شدند'); break;
      case 'say': world.chat = [...world.chat.slice(-5), { who: operator, text: ev.msg, announce: true }]; break;
      case 'stop': notices.push('سرور در حال خاموش‌شدن…'); break;
      case 'restart': notices.push('راه‌اندازی دوباره…'); break;
      default: break;
    }
    if (ev.type !== 'say') {
      // اعلان‌ها در چت هم دیده شوند
    }
  }
  return notices;
}

// ———————————————————————————————— رسم صحنه ————————————————————————————————
function skyColors(t) {
  if (t < 1000 || t > 23000) return ['#87c5f0', '#cfe9fb'];      // صبح
  if (t < 6000) return ['#6db6ef', '#bfe3fa'];                    // روز
  if (t < 12000) return ['#5a9fe0', '#ffd9a0'];                   // غروب
  if (t < 13000) return ['#2b3a6b', '#7d5a8f'];                   // شفق
  if (t < 23000) return ['#0b1030', '#1a2350'];                   // شب
  return ['#87c5f0', '#cfe9fb'];
}

export function drawWorld(ctx, world, w, h, now = Date.now()) {
  const t = world.timeTick % 24000;
  const [top, bottom] = skyColors(t);
  const night = t > 13000 && t < 23000;
  const grad = ctx.createLinearGradient(0, 0, 0, h);
  grad.addColorStop(0, top); grad.addColorStop(1, bottom);
  ctx.fillStyle = grad; ctx.fillRect(0, 0, w, h);

  // خورشید / ماه
  ctx.font = `${Math.round(h / 8)}px serif`;
  ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
  ctx.fillText(night ? '🌙' : '☀️', w * 0.82, h * 0.18);

  // ستاره‌ها در شب
  if (night) {
    ctx.fillStyle = 'rgba(255,255,255,.8)';
    for (let i = 0; i < 24; i++) {
      const sx = (i * 97) % w, sy = (i * 53) % (h / 2);
      ctx.fillRect(sx, sy, 2, 2);
    }
  }

  // تپه‌ها و زمین بلوکی
  const groundY = h * 0.72;
  ctx.fillStyle = night ? '#2f6b2f' : '#5aa843';
  ctx.beginPath(); ctx.moveTo(0, groundY);
  for (let x = 0; x <= w; x += 8) ctx.lineTo(x, groundY - Math.sin(x / 90) * 12 - Math.sin(x / 37) * 5);
  ctx.lineTo(w, h); ctx.lineTo(0, h); ctx.closePath(); ctx.fill();
  ctx.fillStyle = night ? '#4a3524' : '#7a5230';
  ctx.fillRect(0, groundY + 22, w, h - groundY);
  // بافت بلوکی زمین
  ctx.strokeStyle = 'rgba(0,0,0,.12)'; ctx.lineWidth = 1;
  for (let x = 0; x < w; x += 24) { ctx.strokeRect(x, groundY + 22, 24, 24); ctx.strokeRect(x + 12, groundY + 46, 24, 24); }

  // درخت‌ها
  ctx.font = `${Math.round(h / 7)}px serif`;
  ctx.fillText('🌳', w * 0.15, groundY - h * 0.06);
  ctx.fillText('🌲', w * 0.62, groundY - h * 0.07);
  ctx.fillText('🌳', w * 0.9, groundY - h * 0.05);

  // ماب‌ها
  ctx.font = `${Math.round(h / 11)}px serif`;
  for (const mob of world.mobs) {
    const mx = (mob.x / 400) * w, my = groundY - 8;
    ctx.fillText(mob.icon, mx, my);
  }

  // بازیکن
  ctx.font = `${Math.round(h / 8)}px serif`;
  const px = w / 2 + ((world.player.x % 200) / 200) * w * 0.2;
  ctx.fillText(world.player.flying ? '🕊️' : '🧍', px, groundY - 16);

  // باران / رعد
  if (world.weather !== 'clear') {
    ctx.strokeStyle = 'rgba(180,210,255,.5)'; ctx.lineWidth = 1.4;
    const drops = world.weather === 'thunder' ? 140 : 90;
    const shift = (now / 6) % 40;
    for (let i = 0; i < drops; i++) {
      const rx = (i * 61 + shift * 3) % w, ry = (i * 37 + shift * 9) % h;
      ctx.beginPath(); ctx.moveTo(rx, ry); ctx.lineTo(rx - 3, ry + 14); ctx.stroke();
    }
    if (world.weather === 'thunder' && Math.floor(now / 900) % 4 === 0) {
      ctx.fillStyle = 'rgba(255,255,255,.35)'; ctx.fillRect(0, 0, w, h);
    }
  }
}

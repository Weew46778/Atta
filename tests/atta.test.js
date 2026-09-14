// آزمون‌های واحد آتا: میزبان‌ها، ساخت سرور، استقرار، اجرای دستورها و تطبیق گفتار
import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
  HOSTS, EDITIONS, VERSIONS, GAMEMODES, DIFFICULTIES, REGIONS, OVERLAY_TABS,
  QUICK_ACTIONS, ITEM_GROUPS, MOB_GROUPS, POWER_ACTIONS, SUPPORT_TOPICS, PROPERTY_GROUPS, DEFAULT_PROPERTIES, DEPLOY_STAGES,
} from '../src/atta/data.js';
import {
  defaultState, loadState, saveState, buildServer, validateServerConfig, deployStages,
  executeCommand, createBackup, normalizeFa, matchVoiceCommand,
} from '../src/atta/engine.js';

function memStorage() {
  const map = new Map();
  return { getItem: k => (map.has(k) ? map.get(k) : null), setItem: (k, v) => map.set(k, String(v)), removeItem: k => map.delete(k) };
}

const validConfig = (over = {}) => ({
  name: 'سرور دوستان', host: HOSTS[0], edition: 'bedrock', version: '1.21.111',
  gamemode: 'survival', difficulty: 'normal', slots: 10, ram: 4, region: 'ir-tehran', ...over,
});

test('دست‌کم ۱۰ میزبان با فیلدهای کامل وجود دارد', () => {
  assert.ok(HOSTS.length >= 10, `تعداد میزبان‌ها: ${HOSTS.length}`);
  for (const h of HOSTS) {
    for (const f of ['id', 'name', 'latin', 'price', 'ram', 'locations', 'panel', 'ping', 'uptime', 'support']) {
      assert.ok(h[f], `فیلد ${f} در میزبان ${h.id} کم است`);
    }
    assert.equal(typeof h.bedrock, 'boolean');
    assert.ok(h.locations.length >= 1);
    assert.ok(h.ram.length >= 1);
  }
  assert.ok(HOSTS.some(h => h.id === 'eternos-vps'));
  assert.ok(HOSTS.filter(h => h.free).length >= 2, 'حداقل دو میزبان رایگان');
});

test('زبانه‌های اورلای: رنگ یکتا، نام و نام مستعار صوتی', () => {
  assert.ok(OVERLAY_TABS.length >= 8);
  const ids = new Set(OVERLAY_TABS.map(t => t.id));
  const colors = new Set(OVERLAY_TABS.map(t => t.color));
  assert.equal(ids.size, OVERLAY_TABS.length);
  assert.equal(colors.size, OVERLAY_TABS.length);
  for (const t of OVERLAY_TABS) {
    assert.ok(t.voice.length >= 1);
    assert.ok(t.icon && t.desc);
  }
});

test('اعتبارسنجی پیکربندی ساخت سرور', () => {
  assert.deepEqual(validateServerConfig(validConfig()), []);
  assert.ok(validateServerConfig(validConfig({ name: '' })).length);
  assert.ok(validateServerConfig(validConfig({ slots: 1 })).length);
  assert.ok(validateServerConfig(validConfig({ slots: 500 })).length);
  assert.ok(validateServerConfig(validConfig({ host: null })).length);
  assert.ok(validateServerConfig(validConfig({ edition: 'x' })).length);
});

test('ساخت سرور: آدرس، پورت و پیش‌فرض‌ها', () => {
  const { ok, server } = buildServer(validConfig());
  assert.ok(ok);
  assert.equal(server.port, 19132);
  assert.equal(server.status, 'offline');
  assert.equal(server.props['server-name'], 'سرور دوستان');
  assert.equal(server.props['max-players'], 10);
  assert.ok(server.address.includes('eternos.ir'));

  const java = buildServer(validConfig({ edition: 'java', host: HOSTS.find(h => h.id === 'pebblehost') }));
  assert.ok(java.ok);
  assert.equal(java.server.port, 25565);

  const cross = buildServer(validConfig({ edition: 'crossplay' }));
  assert.equal(cross.server.port, 19132);
  assert.equal(cross.server.javaPort, 25565);

  const bad = buildServer(validConfig({ name: '' }));
  assert.equal(bad.ok, false);
  assert.ok(bad.errors.length);
});

test('مراحل استقرار کامل و ترتیب درست است؛ گیسر برای بدراک رد می‌شود', () => {
  const { server } = buildServer(validConfig());
  const stages = deployStages(server);
  assert.equal(stages.length, DEPLOY_STAGES.length);
  assert.equal(stages[0].id, 'order');
  assert.equal(stages[stages.length - 1].id, 'done');
  const geyser = stages.find(s => s.id === 'geyser');
  assert.equal(geyser.skip, true);
  const cross = deployStages(buildServer(validConfig({ edition: 'crossplay' })).server);
  assert.equal(cross.find(s => s.id === 'geyser').skip, false);
  assert.ok(stages.every(s => typeof s.ms === 'number' && s.ms > 0));
});

function serverFor() {
  return buildServer(validConfig()).server;
}

test('اجرای دستورها: حالت بازی، زمان، آب‌وهوا', () => {
  const s = serverFor();
  let r = executeCommand(s, null, '/gamemode creative');
  assert.ok(r.ok);
  assert.equal(r.events[0].value, 'خلاقانه');
  r = executeCommand(s, null, '/time set night');
  assert.equal(r.events[0].type, 'time');
  r = executeCommand(s, null, '/weather thunder');
  assert.equal(r.events[0].value, 'thunder');
  assert.equal(executeCommand(s, null, '/weather snow').ok, false);
  assert.equal(executeCommand(s, null, '/gamemode nonsense').ok, false);
  assert.equal(executeCommand(s, null, '   ').ok, false);
});

test('اجرای دستورها: آیتم، افکت، تله‌پورت و مختصات', () => {
  const s = serverFor();
  const give = executeCommand(s, null, '/give @s diamond_sword 3');
  assert.equal(give.events[0].type, 'give');
  assert.equal(give.events[0].count, 3);
  const fx = executeCommand(s, null, '/effect @s speed 300 2 true');
  assert.equal(fx.events[0].fx, 'speed');
  const clear = executeCommand(s, null, '/effect @s clear');
  assert.equal(clear.events[0].type, 'effects-clear');
  const tp = executeCommand(s, null, '/tp @s 100 70 -200');
  assert.deepEqual([tp.events[0].x, tp.events[0].y, tp.events[0].z], [100, 70, -200]);
  assert.equal(executeCommand(s, null, '/tp @s 5').ok, false);
});

test('اجرای دستورها: اوپ، وایت‌لیست، بن و وضعیت سرور ذخیره می‌شود', () => {
  const s = serverFor();
  executeCommand(s, null, '/op Aria_MC');
  assert.ok(s.ops.includes('Aria_MC'));
  executeCommand(s, null, '/deop Aria_MC');
  assert.ok(!s.ops.includes('Aria_MC'));
  executeCommand(s, null, '/whitelist add Kimia');
  assert.ok(s.whitelist.includes('Kimia'));
  executeCommand(s, null, '/ban Griefer99');
  assert.ok(s.banned.includes('Griefer99'));
  executeCommand(s, null, '/pardon Griefer99');
  assert.ok(!s.banned.includes('Griefer99'));
  const rule = executeCommand(s, null, '/gamerule keepInventory true');
  assert.ok(rule.ok);
  assert.equal(s.props['gamerule-keepInventory'], 'true');
});

test('دستور ناشناخته پیام راهنما دارد', () => {
  const r = executeCommand(serverFor(), null, '/fly');
  assert.equal(r.ok, false);
  assert.match(r.message, /شناخته نشد|راهنما/);
});

test('بکاپ: ساخت، سقف ۱۰ نسخه و بازگردانی امن', () => {
  const s = serverFor();
  for (let i = 0; i < 14; i++) createBackup({ ...s });
  createBackup(s);
  assert.ok(s.backups.length <= 10);
  assert.ok(s.backups[0].id && s.backups[0].sizeMb > 0);
});

test('ذخیره و بازیابی وضعیت با خطای احتمالی', () => {
  const st = memStorage();
  const s = defaultState();
  s.servers = [serverFor()];
  assert.ok(saveState(st, s));
  const loaded = loadState(st);
  assert.equal(loaded.servers.length, 1);
  assert.equal(loaded.servers[0].name, 'سرور دوستان');
  st.setItem('atta-console-v1', '{خراب');
  assert.ok(loadState(st).servers.length === 0);
});

test('نرمال‌سازی فارسی برای گفتار', () => {
  assert.equal(normalizeFa('آیتم‌ها'), normalizeFa('ایتم ها'));
  assert.equal(normalizeFa('می‌کنم'), normalizeFa('می کنم'));
  assert.equal(normalizeFa('ٱب'), normalizeFa('اب'));
  assert.equal(normalizeFa('۱۲۳'), '123');
});

test('تطبیق فرمان صوتی با زبانه‌ها و دکمه‌ها', () => {
  const tabs = OVERLAY_TABS.map(t => ({ id: t.id, phrases: [t.name, ...t.voice] }));
  assert.equal(matchVoiceCommand('زبانه آیتم‌ها را باز کن', tabs), 'items');
  assert.equal(matchVoiceCommand('توانایی', tabs), 'powers');
  assert.equal(matchVoiceCommand('می‌خوام ماب احضار کنم', tabs), 'mobs');
  assert.equal(matchVoiceCommand('مدیریت سرور', tabs), 'admin');
  assert.equal(matchVoiceCommand('کمک', tabs), 'support');
  const quick = QUICK_ACTIONS.map(a => ({ id: a.id, phrases: [a.label] }));
  assert.equal(matchVoiceCommand('شب شود', quick), 'time-night');
  assert.equal(matchVoiceCommand('یه چیز بی‌ربط عجیب', quick), null);
});

test('کاتالوگ‌های اورلای کامل و غیرخالی‌اند', () => {
  assert.ok(QUICK_ACTIONS.length >= 10);
  assert.ok(POWER_ACTIONS.length >= 10);
  assert.ok(ITEM_GROUPS.flatMap(g => g.items).length >= 30);
  assert.ok(MOB_GROUPS.flatMap(g => g.mobs).length >= 20);
  assert.ok(SUPPORT_TOPICS.length >= 6);
  assert.ok(PROPERTY_GROUPS.flatMap(g => g.props).length >= 20);
  assert.ok(Object.keys(DEFAULT_PROPERTIES).length >= 20);
  assert.ok(EDITIONS.length === 3 && VERSIONS.length >= 5 && GAMEMODES.length === 4 && DIFFICULTIES.length === 4 && REGIONS.length >= 5);
});

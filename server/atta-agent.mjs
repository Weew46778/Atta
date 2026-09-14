#!/usr/bin/env node
// «عامل آتا» — اسکریپت واقعی سمت وی‌پی‌اس (اترنوس یا هر سرور لینوکسی)
// یک‌بار اجرا می‌شود و از آن پس اپ اندروید آتا سرور ماینکرافت واقعی را کامل مدیریت می‌کند:
// ساخت (دانلود هستهٔ بدراک یا پیپر)، روشن/خاموش/ری‌استارت، کنسول، دستور و بکاپ.
// بدون هیچ وابستگی خارجی — فقط Node 18+.
//
// اجرا:  node atta-agent.mjs --port 8790 --token <رمز-طولانی> [--dir ~/atta]
//
import http from 'node:http';
import { spawn } from 'node:child_process';
import { promises as fs } from 'node:fs';
import path from 'node:path';
import os from 'node:os';

// ————— تنظیمات —————
const args = Object.fromEntries(process.argv.slice(2).reduce((acc, cur, i, arr) => {
  if (cur.startsWith('--')) acc.push([cur.slice(2), arr[i + 1]?.startsWith('--') ? 'true' : arr[i + 1]]);
  return acc;
}, []));
const PORT = Number(args.port || process.env.ATTA_PORT || 8790);
const TOKEN = args.token || process.env.ATTA_TOKEN || '';
const ROOT = path.resolve((args.dir || process.env.ATTA_DIR || path.join(os.homedir(), 'atta')).replace(/^~(?=\/|$)/, os.homedir()));
if (!TOKEN || TOKEN.length < 12) { console.error('یک توکن طولانی بده: --token <حداقل ۱۲ کاراکتر>'); process.exit(1); }

const SERVER_DIR = path.join(ROOT, 'server');
const WORLD_DIR = path.join(SERVER_DIR, 'worlds');
const BACKUP_DIR = path.join(ROOT, 'backups');
const LOG_FILE = path.join(ROOT, 'server-console.log');
const STATE = { proc: null, edition: null, version: null, startedAt: null, motd: '', port: 19132 };

const json = (res, code, obj) => { res.writeHead(code, { 'Content-Type': 'application/json; charset=utf-8', 'Access-Control-Allow-Origin': '*' }); res.end(JSON.stringify(obj)); };
const authed = (req) => (req.headers.authorization || '').replace(/^Bearer\s+/i, '') === TOKEN;
const readBody = (req) => new Promise((resolve) => { let b = ''; req.on('data', c => { b += c; if (b.length > 1e6) req.destroy(); }); req.on('end', () => { try { resolve(JSON.parse(b || '{}')); } catch { resolve({}); } }); });
async function appendLog(line) { try { await fs.appendFile(LOG_FILE, `[${new Date().toISOString()}] ${line}\n`); } catch { /* noop */ } }

// ————— ساخت واقعی سرور —————
async function ensureServer({ edition, version }) {
  await fs.mkdir(SERVER_DIR, { recursive: true });
  if (edition === 'bedrock') {
    const bin = path.join(SERVER_DIR, 'bedrock_server');
    if (!(await exists(bin))) {
      const url = await findBedrockUrl(version);
      const zipPath = path.join(SERVER_DIR, 'bds.zip');
      await download(url, zipPath);
      await run('unzip', ['-o', zipPath, '-d', SERVER_DIR]);
      await fs.rm(zipPath, { force: true });
    }
    writeServerProperties();
    STATE.edition = 'bedrock'; STATE.version = version; STATE.port = 19132;
  } else {
    const jar = path.join(SERVER_DIR, `paper-${version}.jar`);
    if (!(await exists(jar))) {
      const build = await latestPaperBuild(version);
      await download(`https://api.papermc.io/v2/projects/paper/versions/${version}/builds/${build}/downloads/paper-${version}-${build}.jar`, jar);
    }
    if (!(await exists(path.join(SERVER_DIR, 'eula.txt')))) await fs.writeFile(path.join(SERVER_DIR, 'eula.txt'), 'eula=true\n');
    STATE.edition = 'java'; STATE.version = version; STATE.port = 25565;
  }
}

async function findBedrockUrl(version) {
  // فهرست نسخه‌های رسمی بدراک
  const res = await fetch('https://www.minecraft.net/en-us/download/server/bedrock/', { headers: { 'User-Agent': 'AttaAgent/1.0' } });
  const html = await res.text();
  const m = html.match(/https:\/\/[^"]*bin-linux\/bedrock-server-[^"]*\.zip/);
  if (!m) throw new Error('پیدا نکردن لینک دانلود بدراک؛ نسخهٔ دستی بده.');
  return m[0];
}
async function latestPaperBuild(version) {
  const res = await fetch(`https://api.papermc.io/v2/projects/paper/versions/${version}/builds`);
  const data = await res.json();
  const builds = data.builds || [];
  if (!builds.length) throw new Error(`نسخهٔ ${version} برای پیپر موجود نیست.`);
  return builds[builds.length - 1].build;
}
async function download(url, dest) {
  const res = await fetch(url);
  if (!res.ok) throw new Error(`دانلود ناموفق (${res.status}): ${url}`);
  await fs.writeFile(dest, Buffer.from(await res.arrayBuffer()));
}
async function run(cmd, argv) {
  return new Promise((resolve, reject) => {
    const p = spawn(cmd, argv); let err = '';
    p.stderr.on('data', d => { err += d; });
    p.on('close', code => code === 0 ? resolve() : reject(new Error(`${cmd} failed: ${err}`)));
    p.on('error', reject);
  });
}
const exists = async (p) => { try { await fs.access(p); return true; } catch { return false; } };

function writeServerProperties() {
  const props = [
    'server-name=Atta Server', 'gamemode=survival', 'difficulty=normal', 'allow-cheats=true',
    'max-players=10', 'online-mode=true', 'white-list=false', 'server-port=19132', 'server-portv6=19133',
    'view-distance=10', 'tick-distance=4', 'player-idle-timeout=30', 'level-name=world', 'pvp=true',
  ].join('\n') + '\n';
  return fs.writeFile(path.join(SERVER_DIR, 'server.properties'), props);
}

// ————— روشن/خاموش واقعی —————
function start() {
  if (STATE.proc) return { ok: true, message: 'سرور از قبل روشن است' };
  if (!STATE.edition) return { ok: false, error: 'اول سرور را بساز (پایانهٔ /create)' };
  const argv = STATE.edition === 'java'
    ? ['-Xms512M', '-Xmx2G', '-jar', `paper-${STATE.version}.jar`, 'nogui']
    : ['./bedrock_server'];
  const proc = spawn(argv[0] === './bedrock_server' ? path.join(SERVER_DIR, 'bedrock_server') : 'java', argv, { cwd: SERVER_DIR, stdio: ['pipe', 'pipe', 'pipe'] });
  STATE.proc = proc; STATE.startedAt = Date.now();
  const pipe = (stream, tag) => stream.on('data', d => { d.toString().split('\n').filter(Boolean).forEach(l => appendLog(`${tag} ${l}`)); });
  pipe(proc.stdout, 'OUT'); pipe(proc.stderr, 'ERR');
  proc.on('close', () => { STATE.proc = null; appendLog('سرور خاموش شد.'); });
  appendLog(`سرور ${STATE.edition} روشن شد.`);
  return { ok: true };
}
function stop() {
  if (!STATE.proc) return { ok: true, message: 'خاموش است' };
  if (STATE.edition === 'java') STATE.proc.stdin.write('stop\n');
  else STATE.proc.kill('SIGTERM');
  return { ok: true };
}

// ————— سرور HTTP —————
const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, 'http://localhost');
  if (req.method === 'OPTIONS') {
    res.writeHead(204, { 'Access-Control-Allow-Origin': '*', 'Access-Control-Allow-Headers': '*', 'Access-Control-Allow-Methods': '*' });
    return res.end();
  }
  if (!authed(req)) return json(res, 401, { error: 'توکن اشتباه است' });
  try {
    if (req.method === 'GET' && url.pathname === '/status') {
      const log = await fs.readFile(LOG_FILE, 'utf-8').catch(() => '');
      const lines = log.split('\n').filter(Boolean);
      return json(res, 200, { ok: true, running: !!STATE.proc, edition: STATE.edition, version: STATE.version, port: STATE.port, startedAt: STATE.startedAt, uptimeSec: STATE.proc ? Math.round((Date.now() - STATE.startedAt) / 1000) : 0, logTail: lines.slice(-5) });
    }
    if (req.method === 'POST' && url.pathname === '/create') {
      const body = await readBody(req);
      await ensureServer({ edition: body.edition === 'java' ? 'java' : 'bedrock', version: body.version || '1.21.111' });
      return json(res, 200, { ok: true, message: 'سرور واقعی ساخته و آماده شد', edition: STATE.edition, version: STATE.version, port: STATE.port });
    }
    if (req.method === 'POST' && url.pathname === '/power') {
      const body = await readBody(req);
      if (body.signal === 'start') return json(res, 200, start());
      if (body.signal === 'stop') return json(res, 200, stop());
      if (body.signal === 'restart') { stop(); setTimeout(start, 1500); return json(res, 200, { ok: true, message: 'ری‌استارت آغاز شد' }); }
      return json(res, 400, { error: 'signal نامعتبر' });
    }
    if (req.method === 'POST' && url.pathname === '/command') {
      const body = await readBody(req);
      if (!STATE.proc) return json(res, 400, { error: 'سرور خاموش است؛ اول روشن کن' });
      STATE.proc.stdin.write(String(body.command || '').replace(/^\//, '') + '\n');
      appendLog(`[اپراتور] ${body.command}`);
      return json(res, 200, { ok: true });
    }
    if (req.method === 'GET' && url.pathname === '/console') {
      const n = Number(url.searchParams.get('lines') || 120);
      const log = await fs.readFile(LOG_FILE, 'utf-8').catch(() => '');
      return json(res, 200, { lines: log.split('\n').filter(Boolean).slice(-n) });
    }
    if (req.method === 'POST' && url.pathname === '/backup') {
      await fs.mkdir(BACKUP_DIR, { recursive: true });
      const stamp = new Date().toISOString().replace(/[:.]/g, '-');
      const dest = path.join(BACKUP_DIR, `world-${stamp}`);
      await fs.cp(path.join(SERVER_DIR, 'world'), dest, { recursive: true, errorOnExist: false }).catch(() => fs.cp(WORLD_DIR, dest, { recursive: true }).catch(() => { throw new Error('جهانی برای بکاپ نیست'); }));
      return json(res, 200, { ok: true, path: dest, at: stamp });
    }
    json(res, 404, { error: 'مسیر پیدا نشد' });
  } catch (e) {
    json(res, 500, { error: String(e?.message || e) });
  }
});

server.listen(PORT, '0.0.0.0', () => {
  console.log(`عامل آتا آماده است: پورت ${PORT} — ریشهٔ داده: ${ROOT}`);
  console.log('نکتهٔ امنیتی: این پورت را فقط برای آدرس آی‌پی خودت در فایروال باز کن.');
});

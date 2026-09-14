// آزمون‌های اتصال واقعی آتا: کلاینت پتروداکتیل، عامل وی‌پی‌اس و مسیریابی دستور
// ترانسپورت موک است؛ قرارداد درخواست‌ها دقیقاً مطابق API رسمی پتروداکتیل بررسی می‌شود.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createPterodactylClient } from '../src/atta/real/pterodactyl.js';
import { createVpsAgentClient } from '../src/atta/real/vps-agent.js';
import { normalizePanelUrl } from '../src/atta/real/transport.js';

function mockTransport(routes) {
  const calls = [];
  return {
    calls,
    request: async (url, opts = {}) => {
      // مثل ترانسپورت واقعی، بدنه روی خط به رشتهٔ جیسون تبدیل می‌شود
      const body = opts.body == null ? null : (typeof opts.body === 'string' ? opts.body : JSON.stringify(opts.body));
      calls.push({ url, method: opts.method || 'GET', headers: opts.headers, body });
      const match = routes.find(r => url.includes(r.match) && (r.method || 'GET') === (opts.method || 'GET'));
      if (!match) return { ok: false, status: 404, text: '', json: null };
      if (match.status === 401) return { ok: false, status: 401, text: '', json: null };
      return { ok: true, status: 200, text: JSON.stringify(match.json || {}), json: match.json };
    },
  };
}

const SERVERS_PAYLOAD = {
  data: [{
    attributes: {
      identifier: 'abc12345', uuid: 'u-1', name: 'سرور اصلی', description: '',
      node: 'ir-tehran-1', limits: { memory: 4096 },
      relationships: { allocations: { data: [{ attributes: { ip: '185.120.10.5', port: 25565, is_default: true } }] } },
    },
  }],
};

test('نشانی پنل نرمال می‌شود و مسیرهای اضافی حذف می‌شوند', () => {
  assert.equal(normalizePanelUrl('panel.pebblehost.com'), 'https://panel.pebblehost.com');
  assert.equal(normalizePanelUrl('http://panel.test/server/abc123'), 'http://panel.test');
  assert.equal(normalizePanelUrl(''), null);
  assert.equal(normalizePanelUrl(':::'), null);
});

test('فهرست سرورها با هدر Authorization درست خوانده می‌شود', async () => {
  const t = mockTransport([{ match: '/api/client/', json: SERVERS_PAYLOAD }]);
  const client = createPterodactylClient({ panelUrl: 'https://panel.test', apiKey: 'ptlc_secret', transport: t });
  const servers = await client.listServers();
  assert.equal(servers.length, 1);
  assert.equal(servers[0].id, 'abc12345');
  assert.equal(servers[0].address, '185.120.10.5:25565');
  const call = t.calls[0];
  assert.ok(call.url.startsWith('https://panel.test/api/client/?per_page=50'));
  assert.equal(call.headers.Authorization, 'Bearer ptlc_secret');
});

test('کلید اشتباه پیام خطای روشن می‌دهد', async () => {
  const t = mockTransport([{ match: '/api/client/', method: 'GET', status: 401 }]);
  const client = createPterodactylClient({ panelUrl: 'https://panel.test', apiKey: 'bad', transport: t });
  await assert.rejects(() => client.listServers(), /کلید API اشتباه/);
});

test('فرمان قدرت با سیگنال درست و هدر درست ارسال می‌شود', async () => {
  const t = mockTransport([{ match: '/api/client/servers/abc12345/power', method: 'POST', json: {} }]);
  const client = createPterodactylClient({ panelUrl: 'https://panel.test', apiKey: 'k', transport: t });
  await client.power('abc12345', 'start');
  const call = t.calls.find(c => c.url.includes('/power'));
  assert.equal(JSON.parse(call.body).signal, 'start');
  assert.equal(call.headers['Content-Type'], 'application/json');
  await assert.rejects(() => client.power('abc12345', 'fly'), /نامعتبر/);
});

test('دستور اورلای به مسیر رسمی /command می‌رود و اسلش حذف می‌شود', async () => {
  const t = mockTransport([{ match: '/api/client/servers/abc12345/command', method: 'POST', json: {} }]);
  const client = createPterodactylClient({ panelUrl: 'https://panel.test', apiKey: 'k', transport: t });
  await client.sendCommand('abc12345', '/time set day');
  const call = t.calls.find(c => c.url.endsWith('/command'));
  assert.equal(JSON.parse(call.body).command, 'time set day');
});

test('سرور خاموش برای دستور پیام روشن می‌دهد', async () => {
  const t = { request: async () => ({ ok: false, status: 400, text: '', json: null }) };
  const client = createPterodactylClient({ panelUrl: 'https://panel.test', apiKey: 'k', transport: t });
  await assert.rejects(() => client.sendCommand('abc', 'list'), /خاموش/);
});

test('بکاپ واقعی: ساخت و فهرست', async () => {
  const t = mockTransport([
    { match: '/api/client/servers/abc/backups?per_page=20', json: { data: [{ attributes: { uuid: 'b1', name: 'backup-1', bytes: 1048576, created_at: '2026-09-14T00:00:00Z', is_successful: true } }] } },
    { match: '/backups', method: 'POST', json: { attributes: { uuid: 'b2' } } },
  ]);
  const client = createPterodactylClient({ panelUrl: 'https://panel.test', apiKey: 'k', transport: t });
  const list = await client.backups('abc');
  assert.equal(list[0].name, 'backup-1');
  const made = await client.createBackup('abc');
  assert.equal(made.uuid, 'b2');
});

test('عامل وی‌پی‌اس: وضعیت، قدرت، دستور و ساخت', async () => {
  const t = mockTransport([
    { match: '/status', json: { ok: true, running: false, edition: 'bedrock', version: '1.21.111', port: 19132 } },
    { match: '/power', method: 'POST', json: { ok: true } },
    { match: '/command', method: 'POST', json: { ok: true } },
    { match: '/create', method: 'POST', json: { ok: true, message: 'ساخته شد' } },
    { match: '/console?lines=120', json: { lines: ['a', 'b'] } },
  ]);
  const client = createVpsAgentClient({ agentUrl: '5.160.10.10:8790', token: 'tokentokentoken', transport: t });
  const st = await client.status();
  assert.equal(st.edition, 'bedrock');
  assert.ok(t.calls[0].url.startsWith('http://5.160.10.10:8790/status'));
  assert.equal(t.calls[0].headers.Authorization, 'Bearer tokentokentoken');
  await client.power('start');
  await client.sendCommand('/say hi');
  assert.equal(JSON.parse(t.calls.find(c => c.url.endsWith('/command')).body).command, 'say hi');
  const made = await client.createServer({ name: 'x', edition: 'bedrock', version: '1.21.111' });
  assert.equal(made.ok, true);
  const lines = await client.consoleTail();
  assert.deepEqual(lines, ['a', 'b']);
});

test('توکن اشتباه عامل خطای روشن می‌دهد', async () => {
  const t = { request: async () => ({ ok: false, status: 401, text: '', json: null }) };
  const client = createVpsAgentClient({ agentUrl: 'http://vps:8790', token: 'x', transport: t });
  await assert.rejects(() => client.status(), /توکن/);
});

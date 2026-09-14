// آتا — کلاینت واقعی پنل‌های پتروداکتیل (PebbleHost، FalixNodes، GodLike، Bisect و…)
// مطابق API رسمی پنل: /api/client — فهرست سرورها، قدرت، منابع، دستور، بکاپ و کنسول وب‌سوکت.
// منبع مسیرها: مخزن رسمی pterodactyl/panel فایل routes/api-client.php

import { createTransport, normalizePanelUrl } from './transport.js';

export function createPterodactylClient({ panelUrl, apiKey, transport = createTransport(), wsFactory = null } = {}) {
  const base = normalizePanelUrl(panelUrl);
  if (!base) throw new Error('نشانی پنل نامعتبر است');
  const headers = () => ({ Authorization: `Bearer ${apiKey}`, 'Content-Type': 'application/json' });
  const api = (path) => `${base}/api/client${path}`;

  async function listServers() {
    const res = await transport.request(api('/?per_page=50'), { headers: headers() });
    if (res.status === 401 || res.status === 403) throw new Error('کلید API اشتباه است یا دسترسی ندارد.');
    if (!res.ok) throw new Error(`پنل پاسخ ${res.status} داد؛ نشانی و کلید را بررسی کن.`);
    const data = res.json?.data || [];
    return data.map(parseServer).filter(Boolean);
  }

  function parseServer(item) {
    const a = item?.attributes;
    if (!a) return null;
    const alloc = (a.relationships?.allocations?.data || [])
      .map(x => x.attributes).find(x => x.is_default) || a.relationships?.allocations?.data?.[0]?.attributes;
    return {
      id: a.identifier,
      uuid: a.uuid,
      name: a.name,
      description: a.description || '',
      node: a.node || '',
      address: alloc ? `${alloc.ip}:${alloc.port}` : '',
      ip: alloc?.ip || '',
      port: alloc?.port || null,
      isOnline: null, // از /resources گرفته می‌شود
      limits: a.limits || {},
      sftp: a.sftp_details || null,
      raw: a,
    };
  }

  async function resources(serverId) {
    const res = await transport.request(api(`/servers/${serverId}/resources`), { headers: headers() });
    if (!res.ok) throw new Error('دریافت منابع سرور ناموفق بود.');
    const d = res.json?.data?.attributes || {};
    return { state: d.current_state || 'unknown', cpu: d.resources?.cpu_absolute || 0, ram: d.resources?.memory_bytes || 0, disk: d.resources?.disk_bytes || 0, netRx: d.resources?.network_rx_bytes || 0 };
  }

  async function power(serverId, signal) {
    if (!['start', 'stop', 'restart', 'kill'].includes(signal)) throw new Error('فرمان قدرت نامعتبر است');
    const res = await transport.request(api(`/servers/${serverId}/power`), { method: 'POST', headers: headers(), body: { signal } });
    if (!res.ok) throw new Error(signal === 'start' ? 'روشن‌کردن واقعی سرور ناموفق بود (شاید صف یا محدودیت میزبان).' : 'فرمان قدرت به سرور نرسید.');
    return true;
  }

  // ارسال واقعی دستور به کنسول سرور — همان چیزی که اورلای با یک لمس انجام می‌دهد
  async function sendCommand(serverId, command) {
    const res = await transport.request(api(`/servers/${serverId}/command`), { method: 'POST', headers: headers(), body: { command: String(command || '').replace(/^\//, '') } });
    if (res.status === 400) throw new Error('سرور الان خاموش است؛ اول روشنش کن.');
    if (!res.ok) throw new Error('دستور به سرور واقعی نرسید.');
    return true;
  }

  async function backups(serverId) {
    const res = await transport.request(api(`/servers/${serverId}/backups?per_page=20`), { headers: headers() });
    if (!res.ok) return [];
    return (res.json?.data || []).map(b => ({ uuid: b.attributes?.uuid, name: b.attributes?.name, bytes: b.attributes?.bytes, at: b.attributes?.created_at, completed: b.attributes?.is_successful }));
  }

  async function createBackup(serverId) {
    const res = await transport.request(api(`/servers/${serverId}/backups`), { method: 'POST', headers: headers(), body: {} });
    if (!res.ok) throw new Error('ساخت بکاپ واقعی ناموفق بود (سهمیهٔ میزبان را بررسی کن).');
    return res.json?.attributes || {};
  }

  // کنسول زندهٔ وب‌سوکت با بازاتصال خودکار
  function openConsole(serverId, { onLine = () => {}, onState = () => {}, maxRetries = 4 } = {}) {
    let ws = null, closed = false, retries = 0, aliveTimer = null;
    const W = wsFactory || ((url) => new WebSocket(url));

    async function connect() {
      if (closed) return;
      const cred = await transport.request(api(`/servers/${serverId}/websocket`), { headers: headers() });
      if (!cred.ok || !cred.json?.data?.token) { onState('token-failed'); return; }
      const { token, socket } = cred.json.data;
      try { ws = W(socket); } catch { onState('ws-error'); return; }
      ws.onopen = () => {
        ws.send(JSON.stringify({ event: 'auth', args: [token] }));
        if (aliveTimer) clearInterval(aliveTimer);
        aliveTimer = setInterval(() => { try { ws?.send(JSON.stringify({ event: 'send stats', args: [] })); } catch { /* noop */ } }, 20000);
      };
      ws.onmessage = (ev) => {
        let msg; try { msg = JSON.parse(ev.data); } catch { return; }
        if (msg.event === 'auth success') { retries = 0; onState('connected'); }
        if (msg.event === 'console output') (msg.args || []).forEach(l => onLine(String(l)));
        if (msg.event === 'daemon message') onLine(`[دمون] ${msg.args?.[0] ?? ''}`);
        if (msg.event === 'status') onState(`status:${msg.args?.[0]}`);
        if (msg.event === 'token expiring') reconnect();
      };
      ws.onclose = () => { if (!closed) reconnect(); };
      ws.onerror = () => { try { ws?.close(); } catch { /* noop */ } };
    }
    function reconnect() {
      if (closed || retries >= maxRetries) { onState('closed'); return; }
      retries += 1;
      onState('reconnecting');
      setTimeout(connect, 1500 * retries);
    }
    function close() { closed = true; if (aliveTimer) clearInterval(aliveTimer); try { ws?.close(); } catch { /* noop */ } }
    connect();
    return { close };
  }

  return { kind: 'pterodactyl', panelUrl: base, listServers, resources, power, sendCommand, backups, createBackup, openConsole };
}

// راهنمای ساخت کلید برای کاربر
export const PTERO_KEY_HELP = 'در پنل میزبانت وارد حساب شو، از منوی Account بخش API Keys را باز کن و یک کلید بساز. کلید با ptlc_ شروع می‌شود. این کلید فقط روی دستگاه خودت ذخیره می‌شود.';

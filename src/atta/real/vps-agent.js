// آتا — کلاینت «عامل آتا» روی وی‌پی‌اس (اترنوس یا هر وی‌پی‌اس دیگر)
// عامل یک اسکریپت سبک است که یک‌بار روی وی‌پی‌اس اجرا می‌شود و از آن پس اپ، سرور واقعی را کامل مدیریت می‌کند:
// ساخت سرور، روشن/خاموش، کنسول، دستور، بکاپ. اسکریپت عامل: server/atta-agent.mjs همین مخزن.

import { createTransport, normalizePanelUrl } from './transport.js';

export function createVpsAgentClient({ agentUrl, token, transport = createTransport() } = {}) {
  let origin = String(agentUrl || '').trim();
  if (!origin) throw new Error('نشانی عامل خالی است');
  if (!/^https?:\/\//i.test(origin)) origin = 'http://' + origin;
  try { origin = new URL(origin).origin; } catch { throw new Error('نشانی عامل نامعتبر است'); }
  const headers = () => ({ Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' });
  const api = (p) => `${origin}${p}`;

  async function status() {
    const res = await transport.request(api('/status'), { headers: headers(), timeout: 8000 });
    if (res.status === 401) throw new Error('توکن عامل اشتباه است.');
    if (!res.ok && res.status) throw new Error(`عامل پاسخ ${res.status} داد.`);
    if (!res.ok) throw new Error('به عامل روی وی‌پی‌اس نرسیدم؛ روشن‌بودن وی‌پی‌اس و پورت را بررسی کن.');
    return res.json;
  }

  async function power(signal) {
    const res = await transport.request(api('/power'), { method: 'POST', headers: headers(), body: { signal } });
    if (!res.ok) throw new Error('فرمان قدرت به عامل نرسید.');
    return res.json;
  }

  // دستور واقعی به کنسول سرور — مسیر یک‌لمسی اورلای برای وی‌پی‌اس
  async function sendCommand(command) {
    const res = await transport.request(api('/command'), { method: 'POST', headers: headers(), body: { command: String(command || '').replace(/^\//, '') } });
    if (!res.ok) throw new Error('دستور به سرور واقعی نرسید.');
    return res.json;
  }

  async function consoleTail(lines = 120) {
    const res = await transport.request(api(`/console?lines=${lines}`), { headers: headers() });
    if (!res.ok) return [];
    return res.json?.lines || [];
  }

  // ساخت واقعی سرور جدید روی وی‌پی‌اس: دانلود هسته، نوشتن تنظیمات و آماده‌سازی
  async function createServer({ name, edition, version }) {
    const res = await transport.request(api('/create'), { method: 'POST', headers: headers(), body: { name, edition, version }, timeout: 120000 });
    if (!res.ok) throw new Error(res.json?.error || 'ساخت سرور روی وی‌پی‌اس ناموفق بود.');
    return res.json;
  }

  async function backup() {
    const res = await transport.request(api('/backup'), { method: 'POST', headers: headers(), timeout: 120000 });
    if (!res.ok) throw new Error('بکاپ واقعی ساخته نشد.');
    return res.json;
  }

  return { kind: 'vps-agent', agentUrl: origin, status, power, sendCommand, consoleTail, createServer, backup };
}

export function agentInstallOneLiner({ host = 'آدرس-وی‌پی‌اس', port = 8790, token = 'یک-رمز-دلخواه-طولانی' } = {}) {
  return `curl -fsSL https://raw.githubusercontent.com/Weew46778/Atta/main/server/atta-agent.mjs -o atta-agent.mjs && nohup node atta-agent.mjs --port ${port} --token ${token} > atta-agent.log 2>&1 &`;
}

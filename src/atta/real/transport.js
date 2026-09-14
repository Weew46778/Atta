// آتا — لایهٔ اتصال واقعی: ترانسپورت قابل تزریق برای تست و استفادهٔ واقعی
// همهٔ کلاینت‌های واقعی (پتروداکتیل و عامل وی‌پی‌اس) از همین ترانسپورت استفاده می‌کنند.

export function createTransport({ fetchImpl = (...a) => fetch(...a), timeoutMs = 15000 } = {}) {
  async function request(url, { method = 'GET', headers = {}, body = null, timeout = timeoutMs } = {}) {
    const ctrl = typeof AbortController !== 'undefined' ? new AbortController() : null;
    const timer = ctrl ? setTimeout(() => ctrl.abort(), timeout) : null;
    try {
      const res = await fetchImpl(url, {
        method,
        headers: { Accept: 'application/json', ...headers },
        body: body == null ? undefined : (typeof body === 'string' ? body : JSON.stringify(body)),
        signal: ctrl ? ctrl.signal : undefined,
      });
      const text = await res.text();
      let json = null;
      try { json = text ? JSON.parse(text) : null; } catch { /* پاسخ غیر جیسون */ }
      return { ok: res.ok, status: res.status, text, json, headers: res.headers };
    } catch (e) {
      return { ok: false, status: 0, networkError: e?.name === 'AbortError' ? 'timeout' : (e?.message || 'network'), text: '', json: null };
    } finally {
      if (timer) clearTimeout(timer);
    }
  }
  return { request };
}

export function normalizePanelUrl(raw) {
  let url = String(raw || '').trim();
  if (!url) return null;
  if (!/^https?:\/\//i.test(url)) url = 'https://' + url;
  try {
    const u = new URL(url);
    u.hash = '';
    // حذف مسیرهای اضافی مثل /server/xxx — فقط ریشهٔ پنل
    return u.origin;
  } catch { return null; }
}

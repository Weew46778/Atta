// آزمون مرورگری اتصال واقعی: پنل پتروداکتیل موک می‌شود تا مسیر واقعی اپ سنجیده شود —
// اتصال، فهرست سرورها، انتخاب هدف اورلای، و ارسال واقعی دستور/قدرت با یک کلیک.
import { test, expect } from '@playwright/test';

const PANEL = 'https://panel.mock';
const CORS = {
  'access-control-allow-origin': '*',
  'access-control-allow-headers': '*',
  'access-control-allow-methods': 'GET,POST,PUT,DELETE,OPTIONS',
};

const SERVERS = {
  data: [{
    attributes: {
      identifier: 'abc12345', uuid: 'u-1', name: 'سرور واقعی من', description: '',
      node: 'eu-1', limits: { memory: 4096 },
      relationships: { allocations: { data: [{ attributes: { ip: '185.120.10.5', port: 19132, is_default: true } }] } },
    },
  }],
};

test('اتصال واقعی به پنل، هدف اورلای و ارسال دستور با یک کلیک', async ({ page }) => {
  const calls = { commands: [], powers: [] };
  await page.route('**/*', async (route) => {
    const req = route.request();
    const url = req.url();
    if (req.method() === 'OPTIONS' && url.startsWith(PANEL)) return route.fulfill({ status: 204, headers: CORS });
    if (!url.startsWith(PANEL)) return route.continue();
    if (url.includes('/api/client/?per_page=50') || url.includes('/api/client/?')) return route.fulfill({ json: SERVERS, headers: CORS });
    if (url.includes('/resources')) return route.fulfill({ json: { data: { attributes: { current_state: 'online', resources: { cpu_absolute: 3, memory_bytes: 1e9 } } } }, headers: CORS });
    if (url.includes('/command') && req.method() === 'POST') {
      calls.commands.push(req.postDataJSON());
      return route.fulfill({ status: 204, headers: CORS });
    }
    if (url.includes('/power') && req.method() === 'POST') {
      calls.powers.push(req.postDataJSON());
      return route.fulfill({ status: 204, headers: CORS });
    }
    if (url.includes('/websocket')) return route.fulfill({ json: { data: { token: 't', socket: 'wss://panel.mock/ws' } }, headers: CORS });
    return route.fulfill({ json: {}, headers: CORS });
  });

  await page.goto('/atta/index.html#real');
  await page.evaluate(() => localStorage.clear());
  await page.goto('/atta/index.html#real');

  // ورود نشانی پنل و کلید
  await page.fill('form[data-form="ptero-connect"] input[name="panelUrl"]', 'panel.mock');
  await page.fill('form[data-form="ptero-connect"] input[name="apiKey"]', 'ptlc_test_key');
  await page.click('form[data-form="ptero-connect"] button');
  await expect(page.locator('#ptero-status')).toContainText('سرور پیدا شد', { timeout: 10000 });
  await expect(page.locator('.real-section, #ptero-status')).toBeVisible();

  // سرور واقعی در فهرست سرورها دیده می‌شود
  await page.evaluate(() => window.__atta.go('servers'));
  await expect(page.locator('.real-section')).toContainText('سرور واقعی من');
  await expect(page.locator('.real-badge').first()).toContainText('واقعی');

  // هدف اورلای کردن و ارسال دستور واقعی با یک کلیک
  await page.click('[data-action="real-select"][data-id="abc12345"]');
  await page.evaluate(() => window.__atta.go('game'));
  await page.click('.ov-edge');
  await page.click('[data-testid="tab-quick"]');
  await page.click('.ov-btn[data-cmd="/time set day"]');
  await expect.poll(() => calls.commands.length, { timeout: 8000 }).toBeGreaterThan(0);
  expect(calls.commands[0].command).toBe('time set day');

  // قدرت واقعی از مسیر پل
  await page.evaluate(() => window.__atta.realPower('start'));
  await expect.poll(() => calls.powers.length, { timeout: 8000 }).toBe(1);
  expect(calls.powers[0].signal).toBe('start');
});

test('پل اورلای اندروید: دستور بومی به سرور واقعی می‌رسد', async ({ page }) => {
  const commands = [];
  await page.route('**/*', async (route) => {
    const req = route.request();
    if (req.method() === 'OPTIONS' && req.url().startsWith(PANEL)) return route.fulfill({ status: 204, headers: CORS });
    if (!req.url().startsWith(PANEL)) return route.continue();
    if (req.url().includes('/command') && req.method() === 'POST') {
      commands.push(req.postDataJSON());
      return route.fulfill({ status: 204, headers: CORS });
    }
    return route.fulfill({ json: SERVERS, headers: CORS });
  });

  await page.goto('/atta/index.html#real');
  await page.evaluate(() => localStorage.clear());
  await page.goto('/atta/index.html#real');
  await page.fill('form[data-form="ptero-connect"] input[name="panelUrl"]', 'panel.mock');
  await page.fill('form[data-form="ptero-connect"] input[name="apiKey"]', 'ptlc_test_key');
  await page.click('form[data-form="ptero-connect"] button');
  await expect(page.locator('#ptero-status')).toContainText('سرور پیدا شد', { timeout: 10000 });
  await page.evaluate(() => window.__atta.go('servers'));
  await page.click('[data-action="real-select"][data-id="abc12345"]');

  // همان چیزی که سرویس اورلای بومی می‌فرستد
  await page.evaluate(() => window.__attaBridge.receive({ label: 'روز شود', cmd: 'time set day' }));
  await expect.poll(() => commands.length, { timeout: 8000 }).toBe(1);
  expect(commands[0].command).toBe('time set day');

  // دستور چندگانه با «;»
  await page.evaluate(() => window.__attaBridge.receive({ label: 'کمان و تیر', cmd: 'give @s bow;give @s arrow 64' }));
  await expect.poll(() => commands.length, { timeout: 8000 }).toBe(3);
  expect(commands[1].command).toBe('give @s bow');
  expect(commands[2].command).toBe('give @s arrow 64');
});

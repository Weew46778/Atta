// آزمون مرورگری آتا: مسیر ساخت سرور و رفتار دقیق اورلای داخل بازی
import { test, expect } from '@playwright/test';

const ATTA = '/atta/index.html';

async function clearState(page) {
  await page.evaluate(() => localStorage.clear());
}

test('خانه و فهرست میزبان‌ها با حداقل ۱۰ میزبان', async ({ page }) => {
  await page.goto(ATTA);
  await clearState(page);
  await page.reload();
  await expect(page.locator('.home-grid .tile')).toHaveCount(5);
  await page.click('[data-action="go"][data-to="hosts"]');
  const cards = page.locator('.host-card');
  expect(await cards.count()).toBeGreaterThanOrEqual(10);
  await expect(page.locator('.host-card', { hasText: 'اترنوس وی‌پی‌اس' })).toBeVisible();
  await expect(page.locator('.assist-caption')).toBeVisible();
});

test('ساخت کامل سرور از جادوگر تا صفحهٔ اتصال', async ({ page }) => {
  test.setTimeout(90000);
  await page.goto(ATTA);
  await clearState(page);
  await page.reload();
  await page.click('[data-action="go"][data-to="hosts"]');
  await page.click('.host-card[data-host="aternos"]');
  await expect(page.locator('.wiz-step h2')).toContainText('اسم سرورت');
  await page.fill('#wiz-name', 'سرور تست آتا');
  await page.click('[data-action="wiz-next"]');
  await page.click('[data-action="wiz-edition"][data-id="bedrock"]');
  await page.click('[data-action="wiz-version"][data-id="1.21.111"]');
  await page.click('[data-action="wiz-gamemode"][data-id="survival"]');
  await page.click('[data-action="wiz-difficulty"][data-id="easy"]');
  await page.click('[data-action="wiz-next"]'); // slots
  await page.click('[data-action="wiz-ram"][data-id="2"]');
  await page.click('[data-action="wiz-region"][data-id="ir-tehran"]');
  await expect(page.locator('.review-card')).toContainText('سرور تست آتا');
  await page.click('[data-action="wiz-deploy"]');
  await expect(page.locator('.screen')).toHaveAttribute('data-page', 'connect', { timeout: 30000 });
  await expect(page.locator('.addr-box b')).toContainText('aternos.host');
  const stored = await page.evaluate(() => JSON.parse(localStorage.getItem('atta-console-v1')).servers.length);
  expect(stored).toBe(1);
});

test('اورلای: باز شدن با لبه، زبانه‌ها، تعویض و بستن', async ({ page }) => {
  await page.goto(`${ATTA}?overlayTimeout=60000#game`);
  await clearState(page);
  await page.goto(`${ATTA}?overlayTimeout=60000#game`);
  const root = page.locator('.ov-root');
  await expect(root).toBeVisible();
  await expect(page.locator('.ov-tab.in')).toHaveCount(0);

  // باز شدن با کلیک روی دستگیرهٔ لبهٔ چپ
  await page.click('.ov-edge');
  await expect(page.locator('.ov-tab.in')).toHaveCount(9);

  // باز شدن زبانهٔ «سریع» کنار ستون
  await page.click('[data-testid="tab-quick"]');
  await expect(page.locator('.ov-panel.open')).toBeVisible();
  await expect(page.locator('[data-testid="tab-quick"]')).toHaveClass(/selected/);

  // تعویض به زبانهٔ دیگر: قبلی بسته و جدیدی باز می‌شود
  await page.click('[data-testid="tab-items"]');
  await page.waitForTimeout(300);
  await expect(page.locator('[data-testid="tab-items"]')).toHaveClass(/selected/);
  await expect(page.locator('[data-testid="tab-quick"]')).not.toHaveClass(/selected/);
  await expect(page.locator('.ov-panel-title')).toContainText('آیتم');

  // لمس بقیهٔ صفحه آزاد است: دکمهٔ خروج بازی همچنان قابل کلیک
  await expect(page.locator('.game-exit button[data-to="home"]')).toBeEnabled();

  // زدن همان زبانه → پنل بسته می‌شود
  await page.click('[data-testid="tab-items"]');
  await page.waitForTimeout(300);
  await expect(page.locator('.ov-panel.open')).toHaveCount(0);
});

test('اورلای: اجرای دستور با دکمه و اثر آن در صحنهٔ بازی', async ({ page }) => {
  await page.goto(`${ATTA}?overlayTimeout=60000#game`);
  await page.click('.ov-edge');
  await page.click('[data-testid="tab-quick"]');
  await page.click('.ov-btn[data-cmd="/time set night"]');
  await expect(page.locator('.chat-line').last()).toContainText('زمان جهان تنظیم شد');
  await page.click('[data-testid="tab-items"]');
  await page.waitForTimeout(300);
  await page.click('.ov-btn[data-payload="netherite_sword:1"]');
  await expect(page.locator('.hotbar .slot.full').first()).toContainText('netherite sword');
});

test('اورلای: بستن خودکار بعد از مهلت تعیین‌شده', async ({ page }) => {
  await page.goto(`${ATTA}?overlayTimeout=2200#game`);
  await page.click('.ov-edge');
  await expect(page.locator('.ov-tab.in')).toHaveCount(9);
  await page.click('[data-testid="tab-world"]');
  await expect(page.locator('.ov-panel.open')).toBeVisible();
  await page.waitForTimeout(3200);
  await expect(page.locator('.ov-root')).not.toHaveClass(/open/);
  await expect(page.locator('.ov-tab.in')).toHaveCount(0);
});

test('اورلای: کشیدن از لبهٔ چپ با اشاره‌گر', async ({ page }) => {
  await page.goto(`${ATTA}?overlayTimeout=60000#game`);
  await expect(page.locator('.ov-tab.in')).toHaveCount(0);
  const box = await page.locator('.game-screen').boundingBox();
  await page.mouse.move(6, box.height / 2);
  await page.mouse.down();
  await page.mouse.move(90, box.height / 2, { steps: 6 });
  await page.mouse.up();
  await expect(page.locator('.ov-tab.in')).toHaveCount(9);
});

test('پنل مدیریت: کنسول، تنظیمات و بکاپ', async ({ page }) => {
  await page.goto(ATTA);
  await clearState(page);
  // ساخت سریع یک سرور از طریق همان مسیر
  await page.evaluate(() => {
    const btn = document.querySelector('[data-action="go"][data-to="hosts"]');
    btn.click();
  });
  await page.click('.host-card[data-host="pebblehost"]');
  await page.fill('#wiz-name', 'سرور پنل');
  await page.click('[data-action="wiz-next"]');
  await page.click('[data-action="wiz-edition"][data-id="crossplay"]');
  await page.click('[data-action="wiz-version"][data-id="1.21.111"]');
  await page.click('[data-action="wiz-gamemode"][data-id="creative"]');
  await page.click('[data-action="wiz-difficulty"][data-id="hard"]');
  await page.click('[data-action="wiz-next"]');
  await page.click('[data-action="wiz-ram"][data-id="4"]');
  await page.click('[data-action="wiz-region"][data-id="de-frankfurt"]');
  await page.click('[data-action="wiz-deploy"]');
  await expect(page.locator('.screen')).toHaveAttribute('data-page', 'connect', { timeout: 30000 });

  await page.evaluate(() => window.__atta.go('panel'));
  await expect(page.locator('.panel-head h2')).toContainText('سرور پنل');
  await page.click('.ptab[data-tab="console"]');
  await page.fill('.console-input input', '/say سلام به همه');
  await page.click('.console-input button');
  await expect(page.locator('.console')).toContainText('پیام شما در کل سرور پخش شد');
  await page.click('.ptab[data-tab="backups"]');
  await page.click('[data-action="backup-now"]');
  await expect(page.locator('.backup-row')).toHaveCount(1);
  await page.click('.ptab[data-tab="settings"]');
  await expect(page.locator('.prop-group').first()).toContainText('عمومی');
});

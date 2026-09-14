// آتا — اورلای داخل بازی: زبانه‌های رنگی، انیمیشن جادویی، بستن خودکار و لمس آزاد بقیهٔ صفحه
// رفتارعیناً طبق طراحی: کشیدن از لبهٔ چپ، ستون زبانه‌ها + پنل کنار هم، بقیهٔ صفحه فعال.

import { OVERLAY_TABS, OVERLAY_AUTO_CLOSE_MS } from './data.js';

export function createOverlay({
  mount,                    // عنصری که اورلای داخل آن مطلق می‌شود (صحنهٔ بازی)
  tabContent,               // (tabId) => HTML داخلی پنل هر زبانه
  onAction,                 // (action, tabId) => void — اجرای دکمه/دستور
  onClose = () => {},
  onTabOpen = () => {},
  autoCloseMs = OVERLAY_AUTO_CLOSE_MS,
} = {}) {
  let root = null, colEl = null, panelEl = null, timerEl = null;
  let openState = false;          // ستون زبانه‌ها باز است؟
  let activeTab = null;           // زبانهٔ بازشده
  let autoTimer = null, panelSwapLock = false;
  const timeouts = new Set();
  const later = (fn, ms) => { const t = setTimeout(() => { timeouts.delete(t); fn(); }, ms); timeouts.add(t); return t; };

  function build() {
    root = document.createElement('div');
    root.className = 'ov-root';
    root.dir = 'ltr';
    root.setAttribute('data-testid', 'overlay');
    root.innerHTML = `
      <div class="ov-edge" data-testid="overlay-edge" aria-label="بازکردن پنل آتا">
        <span class="ov-edge-grip">◀</span><span class="ov-edge-label">آتا</span>
      </div>
      <div class="ov-dock" data-testid="overlay-dock">
        <div class="ov-tabs" data-testid="overlay-tabs" role="tablist" aria-label="زبانه‌های اورلای آتا"></div>
        <div class="ov-panel" data-testid="overlay-panel" role="tabpanel" hidden>
          <div class="ov-panel-head">
            <span class="ov-panel-title"></span>
            <span class="ov-panel-timer" aria-hidden="true"></span>
          </div>
          <div class="ov-panel-body" dir="rtl"></div>
        </div>
      </div>`;
    colEl = root.querySelector('.ov-tabs');
    panelEl = root.querySelector('.ov-panel');
    timerEl = root.querySelector('.ov-panel-timer');
    OVERLAY_TABS.forEach((tab, i) => {
      const btn = document.createElement('div');
      btn.className = 'ov-tab';
      btn.style.setProperty('--tab-color', tab.color);
      btn.style.transitionDelay = '0ms';
      btn.dataset.tab = tab.id;
      btn.dataset.testid = `tab-${tab.id}`;
      btn.setAttribute('role', 'tab');
      btn.setAttribute('aria-label', `زبانهٔ ${tab.name}`);
      btn.innerHTML = `
        <button class="ov-tab-btn" data-role="select" aria-label="بازکردن ${tab.name}">
          <span class="ov-tab-icon">${tab.icon}</span><span class="ov-tab-name">${tab.name}</span>
        </button>
        <button class="ov-tab-close" data-role="close" aria-label="بستن ${tab.name}">✕</button>`;
      btn.querySelector('[data-role=select]').addEventListener('click', () => { resetIdle(); toggleTab(tab.id); });
      btn.querySelector('[data-role=close]').addEventListener('click', (e) => { e.stopPropagation(); close(); });
      btn.style.setProperty('--stagger', `${i}`);
      colEl.appendChild(btn);
    });
    root.querySelector('.ov-edge').addEventListener('click', () => open());
    mount.appendChild(root);
    bindSwipe(mount);
  }

  // کشیدن از لبهٔ چپ به داخل → باز شدن پنل (در حالت افقی گوشی)
  function bindSwipe(el) {
    let startX = null, startY = null;
    const zone = 34; // پیکسل از لبهٔ چپ
    el.addEventListener('pointerdown', (e) => {
      if (e.clientX <= zone && !openState) { startX = e.clientX; startY = e.clientY; }
    }, { passive: true });
    el.addEventListener('pointermove', (e) => {
      if (startX === null) return;
      const dx = e.clientX - startX, dy = Math.abs(e.clientY - startY);
      if (dx > 42 && dy < 60) { startX = null; open(); }
    }, { passive: true });
    el.addEventListener('pointerup', () => { startX = null; }, { passive: true });
  }

  function resetIdle() {
    if (autoTimer) clearTimeout(autoTimer);
    if (!openState) return;
    let left = Math.ceil(autoCloseMs / 1000);
    if (timerEl) timerEl.textContent = `${left}s`;
    autoTimer = setInterval(() => {
      left -= 1;
      if (timerEl) timerEl.textContent = left > 0 ? `${left}s` : '';
      if (left <= 0) close();
    }, 1000);
  }

  function open() {
    if (openState) { resetIdle(); return; }
    openState = true;
    root.classList.add('open');
    const tabs = [...colEl.children];
    tabs.forEach((t, i) => {
      t.classList.remove('in');
      later(() => t.classList.add('in'), 40 + i * 70);   // ورود دونه‌دونه و انیمیشنی
    });
    resetIdle();
  }

  function close() {
    if (!openState && !activeTab) return;
    openState = false;
    activeTab = null;
    if (autoTimer) { clearInterval(autoTimer); autoTimer = null; }
    root.classList.remove('open');
    panelEl.classList.remove('open');
    panelEl.hidden = true;
    [...colEl.children].forEach(t => t.classList.remove('in', 'selected'));
    timeouts.forEach(t => clearTimeout(t)); timeouts.clear();
    onClose();
  }

  function toggleTab(id) {
    if (activeTab === id) { closePanel(); return; }
    if (activeTab) {
      // زبانهٔ قبلی بسته شود، سپس جدید با انیمیشن جادویی باز شود
      panelEl.classList.remove('open');
      panelEl.classList.add('closing');
      const prev = activeTab;
      activeTab = null;
      later(() => { panelEl.classList.remove('closing'); renderPanel(id); }, 170);
      markSelected(id);
      return;
    }
    renderPanel(id);
    markSelected(id);
  }

  function markSelected(id) {
    [...colEl.children].forEach(t => t.classList.toggle('selected', t.dataset.tab === id));
  }

  function renderPanel(id) {
    if (panelSwapLock) return;
    panelSwapLock = true;
    const tab = OVERLAY_TABS.find(t => t.id === id);
    activeTab = id;
    panelEl.style.setProperty('--tab-color', tab.color);
    panelEl.querySelector('.ov-panel-title').textContent = `${tab.icon} ${tab.name} — ${tab.desc}`;
    panelEl.querySelector('.ov-panel-body').innerHTML = tabContent(id);
    panelEl.hidden = false;
    // قاب‌بعدی → کلاس باز شدن؛ سرخوردن جادویی از سمت چپ به داخل
    requestAnimationFrame(() => requestAnimationFrame(() => {
      panelEl.classList.add('open');
      panelSwapLock = false;
      bindPanelActions(panelEl, id);
    }));
    resetIdle();
    onTabOpen(id);
  }

  function closePanel() {
    panelEl.classList.remove('open');
    panelEl.classList.add('closing');
    activeTab = null;
    markSelected(null);
    later(() => { panelEl.classList.remove('closing'); panelEl.hidden = true; }, 170);
  }

  function bindPanelActions(panel, tabId) {
    panel.querySelectorAll('[data-act]').forEach(btn => {
      btn.addEventListener('click', () => {
        resetIdle();
        onAction({ id: btn.dataset.act, label: btn.dataset.label || btn.textContent.trim(), cmd: btn.dataset.cmd || null, payload: btn.dataset.payload || null }, tabId);
      });
    });
    const form = panel.querySelector('form[data-ovform]');
    if (form) form.addEventListener('submit', (e) => {
      e.preventDefault();
      resetIdle();
      const data = Object.fromEntries(new FormData(form).entries());
      onAction({ id: form.dataset.ovform, label: form.dataset.label || 'اجرا', data }, tabId);
    });
  }

  function isOpen() { return openState; }
  function getActiveTab() { return activeTab; }

  function destroy() {
    timeouts.forEach(t => clearTimeout(t)); timeouts.clear();
    if (autoTimer) clearInterval(autoTimer);
    if (root && root.parentNode) root.parentNode.removeChild(root);
    root = null;
  }

  build();
  return { open, close, toggleTab, closePanel, resetIdle, isOpen, getActiveTab, destroy, get root() { return root; } };
}

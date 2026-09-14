// آتا — کاتالوگ داده‌ها: میزبان‌ها، نسخه‌ها، آیتم‌ها، افکت‌ها، ماب‌ها و تنظیمات سرور
// همهٔ داده‌ها آفلاین و محلی‌اند؛ هیچ وابستگی به اینترنتی در خود اپ نیست.

export const APP_NAME = 'آتا';
export const APP_TAGLINE = 'دستیار هوشمند سرور ماینکرافت';
export const WAKE_WORD = 'آتا';

// ———————————————————————————————— میزبان‌ها ————————————————————————————————
// ۱۱ میزبان: یک وی‌پی‌اس اختصاصی به نام «اترنوس» و ده میزبان سرویس سرور ماینکرافت.
export const HOSTS = Object.freeze([
  {
    id: 'eternos-vps', name: 'اترنوس وی‌پی‌اس', latin: 'Eternos VPS', free: false,
    badge: 'پیشنهاد آتا', tagline: 'وی‌پی‌اس اختصاصی با دسترسی کامل روت و پنل فارسی',
    price: 'از ۱۹۹ هزار تومان در ماه', ram: [4, 8, 16, 32], slots: 'نامحدود',
    locations: ['تهران', 'فرانکفورت', 'استانبول'], bedrock: true, java: true, crossplay: true,
    panel: 'پنل اختصاصی آتا + ترمینال روت', ping: '۱۲ تا ۳۵ میلی‌ثانیه', uptime: '۹۹٫۹٪',
    support: 'پشتیبانی فارسی ۲۴ ساعته', setup: 'راه‌اندازی خودکار توسط آتا',
  },
  {
    id: 'aternos', name: 'اترنوس رایگان', latin: 'Aternos', free: true,
    badge: 'رایگان', tagline: 'سرویس رایگان جهانی؛ مناسب بازی دوستانه با صف راه‌اندازی',
    price: 'رایگان', ram: [2], slots: '۱۰',
    locations: ['آلمان'], bedrock: true, java: true, crossplay: true,
    panel: 'پنل وب اترنوس', ping: '۹۰ تا ۱۴۰ میلی‌ثانیه', uptime: 'وابسته به صف',
    support: 'انجمن و راهنمای رسمی', setup: 'اتصال خودکار با حساب اترنوس',
  },
  {
    id: 'ploudos', name: 'پلاودوس', latin: 'PloudOS', free: true,
    badge: 'رایگان', tagline: 'سرور رایگان اروپایی با راه‌اندازی سریع و بدون کارت بانکی',
    price: 'رایگان', ram: [2], slots: '۱۲',
    locations: ['آلمان'], bedrock: true, java: true, crossplay: false,
    panel: 'پنل وب پلاودوس', ping: '۱۰۰ تا ۱۵۰ میلی‌ثانیه', uptime: 'وابسته به صف',
    support: 'راهنمای آنلاین', setup: 'ساخت خودکار با نام و رمز',
  },
  {
    id: 'falixnodes', name: 'فالیکس‌نودز', latin: 'FalixNodes', free: true,
    badge: 'رایگان + پرمیوم', tagline: 'پلن رایگان با پنل پروانه‌ای و پلن‌های ارزان پرمیوم',
    price: 'رایگان / از ۳ دلار', ram: [2, 4, 6], slots: '۲۰',
    locations: ['آلمان', 'کانادا'], bedrock: true, java: true, crossplay: true,
    panel: 'پنل پروانه‌ای (Pterodactyl)', ping: '۱۱۰ تا ۱۶۰ میلی‌ثانیه', uptime: '۹۷٪',
    support: 'تیکت انگلیسی', setup: 'ساخت خودکار با پنل',
  },
  {
    id: 'pebblehost', name: 'پبل‌هاست', latin: 'PebbleHost', free: false,
    badge: 'اقتصادی', tagline: 'هر گیگ رم یک دلار؛ محبوب‌ترین میزبان اقتصادی جهان',
    price: '۱ دلار به ازای هر گیگ', ram: [2, 3, 4, 6, 8], slots: 'نامحدود',
    locations: ['آمریکا', 'اروپا', 'آسیا'], bedrock: true, java: true, crossplay: true,
    panel: 'پنل پروانه‌ای', ping: '۱۲۰ تا ۱۷۰ میلی‌ثانیه', uptime: '۹۹٫۵٪',
    support: 'تیکت ۲۴ ساعته', setup: 'نصب خودکار نسخه و پلاگین',
  },
  {
    id: 'shockbyte', name: 'شاک‌بایت', latin: 'Shockbyte', free: false,
    badge: 'حرفه‌ای', tagline: 'میزبان بزرگ جهانی با گارانتی آپ‌تایم و بکاپ خودکار',
    price: 'از ۲٫۵ دلار در ماه', ram: [2, 4, 8, 16], slots: 'نامحدود',
    locations: ['آمریکا', 'اروپا', 'استرالیا', 'سنگاپور'], bedrock: true, java: true, crossplay: true,
    panel: 'پنل اختصاصی + پروانه‌ای', ping: '۱۲۰ تا ۱۸۰ میلی‌ثانیه', uptime: '۱۰۰٪ گارانتی',
    support: 'چت زنده ۲۴ ساعته', setup: 'راه‌اندازی فوری خودکار',
  },
  {
    id: 'apex', name: 'ایپکس هاستینگ', latin: 'Apex Hosting', free: false,
    badge: 'پریمیوم', tagline: 'سرویس پریمیوم با نصب یک‌کلیکی مادپک و پشتیبانی قوی',
    price: 'از ۵ دلار در ماه', ram: [2, 4, 8, 16], slots: 'نامحدود',
    locations: ['آمریکا', 'اروپا', 'آسیا'], bedrock: true, java: true, crossplay: true,
    panel: 'پنل اختصاصی ایپکس', ping: '۱۳۰ تا ۱۸۰ میلی‌ثانیه', uptime: '۹۹٫۹٪',
    support: 'چت زنده و تیکت', setup: 'نصب خودکار مادپک و پلاگین',
  },
  {
    id: 'bisect', name: 'بایسکت‌هاستینگ', latin: 'BisectHosting', free: false,
    badge: 'منعطف', tagline: 'کانفیگ‌های آماده و زیردامنهٔ رایگان برای هر سرور',
    price: 'از ۳ دلار در ماه', ram: [1, 2, 4, 8, 12], slots: 'نامحدود',
    locations: ['آمریکا', 'کانادا', 'اروپا', 'برزیل'], bedrock: true, java: true, crossplay: true,
    panel: 'پنل پروانه‌ای', ping: '۱۲۵ تا ۱۷۵ میلی‌ثانیه', uptime: '۹۹٫۹٪',
    support: 'تیکت سریع', setup: 'ساخت خودکار با پروفایل آماده',
  },
  {
    id: 'godlike', name: 'گادلایک هاست', latin: 'GodLike.host', free: false,
    badge: 'گیمینگ', tagline: 'سخت‌افزار گیمینگ رایزن و پنل مدرن با نصب چند سرور',
    price: 'از ۴ دلار در ماه', ram: [4, 8, 16], slots: 'نامحدود',
    locations: ['آمریکا', 'اروپا'], bedrock: true, java: true, crossplay: true,
    panel: 'پنل پروانه‌ای', ping: '۱۲۰ تا ۱۶۵ میلی‌ثانیه', uptime: '۹۹٫۹٪',
    support: 'چت و دیسکورد', setup: 'راه‌اندازی خودکار چند سرویس',
  },
  {
    id: 'nodecraft', name: 'نودکرافت', latin: 'Nodecraft', free: false,
    badge: 'خودکار', tagline: 'جابجایی خودکار بازی‌ها و کنترل کامل با اپ موبایل',
    price: 'از ۱۰ دلار در ماه', ram: [4, 8, 16, 32], slots: 'نامحدود',
    locations: ['آمریکا', 'اروپا', 'آسیا'], bedrock: true, java: true, crossplay: true,
    panel: 'پنل اختصاصی نودکرافت', ping: '۱۳۰ تا ۱۸۰ میلی‌ثانیه', uptime: '۹۹٫۹٪',
    support: 'تیکت و چت', setup: 'راه‌اندازی خودکار تک‌کلیکی',
  },
  {
    id: 'scalacube', name: 'اسکالاکوب', latin: 'Scalacube', free: false,
    badge: 'لانچر اختصاصی', tagline: 'هاست همراه با لانچر اختصاصی و پلن رایگان محدود',
    price: 'رایگان محدود / از ۲ دلار', ram: [2, 4, 8], slots: '۱۰ تا نامحدود',
    locations: ['اروپا', 'آمریکا'], bedrock: true, java: true, crossplay: false,
    panel: 'پنل اختصاصی اسکالاکوب', ping: '۱۲۰ تا ۱۷۰ میلی‌ثانیه', uptime: '۹۹٫۵٪',
    support: 'تیکت انگلیسی', setup: 'ساخت خودکار داخل پنل',
  },
]);

// ———————————————————————————————— گزینه‌های ساخت ————————————————————————————————
export const EDITIONS = Object.freeze([
  { id: 'bedrock', name: 'بدراک (گوشی و کنسول)', icon: '📱', desc: 'نسخهٔ موبایل، ویندوز و کنسول؛ پورت پیش‌فرض 19132' },
  { id: 'java', name: 'جاوا (کامپیوتر)', icon: '🖥️', desc: 'نسخهٔ کلاسیک کامپیوتر با ماد و پلاگین؛ پورت پیش‌فرض 25565' },
  { id: 'crossplay', name: 'کراس‌پلی (هر دو)', icon: '🌉', desc: 'جاوا + بدراک هم‌زمان با پل گیسر (Geyser)؛ هر دو پورت فعال' },
]);

export const VERSIONS = Object.freeze([
  { id: '1.21.111', name: '۱٫۲۱٫۱۱۱ — جدیدترین پایدار', latest: true },
  { id: '1.21.94', name: '۱٫۲۱٫۹۴' },
  { id: '1.21.81', name: '۱٫۲۱٫۸۱' },
  { id: '1.21.62', name: '۱٫۲۱٫۶۲' },
  { id: '1.21.51', name: '۱٫۲۱٫۵۱' },
  { id: '1.20.81', name: '۱٫۲۰٫۸۱ — برای مادپک‌های قدیمی' },
]);

export const GAMEMODES = Object.freeze([
  { id: 'survival', name: 'بقا (Survival)', icon: '⛏️', desc: 'تجربهٔ اصلی؛ جمع‌کردن منابع و زنده‌ماندن' },
  { id: 'creative', name: 'خلاقانه (Creative)', icon: '🎨', desc: 'منابع نامحدود و پرواز برای ساخت‌وساز' },
  { id: 'adventure', name: 'ماجراجویی (Adventure)', icon: '🧭', desc: 'برای مپ‌های داستانی؛ بدون کندن بلوک' },
  { id: 'spectator', name: 'تماشاگر (Spectator)', icon: '👁️', desc: 'پرواز آزاد و دیدن دنیا بدون دخالت' },
]);

export const DIFFICULTIES = Object.freeze([
  { id: 'peaceful', name: 'صلح‌آمیز', icon: '🕊️', desc: 'بدون ماب متخاصم؛ مناسب شروع آرام' },
  { id: 'easy', name: 'آسان', icon: '🌱', desc: 'ماب‌های ضعیف؛ تجربهٔ سبک' },
  { id: 'normal', name: 'معمولی', icon: '⚖️', desc: 'تعادل استاندارد بازی' },
  { id: 'hard', name: 'سخت', icon: '🔥', desc: 'چالش کامل و ماب‌های قدرتمند' },
]);

export const RAM_OPTIONS = Object.freeze([2, 3, 4, 6, 8, 12, 16]);

export const REGIONS = Object.freeze([
  { id: 'ir-tehran', name: 'ایران — تهران', icon: '🇮🇷', ping: 'کمترین پینگ برای ایران' },
  { id: 'de-frankfurt', name: 'آلمان — فرانکفورت', icon: '🇩🇪', ping: 'پایدار و ارزان' },
  { id: 'tr-istanbul', name: 'ترکیه — استانبول', icon: '🇹🇷', ping: 'پینگ خوب برای ایران' },
  { id: 'us-east', name: 'آمریکا — شرق', icon: '🇺🇸', ping: 'برای بازیکنان آمریکایی' },
  { id: 'asia-sg', name: 'سنگاپور', icon: '🇸🇬', ping: 'برای بازیکنان آسیایی' },
]);

// ———————————————————————————————— محتوای بازی برای اورلای ————————————————————————————————
export const OVERLAY_TABS = Object.freeze([
  { id: 'quick', name: 'سریع', icon: '⚡', color: '#f59e0b', voice: ['سریع'], desc: 'دستورهای فوری و پرکاربرد با یک لمس' },
  { id: 'world', name: 'جهان', icon: '🌍', color: '#10b981', voice: ['جهان', 'دنیا'], desc: 'زمان، آب‌وهوا و قانون‌های جهان' },
  { id: 'player', name: 'بازیکن', icon: '🧍', color: '#3b82f6', voice: ['بازیکن', 'خودم'], desc: 'تله‌پورت، درمان و کنترل بازیکن' },
  { id: 'items', name: 'آیتم‌ها', icon: '🎒', color: '#a855f7', voice: ['آیتم', 'آیتم ها', 'وسایل'], desc: 'دریافت فوری ابزار، سلاح و بلوک' },
  { id: 'powers', name: 'توانایی‌ها', icon: '✨', color: '#ec4899', voice: ['توانایی', 'توانایی ها', 'قدرت', 'افکت'], desc: 'افکت‌ها و توان‌های خاص اپراتور' },
  { id: 'mobs', name: 'ماب‌ها', icon: '🐉', color: '#84cc16', voice: ['ماب', 'ماب ها', 'موجود'], desc: 'احضار و کنترل موجودات' },
  { id: 'command', name: 'دستور', icon: '💬', color: '#06b6d4', voice: ['دستور', 'کنسول', 'فرمان'], desc: 'نوشتن و اجرای هر دستور دلخواه' },
  { id: 'admin', name: 'مدیریت', icon: '🛠️', color: '#ef4444', voice: ['مدیریت', 'ادمین', 'سرور'], desc: 'کنترل کامل سرور، بازیکن‌ها و بکاپ' },
  { id: 'support', name: 'پشتیبانی', icon: '🆘', color: '#14b8a6', voice: ['پشتیبانی', 'کمک', 'راهنما'], desc: 'راهنما، عیب‌یابی و کمک فوری' },
]);

export const OVERLAY_AUTO_CLOSE_MS = 30000;

export const QUICK_ACTIONS = Object.freeze([
  { id: 'gm-survival', label: 'حالت بقا', icon: '⛏️', cmd: '/gamemode survival' },
  { id: 'gm-creative', label: 'حالت خلاقانه', icon: '🎨', cmd: '/gamemode creative' },
  { id: 'gm-adventure', label: 'حالت ماجراجویی', icon: '🧭', cmd: '/gamemode adventure' },
  { id: 'gm-spectator', label: 'حالت تماشاگر', icon: '👁️', cmd: '/gamemode spectator' },
  { id: 'time-day', label: 'روز شود', icon: '☀️', cmd: '/time set day' },
  { id: 'time-night', label: 'شب شود', icon: '🌙', cmd: '/time set night' },
  { id: 'weather-clear', label: 'آسمان صاف', icon: '🌤️', cmd: '/weather clear' },
  { id: 'weather-rain', label: 'باران', icon: '🌧️', cmd: '/weather rain' },
  { id: 'weather-thunder', label: 'طوفان و رعد', icon: '⛈️', cmd: '/weather thunder' },
  { id: 'keep-inventory', label: 'حفظ آیتم بعد مرگ', icon: '🛡️', cmd: '/gamerule keepInventory true' },
  { id: 'heal', label: 'درمان کامل', icon: '❤️', cmd: '/heal @s' },
  { id: 'feed', label: 'سیر شدن', icon: '🍖', cmd: '/feed @s' },
]);

export const WORLD_ACTIONS = Object.freeze([
  { id: 'time-sunrise', label: 'طلوع', icon: '🌅', cmd: '/time set sunrise' },
  { id: 'time-noon', label: 'ظهر', icon: '☀️', cmd: '/time set noon' },
  { id: 'time-sunset', label: 'غروب', icon: '🌇', cmd: '/time set sunset' },
  { id: 'time-midnight', label: 'نیمه‌شب', icon: '🌃', cmd: '/time set midnight' },
  { id: 'freeze-time', label: 'توقف زمان', icon: '⏸️', cmd: '/gamerule doDaylightCycle false' },
  { id: 'flow-time', label: 'جریان زمان', icon: '▶️', cmd: '/gamerule doDaylightCycle true' },
  { id: 'no-mobs', label: 'ماب طبیعی نیاید', icon: '🚫', cmd: '/gamerule doMobSpawning false' },
  { id: 'yes-mobs', label: 'ماب طبیعی بیاید', icon: '✅', cmd: '/gamerule doMobSpawning true' },
  { id: 'no-fire', label: 'آتش گسترش نیابد', icon: '🧯', cmd: '/gamerule doFireTick false' },
  { id: 'instant-break', label: 'کندن فوری بلوک', icon: '⛏️', cmd: '/gamerule instantBreak true' },
]);

export const PLAYER_ACTIONS = Object.freeze([
  { id: 'tp-home', label: 'تله‌پورت به خانه', icon: '🏠', cmd: '/tp @s 0 100 0' },
  { id: 'tp-spawn', label: 'رفتن به اسپاون', icon: '🧭', cmd: '/spawnpoint @s' },
  { id: 'heal', label: 'درمان کامل', icon: '❤️', cmd: '/heal @s' },
  { id: 'feed', label: 'سیر شدن', icon: '🍖', cmd: '/feed @s' },
  { id: 'clear-inv', label: 'پاک‌کردن کیف', icon: '🧹', cmd: '/clear @s' },
  { id: 'xp-100', label: '۱۰۰ سطح تجربه', icon: '🟢', cmd: '/xp 100L @s' },
  { id: 'fly-on', label: 'پرواز روشن', icon: '🕊️', cmd: '/ability @s mayfly true' },
  { id: 'fly-off', label: 'پرواز خاموش', icon: '🚶', cmd: '/ability @s mayfly false' },
  { id: 'god-on', label: 'رویین‌تنی', icon: '🛡️', cmd: '/ability @s invulnerable true' },
  { id: 'op-self', label: 'اپراتور شدن من', icon: '⭐', cmd: '/op @s' },
]);

export const ITEM_GROUPS = Object.freeze([
  {
    id: 'tools', name: 'ابزار و سلاح', icon: '⚔️',
    items: [
      { id: 'netherite_sword', label: 'شمشیر ندرایتی', icon: '🗡️' },
      { id: 'netherite_pickaxe', label: 'کلنگ ندرایتی', icon: '⛏️' },
      { id: 'diamond_axe', label: 'تبر الماسی', icon: '🪓' },
      { id: 'bow', label: 'کمان', icon: '🏹' },
      { id: 'crossbow', label: 'زوبین', icon: '🎯' },
      { id: 'trident', label: 'نیزه', icon: '🔱' },
      { id: 'shield', label: 'سپر', icon: '🛡️' },
      { id: 'fishing_rod', label: 'قلاب ماهیگیری', icon: '🎣' },
      { id: 'arrow', label: 'تیر (۶۴)', icon: '➳', count: 64 },
      { id: 'flint_and_steel', label: 'فندک', icon: '🔥' },
    ],
  },
  {
    id: 'armor', name: 'زره و پوشاک', icon: '🛡️',
    items: [
      { id: 'netherite_helmet', label: 'کلاه ندرایتی', icon: '🪖' },
      { id: 'netherite_chestplate', label: 'زره ندرایتی', icon: '🦺' },
      { id: 'netherite_leggings', label: 'شلوار ندرایتی', icon: '👖' },
      { id: 'netherite_boots', label: 'چکمه ندرایتی', icon: '🥾' },
      { id: 'elytra', label: 'الیترا (بال)', icon: '🪽' },
      { id: 'turtle_helmet', label: 'کلاه لاک‌پشتی', icon: '🐢' },
    ],
  },
  {
    id: 'blocks', name: 'بلوک‌ها', icon: '🧱',
    items: [
      { id: 'diamond_block', label: 'بلوک الماس (۶۴)', icon: '💎', count: 64 },
      { id: 'gold_block', label: 'بلوک طلا (۶۴)', icon: '🟨', count: 64 },
      { id: 'oak_planks', label: 'تخته چوب (۶۴)', icon: '🪵', count: 64 },
      { id: 'stone', label: 'سنگ (۶۴)', icon: '🪨', count: 64 },
      { id: 'glass', label: 'شیشه (۶۴)', icon: '🪟', count: 64 },
      { id: 'tnt', label: 'تی‌ان‌تی (۱۶)', icon: '🧨', count: 16 },
      { id: 'torch', label: 'مشعل (۶۴)', icon: '🕯️', count: 64 },
      { id: 'chest', label: 'صندوقچه (۱۶)', icon: '📦', count: 16 },
    ],
  },
  {
    id: 'food', name: 'غذا', icon: '🍖',
    items: [
      { id: 'golden_apple', label: 'سیب طلایی (۸)', icon: '🍎', count: 8 },
      { id: 'enchanted_golden_apple', label: 'سیب طلایی افسونشده', icon: '✨' },
      { id: 'cooked_beef', label: 'استیک (۶۴)', icon: '🥩', count: 64 },
      { id: 'bread', label: 'نان (۶۴)', icon: '🍞', count: 64 },
      { id: 'golden_carrot', label: 'هویج طلایی (۶۴)', icon: '🥕', count: 64 },
      { id: 'cake', label: 'کیک', icon: '🎂' },
    ],
  },
  {
    id: 'special', name: 'خاص و حمل‌ونقل', icon: '🚀',
    items: [
      { id: 'ender_pearl', label: 'مروارید اندر (۱۶)', icon: '🔮', count: 16 },
      { id: 'ender_eye', label: 'چشم اندر (۱۲)', icon: '👁️', count: 12 },
      { id: 'firework_rocket', label: 'موشک آتش‌بازی (۶۴)', icon: '🎆', count: 64 },
      { id: 'totem_of_undying', label: 'توتم جاودانگی', icon: '🗿' },
      { id: 'beacon', label: 'بیکون', icon: '💡' },
      { id: 'minecart', label: 'واگن', icon: '🛒' },
      { id: 'boat', label: 'قایق', icon: '🛶' },
      { id: 'spyglass', label: 'دوربین', icon: '🔭' },
    ],
  },
]);

export const POWER_ACTIONS = Object.freeze([
  { id: 'speed', label: 'سرعت فوق‌العاده', icon: '💨', cmd: '/effect @s speed 300 2 true' },
  { id: 'jump', label: 'پرش بلند', icon: '🦘', cmd: '/effect @s jump_boost 300 2 true' },
  { id: 'strength', label: 'قدرت ضربت', icon: '💪', cmd: '/effect @s strength 300 2 true' },
  { id: 'regen', label: 'بازیابی جان', icon: '💗', cmd: '/effect @s regeneration 300 2 true' },
  { id: 'resistance', label: 'مقاومت کامل', icon: '🛡️', cmd: '/effect @s resistance 300 2 true' },
  { id: 'fire-res', label: 'ضد آتش', icon: '🔥', cmd: '/effect @s fire_resistance 300 2 true' },
  { id: 'water-breath', label: 'تنفس زیر آب', icon: '🫧', cmd: '/effect @s water_breathing 300 2 true' },
  { id: 'night-vision', label: 'دید در شب', icon: '🌃', cmd: '/effect @s night_vision 300 2 true' },
  { id: 'invisibility', label: 'نامرئی شدن', icon: '👻', cmd: '/effect @s invisibility 300 2 true' },
  { id: 'slow-falling', label: 'سقوط آرام', icon: '🪂', cmd: '/effect @s slow_falling 300 2 true' },
  { id: 'haste', label: 'کندوکاو سریع', icon: '⛏️', cmd: '/effect @s haste 300 2 true' },
  { id: 'clear-effects', label: 'پاک‌کردن افکت‌ها', icon: '🧼', cmd: '/effect @s clear' },
]);

export const MOB_GROUPS = Object.freeze([
  {
    id: 'friendly', name: 'دوستانه', icon: '🐄',
    mobs: [
      { id: 'horse', label: 'اسب', icon: '🐎' }, { id: 'donkey', label: 'الاغ', icon: '🫏' },
      { id: 'cat', label: 'گربه', icon: '🐈' }, { id: 'wolf', label: 'گرگ اهلی', icon: '🐕' },
      { id: 'cow', label: 'گاو', icon: '🐄' }, { id: 'sheep', label: 'گوسفند', icon: '🐑' },
      { id: 'chicken', label: 'مرغ', icon: '🐔' }, { id: 'fox', label: 'روباه', icon: '🦊' },
      { id: 'parrot', label: 'طوطی', icon: '🦜' }, { id: 'villager', label: 'روستایی', icon: '🧑‍🌾' },
      { id: 'iron_golem', label: 'غول آهنی', icon: '🤖' }, { id: 'allay', label: 'الِی', icon: '🧚' },
    ],
  },
  {
    id: 'hostile', name: 'متخاصم', icon: '💀',
    mobs: [
      { id: 'zombie', label: 'زامبی', icon: '🧟' }, { id: 'skeleton', label: 'اسکلت', icon: '💀' },
      { id: 'creeper', label: 'کریپر', icon: '🟢' }, { id: 'spider', label: 'عنکبوت', icon: '🕷️' },
      { id: 'enderman', label: 'اندرمن', icon: '🌌' }, { id: 'witch', label: 'ساحره', icon: '🧙' },
      { id: 'pillager', label: 'تاراجگر', icon: '🏹' }, { id: 'blaze', label: 'شعله‌ور', icon: '🔥' },
      { id: 'ghast', label: 'گاست', icon: '👻' }, { id: 'warden', label: 'واردن', icon: '🕳️' },
    ],
  },
  {
    id: 'boss', name: 'باس‌ها', icon: '🐉',
    mobs: [
      { id: 'ender_dragon', label: 'اژدهای اندر', icon: '🐉' },
      { id: 'wither', label: 'ویذر', icon: '☠️' },
      { id: 'elder_guardian', label: 'نگهبان کهن', icon: '🐡' },
      { id: 'ravager', label: 'ویرانگر', icon: '🐗' },
    ],
  },
]);

export const MOB_ACTIONS = Object.freeze([
  { id: 'killall', label: 'حذف همهٔ ماب‌ها', icon: '🧨', cmd: '/kill @e[type=!player]' },
  { id: 'killhostile', label: 'حذف ماب‌های متخاصم', icon: '⚔️', cmd: '/kill @e[family=monster]' },
]);

// ———————————————————————————————— تنظیمات سرور (server.properties) ————————————————————————————————
export const PROPERTY_GROUPS = Object.freeze([
  {
    id: 'general', name: 'عمومی', icon: '🧩',
    props: [
      { key: 'server-name', type: 'text', label: 'نام نمایشی سرور', hint: 'نامی که در فهرست بازیکن‌ها دیده می‌شود' },
      { key: 'motd', type: 'text', label: 'پیام خوش‌آمد (MOTD)', hint: 'متن زیر نام سرور در فهرست' },
      { key: 'gamemode', type: 'select', label: 'حالت پیش‌فرض بازی', options: ['survival', 'creative', 'adventure', 'spectator'] },
      { key: 'difficulty', type: 'select', label: 'سختی پیش‌فرض', options: ['peaceful', 'easy', 'normal', 'hard'] },
      { key: 'level-seed', type: 'text', label: 'سید جهان (Seed)', hint: 'خالی = جهان تصادفی' },
      { key: 'level-name', type: 'text', label: 'نام پوشهٔ جهان' },
    ],
  },
  {
    id: 'network', name: 'شبکه و اتصال', icon: '🌐',
    props: [
      { key: 'server-port', type: 'number', label: 'پورت بدراک', hint: 'پیش‌فرض 19132' },
      { key: 'server-portv6', type: 'number', label: 'پورت IPv6', hint: 'پیش‌فرض 19133' },
      { key: 'max-players', type: 'number', label: 'حداکثر بازیکن' },
      { key: 'online-mode', type: 'bool', label: 'احراز هویت آنلاین' },
      { key: 'allow-cheats', type: 'bool', label: 'اجازهٔ چیت و دستور' },
    ],
  },
  {
    id: 'world', name: 'جهان و گیم‌پلی', icon: '🌍',
    props: [
      { key: 'pvp', type: 'bool', label: 'نبرد بازیکن‌ها (PvP)' },
      { key: 'spawn-protection', type: 'number', label: 'شعاع حفاظت اسپاون' },
      { key: 'view-distance', type: 'number', label: 'فاصلهٔ دید (چانک)' },
      { key: 'tick-distance', type: 'number', label: 'فاصلهٔ تیک بازیکن' },
      { key: 'default-player-permission-level', type: 'select', label: 'سطح دسترسی پیش‌فرض', options: ['visitor', 'member', 'operator'] },
      { key: 'texturepack-required', type: 'bool', label: 'اجبار تکسچرپک سرور' },
    ],
  },
  {
    id: 'performance', name: 'کارایی', icon: '⚙️',
    props: [
      { key: 'max-threads', type: 'number', label: 'حداکثر رشتهٔ پردازش' },
      { key: 'compression-threshold', type: 'number', label: 'آستانهٔ فشرده‌سازی' },
      { key: 'player-movement-score-threshold', type: 'number', label: 'حساسیت ضدتقلب حرکت' },
      { key: 'correct-player-motion', type: 'bool', label: 'اصیح حرکت بازیکن' },
    ],
  },
]);

export const DEFAULT_PROPERTIES = Object.freeze({
  'server-name': '', motd: 'به سرور آتا خوش آمدید! 🌟', gamemode: 'survival', difficulty: 'normal',
  'level-seed': '', 'level-name': 'world', 'server-port': 19132, 'server-portv6': 19133,
  'max-players': 10, 'online-mode': true, 'allow-cheats': true, pvp: true,
  'spawn-protection': 10, 'view-distance': 10, 'tick-distance': 4,
  'default-player-permission-level': 'member', 'texturepack-required': false,
  'max-threads': 8, 'compression-threshold': 256, 'player-movement-score-threshold': 20,
  'correct-player-motion': true,
});

// ———————————————————————————————— مراحل استقرار ————————————————————————————————
export const DEPLOY_STAGES = Object.freeze([
  { id: 'order', label: 'ثبت سفارش نزد میزبان', icon: '📨', ms: 900 },
  { id: 'allocate', label: 'اختصاص منابع (رم، هسته، دیسک NVMe)', icon: '🖥️', ms: 1200 },
  { id: 'download', label: 'دانلود هستهٔ سرور بدراک / جاوا', icon: '⬇️', ms: 1400 },
  { id: 'config', label: 'نوشتن تنظیمات دقیق با پنل کامل', icon: '📝', ms: 1000 },
  { id: 'geyser', label: 'نصب پل کراس‌پلی (گیسر + فلودگیت)', icon: '🌉', ms: 900 },
  { id: 'firewall', label: 'پیکربندی فایروال و دی‌دی‌اس‌پروتکشن', icon: '🛡️', ms: 800 },
  { id: 'start', label: 'روشن‌کردن سرور و تست پینگ', icon: '🚀', ms: 1000 },
  { id: 'done', label: 'سرور آماده است!', icon: '🎉', ms: 500 },
]);

export const SERVER_CONSOLE_HINTS = Object.freeze([
  'بازیکن Operator_Joined به سرور پیوست',
  'بازیکن Operator_Joined: سلام! سرور عالیه 👋',
  'ذخیرهٔ خودکار جهان انجام شد (AutoSave)',
  'پشتیبان‌گیری خودکار از جهان ساخته شد',
  'پینگ بازیکن‌ها: ۲۸ میلی‌ثانیه — وضعیت سبز',
  'هستهٔ سرور: بدون لگ، تی‌پی‌اس ۲۰٫۰',
  'بازیکن Operator_Joined یک الماس پیدا کرد 💎',
]);

// ———————————————————————————————— محتوای پشتیبانی ————————————————————————————————
export const SUPPORT_TOPICS = Object.freeze([
  {
    id: 'connect', q: 'چطور وارد سرور خودم شوم؟',
    a: 'از صفحهٔ سرور من، دکمهٔ «ورود به بازی» را بزن. آتا نشانی و پورت را برای شما کپی می‌کند و ماینکرافت بدراک را باز می‌کند. در بازی، بخش Play سپس Add Server را بزنید و نشانی کپی‌شده را جاگذاری کنید.',
  },
  {
    id: 'lag', q: 'سرور لگ دارد؛ چه کار کنم؟',
    a: 'از پنل مدیریت، زبانهٔ «تنظیمات» را باز کن و فاصلهٔ دید را کم کن. سپس در زبانهٔ کنسول دستور /gc را اجرا کن تا حافظه آزاد شود. اگر درست نشد، رم سرور را از صفحهٔ سرور من ارتقا بده.',
  },
  {
    id: 'crossplay', q: 'دوستانم با گوشی نمی‌توانند وصل شوند',
    a: 'مطمئن شو سرور با گزینهٔ «کراس‌پلی» ساخته شده است تا پل گیسر فعال باشد. پورت بدراک 19132 باید باز باشد؛ آتا این را هنگام ساخت به‌طور خودکار انجام داده است.',
  },
  {
    id: 'backup', q: 'چطور از جهانم نسخهٔ پشتیبان بگیرم؟',
    a: 'در پنل مدیریت سرور، زبانهٔ «بکاپ» را بزن و «پشتیبان‌گیری همین حالا» را انتخاب کن. آتا نسخهٔ پشتیبان را با تاریخ ذخیره می‌کند و هر زمان می‌توانی با دکمهٔ «بازگردانی» آن را برگردانی.',
  },
  {
    id: 'op', q: 'چطور اپراتور (اوپ) شوم؟',
    a: 'در بازی، اورلای آتا را از لبهٔ چپ بکش، زبانهٔ «بازیکن» را بزن و دکمهٔ «اپراتور شدن من» را لمس کن. بیرون از بازی هم از پنل مدیریت، زبانهٔ بازیکن‌ها، دکمهٔ «اوپ» کنار نامت موجود است.',
  },
  {
    id: 'voice', q: 'دستیار صوتی کار نمی‌کند',
    a: 'در تنظیمات، «کنترل صوتی» را روشن کن و اجازهٔ میکروفن بده. کلمهٔ بیداری «آتا» را واضح بگو. در برخی مرورگرها شناسایی گفتار فقط با اتصال گوگل کار می‌کند؛ داخل اپ اندروید آتا از موتور داخلی استفاده می‌شود.',
  },
  {
    id: 'refund', q: 'نحوهٔ تمدید یا لغو سرویس میزبان',
    a: 'در فهرست میزبان‌ها، هر میزبان بخش تمدید خودکار دارد. تمدید از پنل همان میزبان انجام می‌شود و تا ۷ روز امکان بازپرداخت کامل طبق قوانین اکثر میزبان‌ها وجود دارد.',
  },
]);

// ———————————————————————————————— ابزارهای کمکی ————————————————————————————————
export const FA_DIGITS = ['۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹'];
export function faNum(n) {
  return String(n).replace(/[0-9]/g, d => FA_DIGITS[+d]).replace(/\./g, '٫');
}
export function faDate(ts) {
  try { return new Date(ts).toLocaleDateString('fa-IR'); } catch { return ''; }
}
export function faTime(ts) {
  try { return new Date(ts).toLocaleTimeString('fa-IR', { hour: '2-digit', minute: '2-digit' }); } catch { return ''; }
}

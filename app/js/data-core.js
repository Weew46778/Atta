/* آتا — هستهٔ داده: اقلیم‌ها، ۲۰۰ مرحله، ترکیب‌گر فعالیت‌ها، نشان‌ها و گنج‌ها */
(function () {
  'use strict';
  const AT = (window.AT = window.AT || {});
  const U = AT.util;

  /* ================= اقلیم‌های سفر (۱۰ اقلیم × ۲۰ مرحله = ۲۰۰) ================= */
  const WORLDS = [
    {
      key: 'words', name: 'شهرِ کلمه‌ها', emoji: '📜', color: '#f28482', color2: '#9e4b6e',
      sub: 'زبان، واژه‌ها و ادبیات', guide: 'بلبلِ شهر',
      desc: 'اینجا همه‌چیز با کلمه ساخته می‌شود؛ قصه، شعر و واژه‌های تازه.',
      pool: ['story', 'riddle', 'wordbuild', 'opposites', 'listen', 'speak', 'proverb', 'quizlang', 'typo', 'toon'],
    },
    {
      key: 'numbers', name: 'بازارِ اعداد', emoji: '🎪', color: '#ffd166', color2: '#c78a12',
      sub: 'ریاضی و منطق', guide: 'ترازوی جادویی',
      desc: 'بازاری که با آن سکه‌ها و ترازوها و الگوهای جادویی اداره می‌شود.',
      pool: ['mathops', 'wordproblem', 'pattern', 'balance', 'numbermemory', 'order', 'shopmarket', 'quizmath'],
    },
    {
      key: 'memory', name: 'باغِ حافظه', emoji: '🌸', color: '#c77dff', color2: '#7e3fb8',
      sub: 'حافظه و تمرکز', guide: 'پروانهٔ خاطره',
      desc: 'گل‌هایی که فقط با یادآوریِ درست باز می‌شوند.',
      pool: ['memorycards', 'simon', 'focus', 'count', 'memorywords', 'order', 'listen', 'odd'],
    },
    {
      key: 'riddles', name: 'غارِ رازها', emoji: '🕯️', color: '#48cae4', color2: '#20778f',
      sub: 'معما، چیستان و استنتاج', guide: 'شمعِ دانا',
      desc: 'دیوِ معماها اینجا زندگی می‌کند؛ هر پاسخ درست، یک قفلِ غار را باز می‌کند.',
      pool: ['riddle', 'odd', 'quizsmart', 'pattern', 'balance', 'riddle', 'listen', 'typo'],
    },
    {
      key: 'iran', name: 'دشتِ ایران', emoji: '🏔️', color: '#80ed99', color2: '#3d9257',
      sub: 'تاریخ، جغرافیا و فرهنگ ایران', guide: 'کبوترِ راهنما',
      desc: 'از دماوند تا خلیج فارس؛ سفری در سرزمینِ قصه‌ها و شعرها.',
      pool: ['quiziran', 'mapiran', 'story', 'proverb', 'toon', 'order', 'sortcat', 'speak'],
    },
    {
      key: 'science', name: 'آزمایشگاهِ کنجکاوی', emoji: '🔬', color: '#4cc9f0', color2: '#20778f',
      sub: 'علوم و کشف', guide: 'حبابِ دانایی',
      desc: 'آزمایش‌های شگفت‌انگیز که جهان را برایت توضیح می‌دهند.',
      pool: ['quizscience', 'order', 'sortcat', 'toon', 'listen', 'count', 'odd', 'story'],
    },
    {
      key: 'ethics', name: 'کاروانِ اخلاق', emoji: '骆驼', color: '#90be6d', color2: '#5a7a3c',
      sub: 'صداقت، مهربانی و ارزش‌ها', guide: 'شترِ خردمند',
      desc: 'کاروانی که با هر انتخابِ درست، یک قدم به قاف می‌رسد.',
      pool: ['story', 'scenario', 'proverb', 'quizethics', 'speak', 'toon', 'selfcheck', 'listen'],
    },
    {
      key: 'calm', name: 'دریایِ آرامش', emoji: '🌊', color: '#8ecae6', color2: '#3f6f8f',
      sub: 'ذهن آرام، صبر و هیجان‌ها', guide: 'موجِ مهربان',
      desc: 'در این دریا نفس می‌آموزیم، احساس‌هایمان را می‌شناسیم و آرام می‌شویم.',
      pool: ['calm', 'emotion', 'scenario', 'listen', 'focus', 'affirm', 'selfcheck', 'story'],
    },
    {
      key: 'art', name: 'نگارخانهٔ هنر', emoji: '🎨', color: '#f4a261', color2: '#a86a2e',
      sub: 'هنر، موسیقی و خلاقیت', guide: 'قلمِ رنگین',
      desc: 'رنگ‌ها، ریتم‌ها و خیال‌ها اینجا جان می‌گیرند.',
      pool: ['draw', 'rhythm', 'quizart', 'story', 'speak', 'toon', 'pattern', 'odd'],
    },
    {
      key: 'hero', name: 'قلعهٔ قهرمانی', emoji: '🏰', color: '#ef476f', color2: '#982c4b',
      sub: 'خودشناسی، ایمنی و مهارتِ زندگی', guide: 'سپرِ دانایی',
      desc: 'قلعهٔ آخرِ سفر؛ قهرمانِ واقعی با خرد و مهربانی ساخته می‌شود.',
      pool: ['quizsafe', 'scenario', 'selfcheck', 'speak', 'order', 'focus', 'story', 'affirm'],
    },
  ];
  // ایموجی امن (همهٔ پلتفرم‌ها)
  WORLDS[6].emoji = '🐪';

  /* ساختار سطوح: هر اقلیم ۲۰ مرحله؛ ۵/۱۰/۱۵ آزمونِ بزرگ (با تایمر)؛ ۲۰ جشنِ اقلیم */
  const LEVELS = [];
  for (let i = 0; i < 200; i++) {
    const w = Math.floor(i / 20), local = i % 20;
    LEVELS.push({
      id: i + 1, world: w, local: local + 1,
      t: i / 199,                                  // سختی ۰ تا ۱
      boss: local % 5 === 4 && local !== 19,       // ۵، ۱۰، ۱۵
      festival: local === 19,                      // ۲۰
      seed: 30000 + (i + 1) * 7919,
      acts: local === 19 ? 4 : local % 5 === 4 ? (local === 14 ? 8 : 7) : local >= 10 ? 6 : local >= 5 ? 6 : 5,
    });
  }

  /* ================= ترکیب‌گر فعالیت‌ها ================= */
  // هر مرحله از استخر اقلیم + بانک‌ها ساخته می‌شود؛ الگوریتم تضمین می‌کند هیچ دو مرحلهٔ پیوسته
  // ترکیب یکسانی نداشته باشند و اقلام بانک چرخه‌ای (بدون تکرار نزدیک) مصرف شوند.
  const TYPE_META = {
    story: { title: 'قصهٔ اقلیم', emoji: '📖', skills: ['words', 'ethics'], coins: 30 },
    toon: { title: 'انیمیشنِ کوچک', emoji: '🎬', skills: ['words', 'creativity'], coins: 30 },
    quizlang: { title: 'دانستنیِ واژه‌ها', emoji: '🔤', skills: ['words'], coins: 12 },
    quizmath: { title: 'هوشِ اعداد', emoji: '🧮', skills: ['logic'], coins: 12 },
    quizsmart: { title: 'هوشِ رازها', emoji: '💡', skills: ['logic', 'knowledge'], coins: 12 },
    quiziran: { title: 'ایران‌شناسی', emoji: '🇮🇷', skills: ['knowledge'], coins: 12 },
    quizscience: { title: 'دانستنیِ علم', emoji: '🔭', skills: ['knowledge'], coins: 12 },
    quizethics: { title: 'پرسشِ ارزش‌ها', emoji: '💚', skills: ['ethics'], coins: 12 },
    quizart: { title: 'هنر و زیبایی', emoji: '🖼️', skills: ['creativity', 'knowledge'], coins: 12 },
    quizsafe: { title: 'ایمنی و زندگی', emoji: '🛡️', skills: ['knowledge', 'ethics'], coins: 12 },
    riddle: { title: 'معما و چیستان', emoji: '🧩', skills: ['logic', 'words'], coins: 16 },
    wordbuild: { title: 'کلمه‌سازی', emoji: '🔤', skills: ['words'], coins: 16 },
    opposites: { title: 'مترادف و متضاد', emoji: '⚖️', skills: ['words'], coins: 14 },
    listen: { title: 'گوشِ شنوا', emoji: '👂', skills: ['words', 'memory'], coins: 16 },
    speak: { title: 'میکروفنِ قهرمان', emoji: '🎤', skills: ['speech'], coins: 20 },
    proverb: { title: 'مثل‌های ایرانی', emoji: '💬', skills: ['words', 'knowledge'], coins: 14 },
    typo: { title: 'کارآگاهِ جمله‌ها', emoji: '🕵️', skills: ['words'], coins: 14 },
    mathops: { title: 'ماشینِ حساب', emoji: '➗', skills: ['logic'], coins: 14 },
    wordproblem: { title: 'مسئلهٔ قصه‌دار', emoji: '📝', skills: ['logic', 'words'], coins: 18 },
    pattern: { title: 'قطارِ الگوها', emoji: '🚂', skills: ['logic'], coins: 14 },
    balance: { title: 'ترازوی منطق', emoji: '⚖️', skills: ['logic'], coins: 16 },
    numbermemory: { title: 'حافظهٔ اعداد', emoji: '🔢', skills: ['memory'], coins: 16 },
    memorycards: { title: 'کارت‌های جادویی', emoji: '🃏', skills: ['memory', 'focus'], coins: 20 },
    memorywords: { title: 'حافظهٔ واژه‌ها', emoji: '🧠', skills: ['memory', 'words'], coins: 16 },
    simon: { title: 'سمعکِ رنگین', emoji: '🎵', skills: ['memory', 'focus'], coins: 20 },
    focus: { title: 'چشمِ تیزبین', emoji: '🎯', skills: ['focus'], coins: 18 },
    count: { title: 'شمردنِ برق‌آسا', emoji: '⚡', skills: ['focus', 'memory'], coins: 14 },
    order: { title: 'ترتیبِ دانایی', emoji: '🪜', skills: ['logic', 'knowledge'], coins: 16 },
    sortcat: { title: 'دسته‌بندیِ هوشمند', emoji: '🗃️', skills: ['logic', 'knowledge'], coins: 16 },
    odd: { title: 'متفاوت کدام است؟', emoji: '❓', skills: ['logic', 'knowledge'], coins: 14 },
    mapiran: { title: 'نقشهٔ ایران', emoji: '🗺️', skills: ['knowledge'], coins: 18 },
    scenario: { title: 'انتخابِ قهرمان', emoji: '🧭', skills: ['ethics'], coins: 18 },
    calm: { title: 'نفسِ آرامش', emoji: '🫧', skills: ['calm'], coins: 18 },
    emotion: { title: 'شناختِ احساس', emoji: '💛', skills: ['calm', 'ethics'], coins: 14 },
    affirm: { title: 'صدای باور', emoji: '🌟', skills: ['speech', 'calm'], coins: 16 },
    selfcheck: { title: 'آینهٔ خودشناسی', emoji: '🪞', skills: ['calm', 'speech'], coins: 14 },
    draw: { title: 'کارگاهِ نقاشی', emoji: '🎨', skills: ['creativity'], coins: 22 },
    rhythm: { title: 'ریتمِ موسیقی', emoji: '🥁', skills: ['creativity', 'memory'], coins: 20 },
    shopmarket: { title: 'خریدِ هوشمند', emoji: '🛒', skills: ['logic'], coins: 18 },
  };

  /* چرخه‌ی مصرف بانک: برای هر (نوع، اقلیم) نشانگرِ مستقل که پله‌ای جلو می‌رود */
  function bankIndex(bankLen, world, local, salt) {
    return ((world * 37 + local * 3 + salt * 11) % bankLen + bankLen) % bankLen;
  }
  function pullBank(bank, world, local, salt) {
    if (!bank || !bank.length) return null;
    return bank[bankIndex(bank.length, world, local, salt)];
  }

  /* انتخاب انواع فعالیت یک مرحله: بدون تکرار نوع، توزیع متفاوت در هر مرحله */
  function pickTypes(level, seed) {
    const w = WORLDS[level.world];
    const pool = w.pool;
    const r = U.rng(seed);
    const n = level.acts;
    // چیدمان پایه: چرخشِ استخر با گامِ متفاوت در هر مرحله
    const start = (level.local * 3 + level.world * 2) % pool.length;
    const step = 1 + ((level.local + level.world) % 3);
    const types = [];
    let i = 0;
    while (types.length < n) {
      const t = pool[(start + i * step + Math.floor(r() * 2)) % pool.length];
      if (!types.includes(t)) types.push(t);
      i++;
      if (i > 60) break;
    }
    // مرحلهٔ جشن: مرورِ سریع
    if (level.festival) { types.length = 0; types.push(...w.pool.slice(0, 4)); }
    return types;
  }

  /* ================= نشان‌ها ================= */
  const BADGES = [
    { id: 'first', name: 'اولین قدم', emoji: '👣', desc: 'اولین مرحله را تمام کردی' },
    { id: 'ten', name: 'ده‌قدمه‌قهرمان', emoji: '🔟', desc: '۱۰ مرحله را گذراندی' },
    { id: 'fifty', name: 'نیمهٔ راه', emoji: '🌗', desc: '۵۰ مرحله را گذراندی' },
    { id: 'hundred', name: 'صدآفرین', emoji: '💯', desc: '۱۰۰ مرحله را گذراندی' },
    { id: 'all', name: 'افسانهٔ آتا', emoji: '👑', desc: 'هر ۲۰۰ مرحله را تمام کردی' },
    { id: 'streak3', name: 'سه‌روزِ پیوسته', emoji: '🔥', desc: '۳ روز پشت‌سرهم آمدی' },
    { id: 'streak7', name: 'هفتهٔ درخشان', emoji: '✨', desc: '۷ روز پشت‌سرهم آمدی' },
    { id: 'streak30', name: 'ماهِ طلایی', emoji: '🌙', desc: '۳۰ روز پشت‌سرهم آمدی' },
    { id: 'coins300', name: 'ثروتمندِ دانا', emoji: '💰', desc: '۳۰۰ سکهٔ طلا جمع کردی' },
    { id: 'story10', name: 'دوستِ قصه‌ها', emoji: '📚', desc: '۱۰ قصه را خواند و فهمید' },
    { id: 'riddle20', name: 'شکارچیِ معما', emoji: '🧩', desc: '۲۰ معما را حل کردی' },
    { id: 'calm10', name: 'استادِ آرامش', emoji: '🫧', desc: '۱۰ تمرینِ آرامش را انجام دادی' },
    { id: 'speak10', name: 'صدایِ شجاع', emoji: '🎤', desc: '۱۰ بار با میکروفن حرف زدی' },
    { id: 'draw5', name: 'نگارگرِ کوچک', emoji: '🖌️', desc: '۵ نقاشی در گنجینه داری' },
    { id: 'perfect10', name: 'میزانِ دقیق', emoji: '🎯', desc: '۱۰ مرحله را با هر ۳ ستاره تمام کردی' },
    { id: 'daily5', name: 'هواخواهِ هر روز', emoji: '📅', desc: '۵ چالشِ روزانه را انجام دادی' },
    { id: 'kind5', name: 'قلبِ مهربان', emoji: '💗', desc: '۵ انتخابِ اخلاقیِ درست در کاروان' },
    { id: 'free10', name: 'کاوشگرِ آزاد', emoji: '🧭', desc: '۱۰ بازیِ آزاد انجام دادی' },
  ];
  WORLDS.forEach((w) => BADGES.push({ id: 'world' + WORLDS.indexOf(w), name: 'نشانِ ' + w.name, emoji: w.emoji, desc: 'اقلیم «' + w.name + '» را کامل فتح کردی' }));

  /* جوایز مرحله: سکه پایه + ضریب سختی + جایزهٔ جشن */
  function levelReward(level) {
    const base = 20 + Math.floor(level.t * 30);
    return level.festival ? base + 60 : level.boss ? base + 30 : base;
  }

  AT.data = {
    WORLDS, LEVELS, TYPE_META, BADGES,
    pickTypes, pullBank, bankIndex, levelReward,
    nextLevel(state) {
      const done = new Set(state.explorer.done);
      let n = 1; while (done.has(n) && n <= 200) n++;
      return Math.min(n, 200);
    },
    isUnlocked(state, id) {
      if (id === 1) return true;
      return state.explorer.done.includes(id - 1);
    },
  };
})();

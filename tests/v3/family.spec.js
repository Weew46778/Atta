import {test,expect} from '@playwright/test';
import {TODDLER_SESSIONS,HOT_MOMENTS,WORKSHOPS,CALM_SESSIONS,COOP_GAMES,AWAY_DAYS,HOME_DAYS,PHONE_GAMES,FATHER_COACHING} from '../../src/v3/content/family.js';

/* ------------------------------------------------------------------
   سه نسخهٔ خانواده: جوانه (۲–۳ سال)، همراهِ مادر و همراهِ پدر.
   جریانِ واقعیِ هر صفحه با کلیکِ واقعی سنجیده می‌شود.
------------------------------------------------------------------ */

const initState=()=>({v:3,
  settings:{sound:true,voice:'off',rate:1,autoSpeak:false,bigText:false,motion:'calm',breakMinutes:60},
  names:{explorer:'کاوشگر',toddler:'جوانه',mother:'مامان',father:'بابا'}});

async function boot(page,edition){
  const errors=[];
  page.on('pageerror',e=>errors.push(String(e)));
  page.on('dialog',d=>d.accept());
  await page.addInitScript(s=>localStorage.setItem('bagh.v3.state',JSON.stringify(s)),initState());
  await page.goto(`/?edition=${edition}&test=1`);
  await page.waitForSelector('#app');
  return errors;
}
const sessionOf=type=>TODDLER_SESSIONS.find(s=>s.type===type);
const openSession=async(page,s)=>{
  await page.locator(`.ts-card[data-id="${s.id}"]`).click();
  await page.waitForSelector('.session-toddler .st-body',{timeout:20000});
};

/* ---------------- جوانه: صفحهٔ جلسه‌ها ---------------- */
test('جوانه: ۴۵ جلسهٔ گفتاری روی صفحه است و راهنمای والد باز می‌شود',async({page})=>{
  test.setTimeout(120000);
  const errors=await boot(page,'toddler');
  expect(await page.locator('.ts-card').count()).toBe(45);
  expect(TODDLER_SESSIONS.length).toBe(45);
  await expect(page.locator('#start')).toBeVisible();
  await expect(page.locator('.toddler-beds .bed').first()).toBeVisible();

  const s=sessionOf('story');
  await openSession(page,s);
  await expect(page.locator('.st-top')).toContainText('قصهٔ ما');
  // راهنمای والد باز و بسته می‌شود
  await expect(page.locator('.tp-body')).not.toHaveClass(/open/);
  await page.locator('.tp-toggle').click();
  await expect(page.locator('.tp-body')).toHaveClass(/open/);
  await expect(page.locator('.tp-body')).toContainText(s.tip.slice(0,12));
  expect(errors).toEqual([]);
});

/* ---------------- جوانه: «بگو با من» (گفتارِ کودک) ---------------- */
test('جوانه: جلسهٔ «بگو با من» واژه را سه‌بار می‌گوید و صدای کودک را می‌گیرد',async({page})=>{
  test.setTimeout(240000);
  const errors=await boot(page,'toddler');
  const s=sessionOf('repeat');
  await openSession(page,s);
  for(const w of s.words){
    await expect(page.locator('.word-stage h2')).toHaveText(w.w,{timeout:20000});
    await expect(page.locator('.word-emoji')).toHaveText(w.e);
    await page.locator('#again').click();                       // «دوباره بشنو»
    await page.locator('#say').click();                         // «تو بگو»
    await expect(page.locator('.toddler-cheer')).toBeVisible({timeout:20000});
    await page.locator('.toddler-cheer + .toddler-big').click(); // «بعدی»
  }
  await expect(page.locator('.toddler-finish h2')).toBeVisible({timeout:20000});
  const words=await page.evaluate(()=>JSON.parse(localStorage.getItem('bagh.v3.state')).toddler.words);
  for(const w of s.words) expect(words[w.w],'واژه ثبت نشد: '+w.w).toBeGreaterThanOrEqual(1);
  expect(await page.evaluate(()=>JSON.parse(localStorage.getItem('bagh.v3.state')).toddler.sessionsDone)).toContain(s.id);
  await page.locator('#ok').click();
  await expect(page.locator('.session-toddler')).toHaveCount(0);
  expect(errors).toEqual([]);
});

/* ---------------- جوانه: «پیدا کن» (بازگشتِ باگِ قفل‌شدن) ---------------- */
test('جوانه: «پیدا کن» با زدنِ کارتِ درست و «بعدی» جلو می‌رود',async({page})=>{
  test.setTimeout(240000);
  const errors=await boot(page,'toddler');
  const s=sessionOf('find');
  await openSession(page,s);
  for(let r=0;r<3;r++){
    const asked=await page.locator('.find-q b').textContent();
    expect(asked,'سؤالِ دورِ '+(r+1)).toBeTruthy();
    const want=s.words.find(w=>w.w===asked);
    expect(want,'واژهٔ پرسیده‌شده در جلسه نیست: '+asked).toBeTruthy();
    await expect(page.locator('.find-card').filter({hasText:want.e})).toHaveCount(1);
    await page.locator('.find-card').filter({hasText:want.e}).click();
    await expect(page.locator('.toddler-cheer')).toBeVisible();
    await page.locator('.toddler-cheer + .toddler-big').click();   // «بعدی» — پیش‌تر اینجا قفل می‌شد
    if(r<2) await expect(page.locator('.find-q b')).not.toHaveText(asked,{timeout:15000});
  }
  await expect(page.locator('.toddler-finish h2')).toBeVisible({timeout:15000});
  expect(errors).toEqual([]);
});

/* ---------------- جوانه: قصه، آواز، احساس و روتین ---------------- */
test('جوانه: قصه، آواز، احساس و روتینِ شب کامل می‌شوند',async({page})=>{
  test.setTimeout(300000);
  const errors=await boot(page,'toddler');

  // قصه: خط به خط با «بعدی»
  const st=sessionOf('story');
  await openSession(page,st);
  // اگر جلسه قصهٔ آماده نداشت، برنامه همان قصهٔ پیش‌فرض را می‌گوید
  const lines=(st.story&&st.story.lines)||['یکی بود، یکی نبود.','یک بچهٔ مهربان بود.','هر روز لبخند می‌زد.','و شبِ خوبی داشت.'];
  expect(lines.length).toBeGreaterThan(1);
  for(const line of lines){
    await expect(page.locator('#tline')).toHaveText(line,{timeout:20000});
    await page.locator('#tnext').click();
  }
  await expect(page.locator('.toddler-finish')).toBeVisible({timeout:20000});
  await page.locator('#ok').click();

  // آواز: با طبل می‌خواند و تمام می‌کند
  const so=sessionOf('song');
  await openSession(page,so);
  await expect(page.locator('.tsong h3')).toContainText(so.song.title);
  expect(await page.locator('.tsong-lines p').count()).toBe(so.song.lines.length);
  await page.locator('#drum').click();
  await expect(page.locator('.toddler-cheer')).toBeVisible({timeout:60000});
  await page.locator('.st-body .btn.primary').click();
  await expect(page.locator('.toddler-finish')).toBeVisible({timeout:20000});
  await page.locator('#ok').click();

  // احساس: هر چهار صورت
  const fe=sessionOf('feel');
  await openSession(page,fe);
  for(let k=0;k<4;k++){
    await expect(page.locator('.feel-face')).toBeVisible({timeout:20000});
    await page.locator('#f').click();
    await expect(page.locator('.toddler-cheer')).toBeVisible({timeout:20000});
    await page.locator('.toddler-cheer + .toddler-big').click();
  }
  await expect(page.locator('.toddler-finish')).toBeVisible({timeout:20000});
  await page.locator('#ok').click();

  // روتینِ شب
  const ro=sessionOf('routine');
  await openSession(page,ro);
  const boxes=page.locator('.routine-list input[type=checkbox]');
  expect(await boxes.count()).toBe(5);
  for(let k=0;k<5;k++) await boxes.nth(k).check();
  await page.locator('.st-body .toddler-big').click();
  await expect(page.locator('.toddler-finish')).toBeVisible({timeout:20000});
  const done=await page.evaluate(()=>JSON.parse(localStorage.getItem('bagh.v3.state')).toddler.sessionsDone);
  for(const s of [st,so,fe,ro]) expect(done,'جلسه ثبت نشد: '+s.id).toContain(s.id);
  expect(errors).toEqual([]);
});

/* ---------------- همراهِ مادر ---------------- */
test('مادر: کارتِ لحظهٔ داغ جمله، پرهیز و پروتکل می‌دهد و ثبت می‌شود',async({page})=>{
  test.setTimeout(180000);
  const errors=await boot(page,'mother');
  await page.locator('.p-nav button[data-t="hot"]').click();
  expect(await page.locator('.hot-card').count()).toBe(HOT_MOMENTS.length);
  expect(HOT_MOMENTS.length).toBeGreaterThanOrEqual(16);

  const hm=HOT_MOMENTS[3];
  await page.locator(`.hot-card[data-id="${hm.id}"]`).click();
  await expect(page.locator('.p-card h2')).toHaveText(hm.t);
  await expect(page.locator('.hm-what p')).toContainText(hm.what.slice(0,20));
  expect(await page.locator('.hm-say blockquote').count()).toBe(hm.say.length);
  await expect(page.locator('.hm-say blockquote').first()).toContainText(hm.say[0].slice(0,10));
  expect(await page.locator('.hm-avoid li').count()).toBe(hm.avoid.length);
  expect(await page.locator('.hm-proto li').count()).toBe(hm.protocol.length);
  expect(hm.protocol.length).toBeGreaterThanOrEqual(4);
  await page.locator('#say-all').click();                      // «بشنو»
  await page.locator('#done-hm').click();                      // «استفاده کردم»
  await expect(page.locator('.p-card')).toHaveCount(0);
  const used=await page.evaluate(()=>JSON.parse(localStorage.getItem('bagh.v3.state')).mother.hotMoments);
  expect(used.map(u=>u.id)).toContain(hm.id);
  expect(errors).toEqual([]);
});

test('مادر: کارگاه، آرامش، بازیِ دو بچه، داشبورد و برنامهٔ خانواده باز می‌شوند',async({page})=>{
  test.setTimeout(180000);
  const errors=await boot(page,'mother');
  const tabs=[
    ['work','کارگاه'],
    ['calm','آرام'],
    ['coop','بازی'],
    ['kids','مهارت'],
    ['plan','برنامه']
  ];
  for(const [t,label] of tabs){
    await page.locator(`.p-nav button[data-t="${t}"]`).click();
    const text=await page.locator('#pmain').textContent();
    expect(text,`زبانهٔ ${t} خالی است`).toBeTruthy();
    expect(text.length,`زبانهٔ ${t} محتوا ندارد`).toBeGreaterThan(120);
  }
  // شمارِ محتوا با آزمونِ محتوا یکی باشد
  await page.locator('.p-nav button[data-t="work"]').click();
  expect(await page.locator('#pmain').textContent()).toContain(WORKSHOPS[0].t);
  await page.locator('.p-nav button[data-t="calm"]').click();
  expect(await page.locator('#pmain').textContent()).toContain(CALM_SESSIONS[0].t);
  await page.locator('.p-nav button[data-t="coop"]').click();
  expect(await page.locator('#pmain').textContent()).toContain(COOP_GAMES[0].t);
  expect(WORKSHOPS.length).toBeGreaterThanOrEqual(16);
  expect(CALM_SESSIONS.length).toBeGreaterThanOrEqual(6);
  expect(COOP_GAMES.length).toBeGreaterThanOrEqual(10);
  expect(errors).toEqual([]);
});

/* ---------------- همراهِ پدر ---------------- */
test('پدر: چرخهٔ ۱۵ روزه، روزهای دوری و حضور، بازیِ تلفنی و پیامِ صوتی',async({page})=>{
  test.setTimeout(180000);
  const errors=await boot(page,'father');
  const tabs=[
    ['today','امروز'],
    ['away','دوری'],
    ['home','حضور'],
    ['games','تلفنی'],
    ['coach','مربی'],
    ['kids','داشبورد']
  ];
  for(const [t] of tabs){
    await page.locator(`.p-nav button[data-t="${t}"]`).click();
    const text=await page.locator('#pmain').textContent();
    expect(text.length,`زبانهٔ ${t} محتوا ندارد`).toBeGreaterThan(120);
  }
  // چرخهٔ ۱۰ روز دوری + ۵ روز حضور (در زبانهٔ «امروز»)
  await page.locator('.p-nav button[data-t="today"]').click();
  const strip=await page.evaluate(()=>{
    const el=document.querySelector('.cycle-strip');
    if(!el) return null;
    return {away:el.querySelectorAll('.away').length,home:el.querySelectorAll('.home').length};
  });
  expect(strip,'نوارِ چرخه پیدا نشد').toBeTruthy();
  expect(strip.away).toBe(AWAY_DAYS.length);
  expect(strip.home).toBe(HOME_DAYS.length);
  await page.locator('.p-nav button[data-t="away"]').click();
  expect(await page.locator('#pmain').textContent()).toContain(AWAY_DAYS[0].focus);

  await page.locator('.p-nav button[data-t="home"]').click();
  expect(await page.locator('#pmain').textContent()).toContain(HOME_DAYS[0].title);
  expect(HOME_DAYS[0].plan.length).toBeGreaterThanOrEqual(4);

  await page.locator('.p-nav button[data-t="games"]').click();
  expect(await page.locator('#pmain').textContent()).toContain(PHONE_GAMES[0].t);
  expect(PHONE_GAMES.length).toBeGreaterThanOrEqual(10);

  await page.locator('.p-nav button[data-t="coach"]').click();
  expect(await page.locator('#pmain').textContent()).toContain(FATHER_COACHING[0].t);
  expect(FATHER_COACHING.length).toBeGreaterThanOrEqual(8);
  expect(errors).toEqual([]);
});

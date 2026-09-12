import {test,expect} from '@playwright/test';
import {TOTAL} from '../../src/v3/content/missions.js';

/* ------------------------------------------------------------------
   پیمایشِ کامل: هر ۲۴۰ مأموریت واقعاً باز و کامل می‌شود، هیچ خطای
   صفحه‌ای رخ نمی‌دهد، و صفحه‌ها روی گوشیِ ۴۳۰ پیکسلی سرریز ندارند.
   حالتِ ?test (بدونِ noauto) ایستگاه‌ها را خودکار جلو می‌برد تا
   «مسیرِ واقعیِ اجرا»ی موتور روی همهٔ محتوا سنجیده شود.
------------------------------------------------------------------ */

const initState=(extra={})=>({v:3,
  settings:{sound:true,voice:'off',rate:1,autoSpeak:false,bigText:false,motion:'calm',breakMinutes:60},
  names:{explorer:'کاوشگر',toddler:'جوانه',mother:'مامان',father:'بابا'},...extra});

async function boot(page,edition='explorer',auto=true){
  const errors=[];
  page.on('pageerror',e=>errors.push(String(e)));
  page.on('dialog',d=>d.accept());
  await page.addInitScript(s=>localStorage.setItem('bagh.v3.state',JSON.stringify(s)),initState());
  await page.goto(`/?edition=${edition}&test=1${auto?'':'&noauto=1'}`);
  await page.waitForSelector('#app');
  await page.waitForFunction(()=>typeof window.__baghTest==='object',{timeout:20000});
  return errors;
}

test('۲۴۰ مأموریت: همه باز می‌شوند، کامل می‌شوند و خطایی نمی‌دهند',async({page})=>{
  test.setTimeout(900000);
  const errors=await boot(page);
  const total=await page.evaluate(()=>window.__baghTest.missionsTotal);
  expect(total).toBe(TOTAL());
  expect(total).toBe(240);

  const perStation=[];
  for(let i=0;i<total;i++){
    if(await page.locator('.mission-layer').count()) await page.waitForSelector('.mission-layer',{state:'detached',timeout:30000});
    await page.evaluate(i=>window.__baghTest.start(i),i);
    // مأموریت باید کامل شود و در فهرستِ «انجام‌شده» بنشیند
    await page.waitForFunction(i=>window.__baghTest.state.explorer.missionsDone.includes(i),i,{timeout:45000});
    if(i%40===39){
      const m=await page.evaluate(()=>window.__baghTest.metrics());
      perStation.push(`#${i+1}: ${m.missions} مأموریت، ${m.collectibles} یادگاری، سطحِ مهارت ${m.stations} رویداد`);
    }
  }
  const m=await page.evaluate(()=>window.__baghTest.metrics());
  const detail=await page.evaluate(()=>({
    done:window.__baghTest.state.explorer.missionsDone.length,
    unique:window.__baghTest.state.explorer.collectibles.map(c=>c.name).filter((v,i,a)=>a.indexOf(v)===i).length,
    mechs:window.__baghTest.state.explorer.telemetry.map(t=>t.mech).filter((v,i,a)=>a.indexOf(v)===i).length,
    skills:window.__baghTest.state.explorer.skills,
    xp:window.__baghTest.state.explorer.xp,
    tele:window.__baghTest.state.explorer.telemetry.length
  }));
  console.log('پیمایش:',perStation.join(' | '));

  expect(errors,`خطای صفحه در پیمایش: ${errors.slice(0,3).join(' | ')}`).toEqual([]);
  expect(detail.done).toBe(240);
  expect(detail.unique,'یادگاریِ تکراری داده شده').toBe(240);
  expect(detail.mechs,'تنوعِ سازوکارها کم است').toBeGreaterThanOrEqual(30);
  expect(detail.tele).toBe(600);              // سقفِ تله‌متری رعایت می‌شود
  expect(detail.xp).toBeGreaterThan(240*40);
  for(const [k,v] of Object.entries(detail.skills)) expect(v,`مهارتِ ${k} رشد نکرد`).toBeGreaterThan(0);
});

test('صفحه‌های کودک در ۴۳۰ پیکسل بدونِ سرریزِ افقی باز می‌شوند',async({page})=>{
  test.setTimeout(180000);
  const errors=await boot(page);
  // چند مأموریت تا صفحه‌ها دادهٔ واقعی داشته باشند
  for(let i=0;i<6;i++){
    if(await page.locator('.mission-layer').count()) await page.waitForSelector('.mission-layer',{state:'detached',timeout:30000});
    await page.evaluate(i=>window.__baghTest.start(i),i);
    await page.waitForFunction(i=>window.__baghTest.state.explorer.missionsDone.includes(i),i,{timeout:45000});
  }
  const checks={
    home:'.hero h1',
    journey:'.journey-scroll, .j-world',
    gallery:'.gallery-grid, .pride-list',
    report:'.radar, .rec-list',
    settings:'.setting-row'
  };
  for(const [p,sel] of Object.entries(checks)){
    await page.evaluate(p=>window.__baghTest.go(p),p);
    await page.waitForSelector(sel,{timeout:20000});
    const w=await page.evaluate(()=>({sw:document.documentElement.scrollWidth,iw:window.innerWidth}));
    expect(w.sw,`صفحهٔ ${p} سرریزِ افقی دارد: ${w.sw} > ${w.iw}`).toBeLessThanOrEqual(w.iw+1);
  }
  // قفلِ مرحله‌ها: مأموریتِ هفتم هنوز باز نشده
  const locked=await page.evaluate(()=>window.__baghTest.state.explorer.missionsDone.length);
  expect(locked).toBe(6);
  expect(errors).toEqual([]);
});

test('گوینده: خواندنِ خودکارِ متن‌ها بدونِ خطا اجرا می‌شود',async({page})=>{
  test.setTimeout(300000);
  const errors=[];
  page.on('pageerror',e=>errors.push(String(e)));
  page.on('dialog',d=>d.accept());
  await page.addInitScript(s=>localStorage.setItem('bagh.v3.state',JSON.stringify(s)),
    initState({settings:{sound:true,voice:'auto',rate:1,autoSpeak:true,bigText:false,motion:'calm',breakMinutes:60}}));
  await page.goto('/?edition=explorer&test=1');
  await page.waitForSelector('#app');
  await page.waitForFunction(()=>typeof window.__baghTest==='object',{timeout:20000});
  await page.evaluate(()=>window.__baghTest.start(0));
  await page.waitForFunction(()=>window.__baghTest.state.explorer.missionsDone.includes(0),null,{timeout:240000});
  // گوینده یا صدای دستگاه را پیدا می‌کند یا به متن برمی‌گردد؛ در هر دو حالت نباید بشکند
  const src=await page.evaluate(()=>window.__baghTest.state.settings.voice);
  expect(src).toBe('auto');
  expect(errors,`خطای صفحه در مسیرِ گوینده: ${errors.slice(0,3).join(' | ')}`).toEqual([]);
});

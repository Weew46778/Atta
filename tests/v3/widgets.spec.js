import {test,expect} from '@playwright/test';
import {buildMission} from '../../src/v3/content/missions.js';
import {emptyFamily} from '../../src/v3/core/store.js';
import {PROVINCES} from '../../src/v3/content/iran.js';

/* ------------------------------------------------------------------
   این فایل فقط با «تعاملِ واقعی» کار می‌کند: کلیک روی همان دکمه‌هایی
   که کودک می‌زند، کشیدنِ قلم روی بوم، و خواندنِ همان DOM ای که
   engine/mechanics.js می‌سازد. هیچ‌جا نتیجه را دستی set نمی‌کنیم.
------------------------------------------------------------------ */

const ST=emptyFamily();
const STATION={story:0,game:1,puzzle:2,real:3,create:4,reflect:5};
function findIndex(kind,mech){
  for(let i=0;i<240;i++) if(buildMission(i,ST).stations[STATION[kind]].mech===mech) return i;
  throw new Error(`مأموریتی با سازوکارِ ${mech} در ایستگاهِ ${kind} پیدا نشد`);
}

const sleep=ms=>new Promise(r=>setTimeout(r,ms));

async function boot(page,edition='explorer'){
  const errors=[];
  page.on('pageerror',e=>errors.push(String(e)));
  page.on('dialog',d=>d.accept());           // «خروج از مأموریت؟» همیشه پذیرفته شود
  await page.addInitScript(()=>{
    localStorage.setItem('bagh.v3.state',JSON.stringify({v:3,
      settings:{sound:true,voice:'off',rate:1,autoSpeak:false,bigText:false,motion:'calm',breakMinutes:60},
      names:{explorer:'کاوشگر',toddler:'جوانه',mother:'مامان',father:'بابا'}}));
  });
  await page.goto(`/?edition=${edition}&test=1&noauto=1`);
  await page.waitForSelector('#app');
  await page.waitForFunction(()=>typeof window.__baghTest==='object',{timeout:20000});
  return errors;
}

/** مأموریتِ باز را می‌بندد (با تأییدِ دیالوگ) و به خانه برمی‌گردد */
async function exitMission(page){
  if(!(await page.locator('.mission-layer').count())) return;
  await page.locator('.m-exit').first().click();
  await page.waitForTimeout(250);
  if(await page.locator('.mission-layer').count()){
    await page.evaluate(()=>{window.confirm=()=>true;document.querySelector('.m-exit')?.click();});
    await page.waitForTimeout(250);
  }
  expect(await page.locator('.mission-layer').count(),'مأموریت بسته نشد').toBe(0);
}

async function openStation(page,kind,mech){
  await exitMission(page);
  const i=findIndex(kind,mech);
  await page.evaluate(({i,s})=>{
    window.__baghTest.state.explorer.current={missionId:i,station:s};
    window.__baghTest.start(i);
  },{i,s:STATION[kind]});
  await page.waitForSelector('.station-card .st-body',{timeout:20000});
  return i;
}

const reward=page=>page.waitForSelector('.station-reward,.mission-complete',{timeout:30000});
const atReward=async page=>await page.locator('.station-reward,.mission-complete').count()>0;
/** آخرین .btn.primary همان دکمهٔ «تمام/بعدی» است که سازوکار به پایانِ محتوا اضافه می‌کند */
const next=async page=>{await page.locator('.station-card .btn.primary').last().click();};
const clickOpt=async page=>{await page.locator('.opt:not([disabled])').first().click();};
/** تا رسیدن به پاداش، fn را تکرار می‌کند */
async function solve(page,fn,tries=20,gap=200){
  for(let k=0;k<tries;k++){
    if(await atReward(page)) return;
    await fn(k);
    await page.waitForTimeout(gap);
  }
  await reward(page);
}
async function expectReward(page){
  await reward(page);
  await expect(page.locator('.station-reward,.mission-complete')).toBeVisible();
}

/* ---------------- ۱) سازوکارهای انتخابی (گزینه‌ای) ---------------- */
test('سازوکارهای انتخابی با تعاملِ واقعی کامل می‌شوند',async({page})=>{
  test.setTimeout(300000);
  const errors=await boot(page);
  const cases=[
    ['game','pattern'],['game','oddOne'],['game','mathRace'],
    ['puzzle','quiz'],['puzzle','riddle'],['puzzle','verse'],['puzzle','listen'],
    ['reflect','emotion'],['reflect','dilemma'],['reflect','proverb'],['real','real']
  ];
  for(const [kind,mech] of cases){
    await openStation(page,kind,mech);
    if(mech==='real'){
      // ایستگاهِ «کارِ واقعی»: انجام دادم / بعداً
      await page.locator('#later').click();
    }else if(mech==='oddOne'){
      // چهار کارت؛ با آزمون‌وخطا کارتِ متفاوت را پیدا می‌کنیم
      await solve(page,async()=>{
        if(!(await page.locator('.station-card .btn.primary').count())){
          const n=await page.locator('.odd-item:not([disabled])').count();
          if(n) await page.locator('.odd-item:not([disabled])').first().click();
        }else await next(page);
      },40,220);
    }else{
      await solve(page,async()=>{
        if(await page.locator('.opt:not([disabled])').count()) await clickOpt(page);
        else if(await page.locator('.station-card .btn.primary').count()) await next(page);
      },30);
    }
    await expectReward(page);
    // نتیجهٔ ایستگاه در گزارشِ زندهٔ مأموریت ثبت شده باشد
    const logged=await page.evaluate(()=>{
      const log=(window.__baghTest.state.explorer.current||{}).log||[];
      return log.length?log[log.length-1]:null;
    });
    expect(logged,`ایستگاهِ ${mech} در گزارش ثبت نشد`).toBeTruthy();
    expect(logged.mech).toBe(mech);
    expect(logged.stars).toBeGreaterThanOrEqual(0);
  }
  expect(errors,`خطای صفحه: ${errors.join(' | ')}`).toEqual([]);
});

/* ---------------- ۲) کارت‌های حافظه ---------------- */
test('کارت‌های حافظه: جفت‌ها با کلیکِ واقعی پیدا می‌شوند',async({page})=>{
  test.setTimeout(180000);
  const errors=await boot(page);
  await openStation(page,'game','memoryPairs');
  const fronts=await page.$$eval('.mem-card .front',els=>els.map(e=>e.textContent));
  expect(fronts.length).toBeGreaterThanOrEqual(8);
  const seen={};
  for(let k=0;k<fronts.length;k++){
    const v=fronts[k];
    if(seen[v]===undefined){seen[v]=k;continue;}
    await page.locator('.mem-card').nth(seen[v]).click();
    await page.locator('.mem-card').nth(k).click();
    await page.waitForTimeout(750);
  }
  await solve(page,async()=>{if(await page.locator('.station-card .btn.primary').count())await next(page);},8);
  await expectReward(page);
  expect(errors).toEqual([]);
});

/* ---------------- ۳) چشمِ عقاب ---------------- */
test('چشمِ عقاب: هدف‌ها از میان شبکه پیدا می‌شوند',async({page})=>{
  test.setTimeout(180000);
  const errors=await boot(page);
  await openStation(page,'game','focusFind');
  await solve(page,async()=>{
    const cells=await page.$$eval('.focus-cell',els=>els.map(e=>({t:e.textContent,hit:e.classList.contains('hit')})));
    const targets=await page.$$eval('.target',els=>els.map(e=>e.textContent));
    for(const tg of targets){
      const idx=cells.findIndex(c=>c.t===tg&&!c.hit);
      if(idx>=0){await page.locator('.focus-cell').nth(idx).click();await page.waitForTimeout(80);break;}
    }
    if(await page.locator('.station-card .btn.primary').count()) await next(page);
  },60,120);
  await expectReward(page);
  expect(errors).toEqual([]);
});

/* ---------------- ۴) مسیرِ گنج (BFS روی شبکهٔ واقعی) ---------------- */
test('مسیرِ گنج: با حرکتِ واقعی به صندوقچه می‌رسد',async({page})=>{
  test.setTimeout(240000);
  const errors=await boot(page);
  await openStation(page,'game','path');
  const MV={up:[0,-1],down:[0,1],left:[-1,0],right:[1,0]};
  for(let round=0;round<4;round++){
    if(await atReward(page)) break;
    const grid=await page.$$eval('.path-grid .cell',els=>els.map(e=>({x:+e.dataset.x,y:+e.dataset.y,rock:e.classList.contains('rock')})));
    const size=Math.round(Math.sqrt(grid.length));
    const rock=new Set(grid.filter(c=>c.rock).map(c=>`${c.x},${c.y}`));
    const goal={x:size-1,y:size-1};
    // کوتاه‌ترین مسیر از (0,0) به صندوقچه
    const q=[{x:0,y:0,p:[]}],seen=new Set(['0,0']);
    let found=null;
    while(q.length&&!found){
      const st=q.shift();
      if(st.x===goal.x&&st.y===goal.y){found=st.p;break;}
      for(const d of ['right','down','left','up']){
        const nx=st.x+MV[d][0],ny=st.y+MV[d][1];
        if(nx<0||ny<0||nx>=size||ny>=size||rock.has(`${nx},${ny}`)) continue;
        const k=`${nx},${ny}`;if(seen.has(k)) continue;seen.add(k);
        q.push({x:nx,y:ny,p:[...st.p,d]});
      }
    }
    expect(found,`دورِ ${round+1}: برای صندوقچه مسیری روی شبکه نیست`).toBeTruthy();
    for(const d of found){await page.locator(`.dpad button[data-d="${d}"]`).click();await page.waitForTimeout(60);}
    const info=await page.locator('.path-info').textContent();
    expect(info,`دورِ ${round+1}: شمارندهٔ قدم جلو نرفت`).toContain(String(found.length));
    if(await page.locator('.station-card .btn.primary').count()) await next(page);
    await page.waitForTimeout(200);
  }
  await expectReward(page);
  expect(errors).toEqual([]);
});

/* ---------------- ۵) روباهِ برنامه‌نویس (BFS با جهت) ---------------- */
test('روباهِ برنامه‌نویس: الگوریتمِ BFS مسیر را می‌سازد و اجرا می‌شود',async({page})=>{
  test.setTimeout(180000);
  const errors=await boot(page);
  await openStation(page,'game','bot');
  const size=6;
  const blocked=new Set(await page.$$eval('.bot-grid .cell.rock',els=>els.map(e=>`${e.dataset.x},${e.dataset.y}`)));
  const goal={x:size-1,y:0},DIRS=[[0,-1],[1,0],[0,1],[-1,0]];
  const q=[{s:[0,size-1,0],p:[]}],seen=new Set([`0,${size-1},0`]);
  let found=null;
  while(q.length&&!found){
    const {s,p}=q.shift();
    if(s[0]===goal.x&&s[1]===goal.y){found=p;break;}
    for(const cmd of ['F','L','R']){
      let[x,y,d]=s;
      if(cmd==='F'){x+=DIRS[d][0];y+=DIRS[d][1];}else d=(d+(cmd==='R'?1:3))%4;
      if(x<0||y<0||x>=size||y>=size||blocked.has(`${x},${y}`)) continue;
      const k=`${x},${y},${d}`;if(seen.has(k)) continue;seen.add(k);
      q.push({s:[x,y,d],p:[...p,cmd]});
    }
  }
  expect(found,'چیدمانِ موانع راه‌حل ندارد').toBeTruthy();
  for(const cmd of found) await page.locator(`.bot-cmds button[data-c="${cmd}"]`).click();
  expect(await page.locator('.bot-prog .cmd').count()).toBe(found.length);
  await page.locator('#run').click();
  await page.waitForTimeout(found.length*320+900);
  await expect(page.locator('.bot-box .fb')).toContainText(/عالی|آفرین|کار کرد/);
  await next(page);
  await expectReward(page);
  expect(errors).toEqual([]);
});

/* ---------------- ۶) نورهای دماوند (توالی، دور به دور) ---------------- */
test('نورهای دماوند: الگو دیده و دور به دور تکرار می‌شود',async({page})=>{
  test.setTimeout(240000);
  const errors=await boot(page);
  await openStation(page,'game','sequence');
  // فقط نورهای فازِ «نگاه کن…» را ثبت می‌کنیم (کلیکِ خودِ ما هم نور می‌زند)
  await page.evaluate(()=>{
    window.__seq=[];
    const obs=new MutationObserver(ms=>{
      const st=document.querySelector('.simon-status')?.textContent||'';
      if(!st.includes('نگاه')) return;
      for(const m of ms){
        const e=m.target;
        if(e.dataset&&e.dataset.i!==undefined&&e.classList.contains('lit')) window.__seq.push(+e.dataset.i);
      }
    });
    obs.observe(document.querySelector('.pads'),{attributes:true,subtree:true,attributeFilter:['class']});
  });
  let replayed=0,rounds=0;
  for(let r=0;r<8;r++){
    if(await atReward(page)) break;
    await page.waitForFunction(n=>{
      const st=document.querySelector('.simon-status')?.textContent||'';
      return st.includes('نوبت')&&(window.__seq?.length||0)>=n;
    },replayed+1,{timeout:30000});
    const seq=await page.evaluate(()=>window.__seq.slice());
    expect(seq.length,`دورِ ${r+1}: الگویی دیده نشد`).toBeGreaterThan(replayed);
    for(const pad of seq.slice(replayed)){
      await page.locator(`.pad[data-i="${pad}"]`).click();
      await page.waitForTimeout(280);
    }
    replayed=seq.length;rounds++;
    await page.waitForTimeout(900);
    if(await atReward(page)) break;      // دورِ آخر تمام شد
    await expect(page.locator('.simon-status')).not.toContainText('اشکالی');
  }
  expect(rounds).toBe(buildMission(findIndex('game','sequence'),ST).stations[1].rounds);
  await solve(page,async()=>{if(await page.locator('.station-card .btn.primary').count())await next(page);},6,400);
  await expectReward(page);
  expect(errors).toEqual([]);
});

/* ---------------- ۷) چیدمان‌ها: ستاره، ترتیب، دسته، نقشه، خطِ زمان ---------------- */
test('ستاره، ترتیب، دسته‌بندی، نقشه و خطِ زمان با کلیکِ واقعی حل می‌شوند',async({page})=>{
  test.setTimeout(300000);
  const errors=await boot(page);

  // صورتِ فلکی — ستاره‌ها را به ترتیبِ DOM (= ترتیبِ صورتِ فلکی) وصل می‌کنیم
  await openStation(page,'game','stars');
  const dots=await page.locator('.star-dot').count();
  expect(dots).toBeGreaterThanOrEqual(4);
  for(let k=0;k<dots;k++){await page.locator('.star-dot').nth(k).click();await page.waitForTimeout(90);}
  expect(await page.locator('.star-lines line').count()).toBe(dots-1);
  await next(page);
  await expectReward(page);

  // ترتیبِ درست — چیپِ درست را با آزمون‌وخطایِ واقعی پیدا می‌کنیم
  await openStation(page,'game','order');
  await solve(page,async()=>{
    const n=await page.locator('.order-chip:not([disabled])').count();
    for(let k=0;k<n;k++){
      const before=await page.locator('.order-chip[disabled]').count();
      await page.locator('.order-chip:not([disabled])').nth(k).click();
      await page.waitForTimeout(110);
      if((await page.locator('.order-chip[disabled]').count())>before) break;
    }
    if(await page.locator('.station-card .btn.primary').count()) await next(page);
  },20);
  await expectReward(page);

  // دسته‌بندی — هر چیپ به دستهٔ خودش (از data-g)
  await openStation(page,'game','sort');
  await solve(page,async()=>{
    const chip=page.locator('.sort-chip:not(.used)').first();
    if(await chip.count()){
      const g=await chip.getAttribute('data-g');
      await chip.click();
      await page.locator(`.sort-cat:text-is("${g}")`).click();
      await page.waitForTimeout(130);
    }
    if(await page.locator('.station-card .btn.primary').count()) await next(page);
  },25);
  await expectReward(page);

  // نقشهٔ ایران — استانِ درست از رویِ سرنخ و جدولِ استان‌ها
  await openStation(page,'game','geo');
  await solve(page,async()=>{
    const clue=await page.locator('.geo-q .q-text').textContent();
    const p=PROVINCES.find(x=>clue.includes(x.clue))||PROVINCES.find(x=>clue&&clue.includes(x.n));
    expect(p,`استانی برای سرنخِ «${clue}» پیدا نشد`).toBeTruthy();
    const idx=PROVINCES.indexOf(p);
    await page.locator(`.prov[data-i="${idx}"]`).click();
    await page.waitForTimeout(150);
    if(await page.locator('.geo-q .btn.primary').count()) await page.locator('.geo-q .btn.primary').last().click();
  },20,250);
  await expectReward(page);

  // خطِ زمان
  await openStation(page,'game','timeline');
  await solve(page,async()=>{
    const n=await page.locator('.tl-chip:not([disabled])').count();
    for(let k=0;k<n;k++){
      const before=await page.locator('.tl-chip[disabled]').count();
      await page.locator('.tl-chip:not([disabled])').nth(k).click();
      await page.waitForTimeout(110);
      if((await page.locator('.tl-chip[disabled]').count())>before) break;
    }
    if(await page.locator('.station-card .btn.primary').count()) await next(page);
  },25);
  await expectReward(page);
  expect(errors).toEqual([]);
});

/* ---------------- ۸) واژه‌سازی، ترازو، تخمین ---------------- */
test('واژه‌سازی، ترازوی دانا و تخمین کامل می‌شوند',async({page})=>{
  test.setTimeout(300000);
  const errors=await boot(page);

  await openStation(page,'puzzle','word');
  await solve(page,async()=>{
    const slots=await page.locator('.slot').count();
    const filled=await page.locator('.slot.filled').count();
    if(filled<slots){
      const n=await page.locator('.letter-tile:not([disabled])').count();
      for(let k=0;k<n;k++){
        const before=await page.locator('.slot.filled').count();
        await page.locator('.letter-tile:not([disabled])').nth(k).click();
        await page.waitForTimeout(110);
        if((await page.locator('.slot.filled').count())>before) break;
      }
    }
    if(await page.locator('.station-card .btn.primary').count()) await next(page);
  },40);
  await expectReward(page);

  // ترازوی دانا: سه‌تا سه‌تا وزن کن، بعد حدس بزن
  await openStation(page,'game','balance');
  await solve(page,async()=>{
    if(!(await page.locator('.guess-stone').count())){
      const stones=page.locator('.stone:not(.in-left):not(.in-right)');
      const n=await stones.count();
      for(let k=0;k<Math.min(6,n);k++) await stones.nth(0).click();
      await page.locator('.weigh-actions .btn.primary').click();
      await page.waitForTimeout(300);
    }else{
      await page.locator('.guess-stone').first().click();
      await page.waitForTimeout(250);
    }
    if(await page.locator('.guess-row .btn.primary').count()) await page.locator('.guess-row .btn.primary').last().click();
  },16);
  await expectReward(page);

  // تخمین: ۳ ثانیه نگاه، بعد گزینه
  await openStation(page,'game','estimate');
  await solve(page,async()=>{
    if(await page.locator('.opt:not([disabled])').count()) await clickOpt(page);
    else if(await page.locator('.station-card .btn.primary').count()) await next(page);
    else await sleep(900);
  },20,1200);
  await expectReward(page);
  expect(errors).toEqual([]);
});

/* ---------------- ۹) رمزگشایی، بشقابِ سالم، بگیر و نگیر ---------------- */
test('رمزگشایی، بشقابِ سلامت و بگیر و نگیر کامل می‌شوند',async({page})=>{
  test.setTimeout(300000);
  const errors=await boot(page);

  // رمزگشایی: از چرخِ راهنما (نماد = حرف) متن را تایپ می‌کنیم
  await openStation(page,'puzzle','cipher');
  const wheel=await page.$$eval('.wheel-item',els=>els.map(e=>e.textContent));
  const code=await page.$$eval('.cipher-code .csym',els=>els.map(e=>e.textContent));
  const map={};
  for(const w of wheel){const[s,l]=w.split('=').map(x=>x.trim());map[s]=l;}
  const plain=code.map(s=>map[s]||s).join('');
  expect(plain.length).toBeGreaterThan(2);
  for(const ch of plain) await page.locator(`.pad-k:text-is("${ch}")`).click();
  await page.waitForTimeout(300);
  await expect(page.locator('.cipher-code')).toHaveClass(/solved/);
  await page.locator('.cipher-actions .btn.primary').click();
  await expectReward(page);

  // بشقابِ سالم: از هر گروه یکی
  await openStation(page,'game','food');
  await solve(page,async()=>{
    if(!(await page.locator('.food-box > .btn.primary').count())){
      const chips=page.locator('.food-chip:not(.used)');
      if(await chips.count()) await chips.first().click();
    }else await page.locator('.food-box > .btn.primary').click();
  },25);
  await expectReward(page);

  // بگیر و نگیر: فقط ستاره را بزن
  await openStation(page,'game','reflex');
  await solve(page,async()=>{
    const cells=await page.$$eval('.reflex-cell',els=>els.map(e=>e.textContent));
    const idx=cells.indexOf('⭐');
    if(idx>=0) await page.locator('.reflex-cell').nth(idx).click();
    else await sleep(150);
  },40,220);
  await expectReward(page);
  const score=await page.locator('.reflex-score b').textContent().catch(()=>null);
  expect(errors).toEqual([]);
});

/* ---------------- ۱۰) ایستگاه‌های ساختن: نقاشی، نوشتن، حرکت، آزمایش، کاردستی ---------------- */
test('ایستگاهِ ساختن: قلم روی بوم، گالری، حرکت، آزمایش و کاردستی',async({page})=>{
  test.setTimeout(300000);
  const errors=await boot(page);

  // نقاشی با کشیدنِ واقعیِ ماوس روی بوم
  await openStation(page,'create','draw');
  const box=await page.locator('.canvas-wrap canvas').boundingBox();
  await page.locator('.color-dot').nth(2).click();
  await page.locator('.size-dot').nth(1).click();
  await page.mouse.move(box.x+40,box.y+40);
  await page.mouse.down();
  await page.mouse.move(box.x+140,box.y+110,{steps:8});
  await page.mouse.move(box.x+220,box.y+60,{steps:8});
  await page.mouse.up();
  await page.locator('.paint-actions .btn.ghost').first().click();   // ذخیره در گالری
  await page.waitForTimeout(250);
  const galleryLen=await page.evaluate(()=>window.__baghTest.state.explorer.gallery.length);
  expect(galleryLen).toBeGreaterThanOrEqual(1);
  await page.locator('.paint-actions .btn.primary').click();          // تمام شد
  await expectReward(page);

  // نوشتن روی راهنما (trace) هم همان بوم است
  await openStation(page,'game','trace');
  await expect(page.locator('.guide-word')).toBeVisible();
  const b2=await page.locator('.canvas-wrap canvas').boundingBox();
  await page.mouse.move(b2.x+60,b2.y+80);
  await page.mouse.down();
  await page.mouse.move(b2.x+200,b2.y+80,{steps:10});
  await page.mouse.up();
  await page.locator('.paint-actions .btn.primary').click();
  await expectReward(page);

  // بدنِ قوی
  await openStation(page,'create','move');
  for(let k=0;k<10;k++){await page.locator('#plus').click();await page.waitForTimeout(40);}
  expect(await page.locator('.move-counter b').textContent()).toBe('10');
  await page.locator('.move-box > .btn.primary').click();
  await expectReward(page);

  // آزمایشگاه
  await openStation(page,'create','experiment');
  const steps=await page.locator('.lab-steps .step-check').count();
  expect(steps).toBeGreaterThanOrEqual(4);
  for(let k=0;k<steps;k++) await page.locator('.lab-steps .step-check').nth(k).click();
  await page.waitForTimeout(250);
  expect(await page.locator('.opt').count()).toBeGreaterThanOrEqual(2);
  await clickOpt(page);
  await page.waitForTimeout(250);
  await next(page);
  await expectReward(page);

  // کارگاه
  await openStation(page,'create','craft');
  const cs=await page.locator('.craft-steps .step-check').count();
  expect(cs).toBeGreaterThanOrEqual(3);
  for(let k=0;k<cs;k++) await page.locator('.craft-steps .step-check').nth(k).click();
  await page.waitForTimeout(250);
  await page.locator('.craft-box > .btn.primary').click();
  await expectReward(page);
  expect(errors).toEqual([]);
});

/* ---------------- ۱۱) ایستگاهِ گفتن (نوشتن به جای ضبط) ---------------- */
test('ایستگاهِ گفتن: کودک می‌تواند به جای ضبط بنویسد و کامل کند',async({page})=>{
  test.setTimeout(180000);
  const errors=await boot(page);
  await openStation(page,'create','speak');
  await page.locator('#text').click();
  await page.locator('.speak-text').fill('امروز با برادرم مهربان بودم و با هم بازی کردیم.');
  await page.locator('.speak-log .btn.primary').click();
  await expectReward(page);
  const wrote=await page.evaluate(()=>{
    const log=(window.__baghTest.state.explorer.current||{}).log||[];
    const last=log[log.length-1];
    return !!(last&&last.extra&&last.extra.wrote===true);
  });
  expect(wrote,'نوشتنِ کودک در گزارشِ ایستگاه ثبت نشده').toBe(true);
  expect(errors).toEqual([]);
});

/* ---------------- ۱۲) ریتمِ جنگل ---------------- */
test('ریتمِ جنگل: کودک به تعدادِ ضرب‌ها طبل می‌زند',async({page})=>{
  test.setTimeout(180000);
  const errors=await boot(page);
  const i=findIndex('game','rhythm');
  const st=buildMission(i,ST).stations[1];
  const base=st.params.len,rounds=st.rounds;
  await openStation(page,'game','rhythm');
  for(let r=0;r<rounds;r++){
    await page.waitForFunction(()=>/حالا تکرار کن/.test(document.querySelector('.rhythm-status')?.textContent||''),null,{timeout:30000});
    for(let k=0;k<base+r;k++){await page.locator('#drum').click();await page.waitForTimeout(90);}
    await page.waitForTimeout(500);
  }
  expect(rounds).toBe(st.rounds);
  await solve(page,async()=>{if(await page.locator('.station-card .btn.primary').count())await next(page);},6,500);
  await expectReward(page);
  expect(errors).toEqual([]);
});

/* ---------------- ۱۳) نفسِ آرام در کارتِ استراحت ---------------- */
test('استراحت: تمرینِ نفسِ آرام واقعاً اجرا می‌شود و در تله‌متری می‌نشیند',async({page})=>{
  test.setTimeout(180000);
  const errors=await boot(page);
  await page.evaluate(()=>window.__baghTest.breakNow());
  await expect(page.locator('.break-overlay .breath-box')).toBeVisible();
  await page.waitForFunction(()=>{
    const b=document.querySelector('.break-calm .breath-count');
    return b&&b.textContent.trim().startsWith('۵');
  },null,{timeout:120000});
  const after=await page.evaluate(()=>window.__baghTest.state.explorer.telemetry.slice(-1)[0]);
  expect(after.mech).toBe('breathe');
  expect(after.standalone).toBe(true);
  await page.locator('.break-overlay .btn.primary').click();
  await expect(page.locator('.break-overlay')).toHaveCount(0);
  expect(errors).toEqual([]);
});

import {test,expect} from '@playwright/test';
import {stages,questionFor,stories,shuffle} from '../../src/content.js';
const stored=page=>page.evaluate(()=>JSON.parse(localStorage.getItem('little-star-v1')));
async function solve(page,type,s){
 if(['math','pattern','quiz','emotion'].includes(type)){const q=questionFor(type,s);await page.locator(`[data-answer="${q.answer}"]`).click();}
 if(type==='visual'){const count=12+(s.level-1)*4;await page.locator(`[data-visual="${s.id*7%count}"]`).click();}
 if(type==='audio'){await page.locator('#visual-audio').click();await page.locator(`[data-tone-answer="${2+s.id%3}"]`).click();}
 if(type==='story'){await page.locator('#story-next').click();await page.locator(`[data-answer="${stories[(s.id-1+s.world)%stories.length][6]}"]`).click();}
 if(type==='memory'){
  const pairs=s.level<3?3:4,items=shuffle(['🍓','🚀','🦋','🌻','🐳','🎨','🍀','⭐'],s.id).slice(0,pairs),deck=shuffle([...items,...items],s.id+9);
  for(let item of items)for(let i=0;i<deck.length;i++)if(deck[i]===item)await page.locator(`[data-card="${i}"]`).click();
 }
 if(type==='patience'){await page.clock.runFor(8000);await page.locator('#plant').click();}
 if(type==='breath'){await page.locator('#breath-start').click();await page.clock.runFor(22000);}
 if(type==='sort'){for(let n of [s.id%8+1,s.id%8+4,s.id%8+7,s.id%8+10])await page.locator(`[data-number="${n}"]`).click();}
 if(type==='creative'){await page.locator('#stamp').click();await page.locator('#save-drawing').click();}
 await expect(page.locator('#continue-activity')).toBeVisible();await page.locator('#continue-activity').click();
}
test('dashboard, navigation, locks, mobile layout and all local images',async({page})=>{
 const errors=[];page.on('pageerror',e=>errors.push(e.message));await page.goto('/legacy.html');await expect(page.locator('.hero')).toBeVisible();
 await page.waitForFunction(()=>[...document.images].every(i=>i.complete&&i.naturalWidth>0));await page.screenshot({path:'.tools/desktop.png',fullPage:true});
 await page.locator('[data-world="0"]').click();await expect(page.locator('.stage-card')).toHaveCount(20);await page.locator('[data-start="2"]').first().click();await expect(page.locator('#toast')).toContainText('اول مرحله');await expect(page.locator('.modal')).toHaveCount(0);
 await page.locator('[data-level="3"]').click();await expect(page.locator('.stage-card')).toHaveCount(5);
 await page.locator('[data-view="games"]').first().click();await expect(page.locator('.game-card')).toHaveCount(12);
 await page.locator('[data-view="home"]').first().click();await page.setViewportSize({width:390,height:844});await page.screenshot({path:'.tools/mobile.png',fullPage:true});expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
 await page.locator('#profile').click();await page.locator('#name-input').fill('آوا');await page.locator('#name-form button').click();await expect(page.locator('h1')).toContainText('آوا');await page.reload();await expect(page.locator('h1')).toContainText('آوا');expect(errors).toEqual([]);
});
test('complete first stage, reward once, resume checkpoint and unlock next',async({page})=>{
 await page.clock.install();await page.goto('/legacy.html');await page.locator('[data-start="1"]').first().click();await solve(page,stages[0].activities[0],stages[0]);await page.locator('#close-modal').click();expect((await stored(page)).progress['1']).toBe(1);await page.reload();await page.locator('[data-start="1"]').first().click();
 for(let type of stages[0].activities.slice(1))await solve(page,type,stages[0]);expect((await stored(page)).done).toEqual([1]);await page.locator('#end-stage').click();await page.locator('[data-view="worlds"]').first().click();await expect(page.locator('.stage-card[data-start="2"]')).toHaveClass(/available/);await page.locator('[data-start="1"]').click();for(let type of stages[0].activities)await solve(page,type,stages[0]);expect((await stored(page)).done).toEqual([1]);await expect(page.locator('.earned-reward')).toContainText('بدون امتیاز تکراری');
});
test('wrong answers are retryable, practice has no points and drawings persist',async({page})=>{
 await page.goto('/legacy.html');await page.locator('[data-view="games"]').first().click();await page.locator('[data-practice="math"]').click();const q=questionFor('math',stages[0]);await page.locator(`[data-answer="${(q.answer+1)%4}"]`).click();await expect(page.locator('#feedback')).toHaveClass(/retry/);await solve(page,'math',stages[0]);await page.locator('#back-games').click();
 await page.locator('[data-practice="creative"]').click();await solve(page,'creative',stages[0]);await page.locator('#back-games').click();expect((await stored(page)).done).toEqual([]);expect((await stored(page)).drawings).toHaveLength(1);await page.locator('[data-view="treasures"]').click();await expect(page.locator('.drawing-grid img')).toHaveCount(1);
});
test('parent gate, settings, report export, reset confirmation and rest',async({page})=>{
 await page.clock.install();await page.goto('/legacy.html');await page.locator('#parent').click();let nums=(await page.locator('.gate-question').textContent()).replace(/[۰-۹]/g,c=>'۰۱۲۳۴۵۶۷۸۹'.indexOf(c)).match(/\d+/g).map(Number);await page.locator('#gate-answer').fill('0');await page.locator('#gate-form button').click();await expect(page.locator('#gate-error')).not.toBeEmpty();await page.locator('#gate-answer').fill(String(nums[0]+nums[1]));await page.locator('#gate-form button').click();await page.locator('#time-limit').selectOption('15');expect((await stored(page)).minutes).toBe(15);
 const download=page.waitForEvent('download');await page.locator('#export').click();expect((await download).suggestedFilename()).toBe('little-star-progress.txt');await page.locator('#reset').click();await page.locator('#cancel-reset').click();await expect(page.locator('#time-limit')).toBeVisible();await page.locator('#close-modal').click();await page.locator('#rest').click();await page.locator('#rest-start').click();await page.locator('#close-modal').click();await page.locator('[data-start="1"]').first().click();await expect(page.locator('#rest-start')).toBeDisabled();await page.clock.runFor(61000);await page.locator('#close-modal').click();await page.locator('[data-start="1"]').first().click();await expect(page.locator('#activity-body')).toBeVisible();
});
// Execute all 800 activities, world by world. Seed only prerequisites, never activity success.
for(let world=0;world<10;world++)test(`all 20 stages in world ${world+1} are completable`,async({page})=>{
 test.setTimeout(180000);await page.clock.install();await page.goto('/legacy.html');await page.evaluate(({world})=>localStorage.setItem('little-star-v1',JSON.stringify({done:Array.from({length:world*20},(_,i)=>i+1),progress:{},minutes:30,elapsed:0,drawings:[]})),{world});await page.reload();let errors=[];page.on('pageerror',e=>errors.push(e.message));await page.locator(`[data-start="${world*20+1}"]`).first().click();
 for(let s of stages.filter(s=>s.world===world)){for(let type of s.activities)await solve(page,type,s);expect((await stored(page)).done.includes(s.id)).toBe(true);if(s.local<20)await page.locator('#next-stage').click();}
 expect((await stored(page)).done.length).toBe((world+1)*20);expect(errors).toEqual([]);
});

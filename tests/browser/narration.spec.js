import {test,expect} from '@playwright/test';

test.beforeEach(async({page})=>{
 await page.addInitScript(()=>{const Original=window.Audio;window.__narrationAudios=[];window.Audio=function(...args){const audio=new Original(...args);window.__narrationAudios.push(audio);return audio;};window.Audio.prototype=Original.prototype;});
});

test('both narrators actually play, selection persists independently, switching stops prior audio',async({page})=>{
 const errors=[];page.on('pageerror',e=>errors.push(e.message));await page.goto('/legacy.html');await page.locator('#voice-settings').click();await expect(page.locator('input[name=narrator]')).toHaveCount(2);
 await page.locator('[data-voice-preview=first]').click();await page.waitForFunction(()=>window.__narrationAudios[0]?.currentTime>0.05);await expect(page.locator('#narration-status')).toContainText('صدای اول');
 await page.locator('#narrator-second').check();expect(await page.evaluate(()=>window.__narrationAudios[0].paused)).toBe(true);
 await page.locator('[data-voice-preview=second]').click();await page.waitForFunction(()=>window.__narrationAudios[1]?.currentTime>0.05);expect(await page.evaluate(()=>window.__narrationAudios[1].src)).toContain('/second/explorer-welcome.mp3');
 await page.locator('[data-voice-role=toddler]').click();expect(await page.evaluate(()=>window.__narrationAudios[1].paused)).toBe(true);await expect(page.locator('#narrator-first')).toBeChecked();await page.locator('#narrator-second').check();
 await page.locator('[data-voice-role=mother]').click();await expect(page.locator('#narrator-first')).toBeChecked();await page.locator('[data-voice-preview=first]').click();await page.waitForFunction(()=>window.__narrationAudios[2]?.currentTime>0.05);
 await page.locator('#close-modal').click();expect(await page.evaluate(()=>window.__narrationAudios[2].paused)).toBe(true);await page.reload();await page.locator('#voice-settings').click();await expect(page.locator('#narrator-second')).toBeChecked();
 await page.locator('[data-voice-role=toddler]').click();await expect(page.locator('#narrator-second')).toBeChecked();await page.locator('[data-voice-role=mother]').click();await expect(page.locator('#narrator-first')).toBeChecked();await page.locator('#finish-voice-settings').click();await page.locator('#play-welcome').click();await page.waitForFunction(()=>window.__narrationAudios[0]?.currentTime>0.05);expect(await page.evaluate(()=>window.__narrationAudios[0].src)).toContain('/second/explorer-welcome.mp3');await page.locator('#play-welcome').click();expect(await page.evaluate(()=>window.__narrationAudios[0].paused)).toBe(true);expect(errors).toEqual([]);
});

test('mobile selector works, mute is respected and stop button stops playback',async({page})=>{
 await page.setViewportSize({width:390,height:844});await page.goto('/legacy.html');await page.locator('#profile').click();await page.locator('#profile-voices').click();await page.locator('[data-voice-role=father]').click();await page.locator('[data-voice-preview=second]').click();await page.waitForFunction(()=>window.__narrationAudios[0]?.currentTime>0.05);
 await page.locator('#stop-narration').click();expect(await page.evaluate(()=>window.__narrationAudios[0].paused)).toBe(true);await expect(page.locator('#stop-narration')).toBeDisabled();
 await page.locator('#narration-enabled').uncheck();await page.locator('[data-voice-preview=first]').click();expect(await page.evaluate(()=>window.__narrationAudios.length)).toBe(1);await expect(page.locator('#toast')).toContainText('خاموش');
 expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);await page.screenshot({path:'.tools/voice-picker-mobile.png',fullPage:true});
 await page.locator('#finish-voice-settings').click();await page.reload();await page.locator('#voice-settings').click();await expect(page.locator('#narration-enabled')).not.toBeChecked();
 await page.locator('#narration-enabled').check();await page.locator('[data-voice-preview=second]').click();await page.waitForFunction(()=>window.__narrationAudios[0]?.currentTime>0.05);
});

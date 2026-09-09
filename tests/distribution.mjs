import {createServer} from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert/strict';
import {chromium} from '@playwright/test';
const mime={'.html':'text/html','.js':'application/javascript','.css':'text/css','.svg':'image/svg+xml','.png':'image/png','.woff2':'font/woff2','.webmanifest':'application/manifest+json'};
const root=path.resolve('dist');
const server=createServer((req,res)=>{const pathname=decodeURIComponent(new URL(req.url,'http://localhost').pathname);const p=path.resolve(root,'.'+(pathname==='/'?'/index.html':pathname));if(!p.startsWith(root+'/')||!fs.existsSync(p)||!fs.statSync(p).isFile()){res.writeHead(404).end();return;}res.setHeader('Content-Type',mime[path.extname(p)]||'text/plain');res.end(fs.readFileSync(p));});
await new Promise(resolve=>server.listen(4179,'127.0.0.1',resolve));
let browser;
try{
 browser=await chromium.launch({executablePath:'/tmp/chromium',args:['--no-sandbox'],env:{...process.env,LD_LIBRARY_PATH:path.resolve('.tools/chromium-libs/lib')}});
 const context=await browser.newContext({viewport:{width:390,height:844},reducedMotion:'reduce'}),page=await context.newPage();let errors=[];page.on('pageerror',e=>errors.push(e.message));
 await page.goto('http://127.0.0.1:4179');
 await page.evaluate(()=>navigator.serviceWorker.ready);await page.waitForFunction(()=>navigator.serviceWorker.controller!==null);
 await page.waitForFunction(()=>[...document.images].every(i=>i.complete&&i.naturalWidth>0));
 await page.locator('#profile').click();await page.locator('#profile-parent').click();assert.equal(await page.locator('#gate-form').count(),1);await page.locator('#close-modal').click();
 await context.setOffline(true);await page.reload();await page.locator('[data-start="1"]').first().click();assert.equal(await page.locator('[data-visual]').count(),12);await page.locator('[data-visual="7"]').click();assert.equal(await page.locator('#continue-activity').count(),1);await page.locator('#close-modal').click();
 await page.waitForFunction(()=>[...document.images].every(i=>i.complete&&i.naturalWidth>0));assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);await page.screenshot({path:'.tools/offline-mobile.png',fullPage:true});assert.deepEqual(errors,[]);console.log('PASS: production service worker caches all assets; offline reload and game interaction; mobile parent access; no horizontal page overflow.');
 // Use the exact packed web files on the native app's origin, without any network server.
 const native=await browser.newContext({userAgent:'Mozilla/5.0 Chrome/120.0.0.0 LittleStarAndroid/1.0'});let network=[];
 await native.route('**/*',async route=>{let u=new URL(route.request().url());network.push(u.origin);let p=path.join('android/build/assets/www',u.pathname==='/'?'index.html':u.pathname);if(u.origin!=='https://app.littlestar.local'||!fs.existsSync(p)){await route.abort();return;}await route.fulfill({status:200,body:fs.readFileSync(p),contentType:mime[path.extname(p)]||'text/plain'});});
 const np=await native.newPage();await np.goto('https://app.littlestar.local/');await np.waitForFunction(()=>[...document.images].every(i=>i.complete&&i.naturalWidth>0));await np.locator('[data-start="1"]').first().click();await np.locator('[data-visual="7"]').click();assert.equal(await np.locator('#continue-activity').count(),1);assert.ok(network.every(origin=>origin==='https://app.littlestar.local'));assert.equal(await np.evaluate(async()=> (await navigator.serviceWorker.getRegistrations()).length),0);console.log('PASS: packed Android web assets run on the native local HTTPS origin without external requests. This is not a device/emulator test.');
}finally{await browser?.close();await new Promise(resolve=>server.close(resolve));}

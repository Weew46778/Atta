import {test} from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import {NARRATION_ROLES,NARRATORS,normalizeVoicePreferences,narrationAsset,createNarrationPlayer} from '../src/narration.js';

test('voice defaults, migration and malformed preferences are normalized per role',()=>{
 for(const raw of [null,undefined,{},[],42,'second'])assert.deepEqual(normalizeVoicePreferences(raw),{explorer:'first',toddler:'first',mother:'first',father:'first'});
 assert.deepEqual(normalizeVoicePreferences({explorer:'second',toddler:'unknown',mother:'first',father:'second'}),{explorer:'second',toddler:'first',mother:'first',father:'second'});
});
test('all eight approved role/narrator combinations have bundled MP3 files',()=>{
 const paths=[];for(const r of NARRATION_ROLES)for(const v of NARRATORS){const path=narrationAsset(r.id,v.id);paths.push(path);assert.ok(fs.statSync('public/'+path.slice(2)).size>2000);}
 assert.equal(new Set(paths).size,8);assert.throws(()=>narrationAsset('../escape','first'));assert.throws(()=>narrationAsset('mother','voice-03'));
});
function audioMock(play=()=>Promise.resolve()) {return {src:'',preload:'',paused:false,onended:null,onerror:null,play,pause(){this.paused=true;},removeAttribute(name){if(name==='src')this.src='';},load(){}};}
test('changing narrator stops and unloads previous audio; explicit stop releases the new audio',async()=>{
 const audios=[];const states=[];const player=createNarrationPlayer({createAudio:()=>{const a=audioMock();audios.push(a);return a;},onState:s=>states.push(s)});
 await player.play('explorer','first');assert.match(audios[0].src,/voices\/explorer/);assert.equal(states.at(-1),'playing');
 await player.play('explorer','second');assert.equal(audios[0].paused,true);assert.equal(audios[0].src,'');assert.match(audios[1].src,/voices\/second\/explorer/);
 player.stop();assert.equal(audios[1].paused,true);assert.equal(audios[1].src,'');assert.equal(states.at(-1),'idle');
});
test('audio errors are visible and do not silently select a different narrator',async()=>{
 let errors=0,created=0;const p=createNarrationPlayer({createAudio:()=>{created++;return audioMock(()=>Promise.reject(new Error('blocked')));},onError:()=>errors++});
 await p.play('toddler','second');assert.equal(errors,1);assert.equal(created,1);
});
test('stale play promise cannot resume playback state after stop',async()=>{
 let resolve;const states=[];const p=createNarrationPlayer({createAudio:()=>audioMock(()=>new Promise(r=>resolve=r)),onState:s=>states.push(s)});const playing=p.play('father','first');p.stop();resolve();await playing;assert.equal(states.at(-1),'idle');
});
test('natural end stops the player',async()=>{
 const audio=audioMock();const states=[];const p=createNarrationPlayer({createAudio:()=>audio,onState:s=>states.push(s)});await p.play('mother','second');audio.onended();assert.equal(audio.paused,true);assert.equal(states.at(-1),'idle');
});

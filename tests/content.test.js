import {test} from 'node:test';
import assert from 'node:assert/strict';
import {worlds,stages,mechanics,labels,quizzes,stories,questionFor,shuffle} from '../src/content.js';
test('exactly 200 sequential stages, 10 worlds and four levels per world',()=>{
 assert.equal(stages.length,200);assert.equal(worlds.length,10);
 assert.deepEqual(stages.map(s=>s.id),Array.from({length:200},(_,i)=>i+1));
 worlds.forEach((_,w)=>{let group=stages.filter(s=>s.world===w);assert.equal(group.length,20);for(let level=1;level<=4;level++)assert.equal(group.filter(s=>s.level===level).length,5);});
});
test('800 activities, four distinct valid mechanics in every stage',()=>{
 assert.equal(stages.reduce((n,s)=>n+s.activities.length,0),800);
 stages.forEach(s=>{assert.equal(new Set(s.activities).size,4);assert.ok(s.activities.every(t=>mechanics.includes(t)&&labels[t]));assert.ok(s.reward>=40&&s.reward<=70);});
 assert.equal(new Set(stages.flatMap(s=>s.activities)).size,12);
});
test('all 200 stages have distinct collectibles',()=>assert.equal(new Set(stages.map(s=>s.collectible)).size,200));
test('every generated quiz has one in-range correct option and explanation',()=>{
 for(let s of stages)for(let type of ['math','pattern','quiz','emotion']){
  let q=questionFor(type,s);assert.ok(q.question);assert.ok(q.explain);assert.ok(q.answer>=0&&q.answer<q.options.length);assert.equal(new Set(q.options).size,q.options.length);
 }
});
test('arithmetic and sequence answers verified independently for all levels',()=>{
 for(let s of stages){let q=questionFor('math',s),[a,b]=q.question.match(/\d+/g).map(Number);assert.equal(Number(q.options[q.answer]),q.question.includes('×')?a*b:a+b);let p=questionFor('pattern',s),nums=p.question.match(/\d+/g).map(Number);assert.equal(Number(p.options[p.answer]),nums[3]+nums[1]-nums[0]);}
});
test('curated question and story banks have explanations and valid choices',()=>{
 assert.equal(quizzes.length,20);assert.equal(stories.length,10);
 for(let q of quizzes){assert.ok(q[2]>=0&&q[2]<q[1].length);assert.ok(q[3].length>20);}
 for(let s of stories){assert.equal(s[5].length,3);assert.ok(s[6]>=0&&s[6]<3);assert.ok(s[7].length>20);}
});
test('shuffle preserves input, is deterministic, and does not mutate',()=>{let list=[1,2,3,4];assert.deepEqual(shuffle(list,17),shuffle(list,17));assert.deepEqual(shuffle(list,17).sort(),list);assert.deepEqual(list,[1,2,3,4]);});

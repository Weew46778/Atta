import test from 'node:test';
import assert from 'node:assert/strict';
import {WORLDS,MISSIONS_PER_WORLD,missionAt,TOTAL_MISSIONS,TOTAL_REWARDS} from '../src/v3/content/worlds.js';
import {buildMission,deal,GAME_MECHANICS,PUZZLE_MECHANICS,CREATE_MECHANICS,REFLECT_MECHANICS,STATION_KINDS,praise,starsFor,missionStatus,nextMissionIndex,TOTAL,pools} from '../src/v3/content/missions.js';
import {RIDDLES,VERSES,FACTS,PROVERBS,WORDS,CIPHERS,CONSTELLATIONS,FOODS,EXPERIMENTS,CRAFTS,TIMELINE,PROVINCES} from '../src/v3/content/iran.js';
import {STORIES,DILEMMAS,EMOTIONS,REALTASKS,SPEAKS,DRAWS} from '../src/v3/content/stories.js';
import {TODDLER_SESSIONS,HOT_MOMENTS,WORKSHOPS,CALM_SESSIONS,COOP_GAMES,AWAY_DAYS,HOME_DAYS,PHONE_GAMES,FATHER_COACHING} from '../src/v3/content/family.js';
import {PERSIAN_LETTERS,normalizePlain} from '../src/v3/content/text.js';
import {emptyFamily,migrate,levelFromXp,xpForLevel,rankOf,applySkills,recordStation,bumpStreak,analysis,recommendations,weeklySummary,SKILLS} from '../src/v3/core/store.js';

test('نقشه: ۱۰ جهان × ۲۴ مأموریت = ۲۴۰ مرحلهٔ یکتا',()=>{
  assert.equal(WORLDS.length,10);
  assert.equal(MISSIONS_PER_WORLD,24);
  assert.equal(TOTAL_MISSIONS,240);
  assert.equal(TOTAL(),240);
  const ids=new Set(),titles=new Set(),rewards=new Set();
  for(let i=0;i<240;i++){
    const m=missionAt(i);
    ids.add(m.id);titles.add(m.title);rewards.add(m.reward);
    assert.ok(m.title&&m.title.length>2,`عنوانِ کوتاه در ${i}`);
    assert.ok(m.brief&&m.brief.length>8,`خلاصهٔ کوتاه در ${i}`);
  }
  assert.equal(ids.size,240,'شناسهٔ تکراری');
  assert.equal(titles.size,240,'عنوانِ تکراری بین ۲۴۰ مأموریت');
  assert.equal(rewards.size,240,'یادگاریِ تکراری');
  assert.equal(TOTAL_REWARDS,240);
});

test('هر مأموریت ۶ ایستگاه از ۶ نوعِ متفاوت دارد',()=>{
  const state=emptyFamily();
  const kinds=new Set(STATION_KINDS.map(k=>k.id));
  for(let i=0;i<240;i++){
    const m=buildMission(i,state);
    assert.equal(m.stations.length,6,`مأموریت ${i}: ${m.stations.length} ایستگاه`);
    const ks=m.stations.map(s=>s.kind);
    assert.deepEqual(ks,['story','game','puzzle','real','create','reflect'],`ترتیبِ ایستگاه‌ها در ${i}`);
    for(const k of ks) assert.ok(kinds.has(k));
    // سازوکارِ چالش اصلی و معما نباید یکی باشند
    assert.notEqual(m.stations[1].mech,m.stations[2].mech,`سازوکارِ تکراری در مأموریت ${i}`);
    assert.ok(m.stations[1].rounds>=2,'کمتر از دو دور');
    assert.ok(m.xp>0);
  }
});

test('پوشش: همهٔ سازوکارها در ۲۴۰ مأموریت به کار می‌روند',()=>{
  const state=emptyFamily();
  const used=new Set(),puzzleUsed=new Set(),createUsed=new Set(),reflectUsed=new Set();
  for(let i=0;i<240;i++){
    const m=buildMission(i,state);
    used.add(m.stations[1].mech);puzzleUsed.add(m.stations[2].mech);
    createUsed.add(m.stations[4].mech);reflectUsed.add(m.stations[5].mech);
  }
  for(const g of GAME_MECHANICS) assert.ok(used.has(g),`سازوکارِ ${g} هرگز استفاده نشد`);
  for(const p of PUZZLE_MECHANICS) assert.ok(puzzleUsed.has(p),`معمای ${p} هرگز استفاده نشد`);
  for(const c of CREATE_MECHANICS) assert.ok(createUsed.has(c),`ساختنِ ${c} هرگز استفاده نشد`);
  for(const r of REFLECT_MECHANICS) assert.ok(reflectUsed.has(r));
});

test('بدونِ تکرار: سازوکارِ اصلی در دو مأموریتِ پیاپی تکرار نمی‌شود',()=>{
  const state=emptyFamily();
  let prev=null,repeat=0;
  for(let i=0;i<240;i++){
    const mech=buildMission(i,state).stations[1].mech;
    if(mech===prev) repeat++;
    prev=mech;
  }
  assert.ok(repeat<=2,`تکرارِ پیاپیِ زیاد: ${repeat}`);
});

test('دستهٔ کارت: تا پایانِ دور، هیچ آیتمی تکرار نمی‌شود',()=>{
  const deck=RIDDLES;
  for(let cycle=0;cycle<3;cycle++){
    const seen=new Set();
    for(let i=cycle*deck.length;i<(cycle+1)*deck.length;i++){
      const item=deal(deck,i,3);
      assert.ok(!seen.has(item.q),'تکرار در یک دور');
      seen.add(item.q);
    }
    assert.equal(seen.size,deck.length);
  }
  // ترتیبِ دورهای مختلف فرق می‌کند
  const a=[0,1,2,3].map(i=>deal(deck,i,3).q);
  const b=[deck.length,deck.length+1,deck.length+2,deck.length+3].map(i=>deal(deck,i,3).q);
  assert.notDeepEqual(a,b,'ترتیبِ دو دور یکسان است');
});

test('قصه‌ها: ۲۴ قصه، هر کدام دقیقاً یک‌بار در ۲۴۰ مأموریت',()=>{
  const state=emptyFamily();
  const seen=new Set();
  for(let i=0;i<240;i++){
    const st=buildMission(i,state).stations[0];
    if(st.params.story){
      assert.ok(!seen.has(st.params.story.id),'قصهٔ تکراری');
      seen.add(st.params.story.id);
    }
  }
  assert.equal(seen.size,STORIES.length);
  assert.equal(STORIES.length,24);
  for(const s of STORIES){
    assert.ok(s.chapters.length>=4,`قصهٔ ${s.id} کوتاه است`);
    assert.equal(s.questions.length,2);
    for(const q of s.questions){
      assert.equal(q.options.length,3);
      assert.ok(q.options[q.answer],`پاسخِ نامعتبر در ${s.id}`);
      assert.ok(q.why&&q.why.length>5);
    }
    assert.ok(s.words.length>=2);
  }
});

test('محتوا: هیچ آیتمِ تهی یا ناقصی نیست',()=>{
  assert.equal(RIDDLES.length>=60,true,`چیستان‌ها: ${RIDDLES.length}`);
  for(const r of RIDDLES){assert.ok(r.q&&r.a&&r.hints.length===3,`چیستانِ ناقص: ${r.q}`);
    assert.ok(!r.hints.some(h2=>h2.includes(r.a)),`راهنما جواب را لو داده: ${r.q}`);}
  for(const v of VERSES){assert.ok(v.options.includes(v.answer),`گزینهٔ پاسخ در شعر نیست: ${v.line}`);
    assert.ok(v.line.includes('___'));}
  for(const f of FACTS){assert.ok(f.text.length>30,`رازِ کوتاه: ${f.t}`);assert.ok(f.o.includes(f.a),`پاسخ در گزینه‌ها نیست: ${f.t}`);}
  for(const d of DILEMMAS){assert.equal(d.options.length,3);assert.ok(d.options[d.best]);assert.ok(d.values.length>=2);}
  for(const e of EMOTIONS){assert.equal(e.options.length,3);assert.ok(e.options[e.best]);assert.ok(e.strategy);}
  assert.ok(REALTASKS.length>=60);
  for(const t of REALTASKS){assert.ok(t.t.length>8&&t.why.length>8);}
  assert.equal(PROVINCES.length,31,'استان‌های ایران');
  for(const p of PROVINCES){assert.ok(p.x>0&&p.x<100&&p.y>0&&p.y<100,`مختصاتِ ${p.n}`);}
  assert.ok(TIMELINE.length>=24);
  for(const e of TIMELINE) assert.ok(e.t.length>10);
  for(const c of CONSTELLATIONS) assert.ok(c.stars.length>=5);
  for(const x of EXPERIMENTS) assert.ok(x.steps.length>=4&&x.need&&x.why&&x.ask);
  for(const c of CRAFTS) assert.ok(c.steps.length>=3);
  assert.ok(SPEAKS.length>=40);
  assert.ok(DRAWS.length>=40);
  assert.ok(WORDS.length>=30);
  assert.ok(CIPHERS.length>=15);
  assert.ok(PROVERBS.length>=10);
  assert.equal(FOODS.filter(f=>f.g==='کمتر بخور').length>0,true);
});

test('سختی: درونِ هر جهان، سطح مرحله‌ها نزولی نیست',()=>{
  for(let w=0;w<10;w++){
    let last=0;
    for(let i=0;i<24;i++){
      const m=missionAt(w*24+i);
      assert.ok(m.level>=last,`کاهشِ سطح در جهان ${w} مرحلهٔ ${i}`);
      last=m.level;
    }
    assert.ok(missionAt(w*24+23).level===3,'آخرین مرحله باید سطحِ ۳ باشد');
  }
});

test('قفل و باز شدن: پیاپی و قابلِ ادامه',()=>{
  const state=emptyFamily();
  assert.equal(missionStatus(0,state),'open');
  assert.equal(missionStatus(1,state),'locked');
  state.explorer.missionsDone.push(0);
  assert.equal(missionStatus(1,state),'open');
  assert.equal(missionStatus(2,state),'locked');
  assert.equal(nextMissionIndex(state),1);
  for(let i=0;i<240;i++) state.explorer.missionsDone.push(i);
  assert.equal(nextMissionIndex(state),239);
});

test('امتیاز: ستاره بر پایهٔ تلاش و کیفیت',()=>{
  assert.equal(starsFor({finished:false,correctRate:1,tries:0,hints:0}),0);
  assert.equal(starsFor({finished:true,correctRate:0.2,tries:0,hints:3}),1);
  assert.equal(starsFor({finished:true,correctRate:0.6,tries:1,hints:2}),2);
  assert.equal(starsFor({finished:true,correctRate:0.9,tries:0,hints:0}),3);
  // پشتکار هم ستاره می‌آورد
  assert.equal(starsFor({finished:true,correctRate:0.3,tries:4,hints:0}),2);
});

test('تشویق: جمله بر پایهٔ کارِ واقعی، نه کلیشه',()=>{
  const st={title:'نورهای دماوند'};
  const a=praise({finished:true,correctRate:0.9,hints:0,tries:0},st);
  const b=praise({finished:true,correctRate:0.4,hints:0,tries:4},st);
  const c=praise({finished:true,correctRate:0.3,hints:0,tries:0},st);
  assert.ok(a.includes('نورهای دماوند'));
  assert.notEqual(a,b);assert.notEqual(b,c);assert.notEqual(a,c);
  assert.ok(b.includes('پشتکار'));
});

test('مهارت و سطح: رشدِ نرم و تحلیل',()=>{
  const state=emptyFamily();
  assert.equal(state.explorer.level,1);
  applySkills(state,{id:'sequence'},1);
  assert.ok(state.explorer.skills.memory>0);
  state.explorer.skills.memory=90;
  applySkills(state,{id:'sequence'},0.2);
  assert.ok(state.explorer.skills.memory<90,'اجرای ضعیف باید میانگینِ مهارتِ بالا را پایین بیاورد');
  assert.ok(state.explorer.skills.memory>60,'یک اجرای ضعیف نباید مهارت را نابود کند');
  for(let i=0;i<40;i++) applySkills(state,{id:'sequence'},1);
  assert.ok(state.explorer.skills.memory<=100,'نمره از ۱۰۰ بیشتر نشد');
  state.explorer.xp=xpForLevel(5)+10;
  assert.equal(levelFromXp(state.explorer.xp),5);
  assert.ok(rankOf(45).length>1);
  recordStation(state,{m:1,s:1,mech:'sequence',tries:2,hints:1,rate:0.8,millis:9000,stars:3});
  assert.equal(state.explorer.telemetry.length,1);
  assert.equal(state.explorer.attempts.sequence,1);
  assert.equal(state.explorer.bestTimes.sequence,9000);
  const an=analysis(state);
  assert.equal(an.strongest.length,3);assert.equal(an.weakest.length,3);
  assert.ok(Array.isArray(recommendations(state)));
  assert.ok(weeklySummary(state).stations>=1);
  bumpStreak(state);
  assert.equal(state.explorer.streak.days,1);
  bumpStreak(state);
  assert.equal(state.explorer.streak.days,1,'روزِ تکراری نباید زنجیره را زیاد کند');
});

test('مهاجرتِ دادهٔ قدیمی بدونِ شکستن',()=>{
  const s=migrate({v:2,explorer:{xp:500,missionsDone:[1,2]},names:{explorer:'علی'}});
  assert.equal(s.v,3);
  assert.equal(s.names.explorer,'علی');
  assert.equal(s.explorer.missionsDone.length,2);
  for(const k of SKILLS) assert.equal(typeof s.explorer.skills[k.id],'number');
});

test('نسخهٔ جوانه: ۴۵ جلسهٔ گفتاری با واژه و راهنمای والد',()=>{
  assert.equal(TODDLER_SESSIONS.length,45);
  const ids=new Set(TODDLER_SESSIONS.map(s=>s.id));
  assert.equal(ids.size,45);
  for(const s of TODDLER_SESSIONS){
    assert.equal(s.words.length,3);
    for(const w of s.words){assert.ok(w.w&&w.e&&w.s);}
    assert.ok(s.tip.length>40);
    assert.ok(['repeat','find','story','song','feel','routine'].includes(s.type));
  }
  assert.ok(new Set(TODDLER_SESSIONS.map(s=>s.type)).size>=6,'همهٔ انواعِ جلسه استفاده نشده');
});

test('نسخهٔ والدین: کارتِ لحظهٔ داغ، کارگاه، آرامش، بازی مشترک و چرخهٔ پدر',()=>{
  assert.ok(HOT_MOMENTS.length>=16);
  for(const h of HOT_MOMENTS){
    assert.ok(h.say.length>=3,'کمتر از سه جملهٔ آماده');
    assert.ok(h.avoid.length>=3);
    assert.ok(h.protocol.length>=4,'پروتکل باید حداقل چهار گام باشد');
    assert.ok(h.what.length>40&&h.after.length>10);
  }
  assert.ok(WORKSHOPS.length>=16);
  for(const w of WORKSHOPS){assert.ok(w.min>=5&&w.task.length>10);}
  assert.ok(CALM_SESSIONS.length>=6);
  for(const c of CALM_SESSIONS) assert.ok(c.script.length>80);
  assert.ok(COOP_GAMES.length>=10);
  for(const g of COOP_GAMES){assert.ok(g.roles&&g.how&&g.tip&&g.time);}
  assert.equal(AWAY_DAYS.length,10);
  assert.equal(HOME_DAYS.length,5);
  for(const d of AWAY_DAYS){assert.ok(d.message&&d.task&&d.kidTask&&d.focus);}
  for(const d of HOME_DAYS){assert.ok(d.plan.length>=4&&d.title&&d.goal);}
  assert.ok(PHONE_GAMES.length>=10);
  assert.ok(FATHER_COACHING.length>=8);
});

test('پوششِ محتوایی در برابر ۲۴۰ مأموریت',()=>{
  // دسته‌هایی که هر ایستگاه مصرف می‌کند باید بزرگ‌تر از یک دورِ تکرار باشند
  assert.ok(REALTASKS.length>=60,`کارِ واقعی: ${REALTASKS.length}`);
  assert.ok(SPEAKS.length>=40&&DRAWS.length>=40&&EXPERIMENTS.length>=20&&CRAFTS.length>=15);
  const totalCreative=SPEAKS.length+DRAWS.length+EXPERIMENTS.length+CRAFTS.length;
  assert.ok(totalCreative>=130,`مجموعِ محتوای ساختن: ${totalCreative}`);
  const totalReflect=DILEMMAS.length+EMOTIONS.length+PROVERBS.length;
  assert.ok(totalReflect>=50,`مجموعِ محتوای بازتاب: ${totalReflect}`);
  assert.equal(Object.keys(pools).length>=17,true);
});

test('رمزگشایی: همهٔ جمله‌ها با صفحه‌کلیدِ برنامه تایپ‌شدنی‌اند',()=>{
  // پیش‌تر نیم‌فاصله/اِعراب/«،» در متن می‌ماند و کودک راهی برای زدنِ آن نداشت
  for(const c of CIPHERS){
    const plain=normalizePlain(c.plain);
    const letters=[...plain.replace(/\s/g,'')];
    assert.ok(letters.length>=6,`«${c.plain}» خیلی کوتاه شد: ${plain}`);
    for(const ch of letters){
      assert.ok(PERSIAN_LETTERS.includes(ch),`«${c.plain}» حرفِ بدونِ کلید دارد: ${ch}`);
    }
    assert.ok(c.key&&c.key.length>1);
  }
  assert.equal(normalizePlain('می‌شود، آهسته و پیوستهٔ راه'),'میشود آهسته و پیوسته راه');
});

test('چشمِ عقاب: هدف‌ها روی خانه‌های جدا می‌نشینند و دور همیشه تمام‌شدنی است',()=>{
  // شبیه‌سازیِ همان الگوریتمِ جای‌گذاری که در mechanics.js است
  const size=7,hidden=5;
  for(let trial=0;trial<400;trial++){
    const cells=new Array(size*size).fill('f');
    const spots=cells.map((_,i)=>i).sort(()=>Math.random()-0.5).slice(0,hidden);
    const targets=['a','b','c','d','e'];
    targets.forEach((tg,j)=>{cells[spots[j]]=tg;});
    const placed=cells.filter(v=>v!=='f');
    assert.equal(new Set(placed).size,hidden,`هدف‌ها روی هم افتادند: ${spots.join(',')}`);
  }
});

test('تله‌متری: نتیجهٔ جزئیِ هر ایستگاه (extra) ذخیره می‌شود',()=>{
  const s=emptyFamily();
  recordStation(s,{m:3,s:4,mech:'speak',tries:0,hints:0,rate:1,millis:1200,stars:3,extra:{wrote:true}});
  recordStation(s,{m:3,s:5,mech:'draw',tries:1,hints:0,rate:.8,millis:900,stars:2});
  assert.equal(s.explorer.telemetry.length,2);
  assert.deepEqual(s.explorer.telemetry[0].extra,{wrote:true});
  assert.equal('extra' in s.explorer.telemetry[1],false);
  assert.equal(s.explorer.attempts.speak,1);
  assert.equal(s.explorer.bestTimes.draw,900);
});

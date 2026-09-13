// باغ قصه‌ها ۳ — موتورِ ساختِ مأموریت
// هر مأموریت ۶ ایستگاه دارد و محتوای هر ایستگاه از یک «دسته کارت» برداشته می‌شود:
// تا وقتی کل دسته تمام نشده، هیچ آیتمی تکرار نمی‌شود (این ویژگی آزمون خودکار دارد).

import {WORLDS,MISSIONS_PER_WORLD,missionAt} from './worlds.js';
import {RIDDLES,VERSES,FACTS,PROVERBS,WORDS,CIPHERS,CONSTELLATIONS,FOODS,EXPERIMENTS,CRAFTS,TIMELINE,PROVINCES} from './iran.js';
import {STORIES,DILEMMAS,EMOTIONS,REALTASKS,SPEAKS,DRAWS} from './stories.js';

/* --------- مولدِ قطعی (بدون تصادف غیرقابل تکرار) --------- */
export function seedRandom(seed){
  let s=seed>>>0||1;
  return ()=>{s^=s<<13;s>>>=0;s^=s>>17;s^=s<<5;s>>>=0;return s/4294967296;};
}
function shuffle(arr,seed){
  const a=arr.slice(),r=seedRandom(seed);
  for(let i=a.length-1;i>0;i--){const j=Math.floor(r()*(i+1));[a[i],a[j]]=[a[j],a[i]];}
  return a;
}
/** از دستهٔ کارت، آیتمِ شمارهٔ index را برمی‌گرداند؛ ترتیب هر «دور» عوض می‌شود ولی درونِ هر دور تکرار ندارد. */
export function deal(deck,index,salt=0){
  const len=deck.length;
  const cycle=Math.floor(index/len),pos=index%len;
  return shuffle(deck,1000+salt*77+cycle*13)[pos];
}
export const deckCycleOf=(deckLen,index)=>Math.floor(index/deckLen);

/* --------- انواع ایستگاه و سازوکارها --------- */
export const STATION_KINDS=Object.freeze([
 {id:'story',  name:'قصه و سؤال',      icon:'📜', skills:['heart','language']},
 {id:'game',   name:'چالش اصلی',       icon:'🎮', skills:['focus','logic']},
 {id:'puzzle', name:'معما و دانش',     icon:'🧩', skills:['logic','iran']},
 {id:'real',   name:'کارِ واقعی',      icon:'🌍', skills:['body','heart']},
 {id:'create', name:'ساختن و گفتن',    icon:'🎨', skills:['creative','courage']},
 {id:'reflect',name:'انتخاب و بازتاب', icon:'🪞', skills:['self','heart']}
]);

// سازوکارهای «چالش اصلی» — هر کدام یک بازیِ تعاملیِ جدا
export const GAME_MECHANICS=Object.freeze([
 'sequence','memoryPairs','path','bot','mathRace','focusFind','reflex',
 'pattern','oddOne','balance','sort','order','stars','estimate','rhythm','geo','timeline','food','trace'
]);
// سازوکارهای «معما و دانش»
export const PUZZLE_MECHANICS=Object.freeze(['riddle','verse','cipher','word','quiz','listen']);
// سازوکارهای «ساختن و گفتن»
export const CREATE_MECHANICS=Object.freeze(['draw','experiment','craft','speak','move']);
// سازوکارهای «انتخاب و بازتاب»
export const REFLECT_MECHANICS=Object.freeze(['emotion','dilemma','proverb']);

export const MECHANIC_LABEL={
 sequence:'نورهای دماوند',memoryPairs:'کارت‌های حافظه',path:'مسیرِ گنج',bot:'روباهِ برنامه‌نویس',
 mathRace:'مسابقهٔ عدد',focusFind:'چشمِ عقاب',reflex:'بگیر و نگیر',pattern:'الگویِ گمشده',
 oddOne:'کی فرق داره؟',balance:'ترازوی دانا',sort:'دسته‌بندی',order:'ترتیبِ درست',
 stars:'صورتِ فلکی',estimate:'تخمینِ دقیق',rhythm:'ریتمِ جنگل',geo:'نقشهٔ ایران',
 timeline:'خطِ زمان',food:'بشقابِ سلامت',trace:'خطِ خوش',
 riddle:'چیستان',verse:'شعرِ ناتمام',cipher:'رمزگشایی',word:'واژه‌سازی',quiz:'رازِ امروز',listen:'گوشِ تیز',
 draw:'نقاشی',experiment:'آزمایشگاه',craft:'کارگاه',speak:'بلند بگو',move:'بدنِ قوی',
 emotion:'کارتِ احساس',dilemma:'دو راهی',proverb:'ضرب‌المثل',story:'قصه',breathe:'نفسِ آرام'
};

/* --------- ساختِ مأموریت --------- */
export function buildMission(index,state){
  const meta=missionAt(index);
  const world=WORLDS[meta.world];
  const difficulty=meta.level; // 1..3
  const salt=meta.world*7+meta.local;
  const storyIx=Math.floor(index/1); // هر داستان یک‌بار در هر ۱۰ مأموریت

  const stations=[];
  // ۱) قصه و سؤال
  const storyMode=index%10===0?'tale':(index%3===0?'fact':'riddleStory');
  stations.push(makeStoryStation(storyMode,storyIx,meta,difficulty));
  // ۲) چالش اصلی — با تضمینِ اینکه با مأموریتِ قبلی یکی نباشد
  const gameMech=gameMechanicFor(index);
  stations.push({kind:'game',mech:gameMech,rounds:roundsFor(gameMech,difficulty,meta),
    difficulty,params:gameParams(gameMech,difficulty,meta,index),
    title:MECHANIC_LABEL[gameMech],brief:meta.brief});
  // ۳) معما و دانش (با سازوکار متفاوت از چالش اصلی)
  let puzzleMech=deal(PUZZLE_MECHANICS,index+5,salt);
  if(puzzleMech===gameMech) puzzleMech=PUZZLE_MECHANICS[(PUZZLE_MECHANICS.indexOf(puzzleMech)+1)%PUZZLE_MECHANICS.length];
  stations.push({kind:'puzzle',mech:puzzleMech,difficulty,params:puzzleParams(puzzleMech,index,meta),
    title:MECHANIC_LABEL[puzzleMech],brief:'یک راز را باز کن.'});
  // ۴) کارِ واقعی
  const task=deal(REALTASKS,index+11,salt);
  stations.push({kind:'real',mech:'real',difficulty,params:{task},title:task.t,brief:task.why});
  // ۵) ساختن و گفتن
  const createMech=CREATE_MECHANICS[(index+meta.world)%CREATE_MECHANICS.length];
  stations.push({kind:'create',mech:createMech,difficulty,params:createParams(createMech,index,meta),
    title:MECHANIC_LABEL[createMech],brief:'چیزی بساز یا بگو.'});
  // ۶) انتخاب و بازتاب
  const reflectMech=REFLECT_MECHANICS[(index*2+meta.world)%REFLECT_MECHANICS.length];
  stations.push({kind:'reflect',mech:reflectMech,difficulty,params:reflectParams(reflectMech,index,meta),
    title:MECHANIC_LABEL[reflectMech],brief:'یک انتخابِ واقعی.'});

  const xp=40+difficulty*15+meta.local;
  return {...meta,worldName:world.name,worldEmoji:world.emoji,guide:world.guide,
    intro:world.intro,accent:world.accent,sky:world.sky,ground:world.ground,ambient:world.ambient,
    stations,xp};
}

/** سازوکارِ چالشِ اصلی؛ اگر با مأموریتِ قبلی یکی بود، یکی جلو می‌رود. */
export function gameMechanicFor(index){
  const meta=missionAt(index);
  const salt=meta.world*7+meta.local;
  let mech=deal(GAME_MECHANICS,index,salt);
  if(index>0){
    const pm=missionAt(index-1);
    const prev=deal(GAME_MECHANICS,index-1,pm.world*7+pm.local);
    if(prev===mech) mech=GAME_MECHANICS[(GAME_MECHANICS.indexOf(mech)+1)%GAME_MECHANICS.length];
  }
  return mech;
}
const roundsFor=(mech,d,meta)=>{
  const base={sequence:3,memoryPairs:2,path:2,bot:2,mathRace:3,focusFind:3,reflex:3,pattern:3,oddOne:3,
    balance:2,sort:3,order:3,stars:2,estimate:3,rhythm:3,geo:3,timeline:2,food:2,trace:3}[mech]||3;
  return Math.min(6,base+(d-1)+(meta.local>18?1:0));
};

function makeStoryStation(mode,ix,meta,difficulty){
  if(mode==='tale'){
    const story=STORIES[deckCycleOf(STORIES.length,Math.floor(ix/10))%STORIES.length===0?0:0];
    // هر داستان دقیقاً یک‌بار در هر ۲۴۰ مأموریت استفاده می‌شود (اولین مأموریتِ هر ۱۰ تا)
    const idx=Math.floor(ix/10)%STORIES.length;
    return {kind:'story',mech:'story',difficulty,params:{story:STORIES[idx]},title:STORIES[idx].title,brief:'قصه را بشنو و جواب بده.'};
  }
  if(mode==='fact'){
    const fact=deal(FACTS,ix+23,meta.world);
    return {kind:'story',mech:'quiz',difficulty,params:{fact},title:'رازِ امروز',brief:fact.text.slice(0,60)+'…'};
  }
  const riddle=deal(RIDDLES,ix+41,meta.world);
  return {kind:'story',mech:'riddle',difficulty,params:{riddle,narrate:true},title:'چیستانِ نقال',brief:riddle.q};
}

function gameParams(mech,d,meta,index){
  const hard=d+(meta.local>16?1:0);
  switch(mech){
    case 'sequence': return {len:3+hard,pads:4+(hard>2?2:0)};
    case 'memoryPairs': return {pairs:3+hard*2,theme:meta.worldId};
    case 'path': return {size:5+hard,obstacles:2+hard*2,coins:3};
    case 'bot': return {size:6,steps:4+hard,blocks:2+hard};
    case 'mathRace': return {max:10+hard*25,ops:hard>1?['+','-','×']:['+','-'],count:6+hard*2};
    case 'focusFind': return {grid:hard>2?7:6,hidden:2+Math.min(3,hard)};
    case 'reflex': return {rounds:6+hard*2,traps:1+hard};
    case 'pattern': return {len:4+hard,kind:index%3};
    case 'oddOne': return {count:4+hard*2,rule:index%4};
    case 'balance': return {items:hard>2?9:6,weighs:hard>2?3:2};
    case 'sort': return {groups:3,cats:index%5};
    case 'order': return {len:4+Math.min(2,hard-1),topic:index%6};
    case 'stars': return {ci:index%CONSTELLATIONS.length};
    case 'estimate': return {min:8+hard*10,max:20+hard*22};
    case 'rhythm': return {len:3+hard};
    case 'geo': return {count:3+hard};
    case 'timeline': return {count:4};
    case 'food': return {target:index%3};
    case 'trace': return {wi:index%WORDS.length};
    default: return {hard};
  }
}
function puzzleParams(mech,index,meta){
  switch(mech){
    case 'riddle': return {riddle:deal(RIDDLES,index+61,meta.world)};
    case 'verse': return {verse:deal(VERSES,index+71,meta.world)};
    case 'cipher': return {cipher:deal(CIPHERS,index+81,meta.world)};
    case 'word': return {word:deal(WORDS,index+91,meta.world)};
    case 'quiz': return {fact:deal(FACTS,index+101,meta.world)};
    case 'listen': return {words:pickWords(index,meta),fact:deal(FACTS,index+121,meta.world)};
    default: return {};
  }
}
function pickWords(index,meta){
  const start=(index*4+meta.world)%WORDS.length;
  return [0,1,2,3,4].map(i=>WORDS[(start+i)%WORDS.length]);
}
function createParams(mech,index,meta){
  switch(mech){
    case 'draw': return {draw:deal(DRAWS,index+131,meta.world)};
    case 'experiment': return {exp:deal(EXPERIMENTS,index+141,meta.world)};
    case 'craft': return {craft:deal(CRAFTS,index+151,meta.world)};
    case 'speak': return {speak:deal(SPEAKS,index+161,meta.world)};
    case 'move': return {move:deal(REALTASKS.filter(t=>t.cat==='حرکت'),index+171,meta.world)};
    default: return {};
  }
}
function reflectParams(mech,index,meta){
  switch(mech){
    case 'emotion': return {emo:deal(EMOTIONS,index+181,meta.world)};
    case 'dilemma': return {dil:deal(DILEMMAS,index+191,meta.world)};
    case 'proverb': return {prov:deal(PROVERBS,index+201,meta.world),speak:deal(SPEAKS,index+211,meta.world)};
    default: return {};
  }
}

/* --------- وضعیتِ مأموریت: قفل، جاری، بعدی --------- */
export function missionStatus(index,state){
  const done=state.explorer.missionsDone;
  if(done.includes(index)) return 'done';
  const cur=state.explorer.current;
  if(cur&&cur.missionId===index) return 'current';
  if(index===0) return 'open';
  return done.includes(index-1)?'open':'locked';
}
export function nextMissionIndex(state){
  const done=state.explorer.missionsDone;
  for(let i=0;i<TOTAL();i++) if(!done.includes(i)) return i;
  return TOTAL()-1;
}
export const TOTAL=()=>WORLDS.length*MISSIONS_PER_WORLD;

/* --------- پاداش: ستاره بر پایهٔ تلاش، نه فقط درستی --------- */
export function starsFor(result){
  // result: {correctRate, tries, hints, finished, timeRatio}
  if(!result.finished) return 0;
  let s=1;
  if(result.correctRate>=0.5) s=2;
  if(result.correctRate>=0.8&&result.hints<=1) s=3;
  if(result.tries>2&&result.correctRate<0.5) s=2; // پاداشِ پشتکار
  return s;
}

/* --------- جمله‌های تشویقیِ اختصاصی (بر پایهٔ کارِ واقعیِ انجام‌شده) --------- */
export function praise(result,station){
  const {correctRate:r,hints,tries,finished}=result;
  const name=station&&station.title?`«${station.title}»`:'';
  if(!finished) return 'ادامه دادی و تا این‌جا آمدی؛ همین خودش قدم است.';
  if(hints===0&&r>=0.8) return `بدونِ هیچ راهنمایی ${name} را حل کردی. این فکرِ مستقل است.`;
  if(tries>=3) return `${tries} بار امتحان کردی و ول نکردی. پشتکارِ تو از نمره مهم‌تر است.`;
  if(hints>0&&r>=0.6) return 'از راهنما کمک گرفتی و بعد خودت جواب را پیدا کردی؛ این یعنی یاد گرفتی چطور یاد بگیری.';
  if(r<0.5) return 'این یکی سخت بود، ولی تا آخرش ماندی. مغز دقیقاً همین‌جا قوی می‌شود.';
  return 'خوب پیش رفتی؛ دقتت هر مرحله بیشتر می‌شود.';
}

/* --------- محتوای کمکی برای سازوکارها --------- */
export const pools={RIDDLES,VERSES,FACTS,PROVERBS,WORDS,CIPHERS,CONSTELLATIONS,FOODS,
  EXPERIMENTS,CRAFTS,TIMELINE,PROVINCES,STORIES,DILEMMAS,EMOTIONS,REALTASKS,SPEAKS,DRAWS};

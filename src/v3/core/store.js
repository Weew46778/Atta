// باغ قصه‌ها ۳ — هستهٔ داده و پیشرفت خانواده
// تمام وضعیت روی دستگاه ذخیره می‌شود؛ چیزی بدون اجازهٔ کاربر به سرور نمی‌رود.

export const SKILLS = Object.freeze([
  {id:'focus',   name:'تمرکز و دقت',        icon:'🎯', color:'#ff8a5b'},
  {id:'memory',  name:'حافظه',              icon:'🧠', color:'#8b6cff'},
  {id:'logic',   name:'استدلال و حل مسئله', icon:'🧩', color:'#3ec1d3'},
  {id:'math',    name:'ریاضی و عدد',        icon:'🔢', color:'#ffb020'},
  {id:'language',name:'زبان و ادب',         icon:'📖', color:'#ff6b9d'},
  {id:'creative',name:'خلاقیت و ساختن',     icon:'🎨', color:'#c77dff'},
  {id:'patience',name:'صبر و پشتکار',       icon:'🌿', color:'#5ec26a'},
  {id:'heart',   name:'مهربانی و همدلی',    icon:'💛', color:'#ff5d8f'},
  {id:'courage', name:'شجاعت و خودباوری',   icon:'🦁', color:'#ff7b54'},
  {id:'body',    name:'بدن و سلامت',        icon:'🤸', color:'#2fbf71'},
  {id:'iran',    name:'ایران‌شناسی',        icon:'🏛️', color:'#e0a458'},
  {id:'self',    name:'خودشناسی',           icon:'🪞', color:'#7f9cf5'}
]);

export const RANKS = Object.freeze([
  'جوانه','نهال','جوانهٔ شجاع','کاوشگر','رهگذرِ باغ','ستاره‌یاب','کوه‌نورد','قصه‌گو',
  'دانشمندِ کوچک','راهنما','دلاور','سیمرغِ کوچک'
]);

const KEY='bagh.v3.state';
const TELEMETRY_CAP=600;
const GALLERY_CAP=24;

export const emptyFamily=()=>({
  v:3,
  names:{explorer:'کاوشگر',toddler:'جوانه',mother:'مامان',father:'بابا'},
  edition:null,
  cycleStart:null,                 // تاریخ شروع چرخهٔ ۱۵ روزهٔ پدر
  settings:{sound:true,voice:'auto',rate:1,autoSpeak:true,bigText:false,motion:'full',breakMinutes:20},
  explorer:{
    xp:0,level:1,missionsDone:[],collectibles:[],badges:[],
    skills:Object.fromEntries(SKILLS.map(s=>[s.id,0])),
    interests:{},attempts:{},bestTimes:{},
    current:null,                  // {missionId, station, log:[], startedAt}
    gallery:[],streak:{days:0,last:null,best:0},
    telemetry:[],specials:[],      // مأموریت‌های ویژهٔ پیشنهادشده
    pride:[]                       // جمله‌های «امروز من…»
  },
  toddler:{sessionsDone:[],words:{},current:null,ritual:{},notes:[]},
  mother:{lessonsDone:[],hotMoments:[],calmDone:[],plans:{},coOps:[],notes:[]},
  father:{messages:[],plansDone:[],rituals:[],notes:[]},
  log:[]                           // رویدادهای مشترک خانواده
});

export function load(){
  try{
    const raw=localStorage.getItem(KEY);
    if(!raw) return emptyFamily();
    const parsed=JSON.parse(raw);
    return migrate(parsed);
  }catch{ return emptyFamily(); }
}

export function migrate(raw){
  const base=emptyFamily();
  if(!raw||typeof raw!=='object') return base;
  const s=deepMerge(base,raw);
  s.v=3;
  for(const k of Object.keys(s.explorer.skills)) if(typeof s.explorer.skills[k]!=='number') s.explorer.skills[k]=0;
  for(const skill of SKILLS) if(!(skill.id in s.explorer.skills)) s.explorer.skills[skill.id]=0;
  if(!Array.isArray(s.explorer.telemetry)) s.explorer.telemetry=[];
  s.explorer.telemetry=s.explorer.telemetry.slice(-TELEMETRY_CAP);
  return s;
}

function deepMerge(base,over){
  if(Array.isArray(base)) return Array.isArray(over)?over.slice():base.slice();
  if(base&&typeof base==='object'){
    const out={...base};
    for(const k of Object.keys(base)) if(over&&k in over) out[k]=deepMerge(base[k],over[k]);
    if(over&&typeof over==='object') for(const k of Object.keys(over)) if(!(k in out)) out[k]=over[k];
    return out;
  }
  return over===undefined?base:over;
}

let memoryFallback=null;
export function persist(state){
  try{localStorage.setItem(KEY,JSON.stringify(state));return true;}
  catch{memoryFallback=state;return false;}
}
export const persistenceOk=()=>memoryFallback===null;

export const fa=n=>String(n??'').replace(/[0-9]/g,d=>'۰۱۲۳۴۵۶۷۸۹'[+d]);
export const faWords=n=>fa(n);
export const clamp=(n,a,b)=>Math.max(a,Math.min(b,n));
export const uid=(p='id')=>p+Math.random().toString(36).slice(2,9);
export const todayISO=()=>new Date().toISOString().slice(0,10);
export function daysBetween(a,b){return Math.round((new Date(b)-new Date(a))/86400000);}

// ---------- امتیاز و سطح ----------
export const xpForLevel=l=>Math.round(120*Math.pow(l,1.28));
export function levelFromXp(xp){
  let l=1;while(xp>=xpForLevel(l+1)&&l<60)l++;return l;
}
export const rankOf=level=>RANKS[Math.min(RANKS.length-1,Math.floor((level-1)/4))];
export function levelProgress(state){
  const l=state.explorer.level,cur=xpForLevel(l),next=xpForLevel(l+1);
  return {level:l,rank:rankOf(l),into:state.explorer.xp-cur,need:Math.max(1,next-cur),
    pct:clamp((state.explorer.xp-cur)/Math.max(1,next-cur),0,1)};
}

// هر مهارت ۰ تا ۱۰۰؛ نمرهٔ کیفیت اجرا در آن ذخیره می‌شود
export function applySkills(state,mechanic,quality){
  const map=mechanicSkills[mechanic.id]||mechanic.skills||[];
  for(const {skill,weight=1} of map){
    const cur=state.explorer.skills[skill]||0;
    // میانگین موزون با وزن تلاش: پیشرفت نرم و بدون سقوط ناگهانی
    state.explorer.skills[skill]=Math.round(clamp(cur*0.86+quality*100*weight*0.14,0,100));
  }
  const tags=mechanic.interests||[];
  for(const t of tags) state.explorer.interests[t]=(state.explorer.interests[t]||0)+1;
}
export const mechanicSkills={
  sequence:[{skill:'memory',weight:1.1},{skill:'focus'}],
  rhythm:[{skill:'focus',weight:1.1},{skill:'patience'}],
  memoryPairs:[{skill:'memory',weight:1.2}],
  oddOne:[{skill:'logic',weight:1.1},{skill:'focus'}],
  pattern:[{skill:'logic',weight:1.2},{skill:'math'}],
  path:[{skill:'logic',weight:1.1},{skill:'focus'}],
  bot:[{skill:'logic',weight:1.3}],
  balance:[{skill:'logic',weight:1.2}],
  mathRace:[{skill:'math',weight:1.3},{skill:'focus'}],
  estimate:[{skill:'math',weight:1.2}],
  sort:[{skill:'logic'},{skill:'iran'}],
  order:[{skill:'logic'},{skill:'memory'}],
  cipher:[{skill:'language',weight:1.2},{skill:'logic'}],
  word:[{skill:'language',weight:1.3}],
  verse:[{skill:'language',weight:1.3},{skill:'iran'}],
  geo:[{skill:'iran',weight:1.3},{skill:'memory'}],
  timeline:[{skill:'iran',weight:1.3},{skill:'logic'}],
  listen:[{skill:'memory',weight:1.2},{skill:'language'}],
  focusFind:[{skill:'focus',weight:1.4}],
  speak:[{skill:'courage',weight:1.3},{skill:'language'},{skill:'self'}],
  draw:[{skill:'creative',weight:1.4}],
  breathe:[{skill:'patience',weight:1.3},{skill:'self'}],
  reflex:[{skill:'focus',weight:1.2},{skill:'body'}],
  emotion:[{skill:'heart',weight:1.4},{skill:'self'}],
  dilemma:[{skill:'heart',weight:1.2},{skill:'self'},{skill:'logic'}],
  measure:[{skill:'math',weight:1.2},{skill:'body'}],
  interview:[{skill:'heart',weight:1.2},{skill:'courage'},{skill:'language'}],
  experiment:[{skill:'logic',weight:1.1},{skill:'creative'},{skill:'patience'}],
  craft:[{skill:'creative',weight:1.2},{skill:'math'}],
  move:[{skill:'body',weight:1.4},{skill:'patience'}],
  food:[{skill:'body',weight:1.2},{skill:'logic'}],
  stars:[{skill:'iran',weight:1.1},{skill:'logic'},{skill:'focus'}],
  trace:[{skill:'language',weight:1.2},{skill:'focus'}],
  riddle:[{skill:'logic',weight:1.2},{skill:'language'}],
  story:[{skill:'heart',weight:1.1},{skill:'language'},{skill:'self'}],
  quiz:[{skill:'iran'},{skill:'memory'}]
};

// ---------- ثبت رویداد مرحله ----------
export function recordStation(state,rec){
  const mech=rec.mech||rec.mechanic;
  const {extra,...rest}=rec;
  state.explorer.telemetry.push({ts:Date.now(),...rest,mech,
    ...(extra&&Object.keys(extra).length?{extra}:{})});
  if(state.explorer.telemetry.length>TELEMETRY_CAP) state.explorer.telemetry.splice(0,state.explorer.telemetry.length-TELEMETRY_CAP);
  if(!mech) return;
  const a=state.explorer.attempts;
  a[mech]=(a[mech]||0)+1;
  if(rec.millis!=null){
    const b=state.explorer.bestTimes[mech];
    if(b==null||rec.millis<b) state.explorer.bestTimes[mech]=rec.millis;
  }
}

export function bumpStreak(state){
  const t=todayISO(),s=state.explorer.streak;
  if(s.last===t) return s;
  s.days=(s.last&&daysBetween(s.last,t)===1)?s.days+1:1;
  s.last=t;s.best=Math.max(s.best,s.days);
  return s;
}

export function logEvent(state,role,type,text,extra={}){
  state.log.push({ts:Date.now(),role,type,text,...extra});
  if(state.log.length>400) state.log.splice(0,state.log.length-400);
}

// ---------- تحلیل و توصیه (قاعده‌محور، نه تشخیص بالینی) ----------
export function analysis(state){
  const e=state.explorer,skills=e.skills,tele=e.telemetry.slice(-160);
  const bySkill=Object.fromEntries(SKILLS.map(s=>[s.id,skills[s.id]||0]));
  const sorted=Object.entries(bySkill).sort((a,b)=>b[1]-a[1]);
  const strongest=sorted.slice(0,3),weakest=sorted.slice(-3).reverse();
  const avgMillis=tele.filter(t=>t.millis).reduce((s,t)=>s+t.millis,0)/(tele.filter(t=>t.millis).length||1);
  const hintRate=tele.length?tele.filter(t=>(t.hints||0)>0).length/tele.length:0;
  const retryRate=tele.length?tele.filter(t=>(t.tries||0)>1).length/tele.length:0;
  const interests=Object.entries(e.interests).sort((a,b)=>b[1]-a[1]).slice(0,4);
  const notes=[];
  if(avgMillis&&avgMillis<7000&&hintRate<0.2) notes.push({tone:'good',text:'سرعت پاسخ خوب است؛ حالا چالش‌های سخت‌تر را هم می‌تواند انجام دهد.'});
  if(avgMillis&&avgMillis>32000) notes.push({tone:'care',text:'پاسخ‌ها آهسته است. احتمالاً خستگی یا سختی زیاد؛ مرحله‌های کوتاه‌تر پیشنهاد می‌شود.'});
  if(retryRate>0.45) notes.push({tone:'good',text:'تکرار و تلاش دوباره زیاد است؛ این نشانهٔ پشتکار است، نه ضعف.'});
  if(hintRate>0.6) notes.push({tone:'care',text:'بیشتر مرحله‌ها با راهنما انجام شده. خوب است گاهی بگوییم: «اول خودت یک بار امتحان کن».'});
  if(e.missionsDone.length>=3&&e.streak.days===0) notes.push({tone:'care',text:'زنجیرهٔ روزانه قطع شده. یک مرحلهٔ ۵ دقیقه‌ای امروز کافی است.'});
  const pride=(e.pride||[]).length;
  if(pride<3&&e.missionsDone.length>6) notes.push({tone:'care',text:'«امروز من…» کم نوشته شده. خودشناسی با جمله‌های کوتاه رشد می‌کند.'});
  return {bySkill,strongest,weakest,interests,hintRate,retryRate,avgMillis,notes,
    missions:e.missionsDone.length,collectibles:e.collectibles.length};
}

export function recommendations(state){
  const a=analysis(state),out=[];
  for(const [skill,value] of a.weakest){
    if(value>=45) continue;
    const s=SKILLS.find(x=>x.id===skill);
    out.push({
      skill:s.name,icon:s.icon,
      title:`تمرینِ بیشتر برای ${s.name}`,
      body:`میانگین این مهارت ${fa(Math.round(value))} از ۱۰۰ است. سه روز، هر روز یک مرحلهٔ کوتاه از نوع مرتبط کافی است.`,
      action:'مأموریت ویژه بساز'
    });
  }
  for(const [tag,n] of a.interests.slice(0,2)){
    if(n<3) continue;
    out.push({skill:'علاقه',icon:'⭐',title:`علاقهٔ روشن به ${tag}`,
      body:`${fa(n)} بار سمت موضوع‌های «${tag}» رفته است. یک کتاب، مستند یا کار عملی در همین موضوع، انگیزه را چند برابر می‌کند.`,
      action:'پیشنهاد به خانواده'});
  }
  for(const n of a.notes) out.push({skill:'مشاهده',icon:n.tone==='good'?'✅':'💡',title:n.text,body:'',action:''});
  return out.slice(0,8);
}

export function weeklySummary(state){
  const week=Date.now()-7*86400000;
  const recent=state.explorer.telemetry.filter(t=>t.ts>week);
  const a=analysis(state);
  const mech={};for(const r of recent) mech[r.mechanic]=(mech[r.mechanic]||0)+1;
  const top=Object.entries(mech).sort((x,y)=>y[1]-x[1]).slice(0,3);
  return {
    stations:recent.length,
    missions:state.explorer.missionsDone.length,
    topMechanics:top.map(([m,n])=>({mech:m,count:n})),
    strengths:a.strongest.map(([id,v])=>({name:SKILLS.find(s=>s.id===id).name,value:v})),
    growth:a.weakest.map(([id,v])=>({name:SKILLS.find(s=>s.id===id).name,value:v})),
    interests:a.interests.map(([t,n])=>({tag:t,count:n}))
  };
}

export function exportBundle(state){
  return JSON.stringify({...state,exportedAt:new Date().toISOString(),app:'باغ قصه‌ها ۳'},null,1);
}
export function importBundle(state,text){
  const data=JSON.parse(text);
  if(!data||data.v!==3) throw new Error('فایل معتبر نیست');
  return migrate(data);
}

// باغ قصه‌ها ۳ — پوستهٔ برنامه، خانهٔ کودک، تنظیمات و مسیریابی
import {load,persist,fa,SKILLS,levelFromXp,rankOf,levelProgress,analysis,recommendations,weeklySummary,exportBundle,importBundle,logEvent} from './core/store.js';
import {Narrator,initSpeech,sfx,confetti,sleep,escapeHtml,nativeVoice,canListen,VOICES} from './core/audio.js';
import {buildMission,missionStatus,nextMissionIndex,TOTAL,MECHANIC_LABEL} from './content/missions.js';
import {WORLDS,MISSIONS_PER_WORLD} from './content/worlds.js';
import {openMission,runStandaloneMechanic} from './engine/runner.js';
import {renderJourney} from './journey.js';
import {renderToddler,renderMother,renderFather,renderReport,renderGallery} from './screens/parents.js';

export const state=load();
const params=new URLSearchParams(location.search);
const EDITION=params.get('edition')||state.edition||'explorer';
state.edition=EDITION;

export const narrator=new Narrator(state);
initSpeech();

let page='home';
const root=document.getElementById('app');
const toastEl=document.getElementById('toast');
export function toast(text,ms=3800){
  toastEl.textContent=text;toastEl.classList.add('visible');
  clearTimeout(toast.t);toast.t=setTimeout(()=>toastEl.classList.remove('visible'),ms);
}
export const say=async(text,opts)=>narrator.narrate(text,opts);
export const save=()=>{if(!persist(state)) toast('ذخیره نشد؛ حافظهٔ دستگاه پر است.');};

const EDITIONS={
 explorer:{name:'کاوشگر',sub:'۸ تا ۱۰ سال',icon:'🦊',color:'#ff8a5b'},
 toddler:{name:'جوانه',sub:'۲ تا ۳ سال',icon:'🐣',color:'#ffb020'},
 mother:{name:'مامان',sub:'راهنمای خانواده',icon:'🌷',color:'#ff6b9d'},
 father:{name:'بابا',sub:'چرخهٔ ۱۰ و ۵',icon:'🧭',color:'#5b8def'}
};
document.documentElement.dataset.edition=EDITION;
document.title='باغ قصه‌ها ۳ — '+EDITIONS[EDITION].name;

/* ---------------- پوسته ---------------- */
export function render(){
  document.body.classList.toggle('calm-motion',state.settings.motion==='calm');
  document.body.classList.toggle('big-text',!!state.settings.bigText);
  root.className='shell edition-'+EDITION;
  if(EDITION==='toddler') return renderToddler(root,{state,narrator,save,toast,say});
  if(EDITION==='mother') return renderMother(root,{state,narrator,save,toast,say,go});
  if(EDITION==='father') return renderFather(root,{state,narrator,save,toast,say,go});
  return renderChild();
}
function go(p){page=p;render();window.scrollTo(0,0);}

function navItems(){
  return [
    ['home','🏠','خانه'],
    ['journey','🗺','نقشهٔ سفر'],
    ['gallery','🎨','گالری'],
    ['report','📊','دفترِ من'],
    ['settings','⚙️','تنظیمات']
  ];
}

function renderChild(){
  const done=state.explorer.missionsDone.length;
  const lp=levelProgress(state);
  root.innerHTML=`
  <div class="bg-scene" aria-hidden="true">
    <div class="bg-sun"></div><div class="bg-hill b1"></div><div class="bg-hill b2"></div><div class="bg-hill b3"></div>
    <div class="bg-birds"><span>🕊</span><span>🕊</span></div>
    <div class="bg-fireflies">${'<i></i>'.repeat(14)}</div>
  </div>
  <header class="top">
    <button class="avatar" id="avatar" title="پروفایل">
      <span class="avatar-face">🦊</span>
      <span class="avatar-ring" style="--p:${Math.round(lp.pct*100)}"></span>
    </button>
    <div class="top-id">
      <b>${escapeHtml(state.names.explorer)}</b>
      <small>سطح ${fa(lp.level)} · ${lp.rank}</small>
      <div class="xp-bar"><span style="width:${Math.round(lp.pct*100)}%"></span></div>
    </div>
    <div class="top-stats">
      <div class="chip-stat" title="زنجیرهٔ روزها">🔥 <b>${fa(state.explorer.streak.days)}</b></div>
      <div class="chip-stat" title="برگِ تلاش">🍃 <b>${fa(state.explorer.xp)}</b></div>
      <button class="chip-stat sound-chip" id="sound" title="صدا">${state.settings.sound?'🔊':'🔇'}</button>
    </div>
  </header>
  <main class="content" id="content"></main>
  <nav class="bottom-nav">${navItems().map(([id,ic,label])=>
    `<button class="nav-btn ${page===id?'active':''}" data-page="${id}"><span>${ic}</span><small>${label}</small></button>`).join('')}</nav>
  <div id="layer-root"></div>`;
  root.querySelectorAll('.nav-btn').forEach(b=>b.onclick=()=>{sfx.tap();go(b.dataset.page);});
  root.querySelector('#sound').onclick=()=>{state.settings.sound=!state.settings.sound;save();render();};
  root.querySelector('#avatar').onclick=()=>go('settings');
  const c=root.querySelector('#content');
  if(page==='journey') renderJourney(c,{state,start,go,toast});
  else if(page==='report') renderReport(c,{state,save,toast,say});
  else if(page==='gallery') renderGallery(c,{state,toast});
  else if(page==='settings') renderSettings(c);
  else renderHome(c);
}

/* ---------------- خانهٔ کودک ---------------- */
function renderHome(host){
  const next=nextMissionIndex(state);
  const m=buildMission(next,state);
  const done=state.explorer.missionsDone.length;
  const a=analysis(state);
  const world=WORLDS[m.world];
  const hour=new Date().getHours();
  const greet=hour<12?'صبحِ تو بخیر':hour<17?'ظهرِ تو بخیر':'عصرِ تو بخیر';
  host.innerHTML=`
  <section class="hero">
    <div class="hero-copy">
      <span class="hello">${greet}، ${escapeHtml(state.names.explorer)} 👋</span>
      <h1>امروز <em>${escapeHtml(m.title)}</em><br>منتظرِ توست</h1>
      <p>${escapeHtml(m.brief)}</p>
      <div class="hero-actions">
        <button class="btn primary big pulse" id="play">▶ ${done?'ادامهٔ سفر':'شروعِ سفر'}</button>
        <button class="btn ghost" id="listen-intro">🔊 بشنو</button>
      </div>
      <div class="hero-meta">
        <span>${world.emoji} ${world.name}</span><span>🎯 ${fa(6)} ایستگاه</span><span>⏳ حدود ۱۲ دقیقه</span>
      </div>
    </div>
    <div class="hero-stage">
      <div class="stage-sky" style="--s1:${world.sky[0]};--s2:${world.sky[1]}"></div>
      <div class="stage-ground" style="--g1:${world.ground[0]};--g2:${world.ground[1]}"></div>
      <div class="fox-hero"><div class="fox-body">🦊</div><div class="fox-bubble" id="fox-bubble">سلام! آماده‌ای؟</div></div>
      <div class="hero-particles">${'<i></i>'.repeat(10)}</div>
    </div>
  </section>

  <section class="cards">
    <article class="stat-card c1"><span>🎖</span><div><b>${fa(done)}</b><small>مأموریتِ تمام‌شده از ${fa(TOTAL())}</small></div></article>
    <article class="stat-card c2"><span>🎁</span><div><b>${fa(state.explorer.collectibles.length)}</b><small>یادگاریِ نام‌دار</small></div></article>
    <article class="stat-card c3"><span>⭐</span><div><b>${fa(a.missions?Math.round(Object.values(state.explorer.skills).reduce((x,y)=>x+y,0)/12):0)}</b><small>میانگینِ مهارت</small></div></article>
  </section>

  <section class="panel">
    <header class="panel-head"><h2>مسیرِ تو</h2><button class="link" id="see-map">دیدنِ نقشه ⬅</button></header>
    <div class="world-strip">
      ${WORLDS.map((w,i)=>{
        const total=MISSIONS_PER_WORLD,fin=state.explorer.missionsDone.filter(x=>Math.floor(x/MISSIONS_PER_WORLD)===i).length;
        const unlocked=fin>0||i===0||state.explorer.missionsDone.some(x=>Math.floor(x/MISSIONS_PER_WORLD)===i-1);
        return `<button class="world-pill ${unlocked?'':'locked'} ${i===m.world?'current':''}" data-w="${i}">
          <span class="wp-emoji">${w.emoji}</span><small>${w.name}</small>
          <i style="width:${Math.round(fin/total*100)}%"></i></button>`;}).join('')}
    </div>
  </section>

  <section class="panel">
    <header class="panel-head"><h2>امروز چه چیزی قوی‌تر شد؟</h2></header>
    <div class="skill-mini">${a.strongest.slice(0,3).map(([id,v])=>{
      const s=SKILLS.find(x=>x.id===id);
      return `<div class="skill-mini-item"><span>${s.icon}</span><div><b>${s.name}</b><i style="width:${v}%"></i></div><small>${fa(Math.round(v))}</small></div>`;
    }).join('')}</div>
    <button class="btn ghost wide" id="to-report">📊 گزارشِ کاملِ مهارت‌ها</button>
  </section>

  <section class="panel pride-panel">
    <header class="panel-head"><h2>امروز من…</h2><small>خودشناسی با یک جمله</small></header>
    <div class="pride-actions">
      <button class="btn ghost" id="pride-mic">🎤 با صدا بگو</button>
      <button class="btn ghost" id="pride-write">✍ بنویس</button>
    </div>
    <div class="pride-list">${(state.explorer.pride||[]).slice(-5).reverse().map(p=>`<article><p>${escapeHtml(p.text)}</p><small>${new Date(p.ts).toLocaleDateString('fa-IR')}</small></article>`).join('')||'<p class="muted">هنوز جمله‌ای ننوشته‌ای. یک جملهٔ کوتاه هم حساب است.</p>'}</div>
  </section>`;

  host.querySelector('#play').onclick=()=>start(next);
  host.querySelector('#see-map').onclick=()=>go('journey');
  host.querySelector('#to-report').onclick=()=>go('report');
  host.querySelectorAll('.world-pill:not(.locked)').forEach(b=>b.onclick=()=>{go('journey');setTimeout(()=>{
    const el=document.querySelector(`.j-world[data-w="${b.dataset.w}"]`);el&&el.scrollIntoView({behavior:'smooth',block:'center'});},60);});
  host.querySelector('#listen-intro').onclick=()=>{narrator.narrate(`${greet} ${state.names.explorer}. مأموریتِ امروز: ${m.title}. ${m.brief}`);};
  host.querySelector('#pride-mic').onclick=async()=>{
    const {listenOne}=await import('./core/audio.js');
    const r=await listenOne({timeout:9000});
    if(r.ok&&r.text.trim()){pushPride(r.text.trim());}
    else toast('صدایت ثبت نشد. می‌توانی بنویسی یا برای خانواده بگویی.');
  };
  host.querySelector('#pride-write').onclick=()=>{
    const t=prompt('امروز من… (یک جملهٔ کوتاه)');
    if(t&&t.trim()) pushPride(t.trim());
  };
  const bubble=host.querySelector('#fox-bubble');
  const lines=['سلام! آماده‌ای؟','امروز یک راز تازه داریم!','هر قدمِ تو یک برگِ تلاش است.','از اشتباه نترس؛ مغز با آن رشد می‌کند.'];
  let li=0;setInterval(()=>{if(!document.body.contains(bubble))return;li=(li+1)%lines.length;bubble.textContent=lines[li];},5200);
  if(state.settings.autoSpeak) narrator.narrate(`${greet} ${state.names.explorer}. ${m.brief}`);
}
function pushPride(text){
  state.explorer.pride=state.explorer.pride||[];
  state.explorer.pride.push({text,ts:Date.now()});
  if(state.explorer.pride.length>60) state.explorer.pride.shift();
  logEvent(state,'explorer','pride',text);
  save();render();toast('جملهٔ تو ثبت شد. خودشناسی با همین جمله‌ها ساخته می‌شود.');
}

/* ---------------- اجرای مأموریت ---------------- */
export function start(index){
  const m=buildMission(index,state);
  const layerRoot=root.querySelector('#layer-root')||document.body;
  openMission(layerRoot,{mission:m,state,narrator,sfxOn:state.settings.sound,autoSolve:params.has('test')&&!params.has('noauto'),
    onSaved:save,
    onExit:(log,extra)=>{save();render();
      if(extra&&extra.next){const nx=nextMissionIndex(state);if(nx!==index)start(nx);}
    }});
}

/* ---------------- تنظیمات ---------------- */
function renderSettings(host){
  host.innerHTML=`
  <section class="panel">
    <header class="panel-head"><h2>تنظیمات</h2></header>
    <div class="setting-row"><label>نامِ کاوشگر</label><input id="nm" value="${escapeHtml(state.names.explorer)}"></div>
    <div class="setting-row"><label>نامِ خواهر/برادرِ کوچک</label><input id="nt" value="${escapeHtml(state.names.toddler)}"></div>
    <div class="setting-row"><label>صدا و گوینده</label>
      <div class="seg" id="voice">${VOICES.map(v=>`<button data-v="${v.id}" class="${state.settings.voice===v.id?'on':''}">${v.name}</button>`).join('')}</div>
      <small class="hint">${nativeVoice()?'گویندهٔ فارسیِ دستگاه فعال است.':'گویندهٔ مرورگر استفاده می‌شود. اگر صدایی نشنیدی، متن روی صفحه می‌ماند.'}</small>
    </div>
    <div class="setting-row"><label>سرعتِ گفتار</label><input type="range" id="rate" min="0.7" max="1.3" step="0.05" value="${state.settings.rate}"></div>
    <div class="setting-row"><label>خواندنِ خودکارِ متن‌ها</label><button class="switch ${state.settings.autoSpeak?'on':''}" id="auto"></button></div>
    <div class="setting-row"><label>حرکتِ آرام (کمتر انیمیشن)</label><button class="switch ${state.settings.motion==='calm'?'on':''}" id="motion"></button></div>
    <div class="setting-row"><label>نوشتهٔ بزرگ‌تر</label><button class="switch ${state.settings.bigText?'on':''}" id="big"></button></div>
    <div class="setting-row"><label>یادآوریِ استراحت (دقیقه)</label>
      <div class="seg" id="brk">${[15,20,30,60].map(v=>`<button data-v="${v}" class="${state.settings.breakMinutes===v?'on':''}">${fa(v)}</button>`).join('')}</div>
    </div>
  </section>
  <section class="panel">
    <header class="panel-head"><h2>نسخه‌های خانواده</h2></header>
    <div class="edition-list">${Object.entries(EDITIONS).map(([id,e])=>`<button class="edition-card ${id===EDITION?'on':''}" data-e="${id}">
      <span>${e.icon}</span><div><b>${e.name}</b><small>${e.sub}</small></div>${id===EDITION?'<em>همین نسخه</em>':'<em>باز کردن</em>'}</button>`).join('')}</div>
    <small class="hint">در فایلِ نصبِ هر نسخه، همان نسخه باز می‌شود. این فهرست برای پیش‌نمایشِ وب است.</small>
  </section>
  <section class="panel">
    <header class="panel-head"><h2>داده‌ها و حریم</h2></header>
    <p class="hint">همهٔ اطلاعات فقط روی همین دستگاه ذخیره می‌شود. صداها آپلود نمی‌شوند. ضبطِ صدا اختیاری است.</p>
    <div class="btn-row">
      <button class="btn ghost" id="exp">⬇ گرفتنِ نسخهٔ پشتیبان</button>
      <button class="btn ghost" id="imp">⬆ بازیابی</button>
      <button class="btn danger" id="reset">پاک کردنِ همه</button>
    </div>
  </section>`;
  host.querySelector('#nm').onchange=e=>{state.names.explorer=e.target.value.trim()||'کاوشگر';save();render();};
  host.querySelector('#nt').onchange=e=>{state.names.toddler=e.target.value.trim()||'جوانه';save();};
  host.querySelectorAll('#voice button').forEach(b=>b.onclick=()=>{state.settings.voice=b.dataset.v;save();render();});
  host.querySelector('#rate').oninput=e=>{state.settings.rate=+e.target.value;save();};
  const tog=(sel,key,val)=>host.querySelector(sel).onclick=e=>{state.settings[key]=val();save();e.target.classList.toggle('on');};
  tog('#auto','autoSpeak',()=>!state.settings.autoSpeak);
  tog('#motion','motion',()=>state.settings.motion==='calm'?'full':'calm');
  tog('#big','bigText',()=>!state.settings.bigText);
  host.querySelectorAll('#brk button').forEach(b=>b.onclick=()=>{state.settings.breakMinutes=+b.dataset.v;save();render();});
  host.querySelectorAll('.edition-card').forEach(b=>b.onclick=()=>{
    if(b.dataset.e===EDITION)return;
    location.search='?edition='+b.dataset.e;
  });
  host.querySelector('#exp').onclick=()=>{
    const blob=new Blob([exportBundle(state)],{type:'application/json'});
    const a=document.createElement('a');a.href=URL.createObjectURL(blob);a.download='bagh-family-backup.json';a.click();
  };
  host.querySelector('#imp').onclick=async()=>{
    const f=document.createElement('input');f.type='file';f.accept='.json';
    f.onchange=async()=>{try{const t=await f.files[0].text();Object.assign(state,importBundle(state,t));save();render();toast('بازیابی انجام شد.');}catch{toast('فایل معتبر نبود.');}};
    f.click();
  };
  host.querySelector('#reset').onclick=()=>{if(confirm('همهٔ پیشرفت پاک شود؟')){localStorage.clear();location.reload();}};
}

/* ---------------- استراحتِ دوره‌ای ---------------- */
let playStart=Date.now();
/** استراحتِ دوره‌ای: فقط تذکر نیست؛ تمرینِ نفسِ آرام را هم اجرا می‌کند. */
export function showBreak(){
  if(document.querySelector('.break-overlay')) return null;
  playStart=Date.now();
  const g=document.createElement('div');g.className='break-overlay';
  g.innerHTML=`<div class="break-card"><span>🌙</span><h2>چشم‌هات خسته شد</h2>
    <p>بیست ثانیه به دوردست نگاه کن و پنج نفسِ آرام بکش.</p>
    <div class="break-calm"></div>
    <button class="btn primary">باشه، ادامه می‌دم</button></div>`;
  document.body.appendChild(g);
  runStandaloneMechanic('breathe',{host:g.querySelector('.break-calm'),state,narrator,rounds:1});
  g.querySelector('button').onclick=()=>g.remove();
  return g;
}
setInterval(()=>{
  if(document.querySelector('.mission-layer')&&Date.now()-playStart>state.settings.breakMinutes*60000) showBreak();
},20000);

render();
if(params.has('test')){
  // قلابِ آزمون: فقط برای پیمایشِ خودکارِ ۲۴۰ مأموریت در تستِ مرورگر
  window.__baghTest={state,save,start,go,
    missionsTotal:TOTAL(),
    complete:(i)=>start(i),
    breakNow:()=>showBreak(),
    metrics:()=>({missions:state.explorer.missionsDone.length,xp:state.explorer.xp,
      collectibles:state.explorer.collectibles.length,skills:state.explorer.skills,
      stations:state.explorer.telemetry.length})};
}
window.addEventListener('error',e=>{
  console.error(e.error||e.message);
});

// باغ قصه‌ها ۳ — نقشهٔ سفر: یک جادهٔ پیوسته و انیمیشنی از باغِ بیداری تا قلهٔ سیمرغ
import {WORLDS,MISSIONS_PER_WORLD,missionAt} from './content/worlds.js';
import {missionStatus,nextMissionIndex,buildMission} from './content/missions.js';
import {fa} from './core/store.js';
import {sfx} from './core/audio.js';

const SPACING=82, TOP=46;
function nodePoint(i,seed){
  const x=50+31*Math.sin(i*0.62+seed*1.7)+6*Math.sin(i*0.21+seed);
  return {x,y:TOP+i*SPACING};
}
function pathThrough(pts){
  if(pts.length<2) return '';
  let d=`M ${pts[0].x} ${pts[0].y}`;
  for(let i=0;i<pts.length-1;i++){
    const p=pts[i],n=pts[i+1];
    const mx=(p.x+n.x)/2;
    d+=` C ${mx} ${p.y}, ${mx} ${n.y}, ${n.x} ${n.y}`;
  }
  return d;
}
const decorFor=(world,i)=>{
  const map={garden:['🌷','🌻','🌱','🦋','🍄'],forest:['🌳','🌲','🍄','🦉','🌿'],
    desert:['🌵','⭐','🌙','🏜️','✨'],mountain:['⛰️','🌲','❄️','🦅','🪨'],
    myth:['📜','🏺','🕌','🐎','🕯️'],bazaar:['🔭','⚗️','📚','🧭','⚙️'],
    water:['🌊','⛵','🐚','🐬','🪸'],history:['🏛️','🏺','🗿','📜','🪙'],
    feelings:['💛','🌸','🫧','🌈','🕊️'],summit:['✨','🦅','🌌','⭐','🏔️']}[world.id];
  return map[i%map.length];
};

export function renderJourney(host,{state,start,go,toast}){
  const next=nextMissionIndex(state);
  const done=state.explorer.missionsDone;
  host.innerHTML=`
  <section class="journey-head">
    <div>
      <span class="overline">جادهٔ سیمرغ</span>
      <h1>نقشهٔ سفرِ تو</h1>
      <p>یک راهِ پیوسته، ده سرزمین، ${fa(WORLDS.length*MISSIONS_PER_WORLD)} مأموریت. هر نقطه یک قدمِ تازه است.</p>
    </div>
    <div class="journey-counter"><b>${fa(done.length)}</b><small>از ${fa(WORLDS.length*MISSIONS_PER_WORLD)}</small>
      <div class="journey-bar"><span style="width:${Math.round(done.length/(WORLDS.length*MISSIONS_PER_WORLD)*100)}%"></span></div>
    </div>
  </section>
  <div class="world-jump">${WORLDS.map((w,i)=>{
    const fin=done.filter(x=>Math.floor(x/MISSIONS_PER_WORLD)===i).length;
    const unlocked=fin>0||i===0||done.some(x=>Math.floor(x/MISSIONS_PER_WORLD)===i-1);
    return `<button class="wj ${unlocked?'':'locked'}" data-w="${i}">${w.emoji}<small>${w.name}</small></button>`;
  }).join('')}</div>
  <div class="journey-scroll">${WORLDS.map((w,wi)=>worldBlock(w,wi,state,next)).join('')}</div>`;

  host.querySelectorAll('.wj:not(.locked)').forEach(b=>b.onclick=()=>{
    sfx.tap();
    const t=host.querySelector(`.j-world[data-w="${b.dataset.w}"]`);
    t&&t.scrollIntoView({behavior:'smooth',block:'start'});
  });
  host.querySelectorAll('.m-node.open, .m-node.current, .m-node.done').forEach(b=>{
    b.onclick=()=>{
      sfx.pick();
      const idx=+b.dataset.i;
      if(b.classList.contains('done')){
        const m=missionAt(idx);
        toast(`این مأموریت را تمام کرده‌ای: «${m.title}» — یادگاری: ${m.reward}`);
        return;
      }
      start(idx);
    };
  });
  const cur=host.querySelector('.m-node.current')||host.querySelector('.m-node.open');
  if(cur) setTimeout(()=>cur.scrollIntoView({behavior:'smooth',block:'center'}),220);
}

function worldBlock(w,wi,state,next){
  const base=wi*MISSIONS_PER_WORLD;
  const pts=[];
  for(let i=0;i<MISSIONS_PER_WORLD;i++) pts.push(nodePoint(i,wi));
  const height=TOP*2+(MISSIONS_PER_WORLD-1)*SPACING;
  const fin=state.explorer.missionsDone.filter(x=>Math.floor(x/MISSIONS_PER_WORLD)===wi).length;
  const unlocked=fin>0||wi===0||state.explorer.missionsDone.some(x=>Math.floor(x/MISSIONS_PER_WORLD)===wi-1);
  const decors=Array.from({length:14},(_,i)=>{
    const side=i%2?86:12, top=6+i*6.6;
    return `<span class="j-decor" style="left:${side+(i%3)*2}%;top:${top}%;animation-delay:${(i*0.4).toFixed(1)}s">${decorFor(w,i)}</span>`;
  }).join('');
  const nodes=pts.map((p,i)=>{
    const idx=base+i, m=missionAt(idx), st=missionStatus(idx,state);
    const boss=(i+1)%6===0;
    const isNext=idx===next;
    return `<button class="m-node ${st} ${boss?'boss':''}" data-i="${idx}" style="left:${p.x}%;top:${p.y}px"
       ${st==='locked'?'disabled':''} aria-label="مأموریت ${fa(i+1)}: ${m.title}">
      <span class="m-num">${boss?'👑':fa(i+1)}</span>
      <span class="m-label">${m.title}</span>
      ${isNext?'<span class="m-ping"></span>':''}
      ${st==='done'?'<span class="m-check">✓</span>':''}
    </button>`;
  }).join('');
  const heroPt=pts[Math.max(0,Math.min(MISSIONS_PER_WORLD-1,next-base))];
  return `
  <section class="j-world" data-w="${wi}" style="--sky1:${w.sky[0]};--sky2:${w.sky[1]};--gr1:${w.ground[0]};--gr2:${w.ground[1]};--accent:${w.accent}">
    <div class="j-ambient" data-kind="${w.ambient}"></div>
    ${decors}
    <header class="j-head">
      <span class="j-emoji">${w.emoji}</span>
      <div>
        <small>سرزمینِ ${fa(wi+1)} از ${fa(WORLDS.length)}</small>
        <h2>${w.name}</h2>
        <p>${w.tagline} · ${w.weather}</p>
      </div>
      <div class="j-progress"><b>${fa(fin)}</b><small>/${fa(MISSIONS_PER_WORLD)}</small></div>
    </header>
    <div class="j-guide">${unlocked?`<span>🦊</span><p>${w.intro}</p>`:`<p>🔒 برای باز شدن، آخرین مأموریتِ سرزمینِ قبلی را تمام کن.</p>`}</div>
    <div class="j-road" style="height:${height}px">
      <svg viewBox="0 0 100 ${height}" preserveAspectRatio="none" class="j-svg">
        <path class="road-shadow" d="${pathThrough(pts)}" />
        <path class="road-main" d="${pathThrough(pts)}" />
        <path class="road-dash" d="${pathThrough(pts)}" />
      </svg>
      ${unlocked?nodes:`<div class="j-lock">🔒</div>`}
      ${unlocked?`<div class="j-hero" style="left:${heroPt.x}%;top:${heroPt.y}px"><span>🦊</span><i></i></div>`:''}
    </div>
    <footer class="j-foot">
      <span>🎁 ${fa(fin)} از ${fa(MISSIONS_PER_WORLD)} یادگاری</span>
      <span>👑 سه آزمونِ بزرگ در این سرزمین</span>
    </footer>
  </section>`;
}

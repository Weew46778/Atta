// باغ قصه‌ها ۳ — بازیکنِ مأموریت: ایستگاه‌ها، بازخورد، امتیاز و مراسمِ جایزه
import {MECHANICS} from './mechanics.js';
import {starsFor,STATION_KINDS,praise,MECHANIC_LABEL} from '../content/missions.js';
import {applySkills,recordStation,bumpStreak,levelFromXp,rankOf,xpForLevel,fa,clamp} from '../core/store.js';
import {confetti,sfx,sleep,escapeHtml} from '../core/audio.js';

const KIND_ICON={story:'📜',game:'🎮',puzzle:'🧩',real:'🌍',create:'🎨',reflect:'🪞'};

/**
 * اجرای یک سازوکارِ مستقل (بیرون از مأموریت) — مثلاً «نفسِ آرام» در زمانِ استراحت.
 * همان قراردادِ ctx را می‌سازد تا هیچ سازوکاری کدِ جداگانه نخواهد.
 */
export function runStandaloneMechanic(mechId,{host,state,narrator,params={},rounds=3,onDone}={}){
  const mech=MECHANICS[mechId];
  if(!mech||!host) return Promise.resolve(null);
  return new Promise(resolve=>{
    let settled=false;
    const ctx={
      host,st:{mech:mechId,kind:'reflect',difficulty:1,title:MECHANIC_LABEL[mechId]||''},
      p:params,rounds,idx:0,difficulty:1,state,
      say:async()=>{},sfx,
      narrate:(t,o)=>state.settings.autoSpeak?narrator.narrate(t,o):Promise.resolve({spoken:false}),
      listenFree:async()=>({ok:false,skipped:true}),
      startRecorder:()=>import('../core/audio.js').then(m=>m.startRecorder()),
      progress:()=>{},
      done:result=>{
        if(settled)return;settled=true;
        recordStation(state,{m:-1,s:-1,mech:mechId,tries:result.tries||0,hints:result.hints||0,
          rate:result.correctRate||0,millis:result.millis||0,stars:0,standalone:true,extra:result.extra});
        onDone&&onDone(result);resolve(result);
      }
    };
    try{mech(ctx);}catch(err){console.error(err);if(!settled){settled=true;resolve(null);}}
  });
}

export function openMission(root,{mission,state,narrator,sfxOn,onExit,onSaved,autoSolve=false}){
  let idx=0;
  const log=[];
  const layer=document.createElement('div');
  layer.className='mission-layer';
  layer.style.setProperty('--sky1',mission.sky[0]);
  layer.style.setProperty('--sky2',mission.sky[1]);
  layer.style.setProperty('--gr1',mission.ground[0]);
  layer.style.setProperty('--gr2',mission.ground[1]);
  layer.style.setProperty('--accent',mission.accent);
  root.appendChild(layer);
  document.body.classList.add('no-scroll');

  const shell=document.createElement('div');
  shell.className='mission-shell';
  shell.innerHTML=`
   <div class="mission-ambient" data-kind="${mission.ambient}"></div>
   <header class="mission-top">
     <button class="m-exit" title="خروج">✕</button>
     <div class="m-progress">${mission.stations.map((s,i)=>`<span class="m-dot" data-i="${i}">${KIND_ICON[s.kind]}</span>`).join('')}</div>
     <div class="m-meta"><b>${fa(mission.local)}</b><small>/${fa(24)}</small></div>
   </header>
   <div class="mission-title-row">
     <span class="m-world">${mission.worldEmoji} ${mission.worldName}</span>
     <h2>${mission.title}</h2>
     <p class="m-brief">${mission.brief}</p>
   </div>
   <div class="mission-body"><div class="station-host"></div></div>
   <div class="mission-guide">
     <div class="guide-face" data-mood="calm">🦊</div>
     <div class="guide-bubble"><p></p>
       <div class="guide-actions">
         <button class="gbtn" data-a="repeat">🔁 بشنو</button>
         <button class="gbtn" data-a="skip">⏭ رد شو</button>
       </div>
     </div>
   </div>`;
  layer.appendChild(shell);
  const host=shell.querySelector('.station-host');
  const bubble=shell.querySelector('.guide-bubble p');
  const face=shell.querySelector('.guide-face');
  const dots=[...shell.querySelectorAll('.m-dot')];

  const say=async(text,{mood='calm'}={})=>{
    bubble.textContent=text;face.dataset.mood=mood;
    if(state.settings.autoSpeak) await narrator.narrate(text);
  };
  const setMood=m=>{face.dataset.mood=m;};

  shell.querySelector('.m-exit').onclick=()=>{
    if(confirm('از این مأموریت خارج شوی؟ پیشرفتِ همین ایستگاه ذخیره می‌ماند.')){
      close();onExit&&onExit(log);
    }
  };
  shell.querySelector('[data-a="repeat"]').onclick=()=>narrator.narrate(bubble.textContent);
  shell.querySelector('[data-a="skip"]').onclick=()=>narrator.stop();

  function close(){
    narrator.stop();
    document.body.classList.remove('no-scroll');
    layer.remove();
  }

  async function intro(){
    dots.forEach(d=>d.classList.remove('active','done'));
    await say(mission.intro,{mood:'happy'});
  }

  async function runStation(i){
    idx=i;
    state.explorer.current={missionId:mission.index,station:i,log};
    onSaved&&onSaved();
    dots.forEach((d,j)=>{d.classList.toggle('done',j<i);d.classList.toggle('active',j===i);});
    const st=mission.stations[i];
    host.innerHTML='';
    const card=document.createElement('section');
    card.className='station-card kind-'+st.kind;
    card.innerHTML=`<header class="st-head"><span class="st-icon">${KIND_ICON[st.kind]}</span>
      <div><small>${STATION_KINDS.find(k=>k.id===st.kind).name}</small><h3>${escapeHtml(st.title)}</h3></div>
      <span class="st-num">ایستگاه ${fa(i+1)}</span></header>
      <div class="st-brief">${escapeHtml(st.brief||'')}</div>
      <div class="st-body"></div>`;
    host.appendChild(card);
    const body=card.querySelector('.st-body');
    await say(st.brief||st.title);

    const mech=MECHANICS[st.mech]||MECHANICS.quiz;
    await new Promise(resolve=>{
      const ctx={
        host:body,st,p:st.params||{},rounds:st.rounds||3,idx:mission.index,difficulty:st.difficulty||1,
        state,say:sfxOn?say:(async()=>{}),sfx:sfxOn?sfx:{hint(){},good(){},soft(){},great(){},tap(){},pick(){},
          bell(){},chime(){},drum(){},note(){},thud(){},breath(){},whoosh(){},level(){}},
        narrate:(t,o)=>state.settings.autoSpeak?narrator.narrate(t,o):Promise.resolve({spoken:false}),
        listenFree:async prompt=>await openListenDialog(layer,{prompt,narrator,state}),
        startRecorder:()=>import('../core/audio.js').then(m=>m.startRecorder()),
        progress:(k,n)=>{},
        done:result=>{
          const stars=starsFor({...result,finished:true});
          log.push({station:i,kind:st.kind,mech:st.mech,...result,stars});
          showStationReward(i,st,result,stars);
          resolve();
        }
      };
      if(autoSolve){setTimeout(()=>{if(host.isConnected)ctx.done({correctRate:0.9,tries:0,hints:0,millis:120,finished:true});},0);}
      try{mech(ctx);}catch(err){
        console.error(err);
        body.innerHTML=`<div class="fb soft"><span>💡</span><p>این بازی روی این دستگاه کامل اجرا نشد. می‌توانی به ایستگاه بعد بروی.</p></div>`;
        const nx=document.createElement('button');nx.className='btn primary';nx.textContent='ایستگاه بعد';
        nx.onclick=()=>ctx.done({correctRate:1,tries:0,hints:0,millis:0,finished:true});
        body.appendChild(nx);
      }
    });
  }

  function showStationReward(i,st,result,stars){
    const p=praise(result,st);
    const panel=document.createElement('div');
    panel.className='station-reward';
    panel.innerHTML=`<div class="sr-stars">${'★'.repeat(stars)}${'☆'.repeat(3-stars)}</div>
      <p class="sr-praise">${p}</p>
      <div class="sr-stats">
        <span>⭐ درست: ${fa(Math.round((result.correctRate||0)*100))}٪</span>
        <span>🔁 تلاش: ${fa(result.tries||0)}</span>
        <span>💡 راهنما: ${fa(result.hints||0)}</span>
      </div>
      <button class="btn primary big">${i<mission.stations.length-1?'ایستگاه بعد ⬅':'پایانِ مأموریت 🎉'}</button>`;
    host.innerHTML='';host.appendChild(panel);
    setMood(stars===3?'proud':stars>=2?'happy':'encourage');
    sfxOn&&sfx[stars===3?'great':stars>=2?'good':'soft']();
    narrator.narrate(p);
    const advance=()=>{
      if(i<mission.stations.length-1) runStation(i+1);
      else finish();
    };
    panel.querySelector('button').onclick=advance;
    if(autoSolve) setTimeout(advance,0);
  }

  async function finish(){
    const total=log.reduce((s,l)=>s+(l.stars||0),0);
    const gained=mission.xp+total*5;
    const before=levelFromXp(state.explorer.xp);
    state.explorer.xp+=gained;
    const after=levelFromXp(state.explorer.xp);
    if(!state.explorer.missionsDone.includes(mission.index)){
      state.explorer.missionsDone.push(mission.index);
      state.explorer.missionsDone.sort((a,b)=>a-b);
      state.explorer.collectibles.push({world:mission.worldId,local:mission.local,name:mission.reward,ts:Date.now()});
    }
    log.forEach(l=>{
      recordStation(state,{m:mission.index,s:l.station,mech:l.mech,tries:l.tries,hints:l.hints,
        rate:l.correctRate,millis:l.millis,stars:l.stars,extra:l.extra});
      applySkills(state,{id:l.mech},clamp(0.35+(l.correctRate||0)*0.65-(l.hints||0)*0.06,0.1,1));
    });
    bumpStreak(state);
    state.explorer.current=null;
    onSaved&&onSaved();

    host.innerHTML='';
    const panel=document.createElement('div');
    panel.className='mission-complete';
    panel.innerHTML=`
      <div class="mc-card">
        <span class="mc-ribbon">${mission.worldEmoji} ${mission.worldName}</span>
        <div class="mc-collectible"><div class="mc-art">🎁</div><h3>${mission.reward}</h3><small>یادگاریِ شمارهٔ ${fa(state.explorer.collectibles.length)} از ${fa(240)}</small></div>
        <div class="mc-stars">${'★'.repeat(Math.min(5,total))}</div>
        <div class="mc-xp">+${fa(gained)} برگِ تلاش</div>
        ${after>before?`<div class="mc-level">🎉 سطحِ تازه: ${fa(after)} — ${rankOf(after)}</div>`:''}
        <div class="mc-skill">مهارت‌های این مرحله رشد کرد</div>
        <div class="mc-actions">
          <button class="btn primary" data-a="next">مأموریت بعدی ⬅</button>
          <button class="btn ghost" data-a="map">برگشت به نقشه</button>
        </div>
      </div>`;
    host.appendChild(panel);
    sfxOn&&sfx.level();
    confetti(shell,120);
    setMood('proud');
    await narrator.narrate(`آفرین ${state.names.explorer}! مأموریتِ «${mission.title}» تمام شد. یادگاریِ «${mission.reward}» به مجموعه‌ات اضافه شد. ${after>before?`و به سطحِ ${after} رسیدی: ${rankOf(after)}!`:'هر قدمِ تو یک برگِ تلاش است.'}`);
    panel.querySelector('[data-a="map"]').onclick=()=>{close();onExit&&onExit(log);};
    panel.querySelector('[data-a="next"]').onclick=()=>{close();onExit&&onExit(log,{next:true});};
    if(autoSolve) setTimeout(()=>{close();onExit&&onExit(log);},0);
  }

  (async()=>{
    await intro();
    const resume=state.explorer.current&&state.explorer.current.missionId===mission.index?state.explorer.current.station:0;
    await runStation(resume);
  })();

  return {close};
}

/* گفت‌وگوی آزادِ شنیداری: کودک حرف می‌زند، یا می‌نویسد */
async function openListenDialog(layer,{prompt,narrator,state}){
  const {listenOne,canListen}=await import('../core/audio.js');
  return await new Promise(resolve=>{
    const box=document.createElement('div');
    box.className='listen-dialog';
    box.innerHTML=`<div class="ld-card">
      <p class="ld-prompt">${prompt}</p>
      <div class="ld-wave"><i></i><i></i><i></i><i></i><i></i></div>
      <div class="ld-out"></div>
      <div class="ld-actions">
        <button class="btn mic" data-a="mic">🎤 حرف بزن</button>
        <button class="btn ghost" data-a="type">✍ می‌نویسم</button>
        <button class="btn ghost" data-a="skip">بعداً</button>
      </div></div>`;
    layer.appendChild(box);
    narrator.narrate(prompt);
    const out=box.querySelector('.ld-out');
    const finish=txt=>{box.remove();resolve({ok:!!txt,text:txt||''});};
    box.querySelector('[data-a="mic"]').onclick=async()=>{
      out.innerHTML='<p class="ld-live">● گوش می‌دهم…</p>';
      const r=await listenOne({timeout:9000});
      if(r.ok){out.innerHTML=`<p>شنیدم: <b>${r.text}</b></p>`;
        const ok=document.createElement('button');ok.className='btn primary';ok.textContent='آفرین! ✅';
        ok.onclick=()=>finish(r.text);out.appendChild(ok);
      }else{
        out.innerHTML=`<p>صدایت را نشنیدم (${r.reason==='timeout'?'وقت تمام شد':r.reason==='denied'?'اجازهٔ میکروفن لازم است':'این دستگاه گوش ندارد'}). اشکالی ندارد؛ بنویس یا برای خانواده بگو.</p>`;
      }
    };
    box.querySelector('[data-a="type"]').onclick=()=>{
      out.innerHTML='';
      const ta=document.createElement('textarea');ta.placeholder='این‌جا بنویس…';out.appendChild(ta);
      const ok=document.createElement('button');ok.className='btn primary';ok.textContent='نوشتم ✅';
      ok.onclick=()=>finish(ta.value.trim()||'نوشته شد');
      out.appendChild(ok);
    };
    box.querySelector('[data-a="skip"]').onclick=()=>finish('');
  });
}

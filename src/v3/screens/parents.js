// باغ قصه‌ها ۳ — نسخه‌های خانواده: جوانه (۲–۳ سال)، مادر، پدر، گزارش و گالری
import {TODDLER_SESSIONS,TODDLER_SONGS,HOT_MOMENTS,WORKSHOPS,CALM_SESSIONS,COOP_GAMES,AWAY_DAYS,HOME_DAYS,PHONE_GAMES,FATHER_COACHING,MOTHER_DASHBOARD_TIPS} from '../content/family.js';
import {WORLDS,MISSIONS_PER_WORLD} from '../content/worlds.js';
import {SKILLS,fa,analysis,recommendations,weeklySummary,logEvent,todayISO,daysBetween} from '../core/store.js';
import {sfx,confetti,escapeHtml,sleep,startRecorder} from '../core/audio.js';
import {buildMission,MECHANIC_LABEL} from '../content/missions.js';

const h=html=>{const t=document.createElement('template');t.innerHTML=html.trim();return t.content.firstElementChild;};
const el=(tag,cls,txt)=>{const e=document.createElement(tag);if(cls)e.className=cls;if(txt!=null)e.textContent=txt;return e;};

/* =====================================================================
   نسخهٔ جوانه (۲–۳ سال) — گفتار، تکرارِ شمرده و فرصتِ پاسخ
   ===================================================================== */
export function renderToddler(root,{state,narrator,save,toast}){
  const done=state.toddler.sessionsDone||[];
  const next=state.toddler.current?.id||(done.length?done[done.length-1]+1:1);
  root.innerHTML=`
  <div class="toddler-bg"><div class="tb-sun"></div><div class="tb-cloud c1"></div><div class="tb-cloud c2"></div>
    <div class="tb-hill"></div><div class="tb-flowers">${'<span>🌸</span>'.repeat(8)}</div></div>
  <div class="toddler-wrap">
    <header class="toddler-top">
      <button class="toddler-face" id="tface">🐣</button>
      <div><b>${escapeHtml(state.names.toddler)} جان</b><small>امروز با هم حرف می‌زنیم</small></div>
      <button class="toddler-sound" id="tsound">${state.settings.sound?'🔊':'🔇'}</button>
    </header>
    <main id="tmain"></main>
  </div>`;
  root.querySelector('#tsound').onclick=()=>{state.settings.sound=!state.settings.sound;save();renderToddler(root,{state,narrator,save,toast});};
  root.querySelector('#tface').onclick=()=>{narrator.narrate(`سلام ${state.names.toddler} جان! بیا با هم بازی کنیم.`);};
  const main=root.querySelector('#tmain');
  if(state.toddler.current) return runSession(main,state.toddler.current,{state,narrator,save,toast,exit:()=>{state.toddler.current=null;save();renderToddler(root,{state,narrator,save,toast});}});
  main.innerHTML=`
    <section class="toddler-hero">
      <div class="th-anim"><span class="th-chick">🐣</span><span class="th-bubble" id="tbub">سلام! بیا با هم حرف بزنیم</span></div>
      <button class="toddler-big" id="start">▶ بازیِ امروز</button>
      <p class="toddler-note">۴۵ جلسهٔ کوتاه و گفتاری · هر جلسه ۵ تا ۸ دقیقه</p>
    </section>
    <section class="toddler-beds">
      ${[1,2,3,4,5].map(b=>{
        const list=TODDLER_SESSIONS.filter(s=>s.bed===b);
        const fin=list.filter(s=>done.includes(s.id)).length;
        return `<div class="bed ${fin===list.length?'full':''}">
          <span class="bed-emoji">${['🌷','🌻','🌿','🍎','🌟'][b-1]}</span>
          <b>بسترِ ${fa(b)}</b><small>${fa(fin)} از ${fa(list.length)}</small>
          <div class="bed-dots">${list.map(s=>`<i class="${done.includes(s.id)?'on':''}"></i>`).join('')}</div>
        </div>`;}).join('')}
    </section>
    <section class="toddler-sessions">
      ${TODDLER_SESSIONS.map(s=>`<button class="ts-card ${done.includes(s.id)?'done':''}" data-id="${s.id}">
        <span>${{repeat:'🗣',find:'👀',story:'📖',song:'🎵',feel:'💛',routine:'🧼'}[s.type]}</span>
        <b>${s.fam}</b><small>${{repeat:'بگو با من',find:'پیدا کن',story:'قصه',song:'آواز',feel:'احساسِ من',routine:'روتینِ شیرین'}[s.type]}</small>
      </button>`).join('')}
    </section>
    <section class="parent-note">
      <h3>👤 برای والد</h3>
      <p>این نسخه برای همراهیِ بزرگ‌تر ساخته شده. کنارِ کودک بنشینید، کلمه‌ها را شمرده تکرار کنید و سه ثانیه صبر کنید. اصلاحِ مستقیم نکنید؛ درستش را آرام تکرار کنید. اگر کودک خسته شد، جلسه را تمام کنید.</p>
      <ul>
        <li>هر جلسه فقط ۵ تا ۸ دقیقه است؛ کوتاهی، ویژگی است نه ضعف.</li>
        <li>اگر کودک اشاره کرد، نامِ شیء را شما بگویید.</li>
        <li>تلویزیونِ پس‌زمینه را خاموش کنید؛ گفت‌وگوی دوطرفه مهم است.</li>
        <li>اگر تا ۳ سالگی واژه‌های خیلی کم دارد یا به صدا پاسخ نمی‌دهد، با متخصصِ گفتاردرمانی مشورت کنید. این برنامه جایگزینِ ارزیابیِ تخصصی نیست.</li>
      </ul>
    </section>`;
  main.querySelector('#start').onclick=()=>{state.toddler.current=next;save();renderToddler(root,{state,narrator,save,toast});};
  main.querySelectorAll('.ts-card').forEach(b=>b.onclick=()=>{state.toddler.current=+b.dataset.id;save();renderToddler(root,{state,narrator,save,toast});});
}

function runSession(host,id,{state,narrator,save,toast,exit}){
  const s=TODDLER_SESSIONS.find(x=>x.id===id);
  const TYPE={repeat:'بگو با من',find:'پیدا کن',story:'قصهٔ ما',song:'آواز',feel:'احساسِ من',routine:'روتینِ شیرین'};
  host.innerHTML=`<div class="session-toddler">
    <header class="st-top"><button class="t-exit">✕</button><span>${TYPE[s.type]} · ${s.fam}</span><b>${fa(s.id)}</b></header>
    <div class="st-body" id="tbody"></div>
    <footer class="st-parent"><button class="tp-toggle">👤 راهنمای والد</button><div class="tp-body"><p>${s.tip}</p></div></footer>
  </div>`;
  const body=host.querySelector('#tbody');
  host.querySelector('.t-exit').onclick=()=>{narrator.stop();if(confirm('از این جلسه خارج شوی؟'))exit();};
  const tp=host.querySelector('.tp-toggle');
  tp.onclick=()=>host.querySelector('.tp-body').classList.toggle('open');
  (async()=>{
    if(s.type==='repeat'){
      for(const w of s.words){
        body.innerHTML=`<div class="word-stage"><span class="word-emoji">${w.e}</span><h2>${w.w}</h2><p>${w.s}</p></div>
          <div class="word-actions"><button class="toddler-big mic" id="say" disabled>🎤 تو بگو</button><button class="btn ghost" id="again">🔁 دوباره بشنو</button></div>`;
        const sayBtn=body.querySelector('#say'),againBtn=body.querySelector('#again');
        let ready=false;
        const speak=async()=>{
          ready=false;sayBtn.disabled=true;
          await narrator.narrate(`${w.w}. ${w.w}. ${w.w}. ${w.s}`);
          await sleep(1400);
          ready=true;sayBtn.disabled=false;   // تا این‌جا کودک فقط گوش می‌دهد
        };
        againBtn.onclick=speak;
        // دکمه از همان اول handler دارد؛ پیش‌تر ضربه‌های نخست بی‌پاسخ می‌ماند
        await new Promise(res=>{
          sayBtn.onclick=async()=>{
            if(!ready)return;
            sayBtn.disabled=true;againBtn.disabled=true;
            body.classList.add('listening');
            await narrator.narrate(`حالا تو بگو: ${w.w}`);
            const {listenOne}=await import('../core/audio.js');
            const r=await listenOne({timeout:6000});
            body.classList.remove('listening');
            const cheer=el('div','toddler-cheer');
            cheer.innerHTML=`<span>🎉</span><p>${r.ok&&r.text?`شنیدم: «${r.text}» — آفرین!`:'آفرین! صدای تو قشنگ بود.'}</p>`;
            body.appendChild(cheer);
            state.toddler.words[w.w]=(state.toddler.words[w.w]||0)+1;
            sfx.great();
            const nx=el('button','toddler-big','بعدی ⬅');
            nx.onclick=()=>{cheer.remove();res();};
            body.appendChild(nx);
          };
          speak();
        });
      }
      finishSession(host,body,{state,save,narrator,s,exit,text:'آفرین! امروز سه کلمهٔ تازه گفتی.'});
    } else if(s.type==='find'){
      // هر دور دقیقاً با «بعدی» جلو می‌رود؛ پیش‌تر اینجا به یک تابعِ بیرون از دامنه
      // اشاره می‌شد و کودک شصت ثانیه منتظرِ بی‌دلیل می‌ماند.
      for(let r=0;r<3;r++){
        const target=s.words[r%s.words.length];
        const others=s.words.filter(w=>w!==target);
        const pool=[target,...others].slice(0,3);
        body.innerHTML=`<p class="find-q">👂 «<b>${target.w}</b>» را پیدا کن</p><div class="find-grid"></div>`;
        const grid=body.querySelector('.find-grid');
        await new Promise(res=>{
          pool.forEach(w=>{
            const b=el('button','find-card',w.e);
            b.onclick=()=>{
              const ok=w===target;
              b.classList.add(ok?'right':'wrong');
              grid.querySelectorAll('.find-card').forEach(x=>x.disabled=true);
              if(ok){sfx.great();const c=el('div','toddler-cheer');c.innerHTML='<span>🎉</span><p>آفرین! درست بود.</p>';body.appendChild(c);
                const nx=el('button','toddler-big','بعدی');nx.onclick=()=>{c.remove();res();};body.appendChild(nx);}
              else{narrator.narrate(`نه، این ${w.w} است. ${target.w} این یکی است.`);}
            };
            grid.appendChild(b);
          });
          narrator.narrate(`${target.w} را پیدا کن.`);
        });
      }
      finishSession(host,body,{state,save,narrator,s,exit,text:'آفرین! گوشِ تیزی داری.'});
    } else if(s.type==='story'){
      const st=s.story||{t:'قصهٔ امروز',lines:['یکی بود، یکی نبود.','یک بچهٔ مهربان بود.','هر روز لبخند می‌زد.','و شبِ خوبی داشت.']};
      body.innerHTML=`<div class="tstory"><h3>${st.t}</h3><div class="tstory-anim"><span>🐰</span></div><p id="tline"></p><button class="toddler-big" id="tnext">بعدی ⬅</button></div>`;
      for(const line of st.lines){
        const b=body.querySelector('#tnext');
        await new Promise(res=>{
          b.onclick=()=>{b.onclick=null;res();};
          body.querySelector('#tline').textContent=line;
          narrator.narrate(line);
        });
      }
      await narrator.narrate('قصه تمام شد! آفرین که گوش دادی.');
      finishSession(host,body,{state,save,narrator,s,exit,text:'قصه را خوب گوش دادی.'});
    } else if(s.type==='song'){
      body.innerHTML=`<div class="tsong"><h3>🎵 ${s.song.title}</h3><div class="tsong-lines">${s.song.lines.map(l=>`<p>${l}</p>`).join('')}</div>
        <button class="toddler-big drum" id="drum">🥁 با من بخوان</button></div>`;
      body.querySelector('#drum').onclick=async()=>{
        for(const l of s.song.lines){sfx.drum(1);await narrator.narrate(l);await sleep(500);}
        const c=el('div','toddler-cheer');c.innerHTML='<span>🎉</span><p>صدای تو قشنگ بود!</p>';body.appendChild(c);
      };
      await narrator.narrate(`آوازِ امروز: ${s.song.title}. با من بخوان.`);
      const fin=el('button','btn primary','تمام ✅');
      fin.onclick=()=>finishSession(host,body,{state,save,narrator,s,exit,text:'آفرین! چه صدای قشنگی.'});
      body.appendChild(fin);
    } else if(s.type==='feel'){
      const faces=[{e:'😄',n:'شاد'},{e:'😢',n:'غمگین'},{e:'😠',n:'عصبانی'},{e:'😴',n:'خوابالو'}];
      for(const f of faces){
        body.innerHTML=`<div class="feel-stage"><span class="feel-face">${f.e}</span><h2>${f.n}</h2></div>
          <div class="feel-ask"><p>تو کی ${f.n} می‌شوی؟</p><button class="toddler-big mic" id="f">🎤 بگو</button></div>`;
        await new Promise(res=>{
          const btn=body.querySelector('#f');
          btn.onclick=async()=>{
            btn.disabled=true;
            const {listenOne}=await import('../core/audio.js');
            await listenOne({timeout:5000});
            const c=el('div','toddler-cheer');c.innerHTML=`<span>💛</span><p>ممنون که گفتی!</p>`;body.appendChild(c);
            const nx=el('button','toddler-big','بعدی');nx.onclick=()=>{c.remove();res();};body.appendChild(nx);
          };
          narrator.narrate(`این صورت ${f.n} است. ${f.n}. تو کی ${f.n} می‌شوی؟`);
        });
      }
      finishSession(host,body,{state,save,narrator,s,exit,text:'حالت را گفتی؛ آفرین.'});
    } else {
      const steps=[['🧼','دست‌ها را بیست ثانیه با صابون بشوی'],['🦷','دندان‌ها را مسواک بزن'],['👕','لباسِ خواب را بپوش'],['📖','یک قصهٔ کوتاه بشنو'],['🛏','بخواب و ستاره‌ها را ببین']];
      body.innerHTML=`<p class="routine-title">روتینِ شیرینِ امشب</p><div class="routine-list">${steps.map(([e,t])=>`<label><input type="checkbox"><span>${e}</span><p>${t}</p></label>`).join('')}</div>`;
      await narrator.narrate('روتینِ شیرینِ امشب. هر کاری را که انجام دادی، تیک بزن.');
      const fin=el('button','toddler-big','همه را انجام دادم ✅');
      fin.onclick=()=>{sfx.great();finishSession(host,body,{state,save,narrator,s,exit,text:'آفرین! امشب خوب خوابیدی.'});};
      body.appendChild(fin);
    }
  })();
}
function finishSession(host,body,{state,save,narrator,s,exit,text}){
  if(!(state.toddler.sessionsDone||[]).includes(s.id)){
    state.toddler.sessionsDone=[...(state.toddler.sessionsDone||[]),s.id];
    logEvent(state,'toddler','session','جلسهٔ '+s.id+' — '+s.fam);
  }
  state.toddler.current=null;save();
  body.innerHTML=`<div class="toddler-finish"><span class="tf-sticker">🌟</span><h2>${text}</h2>
    <p>برچسبِ امروز: ${s.fam}</p>
    <button class="toddler-big" id="ok">باشه 🎉</button></div>`;
  confetti(host,60,['#ffd166','#ff6b9d','#5ec26a','#4ea8de']);
  sfx.level();
  narrator.narrate(text+' برچسبِ امروز به دفترِ تو اضافه شد.');
  body.querySelector('#ok').onclick=exit;
}

/* =====================================================================
   گزارش و داشبورد (مشترک)
   ===================================================================== */
export function renderReport(host,{state,save,toast,say}){
  const a=analysis(state),recs=recommendations(state),wk=weeklySummary(state);
  const pts=SKILLS.map((s,i)=>{
    const v=(state.explorer.skills[s.id]||0)/100;
    const ang=(Math.PI*2*i)/SKILLS.length-Math.PI/2;
    return `${50+Math.cos(ang)*38*v} ${50+Math.sin(ang)*38*v}`;
  }).join(',');
  const axis=SKILLS.map((s,i)=>{
    const ang=(Math.PI*2*i)/SKILLS.length-Math.PI/2;
    const x=50+Math.cos(ang)*46,y=50+Math.sin(ang)*46;
    const lx=50+Math.cos(ang)*56,ly=50+Math.sin(ang)*56;
    return `<line x1="50" y1="50" x2="${x}" y2="${y}" class="axis"/>
      <text x="${lx}" y="${ly}" text-anchor="middle" class="axis-t">${s.icon}</text>`;
  }).join('');
  host.innerHTML=`
  <section class="panel">
    <header class="panel-head"><h2>نقشهٔ مهارت‌ها</h2><small>این نمره‌ها رشدِ تلاش را نشان می‌دهند، نه هوش</small></header>
    <div class="radar-wrap">
      <svg viewBox="0 0 100 100" class="radar">
        ${[0.25,0.5,0.75,1].map(r=>`<polygon class="ring" points="${SKILLS.map((_,i)=>{const ang=(Math.PI*2*i)/SKILLS.length-Math.PI/2;return `${50+Math.cos(ang)*38*r} ${50+Math.sin(ang)*38*r}`;}).join(',')}"/>`).join('')}
        ${axis}
        <polygon class="radar-fill" points="${pts}"/>
      </svg>
      <ul class="radar-list">${SKILLS.map(s=>`<li><span>${s.icon}</span><b>${s.name}</b><i style="width:${state.explorer.skills[s.id]||0}%"></i><small>${fa(state.explorer.skills[s.id]||0)}</small></li>`).join('')}</ul>
    </div>
  </section>
  <section class="panel">
    <header class="panel-head"><h2>این هفته</h2></header>
    <div class="week-grid">
      <div><b>${fa(wk.stations)}</b><small>ایستگاهِ انجام‌شده</small></div>
      <div><b>${fa(wk.missions)}</b><small>مأموریت</small></div>
      <div><b>${fa(wk.strengths[0]?.value||0)}</b><small>قوی‌ترین مهارت</small></div>
      <div><b>${fa(wk.interests.length)}</b><small>علاقهٔ شناسایی‌شده</small></div>
    </div>
    ${wk.interests.length?`<div class="chips">${wk.interests.map(i=>`<span class="chip">${i.tag} ×${fa(i.count)}</span>`).join('')}</div>`:''}
  </section>
  <section class="panel">
    <header class="panel-head"><h2>توصیه‌ها</h2><small>پیشنهادِ قاعده‌محور، نه تشخیصِ تخصصی</small></header>
    <div class="rec-list">${recs.map(r=>`<article><span>${r.icon}</span><div><b>${r.title}</b>${r.body?`<p>${r.body}</p>`:''}</div></article>`).join('')||'<p class="muted">هنوز دادهٔ کافی نیست؛ بعد از چند مأموریت، توصیه‌ها ظاهر می‌شوند.</p>'}</div>
  </section>
  <section class="panel">
    <header class="panel-head"><h2>یادگاری‌ها</h2><small>${fa(state.explorer.collectibles.length)} از ${fa(240)}</small></header>
    <div class="collect-grid">${state.explorer.collectibles.slice(-40).reverse().map(c=>`<span class="collect" title="${escapeHtml(c.name)}">🎁<small>${escapeHtml(c.name)}</small></span>`).join('')||'<p class="muted">هنوز یادگاری نداری.</p>'}</div>
  </section>`;
}

export function renderGallery(host,{state,toast}){
  host.innerHTML=`
  <section class="panel"><header class="panel-head"><h2>نقاشی‌ها و ساخته‌ها</h2></header>
    <div class="gallery-grid">${(state.explorer.gallery||[]).map(g=>`<figure><img src="${g.url}" alt="${escapeHtml(g.title)}"><figcaption>${escapeHtml(g.title)}</figcaption></figure>`).join('')||'<p class="muted">در ایستگاهِ «نقاشی» می‌توانی آثار را این‌جا ذخیره کنی.</p>'}</div>
  </section>
  <section class="panel"><header class="panel-head"><h2>جمله‌های «امروز من…»</h2></header>
    <div class="pride-list">${(state.explorer.pride||[]).slice().reverse().map(p=>`<article><p>${escapeHtml(p.text)}</p><small>${new Date(p.ts).toLocaleDateString('fa-IR')}</small></article>`).join('')||'<p class="muted">هنوز جمله‌ای ثبت نشده.</p>'}</div>
  </section>`;
}

/* =====================================================================
   نسخهٔ مادر
   ===================================================================== */
export function renderMother(root,{state,narrator,save,toast,say,go}){
  let tab='home';
  root.innerHTML=`
  <div class="parent-shell">
    <aside class="p-side">
      <div class="p-brand"><span>🌷</span><div><b>همراهِ مادر</b><small>آرام، پیوسته، کافی</small></div></div>
      <nav class="p-nav">
        ${[['home','🏠','خانه'],['hot','🔥','لحظهٔ داغ'],['work','📚','کارگاه‌ها'],['calm','🌙','آرامشِ من'],['coop','👨‍👩‍👧','بازیِ دو بچه'],['kids','📊','داشبوردِ بچه‌ها'],['plan','🗓','برنامهٔ خانواده']].map(([id,ic,t])=>
        `<button data-t="${id}" class="${tab===id?'on':''}"><span>${ic}</span>${t}</button>`).join('')}
      </nav>
      <div class="p-foot"><small>این محتوا راهنمای عمومی است، نه جایگزینِ مشاورهٔ تخصصی.</small></div>
    </aside>
    <main class="p-main" id="pmain"></main>
  </div>`;
  const main=root.querySelector('#pmain');
  root.querySelectorAll('.p-nav button').forEach(b=>b.onclick=()=>{
    tab=b.dataset.t;sfx.tap();
    root.querySelectorAll('.p-nav button').forEach(x=>x.classList.toggle('on',x===b));
    paint();
  });
  function paint(){
    if(tab==='home') homeTab();
    else if(tab==='hot') hotTab();
    else if(tab==='work') workTab();
    else if(tab==='calm') calmTab();
    else if(tab==='coop') coopTab();
    else if(tab==='kids') kidsTab();
    else planTab();
  }
  function homeTab(){
    const done=state.mother.lessonsDone||[];
    const kidDone=state.explorer.missionsDone.length;
    main.innerHTML=`
    <section class="p-hero"><div><span class="overline">امروز</span><h1>سلام ${escapeHtml(state.names.mother)} 🌷</h1>
      <p>کافیِ خوب، بهتر از کاملِ خسته است. امروز یک قدمِ کوچک بردار.</p></div>
      <div class="p-hero-art">🌷</div></section>
    <section class="p-cards">
      <article><span>🧒</span><div><b>${escapeHtml(state.names.explorer)}</b><small>${fa(kidDone)} مأموریت · زنجیرهٔ ${fa(state.explorer.streak.days)} روز</small></div></article>
      <article><span>🐣</span><div><b>${escapeHtml(state.names.toddler)}</b><small>${fa((state.toddler.sessionsDone||[]).length)} جلسهٔ گفتاری</small></div></article>
      <article><span>📚</span><div><b>کارگاه‌ها</b><small>${fa(done.length)} از ${fa(WORKSHOPS.length)} انجام شده</small></div></article>
      <article><span>🧭</span><div><b>چرخهٔ پدر</b><small>${fatherPhase(state)}</small></div></article>
    </section>
    <section class="panel"><header class="panel-head"><h2>اگر الان شرایط سخت است</h2></header>
      <p class="hint">«لحظهٔ داغ» را باز کن، موقعیت را انتخاب کن. سه جملهٔ آماده، یک پروتکلِ ۶۰ ثانیه‌ای و یک قدمِ بعدی می‌گیری.</p>
      <button class="btn primary" id="to-hot">🔥 رفتن به لحظهٔ داغ</button></section>
    <section class="panel"><header class="panel-head"><h2>نکتهٔ امروز</h2></header>
      <p class="big-note">${MOTHER_DASHBOARD_TIPS[new Date().getDate()%MOTHER_DASHBOARD_TIPS.length]}</p></section>`;
    main.querySelector('#to-hot').onclick=()=>{tab='hot';root.querySelectorAll('.p-nav button').forEach(x=>x.classList.toggle('on',x.dataset.t==='hot'));paint();};
  }
  function hotTab(){
    main.innerHTML=`<section class="panel"><header class="panel-head"><h2>کارتِ لحظهٔ داغ</h2><small>اول آرامشِ خودت، بعدِ کودک</small></header>
      <div class="hot-grid">${HOT_MOMENTS.map(h2=>`<button class="hot-card" data-id="${h2.id}"><span>🔥</span><b>${h2.t}</b><small>جمله‌های آماده + پروتکل</small></button>`).join('')}</div></section>`;
    main.querySelectorAll('.hot-card').forEach(b=>b.onclick=()=>openHot(b.dataset.id));
  }
  function openHot(id){
    const hm=HOT_MOMENTS.find(x=>x.id===id);
    const layer=h(`<div class="p-layer"><div class="p-card">
      <button class="p-close">✕</button>
      <h2>${hm.t}</h2>
      <section class="hm-what"><h3>چه اتفاقی در مغزِ کودک می‌افتد؟</h3><p>${hm.what}</p></section>
      <section class="hm-say"><h3>🗣 این‌ها را بگو <button class="mini" id="say-all">🔊 بشنو</button></h3>
        ${hm.say.map(s=>`<blockquote>«${s}»</blockquote>`).join('')}</section>
      <section class="hm-avoid"><h3>🚫 این‌ها را نکن</h3><ul>${hm.avoid.map(a=>`<li>${a}</li>`).join('')}</ul></section>
      <section class="hm-proto"><h3>⏱ پروتکلِ ۶۰ ثانیه</h3><ol>${hm.protocol.map(p=>`<li>${p.replace(/^\d\)\s*/,'')}</li>`).join('')}</ol></section>
      <section class="hm-after"><h3>بعدش</h3><p>${hm.after}</p></section>
      <button class="btn primary" id="done-hm">استفاده کردم ✅</button>
    </div></div>`);
    document.body.appendChild(layer);
    narrator.narrate(`${hm.t}. ${hm.what}`);
    layer.querySelector('.p-close').onclick=()=>{narrator.stop();layer.remove();};
    layer.querySelector('#say-all').onclick=()=>narrator.narrate(hm.say.join(' '));
    layer.querySelector('#done-hm').onclick=()=>{
      state.mother.hotMoments=[...(state.mother.hotMoments||[]),{id,ts:Date.now()}];
      logEvent(state,'mother','hot',hm.t);save();layer.remove();toast('ثبت شد. یادت باشد: تو هم انسانی و ممکن است اشتباه کنی؛ جبرانش مهم است.');
    };
  }
  function workTab(){
    main.innerHTML=`<section class="panel"><header class="panel-head"><h2>کارگاه‌های کوتاه</h2><small>هر کدام ۵ تا ۱۵ دقیقه</small></header>
      <div class="work-grid">${WORKSHOPS.map(w=>`<button class="work-card ${(state.mother.lessonsDone||[]).includes(w.id)?'done':''}" data-id="${w.id}">
        <b>${w.t}</b><small>${fa(w.min)} دقیقه</small><span>${(state.mother.lessonsDone||[]).includes(w.id)?'✅':''}</span></button>`).join('')}</div></section>`;
    main.querySelectorAll('.work-card').forEach(b=>b.onclick=()=>{
      const w=WORKSHOPS.find(x=>x.id===b.dataset.id);
      const layer=h(`<div class="p-layer"><div class="p-card"><button class="p-close">✕</button>
        <h2>${w.t}</h2><small class="overline">${fa(w.min)} دقیقه</small>
        <p class="work-body">${w.body}</p>
        <div class="work-task"><b>تمرینِ امروز:</b><p>${w.task}</p></div>
        <button class="btn ghost" id="say-w">🔊 بشنو</button>
        <button class="btn primary" id="done-w">انجام دادم ✅</button></div></div>`);
      document.body.appendChild(layer);
      layer.querySelector('.p-close').onclick=()=>layer.remove();
      layer.querySelector('#say-w').onclick=()=>narrator.narrate(`${w.t}. ${w.body} تمرینِ امروز: ${w.task}`);
      layer.querySelector('#done-w').onclick=()=>{
        state.mother.lessonsDone=[...(state.mother.lessonsDone||[]),w.id];
        logEvent(state,'mother','workshop',w.t);save();layer.remove();paint();toast('آفرین. یک قدمِ کوچک، یک تغییرِ بزرگ.');
      };
    });
  }
  function calmTab(){
    main.innerHTML=`<section class="panel"><header class="panel-head"><h2>آرامشِ مادر</h2><small>ظرفِ پر، چیزی برای بخشیدن دارد</small></header>
      <div class="calm-stage"><div class="calm-circle"><span>نفس</span></div><div class="calm-text"></div></div>
      <div class="calm-list">${CALM_SESSIONS.map(c=>`<button class="calm-card" data-id="${c.id}"><b>${c.t}</b><small>${fa(c.min)} دقیقه</small></button>`).join('')}</div></section>`;
    main.querySelectorAll('.calm-card').forEach(b=>b.onclick=async()=>{
      const c=CALM_SESSIONS.find(x=>x.id===b.dataset.id);
      const circle=main.querySelector('.calm-circle'),txt=main.querySelector('.calm-text');
      circle.classList.add('on');
      txt.textContent='در حال پخش…';
      state.mother.calmDone=[...(state.mother.calmDone||[]),{id:c.id,ts:Date.now()}];save();
      await narrator.narrate(c.script);
      circle.classList.remove('on');txt.textContent='آفرین. این چهار دقیقه، سرمایه‌گذاری در کلِ خانواده بود.';
    });
  }
  function coopTab(){
    main.innerHTML=`<section class="panel"><header class="panel-head"><h2>بازیِ مشترکِ دو بچه</h2><small>طراحی‌شده برای ${fa(8)}–${fa(10)} و ${fa(2)}–${fa(3)} سال با هم</small></header>
      <div class="coop-grid">${COOP_GAMES.map(g=>`<article class="coop-card">
        <h3>${g.t}</h3><span class="coop-time">⏱ ${fa(g.time)} دقیقه</span>
        <p><b>نقش‌ها:</b> ${g.roles}</p><p>${g.how}</p>
        <p class="coop-tip">💡 ${g.tip}</p></article>`).join('')}</div></section>
      <section class="panel"><header class="panel-head"><h2>بازی‌های تلفنی با پدر</h2></header>
      <div class="chips">${PHONE_GAMES.map(g=>`<span class="chip">${g.t}</span>`).join('')}</div></section>`;
  }
  function kidsTab(){
    const a=analysis(state);
    main.innerHTML=`<section class="panel"><header class="panel-head"><h2>داشبوردِ ${escapeHtml(state.names.explorer)}</h2></header>
      <div class="dash-grid">
        <div><b>${fa(state.explorer.missionsDone.length)}</b><small>مأموریت</small></div>
        <div><b>${fa(state.explorer.streak.days)}</b><small>روزِ پیوسته</small></div>
        <div><b>${fa(Math.round(a.avgMillis/1000)||0)}</b><small>ثانیه/ایستگاه</small></div>
        <div><b>${fa(Math.round(a.hintRate*100))}٪</b><small>با راهنما</small></div>
      </div>
      <ul class="dash-skills">${a.strongest.map(([id,v])=>`<li>🌟 <b>${SKILLS.find(s=>s.id===id).name}</b> ${fa(Math.round(v))}</li>`).join('')}
        ${a.weakest.map(([id,v])=>`<li>🌱 <b>${SKILLS.find(s=>s.id===id).name}</b> ${fa(Math.round(v))}</li>`).join('')}</ul>
      <div class="rec-list">${recommendations(state).map(r=>`<article><span>${r.icon}</span><div><b>${r.title}</b><p>${r.body||''}</p></div></article>`).join('')}</div>
    </section>
    <section class="panel"><header class="panel-head"><h2>داشبوردِ ${escapeHtml(state.names.toddler)}</h2></header>
      <div class="dash-grid">
        <div><b>${fa((state.toddler.sessionsDone||[]).length)}</b><small>جلسه</small></div>
        <div><b>${fa(Object.keys(state.toddler.words||{}).length)}</b><small>واژهٔ تمرین‌شده</small></div>
        <div><b>${fa(Object.values(state.toddler.words||{}).reduce((a2,b)=>a2+b,0))}</b><small>کلِ تکرارها</small></div>
      </div>
      <div class="chips">${Object.entries(state.toddler.words||{}).slice(-12).map(([w,n])=>`<span class="chip">${w} ×${fa(n)}</span>`).join('')||'<span class="muted">هنوز واژه‌ای ثبت نشده.</span>'}</div>
    </section>
    <section class="panel"><header class="panel-head"><h2>رویدادهای خانواده</h2></header>
      <ul class="event-list">${state.log.slice(-25).reverse().map(e=>`<li><small>${new Date(e.ts).toLocaleDateString('fa-IR')}</small><span>${e.role==='mother'?'🌷':e.role==='father'?'🧭':e.role==='toddler'?'🐣':'🦊'}</span><p>${escapeHtml(e.text)}</p></li>`).join('')||'<p class="muted">رویدادی ثبت نشده.</p>'}</ul>
    </section>`;
  }
  function planTab(){
    const days=['شنبه','یک‌شنبه','دوشنبه','سه‌شنبه','چهارشنبه','پنج‌شنبه','جمعه'];
    main.innerHTML=`<section class="panel"><header class="panel-head"><h2>برنامهٔ هفته</h2><small>ثابت، ساده و قابلِ انجام</small></header>
      <div class="plan-grid">${days.map((d,i)=>`<div class="plan-day"><b>${d}</b>
        <ul>
          <li>🌅 روتینِ صبحِ ثابت</li>
          <li>🌳 ${i%2?'بازیِ حیاطی':'کاردستی'}</li>
          <li>📖 قصهٔ شبِ ${i===4?'خانوادگی':'کوتاه'}</li>
          <li>🛏 خوابِ ساعتِ ${fa(21)}:${fa(0)}</li>
        </ul></div>`).join('')}</div></section>
      <section class="panel"><header class="panel-head"><h2>چک‌لیستِ روزانه</h2></header>
      ${['۲۰ دقیقه بازیِ اختصاصی با هر بچه','یک وعدهٔ غذاییِ مشترک بدونِ صفحه','روتینِ خوابِ ثابت','یک جملهٔ قدردانی به همسر','ده دقیقه آرامشِ خودم'].map(t=>
        `<label class="check-row"><input type="checkbox" data-k="${t}"><span>${t}</span></label>`).join('')}</section>`;
    main.querySelectorAll('.check-row input').forEach(c=>{
      c.checked=!!(state.mother.plans||{})[todayISO()+'|'+c.dataset.k];
      c.onchange=()=>{
        state.mother.plans=state.mother.plans||{};
        state.mother.plans[todayISO()+'|'+c.dataset.k]=c.checked;save();
      };
    });
  }
  paint();
}

/* =====================================================================
   نسخهٔ پدر — چرخهٔ ۱۰ روز دور / ۵ روز خانه
   ===================================================================== */
export function fatherPhase(state){
  if(!state.cycleStart) return 'تاریخِ شروعِ چرخه را تنظیم کن';
  const d=daysBetween(state.cycleStart,todayISO());
  const pos=((d%15)+15)%15;
  return pos<10?`روزِ دوریِ ${fa(pos+1)} از ${fa(10)}`:`روزِ حضورِ ${fa(pos-9)} از ${fa(5)}`;
}
function fatherPos(state){
  const d=state.cycleStart?daysBetween(state.cycleStart,todayISO()):0;
  return ((d%15)+15)%15;
}
export function renderFather(root,{state,narrator,save,toast,go}){
  let tab='today';
  root.innerHTML=`
  <div class="parent-shell father">
    <aside class="p-side">
      <div class="p-brand"><span>🧭</span><div><b>همراهِ پدر</b><small>${fatherPhase(state)}</small></div></div>
      <nav class="p-nav">
        ${[['today','📍','امروز'],['away','✈️','روزهای دوری'],['home','🏠','روزهای حضور'],['games','📞','بازیِ تلفنی'],['coach','🧠','مربی‌گری'],['kids','📊','داشبورد']].map(([id,ic,t])=>
        `<button data-t="${id}" class="${tab===id?'on':''}"><span>${ic}</span>${t}</button>`).join('')}
      </nav>
      <div class="p-foot"><small>حضورِ باکیفیتِ پنج روز، از حضورِ خستهٔ پانزده روز ارزشمندتر است.</small></div>
    </aside>
    <main class="p-main" id="pmain"></main>
  </div>`;
  const main=root.querySelector('#pmain');
  root.querySelectorAll('.p-nav button').forEach(b=>b.onclick=()=>{
    tab=b.dataset.t;
    root.querySelectorAll('.p-nav button').forEach(x=>x.classList.toggle('on',x===b));paint();
  });
  function paint(){
    const pos=fatherPos(state), away=pos<10;
    if(tab==='today') todayTab(pos,away);
    else if(tab==='away') awayTab(pos);
    else if(tab==='home') homeTab(pos);
    else if(tab==='games') gamesTab();
    else if(tab==='coach') coachTab();
    else kidsTab();
  }
  function cycleBar(pos){
    return `<div class="cycle-strip">${Array.from({length:15},(_,i)=>
      `<span class="${i<10?'away':'home'} ${i===pos?'now':''}">${i<10?fa(i+1):fa(i-9)}</span>`).join('')}</div>
      <p class="cycle-legend"><i class="away"></i> ده روز سرِ کار <i class="home"></i> پنج روز خانه</p>`;
  }
  function todayTab(pos,away){
    const d=away?AWAY_DAYS[pos]:HOME_DAYS[pos-10];
    main.innerHTML=`
    <section class="p-hero"><div><span class="overline">${fatherPhase(state)}</span>
      <h1>${away?'امروز دوری، ولی نزدیک':'امروز خانه‌ای'}</h1>
      <p>${away?d.focus:d.goal}</p></div><div class="p-hero-art">${away?'✈️':'🏠'}</div></section>
    <section class="panel">${cycleBar(pos)}</section>
    ${away?`<section class="panel"><header class="panel-head"><h2>پیامِ امروزِ تو</h2></header>
      <blockquote class="msg-script">${d.message}</blockquote>
      <div class="btn-row">
        <button class="btn ghost" id="say-msg">🔊 متن را بشنو</button>
        <button class="btn primary mic" id="rec-msg">🎤 پیامِ صوتی بگیر</button>
      </div>
      <div class="rec-out"></div>
      <p class="hint">وظیفهٔ امروزِ تو: ${d.task}</p>
      <p class="hint">کارِ کودک: ${d.kidTask}</p>
      <button class="btn ghost" id="sent">فرستادم ✅</button></section>`
    :`<section class="panel"><header class="panel-head"><h2>برنامهٔ ${d.title}</h2><small>${d.goal}</small></header>
      <ul class="home-plan">${d.plan.map(p=>`<li>${p}</li>`).join('')}</ul></section>`}
    <section class="panel"><header class="panel-head"><h2>سه قانونِ طلاییِ امروز</h2></header>
      <ul class="golden">${away?['یک پیامِ صوتیِ روزانه، حتی یک دقیقه','از درس و نمره نپرس؛ از روزش بپرس','وعدهٔ بی‌زمان نده؛ تقویم را نشان بده']:
      ['اول اتصال، بعد آموزش','بدونِ بازجویی از ده روز','هر کودک، یک زمانِ اختصاصی']}</ul></section>`;
    if(away){
      main.querySelector('#say-msg').onclick=()=>narrator.narrate(d.message);
      main.querySelector('#rec-msg').onclick=async()=>{
        const out=main.querySelector('.rec-out');out.innerHTML='<p class="rec-live">● در حال ضبط… دوباره بزن تا تمام شود.</p>';
        const r=await startRecorder();
        if(!r.ok){out.innerHTML='<p>ضبط روی این دستگاه ممکن نیست. متن را با صدای خودت از طریقِ پیام‌رسان بفرست.</p>';return;}
        main.querySelector('#rec-msg').onclick=async()=>{
          const f=await r.stop();out.innerHTML=`<audio controls src="${f.url}"></audio><p>آمادهٔ ارسال. این فایل روی دستگاه می‌ماند و جایی آپلود نمی‌شود.</p>`;
        };
      };
      main.querySelector('#sent').onclick=()=>{
        state.father.messages=[...(state.father.messages||[]),{day:pos+1,ts:Date.now()}];
        logEvent(state,'father','message','پیامِ روزِ '+fa(pos+1));save();toast('ثبت شد. صدای تو برای بچه‌ها امنیت می‌سازد.');
      };
    }
  }
  function awayTab(pos){
    main.innerHTML=`<section class="panel"><header class="panel-head"><h2>ده روزِ دوری</h2><small>هر روز یک کارِ کوچک و مشخص</small></header>
      <div class="away-list">${AWAY_DAYS.map((d,i)=>`<article class="away-card ${i===pos?'now':''} ${(state.father.messages||[]).some(m=>m.day===d.day)?'done':''}">
        <b>روزِ ${fa(d.day)}</b><h3>${d.focus}</h3><p><b>تو:</b> ${d.task}</p><p><b>کودک:</b> ${d.kidTask}</p></article>`).join('')}</div></section>`;
  }
  function homeTab(pos){
    main.innerHTML=`<section class="panel"><header class="panel-head"><h2>پنج روزِ حضور</h2><small>حضورِ باکیفیت، نه حضورِ خسته</small></header>
      <div class="home-list">${HOME_DAYS.map((d,i)=>`<article class="home-card ${i+10===pos?'now':''}">
        <b>روزِ ${fa(d.day)}</b><h3>${d.title}</h3><small>${d.goal}</small>
        <ul>${d.plan.map(p=>`<li>${p}</li>`).join('')}</ul></article>`).join('')}</div></section>
      <section class="panel"><header class="panel-head"><h2>تاریخِ شروعِ چرخه</h2></header>
      <input type="date" id="cyc" value="${state.cycleStart||''}"><p class="hint">روزِ اولِ ده روزِ کاری را انتخاب کن تا تقویم درست شود.</p></section>`;
    main.querySelector('#cyc').onchange=e=>{state.cycleStart=e.target.value;save();paint();};
  }
  function gamesTab(){
    main.innerHTML=`<section class="panel"><header class="panel-head"><h2>بازی‌های تلفنی</h2><small>۵ تا ۱۰ دقیقه، بدونِ صفحه</small></header>
      <div class="game-grid">${PHONE_GAMES.map(g=>`<article><h3>${g.t}</h3><p>${g.how}</p><small>مهارت: ${g.skill}</small></article>`).join('')}</div></section>`;
  }
  function coachTab(){
    main.innerHTML=`<section class="panel"><header class="panel-head"><h2>مربی‌گریِ پدر</h2><small>واقع‌بینانه، بدونِ احساسِ گناه</small></header>
      <div class="coach-list">${FATHER_COACHING.map(c=>`<article><h3>${c.t}</h3><p>${c.body}</p></article>`).join('')}</div></section>`;
  }
  function kidsTab(){
    main.innerHTML=`<section class="panel"><header class="panel-head"><h2>داشبوردِ بچه‌ها</h2></header>
      <div class="dash-grid">
        <div><b>${fa(state.explorer.missionsDone.length)}</b><small>مأموریتِ ${escapeHtml(state.names.explorer)}</small></div>
        <div><b>${fa((state.toddler.sessionsDone||[]).length)}</b><small>جلسهٔ ${escapeHtml(state.names.toddler)}</small></div>
        <div><b>${fa(state.explorer.streak.days)}</b><small>روزِ پیوسته</small></div>
        <div><b>${fa((state.father.messages||[]).length)}</b><small>پیامِ ارسالیِ تو</small></div>
      </div>
      <ul class="dash-skills">${analysis(state).strongest.map(([id,v])=>`<li>🌟 ${SKILLS.find(s=>s.id===id).name}: ${fa(Math.round(v))}</li>`).join('')}</ul>
      <div class="rec-list">${recommendations(state).slice(0,4).map(r=>`<article><span>${r.icon}</span><div><b>${r.title}</b><p>${r.body||''}</p></div></article>`).join('')}</div>
    </section>
    <section class="panel"><header class="panel-head"><h2>کارهایی که در پنج روزِ حضور اثر دارد</h2></header>
      <ul class="golden"><li>یک ساعتِ دونفره با همسر، بدونِ بچه‌ها</li><li>روتینِ خواب را به هم نریز</li>
      <li>قانون‌های خانه را با مادر هماهنگ کن</li><li>هر کودک، یک زمانِ اختصاصی</li>
      <li>قبل از رفتن، برنامهٔ ده روز را با هم مرور کنید</li></ul></section>`;
  }
  paint();
}

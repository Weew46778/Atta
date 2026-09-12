// باغ قصه‌ها ۳ — سازوکارهای بازی
// هر سازوکار یک بازیِ تعاملیِ جداست. همه از یک قرارداد پیروی می‌کنند:
//   M.نام(ctx) با ctx = {host, st, p, rounds, idx, state, say, sfx, done, hint, progress, narrate}

import {pools} from '../content/missions.js';
import {FOODS,PROVINCES,TIMELINE,CONSTELLATIONS,WORDS,FACTS,RIDDLES,VERSES} from '../content/iran.js';
import {sleep} from '../core/audio.js';

/* ---------------- ابزارهای کوچک ---------------- */
const H=html=>{const t=document.createElement('template');t.innerHTML=html.trim();return t.content.firstElementChild;};
const el=(tag,cls,txt)=>{const e=document.createElement(tag);if(cls)e.className=cls;if(txt!=null)e.textContent=txt;return e;};
function on(root,ev,sel,fn){root.addEventListener(ev,e=>{const t=e.target.closest(sel);if(t&&root.contains(t))fn(t,e);});}
const rnd=(a,b)=>a+Math.floor(Math.random()*(b-a+1));
const pick=a=>a[Math.floor(Math.random()*a.length)];
function shuffled(a){const r=a.slice();for(let i=r.length-1;i>0;i--){const j=Math.floor(Math.random()*(i+1));[r[i],r[j]]=[r[j],r[i]];}return r;}
const optsWith=(answer,distractors,n=4)=>shuffled([answer,...distractors]).slice(0,n);
const EMO=['🍎','🌸','🐢','🦊','🌟','🍇','🐝','🌙','🍉','🐟','🌷','🦋','🍄','🐘','🌵','🐦','🍒','🐇','🌻','🦉','🍋','🐚','🌿','🦜','🍑','🐞','🌊','🦁','🍐','🐳','🌺','🦔','🍓','🐙','🌾','🦚','🥝','🐸','🌴','🦌','🍊','🐿️'];
const SHAPES=['●','■','▲','◆','★','⬢'];

export function createTracker(ctx){
  const t={correct:0,total:0,tries:0,hints:0,start:Date.now(),extra:{}};
  t.finish=extra=>{ctx.done({correctRate:t.total?t.correct/t.total:1,tries:t.tries,hints:t.hints,
    millis:Date.now()-t.start,extra:{...t.extra,...(extra||{})}});};
  t.hit=ok=>{t.total++;if(ok)t.correct++;return ok;};
  t.miss=()=>{t.tries++;};
  t.hintUsed=()=>{t.hints++;ctx.sfx.hint();};
  return t;
}
/** گزینه‌های بزرگ و لمسی با بازخورد فوری */
function options(root,list,onPick,{big=false}={}){
  const box=el('div','opts'+(big?' big':''));
  list.forEach((o,i)=>{
    const b=el('button','opt');b.innerHTML=`<span class="opt-k">${i+1}</span><span>${o.html||o}</span>`;
    b.onclick=()=>{box.querySelectorAll('.opt').forEach(x=>x.disabled=true);onPick(i,b,box);};
    box.appendChild(b);
  });
  root.appendChild(box);
  return box;
}
function feedback(root,ok,text){
  const f=el('div','fb '+(ok?'good':'soft'));f.innerHTML=`<span>${ok?'✅':'💡'}</span><p>${text}</p>`;
  root.appendChild(f);
  return f;
}
function roundBadge(root,k,total){
  const b=el('div','round-badge');b.innerHTML=`<b>${k}</b><small>/${total}</small>`;
  root.appendChild(b);return b;
}
function bigCenter(root,html){const d=el('div','stage-center');d.innerHTML=html;root.appendChild(d);return d;}

/* ================= ۱) قصه ================= */
const M={};
M.story=async ctx=>{
  const {host,p,narrate}=ctx,{story}=p;
  const t=createTracker(ctx);
  const box=el('div','story-box');
  box.innerHTML=`<header class="story-head"><span class="story-kind">${story.kind}</span><h3>${story.title}</h3></header>
    <div class="story-text" id="story-text"></div><div class="story-stage"><div class="stage-sky"></div><div class="stage-figure">🦊</div></div>
    <div class="story-actions"></div>`;
  host.appendChild(box);
  const txt=box.querySelector('#story-text'),act=box.querySelector('.story-actions');
  const chapters=story.chapters;
  const qAfter=[1,3]; // پس از این فصل‌ها پرسش
  const qs=story.questions.slice();
  for(let i=0;i<chapters.length;i++){
    txt.innerHTML=`<p class="chapter">${chapters[i]}</p>`;
    await narrate(chapters[i],{sentenceEl:txt});
    if(qAfter.includes(i)&&qs.length){
      const q=qs.shift();
      const card=el('div','inline-q');
      card.innerHTML=`<p class="q-text">${q.q}</p>`;
      act.innerHTML='';act.appendChild(card);
      await new Promise(res=>{
        options(card,q.options,(idx,btn)=>{
          const ok=idx===q.answer;
          btn.classList.add(ok?'right':'wrong');
          if(!ok)card.querySelectorAll('.opt')[q.answer].classList.add('right');
          t.hit(ok);if(!ok)t.miss();
          feedback(card,ok,q.why);
          const next=el('button','btn primary','ادامهٔ قصه');
          next.onclick=()=>{card.remove();res();};
          card.appendChild(next);next.focus();
          ctx.sfx[ok?'good':'soft']();
        });
      });
    }else{
      const next=el('button','btn ghost','ادامه ⬅');
      act.innerHTML='';act.appendChild(next);
      await new Promise(res=>{next.onclick=res;});
    }
  }
  box.querySelector('.story-stage').classList.add('finale');
  const moral=el('div','moral');moral.innerHTML=`<span>🌟</span><p>${story.moral}</p>`;
  box.appendChild(moral);
  const words=el('div','word-chips');
  words.innerHTML=`<small>واژه‌های تازه:</small>`+story.words.map(w=>`<button class="chip" data-w="${w.w}" data-m="${w.m}"><b>${w.w}</b></button>`).join('');
  box.appendChild(words);
  on(words,'click','.chip',c=>{ctx.say(`${c.dataset.w} یعنی ${c.dataset.m}`);c.classList.add('said');});
  const speak=el('div','speak-mini');
  speak.innerHTML=`<p>حالا با صدای خودت بگو: <b>این قصه به تو چه یاد داد؟</b></p>`;
  const mic=el('button','btn mic','🎤 بلند بگو');
  speak.appendChild(mic);box.appendChild(speak);
  await new Promise(res=>{mic.onclick=async()=>{await ctx.listenFree('این قصه به تو چه یاد داد؟');res();};});
  await narrate(story.moral);
  t.finish({kind:'story',id:story.id});
};

/* ================= ۲) رازِ امروز (دانش + پرسش) ================= */
M.quiz=async ctx=>{
  const t=createTracker(ctx);
  const facts=[];
  const base=ctx.p.fact||FACTS[ctx.idx%FACTS.length];
  facts.push(base);
  for(let i=1;i<ctx.rounds;i++) facts.push(FACTS[(ctx.idx*7+i*13)%FACTS.length]);
  for(let r=0;r<facts.length;r++){
    const f=facts[r];
    ctx.host.innerHTML='';
    roundBadge(ctx.host,r+1,facts.length);
    const card=el('div','fact-card');
    card.innerHTML=`<span class="fact-tag">🔎 ${f.t}</span><p class="fact-text">${f.text}</p>`;
    ctx.host.appendChild(card);
    await ctx.narrate(`${f.text} حالا یک پرسش: ${f.ask}`,{sentenceEl:card.querySelector('.fact-text')});
    await new Promise(res=>{
      options(card,f.o,(i,btn)=>{
        const ok=f.o[i]===f.a;btn.classList.add(ok?'right':'wrong');
        if(!ok)card.querySelectorAll('.opt').forEach(b=>{if(b.textContent.includes(f.a))b.classList.add('right');});
        t.hit(ok);if(!ok)t.miss();
        feedback(card,ok,ok?'آفرین! همین درست بود.':'اشکالی ندارد؛ حالا درستش را یاد گرفتی.');
        ctx.sfx[ok?'good':'soft']();
        const nx=el('button','btn primary','بعدی');
        nx.onclick=()=>res();card.appendChild(nx);
      });
    });
  }
  t.finish({kind:'quiz'});
};

/* ================= ۳) چیستان ================= */
M.riddle=async ctx=>{
  const t=createTracker(ctx);
  const r=ctx.p.riddle||RIDDLES[ctx.idx%RIDDLES.length];
  const count=ctx.p.narrate?2:Math.max(2,ctx.rounds);
  const list=[r];
  for(let i=1;i<count;i++) list.push(RIDDLES[(ctx.idx*5+i*17)%RIDDLES.length]);
  for(let k=0;k<list.length;k++){
    const rr=list[k];
    ctx.host.innerHTML='';
    roundBadge(ctx.host,k+1,list.length);
    const card=el('div','riddle-card');
    card.innerHTML=`<div class="riddle-scroll"><p class="riddle-q">${rr.q}</p></div><div class="hint-row"></div>`;
    ctx.host.appendChild(card);
    await ctx.narrate(rr.q,{sentenceEl:card.querySelector('.riddle-q')});
    const hr=card.querySelector('.hint-row');
    let used=0;
    const hbtn=el('button','btn ghost','💡 راهنما');
    hbtn.onclick=()=>{if(used<rr.hints.length){hr.appendChild(el('span','hint-chip',rr.hints[used]));used++;t.hintUsed();}else hbtn.disabled=true;};
    hr.appendChild(hbtn);
    await new Promise(res=>{
      const distract=RIDDLES.filter(x=>x!==rr).map(x=>x.a).sort(()=>Math.random()-.5).slice(0,3);
      options(card,optsWith(rr.a,distract),(i,btn)=>{
        const chosen=[...card.querySelectorAll('.opt')][i].textContent.replace(/^\d/,'').trim();
        const ok=chosen.includes(rr.a)||rr.a.includes(chosen);
        btn.classList.add(ok?'right':'wrong');
        if(!ok)card.querySelectorAll('.opt').forEach(b=>{if(b.textContent.includes(rr.a))b.classList.add('right');});
        t.hit(ok);if(!ok)t.miss();
        t.extra.hintsUsed=(t.extra.hintsUsed||0)+used;
        feedback(card,ok,ok?`درست! جواب «${rr.a}» بود.`:`جواب «${rr.a}» بود. معماها با تمرین آسان می‌شوند.`);
        ctx.sfx[ok?'great':'soft']();
        const nx=el('button','btn primary','معمأ بعدی');nx.onclick=()=>res();card.appendChild(nx);
      });
    });
  }
  t.finish({kind:'riddle'});
};

/* ================= ۴) شعرِ ناتمام ================= */
M.verse=async ctx=>{
  const t=createTracker(ctx);
  const list=[];
  for(let i=0;i<Math.max(2,ctx.rounds);i++) list.push(VERSES[(ctx.idx*3+i*11)%VERSES.length]);
  for(let k=0;k<list.length;k++){
    const v=list[k];
    ctx.host.innerHTML='';
    roundBadge(ctx.host,k+1,list.length);
    const card=el('div','verse-card');
    card.innerHTML=`<span class="poet">${v.poet}</span><p class="verse-line">${v.line.replace('___','<b class="blank">؟</b>')}</p>`;
    ctx.host.appendChild(card);
    await ctx.narrate(`بیتِ ناقص از ${v.poet}: ${v.line.replace('___','جا خالی')}`);
    await new Promise(res=>{
      options(card,v.options,(i,btn)=>{
        const ok=v.options[i]===v.answer;
        btn.classList.add(ok?'right':'wrong');
        card.querySelector('.blank').textContent=v.answer;
        card.querySelector('.blank').classList.add('filled');
        t.hit(ok);if(!ok)t.miss();
        feedback(card,ok,v.note);
        ctx.sfx[ok?'bell':'soft']();
        const nx=el('button','btn primary','بیتِ بعدی');nx.onclick=()=>res();card.appendChild(nx);
      });
    });
  }
  t.finish({kind:'verse'});
};

/* ================= ۵) رمزگشایی ================= */
const SYMBOLS='☀☾★✿❀☘♣♦♥♠✦✧⚘⚛☂☃⚓⚔⚖⚙⚗⚡❄❥❦❧✤✥✜✝✚✛✢✣✤✥✦✧✩✪✫✬✭✮✯✰'.split('');
import {PERSIAN_LETTERS as PERSIAN,normalizePlain} from '../content/text.js';
M.cipher=ctx=>{
  const t=createTracker(ctx);
  const c=ctx.p.cipher||{plain:'دانش گنج است',key:'راز'};
  const plain=normalizePlain(c.plain);
  const shift=2+ctx.difficulty;
  const map={};
  const plainLetters=[...plain.replace(/\s/g,'')];
  [...new Set(plainLetters)].forEach((ch,i)=>{map[ch]=SYMBOLS[(i*3+shift)%SYMBOLS.length];});
  const encoded=plain.split('').map(ch=>ch===' '?' ':map[ch]||ch).join('');
  const box=el('div','cipher-box');
  box.innerHTML=`<p class="cipher-key">🔐 ${c.key}</p>
   <div class="cipher-code">${encoded.split('').map(ch=>ch===' '?'<span class="csp"></span>':`<span class="csym">${ch}</span>`).join('')}</div>
   <div class="cipher-wheel"></div>
   <div class="cipher-input"></div>
   <div class="cipher-actions"></div>`;
  ctx.host.appendChild(box);
  const wheel=box.querySelector('.cipher-wheel');
  Object.entries(map).forEach(([l,s])=>{wheel.appendChild(el('span','wheel-item',`${s} = ${l}`));});
  const input=box.querySelector('.cipher-input');
  let typed='';
  const show=()=>{input.textContent=typed+'▌';};
  show();
  const pad=el('div','letter-pad');
  PERSIAN.forEach(l=>{const b=el('button','pad-k',l);b.onclick=()=>{typed+=l;show();ctx.sfx.tap();check();};pad.appendChild(b);});
  box.querySelector('.cipher-actions').appendChild(pad);
  const del=el('button','btn ghost','⌫ پاک کردن');
  del.onclick=()=>{typed=typed.slice(0,-1);show();};
  box.querySelector('.cipher-actions').appendChild(del);
  const hintBtn=el('button','btn ghost','💡 حرف اول');
  hintBtn.onclick=()=>{t.hintUsed();feedback(box,true,`حرفِ اول «${plain[0]}» است.`);};
  box.querySelector('.cipher-actions').appendChild(hintBtn);
  const target=plain.replace(/\s/g,'');
  function check(){
    if(typed.length>=target.length){
      const ok=typed===target;
      t.hit(ok);if(!ok){t.miss();typed='';show();feedback(box,false,'نزدیک بود! دوباره از اول امتحان کن.');ctx.sfx.soft();return;}
      ctx.sfx.great();
      box.querySelector('.cipher-code').classList.add('solved');
      feedback(box,true,`رمز باز شد: «${c.plain}»`);
      pad.remove();del.remove();hintBtn.remove();
      const nx=el('button','btn primary','تمام شد');
      nx.onclick=()=>t.finish({kind:'cipher'});
      box.querySelector('.cipher-actions').appendChild(nx);
    }
  }
};

/* ================= ۶) واژه‌سازی ================= */
M.word=ctx=>{
  const t=createTracker(ctx);
  const count=Math.max(2,ctx.rounds);
  const list=[];
  for(let i=0;i<count;i++) list.push(WORDS[(ctx.idx*4+i*9)%WORDS.length]);
  let k=0;
  const render=()=>{
    const w=list[k];
    ctx.host.innerHTML='';
    roundBadge(ctx.host,k+1,count);
    const letters=shuffled([...w.w]);
    const card=el('div','word-card');
    card.innerHTML=`<p class="word-hint">🪄 ${w.hint}</p><div class="slots">${w.w.split('').map(()=>'<span class="slot"></span>').join('')}</div><div class="letters"></div>`;
    ctx.host.appendChild(card);
    ctx.narrate(w.hint);
    const slots=[...card.querySelectorAll('.slot')];
    const lbox=card.querySelector('.letters');
    let pos=0;
    letters.forEach(l=>{
      const b=el('button','letter-tile',l);
      b.onclick=()=>{
        if(b.disabled)return;
        if(w.w[pos]===l){slots[pos].textContent=l;slots[pos].classList.add('filled');b.disabled=true;pos++;ctx.sfx.pick();
          if(pos===w.w.length){t.hit(true);ctx.sfx.great();
            feedback(card,true,`آفرین! «${w.w}» — ${w.hint}`);
            const nx=el('button','btn primary',k+1<count?'واژهٔ بعدی':'تمام');
            nx.onclick=()=>{k++;k<count?render():t.finish({kind:'word'});};
            card.appendChild(nx);}
        }else{t.miss();b.classList.add('shake');ctx.sfx.soft();setTimeout(()=>b.classList.remove('shake'),400);}
      };
      lbox.appendChild(b);
    });
    const hb=el('button','btn ghost','💡 حرف بعدی');
    hb.onclick=()=>{t.hintUsed();feedback(card,true,`حرفِ بعدی «${w.w[pos]}» است.`);};
    card.appendChild(hb);
  };
  render();
};

/* ================= ۷) گوشِ تیز (حافظهٔ شنیداری) ================= */
M.listen=async ctx=>{
  const t=createTracker(ctx);
  const rounds=Math.max(2,ctx.rounds);
  for(let r=0;r<rounds;r++){
    const start=(ctx.idx*3+r*7)%WORDS.length;
    const words=[0,1,2,3+(ctx.difficulty>1?1:0)].map(i=>WORDS[(start+i)%WORDS.length].w);
    ctx.host.innerHTML='';
    roundBadge(ctx.host,r+1,rounds);
    const card=el('div','listen-card');
    card.innerHTML=`<div class="ear-anim"><span>👂</span></div><p class="listen-title">گوش کن و بعد جواب بده</p>
      <button class="btn primary" id="play-listen">▶ بشنو</button><div class="listen-q"></div>`;
    ctx.host.appendChild(card);
    const play=async()=>{
      card.classList.add('playing');
      await ctx.narrate(`کلمه‌ها را با دقت گوش کن: ${words.join('، ')}`);
      await sleep(300);
      await ctx.narrate(`یک بار دیگر: ${words.join('، ')}`);
      card.classList.remove('playing');
    };
    card.querySelector('#play-listen').onclick=play;
    await play();
    const qtype=r%3;
    const q=qtype===0?{q:'کدام کلمه اول بود؟',a:words[0]}:qtype===1?{q:'کدام کلمه آخر بود؟',a:words[words.length-1]}:{q:'کدام کلمه در این فهرست نبود؟',a:null};
    const qb=card.querySelector('.listen-q');
    qb.innerHTML=`<p class="q-text">${q.q}</p>`;
    const distract=WORDS.filter(w=>!words.includes(w.w)).sort(()=>Math.random()-.5).slice(0,3).map(w=>w.w);
    const answer=q.a??distract[0];
    const list=q.a?optsWith(answer,distract):shuffled([answer,...words]);
    await new Promise(res=>{
      options(qb,list,(i)=>{
        const chosen=list[i],ok=chosen===answer;
        t.hit(ok);if(!ok)t.miss();
        qb.querySelectorAll('.opt')[i].classList.add(ok?'right':'wrong');
        feedback(qb,ok,ok?'گوشِ تیزی داری!':`کلمه‌ها این‌ها بودند: ${words.join('، ')}`);
        ctx.sfx[ok?'good':'soft']();
        const nx=el('button','btn primary','دورِ بعد');nx.onclick=()=>res();qb.appendChild(nx);
      });
    });
  }
  t.finish({kind:'listen'});
};

/* ================= ۸) نورهای دماوند (توالی) ================= */
M.sequence=async ctx=>{
  const t=createTracker(ctx);
  const pads=ctx.p.pads||4,rounds=ctx.rounds;
  const colors=['#ff6b6b','#ffd166','#5ec26a','#4ea8de','#c77dff','#ff9f43'];
  const box=el('div','simon-box');
  box.innerHTML=`<p class="simon-title">🔦 الگو را ببین و تکرار کن</p><div class="simon-status">آماده</div><div class="pads"></div>`;
  ctx.host.appendChild(box);
  const padBox=box.querySelector('.pads');
  const status=box.querySelector('.simon-status');
  const buttons=[];
  for(let i=0;i<pads;i++){
    const b=el('button','pad');b.style.background=colors[i];b.dataset.i=i;
    padBox.appendChild(b);buttons.push(b);
  }
  const flash=async i=>{
    buttons[i].classList.add('lit');ctx.sfx.note(i*2);
    await sleep(380);buttons[i].classList.remove('lit');await sleep(120);
  };
  let seq=[];
  let locked=true;
  for(let r=0;r<rounds;r++){
    seq.push(rnd(0,pads-1));
    status.textContent='نگاه کن…';locked=true;
    await sleep(400);
    for(const i of seq) await flash(i);
    status.textContent='نوبتِ تو!';locked=false;
    const ok=await new Promise(res=>{
      let pos=0;
      const handler=e=>{
        if(locked)return;
        const b=e.target.closest('.pad');if(!b)return;
        const i=+b.dataset.i;
        if(i===seq[pos]){flash(i);pos++;if(pos===seq.length){padBox.removeEventListener('click',handler);res(true);}}
        else{b.classList.add('bad');padBox.removeEventListener('click',handler);res(false);}
      };
      padBox.addEventListener('click',handler);
    });
    t.hit(ok);if(!ok)t.miss();
    ctx.sfx[ok?'good':'soft']();
    status.textContent=ok?'آفرین! ادامه بده':'اشکالی ندارد؛ دوباره گوش کن.';
    await sleep(700);
  }
  t.finish({kind:'sequence'});
};

/* ================= ۹) کارت‌های حافظه ================= */
M.memoryPairs=ctx=>{
  const t=createTracker(ctx);
  const pairs=ctx.p.pairs||6;
  const emojis=shuffled(EMO).slice(0,pairs);
  const cards=shuffled([...emojis,...emojis]);
  const box=el('div','memory-box');
  box.innerHTML=`<div class="mem-stats"><span>جفت‌ها: <b id="mm">0</b>/${pairs}</span><span>تلاش: <b id="mv">0</b></span></div><div class="mem-grid"></div>`;
  ctx.host.appendChild(box);
  const grid=box.querySelector('.mem-grid');
  grid.style.setProperty('--cols',Math.ceil(Math.sqrt(cards.length)));
  let first=null,lock=false,found=0,moves=0;
  cards.forEach((emo,i)=>{
    const c=el('button','mem-card');c.innerHTML=`<span class="back">✳</span><span class="front">${emo}</span>`;
    c.onclick=()=>{
      if(lock||c.classList.contains('done')||c===first)return;
      c.classList.add('open');ctx.sfx.tap();
      if(!first){first=c;return;}
      moves++;box.querySelector('#mv').textContent=moves;t.tries++;
      lock=true;
      const match=first.querySelector('.front').textContent===c.querySelector('.front').textContent;
      setTimeout(()=>{
        if(match){first.classList.add('done');c.classList.add('done');found++;
          box.querySelector('#mm').textContent=found;t.correct++;ctx.sfx.good();
          if(found===pairs){t.finish({kind:'memoryPairs',moves});}
        }else{first.classList.remove('open');c.classList.remove('open');t.total++;}
        first=null;lock=false;
      },match?380:680);
    };
    grid.appendChild(c);
  });
};

/* ================= ۱۰) مسیرِ گنج ================= */
M.path=ctx=>{
  const t=createTracker(ctx);
  const rounds=ctx.rounds;
  let r=0;
  const render=()=>{
    const size=ctx.p.size+(r>0?1:0);
    const start=[0,0],goal=[size-1,size-1];
    // موانع تصادفی‌اند؛ چیدمانی را برمی‌داریم که راهِ صندوقچه را بسته باشد
    const reaches=(ob)=>{
      const seen=new Set(['0,0']);const q=[[0,0]];
      while(q.length){
        const [x,y]=q.shift();
        if(x===goal[0]&&y===goal[1]) return true;
        for(const [dx,dy] of [[0,-1],[1,0],[0,1],[-1,0]]){
          const nx=x+dx,ny=y+dy;
          if(nx<0||ny<0||nx>=size||ny>=size||ob.has(`${nx},${ny}`)) continue;
          const k=`${nx},${ny}`;if(seen.has(k)) continue;seen.add(k);q.push([nx,ny]);
        }
      }
      return false;
    };
    let obstacles=new Set();
    for(let a=0;a<60;a++){
      obstacles=new Set();
      while(obstacles.size<ctx.p.obstacles+r){
        const x=rnd(0,size-1),y=rnd(0,size-1);
        if((x===0&&y===0)||(x===goal[0]&&y===goal[1]))continue;
        obstacles.add(`${x},${y}`);
      }
      if(reaches(obstacles)) break;
    }
    const coins=new Set();
    while(coins.size<ctx.p.coins){const x=rnd(0,size-1),y=rnd(0,size-1);
      if(obstacles.has(`${x},${y}`)||(x===0&&y===0))continue;coins.add(`${x},${y}`);}
    let pos=[...start],moves=0,got=0;
    const budget=size*2+2;
    ctx.host.innerHTML='';
    roundBadge(ctx.host,r+1,rounds);
    const box=el('div','path-box');
    box.innerHTML=`<p class="path-goal">🎯 به صندوقچه برس و سکه‌ها را جمع کن — حداکثر ${budget} قدم</p>
      <div class="path-grid"></div><div class="path-info"></div>
      <div class="dpad"><button data-d="up">▲</button><div><button data-d="left">◀</button><button data-d="down">▼</button><button data-d="right">▶</button></div></div>`;
    ctx.host.appendChild(box);
    const grid=box.querySelector('.path-grid');
    grid.style.setProperty('--size',size);
    const cells=[];
    for(let y=0;y<size;y++)for(let x=0;x<size;x++){
      const c=el('div','cell');c.dataset.x=x;c.dataset.y=y;
      if(obstacles.has(`${x},${y}`))c.classList.add('rock');
      if(coins.has(`${x},${y}`))c.classList.add('coin');
      if(x===goal[0]&&y===goal[1])c.classList.add('goal');
      grid.appendChild(c);cells.push(c);
    }
    const hero=el('div','hero','🦊');grid.appendChild(hero);
    const paint=()=>{
      hero.style.left=`calc(${pos[0]} * (100% / ${size}) + (100% / ${size} / 2))`;
      hero.style.top=`calc(${pos[1]} * (100% / ${size}) + (100% / ${size} / 2))`;
      box.querySelector('.path-info').innerHTML=`<span>قدم: <b>${moves}</b>/${budget}</span><span>سکه: <b>${got}</b>/${coins.size}</span>`;
    };
    paint();
    const move=d=>{
      const n=[...pos];
      if(d==='up')n[1]--;if(d==='down')n[1]++;if(d==='left')n[0]--;if(d==='right')n[0]++;
      if(n[0]<0||n[1]<0||n[0]>=size||n[1]>=size){ctx.sfx.soft();return;}
      if(obstacles.has(`${n[0]},${n[1]}`)){ctx.sfx.thud();return;}
      pos=n;moves++;ctx.sfx.tap();
      const key=`${pos[0]},${pos[1]}`;
      if(coins.has(key)){coins.delete(key);got++;ctx.sfx.pick();
        cells.find(c=>+c.dataset.x===pos[0]&&+c.dataset.y===pos[1]).classList.remove('coin');}
      paint();
      if(pos[0]===goal[0]&&pos[1]===goal[1]){
        const ok=moves<=budget&&got===ctx.p.coins;
        t.hit(ok);if(!ok)t.miss();
        ctx.sfx[ok?'great':'good']();
        const f=el('div','fb good');f.innerHTML=`<span>🎉</span><p>رسیدی! با ${moves} قدم و ${got} سکه.</p>`;
        box.appendChild(f);
        const nx=el('button','btn primary',r+1<rounds?'مسیرِ بعدی':'تمام');
        nx.onclick=()=>{r++;r<rounds?render():t.finish({kind:'path'});};
        box.appendChild(nx);return;
      }
      if(moves>=budget){
        t.hit(false);t.miss();ctx.sfx.soft();
        const f=el('div','fb soft');f.innerHTML=`<span>💡</span><p>قدم‌ها تمام شد. مسیرِ کوتاه‌تر را پیدا کن.</p>`;
        box.appendChild(f);
        const nx=el('button','btn primary','دوباره این مسیر');
        nx.onclick=()=>render();box.appendChild(nx);
      }
    };
    on(box,'click','.dpad button',b=>move(b.dataset.d));
  };
  render();
};

/* ================= ۱۱) روباهِ برنامه‌نویس ================= */
M.bot=ctx=>{
  const t=createTracker(ctx);
  const size=6;
  const start=[0,size-1],goal=[size-1,0];
  // چیدمانِ موانع را تا حدی تصادفی می‌سازیم که «حتماً راه‌حل داشته باشد»
  const DIRS=[[0,-1],[1,0],[0,1],[-1,0]];
  const solvable=b=>{
    const seen=new Set([`${start[0]},${start[1]},0`]);const q=[[start[0],start[1],0]];
    while(q.length){
      const [x,y,d]=q.shift();
      if(x===goal[0]&&y===goal[1]) return true;
      for(const step of ['F','L','R']){
        let nx=x,ny=y,nd=d;
        if(step==='F'){nx+=DIRS[d][0];ny+=DIRS[d][1];}else nd=(d+(step==='R'?1:3))%4;
        if(nx<0||ny<0||nx>=size||ny>=size||b.has(`${nx},${ny}`)) continue;
        const k=`${nx},${ny},${nd}`;if(seen.has(k)) continue;seen.add(k);q.push([nx,ny,nd]);
      }
    }
    return false;
  };
  let blocks=new Set();
  for(let attempt=0;attempt<60;attempt++){
    blocks=new Set();
    while(blocks.size<ctx.p.blocks){
      const x=rnd(1,size-1),y=rnd(0,size-2);
      if(x===goal[0]&&y===goal[1]) continue;
      blocks.add(`${x},${y}`);
    }
    if(solvable(blocks)) break;
  }
  const box=el('div','bot-box');
  box.innerHTML=`<p class="bot-goal">🤖 دستور بده تا روبیکا به لانه برسد</p>
    <div class="bot-grid"></div><div class="bot-prog"></div>
    <div class="bot-cmds"><button data-c="F">یک قدم جلو ⬆</button><button data-c="L">به چپ بچرخ ↺</button><button data-c="R">به راست بچرخ ↻</button></div>
    <div class="bot-run"><button class="btn primary" id="run">▶ اجرا</button><button class="btn ghost" id="clear">پاک کردن</button></div>`;
  ctx.host.appendChild(box);
  const grid=box.querySelector('.bot-grid');
  grid.style.setProperty('--size',size);
  const hero=el('div','bot-hero','🦊');
  for(let y=0;y<size;y++)for(let x=0;x<size;x++){
    const c=el('div','cell');c.dataset.x=x;c.dataset.y=y;
    if(blocks.has(`${x},${y}`))c.classList.add('rock');
    if(x===goal[0]&&y===goal[1])c.classList.add('goal');
    grid.appendChild(c);
  }
  grid.appendChild(hero);
  let prog=[],pos=[...start],dir=0; // 0=up,1=right,2=down,3=left
  const paint=()=>{
    hero.style.left=`calc(${pos[0]} * (100%/${size}) + (100%/${size}/2))`;
    hero.style.top=`calc(${pos[1]} * (100%/${size}) + (100%/${size}/2))`;
    hero.style.transform=`translate(-50%,-50%) rotate(${dir*90}deg)`;
  };
  paint();
  const paintProg=()=>{box.querySelector('.bot-prog').innerHTML=prog.map(c=>`<span class="cmd">${{F:'⬆',L:'↺',R:'↻'}[c]}</span>`).join('')||'<small>هنوز دستوری نداده‌ای</small>';};
  paintProg();
  on(box,'click','.bot-cmds button',b=>{prog.push(b.dataset.c);ctx.sfx.tap();paintProg();});
  box.querySelector('#clear').onclick=()=>{prog=[];pos=[...start];dir=0;paint();paintProg();};
  box.querySelector('#run').onclick=async()=>{
    pos=[...start];dir=0;paint();
    const steps={F:[0,-1],L:0,R:0};
    for(const c of prog){
      if(c==='L')dir=(dir+3)%4;else if(c==='R')dir=(dir+1)%4;
      else{const d=[[0,-1],[1,0],[0,1],[-1,0]][dir];pos=[pos[0]+d[0],pos[1]+d[1]];}
      paint();ctx.sfx.tap();await sleep(260);
      if(pos[0]<0||pos[1]<0||pos[0]>=size||pos[1]>=size||blocks.has(`${pos[0]},${pos[1]}`)){
        pos=[Math.max(0,Math.min(size-1,pos[0])),Math.max(0,Math.min(size-1,pos[1]))];paint();
        t.hit(false);t.miss();ctx.sfx.thud();
        feedback(box,false,'روبیکا به مانع خورد! دستور را درست کن.');
        paintProg();return;
      }
    }
    const ok=pos[0]===goal[0]&&pos[1]===goal[1];
    t.hit(ok);if(!ok)t.miss();
    ctx.sfx[ok?'great':'soft']();
    feedback(box,ok,ok?'عالی! الگوریتمِ تو کار کرد.':'نرسید؛ چند قدم دیگر لازم دارد.');
    if(ok){const nx=el('button','btn primary','تمام');nx.onclick=()=>t.finish({kind:'bot'});box.appendChild(nx);}
  };
};

/* ================= ۱۲) مسابقهٔ عدد ================= */
M.mathRace=async ctx=>{
  const t=createTracker(ctx);
  const {max,ops,count}=ctx.p;
  let streak=0,lives=3;
  const box=el('div','math-box');
  box.innerHTML=`<div class="math-hud"><span>❤️ <b id="lv">${lives}</b></span><span>🔥 <b id="sk">0</b></span><span>⏱ <b id="tm">10</b></span></div>
   <div class="math-q"></div><div class="math-pad"></div>`;
  ctx.host.appendChild(box);
  for(let i=0;i<count&&lives>0;i++){
    const op=ops[rnd(0,ops.length-1)];
    let a=rnd(1,max),b=rnd(1,max),ans;
    if(op==='+')ans=a+b;
    else if(op==='-'){if(b>a)[a,b]=[b,a];ans=a-b;}
    else{a=rnd(2,Math.min(12,max/2));b=rnd(2,9);ans=a*b;}
    box.querySelector('.math-q').innerHTML=`<span class="math-eq">${a} ${op} ${b} = <b>؟</b></span>`;
    const choices=optsWith(ans,[ans+1,ans-1,ans+rnd(2,9),ans-rnd(2,9)].filter(n=>n!==ans&&n>=0),4);
    const pad=box.querySelector('.math-pad');pad.innerHTML='';
    let time=10;
    box.querySelector('#tm').textContent=time;
    const timer=setInterval(()=>{time--;box.querySelector('#tm').textContent=time;if(time<=0){clearInterval(timer);answer(-1);}},1000);
    const answer=idx=>{
      clearInterval(timer);
      const ok=idx>=0&&choices[idx]===ans;
      t.hit(ok);
      pad.querySelectorAll('.opt').forEach((b,j)=>{if(choices[j]===ans)b.classList.add('right');});
      if(ok){streak++;ctx.sfx.pick();}else{lives--;streak=0;t.miss();ctx.sfx.soft();}
      box.querySelector('#lv').textContent=lives;
      box.querySelector('#sk').textContent=streak;
    };
    options(pad,choices.map(c=>String(c)),i2=>answer(i2));
    await sleep(700);
  }
  t.finish({kind:'mathRace',streak});
};

/* ================= ۱۳) چشمِ عقاب (پیدا کردن) ================= */
M.focusFind=ctx=>{
  const t=createTracker(ctx);
  const size=ctx.p.grid,hidden=ctx.p.hidden;
  let r=0;
  const render=()=>{
    ctx.host.innerHTML='';
    roundBadge(ctx.host,r+1,ctx.rounds);
    const targets=shuffled(EMO).slice(0,hidden);
    const filler=EMO.filter(e=>!targets.includes(e));
    const cells=[];
    for(let i=0;i<size*size;i++) cells.push(filler[i%filler.length]);
    // خانه‌ها را بُر می‌زنیم تا هر هدف روی یک خانهٔ «جدا» بنشیند؛
    // قبلاً دو هدف گاهی روی یک خانه می‌افتادند و دور هرگز تمام نمی‌شد.
    const spots=shuffled(cells.map((_,i)=>i)).slice(0,targets.length);
    targets.forEach((tg,j)=>{cells[spots[j]]=tg;});
    const box=el('div','focus-box');
    box.innerHTML=`<p class="focus-goal">🦅 این‌ها را پیدا کن: ${targets.map(x=>`<span class="target">${x}</span>`).join('')}</p>
      <div class="focus-grid"></div><div class="focus-timer">⏳ <b>30</b></div>`;
    ctx.host.appendChild(box);
    const grid=box.querySelector('.focus-grid');
    grid.style.setProperty('--cols',size);
    let found=0,time=30;
    const timer=setInterval(()=>{time--;box.querySelector('.focus-timer b').textContent=time;
      if(time<=0){clearInterval(timer);t.hit(false);t.miss();feedback(box,false,'وقت تمام! دفعهٔ بعد سریع‌تر نگاه کن.');next();}},1000);
    cells.forEach(emo=>{
      const c=el('button','focus-cell',emo);
      c.onclick=()=>{
        if(targets.includes(emo)&&!c.classList.contains('hit')){
          c.classList.add('hit');found++;ctx.sfx.pick();
          if(found===targets.length){clearInterval(timer);t.hit(true);ctx.sfx.great();feedback(box,true,'همه را پیدا کردی! چشمِ تیزی داری.');next();}
        }else if(!targets.includes(emo)){c.classList.add('miss');ctx.sfx.soft();t.miss();}
      };
      grid.appendChild(c);
    });
    const next=()=>{const nx=el('button','btn primary',r+1<ctx.rounds?'دورِ بعد':'تمام');
      nx.onclick=()=>{r++;r<ctx.rounds?render():t.finish({kind:'focusFind'});};box.appendChild(nx);};
  };
  render();
};

/* ================= ۱۴) بگیر و نگیر ================= */
M.reflex=ctx=>{
  const t=createTracker(ctx);
  const rounds=ctx.rounds;
  const box=el('div','reflex-box');
  box.innerHTML=`<p class="reflex-goal">⭐ ستاره را بگیر، 💣 بمب را نه!</p><div class="reflex-grid"></div><div class="reflex-score">⭐ <b>0</b></div>`;
  ctx.host.appendChild(box);
  const grid=box.querySelector('.reflex-grid');
  const N=9;
  for(let i=0;i<N;i++) grid.appendChild(el('button','reflex-cell'));
  let k=0,score=0;
  const step=()=>{
    if(k>=rounds){t.finish({kind:'reflex',score});return;}
    grid.querySelectorAll('.reflex-cell').forEach(c=>{c.textContent='';c.className='reflex-cell';});
    const good=rnd(0,N-1);
    grid.children[good].textContent='⭐';
    const traps=ctx.p.traps;
    for(let i=0;i<traps;i++){let p=rnd(0,N-1);if(p===good)continue;grid.children[p].textContent='💣';}
    let done=false;
    const t0=Date.now();
    const handler=e=>{
      if(done)return;
      const c=e.target.closest('.reflex-cell');if(!c||!c.textContent)return;
      done=true;k++;
      const ms=Date.now()-t0;
      if(c.textContent==='⭐'){score++;t.hit(true);c.classList.add('good');ctx.sfx.pick();}
      else{t.hit(false);t.miss();c.classList.add('bad');ctx.sfx.thud();}
      t.extra.lastMs=ms;
      grid.removeEventListener('click',handler);
      box.querySelector('.reflex-score b').textContent=score;
      setTimeout(step,420);
    };
    grid.addEventListener('click',handler);
  };
  step();
};

/* ================= ۱۵) الگویِ گمشده ================= */
M.pattern=ctx=>{
  const t=createTracker(ctx);
  const rounds=ctx.rounds;
  let r=0;
  const render=()=>{
    ctx.host.innerHTML='';
    roundBadge(ctx.host,r+1,rounds);
    const kind=(ctx.p.kind+r)%3;
    let seq=[],answer,choices;
    if(kind===0){ // عددی
      const s=rnd(1,6),d=rnd(2,5);
      seq=[0,1,2,3].map(i=>s+i*d);answer=s+4*d;
      choices=optsWith(answer,[answer+d,answer-d,answer+1],4).map(String);
    }else if(kind===1){ // رنگی
      const cols=['🔴','🔵','🟡','🟢'];
      const a=cols[rnd(0,3)],b=cols[rnd(0,3)],c=cols[rnd(0,3)];
      seq=[a,b,c,a,b];answer=c;
      choices=shuffled(cols);
    }else{ // شکلی
      const a=SHAPES[rnd(0,5)],b=SHAPES[rnd(0,5)];
      seq=[a,b,a,b,a];answer=b;
      choices=shuffled(SHAPES.slice(0,4));
    }
    const box=el('div','pattern-box');
    box.innerHTML=`<p class="pattern-goal">🧩 بعدی چیست؟</p><div class="pattern-seq">${seq.map(x=>`<span>${x}</span>`).join('')}<span class="q">؟</span></div><div class="pattern-opts"></div>`;
    ctx.host.appendChild(box);
    options(box.querySelector('.pattern-opts'),choices.map(c=>String(c)),(i)=>{
      const ok=String(choices[i])===String(answer);
      box.querySelectorAll('.opt')[i].classList.add(ok?'right':'wrong');
      t.hit(ok);if(!ok)t.miss();
      ctx.sfx[ok?'good':'soft']();
      feedback(box,ok,ok?'الگو را گرفتی!':`الگو این بود: ${seq.join(' ')} → ${answer}`);
      const nx=el('button','btn primary',r+1<rounds?'بعدی':'تمام');
      nx.onclick=()=>{r++;r<rounds?render():t.finish({kind:'pattern'});};
      box.appendChild(nx);
    });
  };
  render();
};

/* ================= ۱۶) کی فرق داره؟ ================= */
M.oddOne=ctx=>{
  const t=createTracker(ctx);
  const groups=[
    {items:['🍎','🍇','🍉','🐢'],odd:3,why:'سه تا میوه‌اند، لاک‌پشت حیوان است.'},
    {items:['🌸','🌷','🌻','🦋'],odd:3,why:'سه تا گل‌اند، پروانه حشره است.'},
    {items:['🐟','🐳','🐙','🦁'],odd:3,why:'سه تا در آب‌اند، شیر در خشکی.'},
    {items:['🔺','🟥','🔵','🔵'],odd:0,why:'سه تا گرد/بی‌ضی‌اند، مثلث گوشه دارد.'},
    {items:['🍞','🍚','🍝','🥛'],odd:3,why:'سه تا غلات‌اند، شیر لبنیات است.'},
    {items:['🚗','🚌','🚲','✈️'],odd:3,why:'سه تا روی زمین‌اند، هواپیما در آسمان.'},
    {items:['🌵','🌴','🌿','🐚'],odd:3,why:'سه تا گیاه‌اند، صدف جانور دریایی است.'},
    {items:['🦉','🦜','🦅','🐸'],odd:3,why:'سه تا پرنده‌اند، قورباغه دوزیست.'}
  ];
  let r=0;
  const render=()=>{
    ctx.host.innerHTML='';
    roundBadge(ctx.host,r+1,ctx.rounds);
    const g=groups[(ctx.idx+r)%groups.length];
    const box=el('div','odd-box');
    box.innerHTML=`<p class="odd-goal">🔍 کدام یکی با بقیه فرق دارد؟</p><div class="odd-grid"></div>`;
    ctx.host.appendChild(box);
    const grid=box.querySelector('.odd-grid');
    const items=shuffled(g.items.map((e,i)=>({e,odd:i===g.odd})));
    items.forEach(it=>{
      const b=el('button','odd-item',it.e);
      b.onclick=()=>{
        const ok=it.odd;
        grid.querySelectorAll('.odd-item').forEach(x=>x.disabled=true);
        b.classList.add(ok?'right':'wrong');
        if(!ok)items.forEach((o,j)=>{if(o.odd)grid.children[j].classList.add('right');});
        t.hit(ok);if(!ok)t.miss();
        ctx.sfx[ok?'good':'soft']();
        feedback(box,ok,g.why);
        const nx=el('button','btn primary',r+1<ctx.rounds?'بعدی':'تمام');
        nx.onclick=()=>{r++;r<ctx.rounds?render():t.finish({kind:'oddOne'});};
        box.appendChild(nx);
      };
      grid.appendChild(b);
    });
  };
  render();
};

/* ================= ۱۷) ترازوی دانا ================= */
M.balance=ctx=>{
  const t=createTracker(ctx);
  const items=ctx.p.items,weighs=ctx.p.weighs;
  const N=items;
  const heavy=rnd(0,N-1);
  const box=el('div','balance-box');
  box.innerHTML=`<p class="balance-goal">⚖ یکی از ${N} سنگ، سنگین‌تر است. فقط ${weighs} بار می‌توانی وزن کنی.</p>
   <div class="scale"><div class="pan left"></div><div class="beam"></div><div class="pan right"></div></div>
   <div class="stones"></div><div class="weigh-actions"></div>`;
  ctx.host.appendChild(box);
  const stones=box.querySelector('.stones');
  let left=[],right=[],used=0;
  for(let i=0;i<N;i++){const b=el('button','stone',`🪨${i+1}`);b.dataset.i=i;
    b.onclick=()=>{
      if(left.includes(i)||right.includes(i))return;
      if(left.length<=right.length){left.push(i);b.classList.add('in-left');}
      else{right.push(i);b.classList.add('in-right');}
      ctx.sfx.tap();
    };
    stones.appendChild(b);}
  const act=box.querySelector('.weigh-actions');
  const reset=el('button','btn ghost','برداشتن');
  reset.onclick=()=>{left=[];right=[];stones.querySelectorAll('.stone').forEach(s=>s.className='stone');};
  const weigh=el('button','btn primary','⚖ وزن کن');
  const guessBox=el('div','guess-row');
  act.append(weigh,reset,guessBox);
  weigh.onclick=()=>{
    if(left.length===0||left.length!==right.length){feedback(box,false,'دو طرف باید تعدادِ مساوی سنگ داشته باشند.');return;}
    if(used>=weighs){feedback(box,false,'وزنه‌ها تمام شد! حالا حدست را بگو.');return;}
    used++;
    const inLeft=left.includes(heavy),inRight=right.includes(heavy);
    const res=inLeft?'left':inRight?'right':'equal';
    box.querySelector('.scale').className='scale '+res;
    ctx.sfx.chime();
    feedback(box,true,res==='left'?'کفهٔ چپ سنگین‌تر است.':res==='right'?'کفهٔ راست سنگین‌تر است.':'دو طرف مساوی‌اند؛ سنگِ سنگین بیرون است!');
    left=[];right=[];stones.querySelectorAll('.stone').forEach(s=>s.className='stone');
    if(used>=weighs) showGuess();
  };
  function showGuess(){
    guessBox.innerHTML='<p>حالا بگو کدام سنگ سنگین‌تر است:</p>';
    for(let i=0;i<N;i++){
      const b=el('button','guess-stone',`🪨${i+1}`);
      b.onclick=()=>{
        const ok=i===heavy;
        t.hit(ok);if(!ok)t.miss();
        ctx.sfx[ok?'great':'soft']();
        feedback(box,ok,ok?`درست! سنگِ ${i+1} سنگین‌تر بود.`:`سنگِ ${heavy+1} سنگین‌تر بود.`);
        const nx=el('button','btn primary','تمام');nx.onclick=()=>t.finish({kind:'balance'});
        guessBox.appendChild(nx);
      };
      guessBox.appendChild(b);
    }
  }
};

/* ================= ۱۸) دسته‌بندی ================= */
M.sort=ctx=>{
  const t=createTracker(ctx);
  const sets=[
    {name:'غذاها را دسته کن',groups:{'غلات':['نان','برنج','ماکارونی','جو'],'پروتئین':['مرغ','تخم‌مرغ','عدس','ماهی'],'میوه':['سیب','انار','موز','گیلاس']}},
    {name:'زنده و غیرزنده',groups:{'زنده':['درخت','گربه','مورچه','گل'],'غیرزنده':['سنگ','میز','کفش','لیوان'],'نیمه‌زنده! (خوراکی گیاهی)':['سیب','هویج','گردو','کاهو']}},
    {name:'ایران را دسته کن',groups:{'شمال':['رشت','ساری','گرگان','آستارا'],'کویر':['یزد','کرمان','سمنان','بیرجند'],'جنوب و ساحل':['بندرعباس','بوشهر','چابهار','عبادان']}},
    {name:'احساس‌ها',groups:{'خوشحال':['شادی','خنده','ذوق','افتخار'],'ناراحت':['غم','دلتنگی','ناامیدی','تنهایی'],'عصبانی':['خشم','کلافگی','بیزاری','جوش']}},
    {name:'علم یا هنر؟',groups:{'علم':['آزمایش','عدد','ستاره','گیاه'],'هنر':['نقاشی','موسیقی','خوشنویسی','سفال'],'ورزش':['دویدن','شنا','ژیمناستیک','فوتبال']}}
  ];
  const s=sets[ctx.p.cats%sets.length];
  const names=Object.keys(s.groups);
  const all=shuffled(names.flatMap(g=>s.groups[g].map(v=>({v,g}))));
  const box=el('div','sort-box');
  box.innerHTML=`<p class="sort-goal">🗂 ${s.name}</p><div class="sort-pool"></div><div class="sort-cats"></div>`;
  ctx.host.appendChild(box);
  const pool=box.querySelector('.sort-pool'),cats=box.querySelector('.sort-cats');
  const chips=all.map(it=>{const c=el('button','sort-chip',it.v);c.dataset.g=it.g;
    c.onclick=()=>{if(c.classList.contains('used'))return;c.classList.add('selected');pool.querySelectorAll('.sort-chip').forEach(x=>{if(x!==c)x.classList.remove('selected');});};
    pool.appendChild(c);return c;});
  names.forEach(g=>{
    const b=el('button','sort-cat',g);
    b.onclick=()=>{
      const sel=pool.querySelector('.sort-chip.selected');
      if(!sel)return;
      const ok=sel.dataset.g===g;
      t.hit(ok);
      if(ok){sel.classList.add('used','right');sel.classList.remove('selected');ctx.sfx.pick();}
      else{t.miss();sel.classList.add('shake');ctx.sfx.soft();setTimeout(()=>sel.classList.remove('shake'),400);}
      if([...pool.querySelectorAll('.sort-chip')].every(c=>c.classList.contains('used'))){
        ctx.sfx.great();feedback(box,true,'همه درست دسته‌بندی شدند!');
        const nx=el('button','btn primary','تمام');nx.onclick=()=>t.finish({kind:'sort'});box.appendChild(nx);
      }
    };
    cats.appendChild(b);
  });
};

/* ================= ۱۹) ترتیبِ درست ================= */
M.order=ctx=>{
  const t=createTracker(ctx);
  const sets=[
    {n:'رویشِ دانه',steps:['دانه در خاک می‌افتد','آب می‌خورد و نرم می‌شود','ریشه پایین می‌رود','جوانه از خاک بیرون می‌آید','گل می‌دهد و دانه می‌سازد']},
    {n:'نانِ سنگک',steps:['گندم را آرد می‌کنند','خمیر درست می‌کنند','خمیر ور می‌آید','روی سبزهٔ تنور می‌چسبانند','نان برشته می‌شود']},
    {n:'چرخهٔ آب',steps:['خورشید آب دریا را گرم می‌کند','بخار بالا می‌رود','ابر ساخته می‌شود','باران می‌بارد','آب به رود و دریا برمی‌گردد']},
    {n:'صبحِ تو',steps:['بیدار می‌شوی','دست و صورت را می‌شویی','صبحانه می‌خوری','لباس می‌پوشی','به مدرسه می‌روی']},
    {n:'نامهٔ پست',steps:['نامه را می‌نویسی','در پاکت می‌گذاری','تمبر می‌چسبانی','به صندوق می‌اندازی','به دستِ دوستت می‌رسد']},
    {n:'تاریخِ ایران',steps:['شهرِ سوخته','هخامنشیان','ساسانیان','صفویه','امروز']}
  ];
  const s=sets[ctx.p.topic%sets.length];
  const shuffledSteps=shuffled(s.steps.map((v,i)=>({v,i})));
  const box=el('div','order-box');
  box.innerHTML=`<p class="order-goal">🔢 ${s.n} را به ترتیب بچین</p><div class="order-slots"></div><div class="order-pool"></div>`;
  ctx.host.appendChild(box);
  const slots=box.querySelector('.order-slots'),pool=box.querySelector('.order-pool');
  s.steps.forEach(()=>{slots.appendChild(el('span','slot','—'));});
  let pos=0;
  shuffledSteps.forEach(it=>{
    const b=el('button','order-chip',it.v);
    b.onclick=()=>{
      if(b.disabled)return;
      const ok=it.i===pos;
      t.hit(ok);
      if(ok){b.disabled=true;b.classList.add('used');slots.children[pos].textContent=it.v;slots.children[pos].classList.add('filled');pos++;ctx.sfx.pick();
        if(pos===s.steps.length){ctx.sfx.great();feedback(box,true,'ترتیب درست بود!');
          const nx=el('button','btn primary','تمام');nx.onclick=()=>t.finish({kind:'order'});box.appendChild(nx);}
      }else{t.miss();b.classList.add('shake');ctx.sfx.soft();setTimeout(()=>b.classList.remove('shake'),400);}
    };
    pool.appendChild(b);
  });
};

/* ================= ۲۰) صورتِ فلکی ================= */
M.stars=ctx=>{
  const t=createTracker(ctx);
  const c=CONSTELLATIONS[ctx.p.ci];
  const box=el('div','stars-box');
  box.innerHTML=`<p class="stars-goal">✨ ${c.n} — ستاره‌ها را به ترتیب به هم وصل کن</p>
   <div class="star-field"><svg class="star-lines"></svg></div><p class="stars-note">${c.note}</p>`;
  ctx.host.appendChild(box);
  const field=box.querySelector('.star-field');
  const svg=box.querySelector('.star-lines');
  let idx=0,last=null;
  c.stars.forEach(([x,y],i)=>{
    const s=el('button','star-dot','★');
    s.style.left=x+'%';s.style.top=y+'%';
    s.onclick=()=>{
      if(i===idx){
        s.classList.add('on');
        if(last){
          const l=document.createElementNS('http://www.w3.org/2000/svg','line');
          l.setAttribute('x1',last.x+'%');l.setAttribute('y1',last.y+'%');
          l.setAttribute('x2',x+'%');l.setAttribute('y2',y+'%');
          svg.appendChild(l);
        }
        last={x,y};idx++;ctx.sfx.chime();
        if(idx===c.stars.length){
          t.hit(true);ctx.sfx.great();
          feedback(box,true,`${c.n} کامل شد! ${c.note}`);
          const nx=el('button','btn primary','تمام');nx.onclick=()=>t.finish({kind:'stars'});box.appendChild(nx);
        }
      }else{t.miss();t.total++;s.classList.add('off');ctx.sfx.soft();setTimeout(()=>s.classList.remove('off'),400);}
    };
    field.appendChild(s);
  });
};

/* ================= ۲۱) تخمینِ دقیق ================= */
M.estimate=ctx=>{
  const t=createTracker(ctx);
  let r=0;
  const render=()=>{
    ctx.host.innerHTML='';
    roundBadge(ctx.host,r+1,ctx.rounds);
    const n=rnd(ctx.p.min,ctx.p.max);
    const box=el('div','est-box');
    box.innerHTML=`<p class="est-goal">🫙 سه ثانیه نگاه کن، بعد حدس بزن</p><div class="est-jar"></div>`;
    ctx.host.appendChild(box);
    const jar=box.querySelector('.est-jar');
    for(let i=0;i<n;i++){const s=el('span','est-dot','✦');
      s.style.left=rnd(4,92)+'%';s.style.top=rnd(8,90)+'%';jar.appendChild(s);}
    setTimeout(()=>{
      jar.classList.add('hidden');
      const guess=optsWith(n,[n+rnd(3,9),Math.max(1,n-rnd(3,9)),n+rnd(10,20)].filter(v=>v!==n),4);
      const card=el('div','est-ask');card.innerHTML='<p>چند تا بود؟</p>';
      box.appendChild(card);
      options(card,guess.map(String),i=>{
        const chosen=guess[i],diff=Math.abs(chosen-n);
        const ok=chosen===n,near=diff<=2;
        t.hit(ok||near);if(!ok&&!near)t.miss();
        card.querySelectorAll('.opt')[i].classList.add(ok?'right':near?'near':'wrong');
        ctx.sfx[ok?'great':near?'good':'soft']();
        feedback(card,ok||near,`تعدادِ واقعی ${n} بود. اختلافِ تو: ${diff}`);
        const nx=el('button','btn primary',r+1<ctx.rounds?'دورِ بعد':'تمام');
        nx.onclick=()=>{r++;r<ctx.rounds?render():t.finish({kind:'estimate'});};
        card.appendChild(nx);
      });
    },3000);
  };
  render();
};

/* ================= ۲۲) ریتمِ جنگل ================= */
M.rhythm=async ctx=>{
  const t=createTracker(ctx);
  const box=el('div','rhythm-box');
  box.innerHTML=`<p class="rhythm-goal">🥁 ریتم را بشنو و دقیقاً تکرار کن</p>
   <div class="rhythm-status">آماده</div><button class="drum" id="drum">🥁</button><div class="rhythm-view"></div>`;
  ctx.host.appendChild(box);
  const status=box.querySelector('.rhythm-status');
  for(let r=0;r<ctx.rounds;r++){
    const len=ctx.p.len+r;
    const pat=Array.from({length:len},()=>rnd(0,2)); // 0=بم،1=وسط،2=زیر
    status.textContent='گوش کن…';
    await sleep(400);
    for(const p of pat){ctx.sfx.drum(p);await sleep(420);}
    status.textContent='حالا تکرار کن!';
    const got=[];
    await new Promise(res=>{
      const handler=e=>{
        if(!e.target.closest('#drum'))return;
        const now=Date.now();
        got.push(now);
        ctx.sfx.drum(rnd(0,2));
        const view=box.querySelector('.rhythm-view');
        view.appendChild(el('span','beat'));
        if(got.length===len){box.removeEventListener('click',handler);res();}
      };
      box.addEventListener('click',handler);
    });
    const ok=got.length===len;
    t.hit(ok);if(!ok)t.miss();
    ctx.sfx[ok?'good':'soft']();
    status.textContent=ok?'عالی! ریتم را گرفتی.':'دوباره گوش کن و آرام بزن.';
    box.querySelector('.rhythm-view').innerHTML='';
    await sleep(600);
  }
  t.finish({kind:'rhythm'});
};

/* ================= ۲۳) نقشهٔ ایران ================= */
M.geo=ctx=>{
  const t=createTracker(ctx);
  const count=ctx.p.count;
  const picks=shuffled(PROVINCES).slice(0,count);
  let k=0;
  const box=el('div','geo-box');
  box.innerHTML=`<p class="geo-goal">🗺 استان را روی نقشهٔ ساده پیدا کن</p>
   <div class="iran-map">${PROVINCES.map((p,i)=>`<button class="prov" data-i="${i}" style="left:${p.x}%;top:${p.y}%" title="؟"></button>`).join('')}
   <svg class="iran-outline" viewBox="0 0 100 100" preserveAspectRatio="none"><path d="M8,14 L18,8 L30,10 L38,6 L48,10 L58,8 L68,12 L78,10 L90,16 L92,26 L88,34 L92,44 L88,54 L92,64 L84,70 L74,74 L66,84 L56,86 L48,80 L40,76 L32,70 L24,64 L16,56 L10,46 L8,34 Z"/></svg></div>
   <div class="geo-q"></div>`;
  ctx.host.appendChild(box);
  const q=box.querySelector('.geo-q');
  const render=()=>{
    const p=picks[k];
    q.innerHTML=`<p class="q-text">🔎 ${p.clue}</p><p class="q-hint">مرکزِ این استان: <b>؟</b></p>`;
    ctx.narrate(p.clue);
    const btns=[...box.querySelectorAll('.prov')];
    btns.forEach(b=>{
      b.onclick=()=>{
        const chosen=PROVINCES[+b.dataset.i];
        const ok=chosen.n===p.n;
        b.classList.add(ok?'right':'wrong');
        t.hit(ok);if(!ok)t.miss();
        ctx.sfx[ok?'good':'soft']();
        if(ok){b.title=p.n;q.querySelector('.q-hint').innerHTML=`مرکزِ این استان: <b>${p.c}</b>`;}
        feedback(q,ok,ok?`آفرین! ${p.n} با مرکزِ ${p.c}.`:`این ${chosen.n} بود. ${p.n} جای دیگری است.`);
        if(ok){const nx=el('button','btn primary',k+1<count?'استانِ بعدی':'تمام');
          nx.onclick=()=>{k++;k<count?render():t.finish({kind:'geo'});};q.appendChild(nx);}
      };
    });
  };
  render();
};

/* ================= ۲۴) خطِ زمان ================= */
M.timeline=ctx=>{
  const t=createTracker(ctx);
  const picks=shuffled(TIMELINE).slice(0,ctx.p.count);
  const sorted=[...picks].sort((a,b)=>a.y-b.y);
  const box=el('div','tl-box');
  box.innerHTML=`<p class="tl-goal">⏳ رویدادها را از قدیمی‌ترین به جدیدترین بچین</p><div class="tl-slots"></div><div class="tl-pool"></div>`;
  ctx.host.appendChild(box);
  const slots=box.querySelector('.tl-slots'),pool=box.querySelector('.tl-pool');
  sorted.forEach(()=>slots.appendChild(el('span','tl-slot','—')));
  let pos=0;
  shuffled(picks).forEach(ev=>{
    const b=el('button','tl-chip',ev.t);
    b.onclick=()=>{
      if(b.disabled)return;
      const ok=sorted[pos]===ev;
      t.hit(ok);
      if(ok){b.disabled=true;slots.children[pos].textContent=`${ev.y<0?Math.abs(ev.y)+' پ.م':ev.y} — ${ev.t}`;slots.children[pos].classList.add('filled');pos++;ctx.sfx.pick();
        if(pos===sorted.length){ctx.sfx.great();feedback(box,true,'خطِ زمان درست چیده شد!');
          const nx=el('button','btn primary','تمام');nx.onclick=()=>t.finish({kind:'timeline'});box.appendChild(nx);}
      }else{t.miss();b.classList.add('shake');ctx.sfx.soft();setTimeout(()=>b.classList.remove('shake'),400);}
    };
    pool.appendChild(b);
  });
};

/* ================= ۲۵) بشقابِ سلامت ================= */
M.food=ctx=>{
  const t=createTracker(ctx);
  const box=el('div','food-box');
  box.innerHTML=`<p class="food-goal">🍽 یک بشقابِ سالم بساز: از هر گروه یکی (جز «کمتر بخور»)</p>
   <div class="plate"><span>🍽</span></div><div class="food-pool"></div><div class="food-check"></div>`;
  ctx.host.appendChild(box);
  const pool=box.querySelector('.food-pool'),plate=box.querySelector('.plate');
  const chosen=[];
  shuffled(FOODS).forEach(f=>{
    const b=el('button','food-chip',`${f.e} ${f.n}`);
    b.onclick=()=>{
      if(chosen.includes(f.n))return;
      chosen.push(f.n);b.classList.add('used');
      const c=el('span','plate-item',f.e);plate.appendChild(c);
      ctx.sfx.tap();
      if(f.g==='کمتر بخور'){t.hit(false);t.miss();ctx.sfx.soft();
        feedback(box,false,`${f.n} را کمتر بخور؛ بشقابِ سالم با آن کامل نمی‌شود.`);}
      else{t.hit(true);ctx.sfx.pick();}
      const groups=new Set(chosen.map(n=>FOODS.find(x=>x.n===n).g));
      if(['غلات','پروتئین','میوه و سبزی','لبنیات'].every(g=>groups.has(g))){
        ctx.sfx.great();feedback(box,true,'بشقابِ کامل! غلات + پروتئین + میوه و سبزی + لبنیات.');
        const nx=el('button','btn primary','تمام');nx.onclick=()=>t.finish({kind:'food'});box.appendChild(nx);
      }
    };
    pool.appendChild(b);
  });
};

/* ================= ۲۶) خطِ خوش (کشیدن روی راهنما) ================= */
M.trace=ctx=>paintBoard(ctx,{prompt:`«${WORDS[ctx.p.wi].w}» را روی راهنما بنویس`,guide:WORDS[ctx.p.wi].w,kind:'trace'});

/* ================= ۲۷) نقاشی ================= */
M.draw=ctx=>paintBoard(ctx,{prompt:ctx.p.draw.t,tip:ctx.p.draw.tip,kind:'draw'});

function paintBoard(ctx,{prompt,tip='',guide='',kind}){
  const t=createTracker(ctx);
  const box=el('div','paint-box');
  box.innerHTML=`<p class="paint-prompt">🎨 ${prompt}</p>${tip?`<p class="paint-tip">${tip}</p>`:''}
   <div class="canvas-wrap"><canvas width="640" height="420"></canvas>${guide?`<span class="guide-word">${guide}</span>`:''}</div>
   <div class="paint-tools"></div><div class="paint-actions"></div>`;
  ctx.host.appendChild(box);
  const canvas=box.querySelector('canvas'),g=canvas.getContext('2d');
  g.lineCap='round';g.lineJoin='round';g.fillStyle='#fffdf7';g.fillRect(0,0,640,420);
  const tools=box.querySelector('.paint-tools');
  let color='#ff6b9d',size=6,mode='pen';
  const colors=['#ff6b9d','#5b8def','#5ec26a','#ffb020','#8b6cff','#2b2d42','#ffffff'];
  colors.forEach(c=>{const b=el('button','color-dot');b.style.background=c;
    b.onclick=()=>{color=c;mode='pen';tools.querySelectorAll('.color-dot').forEach(x=>x.classList.remove('on'));b.classList.add('on');};
    tools.appendChild(b);});
  [3,8,18].forEach(s=>{const b=el('button','size-dot',s===3?'نوک ریز':s===8?'قلم':'قلم‌مو');
    b.onclick=()=>{size=s;mode='pen';};tools.appendChild(b);});
  const er=el('button','tool-btn','🧽 پاک‌کن');er.onclick=()=>{mode='erase';};tools.appendChild(er);
  const clear=el('button','tool-btn','🗑 پاک کردن همه');clear.onclick=()=>{g.fillStyle='#fffdf7';g.fillRect(0,0,640,420);};tools.appendChild(clear);
  let drawing=false,lx=0,ly=0;
  const pos=e=>{const r=canvas.getBoundingClientRect();const p=e.touches?e.touches[0]:e;
    return [(p.clientX-r.left)*640/r.width,(p.clientY-r.top)*420/r.height];};
  const start=e=>{drawing=true;[lx,ly]=pos(e);e.preventDefault();};
  const move=e=>{if(!drawing)return;const [x,y]=pos(e);
    g.strokeStyle=mode==='erase'?'#fffdf7':color;g.lineWidth=mode==='erase'?size*3:size;
    g.beginPath();g.moveTo(lx,ly);g.lineTo(x,y);g.stroke();lx=x;ly=y;e.preventDefault();};
  const end=()=>{drawing=false;};
  canvas.addEventListener('mousedown',start);canvas.addEventListener('mousemove',move);
  window.addEventListener('mouseup',end);
  canvas.addEventListener('touchstart',start,{passive:false});canvas.addEventListener('touchmove',move,{passive:false});
  canvas.addEventListener('touchend',end);
  const done=el('button','btn primary','تمام شد ✨');
  const save=el('button','btn ghost','💾 در گالری نگه دار');
  const say=el('button','btn ghost','🎤 دربارهٔ نقاشی‌ام بگو');
  box.querySelector('.paint-actions').append(done,save,say);
  save.onclick=()=>{
    try{const url=canvas.toDataURL('image/png');
      ctx.state.explorer.gallery.unshift({url,title:prompt,ts:Date.now()});
      ctx.state.explorer.gallery=ctx.state.explorer.gallery.slice(0,24);
      feedback(box,true,'در گالریِ تو ذخیره شد.');ctx.sfx.chime();
    }catch{feedback(box,false,'ذخیره در این دستگاه ممکن نشد.');}
  };
  say.onclick=async()=>{await ctx.listenFree(prompt);t.hit(true);};
  done.onclick=()=>{t.hit(true);t.finish({kind});};
}

/* ================= ۲۸) آزمایشگاه ================= */
M.experiment=ctx=>{
  const t=createTracker(ctx);
  const e=ctx.p.exp;
  const box=el('div','lab-box');
  box.innerHTML=`<h3 class="lab-title">🧪 ${e.n}</h3><p class="lab-why">${e.why}</p>
   <div class="lab-need"><small>وسایل:</small><p>${e.need}</p></div>
   <ol class="lab-steps">${e.steps.map(s=>`<li><button class="step-check"></button><span>${s}</span></li>`).join('')}</ol>
   <div class="lab-q"></div>`;
  ctx.host.appendChild(box);
  ctx.narrate(`آزمایشِ امروز: ${e.n}. ${e.why} وسایل لازم: ${e.need}`);
  let done=0;
  on(box,'click','.step-check',b=>{
    if(b.classList.contains('on'))return;
    b.classList.add('on');b.textContent='✓';done++;ctx.sfx.pick();
    if(done===e.steps.length) showQ();
  });
  const showQ=()=>{
    const q=box.querySelector('.lab-q');
    q.innerHTML=`<p class="q-text">🔬 ${e.ask}</p>`;
    options(q,['فهمیدم و جواب دارم','هنوز انجام ندادم','سؤال دارم'],(i,b)=>{
      b.classList.add(i===0?'right':'soft');
      t.hit(i===0);if(i!==0)t.miss();
      if(i===2){feedback(q,true,'سؤالت را بلند بپرس؛ کنجکاوی بهترین ابزارِ دانشمند است.');}
      const sp=el('button','btn ghost','🎤 مشاهداتم را بگو');
      sp.onclick=async()=>{await ctx.listenFree(e.ask);t.hit(true);finish();};
      q.appendChild(sp);
      const nx=el('button','btn primary','تمام');nx.onclick=finish;q.appendChild(nx);
    });
  };
  const finish=()=>t.finish({kind:'experiment',steps:done});
};

/* ================= ۲۹) کارگاهِ ساختن ================= */
M.craft=ctx=>{
  const t=createTracker(ctx);
  const c=ctx.p.craft;
  const box=el('div','craft-box');
  box.innerHTML=`<h3 class="craft-title">🛠 ${c.n}</h3><p class="craft-learn">یادگیری: ${c.learn}</p>
   <ol class="craft-steps">${c.steps.map(s=>`<li><button class="step-check"></button><span>${s}</span></li>`).join('')}</ol>
   <div class="craft-photo"><button class="btn ghost">🎤 نتیجه را توصیف کن</button></div>`;
  ctx.host.appendChild(box);
  ctx.narrate(`کارگاهِ امروز: ${c.n}. ${c.learn}`);
  let done=0;
  on(box,'click','.step-check',b=>{
    if(b.classList.contains('on'))return;
    b.classList.add('on');b.textContent='✓';done++;ctx.sfx.pick();
    if(done===c.steps.length){t.hit(true);ctx.sfx.great();
      feedback(box,true,'تمام شد! حالا نتیجه را توصیف کن.');}
  });
  box.querySelector('.craft-photo button').onclick=async()=>{
    await ctx.listenFree(`دربارهٔ ${c.n} حرف بزن: چه ساختی و چطور بود؟`);
    t.hit(true);t.finish({kind:'craft'});
  };
  const done2=el('button','btn primary','تمام');done2.onclick=()=>t.finish({kind:'craft'});
  box.appendChild(done2);
};

/* ================= ۳۰) بلند بگو (سخنرانی کوتاه) ================= */
M.speak=ctx=>{
  const t=createTracker(ctx);
  const s=ctx.p.speak;
  const box=el('div','speak-box');
  box.innerHTML=`<p class="speak-topic">🎙 ${s.t}</p><p class="speak-tip">💡 ${s.tip}</p>
   <div class="speak-timer"><b>45</b></div>
   <div class="speak-actions">
     <button class="btn mic" id="rec">🎤 شروع ضبط</button>
     <button class="btn ghost" id="play" disabled>▶ گوش دادن به صدایم</button>
     <button class="btn ghost" id="text">✍ می‌خواهم بنویسم</button>
   </div><div class="speak-log"></div>`;
  ctx.host.appendChild(box);
  ctx.narrate(`${s.t} ${s.tip} هر وقت آماده بودی دکمهٔ ضبط را بزن.`);
  let time=45,recorder=null,url=null,timer=null;
  const tm=box.querySelector('.speak-timer b');
  box.querySelector('#rec').onclick=async()=>{
    time=45;tm.textContent=time;
    timer=setInterval(()=>{time--;tm.textContent=time;if(time<=0)stop();},1000);
    const r=await ctx.startRecorder();
    if(!r.ok){box.querySelector('.speak-log').innerHTML='<p>ضبط در این دستگاه ممکن نیست؛ فقط بلند حرف بزن، بعد دکمهٔ «تمام» را بزن.</p>';clearInterval(timer);return;}
    recorder=r;
    box.querySelector('.speak-log').innerHTML='<p class="rec-live">● در حال ضبط… هر وقت تمام شد، دوباره همان دکمه را بزن.</p>';
    box.querySelector('#rec').textContent='⏹ پایان ضبط';
    box.querySelector('#rec').onclick=stop;
  };
  const stop=async()=>{
    clearInterval(timer);
    if(recorder){const r=await recorder.stop();url=r.url;box.querySelector('#play').disabled=false;}
    box.querySelector('#rec').textContent='🎤 دوباره ضبط کن';
    box.querySelector('.speak-log').innerHTML='<p>ضبط تمام شد. به صدای خودت گوش بده؛ بعد برای خانواده اجرا کن.</p>';
    t.hit(true);
  };
  box.querySelector('#play').onclick=()=>{
    if(!url)return;
    const a=new Audio(url);a.play().catch(()=>{});
    box.querySelector('.speak-log').innerHTML='<p>در حال پخشِ صدای تو…</p>';
  };
  box.querySelector('#text').onclick=()=>{
    const ta=el('textarea','speak-text');ta.placeholder='این‌جا بنویس…';
    box.querySelector('.speak-log').innerHTML='';box.querySelector('.speak-log').appendChild(ta);
    const ok=el('button','btn primary','نوشتم');
    ok.onclick=()=>{if(ta.value.trim().length>3){t.hit(true);t.extra.wrote=true;t.finish({kind:'speak'});}};
    box.querySelector('.speak-log').appendChild(ok);
  };
  const done=el('button','btn primary','بلند گفتم ✅');
  done.onclick=()=>{t.hit(true);t.finish({kind:'speak'});};
  box.appendChild(done);
};

/* ================= ۳۱) بدنِ قوی ================= */
M.move=ctx=>{
  const t=createTracker(ctx);
  const m=ctx.p.move;
  const box=el('div','move-box');
  box.innerHTML=`<p class="move-task">🤸 ${m.t}</p><p class="move-why">${m.why}</p>
   <div class="move-counter"><b>0</b><small>بار</small></div>
   <div class="move-actions"><button class="btn big primary" id="plus">+ یکی انجام دادم</button></div>`;
  ctx.host.appendChild(box);
  ctx.narrate(m.t);
  const target=10;
  const b=box.querySelector('.move-counter b');
  let n=0;
  box.querySelector('#plus').onclick=()=>{
    n++;b.textContent=n;ctx.sfx.drum(1);
    if(n>=target){t.hit(true);ctx.sfx.great();
      feedback(box,true,'آفرین! بدنت قوی‌تر شد. فردا ده تا بیشتر.');
      const nx=el('button','btn primary','تمام');nx.onclick=()=>t.finish({kind:'move'});box.appendChild(nx);}
  };
};

/* ================= ۳۲) نفسِ آرام ================= */
M.breathe=async ctx=>{
  const t=createTracker(ctx);
  const box=el('div','breath-box');
  box.innerHTML=`<p class="breath-goal">🌬 پنج نفسِ آرام: چهار ثانیه دم، شش ثانیه بازدم</p>
   <div class="breath-circle"><span>نفس</span></div><div class="breath-count">۰ / ۵</div>`;
  ctx.host.appendChild(box);
  const circle=box.querySelector('.breath-circle'),cnt=box.querySelector('.breath-count');
  await ctx.narrate('آرام بنشین. شانه‌ها را شل کن. با من نفس بکش.');
  for(let i=1;i<=5;i++){
    circle.classList.add('in');circle.querySelector('span').textContent='دم…';
    ctx.sfx.breath();
    await sleep(4000);
    circle.classList.remove('in');circle.querySelector('span').textContent='بازدم…';
    await sleep(6000);
    cnt.textContent=`${'۰۱۲۳۴۵'[i]} / ۵`;
    t.hit(true);
  }
  await ctx.narrate('آفرین. حالا بدنت آرام‌تر است.');
  t.finish({kind:'breathe'});
};

/* ================= ۳۳) کارتِ احساس ================= */
M.emotion=ctx=>{
  const t=createTracker(ctx);
  const e=ctx.p.emo;
  const box=el('div','emo-box');
  box.innerHTML=`<div class="emo-face">${e.face}</div><p class="emo-sit">${e.sit}</p>
   <p class="emo-name">اسمِ این حس: <b>${e.feeling}</b></p><div class="emo-opts"></div>`;
  ctx.host.appendChild(box);
  ctx.narrate(`${e.sit} به نظرت چه حسی دارد؟ اسمِ این حس ${e.feeling} است. حالا بهترین کار چیست؟`);
  options(box.querySelector('.emo-opts'),e.options.map(o=>o.t),(i,b)=>{
    const ok=i===e.best;
    b.classList.add(ok?'right':'wrong');
    if(!ok)box.querySelectorAll('.opt')[e.best].classList.add('right');
    t.hit(ok);if(!ok)t.miss();
    ctx.sfx[ok?'good':'soft']();
    feedback(box,ok,e.options[i].r);
    const strat=el('div','emo-strategy');
    strat.innerHTML=`<span>🧰</span><p>${e.strategy}</p>`;
    box.appendChild(strat);
    const sp=el('button','btn ghost','🎤 تو چه کار می‌کردی؟ بگو');
    sp.onclick=async()=>{await ctx.listenFree('تو در این موقعیت چه کار می‌کردی؟');t.hit(true);finish();};
    box.appendChild(sp);
    const nx=el('button','btn primary','تمام');nx.onclick=finish;box.appendChild(nx);
  });
  const finish=()=>t.finish({kind:'emotion'});
};

/* ================= ۳۴) دو راهی ================= */
M.dilemma=ctx=>{
  const t=createTracker(ctx);
  const d=ctx.p.dil;
  const box=el('div','dil-box');
  box.innerHTML=`<div class="dil-scene">🌗</div><p class="dil-sit">${d.situation}</p><div class="dil-opts"></div>`;
  ctx.host.appendChild(box);
  ctx.narrate(`${d.situation} سه راه داری. خوب فکر کن؛ هیچ‌کس نمی‌گوید کدام درست است، تو انتخاب می‌کنی.`);
  options(box.querySelector('.dil-opts'),d.options.map(o=>o.t),(i,b)=>{
    const chosen=d.options[i],ok=i===d.best;
    b.classList.add(ok?'right':'chosen');
    t.hit(ok);
    t.extra.choice=i;
    ctx.sfx[ok?'good':'soft']();
    const cons=el('div','dil-consequence');
    cons.innerHTML=`<p><b>چه می‌شود:</b> ${chosen.r}</p>`;
    box.appendChild(cons);
    if(!ok){
      const best=el('div','dil-best');
      best.innerHTML=`<p><b>راهی که بیشتر آدم‌های دانا انتخاب می‌کنند:</b> ${d.options[d.best].t}</p>`;
      box.appendChild(best);
    }
    const vals=el('div','dil-values');
    vals.innerHTML=`<small>ارزش‌های این موقعیت:</small>`+d.values.map(v=>`<span class="chip">${v}</span>`).join('');
    box.appendChild(vals);
    const ask=el('div','dil-ask');
    ask.innerHTML=`<p>❓ ${d.ask}</p>`;
    box.appendChild(ask);
    const sp=el('button','btn mic','🎤 جوابم را بگو');
    sp.onclick=async()=>{await ctx.listenFree(d.ask);t.hit(true);finish();};
    box.appendChild(sp);
    const nx=el('button','btn primary','تمام');nx.onclick=finish;box.appendChild(nx);
  });
  const finish=()=>t.finish({kind:'dilemma'});
};

/* ================= ۳۵) ضرب‌المثل ================= */
M.proverb=ctx=>{
  const t=createTracker(ctx);
  const p=ctx.p.prov;
  const box=el('div','prov-box');
  box.innerHTML=`<div class="prov-scroll"><p>«${p.p}»</p></div><p class="prov-q">معنی‌اش چیست؟</p><div class="prov-opts"></div>`;
  ctx.host.appendChild(box);
  ctx.narrate(`یک ضرب‌المثلِ قدیمی: ${p.p} به نظرت یعنی چه؟`);
  const distract=pools.PROVERBS.filter(x=>x!==p).sort(()=>Math.random()-.5).slice(0,3).map(x=>x.m);
  options(box.querySelector('.prov-opts'),optsWith(p.m,distract),(i,b)=>{
    const chosen=[...box.querySelectorAll('.opt')][i].textContent.replace(/^\d/,'').trim();
    const ok=chosen===p.m;
    b.classList.add(ok?'right':'wrong');
    t.hit(ok);if(!ok)t.miss();
    ctx.sfx[ok?'good':'soft']();
    feedback(box,ok,ok?`درست! ${p.m}`:`معنیِ درست: ${p.m}`);
    const sp=el('button','btn ghost','🎤 یک مثال از زندگی خودم می‌گویم');
    sp.onclick=async()=>{await ctx.listenFree('یک مثال از زندگی خودت برای این ضرب‌المثل بگو.');t.hit(true);t.finish({kind:'proverb'});};
    box.appendChild(sp);
    const nx=el('button','btn primary','تمام');nx.onclick=()=>t.finish({kind:'proverb'});box.appendChild(nx);
  });
};

/* ================= ۳۶) کارِ واقعی ================= */
M.real=ctx=>{
  const t=createTracker(ctx);
  const task=ctx.p.task;
  const box=el('div','real-box');
  box.innerHTML=`<div class="real-badge">🌍 کارِ واقعی</div>
   <p class="real-task">${task.t}</p><p class="real-why"><b>چرا؟</b> ${task.why}</p>
   <div class="real-steps">
     <label><input type="checkbox" id="r1"> انجامش دادم</label>
     <label><input type="checkbox" id="r2"> به یک نفر نشان دادم یا گفتم</label>
   </div>
   <div class="real-actions">
     <button class="btn mic" id="say">🎤 نتیجه را بگو</button>
     <button class="btn ghost" id="later">⏰ بعداً انجام می‌دهم</button>
   </div>`;
  ctx.host.appendChild(box);
  ctx.narrate(`حالا نوبتِ کارِ واقعی است: ${task.t} ${task.why}`);
  const done=()=>{
    const a=box.querySelector('#r1').checked,b=box.querySelector('#r2').checked;
    t.hit(a);if(b)t.extra.showed=true;
    t.finish({kind:'real',did:a,showed:b});
  };
  box.querySelector('#say').onclick=async()=>{
    await ctx.listenFree(`دربارهٔ «${task.t}» بگو: چه دیدی یا چه کردی؟`);
    box.querySelector('#r1').checked=true;t.hit(true);
    const nx=el('button','btn primary','تمام ✅');nx.onclick=done;box.appendChild(nx);
  };
  box.querySelector('#later').onclick=()=>{t.finish({kind:'real',did:false});};
};

export const MECHANICS=M;
export const hasMechanic=id=>typeof M[id]==='function';
export const mechanicIds=()=>Object.keys(M);

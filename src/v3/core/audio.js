// باغ قصه‌ها ۳ — صدا، گفتار و افکت‌ها
// اولویت گوینده: فایل صوتی همراه برنامه → گویندهٔ بومی اندروید → گویندهٔ مرورگر → متن.
// هیچ متنی بدون خوانده‌شدن گم نمی‌شود: اگر صدایی نبود، همان متن روی صفحه روشن می‌ماند.

export const VOICES=[
  {id:'auto',name:'خودکار'},
  {id:'device',name:'گویندهٔ دستگاه'},
  {id:'off',name:'بی‌صدا (فقط متن)'}
];

/* ---------------- صداهای کوچک (ساخته‌شده با WebAudio، بدون فایل) ---------------- */
let ctx=null;
export function audioCtx(){
  if(ctx) return ctx;
  const AC=window.AudioContext||window.webkitAudioContext;
  if(!AC) return null;
  ctx=new AC();
  return ctx;
}
export function resumeAudio(){const c=audioCtx();if(c&&c.state==='suspended')c.resume().catch(()=>{});}
function tone({freq=440,dur=0.18,type='sine',gain=0.18,slide=0,delay=0}={}){
  const c=audioCtx();if(!c) return;
  const t0=c.currentTime+delay;
  const osc=c.createOscillator(),g=c.createGain();
  osc.type=type;osc.frequency.setValueAtTime(freq,t0);
  if(slide) osc.frequency.exponentialRampToValueAtTime(Math.max(60,freq+slide),t0+dur);
  g.gain.setValueAtTime(0.0001,t0);
  g.gain.exponentialRampToValueAtTime(gain,t0+0.02);
  g.gain.exponentialRampToValueAtTime(0.0001,t0+dur);
  osc.connect(g).connect(c.destination);osc.start(t0);osc.stop(t0+dur+0.05);
}
function noise({dur=0.2,gain=0.12,delay=0,filter=1200}={}){
  const c=audioCtx();if(!c) return;
  const len=Math.max(1,Math.floor(c.sampleRate*dur));
  const buf=c.createBuffer(1,len,c.sampleRate),d=buf.getChannelData(0);
  for(let i=0;i<len;i++) d[i]=(Math.random()*2-1)*(1-i/len);
  const src=c.createBufferSource();src.buffer=buf;
  const bq=c.createBiquadFilter();bq.type='lowpass';bq.frequency.value=filter;
  const g=c.createGain();g.gain.value=gain;
  src.connect(bq).connect(g).connect(c.destination);src.start(c.currentTime+delay);
}
export const sfx={
  tap(){tone({freq:520,dur:0.07,gain:0.1,type:'triangle'});},
  pick(){tone({freq:660,dur:0.09,gain:0.12,type:'triangle'});tone({freq:990,dur:0.08,gain:0.07,delay:0.05});},
  good(){[523,659,784,1047].forEach((f,i)=>tone({freq:f,dur:0.16,gain:0.13,delay:i*0.075,type:'triangle'}));},
  great(){[523,659,784,1047,1319].forEach((f,i)=>tone({freq:f,dur:0.2,gain:0.14,delay:i*0.08,type:'sine'}));},
  soft(){tone({freq:330,dur:0.22,gain:0.09,type:'sine'});tone({freq:247,dur:0.3,gain:0.07,delay:0.1,type:'sine'});},
  hint(){tone({freq:880,dur:0.1,gain:0.07,type:'sine'});tone({freq:1175,dur:0.14,gain:0.06,delay:0.08,type:'sine'});},
  level(){[392,523,659,784,1047,1319].forEach((f,i)=>tone({freq:f,dur:0.28,gain:0.13,delay:i*0.09}));noise({dur:0.4,gain:0.05,delay:0.1,filter:3000});},
  whoosh(){noise({dur:0.35,gain:0.08,filter:900});},
  thud(){tone({freq:120,dur:0.2,gain:0.16,type:'sine',slide:-40});noise({dur:0.12,gain:0.08,filter:400});},
  drum(pitch=0){tone({freq:150+pitch*40,dur:0.13,gain:0.2,type:'sine',slide:-60});noise({dur:0.06,gain:0.06,filter:600});},
  bell(){[1047,1568,2093].forEach((f,i)=>tone({freq:f,dur:0.5,gain:0.07,delay:i*0.04,type:'sine'}));},
  breath(){noise({dur:1.6,gain:0.045,filter:700});},
  chime(){tone({freq:1319,dur:0.4,gain:0.08,type:'sine'});tone({freq:1760,dur:0.5,gain:0.05,delay:0.06,type:'sine'});},
  note(i=0){tone({freq:262*Math.pow(2,i/12),dur:0.35,gain:0.16,type:'triangle'});}
};
export function buzz(pattern){try{navigator.vibrate&&navigator.vibrate(pattern);}catch{}}

/* ---------------- پل گفتار بومی اندروید ---------------- */
const bridge=()=>window.BaghVoice||null;
export const nativeVoice=()=>!!bridge();
window.__baghVoiceEvents=window.__baghVoiceEvents||{};
window.__baghVoiceEvent=function(payload){
  let data=payload;
  if(typeof payload==='string'){try{data=JSON.parse(payload);}catch{data={};}}
  const cb=window.__baghVoiceEvents[data.id];
  if(!cb) return;
  if(data.type==='done'||data.type==='error') delete window.__baghVoiceEvents[data.id];
  cb(data);
};

let seq=0;
function bridgeCall(method,args){
  const b=bridge();
  if(!b||typeof b[method]!=='function') return null;
  const id='c'+(++seq);
  try{b[method](JSON.stringify({id,...args}));return id;}catch{return null;}
}
export function nativeVoicesInfo(){
  return new Promise(resolve=>{
    const id=bridgeCall('voices',{});
    if(!id) return resolve(null);
    const t=setTimeout(()=>resolve(null),1500);
    window.__baghVoiceEvents[id]=data=>{clearTimeout(t);resolve(data.voices||null);};
  });
}

/* ---------------- وب: گفتار مرورگر ---------------- */
const synth=()=>window.speechSynthesis||null;
export function webVoices(){
  const s=synth();if(!s) return [];
  try{return s.getVoices().filter(v=>/^fa/i.test(v.lang)||/persian|farsi/i.test(v.name));}catch{return [];}
}
let voicesReady=false;
export function initSpeech(){
  const s=synth();if(!s) return;
  const ready=()=>{voicesReady=true;};
  try{s.addEventListener('voiceschanged',ready);}catch{}
  try{if(s.getVoices().length) ready();}catch{}
}
function pickWebVoice(){
  const list=webVoices();
  if(list.length) return list.find(v=>/IR/i.test(v.lang))||list[0];
  return null;
}

/* ---------------- صف روایت ---------------- */
export class Narrator{
  constructor(state,opts={}){
    this.state=state;this.onState=opts.onState||(()=>{});
    this.token=0;this.speaking=false;this.mode='auto';this.source='none';
    this.audio=null;this.clips=opts.clips||{};
  }
  get enabled(){return this.state.settings.sound&&this.state.settings.voice!=='off';}
  get rate(){return (this.state.settings.rate||1)*(this.mode==='toddler'?0.82:1);}
  stop(){
    this.token++;this.speaking=false;
    if(this.audio){try{this.audio.pause();this.audio.removeAttribute('src');this.audio.load();}catch{}}
    const b=bridge();if(b&&b.stop) try{b.stop();}catch{}
    const s=synth();if(s) try{s.cancel();}catch{}
    this.onState({phase:'idle'});
  }
  /** روایت یک متن بلند: جمله‌به‌جمله، با امکان روشن‌ماندن جملهٔ در حال خواندن */
  async narrate(text,{sentenceEl=null,clip=null,rate=null,pause=240}={}){
    this.stop();
    const token=this.token;
    if(!this.enabled){this.onState({phase:'idle',silent:true});return {spoken:false};}
    if(clip&&this.clips[clip]){
      const ok=await this.playClip(this.clips[clip],token);
      if(ok) return {spoken:true,source:'clip'};
    }
    const parts=splitSentences(text);
    for(let i=0;i<parts.length;i++){
      if(token!==this.token) return {spoken:false,aborted:true};
      sentenceEl&&highlight(sentenceEl,parts,i);
      const ok=await this.speakOne(parts[i],token,rate);
      if(token!==this.token) return {spoken:false,aborted:true};
      if(!ok){this.source='text';break;}
      this.source=this.mode==='native'?'device':'web';
      await sleep(pause);
    }
    this.onState({phase:'idle'});
    return {spoken:true,source:this.source};
  }
  async speakOne(text,token,rate){
    const speed=rate??this.rate;
    const b=bridge();
    if(b){
      const id=bridgeCall('speak',{text,rate:speed});
      if(id){
        this.mode='native';
        return await new Promise(res=>{
          const t=setTimeout(()=>res(false),Math.max(4000,text.length*260));
          window.__baghVoiceEvents[id]=data=>{
            if(data.type==='start'){this.onState({phase:'speaking',text});return;}
            clearTimeout(t);res(data.type!=='error');
          };
        });
      }
    }
    const s=synth();
    if(s){
      const v=pickWebVoice();
      const u=new SpeechSynthesisUtterance(text);
      u.lang=v?v.lang:'fa-IR';if(v)u.voice=v;
      u.rate=speed;u.pitch=1.02;
      return await new Promise(res=>{
        let settled=false;
        const done=ok=>{if(settled)return;settled=true;res(ok);};
        u.onstart=()=>{this.onState({phase:'speaking',text});};
        u.onend=()=>done(true);
        u.onerror=()=>done(false);
        setTimeout(()=>done(false),Math.max(4000,text.length*280));
        try{s.speak(u);}catch{done(false);}
      });
    }
    return false;
  }
  playClip(src,token){
    return new Promise(res=>{
      const a=new Audio(src);this.audio=a;
      a.onended=()=>{if(token===this.token)res(true);else res(false);};
      a.onerror=()=>res(false);
      this.onState({phase:'speaking',clip:src});
      a.play().catch(()=>res(false));
    });
  }
}

export function splitSentences(text){
  return String(text||'').split(/(?<=[.!؟…\n])\s+/).map(s=>s.trim()).filter(Boolean);
}
function highlight(el,parts,i){
  el.innerHTML=parts.map((p,idx)=>`<span class="nar-line ${idx<i?'past':''} ${idx===i?'now':''}">${escapeHtml(p)}</span>`).join(' ');
}
export const sleep=ms=>new Promise(r=>setTimeout(r,ms));
export const escapeHtml=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));

/* ---------------- شنیدن صدای کودک ---------------- */
export const canListen=()=>!!bridge()||!!(window.SpeechRecognition||window.webkitSpeechRecognition);
export function listenOnce({lang='fa-IR',timeout=9000,onLevel}={}){
  return new Promise(resolve=>{
    const b=bridge();
    if(b){
      const id=bridgeCall('listen',{lang,timeout});
      if(!id) return resolve({ok:false,reason:'no-native'});
      const t=setTimeout(()=>resolve({ok:false,reason:'timeout'}),timeout+3000);
      window.__baghVoiceEvents[id]=data=>{
        if(data.type==='level'){onLevel&&onLevel(data.level||0);return;}
        clearTimeout(t);
        if(data.type==='result') resolve({ok:true,text:data.text||''});
        else resolve({ok:false,reason:data.reason||'error'});
      };
      return;
    }
    const SR=window.SpeechRecognition||window.webkitSpeechRecognition;
    if(!SR) return resolve({ok:false,reason:'unsupported'});
    try{
      const r=new SR();r.lang=lang;r.interimResults=false;r.maxAlternatives=1;
      let done=false;
      const finish=v=>{if(done)return;done=true;try{r.stop();}catch{}resolve(v);};
      r.onresult=e=>finish({ok:true,text:e.results[0][0].transcript||''});
      r.onerror=e=>finish({ok:false,reason:e.error||'error'});
      r.onend=()=>finish({ok:false,reason:'silent'});
      setTimeout(()=>finish({ok:false,reason:'timeout'}),timeout);
      r.start();
    }catch{resolve({ok:false,reason:'denied'});}
  });
}

/** ضبط کوتاه صدا روی خود دستگاه برای پخش دوباره؛ چیزی آپلود نمی‌شود. */
export async function startRecorder(){
  if(!navigator.mediaDevices?.getUserMedia) return {ok:false,reason:'unsupported'};
  try{
    const stream=await navigator.mediaDevices.getUserMedia({audio:true});
    const MR=window.MediaRecorder;
    if(!MR) {stream.getTracks().forEach(t=>t.stop());return {ok:false,reason:'unsupported'};}
    const rec=new MR(stream);const chunks=[];
    rec.ondataavailable=e=>e.data.size&&chunks.push(e.data);
    rec.start();
    return {ok:true,stop:()=>new Promise(res=>{
      rec.onstop=()=>{stream.getTracks().forEach(t=>t.stop());
        res({ok:true,blob:new Blob(chunks,{type:rec.mimeType||'audio/webm'}),url:URL.createObjectURL(new Blob(chunks,{type:rec.mimeType||'audio/webm'}))});};
      rec.stop();
    })};
  }catch{
    return {ok:false,reason:'denied'};
  }
}

/* ---------------- افکت‌های بصری ---------------- */
let confettiRaf=0;
export function confetti(host,count=90,palette=['#ffd166','#ff6b9d','#5ec26a','#3ec1d3','#c77dff']){
  const canvas=document.createElement('canvas');
  canvas.className='fx-canvas';
  host.appendChild(canvas);
  const dpr=Math.min(2,window.devicePixelRatio||1);
  const w=canvas.width=host.clientWidth*dpr,h=canvas.height=host.clientHeight*dpr;
  const g=canvas.getContext('2d');
  const parts=Array.from({length:count},()=>({
    x:Math.random()*w,y:-20*dpr-Math.random()*h*0.4,
    vx:(Math.random()-0.5)*2.2*dpr,vy:(1.6+Math.random()*2.4)*dpr,
    r:(3+Math.random()*5)*dpr,a:Math.random()*Math.PI,va:(Math.random()-0.5)*0.24,
    c:palette[(Math.random()*palette.length)|0],sq:Math.random()>0.4
  }));
  let frames=0;
  cancelAnimationFrame(confettiRaf);
  const step=()=>{
    g.clearRect(0,0,w,h);frames++;
    for(const p of parts){
      p.x+=p.vx;p.y+=p.vy;p.a+=p.va;p.vy+=0.012*dpr;
      g.save();g.translate(p.x,p.y);g.rotate(p.a);g.fillStyle=p.c;
      if(p.sq) g.fillRect(-p.r,-p.r*0.6,p.r*2,p.r*1.2);
      else{g.beginPath();g.arc(0,0,p.r*0.7,0,7);g.fill();}
      g.restore();
    }
    if(frames<220&&parts.some(p=>p.y<h+40)) confettiRaf=requestAnimationFrame(step);
    else canvas.remove();
  };
  confettiRaf=requestAnimationFrame(step);
}
export function shake(el,strength=6){
  if(document.body.classList.contains('calm-motion')) return;
  el.animate([{transform:'translate3d(0,0,0)'},{transform:`translate3d(${strength}px,-3px,0)`},
    {transform:`translate3d(${-strength}px,3px,0)`},{transform:'translate3d(0,0,0)'}],
    {duration:280,easing:'ease-out'});
}
export function pop(el,scale=1.08){
  el.animate([{transform:'scale(1)'},{transform:`scale(${scale})`},{transform:'scale(1)'}],
    {duration:320,easing:'cubic-bezier(.2,1.4,.4,1)'});
}
export function sparkleAt(host,x,y,count=14){
  for(let i=0;i<count;i++){
    const s=document.createElement('i');s.className='fx-spark';
    s.style.left=x+'px';s.style.top=y+'px';
    const ang=(Math.PI*2*i)/count,dist=26+Math.random()*46;
    s.style.setProperty('--dx',Math.cos(ang)*dist+'px');
    s.style.setProperty('--dy',Math.sin(ang)*dist+'px');
    host.appendChild(s);setTimeout(()=>s.remove(),900);
  }
}
export function floatText(host,text,cls='fx-float'){
  const el=document.createElement('div');el.className=cls;el.textContent=text;
  host.appendChild(el);setTimeout(()=>el.remove(),1600);
}

export {VOICES as VOICE_MODES, listenOnce as listenOne};

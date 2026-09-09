// Only registered, user-approved fa-IR voices. Never substitute a rejected/system voice.
export const NARRATORS = Object.freeze([
  {id:'first', name:'صدای اول', registration:'voice-06'},
  {id:'second', name:'صدای دوم', registration:'voice-07'}
]);
export const NARRATION_ROLES = Object.freeze([
  {id:'explorer', title:'کودک ۸–۱۰ سال', description:'راهنمای ماجراجویی و قصه'},
  {id:'toddler', title:'کودک ۲–۳ سال', description:'بازی کوتاه، همراه بزرگ‌تر'},
  {id:'mother', title:'مادر', description:'راهنمای آرام و همراهانه'},
  {id:'father', title:'پدر', description:'همراه خانواده، دور یا نزدیک'}
]);
export function normalizeVoicePreferences(raw) {
  return Object.fromEntries(NARRATION_ROLES.map(({id})=>[id,raw?.[id]==='second'?'second':'first']));
}
export function narrationAsset(role,voice) {
  if(!NARRATION_ROLES.some(r=>r.id===role)) throw new Error('Unknown narration role');
  if(!NARRATORS.some(v=>v.id===voice)) throw new Error('Unknown narrator');
  return `./assets/v2/voices/${voice==='second'?'second/':''}${role}-welcome.mp3`;
}
export function createNarrationPlayer({createAudio=()=>new Audio(),onState=()=>{},onError=()=>{}}={}) {
  let audio=null,version=0;
  function stop(){
    version++;
    if(audio){audio.onended=null;audio.onerror=null;audio.pause();audio.removeAttribute('src');audio.load();audio=null;}
    onState('idle');
  }
  async function play(role,voice){
    const src=narrationAsset(role,voice);
    stop();const token=version;
    const current=createAudio();audio=current;current.preload='auto';current.src=src;
    const fail=()=>{if(token!==version)return;stop();onError('پخش صدا انجام نشد. صدای دستگاه و فایل صوتی را بررسی کن؛ سپس دوباره امتحان کن.');};
    current.onended=()=>{if(token===version)stop();};
    current.onerror=fail;
    onState('loading',role,voice);
    try {await current.play();if(token===version)onState('playing',role,voice);}
    catch {fail();}
  }
  return {play,stop};
}

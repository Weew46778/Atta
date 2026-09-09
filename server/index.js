import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import {DatabaseSync} from 'node:sqlite';
import {fileURLToPath} from 'node:url';
import {validEvent} from '../src/family/store.js';
export function createFamilyServer({directory=process.env.FAMILY_DATA_DIR||'.family-data',allowedOrigins=(process.env.ALLOWED_ORIGINS||'https://app.littlestar.local').split(',')}={}){
 fs.mkdirSync(directory,{recursive:true,mode:0o700});
 const keyPath=path.join(directory,'encryption.key');if(!fs.existsSync(keyPath))fs.writeFileSync(keyPath,crypto.randomBytes(32),{mode:0o600});const key=fs.readFileSync(keyPath);if(key.length!==32)throw Error('Invalid encryption key');
 const db=new DatabaseSync(path.join(directory,'family.sqlite'));db.exec('PRAGMA journal_mode=WAL; PRAGMA foreign_keys=ON; CREATE TABLE IF NOT EXISTS accounts (id TEXT PRIMARY KEY, username TEXT UNIQUE, salt TEXT, hash TEXT); CREATE TABLE IF NOT EXISTS events (account TEXT REFERENCES accounts(id) ON DELETE CASCADE, id TEXT, value TEXT, PRIMARY KEY(account,id));');
 const encrypt=value=>{let iv=crypto.randomBytes(12),cipher=crypto.createCipheriv('aes-256-gcm',key,iv);return Buffer.concat([iv,cipher.update(JSON.stringify(value),'utf8'),cipher.final(),cipher.getAuthTag()]).toString('base64');};
 const decrypt=value=>{let b=Buffer.from(value,'base64'),dec=crypto.createDecipheriv('aes-256-gcm',key,b.subarray(0,12));dec.setAuthTag(b.subarray(-16));return JSON.parse(Buffer.concat([dec.update(b.subarray(12,-16)),dec.final()]).toString('utf8'));};
 const hash=(p,s)=>crypto.scryptSync(p,s,64).toString('hex');const sessions=new Map(),rates=new Map();
 const token=id=>{for(const [t,s] of sessions)if(s.until<Date.now())sessions.delete(t);const t=crypto.randomBytes(32).toString('hex');sessions.set(t,{id,until:Date.now()+12*3600000});return t;};
 const validCredentials=b=>typeof b.username==='string'&&/^[a-zA-Z0-9_-]{3,40}$/.test(b.username)&&typeof b.password==='string'&&b.password.length>=10&&b.password.length<=128;
 const server=http.createServer(async(req,res)=>{
  const origin=req.headers.origin;const allowed=!origin||allowedOrigins.includes(origin)||(()=>{try{return new URL(origin).host===req.headers.host;}catch{return false;}})();
  res.setHeader('Cache-Control','no-store');res.setHeader('X-Content-Type-Options','nosniff');
  if(origin&&allowed){res.setHeader('Access-Control-Allow-Origin',origin);res.setHeader('Vary','Origin');res.setHeader('Access-Control-Allow-Headers','Content-Type, Authorization');res.setHeader('Access-Control-Allow-Methods','GET,POST,DELETE,OPTIONS');}
  const send=(code,data)=>{res.writeHead(code,{'Content-Type':'application/json; charset=utf-8'});res.end(JSON.stringify(data));};
  if(!allowed){send(403,{error:'این مبدأ برای سرور مجاز نشده است.'});return;}if(req.method==='OPTIONS'){res.writeHead(204);res.end();return;}
  const route=new URL(req.url,'http://server').pathname;
  if(route==='/api/health'&&req.method==='GET'){send(200,{ok:true,service:'hamrah-family',version:2});return;}
  let body={};
  try{if(['POST','DELETE'].includes(req.method)){let chunks=[],size=0;for await(const chunk of req){size+=chunk.length;if(size>1024*1024){send(413,{error:'حجم درخواست زیاد است.'});return;}chunks.push(chunk);}body=JSON.parse(Buffer.concat(chunks).toString('utf8')||'{}');if(!body||typeof body!=='object'||Array.isArray(body))throw Error();}}catch{send(400,{error:'درخواست معتبر نیست.'});return;}
  try{
   if(['/api/register','/api/login'].includes(route)&&req.method==='POST'){
    const ip=req.socket.remoteAddress;const rate=rates.get(ip)||{n:0,until:Date.now()+15*60000};if(rate.until<Date.now()){rate.n=0;rate.until=Date.now()+15*60000;}rate.n++;rates.set(ip,rate);if(rate.n>20){send(429,{error:'تلاش‌های زیادی انجام شده؛ ۱۵ دقیقه بعد امتحان کنید.'});return;}
    if(!validCredentials(body)){send(400,{error:'نام کاربری انگلیسی ۳ تا ۴۰ حرف و رمز ۱۰ تا ۱۲۸ نویسه لازم است.'});return;}
    const username=body.username.toLowerCase();let account=db.prepare('SELECT * FROM accounts WHERE username=?').get(username);
    if(route==='/api/register'){
     if(account){send(409,{error:'این نام کاربری قابل استفاده نیست.'});return;}
     const id=crypto.randomUUID(),salt=crypto.randomBytes(16).toString('hex');db.prepare('INSERT INTO accounts VALUES(?,?,?,?)').run(id,username,salt,hash(body.password,salt));send(201,{token:token(id),username});return;
    }
    const computed=hash(body.password,account?.salt||'constant-dummy-salt');
    if(!account||!crypto.timingSafeEqual(Buffer.from(computed,'hex'),Buffer.from(account.hash,'hex'))){send(401,{error:'نام کاربری یا رمز صحیح نیست.'});return;}
    send(200,{token:token(account.id),username});return;
   }
   const auth=(req.headers.authorization||'').replace(/^Bearer /,'');const session=sessions.get(auth);
   if(!session||session.until<Date.now()){sessions.delete(auth);send(401,{error:'ابتدا وارد حساب والدین شوید.'});return;}
   if(route==='/api/logout'&&req.method==='POST'){sessions.delete(auth);send(200,{ok:true});return;}
   if(route==='/api/events'&&req.method==='GET'){send(200,{events:db.prepare('SELECT value FROM events WHERE account=?').all(session.id).map(r=>decrypt(r.value))});return;}
   if(route==='/api/events'&&req.method==='POST'){
    if(!Array.isArray(body.events)||body.events.length>500||!body.events.every(validEvent)){send(400,{error:'ساختار گزارش معتبر نیست.'});return;}
    const existing=Number(db.prepare('SELECT count(*) AS n FROM events WHERE account=?').get(session.id).n);
    if(existing+body.events.length>10500){send(413,{error:'ظرفیت گزارش حساب پر شده است. ابتدا خروجی بگیرید.'});return;}
    db.exec('BEGIN');try{const insert=db.prepare('INSERT OR IGNORE INTO events VALUES(?,?,?)');for(const e of body.events)insert.run(session.id,e.id,encrypt(e));db.exec('COMMIT');}catch(e){db.exec('ROLLBACK');throw e;}send(200,{ok:true});return;
   }
   if(route==='/api/account'&&req.method==='DELETE'){
    const account=db.prepare('SELECT * FROM accounts WHERE id=?').get(session.id);if(typeof body.password!=='string'||body.password.length>128||!crypto.timingSafeEqual(Buffer.from(hash(body.password,account.salt),'hex'),Buffer.from(account.hash,'hex'))){send(403,{error:'برای حذف حساب رمز را دوباره وارد کنید.'});return;}
    db.prepare('DELETE FROM accounts WHERE id=?').run(session.id);for(const [t,s] of sessions)if(s.id===session.id)sessions.delete(t);send(200,{ok:true});return;
   }
   send(404,{error:'مسیر پیدا نشد.'});
  }catch{send(500,{error:'خطای سرور؛ دوباره تلاش کنید.'});}
 });
 server.on('close',()=>db.close());return server;
}
if(process.argv[1]===fileURLToPath(import.meta.url))createFamilyServer().listen(Number(process.env.PORT||8787),'0.0.0.0',()=>console.log('Family API listening on port '+(process.env.PORT||8787)));

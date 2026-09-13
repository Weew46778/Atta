// سرورِ سادهٔ دانلودِ APKها (بدونِ وابستگی) — برای گرفتنِ فایل‌ها از مرورگر
import http from 'node:http';
import {createReadStream,statSync,readdirSync,existsSync} from 'node:fs';
import path from 'node:path';

const ROOT=path.resolve('releases/v3');
const EXTRA=[path.resolve('releases/Bagh-Ghesse3-all.zip')].filter(f=>existsSync(f));
const PORT=Number(process.env.PORT||8000);
const TITLES={
  'Bagh-Ghesse3-explorer.apk':'کاوشگر — کودک ۸ تا ۱۰ سال (۲۴۰ مرحله)',
  'Bagh-Ghesse3-toddler.apk':'جوانه — کودک ۲ تا ۳ سال (گفتار و قصه)',
  'Bagh-Ghesse3-mother.apk':'همراهِ مادر — کوچینگ و برنامهٔ خانواده',
  'Bagh-Ghesse3-father.apk':'همراهِ پدر — چرخهٔ ۱۰ روز دوری / ۵ روز حضور',
  'Bagh-Ghesse3-all.zip':'بستهٔ کامل (هر چهار APK)',
  'SHA256SUMS.txt':' checksum برای کنترلِ سلامتِ فایل‌ها'
};
const MB=b=>(b/1048576).toFixed(2)+' MB';

function listing(){
  const files=[...readdirSync(ROOT).map(f=>path.join(ROOT,f)),...EXTRA]
    .filter(f=>statSync(f).isFile())
    .sort((a,b)=>path.basename(a).localeCompare(path.basename(b)));
  const rows=files.map(f=>{
    const n=path.basename(f),st=statSync(f);
    return `<li><a href="/${encodeURIComponent(n)}" download="${n}"><b>${n}</b>
      <span>${TITLES[n]||''}</span><em>${MB(st.size)}</em></a></li>`;
  }).join('\n');
  return `<!doctype html><html lang="fa" dir="rtl"><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>باغ قصه‌ها ۳ — دانلودِ APK</title>
<style>
 body{font:16px/1.9 system-ui,'Vazirmatn',Tahoma,sans-serif;background:#12132a;color:#fff;margin:0;padding:24px}
 h1{font-size:1.35rem} ul{list-style:none;padding:0;margin:0}
 li{margin:10px 0}
 a{display:flex;flex-wrap:wrap;gap:6px 14px;align-items:baseline;text-decoration:none;
   background:#1e2148;border:1px solid #38407f;border-radius:14px;padding:14px 16px;color:#fff}
 a:hover{background:#272c60}
 a b{font-size:1rem} a span{flex:1 1 220px;color:#b9c0ff;font-size:.9rem} a em{color:#8fe3b0;font-style:normal}
 p.note{color:#aab1e6;font-size:.85rem;max-width:60ch}
 code{background:#0008;padding:2px 6px;border-radius:6px}
</style>
<h1>🌳 باغ قصه‌ها ۳ — نسخهٔ ۳٫۰٫۰ (versionCode 300)</h1>
<p class="note">روی هر فایل بزن تا دانلود شود. روی گوشی، هنگامِ نصب، اجازهٔ «نصب از منابعِ ناشناس»
را به مرورگرت بده. هر چهار برنامه آفلاین کار می‌کنند، بدونِ تبلیغ، خرید درون‌برنامه‌ای یا چت.</p>
<ul>
${rows}
</ul>
<p class="note">کنترلِ سلامتِ فایل: <code>sha256sum -c SHA256SUMS.txt</code></p>
</html>`;
}

http.createServer((req,res)=>{
  const name=decodeURIComponent((req.url||'/').split('?')[0]);
  if(name==='/'||name==='') {
    res.writeHead(200,{'Content-Type':'text/html; charset=utf-8'});
    return res.end(listing());
  }
  const safe=path.basename(name);
  const file=[ROOT,path.resolve('releases')].map(d=>path.join(d,safe)).find(f=>{
    try{return statSync(f).isFile();}catch{return false;}
  });
  if(!file){res.writeHead(404,{'Content-Type':'text/plain; charset=utf-8'});return res.end('یافت نشد');}
  const st=statSync(file);
  res.writeHead(200,{
    'Content-Type':file.endsWith('.apk')?'application/vnd.android.package-archive':'application/octet-stream',
    'Content-Length':st.size,
    'Content-Disposition':`attachment; filename="${safe}"`,
    'Cache-Control':'no-store'
  });
  createReadStream(file).pipe(res);
}).listen(PORT,'0.0.0.0',()=>console.log(`Download server on http://0.0.0.0:${PORT} serving ${ROOT}`));

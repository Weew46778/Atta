(function(){
try{
 try{if(window.ATTA_SRV_IV){clearInterval(window.ATTA_SRV_IV);window.ATTA_SRV_IV=null;}}catch(e){}
 function grab(){
  var out=[],seen={};
  var els=document.querySelectorAll('[data-id]');
  for(var i=0;i<els.length;i++){
   var el=els[i];var id=el.getAttribute('data-id');
   if(!id||seen[id])continue;seen[id]=1;
   var n=el.querySelector('.server-name, .server-description, .server-body, .server-title');
   var nm=n?String(n.textContent).replace(/\s+/g,' ').trim().slice(0,60):'';
   out.push({id:id,name:nm});
  }
  if(out.length){AttaBridge.post('servers',JSON.stringify(out));return true;}
  var ti=(document.title||'');
  if(ti.indexOf('Just a moment')>=0){AttaBridge.post('diag','محافظ کلودفلر — چند ثانیه دیگر خودش رد می‌شود');return false;}
  if(location.pathname==='/server/'||location.pathname==='/server'){AttaBridge.post('diag','مستقیم روی صفحهٔ سرور افتادیم (اکانت تک‌سرور)');return true;}
  var bt=(document.body?String(document.body.innerText):'').replace(/\s+/g,' ').trim().slice(0,200);
  AttaBridge.post('diag','سروری در صفحهٔ فهرست پیدا نشد | عنوان: '+ti.slice(0,50)+' | متن: '+bt);
  return false;
 }
 if(grab())return;
 var n=0;
 window.ATTA_SRV_IV=setInterval(function(){n++;if(grab()||n>6){clearInterval(window.ATTA_SRV_IV);window.ATTA_SRV_IV=null;}},1200);
}catch(e){AttaBridge.post('err','servers:'+e)}
})();

(function(){try{
var ti=(document.title||'');
if(ti.indexOf('Just a moment')>=0){AttaBridge.post('diag','محافظ کلودفلر — کمی صبر کن؛ خودش رد می‌شود');return;}
if(!window.ATTA_HOOKED){
 window.ATTA_HOOKED=1;window.ATTA_STATUS='';
 try{
  var of=window.fetch;
  if(of){
   window.fetch=function(){
    var p=of.apply(this,arguments);
    try{
     p.then(function(r){
      r.clone().text().then(function(t){
       try{if(t&&t.length<4000&&t.indexOf('"status"')>=0){window.ATTA_STATUS=t;}}catch(e){}
      });
     });
    }catch(e){}
    return p;
   };
  }
 }catch(e){}
 try{
  var oo=XMLHttpRequest.prototype.open;
  var os=XMLHttpRequest.prototype.send;
  XMLHttpRequest.prototype.open=function(m,u){this._atta_u=u;return oo.apply(this,arguments);};
  XMLHttpRequest.prototype.send=function(){
   var x=this;
   try{
    x.addEventListener('load',function(){
     try{if(x.responseText&&x.responseText.length<4000&&x.responseText.indexOf('"status"')>=0){window.ATTA_STATUS=x.responseText;}}catch(e){}
    });
   }catch(e){}
   return os.apply(this,arguments);
  };
 }catch(e){}
}
var t=window.AJAX_TOKEN||'';
if(!t){var hs=document.head.innerHTML;var m=hs.match(/\(\(\)[\s\S]*?\)\)\(\);/);if(m){try{(0,eval)(m[0]);}catch(e){}t=window.AJAX_TOKEN||'';}}
window.ATTA_TOKEN=t;
var sid='';var cm=document.cookie.match(/(?:^|;\s*)ATERNOS_SERVER=([^;]+)/);if(cm){sid=cm[1];}
var ls='{}';try{ls=JSON.stringify(window.lastStatus||{});}catch(e){}
var st='';try{var el=document.querySelector('.status, #status, .server-status, [class*="status"]');if(el){st=String(el.className+' | '+el.textContent).replace(/\s+/g,' ').trim().slice(0,140);}}catch(e){}
AttaBridge.post('status',ls+'@@'+st+'@@'+(window.ATTA_STATUS||''));
AttaBridge.post('ready',(t?'token-ok':'no-token')+'|'+(sid||''));
}catch(e){AttaBridge.post('err','ready:'+e)}})();

(function(){try{
var ls='{}';try{ls=JSON.stringify(window.lastStatus||{});}catch(e){}
var st='';try{var el=document.querySelector('.status, #status, .server-status, [class*="status"]');if(el){st=String(el.className+' | '+el.textContent).replace(/\s+/g,' ').trim().slice(0,140);}}catch(e){}
AttaBridge.post('status',ls+'@@'+st+'@@'+(window.ATTA_STATUS||''));
}catch(e){AttaBridge.post('err','poll:'+e)}})();

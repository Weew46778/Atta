(function(){
try{
 if(window.ATTA_WS){try{window.ATTA_WS.close();}catch(e){}}
 var ws=new WebSocket('wss://aternos.org/hermes/');
 window.ATTA_WS=ws;
 var ka=null;
 ws.onopen=function(){
  AttaBridge.post('wss','open');
  try{ws.send(JSON.stringify({stream:'console',type:'start'}));}catch(e){}
  ka=setInterval(function(){try{ws.send('{"type":"\u2764"}');}catch(e){}},45000);
 };
 ws.onmessage=function(ev){
  try{AttaBridge.post('wss',String(ev.data).slice(0,1500));}catch(e){}
 };
 ws.onclose=function(){
  if(ka){clearInterval(ka);}
  AttaBridge.post('wss','closed');
  window.ATTA_WS=null;
 };
 ws.onerror=function(){AttaBridge.post('wss','error');};
}catch(e){AttaBridge.post('err','hermes:'+e);}
})();

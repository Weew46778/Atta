(function(){
try{
 var ws=window.ATTA_WS;
 if(!ws||ws.readyState!==1){AttaBridge.post('wss','no-socket');return;}
 ws.send(JSON.stringify({stream:'console',type:'command',data:'%CMD%'}));
 AttaBridge.post('wss','sent');
}catch(e){AttaBridge.post('err','cmd:'+e);}
})();

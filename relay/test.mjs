import test from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import {once} from 'node:events';
import WebSocket,{WebSocketServer} from 'ws';
import {createRelay} from './server.mjs';
import {connectBridge} from './bridge.mjs';
const secret='a'.repeat(43),origin='https://craftlink.example';
const listen=async server=>{server.listen(0,'127.0.0.1');await once(server,'listening');return server.address().port;};
async function fixture(withBridge=true){
 const local=http.createServer((req,res)=>{res.writeHead(200,{'Content-Type':'text/html','Content-Security-Policy':"connect-src 'self' wss:"});res.end('<p>CraftLink</p>');});
 const localSockets=new WebSocketServer({server:local});let lastOrigin;
 localSockets.on('connection',(ws,req)=>{lastOrigin=req.headers.origin;ws.on('message',bytes=>{ws.send(bytes,{binary:false});ws.send(Buffer.from([0,255,42]),{binary:true});});});
 const localPort=await listen(local),relay=createRelay({secret,origin,timeout:1000}),relayPort=await listen(relay.server);
 let bridge;
 if(withBridge)await new Promise(resolve=>bridge=connectBridge({url:`ws://127.0.0.1:${relayPort}/_bridge`,secret,port:localPort,onReady:resolve}));
 return {base:`http://127.0.0.1:${relayPort}`,ws:`ws://127.0.0.1:${relayPort}`,bridge,relay,lastOrigin:()=>lastOrigin,
  close:async()=>{bridge?.terminate();await relay.close();for(const ws of localSockets.clients)ws.terminate();await new Promise(r=>local.close(r));}};
}
test('HTTP UI and security headers pass through the authenticated Mac bridge',async()=>{
 const f=await fixture();try{const response=await fetch(f.base+'/');assert.equal(response.status,200);assert.match(await response.text(),/CraftLink/);assert.equal(response.headers.get('content-security-policy'),"connect-src 'self' wss:");assert.equal(response.headers.get('cache-control'),'no-store');assert.equal((await fetch(f.base+'/_bridge')).status,404);assert.equal((await fetch(f.base+'/outside')).status,404);}finally{await f.close();}
});
for(const path of ['/ws','/classroom/ws'])test('text input and binary video survive relay '+path,async()=>{
 const f=await fixture();let client;try{client=new WebSocket(f.ws+path,{origin});const messages=[];client.on('message',(data,binary)=>messages.push({data,binary}));await once(client,'open');client.send('{"type":"hello","token":"test"}');const deadline=Date.now()+3000;while(messages.length<2&&Date.now()<deadline)await new Promise(r=>setTimeout(r,10));assert.equal(messages.length,2);assert.equal(messages[0].binary,false);assert.match(messages[0].data.toString(),/hello/);assert.equal(messages[1].binary,true);assert.deepEqual([...messages[1].data],[0,255,42]);assert.equal(f.lastOrigin(),origin);}finally{client?.terminate();await f.close();}
});
async function rejected(url,headers){const ws=new WebSocket(url,{headers});const [error]=await once(ws,'error');assert.match(error.message,/403/);}
test('missing and wrong bridge authentication are rejected without an existing owner',async()=>{
 const f=await fixture(false);try{await rejected(f.ws+'/_bridge',{});await rejected(f.ws+'/_bridge',{Authorization:'Bearer invalid'});}finally{await f.close();}
});
test('wrong browser Origin and a second Mac bridge are rejected',async()=>{
 const f=await fixture();try{await rejected(f.ws+'/ws',{Origin:'https://other.example'});await rejected(f.ws+'/_bridge',{Authorization:'Bearer '+secret});}finally{await f.close();}
});
test('no Mac bridge returns 503, health endpoint stays available',async()=>{
 const f=await fixture(false);try{assert.equal((await fetch(f.base+'/')).status,503);assert.equal((await fetch(f.base+'/healthz')).status,200);}finally{await f.close();}
});
test('Mac bridge disconnection closes browser sockets',async()=>{
 const f=await fixture();const client=new WebSocket(f.ws+'/ws',{origin});try{await once(client,'open');const closed=once(client,'close');f.bridge.terminate();const [code]=await closed;assert.equal(code,1012);}finally{client.terminate();await f.close();}
});

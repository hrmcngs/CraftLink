import {generateKeyPairSync} from 'node:crypto';
import {ownerHeaders} from './owner-auth.mjs';
import test from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import {once} from 'node:events';
import WebSocket,{WebSocketServer} from 'ws';
import {createRelay} from './server.mjs';
import {validateSecret} from './protocol.mjs';
import {connectBridge} from './bridge.mjs';
const owner=generateKeyPairSync('ed25519'),ownerPublicKey=owner.publicKey.export({format:'der',type:'spki'}).toString('base64');
const secret='a'.repeat(43),origin='https://craftlink.example';
const listen=async server=>{server.listen(0,'127.0.0.1');await once(server,'listening');return server.address().port;};
async function fixture(withBridge=true,ownerSecret=secret){
 const local=http.createServer((req,res)=>{res.writeHead(200,{'Content-Type':'text/html','Content-Security-Policy':"connect-src 'self' wss:"});res.end('<p>CraftLink</p>');});
 const localSockets=new WebSocketServer({server:local});let lastOrigin;
 localSockets.on('connection',(ws,req)=>{lastOrigin=req.headers.origin;ws.on('message',bytes=>{ws.send(bytes,{binary:false});ws.send(Buffer.from([0,255,42]),{binary:true});});});
 const localPort=await listen(local),relay=createRelay({secret:ownerSecret,origin,ownerPublicKey,timeout:1000}),relayPort=await listen(relay.server);
 let bridge;
 if(withBridge)await new Promise(resolve=>bridge=connectBridge({url:`ws://127.0.0.1:${relayPort}/_bridge`,secret:ownerSecret,port:localPort,ownerKey:owner.privateKey,ownerOrigin:origin,onReady:resolve}));
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
test('bridge reports HTTP rejection and closes without exposing credentials',async()=>{
 const f=await fixture(false);let bridge;
 try{
  let status,ready=false;
  await new Promise(resolve=>{bridge=connectBridge({url:f.ws+'/_bridge',secret:'b'.repeat(43),port:8766,onRejected:value=>{status=value;},onReady:()=>{ready=true;},onClose:resolve});});
  assert.equal(status,403);assert.equal(ready,false);assert.equal(bridge.readyState,WebSocket.CLOSED);
 }finally{bridge?.terminate();await f.close();}
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

test('Render standard Base64 secret authenticates without modification',async()=>{
 const generated=Buffer.alloc(32,251).toString('base64');
 assert.match(generated,/\+/);assert.match(generated,/\//);assert.match(generated,/=$/);
 const f=await fixture(true,generated);try{assert.equal((await fetch(f.base+'/')).status,200);}finally{await f.close();}
});
test('missing, short, oversized and header-unsafe secrets are rejected',()=>{
 for(const value of [undefined,'','short','a'.repeat(129),'a'.repeat(32)+'\r\nInjected: value','a'.repeat(32)+' '])assert.throws(()=>validateSecret(value),/CRAFTLINK_RELAY_SECRET/);
 assert.doesNotThrow(()=>validateSecret('a'.repeat(43)));
});

test('knowing the relay secret and claiming hrmcngs is insufficient without the registered key',async()=>{
 const f=await fixture(false);try{
  await rejected(f.ws+'/_bridge',{Authorization:'Bearer '+secret,'x-craftlink-owner':'hrmcngs'});
  const stranger=generateKeyPairSync('ed25519');await rejected(f.ws+'/_bridge',{Authorization:'Bearer '+secret,...ownerHeaders(stranger.privateKey,origin)});
 }finally{await f.close();}
});

test('approved skin upload payloads larger than ordinary input survive the bridge',async()=>{
 const f=await fixture();let client;try{
  client=new WebSocket(f.ws+'/classroom/ws',{origin});const messages=[];client.on('message',(data,binary)=>{if(!binary)messages.push(data.toString());});await once(client,'open');client.send('{"type":"hello"}');
  const until=Date.now()+3000;while(messages.length<1&&Date.now()<until)await new Promise(resolve=>setTimeout(resolve,10));assert.equal(messages.length,1);
  const payload=JSON.stringify({type:'skinUpload',image:'data:image/png;base64,'+'A'.repeat(22000)});client.send(payload);
  while(messages.length<2&&Date.now()<until)await new Promise(resolve=>setTimeout(resolve,10));assert.equal(messages[1],payload);
 }finally{client?.terminate();await f.close();}
});

for(const [label,payload] of [
 ['oversized input','A'.repeat(98305)],
 ['malformed large JSON','A'.repeat(8193)],
 ['large non-skin input',JSON.stringify({type:'input',padding:'A'.repeat(8192)})]
])test('invalid peer input preserves the owner bridge and other participants: '+label,{timeout:5000},async()=>{
 const f=await fixture();let healthy,invalid;
 const event=(socket,name)=>once(socket,name,{signal:AbortSignal.timeout(3000)});
 try{
  healthy=new WebSocket(f.ws+'/classroom/ws',{origin});const messages=[];
  healthy.on('message',(data,binary)=>{if(!binary)messages.push(data.toString());});
  const echoed=async text=>{const until=Date.now()+3000;while(!messages.includes(text)&&Date.now()<until)await new Promise(resolve=>setTimeout(resolve,10));assert.ok(messages.includes(text),'healthy peer still receives '+text);};
  await event(healthy,'open');healthy.send('{"type":"hello"}');await echoed('{"type":"hello"}');
  // The offending peer has no room hello or invitation.
  invalid=new WebSocket(f.ws+'/classroom/ws',{origin});await event(invalid,'open');
  const closed=event(invalid,'close');invalid.send(payload);const [code]=await closed;
  assert.equal(code,1012);assert.equal(f.bridge.readyState,WebSocket.OPEN);assert.equal(healthy.readyState,WebSocket.OPEN);
  healthy.send('{"type":"ping"}');await echoed('{"type":"ping"}');
  assert.equal((await fetch(f.base+'/',{signal:AbortSignal.timeout(3000)})).status,200);
 }finally{invalid?.terminate();healthy?.terminate();await f.close();}
});

test('owner rejection details distinguish secret, signature and occupied relay without exposing credentials',async()=>{
 for(const reason of ['relay-key','owner-proof','already-connected']){
  const f=await fixture(reason==='already-connected');
  try{
   const headers={Authorization:'Bearer '+(reason==='relay-key'?'wrong-key':secret),...(reason==='already-connected'?ownerHeaders(owner.privateKey,origin):{})};
   const result=await new Promise(resolve=>{
    const socket=new WebSocket(f.ws+'/_bridge',{headers});socket.on('error',()=>{});
    socket.on('unexpected-response',(_request,response)=>{response.resume();resolve({status:response.statusCode,headers:response.headers});socket.terminate();});
   });
   assert.equal(result.status,403);assert.equal(result.headers['x-craftlink-rejection'],reason);
   assert(!JSON.stringify(result.headers).includes(secret));assert(!JSON.stringify(result.headers).includes(ownerPublicKey));
   assert.equal(typeof result.headers['x-craftlink-owner-fingerprint'],reason==='owner-proof'?'string':'undefined');
  }finally{await f.close();}
 }
});

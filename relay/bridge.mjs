import {pathToFileURL} from 'node:url';
import WebSocket from 'ws';
import {MAX_PAYLOAD, send, assetPath, socketPath, validateSecret} from './protocol.mjs';

export function connectBridge({url,secret,port,onReady=()=>{},onClose=()=>{},onRejected=()=>{}}) {
  validateSecret(secret);
  const endpoint = new URL(url); const peers = new Map(), requests = new Map(); let origin;
  const ws = new WebSocket(endpoint,{headers:{Authorization:'Bearer '+secret},handshakeTimeout:15000,maxPayload:MAX_PAYLOAD,perMessageDeflate:false});
  ws.on('unexpected-response',(_request,response)=>{
    response.resume();
    onRejected(response.statusCode);
    ws.terminate();
  });
  ws.on('message',async bytes=>{
    try {
      const packet = JSON.parse(bytes.toString());
      if (packet.type === 'ready') { origin=packet.origin;onReady(origin);return; }
      if (!origin) throw new Error('Relay not ready');
      if (packet.type === 'http') {
        if (!assetPath(packet.path) || requests.size >= 32) throw new Error('Invalid request');
        const abort = new AbortController();requests.set(packet.id,abort);
        try {
          const response = await fetch(`http://127.0.0.1:${port}${packet.path}`,{signal:AbortSignal.any([abort.signal,AbortSignal.timeout(8000)]),redirect:'error'});
          const body=Buffer.from(await response.arrayBuffer());if(body.length>MAX_PAYLOAD)throw new Error('Response too large');
          send(ws,{type:'http',id:packet.id,status:response.status,headers:{'content-type':response.headers.get('content-type'),'content-security-policy':response.headers.get('content-security-policy')},body:body.toString('base64')});
        } catch { send(ws,{type:'http',id:packet.id,status:502,body:Buffer.from('Minecraft connection is stopped, or the local port is incorrect.').toString('base64')}); }
        finally { requests.delete(packet.id); }
      } else if (packet.type === 'open') {
        if (!socketPath(packet.path) || packet.origin !== origin || peers.size >= 16) throw new Error('Invalid socket');
        const local=new WebSocket(`ws://127.0.0.1:${port}${packet.path}`,{origin,maxPayload:MAX_PAYLOAD,perMessageDeflate:false});
        const state={local,queue:[],size:0};peers.set(packet.id,state);
        local.on('open',()=>{for(const body of state.queue)local.send(body,{binary:false});state.queue=[];state.size=0;});
        local.on('message',(data,binary)=>send(ws,{type:'frame',id:packet.id,body:data.toString('base64'),binary}));
        local.on('error',()=>{});local.on('close',()=>{peers.delete(packet.id);send(ws,{type:'close',id:packet.id});});
      } else if (packet.type === 'frame') {
        const state=peers.get(packet.id);if(!state)return;
        const body=Buffer.from(packet.body,'base64');if(packet.binary || body.length>8192)throw new Error('Invalid controller input');
        if(state.local.readyState===1){if(state.local.bufferedAmount>MAX_PAYLOAD)state.local.close();else state.local.send(body,{binary:false});}
        else if(state.local.readyState===0){state.size+=body.length;if(state.size>16384)throw new Error('Input queue full');state.queue.push(body);}
      } else if (packet.type === 'close') peers.get(packet.id)?.local.close();
    } catch { ws.close(1008,'Invalid relay message'); }
  });
  ws.on('error',()=>{});
  ws.on('close',()=>{for(const {local} of peers.values())local.terminate();peers.clear();for(const abort of requests.values())abort.abort();onClose();});
  return ws;
}
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const [mode='controller',url] = process.argv.slice(2), secret=process.env.CRAFTLINK_RELAY_SECRET;
  if(!['controller','classroom'].includes(mode)||!url||!secret)throw new Error('Usage: CRAFTLINK_RELAY_SECRET=... sh relay.sh controller|classroom https://your-relay.onrender.com');
  const origin=new URL(url);if(origin.protocol!=='https:'||origin.origin!==url||origin.username||origin.password)throw new Error('Use the root HTTPS URL');
  const port=Number(process.env.CRAFTLINK_LOCAL_PORT??(mode==='classroom'?8766:8765));
  if(!Number.isInteger(port)||port<1024||port>65535)throw new Error('Invalid local port');
  let stopped=false,current,retry;
  const connect=()=>{if(stopped)return;console.log('HTTPS中継へ接続中…');current=connectBridge({url:url.replace(/^https:/,'wss:')+'/_bridge',secret,port,onReady:()=>console.log(`中継接続済み。Minecraftの${mode==='classroom'?'みんなで参加 → ':''}「別のWi-Fi」に ${url} を保存し、開始後のQRを読みます。`),onClose:()=>{if(!stopped){console.log('中継切断。再接続します。URL・認証キー・回線を確認してください。');retry=setTimeout(connect,3000);}}});};
  for(const signal of ['SIGINT','SIGTERM'])process.on(signal,()=>{stopped=true;clearTimeout(retry);current?.terminate();});
  connect();
}

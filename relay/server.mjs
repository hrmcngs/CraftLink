import http from 'node:http';
import {timingSafeEqual, randomUUID} from 'node:crypto';
import {pathToFileURL} from 'node:url';
import {WebSocketServer} from 'ws';
import {MAX_PAYLOAD, send, assetPath, socketPath} from './protocol.mjs';

/** One private Mac bridge per deployment. No arbitrary upstream URLs or persistent data. */
export function createRelay({secret, origin, timeout = 10000}) {
  if (!/^[A-Za-z0-9_-]{32,128}$/.test(secret ?? '')) throw new Error('CRAFTLINK_RELAY_SECRET must contain 32..128 URL-safe characters');
  const parsed = new URL(origin); if (parsed.origin !== origin) throw new Error('Use a root public origin');
  let bridge; const pending = new Map(), peers = new Map();
  const fail = response => { if (!response.writableEnded) { response.writeHead(503, {'Cache-Control':'no-store'}); response.end('CraftLink: Mac is not connected to the relay.'); } };
  function dropBridge(ws) {
    if (bridge !== ws) return; bridge = undefined;
    for (const {response,timer} of pending.values()) { clearTimeout(timer); fail(response); } pending.clear();
    for (const peer of peers.values()) peer.close(1012, 'Mac disconnected'); peers.clear();
  }
  const server = http.createServer((request,response) => {
    if (request.url === '/healthz') { response.writeHead(200, {'Content-Type':'text/plain'}); response.end('CraftLink relay'); return; }
    if (request.method !== 'GET' || !assetPath(request.url)) { response.writeHead(404); response.end(); return; }
    if (!bridge || bridge.readyState !== 1 || pending.size >= 32) { fail(response); return; }
    const id = randomUUID(), timer = setTimeout(() => { pending.delete(id); fail(response); }, timeout);
    pending.set(id,{response,timer});
    response.on('close',() => { clearTimeout(timer); pending.delete(id); });
    send(bridge,{type:'http',id,path:request.url});
  });
  const sockets = new WebSocketServer({noServer:true,maxPayload:MAX_PAYLOAD,perMessageDeflate:false});
  server.on('upgrade',(request,network,head) => {
    const path = request.url;
    if (path === '/_bridge') {
      const supplied = Buffer.from(request.headers.authorization ?? ''), expected = Buffer.from('Bearer '+secret);
      if (supplied.length !== expected.length || !timingSafeEqual(supplied,expected) || bridge) { network.end('HTTP/1.1 403 Forbidden\r\nConnection: close\r\n\r\n'); return; }
      sockets.handleUpgrade(request,network,head,ws => {
        bridge = ws; ws.alive = true;
        ws.on('pong',()=>ws.alive=true);
        ws.on('message',bytes => {
          try {
            const packet = JSON.parse(bytes.toString());
            if (packet.type === 'http') {
              const job = pending.get(packet.id); if (!job) return;
              const body = Buffer.from(packet.body,'base64');
              if (body.length > MAX_PAYLOAD || !Number.isInteger(packet.status) || packet.status < 200 || packet.status > 599) throw new Error('Invalid response');
              pending.delete(packet.id); clearTimeout(job.timer);
              const headers = {'Cache-Control':'no-store','Referrer-Policy':'no-referrer','X-Content-Type-Options':'nosniff'};
              for (const name of ['content-type','content-security-policy']) if (typeof packet.headers?.[name] === 'string') headers[name] = packet.headers[name];
              job.response.writeHead(packet.status,headers); job.response.end(body);
            } else if (packet.type === 'frame') {
              const peer = peers.get(packet.id); if (peer?.readyState === 1) {
                if (peer.bufferedAmount > MAX_PAYLOAD) peer.close(1013,'Backpressure');
                else peer.send(Buffer.from(packet.body,'base64'),{binary:packet.binary === true});
              }
            } else if (packet.type === 'close') peers.get(packet.id)?.close(1012,'Local connection closed');
          } catch { ws.close(1008,'Invalid bridge message'); }
        });
        ws.on('error',()=>{}); ws.on('close',()=>dropBridge(ws));
        send(ws,{type:'ready',origin});
      }); return;
    }
    if (!socketPath(path) || request.headers.origin !== origin || !bridge || peers.size >= 16) { network.end('HTTP/1.1 403 Forbidden\r\nConnection: close\r\n\r\n'); return; }
    sockets.handleUpgrade(request,network,head,ws => {
      const id = randomUUID(); peers.set(id,ws); ws.alive = true;
      ws.on('pong',()=>ws.alive=true);
      let window = Date.now(), count = 0;
      ws.on('message',(bytes,binary) => { if(Date.now()-window>1000){window=Date.now();count=0;}if(++count>128){ws.close(1008,'Input rate exceeded');return;} if (binary) { ws.close(1003,'Text input only'); return; } send(bridge,{type:'frame',id,body:bytes.toString('base64'),binary:false}); });
      ws.on('error',()=>{});
      ws.on('close',()=>{ peers.delete(id); if (bridge) send(bridge,{type:'close',id}); });
      send(bridge,{type:'open',id,path,origin});
    });
  });
  const heartbeat = setInterval(()=>{ for (const ws of sockets.clients) { if (ws.alive === false) ws.terminate(); else { ws.alive=false;ws.ping(); } } },15000);
  heartbeat.unref();
  return {server, close:async()=>{clearInterval(heartbeat);for(const ws of sockets.clients)ws.terminate();await new Promise(resolve=>server.close(resolve));}};
}
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const origin = process.env.RENDER_EXTERNAL_URL ?? process.env.CRAFTLINK_RELAY_ORIGIN;
  if (!origin?.startsWith('https://')) throw new Error('Set CRAFTLINK_RELAY_ORIGIN to the public HTTPS origin');
  const relay = createRelay({secret:process.env.CRAFTLINK_RELAY_SECRET,origin});
  relay.server.listen(Number(process.env.PORT ?? 3000),'0.0.0.0',()=>console.log('CraftLink relay ready'));
  for (const signal of ['SIGINT','SIGTERM']) process.on(signal,()=>relay.close().then(()=>process.exit(0)));
}

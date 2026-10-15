import { websocketUrl } from '../network/url';
import './style.css';
import {VideoView} from '../video/view';
const root=document.querySelector<HTMLElement>('#classroom')!;
root.innerHTML=`<canvas id="bot-view" aria-label="自分のBOTの視界"></canvas><div id="video-state" role="status"></div><header><strong id="identity">CraftLink</strong><output id="status" aria-live="polite">せつぞく中…</output><button id="video-quality">画質: 標準</button><button id="video-toggle">映像: ON</button><button id="leave">おわる</button></header><p class="help">あなたのBOTの視界を見ながら操作しよう</p><main><section class="dpad"><button data-key="forward">まえ ▲</button><button data-key="left">◀ ひだり</button><button data-key="back">うしろ ▼</button><button data-key="right">みぎ ▶</button></section><div id="look" aria-label="スワイプで向きをかえる">ここをスワイプ<br>むきをかえる ↔</div><section class="actions"><button data-key="jump">ジャンプ</button><button data-key="sprint">はやく</button><button data-key="sneak">ゆっくり</button><button data-key="use">あいさつ ✨</button></section></main><nav id="bot-hotbar" aria-label="BOTのホットバー">${Array.from({length:9},(_,i)=>`<button data-bot-slot="${i}" aria-label="持ち物 ${i+1}"><span></span><small></small></button>`).join('')}<button id="bot-inventory-open" aria-label="BOTのインベントリを開く">⋯</button></nav><dialog id="bot-inventory" aria-labelledby="inventory-title"><h1 id="inventory-title">BOTの持ち物</h1><p>近くに落ちたアイテムを拾います。持ち物をタップして手に持ちます。</p><div id="bot-inventory-grid">${Array.from({length:36},(_,i)=>`<button data-bot-slot="${i}" aria-label="持ち物 ${i+1}"><span></span><small></small></button>`).join('')}</div><button id="bot-inventory-close">戻る</button></dialog><footer>QRを読み込むと自動で参加できます</footer>`;
const video=new VideoView(document.querySelector<HTMLCanvasElement>('#bot-view')!,root);
let videoEnabled=true, videoError="", inventoryOpen=false;
const qualities=["balanced","smooth","sharp"] as const;
let qualityIndex=0;try{const saved=localStorage.getItem("craftlink.botVideoQuality");const index=qualities.findIndex(q=>q===saved);if(index>=0)qualityIndex=index;}catch{}
const qualityButton=document.querySelector<HTMLButtonElement>("#video-quality")!;
function qualityLabel(){qualityButton.textContent="画質: "+["標準","軽快","鮮明"][qualityIndex];}
qualityLabel();qualityButton.onclick=()=>{qualityIndex=(qualityIndex+1)%qualities.length;try{localStorage.setItem("craftlink.botVideoQuality",qualities[qualityIndex]);}catch{}qualityLabel();requestVideo();};
const status=document.querySelector<HTMLOutputElement>('#status')!;
const token=location.hash.slice(1), storageKey='craftlink.classroom.'+token;
let resume='';try{resume=sessionStorage.getItem(storageKey)??'';}catch{}
let socket:WebSocket|undefined, ready=false, approved=false, paused=true, stopped=false, retry:number|undefined;
const held=new Map<number,string>();let dx=0,dy=0,lastSent='',sentAt=-Infinity,lastPong=0,pingAt=0;
let look:{id:number;x:number;y:number}|undefined;
const feedback=()=>root.querySelectorAll<HTMLElement>('[data-key]').forEach(b=>b.classList.toggle('pressed',[...held.values()].includes(b.dataset.key!)));
function send(force=false){if(!ready||socket?.readyState!==WebSocket.OPEN)return;const packet=JSON.stringify({type:'input',held:[...new Set(held.values())],dx:Math.max(-180,Math.min(180,dx)),dy:Math.max(-180,Math.min(180,dy))});const now=performance.now();if(force||dx!==0||dy!==0||packet!==lastSent||now-sentAt>=250){socket.send(packet);lastSent=packet;sentAt=now;dx=0;dy=0;}}
function release(){held.clear();look=undefined;dx=0;dy=0;feedback();send(true);}
function update(){ready=!stopped&&approved&&!paused&&!document.hidden&&!inventoryOpen;root.dataset.enabled=String(ready);root.querySelectorAll<HTMLButtonElement>('[data-key]').forEach(button=>button.disabled=!ready);if(stopped)return;document.querySelector('footer')!.textContent=!approved?'参加の接続を確認しています…':paused?'操作は一時停止中です。PCで「開始 / 再開」を押してください':'移動・ジャンプ・視点を同時に操作できます';status.textContent=!approved?'参加の接続を確認中…':paused?'一時停止中 · PCで開始してください':document.hidden?'おやすみ中':'うごかせるよ';}
const inventoryDialog=document.querySelector<HTMLDialogElement>('#bot-inventory')!;
function requestInventory(slot=-1){if(approved&&!stopped&&socket?.readyState===WebSocket.OPEN)socket.send(JSON.stringify({type:'inventory',slot}));}
document.querySelector('#bot-inventory-open')!.addEventListener('click',()=>{if(!approved)return;release();inventoryOpen=true;update();inventoryDialog.showModal();requestInventory();});
document.querySelector('#bot-inventory-close')!.addEventListener('click',()=>inventoryDialog.close());
inventoryDialog.addEventListener('close',()=>{inventoryOpen=false;update();});
root.querySelectorAll<HTMLButtonElement>('[data-bot-slot]').forEach(button=>button.onclick=()=>requestInventory(Number(button.dataset.botSlot)));
function renderInventory(data: {selected?:unknown;slots?:unknown}) {
 if(!Number.isInteger(data.selected)||!Array.isArray(data.slots)||data.slots.length!==36)return;
 for(const item of data.slots){if(!item||!Number.isInteger(item.slot)||item.slot<0||item.slot>35||typeof item.name!=='string'||typeof item.count!=='number')return;}
 for(const item of data.slots){root.querySelectorAll<HTMLButtonElement>('[data-bot-slot="'+item.slot+'"]').forEach(button=>{button.querySelector('span')!.textContent=item.count>0?item.name:'';button.querySelector('small')!.textContent=item.count>1?String(item.count):'';button.title=item.name;button.classList.toggle('selected',item.slot===data.selected);});}
}
function connect(){
 if(stopped)return;
 approved=false;ready=false;document.querySelector('#identity')!.textContent='接続中…';delete root.dataset.number;status.textContent='せつぞく中…';
 const ws=socket=new WebSocket(websocketUrl(location,'/classroom/ws'));
 ws.onopen=()=>{lastPong=performance.now();lastSent='';sentAt=-Infinity;ws.send(JSON.stringify({type:'hello',token,resume}));};
ws.onmessage=async e=>{
 if(typeof e.data!=='string'){
  try{const id=await video.receive(e.data instanceof Blob?e.data:new Blob([e.data]));if(id!==undefined)videoError='';if(id!==undefined&&socket===ws&&approved&&ws.readyState===WebSocket.OPEN)ws.send(JSON.stringify({type:'frameAck',frame:id}));}catch{videoError='BOT映像を表示できませんでした。ページを開き直してください';}return;
 }
 const data=JSON.parse(e.data);

  if(data.type==='welcome'){if(typeof data.resume!=='string'||!Number.isInteger(data.number)||data.number<1||data.number>8){ws.close();return;}resume=data.resume;try{sessionStorage.setItem(storageKey,resume);}catch{}root.dataset.number=String(data.number);document.querySelector('#identity')!.textContent=`あなたは ${data.number}号`;}
  if(data.type==='inventory')renderInventory(data);
  if(data.type==='state'){release();approved=data.approved===true;paused=data.paused!==false;update();requestVideo();}
  if(data.type==='pong'){videoError=typeof data.videoStatus==='string'?data.videoStatus:'';lastPong=performance.now();if(ready)status.textContent=`うごかせるよ · ${Math.round(lastPong-pingAt)} ms`;}
  if(data.type==='revoked'){stopped=true;resume='';try{sessionStorage.removeItem(storageKey);}catch{}release();ws.close();status.textContent='主催者がさんかをしゅうりょうしました';}
 };
 ws.onclose=()=>{inventoryDialog.close();root.querySelectorAll('[data-bot-slot] span,[data-bot-slot] small').forEach(label=>label.textContent='');document.querySelector('#identity')!.textContent='未接続';delete root.dataset.number;video.clear();ready=false;approved=false;release();update();root.dataset.enabled='false';if(!stopped){status.textContent='切断 · さいせつぞく中…';document.querySelector('footer')!.textContent='PCの学校用起動を確認してください。部屋を作り直した場合は、新しいQRを読み直します。';retry=window.setTimeout(connect,2000);}};
 ws.onerror=()=>ws.close();
}
root.querySelectorAll<HTMLElement>('[data-key]').forEach(button=>{
 button.onpointerdown=e=>{if(!ready)return;e.preventDefault();button.setPointerCapture(e.pointerId);held.set(e.pointerId,button.dataset.key!);feedback();send(true);};
 const up=(e:PointerEvent)=>{if(held.delete(e.pointerId)){feedback();send(true);}};
 button.onpointerup=up;button.onpointercancel=up;button.onlostpointercapture=up;
});
const area=document.querySelector<HTMLElement>('#look')!;
area.onpointerdown=e=>{if(!ready||look)return;e.preventDefault();area.setPointerCapture(e.pointerId);look={id:e.pointerId,x:e.clientX,y:e.clientY};};
area.onpointermove=e=>{if(look?.id===e.pointerId){dx+=(e.clientX-look.x)*.35;dy+=(e.clientY-look.y)*.35;look.x=e.clientX;look.y=e.clientY;}};
const endLook=(e:PointerEvent)=>{if(look?.id===e.pointerId){send();look=undefined;}};area.onpointerup=endLook;area.onpointercancel=endLook;area.onlostpointercapture=endLook;
function requestVideo(){const enabled=videoEnabled&&approved&&!document.hidden&&!stopped;video.setEnabled(enabled);if(socket?.readyState===WebSocket.OPEN)socket.send(JSON.stringify({type:'video',enabled,quality:qualities[qualityIndex]}));}
document.querySelector('#video-toggle')!.addEventListener('click',()=>{videoEnabled=!videoEnabled;document.querySelector('#video-toggle')!.textContent=`映像: ${videoEnabled?'ON':'OFF'}`;requestVideo();});
document.querySelector('#leave')!.addEventListener('click',()=>{release();stopped=true;clearTimeout(retry);if(socket?.readyState===WebSocket.OPEN)socket.send('{"type":"disconnect"}');socket?.close();resume='';try{sessionStorage.removeItem(storageKey);}catch{}status.textContent='おつかれさま！';});
window.addEventListener('blur',release);window.addEventListener('resize',release);window.addEventListener('pagehide',()=>{release();stopped=true;clearTimeout(retry);socket?.close();});
document.addEventListener('visibilitychange',()=>{if(document.hidden)release();update();requestVideo();});document.addEventListener('contextmenu',e=>e.preventDefault());
setInterval(()=>{if(ready)send();video.refresh();if(videoEnabled&&videoError)document.querySelector('#video-state')!.textContent=videoError;},33);
setInterval(()=>{if(socket?.readyState===WebSocket.OPEN){if(performance.now()-lastPong>5000){socket.close();return;}pingAt=performance.now();socket.send('{"type":"ping"}');}},1000);
update();root.dataset.enabled='false';if(/^[A-Za-z0-9_-]{43}$/.test(token))connect();else status.textContent='主催者のQRコードからひらいてね';

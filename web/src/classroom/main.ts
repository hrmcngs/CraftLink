import {mawUi} from './maw-ui';
import { accessoryPlaceholder } from './accessory-icons';
import {remoteMenu} from './remote-menu';
import {renderVitals,resetVitals,vitalsMarkup} from './vitals';
import './vitals.css';
import {stickVector,mobilePreset,holdQuantity,strongForward} from './mobile-controls';
import {WorldGesture} from '../controller/gesture';
import {drawSkin} from './skin-preview';
import {inventoryMarkup} from './inventory-layout';
import { websocketUrl } from '../network/url';
import './style.css';
import './inventory.css';
import {observeInventoryLayout} from './inventory-viewport';
import {loadTouchSettings,saveTouchSettings,normalizeTouchSettings,DoubleTap} from './touch-settings';
import {LayoutEditor} from './layout-editor';
import {loadProfile,saveProfile,normalizeProfile,validName,skinChoices} from './participant-profile';
import {VideoView} from '../video/view';
const root=document.querySelector<HTMLElement>('#classroom')!;
root.innerHTML=`<canvas id="bot-view" aria-label="自分のBOTの視界"></canvas><div id="video-state" role="status"></div>${vitalsMarkup}<header><strong id="identity">CraftLink</strong><output id="status" aria-live="polite">せつぞく中…</output><button id="chat-open" aria-label="チャット">💬</button><button id="settings-open" aria-label="ポーズ・設定メニュー">☰</button><button id="fullscreen" aria-label="全画面表示">⛶</button></header><main><section class="dpad" data-control="dpad" aria-label="方向パッド"><button data-move="forward left" aria-label="左前へ">↖</button><button data-key="forward">▲</button><button data-move="forward right" aria-label="右前へ">↗</button><button data-key="left">◀</button><button data-key="back">▼</button><button data-key="right">▶</button></section><div id="joystick" data-control="joystick" aria-label="移動ジョイスティック"><i></i></div><div id="crosshair" aria-hidden="true">+</div><div id="look" aria-label="スワイプで向きをかえる"></div><section class="actions"><button data-control="jump" data-key="jump" aria-label="ジャンプ">↥</button><button data-control="sprint" data-key="sprint">ダッシュ</button><button data-control="sneak" data-key="sneak">スニーク</button><button data-control="attack" data-key="attack">こわす</button><button data-control="use" data-key="use">おく / つかう</button><button id="mwa-skill" hidden aria-label="MWAの技。長押しで溜め攻撃">技 / 溜め</button><button id="mwa-sheathe" hidden>抜刀 / 納刀</button><button id="drop-item" data-control="drop" aria-label="手持ちを捨てる">捨てる</button></section></main><nav data-control="hotbar" id="bot-hotbar" aria-label="BOTのホットバー">${Array.from({length:9},(_,i)=>`<button data-bot-slot="${i}" aria-label="持ち物 ${i+1}"><span></span><small></small></button>`).join('')}<button id="bot-inventory-open" aria-label="BOTのインベントリを開く">⋯</button></nav>${inventoryMarkup()}<dialog id="bot-settings" aria-labelledby="settings-title"><button id="settings-close" aria-label="設定を閉じる">×</button><h1 id="settings-title">設定</h1><p id="participant-mode">サバイバル</p><p>ブロックをタップ: 設置・使用<br>長押し: 破壊 / スワイプ: 視点</p><section id="profile-options"><h2>プレイヤー</h2><label>ユーザーネーム <input id="profile-name" type="text" maxlength="32" autocomplete="nickname" placeholder="空欄なら自動" aria-describedby="profile-name-help"></label><p id="profile-name-help">日本語・英数字・空白・_-で16文字まで</p><fieldset><legend>スキン</legend><div id="skin-choices">${skinChoices.map(([id,name])=>`<button type="button" data-skin="${id}" aria-pressed="false"><img alt="" hidden><span>${name}</span></button>`).join('')}</div></fieldset><button id="profile-save">名前とスキンを変更</button><p id="profile-status" role="status"></p></section><section id="touch-options"><h2>タッチ</h2><div id="mobile-presets"><button data-mobile-preset="joystick_tap">ジョイスティック＋タップ</button><button data-mobile-preset="dpad_tap">十字キー＋タップ</button><button data-mobile-preset="joystick_crosshair">ジョイスティック＋照準</button></div><label>操作モード <select id="touch-mode"><option value="joystick_tap">ジョイスティック + タップ</option><option value="dpad_tap">D-Pad + タップ</option><option value="joystick_crosshair">ジョイスティック + クロスヘア</option></select></label><label>視点感度 <input data-touch="sensitivity" type="range" min="0.05" max="1" step="0.05"><output data-touch-value="sensitivity"></output></label><label>全体のサイズ <input data-touch="size" type="range" min="70" max="160" step="5"><output data-touch-value="size"></output></label><label>不透明度 <input data-touch="opacity" type="range" min="10" max="100" step="5"><output data-touch-value="opacity"></output></label><label>捨てる長押し時間 <input data-touch="holdToDrop" type="range" min="500" max="3000" step="100"><output data-touch-value="holdToDrop"></output></label><label><input data-touch="invertY" type="checkbox">上下の視点を反転</label><label><input data-touch="autoJump" type="checkbox">自動ジャンプ</label><label><input data-touch="sneakToggle" type="checkbox">スニークをタップ切り替え</label><label><input data-touch="doubleForward" type="checkbox">前進2回でダッシュ</label><label><input data-touch="doubleJumpSneak" type="checkbox">ジャンプ2回でスニーク（サバイバル）</label><label><input data-touch="splitActions" type="checkbox">攻撃と使用ボタンを左右に分ける</label><label><input data-touch="showActions" type="checkbox">タップモードでも攻撃・使用ボタンを表示</label><label><input data-touch="showButtons" type="checkbox">操作ボタンを表示</label><label><input data-touch="showDrop" type="checkbox">捨てるボタンを表示</label><label><input data-touch="dropStack" type="checkbox">長押し・捨てるボタンでまとめて捨てる</label><button id="customize-touch">操作方法のカスタマイズ</button><button id="touch-reset">タッチ設定を初期化</button></section><button id="video-quality">画質: 標準</button><button id="video-toggle">映像: ON</button><button id="leave">参加を終了</button><p id="fullscreen-help"></p><footer>QRを読み込むと自動で参加できます</footer></dialog>`;
const video=new VideoView(document.querySelector<HTMLCanvasElement>('#bot-view')!,root);
let videoEnabled=true, videoError="", inventoryOpen=false, settingsOpen=false;
const qualities=["balanced","smooth","sharp"] as const;
let qualityIndex=0;try{const saved=localStorage.getItem("craftlink.botVideoQuality");const index=qualities.findIndex(q=>q===saved);if(index>=0)qualityIndex=index;}catch{}
const qualityButton=document.querySelector<HTMLButtonElement>("#video-quality")!;
function qualityLabel(){qualityButton.textContent="画質: "+["標準","軽快","鮮明"][qualityIndex];}
qualityLabel();qualityButton.onclick=()=>{qualityIndex=(qualityIndex+1)%qualities.length;try{localStorage.setItem("craftlink.botVideoQuality",qualities[qualityIndex]);}catch{}qualityLabel();requestVideo();};

let profile=loadProfile(),skinDraft=profile.skin,participantNumber=0;
const profileName=document.querySelector<HTMLInputElement>('#profile-name')!;profileName.value=profile.name;
const skinImages=new Map<string,string>(),skinTextures=new Map<string,string>();
let activeSkin=profile.skin;
function updateSkinSelection(){
 const resolved=activeSkin==='auto'?skinChoices[1+Math.max(0,participantNumber-1)%9][0]:activeSkin;
 drawSkin(document.querySelector<HTMLCanvasElement>('#inventory-skin')!,skinTextures.get(resolved));
 root.querySelectorAll<HTMLButtonElement>('[data-skin]').forEach(button=>{
  const id=button.dataset.skin!;button.setAttribute('aria-pressed',String(id===skinDraft));
  const resolved=id==='auto'?skinChoices[1+Math.max(0,participantNumber-1)%9][0]:id;
  const image=skinImages.get(resolved),preview=button.querySelector('img')!;preview.hidden=!image;if(image)preview.src=image;
 });
}
function renderSkinCatalog(data:{skins?:unknown}){
 if(!Array.isArray(data.skins)||data.skins.length>9)return;
 for(const skin of data.skins)if(skin&&skinChoices.some(([id])=>id!=='auto'&&id===skin.id)&&typeof skin.image==='string'&&skin.image.length<=65536&&/^data:image\/png;base64,[A-Za-z0-9+/=]+$/.test(skin.image)){skinImages.set(skin.id,skin.image);if(typeof skin.texture==='string'&&skin.texture.length<=262144&&/^data:image\/png;base64,[A-Za-z0-9+/=]+$/.test(skin.texture))skinTextures.set(skin.id,skin.texture);}
 updateSkinSelection();
}
root.querySelectorAll<HTMLButtonElement>('[data-skin]').forEach(button=>button.onclick=()=>{skinDraft=button.dataset.skin!;updateSkinSelection();});
document.querySelector('#profile-save')!.addEventListener('click',()=>{
 if(!validName(profileName.value)){document.querySelector('#profile-status')!.textContent='日本語・英数字・空白・_-で16文字以内にしてください';return;}
 profile=normalizeProfile({name:profileName.value,skin:skinDraft});profileName.value=profile.name;saveProfile(profile);
 if(socket?.readyState===WebSocket.OPEN&&approved){socket.send(JSON.stringify({type:'profile',...profile}));document.querySelector('#profile-status')!.textContent='変更を送信しました';}
 else document.querySelector('#profile-status')!.textContent='保存しました。次の接続で使います';
});
function renderIdentity(data:{number?:unknown;name?:unknown;skin?:unknown}){
 if(!Number.isInteger(data.number)||Number(data.number)<1||Number(data.number)>8)return;participantNumber=Number(data.number);
 document.querySelector('#identity')!.textContent=typeof data.name==='string'&&validName(data.name)?data.name+'（'+participantNumber+'号）':'あなたは '+participantNumber+'号';
 activeSkin=typeof data.skin==='string'&&skinChoices.some(([id])=>id===data.skin)?data.skin:profile.skin;
 document.querySelector('#inventory-player-name')!.textContent=typeof data.name==='string'&&validName(data.name)?data.name:'あなたのBOT';
 updateSkinSelection();
}
updateSkinSelection();
const status=document.querySelector<HTMLOutputElement>('#status')!;
const token=location.hash.slice(1), storageKey='craftlink.classroom.'+token;
let resume='';try{resume=sessionStorage.getItem(storageKey)??'';}catch{}
let socket:WebSocket|undefined, ready=false, approved=false, paused=true, stopped=false, retry:number|undefined;
let touchSettings=loadTouchSettings(), editor:LayoutEditor|undefined;
let menuOpen=false,chatOpen=false,inWater=false;
let flying=false,participantMode='survival',sneakLatched=false,sprintLatched=false;
const jumpTaps=new DoubleTap(),forwardTaps=new DoubleTap();let stickKeys:string[]=[];let movement:{x:number;z:number}|undefined;
let stickPointer:number|undefined;
let touchPoint:{x:number;y:number}|undefined;
const held=new Map<number,string>();let dx=0,dy=0,lastSent='',sentAt=-Infinity,lastPong=0,pingAt=0;
let look:{id:number;x:number;y:number;startX:number;startY:number;moved:boolean;mining:boolean;gesture:WorldGesture}|undefined;let longPress:number|undefined;
function keysHeld(){const keys=new Set([...held.values()].flatMap(value=>value.split(' ')).concat(stickKeys));if(sneakLatched)keys.add('sneak');if(sprintLatched&&keys.has('forward'))keys.add('sprint');return [...keys];}
const feedback=()=>root.querySelectorAll<HTMLElement>('[data-key],[data-move]').forEach(b=>b.classList.toggle('pressed',(b.dataset.key??b.dataset.move??'').split(' ').every(key=>keysHeld().includes(key))));
function send(force=false){if(!ready||socket?.readyState!==WebSocket.OPEN)return;const target=touchPoint?video.point(touchPoint.x,touchPoint.y):undefined;const keys=keysHeld().filter(key=>!touchPoint||target||(key!=='attack'&&key!=='use'));const packet=JSON.stringify({type:'input',held:keys,dx:Math.max(-180,Math.min(180,dx)),dy:Math.max(-180,Math.min(180,dy)),target,...(movement?{moveX:movement.x,moveZ:movement.z}:{})});const now=performance.now();if(force||dx!==0||dy!==0||packet!==lastSent||now-sentAt>=250){socket.send(packet);lastSent=packet;sentAt=now;dx=0;dy=0;}}
function release(){sendUi('skillCancel');jumpTaps.reset();forwardTaps.reset();cancelSlotHolds();stickKeys=[];movement=undefined;stickPointer=undefined;sneakLatched=false;sprintLatched=false;resetStick();held.clear();look=undefined;clearTimeout(longPress);touchPoint=undefined;dx=0;dy=0;feedback();send(true);}
function update(){ready=!stopped&&approved&&!paused&&!document.hidden&&!inventoryOpen&&!menuOpen&&!chatOpen&&!settingsOpen&&!editor?.active;root.dataset.enabled=String(ready);root.querySelectorAll<HTMLButtonElement>('[data-key],[data-move],#mwa-skill,#mwa-sheathe').forEach(button=>button.disabled=!ready&&!editor?.active);if(stopped)return;document.querySelector('footer')!.textContent=!approved?'参加の接続を確認しています…':paused?'操作は一時停止中です。PCで「開始 / 再開」を押してください':'移動・ジャンプ・視点を同時に操作できます';status.textContent=!approved?'参加の接続を確認中…':paused?'一時停止中 · PCで開始してください':document.hidden?'おやすみ中':'うごかせるよ';}
const inventoryDialog=document.querySelector<HTMLDialogElement>('#bot-inventory')!;
const inventoryLayout=observeInventoryLayout(inventoryDialog);
inventoryDialog.dataset.view='inventory';
function inventoryView(view:string){
 inventoryDialog.dataset.view=view;
 inventoryDialog.querySelectorAll<HTMLElement>('[data-inventory-view]').forEach(button=>button.setAttribute('aria-pressed',String(button.dataset.inventoryView===view)));
 inventoryLayout.refresh();
 if(view==='creative'&&creativeAllowed){
  if(document.querySelector('#creative-items')!.childElementCount===0){creativePage=0;requestCreative();}
  requestAnimationFrame(loadMoreCreative);
 }
}
inventoryDialog.querySelectorAll<HTMLElement>('[data-inventory-view]').forEach(button=>button.onclick=()=>inventoryView(button.dataset.inventoryView!));
function requestInventory(slot=-1){if(approved&&!stopped&&socket?.readyState===WebSocket.OPEN)socket.send(JSON.stringify({type:'inventory',slot}));}
document.querySelector('#bot-inventory-open')!.addEventListener('click',()=>{if(!approved)return;inventorySelection=undefined;selectInventory(undefined);release();inventoryOpen=true;update();inventoryDialog.showModal();inventoryLayout.refresh();creativePage=0;requestInventory();if(creativeAllowed)requestCreative();});
function closeInventory(){inventoryDialog.close();inventoryOpen=false;release();update();}
document.querySelector('#bot-inventory-close')!.addEventListener('click',closeInventory);
inventoryDialog.addEventListener('cancel',e=>{e.preventDefault();closeInventory();});
inventoryDialog.addEventListener('close',()=>{inventoryOpen=false;update();});
let selectedSlot=0,inventorySelection:number|undefined,inventoryHalf=false,inventoryAmount:number|undefined,lastInventoryTap={slot:-1,time:-Infinity};
const inventoryCounts=new Map<number,number>();
const slotHolds=new Map<number,{button:HTMLButtonElement;slot:number;timer:number;started:number;dropped:boolean;x:number;y:number;cancelled:boolean}>();
function requestDrop(slot=-1,all=touchSettings.dropStack,count?:number){if(slot>40)return;if(approved&&!paused&&!stopped&&socket?.readyState===WebSocket.OPEN)socket.send(JSON.stringify({type:'drop',slot,all,...(count?{count}:{})}));}
function cancelSlotHolds(){for(const hold of slotHolds.values()){clearTimeout(hold.timer);hold.button.classList.remove('drop-holding');hold.button.style.removeProperty('--drop-progress');}slotHolds.clear();}
function selectInventory(slot:number|undefined,half=false,count?:number){inventorySelection=slot;inventoryHalf=half;inventoryAmount=count;
 const selected=slot===undefined?undefined:root.querySelector<HTMLButtonElement>('#inventory-player [data-bot-slot="'+slot+'"]');
 document.querySelector('#inventory-selection')!.textContent=selected?(selected.title+(count?'（'+count+'個）':half?'（半分）':'')):'アイテムをタップ → 移動先をタップ';root.querySelectorAll<HTMLButtonElement>('#inventory-actions button').forEach(button=>button.disabled=button.id==='inventory-craft'?paused:slot===undefined||paused||(slot>=41&&button.id!=='inventory-wear'));root.querySelectorAll<HTMLElement>('#inventory-player [data-bot-slot]').forEach(button=>button.classList.toggle('inventory-selected',Number(button.dataset.botSlot)===slot));}
function chooseInventorySlot(slot:number){
 if(inventorySelection===slot){if(performance.now()-lastInventoryTap.time<300&&lastInventoryTap.slot===slot)selectInventory(slot,true,Math.ceil((inventoryCounts.get(slot)||0)/2));else selectInventory(undefined);lastInventoryTap={slot,time:performance.now()};return;}
 if(inventorySelection===undefined){if((inventoryCounts.get(slot)||0)>0)selectInventory(slot);lastInventoryTap={slot,time:performance.now()};return;}
 if(!approved||paused||stopped||socket?.readyState!==WebSocket.OPEN)return;
 socket.send(JSON.stringify({type:'moveItem',source:inventorySelection,target:slot,half:inventoryHalf,...(inventoryAmount?{count:inventoryAmount}:{})}));
 selectInventory(undefined);
}
function bindSlot(button:HTMLButtonElement){
 const slot=Number(button.dataset.botSlot);
 button.onpointerdown=e=>{
  if(!approved||paused||settingsOpen||editor?.active)return;
  if(!button.closest('#inventory-player')){e.preventDefault();button.setPointerCapture(e.pointerId);}
  const hold={button,slot,timer:0,started:performance.now(),dropped:false,x:e.clientX,y:e.clientY,cancelled:false};
  button.classList.add('drop-holding');hold.timer=window.setTimeout(()=>{if(hold.cancelled)return;hold.dropped=true;if(button.closest('#inventory-player')){button.setPointerCapture(e.pointerId);selectInventory(slot,false,1);}else requestDrop(slot);button.style.setProperty('--drop-progress','1');},button.closest('#inventory-player')?350:touchSettings.holdToDrop);slotHolds.set(e.pointerId,hold);
 };
 button.onpointermove=e=>{const hold=slotHolds.get(e.pointerId);if(hold&&!hold.dropped&&Math.hypot(e.clientX-hold.x,e.clientY-hold.y)>12){hold.cancelled=true;clearTimeout(hold.timer);button.classList.remove('drop-holding');}};
 const finish=(e:PointerEvent)=>{const hold=slotHolds.get(e.pointerId);if(!hold)return;clearTimeout(hold.timer);slotHolds.delete(e.pointerId);button.classList.remove('drop-holding');button.style.removeProperty('--drop-progress');
  if(e.type==='pointerup'&&hold.dropped&&button.closest('#inventory-player')){const box=inventoryDialog.getBoundingClientRect();if(e.clientX<box.left||e.clientX>box.right||e.clientY<box.top||e.clientY>box.bottom){requestDrop(slot,false,inventoryAmount);selectInventory(undefined);}}
  if(e.type==='pointerup'&&!hold.dropped&&!hold.cancelled){if(button.closest('#inventory-player'))chooseInventorySlot(slot);else requestInventory(slot);}
 };
 button.onpointerup=finish;button.onpointercancel=finish;button.onlostpointercapture=finish;
 button.onclick=e=>{if(e.detail===0){if(button.closest('#inventory-player'))chooseInventorySlot(slot);else requestInventory(slot);}};
}
root.querySelectorAll<HTMLButtonElement>('[data-bot-slot]').forEach(bindSlot);
inventoryDialog.addEventListener('click',e=>{if(e.target===inventoryDialog&&inventorySelection!==undefined){const box=inventoryDialog.getBoundingClientRect(),event=e as MouseEvent;if(event.clientX<box.left||event.clientX>box.right||event.clientY<box.top||event.clientY>box.bottom){requestDrop(inventorySelection,!inventoryAmount,inventoryAmount);selectInventory(undefined);}}});
document.querySelector('.inventory-avatar')!.addEventListener('click',()=>{if(inventorySelection!==undefined){requestDrop(inventorySelection,!inventoryAmount,inventoryAmount);selectInventory(undefined);}});
document.querySelector('#inventory-equip')!.addEventListener('click',()=>{if(inventorySelection!==undefined&&inventorySelection<41)requestInventory(inventorySelection);});
document.querySelector('#inventory-wear')!.addEventListener('click',()=>{if(inventorySelection===undefined||paused||socket?.readyState!==WebSocket.OPEN)return;socket.send(JSON.stringify({type:'equip',slot:inventorySelection<36?inventorySelection:selectedSlot,target:inventorySelection<36?Number(document.querySelector<HTMLSelectElement>('#equipment-target')!.value):inventorySelection}));});
document.querySelector('#inventory-drop-one')!.addEventListener('click',()=>{if(inventorySelection!==undefined)requestDrop(inventorySelection,false);});
document.querySelector('#inventory-drop-stack')!.addEventListener('click',()=>{if(inventorySelection!==undefined)requestDrop(inventorySelection,true);});
inventoryDialog.addEventListener('click',e=>{if(e.target===inventoryDialog)selectInventory(undefined);});
document.querySelector('#drop-item')!.addEventListener('click',()=>{if(ready)requestDrop();});
const icons=new Map<string,string>();
function iconLabel(label:HTMLElement,key:unknown,name:string){
 label.replaceChildren();const text=document.createElement('span');text.className='item-name';text.textContent=name;label.append(text);label.classList.remove('has-icon');
 if(typeof key!=='string'||!/^\d+$/.test(key))return;
 const image=document.createElement('img');image.className='item-icon';image.dataset.iconKey=key;image.alt=name;image.draggable=false;
 const cached=icons.get(key);if(cached){image.src=cached;label.classList.add('has-icon');}label.append(image);
}
function sendUi(action:string,fields:Record<string,unknown>={}){if(approved&&(!paused||action==='close')&&!stopped&&socket?.readyState===WebSocket.OPEN)socket.send(JSON.stringify({type:'ui',action,...fields}));}
const skillButton=document.querySelector<HTMLButtonElement>('#mwa-skill')!;
skillButton.onpointerdown=e=>{if(!ready)return;e.preventDefault();skillButton.setPointerCapture(e.pointerId);skillButton.classList.add('pressed');sendUi('skillStart');};
const endSkill=(e:PointerEvent)=>{if(!skillButton.classList.contains('pressed'))return;skillButton.classList.remove('pressed');sendUi(e.type==='pointerup'&&ready?'skillRelease':'skillCancel');};
skillButton.onpointerup=endSkill;skillButton.onpointercancel=endSkill;skillButton.onlostpointercapture=endSkill;
document.querySelector('#mwa-sheathe')!.addEventListener('click',()=>{if(ready)sendUi('sheathe');});
const blockButton=document.createElement('button');blockButton.id='block-open';blockButton.textContent='機能ブロックを開く';blockButton.addEventListener('click',()=>{if(ready)sendUi('openBlock');});root.querySelector('.actions')!.append(blockButton);
const maw=mawUi(root,sendUi,open=>{if(open){closeInventory();release();}menuOpen=open;update();});
const mawSkillsButton=document.createElement('button');mawSkillsButton.id='maw-skills-open';mawSkillsButton.textContent='MAW 技の選択';mawSkillsButton.hidden=true;mawSkillsButton.onclick=()=>sendUi('mawSkills');root.querySelector('.inventory-side')!.append(mawSkillsButton);
const menu=remoteMenu(root,sendUi,open=>{if(open){closeInventory();release();}menuOpen=open;update();},iconLabel);
const chat=document.createElement('dialog');chat.id='bot-chat';chat.innerHTML='<header><h2>チャット / コマンド</h2><button aria-label="閉じる">×</button></header><ul aria-live="polite"></ul><form><input maxlength="256" aria-label="メッセージまたはコマンド" placeholder="メッセージ / コマンド"><button>送信</button></form>';root.append(chat);
function closeChat(){chat.close();chatOpen=false;release();update();}
chat.querySelector('header button')!.addEventListener('click',closeChat);chat.addEventListener('cancel',e=>{e.preventDefault();closeChat();});
document.querySelector('#chat-open')!.addEventListener('click',()=>{if(!approved)return;closeInventory();release();chatOpen=true;update();chat.showModal();chat.querySelector('input')!.focus();});
chat.querySelector('form')!.addEventListener('submit',e=>{e.preventDefault();const input=chat.querySelector('input')!;if(input.value.trim()){sendUi('chat',{text:input.value});input.value='';}});
const craftButton=document.createElement('button');craftButton.id='inventory-craft';craftButton.textContent='クラフト（2×2）';craftButton.addEventListener('click',()=>sendUi('craft'));document.querySelector('#inventory-actions')!.append(craftButton);
function renderIcons(data:{icons?:unknown}){
 if(!Array.isArray(data.icons)||data.icons.length>256)return;
 for(const icon of data.icons){if(!icon||typeof icon.key!=='string'||!/^\d+$/.test(icon.key)||typeof icon.image!=='string'||icon.image.length>65536||!/^data:image\/png;base64,[A-Za-z0-9+/=]+$/.test(icon.image))continue;
 icons.set(icon.key,icon.image);while(icons.size>1024)icons.delete(icons.keys().next().value!);
 root.querySelectorAll<HTMLImageElement>('img[data-icon-key="'+icon.key+'"]').forEach(image=>{image.src=icon.image;image.parentElement!.classList.add('has-icon');});}
}
document.querySelector('#inventory-open-backpack')!.addEventListener('click',()=>{if(ready)sendUi('backpack');});
function renderInventory(data: {selected?:unknown;slots?:unknown;creative?:unknown;weaponSkills?:unknown;backpacks?:unknown}) {
 mawSkillsButton.hidden=data.weaponSkills!==true;document.querySelector<HTMLButtonElement>('#mwa-skill')!.hidden=data.weaponSkills!==true;document.querySelector<HTMLButtonElement>('#mwa-sheathe')!.hidden=data.weaponSkills!==true;
 document.querySelector<HTMLButtonElement>('#inventory-open-backpack')!.hidden=data.backpacks!==true;
 const wasCreative=creativeAllowed;creativeAllowed=data.creative===true;
 inventoryTab(creativeAllowed,false);if(creativeAllowed&&!wasCreative&&inventoryOpen)requestCreative();document.querySelector<HTMLButtonElement>('#tab-creative')!.disabled=!creativeAllowed;
 if(!Number.isInteger(data.selected)||!Array.isArray(data.slots)||(data.slots.length<41||data.slots.length>169))return;
 for(const item of data.slots){if(!item||!Number.isInteger(item.slot)||item.slot<0||item.slot>168||typeof item.name!=='string'||typeof item.count!=='number')return;}
 const equipmentTarget=document.querySelector<HTMLSelectElement>('#equipment-target')!;
 const accessoryGrid=document.querySelector<HTMLElement>('#inventory-accessories')!;accessoryGrid.hidden=data.slots.length===41;
 equipmentTarget.querySelectorAll('option[data-accessory]').forEach(option=>option.remove());
 root.querySelectorAll('[data-accessory-slot]').forEach(button=>button.remove());
 for(const item of data.slots){if(item.slot<41)continue;const option=document.createElement('option');option.value=String(item.slot);option.textContent=item.label;option.dataset.accessory='true';equipmentTarget.append(option);const button=document.createElement('button');button.dataset.botSlot=String(item.slot);button.dataset.accessorySlot='true';button.innerHTML='<span></span><small></small>';const outline=document.createElement('i');outline.className='accessory-outline';outline.innerHTML=accessoryPlaceholder(item.label||'');button.append(outline);const caption=document.createElement('em');caption.textContent=item.label;button.append(caption);accessoryGrid.append(button);bindSlot(button);}
 inventoryCounts.clear();for(const item of data.slots)inventoryCounts.set(item.slot,item.count);
 selectedSlot=data.selected as number;
 for(const item of data.slots){root.querySelectorAll<HTMLButtonElement>('[data-bot-slot="'+item.slot+'"]').forEach(button=>{iconLabel(button.querySelector('span')!,item.count>0?item.iconKey:'',item.count>0?item.name:'');button.setAttribute('aria-label',item.name||'空の持ち物');button.querySelector('small')!.textContent=item.count>1?String(item.count):'';button.title=(item.label||['足','脚','胴','頭','オフハンド'][item.slot-36]||'')+' '+(item.count>0?item.name:'空');button.classList.toggle('selected',item.slot===data.selected);});}
 selectInventory(inventorySelection,inventoryHalf,inventoryAmount);inventoryLayout.refresh();
}
const settingsDialog=document.querySelector<HTMLDialogElement>('#bot-settings')!;
function closeSettings(){settingsDialog.close();settingsOpen=false;release();update();}
document.querySelector('#settings-open')!.addEventListener('click',()=>{closeInventory();release();settingsOpen=true;update();settingsDialog.showModal();});
document.querySelector('#settings-close')!.addEventListener('click',closeSettings);
settingsDialog.addEventListener('close',()=>{settingsOpen=false;update();});
settingsDialog.addEventListener('cancel',e=>{e.preventDefault();closeSettings();});
document.querySelector('#fullscreen')!.addEventListener('click',async()=>{
 release();
 try{if(document.fullscreenElement){await document.exitFullscreen();return;}if(document.documentElement.requestFullscreen){await document.documentElement.requestFullscreen();return;}}catch{}
 closeInventory();settingsOpen=true;update();settingsDialog.showModal();
 document.querySelector('#fullscreen-help')!.textContent='このブラウザではボタンで全画面にできません。iPhoneはSafariの共有 →「ホーム画面に追加」→「Webアプリとして開く」で、追加したアイコンから起動してください。部屋が変わったら新しいQRから追加し直します。';
});
let creativeAllowed=false, creativeTab='', creativePage=0, creativePages=1,creativeAppending=false,creativeLoading=false;
const creativeCategory=document.querySelector<HTMLSelectElement>('#creative-category')!;
function requestCreative(item=-1,append=false){
 if(!approved||paused||!creativeAllowed||stopped||socket?.readyState!==WebSocket.OPEN)return;
 if(item<0){creativeAppending=append;creativeLoading=true;}
 socket.send(JSON.stringify({type:'creative',tab:creativeTab,page:creativePage,item}));
}
function inventoryTab(creative:boolean,request=true){
 inventoryDialog.dataset.creative=String(creativeAllowed);
 document.querySelector<HTMLElement>('#creative-locked')!.hidden=creativeAllowed;
 document.querySelector('#tab-inventory')!.setAttribute('aria-pressed',String(!creative));document.querySelector('#tab-creative')!.setAttribute('aria-pressed',String(creative));
 if(creative&&request)requestCreative();
}
document.querySelector('#tab-inventory')!.addEventListener('click',()=>{inventoryView('inventory');inventoryTab(false);});
document.querySelector('#tab-creative')!.addEventListener('click',()=>{creativePage=0;inventoryView('creative');inventoryTab(true);});
creativeCategory.onchange=()=>{creativeTab=creativeCategory.value;creativePage=0;requestCreative();};
document.querySelector('#creative-prev')!.addEventListener('click',()=>{creativePage=Math.max(0,creativePage-1);requestCreative();});
document.querySelector('#creative-next')!.addEventListener('click',()=>{creativePage=Math.min(creativePages-1,creativePage+1);requestCreative();});
function loadMoreCreative(){
 const grid=document.querySelector<HTMLElement>('#creative-items')!;
 if(grid.clientHeight===0||grid.clientWidth===0||!inventoryOpen||!approved||paused||stopped||!creativeAllowed||creativeLoading||creativePage+1>=creativePages||grid.scrollHeight-grid.scrollTop-grid.clientHeight>40)return;
 creativePage++;requestCreative(-1,true);
}
document.querySelector('#creative-items')!.addEventListener('scroll',loadMoreCreative);
function renderCreative(data: {tabs?:unknown;items?:unknown;tab?:unknown;page?:unknown;pages?:unknown}){
 if(!Array.isArray(data.tabs)||data.tabs.length>128||!Array.isArray(data.items)||data.items.length>45||typeof data.tab!=='string'||typeof data.page!=='number'||typeof data.pages!=='number')return;
 if(data.tabs.some(t=>typeof t.id!=='string'||typeof t.name!=='string')||data.items.some(i=>!Number.isInteger(i.index)||i.index<0||i.index>=25000||typeof i.name!=='string'))return;
 creativeCategory.replaceChildren();for(const tab of data.tabs){const option=document.createElement('option');option.value=tab.id;option.textContent=tab.name;creativeCategory.append(option);}
 creativeTab=data.tab;creativePage=data.page;creativePages=data.pages;creativeCategory.value=creativeTab;
 creativeLoading=false;
 const strip=document.querySelector('#creative-tab-strip')!;const scrollLeft=strip.scrollLeft;strip.replaceChildren();
 for(const tab of data.tabs){const button=document.createElement('button');button.textContent=tab.name;button.title=tab.name;button.setAttribute('aria-pressed',String(tab.id===creativeTab));button.onclick=()=>{creativeTab=tab.id;creativePage=0;requestCreative();};strip.append(button);}
 strip.scrollLeft=scrollLeft;
 const grid=document.querySelector('#creative-items')!;if(!creativeAppending){grid.replaceChildren();grid.scrollTop=0;}
 for(const item of data.items){const button=document.createElement('button');const label=document.createElement('span');iconLabel(label,item.iconKey,item.name);button.append(label);button.title=item.name;button.setAttribute('aria-label',item.name);button.onclick=()=>{requestCreative(item.index);document.querySelector('#creative-status')!.textContent=item.name+' を選択しました';};grid.append(button);}
 creativeAppending=false;
 document.querySelector('#creative-page')!.textContent=(creativePage+1)+' / '+creativePages;
 document.querySelector<HTMLButtonElement>('#creative-prev')!.disabled=creativePage<=0;document.querySelector<HTMLButtonElement>('#creative-next')!.disabled=creativePage+1>=creativePages;
 document.querySelector('#creative-status')!.textContent='タップで持ち物に追加。下へスクロールして続きを表示';
 requestAnimationFrame(()=>{const grid=document.querySelector<HTMLElement>('#creative-items')!;if(grid.scrollHeight<=grid.clientHeight)loadMoreCreative();});
}
function connect(){
 if(stopped)return;
 approved=false;ready=false;creativeAllowed=false;document.querySelector<HTMLButtonElement>('#tab-creative')!.disabled=true;document.querySelector('#identity')!.textContent='接続中…';delete root.dataset.number;status.textContent='せつぞく中…';
 const ws=socket=new WebSocket(websocketUrl(location,'/classroom/ws'));
 ws.onopen=()=>{lastPong=performance.now();lastSent='';sentAt=-Infinity;ws.send(JSON.stringify({type:'hello',token,resume,...profile}));};
ws.onmessage=async e=>{
 if(typeof e.data!=='string'){
  try{const id=await video.receive(e.data instanceof Blob?e.data:new Blob([e.data]));if(id!==undefined)videoError='';if(id!==undefined&&socket===ws&&approved&&ws.readyState===WebSocket.OPEN)ws.send(JSON.stringify({type:'frameAck',frame:id}));}catch{videoError='BOT映像を表示できませんでした。ページを開き直してください';}return;
 }
 const data=JSON.parse(e.data);

  if(data.type==='welcome'){if(typeof data.resume!=='string'||!Number.isInteger(data.number)||data.number<1||data.number>8){ws.close();return;}resume=data.resume;try{sessionStorage.setItem(storageKey,resume);}catch{}root.dataset.number=String(data.number);renderIdentity(data);}
  if(data.type==='profile'){renderIdentity(data);document.querySelector('#profile-status')!.textContent='名前とスキンを変更しました';}
  if(data.type==='skins')renderSkinCatalog(data);
  if(data.type==='inventory')renderInventory(data);
  if(data.type==='mawSkills')maw.receive(data);
  if(data.type==='menu')menu.receive(data);
  if(data.type==='menuStatus'&&typeof data.message==='string')status.textContent=data.message;
  if(data.type==='chat'&&typeof data.text==='string'){const line=document.createElement('li');line.textContent=data.text;const list=chat.querySelector('ul')!;list.append(line);while(list.children.length>100)list.firstElementChild!.remove();list.scrollTop=list.scrollHeight;}
  if(data.type==='vitals'){if(inWater!==(data.inWater===true))sneakLatched=false;inWater=data.inWater===true;renderVitals(root,data);maw.status(data.maw);}
  if(data.type==='backpackStatus'&&typeof data.message==='string')document.querySelector('#inventory-selection')!.textContent=data.message.slice(0,256);
  if(data.type==='appearance'&&typeof data.image==='string'&&data.image.length<=262144&&/^data:image\/png;base64,[A-Za-z0-9+/=]+$/.test(data.image)){
   const preview=document.querySelector<HTMLImageElement>('#inventory-appearance')!;preview.src=data.image;preview.hidden=false;document.querySelector<HTMLCanvasElement>('#inventory-skin')!.hidden=true;
  }
  if(data.type==='itemIcons')renderIcons(data);
  if(data.type==='creative')renderCreative(data);
  if(data.type==='creativeDenied'){document.querySelector('#creative-status')!.textContent='主催者が参加者をクリエイティブに切り替えると使えます';}
  if(data.type==='state'){flying=data.flying===true;participantMode=data.mode==='creative'?'creative':'survival';document.querySelector<HTMLElement>('#survival-bars')!.hidden=participantMode!=='survival';document.querySelector('#participant-mode')!.textContent=participantMode==='creative'?(flying?'クリエイティブ / 飛行中':'クリエイティブ'):'サバイバル';root.dataset.flying=String(flying);root.querySelector('[data-key=sneak]')!.textContent=flying?'下降':'スニーク';root.querySelector('[data-key=jump]')!.setAttribute('aria-label',flying?'上昇':'ジャンプ');release();approved=data.approved===true;paused=data.paused!==false;update();requestVideo();requestPreferences();}
  if(data.type==='pong'){videoError=typeof data.videoStatus==='string'?data.videoStatus:'';lastPong=performance.now();if(ready)status.textContent=`うごかせるよ · ${Math.round(lastPong-pingAt)} ms`;}
  if(data.type==='revoked'){stopped=true;resume='';try{sessionStorage.removeItem(storageKey);}catch{}release();ws.close();status.textContent='主催者がさんかをしゅうりょうしました';}
 };
 ws.onclose=()=>{resetVitals(root);maw.reset();document.querySelector<HTMLImageElement>('#inventory-appearance')!.hidden=true;document.querySelector<HTMLCanvasElement>('#inventory-skin')!.hidden=false;closeSettings();closeInventory();root.querySelectorAll('[data-bot-slot] span,[data-bot-slot] small').forEach(label=>label.textContent='');document.querySelector('#identity')!.textContent='未接続';delete root.dataset.number;video.clear();ready=false;approved=false;release();update();root.dataset.enabled='false';if(!stopped){status.textContent='切断 · さいせつぞく中…';document.querySelector('footer')!.textContent='PCの学校用起動を確認してください。部屋を作り直した場合は、新しいQRを読み直します。';retry=window.setTimeout(connect,2000);}};
 ws.onerror=()=>ws.close();
}
root.querySelectorAll<HTMLElement>('[data-key],[data-move]').forEach(button=>{
 button.onpointerdown=e=>{
  if(!ready)return;e.preventDefault();button.setPointerCapture(e.pointerId);const key=button.dataset.key??button.dataset.move!;
  if(key.split(' ').includes('forward')&&touchSettings.doubleForward&&forwardTaps.tap(performance.now()))sprintLatched=true;
  if(key==='jump'&&jumpTaps.tap(performance.now())){
   if(participantMode==='creative'&&socket?.readyState===WebSocket.OPEN)socket.send('{"type":"flight"}');
   else if(touchSettings.doubleJumpSneak)sneakLatched=!sneakLatched;
  }
  if(key==='sprint')sprintLatched=!sprintLatched;else if(key==='sneak'&&touchSettings.sneakToggle&&!flying&&!inWater)sneakLatched=!sneakLatched;else held.set(e.pointerId,key);
  feedback();send(true);
 };
 const up=(e:PointerEvent)=>{if(held.has(e.pointerId)&&e.type!=='pointerup'&&button.dataset.key==='attack')sendUi('skillCancel');if(held.delete(e.pointerId)){if(!keysHeld().includes('forward'))sprintLatched=false;feedback();send(true);}};
 button.onpointermove=e=>{
  if(!ready||!button.closest('.dpad')||!button.hasPointerCapture(e.pointerId))return;
  const target=document.elementFromPoint(e.clientX,e.clientY)?.closest<HTMLElement>('.dpad button');
  const next=target?.dataset.move??target?.dataset.key;
  if(next)held.set(e.pointerId,next);else held.delete(e.pointerId);
  feedback();send(true);
 };
 button.onpointerup=up;button.onpointercancel=up;button.onlostpointercapture=up;
});
const stick=document.querySelector<HTMLElement>('#joystick')!;
function resetStick(){document.querySelector<HTMLElement>('#joystick i')?.style.setProperty('transform','translate(-50%,-50%)');}
function moveStick(e:PointerEvent){const box=stick.getBoundingClientRect(),radius=box.width/2;let x=(e.clientX-box.x-radius)/radius,y=(e.clientY-box.y-box.height/2)/radius;const length=Math.max(1,Math.hypot(x,y));x/=length;y/=length;
 movement=stickVector(x,y);const wasForward=stickKeys.includes('forward');stickKeys=[];if(movement.z>.01)stickKeys.push('forward');if(movement.z<-.01)stickKeys.push('back');if(movement.x<-.01)stickKeys.push('left');if(movement.x>.01)stickKeys.push('right');if(!wasForward&&stickKeys.includes('forward')&&touchSettings.doubleForward&&forwardTaps.tap(performance.now()))sprintLatched=true;
 if(strongForward(movement))sprintLatched=true;else if(!stickKeys.includes('forward'))sprintLatched=false;
 stick.querySelector<HTMLElement>('i')!.style.transform='translate(calc(-50% + '+x*radius*.55+'px),calc(-50% + '+y*radius*.55+'px))';feedback();send();
}
stick.onpointerdown=e=>{if(!ready||stickPointer!==undefined)return;e.preventDefault();stickPointer=e.pointerId;stick.setPointerCapture(e.pointerId);moveStick(e);};
stick.onpointermove=e=>{if(e.pointerId===stickPointer)moveStick(e);};
const releaseStick=(e:PointerEvent)=>{if(e.pointerId!==stickPointer)return;stickPointer=undefined;stickKeys=[];movement=undefined;sprintLatched=false;resetStick();feedback();send(true);};stick.onpointerup=releaseStick;stick.onpointercancel=releaseStick;stick.onlostpointercapture=releaseStick;
const area=document.querySelector<HTMLElement>('#look')!;
area.onpointerdown=e=>{
 if(!ready||look)return;e.preventDefault();area.setPointerCapture(e.pointerId);look={id:e.pointerId,x:e.clientX,y:e.clientY,startX:e.clientX,startY:e.clientY,moved:false,mining:false,gesture:new WorldGesture(e.clientX,e.clientY,performance.now())};
 const id=e.pointerId;
 longPress=window.setTimeout(()=>{if(touchSettings.mode==='joystick_crosshair'||!ready||look?.id!==id||look.moved||!video.point(look.x,look.y))return;look.gesture.advance(performance.now());look.mining=true;touchPoint={x:look.x,y:look.y};held.set(id,'attack');feedback();send(true);},350);
};
area.onpointermove=e=>{if(look?.id!==e.pointerId)return;
 const move=look.gesture.move(e.clientX,e.clientY);
 if(look.gesture.state==='look'){
  look.moved=true;clearTimeout(longPress);
  if(move.release){sendUi('skillCancel');look.mining=false;held.delete(e.pointerId);touchPoint=undefined;send(true);}
  dx+=move.dx*touchSettings.sensitivity;dy+=move.dy*touchSettings.sensitivity*(touchSettings.invertY?-1:1);
 }else if(look.mining)touchPoint={x:e.clientX,y:e.clientY};
 look.x=e.clientX;look.y=e.clientY;
};
const endLook=(e:PointerEvent)=>{
 if(look?.id!==e.pointerId)return;clearTimeout(longPress);if(e.type!=='pointerup')sendUi('skillCancel');
 if(touchSettings.mode!=='joystick_crosshair'&&e.type==='pointerup'&&!look.moved&&!look.mining&&ready&&video.point(e.clientX,e.clientY)){
  touchPoint={x:e.clientX,y:e.clientY};held.set(e.pointerId,'tap');send(true);
 }
 held.delete(e.pointerId);touchPoint=undefined;feedback();send(true);look=undefined;
};
area.onpointerup=endLook;area.onpointercancel=endLook;area.onlostpointercapture=endLook;

function requestPreferences(){if(approved&&socket?.readyState===WebSocket.OPEN)socket.send(JSON.stringify({type:'preferences',autoJump:touchSettings.autoJump}));}
function applyTouchSettings(persist=true){
 root.dataset.touchMode=touchSettings.mode;root.querySelectorAll('[data-mobile-preset]').forEach(button=>button.setAttribute('aria-pressed',String((button as HTMLElement).dataset.mobilePreset===touchSettings.mode)));root.dataset.split=String(touchSettings.splitActions);root.dataset.showActions=String(touchSettings.showActions);root.dataset.showDrop=String(touchSettings.showDrop);
 root.style.setProperty('--controls-opacity',String(touchSettings.showButtons?touchSettings.opacity/100:0));
 const base=Math.max(44,Math.min(68,innerWidth*(innerHeight>innerWidth?.13:.09)));root.style.setProperty('--size',Math.round(Math.min(base*touchSettings.size/100,Math.max(32,(innerHeight-130)/4)))+'px');
 document.querySelector<HTMLSelectElement>('#touch-mode')!.value=touchSettings.mode;
 root.querySelectorAll<HTMLInputElement>('[data-touch]').forEach(input=>{const key=input.dataset.touch as keyof typeof touchSettings;const value=touchSettings[key];if(typeof value==='boolean')input.checked=value;else if(typeof value==='number'){input.value=String(value);const output=root.querySelector('[data-touch-value="'+key+'"]');if(output)output.textContent=key==='sensitivity'?Math.round(value*100)+'%':key==='holdToDrop'?(value/1000).toFixed(1)+'秒':value+'%';}});
 editor?.apply();if(persist)saveTouchSettings(touchSettings);
}
root.querySelectorAll<HTMLButtonElement>('[data-mobile-preset]').forEach(button=>button.onclick=()=>{
 release();touchSettings=normalizeTouchSettings({...touchSettings,...mobilePreset(button.dataset.mobilePreset as typeof touchSettings.mode)});applyTouchSettings();requestPreferences();
});
document.querySelector<HTMLSelectElement>('#touch-mode')!.onchange=e=>{release();touchSettings.mode=(e.target as HTMLSelectElement).value as typeof touchSettings.mode;applyTouchSettings();};
root.querySelectorAll<HTMLInputElement>('[data-touch]').forEach(input=>input.oninput=()=>{release();touchSettings=normalizeTouchSettings({...touchSettings,[input.dataset.touch!]:input.type==='checkbox'?input.checked:Number(input.value)});applyTouchSettings();requestPreferences();});
editor=new LayoutEditor(root,()=>touchSettings,()=>applyTouchSettings(false),()=>{release();update();settingsOpen=true;update();settingsDialog.showModal();});
document.querySelector('#customize-touch')!.addEventListener('click',()=>{closeSettings();release();editor!.open();update();});
document.querySelector('#touch-reset')!.addEventListener('click',()=>{release();touchSettings=normalizeTouchSettings(null);applyTouchSettings();requestPreferences();});
applyTouchSettings(false);
function requestVideo(){const enabled=videoEnabled&&approved&&!document.hidden&&!stopped;video.setEnabled(enabled);if(socket?.readyState===WebSocket.OPEN)socket.send(JSON.stringify({type:'video',enabled,quality:qualities[qualityIndex]}));}
document.querySelector('#video-toggle')!.addEventListener('click',()=>{videoEnabled=!videoEnabled;document.querySelector('#video-toggle')!.textContent=`映像: ${videoEnabled?'ON':'OFF'}`;requestVideo();});
document.querySelector('#leave')!.addEventListener('click',()=>{release();stopped=true;clearTimeout(retry);if(socket?.readyState===WebSocket.OPEN)socket.send('{"type":"disconnect"}');socket?.close();resume='';try{sessionStorage.removeItem(storageKey);}catch{}status.textContent='おつかれさま！';});
window.addEventListener('blur',release);window.addEventListener('resize',()=>{release();applyTouchSettings(false);});window.addEventListener('pagehide',()=>{release();stopped=true;clearTimeout(retry);socket?.close();});
document.addEventListener('visibilitychange',()=>{if(document.hidden)release();update();requestVideo();});document.addEventListener('contextmenu',e=>e.preventDefault());
setInterval(()=>{if(ready)send();for(const hold of slotHolds.values()){if(hold.cancelled)continue;if(hold.button.closest('#inventory-player')){if(hold.dropped){const total=inventoryCounts.get(hold.slot)||0;if(total){const count=holdQuantity(total,performance.now()-hold.started);if(inventoryAmount!==count)selectInventory(hold.slot,false,count);hold.button.style.setProperty('--drop-progress',String(count/total));}}}else if(!hold.dropped)hold.button.style.setProperty('--drop-progress',String(Math.min(1,(performance.now()-hold.started)/touchSettings.holdToDrop)));}video.refresh();if(videoEnabled&&videoError)document.querySelector('#video-state')!.textContent=videoError;},33);
setInterval(()=>{if(socket?.readyState===WebSocket.OPEN){if(performance.now()-lastPong>5000){socket.close();return;}pingAt=performance.now();socket.send('{"type":"ping"}');}},1000);
update();root.dataset.enabled='false';if(/^[A-Za-z0-9_-]{43}$/.test(token))connect();else status.textContent='主催者のQRコードからひらいてね';

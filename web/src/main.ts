import './styles/controller.css';
import { addonActions } from './controller/addons';
import { VideoView } from './video/view';
import { Link } from './network/link';
import { TouchController } from './controller/touch';
import { normalizePreferences, defaults } from './controller/preferences';
const app = document.querySelector<HTMLElement>('#app')!;
const button = (key: string, label: string) => `<button data-key="${key}" aria-label="${label}">${label}</button>`;
app.innerHTML = `<canvas id="game-view" aria-label="Minecraft映像"></canvas><div id="video-state" role="status"></div><header><b>CRAFTLINK</b><output id="status" aria-live="polite">未接続</output><button id="game-back">戻る</button><button id="addons-open" hidden>MOD操作</button><button id="settings-open">操作設定</button><button id="fullscreen">全画面</button><button id="disconnect">切断</button></header>
<section id="addons-panel" hidden aria-label="MODの追加操作"></section><main id="controls"><section class="movement"><div id="joystick" aria-label="移動ジョイスティック"><span class="stick-guide">＋</span><span id="stick-knob"></span></div><div class="dpad">${button('forward','▲')}${button('left','◀')}${button('back','▼')}${button('right','▶')}</div><div class="moves">${button('sneak','スニーク')}${button('sprint','ダッシュ')}</div></section>
<div id="look"><span>スワイプで視点移動</span><i aria-hidden="true">＋</i></div>
<section class="actions">${button('attack','攻撃 / 掘る')}${button('use','使う / 置く')}${button('jump','ジャンプ')}</section></main>
<footer id="inventory"><div class="tools">${button('drop','捨てる')}${button('swap','オフハンド')}${button('escape','ESC')}</div><nav aria-label="ホットバー">${Array.from({length:9},(_,i)=>`<button data-slot="${i}" class="${i===0?'selected':''}">${i+1}</button>`).join('')}<button data-key="inventory" id="inventory-open" aria-label="インベントリを開く">⋯</button></nav></footer>
<aside>スマートフォンを横向きにすると操作しやすくなります</aside>
<dialog id="settings"><h1>操作設定</h1><p>携帯コントローラー</p><label>操作方式<select id="layout"><option value="tap">ジョイスティック＋直接タップ</option><option value="mobile">ジョイスティック＋照準</option><option value="classic_tap">方向ボタン＋直接タップ</option><option value="classic">方向ボタン＋照準</option></select></label><label>視点感度 <output id="sensitivity-value"></output><input id="sensitivity" type="range" min="0.03" max="0.5" step="0.01"></label><label>ボタンサイズ <output id="size-value"></output><input id="button-size" type="range" min="80" max="140" step="1"></label><label class="video-option"><input id="video-enabled" type="checkbox" checked> Minecraft映像を表示</label><p class="note">タップで設置・使用、長押しで破壊、ドラッグで視点。<br>ホットバー長押しで捨てる。スニークはタップで切替。<br>直接タップは映像受信中の一人称視点で使用します。</p><button id="settings-done">完了</button></dialog>`;
const video = new VideoView(document.querySelector<HTMLCanvasElement>('#game-view')!,app,()=>touch.reset());
const touch = new TouchController(app, () => link.send(touch.packet()), video, event=>link.send({type:'touch',...event}));
let preferences = {...defaults};
const layout = document.querySelector<HTMLSelectElement>('#layout')!;
const sensitivity = document.querySelector<HTMLInputElement>('#sensitivity')!;
const size = document.querySelector<HTMLInputElement>('#button-size')!;
const dialog = document.querySelector<HTMLDialogElement>('#settings')!;
const applyPreferences = (data: unknown) => {
  const hadInput = touch.hasActiveInput();
  touch.reset();
  preferences=normalizePreferences(data);
  app.dataset.layout=preferences.layout;
  app.style.setProperty('--control-scale', String(preferences.buttonSize / 100));
  touch.sensitivity=preferences.sensitivity;
  layout.value=preferences.layout; sensitivity.value=String(preferences.sensitivity); size.value=String(preferences.buttonSize);
  document.querySelector('#sensitivity-value')!.textContent=preferences.sensitivity.toFixed(2);
  document.querySelector('#size-value')!.textContent=`${preferences.buttonSize}%`;
  if (hadInput) link.send(touch.packet());
  try { localStorage.setItem('craftlink.preferences',JSON.stringify(preferences)); } catch { /* Storage can be unavailable; controls still work. */ }
};
const link = new Link(location.hash.slice(1), text => document.querySelector('#status')!.textContent = text, () => {touch.reset();video.clear();}, applyPreferences, blob=>video.receive(blob), value=>{
  touch.reset();
  const panel=document.querySelector<HTMLElement>('#addons-panel')!;
  const actions=addonActions(value);
  panel.replaceChildren(); panel.hidden=true;
  document.querySelector<HTMLButtonElement>('#addons-open')!.hidden=actions.length===0;
  const buttons=actions.map(action=>{const button=document.createElement('button');button.dataset.key=action.id;button.textContent=action.label;panel.append(button);touch.bindKeyButton(button);return button;});
  let page=0;
  const paging=document.createElement('div');paging.id='addons-paging';
  const previous=document.createElement('button'),next=document.createElement('button'),count=document.createElement('span');
  previous.textContent='前へ';next.textContent='次へ';paging.append(previous,count,next);
  const show=()=>{touch.reset();buttons.forEach((button,i)=>button.hidden=Math.floor(i/4)!==page);previous.disabled=page===0;next.disabled=(page+1)*4>=buttons.length;count.textContent=`${page+1} / ${Math.max(1,Math.ceil(buttons.length/4))}`;link.send(touch.packet());};
  previous.onclick=()=>{page--;show();};next.onclick=()=>{page++;show();};
  if(buttons.length>4)panel.append(paging);
  show();
});
let stored: unknown = defaults;
try { stored=JSON.parse(localStorage.getItem('craftlink.preferences') ?? 'null'); } catch { /* Use defaults. */ }
applyPreferences(stored);
if (/^[A-Za-z0-9_-]{43}$/.test(location.hash.slice(1))) link.connect();
else document.querySelector('#status')!.textContent = 'PCに表示したQRコードから開いてください';
const edit = () => applyPreferences({layout:layout.value,sensitivity:Number(sensitivity.value),buttonSize:Number(size.value)});
document.querySelector<HTMLInputElement>('#video-enabled')!.onchange=e=>{release();const enabled=(e.target as HTMLInputElement).checked;video.setEnabled(enabled);link.setVideoEnabled(enabled);};
layout.onchange=edit; sensitivity.oninput=edit; size.oninput=edit;
const release = () => { touch.reset(); link.send(touch.packet()); };
document.querySelector('#addons-open')!.addEventListener('click',()=>{release();const panel=document.querySelector<HTMLElement>('#addons-panel')!;panel.hidden=!panel.hidden;});
document.querySelector('#settings-open')!.addEventListener('click', () => { release(); touch.enabled=false; dialog.showModal(); });
document.querySelector('#settings-done')!.addEventListener('click', () => dialog.close());
dialog.addEventListener('close', () => { release(); touch.enabled=true; });
document.querySelector('#game-back')!.addEventListener('click',()=>touch.tapAction('escape'));
document.querySelector('#disconnect')!.addEventListener('click', () => { dialog.close(); link.disconnect(); });
document.querySelector('#fullscreen')!.addEventListener('click', () => { document.documentElement.requestFullscreen?.().catch(() => {}); });
window.addEventListener('blur', release);
window.addEventListener('resize', release);
document.addEventListener('visibilitychange', () => {
  if (document.hidden) { release(); video.clear(); }
  // Stop GPU capture/JPEG/network work while the controller tab is in the background.
  link.setVideoEnabled(!document.hidden && document.querySelector<HTMLInputElement>('#video-enabled')!.checked);
});
window.addEventListener('pagehide', () => link.disconnect());
document.addEventListener('contextmenu', e => e.preventDefault());
setInterval(() => { if (!document.hidden && link.ready) {touch.pump();link.send(touch.packet());} video.refresh(); }, 33);
setInterval(() => link.heartbeat(), 1000);

import './styles/controller.css';
import { Link } from './network/link';
import { TouchController } from './controller/touch';
import { normalizePreferences, defaults } from './controller/preferences';
const app = document.querySelector<HTMLElement>('#app')!;
const button = (key: string, label: string) => `<button data-key="${key}" aria-label="${label}">${label}</button>`;
app.innerHTML = `<header><b>CRAFTLINK</b><output id="status" aria-live="polite">未接続</output><button id="settings-open">操作設定</button><button id="fullscreen">全画面</button><button id="disconnect">切断</button></header>
<main id="controls"><section class="movement"><div id="joystick" aria-label="移動ジョイスティック"><span class="stick-guide">＋</span><span id="stick-knob"></span></div><div class="dpad">${button('forward','▲')}${button('left','◀')}${button('back','▼')}${button('right','▶')}</div><div class="moves">${button('sneak','スニーク')}${button('sprint','ダッシュ')}</div></section>
<div id="look"><span>スワイプで視点移動</span><i aria-hidden="true">＋</i></div>
<section class="actions">${button('attack','攻撃 / 掘る')}${button('use','使う / 置く')}${button('jump','ジャンプ')}</section></main>
<footer id="inventory"><div class="tools">${button('inventory','持ち物')}${button('drop','捨てる')}${button('swap','オフハンド')}${button('escape','ESC')}</div><nav aria-label="ホットバー">${Array.from({length:9},(_,i)=>`<button data-slot="${i}" class="${i===0?'selected':''}">${i+1}</button>`).join('')}</nav></footer>
<aside>スマートフォンを横向きにすると操作しやすくなります</aside>
<dialog id="settings"><h1>操作設定</h1><p>携帯コントローラー</p><label>操作方式<select id="layout"><option value="mobile">携帯版風ジョイスティック</option><option value="classic">方向ボタン</option></select></label><label>視点感度 <output id="sensitivity-value"></output><input id="sensitivity" type="range" min="0.03" max="0.5" step="0.01"></label><label>ボタンサイズ <output id="size-value"></output><input id="button-size" type="range" min="80" max="140" step="1"></label><p class="note">移動と視点は別々の指で操作できます。<br>接続時にはPC側の設定が反映されます。</p><button id="settings-done">完了</button></dialog>`;
const touch = new TouchController(app, () => link.send(touch.packet()));
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
const link = new Link(location.hash.slice(1), text => document.querySelector('#status')!.textContent = text, () => touch.reset(), applyPreferences);
let stored: unknown = defaults;
try { stored=JSON.parse(localStorage.getItem('craftlink.preferences') ?? 'null'); } catch { /* Use defaults. */ }
applyPreferences(stored);
if (/^[A-Za-z0-9_-]{43}$/.test(location.hash.slice(1))) link.connect();
else document.querySelector('#status')!.textContent = 'PCに表示したQRコードから開いてください';
const edit = () => applyPreferences({layout:layout.value,sensitivity:Number(sensitivity.value),buttonSize:Number(size.value)});
layout.onchange=edit; sensitivity.oninput=edit; size.oninput=edit;
const release = () => { touch.reset(); link.send(touch.packet()); };
document.querySelector('#settings-open')!.addEventListener('click', () => { release(); touch.enabled=false; dialog.showModal(); });
document.querySelector('#settings-done')!.addEventListener('click', () => dialog.close());
dialog.addEventListener('close', () => { release(); touch.enabled=true; });
document.querySelector('#disconnect')!.addEventListener('click', () => { dialog.close(); link.disconnect(); });
document.querySelector('#fullscreen')!.addEventListener('click', () => { document.documentElement.requestFullscreen?.().catch(() => {}); });
window.addEventListener('blur', release);
window.addEventListener('resize', release);
document.addEventListener('visibilitychange', () => { if (document.hidden) release(); });
window.addEventListener('pagehide', () => link.disconnect());
document.addEventListener('contextmenu', e => e.preventDefault());
setInterval(() => { if (!document.hidden && link.ready) link.send(touch.packet()); }, 33);
setInterval(() => link.heartbeat(), 1000);

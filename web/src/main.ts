import './styles/controller.css';
import { Link } from './network/link';
import { TouchController } from './controller/touch';
const app = document.querySelector<HTMLElement>('#app')!;
const button = (key: string, label: string) => `<button data-key="${key}" aria-label="${label}">${label}</button>`;
app.innerHTML = `<header><b>CRAFT<span>LINK</span></b><output id="status">未接続</output><button id="fullscreen">全画面</button><button id="disconnect">切断</button></header>
<main><section class="movement"><div class="dpad">${button('forward','▲')}${button('left','◀')}${button('back','▼')}${button('right','▶')}</div><div class="moves">${button('sneak','スニーク')}${button('sprint','ダッシュ')}</div></section>
<div id="look"><span>スワイプで視点移動</span></div>
<section class="actions">${button('attack','攻撃 / 掘る')}${button('use','使う / 置く')}${button('jump','ジャンプ')}</section></main>
<footer><div class="tools">${button('inventory','持ち物')}${button('drop','捨てる')}${button('swap','オフハンド')}${button('escape','ESC')}<label>感度 <input id="sensitivity" type="range" min="0.03" max="0.5" step="0.01"></label></div><nav aria-label="ホットバー">${Array.from({length:9},(_,i)=>`<button data-slot="${i}" class="${i===0?'selected':''}">${i+1}</button>`).join('')}</nav></footer><aside>スマートフォンを横向きにすると操作しやすくなります</aside>`;
const touch = new TouchController(app, () => link.send(touch.packet()));
const token = location.hash.slice(1);
const link = new Link(token, text => document.querySelector('#status')!.textContent = text, () => touch.reset());
if (/^[A-Za-z0-9_-]{43}$/.test(token)) link.connect();
else document.querySelector('#status')!.textContent = 'PCに表示したQRコードから開いてください';
const sensitivity = document.querySelector<HTMLInputElement>('#sensitivity')!;
if (!Number.isFinite(touch.sensitivity) || touch.sensitivity < 0.03 || touch.sensitivity > 0.5) touch.sensitivity = 0.15;
sensitivity.value = String(touch.sensitivity);
sensitivity.oninput = () => { touch.sensitivity=Number(sensitivity.value); localStorage.setItem('sensitivity',sensitivity.value); };
document.querySelector('#disconnect')!.addEventListener('click', () => link.disconnect());
document.querySelector('#fullscreen')!.addEventListener('click', () => { document.documentElement.requestFullscreen?.().catch(() => {}); });
const release = () => { touch.reset(); link.send(touch.packet()); };
window.addEventListener('blur', release);
document.addEventListener('visibilitychange', () => { if (document.hidden) release(); });
window.addEventListener('pagehide', () => link.disconnect());
document.addEventListener('contextmenu', e => e.preventDefault());
setInterval(() => { if (!document.hidden && link.ready) link.send(touch.packet()); }, 33);
setInterval(() => link.heartbeat(), 1000);

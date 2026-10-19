import {nativeMenuImage} from './native-ui';
import './remote-menu.css';
type Send=(action:string,fields?:Record<string,unknown>)=>void;
type Slot={slot:number;x:number;y:number;name:string;count:number;iconKey:string};
export function remoteMenu(root:HTMLElement,send:Send,visibility:(open:boolean)=>void,icon:(label:HTMLElement,key:unknown,name:string)=>void){
 const dialog=document.createElement('dialog');dialog.id='remote-menu';dialog.innerHTML='<header><h2></h2><button aria-label="閉じる">×</button></header><p class="carried" role="status"></p><p class="menu-process" role="status"></p><div class="menu-body"><details><summary>レシピ本</summary><div class="recipes"></div></details><div class="menu-slots"></div></div><p class="menu-help">タップで移動・まとめる / 長押しで高速移動 / 持った素材をなぞって1個ずつ置く</p>';root.append(dialog);
 let revision=-1;
 let id=-1,carried=false,lastTap={slot:-1,time:-Infinity};
 function close(){send('close',{menu:id});if(dialog.open)dialog.close();visibility(false);}
 dialog.querySelector('header button')!.addEventListener('click',close);dialog.addEventListener('cancel',e=>{e.preventDefault();close();});
 const grid=dialog.querySelector<HTMLElement>('.menu-slots')!;let layoutSlots:Slot[]=[];
 function receive(data:{open?:unknown;id?:unknown;title?:unknown;carried?:unknown;slots?:unknown;revision?:unknown;recipes?:unknown;craftWidth?:unknown;craftHeight?:unknown;burnProgress?:unknown;litProgress?:unknown}){
  if(data.open!==true){if(dialog.open)dialog.close();visibility(false);return;}
  if(!Number.isInteger(data.id)||!Array.isArray(data.slots)||data.slots.length>256)return;
  const slots=data.slots as Slot[];if(slots.some(s=>!Number.isInteger(s.slot)||s.slot<0||s.slot>255||!Number.isFinite(s.x)||!Number.isFinite(s.y)||s.x<0||s.y<0||s.x>1000||s.y>1000||typeof s.name!=='string'))return;
  revision=typeof data.revision==='number'?data.revision:-1;grid.classList.remove('native-menu');grid.querySelector('.native-menu-image')?.remove();
  id=Number(data.id);dialog.querySelector('h2')!.textContent=typeof data.title==='string'?data.title:'アイテム';carried=typeof data.carried==='string'&&data.carried.length>0;dialog.querySelector('.carried')!.textContent=carried?String(data.carried):'アイテム → 移動先をタップ';
  layoutSlots=slots;
  const process=dialog.querySelector<HTMLElement>('.menu-process')!;process.replaceChildren();
  const crafting=(data.craftWidth===2&&data.craftHeight===2)||(data.craftWidth===3&&data.craftHeight===3);
  if(crafting)process.textContent=`クラフト ${data.craftWidth}×${data.craftHeight}：素材を置く → 完成品をタップして取り出す`;
  if(typeof data.burnProgress==='number'&&Number.isFinite(data.burnProgress)&&typeof data.litProgress==='number'&&Number.isFinite(data.litProgress)){
   for(const [label,value] of [['精錬',data.burnProgress],['燃料',data.litProgress]] as const){const text=document.createElement('label');text.textContent=label+' ';const meter=document.createElement('progress');meter.max=1;meter.value=Math.max(0,Math.min(1,value));meter.setAttribute('aria-label',label);text.append(meter);process.append(text);}
  }
  process.hidden=!process.hasChildNodes();
  const width=Math.max(176,...slots.map(s=>s.x+18)),height=Math.max(80,...slots.map(s=>s.y+18));grid.style.aspectRatio=width+'/'+height;grid.style.setProperty('--menu-ratio',String(width/height));const layout=id+":"+slots.map(s=>s.slot+","+s.x+","+s.y).join(";");if(grid.dataset.layout!==layout){grid.replaceChildren();grid.dataset.layout=layout;}
  for(const slot of slots){
   const button=grid.querySelector<HTMLButtonElement>('[data-menu-slot="'+slot.slot+'"]')??document.createElement('button');button.replaceChildren();button.dataset.menuSlot=String(slot.slot);button.style.cssText=`left:${slot.x/width*100}%;top:${slot.y/height*100}%;width:${16/width*100}%;height:${16/height*100}%`;button.title=slot.name||'空';button.setAttribute('aria-label',slot.name||'空');const label=document.createElement('span');icon(label,slot.iconKey,slot.name);button.append(label);if(slot.count>1){const count=document.createElement('small');count.textContent=String(slot.count);button.append(count);}if(!button.parentElement)grid.append(button);if(button.dataset.bound)continue;button.dataset.bound="true";
   let timer=0,held=false,moved=false,startX=0,startY=0;const visited=new Set<number>();
   button.onpointerdown=e=>{e.preventDefault();button.setPointerCapture(e.pointerId);startX=e.clientX;startY=e.clientY;held=false;moved=false;visited.clear();timer=window.setTimeout(()=>{if(!carried&&!moved){held=true;send('quick',{menu:id,slot:slot.slot});}},900);};
   button.onpointermove=e=>{if(!button.hasPointerCapture(e.pointerId)||Math.hypot(e.clientX-startX,e.clientY-startY)<8)return;moved=true;clearTimeout(timer);if(!carried)return;const target=document.elementFromPoint(e.clientX,e.clientY)?.closest<HTMLButtonElement>('[data-menu-slot]');if(target){const targetSlot=Number(target.dataset.menuSlot);if(!visited.has(targetSlot)){visited.add(targetSlot);send('click',{menu:id,slot:targetSlot,button:1});}}};
   button.onpointerup=e=>{clearTimeout(timer);if(!held&&!moved){const now=performance.now(),double=lastTap.slot===slot.slot&&now-lastTap.time<300;send(double?'collect':'click',{menu:id,slot:slot.slot});lastTap={slot:slot.slot,time:now};}button.releasePointerCapture(e.pointerId);};button.onpointercancel=()=>clearTimeout(timer);
   button.onclick=e=>{if(e.detail===0)send('click',{menu:id,slot:slot.slot});};
  }
  const recipes=dialog.querySelector('.recipes')!;recipes.replaceChildren();if(Array.isArray(data.recipes))for(const recipe of data.recipes.slice(0,256)){if(typeof recipe?.id!=='string'||typeof recipe?.name!=='string')continue;const button=document.createElement('button');button.textContent=recipe.name;button.onclick=()=>send('recipe',{menu:id,text:recipe.id});recipes.append(button);}
  dialog.querySelector('details')!.hidden=recipes.children.length===0;
  if(!dialog.open){visibility(true);dialog.showModal();}
 }
 function native(message:unknown){
  if(!dialog.open)return;const value=nativeMenuImage(message,id,revision);if(!value)return;
  const image=document.createElement('img');image.className='native-menu-image';image.alt='';image.src=value.image;
  grid.querySelector('.native-menu-image')?.remove();grid.prepend(image);grid.classList.add('native-menu');grid.style.aspectRatio=value.width+'/'+value.height;grid.style.setProperty('--menu-ratio',String(value.width/value.height));
  for(const slot of Array.from(grid.querySelectorAll<HTMLElement>('[data-menu-slot]'))){
   const entry=layoutSlots.find(item=>item.slot===Number(slot.dataset.menuSlot));if(!entry)continue;
   slot.style.cssText=`left:${entry.x/value.width*100}%;top:${entry.y/value.height*100}%;width:${16/value.width*100}%;height:${16/value.height*100}%`;
  }
 }
 return {receive,close,native};
}

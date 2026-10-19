import {normalizeMawSkills,normalizeMawStatus} from './maw-state';
import './maw.css';
export function mawUi(root:HTMLElement,send:(action:string,fields?:Record<string,unknown>)=>void,visibility:(open:boolean)=>void){
 const hud=document.createElement('aside');hud.id='maw-hud';hud.setAttribute('aria-label','MAWの盾チャージ');root.append(hud);
 const dialog=document.createElement('dialog');dialog.id='maw-skills';dialog.innerHTML='<div class="maw-heading"><h2>MAW 技の選択</h2><button aria-label="技の選択を閉じる">×</button></div><p class="maw-weapon"></p><p class="maw-error" role="status"></p><p>現在メインハンドに持っている武器の技を設定します。</p><div class="maw-choices"></div>';root.append(dialog);
 const pending=new Set<number>();
 function close(){pending.clear();if(dialog.open)dialog.close();visibility(false);}
 dialog.querySelector('button')!.onclick=close;dialog.addEventListener('cancel',event=>{event.preventDefault();close();});
 function status(value:unknown){
  const data=normalizeMawStatus(value);hud.replaceChildren();if(!data)return;
  const bar=(name:string,value:number,max:number,kind:string,threshold?:number)=>{
   const label=document.createElement('label');label.className='maw-meter '+kind;label.textContent=name;
   const track=document.createElement('span');track.className='maw-track';track.setAttribute('role','progressbar');track.setAttribute('aria-label',name);track.setAttribute('aria-valuemin','0');track.setAttribute('aria-valuemax',String(max));track.setAttribute('aria-valuenow',String(value));
   const fill=document.createElement('i');fill.style.width=(value/max*100)+'%';track.append(fill);
   if(threshold!==undefined){const mark=document.createElement('b');mark.style.left=(threshold*100)+'%';track.append(mark);}
   label.append(track);hud.append(label);
  };
  if(data.shieldCharge!==undefined)bar(`盾チャージ ${Math.round(data.shieldCharge*100)}%`,data.shieldCharge,1,'shield',data.shieldThreshold);
  if(data.text){const text=document.createElement('p');text.textContent=data.text;hud.append(text);}
 }
 function receive(value:unknown){
  const data=normalizeMawSkills(value);if(!data)return;pending.clear();
  dialog.querySelector('.maw-weapon')!.textContent=data.weapon;dialog.querySelector('.maw-error')!.textContent=data.error;
  const choices=dialog.querySelector('.maw-choices')!;choices.replaceChildren();
  for(const slot of data.slots){
   const label=document.createElement('label');label.textContent=slot.name;
   const select=document.createElement('select');select.setAttribute('aria-label',slot.name);
   for(const choice of slot.choices){const option=document.createElement('option');option.value=choice.id;option.textContent=choice.name;option.title=choice.description;option.selected=choice.id===slot.selected;select.append(option);}
   if(!slot.choices.some(choice=>choice.id===slot.selected)){const option=document.createElement('option');option.value=slot.selected;option.textContent='現在の設定';option.selected=true;option.disabled=true;select.prepend(option);}
   const description=document.createElement('small');description.textContent=slot.choices.find(choice=>choice.id===slot.selected)?.description??'';
   select.onchange=()=>{if(pending.has(slot.slot))return;pending.add(slot.slot);select.disabled=true;send('mawSelect',{slot:slot.slot,text:select.value});};
   label.append(select,description);choices.append(label);
  }
  if(!dialog.open){visibility(true);dialog.showModal();}
 }
 return {status,receive,reset(){status(undefined);close();}};
}

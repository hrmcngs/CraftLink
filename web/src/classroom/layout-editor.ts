import {controlIds,saveTouchSettings} from './touch-settings';
import type {TouchSettings,ControlId} from './touch-settings';

/** Edits the real HUD, while its gameplay listeners are disabled by the owner. */
export class LayoutEditor {
 private selected?:ControlId;
 private drag?:{pointer:number;element:HTMLElement;key:ControlId;offsetX:number;offsetY:number};
 private panel:HTMLElement;
 private controls=new Map<ControlId,HTMLElement>();
 active=false;
 constructor(private root:HTMLElement,private settings:()=>TouchSettings,private changed:()=>void,private finished:()=>void){
  for(const id of controlIds){const element=root.querySelector<HTMLElement>('[data-control="'+id+'"]');if(element)this.controls.set(id,element);}
  this.panel=document.createElement('aside');this.panel.id='touch-editor';this.panel.hidden=true;
  this.panel.innerHTML='<strong>タッチ操作のカスタマイズ</strong><p>オレンジの枠をドラッグして移動</p><label>選択 <select id="edit-control"></select></label><label>サイズ <input id="edit-size" type="range" min="60" max="180" value="100"><output id="edit-size-value"></output></label><label>不透明度 <input id="edit-opacity" type="range" min="10" max="100" value="80"><output id="edit-opacity-value"></output></label><button id="edit-reset">配置を初期化</button><button id="edit-done">保存して戻る</button>';
  root.append(this.panel);
  this.panel.querySelector<HTMLSelectElement>('#edit-control')!.onchange=e=>this.select((e.target as HTMLSelectElement).value as ControlId);
  for(const key of ['size','opacity'] as const)this.panel.querySelector<HTMLInputElement>('#edit-'+key)!.oninput=e=>{
   if(!this.selected)return;const placement=this.settings().placements[this.selected];if(!placement)return;
   placement[key]=Number((e.target as HTMLInputElement).value);this.changed();this.select(this.selected);
  };
  this.panel.querySelector('#edit-reset')!.addEventListener('click',()=>{this.settings().placements={};this.changed();this.capturePositions();this.select(this.selected??'jump');});
  this.panel.querySelector('#edit-done')!.addEventListener('click',()=>this.close());
  root.addEventListener('pointerdown',e=>this.down(e),true);
  root.addEventListener('pointermove',e=>this.move(e),true);
  for(const name of ['pointerup','pointercancel','lostpointercapture'])root.addEventListener(name,e=>{if(this.active&&this.drag?.pointer===(e as PointerEvent).pointerId){this.drag=undefined;e.stopPropagation();saveTouchSettings(this.settings());}},true);
 }
 apply(){
  for(const [id,element] of this.controls){
   const p=this.settings().placements[id];element.classList.toggle('custom-control',!!p);element.classList.toggle('editing-control',this.active);
   for(const property of ['left','top','right','bottom','position','transform','opacity','z-index'])element.style.removeProperty(property);
   if(!p)continue;
   const scale=Math.min(p.size/100,(innerWidth-12)/Math.max(1,element.offsetWidth),(innerHeight-80)/Math.max(1,element.offsetHeight));
   element.style.position='fixed';element.style.right='auto';element.style.bottom='auto';element.style.transform='translate(-50%,-50%) scale('+scale+')';element.style.zIndex='5';element.style.opacity=this.active?'1':String(this.settings().showButtons?p.opacity*this.settings().opacity/10000:0);
   // A saved center never places a control past the screen edges after rotation.
   const width=element.offsetWidth*scale,height=element.offsetHeight*scale;
   const x=Math.max(Math.min(width/2,innerWidth/2),Math.min(innerWidth-width/2,p.x*innerWidth));
   const y=Math.max(Math.min(height/2,innerHeight/2),Math.min(innerHeight-height/2,p.y*innerHeight));
   element.style.left=x+'px';element.style.top=y+'px';
  }
 }
 open(){
  this.active=true;this.root.dataset.editing='true';this.panel.hidden=false;
  // Freeze all visible controls together so moving a button cannot reflow its neighbours.
  this.capturePositions();const select=this.panel.querySelector<HTMLSelectElement>('#edit-control')!;select.replaceChildren();
  for(const [id,element] of this.controls)if(element.getBoundingClientRect().width>0&&getComputedStyle(element).display!=='none'){
   const option=document.createElement('option');option.value=id;option.textContent=element.getAttribute('aria-label')??element.textContent?.trim()??id;select.append(option);
  }
  this.select('jump');this.changed();
 }
 close(){if(!this.active)return;this.active=false;this.drag=undefined;this.root.dataset.editing='false';this.panel.hidden=true;saveTouchSettings(this.settings());this.changed();this.finished();}
 private capturePositions(){
  for(const [id,element] of this.controls){const box=element.getBoundingClientRect();if(box.width===0||box.height===0)continue;
   if(!this.settings().placements[id])this.settings().placements[id]={x:(box.x+box.width/2)/innerWidth,y:(box.y+box.height/2)/innerHeight,size:100,opacity:100};
  }
  this.apply();
 }
 private select(key:ControlId){
  this.selected=key;this.panel.querySelector<HTMLSelectElement>('#edit-control')!.value=key;
  const p=this.settings().placements[key];if(!p)return;
  for(const field of ['size','opacity'] as const){this.panel.querySelector<HTMLInputElement>('#edit-'+field)!.value=String(p[field]);this.panel.querySelector('#edit-'+field+'-value')!.textContent=p[field]+'%';}
  this.controls.forEach((element,id)=>element.classList.toggle('edit-selected',key===id));
 }
 private down(e:PointerEvent){
  if(!this.active||(e.target as HTMLElement).closest('#touch-editor'))return;
  const element=(e.target as HTMLElement).closest<HTMLElement>('[data-control]');if(!element)return;
  const key=element.dataset.control as ControlId;this.select(key);const box=element.getBoundingClientRect();
  this.drag={pointer:e.pointerId,element,key,offsetX:e.clientX-(box.x+box.width/2),offsetY:e.clientY-(box.y+box.height/2)};
  element.setPointerCapture(e.pointerId);e.preventDefault();e.stopImmediatePropagation();
 }
 private move(e:PointerEvent){
  if(!this.active||this.drag?.pointer!==e.pointerId)return;const p=this.settings().placements[this.drag.key];if(!p)return;
  p.x=Math.max(0,Math.min(1,(e.clientX-this.drag.offsetX)/innerWidth));p.y=Math.max(0,Math.min(1,(e.clientY-this.drag.offsetY)/innerHeight));this.apply();e.preventDefault();e.stopImmediatePropagation();
 }
}

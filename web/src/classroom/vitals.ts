export interface Vitals {damageSequence:number;onFire:boolean;soulFire:boolean;inWater:boolean;air:number;maxAir:number;frozen:number;freezeThreshold:number;survival:boolean;health:number;maxHealth:number;absorption:number;armor:number;food:number;level:number;progress:number;effects:{id:string;name:string;amplifier:number;duration:number;color:number}[]}
export function normalizeVitals(value:unknown):Vitals|undefined{
 if(!value||typeof value!=='object')return;
 const d=value as Record<string,unknown>;
 for(const key of ['health','maxHealth','absorption','armor','food','level','progress'])if(typeof d[key]!=='number'||!Number.isFinite(d[key]))return;
 if(typeof d.survival!=='boolean'||!Array.isArray(d.effects)||d.effects.length>128)return;
 for(const key of ['damageSequence','air','maxAir','frozen','freezeThreshold'])if(d[key]!==undefined&&(typeof d[key]!=='number'||!Number.isSafeInteger(d[key])))return;
 for(const key of ['onFire','inWater','soulFire'])if(d[key]!==undefined&&typeof d[key]!=='boolean')return;
 const effects=[];
 for(const effect of d.effects){
  if(!effect||typeof effect.id!=='string'||typeof effect.name!=='string'||effect.name.length>160||!Number.isInteger(effect.amplifier)||!Number.isInteger(effect.duration)||!Number.isInteger(effect.color))return;
  effects.push({id:effect.id,name:effect.name,amplifier:Math.max(0,Math.min(255,effect.amplifier)),duration:Math.max(-1,effect.duration),color:effect.color&0xffffff});
 }
 const maxHealth=Math.max(1,Math.min(1024,d.maxHealth as number));
 return {damageSequence:Math.max(0,(d.damageSequence as number)??0),onFire:d.onFire===true,soulFire:d.soulFire===true,inWater:d.inWater===true,air:Math.max(0,Math.min(100000,(d.air as number)??300)),maxAir:Math.max(1,Math.min(100000,(d.maxAir as number)??300)),frozen:Math.max(0,Math.min(100000,(d.frozen as number)??0)),freezeThreshold:Math.max(1,Math.min(100000,(d.freezeThreshold as number)??140)),survival:d.survival,health:Math.max(0,Math.min(maxHealth,d.health as number)),maxHealth,absorption:Math.max(0,Math.min(1024,d.absorption as number)),armor:Math.max(0,Math.min(20,d.armor as number)),food:Math.max(0,Math.min(20,d.food as number)),level:Math.max(0,Math.min(2147483647,Math.floor(d.level as number))),progress:Math.max(0,Math.min(1,d.progress as number)),effects};
}
const heart='M2 1h3v2h2V1h3v1h2v4h-2v2H8v2H6v2H4v-2H2V8H0V3h2z';
const food='M2 1h5v1h2v5H7v2H3V7H1V3h1z M7 8h2v2h2v2H9v-1H7z';
const shield='M1 1h10v6L6 12 1 7z';
function meter(element:HTMLElement,value:number,count:number,path:string,color:string){
 element.replaceChildren();element.style.setProperty('--meter-color',color);
 for(let i=0;i<count;i++){
  const icon=document.createElement('span');icon.className='meter-icon';icon.setAttribute('aria-hidden','true');
  icon.innerHTML=`<svg viewBox="0 0 12 13"><path d="${path}"/></svg><span class="meter-fill"><svg viewBox="0 0 12 13"><path d="${path}"/></svg></span>`;
  (icon.lastElementChild as HTMLElement).style.clipPath=`inset(0 ${100-Math.max(0,Math.min(1,value/2-i))*100}% 0 0)`;
  const sprite=(x:number,y:number)=>{const part=document.createElement('i');part.className='native-meter-sprite';part.style.backgroundPosition=`${x/247*100}% ${y/247*100}%`;return part;};
  const isHeart=path===heart,isArmor=path===shield;
  const row=isHeart?0:isArmor?9:27;
  const full=isHeart?(color==='#779d32'?88:color==='#63535d'?124:color==='#f8d64c'?160:52):isArmor?34:52;
  icon.append(sprite(16,row));
  const amount=Math.max(0,Math.min(1,value/2-i));
  if(amount>0)icon.append(sprite(amount<1?(isArmor?25:full+9):full,row));
  element.append(icon);
 }
}
export function receivedDamage(previous:Vitals|undefined,current:Vitals):boolean{
 return !!previous&&current.survival&&current.damageSequence>previous.damageSequence;
}
const snapshots=new WeakMap<HTMLElement,Vitals>();
const damageAnimations=new WeakMap<HTMLElement,Animation[]>();
export function resetVitals(root:HTMLElement){
 snapshots.delete(root);damageAnimations.get(root)?.forEach(animation=>animation.cancel());damageAnimations.delete(root);
 root.querySelector<HTMLElement>('#survival-bars')!.hidden=true;
 root.querySelector('#bot-effects')!.replaceChildren();
 root.querySelector('#environment-status')!.replaceChildren();
 root.querySelector<HTMLElement>('#environment-overlay')!.removeAttribute('data-fire');
 root.querySelector<HTMLElement>('#environment-overlay')!.removeAttribute('data-soul-fire');
 root.querySelector<HTMLElement>('#environment-overlay')!.style.setProperty('--frozen','0');
}
export function renderVitals(root:HTMLElement,value:unknown){
 const data=normalizeVitals(value);if(!data)return;
 const previous=snapshots.get(root);snapshots.set(root,data);
 if(receivedDamage(previous,data)){
  damageAnimations.get(root)?.forEach(animation=>animation.cancel());
  const animations=[root.querySelector<HTMLElement>('#damage-overlay')!.animate([{opacity:0},{opacity:.65,offset:.12},{opacity:0}],{duration:450})];
  if(!window.matchMedia('(prefers-reduced-motion: reduce)').matches)animations.push(root.querySelector<HTMLElement>('#bot-view')!.animate([{transform:'translate(0)'},{transform:'translate(-7px, 3px)',offset:.2},{transform:'translate(6px, -2px)',offset:.4},{transform:'translate(-3px, 1px)',offset:.65},{transform:'translate(0)'}],{duration:300}));
  damageAnimations.set(root,animations);
 }
 const environment=root.querySelector<HTMLElement>('#environment-overlay')!;
 environment.toggleAttribute('data-fire',data.survival&&data.onFire);
 environment.toggleAttribute('data-soul-fire',data.soulFire);
 environment.style.setProperty('--frozen',String(data.survival?Math.min(1,data.frozen/data.freezeThreshold):0));
 const status=root.querySelector<HTMLElement>('#environment-status')!;status.replaceChildren();
 const badge=(text:string,kind:string)=>{const element=document.createElement('div');element.className='environment-badge '+kind;element.textContent=text;status.append(element);};
 if(data.survival){
  if(data.onFire)badge(data.soulFire?'魂の炎で炎上中':'炎上中',data.soulFire?'soul-fire':'fire');
  if(data.inWater||data.air<data.maxAir)badge(`空気 ${Math.round(Math.min(1,data.air/data.maxAir)*100)}%`,data.air<=data.maxAir*.2?'air danger':'air');
  if(data.frozen>0)badge(`凍結 ${Math.round(Math.min(1,data.frozen/data.freezeThreshold)*100)}%`,'frozen');
 }
 const bars=root.querySelector<HTMLElement>('#survival-bars')!;bars.hidden=!data.survival;
 const poisoned=data.effects.some(e=>e.id==='minecraft:poison'),withered=data.effects.some(e=>e.id==='minecraft:wither');
 const health=root.querySelector<HTMLElement>('#health-meter')!;meter(health,data.health,Math.min(40,Math.ceil(data.maxHealth/2)),heart,poisoned?'#779d32':withered?'#63535d':'#ed3939');health.setAttribute('aria-label',`体力 ${data.health} / ${data.maxHealth}`);
 const absorption=root.querySelector<HTMLElement>('#absorption-meter')!;meter(absorption,data.absorption,Math.min(20,Math.ceil(data.absorption/2)),heart,'#f8d64c');absorption.hidden=data.absorption<=0;
 const armor=root.querySelector<HTMLElement>('#armor-meter')!;meter(armor,data.armor,10,shield,'#d6e3e8');armor.hidden=data.armor<=0;armor.setAttribute('aria-label',`防御力 ${data.armor} / 20`);
 const hunger=root.querySelector<HTMLElement>('#food-meter')!;meter(hunger,data.food,10,food,'#bb7842');hunger.setAttribute('aria-label',`満腹度 ${data.food} / 20`);
 root.querySelector<HTMLElement>('#experience-level')!.textContent=String(data.level);
 root.querySelector<HTMLElement>('#experience-fill')!.style.width=(data.progress*100)+'%';
 const xp=root.querySelector<HTMLElement>('#experience-bar')!;xp.setAttribute('aria-valuenow',String(Math.round(data.progress*100)));xp.setAttribute('aria-valuetext',`レベル ${data.level}`);
 const effects=root.querySelector<HTMLElement>('#bot-effects')!;effects.replaceChildren();
 for(const effect of data.effects){
  const badge=document.createElement('div');badge.className='effect-badge';badge.style.setProperty('--effect-color','#'+effect.color.toString(16).padStart(6,'0'));
  const name=document.createElement('span');name.textContent=effect.name+(effect.amplifier>0?' '+(effect.amplifier+1):'');
  const time=document.createElement('small');const seconds=Math.ceil(effect.duration/20);time.textContent=effect.duration<0?'∞':Math.floor(seconds/60)+':'+String(seconds%60).padStart(2,'0');badge.append(name,time);effects.append(badge);
 }
}
export const vitalsMarkup='<div id="environment-overlay" aria-hidden="true"></div><div id="damage-overlay" aria-hidden="true"></div><aside id="environment-status" aria-label="周囲の危険と残り空気"></aside><div id="bot-vitals"><div id="survival-bars" hidden><div id="armor-meter" class="meter" aria-label="防御力" hidden></div><div class="survival-row"><div class="heart-meters"><div id="health-meter" class="meter" aria-label="体力"></div><div id="absorption-meter" class="meter" hidden></div></div><div id="food-meter" class="meter" aria-label="満腹度"></div></div><span id="experience-level"></span><div id="experience-bar" role="progressbar" aria-label="経験値" aria-valuemin="0" aria-valuemax="100"><i id="experience-fill"></i></div></div></div><aside id="bot-effects" aria-label="現在のステータス効果"></aside>';

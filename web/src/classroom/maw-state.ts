export interface MawStatus {mana?:number;maxMana?:number;shieldCharge?:number;shieldThreshold?:number;text?:string}
export function normalizeMawStatus(value:unknown):MawStatus|undefined{
 if(!value||typeof value!=='object')return;
 const d=value as Record<string,unknown>,result:MawStatus={};
 if(d.mana!==undefined||d.maxMana!==undefined){
  if(typeof d.mana!=='number'||!Number.isFinite(d.mana)||typeof d.maxMana!=='number'||!Number.isFinite(d.maxMana)||d.maxMana<=0)return;
  result.maxMana=d.maxMana;result.mana=Math.max(0,Math.min(d.maxMana,d.mana));
 }
 if(d.shieldCharge!==undefined){
  if(typeof d.shieldCharge!=='number'||!Number.isFinite(d.shieldCharge)||typeof d.shieldThreshold!=='number'||!Number.isFinite(d.shieldThreshold))return;
  result.shieldCharge=Math.max(0,Math.min(1,d.shieldCharge));result.shieldThreshold=Math.max(0,Math.min(1,d.shieldThreshold));
 }
 if(d.text!==undefined){if(typeof d.text!=='string'||d.text.length>160)return;result.text=d.text;}
 return result;
}
export interface MawSkillSlot {slot:number;name:string;selected:string;choices:{id:string;name:string;description:string}[]}
export function normalizeMawSkills(value:unknown):{weapon:string;error:string;slots:MawSkillSlot[]}|undefined{
 if(!value||typeof value!=='object')return;
 const d=value as Record<string,unknown>;
 if(!Array.isArray(d.slots)||d.slots.length>32||typeof d.weapon!=='string'&&d.weapon!==undefined||typeof d.error!=='string'&&d.error!==undefined)return;
 const slots:MawSkillSlot[]=[],seen=new Set<number>();
 for(const slot of d.slots){
  if(!slot||!Number.isInteger(slot.slot)||slot.slot<0||slot.slot>255||seen.has(slot.slot)||typeof slot.name!=='string'||typeof slot.selected!=='string'||!Array.isArray(slot.choices)||slot.choices.length>256)return;
  seen.add(slot.slot);
  if(slot.choices.some((choice:any)=>!choice||typeof choice.id!=='string'||choice.id.length>256||typeof choice.name!=='string'||typeof choice.description!=='string'))return;
  slots.push(slot);
 }
 return {weapon:typeof d.weapon==='string'?d.weapon:'',error:typeof d.error==='string'?d.error:'',slots};
}

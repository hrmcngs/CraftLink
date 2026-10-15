export const controlIds=['dpad','joystick','jump','sneak','sprint','attack','use','drop','hotbar'] as const;
export type ControlId=typeof controlIds[number];
export interface Placement {x:number;y:number;size:number;opacity:number}
export interface TouchSettings {
 mode:'joystick_tap'|'dpad_tap'|'joystick_crosshair';sensitivity:number;invertY:boolean;size:number;opacity:number;
 autoJump:boolean;sneakToggle:boolean;doubleForward:boolean;doubleJumpSneak:boolean;splitActions:boolean;showActions:boolean;showButtons:boolean;showDrop:boolean;
 holdToDrop:number;dropStack:boolean;placements:Partial<Record<ControlId,Placement>>;
}
export const touchDefaults:TouchSettings={mode:'joystick_tap',sensitivity:.35,invertY:false,size:100,opacity:80,autoJump:false,sneakToggle:true,doubleForward:true,doubleJumpSneak:false,splitActions:false,showActions:false,showButtons:true,showDrop:true,holdToDrop:2000,dropStack:false,placements:{}};
const finite=(v:unknown,min:number,max:number,fallback:number)=>typeof v==='number'&&Number.isFinite(v)&&v>=min&&v<=max?v:fallback;
export function normalizeTouchSettings(value:unknown):TouchSettings {
 const data=(typeof value==='object'&&value!==null?value:{}) as Record<string,unknown>;
 const result:TouchSettings={...touchDefaults,placements:{}};
 if(data.mode==='dpad_tap'||data.mode==='joystick_tap'||data.mode==='joystick_crosshair')result.mode=data.mode;
 result.sensitivity=finite(data.sensitivity,.05,1,.35);result.size=finite(data.size,70,160,100);result.opacity=finite(data.opacity,10,100,80);result.holdToDrop=finite(data.holdToDrop,500,3000,2000);
 for(const key of ['invertY','autoJump','sneakToggle','doubleForward','doubleJumpSneak','splitActions','showActions','showButtons','showDrop','dropStack'] as const)if(typeof data[key]==='boolean')result[key]=data[key];
 if(typeof data.placements==='object'&&data.placements!==null)for(const key of controlIds){
  const p=(data.placements as Record<string,unknown>)[key];if(typeof p!=='object'||p===null)continue;const item=p as Record<string,unknown>;
  if(typeof item.x!=='number'||typeof item.y!=='number'||!Number.isFinite(item.x)||!Number.isFinite(item.y)||item.x<0||item.x>1||item.y<0||item.y>1)continue;
  result.placements[key]={x:item.x,y:item.y,size:finite(item.size,60,180,100),opacity:finite(item.opacity,10,100,100)};
 }
 return result;
}
export class DoubleTap {
 private last=-Infinity;
 tap(now:number){const elapsed=now-this.last;this.last=now;const doubled=elapsed>=40&&elapsed<=300;if(doubled)this.last=-Infinity;return doubled;}
 reset(){this.last=-Infinity;}
}
export function loadTouchSettings():TouchSettings {try{return normalizeTouchSettings(JSON.parse(localStorage.getItem('craftlink.botTouch')??'null'));}catch{return normalizeTouchSettings(null);}}
export function saveTouchSettings(value:TouchSettings){try{localStorage.setItem('craftlink.botTouch',JSON.stringify(normalizeTouchSettings(value)));}catch{}}

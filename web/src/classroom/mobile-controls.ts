import type {TouchSettings} from './touch-settings';
export function stickVector(x:number,y:number){
 if(!Number.isFinite(x)||!Number.isFinite(y))return {x:0,z:0};
 const distance=Math.hypot(x,y);if(distance<=.15)return {x:0,z:0};
 const strength=Math.min(1,(distance-.15)/.85);
 return {x:x/distance*strength,z:-y/distance*strength};
}
export function mobilePreset(mode:TouchSettings['mode']):Partial<TouchSettings>{
 return {mode,autoJump:mode!=='joystick_crosshair',sneakToggle:true,doubleForward:true,doubleJumpSneak:false,showActions:mode==='joystick_crosshair',showButtons:true,showDrop:false,splitActions:false,placements:{}};
}

export function holdQuantity(total:number,elapsed:number){return Math.min(Math.max(1,total),1+Math.floor(Math.max(0,elapsed-350)/45));}
export function strongForward(movement:{x:number;z:number}){return movement.z>=.85&&Math.abs(movement.x)<.5;}

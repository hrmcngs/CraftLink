import { joystickDirections } from './joystick';
import { WorldGesture } from './gesture';
import type { VideoView } from '../video/view';
export interface TouchCommand {phase:string;frame:number;x:number;y:number}
export class TouchController {
  private presses=new Map<number,string>();
  private look?:{id:number;x:number;y:number;kind:'look'|'world'|'gui';gesture?:WorldGesture};
  private holdTimer?:number;
  private lastTouchFrame=-1;
  private lastTouchX=0;private lastTouchY=0;
  private hotbarTimers=new Map<number,{timer:number;x:number;y:number}>();
  private latches=new Set<string>();
  private stick?:number;
  private stickHeld:string[]=[];
  enabled=true;
  dx=0;dy=0;slot=-1;
  sensitivity=.15;
  constructor(private root:HTMLElement,private changed:()=>void=()=>{},private video?:VideoView,private sendTouch:(event:TouchCommand)=>void=()=>{}) {
    root.querySelectorAll<HTMLElement>('[data-key]').forEach(button=>{
      button.addEventListener('pointerdown',e=>{
        if(!this.enabled)return;
        e.preventDefault();button.setPointerCapture(e.pointerId);
        const key=button.dataset.key!;
        if(this.toggleAction(key)&&![...this.presses.values()].includes(key)){if(this.latches.has(key))this.latches.delete(key);else this.latches.add(key);}
        this.presses.set(e.pointerId,key);this.feedback();this.changed();
      });
      const up=(e:PointerEvent)=>{if(!this.presses.delete(e.pointerId))return;this.feedback();this.changed();};
      button.addEventListener('pointerup',up);button.addEventListener('pointercancel',up);button.addEventListener('lostpointercapture',up);
    });
    root.querySelectorAll<HTMLElement>('[data-slot]').forEach(button=>{
      button.addEventListener('pointerdown',e=>{
        if(!this.enabled)return;
        e.preventDefault();button.setPointerCapture(e.pointerId);this.slot=Number(button.dataset.slot);
        root.querySelectorAll('[data-slot]').forEach(b=>b.classList.remove('selected'));button.classList.add('selected');this.changed();
        const timer=window.setTimeout(()=>{this.presses.set(e.pointerId,'drop');this.changed();},750);
        this.hotbarTimers.set(e.pointerId,{timer,x:e.clientX,y:e.clientY});
      });
      const end=(e:PointerEvent)=>{const entry=this.hotbarTimers.get(e.pointerId);if(entry)clearTimeout(entry.timer);this.hotbarTimers.delete(e.pointerId);if(this.presses.delete(e.pointerId))this.changed();};
      button.addEventListener('pointerup',end);button.addEventListener('pointercancel',end);button.addEventListener('lostpointercapture',end);
      button.addEventListener('pointermove',e=>{const entry=this.hotbarTimers.get(e.pointerId);if(entry&&Math.hypot(e.clientX-entry.x,e.clientY-entry.y)>8)end(e);});
    });
    const area=root.querySelector<HTMLElement>('#look')!;
    area.addEventListener('pointerdown',e=>{
      if(!this.enabled||this.look)return;
      const tapMode=root.dataset.layout==='tap'||root.dataset.layout==='classic_tap';
      const kind=this.video?.context()===1?'gui':tapMode?'world':'look';
      if(kind!=='look'&&!this.video?.point(e.clientX,e.clientY))return;
      e.preventDefault();area.setPointerCapture(e.pointerId);
      const look=this.look={id:e.pointerId,x:e.clientX,y:e.clientY,kind,gesture:kind==='world'?new WorldGesture(e.clientX,e.clientY,performance.now()):undefined};
      if(kind==='gui')this.emitTouch('down',look.x,look.y);
      if(kind==='world')this.holdTimer=window.setTimeout(()=>{if(this.look===look&&look.gesture?.advance(performance.now())==='hold')this.emitTouch('hold',look.x,look.y);},350);
    });
    area.addEventListener('pointermove',e=>{
      const look=this.look;if(look?.id!==e.pointerId)return;
      const dx=e.clientX-look.x,dy=e.clientY-look.y;look.x=e.clientX;look.y=e.clientY;
      if(look.kind==='look'){this.dx+=dx*this.sensitivity;this.dy+=dy*this.sensitivity;}
      if(look.kind==='world'){
        const move=look.gesture!.move(look.x,look.y);if(move.release)this.emitTouch('up',look.x,look.y);
        this.dx+=move.dx*this.sensitivity;this.dy+=move.dy*this.sensitivity;
      }
    });
    const end=(e:PointerEvent)=>{
      const look=this.look;if(look?.id!==e.pointerId)return;clearTimeout(this.holdTimer);
      if(look.kind==='gui')this.emitTouch(e.type==='pointerup'?'up':'cancel',look.x,look.y);
      if(look.kind==='world'){const phase=look.gesture!.end(e.type!=='pointerup');if(phase)this.emitTouch(phase,look.x,look.y);}
      this.look=undefined;
    };
    area.addEventListener('pointerup',end);area.addEventListener('pointercancel',end);area.addEventListener('lostpointercapture',end);
    const joystick=root.querySelector<HTMLElement>('#joystick')!;
    const move=(e:PointerEvent)=>{
      if(this.stick!==e.pointerId)return;
      const bounds=joystick.getBoundingClientRect(),radius=bounds.width/2;
      let x=(e.clientX-bounds.left-radius)/radius,y=(e.clientY-bounds.top-radius)/radius;
      const length=Math.max(1,Math.hypot(x,y));x/=length;y/=length;
      this.stickHeld=joystickDirections(x,y);
      root.querySelector<HTMLElement>('#stick-knob')!.style.transform=`translate(${x*radius*.55}px, ${y*radius*.55}px)`;
    };
    joystick.addEventListener('pointerdown',e=>{if(!this.enabled||this.stick!==undefined)return;e.preventDefault();this.stick=e.pointerId;joystick.setPointerCapture(e.pointerId);move(e);this.changed();});
    joystick.addEventListener('pointermove',e=>{if(this.stick!==e.pointerId)return;const previous=this.stickHeld.join(',');move(e);if(this.stickHeld.join(',')!==previous)this.changed();});
    const stop=(e:PointerEvent)=>{if(this.stick!==e.pointerId)return;this.stick=undefined;this.stickHeld=[];root.querySelector<HTMLElement>('#stick-knob')!.style.transform='';this.changed();};
    joystick.addEventListener('pointerup',stop);joystick.addEventListener('pointercancel',stop);joystick.addEventListener('lostpointercapture',stop);
  }
  private toggleAction(key:string){return this.root.dataset.layout!=='classic'&&(key==='sneak'||key==='sprint');}
  private feedback(){
    this.root.querySelectorAll<HTMLElement>('[data-key]').forEach(button=>{
      const key=button.dataset.key!,active=this.toggleAction(key)?this.latches.has(key):[...this.presses.values()].includes(key);
      button.classList.toggle('pressed',active);if(this.toggleAction(key))button.setAttribute('aria-pressed',String(active));
    });
  }
  private emitTouch(phase:string,x:number,y:number){
    const point=this.video?.point(x,y);if(!point&&phase!=='up'&&phase!=='cancel')return;
    this.sendTouch({phase,...(point??{frame:0,x:0,y:0})});
    if(point){this.lastTouchFrame=point.frame;this.lastTouchX=x;this.lastTouchY=y;}
  }
  pump(){
    const look=this.look;if(!look||look.kind==='look')return;
    if(!this.video?.fresh()){this.emitTouch('cancel',look.x,look.y);clearTimeout(this.holdTimer);this.look=undefined;return;}
    if(look.kind==='gui'||look.gesture?.state==='mining'){
      const point=this.video.point(look.x,look.y);
      if(point&&(point.frame!==this.lastTouchFrame||look.x!==this.lastTouchX||look.y!==this.lastTouchY))this.emitTouch('move',look.x,look.y);
    }
  }
  tapAction(action:string){this.presses.set(-1,action);this.changed();this.presses.delete(-1);this.changed();}
  hasActiveInput(){return this.presses.size>0||this.latches.size>0||this.hotbarTimers.size>0||this.stick!==undefined||this.look!==undefined;}
  reset(){
    if(this.look&&this.look.kind!=='look')this.emitTouch('cancel',this.look.x,this.look.y);
    clearTimeout(this.holdTimer);for(const entry of this.hotbarTimers.values())clearTimeout(entry.timer);this.hotbarTimers.clear();this.latches.clear();
    this.presses.clear();this.look=undefined;this.stick=undefined;this.stickHeld=[];this.dx=this.dy=0;this.slot=-1;this.feedback();
    this.root.querySelector<HTMLElement>('#stick-knob')!.style.transform='';
  }
  packet(){
    const packet={type:'input',held:[...new Set([...this.presses.values()].filter(key=>!this.toggleAction(key)).concat([...this.latches],this.stickHeld))],dx:Math.max(-180,Math.min(180,this.dx)),dy:Math.max(-180,Math.min(180,this.dy)),slot:this.slot};
    this.dx=this.dy=0;this.slot=-1;return packet;
  }
}

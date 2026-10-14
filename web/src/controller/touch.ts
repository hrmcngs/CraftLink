import { joystickDirections } from './joystick';
export class TouchController {
  private presses = new Map<number, string>();
  private look?: {id: number; x: number; y: number};
  private stick?: number;
  private stickHeld: string[] = [];
  enabled = true;
  dx = 0; dy = 0; slot = -1;
  sensitivity = 0.15;
  constructor(private root: HTMLElement, private changed: () => void = () => {}) {
    root.querySelectorAll<HTMLElement>('[data-key]').forEach(button => {
      button.addEventListener('pointerdown', e => {
        if (!this.enabled) return;
        e.preventDefault(); button.setPointerCapture(e.pointerId); this.presses.set(e.pointerId, button.dataset.key!); button.classList.add('pressed'); this.changed();
      });
      const up = (e: PointerEvent) => {
        if (!this.presses.delete(e.pointerId)) return;
        if (![...this.presses.values()].includes(button.dataset.key!)) button.classList.remove('pressed');
        this.changed();
      };
      button.addEventListener('pointerup', up); button.addEventListener('pointercancel', up); button.addEventListener('lostpointercapture', up);
    });
    root.querySelectorAll<HTMLElement>('[data-slot]').forEach(button => button.addEventListener('pointerdown', e => {
      if (!this.enabled) return;
      e.preventDefault(); this.slot = Number(button.dataset.slot);
      root.querySelectorAll('[data-slot]').forEach(b => b.classList.remove('selected')); button.classList.add('selected'); this.changed();
    }));
    const area = root.querySelector<HTMLElement>('#look')!;
    area.addEventListener('pointerdown', e => { if (!this.enabled || this.look) return; e.preventDefault(); area.setPointerCapture(e.pointerId); this.look = {id:e.pointerId,x:e.clientX,y:e.clientY}; });
    area.addEventListener('pointermove', e => { if (this.look?.id !== e.pointerId) return; this.dx += (e.clientX - this.look.x) * this.sensitivity; this.dy += (e.clientY - this.look.y) * this.sensitivity; this.look.x=e.clientX; this.look.y=e.clientY; });
    const end = (e: PointerEvent) => { if (this.look?.id === e.pointerId) this.look = undefined; };
    area.addEventListener('pointerup', end); area.addEventListener('pointercancel', end); area.addEventListener('lostpointercapture', end);
    const joystick = root.querySelector<HTMLElement>('#joystick')!;
    const move = (e: PointerEvent) => {
      if (this.stick !== e.pointerId) return;
      const bounds = joystick.getBoundingClientRect(), radius = bounds.width / 2;
      let x = (e.clientX - bounds.left - radius) / radius, y = (e.clientY - bounds.top - radius) / radius;
      const length = Math.max(1, Math.hypot(x, y)); x /= length; y /= length;
      this.stickHeld = joystickDirections(x, y);
      root.querySelector<HTMLElement>('#stick-knob')!.style.transform = `translate(${x * radius * 0.55}px, ${y * radius * 0.55}px)`;
    };
    joystick.addEventListener('pointerdown', e => {
      if (!this.enabled || this.stick !== undefined) return;
      e.preventDefault(); this.stick=e.pointerId; joystick.setPointerCapture(e.pointerId); move(e); this.changed();
    });
    joystick.addEventListener('pointermove', e => {
      // Visual tracking can be fast; state is sent on the regular 30Hz timer.
      if (this.stick !== e.pointerId) return;
      const previous = this.stickHeld.join(',');
      move(e);
      if (this.stickHeld.join(',') !== previous) this.changed();
    });
    const stop = (e: PointerEvent) => { if (this.stick !== e.pointerId) return; this.stick=undefined; this.stickHeld=[]; root.querySelector<HTMLElement>('#stick-knob')!.style.transform=''; this.changed(); };
    joystick.addEventListener('pointerup', stop); joystick.addEventListener('pointercancel', stop); joystick.addEventListener('lostpointercapture', stop);
  }
  hasActiveInput() { return this.presses.size > 0 || this.stick !== undefined || this.look !== undefined; }
  reset() {
    this.presses.clear(); this.look = undefined; this.stick=undefined; this.stickHeld=[]; this.dx = this.dy = 0; this.slot = -1;
    this.root.querySelectorAll('.pressed').forEach(b => b.classList.remove('pressed'));
    this.root.querySelector<HTMLElement>('#stick-knob')!.style.transform='';
  }
  packet() {
    const packet = {type:'input', held:[...new Set([...this.presses.values(), ...this.stickHeld])], dx:Math.max(-180,Math.min(180,this.dx)), dy:Math.max(-180,Math.min(180,this.dy)), slot:this.slot};
    this.dx=this.dy=0; this.slot=-1; return packet;
  }
}

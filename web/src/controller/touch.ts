export class TouchController {
  private presses = new Map<number, string>();
  private look?: {id: number; x: number; y: number};
  dx = 0; dy = 0; slot = -1;
  sensitivity = Number(localStorage.getItem('sensitivity') ?? 0.15);
  constructor(root: HTMLElement, private changed: () => void = () => {}) {
    root.querySelectorAll<HTMLElement>('[data-key]').forEach(button => {
      button.addEventListener('pointerdown', e => { e.preventDefault(); button.setPointerCapture(e.pointerId); this.presses.set(e.pointerId, button.dataset.key!); button.classList.add('pressed'); this.changed(); });
      const up = (e: PointerEvent) => { this.presses.delete(e.pointerId); button.classList.remove('pressed'); this.changed(); };
      button.addEventListener('pointerup', up); button.addEventListener('pointercancel', up); button.addEventListener('lostpointercapture', up);
    });
    root.querySelectorAll<HTMLElement>('[data-slot]').forEach(button => button.addEventListener('pointerdown', e => {
      e.preventDefault(); this.slot = Number(button.dataset.slot);
      root.querySelectorAll('[data-slot]').forEach(b => b.classList.remove('selected')); button.classList.add('selected'); this.changed();
    }));
    const area = root.querySelector<HTMLElement>('#look')!;
    area.addEventListener('pointerdown', e => { if (this.look) return; e.preventDefault(); area.setPointerCapture(e.pointerId); this.look = {id:e.pointerId,x:e.clientX,y:e.clientY}; });
    area.addEventListener('pointermove', e => { if (this.look?.id !== e.pointerId) return; this.dx += (e.clientX - this.look.x) * this.sensitivity; this.dy += (e.clientY - this.look.y) * this.sensitivity; this.look.x=e.clientX; this.look.y=e.clientY; });
    const end = (e: PointerEvent) => { if (this.look?.id === e.pointerId) this.look = undefined; };
    area.addEventListener('pointerup', end); area.addEventListener('pointercancel', end); area.addEventListener('lostpointercapture', end);
  }
  reset() { this.presses.clear(); this.look = undefined; this.dx = this.dy = 0; this.slot = -1; document.querySelectorAll('.pressed').forEach(b => b.classList.remove('pressed')); }
  packet() {
    const packet = {type:'input', held:[...new Set(this.presses.values())], dx:Math.max(-180,Math.min(180,this.dx)), dy:Math.max(-180,Math.min(180,this.dy)), slot:this.slot};
    this.dx=this.dy=0; this.slot=-1; return packet;
  }
}

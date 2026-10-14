/** Distinguish tap/use, hold/mine and drag/look without depending on DOM timing. */
export class WorldGesture {
  readonly startX:number; readonly startY:number; readonly startedAt:number;
  private lastX:number;private lastY:number;
  state:'pending'|'look'|'mining'|'ended'='pending';
  constructor(x:number,y:number,now:number) {this.startX=this.lastX=x;this.startY=this.lastY=y;this.startedAt=now;}
  advance(now:number) { if(this.state==='pending' && now-this.startedAt>=350){this.state='mining';return 'hold';}return undefined; }
  move(x:number,y:number) {
    const dx=x-this.lastX,dy=y-this.lastY;this.lastX=x;this.lastY=y;
    let release=false;
    if(this.state!=='ended' && Math.hypot(x-this.startX,y-this.startY)>8){release=this.state==='mining';this.state='look';}
    return {dx:this.state==='look'?dx:0,dy:this.state==='look'?dy:0,release};
  }
  end(cancelled=false):'tap'|'up'|undefined {
    const result=this.state==='mining'?'up':!cancelled && this.state==='pending'?'tap':undefined;
    this.state='ended';return result;
  }
}

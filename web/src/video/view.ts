import {HEADER_BYTES, parseFrameHeader, videoPoint} from './protocol';
import type {FrameHeader} from './protocol';
export class VideoView {
  private frame?: FrameHeader;
  private receivedAt=0;
  private generation=0;
  private enabled=true;
  constructor(private canvas: HTMLCanvasElement, private root: HTMLElement, private contextChanged:()=>void=()=>{}) { root.dataset.streaming='true'; }
  setEnabled(enabled:boolean) { this.enabled=enabled; this.root.dataset.streaming=String(enabled); this.clear(); }
  fresh() { return this.enabled && this.frame !== undefined && performance.now()-this.receivedAt<1500; }
  context() { return this.fresh() ? this.frame!.context : 2; }
  point(x:number,y:number) {
    if (!this.fresh()) return;
    const position=videoPoint(x,y,this.canvas.getBoundingClientRect(),this.frame!.width,this.frame!.height);
    return position ? {...position,frame:this.frame!.id} : undefined;
  }
  async receive(blob:Blob):Promise<number|undefined> {
    if (!this.enabled) return;
    const generation=this.generation;
    const bytes=await blob.arrayBuffer(), header=parseFrameHeader(bytes);
    const url=URL.createObjectURL(new Blob([bytes.slice(HEADER_BYTES)],{type:'image/jpeg'}));
    try {
      const image=new Image();
      await new Promise<void>((resolve,reject)=>{image.onload=()=>resolve();image.onerror=()=>reject(new Error('JPEG decode failed'));image.src=url;});
      if (generation!==this.generation || !this.enabled) return;
      // Parallel browser decodes must never replace a newer frame with an older one.
      if (this.frame && header.id<this.frame.id && !(header.id===1 && this.frame.id===2147483647)) return header.id;
      if (image.naturalWidth!==header.width || image.naturalHeight!==header.height) throw new Error('Video dimensions mismatch');
      if(this.canvas.width!==header.width || this.canvas.height!==header.height) {this.canvas.width=header.width;this.canvas.height=header.height;}
      this.canvas.getContext('2d')!.drawImage(image,0,0);
      if(this.frame && this.frame.context!==header.context)this.contextChanged();
      this.frame=header;this.receivedAt=performance.now();this.root.dataset.context=header.context===1?'gui':'world';
      this.refresh();return header.id;
    } finally { URL.revokeObjectURL(url); }
  }
  refresh() {
    const fresh=this.fresh(), state=fresh?'live':'waiting';
    if(this.root.dataset.video!==state)this.root.dataset.video=state;
    const status=this.root.querySelector<HTMLElement>('#video-state');
    const message=!this.enabled?'映像転送はオフです':!fresh?'Minecraftの映像を待っています':this.frame?.context===2?'直接タップ操作は一人称視点で使用してください':'';
    if(status && status.textContent!==message) status.textContent=message;
  }
  clear() {this.generation++;this.frame=undefined;this.canvas.getContext('2d')?.clearRect(0,0,this.canvas.width,this.canvas.height);this.root.dataset.context='world';this.refresh();}
}

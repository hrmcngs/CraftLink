export interface FrameHeader { id: number; width: number; height: number; context: 0 | 1 | 2 }
export const HEADER_BYTES = 20;
export const MAX_FRAME_BYTES = 512020;
export function parseFrameHeader(buffer: ArrayBuffer): FrameHeader {
  if (buffer.byteLength <= HEADER_BYTES || buffer.byteLength > MAX_FRAME_BYTES) throw new Error('Invalid video frame size');
  const view=new DataView(buffer);
  const id=view.getUint32(4), width=view.getUint32(8), height=view.getUint32(12), context=view.getUint32(16);
  if (view.getUint32(0)!==0x434c5631 || id<1 || id>0x7fffffff || width<1 || width>1920 || height<1 || height>1080 || context>2) throw new Error('Invalid video header');
  return {id,width,height,context:context as 0|1|2};
}
export function videoPoint(clientX: number, clientY: number, bounds: {left:number;top:number;width:number;height:number}, width:number, height:number): {x:number;y:number} | undefined {
  if (bounds.width<=0 || bounds.height<=0 || width<=0 || height<=0) return;
  const scale=Math.min(bounds.width/width,bounds.height/height);
  const drawnWidth=width*scale, drawnHeight=height*scale;
  const x=(clientX-bounds.left-(bounds.width-drawnWidth)/2)/drawnWidth;
  const y=(clientY-bounds.top-(bounds.height-drawnHeight)/2)/drawnHeight;
  if (!Number.isFinite(x) || !Number.isFinite(y) || x<0 || x>1 || y<0 || y>1) return;
  return {x,y};
}

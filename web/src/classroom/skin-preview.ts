/** A front view of the installed skin, with the same outer layers as the BOT. */
export function drawSkin(canvas:HTMLCanvasElement,source:string|undefined){
 canvas.dataset.source=source||'';
 const ctx=canvas.getContext('2d');if(!ctx)return;
 ctx.imageSmoothingEnabled=false;ctx.clearRect(0,0,canvas.width,canvas.height);
 if(!source)return;
 const image=new Image();image.onload=()=>{
  if(canvas.dataset.source!==source)return;
  ctx.clearRect(0,0,canvas.width,canvas.height);
  const scale=image.width/64;
  const part=(sx:number,sy:number,w:number,h:number,x:number,y:number)=>ctx.drawImage(image,sx*scale,sy*scale,w*scale,h*scale,x*4,y*4,w*4,h*4);
  part(8,8,8,8,4,0);part(20,20,8,12,4,8);
  part(44,20,4,12,0,8);part(36,52,4,12,12,8);
  part(4,20,4,12,4,20);part(20,52,4,12,8,20);
  part(40,8,8,8,4,0);part(20,36,8,12,4,8);
  part(44,36,4,12,0,8);part(52,52,4,12,12,8);
  part(4,36,4,12,4,20);part(4,52,4,12,8,20);
 };image.src=source;
}

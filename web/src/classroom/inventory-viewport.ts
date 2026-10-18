/** Explicit row sizing prevents overflowing grids from compressing their implicit rows in WebKit. */
export function observeInventoryLayout(dialog:HTMLDialogElement){
 let pending=0;
 function refresh(){
  const viewport=window.visualViewport;
  const style=document.documentElement.style;
  style.setProperty('--view-width',(viewport?.width??innerWidth)+'px');
  style.setProperty('--view-height',(viewport?.height??innerHeight)+'px');
  style.setProperty('--view-top',(viewport?.offsetTop??0)+'px');
  style.setProperty('--view-left',(viewport?.offsetLeft??0)+'px');
  for(const selector of ['#creative-items','#bot-inventory-grid','#inventory-hotbar']){
   const grid=dialog.querySelector<HTMLElement>(selector)!;
   const parent=grid.parentElement!;const computed=getComputedStyle(parent);
   const width=parent.clientWidth-parseFloat(computed.paddingLeft)-parseFloat(computed.paddingRight);if(!width)continue;
   let size=Math.max(18,Math.floor((width-8)/9));
   if(selector!=='#creative-items'){
    const body=dialog.querySelector<HTMLElement>('.inventory-workspace')!.clientHeight;
    const accessories=dialog.querySelector<HTMLElement>('#inventory-accessories')!;
    const player=dialog.querySelector<HTMLElement>('#inventory-player')!;
    const playerStyle=getComputedStyle(player);
    const available=player.clientWidth-parseFloat(playerStyle.paddingLeft)-parseFloat(playerStyle.paddingRight);
    size=Math.max(18,Math.floor((available-(accessories.hidden?8:29))/(accessories.hidden?9:11)));
    const rows=Math.max(8,accessories.hidden?0:Math.ceil(accessories.children.length/2));
    const extra=52+(dialog.querySelector<HTMLButtonElement>('#inventory-open-backpack')!.hidden?0:32);
    size=Math.min(size,Math.max(18,Math.floor((body-extra)/rows)));
    dialog.querySelector<HTMLElement>('#inventory-player')!.dataset.overflow=String(rows*size+extra>body);
   }
   grid.style.setProperty('--slot-size',size+'px');
   if(selector==='#bot-inventory-grid')dialog.querySelector<HTMLElement>('#inventory-player')!.style.setProperty('--slot-size',size+'px');
  }
 }
 function schedule(){cancelAnimationFrame(pending);pending=requestAnimationFrame(refresh);}
 const observer=new ResizeObserver(schedule);
 for(const selector of ['.inventory-workspace','#creative-items','#bot-inventory-grid'])observer.observe(dialog.querySelector(selector)!);
 window.visualViewport?.addEventListener('resize',schedule);
 window.visualViewport?.addEventListener('scroll',schedule);
 window.addEventListener('resize',schedule);
 refresh();return {refresh};
}

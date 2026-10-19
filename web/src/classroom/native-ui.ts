export function nativeHudImage(value:unknown):string|undefined{
 return typeof value==='string'&&value.length<400000&&/^data:image\/png;base64,[A-Za-z0-9+/=]+$/.test(value)?value:undefined;
}
export function nativeMenuImage(value:unknown,id:number,revision:number):{width:number;height:number;image:string}|undefined{
 if(!value||typeof value!=='object')return;
 const d=value as Record<string,unknown>;
 if(d.id!==id||d.revision!==revision||!Number.isInteger(d.width)||!Number.isInteger(d.height)||(d.width as number)<16||(d.height as number)<16||(d.width as number)>512||(d.height as number)>512||typeof d.image!=='string'||d.image.length>1500000||!/^data:image\/png;base64,[A-Za-z0-9+/=]+$/.test(d.image))return;
 return {width:d.width as number,height:d.height as number,image:d.image};
}

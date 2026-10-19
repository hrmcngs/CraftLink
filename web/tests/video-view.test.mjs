import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import ts from 'typescript';
const moduleUrl=source=>'data:text/javascript;base64,'+Buffer.from(ts.transpile(source,{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.ESNext})).toString('base64');
const protocol=moduleUrl(readFileSync(new URL('../src/video/protocol.ts',import.meta.url),'utf8'));
const {VideoView}=await import(moduleUrl(readFileSync(new URL('../src/video/view.ts',import.meta.url),'utf8').replace("from './protocol'",'from '+JSON.stringify(protocol))));
function frame(id){const bytes=new ArrayBuffer(21),view=new DataView(bytes);[0x434c5631,id,16,9,0].forEach((n,i)=>view.setUint32(i*4,n));return new Blob([bytes]);}
test('slow JPEG decoding skips overlapping frames and discards frames cleared during decoding',async()=>{
 const original=globalThis.Image;const images=[];globalThis.Image=class{naturalWidth=16;naturalHeight=9;constructor(){images.push(this);}set src(value){}};
 try{
  let draws=0,contexts=0;const canvas={width:16,height:9,getContext(){contexts++;return {drawImage(){draws++;},clearRect(){}};}};
  const video=new VideoView(canvas,{dataset:{},querySelector(){return null;}});
  const first=video.receive(frame(1));await new Promise(resolve=>setImmediate(resolve));
  assert.equal(images.length,1);assert.equal(await video.receive(frame(2)),2);assert.equal(images.length,1);
  video.clear();images[0].onload();assert.equal(await first,undefined);assert.equal(draws,0);
  const next=video.receive(frame(3));await new Promise(resolve=>setImmediate(resolve));images[1].onload();assert.equal(await next,3);assert.equal(draws,1);assert.equal(contexts,1);
 }finally{globalThis.Image=original;}
});

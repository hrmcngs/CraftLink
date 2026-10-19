import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import ts from 'typescript';
const moduleUrl=source=>'data:text/javascript;base64,'+Buffer.from(ts.transpile(source,{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.ESNext})).toString('base64');
const protocol=moduleUrl(readFileSync(new URL('../src/video/protocol.ts',import.meta.url),'utf8'));
const {VideoView}=await import(moduleUrl(readFileSync(new URL('../src/video/view.ts',import.meta.url),'utf8').replace("from './protocol'",'from '+JSON.stringify(protocol))));
function frame(id,size=21){const bytes=new ArrayBuffer(size),view=new DataView(bytes);[0x434c5631,id,16,9,0].forEach((n,i)=>view.setUint32(i*4,n));new Uint8Array(bytes)[size-1]=42;return new Blob([bytes]);}
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

test('maximum-size video reads only its header into JS and preserves the JPEG body and URL cleanup',async()=>{
 const originalImage=globalThis.Image,create=URL.createObjectURL,revoke=URL.revokeObjectURL;
 const images=[],reads=[],urls=[],revoked=[];
 class TrackedBlob extends Blob{
  async arrayBuffer(){reads.push(this.size);return super.arrayBuffer();}
  slice(...args){return new TrackedBlob([super.slice(...args)],{type:args[2]});}
 }
 globalThis.Image=class{naturalWidth=16;naturalHeight=9;constructor(){images.push(this);}set src(value){}};
 URL.createObjectURL=blob=>{urls.push(blob);return 'blob:video-test';};URL.revokeObjectURL=url=>revoked.push(url);
 try{
  let draws=0;const video=new VideoView({width:16,height:9,getContext(){return {drawImage(){draws++;},clearRect(){}};}},{dataset:{},querySelector(){return null;}});
  const packet=new TrackedBlob([frame(1,512020)]),pending=video.receive(packet);await new Promise(resolve=>setImmediate(resolve));
  assert.deepEqual(reads,[21]);assert.equal(urls.length,1);assert.equal(urls[0].type,'image/jpeg');assert.equal(urls[0].size,512000);
  images[0].onload();assert.equal(await pending,1);assert.equal(draws,1);assert.deepEqual(revoked,['blob:video-test']);
  assert.deepEqual(new Uint8Array(await urls[0].arrayBuffer()),new Uint8Array(await packet.slice(20).arrayBuffer()));
 }finally{globalThis.Image=originalImage;URL.createObjectURL=create;URL.revokeObjectURL=revoke;}
});

test('invalid packet lengths and headers are rejected both while idle and while decoding',async()=>{
 const original=globalThis.Image,images=[];globalThis.Image=class{naturalWidth=16;naturalHeight=9;constructor(){images.push(this);}set src(value){}};
 try{
  const video=new VideoView({width:16,height:9,getContext(){return {drawImage(){},clearRect(){}};}},{dataset:{},querySelector(){return null;}});
  const invalid=[new Blob([new Uint8Array(20)]),frame(2,512021),new Blob([new Uint8Array(21)])];
  for(const packet of invalid)await assert.rejects(video.receive(packet),/Invalid video/);
  assert.equal(images.length,0);
  const pending=video.receive(frame(1));await new Promise(resolve=>setImmediate(resolve));
  for(const packet of invalid)await assert.rejects(video.receive(packet),/Invalid video/);
  assert.equal(images.length,1);images[0].onload();assert.equal(await pending,1);
 }finally{globalThis.Image=original;}
});

test('dimension mismatch and decode errors release the decoder and revoke their URLs',async()=>{
 const originalImage=globalThis.Image,revoke=URL.revokeObjectURL,images=[],revoked=[];
 globalThis.Image=class{naturalWidth=16;naturalHeight=9;constructor(){images.push(this);}set src(value){}};
 URL.revokeObjectURL=url=>{revoked.push(url);revoke.call(URL,url);};
 try{
  let draws=0;const video=new VideoView({width:16,height:9,getContext(){return {drawImage(){draws++;},clearRect(){}};}},{dataset:{},querySelector(){return null;}});
  const mismatch=video.receive(frame(1)),rejected=assert.rejects(mismatch,/dimensions mismatch/);
  await new Promise(resolve=>setImmediate(resolve));images[0].naturalWidth=17;images[0].onload();await rejected;
  const failure=video.receive(frame(2)),failed=assert.rejects(failure,/JPEG decode failed/);
  await new Promise(resolve=>setImmediate(resolve));images[1].onerror();await failed;
  const valid=video.receive(frame(3));await new Promise(resolve=>setImmediate(resolve));images[2].onload();assert.equal(await valid,3);
  assert.equal(draws,1);assert.equal(revoked.length,3);assert.equal(new Set(revoked).size,3);
 }finally{globalThis.Image=originalImage;URL.revokeObjectURL=revoke;}
});

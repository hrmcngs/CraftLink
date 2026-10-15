import test from 'node:test';
import assert from 'node:assert/strict';
import {WorldGesture} from '../src/controller/gesture.ts';
import {parseFrameHeader,videoPoint} from '../src/video/protocol.ts';

test('quick tap uses, long press mines, drag looks without placing',()=>{
 const tap=new WorldGesture(10,20,0);assert.equal(tap.advance(100),undefined);assert.equal(tap.end(),'tap');
 const hold=new WorldGesture(10,20,0);assert.equal(hold.advance(350),'hold');assert.equal(hold.advance(600),undefined);assert.equal(hold.end(),'up');
 const drag=new WorldGesture(10,20,0);assert.deepEqual(drag.move(30,25),{dx:20,dy:5,release:false});assert.equal(drag.advance(500),undefined);assert.equal(drag.end(),undefined);
});
test('dragging cancels mining and cancellation never places',()=>{
 const hold=new WorldGesture(0,0,0);hold.advance(350);assert.equal(hold.move(20,0).release,true);assert.equal(hold.end(),undefined);
 const tap=new WorldGesture(0,0,0);assert.equal(tap.end(true),undefined);
});
test('letterboxing maps only the actual image',()=>{
 const bounds={left:10,top:20,width:1000,height:1000};
 assert.deepEqual(videoPoint(510,520,bounds,1600,900),{x:.5,y:.5});
 assert.equal(videoPoint(510,30,bounds,1600,900),undefined);
 assert.deepEqual(videoPoint(10,238.75,bounds,1600,900),{x:0,y:0});
});
test('video header validates context, dimensions and frame id',()=>{
 const packet=new ArrayBuffer(22),view=new DataView(packet);
 [0x434c5631,12,854,480,1].forEach((n,i)=>view.setUint32(i*4,n));
 assert.deepEqual(parseFrameHeader(packet),{id:12,width:854,height:480,context:1});
 view.setUint32(8,999999);assert.throws(()=>parseFrameHeader(packet));
 assert.throws(()=>parseFrameHeader(new ArrayBuffer(10)));
});

test('idle input is refreshed before timeout without losing edges or look deltas',async()=>{
 const {InputCadence}=await import('../src/network/input-cadence.ts');
 const cadence=new InputCadence();
 assert.equal(cadence.allow('forward',0,false),true);
 assert.equal(cadence.allow('forward',33,false),false);
 assert.equal(cadence.allow('forward',250,false),true);
 assert.equal(cadence.allow('released',251,false),true);
 assert.equal(cadence.allow('forward',252,false),true);
 assert.equal(cadence.allow('look',253,true),true);
 assert.equal(cadence.allow('look',254,true),true);
 assert.equal(cadence.allow('slot',255,true),true);
 assert.equal(cadence.allow('slot',256,true),true);
});

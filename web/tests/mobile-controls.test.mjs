import test from 'node:test';import assert from 'node:assert/strict';
import {stickVector,mobilePreset,holdQuantity,strongForward} from '../src/classroom/mobile-controls.ts';
import {WorldGesture} from '../src/controller/gesture.ts';
test('analog movement has a neutral zone, proportional speed and bounded diagonals',()=>{
 assert.deepEqual(stickVector(.1,.1),{x:0,z:0});
 assert.deepEqual(stickVector(NaN,0),{x:0,z:0});
 assert.ok(stickVector(0,-.3).z>0&&stickVector(0,-.3).z<.3);
 assert.ok(stickVector(0,-.8).z>stickVector(0,-.3).z);
 assert.equal(stickVector(0,-2).z,1);
 const diagonal=stickVector(1,-1);assert.ok(diagonal.x>0&&diagonal.z>0);assert.ok(Math.abs(Math.hypot(diagonal.x,diagonal.z)-1)<1e-6);
});
test('mobile presets provide distinct tap and crosshair controls',()=>{
 for(const mode of ['joystick_tap','dpad_tap','joystick_crosshair']){
  const preset=mobilePreset(mode);assert.equal(preset.mode,mode);assert.equal(preset.showActions,mode==='joystick_crosshair');assert.equal(preset.autoJump,mode!=='joystick_crosshair');assert.deepEqual(preset.placements,{});
 }
});
test('dragging after a hold releases mining and changes to looking without placing',()=>{
 const gesture=new WorldGesture(20,20,0);assert.equal(gesture.advance(350),'hold');
 const moved=gesture.move(45,30);assert.equal(moved.release,true);assert.equal(gesture.state,'look');assert.equal(moved.dx,25);assert.equal(gesture.end(),undefined);
});

test('hold selects an increasing bounded quantity and only a strong forward push sprints',()=>{assert.equal(holdQuantity(64,350),1);assert.equal(holdQuantity(64,800),11);assert.equal(holdQuantity(64,10000),64);assert.equal(holdQuantity(1,800),1);assert.equal(strongForward({x:0,z:.9}),true);assert.equal(strongForward({x:.8,z:.9}),false);assert.equal(strongForward({x:0,z:.5}),false);});

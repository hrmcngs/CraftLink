import test from 'node:test';import assert from 'node:assert/strict';
import {normalizeTouchSettings,touchDefaults,DoubleTap} from '../src/classroom/touch-settings.ts';
test('touch settings recover from corrupted or malicious saved values',()=>{
 assert.deepEqual(normalizeTouchSettings(null),touchDefaults);
 const s=normalizeTouchSettings({mode:'unknown',size:999,sensitivity:NaN,opacity:-1,holdToDrop:0,autoJump:'true',placements:{jump:{x:Infinity,y:0},sneak:{x:.5,y:.8,size:999,opacity:-2},unknown:{x:.2,y:.2}}});
 assert.equal(s.mode,'joystick_tap');assert.equal(s.autoJump,false);assert.equal(s.size,100);assert.equal(s.holdToDrop,2000);assert.equal(s.placements.jump,undefined);assert.equal(s.placements.unknown,undefined);assert.deepEqual(s.placements.sneak,{x:.5,y:.8,size:100,opacity:100});
});
test('three modes and bounded per-button settings survive serialization',()=>{
 for(const mode of ['joystick_tap','dpad_tap','joystick_crosshair']){const s=normalizeTouchSettings({mode,size:160,opacity:10,sensitivity:1,invertY:true,holdToDrop:500,dropStack:true,placements:{jump:{x:.9,y:.7,size:180,opacity:50}}});assert.deepEqual(normalizeTouchSettings(JSON.parse(JSON.stringify(s))),s);assert.equal(s.mode,mode);assert.equal(s.placements.jump.size,180);}
});
test('double tap rejects noise, held buttons, expired taps and survives reset',()=>{
 const tap=new DoubleTap();assert.equal(tap.tap(100),false);assert.equal(tap.tap(120),false);assert.equal(tap.tap(260),true);assert.equal(tap.tap(400),false);assert.equal(tap.tap(800),false);tap.reset();assert.equal(tap.tap(850),false);assert.equal(tap.tap(1000),true);
});

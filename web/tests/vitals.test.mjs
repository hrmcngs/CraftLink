import test from 'node:test';import assert from 'node:assert/strict';
import {normalizeVitals,receivedDamage} from '../src/classroom/vitals.ts';
const state={damageSequence:0,onFire:false,soulFire:false,inWater:false,air:300,maxAir:300,frozen:0,freezeThreshold:140,survival:true,health:14,maxHealth:20,absorption:2,armor:12,food:13,level:7,progress:.4,effects:[{id:'minecraft:speed',name:'移動速度上昇',amplifier:1,duration:1200,color:8171462}]};
test('actual survival values and effect durations survive HUD validation',()=>{assert.deepEqual(normalizeVitals(state),state);assert.equal(normalizeVitals({...state,survival:false}).survival,false);});
test('HUD values are bounded and invalid snapshots are rejected',()=>{
 assert.equal(normalizeVitals({...state,health:Infinity}),undefined);assert.equal(normalizeVitals({...state,food:'20'}),undefined);assert.equal(normalizeVitals({...state,effects:[{name:'bad'}]}),undefined);
 const bounded=normalizeVitals({...state,health:100,food:-2,progress:5,level:-1});assert.equal(bounded.health,20);assert.equal(bounded.food,0);assert.equal(bounded.progress,1);assert.equal(bounded.level,0);
 assert.equal(normalizeVitals({...state,effects:[{...state.effects[0],duration:-1}]}).effects[0].duration,-1);
});

test('damage feedback survives skipped snapshots and ignores initial or reset counters',()=>{
 const before=normalizeVitals(state),after=normalizeVitals({...state,damageSequence:3,absorption:0});
 assert.equal(receivedDamage(undefined,after),false);
 assert.equal(receivedDamage(before,after),true);
 assert.equal(receivedDamage(after,after),false);
 assert.equal(receivedDamage(after,before),false);
 assert.equal(receivedDamage(before,{...after,survival:false}),false);
});
test('environment data validates fire, air and freezing and supports older hosts',()=>{
 assert.equal(normalizeVitals({...state,onFire:'true'}),undefined);
 assert.equal(normalizeVitals({...state,damageSequence:NaN}),undefined);
 const next=normalizeVitals({...state,onFire:true,inWater:true,air:-20,maxAir:0,frozen:80});
 assert.equal(next.onFire,true);assert.equal(next.inWater,true);assert.equal(next.air,0);assert.equal(next.maxAir,1);assert.equal(next.frozen,80);
 const {damageSequence,onFire,inWater,air,maxAir,frozen,freezeThreshold,...legacy}=state;
 assert.deepEqual(normalizeVitals(legacy),state);
 assert.equal(normalizeVitals({...state,onFire:false,frozen:0}).onFire,false);
});

test('soul fire is a separate flag and malformed values are rejected',()=>{assert.equal(normalizeVitals({...state,onFire:true,soulFire:true}).soulFire,true);assert.equal(normalizeVitals({...state,soulFire:'yes'}),undefined);});

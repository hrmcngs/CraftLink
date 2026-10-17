import test from 'node:test';import assert from 'node:assert/strict';
import {normalizeVitals} from '../src/classroom/vitals.ts';
const state={survival:true,health:14,maxHealth:20,absorption:2,armor:12,food:13,level:7,progress:.4,effects:[{id:'minecraft:speed',name:'移動速度上昇',amplifier:1,duration:1200,color:8171462}]};
test('actual survival values and effect durations survive HUD validation',()=>{assert.deepEqual(normalizeVitals(state),state);assert.equal(normalizeVitals({...state,survival:false}).survival,false);});
test('HUD values are bounded and invalid snapshots are rejected',()=>{
 assert.equal(normalizeVitals({...state,health:Infinity}),undefined);assert.equal(normalizeVitals({...state,food:'20'}),undefined);assert.equal(normalizeVitals({...state,effects:[{name:'bad'}]}),undefined);
 const bounded=normalizeVitals({...state,health:100,food:-2,progress:5,level:-1});assert.equal(bounded.health,20);assert.equal(bounded.food,0);assert.equal(bounded.progress,1);assert.equal(bounded.level,0);
 assert.equal(normalizeVitals({...state,effects:[{...state.effects[0],duration:-1}]}).effects[0].duration,-1);
});

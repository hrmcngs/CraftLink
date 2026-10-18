import test from 'node:test';
import assert from 'node:assert/strict';
import {normalizeMawStatus,normalizeMawSkills} from '../src/classroom/maw-state.ts';
test('MAW HUD retains participant resources and clears released shield charge',()=>{
 assert.deepEqual(normalizeMawStatus({mana:23.5,maxMana:140,shieldCharge:.6,shieldThreshold:.125,text:'技'}),{mana:23.5,maxMana:140,shieldCharge:.6,shieldThreshold:.125,text:'技'});
 assert.deepEqual(normalizeMawStatus({mana:23.5,maxMana:140}),{mana:23.5,maxMana:140});
 assert.equal(normalizeMawStatus(undefined),undefined);
 assert.equal(normalizeMawStatus({mana:Infinity,maxMana:100}),undefined);
 assert.equal(normalizeMawStatus({mana:1,maxMana:0}),undefined);
 assert.equal(normalizeMawStatus({shieldCharge:'1',shieldThreshold:0}),undefined);
 assert.equal(normalizeMawStatus({text:'x'.repeat(161)}),undefined);
 assert.deepEqual(normalizeMawStatus({mana:999,maxMana:100,shieldCharge:-1,shieldThreshold:5}),{mana:100,maxMana:100,shieldCharge:0,shieldThreshold:1});
});
test('weapon and bow selections accept server choices and reject malformed or duplicate slots',()=>{
 const slot={slot:5,name:'右クリック',selected:'bow',choices:[{id:'bow',name:'弓の技',description:'説明'}]};
 const data={weapon:'弓',slots:[slot]};
 assert.deepEqual(normalizeMawSkills(data),{...data,error:''});
 assert.equal(normalizeMawSkills({...data,slots:[slot,slot]}),undefined);
 assert.equal(normalizeMawSkills({...data,slots:[{...slot,slot:-1}]}),undefined);
 assert.equal(normalizeMawSkills({...data,slots:[{...slot,choices:[{id:'bad'}]}]}),undefined);
 assert.deepEqual(normalizeMawSkills({slots:[],error:'武器を持ってください'}),{slots:[],error:'武器を持ってください',weapon:''});
});

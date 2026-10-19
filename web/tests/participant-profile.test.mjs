import test from 'node:test';import assert from 'node:assert/strict';
import {skinChoices,validName,normalizeProfile} from '../src/classroom/participant-profile.ts';
test('participant name supports Japanese, NFC and bounded plain text',()=>{
 assert.equal(validName('はると_7'),true);assert.equal(validName(' あか '),true);assert.equal(validName('か\u3099'),true);
 for(const name of ['a'.repeat(17),'a\nb','§cName','<img>','a\u202Eb','😀'])assert.equal(validName(name),false);
 assert.deepEqual(normalizeProfile({name:' か\u3099 ',skin:'zuri'}),{name:'が',skin:'zuri'});
});
test('saved profiles recover safely and expose only automatic or nine bundled skins',()=>{
 assert.equal(skinChoices.length,10);assert.deepEqual(normalizeProfile(null),{name:'',skin:'auto'});
 assert.deepEqual(normalizeProfile({name:'<bad>',skin:'https://elsewhere/skin.png'}),{name:'',skin:'auto'});
 for(const [skin] of skinChoices)assert.deepEqual(normalizeProfile({name:'名前',skin}),{name:'名前',skin});
});

test('uploaded local skin IDs survive saved profiles without accepting URLs',()=>{const skin='custom:'+'a'.repeat(64);assert.equal(normalizeProfile({name:'Bot',skin}).skin,skin);assert.equal(normalizeProfile({skin:'custom:../skin.png'}).skin,'auto');});

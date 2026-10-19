import test from 'node:test';import assert from 'node:assert/strict';
import {nativeMenuImage,nativeHudImage} from '../src/classroom/native-ui.ts';
const image='data:image/png;base64,iVBORw0KGgo=';
test('native GUI frames must match the currently open menu revision',()=>{
 const frame={id:1,revision:12,width:176,height:166,image};
 assert.deepEqual(nativeMenuImage(frame,1,12),{width:176,height:166,image});
 assert.equal(nativeMenuImage(frame,2,12),undefined);assert.equal(nativeMenuImage(frame,1,13),undefined);
 assert.equal(nativeMenuImage({...frame,width:99999},1,12),undefined);assert.equal(nativeMenuImage({...frame,width:NaN},1,12),undefined);
 assert.equal(nativeMenuImage({...frame,image:'https://untrusted/image'},1,12),undefined);
});
test('native HUD accepts bounded PNG resources only',()=>{
 assert.equal(nativeHudImage(image),image);assert.equal(nativeHudImage('data:image/svg+xml;base64,AAAA'),undefined);
 assert.equal(nativeHudImage('data:image/png;base64,'+'A'.repeat(400000)),undefined);
});

import test from 'node:test';import assert from 'node:assert/strict';
import {mkdtempSync,mkdirSync,writeFileSync,rmSync} from 'node:fs';import{tmpdir}from'node:os';import{join}from'node:path';import{createHash}from'node:crypto';
import{verifyAssets}from'../verify-assets.mjs';
test('offline assets accept a saved index revision but require every intact object',()=>{
 const dir=mkdtempSync(join(tmpdir(),'craftlink-assets-'));
 try{
  const content=Buffer.from('sound-data'),hash=createHash('sha1').update(content).digest('hex');
  mkdirSync(join(dir,'indexes'));mkdirSync(join(dir,'objects',hash.slice(0,2)),{recursive:true});
  const object=join(dir,'objects',hash.slice(0,2),hash);
  const index=JSON.stringify({objects:{sound:{hash,size:content.length}}});
  const meta=join(dir,'meta.json');writeFileSync(meta,JSON.stringify({assetIndex:{id:'5',sha1:createHash('sha1').update(index).digest('hex')}}));
  writeFileSync(join(dir,'indexes/5.json'),index);
  assert.throws(()=>verifyAssets(meta,dir));
  writeFileSync(object,content);assert.equal(verifyAssets(meta,dir),1);
  writeFileSync(meta,JSON.stringify({assetIndex:{id:'5',sha1:'0'.repeat(40)}}));assert.equal(verifyAssets(meta,dir),1);
  writeFileSync(object,'wrong-data');assert.throws(()=>verifyAssets(meta,dir));
  writeFileSync(join(dir,'indexes/5.json'),'{}');assert.throws(()=>verifyAssets(meta,dir));
 }finally{rmSync(dir,{recursive:true,force:true});}
});

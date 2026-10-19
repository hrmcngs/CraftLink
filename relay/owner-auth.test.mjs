import test from 'node:test';import assert from 'node:assert/strict';
import {generateKeyPairSync} from 'node:crypto';import fs from 'node:fs';import os from 'node:os';import path from 'node:path';
import {ownerHeaders,ownerVerifier,setupOwner,readOwnerKey} from './owner-auth.mjs';
const origin='https://craftlink-relay.onrender.com';
test('registered owner signatures accept once and reject replay, altered owner, wrong origin, expiration and impostors',()=>{
 const pair=generateKeyPairSync('ed25519'),publicKey=pair.publicKey.export({type:'spki',format:'der'}).toString('base64'),now=Date.now();
 const accept=ownerVerifier(publicKey,origin,{now:()=>now}),headers=ownerHeaders(pair.privateKey,origin,{now});
 assert.equal(accept({...headers,'x-craftlink-owner':'other'}),false);
 assert.equal(accept(ownerHeaders(pair.privateKey,'https://other.example',{now})),false);
 assert.equal(accept(ownerHeaders(pair.privateKey,origin,{now:now-60001})),false);
 assert.equal(accept(ownerHeaders(generateKeyPairSync('ed25519').privateKey,origin,{now})),false);
 assert.equal(accept(headers),true);assert.equal(accept(headers),false);
 assert.throws(()=>ownerVerifier(undefined,origin));
});
test('only hrmcngs can set up a local owner key and setup never replaces an existing private key',()=>{
 const root=fs.mkdtempSync(path.join(os.tmpdir(),'craftlink-owner-'));try{
  assert.throws(()=>setupOwner(root));fs.mkdirSync(path.join(root,'.craftlink'));
  const identity=path.join(root,'.craftlink/identity.json');fs.writeFileSync(identity,JSON.stringify({name:'other'}));assert.throws(()=>setupOwner(root));
  fs.writeFileSync(identity,JSON.stringify({name:'hrmcngs'}));const publicKey=setupOwner(root);assert.equal(setupOwner(root),publicKey);
  const file=path.join(root,'.craftlink/owner-key.pem');assert.equal(fs.statSync(file).mode&0o777,0o600);assert.equal(readOwnerKey(root).asymmetricKeyType,'ed25519');
  fs.chmodSync(file,0o644);assert.throws(()=>readOwnerKey(root));
 }finally{fs.rmSync(root,{recursive:true,force:true});}
});

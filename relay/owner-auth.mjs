import fs from 'node:fs';
import path from 'node:path';
import {createPrivateKey,createPublicKey,generateKeyPairSync,randomUUID,sign,verify} from 'node:crypto';
import {fileURLToPath,pathToFileURL} from 'node:url';
export const OWNER='hrmcngs';
const payload=(origin,time,nonce)=>Buffer.from(`CraftLink owner v1\n${origin}\n${OWNER}\n${time}\n${nonce}`);
export function ownerHeaders(privateKey,origin,{now=Date.now(),nonce=randomUUID()}={}){
 const time=String(now);
 return {'x-craftlink-owner':OWNER,'x-craftlink-time':time,'x-craftlink-nonce':nonce,'x-craftlink-proof':sign(null,payload(origin,time,nonce),privateKey).toString('base64')};
}
export function ownerVerifier(encodedKey,origin,{now=Date.now}={}){
 if(typeof encodedKey!=='string'||encodedKey.length>1024||!encodedKey)throw new Error('Set CRAFTLINK_OWNER_PUBLIC_KEY to the registered hrmcngs public key.');
 const key=createPublicKey({key:Buffer.from(encodedKey,'base64'),format:'der',type:'spki'});
 if(key.asymmetricKeyType!=='ed25519')throw new Error('The owner public key must be Ed25519.');
 const used=new Map();
 return headers=>{
  const owner=headers['x-craftlink-owner'],time=headers['x-craftlink-time'],nonce=headers['x-craftlink-nonce'],proof=headers['x-craftlink-proof'];
  if(owner!==OWNER||typeof time!=='string'||!/^\d{13}$/.test(time)||typeof nonce!=='string'||!/^[0-9a-f-]{36}$/.test(nonce)||typeof proof!=='string'||!/^[A-Za-z0-9+/]{86}==$/.test(proof))return false;
  const current=now();
  if(Math.abs(current-Number(time))>60000)return false;
  for(const [value,expires] of used)if(expires<current)used.delete(value);
  if(used.has(nonce)||used.size>=1024)return false;
  if(!verify(null,payload(origin,time,nonce),key,Buffer.from(proof,'base64')))return false;
  used.set(nonce,current+120000);return true;
 };
}
function identity(root){
 const directory=path.join(root,'.craftlink'),file=path.join(directory,'identity.json');
 if(!fs.existsSync(file)||fs.lstatSync(directory).isSymbolicLink()||fs.lstatSync(file).isSymbolicLink()||fs.statSync(file).size>1024)throw new Error('自宅で hrmcngs として sh run.sh --offline --login をタイトル画面まで起動し、本人情報を保存してください。');
 const value=JSON.parse(fs.readFileSync(file,'utf8'));
 if(typeof value.name!=='string'||value.name.toLowerCase()!==OWNER)throw new Error('この中継の主催者は hrmcngs のみです。');
 return directory;
}
export function readOwnerKey(root){
 const directory=identity(root),file=path.join(directory,'owner-key.pem');
 if(!fs.existsSync(file))throw new Error('主催者キーがありません。node relay/owner-auth.mjs --setup を実行し、公開鍵をRenderに登録してください。');
 const stat=fs.lstatSync(file);
 if(!stat.isFile()||stat.isSymbolicLink()||(stat.mode&0o077)!==0||stat.size>4096)throw new Error('主催者の秘密鍵は通常ファイル・権限600で保存してください。');
 const key=createPrivateKey(fs.readFileSync(file));
 if(key.asymmetricKeyType!=='ed25519')throw new Error('主催者キーが不正です。');
 return key;
}
export function setupOwner(root){
 const directory=identity(root),file=path.join(directory,'owner-key.pem');
 if(!fs.existsSync(file)){
  const {privateKey}=generateKeyPairSync('ed25519');
  fs.writeFileSync(file,privateKey.export({type:'pkcs8',format:'pem'}),{flag:'wx',mode:0o600});
 }
 const key=readOwnerKey(root),publicKey=createPublicKey(key).export({type:'spki',format:'der'}).toString('base64');
 const publicFile=path.join(directory,'owner-public-key.txt');
 if(fs.lstatSync(publicFile,{throwIfNoEntry:false})?.isSymbolicLink())throw new Error('公開鍵の保存先をリンクにはできません。');
 fs.writeFileSync(publicFile,publicKey+'\n',{mode:0o600});
 return publicKey;
}
if(process.argv[1]&&import.meta.url===pathToFileURL(process.argv[1]).href){
 try{
  if(process.argv[2]!=='--setup')throw new Error('Usage: node relay/owner-auth.mjs --setup');
  const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
  console.log('RenderのEnvironmentで CRAFTLINK_OWNER_PUBLIC_KEY に次の公開鍵を登録してください（秘密鍵ではありません）。');
  console.log(setupOwner(root));
 }catch(error){console.error(error.message);process.exitCode=1;}
}

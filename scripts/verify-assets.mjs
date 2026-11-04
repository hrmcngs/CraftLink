import {readFileSync,statSync} from 'node:fs';
import {createHash} from 'node:crypto';
import {join,resolve} from 'node:path';
import {pathToFileURL} from 'node:url';
export function verifyAssets(metadataFile, directory) {
 const meta=JSON.parse(readFileSync(metadataFile,'utf8'));
 const index=meta.assetIndex;
 if(!index||!/^\w[\w.-]*$/.test(index.id)||! /^[a-f0-9]{40}$/.test(index.sha1))throw new Error('Invalid asset index metadata');
 const bytes=readFileSync(join(directory,'indexes',index.id+'.json'));
 // Asset indexes are updated by Mojang under the same ID. Offline launches
 // use the previously downloaded index and validate all its content hashes.
 const objects=JSON.parse(bytes).objects;
 if(!objects||typeof objects!=='object'||Array.isArray(objects)||Object.keys(objects).length===0)throw new Error('Invalid asset index');
 const checked=new Set();
 for(const entry of Object.values(objects)){
  if(!entry||! /^[a-f0-9]{40}$/.test(entry.hash)||!Number.isSafeInteger(entry.size)||entry.size<0)throw new Error('Invalid asset entry');
  if(checked.has(entry.hash))continue;
  const file=join(directory,'objects',entry.hash.slice(0,2),entry.hash);
  if(statSync(file).size!==entry.size||createHash('sha1').update(readFileSync(file)).digest('hex')!==entry.hash)throw new Error('Missing or damaged asset: '+entry.hash);
  checked.add(entry.hash);
 }
 return checked.size;
}
if(process.argv[1]&&import.meta.url===pathToFileURL(resolve(process.argv[1])).href){
 try{console.log(`保存済みMinecraftデータを確認しました（${verifyAssets(process.argv[2],process.argv[3])}件）。オフラインで使用します。`);}
 catch{console.error('Minecraftの画像・音声データが不足、または破損しています。取得可能なネットワークで sh run.sh --prepare を一度実行してから、学校用起動をやり直してください。');process.exitCode=1;}
}

import {readOwnerKey} from './owner-auth.mjs';
import fs from 'node:fs';
import path from 'node:path';
import {randomUUID,createHash,createPublicKey} from 'node:crypto';
import {spawn} from 'node:child_process';
import {fileURLToPath,pathToFileURL} from 'node:url';
import {connectBridge} from './bridge.mjs';
import {validateSecret} from './protocol.mjs';

export const DEFAULT_URL='https://craftlink-relay.onrender.com';
export function validateConfig(value) {
  validateSecret(value?.secret);
  const url=new URL(value?.url);
  if(url.protocol!=='https:'||url.origin!==value.url||url.username||url.password)throw new Error('中継URLは https://ホスト名 の形式で指定してください。');
  return {url:url.origin,secret:value.secret};
}
export function readConfig(root) {
  const file=path.join(root,'.craftlink','school.json');
  if(!fs.existsSync(file))return;
  const stat=fs.lstatSync(file);
  if(!stat.isFile()||stat.isSymbolicLink()||(stat.mode&0o077)!==0)throw new Error('保存した学校接続設定の権限を600にしてください。');
  return validateConfig(JSON.parse(fs.readFileSync(file,'utf8')));
}
export function saveConfig(root,value) {
  const config=validateConfig(value),directory=path.join(root,'.craftlink');
  fs.mkdirSync(directory,{recursive:true,mode:0o700});
  if(fs.lstatSync(directory).isSymbolicLink())throw new Error('設定ディレクトリはリンクにできません。');
  fs.chmodSync(directory,0o700);
  const temporary=path.join(directory,randomUUID()+'.tmp');
  try{fs.writeFileSync(temporary,JSON.stringify(config),{flag:'wx',mode:0o600});fs.renameSync(temporary,path.join(directory,'school.json'));}
  finally{if(fs.existsSync(temporary))fs.unlinkSync(temporary);}
  return config;
}
export function localPort(root,mode) {
  const key=mode==='classroom'?'classroomPort':'port',fallback=mode==='classroom'?8766:8765;
  const file=path.join(root,'run/config/craftlink-client.toml');if(!fs.existsSync(file))return fallback;
  const match=fs.readFileSync(file,'utf8').match(new RegExp('^'+key+'\\s*=\\s*(\\d+)\\s*(?:#.*)?$','m'));
  const value=match?Number(match[1]):fallback;
  if(!Number.isInteger(value)||value<1024||value>65535)throw new Error('Minecraftの接続ポート設定が不正です。');
  return value;
}
export function launchSchool(options,dependencies={}) {
  if(options.login==='guest')throw new Error('devは同じWi-Fi・LANのローカル起動専用です。sh craftlink play --dev を使ってください。');
  const {url,secret}=validateConfig(options);
  if(!['controller','classroom'].includes(options.mode))throw new Error('接続モードが不正です。');
  const connect=dependencies.connect??connectBridge,spawnGame=dependencies.spawnGame??spawn,log=dependencies.log??console.log;
  const retryMs=dependencies.retryMs??3000;
  let bridge,game,retry,finished=false,starting=false,resolveDone;
  const done=new Promise(resolve=>resolveDone=resolve);
  const terminate=dependencies.terminate??(child=>{try{process.kill(-child.pid,'SIGTERM');}catch{child.kill('SIGTERM');}});
  const startup=setTimeout(()=>{log('中継へ接続できません。ネット接続・保存したキー・Renderの稼働状況を確認してください。再設定は sh school.sh --setup');stop(1);},dependencies.startupMs??120000);
  function finish(code){if(finished)return;finished=true;clearTimeout(startup);clearTimeout(retry);bridge?.terminate();resolveDone(code);}
  function stop(code=130){if(finished)return;if(game)terminate(game);finish(code);}
  function reconnect(){
    if(finished)return;
    log('学校用HTTPS中継へ接続中…');
    bridge=connect({url:url.replace(/^https:/,'wss:')+'/_bridge',secret,port:options.port,ownerKey:options.ownerKey,onRejected:(status,details={})=>{
      log(`中継が接続を拒否しました（HTTP ${status}）。主催者 hrmcngs の公開鍵登録と認証キーを確認してください。`);
      if(status===401||status===403){
        log('認証拒否のため自動再接続を停止します。Minecraftのログインとは別の、中継の主催者認証です。');
        if(details.reason==='relay-key')log('原因: 保存済みの中継キーがRenderと一致しません。メニューの「3 → 6」でCRAFTLINK_RELAY_SECRETを入力し直してください。');
        else if(details.reason==='already-connected')log('原因: 同じ中継に別の主催者接続があります。接続中の別ターミナル・Macを終了してから再実行してください。');
        else if(details.reason==='owner-proof'){
          const fingerprint=options.ownerKey?createHash('sha256').update(createPublicKey(options.ownerKey).export({format:'der',type:'spki'})).digest('hex'):undefined;
          if(details.ownerFingerprint&&fingerprint&&details.ownerFingerprint!==fingerprint)log('原因: Renderに登録された公開鍵がこのMacの公開鍵と一致しません。「3 → 5」の公開鍵をCRAFTLINK_OWNER_PUBLIC_KEYに登録し、再デプロイしてください。');
          else if(details.origin&&details.origin!==url)log('原因: 中継の公開URL設定がMacの接続先と一致しません。Renderの公開URLと接続先設定を確認してください。');
          else log('原因: 主催者署名が受理されませんでした。公開鍵とMacの日時を確認してください。');
        }else log('中継が詳細診断に未対応です。Renderで最新コミットをデプロイすると原因を個別表示できます。');
        log('メニューの「3 → 5」で公開鍵を表示し、RenderのCRAFTLINK_OWNER_PUBLIC_KEYと照合してください。「3 → 6」で中継キーを再設定できます。');
        log('同じ中継へ接続中の別ターミナル・別Macがあれば終了し、Macの日時も確認してください。キーはチャットに送らないでください。');
        stop(77);
      }
    },onReady:()=>{
      if(finished||starting)return;starting=true;clearTimeout(startup);
      log('中継接続済み。Minecraftでワールドを開くと、参加用QRを自動表示します。');
      const args=[path.join(options.root,'run.sh'),'lan'];if(options.offline)args.push('--offline');
      if(options.mods)args.push('--mods');
      args.push(options.login==='online'?'--login':options.login==='guest'?'--no-login':'--cached-login');
      const childEnv={...process.env,ORG_GRADLE_PROJECT_craftlinkRemote:'true',ORG_GRADLE_PROJECT_craftlinkExternalUrl:url,ORG_GRADLE_PROJECT_craftlinkSchoolMode:options.mode};
      delete childEnv.CRAFTLINK_RELAY_SECRET;
      try{
        game=spawnGame('sh',args,{cwd:options.root,stdio:'inherit',detached:true,env:childEnv});
        game.once('error',()=>{log('Minecraftを起動できませんでした。');finish(1);});
        game.once('exit',(code)=>finish(code??1));
      }catch{finish(1);}
    },onClose:()=>{if(!finished){log('中継が切れました。入力を解除し、再接続します。');retry=setTimeout(reconnect,retryMs);}}});
  }
  reconnect();return {done,stop};
}
if(process.argv[1]&&import.meta.url===pathToFileURL(process.argv[1]).href){
 try{
  const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..'),args=process.argv.slice(2);
  let mode='classroom',offline=true,setup=false,mods=false,login='cached';
  for(const arg of args){if(arg==='controller'||arg==='classroom')mode=arg;else if(arg==='--offline')offline=true;else if(arg==='--online')offline=false;else if(arg==='--setup')setup=true;else if(arg==='--mods')mods=true;else if(arg==='--login')login='online';else if(arg==='--cached-login')login='cached';else if(arg==='--previous'){}else if(arg==='--dev'||arg==='--no-login')login='guest';else throw new Error('Usage: sh school.sh [classroom|controller] [--offline|--online] [--setup] [--mods] [--login|--cached-login|--dev|--no-login]');}
  if(login==='guest')throw new Error('devは同じWi-Fi・LANのローカル起動専用です。sh craftlink play --dev を使ってください。');
  let config=setup?undefined:readConfig(root);
  if(!config){config=saveConfig(root,{url:process.env.CRAFTLINK_SCHOOL_URL??DEFAULT_URL,secret:process.env.CRAFTLINK_RELAY_SECRET});console.log('初回設定をこのMacに保存しました。次回からキーの入力は不要です。');}
  // Do not pass the relay credential into Minecraft/Gradle child processes.
  delete process.env.CRAFTLINK_RELAY_SECRET;
  const ownerKey=readOwnerKey(root);
  const running=launchSchool({...config,ownerKey,root,mode,offline,mods,login,port:localPort(root,mode)});
  process.once('SIGINT',()=>running.stop(130));process.once('SIGTERM',()=>running.stop(143));
  process.exitCode=await running.done;
 }catch(error){console.error(error.message);process.exitCode=1;}
}

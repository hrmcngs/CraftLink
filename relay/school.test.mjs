import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {EventEmitter} from 'node:events';
import {saveConfig,readConfig,validateConfig,localPort,launchSchool} from './school.mjs';
const secret=Buffer.alloc(32,251).toString('base64'),url='https://craftlink.example';
function temporary(){return fs.mkdtempSync(path.join(os.tmpdir(),'craftlink school '));}
test('first setup saves a private config and later launches can reuse the exact Base64 key',()=>{
 const root=temporary();try{assert.equal(readConfig(root),undefined);saveConfig(root,{url,secret});assert.deepEqual(readConfig(root),{url,secret});assert.equal(fs.statSync(path.join(root,'.craftlink/school.json')).mode&0o777,0o600);assert.equal(fs.statSync(path.join(root,'.craftlink')).mode&0o777,0o700);assert.throws(()=>saveConfig(root,{url:'http://unsafe.example',secret}));assert.deepEqual(readConfig(root),{url,secret});}finally{fs.rmSync(root,{recursive:true});}
});
test('unsafe URLs, exposed credentials and symlink settings are rejected',()=>{
 for(const value of ['https://user:pass@host.example','https://host.example/#token','https://host.example/path'])assert.throws(()=>validateConfig({url:value,secret}));
 const root=temporary();try{saveConfig(root,{url,secret});const file=path.join(root,'.craftlink/school.json');fs.chmodSync(file,0o644);assert.throws(()=>readConfig(root),/600/);fs.chmodSync(file,0o600);fs.renameSync(file,file+'.real');fs.symlinkSync(file+'.real',file);assert.throws(()=>readConfig(root),/600/);}finally{fs.rmSync(root,{recursive:true});}
});
test('school bridge uses the existing Minecraft port for each mode',()=>{
 const root=temporary();try{assert.equal(localPort(root,'classroom'),8766);fs.mkdirSync(path.join(root,'run/config'),{recursive:true});fs.writeFileSync(path.join(root,'run/config/craftlink-client.toml'),'port = 9000\nclassroomPort = 9001 # classroom\n');assert.equal(localPort(root,'controller'),9000);assert.equal(localPort(root,'classroom'),9001);}finally{fs.rmSync(root,{recursive:true});}
});
function lifecycle(extra={},options={}){
 const connections=[],games=[],game=new EventEmitter();let terminated=0;
 const running=launchSchool({url,secret,mode:'classroom',port:8766,root:'/tmp/project with spaces',offline:true,...options},{
  connect:options=>{const bridge={terminate(){bridge.closed=true;}};connections.push({options,bridge});return bridge;},
  spawnGame:(...args)=>{games.push(args);return game;},terminate:()=>terminated++,log:()=>{},retryMs:5,startupMs:1000,...extra
 });return {running,connections,games,game,terminated:()=>terminated};
}
test('one launch connects first, starts Minecraft with the URL automatically, and cleans up after game exit',async()=>{
 process.env.CRAFTLINK_RELAY_SECRET=secret;
 const f=lifecycle();delete process.env.CRAFTLINK_RELAY_SECRET;
 try{assert.equal(f.games.length,0);f.connections[0].options.onReady();assert.equal(f.games.length,1);const [command,args,settings]=f.games[0];assert.equal(command,'sh');assert.deepEqual(args,['/tmp/project with spaces/run.sh','lan','--offline','--cached-login']);assert.equal(settings.env.ORG_GRADLE_PROJECT_craftlinkExternalUrl,url);assert.equal(settings.env.ORG_GRADLE_PROJECT_craftlinkRemote,'true');assert.equal(settings.env.ORG_GRADLE_PROJECT_craftlinkSchoolMode,'classroom');assert.equal(settings.env.CRAFTLINK_RELAY_SECRET,undefined);f.game.emit('exit',7);assert.equal(await f.running.done,7);assert.equal(f.connections[0].bridge.closed,true);}finally{f.running.stop();}
});
test('relay reconnects without starting a second Minecraft; Ctrl+C stops both',async()=>{
 const f=lifecycle();try{f.connections[0].options.onReady();f.connections[0].options.onClose();await new Promise(resolve=>setTimeout(resolve,20));assert.equal(f.connections.length,2);f.connections[1].options.onReady();assert.equal(f.games.length,1);f.running.stop(130);assert.equal(await f.running.done,130);assert.equal(f.terminated(),1);assert.equal(f.connections[1].bridge.closed,true);}finally{f.running.stop();}
});
test('Mod-enabled school launch passes the Mod profile through to Minecraft',async()=>{
 const f=lifecycle({}, {mods:true});try{f.connections[0].options.onReady();assert.deepEqual(f.games[0][1],['/tmp/project with spaces/run.sh','lan','--offline','--mods','--cached-login']);f.game.emit('exit',0);assert.equal(await f.running.done,0);}finally{f.running.stop();}
});
test('unreachable relay times out without starting Minecraft',async()=>{
 const f=lifecycle({startupMs:10});assert.equal(await f.running.done,1);assert.equal(f.games.length,0);assert.equal(f.connections[0].bridge.closed,true);
});

test('online login is explicit and independent of Gradle offline mode',async()=>{
 const f=lifecycle({}, {login:'online'});try{f.connections[0].options.onReady();assert.deepEqual(f.games[0][1],['/tmp/project with spaces/run.sh','lan','--offline','--login']);f.game.emit('exit',0);assert.equal(await f.running.done,0);}finally{f.running.stop();}
});

for(const status of [401,403])test('authentication rejection '+status+' stops instead of reconnecting',async()=>{
 const f=lifecycle();
 try{
  f.connections[0].options.onRejected(status);
  f.connections[0].options.onClose();
  assert.equal(await f.running.done,77);
  await new Promise(resolve=>setTimeout(resolve,20));
  assert.equal(f.connections.length,1);
  assert.equal(f.games.length,0);
  assert.equal(f.connections[0].bridge.closed,true);
 }finally{f.running.stop();}
});
test('authentication rejection after a connection loss stops the owned game',async()=>{
 const f=lifecycle();try{
  f.connections[0].options.onReady();f.connections[0].options.onClose();
  await new Promise(resolve=>setTimeout(resolve,20));
  f.connections[1].options.onRejected(403);f.connections[1].options.onClose();
  assert.equal(await f.running.done,77);assert.equal(f.terminated(),1);
 }finally{f.running.stop();}
});

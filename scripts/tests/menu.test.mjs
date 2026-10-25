import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
const source=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../../menu.sh');
function menu(input,extra={}){
 const root=fs.mkdtempSync(path.join(os.tmpdir(),'craftlink menu '));
 try{
  fs.copyFileSync(source,path.join(root,'menu.sh'));
  fs.copyFileSync(path.join(path.dirname(source),'craftlink'),path.join(root,'craftlink'));
  fs.mkdirSync(path.join(root,'scripts'));
  for(const name of ['run.sh','school.sh','build.sh','tunnel.sh','relay.sh','scripts/build-mods.sh']){
   fs.writeFileSync(path.join(root,name),'#!/bin/sh\nprintf "CALL %s\\n" "'+name+'" >> "$MENU_LOG"\nprintf "%s\\n" "$@" >> "$MENU_LOG"\nexit "${MENU_EXIT:-0}"\n');
  }
  const log=path.join(root,'calls');
  const result=spawnSync('sh',[path.join(root,'menu.sh')],{input:'4\n'+input,encoding:'utf8',cwd:os.tmpdir(),env:{...process.env,MENU_LOG:log,...extra}});
  return {...result,calls:fs.existsSync(log)?fs.readFileSync(log,'utf8'):''};
 }finally{fs.rmSync(root,{recursive:true,force:true});}
}
test('exit and invalid input never start a child',()=>{
 const r=menu('invalid\n0\n');assert.equal(r.status,0);assert.equal(r.calls,'');assert.match(r.stdout,/メニューの番号/);
});
test('normal and USB launches use selected port, dependency and MOD options',()=>{
 const r=menu('5\n6\n09876\n14\n11\n1\n2\n3\n0\n');
 assert.equal(r.status,0,r.stderr);
 assert.match(r.calls,/CALL run.sh\nlan\n--offline\n--port\n9876\n--dev\n--mods\n/);
 assert.match(r.calls,/CALL run.sh\nusb\n--offline\n--port\n9876\n--dev\n--mods\n/);
});
for(const [number,argument]of [['10',''],['12','controller\n'],['13','--setup\n']]){
 test('school action '+number+' forwards login and MOD selections',()=>{
  const r=menu('11\n2\n14\n'+number+'\n0\n');
  assert.equal(r.status,0,r.stderr);
  assert(r.calls.includes('CALL school.sh\n'+argument+'--online\n--login\n--mods\n'));
 });
}
test('previous login selection can be restored without sending a new login choice',()=>{
 const r=menu('11\n1\n11\n4\n10\n0\n');
 assert.equal(r.status,0);assert.match(r.calls,/--previous/);assert.doesNotMatch(r.calls,/--dev/);
});
test('build and preparation actions do not need Minecraft or a phone',()=>{
 const r=menu('5\n1\n4\n15\n0\n');assert.equal(r.status,0);
 assert.match(r.calls,/CALL build.sh\noffline/);assert.match(r.calls,/--prepare\n--offline/);assert.match(r.calls,/CALL scripts\/build-mods.sh\n--offline/);
});
test('invalid port cannot be passed to a launcher',()=>{
 const r=menu('6\n99999\n2\n0\n');assert.equal(r.status,0);assert.doesNotMatch(r.calls,/--port/);
});
test('tunnel and relay modes remain selectable',()=>{
 const r=menu('8\n2\n9\nhttps://example.invalid\n1\n0\n');assert.equal(r.status,0);
 assert.match(r.calls,/CALL tunnel.sh\nclassroom/);assert.match(r.calls,/CALL relay.sh\ncontroller\nhttps:\/\/example.invalid/);
});
test('a failed child reports its status and returns to the menu',()=>{
 const r=menu('1\n0\n',{MENU_EXIT:'7'});assert.equal(r.status,0);assert.match(r.stderr,/終了コード: 7/);
});

test('help explains a script without launching it',()=>{
 const r=menu('16\nschool\n0\n');assert.equal(r.status,0);assert.equal(r.calls,'');assert.match(r.stdout,/scripts\/school.sh/);
});

test('main screen makes Enter start Minecraft using the school profile',()=>{
 const r=menu('0\n\n0\n');assert.equal(r.status,0);assert.match(r.calls,/CALL school.sh/);assert.match(r.stdout,/Minecraftを起動しています/);
});
test('switching launch method waits for launch selection',()=>{
 const r=menu('0\n2\n2\n1\n0\n');assert.equal(r.status,0);assert.match(r.calls,/CALL run.sh\nlan/);assert.doesNotMatch(r.calls,/school.sh/);
});
test('simple settings preserve selected login mode',()=>{
 const r=menu('0\n3\n1\n1\n1\n0\n');assert.equal(r.status,0);assert.match(r.calls,/CALL school.sh[\s\S]*--dev/);
});

test('first screen presents launch first and hides developer commands',()=>{
 const r=spawnSync('sh',[source],{input:'0\n',encoding:'utf8'});
 assert.equal(r.status,0);
 assert.match(r.stdout,/1\) ▶ Minecraftを起動/);
 assert.doesNotMatch(r.stdout,/1\) MODをビルド|公開済み中継のHTTPS URL|実行するshell/);
});

test('auth rejection returns to the main menu with recovery instructions',()=>{
 const r=menu('0\n1\n0\n',{MENU_EXIT:'77'});
 assert.equal(r.status,0);assert.match(r.stderr,/3 → 5/);
 assert.equal((r.calls.match(/CALL school.sh/g)||[]).length,1);
});
test('main settings can reset the relay credential without using the advanced menu',()=>{
 const r=menu('0\n3\n6\n0\n');assert.equal(r.status,0);assert.match(r.calls,/CALL school.sh\n--setup/);
});

import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
test('every help topic is available without launching Minecraft or the relay',()=>{
 for(const topic of ['overview','school','play','usb','build','prepare','mods','tunnel','relay','login','terminal']){
  const result=spawnSync('sh',[path.join(root,'craftlink'),'help',topic],{encoding:'utf8',cwd:os.tmpdir()});
  assert.equal(result.status,0,result.stderr);assert(result.stdout.length>50);
 }
});
test('unknown commands fail instead of being evaluated',()=>{
 const r=spawnSync('sh',[path.join(root,'craftlink'),'$(exit 0)'],{encoding:'utf8'});
 assert.equal(r.status,2);assert.match(r.stderr,/不明なコマンド/);
});
test('unified commands forward literal arguments from a directory with spaces',()=>{
 const fixture=fs.mkdtempSync(path.join(os.tmpdir(),'craftlink terminal '));
 try{
  fs.copyFileSync(path.join(root,'craftlink'),path.join(fixture,'craftlink'));fs.mkdirSync(path.join(fixture,'scripts'));
  for(const script of ['run.sh','school.sh','build.sh','build-mods.sh','tunnel.sh','relay.sh'])fs.writeFileSync(path.join(fixture,'scripts',script),'#!/bin/sh\nprintf "%s\\n" "$@"\n');
  fs.writeFileSync(path.join(fixture,'menu.sh'),'#!/bin/sh\nprintf "MENU\\n"\n');
  for(const [action,prefix]of [['play',[]],['usb',['usb']],['prepare',['--prepare']],['school',[]],['build',[]],['mods',[]],['tunnel',[]],['relay',[]]]){
   const r=spawnSync('sh',[path.join(fixture,'craftlink'),action,'--dev','value with spaces'],{encoding:'utf8',cwd:os.tmpdir()});
   assert.equal(r.status,0,r.stderr);assert.deepEqual(r.stdout.trim().split('\n'),[...prefix,'--dev','value with spaces']);
  }
  const menu=spawnSync('sh',[path.join(fixture,'craftlink')],{encoding:'utf8'});assert.equal(menu.stdout,'MENU\n');
 }finally{fs.rmSync(fixture,{recursive:true,force:true});}
});
test('zsh extension registers command and completion outside the project directory',()=>{
 const r=spawnSync('zsh',['-fc','source "$1/scripts/terminal.zsh"; craftlink help login; whence -w _craftlink','test',root],{encoding:'utf8',cwd:os.tmpdir()});
 assert.equal(r.status,0,r.stderr);assert.match(r.stdout,/Microsoft認証/);assert.match(r.stdout,/_craftlink: function/);
});

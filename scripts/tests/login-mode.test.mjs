import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {spawnSync} from 'node:child_process';
const source=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
function fixture(run){
 const root=fs.mkdtempSync(path.join(os.tmpdir(),'craftlink-school-login-'));
 try{
  for(const dir of ['scripts','relay/node_modules/ws','.craftlink','bin'])fs.mkdirSync(path.join(root,dir),{recursive:true});
  for(const name of ['school.sh','login-mode.sh'])fs.copyFileSync(path.join(source,name),path.join(root,'scripts',name));
  fs.writeFileSync(path.join(root,'.craftlink/school.json'),'{}');
  fs.writeFileSync(path.join(root,'bin/node'),'#!/bin/sh\nif [ "$1" = "-e" ]; then exit 0; fi\nprintf "%s\\n" "$CRAFTLINK_LOGIN_PROFILE" "$@"\n',{mode:0o700});
  const launch=(args=[])=>spawnSync('bash',[path.join(root,'scripts/school.sh'),...args],{encoding:'utf8',env:{...process.env,PATH:path.join(root,'bin')+':'+process.env.PATH}});
  run(root,launch);
 }finally{fs.rmSync(root,{recursive:true,force:true});}
}
for(const [flag,mode]of [['--dev','guest'],['--login','online'],['--cached-login','cached']]){
 test('school preserves '+mode+' across launches without changing regular choice',()=>fixture((root,launch)=>{
  fs.writeFileSync(path.join(root,'.craftlink/login-run'),'guest\n');
  assert.equal(launch([flag]).status,0);
  const restored=launch(['--previous']);
  assert.equal(restored.status,0,restored.stderr);
  assert.equal(restored.stdout.trim().split('\n').at(-1),flag);
  assert.equal(fs.readFileSync(path.join(root,'.craftlink/login-school'),'utf8'),mode+'\n');
  assert.equal(fs.readFileSync(path.join(root,'.craftlink/login-run'),'utf8'),'guest\n');
  assert.equal(fs.statSync(path.join(root,'.craftlink/login-school')).mode&0o777,0o600);
 }));
}
test('school rejects invalid saved mode and permits explicit reselection',()=>fixture((root,launch)=>{
 fs.writeFileSync(path.join(root,'.craftlink/login-school'),'invalid\n');
 assert.notEqual(launch().status,0);
 assert.equal(launch(['--dev']).status,0);
}));
test('school rejects a symlink instead of overwriting its target',()=>fixture((root,launch)=>{
 const target=path.join(root,'keep');fs.writeFileSync(target,'unchanged');
 fs.symlinkSync(target,path.join(root,'.craftlink/login-school'));
 assert.notEqual(launch(['--dev']).status,0);
 assert.equal(fs.readFileSync(target,'utf8'),'unchanged');
}));

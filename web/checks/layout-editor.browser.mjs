import http from 'node:http';import fs from 'node:fs';import assert from 'node:assert/strict';
import ts from '../node_modules/typescript/lib/typescript.js';
import{fileURLToPath}from'node:url';
import path from'node:path';
const {chromium}=await import(process.env.CRAFTLINK_PLAYWRIGHT_MODULE??'playwright-core');
const repo=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..'),base=repo+'/web/src/classroom/';
const html=`<!doctype html><html><meta name="viewport" content="width=device-width,initial-scale=1"><link rel="stylesheet" href="/style.css"><div id="classroom" data-enabled="true" data-touch-mode="joystick_tap" data-show-actions="false" data-show-drop="false"><main><div class="dpad" data-control="dpad"><button>▲</button></div><div id="joystick" data-control="joystick"><i></i></div><div id="look"></div><section class="actions"><button data-control="jump" data-key="jump">↥</button><button data-control="sneak" data-key="sneak">スニーク</button><button data-control="attack" data-key="attack">こわす</button><button data-control="use" data-key="use">おく</button><button id="drop-item" data-control="drop">捨てる</button></section></main><nav id="bot-hotbar" data-control="hotbar"><button>1</button><button>2</button></nav></div><script type="module">import{LayoutEditor}from'/layout-editor';import{loadTouchSettings}from'/touch-settings';const root=document.querySelector('#classroom');window.settings=loadTouchSettings();if(!localStorage.getItem('craftlink.botTouch'))settings.showDrop=false;function apply(){root.dataset.showActions=String(settings.showActions);root.dataset.showDrop=String(settings.showDrop);root.dataset.touchMode=settings.mode;root.style.setProperty('--controls-opacity',String(settings.showButtons?settings.opacity/100:0));window.editor?.apply();}window.editor=new LayoutEditor(root,()=>settings,apply,()=>{});apply();</script></html>`;
const server=http.createServer((req,res)=>{if(req.url==='/'){res.setHeader('Content-Type','text/html');res.end(html);return;}const path=req.url.split('?')[0];if(path==='/style.css'){res.setHeader('Content-Type','text/css');res.end(fs.readFileSync(base+'style.css'));return;}const name=path.slice(1).replace(/\.ts$/,'');if(!/^[a-z-]+$/.test(name)){res.writeHead(404).end();return;}try{res.setHeader('Content-Type','text/javascript');res.end(ts.transpileModule(fs.readFileSync(base+name+'.ts','utf8'),{compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.ESNext}}).outputText);}catch{res.writeHead(404).end();}});
await new Promise(r=>server.listen(0,'127.0.0.1',r));const browser=await chromium.launch({executablePath:process.env.CRAFTLINK_CHROME??'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',headless:true});
try{
 const page=await browser.newPage({viewport:{width:900,height:500}});
 page.on('pageerror',error=>{throw error;});
 const url='http://127.0.0.1:'+server.address().port;
 async function fresh(){
  await page.setViewportSize({width:900,height:500});await page.goto(url);await page.waitForFunction(()=>window.editor);
  await page.evaluate(()=>localStorage.clear());await page.reload();await page.waitForFunction(()=>window.editor);
 }
 await fresh();await page.evaluate(()=>{editor.open();editor.close();});
 assert.equal(await page.locator('[data-control=use]').isVisible(),false);
 assert.equal(await page.locator('[data-control=drop]').isVisible(),false);
 console.log('PASS opening and closing the editor preserves intentionally hidden buttons');
 for(const id of ['use','attack','drop']){
  await fresh();await page.evaluate(()=>editor.open());const control=page.locator('[data-control='+id+']');const box=await control.boundingBox();assert(box);
  await page.mouse.move(box.x+box.width/2,box.y+box.height/2);await page.mouse.down();await page.mouse.move(160,100,{steps:6});await page.mouse.up();await page.evaluate(()=>editor.close());
  assert(await control.isVisible(),id+' disappeared after saving');
  const saved=await page.evaluate(id=>({placement:settings.placements[id],actions:settings.showActions,drop:settings.showDrop}),id);
  assert(saved.placement);if(id==='drop')assert(saved.drop);else assert(saved.actions);
  let moved=await control.boundingBox();assert(Math.abs(moved.x+moved.width/2-160)<2);assert(Math.abs(moved.y+moved.height/2-100)<2);
  await page.reload();await page.waitForFunction(()=>window.editor);assert(await control.isVisible(),id+' disappeared after reload');
  for(const viewport of [{width:375,height:812},{width:844,height:390},{width:320,height:568}]){
   await page.setViewportSize(viewport);await page.evaluate(()=>editor.apply());moved=await control.boundingBox();assert(moved);
   assert(moved.x>=-1&&moved.y>=-1&&moved.x+moved.width<=viewport.width+1&&moved.y+moved.height<=viewport.height+1,id+' outside viewport');
  }
  console.log('PASS '+id+' stays visible after moving, saving, reloading and rotating');
 }
 await fresh();await page.evaluate(()=>editor.open());await page.selectOption('#edit-control','attack');
 await page.locator('#edit-size').evaluate(input=>{input.value='150';input.dispatchEvent(new Event('input',{bubbles:true}));});
 await page.evaluate(()=>editor.close());assert(await page.locator('[data-control=attack]').isVisible());
 console.log('PASS resizing a hidden action enables it after saving');
 await fresh();await page.evaluate(()=>{settings.showButtons=false;editor.open();});
 await page.locator('#edit-opacity').evaluate(input=>{input.value='60';input.dispatchEvent(new Event('input',{bubbles:true}));});
 await page.evaluate(()=>editor.close());assert.equal(await page.evaluate(()=>settings.showButtons),true);assert.equal(await page.evaluate(()=>settings.showDrop),false);
 console.log('PASS editing a control enables button graphics without enabling an untouched drop button');
}finally{await browser.close();await new Promise(resolve=>server.close(resolve));}

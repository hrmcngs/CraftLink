import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import ts from 'typescript';
const catalog=JSON.parse(readFileSync(new URL('../src/i18n/en.json',import.meta.url),'utf8'));
const source=readFileSync(new URL('../src/i18n/index.ts',import.meta.url),'utf8').replace("import english from './en.json';",`const english=${JSON.stringify(catalog)};`);
const compiled=ts.transpileModule(source,{compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.ESNext}}).outputText;
const {initialLanguage,translate}=await import('data:text/javascript;base64,'+Buffer.from(compiled).toString('base64'));
test('saved language overrides browser, unknown languages fall back to English',()=>{
 assert.equal(initialLanguage('en','ja-JP'),'en');assert.equal(initialLanguage('ja','en-US'),'ja');
 assert.equal(initialLanguage(null,'ja-JP'),'ja');assert.equal(initialLanguage('broken','fr-FR'),'en');
});
test('presentation translation preserves whitespace, Japanese, names and URLs',()=>{
 assert.equal(translate('  ジャンプ ','en'),'  Jump ');assert.equal(translate('ジャンプ','ja'),'ジャンプ');
 for(const value of ['hrmcngs','https://example.test/#private-token','Unlisted item'])assert.equal(translate(value,'en'),value);
 assert.ok(Object.values(catalog).every(value=>typeof value==='string'&&value.length>0));
});
test('Minecraft translation catalogs have matching keys',()=>{
 const base=new URL('../../src/main/resources/assets/craftlink/lang/',import.meta.url);
 const ja=JSON.parse(readFileSync(new URL('ja_jp.json',base)));
 const en=JSON.parse(readFileSync(new URL('en_us.json',base)));
 assert.deepEqual(Object.keys(ja).sort(),Object.keys(en).sort());
});

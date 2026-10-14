'use strict';
const assert=require('node:assert/strict');
const test=require('node:test');
require('../app/src/main/assets/core.js');
const {effectDuration,effectiveEffectInterval}=globalThis.FocoCore;
test('limites de duração de 0,1 a 10 segundos',()=>{
 assert.equal(effectDuration(0.1),0.1);
 assert.equal(effectDuration(10),10);
 assert.equal(effectDuration(-2),0.1);
 assert.equal(effectDuration(99),10);
 assert.equal(effectDuration(undefined),3.3);
 assert.equal(effectDuration(Number.NaN),3.3);
 assert.equal(effectDuration(2.67),2.7);
});
test('intervalo mínimo seguro e tempo suficiente para terminar a transição',()=>{
 assert.equal(effectiveEffectInterval(0.1,0.1),8);
 assert.equal(effectiveEffectInterval(8,3.3),8);
 assert.equal(effectiveEffectInterval(8,10),11);
 assert.equal(effectiveEffectInterval(15,10),15);
 assert.equal(effectiveEffectInterval(35,5),30);
 assert.equal(effectiveEffectInterval(undefined,3.3),10);
});
test('controle visual e regras de destaque estão presentes',()=>{
 const fs=require('node:fs');
 const html=fs.readFileSync('app/src/main/assets/index.html','utf8');
 const css=fs.readFileSync('app/src/main/assets/style.css','utf8');
 const js=fs.readFileSync('app/src/main/assets/app.js','utf8');
 assert.match(html,/id="effectDuration"[^>]*min="0.1"[^>]*max="10"[^>]*step="0.1"/);
 assert.match(html,/id="effectDurationNumber"/);
 assert.match(js,/--effect-duration/);
 assert.match(js,/effectiveEffectInterval/);
 assert.match(css,/effect-soft\.effect-quick\.effect-on\{background-color:var\(--reader-bg\)\}/);
});

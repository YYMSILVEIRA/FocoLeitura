'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs');
const js=fs.readFileSync('app/src/main/assets/app.js','utf8');
const kt=fs.readFileSync('app/src/main/java/br/com/focoleitura/MainActivity.kt','utf8');
const html=fs.readFileSync('app/src/main/assets/index.html','utf8');
test('botão acessível e evento de troca',()=>{
 assert.match(html,/id="swapDocument"/);
 assert.match(js,/\$\('swapDocument'\)\.onclick=openAnotherDocument/);
 assert.match(js,/if\(settings\.focusMode\)leaveFocus\(\)/);
});
test('salva leitura anterior antes da troca',()=>{
 assert.match(js,/if\(documentNow\.id!=='demo'\)await saveDoc\(\)/);
});
test('Android usa seletor consistente para PDF ou texto, inclusive cartão SD',()=>{
 assert.match(kt,/Intent\(Intent\.ACTION_OPEN_DOCUMENT\)/);
 assert.match(kt,/type = "\*\/\*"/);
 assert.doesNotMatch(kt,/EXTRA_MIME_TYPES/);
 assert.match(kt,/if \(requestCode != nativeOpenRequest && requestCode != fileChooserRequest\) return/);
 assert.match(kt,/current\?\.onReceiveValue\(null\)/);
 assert.match(kt,/importSerial\.incrementAndGet\(\)/);
});
test('cancelamento mantém o documento atual',()=>{
 assert.match(kt,/if \(uri != null\) importNativeDocument\(uri\)/);
});
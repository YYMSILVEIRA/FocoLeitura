'use strict';
const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
require('../app/src/main/assets/core.js');
const C=globalThis.FocoCore;
const native=fs.readFileSync('app/src/main/java/br/com/focoleitura/MainActivity.kt','utf8');
const ocr=fs.readFileSync('app/src/main/java/br/com/focoleitura/PdfOcr.kt','utf8');
const js=fs.readFileSync('app/src/main/assets/app.js','utf8');
const gradle=fs.readFileSync('app/build.gradle.kts','utf8');
test('PPM ajustam a voz proporcionalmente',()=>{
 assert.equal(C.speechRateForWpm(180),1);
 assert.equal(C.speechRateForWpm(360),2);
 assert.equal(C.speechRateForWpm(60),.5);
 assert.equal(C.speechRateForWpm(900),4);
 assert.ok(C.speechRateForWpm(240,1.5)>C.speechRateForWpm(240));
});
test('feedback se ajusta lentamente e tem limites',()=>{
 assert.ok(C.updatedSpeechFeedback(1,1000,250)>1);
 assert.ok(C.updatedSpeechFeedback(1,100,1000)<1);
 assert.equal(C.updatedSpeechFeedback(1,0,100),1);
 assert.equal(C.updatedSpeechFeedback(1,100,0),1);
});
test('nunca avança o texto antes de a voz terminar',()=>{
 assert.match(js,/speechGroupTarget=C\.groupDuration/);
 assert.match(js,/speechFeedback=C\.updatedSpeechFeedback/);
 assert.match(js,/setTimeout\(\(\)=>nextGroup\(generation\),wait\)/);
 assert.match(js,/AndroidTTS\.speak\(text,String\(gen\),speechActiveRate/);
 assert.match(js,/utterance\.rate=speechActiveRate/);
 assert.match(native,/setSpeechRate\(rate\.toFloat\(\)\.coerceIn\(0\.5f, 4f\)\)/);
});
test('OCR em PDFs digitalizados está incluído localmente no Android',()=>{
 assert.match(gradle,/com\.google\.mlkit:text-recognition:16\.0\.1/);
 assert.match(ocr,/TextRecognition\.getClient\(TextRecognizerOptions\.DEFAULT_OPTIONS\)/);
 assert.match(ocr,/Tasks\.await\(recognizer\.process/);
 assert.match(ocr,/PdfRenderer/);
 assert.match(ocr,/PDDocument\.load\(pdf\)/);
 assert.match(native,/PdfOcr\.read\(applicationContext, uri\)/);
 assert.match(js,/window\.FocoNativeImportProgress=/);
});
#!/usr/bin/env python3
"""FocoLeitura 2.1: corrige foco, importação e opt-in de estímulos seguros.
Aplicado ao conteúdo do ZIP legado durante o build no GitHub Actions.
"""
import sys
from pathlib import Path

root=Path(sys.argv[1] if len(sys.argv)>1 else 'projeto')
assets=root/'app/src/main/assets'
activity=root/'app/src/main/java/br/com/focoleitura/MainActivity.kt'

def replace(path, old, new, label):
    original=path.read_text(encoding='utf-8')
    if new in original: print('Already applied:',label);return
    if old not in original:raise RuntimeError(f'{label}: expected base not found in {path}')
    path.write_text(original.replace(old,new,1),encoding='utf-8')
    print('Patched:',label)

js=assets/'app.js'
html=assets/'index.html'
css=assets/'style.css'

replace(js,"function selectView(view){stop(false);", "function selectView(view){stop(false);if(view!=='read'&&settings.focusMode){settings.focusMode=false;$('view-read').classList.remove('focus-only');$('focusExit').hidden=true;storeSettings();}", 'sair do foco ao trocar tela')
replace(js,"$('view-read').classList.toggle('focus-only',!!settings.focusMode);applyEffects();renderWord();", "$('view-read').classList.toggle('focus-only',!!settings.focusMode);$('focusExit').hidden=!settings.focusMode;applyEffects();renderWord();", 'botão de sair visível no modo foco')
replace(js,"window.FocoLoadNativePdf=async(title,text)=>{try{await loadDocument(text,title,'pdf-nativo');notify('PDF extraído offline no Android. Texto pronto.')}", "window.FocoLoadNativePdf=async(title,text)=>{try{await loadDocument(text,title,'pdf-nativo',0,null,true);notify('PDF extraído offline no Android. Revise e escolha o modo de leitura.')}", 'prévia após importar PDF nativo')
replace(js,"window.FocoNativeImportError=msg=>{selectView('import');notify(String(msg))};", "window.FocoNativeImportError=msg=>{selectView('import');notify(String(msg))};\nwindow.FocoLoadNativeText=async(title,text,source)=>{try{const safe=/\\.html?$/i.test(String(title))?escapeHTMLToText(text):text;await loadDocument(safe,String(title).replace(/\\.[^.]+$/,''),source||'texto-nativo',0,null,true);notify('Documento importado. Revise e escolha o modo de leitura.')}catch(e){selectView('import');notify('Erro ao importar: '+e.message)}};", 'ponte nativa de TXT e HTML')
replace(js,"function context(){openModal('Parágrafo completo · consulta',currentParagraphText())}", '''function context(){openModal('Parágrafo completo · consulta',currentParagraphText())}
function leaveFocus(){settings.focusMode=false;applySettings();if(document.fullscreenElement)document.exitFullscreen().catch(()=>{});setStatus('Modo Foco encerrado · controles disponíveis')}
window.FocoHandleBack=()=>{if($('modal').open){closeModal();return true}if(document.fullscreenElement){document.exitFullscreen().catch(()=>{});return true}if(settings.focusMode){leaveFocus();return true}if(!document.getElementById('view-read').classList.contains('visible')){selectView('read');return true}return false};
function showDocumentReview(){
 stop(false);settings.effects='off';settings.effectConsent=false;applySettings();
 const body=document.createElement('div'),note=document.createElement('p');
 note.textContent='Documento: '+documentNow.title+' · '+tokens.length+' palavras. Confira um trecho antes de iniciar a leitura. Nenhum efeito luminoso será ativado sem sua confirmação.';body.append(note);
 const sample=document.createElement('div');sample.className='document-preview';sample.textContent=documentNow.text.slice(0,2800)+(documentNow.text.length>2800?'\\n… prévia limitada aos primeiros 2.800 caracteres. Use Contexto durante a leitura para consultar o texto.':'');body.append(sample);
 const warning=document.createElement('p');warning.className='notice warning';warning.textContent='Atenção: alterações luminosas podem causar desconforto ou crises em pessoas fotossensíveis. O aplicativo NÃO utiliza flashes ou piscadas rápidas; somente transições lentas, sutis e opcionais. Não há garantia absoluta de segurança.';body.append(warning);
 const consent=document.createElement('label');consent.className='check';const box=document.createElement('input');box.type='checkbox';const explain=document.createElement('span');explain.textContent='Li o alerta, conferi o documento e desejo experimentar as transições suaves nesta leitura.';consent.append(box,explain);body.append(consent);
 const actions=document.createElement('div');actions.className='actions';const normal=document.createElement('button');normal.className='primary';normal.textContent='Ler sem efeitos';normal.onclick=()=>{settings.effects='off';settings.effectConsent=false;applySettings();closeModal();selectView('read');setStatus('Pronto para ler · sem estímulos visuais')};
 const gentle=document.createElement('button');gentle.textContent='Ativar transições suaves';gentle.disabled=true;box.onchange=()=>{gentle.disabled=!box.checked};gentle.onclick=()=>{if(!box.checked)return;settings.effects='soft';settings.effectConsent=true;applySettings();closeModal();selectView('read');setStatus('Transições suaves autorizadas somente nesta leitura · toque em reproduzir')};
 actions.append(normal,gentle);body.append(actions);
 openModal('Revisar documento e confirmar modo visual',body);
}
''','revisão de texto e consentimento explícito')
replace(js,"$('effects').onchange=e=>{if(e.target.value!=='off'){if(!settings.effectConsent){e.target.value='off';alert('Leia o aviso e marque o consentimento antes de ativar estímulos.');return}if(!confirm('Você aceita experimentar mudanças muito sutis de aparência, que ainda podem causar desconforto?')){e.target.value='off';return}}settings.effects=e.target.value;storeSettings();applyEffects();if(playing)startEffects()};$('effectConsent').onchange=e=>{settings.effectConsent=e.target.checked;if(!e.target.checked)settings.effects='off';storeSettings();applySettings()};", "$('effects').onchange=e=>{if(e.target.value==='soft'){settings.effects='off';settings.effectConsent=false;applySettings();showDocumentReview();return}settings.effects='off';settings.effectConsent=false;applySettings()};$('effectConsent').onchange=e=>{if(!e.target.checked){settings.effectConsent=false;settings.effects='off';applySettings()}else{e.target.checked=false;showDocumentReview()}};", 'consentimento revisado antes de iniciar efeitos')
replace(js,"async function loadDocument(text,title='Sem título',source='texto',at=0,old){", "async function loadDocument(text,title='Sem título',source='texto',at=0,old,preview=false){", 'parâmetro de prévia na importação')
replace(js,"selectView('read');setStatus('Documento carregado · toque em reproduzir')}", "selectView('read');setStatus('Documento carregado · toque em reproduzir');if(preview)showDocumentReview()}", 'apresentar prévia do documento')
replace(js,"btn.onclick=()=>loadDocument(item.text,item.title,item.source,item.index||0,item);", "btn.onclick=()=>loadDocument(item.text,item.title,item.source,item.index||0,item,true);", 'prévia quando abre item da biblioteca')
replace(js,"await loadDocument(text,file.name.replace(/\\.[^/.]+$/,''),'arquivo');", "await loadDocument(text,file.name.replace(/\\.[^/.]+$/,''),'arquivo',0,null,true);", 'prévia após importar arquivo')
replace(js,"$('focus').onclick=()=>togglePlay();", "$('focus').onclick=()=>togglePlay();$('focusExit').onclick=leaveFocus;$('reviewDocument').onclick=showDocumentReview;$('nativeOpenFile').hidden=!globalThis.AndroidFiles;$('nativeOpenFile').onclick=()=>{if(globalThis.AndroidFiles)AndroidFiles.openDocument()};", 'controles de foco, revisão e abrir nativo')
replace(js,"await loadDocument($('paste').value,$('newTitle').value||'Texto colado','texto')", "await loadDocument($('paste').value,$('newTitle').value||'Texto colado','texto',0,null,true)", 'prévia após colar texto')
replace(js,"$('example').onclick=()=>loadDocument(SAMPLE,'Exemplo de aprendizagem','demo');", "$('example').onclick=()=>loadDocument(SAMPLE,'Exemplo de aprendizagem','demo',0,null,true);", 'prévia exemplo')
replace(js,"window.FocoLeituraTest={getState:", "window.FocoLeituraTest={showDocumentReview,leaveFocus,getState:", 'test hooks')

replace(html,'<div class="reading" id="reading" role="region" aria-label="Área de leitura">','<div class="reading" id="reading" role="region" aria-label="Área de leitura"><button id="focusExit" class="focus-exit" hidden aria-label="Sair do Modo Foco">✕ Sair do Foco</button>', 'botão visível acima do conteúdo')
replace(html,'<button id="fullscreen" title="Tela cheia">⛶</button></div>', '<button id="fullscreen" title="Tela cheia">⛶</button><button id="reviewDocument" title="Revisar o documento e escolher estímulos">▤ Revisar / luz</button></div>', 'atalho de prévia no leitor')
replace(html,'<label class="file-card">▤ Escolher PDF ou texto<input id="file" type="file" accept=".pdf,.txt,.md,.html,.htm,application/pdf,text/plain,text/html"></label>', '<button id="nativeOpenFile" class="primary" hidden>▤ Abrir PDF ou texto pelo Android</button><label class="file-card">▤ Escolher PDF ou texto<input id="file" type="file" accept=".pdf,.txt,.md,.html,.htm,application/pdf,text/plain,text/html"></label>', 'botão nativo de abrir documento')
replace(html,'<label class="check"><input id="effectConsent" type="checkbox"> Li o aviso e desejo experimentar os efeitos suaves</label>', '<label class="check"><input id="effectConsent" type="checkbox"> Autorizei os efeitos suaves nesta leitura (requer revisão do documento)</label>', 'legenda do consentimento')
replace(html,'<p class="hint">Por segurança, não há opção de flashes rápidos, estroboscópio ou alternância abrupta de alto contraste.', '<p class="hint">O modo visual dinâmico usa transições suaves e lentas, não flashes piscantes. Revise o documento para confirmar antes de cada leitura. Por segurança, não há opção de flashes rápidos, estroboscópio ou alternância abrupta de alto contraste.', 'descrição precisa efeitos')

style=css.read_text(encoding='utf-8')
addition='''\n/* Ajustes 2.1: saída sempre acessível mesmo em Modo Foco/tela cheia. */
[hidden]{display:none!important}
.focus-exit{position:absolute;top:12px;right:12px;z-index:20;background:#172a45;color:#fff;border:2px solid #80d5e4;box-shadow:0 1px 8px #0008;min-width:118px;min-height:48px}
.reading:fullscreen .focus-exit{top:max(12px,env(safe-area-inset-top));right:max(12px,env(safe-area-inset-right))}
.document-preview{max-height:33dvh;overflow:auto;white-space:pre-wrap;overflow-wrap:anywhere;user-select:text;background:#0c1627;padding:13px;border:1px solid #405573;border-radius:12px;line-height:1.55;color:#edf5ff}
.effect-soft{transition:background-color 3.3s ease}
.effect-soft.effect-on{background-color:color-mix(in srgb,var(--reader-bg) 90%,var(--reader-accent) 10%)}
.effect-soft #wordText{transition:color 3.3s ease,text-shadow 3.3s ease}
.effect-soft.effect-on #wordText{color:color-mix(in srgb,var(--reader-text) 90%,var(--reader-accent) 10%);text-shadow:0 0 11px color-mix(in srgb,var(--reader-accent) 18%,transparent)}
'''
if addition.strip() not in style:css.write_text(style+addition,encoding='utf-8')

replace(activity,'    private val backupSaveRequest = 1032','    private val backupSaveRequest = 1032\n    private val nativeOpenRequest = 1033', 'código da seleção nativa')
replace(activity,'        if (requestCode != fileChooserRequest) return', '''        if (requestCode == nativeOpenRequest) {
            if (resultCode == RESULT_OK && data?.data != null) importNativeDocument(data.data!!)
            return
        }
        if (requestCode != fileChooserRequest) return''', 'retorno do seletor nativo')
replace(activity,'    private fun extractPdfOffline(uri: Uri) {', '''    private fun importNativeDocument(uri: Uri) {
        Thread {
            try {
                var filename = "Documento"
                contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) filename = cursor.getString(0) ?: filename
                }
                val mime = contentResolver.getType(uri).orEmpty()
                if (mime == "application/pdf" || filename.lowercase(Locale.ROOT).endsWith(".pdf")) {
                    extractPdfOffline(uri)
                    return@Thread
                }
                if (!Regex("(?i).*\\\\.(txt|md|markdown|html|htm)$").matches(filename) && !mime.startsWith("text/")) {
                    throw IllegalArgumentException("Formato não suportado. Selecione PDF, TXT, MD ou HTML.")
                }
                val bytes = contentResolver.openInputStream(uri)?.use { input -> input.readBytes() }
                    ?: throw IllegalArgumentException("Não foi possível abrir arquivo")
                if (bytes.size > 4_000_000) throw IllegalArgumentException("Texto maior que 4 MB; divida o arquivo.")
                val safeTitle = JSONObject.quote(filename)
                val safeText = JSONObject.quote(String(bytes, Charsets.UTF_8))
                runOnUiThread { webView.evaluateJavascript("window.FocoLoadNativeText?.($safeTitle,$safeText,'texto-nativo')", null) }
            } catch (e: Exception) {
                val message = JSONObject.quote("Erro ao abrir documento: " + (e.message ?: "Arquivo inválido"))
                runOnUiThread { webView.evaluateJavascript("window.FocoNativeImportError?.($message)", null) }
            }
        }.start()
    }

    private fun extractPdfOffline(uri: Uri) {''', 'importador nativo offline de PDF e texto')
replace(activity,'    private inner class ReaderFileBridge {', '''    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        webView.evaluateJavascript("window.FocoHandleBack?.() === true") { handled ->
            if (handled != "true") finish()
        }
    }

    private inner class ReaderFileBridge {
        @JavascriptInterface
        fun openDocument() {
            runOnUiThread {
                try {
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                        putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/pdf", "text/plain", "text/html", "text/markdown", "application/octet-stream"))
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    @Suppress("DEPRECATION")
                    startActivityForResult(intent, nativeOpenRequest)
                } catch (e: Exception) {
                    val message = JSONObject.quote("Não foi possível abrir o seletor Android: " + e.message)
                    webView.evaluateJavascript("window.FocoNativeImportError?.($message)", null)
                }
            }
        }
''', 'ponte abrir documento e voltar Android')

print('FocoLeitura patch aplicado:', root)

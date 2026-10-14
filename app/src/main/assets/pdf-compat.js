/* Extrator de compatibilidade: preservado da versão 1.
 * PDF.js é recomendado para documentos complexos. Este extrator cobre alguns PDFs simples, não OCR. */
 function normalizePdfText(t){return t.replace(/(?<=\p{L})-\s*\n\s*(?=\p{L})/gu,'').replace(/\u0000/g,'').replace(/[ \t]{2,}/g,' ').replace(/\n{3,}/g,'\n\n').trim()}
 function escapeHTMLToText(source){const doc=new DOMParser().parseFromString(source,'text/html');doc.querySelectorAll('script,style,noscript,img,svg,video,audio').forEach(x=>x.remove());return doc.body?.textContent||''}
 // Modo web totalmente offline: apenas extrator básico local.
 // No APK Android a importação de PDFs usa PDFBox-Android, também offline.
 async function readPdf(file){const text=normalizePdfText(await readSimplePDF(await file.arrayBuffer()));if(!text||text.length<4)throw Error('PDF sem texto extraível pelo leitor web básico: pode ser escaneado ou complexo. O projeto Android usa PDFBox offline; OCR permanece pendente.');return {text,pages:null,engine:'extrator local offline'}}
 async function handleFile(file){notify('Lendo o arquivo localmente...');try{if(file.size>50*1024*1024)throw Error('Arquivo muito grande: limite de 50 MB nesta versão.');let text,pages=null,engine='texto';const lower=file.name.toLowerCase();if(lower.endsWith('.pdf')||file.type==='application/pdf'){const r=await readPdf(file);text=r.text;pages=r.pages;engine=r.engine}else if(/\.(txt|md|markdown|html|htm)$/i.test(lower)||file.type.startsWith('text/')){const raw=await file.text();text=/\.html?$/i.test(lower)||file.type==='text/html'?escapeHTMLToText(raw):raw}else throw Error('Formato não suportado. Escolha PDF, TXT, MD ou HTML.');if(!text.trim())throw Error('O arquivo não tem texto selecionável. PDF digitalizado exige OCR.');const title=file.name.replace(/\.[^.]+$/,'');loadDocument(text,title,'arquivo');notify(`Importado: ${tokens.length} palavras${pages?` em ${pages} páginas`:''} (${engine}). Imagens não aparecem durante o RSVP.`)}
 catch(e){notify(e.message||'Erro ao importar arquivo.')}}
 // Leitor simplificado totalmente local para PDFs de texto comuns, caso o PDF.js não esteja acessível.
 // Não substitui o PDF.js: PDFs escaneados, fontes CID e documentos complexos podem exigir outro extrator.
 async function readSimplePDF(buffer){
  const bytes=new Uint8Array(buffer);
  // Latin-1 binário sem reinterpretar caracteres de controle como Windows-1252.
  const binStr=u=>{const batch=16384;let str='';for(let i=0;i<u.length;i+=batch)str+=String.fromCharCode(...u.subarray(i,i+batch));return str};
  const src=binStr(bytes);
  if(!src.startsWith('%PDF-'))throw Error('Arquivo não é PDF');
  const objects=new Map();const objRe=/(\d+)\s+\d+\s+obj\b([\s\S]*?)\bendobj\b/g;let m;
  while((m=objRe.exec(src)))objects.set(Number(m[1]),m[2]);
  const ascii85=input=>{
    let data=input.replace(/^[\s<]*~/,'').replace(/~>[\s\S]*$/,'');const result=[];let group=[];
    const flush=(padding=0)=>{while(group.length<5)group.push(117);let n=0;for(const c of group)n=n*85+(c-33);const q=[Math.floor(n/16777216)&255,Math.floor(n/65536)&255,Math.floor(n/256)&255,n&255];result.push(...q.slice(0,4-padding));group=[]};
    for(const ch of data){const x=ch.charCodeAt(0);if(/\s/.test(ch))continue;if(ch==='z'&&group.length===0){result.push(0,0,0,0);continue}if(x<33||x>117)continue;group.push(x);if(group.length===5)flush()}
    if(group.length>1)flush(5-group.length);return new Uint8Array(result)
  };
  const decodeStream=async s=>{
    const at=s.search(/\bstream\r?\n/);if(at<0)return s;
    const marker=s.slice(at).match(/^stream\r?\n/);if(!marker)return s;
    const start=at+marker[0].length;const dict=s.slice(0,at);
    const len=dict.match(/\/Length\s+(\d+)\b/);
    let raw=len?s.slice(start,start+Number(len[1])):s.slice(start,s.lastIndexOf('endstream')).replace(/[\r\n]+$/,'');
    let arr=Uint8Array.from(raw,ch=>ch.charCodeAt(0)&255);
    const filters=(dict.match(/\/Filter\s*\[([^\]]+)\]/)||[])[1]?.match(/\/[a-zA-Z0-9]+/g)||dict.match(/\/Filter\s*(\/[a-zA-Z0-9]+)/)?.slice(1)||[];
    for(const filter of filters){if(filter==='/ASCII85Decode'||filter==='/A85')arr=ascii85(binStr(arr));
      else if(filter==='/FlateDecode'||filter==='/Fl'){if(!('DecompressionStream' in window))throw Error('Sem descompactação');arr=new Uint8Array(await new Response(new Blob([arr]).stream().pipeThrough(new DecompressionStream('deflate'))).arrayBuffer())}
      else throw Error('Filtro PDF não suportado: '+filter)}
    return binStr(arr)
  };
  const unicodeMap=new Map();for(const obj of objects.values()){if(!/\/FlateDecode|beginbfchar|beginbfrange|\/CMap/.test(obj))continue;let cmap;try{cmap=await decodeStream(obj)}catch(_){continue}if(!/beginbfchar|beginbfrange/.test(cmap))continue;const bfc=/beginbfchar([\s\S]*?)endbfchar/g;let z;while((z=bfc.exec(cmap))){for(const e of z[1].matchAll(/<([0-9a-fA-F]+)>\s*<([0-9a-fA-F]+)>/g))unicodeMap.set(e[1].toUpperCase(),hexToUnicode(e[2]))}const bfr=/beginbfrange([\s\S]*?)endbfrange/g;while((z=bfr.exec(cmap))){for(const e of z[1].matchAll(/<([0-9a-fA-F]+)>\s*<([0-9a-fA-F]+)>\s*<([0-9a-fA-F]+)>/g)){const st=parseInt(e[1],16),end=parseInt(e[2],16),base=parseInt(e[3],16);if(end-st>1000)continue;for(let k=st;k<=end;k++){const key=k.toString(16).toUpperCase().padStart(e[1].length,'0');unicodeMap.set(key,hexToUnicode((base+k-st).toString(16).padStart(e[3].length,'0')))}}}}
  function bytesToString(encoded){let o='';if(unicodeMap.size){const unit=Array.from(unicodeMap.keys()).some(k=>k.length===4)?2:1;for(let i=0;i<encoded.length;i+=unit){const key=Array.from(encoded.slice(i,i+unit),b=>b.toString(16).padStart(2,'0').toUpperCase()).join('');o+=unicodeMap.get(key)||String.fromCharCode(...encoded.slice(i,i+unit))}return o}return new TextDecoder('windows-1252').decode(encoded)}
  function literalToBytes(raw){let ar=[];for(let i=0;i<raw.length;i++){let c=raw.charCodeAt(i)&255;if(c!==92){ar.push(c);continue}i++;let t=raw[i];if(t===undefined)break;const map={'n':10,'r':13,'t':9,'b':8,'f':12};if(t in map)ar.push(map[t]);else if(/[0-7]/.test(t)){let num=t;for(let j=0;j<2&&/[0-7]/.test(raw[i+1]||'');j++)num+=raw[++i];ar.push(parseInt(num,8)&255)}else if(t==='\r'||t==='\n'){if(t==='\r'&&raw[i+1]==='\n')i++}else ar.push(t.charCodeAt(0)&255)}return new Uint8Array(ar)}
  function decodePiece(s){if(s.startsWith('('))return bytesToString(literalToBytes(s.slice(1,-1)));if(/^<[a-fA-F0-9\s]*>$/.test(s)){let h=s.slice(1,-1).replace(/\s/g,'');if(h.length%2)h+='0';const a=new Uint8Array(h.length/2);for(let i=0;i<a.length;i++)a[i]=parseInt(h.slice(2*i,2*i+2),16);return bytesToString(a)}return ''}
  const extracted=[];const contentsRefs=[];for(const obj of objects.values()){if(!/\/Type\s*\/Page\b/.test(obj))continue;const cref=obj.match(/\/Contents\s+(\d+)\s+\d+\s+R/)||obj.match(/\/Contents\s*\[\s*(\d+)\s+\d+\s+R/);if(cref)contentsRefs.push(+cref[1])}
  if(!contentsRefs.length){for(const [id,obj] of objects)if(/stream/.test(obj)&&/\bBT\b|\/FlateDecode/.test(obj))contentsRefs.push(id)}
  for(const id of contentsRefs){const raw=objects.get(id);if(!raw)continue;let stream;try{stream=await decodeStream(raw)}catch(_){continue}const chunks=[...stream.matchAll(/\bBT\b([\s\S]*?)\bET\b/g)];for(const c of chunks){const block=c[1];const operators=/(\((?:\\[\s\S]|[^\\)])*\)|<[0-9A-Fa-f\s]*>|\[(?:[^\[\]]|\([^)]*\))*\])\s*(Tj|TJ|\'|\")/g;let q;let text='';while((q=operators.exec(block))){if(q[2]==='TJ'){let arr=[...q[1].matchAll(/\((?:\\[\s\S]|[^\\)])*\)|<[0-9A-Fa-f\s]*>|-?\d+(?:\.\d+)?/g)].map(x=>x[0]);for(const part of arr){if(/^-?\d/.test(part)){if(+part < -160)text+=' ';}else text+=decodePiece(part)}}else text+=decodePiece(q[1]);text+=' '}if(text.trim())extracted.push(text.trim())}}
  return extracted.join('\n')
 }
 function hexToUnicode(s){if(s.length%4)return String.fromCharCode(parseInt(s,16));const buf=new Uint8Array(s.length/2);for(let i=0;i<buf.length;i++)buf[i]=parseInt(s.slice(i*2,i*2+2),16);return new TextDecoder('utf-16be').decode(buf)}


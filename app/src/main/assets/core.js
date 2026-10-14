/* FocoLeitura v2 — funções puras reutilizáveis/testáveis; sem serviços remotos. */
(function(root){
'use strict';
const RANGE = {min:60,max:900};
function tokenizeText(text){
 const items=[]; let prev=0,paragraph=0; const re=/\S+/gu; let m;
 while((m=re.exec(text||''))!==null){
   const sep=(text||'').slice(prev,m.index);
   if(/\n\s*\n/.test(sep))paragraph++;
   items.push({text:m[0],start:m.index,end:m.index+m[0].length,paragraph});prev=m.index+m[0].length;
 }
 return items;
}
function clamp(n,min,max){return Math.min(max,Math.max(min,Number(n)||min))}
function groupFor(tokens,index,count){const size=clamp(count,1,10);const start=Math.floor(Math.max(0,index)/size)*size;return {start,end:Math.min(tokens.length,start+size),items:tokens.slice(start,start+size)};}
function groupText(group){return group.items.map(t=>t.text).join(' ')}
function sentenceStart(tokens,index){let i=clamp(index,0,Math.max(0,tokens.length-1));while(i>0&&!/[.!?…]["”’)]*$/.test(tokens[i-1].text)&&tokens[i-1].paragraph===tokens[i].paragraph)i--;return i}
function paragraphStart(tokens,index){let i=clamp(index,0,Math.max(0,tokens.length-1));while(i>0&&tokens[i-1].paragraph===tokens[i].paragraph)i--;return i}
function pauseFactor(word,mode){let value={off:0,light:.22,moderate:.55,intense:1}[mode]||0;return /[.!?…]["”’)]*$/.test(word)?1+value:/[,;:]["”’)]*$/.test(word)?1+value*.5:1}
function groupDuration(tokens,start,end,wpm,mode){const base=60000/clamp(wpm,RANGE.min,RANGE.max);let total=0;
 for(let i=start;i<end;i++){const token=tokens[i];total+=base*pauseFactor(token.text,mode);if(i+1<tokens.length&&tokens[i+1].paragraph>token.paragraph)total+=base*({off:0,light:.5,moderate:1,intense:1.8}[mode]||0)}
 return total;
}
function wordOffsets(items){let off=0;return items.map((w,i)=>{const start=off;off+=w.text.length+(i===items.length-1?0:1);return {start,end:start+w.text.length}})}
function offsetWordIndex(offsets,char){const ix=offsets.findIndex(o=>char>=o.start&&char<o.end);if(ix>=0)return ix;const next=offsets.findIndex(o=>char<o.end);return next<0?Math.max(0,offsets.length-1):next;}
function suggestSpeed(wpm,selfRating){const rating=clamp(selfRating,1,5);const factor=rating<=2?.9:rating===5?1.05:1;return Math.round(clamp(Math.round(wpm*factor/10)*10,60,900));}
root.FocoCore={tokenizeText,groupFor,groupText,sentenceStart,paragraphStart,groupDuration,wordOffsets,offsetWordIndex,clamp,suggestSpeed};
})(typeof globalThis!=='undefined'?globalThis:this);

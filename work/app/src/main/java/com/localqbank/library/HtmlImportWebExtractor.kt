package com.localqbank.library

/** WebView-side extraction script kept out of the Activity so the Activity remains orchestration-only. */
object HtmlImportWebExtractor {
    const val EXTRACT_JS = """
(function(){
 function clean(v){if(v==null)return '';if(typeof v==='string')return v;try{return JSON.stringify(v)}catch(e){return String(v)}}
 function img(v,base){let x=clean(v).trim();if(!x)return '';if(x.startsWith('data:image'))return x;try{let b=(base&&base.baseURI)||'';if(!b||b.startsWith('about:blank'))b=document.baseURI;return new URL(x,b).href}catch(e){return x}}
 function imgs(v,base){let out=[];if(Array.isArray(v))v.forEach(x=>{let u=img(x,base);if(u)out.push(u)});else if(typeof v==='string'){let re=/<img[^>]+(?:src|data-src|data-original|data-lazy-src)=['\"]([^'\"]+)['\"]/ig,m;while((m=re.exec(v)))out.push(img(m[1],base));}return [...new Set(out)]}
 function balanced(s,start){let d=0,q=null,e=false;for(let i=start;i<s.length;i++){let c=s[i];if(q){if(e)e=false;else if(c==='\\')e=true;else if(c===q)q=null;continue;}if(c==='"'||c==="'")q=c;else if(c==='['||c==='{')d++;else if(c===']'||c==='}')if(--d===0)return s.slice(start,i+1);}return null;}
 function qlike(o){return o&&typeof o==='object'&&(o.text!=null||o.question!=null||o.stem!=null||o.question_text!=null||o.questionText!=null)&&(o.options!=null||o.choices!=null||o.answers!=null||o.option_list!=null||o.answer!=null||o.ans!=null||o.correct_answer!=null||o.correctAnswer!=null||o.explanation!=null||o.solution!=null||o.expl!=null)}
 function normQ(q,base){
   let src=clean(q.text??q.question??q.stem??q.question_text??q.questionText??q.raw_text??q.title??'');
   let opts=[];let os=q.options??q.choices??q.answers??q.option_list??q.optionList??{};
   if(Array.isArray(os))os.forEach((o,i)=>opts.push({label:o&&typeof o==='object'?(o.label??o.key??o.id??String.fromCharCode(65+i)):String.fromCharCode(65+i),text:clean(o&&typeof o==='object'?(o.text??o.value??o.option??o.html??o.label??''):o),correct:!!(o&&typeof o==='object'&&(o.correct??o.is_correct??o.isCorrect??o.correct_option))}));
   else if(os&&typeof os==='object')Object.keys(os).forEach(k=>{let v=os[k];opts.push({label:k,text:clean(v&&typeof v==='object'?(v.text??v.value??v.option??v.html??''):v),correct:!!(v&&typeof v==='object'&&(v.correct??v.is_correct??v.isCorrect))})});
   let ans=q.correct_answer??q.correctAnswer??q.answer??q.ans??q.correct_option??q.correctOption??q.correct??null;
   if(typeof ans==='object')ans=null;
   let ansText=clean(ans).trim();
   // Many QBanks store the answer as "B" or "B. Wood's lamp" rather than a per-option flag.
   if(ansText)opts=opts.map(o=>{let l=String(o.label||'').trim();let hit=ansText.replace(/\s+/g,' ').toLowerCase()===l.toLowerCase()||ansText.toLowerCase().startsWith(l.toLowerCase()+'.')||ansText.toLowerCase().startsWith(l.toLowerCase()+')');return {...o,correct:o.correct||hit}});
   let ex=clean(q.explanation??q.solution??q.expl??q.exp??q.rationale??q.answer_explanation??'');
   return{id:q.id??q.question_id??q.questionId??q.uid??null,text:src,raw_text:clean(q.raw_text??src),correct_answer:ans,explanation:ex,bot:clean(q.bot??''),video:clean(q.video??q.video_url??q.videoUrl??''),audio:clean(q.audio??q.audio_url??q.audioUrl??''),options:opts,question_images:imgs(q.question_images??q.images??q.img??q.image??q.questionImage??q.question_image??'',base).concat(imgs(src,base)),explanation_images:imgs(q.explanation_images??q.explanationImage??q.explanation_images_urls??'',base).concat(imgs(ex,base))}
 }
 let found=[],seenTests=new Set(),seenQuestions=new Set();
 function addTest(title,questions,path,base){
   let cleanTitle=(title||'Imported Section').trim().replace(/\s+/g,' ').slice(0,150)||'Imported Section';
   let qs=[];
   questions.forEach(q0=>{let q=normQ(q0,base);if(!q.text.trim()||q.options.length<2)return;let key=q.id?('id:'+cleanTitle+'|'+String(q.id)):('txt:'+cleanTitle+'|'+q.text.trim().toLowerCase().slice(0,500));if(seenQuestions.has(key))return;seenQuestions.add(key);qs.push(q)});
   if(!qs.length)return;
   let key=cleanTitle+'|'+qs.map(x=>x.id||x.text).join('|').slice(0,400);if(seenTests.has(key))return;seenTests.add(key);found.push({title:cleanTitle,path:path||'',questions:qs});
 }
 function walk(v,path,base){
   if(v==null)return;
   if(Array.isArray(v)){let qs=v.filter(qlike);if(qs.length)addTest(path.filter(Boolean).slice(-1)[0]||'Imported Section',qs,'',base);v.forEach(x=>walk(x,path,base));return}
   if(typeof v==='object'){
     let title=v.title??v.name??v.subject??v.lesson??v.section??v.chapter??null;let next=title?[...path,String(title)]:path;
     Object.keys(v).forEach(k=>{if(!['options','choices','answers','option_list','optionList'].includes(k))walk(v[k],next,base)})
   }
 }
 function extractValue(v,path,base){try{if(v&&typeof v==='object')walk(v,path,base)}catch(e){}}
 function decodeEntities(s){try{let ta=document.createElement('textarea');ta.innerHTML=s;return ta.value}catch(e){return s}}
 function extractScripts(doc,prefix){
   if(!doc)return;
   let re=/(?:(?:const|let|var)\s+)?(?:window\.)?([A-Za-z_$][\w$]*)\s*=\s*/g;
   doc.querySelectorAll('script').forEach(s=>{
     let src=s.textContent||'';re.lastIndex=0;let m;
     while((m=re.exec(src))){
       let p=m.index+m[0].length;while(p<src.length&&/\s/.test(src[p]))p++;
       if(src[p]!=='['&&src[p]!=='{')continue;
       let ex=balanced(src,p);if(!ex)continue;
       try{let value;try{value=JSON.parse(ex)}catch(_e){value=Function('return ('+ex+')')()};extractValue(value,[prefix||m[1]],doc)}catch(e){}
     }
     let typ=(s.type||'').toLowerCase();
     if(typ.includes('json')){try{extractValue(JSON.parse(src),[prefix||s.id||'Imported JSON'],doc)}catch(e){}}
   });
 }
 function textOf(el){return (el?.innerText||el?.textContent||'').replace(/\s+/g,' ').trim()}
 function firstText(root,selectors){for(let sel of selectors){let e=root.querySelector(sel);if(e){let t=textOf(e);if(t)return t}}return ''}
 function domFallback(doc,prefix){
   if(!doc)return;
   let selectors=['[data-question]','.question-card','.mcq-question','.quiz-question','.question-container','.question-block','.question-item','li.question','article.question'];
   let containers=[];selectors.forEach(sel=>doc.querySelectorAll(sel).forEach(e=>containers.push(e)));
   let unique=[...new Set(containers)];let qs=[];
   unique.forEach((el,idx)=>{
     let stem=el.getAttribute('data-question-text')||firstText(el,['[data-question-text]','.question-text','.question-title','.stem','.question-stem','.q-text']);
     if(!stem){let h=el.querySelector('h1,h2,h3,h4,h5');if(h)stem=textOf(h)}
     if(!stem){let clone=el.cloneNode(true);clone.querySelectorAll('label,.option,.choice,button,input,select,textarea,.answer,.explanation,.solution').forEach(x=>x.remove());stem=textOf(clone)}
     let options=[];let labels=el.querySelectorAll('label');
     if(labels.length){labels.forEach((lab,i)=>{let input=lab.querySelector('input');let txt=textOf(lab);if(txt)options.push({label:String.fromCharCode(65+i),text:txt,correct:!!(lab.matches('.correct,[data-correct="true"]')||input?.getAttribute('data-correct')==='true')})})}
     if(!options.length){el.querySelectorAll('[data-option],.option,.choice,.answer-option,.mcq-option,.option-item').forEach((o,i)=>{let txt=textOf(o);if(txt)options.push({label:o.getAttribute('data-label')||String.fromCharCode(65+i),text:txt,correct:o.matches('.correct,[data-correct="true"]')||o.getAttribute('data-correct')==='true'})})}
     if(!options.length){let lis=el.querySelectorAll('li');lis.forEach((o,i)=>{let txt=textOf(o);if(txt&&txt.length<500)options.push({label:String.fromCharCode(65+i),text:txt,correct:o.matches('.correct,[data-correct="true"]')})})}
     let ans=el.getAttribute('data-correct')||el.getAttribute('data-answer')||null;
     let ex=firstText(el,['.explanation','.solution','.answer-explanation','[data-explanation]']);
     let section=firstText(doc,['h1','h2','h3','[data-section-title]'])||prefix||'Imported Section';
     if(stem&&options.length>=2)qs.push({id:el.getAttribute('data-id')||el.id||String(idx+1),text:stem,correct_answer:ans,explanation:ex,options:options,question_images:[...el.querySelectorAll('img')].map(x=>img(x.getAttribute('src')||x.getAttribute('data-src')||'',doc))});
   });
   if(qs.length)addTest(section,qs,'dom',doc);
 }
 let processedDocs=new Set();
 function processDocument(doc,prefix){
   if(!doc||processedDocs.has(doc))return;processedDocs.add(doc);
   let before=found.length;
   extractScripts(doc,prefix);
   if(found.length===before)domFallback(doc,prefix);
   doc.querySelectorAll('iframe').forEach((f,i)=>{
     try{
       let child=f.contentDocument;
       let label=(prefix?prefix+' / ':'')+(f.title||f.getAttribute('title')||child?.title||('Section '+(i+1)));
       if(child)processDocument(child,label);
       let raw=f.getAttribute('srcdoc')||f.srcdoc||'';
       if(raw){
         // Combined QBank exports store the embedded quiz as HTML-entity encoded srcdoc.
         // Decode it before parsing so the nested `questions = [...]` data becomes visible.
         let decoded=(raw.indexOf('&lt;')>=0||raw.indexOf('&quot;')>=0||raw.indexOf('&#')>=0)?decodeEntities(raw):raw;
         let parsed=new DOMParser().parseFromString(decoded,'text/html');
         try{let be=parsed.createElement('base');be.href=document.baseURI;parsed.head.prepend(be)}catch(_e){}
         processDocument(parsed,label);
       }
     }catch(e){
       try{
         let raw=f.getAttribute('srcdoc')||f.srcdoc||'';
         if(raw){let decoded=(raw.indexOf('&lt;')>=0||raw.indexOf('&quot;')>=0||raw.indexOf('&#')>=0)?decodeEntities(raw):raw;let parsed=new DOMParser().parseFromString(decoded,'text/html');try{let be=parsed.createElement('base');be.href=document.baseURI;parsed.head.prepend(be)}catch(_e){}processDocument(parsed,prefix||'Imported Section')}
       }catch(_e){}
     }
   });
 }
 processDocument(document,document.title||'Imported Section');
 // Give srcdoc iframes one extra chance to become available on Android WebView.
 setTimeout(function(){processDocument(document,document.title||'Imported Section');AndroidImport.receive(JSON.stringify({fileName:(document.title||'Imported QBank.html').trim(),tests:found.filter(t=>t.questions.length)}));},250);
})();"""
}

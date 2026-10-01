'use strict';
(() => {
 const byId=id=>document.getElementById(id);
 const make=(tag,text,cls)=>{
  const n=document.createElement(tag);
  if(text!==undefined)n.textContent=String(text);
  if(cls)n.className=cls;
  return n;
 };
 const allowed=()=>typeof tieneRol==='function'&&
  (tieneRol('ADMIN')||tieneRol('RECURSOS_HUMANOS'));
 const admin=()=>typeof tieneRol==='function'&&(tieneRol('ADMIN')||tieneRol('IT_ENGINEER_VALIDATOR'));
 const kinds={
  computer:new Set(['desktop','laptop','mini pc','mini tower','workstation']),
  phone:new Set(['phone','telephone'])
 };

 async function request(url,options={}){
  const response=await fetch(url,{
   credentials:'same-origin',cache:'no-store',...options
  });
  if(response.status===401||response.redirected)
   throw new Error('Session expired. Sign in again.');
  let data;
  try{data=await response.json();}catch{
   throw new Error('Unexpected response. Refresh your session.');
  }
  if(!response.ok){
   const error=new Error(data?.detail||data?.message||`HTTP ${response.status}`);
   error.status=response.status;throw error;
  }
  return data;
 }

 function reference(values,id){
  return [
   values['Asset ID']?'Asset ID: '+values['Asset ID']:'',
   values.Name||'',values.Model||''
  ].filter(Boolean).join(' | ')||('Inventory record '+id);
 }

 async function select(hit,kind){
  if(!allowed())throw new Error('Your profile cannot select inventory assets.');
  const employee=byId('idf')?.value||'';
  const fresh=await request('/api/bulk/assets/record/'+encodeURIComponent(hit.id));

  if((byId('idf')?.value||'')!==employee)
   throw new Error('The employee changed. Search again.');

  const type=String(fresh.values.DevType||'').trim().toLowerCase();
  if(!kinds[kind]?.has(type))
   throw new Error('This equipment does not correspond to the selected field.');

  const value=reference(fresh.values,fresh.id);
  if(value.length>250)
   throw new Error('The reference exceeds 250 characters. Enter a shorter reference manually.');

  const input=byId(kind==='computer'?'computer_details':'phone_details');
  const check=byId(kind==='computer'?'eq_computer':'eq_phone');
  if(!input||!check)throw new Error('The offboarding form is unavailable.');

  check.checked=true;input.value=value;
  input.classList.add('auto-filled');
  input.dataset.inventoryRecord=String(fresh.id);
  if(typeof actualizarCamposAsignacion==='function')actualizarCamposAsignacion();
  const box=byId(kind==='computer'?'computer_inv_result':'phone_inv_result');
  if(box)box.replaceChildren();
  input.dispatchEvent(new Event('change',{bubbles:true}));
  return fresh;
 }

 window.AssetCatalogR6={select};

 const css=make('style');
 css.textContent=`
 .r6-results{border:1px solid #ddd;border-radius:8px;background:white;overflow:hidden;margin-top:8px}
 .r6-hit{padding:12px;border-bottom:1px solid #eee}
 .r6-hit:last-child{border-bottom:0}
 .r6-hit-title{font-weight:700;font-size:13px}
 .r6-muted{color:#707070;font-size:12px;margin-top:4px}
 .r6-hit-actions{display:flex;gap:8px;align-items:center;margin-top:6px;flex-wrap:wrap}
 .r6-hit-actions button{margin:0}
 .r6-original{display:grid;grid-template-columns:repeat(auto-fit,minmax(220px,1fr));gap:8px;margin-top:10px;padding:10px;background:#f5f5f5}
 .r6-original div{white-space:pre-wrap;overflow-wrap:anywhere;font-size:12px}
 .r6-error{color:#b91c1c;font-size:13px;white-space:pre-wrap}
 #r6-update-dialog{
  position:fixed;inset:0;margin:auto;width:min(800px,calc(100vw - 32px));
  max-height:90vh;overflow:auto;padding:24px;border:1px solid #ddd;
  border-radius:12px;background:white;color:#242424;box-shadow:0 20px 60px #0004
 }
 #r6-update-dialog::backdrop{background:#0008}
 #r6-update-dialog h2{margin-bottom:12px}
 #r6-update-dialog .r6-box{padding:12px;background:#f4f4f4;border-radius:8px;margin:12px 0;white-space:pre-wrap}
 #r6-update-dialog .r6-confirm{display:flex;align-items:flex-start;gap:10px;text-transform:none;letter-spacing:0;margin:16px 0}
 #r6-update-dialog .r6-confirm input{width:auto;margin-top:3px}
 #r6-update-dialog .r6-actions{display:flex;justify-content:flex-end;gap:8px;flex-wrap:wrap;margin-top:16px}
 #r6-update-dialog button{margin:0}
 #r6-update-dialog button:disabled{opacity:.5;cursor:default;transform:none}
 `;
 document.head.append(css);

 const states=[];
 function attach(kind,inputId,resultId,checkId){
  const input=byId(inputId),box=byId(resultId),check=byId(checkId);
  if(!input||!box||!check)return;

  input.removeAttribute('oninput');input.oninput=null;
  input.setAttribute('autocomplete','off');
  input.setAttribute('placeholder','Search user, Asset ID, equipment, model or serial...');
  input.setAttribute('aria-controls',resultId);

  if(window._invTimers?.[resultId])clearTimeout(window._invTimers[resultId]);

  const state={kind,input,box,check,timer:null,controller:null,sequence:0};
  states.push(state);

  function cancel(){
   clearTimeout(state.timer);
   if(state.controller)state.controller.abort();
   state.sequence++;box.replaceChildren();
  }

  function details(values){
   const wrapper=make('details'),summary=make('summary','Details');
   const grid=make('div',undefined,'r6-original');
   for(const [key,value] of Object.entries(values)){
    const cell=make('div');cell.append(make('strong',key),make('div',value));
    grid.append(cell);
   }
   wrapper.append(summary,grid);return wrapper;
  }

  async function search(term,offset=0,append=false){
   if(!allowed()||!check.checked)return;
   if(!term.trim()){cancel();return;}

   if(state.controller)state.controller.abort();
   state.controller=new AbortController();
   const ticket=++state.sequence;
   const inputValue=input.value,employee=byId('idf')?.value||'';

   if(!append)box.replaceChildren(make('div','Searching...','r6-muted'));

   try{
    const data=await request(
     '/api/bulk/assets/search?q='+encodeURIComponent(term.trim())+
     '&kind='+kind+'&offset='+offset,
     {signal:state.controller.signal,headers:{Accept:'application/json'}}
    );

    if(ticket!==state.sequence||!check.checked||input.value!==inputValue||
       (byId('idf')?.value||'')!==employee)return;

    if(!append)box.replaceChildren();
    box.querySelector('.r6-more')?.remove();

    let list=box.querySelector('.r6-results');
    if(!list){list=make('div',undefined,'r6-results');box.append(list);}

    for(const hit of data.items){
     const v=hit.values,entry=make('div',undefined,'r6-hit');
     entry.append(
      make('div',[v['Asset ID']?'Asset ID: '+v['Asset ID']:'No Asset ID',v.Name,v.Model].filter(Boolean).join(' | '),'r6-hit-title'),
      make('div',`User: ${v.User||'—'} · Type: ${v.DevType||'—'} · Status: ${v.Status||'—'}`,'r6-muted'),
      make('div',`Serial: ${v['Serial Number / Parent Serial']||'—'} · Location: ${v.Location||'—'}`,'r6-muted')
     );

     const actions=make('div',undefined,'r6-hit-actions');
     const choose=make('button','Select','mini');
     choose.type='button';
     choose.onclick=async()=>{
      choose.disabled=true;
      try{await select(hit,kind);cancel();}
      catch(error){entry.append(make('div',error.message,'r6-error'));}
      finally{choose.disabled=false;}
     };
     actions.append(choose);entry.append(actions,details(v));list.append(entry);
    }

    if(!data.items.length&&!append)
     box.replaceChildren(make('div','No matching equipment. Try another search value.','r6-muted'));

    if(data.hasMore){
     const more=make('button','More results','mini r6-more');more.type='button';
     more.onclick=()=>{
      more.disabled=true;
      search(term,offset+data.items.length,true).finally(()=>{more.disabled=false;});
     };
     box.append(more);
    }
   }catch(error){
    if(error.name!=='AbortError'&&ticket===state.sequence)
     box.replaceChildren(make('div',error.message,'r6-error'));
   }
  }

  input.addEventListener('input',()=>{
   cancel();input.classList.remove('auto-filled');delete input.dataset.inventoryRecord;
   state.timer=setTimeout(()=>search(input.value),250);
  });

  input.addEventListener('keydown',event=>{
   if(event.key==='ArrowDown'){
    const first=box.querySelector('button');
    if(first){event.preventDefault();first.focus();}
   }
   if(event.key==='Escape')cancel();
   if(event.key==='Enter')event.preventDefault();
  });

  check.addEventListener('change',()=>{
   cancel();
   if(check.checked&&!input.value.trim()){
    const name=byId('n')?.value.trim();
    if(name)search(name);
   }
  });

  window.addEventListener('offboarding:inventory-updated',cancel);
 }

 attach('computer','computer_details','computer_inv_result','eq_computer');
 attach('phone','phone_details','phone_inv_result','eq_phone');

 // Administracion del inventario.
 const opener=make('button','Update inventory');opener.id='inventory-update-open';
 opener.type='button';opener.hidden=true;opener.style.margin='0 0 16px';
 const anchor=byId('asset-catalog-open')||byId('bulk-simple-open');
 if(!anchor)return;
 anchor.insertAdjacentElement('afterend',opener);

 const dialog=make('dialog');dialog.id='r6-update-dialog';
 dialog.innerHTML=`
 <h2>Update asset inventory</h2>
 <p>Upload the original Excel with the 22 inventory columns.</p>
 <div id="r6-current" class="r6-box"></div>
 <label for="r6-file">Inventory Excel (.xlsx, maximum 20 MB)</label>
 <input id="r6-file" type="file" accept=".xlsx">
 <div id="r6-message" class="r6-box" role="status" aria-live="polite"></div>
 <div id="r6-preview" hidden></div>
 <label id="r6-confirm-label" class="r6-confirm" hidden>
  <input id="r6-complete" type="checkbox">
  <span>I confirm this file contains the COMPLETE inventory and replaces ALL current catalog rows. It is not a partial update.</span>
 </label>
 <div class="r6-actions">
  <button id="r6-close" type="button">Close</button>
  <button id="r6-status" type="button">Refresh status</button>
  <button id="r6-analyze" type="button">Analyze Excel</button>
  <button id="r6-apply" type="button" disabled>Replace inventory</button>
 </div>`;
 document.body.append(dialog);
 const $=id=>dialog.querySelector('#'+id);
 let session=null,preview=null,busy=false;

 function message(text,error=false){
  $('r6-message').textContent=text;
  $('r6-message').className='r6-box'+(error?' r6-error':'');
 }

 function controls(){
  ['r6-close','r6-status','r6-analyze','r6-file','r6-complete']
   .forEach(id=>{$(id).disabled=busy;});
  $('r6-apply').disabled=busy||!preview||!$('r6-complete').checked;
 }

 function current(info){
  $('r6-current').textContent=
   `Current inventory: ${info.rows} rows.\nLast import: ${info.updatedAt||'Not recorded'}`;
 }

 function clearPreview(){
  preview=null;$('r6-complete').checked=false;
  $('r6-confirm-label').hidden=true;
  $('r6-preview').hidden=true;$('r6-preview').replaceChildren();
  controls();
 }

 async function loadSession(){
  session=await request('/api/bulk/assets/manage/session');
  current(session.current);return session;
 }

 async function send(path,file,extra={}){
  if(!session)throw new Error('Close and reopen the inventory updater.');
  return request('/api/bulk/assets/manage/'+path,{
   method:'POST',
   headers:{
    Accept:'application/json','Content-Type':'application/octet-stream',
    [session.header]:session.token,...extra
   },
   body:file
  });
 }

 function chosenFile(){
  const file=$('r6-file').files[0];
  if(!file)throw new Error('Select the inventory Excel.');
  if(!/\.xlsx$/i.test(file.name))throw new Error('Select the original .xlsx file, not a PDF.');
  if(file.size>20*1024*1024)throw new Error('Maximum file size: 20 MB.');
  return file;
 }

 opener.onclick=async()=>{
  if(!admin()||busy)return;
  clearPreview();dialog.showModal();busy=true;controls();
  try{
   await loadSession();
   message('Analyze the file first. Nothing is changed until you confirm replacement.');
  }catch(error){message(error.message,true);}
  finally{busy=false;controls();}
 };

 $('r6-file').onchange=()=>{clearPreview();message('Analyze the selected file.');};
 $('r6-complete').onchange=controls;

 $('r6-analyze').onclick=async()=>{
  if(busy||!admin())return;
  clearPreview();busy=true;controls();
  try{
   const file=chosenFile();
   message('Reading and analyzing the Excel...');
   await loadSession();

   preview=await send('preview',file,{'X-File-Name':encodeURIComponent(file.name)});
   current(preview.current);

   const difference=preview.newRows-preview.current.rows;
   message(
    `File: ${preview.fileName}\nCurrent rows: ${preview.current.rows}\n`+
    `Excel rows: ${preview.newRows}\n`+
    (difference<0?`The file has ${-difference} fewer rows.\n`:'')+
    'Replacement will replace the entire inventory catalog. Cases and tasks are not changed.'
   );

   const table=make('table'),head=make('tr');
   ['Asset ID','Name','Model','User','Type','Status'].forEach(h=>head.append(make('th',h)));
   table.append(head);
   for(const values of preview.sample){
    const row=make('tr');
    [0,2,3,9,5,17].forEach(i=>row.append(make('td',values[i]||'')));
    table.append(row);
   }

   $('r6-preview').replaceChildren(make('p','Sample from the uploaded file:'),table);
   $('r6-preview').hidden=false;$('r6-confirm-label').hidden=false;
  }catch(error){clearPreview();message(error.message,true);}
  finally{busy=false;controls();}
 };

 $('r6-apply').onclick=async()=>{
  if(busy||!admin()||!preview||!$('r6-complete').checked)return;
  const confirmation=preview;
  busy=true;controls();

  try{
   message('Replacing inventory. Closing the browser does not cancel a request already submitted.');
   const result=await send('commit',chosenFile(),{
    'X-Inventory-Preview':confirmation.token,'X-Complete-Inventory':'true'
   });

   current(result.current);clearPreview();
   if(result.superseded){
    message('This operation was already applied, but a later inventory update is now current. No replacement was repeated.',true);
   }else{
    message(`${result.appliedRows} inventory rows loaded successfully.`+
     (result.replayed?'\nThe prior confirmation was recovered without repeating the update.':''));
   }
   window.dispatchEvent(new Event('offboarding:inventory-updated'));
   if(typeof cargarAuditoria==='function')
    Promise.resolve(cargarAuditoria()).catch(()=>{});
  }catch(error){
   message(error.message+
    '\nIf the connection was interrupted, use Refresh status. Do not assume the update failed.',true);
  }finally{busy=false;controls();}
 };

 $('r6-status').onclick=async()=>{
  if(busy||!admin())return;
  busy=true;controls();
  try{
   await loadSession();
   if(preview&&session.current.sourceHash===preview.fileHash)
    message('The current inventory has the same source file hash as the analyzed Excel.');
   else message('Current inventory status refreshed.');
  }catch(error){message(error.message,true);}
  finally{busy=false;controls();}
 };

 $('r6-close').onclick=()=>{if(!busy)dialog.close();};
 dialog.addEventListener('cancel',event=>{if(busy)event.preventDefault();});

 function permissions(){
  opener.hidden=!admin();
  if(!allowed()){
   for(const state of states){
    clearTimeout(state.timer);
    if(state.controller)state.controller.abort();
    state.sequence++;state.box.replaceChildren();
   }
  }
  if(!admin()&&dialog.open){
   dialog.close();session=null;clearPreview();
  }
 }
 const section=byId('seccion_captura');
 if(section)new MutationObserver(permissions).observe(section,{
  attributes:true,attributeFilter:['class']
 });
 permissions();
})();
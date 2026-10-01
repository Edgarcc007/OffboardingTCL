'use strict';
(() => {
 const endpoint='/api/bulk/simple';
 const opener=document.getElementById('bulk-simple-open');
 if(!opener)return;

 let session=null,current=null,requestId=null,busy=false;
 const node=(tag,text)=>{
  const n=document.createElement(tag);
  if(text!==undefined)n.textContent=text;
  return n;
 };
 const allowed=()=>typeof tieneRol==='function'&&
  (tieneRol('ADMIN')||tieneRol('RECURSOS_HUMANOS'));

 const style=node('style');
 style.textContent=`
 #bulk-simple-dialog{
  position:fixed;inset:0;margin:auto;width:min(1040px,calc(100vw - 32px));
  max-height:90vh;overflow:auto;padding:24px;border:1px solid #ddd;
  border-radius:12px;background:#fff;color:#242424;box-shadow:0 20px 60px #0004
 }
 #bulk-simple-dialog::backdrop{background:#0008}
 #bulk-simple-dialog h2{margin-bottom:12px}
 #bulk-simple-dialog .bs-actions{display:flex;justify-content:flex-end;gap:10px;flex-wrap:wrap}
 #bulk-simple-dialog .bs-notice{margin:12px 0;padding:12px;border-radius:8px;background:#f3f3f3;white-space:pre-wrap}
 #bulk-simple-dialog .bs-bad{color:#991b1b;background:#fff1f2;border:1px solid #dc2626}
 #bulk-simple-dialog .bs-row{border:1px solid #ddd;border-radius:8px;padding:16px;margin:14px 0}
 #bulk-simple-dialog .bs-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(230px,1fr));gap:12px}
 #bulk-simple-dialog .bs-invalid{border:2px solid #dc2626;background:#fff1f2}
 #bulk-simple-dialog .bs-error{display:block;color:#b91c1c;margin-top:4px;font-weight:600}
 #bulk-simple-dialog label{text-transform:none}
 #bulk-simple-dialog button:disabled{opacity:.5;cursor:wait;transform:none}
 `;
 document.head.append(style);

 const dialog=node('dialog');
 dialog.id='bulk-simple-dialog';
 dialog.setAttribute('aria-labelledby','bulk-simple-title');
 dialog.innerHTML=`
 <h2 id="bulk-simple-title">Bulk Offboarding</h2>
 <p>Upload the completed Excel file. If it is valid, real offboarding cases and tasks will be registered.</p>
 <div id="bs-upload">
  <label for="bs-file">Excel file (.xlsx)</label>
  <input id="bs-file" type="file" accept=".xlsx">
  <p style="margin-top:8px"><a href="/api/bulk/template">Download template</a></p>
 </div>
 <div id="bs-notice" class="bs-notice" role="status" aria-live="polite"></div>
 <div id="bs-editor" hidden></div>
 <div id="bs-results" hidden></div>
 <div class="bs-actions">
  <button id="bs-close" type="button">Close</button>
  <button id="bs-skip" type="button" hidden>Omit active offboardings and continue</button><button id="bs-mail" type="button" hidden>Retry notification</button>
  <button id="bs-action" type="button">Upload and register</button>
 </div>`;
 document.body.append(dialog);
 const $=id=>dialog.querySelector('#'+id);

 function message(text,error=false){
  $('bs-notice').textContent=text||'';
  $('bs-notice').className='bs-notice'+(error?' bs-bad':'');
 }
 function lock(value){
  busy=value;
  dialog.querySelectorAll('button,input,select,textarea').forEach(n=>n.disabled=value);
 }
 function uuid(){
  const b=new Uint8Array(16);crypto.getRandomValues(b);
  b[6]=(b[6]&15)|64;b[8]=(b[8]&63)|128;
  const s=Array.from(b,x=>x.toString(16).padStart(2,'0')).join('');
  return `${s.slice(0,8)}-${s.slice(8,12)}-${s.slice(12,16)}-${s.slice(16,20)}-${s.slice(20)}`;
 }
 async function call(method,path,body,extra={}){
  const headers={Accept:'application/json',...extra};
  if(method!=='GET'){
   if(!session)throw new Error('Session unavailable. Close and reopen Bulk Offboarding.');
   headers[session.header]=session.token;
  }
  let data=body;
  if(body!==undefined&&!(body instanceof Blob)){
   headers['Content-Type']='application/json';data=JSON.stringify(body);
  }
  const response=await fetch(endpoint+path,{
   method,headers,body:data,credentials:'same-origin',cache:'no-store'
  });
  if(response.status===401||response.redirected){
   current=null;session=null;
   $('bs-editor').replaceChildren();$('bs-results').replaceChildren();
   throw new Error('Session expired. Sign in again.');
  }
  const text=await response.text();
  let result;
  try{result=text?JSON.parse(text):null;}catch{
   throw new Error('Unexpected response. Check the batch status before retrying.');
  }
  if(!response.ok){
   const error=new Error(result?.detail||result?.message||result?.title||`HTTP ${response.status}`);
   error.status=response.status;throw error;
  }
  if(result?.batch && result.batch.ownerId!==session?.owner?.id)
   throw new Error('The session changed. Reopen Bulk Offboarding.');
  return result;
 }
 function inputBody(){
  return {
   version:current.batch.version,name:current.batch.name,
   rows:current.batch.document.rows.map(r=>({
    excelRow:r.excelRow,values:r.values,included:true,
    reviewed:false,fingerprint:null,personalized:[]
   }))
  };
 }
 const norm=s=>String(s||'').normalize('NFD')
  .replace(/[\u0300-\u036f]/g,'').toUpperCase().replace(/[^A-Z0-9]/g,'');
 const typeOptions=['RENUNCIA','DESPIDO','FIN_DE_CONTRATO','JUBILACION','MUTUO_ACUERDO','FALLECIMIENTO'];
 const booleanKeys=new Set([
  'FACEID','FINGERPRINT','CONFIDENTIAL','COMPUTERASSIGNED','PHONEASSIGNED'
 ]);
 function matches(key,error){
  const k=norm(key),e=norm(error);
  if(e.includes(k))return true;
  const aliases={
   EMPLOYEENUMBER:['EMPLOYEEIDENTIFIER','NUMERODEEMPLEADO','EMPLEADONOENCONTRADO'],
   OFFBOARDINGTYPE:['TERMINATIONTYPE','TIPODEBAJA','MOTIVO'],
   CORPORATEEMAIL:['EMAIL','CORREO'],
   EFFECTIVEAT:['EFFECTIVE','FECHA'],
   EFFECTIVEDATE:['EFFECTIVE','FECHA'],
   OBSERVATIONS:['OBSERVACIONES'],
   OTHERACCESSES:['OTROSACCESOS']
  };
  return (aliases[k]||[]).some(a=>e.includes(a));
 }
 function showEditor(extra=''){
  const box=$('bs-editor');box.replaceChildren();box.hidden=false;
  $('bs-results').hidden=true;
  $('bs-action').hidden=false;$('bs-action').textContent='Validate and register';
  $('bs-upload').hidden=true;
  const attention=new Set(current.attention||[]);
  let rows=current.batch.document.rows.filter(r=>
   r.included!==false&&((r.issues||[]).length||attention.has(r.excelRow)));
  if(!rows.length)rows=current.batch.document.rows.filter(r=>r.included!==false);

  message(extra||current.message||'Correct the indicated fields. No new offboarding cases have been confirmed for this batch.',true);

  for(const row of rows){
   const card=node('section');card.className='bs-row';
   card.append(node('h3',`Employee ${row.values['EMPLOYEE NUMBER']||''} · Excel row ${row.excelRow}`));
   const name=row.form?.employeeName||row.employee?.name||'';
   if(name)card.append(node('p',name));

   const errors=row.issues||[];
   const ul=node('ul');ul.style.color='#b91c1c';
   for(const error of errors)ul.append(node('li',String(error)));
   if(attention.has(row.excelRow))ul.append(node('li','Directory or task information changed. Review the updated data.'));
   card.append(ul);

   const grid=node('div');grid.className='bs-grid';
   for(const key of session.columns){
    const label=node('label',key);
    const k=norm(key),value=String(row.values[key]??'');
    let input;

    if(k==='OFFBOARDINGTYPE'||booleanKeys.has(k)){
     input=node('select');
     const choices=k==='OFFBOARDINGTYPE'?typeOptions:['SI','NO'];
     const empty=node('option','Select a value');empty.value='';input.append(empty);
     if(value&&!choices.includes(value)){
      const invalid=node('option',`Current value: ${value}`);invalid.value=value;input.append(invalid);
     }
     for(const choice of choices){const option=node('option',choice);option.value=choice;input.append(option);}
     input.value=value;
    }else{
     input=node(k==='OBSERVATIONS'||k==='OTHERACCESSES'?'textarea':'input');
     if(input.tagName==='INPUT')input.type='text';
     input.value=value;
    }

    const id=`bs-${row.excelRow}-${norm(key)}`;
    input.id=id;label.htmlFor=id;
    input.autocomplete='off';

    const related=errors.filter(error=>matches(key,error));
    if((k==='OFFBOARDINGTYPE'||k==='EMPLOYEENUMBER')&&!value.trim())
     related.push('This field is required.');

    if(related.length){
     input.classList.add('bs-invalid');input.setAttribute('aria-invalid','true');
    }

    input.addEventListener('input',()=>{row.values[key]=input.value;});
    input.addEventListener('change',()=>{row.values[key]=input.value;});

    label.append(input);
    if(related.length){
     const help=node('small',related.join(' '));
     help.id=id+'-error';help.className='bs-error';
     input.setAttribute('aria-describedby',help.id);
     label.append(help);
    }
    grid.append(label);
   }
   card.append(grid);
   const directoryErrors=errors.filter(e=>
    /department|departamento|work.?area|employee.?name/i.test(String(e)));
   if(directoryErrors.length)card.append(node('p',
    'Directory fields are read-only. Correct the employee number or update the directory, then validate again.'));
   box.append(card);
  }
 }
 function showResult(){
  $('bs-editor').hidden=true;$('bs-upload').hidden=true;
  $('bs-action').hidden=true;$('bs-results').hidden=false;
  const box=$('bs-results');box.replaceChildren();
  const results=current.batch.results||[];
  const state=current.mailState; const skipped=current.batch.document.rows.filter(r=>r.included===false).length;
  const labels={
   NOT_NEEDED:'No new cases were needed. No email was sent.',SENT:'The consolidated email was accepted by SMTP.',
   PENDING:'The consolidated email is pending.',
   SENDING:'The email is being processed or its confirmation is pending.',
   UNKNOWN:'Email delivery could not be confirmed. It will not be resent automatically; ask an administrator to check.',
   DISABLED:'Email notifications are disabled.',
   NO_RECIPIENTS:'No active notification recipients are configured.',
   CONFIG_ERROR:'The email configuration requires attention.'
  };
  message(`${results.length} offboarding cases registered. ${skipped} omitted due to an active offboarding.\n${labels[state]||state}`,
   !['SENT','NOT_NEEDED'].includes(state));

  const table=node('table'),head=node('tr');
  ['Excel row','Case','Employee number'].forEach(x=>head.append(node('th',x)));
  table.append(head);
  for(const result of results){
   const row=node('tr');
   const item=current.batch.document.rows.find(r=>r.excelRow===result.excelRow);
   row.append(node('td',result.excelRow),node('td',result.caseNumber),
    node('td',item?.values['EMPLOYEE NUMBER']||''));
   table.append(row);
  }
  box.append(table);
  $('bs-mail').hidden=!['PENDING','DISABLED','NO_RECIPIENTS','CONFIG_ERROR'].includes(state);

  if(typeof tieneRol==='function'&&tieneRol('ADMIN')){
   const link=node('a','View Cases and tasks');
   link.href='#seccion_casos';
   link.onclick=()=>dialog.close();
   box.append(link);
   if(typeof cargarCases==='function')Promise.resolve(cargarCases()).catch(()=>{});
  }
 }
 async function register(){
  message('Registering the batch. Closing the browser does not cancel a request already submitted.');
  current=await call('POST',`/${current.batch.id}/register?version=${current.batch.version}`);
  if(current.registered)showResult();else showEditor();
 }
 async function recover(error){
  if(current?.batch?.id){
   try{
    current=await call('GET','/'+current.batch.id);
    if(current.registered){showResult();return;}
    if(error.status===409){
     current=await call('POST','/'+current.batch.id+'/validate',inputBody());
    }
    showEditor(error.message);return;
   }catch{}
  }
  message(error.message||'The result could not be confirmed. Retry this same operation.',true);
 }
 $('bs-action').onclick=async()=>{
  if(busy)return;
  lock(true);
  try{
   if(!current){
    const file=$('bs-file').files[0];
    if(!file)throw new Error('Select an Excel file.');
    if(!/\.xlsx$/i.test(file.name))throw new Error('Only .xlsx files are accepted.');
    if(file.size>8*1024*1024)throw new Error('The maximum file size is 8 MB.');
    if(!requestId)requestId=uuid();

    message('Reading and validating the Excel file...');
    current=await call('POST','/upload',file,{
     'Content-Type':'application/octet-stream',
     'X-Request-ID':requestId,'X-File-Name':encodeURIComponent(file.name)
    });
   }else if(!current.registered){
    message('Validating the corrections...');
    current=await call('POST','/'+current.batch.id+'/validate',inputBody());
   }

   if(current.registered){showResult();return;}
   if(current.batch.document.rows.some(r=>r.included!==false&&(r.issues?.length||!r.form))){
    showEditor();return;
   }
   await register();
  }catch(error){await recover(error);}
  finally{lock(false);}
 };
 $('bs-mail').onclick=async()=>{
  if(busy||!current?.registered)return;
  lock(true);
  try{
   current=await call('POST','/'+current.batch.id+'/mail');
   showResult();
  }catch(error){message(error.message,true);}
  finally{lock(false);}
 };
 $('bs-file').onchange=()=>{
  requestId=uuid();current=null;
  $('bs-editor').replaceChildren();$('bs-results').replaceChildren();
  $('bs-editor').hidden=true;$('bs-results').hidden=true;
  $('bs-mail').hidden=true;
  $('bs-action').hidden=false;$('bs-action').textContent='Upload and register';
  message('');
 };
 $('bs-close').onclick=()=>{if(!busy)dialog.close();};
 dialog.addEventListener('cancel',event=>{if(busy)event.preventDefault();});

 opener.onclick=async()=>{
  if(!allowed()||busy)return;
  dialog.showModal();lock(true);
  try{
   session=await call('GET','/session');
   if(current){
    current=await call('GET','/'+current.batch.id);
    if(current.registered)showResult();else showEditor();
   }else message('Valid files will be registered without individual review steps.');
  }catch(error){message(error.message,true);}
  finally{lock(false);}
 };

  /* R4_BULK_OMISSIONS_UI */
 $('bs-skip').onclick=async()=>{
  if(busy||!current||current.registered)return;
  lock(true);
  try{
   current=await call('POST','/'+current.batch.id+'/skip-active',inputBody());
   if(current.registered){showResult();return;}
   if(current.batch.document.rows.some(r=>
      r.included!==false&&(r.issues?.length||!r.form))){
    showEditor(current.message);return;
   }
   await register();
  }catch(error){await recover(error);}
  finally{lock(false);}
 };

 const r4Observer=new MutationObserver(()=>{
  $('bs-skip').hidden=!current||current.registered||$('bs-editor').hidden;
 });
 r4Observer.observe($('bs-editor'),{
  attributes:true,attributeFilter:['hidden'],childList:true
 });
 r4Observer.observe($('bs-results'),{
  attributes:true,attributeFilter:['hidden']
 });

 let autoOpened=false;
 function permissions(){
  opener.hidden=!allowed();
  if(!allowed()&&dialog.open){
   dialog.close();current=null;session=null;
   $('bs-editor').replaceChildren();$('bs-results').replaceChildren();
  }
  if(allowed()&&!autoOpened&&new URLSearchParams(location.search).get('bulk')==='1'){
   autoOpened=true;opener.click();
  }
 }
 new MutationObserver(permissions).observe(
  document.getElementById('seccion_captura'),{attributes:true,attributeFilter:['class']});
 permissions();
})();
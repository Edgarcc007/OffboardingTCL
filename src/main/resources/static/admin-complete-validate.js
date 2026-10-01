'use strict';
(() => {
 if(window.AdminCompleteValidate)return;
 let busy=false;
 const allowed=()=>typeof tieneRol==='function'&&tieneRol('ADMIN');
 const make=(tag,text)=>{
  const element=document.createElement(tag);
  if(text!==undefined)element.textContent=String(text);
  return element;
 };

 async function request(path,options={}){
  const response=await fetch(path,{
   credentials:'same-origin',cache:'no-store',...options
  });
  let data=null;
  try{data=await response.json();}catch{}
  if(response.redirected||response.status===401)
   throw new Error('Session expired. Sign in again.');
  if(!response.ok){
   const ref=data?.requestId||response.headers.get('X-Request-Id');
   throw new Error(
    (data?.detail||data?.message||'The operation could not be confirmed.')+
    `\nHTTP ${response.status}`+(ref?`\nReference: ${ref}`:''));
  }
  return data;
 }

 const css=make('style');
 css.textContent=`
 #admin-complete-validate{position:fixed;inset:0;margin:auto;padding:24px;
 width:min(650px,calc(100vw - 32px));max-height:90vh;overflow:auto;
 border:1px solid var(--border);border-radius:12px;background:white;
 color:var(--text-primary);font-family:var(--font);box-shadow:0 20px 60px #0004}
 #admin-complete-validate::backdrop{background:#0008}
 #admin-complete-validate h2{font-size:18px;margin-bottom:14px}
 #admin-complete-validate .acv-summary{white-space:pre-wrap;background:#f3f3f3;padding:12px;border-radius:8px;font-size:13px}
 #admin-complete-validate .acv-check{display:flex;gap:10px;align-items:flex-start;text-transform:none;letter-spacing:0;margin:18px 0}
 #admin-complete-validate .acv-check input{width:auto;margin-top:3px}
 #admin-complete-validate .acv-actions{display:flex;justify-content:flex-end;gap:8px;margin-top:16px}
 #admin-complete-validate .acv-message{white-space:pre-wrap;color:var(--danger);font-size:13px;margin-top:12px}
 #admin-complete-validate button:disabled{opacity:.5;cursor:default}
 `;
 document.head.append(css);

 window.AdminCompleteValidate={
  async open(taskId){
   if(!allowed()||busy)return;
   busy=true;
   let dialog=null,sending=false;
   try{
    const session=await request('/api/bulk/admin-tasks/session');
    const preview=await request('/api/bulk/admin-tasks/'+taskId);
    if(!['PENDIENTE','EN_PROCESO'].includes(preview.status))
     throw new Error('This task changed. Refresh; use Validate if it is already completed.');

    dialog=make('dialog');dialog.id='admin-complete-validate';
    const form=make('form');
    const heading=make('h2','Complete and validate — ADMIN');
    const summary=make('div',
     `${preview.caseNumber}\n${preview.employeeName}\n${preview.systemName}\n${preview.taskName}`);
    summary.className='acv-summary';
    const label=make('label');label.className='acv-check';
    const confirmed=make('input');confirmed.type='checkbox';confirmed.required=true;
    label.append(confirmed,make('span',
     'I confirm this task was actually completed and the corresponding record was updated. Both actions will be recorded under my ADMIN account.'));
    const commentsLabel=make('label','Comments (optional)');
    const comments=make('textarea');comments.rows=3;comments.maxLength=2000;
    const message=make('div');message.className='acv-message';message.setAttribute('role','status');
    const actions=make('div');actions.className='acv-actions';
    const cancel=make('button','Cancel');cancel.type='button';
    const save=make('button','Confirm completion and validation');save.type='submit';
    actions.append(cancel,save);
    form.append(heading,summary,label,commentsLabel,comments,message,actions);
    dialog.append(form);document.body.append(dialog);

    cancel.onclick=()=>{if(!sending)dialog.close();};
    dialog.addEventListener('cancel',event=>{if(sending)event.preventDefault();});
    dialog.addEventListener('close',()=>{dialog.remove();busy=false;},{once:true});

    let fixedPayload=null;
    form.onsubmit=async event=>{
     event.preventDefault();
     if(sending||!allowed()||!form.reportValidity())return;

     // Si se pierde la respuesta, se reutilizan la misma operacion y datos.
     if(!fixedPayload)fixedPayload={
      operationId:preview.operationId,revision:preview.revision,
      confirmed:confirmed.checked,comments:comments.value.trim()||null
     };

     sending=true;save.disabled=true;cancel.disabled=true;
     confirmed.disabled=true;comments.disabled=true;
     message.textContent='Saving both actions...';
     try{
      const result=await request(
       `/api/bulk/admin-tasks/${taskId}/complete-validate`,{
        method:'POST',
        headers:{'Content-Type':'application/json',[session.header]:session.token},
        body:JSON.stringify(fixedPayload)
       });
      dialog.close();
      await Promise.allSettled([
       typeof cargarCases==='function'?cargarCases():Promise.resolve(),
       typeof cargarAuditoria==='function'?cargarAuditoria():Promise.resolve()
      ]);
      if(typeof log==='function')
       log(`Task ${taskId}: completion and validation ${result.replayed?'already confirmed':'saved'}.`);
     }catch(error){
      message.textContent=error.message+
       '\nIf the connection was interrupted, retrying this same confirmation will not repeat a completed operation. If the record changed, close and preview again.';
     }finally{
      sending=false;save.disabled=false;cancel.disabled=false;
      // Tras enviar, los datos se mantienen fijos para un reintento.
     }
    };
    dialog.showModal();
   }catch(error){
    dialog?.remove();busy=false;alert(error.message);
   }
  }
 };
})();
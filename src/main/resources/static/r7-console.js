'use strict';
(() => {
 const el=id=>document.getElementById(id);
 const make=(tag,text)=>{
  const n=document.createElement(tag);
  if(text!==undefined)n.textContent=String(text);
  return n;
 };
 const has=role=>typeof tieneRol==='function'&&tieneRol(role);
 const endpoint='/api/bulk/admin-maintenance';
 let open=false;

 async function request(path,options={}){
  const r=await fetch(path,{credentials:'same-origin',cache:'no-store',...options});
  if(r.status===401||r.redirected)throw new Error('Session expired. Sign in again.');
  let data;
  try{data=await r.json();}catch{throw new Error('Unexpected response. Refresh your session.');}
  if(!r.ok)throw new Error(window.OffboardingErrors?.describe(r,data)||data.detail||data.message||`HTTP ${r.status}`);
  return data;
 }

 const style=make('style');
 style.textContent=`
 #r7-maintenance{position:fixed;inset:0;margin:auto;width:min(760px,calc(100vw - 32px));
 max-height:90vh;overflow:auto;padding:24px;border:1px solid var(--border);border-radius:12px;
 color:var(--text-primary);background:white;box-shadow:0 20px 60px #0004;font-family:var(--font)}
 #r7-maintenance::backdrop{background:#0008}
 #r7-maintenance h2{font-size:18px;margin-bottom:14px}
 #r7-maintenance .r7-summary{white-space:pre-wrap;padding:12px;background:#f4f4f4;border-radius:8px}
 #r7-maintenance .r7-actions{display:flex;justify-content:flex-end;gap:8px;flex-wrap:wrap;margin-top:16px}
 #r7-maintenance .r7-error{color:#a51d24;white-space:pre-wrap;margin-top:12px}
 #r7-maintenance button:disabled{opacity:.5;cursor:default;transform:none}
 .r7-case-actions{display:flex;gap:6px;flex-wrap:wrap;margin-top:8px}
 #r7-inventory-tools{display:flex;gap:10px;flex-wrap:wrap;margin-bottom:14px}
 `;
 document.head.append(style);

 const labels={
  employee_name:'Employee name',corporate_email:'Corporate email',
  department:'Department',building:'Building',work_area:'Work area',
  manager_name:'Manager',observations:'Observations',
  comments:'Comments',evidence_reference:'Evidence reference',
  validation_comments:'Validation comments',inventory_reference:'Inventory reference',
  details:'Event details'
 };
 const limits={
  employee_name:150,corporate_email:150,department:100,building:100,
  work_area:150,manager_name:150,observations:4000,comments:4000,
  evidence_reference:200,validation_comments:4000,inventory_reference:200,details:4000
 };

 async function refresh(){
  const calls=[];
  if(typeof cargarCases==='function'&&(has('ADMIN')||has('IT_ENGINEER')||has('AUDITOR')||has('CONTROL_ACCESOS')))
   calls.push(cargarCases());
  if(typeof cargarAuditoria==='function'&&(has('ADMIN')||has('AUDITOR')))
   calls.push(cargarAuditoria());
  await Promise.allSettled(calls);
  el('obd-refresh')?.click();
 }

 window.R7Maintenance={
  async open(kind,id,action){
   if(open||!has('ADMIN'))return;
   open=true;
   let dialog=null,busy=false;
   try{
    const session=await request(endpoint+'/session');
    const view=await request(endpoint+'/record/'+kind+'/'+id);
    const deleting=action==='delete';
    dialog=make('dialog');dialog.id='r7-maintenance';
    const heading=make('h2',deleting?'Permanent deletion':'Edit record details');
    const summary=make('div',view.label);summary.className='r7-summary';
    const form=make('form'),fields=new Map(),message=make('div');
    message.className='r7-error';message.setAttribute('role','status');
    form.append(heading,summary);

    let confirmation=null,reason=null,proof='';
    if(deleting){
     const warning=make('p',
      `Selected: ${kind==='case'?'1 case; ':''}${view.tasks} tasks; ${view.events} audit events; ${view.bulkRows} Bulk links.\n`+
      'This removes application records, not delivered emails, exported files or external backups. '+
      'Other cases and inventory are preserved. A general maintenance receipt is retained without employee data or case number.');
     warning.style.cssText='white-space:pre-wrap;margin-top:12px;font-size:13px';
     form.append(warning);

     const label=make('label','Type exactly: '+view.confirmation);
     confirmation=make('input');confirmation.autocomplete='off';confirmation.required=true;
     label.append(confirmation);form.append(label);
     if(kind==='all')
      form.append(make('p','New audit events created after this preview are not included.'));
    }else{
     for(const [name,value] of Object.entries(view.fields)){
      const label=make('label',labels[name]||name);
      const input=make(limits[name]>500?'textarea':'input');
      input.value=value||'';input.maxLength=limits[name]||4000;
      if(input.tagName==='TEXTAREA')input.rows=4;
      if(name==='corporate_email')input.type='email';
      if(['employee_name','department','work_area'].includes(name))input.required=true;
      label.append(input);form.append(label);fields.set(name,input);
     }
     const label=make('label','Reason for correction');
     reason=make('textarea');reason.rows=2;reason.minLength=5;reason.maxLength=500;reason.required=true;
     label.append(reason);form.append(label);
    }

    const actions=make('div');actions.className='r7-actions';
    const close=make('button','Cancel');close.type='button';
    const validate=make('button','Validate deletion — no changes saved');validate.type='button';
    const save=make('button',deleting?'Confirm permanent deletion':'Save changes');save.type='submit';
    save.disabled=deleting;
    close.onclick=()=>{if(!busy)dialog.close();};
    actions.append(close);if(deleting)actions.append(validate);actions.append(save);
    form.append(message,actions);dialog.append(form);document.body.append(dialog);

    function controls(){
     close.disabled=busy;
     if(deleting)validate.disabled=busy;
     save.disabled=busy||(deleting&&!proof);
     for(const input of fields.values())input.disabled=busy;
     if(confirmation)confirmation.disabled=busy;
     if(reason)reason.disabled=busy;
    }
    if(confirmation)confirmation.addEventListener('input',()=>{proof='';controls();});

    async function submit(dryRun){
     if(busy||!has('ADMIN')||!form.reportValidity())return;
     if(deleting&&confirmation.value!==view.confirmation){
      message.textContent='The confirmation text does not match.';return;
     }
     busy=true;controls();message.textContent=dryRun?'Validating the full operation with rollback...':'Saving...';
     try{
      const values={};
      for(const [name,input] of fields)values[name]=input.value;
      const result=await request(
       endpoint+'/record/'+kind+'/'+view.id+'/'+action,{
        method:'POST',
        headers:{'Content-Type':'application/json',[session.header]:session.token},
        body:JSON.stringify({
         revision:view.revision,fields:values,
         reason:reason?.value||'',
         confirmation:confirmation?.value||'',
         dryRun,proof
        })
       });

      if(dryRun){
       proof=result.proof;
       message.textContent='Validation passed and was rolled back. No deletion was saved. You may now confirm within five minutes.';
      }else{
       dialog.close();
       if(result.caseNumber){
        try{
         for(let i=0;i<sessionStorage.length;i++){
          const key=sessionStorage.key(i);
          if(!key?.startsWith('offboarding.rh.session.'))continue;
          const rows=JSON.parse(sessionStorage.getItem(key)||'[]');
          if(Array.isArray(rows))
           sessionStorage.setItem(key,JSON.stringify(rows.filter(r=>r.caseNumber!==result.caseNumber)));
         }
        }catch{}
       }
       await refresh();
      }
     }catch(error){
      message.textContent=error.message+'\nIf the connection was interrupted, refresh before retrying.';
     }finally{busy=false;controls();}
    }

    validate.onclick=()=>submit(true);
    form.onsubmit=event=>{event.preventDefault();submit(false);};
    dialog.addEventListener('cancel',event=>{if(busy)event.preventDefault();});
    dialog.addEventListener('close',()=>{dialog.remove();open=false;},{once:true});
    dialog.showModal();
   }catch(error){
    if(dialog)dialog.remove();
    open=false;alert(error.message);
   }
  }
 };

 // Controles de casos: no sustituir el renderizador original.
 function decorateCases(){
  document.querySelectorAll('#casos .caso').forEach(card=>{
   let actions=card.querySelector('.r7-case-actions');
   if(!has('ADMIN')){actions?.remove();return;}
   if(actions)return;
   const id=Number(card.dataset.caseId);
   if(!Number.isSafeInteger(id)||id<1)return;
   actions=make('div');actions.className='r7-case-actions';
   for(const [caption,action] of [['Edit case','edit'],['Delete case + history','delete']]){
    const button=make('button',caption);button.type='button';button.className='mini';
    button.onclick=()=>window.R7Maintenance.open('case',id,action);
    actions.append(button);
   }
   card.querySelector('.caso-h')?.append(actions);
  });
 }

 // Inventario visible para el perfil nuevo fuera del formulario RH.
 const homes=new Map();
 function place(button,target){
  if(!button||!target)return;
  if(!homes.has(button)){
   const home=document.createComment('r7-original-position');
   button.before(home);homes.set(button,home);
  }
  if(button.parentElement!==target)target.append(button);
 }
 function restore(button){
  const home=homes.get(button);
  if(home?.parentNode&&button.previousSibling!==home)
   home.parentNode.insertBefore(button,home.nextSibling);
 }
 let queued=false;
 function sync(){
  queued=false;
  const validator=has('IT_ENGINEER_VALIDATOR');
  const independent=validator&&!has('ADMIN')&&!has('RECURSOS_HUMANOS');
  const search=el('asset-catalog-open'),update=el('inventory-update-open');
  let tools=el('r7-inventory-tools');
  if(independent&&!tools&&el('casos')){
   tools=make('div');tools.id='r7-inventory-tools';el('casos').before(tools);
  }
  if(independent&&tools){place(search,tools);place(update,tools);}
  else {if(search)restore(search);if(update)restore(update);}
  if(tools&&tools.hidden!==!independent)tools.hidden=!independent;
  const canSearch=has('ADMIN')||has('RECURSOS_HUMANOS')||validator;
  const canUpdate=has('ADMIN')||validator;
  if(search&&search.hidden!==!canSearch)search.hidden=!canSearch;
  if(update&&update.hidden!==!canUpdate)update.hidden=!canUpdate;

  const container=el('r5-audit')?.querySelector('.r5-buttons');
  let purge=el('r7-purge-audit');
  if(container&&!purge){
   purge=make('button','Purge audit log');purge.type='button';purge.id='r7-purge-audit';
   purge.onclick=()=>window.R7Maintenance.open('all',0,'delete');
   container.append(purge);
  }
  if(purge&&purge.hidden!==!has('ADMIN'))purge.hidden=!has('ADMIN');
  decorateCases();
 }
 function schedule(){if(!queued){queued=true;requestAnimationFrame(sync);}}
 new MutationObserver(schedule).observe(document.body,{
  childList:true,subtree:true,attributes:true,attributeFilter:['class']
 });
 sync();

 // Retirar del historial de sesion RH los casos que ya no existen.
 const originalSessionRender=window.renderizarRegistrosRH;
 let checkingSession=false;
 async function reconcileSession(){
  if(checkingSession||!has('RECURSOS_HUMANOS')||
     typeof obtenerRegistrosRH!=='function')return;
  const rows=obtenerRegistrosRH();
  if(!rows.length){originalSessionRender?.();return;}
  checkingSession=true;
  try{
   const numbers=rows.map(r=>r.caseNumber).filter(
    n=>/^OB-\d{4}-\d{2}-\d{2}-\d{6}$/.test(n)).slice(0,100);
   const alive=await request('/api/bulk/session-records?numbers='+encodeURIComponent(numbers.join(',')));
   guardarRegistrosRH(rows.filter(r=>alive.includes(r.caseNumber)));
   originalSessionRender?.();
  }catch{}finally{checkingSession=false;}
 }
 if(originalSessionRender)window.renderizarRegistrosRH=reconcileSession;
 setInterval(reconcileSession,30000);
 reconcileSession();
})();
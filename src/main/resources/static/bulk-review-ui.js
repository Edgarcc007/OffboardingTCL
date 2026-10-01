'use strict';
(() => {
 const E=window.BulkEditor, F=window.BulkFull;
 const controls=document.getElementById('controls');
 const mark=document.getElementById('mark');

 if(!E?.current||!E?.focusRow||!F||!controls||!mark){
  const warning=document.createElement('p');
  warning.textContent='No se cargo la actualizacion del editor. Guarda tu trabajo y actualiza con Ctrl+F5.';
  warning.style.color='#b91c1c';
  document.body.prepend(warning);
  return;
 }

 const style=document.createElement('style');
 style.textContent=`
 .bulk-r2-panel{
   position:sticky;top:6px;z-index:20;background:#fff;
   border:2px solid #b91c1c;border-radius:8px;padding:12px;
   margin:12px 0;max-height:35vh;overflow:auto;color:#991b1b
 }
 .bulk-r2-panel.ok{border-color:#2563eb;color:#1e3a8a}
 .bulk-r2-panel ul{margin:6px 0;padding-left:22px}
 .bulk-r2-panel button{margin-right:8px}
 .bulk-r2-invalid{
   border:2px solid #b91c1c!important;background:#fff1f2!important
 }
 .bulk-r2-error-text{
   display:block;color:#b91c1c!important;font-weight:600;
   margin:5px 0 10px;white-space:normal
 }
 .bulk-r2-feedback{font-weight:600;margin-top:8px}
 `;
 document.head.append(style);

 const panel=document.createElement('section');
 panel.className='bulk-r2-panel';
 panel.setAttribute('aria-label','Pendientes de revision del lote');

 const heading=document.createElement('strong');
 const list=document.createElement('ul');
 const feedback=document.createElement('div');
 feedback.className='bulk-r2-feedback';
 feedback.setAttribute('role','status');
 feedback.setAttribute('aria-live','polite');

 panel.append(heading,list,feedback);
 controls.parentNode.insertBefore(panel,controls);

 let queued=false;
 const previousAria=new WeakMap();

 function normalized(value){
  return String(value||'').normalize('NFD')
   .replace(/[\u0300-\u036f]/g,'').toLowerCase();
 }
 function errors(row){
  if(!row||row.excluded)return [];
  const result=(row.issues||[]).map(e=>
   typeof e==='string'?e:(e.message||JSON.stringify(e))
  );
  const values=row.values||{};
  if(Object.prototype.hasOwnProperty.call(values,'OFFBOARDING TYPE') &&
     !String(values['OFFBOARDING TYPE']||'').trim()){
   result.push('OFFBOARDING TYPE: selecciona el tipo o motivo de la baja.');
  }
  if(!String(values['EMPLOYEE NUMBER']||'').trim()){
   result.push('EMPLOYEE NUMBER: falta el numero de empleado.');
  }
  if(!row.form&&!result.length){
   result.push('Faltan datos validos. Guarda y valida para consultar el detalle.');
  }
  return [...new Set(result)];
 }
 function report(text,bad=false){
  feedback.textContent=text;
  feedback.style.color=bad?'#b91c1c':'#1e3a8a';
  E.message(text,bad);
 }
 function schedule(){
  if(queued)return;
  queued=true;
  requestAnimationFrame(()=>{queued=false;paint();});
 }
 function labelText(input){
  const label=input.labels?.[0]||input.closest('label');
  if(label){
   const copy=label.cloneNode(true);
   copy.querySelectorAll('input,select,textarea,small,.bulk-r2-error-text')
    .forEach(n=>n.remove());
   return normalized(copy.textContent);
  }
  const parent=input.parentElement;
  const explicit=parent?.querySelector('label');
  return normalized(explicit?.textContent||input.name||input.id);
 }

 const mappings=[
  [/tipo.*baja|motivo/,/offboarding.?type|termination.?type|tipo.*baja|motivo/],
  [/numero.*empleado/,/employee.?number|employee.?identifier|numero.*empleado|no.*encontr.*empleado/],
  [/^nombre/,/employee.?name|nombre.*empleado/],
  [/departamento/,/department|departamento/],
  [/^area/,/work.?area|area.*(requer|oblig|falt)|^area:/],
  [/fecha efectiva/,/effective|fecha/],
  [/correo/,/email|correo/],
  [/referencia.*computadora/,/computer.?details|referencia.*computadora/],
  [/referencia.*telefono/,/phone.?details|referencia.*telefono/],
  [/otros? accesos/,/other.?access|otros? accesos/],
  [/^accesos:/,/accesses|accesos.*(invalid|desconoc)/],
  [/observaciones/,/observation|observacion/],
  [/confidencial/,/confidential|confidencial/],
  [/face id/,/face.?id/],
  [/huella/,/fingerprint|huella/]
 ];

 const observer=new MutationObserver(schedule);
 function watch(){
  observer.observe(controls,{
   subtree:true,childList:true,attributes:true,attributeFilter:['disabled']
  });
 }

 function paint(){
  observer.disconnect();
  try{
   controls.querySelectorAll('.bulk-r2-error-text').forEach(n=>n.remove());
   controls.querySelectorAll('.bulk-r2-invalid').forEach(input=>{
    input.classList.remove('bulk-r2-invalid');
    const old=previousAria.get(input);
    if(old){
     for(const [key,value] of Object.entries(old)){
      if(value===null)input.removeAttribute(key);
      else input.setAttribute(key,value);
     }
     previousAria.delete(input);
    }
   });

   const included=E.rows().filter(r=>!r.excluded);
   const blocked=included.filter(r=>errors(r).length);
   const pending=included.filter(r=>!r.reviewed||r.dirty);
   const busy=E.isBusy();

   list.replaceChildren();
   panel.classList.toggle('ok',blocked.length===0);

   heading.textContent=F.frozen()?
    'Lote confirmado. Consulta los resultados de registro abajo.':
    `${included.length} incluidas · ${blocked.length} con errores · ${pending.length} pendientes de revision. Las excluidas no bloquean.`;

   if(!F.frozen()){
    const attention=included.filter(r=>errors(r).length||r.dirty||!r.reviewed);
    for(const row of attention.slice(0,20)){
     const li=document.createElement('li');
     const button=document.createElement('button');
     button.type='button';
     button.textContent='Empleado '+(row.values?.['EMPLOYEE NUMBER']||row.excelRow);
     button.disabled=busy;
     button.onclick=()=>{
      if(E.isBusy())return;
      E.focusRow(row.excelRow);schedule();
     };
     const messages=errors(row);
     const text=messages.length?messages.join(' | '):
      row.dirty?'Cambios pendientes de guardar y validar.':'Falta marcar como revisado.';
     li.append(button,document.createTextNode(text));
     list.append(li);
    }
    if(attention.length>20){
     const li=document.createElement('li');
     li.textContent=`Hay ${attention.length-20} filas pendientes adicionales.`;
     list.append(li);
    }
   }

   const current=E.current();
   const messages=errors(current);
   const detail=mark.closest('.card')||mark.closest('section')||
    mark.parentElement.parentElement;

   let index=0;
   if(current&&!current.excluded&&!F.frozen()){
    for(const input of detail.querySelectorAll('input,select,textarea')){
     const text=labelText(input);
     const mapping=mappings.find(([label])=>label.test(text));
     if(!mapping)continue;
     const matching=messages.filter(message=>mapping[1].test(normalized(message)));
     if(!matching.length)continue;

     previousAria.set(input,{
      'aria-invalid':input.getAttribute('aria-invalid'),
      'aria-describedby':input.getAttribute('aria-describedby')
     });

     input.classList.add('bulk-r2-invalid');
     input.setAttribute('aria-invalid','true');

     const help=document.createElement('small');
     help.id='bulk-r2-field-'+(++index);
     help.className='bulk-r2-error-text';
     help.textContent=matching.join(' ') +
      (input.readOnly?' Este dato procede del directorio y debe corregirse en origen.':'');

     const described=input.getAttribute('aria-describedby')||'';
     input.setAttribute('aria-describedby',(described+' '+help.id).trim());
     input.insertAdjacentElement('afterend',help);
    }
   }

   mark.disabled=busy||F.frozen()||!current||current.excluded;
   mark.textContent=F.frozen()?'Lote confirmado':
    current?.dirty?'Validar cambios para revisar':
    current?.reviewed?'Guardar revision e ir al pendiente':
    'Marcar revisado y siguiente';

   const register=document.getElementById('register');
   if(register){
    register.disabled=busy||!F.canRegister()||blocked.length>0;
    register.title=blocked.length?
     'Corrige los errores de las filas incluidas.':
     pending.length?'Revisa las filas pendientes.':
     'Confirmacion final: crea bajas reales.';
   }
  } finally {watch();}
 }

 function showFirstError(){
  schedule();
  requestAnimationFrame(()=>{
   const input=controls.querySelector('.bulk-r2-invalid');
   if(input){
    input.scrollIntoView({block:'center',behavior:'smooth'});
    input.focus({preventScroll:true});
   }else panel.scrollIntoView({block:'start',behavior:'smooth'});
  });
 }

 mark.onclick=()=>E.run(async()=>{
  try{
   if(F.frozen())return;
   let row=E.current();
   if(!row||row.excluded){
    report('Selecciona un empleado incluido en el lote.',true);return;
   }
   const number=row.excelRow;

   if(row.dirty||errors(row).length){
    await F.saveInsideRun();
    E.focusRow(number);
    row=E.current();

    if(errors(row).length){
     report('Esta fila aun tiene errores. Se muestran en rojo.',true);
     showFirstError();return;
    }

    report('Cambios guardados y validados. Revisa los datos actualizados y pulsa de nuevo para marcar la revision.');
    schedule();return;
   }

   if(!row.reviewed && row.warnings?.length &&
      !confirm('Revisa estos avisos:\n\n'+row.warnings.join('\n')+
       '\n\n¿Confirmas que revisaste los datos?')){
    report('La fila no se marco como revisada.');return;
   }

   const wasReviewed=row.reviewed;
   row.reviewed=true;
   try{
    await F.saveInsideRun();
   }catch(error){
    const current=E.rows().find(r=>r.excelRow===number);
    if(current)current.reviewed=wasReviewed;
    E.refresh();
    throw error;
   }

   row=E.rows().find(r=>r.excelRow===number);
   if(!row?.reviewed||errors(row).length){
    E.focusRow(number);
    report('La validacion actualizo datos o encontro un problema. Revisa nuevamente esta fila.',true);
    showFirstError();return;
   }

   const included=E.rows().filter(r=>!r.excluded);
   const start=included.findIndex(r=>r.excelRow===number);
   let next=null;
   for(let step=1;step<=included.length;step++){
    const candidate=included[(start+step)%included.length];
    if(!candidate.reviewed||candidate.dirty||errors(candidate).length){
     next=candidate;break;
    }
   }

   if(next){
    E.focusRow(next.excelRow);
    report('Revision guardada. Continua con el empleado '+
     next.values['EMPLOYEE NUMBER']+'.');
   }else{
    E.refresh();
    report('Revision completa y guardada. Ya puedes usar Confirmar y registrar bajas. Aun no se registro ninguna baja con este boton.');
    document.getElementById('register')?.scrollIntoView({
     block:'center',behavior:'smooth'
    });
   }
   schedule();
  }catch(error){
   report(error.message||'No se pudo guardar la revision. Intenta nuevamente.',true);
   schedule();
  }
 });

 controls.addEventListener('input',schedule);
 controls.addEventListener('change',schedule);
 paint();
})();
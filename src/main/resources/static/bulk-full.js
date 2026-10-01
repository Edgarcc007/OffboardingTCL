'use strict';
(() => {
 const E=window.BulkEditor;
 const $=id=>document.getElementById(id);
 let batch=null,page=0,stop=false,processing=false;

 function el(tag,text){
  const n=document.createElement(tag);
  if(text!==undefined)n.textContent=text;
  return n;
 }
 function frozen(){return !!batch&&batch.phase==='CONFIRMED';}
 function canRegister(){
  const included=E.rows().filter(r=>!r.excluded);
  return !!batch&&!frozen()&&included.length>0&&included.every(
   r=>!r.dirty&&r.reviewed&&r.form&&r.issues.length===0
  );
 }
 async function api(method,path,body){
  const response=await E.request('/api/bulk'+path,{
   method,headers:E.headers('application/json'),
   ...(body!==undefined?{body:JSON.stringify(body)}:{})
  });
  return response.json();
 }
 function reset(){
  stop=true;batch=null;processing=false;
  $('fullResults').replaceChildren();$('batchHistory').replaceChildren();
  $('fullInfo').textContent='Sin lote cargado.';
 }
 function apply(value){
  if(value.ownerId!==E.owner())throw new Error('Cambio la sesion. Vuelve a iniciar sesion.');
  batch=value;E.load(value);render();
 }
 function render(){
  $('fullInfo').textContent=batch?
   `Lote ${batch.id} · Versión ${batch.version} · ${batch.phase==='DRAFT'?'Borrador':'Confirmado — datos bloqueados'}`:
   'Sin lote cargado.';
  $('resumeBulk').disabled=!frozen()||processing;
  $('copyPending').disabled=!frozen()||processing;
  $('pauseBulk').disabled=!processing;

  const box=$('fullResults');box.replaceChildren();
  if(!batch)return;
  if(batch.phase==='DRAFT'){
   const details=el('details');details.append(el('summary','Tareas previstas por empleado'));
   for(const r of batch.document.rows){
    if(!r.included)continue;
    details.append(el('h3',(r.values['EMPLOYEE NUMBER']||'')+' · '+(r.employee?.name||'Sin datos')));
    const ul=el('ul');
    for(const t of r.tasks){
     ul.append(el('li',`${t.system}: ${t.name} · Límite: ${t.dueAt}`));
    }
    if(!r.tasks.length)ul.append(el('li','Sin tareas previstas; verifica las opciones seleccionadas.'));
    details.append(ul);
   }
   box.append(details);
  }else{
   const done=batch.results.filter(r=>r.state==='REGISTERED').length;
   box.append(el('p',`${done} registradas de ${batch.results.length}`));
   const table=el('table');table.style.width='100%';
   const head=el('tr');
   ['Fila Excel','Estado','Folio / aviso'].forEach(t=>head.append(el('th',t)));
   table.append(head);
   for(const r of batch.results){
    const tr=el('tr');
    tr.append(el('td',r.excelRow),el('td',r.state),
      el('td',r.caseNumber||r.error||'Pendiente'));
    table.append(tr);
   }
   box.append(table);
  }
 }
 async function saveInsideRun(){
  if(!batch)throw new Error('Carga un archivo primero.');
  if(frozen())throw new Error('El lote confirmado no puede editarse.');
  apply(await api('PUT','/batches/'+batch.id,{
   version:batch.version,name:batch.name,rows:E.payload()
  }));
 }
 async function history(){
  const data=await api('GET','/batches?page='+page);
  const select=$('batchHistory');select.replaceChildren();
  for(const b of data){
   const option=el('option',`${b.name} · ${b.phase} · ${b.registered}/${b.total} registradas · ${b.updatedAt}`);
   option.value=b.id;select.append(option);
  }
  if(!data.length)E.message('No hay lotes en esta página.');
 }
 async function process(){
  if(!frozen())throw new Error('Confirma primero el lote.');
  const id=batch.id;
  stop=false;processing=true;render();
  try{
   for(const original of [...batch.results]){
    if(stop||batch?.id!==id)break;
    if(original.state==='REGISTERED')continue;
    E.message(`Registrando fila ${original.excelRow}. No cierres la pestaña durante esta solicitud.`);
    const result=await api('POST',`/batches/${id}/rows/${original.excelRow}/register`);
    if(batch?.id!==id)return;
    const index=batch.results.findIndex(r=>r.excelRow===result.excelRow);
    batch.results[index]=result;render();
    if(result.state!=='REGISTERED'){
     E.message('Se detuvo el proceso en una fila con error. Los casos registrados se conservan.',true);
     return;
    }
   }
   E.message(stop?'Proceso pausado. Puedes retomarlo desde Mis lotes.':
    'Procesamiento terminado. Revisa los folios y resultados.');
  }finally{processing=false;render();}
 }
 window.BulkFull={
  frozen,canRegister,reset,saveInsideRun,
  acceptUploaded:value=>{stop=true;apply(value);}
 };
 $('register').textContent='Confirmar y registrar bajas';
 $('register').title='Crea casos reales, tareas, auditoría y notificaciones';
 $('validate').textContent='Guardar y validar lote';
 $('register').onclick=()=>E.run(async()=>{
  await saveInsideRun();
  if(!canRegister()){
   E.message('Hay cambios o avisos pendientes. Revisa nuevamente las filas antes de confirmar.',true);return;
  }
  const count=E.rows().filter(r=>!r.excluded).length;
  if(!confirm(`Se crearán ${count} bajas REALES con las tareas y notificaciones actuales. ¿Confirmas?`))return;
  apply(await api('POST',`/batches/${batch.id}/confirm?version=${batch.version}`));
  await process();
 });
 $('resumeBulk').onclick=()=>E.run(async()=>{
  apply(await api('GET','/batches/'+batch.id));
  if(confirm('¿Retomar las filas pendientes o fallidas del mismo lote?'))await process();
 });
 $('pauseBulk').onclick=()=>{
  stop=true;E.message('Se pausará al terminar la solicitud actual. Una baja ya enviada no se cancela.');
 };
 $('copyPending').onclick=()=>E.run(async()=>{
  if(confirm('Crear otro borrador solamente con las filas no registradas. Tendrás que revisarlas nuevamente.')){
   apply(await api('POST','/batches/'+batch.id+'/copy-pending'));
   E.message('Nuevo borrador creado. Los casos ya registrados no se copiaron.');
  }
 });
 $('loadHistory').onclick=()=>E.run(history);
 $('openBatch').onclick=()=>E.run(async()=>{
  const id=$('batchHistory').value;
  if(!id)return;
  if(batch&&!confirm('Abrir otro lote descartará cambios que aún no hayas guardado. ¿Continuar?'))return;
  apply(await api('GET','/batches/'+id));
 });
 $('historyPrevious').onclick=()=>E.run(async()=>{page=Math.max(0,page-1);await history();});
 $('historyNext').onclick=()=>E.run(async()=>{page++;await history();});
 render();E.refresh();
})();
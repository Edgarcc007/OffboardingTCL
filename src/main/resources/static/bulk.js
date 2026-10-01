'use strict';
(() => {
 const $ = id => document.getElementById(id);
 const TYPES = ['RENUNCIA','DESPIDO','FIN_DE_CONTRATO','JUBILACION','MUTUO_ACUERDO','FALLECIMIENTO'];
 const FIELDS = [
  ['EMPLOYEE NUMBER','Número de empleado','text',60],
  ['OFFBOARDING TYPE','Tipo de baja','type'],
  ['EFFECTIVE DATE','Fecha efectiva: aaaa-MM-dd HH:mm o ISO','text',80],
  ['CORPORATE EMAIL','Correo corporativo','email',150],
  ['FACE ID','Face ID','check'],['FINGERPRINT','Huella','check'],
  ['COMPUTER ASSIGNED','Computadora asignada','check'],
  ['COMPUTER DETAILS','Referencia de computadora','text',250],
  ['PHONE ASSIGNED','Teléfono asignado','check'],
  ['PHONE DETAILS','Referencia de teléfono','text',250],
  ['CONFIDENTIAL','Confidential','check'],
  ['ACCESSES','Accesos: WINDOWS;OFFICE;SMES;CMP;VPN;OTHER','text',150],
  ['OTHER ACCESSES','Otros accesos','area',1000],
  ['OBSERVATIONS','Observaciones','area',4000]
 ];
 let session=null, rows=[], active=null, busy=false;

 function node(tag,text,cls) {
  const n=document.createElement(tag);
  if(text!==undefined)n.textContent=text;
  if(cls)n.className=cls;
  return n;
 }
 function message(text,bad=false){$('message').textContent=text;$('message').className=bad?'bad':'';}
 function clear(){
  rows=[];active=null;$('file').value='';if(window.BulkFull)window.BulkFull.reset();render();
 }
 function setBusy(value){
  busy=value;$('controls').disabled=value||!session||(window.BulkFull&&window.BulkFull.frozen());
  buttons();
 }
 async function request(path,options={}){
  const response=await fetch(path,{credentials:'same-origin',cache:'no-store',...options});
  if(response.status===401||response.status===403){
   session=null;clear();$('controls').disabled=true;
   $('identity').textContent='Sesión no disponible para esta operación.';
   throw new Error('Inicia sesión con una cuenta ADMIN o Recursos Humanos habilitada.');
  }
  if(!response.ok){
   const body=await response.text();let reason;
   try{const j=JSON.parse(body);reason=j.message||j.detail||j.error;}catch{}
   throw new Error(reason||('La solicitud falló: HTTP '+response.status));
  }
  return response;
 }
 async function refreshSession(){
  const next=await (await request('/api/bulk/session')).json();
  if(session&&session.owner.id!==next.owner.id){
   clear();session=next;
   $('identity').textContent=`Usuario: ${next.owner.username} · Zona: ${next.timeZone}`;
   throw new Error('Cambió el usuario de la sesión. Se descartó el borrador anterior.');
  }
  session=next;
  $('identity').textContent=`Usuario: ${next.owner.username} · Zona de las fechas: ${next.timeZone}`;
 }
 async function run(action){
  if(busy)return;
  setBusy(true);
  try{await refreshSession();await action();}
  catch(error){message(error.message||'No se pudo completar la operación.',true);}
  finally{setBusy(false);}
 }
 function headers(type){return {'Content-Type':type,[session.csrfHeader]:session.csrfToken};}
 function signature(r){
  return JSON.stringify([r.values,r.employee,r.form,r.issues,r.warnings]);
 }
 function accept(data,merge){
  if(data.owner.id!==session.owner.id){
   clear();throw new Error('La respuesta pertenece a otra sesión.');
  }
  const incoming=new Map(data.rows.map(r=>[r.excelRow,r]));
  if(!merge){
   rows=data.rows.map(r=>({...r,values:{...r.excelValues},selected:true,excluded:false,
     dirty:false,reviewed:false,overrides:new Set(),origins:{}}));
   active=rows[0]||null;
  }else{
   for(const r of rows){
    if(r.excluded)continue;
    const fresh=incoming.get(r.excelRow);
    if(!fresh)throw new Error('La respuesta no contiene todas las filas enviadas.');
    const old=signature(r),wasReviewed=r.reviewed,wasDirty=r.dirty;
    Object.assign(r,{...fresh,values:{...fresh.excelValues},dirty:false});
    r.reviewed=wasReviewed&&!wasDirty&&old===signature(r);
   }
  }
  render();
 }
 function valid(r){return !r.excluded&&!r.dirty&&!!r.form&&!!r.employee&&r.issues.length===0;}
 function state(r){
  if(r.excluded)return ['Excluido',''];
  if(r.dirty)return ['Modificado: validar','pending'];
  if(r.issues.length)return ['Con errores / faltantes','bad'];
  if(r.reviewed)return ['Revisado','ok'];
  return ['Pendiente de revisión','pending'];
 }
 function list(){
  const filter=$('search').value.toLocaleLowerCase();
  const fragment=document.createDocumentFragment();
  for(const r of rows){
   const number=r.values['EMPLOYEE NUMBER']||'Sin número';
   const name=r.employee?.name||'Datos pendientes';
   if(!(number+' '+name).toLocaleLowerCase().includes(filter))continue;
   const item=node('div',undefined,'person'+(r===active?' active':'')+(r.excluded?' excluded':''));
   const check=node('input');check.type='checkbox';check.checked=r.selected;
   check.disabled=r.excluded;check.setAttribute('aria-label','Seleccionar '+number);
   check.onchange=()=>{r.selected=check.checked;summary();};
   const button=node('button');button.type='button';
   button.append(node('strong',number),node('span',name));
   const [text,cls]=state(r);button.append(node('span',text,'state '+cls));
   button.onclick=()=>{active=r;render();};
   item.append(check,button);fragment.append(item);
  }
  $('rows').replaceChildren(fragment);
 }
 function summary(){
  const included=rows.filter(r=>!r.excluded);
  $('summary').textContent=rows.length?
   `${rows.length} filas · ${included.length} incluidas · ${rows.filter(r=>r.excluded).length} excluidas · `+
   `${included.filter(r=>valid(r)&&r.reviewed).length} revisadas · `+
   `${included.filter(r=>r.dirty).length} por validar`:'Carga un Excel para comenzar.';
 }
 function buttons(){
  for(const id of ['apply','validate','markSelected','selectAll','selectNone'])
   $(id).disabled=busy||rows.length===0;
  for(const id of ['previous','next','exclude'])$(id).disabled=busy||!active;
  $('mark').disabled=busy||!active||!valid(active);
  $('register').disabled=busy||!window.BulkFull||!window.BulkFull.canRegister();
 }
 function snapshot(){
  const box=$('directory');box.replaceChildren();
  if(!active)return;
  $('personTitle').textContent='Empleado '+(active.values['EMPLOYEE NUMBER']||'sin número');
  if(!active.employee){box.append(node('p','Los datos laborales se mostrarán después de una consulta válida.','muted'));return;}
  box.append(node('p','Datos del directorio · Solo lectura','muted'));
  const grid=node('div',undefined,'grid');
  for(const [key,label] of [['code','Código'],['number','Número interno'],['name','Nombre'],
   ['department','Departamento'],['area','Área'],['manager','Jefe'],['position','Puesto'],['shift','Turno']]){
   const l=node('label',label),input=node('input');
   input.readOnly=true;input.value=active.employee[key]??'';l.append(input);grid.append(l);
  }
  box.append(grid);
 }
 function change(r,key,value,origin){
  if(r.values[key]===value)return false;
  r.values[key]=value;r.dirty=true;r.reviewed=false;r.origins[key]=origin;
  if(origin==='individual')r.overrides.add(key);
  if(key==='EMPLOYEE NUMBER'){r.employee=null;r.form=null;snapshot();}
  return true;
 }
 function details(){
  $('editor').replaceChildren();$('issues').replaceChildren();snapshot();
  if(!active){$('personTitle').textContent='Formulario individual';return;}
  const r=active;
  if(r.dirty)$('issues').append(node('p','Cambios pendientes de validar. Los avisos siguientes corresponden a la última consulta.','pending'));
  for(const [items,cls] of [[r.issues,'bad'],[r.warnings,'pending']]){
   if(items.length){const ul=node('ul',undefined,cls);items.forEach(t=>ul.append(node('li',t)));$('issues').append(ul);}
  }
  $('exclude').textContent=r.excluded?'Reincluir en el lote':'Excluir del lote';
  for(const [key,label,kind,max] of FIELDS){
   const wrap=node('label',undefined,kind==='check'?'check':'');
   const title=node('span',label),source=node('small',r.origins[key]||'Excel / predeterminados','source');
   let input;
   if(kind==='type'){
    input=node('select');
    for(const value of ['',...TYPES]){const o=node('option',value||'Seleccionar…');o.value=value;input.append(o);}
    if(r.values[key]&&!TYPES.includes(r.values[key])){
     const o=node('option','Inválido: '+r.values[key]);o.value=r.values[key];input.append(o);
    }
    input.value=r.values[key]||'';
   }else{
    input=node(kind==='area'?'textarea':'input');
    if(kind==='check'){
     input.type='checkbox';
     input.checked=r.values[key]==='SI';
     input.indeterminate=!['SI','NO'].includes(r.values[key]);
    }else{
     if(kind!=='area')input.type=kind;
     input.value=r.values[key]||'';if(max)input.maxLength=max;
    }
   }
   input.disabled=r.excluded;
   input.oninput=()=>{
    const value=kind==='check'?(input.checked?'SI':'NO'):input.value;
    if(kind==='check')input.indeterminate=false;
    change(r,key,value,'individual');source.textContent='Personalizado';
    list();summary();buttons();
   };
   if(kind==='check')wrap.append(input,title,source);else wrap.append(title,input,source);
   $('editor').append(wrap);
  }
 }
 function render(){list();summary();details();buttons();}
 function move(delta){
  if(!active)return;
  const index=rows.indexOf(active),next=rows[index+delta];
  if(next){active=next;render();}
 }
 function common(){
  const keys=['OFFBOARDING TYPE','EFFECTIVE DATE','FACE ID','FINGERPRINT',
   'COMPUTER ASSIGNED','PHONE ASSIGNED','CONFIDENTIAL','OBSERVATIONS'];
  for(const key of keys){
   const spec=FIELDS.find(f=>f[0]===key),label=node('label',spec[1]);
   let control;
   if(spec[2]==='check'||spec[2]==='type'){
    control=node('select');
    const choices=spec[2]==='check'?['SI','NO']:TYPES;
    for(const value of ['',...choices]){
     const option=node('option',value||'Sin cambios');option.value=value;control.append(option);
    }
   }else{
    control=node(spec[2]==='area'?'textarea':'input');
    if(spec[2]!=='area')control.type='text';
    control.placeholder='Vacío = sin cambios';
    control.maxLength=spec[3];
   }
   control.dataset.common=key;label.append(control);$('common').append(label);
  }
 }
 $('template').onclick=()=>run(async()=>{
  const response=await request('/api/bulk/template');
  const url=URL.createObjectURL(await response.blob());
  const a=node('a');a.href=url;a.download='Bajas-TCL.xlsx';document.body.append(a);a.click();a.remove();
  setTimeout(()=>URL.revokeObjectURL(url),1000);
  message('Plantilla descargada. Completa solamente BAJAS.');
 });
 $('upload').onclick=()=>{
  const file=$('file').files[0];
  if(!file){message('Selecciona un archivo .xlsx.',true);return;}
  if(!/\.xlsx$/i.test(file.name)||file.size>8*1024*1024){message('Utiliza un XLSX de hasta 8 MB.',true);return;}
  if(rows.length&&!confirm('La nueva carga reemplazará el borrador de esta pestaña. ¿Continuar?'))return;
  run(async()=>{
   message('Leyendo Excel y consultando empleados. No se registran bajas…');
   const response=await request('/api/bulk/batches/upload',{method:'POST',
    headers:{...headers('application/octet-stream'),'X-File-Name':encodeURIComponent(file.name)},body:file});
   window.BulkFull.acceptUploaded(await response.json());
   message('Vista previa preparada. Revisa faltantes y excepciones.');
  });
 };
 $('apply').onclick=()=>{
  const patch={};
  for(const control of document.querySelectorAll('[data-common]'))
   if(control.value!=='')patch[control.dataset.common]=control.value;
  const targets=rows.filter(r=>r.selected&&!r.excluded);
  if(!Object.keys(patch).length||!targets.length){message('Selecciona filas y al menos un valor común.',true);return;}
  let changes=0,skipped=0;
  for(const r of targets)for(const [key,value] of Object.entries(patch)){
   if(r.overrides.has(key)&&!$('overwrite').checked){skipped++;continue;}
   if(change(r,key,value,'lote'))changes++;
  }
  render();message(`${changes} campos cambiados. ${skipped} excepciones individuales conservadas. Valida los cambios.`);
 };
  $('validate').onclick=()=>run(async()=>{
  await window.BulkFull.saveInsideRun();
  message('Lote guardado y validado. Revisa los avisos antes de registrar.');
 });
 $('mark').onclick=()=>{
  if(!active||!valid(active))return;
  if(active.warnings.length&&!confirm(active.warnings.join('\n')+'\n\n¿Confirmas que revisaste estos avisos?'))return;
  active.reviewed=true;move(1);render();
 };
 $('markSelected').onclick=()=>{
  const selected=rows.filter(r=>r.selected&&!r.excluded);
  const eligible=selected.filter(r=>valid(r)&&r.warnings.length===0);
  if(!eligible.length){message('No hay seleccionados válidos sin avisos pendientes.',true);return;}
  if(!confirm(`Marcar ${eligible.length} filas como revisadas. ${selected.length-eligible.length} permanecerán pendientes. No se registrarán bajas.`))return;
  eligible.forEach(r=>r.reviewed=true);render();
 };
 $('exclude').onclick=()=>{
  if(!active)return;
  active.excluded=!active.excluded;active.selected=!active.excluded;
  for(const r of rows)if(!r.excluded){r.dirty=true;r.reviewed=false;}
  render();message('Cambió el conjunto de filas. Valida nuevamente para comprobar duplicados.');
 };
 $('previous').onclick=()=>move(-1);$('next').onclick=()=>move(1);
 $('search').oninput=list;
 $('selectAll').onclick=()=>{rows.forEach(r=>r.selected=!r.excluded);list();};
 $('selectNone').onclick=()=>{rows.forEach(r=>r.selected=false);list();};
 window.addEventListener('beforeunload',event=>{
  if(rows.length){event.preventDefault();event.returnValue='';}
 });
 window.addEventListener('focus',()=>{
  if(session&&!busy)refreshSession().catch(error=>{
   message(error.message,true);$('controls').disabled=!session;
  });
 });
  /* BULK_FULL_BRIDGE */
 window.BulkEditor={
  /* BULK_REVIEW_UI_BRIDGE_R2 */
  current:()=>active,
  isBusy:()=>busy,
  focusRow:number=>{
   const row=rows.find(r=>r.excelRow===number);
   if(!row)return false;
   active=row;render();return true;
  },
  run,request,headers,message,
  owner:()=>session?.owner?.id,
  rows:()=>rows,
  load:b=>{
   const previous=active?.excelRow;
   rows=b.document.rows.map(r=>({
    ...r,excelValues:{...r.values},values:{...r.values},
    selected:r.included,excluded:!r.included,dirty:false,
    overrides:new Set(r.personalized||[]),
    origins:Object.fromEntries((r.personalized||[]).map(k=>[k,'individual']))
   }));
   active=rows.find(r=>r.excelRow===previous)||rows[0]||null;
   render();
  },
  payload:()=>rows.map(r=>({
   excelRow:r.excelRow,values:r.values,included:!r.excluded,
   reviewed:r.reviewed&&!r.dirty,
   fingerprint:r.fingerprint||null,
   personalized:Array.from(r.overrides||[])
  })),
  refresh:()=>{render();$('controls').disabled=busy||!session||
    !!(window.BulkFull&&window.BulkFull.frozen());}
 };
 common();render();
 run(async()=>{message('Sesión lista. Descarga la plantilla o carga tu archivo.');});
})();
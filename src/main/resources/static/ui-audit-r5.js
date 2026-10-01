'use strict';
(() => {
 const byId=id=>document.getElementById(id);

 // Tab usa el onblur original. Enter mueve el foco y dispara ese mismo onblur.
 const employee=byId('idf');
 if(employee){
  employee.addEventListener('keydown',event=>{
   if(event.key!=='Enter'||event.isComposing)return;
   event.preventDefault();
   if(event.repeat)return;
   const next=byId('n');
   if(next)next.focus();else employee.blur();
  });
 }

 const style=document.createElement('style');
 style.textContent=`
 .r5-employee-number .detail-value{font-variant-numeric:tabular-nums;letter-spacing:.02em}
 #r5-audit .r5-filters{
  display:grid;grid-template-columns:150px 150px 170px minmax(180px,1fr);
  gap:12px;align-items:end;margin-bottom:14px
 }
 #r5-audit .r5-filters label{margin:0;text-transform:none}
 #r5-audit .r5-buttons{display:flex;gap:8px;justify-content:flex-end;flex-wrap:wrap}
 #r5-audit button{margin-top:0}
 #r5-audit .r5-stats{display:flex;gap:12px;flex-wrap:wrap;margin:16px 0}
 #r5-audit .r5-stat{
  flex:1;min-width:150px;background:#f7f7f7;border:1px solid #e1e1e1;
  border-radius:9px;padding:13px 16px
 }
 #r5-audit .r5-stat strong{display:block;font-size:23px;color:#303030}
 #r5-audit .r5-stat span{font-size:12px;color:#666}
 #r5-audit td{padding:13px 10px}
 #r5-audit .r5-muted{color:#747474;font-size:12px;margin-top:4px}
 #r5-audit .r5-badge{
  display:inline-block;padding:4px 9px;border-radius:6px;
  background:#ededed;color:#444;font-size:12px;font-weight:700
 }
 #r5-audit .r5-green{background:#e7f5eb;color:#176639}
 #r5-audit .r5-blue{background:#eaf1fc;color:#24558d}
 #r5-audit .r5-amber{background:#fff0d8;color:#885b10}
 #r5-audit .r5-number{font-weight:700;font-variant-numeric:tabular-nums}
 #r5-audit .r5-message{white-space:pre-wrap;font-size:13px;margin:10px 0}
 #r5-audit .r5-error{color:#b91c1c}
 #r5-audit .r5-footer{display:flex;align-items:center;justify-content:space-between;gap:12px;margin-top:14px}
 #r5-audit .r5-pager{display:flex;gap:8px}
 #r5-audit summary{cursor:pointer;color:#444;font-weight:600}
 #r5-audit pre{
  white-space:pre-wrap;overflow-wrap:anywhere;font:12px/1.5 var(--font);
  background:#f5f5f5;border:1px solid #ddd;padding:10px;margin-top:8px;
  max-width:500px;max-height:300px;overflow:auto
 }
 #r5-audit button:disabled{opacity:.5;cursor:default;transform:none}
 @media(max-width:1000px){#r5-audit .r5-filters{grid-template-columns:1fr 1fr}}
 @media(max-width:600px){#r5-audit .r5-filters{grid-template-columns:1fr}}
 `;
 document.head.append(style);

 const section=byId('seccion_auditoria'),body=byId('audit_body');
 if(!section||!body)return;

 const node=(tag,text,className)=>{
  const element=document.createElement(tag);
  if(text!==undefined)element.textContent=String(text);
  if(className)element.className=className;
  return element;
 };

 const root=node('div');root.id='r5-audit';
 root.innerHTML=`
 <div class="r5-filters">
  <label>From<input id="r5-from" type="date"></label>
  <label>To<input id="r5-to" type="date"></label>
  <label>Search by<select id="r5-field">
   <option value="ALL">All fields</option>
   <option value="EMPLOYEE">Employee number</option>
   <option value="NAME">Employee name</option>
   <option value="ASSET">Asset ID</option>
  </select></label>
  <label>Search<input id="r5-query" type="search" maxlength="256"
   placeholder="Name, employee number, Asset ID, case..." autocomplete="off"></label>
 </div>
 <div class="r5-buttons">
  <button id="r5-clear" type="button">Clear filters</button>
  <button id="r5-refresh" type="button">Refresh</button>
  <button id="r5-export" type="button" disabled>Export Excel</button>
 </div>
 <div id="r5-zone" class="r5-muted"></div>
 <div class="r5-stats">
  <div class="r5-stat"><strong id="r5-total">0</strong><span>Matching events</span></div>
  <div class="r5-stat"><strong id="r5-cases">0</strong><span>Related cases</span></div>
  <div class="r5-stat"><strong id="r5-actors">0</strong><span>Actors</span></div>
 </div>
 <div id="r5-message" class="r5-message" role="status" aria-live="polite"></div>
 <div class="table-wrapper"><table>
 <thead><tr><th>Date / time</th><th>Activity</th><th>Employee / case</th><th>Actor</th><th>Details</th></tr></thead>
 <tbody id="r5-rows"></tbody></table></div>
 <div class="r5-footer">
  <span id="r5-page" class="r5-muted"></span>
  <div class="r5-pager">
   <button id="r5-prev" type="button" disabled>Previous</button>
   <button id="r5-next" type="button" disabled>Next</button>
  </div>
 </div>`;
 body.replaceChildren(root);

 const $=id=>root.querySelector('#'+id);
 const allowed=()=>typeof tieneRol==='function'&&(tieneRol('ADMIN')||tieneRol('AUDITOR'));
 const visible=()=>allowed()&&!section.classList.contains('hidden')&&
  !section.classList.contains('ob-tab-inactive')&&!body.classList.contains('hidden');

 let offset=0,snapshot='',report=null,controller=null,sequence=0,timer=null;
 let downloading=false,loading=false,lastVisible=false;

 function notice(text,error=false){
  $('r5-message').textContent=text||'';
  $('r5-message').className='r5-message'+(error?' r5-error':'');
 }

 function parameters(){
  const values=new URLSearchParams();
  if($('r5-from').value)values.set('from',$('r5-from').value);
  if($('r5-to').value)values.set('to',$('r5-to').value);
  values.set('q',$('r5-query').value.trim());
  values.set('field',$('r5-field').value);
  if(snapshot)values.set('snapshot',snapshot);
  return values;
 }

 async function errorResponse(response){
  let data;
  try{data=await response.json();}catch{}
  if(response.status===401||response.redirected)return 'Session expired. Sign in again.';
  return data?.detail||data?.message||`HTTP ${response.status}`;
 }

 function controls(){
  $('r5-export').disabled=loading||downloading||!report||report.total===0;
  $('r5-prev').disabled=loading||offset===0;
  $('r5-next').disabled=loading||!report||offset+report.pageSize>=report.total;
 }

 function badgeClass(code){
  if(code==='TASK_VALIDATED')return 'r5-badge r5-green';
  if(code==='TASK_REOPENED')return 'r5-badge r5-amber';
  if(['CASE_CREATED','TASK_COMPLETED','BULK_CASE_LINKED'].includes(code))
   return 'r5-badge r5-blue';
  return 'r5-badge';
 }

 function render(){
  const rows=$('r5-rows');rows.replaceChildren();
  $('r5-total').textContent=report.total.toLocaleString();
  $('r5-cases').textContent=report.cases.toLocaleString();
  $('r5-actors').textContent=report.actors.toLocaleString();
  $('r5-zone').textContent=`Dates use ${report.zone}. The final date includes the entire day.`;
  if(byId('audit_count'))byId('audit_count').textContent=`${report.total} events`;

  const dateFormat=new Intl.DateTimeFormat('en-GB',{
   timeZone:report.zone,year:'numeric',month:'short',day:'2-digit',
   hour:'2-digit',minute:'2-digit',second:'2-digit',hour12:false
  });

  for(const event of report.items){
   const row=node('tr');

   const date=node('td');
   const instant=new Date(event.timestamp);
   date.append(node('div',Number.isNaN(instant.getTime())?event.timestamp:dateFormat.format(instant)));
   date.append(node('div',`Event #${event.id}`,'r5-muted'));

   const activity=node('td');
   activity.append(node('span',event.action,badgeClass(event.code)));
   if(event.system)activity.append(node('div',event.system,'r5-muted'));
   if(event.task)activity.append(node('div',event.task,'r5-muted'));

   const person=node('td');
   if(event.employeeNumber)person.append(node('div',`Employee # ${event.employeeNumber}`,'r5-number'));
   if(event.employeeName)person.append(node('div',event.employeeName));
   if(event.caseNumber)person.append(node('div',event.caseNumber,'r5-muted'));
   if(event.assets)person.append(node('div',`Asset ID: ${event.assets}`,'r5-muted'));
   if(!event.employeeNumber&&!event.caseNumber)
    person.append(node('span','Not linked to an offboarding case','r5-muted'));

   const actor=node('td',event.actor||'—');
   const extra=node('td'),details=node('details');
   details.append(node('summary','View details'));
   details.append(node('pre',
    `${event.code}\n${event.entityType} #${event.entityId}\n\n${event.details||'No additional details.'}`));
   extra.append(details);
   // R7_AUDIT_ACTIONS
   if(typeof tieneRol==='function'&&tieneRol('ADMIN')){
    for(const [caption,action] of [['Edit details','edit'],['Delete','delete']]){
     const button=node('button',caption,'mini');button.type='button';
     button.onclick=()=>window.R7Maintenance.open('audit',event.id,action);
     extra.append(button);
    }
   }

   row.append(date,activity,person,actor,extra);
   rows.append(row);
  }

  if(!report.items.length){
   const row=node('tr'),cell=node('td','No events match these filters.');
   cell.colSpan=5;cell.className='empty-row';row.append(cell);rows.append(row);
  }

  const first=report.items.length?offset+1:0;
  const last=offset+report.items.length;
  $('r5-page').textContent=`${first}–${last} of ${report.total} events`;
 }

 async function load(fresh=false){
  if(!visible())return;
  if(fresh)snapshot='';
  if(controller)controller.abort();
  controller=new AbortController();
  const ticket=++sequence;
  loading=true;controls();notice('Loading audit events...');

  const p=parameters();p.set('offset',offset);
  try{
   const response=await fetch('/api/audit/explorer?'+p,{
    credentials:'same-origin',cache:'no-store',signal:controller.signal,
    headers:{Accept:'application/json'}
   });
   if(!response.ok||response.redirected)throw new Error(await errorResponse(response));
   const data=await response.json();
   if(ticket!==sequence)return;

   report=data;snapshot=data.snapshot;
   render();notice('');
  }catch(error){
   if(error.name!=='AbortError'&&ticket===sequence){
    report=null;notice(error.message,true);
   }
  }finally{
   if(ticket===sequence){loading=false;controls();}
  }
 }

 function changed(){
  offset=0;snapshot='';report=null;
  clearTimeout(timer);
  if(controller)controller.abort();
  ++sequence;loading=false;controls();
  timer=setTimeout(()=>load(true),300);
 }

 $('r5-query').addEventListener('input',changed);
 ['r5-from','r5-to','r5-field'].forEach(id=>$(id).addEventListener('change',changed));

 $('r5-clear').onclick=()=>{
  $('r5-from').value='';$('r5-to').value='';$('r5-query').value='';
  $('r5-field').value='ALL';changed();
 };
 $('r5-refresh').onclick=()=>{clearTimeout(timer);offset=0;load(true);};
 $('r5-prev').onclick=()=>{offset=Math.max(0,offset-50);load(false);};
 $('r5-next').onclick=()=>{offset+=50;load(false);};

 $('r5-export').onclick=()=>{
  // DIRECT_AUDIT_DOWNLOAD_R6D
  if(downloading||loading||!report||!allowed())return;

  if(report.total>20000){
   notice('More than 20000 events. Narrow the date range or search.',true);
   return;
  }

  downloading=true;controls();
  const link=document.createElement('a');
  link.href='/api/audit/explorer/export?'+parameters().toString();
  link.target='_blank';
  link.rel='noopener';
  document.body.append(link);
  link.click();
  link.remove();

  notice('Excel requested from the server. Confirm the save dialog and check browser Downloads. This message does not confirm that saving has finished.');
  setTimeout(()=>{downloading=false;controls();},3000);
 };

 // Las llamadas existentes de refresco utilizan la nueva vista.
 window.cargarAuditoria=()=>load(true);

 function observe(){
  const now=visible();
  if(!allowed()){
   if(controller)controller.abort();
   ++sequence;loading=false;report=null;snapshot='';
   $('r5-rows').replaceChildren();controls();
  }
  if(now&&!lastVisible)load(true);
  lastVisible=now;
 }

 const observer=new MutationObserver(observe);
 observer.observe(section,{attributes:true,attributeFilter:['class']});
 observer.observe(body,{attributes:true,attributeFilter:['class']});
 observe();
})();
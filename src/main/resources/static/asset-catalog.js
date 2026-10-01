// R7_ASSET_VIEW
'use strict';
(() => {
 const capture=document.getElementById('seccion_captura');
 const bulk=document.getElementById('bulk-simple-open');
 if(!capture||!bulk)return;

 const make=(tag,text)=>{
  const element=document.createElement(tag);
  if(text!==undefined)element.textContent=String(text);
  return element;
 };
 const permitted=()=>typeof tieneRol==='function'&&
  (tieneRol('ADMIN')||tieneRol('RECURSOS_HUMANOS')||tieneRol('IT_ENGINEER_VALIDATOR'));

 const button=make('button','Search assets');button.id='asset-catalog-open';
 button.type='button';button.hidden=true;
 button.style.margin='0 0 16px';
 bulk.insertAdjacentElement('afterend',button);

 const style=make('style');
 style.textContent=`
 #asset-catalog-dialog{
  position:fixed;inset:0;margin:auto;width:min(1100px,calc(100vw - 30px));
  max-height:90vh;overflow:auto;padding:24px;border:1px solid #ddd;
  border-radius:12px;background:white;color:#242424;box-shadow:0 20px 60px #0004
 }
 #asset-catalog-dialog::backdrop{background:#0008}
 #asset-catalog-dialog .ac-actions{display:flex;gap:8px;justify-content:flex-end}
 #asset-catalog-dialog .ac-message{margin:12px 0;white-space:pre-wrap}
 #asset-catalog-dialog .ac-details{
  display:grid;grid-template-columns:repeat(auto-fit,minmax(240px,1fr));
  gap:12px;padding:16px;background:#f5f5f5;border-radius:8px;margin:12px 0
 }
 #asset-catalog-dialog .ac-value{white-space:pre-wrap;overflow-wrap:anywhere}
 `;
 document.head.append(style);

 const dialog=make('dialog');
 dialog.id='asset-catalog-dialog';
 dialog.innerHTML=`
 <h2>Asset inventory</h2>
 <label for="ac-search">Search any field — user, Asset ID, equipment, serial, location...</label>
 <input id="ac-search" autocomplete="off" placeholder="Start typing...">
 <div id="ac-message" class="ac-message" role="status" aria-live="polite"></div>
 <div class="table-wrapper"><table>
 <thead><tr><th>Asset ID</th><th>User</th><th>Name</th><th>Model</th><th>Type</th><th>Status</th><th>Actions</th></tr></thead>
 <tbody id="ac-results"></tbody></table></div>
 <div id="ac-details"></div>
 <div class="ac-actions">
 <button id="ac-more" type="button" hidden>More results</button>
 <button id="ac-close" type="button">Close</button>
 </div>`;
 document.body.append(dialog);

 const $=id=>dialog.querySelector('#'+id);
 let controller=null,timer=null,generation=0,offset=0,lastQuery='';
 const computerTypes=new Set(['desktop','laptop','mini pc','mini tower','workstation']);
 const phoneTypes=new Set(['phone','telephone']);

 function showDetails(hit){
  const box=$('ac-details');box.replaceChildren();
  const grid=make('div');grid.className='ac-details';
  for(const [key,value] of Object.entries(hit.values)){
   const cell=make('div');
   cell.append(make('strong',key));
   const data=make('div',value);data.className='ac-value';
   cell.append(data);grid.append(cell);
  }
  box.append(grid);
 }

 // R6_SHARED_SELECTION
 function select(hit,kind){
  if(!window.AssetCatalogR6){
   $('ac-message').textContent='The inventory controls are still loading.';
   return;
  }
  window.AssetCatalogR6.select(hit,kind)
   .then(()=>{if(dialog.open)dialog.close();})
   .catch(error=>{$('ac-message').textContent=error.message;});
 }

 function append(hit){
  const row=make('tr'),v=hit.values;
  for(const name of ['Asset ID','User','Name','Model','DevType','Status'])
   row.append(make('td',v[name]||''));

  const actions=make('td');
  const view=make('button','Details');view.className='mini';view.type='button';
  view.onclick=()=>showDetails(hit);actions.append(view);

  const type=String(v.DevType||'').trim().toLowerCase();
  const kind=computerTypes.has(type)?'computer':phoneTypes.has(type)?'phone':null;
  if(kind){
   const use=make('button','Use as '+kind);use.className='mini';use.type='button';
   use.onclick=()=>select(hit,kind);actions.append(use);
  }

  row.append(actions);$('ac-results').append(row);
 }

 async function search(appendResults=false){
  const q=$('ac-search').value.trim();
  if(!q){
   if(controller)controller.abort();
   $('ac-results').replaceChildren();$('ac-details').replaceChildren();
   $('ac-more').hidden=true;$('ac-message').textContent='Type a name, Asset ID or any other value.';
   return;
  }

  if(!appendResults){
   offset=0;lastQuery=q;
   $('ac-results').replaceChildren();$('ac-details').replaceChildren();
  }
  if(controller)controller.abort();
  controller=new AbortController();
  const ticket=++generation;
  $('ac-message').textContent='Searching...';

  try{
   const response=await fetch(
    '/api/bulk/assets/search?q='+encodeURIComponent(q)+'&offset='+offset,{
     credentials:'same-origin',cache:'no-store',signal:controller.signal,
     headers:{Accept:'application/json'}
    });
   if(response.redirected||response.status===401)throw new Error('Session expired. Sign in again.');
   const data=await response.json();
   if(!response.ok)throw new Error(data.detail||data.message||'Unable to search.');
   if(ticket!==generation||q!==$('ac-search').value.trim())return;

   for(const hit of data.items)append(hit);
   offset+=data.items.length;
   $('ac-more').hidden=!data.hasMore;
   $('ac-message').textContent=offset?
    offset+' matches displayed'+(data.hasMore?' — more available.':'.'):
    'No matches. Try a shorter name, Asset ID, equipment name or another field.';
  }catch(error){
   if(error.name!=='AbortError'&&ticket===generation)
    $('ac-message').textContent=error.message;
  }
 }

 $('ac-search').addEventListener('input',()=>{
  clearTimeout(timer);
  ++generation;if(controller)controller.abort();
  timer=setTimeout(()=>search(false),250);
 });
 $('ac-more').onclick=()=>{if(lastQuery===$('ac-search').value.trim())search(true);};
 $('ac-close').onclick=()=>dialog.close();
 dialog.addEventListener('close',()=>{
  clearTimeout(timer);++generation;if(controller)controller.abort();
 });

 button.onclick=()=>{
  if(!permitted())return;
  $('ac-search').value=document.getElementById('n')?.value||'';
  dialog.showModal();$('ac-search').focus();search(false);
 };

 function permissions(){
  button.hidden=!permitted();
  if(!permitted()&&dialog.open){
   dialog.close();$('ac-results').replaceChildren();$('ac-details').replaceChildren();
  }
 }
 new MutationObserver(permissions).observe(capture,{
  attributes:true,attributeFilter:['class']
 });
 permissions();
 window.addEventListener('offboarding:inventory-updated',()=>{
  if(dialog.open)search(false);
  else{
   $('ac-results').replaceChildren();
   $('ac-details').replaceChildren();
  }
 });
})();
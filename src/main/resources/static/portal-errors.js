'use strict';
(() => {
 if(window.OffboardingErrors)return;
 const originalFetch=window.fetch.bind(window);
 const originalAlert=window.alert.bind(window);
 let last=null,panel=null;

 const generic=value=>!value||/\bunexpected error\b|internal server error/i.test(String(value));

 function describe(response,data){
  const status=response.redirected?401:response.status;
  const ref=data?.requestId||response.headers?.get('X-Request-Id')||'';
  let path='';
  try{path=new URL(response.url,location.href).pathname;}catch{}
  let message=data?.detail||data?.message||data?.title||'';

  if(generic(message)){
   message=status===401?'Session expired. Sign in again.':
    status===403?'Operation not permitted. Check your profile and session.':
    status===400?'Check the required fields and date format.':
    status===404?'The requested record or endpoint was not found.':
    status===409?'The record changed or an integrity rule prevented the operation. Refresh before retrying.':
    status===429?'Another operation is being processed. Wait before retrying.':
    'The server could not confirm the operation. Refresh before repeating it.';
  }

  return String(message).slice(0,800)+
   `\nHTTP ${status}${path?' · '+path:''}`+
   (ref?`\nReference: ${ref}`:'');
 }

 function show(text){
  if(!document.body)return;
  if(!panel){
   panel=document.createElement('section');
   panel.className='card';panel.id='portal-api-message';
   panel.setAttribute('role','alert');
   panel.style.cssText='border-left:4px solid #777;white-space:pre-wrap';
   const title=document.createElement('strong');title.textContent='Operation not completed';
   const content=document.createElement('div');content.className='portal-api-detail';
   content.style.cssText='font-size:13px;margin:8px 0;overflow-wrap:anywhere';
   const close=document.createElement('button');close.type='button';close.className='mini';
   close.textContent='Dismiss';close.onclick=()=>{panel.hidden=true;};
   panel.append(title,content,close);
   const session=document.querySelector('.session-card');
   if(session)session.after(panel);
   else (document.querySelector('main')||document.body).prepend(panel);
  }
  panel.querySelector('.portal-api-detail').textContent=text;
  panel.hidden=false;
 }

 window.OffboardingErrors={describe,show};

 window.fetch=async function(input,options){
  let context=null;
  try{
   const target=new URL(typeof input==='string'?input:(input.url||input.href),location.href);
   if(target.origin===location.origin&&target.pathname.startsWith('/api/'))
    context={path:target.pathname,method:options?.method||input?.method||'GET'};
  }catch{}

  let response;
  try{
   response=await originalFetch(input,options);
  }catch(error){
   if(context&&error.name!=='AbortError'){
    const text=`Connection interrupted: ${context.method} ${context.path}.\nThe result is unknown. Refresh the record before retrying.`;
    last={at:Date.now(),text};show(text);
   }
   throw error;
  }

  if(context&&!response.ok){
   let data=null;
   if((response.headers.get('Content-Type')||'').includes('json')){
    try{data=await response.clone().json();}catch{}
   }
   const text=describe(response,data);
   last={at:Date.now(),text};show(text);
  }
  return response;
 };

 window.alert=function(value){
  const text=String(value??'');
  if(/\bunexpected error\b/i.test(text)){
   const detailed=last&&Date.now()-last.at<10000?
    last.text:
    'An unexpected screen error occurred, but no recent failed HTTP request was captured. Check the browser console for this action.';
   show(detailed);originalAlert(detailed);return;
  }
  originalAlert(text);
 };
})();
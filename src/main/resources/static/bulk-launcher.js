(() => {
 if(document.getElementById('bulk-launcher'))return;
 fetch('/api/bulk/session',{credentials:'same-origin',cache:'no-store'})
  .then(response=>{
   if(!response.ok)return;
   const link=document.createElement('a');
   link.id='bulk-launcher';link.href='/bulk.html';
   link.textContent='Importar bajas';
   link.style.cssText='position:fixed;left:18px;bottom:16px;z-index:100;padding:10px 15px;background:#17365d;color:white;border-radius:7px;font:14px Segoe UI,Arial;text-decoration:none;box-shadow:0 2px 7px #0003';
   document.body.append(link);
  }).catch(()=>{});
})();
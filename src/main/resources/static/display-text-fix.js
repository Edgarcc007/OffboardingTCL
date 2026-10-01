'use strict';
(() => {
 const excluded='script,style,textarea,input,select,option,[contenteditable]';

 function normalize(value){
  return value
   .replace(/\bEquipo de c(?:o|\u00f3|\u00c3\u00b3|\u00c3\u0192\u00c2\u00b3|\u00c3\u0083\u00c2\u00b3)mputo\b/gi,'Equipo de Computo')
   .replace(/\bc(?:\u00f3|\u00c3\u00b3|\u00c3\u0192\u00c2\u00b3|\u00c3\u0083\u00c2\u00b3)mputadora\b/gi,'Computadora');
 }

 function fixText(node){
  if(!node.parentElement||node.parentElement.closest(excluded))return;
  const before=node.nodeValue||'';
  const after=normalize(before);
  if(after!==before)node.nodeValue=after;
 }

 function scan(node){
  if(node.nodeType===Node.TEXT_NODE){fixText(node);return;}
  if(node.nodeType!==Node.ELEMENT_NODE||node.matches(excluded))return;
  const walker=document.createTreeWalker(node,NodeFilter.SHOW_TEXT);
  while(walker.nextNode())fixText(walker.currentNode);
 }

 scan(document.body);
 new MutationObserver(records=>{
  for(const record of records){
   if(record.type==='characterData')fixText(record.target);
   else for(const node of record.addedNodes)scan(node);
  }
 }).observe(document.body,{childList:true,subtree:true,characterData:true});
})();
'use strict';
(() => {
 const el=id=>document.getElementById(id);
 const section=el('seccion_auditoria'),audit=el('audit_body');
 if(!section||!audit)return;

 const make=(tag,text,cls)=>{
  const n=document.createElement(tag);
  if(text!==undefined)n.textContent=String(text);
  if(cls)n.className=cls;
  return n;
 };
 const allowed=()=>typeof tieneRol==='function'&&(tieneRol('ADMIN')||tieneRol('AUDITOR'));
 const visible=()=>allowed()&&!section.classList.contains('hidden')&&!section.classList.contains('ob-tab-inactive');
 const count=value=>new Intl.NumberFormat('en-US').format(value);
 const percent=value=>new Intl.NumberFormat('en-US',{maximumFractionDigits:1}).format(value);

 const root=make('section');root.id='ob-dashboard';root.hidden=true;
 root.innerHTML=`
 <div class="ux-heading">
  <div><h3>Offboarding dashboard</h3><p>Choose your dates. No range is selected automatically.</p></div>
  <span class="ux-label">Registration date</span>
 </div>
 <form id="ux-form">
  <div class="ux-filters">
   <label>From<input id="obd-from" type="date" required autocomplete="off"></label>
   <label>To<input id="obd-to" type="date" required autocomplete="off"></label>
   <label>Group by<select id="obd-period">
    <option value="DAY">Day</option><option value="WEEK" selected>Week</option>
    <option value="MONTH">Month</option><option value="YEAR">Year</option>
   </select></label>
   <label>Department<select id="obd-department"><option value="">All departments</option></select></label>
   <label>Area<select id="obd-area"><option value="">All areas</option></select></label>
   <label>Breakdown<select id="obd-dimension">
    <option value="DEPARTMENT">Department</option><option value="AREA">Area</option>
   </select></label>
  </div>
  <div class="ux-toolbar">
   <span id="ux-zone">Select From and To to begin.</span>
   <div><button id="ux-clear" type="button" class="mini">Clear</button>
   <button id="obd-refresh" type="submit">Consultar</button></div>
  </div>
 </form>
 <div id="obd-message" class="ux-message" role="status"></div>
 <div id="ux-empty" class="ux-empty">
  <strong>Select a date range</strong>
  <span>Statistics remain empty until you press Consultar.</span>
 </div>
 <div id="ux-results" hidden>
  <div id="ux-applied" class="ux-applied"></div>
  <div class="ux-stats">
   <div class="ux-stat"><span>Registered offboardings</span><strong id="ux-total">—</strong><small>Each case counts once, not once per task.</small></div>
   <div class="ux-stat"><span>Complete periods</span><strong id="ux-periods">—</strong><small id="ux-partials"></small></div>
   <div class="ux-stat"><span>Current status of selected cases</span><div id="ux-status"></div></div>
  </div>
  <div class="ux-panel">
   <div class="ux-panel-title">
    <div><h3>Offboardings over time</h3><p>Vertical axis: number of registered cases.</p></div>
    <label>Chart<select id="ux-chart-type"><option value="BAR">Bars</option><option value="LINE">Line</option></select></label>
   </div>
   <div class="ux-legend"><span>■ Complete period</span><span>▧ Partial period</span></div>
   <div id="obd-chart"></div>
   <div id="ux-point" class="ux-point">Point to or focus a period to inspect its count.</div>
   <div class="ux-chart-nav">
    <button id="ux-earlier" type="button" class="mini">← Earlier</button>
    <span id="ux-window"></span>
    <button id="ux-later" type="button" class="mini">Later →</button>
   </div>
  </div>
  <div class="ux-two">
   <div class="ux-panel">
    <h3>Compare complete periods</h3>
    <p class="ux-muted">Partial periods are excluded from this comparison.</p>
    <div class="ux-compare-selects">
     <label>Baseline<select id="ux-base"></select></label>
     <label>Compared period<select id="ux-current"></select></label>
    </div>
    <div id="ux-comparison"></div>
   </div>
   <div class="ux-panel">
    <h3 id="ux-group-title">By department</h3>
    <p class="ux-muted">Click a named group to filter the selected date range.</p>
    <div id="obd-groups"></div>
   </div>
  </div>
  <details class="ux-panel"><summary>View exact period totals</summary><div id="obd-table" class="table-wrapper"></div></details>
 </div>`;
 audit.before(root);

 const style=make('style');
 style.textContent=`
 #ob-dashboard{margin:0 0 24px;padding:0 0 24px;border-bottom:1px solid var(--border);font-family:var(--font)}
 #ob-dashboard[hidden],#ob-dashboard [hidden]{display:none!important}
 #portal-api-message[hidden]{display:none!important}
 #ob-dashboard h3{font-size:15px;font-weight:750;margin:0 0 5px;color:var(--text-primary)}
 #ob-dashboard p{font-size:12px;line-height:1.5;color:var(--text-muted)}
 #ob-dashboard .ux-heading,#ob-dashboard .ux-panel-title{display:flex;align-items:center;justify-content:space-between;gap:16px;flex-wrap:wrap}
 #ob-dashboard .ux-heading{margin-bottom:18px}
 #ob-dashboard .ux-label{font-size:11px;border:1px solid var(--border);padding:6px 10px;border-radius:20px;color:var(--text-secondary)}
 #ob-dashboard .ux-filters{display:grid;grid-template-columns:repeat(3,minmax(130px,1fr));gap:12px}
 #ob-dashboard label{margin:0;font-size:11px;text-transform:none;letter-spacing:0}
 #ob-dashboard label input,#ob-dashboard label select{margin-top:5px}
 #ob-dashboard .ux-toolbar{display:flex;justify-content:space-between;align-items:center;gap:10px;flex-wrap:wrap;margin-top:14px}
 #ob-dashboard .ux-toolbar>span{font-size:12px;color:var(--text-muted)}
 #ob-dashboard button{margin:0}
 #ob-dashboard button:disabled{opacity:.45;cursor:default;transform:none;box-shadow:none}
 #ob-dashboard .ux-empty{min-height:155px;margin-top:18px;border:1px dashed #c9c9c9;border-radius:12px;display:flex;align-items:center;justify-content:center;flex-direction:column;gap:9px;background:#fafafa;color:var(--text-secondary)}
 #ob-dashboard .ux-empty span{font-size:12px}
 #ob-dashboard .ux-message{white-space:pre-wrap;font-size:13px;line-height:1.6;margin-top:12px}
 #ob-dashboard .ux-error{color:var(--danger)}
 #ob-dashboard .ux-applied{font-size:12px;color:var(--text-secondary);margin:18px 0 12px}
 #ob-dashboard .ux-stats{display:grid;grid-template-columns:1fr 1fr 1.3fr;gap:12px;margin-bottom:16px}
 #ob-dashboard .ux-stat{background:linear-gradient(135deg,#fafafa,#f1f1f1);border:1px solid var(--border);border-radius:12px;padding:17px}
 #ob-dashboard .ux-stat>span{font-size:11px;text-transform:uppercase;letter-spacing:.5px;color:var(--text-secondary)}
 #ob-dashboard .ux-stat>strong{display:block;font-size:32px;font-weight:750;margin:7px 0;font-variant-numeric:tabular-nums}
 #ob-dashboard .ux-stat small{font-size:11px;color:var(--text-muted)}
 #ob-dashboard #ux-status{display:flex;gap:7px;flex-wrap:wrap;margin-top:13px}
 #ob-dashboard .ux-status-item{padding:6px 9px;border:1px solid #d6d6d6;border-radius:7px;background:white;font-size:11px}
 #ob-dashboard .ux-panel{background:#fff;border:1px solid var(--border);border-radius:12px;padding:18px;margin-bottom:16px}
 #ob-dashboard .ux-panel-title label{min-width:125px}
 #ob-dashboard .ux-legend{display:flex;gap:16px;font-size:11px;color:var(--text-muted);margin:12px 0}
 #ob-dashboard svg{display:block;width:100%;height:auto;min-height:190px}
 #ob-dashboard svg .ux-mark{cursor:pointer}
 #ob-dashboard svg .ux-mark:hover,#ob-dashboard svg .ux-mark:focus{stroke:#111;stroke-width:2;outline:none}
 #ob-dashboard .ux-point{background:#f6f6f6;border-radius:7px;padding:10px 12px;font-size:12px;min-height:37px;color:var(--text-secondary)}
 #ob-dashboard .ux-chart-nav{display:flex;align-items:center;justify-content:space-between;gap:10px;margin-top:10px;font-size:11px;color:var(--text-muted)}
 #ob-dashboard .ux-two{display:grid;grid-template-columns:1fr 1fr;gap:16px}
 #ob-dashboard .ux-compare-selects{display:grid;grid-template-columns:1fr 1fr;gap:12px;margin:14px 0}
 #ob-dashboard .ux-pair{display:grid;grid-template-columns:1fr 35px 1fr;align-items:center;text-align:center;gap:6px;margin:20px 0}
 #ob-dashboard .ux-pair strong{display:block;font-size:28px;font-variant-numeric:tabular-nums}
 #ob-dashboard .ux-pair small{display:block;font-size:11px;color:var(--text-muted);margin-top:5px}
 #ob-dashboard .ux-change{border-top:1px solid var(--border);padding-top:13px;font-size:21px;font-weight:750}
 #ob-dashboard .ux-formula{margin-top:13px;font-size:12px;line-height:1.7;color:var(--text-secondary)}
 #ob-dashboard .ux-formula summary{cursor:pointer;font-weight:650}
 #ob-dashboard .ux-baseline-note{margin-top:8px;padding:9px;border:1px dashed #bbb;border-radius:7px;font-size:11px;color:var(--text-secondary)}
 #ob-dashboard .ux-muted{color:var(--text-muted);font-size:12px}
 #ob-dashboard .ux-group{display:block;width:100%;padding:10px 0;border:0;background:transparent;color:var(--text-primary);text-align:left;transform:none;box-shadow:none;font:inherit}
 #ob-dashboard button.ux-group:hover{background:#f8f8f8}
 #ob-dashboard .ux-group-heading{display:flex;justify-content:space-between;gap:10px;font-size:12px;margin-bottom:7px}
 #ob-dashboard .ux-group-heading span{overflow-wrap:anywhere}
 #ob-dashboard .ux-track{height:8px;background:#ededed;border-radius:6px;overflow:hidden}
 #ob-dashboard .ux-fill{height:100%;background:linear-gradient(90deg,#898989,#444);border-radius:6px}
 #ob-dashboard .ux-group small{display:block;font-size:10px;color:var(--text-muted);margin-top:4px}
 #ob-dashboard details>summary{cursor:pointer;font-size:12px;font-weight:650}
 #ob-dashboard #obd-table{margin-top:12px}
 @media(max-width:950px){#ob-dashboard .ux-two{grid-template-columns:1fr}}
 @media(max-width:700px){#ob-dashboard .ux-filters{grid-template-columns:1fr 1fr}#ob-dashboard .ux-stats{grid-template-columns:1fr}#ob-dashboard .ux-compare-selects{grid-template-columns:1fr}}
 `;
 document.head.append(style);

 const $=id=>root.querySelector('#'+id);
 let report=null,applied=null,dirty=true,busy=false,controller=null,ticket=0;
 let optionsReady=false,optionsLoading=false,lastVisible=false,lastLoaded=0;
 let chartOffset=0;
 const windowSize=60;

 function message(text,error=false){
  $('obd-message').textContent=text||'';
  $('obd-message').className='ux-message'+(error?' ux-error':'');
 }
 function controls(){
  $('obd-refresh').disabled=busy||!$('obd-from').value||!$('obd-to').value;
  $('ux-clear').disabled=false;
 }
 function invalidate(text='Filters changed. Press Consultar to apply them.'){
  controller?.abort();ticket++;busy=false;dirty=true;report=null;
  $('ux-results').hidden=true;$('ux-empty').hidden=false;
  $('ux-empty').querySelector('strong').textContent='Select and apply your range';
  message(text);controls();
 }
 function optionList(select,values,caption){
  const old=select.value;
  select.replaceChildren(new Option(caption,''));
  for(const value of values||[])select.add(new Option(value,value));
  if(old&&!(values||[]).includes(old))select.add(new Option(old,old));
  select.value=old;
 }

 async function fetchJson(url,signal){
  const response=await fetch(url,{
   credentials:'same-origin',cache:'no-store',signal,headers:{Accept:'application/json'}
  });
  let data=null;
  try{data=await response.json();}catch{}
  if(!response.ok||response.redirected)
   throw new Error(window.OffboardingErrors?.describe(response,data)||`HTTP ${response.status}`);
  if(!data)throw new Error('The server returned an empty report.');
  return data;
 }

 async function loadOptions(){
  if(optionsReady||optionsLoading||!allowed())return;
  optionsLoading=true;
  try{
   const data=await fetchJson('/api/audit/offboarding-dashboard/options');
   if(!allowed())return;
   optionList($('obd-department'),data.departments,'All departments');
   optionList($('obd-area'),data.areas,'All areas');
   $('obd-from').max=data.today;$('obd-to').max=data.today;
   $('ux-zone').textContent=`Calendar dates use ${data.zone}.`;
   optionsReady=true;
  }catch(error){message(error.message,true);}
  finally{optionsLoading=false;}
 }

 function selected(){
  return {
   from:$('obd-from').value,to:$('obd-to').value,period:$('obd-period').value,
   department:$('obd-department').value,area:$('obd-area').value,
   dimension:$('obd-dimension').value
  };
 }

 async function load(filters){
  if(!visible())return;
  const f=filters||selected();
  if(!f.from||!f.to){message('Select both From and To.');return;}
  if(f.from>f.to){message('From cannot be later than To.',true);return;}
  if(!filters&&!$('ux-form').reportValidity())return;

  controller?.abort();controller=new AbortController();
  const own=++ticket;busy=true;controls();message('Loading the selected range...');
  try{
   const data=await fetchJson('/api/audit/offboarding-dashboard?'+new URLSearchParams(f),controller.signal);
   if(own!==ticket||!allowed())return;
   if(!Array.isArray(data.series)||!Array.isArray(data.groups)||
      !data.series.every(p=>Number.isSafeInteger(p.count)&&p.count>=0))
    throw new Error('The report response has an unexpected structure.');
   report=data;applied={...f};dirty=false;lastLoaded=Date.now();
   chartOffset=Math.max(0,data.series.length-windowSize);
   render();
   message(data.total===0?'No offboardings match the selected filters.':'');
  }catch(error){
   if(error.name!=='AbortError'&&own===ticket){
    report=null;dirty=true;$('ux-results').hidden=true;$('ux-empty').hidden=false;
    message(error.message,true);
   }
  }finally{if(own===ticket){busy=false;controls();}}
 }

 function calendar(value){
  const [y,m,d]=value.split('-').map(Number);
  return new Date(Date.UTC(y,m-1,d));
 }
 function dateLabel(value){
  return new Intl.DateTimeFormat('en-GB',{
   timeZone:'UTC',day:'2-digit',month:'short',year:'numeric'
  }).format(calendar(value));
 }
 function periodDescription(point){
  const start=calendar(point.start),end=new Date(start);
  if(report.period==='DAY')return dateLabel(point.start);
  if(report.period==='WEEK')end.setUTCDate(end.getUTCDate()+6);
  if(report.period==='MONTH')end.setUTCMonth(end.getUTCMonth()+1,0);
  if(report.period==='YEAR')end.setUTCFullYear(end.getUTCFullYear()+1,0,0);
  return `${point.label} · ${dateLabel(point.start)} – ${dateLabel(end.toISOString().slice(0,10))}`;
 }
 function svgNode(tag,attributes,text){
  const n=document.createElementNS('http://www.w3.org/2000/svg',tag);
  for(const [key,value] of Object.entries(attributes||{}))n.setAttribute(key,String(value));
  if(text!==undefined)n.textContent=String(text);
  return n;
 }
 function niceStep(value){
  const power=10**Math.floor(Math.log10(Math.max(1,value)));
  const fraction=value/power;
  return Math.max(1,(fraction<=1?1:fraction<=2?2:fraction<=5?5:10)*power);
 }

 function draw(){
  if(!report)return;
  const host=$('obd-chart');host.replaceChildren();
  const points=report.series.slice(chartOffset,chartOffset+windowSize);
  if(!points.length)return;

  const width=1000,height=310,left=65,right=20,top=25,bottom=55;
  const pw=width-left-right,ph=height-top-bottom;
  const maximum=Math.max(1,...points.map(p=>p.count));
  const step=niceStep(maximum/4),ceiling=Math.ceil(maximum/step)*step;
  const x=i=>left+(i+.5)*pw/points.length;
  const y=value=>top+ph-value*ph/ceiling;
  const svg=svgNode('svg',{viewBox:`0 0 ${width} ${height}`,role:'img','aria-label':'Registered offboardings per period'});

  const defs=svgNode('defs'),pattern=svgNode('pattern',{
   id:'ux-partial-pattern',width:7,height:7,patternUnits:'userSpaceOnUse'
  });
  pattern.append(svgNode('rect',{width:7,height:7,fill:'#eeeeee'}));
  pattern.append(svgNode('path',{d:'M-1 1L1-1M0 7L7 0M6 8L8 6',stroke:'#999','stroke-width':1.4}));
  defs.append(pattern);svg.append(defs);

  for(let value=0;value<=ceiling;value+=step){
   svg.append(svgNode('line',{x1:left,y1:y(value),x2:width-right,y2:y(value),stroke:'#e5e5e5'}));
   svg.append(svgNode('text',{x:left-10,y:y(value)+4,'text-anchor':'end',fill:'#666','font-size':12},count(value)));
  }
  if($('ux-chart-type').value==='LINE'){
   for(let i=1;i<points.length;i++){
    svg.append(svgNode('line',{
     x1:x(i-1),y1:y(points[i-1].count),x2:x(i),y2:y(points[i].count),
     stroke:'#4b4b4b','stroke-width':2.5,
     'stroke-dasharray':points[i-1].partial||points[i].partial?'5 4':'none'
    }));
   }
  }

  const marks=[];
  const labelStep=Math.max(1,Math.ceil(points.length/7));
  points.forEach((point,i)=>{
   const bar=$('ux-chart-type').value==='BAR';
   const bw=Math.min(55,Math.max(5,pw/points.length*.66));
   const mark=bar?svgNode('rect',{
    x:x(i)-bw/2,y:point.count===0?y(0)-1:y(point.count),
    width:bw,height:point.count===0?1:ph*point.count/ceiling,rx:3,
    fill:point.partial?'url(#ux-partial-pattern)':'#626262'
   }):svgNode('circle',{
    cx:x(i),cy:y(point.count),r:5,fill:point.partial?'#fff':'#4b4b4b',
    stroke:'#4b4b4b','stroke-width':1.5
   });
   const text=`${periodDescription(point)}: ${count(point.count)} offboardings${point.partial?' · PARTIAL: only the selected/elapsed part is counted.':''}`;
   mark.setAttribute('class','ux-mark');mark.setAttribute('tabindex',i===0?'0':'-1');
   mark.setAttribute('role','button');mark.setAttribute('aria-label',text);
   mark.append(svgNode('title',{},text));
   const inspect=()=>{$('ux-point').textContent=text;};
   mark.addEventListener('mouseenter',inspect);mark.addEventListener('focus',inspect);mark.addEventListener('click',inspect);
   marks.push(mark);svg.append(mark);

   if(points.length<=18&&point.count>0){
    svg.append(svgNode('text',{x:x(i),y:y(point.count)-8,'text-anchor':'middle','font-size':12,fill:'#444'},count(point.count)));
   }
   if(i%labelStep===0||i===points.length-1)
    svg.append(svgNode('text',{x:x(i),y:height-25,'text-anchor':'middle','font-size':11,fill:'#666'},point.label));
  });

  svg.addEventListener('keydown',event=>{
   if(!['ArrowLeft','ArrowRight'].includes(event.key))return;
   const i=marks.indexOf(document.activeElement);
   if(i<0)return;
   event.preventDefault();
   const next=Math.max(0,Math.min(marks.length-1,i+(event.key==='ArrowRight'?1:-1)));
   marks[i].setAttribute('tabindex','-1');marks[next].setAttribute('tabindex','0');marks[next].focus();
  });

  host.append(svg);
  $('ux-window').textContent=`Periods ${chartOffset+1}–${chartOffset+points.length} of ${report.series.length}`;
  $('ux-earlier').disabled=chartOffset===0;
  $('ux-later').disabled=chartOffset+points.length>=report.series.length;
  $('ux-point').textContent='Point to or focus a period to inspect its count. Use arrow keys while focused.';
 }

 function compare(){
  const host=$('ux-comparison');host.replaceChildren();
  if(!report)return;
  const complete=report.series.filter(p=>!p.partial);
  const a=complete.find(p=>p.start===$('ux-base').value);
  const b=complete.find(p=>p.start===$('ux-current').value);

  if(!a||!b||a.start>=b.start){
   host.append(make('p',complete.length<2?
    'The selected range does not contain two complete periods. Choose a wider range or a shorter grouping.':
    'Choose a compared period later than the baseline.','ux-muted'));
   return;
  }

  const pair=make('div',undefined,'ux-pair');
  const baseline=make('div'),current=make('div');
  baseline.append(make('strong',count(a.count)),make('small',periodDescription(a)));
  current.append(make('strong',count(b.count)),make('small',periodDescription(b)));
  pair.append(baseline,make('span','→'),current);host.append(pair);

  const delta=b.count-a.count;
  host.append(make('div',delta===0?'No change':
   `${delta>0?'↑':'↓'} ${count(Math.abs(delta))} ${delta>0?'more':'fewer'} offboardings`,'ux-change'));

  const formula=make('details',undefined,'ux-formula');
  formula.append(make('summary','How the relative change is calculated'));
  if(a.count===0){
   formula.append(make('div',
    `Baseline: 0; compared period: ${count(b.count)}. A percentage change is not defined when the baseline is zero.`));
  }else{
   const p=delta/a.count*100;
   formula.append(make('div',
    `(${count(b.count)} − ${count(a.count)}) ÷ ${count(a.count)} × 100 = ${p>0?'+':''}${percent(p)}%.`));
   if(a.count<10)
    formula.append(make('div',
     `Small baseline: ${count(a.count)} offboarding(s). A large percentage can represent a small change in case counts.`,'ux-baseline-note'));
  }
  formula.append(make('div','This compares registered case counts. It is not an employee turnover rate.'));
  host.append(formula);
 }

 function comparisonOptions(){
  const complete=report.series.filter(p=>!p.partial);
  for(const id of ['ux-base','ux-current']){
   $(id).replaceChildren();
   for(const point of complete)$(id).add(new Option(
    `${point.label} — ${count(point.count)} offboardings`,point.start));
   $(id).disabled=complete.length<2;
  }
  if(complete.length>=2){
   $('ux-base').value=complete[complete.length-2].start;
   $('ux-current').value=complete[complete.length-1].start;
  }
  compare();
 }

 function groups(){
  const host=$('obd-groups');host.replaceChildren();
  $('ux-group-title').textContent=report.dimension==='AREA'?'By area':'By department';
  const shown=report.groups.slice(0,12).map(g=>({...g,other:false}));
  if(report.groups.length>12)
   shown.push({name:'Other groups',count:report.groups.slice(12).reduce((sum,g)=>sum+g.count,0),other:true});
  if(!shown.length){host.append(make('p','No matching groups.','ux-muted'));return;}

  for(const group of shown){
   const block=make(group.other?'div':'button',undefined,'ux-group');
   if(!group.other)block.type='button';
   const header=make('div',undefined,'ux-group-heading');
   header.append(make('span',group.name),make('strong',count(group.count)));
   const track=make('div',undefined,'ux-track'),fill=make('div',undefined,'ux-fill');
   const share=report.total?group.count/report.total*100:0;
   fill.style.width=Math.min(100,share)+'%';track.append(fill);
   block.append(header,track,make('small',`${percent(share)}% of ${count(report.total)} selected cases`));
   if(!group.other){
    block.title='Filter this group within your selected date range';
    block.onclick=()=>{
     const control=$(report.dimension==='AREA'?'obd-area':'obd-department');
     if(!Array.from(control.options).some(o=>o.value===group.name))
      control.add(new Option(group.name,group.name));
     control.value=group.name;invalidate('Applying group filter...');load();
    };
   }
   host.append(block);
  }
 }

 function render(){
  $('ux-empty').hidden=true;$('ux-results').hidden=false;
  $('ux-total').textContent=count(report.total);
  const complete=report.series.filter(p=>!p.partial).length;
  $('ux-periods').textContent=count(complete);
  $('ux-partials').textContent=`${report.series.length-complete} partial · ${report.series.length} periods in the selected range`;
  $('ux-applied').textContent=
   `Applied range: ${dateLabel(report.from)} – ${dateLabel(report.to)} · ${report.zone}`+
   (applied.department?` · Department: ${applied.department}`:'')+
   (applied.area?` · Area: ${applied.area}`:'');

  const names={PROGRAMADA:'Scheduled',EN_PROCESO:'In progress',COMPLETADA:'Completed',CANCELADA:'Cancelled'};
  $('ux-status').replaceChildren();
  for(const item of report.statuses||[])
   $('ux-status').append(make('span',`${names[item.name]||item.name}: ${count(item.count)}`,'ux-status-item'));
  if(!(report.statuses||[]).length)$('ux-status').append(make('span','No cases','ux-muted'));

  optionList($('obd-department'),report.departments,'All departments');
  optionList($('obd-area'),report.areas,'All areas');
  draw();comparisonOptions();groups();

  const table=make('table'),head=make('thead'),tr=make('tr');
  ['Period','Date coverage','Offboardings','Completeness'].forEach(t=>tr.append(make('th',t)));
  head.append(tr);table.append(head);
  const body=make('tbody');
  for(const point of report.series){
   const row=make('tr');
   row.append(make('td',point.label),make('td',periodDescription(point)),
    make('td',count(point.count)),make('td',point.partial?'Partial':'Complete'));
   body.append(row);
  }
  table.append(body);$('obd-table').replaceChildren(table);
 }

 $('ux-form').onsubmit=event=>{event.preventDefault();load();};
 $('ux-clear').onclick=()=>{
  $('obd-from').value='';$('obd-to').value='';
  $('obd-department').value='';$('obd-area').value='';
  applied=null;invalidate('Select From and To, then press Consultar.');
 };
 for(const id of ['obd-from','obd-to','obd-period','obd-area','obd-dimension'])
  $(id).addEventListener('change',()=>invalidate());
 $('obd-department').addEventListener('change',()=>{
  $('obd-area').value='';invalidate();
 });
 $('ux-chart-type').onchange=draw;
 $('ux-base').onchange=compare;$('ux-current').onchange=compare;
 $('ux-earlier').onclick=()=>{chartOffset=Math.max(0,chartOffset-windowSize);draw();};
 $('ux-later').onclick=()=>{chartOffset=Math.min(Math.max(0,report.series.length-windowSize),chartOffset+windowSize);draw();};

 function sync(){
  const canRead=allowed();
  if(root.hidden!==!canRead)root.hidden=!canRead;
  const now=visible();
  if(!canRead){
   controller?.abort();ticket++;busy=false;report=null;applied=null;dirty=true;
   $('ux-results').hidden=true;$('ux-empty').hidden=false;
   $('obd-from').value='';$('obd-to').value='';optionsReady=false;controls();
  }
  if(now&&!lastVisible){
   loadOptions();
   if(applied&&!dirty&&Date.now()-lastLoaded>60000)load(applied);
  }
  lastVisible=now;
 }
 new MutationObserver(sync).observe(section,{attributes:true,attributeFilter:['class']});
 // No automatic statistics request and no preset dates on initialization.
 controls();sync();
})();
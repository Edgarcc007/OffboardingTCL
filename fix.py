f = r'C:\OffboardingTCL\src\main\resources\static\index.html'
with open(f, 'r', encoding='utf-8') as fh:
    h = fh.read()

c = 0

# FIX 1: effectiveAt ISO
o = 'effectiveAt: fecha,'
n = 'effectiveAt: fecha ? new Date(fecha).toISOString() : new Date().toISOString(),'
if o in h:
    h = h.replace(o, n, 1); c+=1; print('FIX1 OK')
else: print('FIX1 SKIP')

# FIX 2: CSS
anchor = 'input:focus,select:focus,textarea:focus{outline:none;border-color:var(--accent);box-shadow:0 0 0 3px rgba(229,45,39,.1)}'
if '.auto-filled' not in h and anchor in h:
    h = h.replace(anchor, anchor + chr(10) + '  .auto-filled{background-color:#ecfdf5!important;border-color:#6ee7b7!important;transition:background-color .3s ease,border-color .3s ease}', 1); c+=1; print('FIX2 OK')
else: print('FIX2 SKIP')

# FIX 3: auto-filled en campos
for fld, src in [('n','employeeName'),('dep','deptName'),('position','rollName'),('shift','turnName'),('work_area','areaName'),('jefe','superName')]:
    o = "if (r.data."+src+") "+fld+".value = r.data."+src+";"
    n = "if (r.data."+src+") { "+fld+".value = r.data."+src+"; "+fld+".classList.add('auto-filled'); }"
    if o in h:
        h = h.replace(o, n, 1); c+=1

print('FIX3 OK - campos')

# FIX 4: limpiar auto-filled
o = ".value = ''; });"
n = ".value = ''; .classList.remove('auto-filled'); });"
if o in h and 'classList.remove' not in h.split(o)[0].split('\n')[-1]:
    h = h.replace(o, n, 1); c+=1; print('FIX4 OK')
else: print('FIX4 SKIP')

# FIX 5: autoFillFecha
if 'autoFillFecha' not in h:
    o = '  function limpiarCaptura() {'
    n = '  function autoFillFecha() {\n    var ahora = new Date();\n    ahora.setMinutes(ahora.getMinutes() - ahora.getTimezoneOffset());\n    .value = ahora.toISOString().slice(0, 16);\n  }\n  function limpiarCaptura() {'
    if o in h:
        h = h.replace(o, n, 1); c+=1; print('FIX5 OK')
    else: print('FIX5 SKIP')
else: print('FIX5 SKIP')

# FIX 6: fecha auto en limpiar
o = "fecha.value = '';"
n = "autoFillFecha();"
if o in h:
    h = h.replace(o, n, 1); c+=1; print('FIX6 OK')
else: print('FIX6 SKIP')

# FIX 7: asset clickeable
o = 'contenedor.innerHTML = \'<div class="inv-tag found">\' +'
n = 'contenedor.innerHTML = \'<div class="inv-tag found" style="cursor:pointer" onclick="seleccionarAsset(this,\\x27\' + resultadoId + \'\\x27)">\' +'
if o in h:
    h = h.replace(o, n, 1); c+=1; print('FIX7 OK')
else: print('FIX7 SKIP')

# FIX 8: seleccionarAsset
if 'seleccionarAsset' not in h:
    o = '  function agregarOtroEquipo() {'
    fn = """  function seleccionarAsset(tag, rid) {
    var campo = tag.querySelector('.inv-field').textContent;
    var spans = tag.querySelectorAll('span');
    var desc = spans[3] ? spans[3].textContent : '';
    var texto = campo + ' | ' + desc;
    var m = {'computer_inv_result':'computer_details','phone_inv_result':'phone_details','other_equip_inv_result':'other_equip_input'};
    var iid = m[rid];
    if (iid && document.getElementById(iid)) { document.getElementById(iid).value = texto.trim(); document.getElementById(iid).classList.add('auto-filled'); }
    tag.style.outline = '2px solid var(--success)';
    tag.style.background = '#d1fae5';
  }
  function agregarOtroEquipo() {"""
    if o in h:
        h = h.replace(o, fn, 1); c+=1; print('FIX8 OK')
    else: print('FIX8 SKIP')
else: print('FIX8 SKIP')

with open(f, 'w', encoding='utf-8') as fh:
    fh.write(h)
print('Total:', c)

f = r'C:\OffboardingTCL\src\main\resources\static\index.html'
with open(f, 'r', encoding='utf-8') as fh:
    h = fh.read()
c = 0

# FIX3: auto-filled en buscarEmpleado
for fld, src in [('n','employeeName'),('dep','deptName'),('position','rollName'),('shift','turnName'),('work_area','areaName'),('jefe','superName')]:
    old = "if (r.data." + src + ") $('" + fld + "').value = r.data." + src + ";"
    new = "if (r.data." + src + ") { $('" + fld + "').value = r.data." + src + "; $('" + fld + "').classList.add('auto-filled'); }"
    if old in h:
        h = h.replace(old, new, 1); c+=1; print('FIX3 OK -', fld)

# FIX5: autoFillFecha (sin indentacion)
if 'autoFillFecha' not in h:
    old = 'function limpiarCaptura() {'
    nuevo = "function autoFillFecha() {\n    var ahora = new Date();\n    ahora.setMinutes(ahora.getMinutes() - ahora.getTimezoneOffset());\n    $('fecha').value = ahora.toISOString().slice(0, 16);\n  }\n  function limpiarCaptura() {"
    if old in h:
        h = h.replace(old, nuevo, 1); c+=1; print('FIX5 OK')
    else: print('FIX5 SKIP')

# FIX8: seleccionarAsset (sin indentacion)
if 'function seleccionarAsset' not in h:
    old = 'function agregarOtroEquipo() {'
    nuevo = """function seleccionarAsset(tag, rid) {
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
    if old in h:
        h = h.replace(old, nuevo, 1); c+=1; print('FIX8 OK')
    else: print('FIX8 SKIP')

with open(f, 'w', encoding='utf-8') as fh:
    fh.write(h)
print('Total:', c)

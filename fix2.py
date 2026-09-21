import sys
f = r'C:\OffboardingTCL\src\main\resources\static\index.html'
with open(f, 'r', encoding='utf-8') as fh:
    h = fh.read()
c = 0

# HOTFIX: linea 729 rota - arreglar el classList.remove
bad = "$(id).value = ''; .classList.remove('auto-filled'); });"
good = "$(id).value = ''; $(id).classList.remove('auto-filled'); });"
if bad in h:
    h = h.replace(bad, good, 1); c+=1; print('HOTFIX OK - linea 729')
else: print('HOTFIX SKIP')

# FIX3: verificar auto-filled en buscarEmpleado
if "classList.add('auto-filled')" in h:
    print('FIX3 ya aplicado')
else:
    print('FIX3 FALTA - revisar manualmente')

# FIX5: autoFillFecha
if 'autoFillFecha' not in h:
    old = '  function limpiarCaptura() {'
    nuevo = "  function autoFillFecha() {\n    var ahora = new Date();\n    ahora.setMinutes(ahora.getMinutes() - ahora.getTimezoneOffset());\n    $('fecha').value = ahora.toISOString().slice(0, 16);\n  }\n  function limpiarCaptura() {"
    if old in h:
        h = h.replace(old, nuevo, 1); c+=1; print('FIX5 OK')
    else: print('FIX5 SKIP - anchor no encontrado')
else: print('FIX5 ya existe')

# FIX6: fecha auto en limpiar
old6 = "$('fecha').value = '';"
new6 = "autoFillFecha();"
if old6 in h:
    h = h.replace(old6, new6, 1); c+=1; print('FIX6 OK')
else: print('FIX6 SKIP')

# FIX8: seleccionarAsset
if 'function seleccionarAsset' not in h:
    old8 = '  function agregarOtroEquipo() {'
    nuevo8 = """  function seleccionarAsset(tag, rid) {
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
    if old8 in h:
        h = h.replace(old8, nuevo8, 1); c+=1; print('FIX8 OK')
    else: print('FIX8 SKIP')
else: print('FIX8 ya existe')

with open(f, 'w', encoding='utf-8') as fh:
    fh.write(h)
print('Total:', c)

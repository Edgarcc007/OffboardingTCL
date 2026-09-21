f = r'C:\OffboardingTCL\src\main\resources\static\index.html'
with open(f, 'r', encoding='utf-8') as fh:
    h = fh.read()
c = 0

# FIX5: insertar autoFillFecha justo antes de limpiarCaptura
if 'function autoFillFecha' not in h:
    old = 'function limpiarCaptura() {'
    nuevo = """function autoFillFecha() {
    var ahora = new Date();
    ahora.setMinutes(ahora.getMinutes() - ahora.getTimezoneOffset());
    $('fecha').value = ahora.toISOString().slice(0, 16);
}
function limpiarCaptura() {"""
    if old in h:
        h = h.replace(old, nuevo, 1); c+=1; print('FIX5 OK - autoFillFecha definida')
    else: print('FIX5 SKIP')
else: print('FIX5 ya existe')

with open(f, 'w', encoding='utf-8') as fh:
    fh.write(h)
print('Total:', c)

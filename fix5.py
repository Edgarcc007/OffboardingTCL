f = r'C:\OffboardingTCL\src\main\resources\static\index.html'
with open(f, 'r', encoding='utf-8') as fh:
    h = fh.read()

old = '  actualizarIndicadorSemana();'
new = '  actualizarIndicadorSemana();\n  autoFillFecha();'
if old in h and 'autoFillFecha();' not in h.split('async function init')[1].split('}')[0]:
    h = h.replace(old, new, 1)
    print('OK - autoFillFecha en init()')
else:
    print('SKIP')

with open(f, 'w', encoding='utf-8') as fh:
    fh.write(h)

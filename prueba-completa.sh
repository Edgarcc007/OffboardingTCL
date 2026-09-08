#!/usr/bin/env bash
BASE='http://localhost:8080'
ADMIN='admin:TCLAdmin2026#'
ANA='ana.torres:AnaTorres2026#Ej'
LUIS='luis.medina:LuisMedina2026#Vl'

getid() {  # $1=archivo  $2=cual ocurrencia
  if command -v python3 >/dev/null 2>&1; then
    python3 - "$1" "$2" <<'PY'
import json,sys
d=json.load(open(sys.argv[1])); n=int(sys.argv[2]); out=[]
def walk(o):
    if isinstance(o,dict):
        if 'id' in o and isinstance(o['id'],int): out.append(o['id'])
        for v in o.values(): walk(v)
    elif isinstance(o,list):
        for i in o: walk(i)
walk(d)
print(out[n] if len(out)>n else '')
PY
  else
    grep -o '"id":[0-9]*' "$1" | sed -n "$(($2+1))p" | cut -d: -f2
  fi
}

echo "=========== 1. USUARIOS ==========="
curl -s -o /tmp/a.json -w 'Ana  HTTP %{http_code}\n' -u "$ADMIN" -X POST "$BASE/api/users" \
 -H 'Content-Type: application/json' \
 -d '{"username":"ana.torres","fullName":"Ana Torres","email":"ana.torres@empresa.com","password":"AnaTorres2026#Ej","roles":["EJECUTOR"]}'
cat /tmp/a.json; echo
curl -s -o /tmp/l.json -w 'Luis HTTP %{http_code}\n' -u "$ADMIN" -X POST "$BASE/api/users" \
 -H 'Content-Type: application/json' \
 -d '{"username":"luis.medina","fullName":"Luis Medina","email":"luis.medina@empresa.com","password":"LuisMedina2026#Vl","roles":["SEGURIDAD"]}'
cat /tmp/l.json; echo

echo "=========== 2. REGISTRAR BAJA ==========="
curl -s -o /tmp/case.json -w 'HTTP %{http_code}\n' -u "$ADMIN" -X POST "$BASE/api/offboardings" \
 -H 'Content-Type: application/json' \
 -d '{"employeeName":"Roberto Salas","employeeIdentifier":"EMP-4471","corporateEmail":"roberto.salas@empresa.com","department":"Infraestructura TI","managerName":"Patricia Lugo","terminationType":"DESPIDO","effectiveAt":"2026-09-05T18:00:00Z","riskLevel":"CRITICO","confidential":true,"observations":"Administrador de dominio."}'
cat /tmp/case.json; echo
CASE_ID=$(getid /tmp/case.json 0)
echo ">> Caso creado: $CASE_ID"

echo "=========== 3. TAREAS GENERADAS ==========="
curl -s -u "$ADMIN" "$BASE/api/offboardings/$CASE_ID" -o /tmp/detail.json
cat /tmp/detail.json; echo
TASK_ID=$(getid /tmp/detail.json 1)
echo ">> Tarea elegida: $TASK_ID"

if [ -z "$TASK_ID" ]; then echo "!! No hay tareas. Detengo aqui."; exit 1; fi

echo "=========== 4. ANA COMPLETA LA TAREA (espero 200) ==========="
curl -s -w '\nHTTP %{http_code}\n' -u "$ANA" -X PATCH "$BASE/api/tasks/$TASK_ID/complete" \
 -H 'Content-Type: application/json' \
 -d '{"evidenceReference":"TICKET-INC-88231","comments":"Cuenta de dominio deshabilitada."}'

echo "=========== 5. ANA VALIDA SU PROPIA TAREA (espero 403) ==========="
curl -s -w '\nHTTP %{http_code}\n' -u "$ANA" -X PATCH "$BASE/api/tasks/$TASK_ID/validate"

echo "=========== 6. LUIS VALIDA (espero 200) ==========="
curl -s -w '\nHTTP %{http_code}\n' -u "$LUIS" -X PATCH "$BASE/api/tasks/$TASK_ID/validate"

echo "=========== 7. BITACORA ==========="
curl -s -u "$ADMIN" "$BASE/api/audit"; echo

echo "=========== 8. INMUTABILIDAD EN BASE DE DATOS ==========="
docker compose exec -T database bash -lc 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"' <<'SQL'
\dt
DO $$
DECLARE t text;
BEGIN
  SELECT table_name INTO t FROM information_schema.tables
   WHERE table_schema='public' AND table_name ILIKE '%audit%' LIMIT 1;
  RAISE NOTICE 'Tabla de auditoria detectada: %', t;
  EXECUTE format('UPDATE %I SET id = id', t);
  RAISE NOTICE '>>> ATENCION: el UPDATE fue PERMITIDO';
EXCEPTION WHEN others THEN
  RAISE NOTICE '>>> BLOQUEADO POR LA BASE: %', SQLERRM;
END $$;
SQL

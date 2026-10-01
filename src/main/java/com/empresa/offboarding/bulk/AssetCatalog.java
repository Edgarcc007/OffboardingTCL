package com.empresa.offboarding.bulk;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.*;
import java.util.zip.ZipFile;

@Service
public class AssetCatalog {
    public static final List<String> HEADERS=List.of(
        "Asset ID","Master Asset ID","Name","Model","Serial Number / Parent Serial",
        "DevType","Nivel de Importancia","Tiempo de Vida","Operating System","User",
        "Department","DeptoArea","Finance","Company","Building/Floor","Location",
        "Station (Sub-Location)","Status","Physical Inventory mm/dd/aaaa",
        "Inventory Comment","Log de cambios","Fecha de Modificación / Actualización"
    );

    private static final List<String> COLUMNS=List.of(
        "asset_id","master_asset_id","asset_name","model","serial_number",
        "dev_type","importance_level","lifetime","operating_system","user_name",
        "department","department_area","finance","company","building_floor","location",
        "station","status","physical_inventory","inventory_comment",
        "change_log","modified_at_text"
    );

    private static final String AUDIT_RESET="audit-reset-20260925-r4";

    @PersistenceContext private EntityManager em;
    private final TransactionTemplate tx;

    public AssetCatalog(PlatformTransactionManager manager) {
        tx=new TransactionTemplate(manager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.setTimeout(300);
    }

    public record RawRow(String sheet,int row,List<String> values) {}
    public record Hit(long id,Map<String,String> values) {}
    public record Page(List<Hit> items,boolean hasMore) {}

    public static String fold(String value) {
        return Normalizer.normalize(Objects.toString(value,""),Normalizer.Form.NFD)
            .replaceAll("\\p{M}+","").toLowerCase(Locale.ROOT)
            .replaceAll("[\\s\\p{Z}]+"," ").strip();
    }

    private static String headerKey(String value) {
        return fold(value).replaceAll("[^a-z0-9]","");
    }

    private Query query(String sql,Object... parameters) {
        Query q=em.createNativeQuery(sql);
        for(int i=0;i<parameters.length;i+=2)
            q.setParameter((String)parameters[i],parameters[i+1]);
        return q;
    }

    private boolean done(String step) {
        return !query(
            "SELECT step_id FROM offboarding_setup_done WHERE step_id=:step",
            "step",step).getResultList().isEmpty();
    }

    private void mark(String step) {
        query("""
            INSERT INTO offboarding_setup_done(step_id) VALUES(:step)
            ON CONFLICT(step_id) DO NOTHING
            ""","step",step).executeUpdate();
    }

    public static List<RawRow> readWorkbook(Path file) throws Exception {
        if(Files.size(file)>20L*1024*1024)
            throw new IllegalArgumentException("El inventario excede 20 MB.");

        try(ZipFile zip=new ZipFile(file.toFile())) {
            long expanded=0;
            int count=0;
            var entries=zip.entries();
            while(entries.hasMoreElements()) {
                var entry=entries.nextElement();
                if(++count>4096 || entry.getSize()<0)
                    throw new IllegalArgumentException("Estructura XLSX no admitida.");
                expanded+=entry.getSize();
                if(expanded>128L*1024*1024)
                    throw new IllegalArgumentException("El XLSX expandido es demasiado grande.");
            }
        }

        List<RawRow> result=new ArrayList<>();
        DataFormatter formatter=new DataFormatter(Locale.US,true);
        formatter.setUseCachedValuesForFormulaCells(true);

        try(InputStream input=Files.newInputStream(file);
            Workbook workbook=WorkbookFactory.create(input)) {

            for(Sheet sheet:workbook) {
                Row header=null;
                Map<String,Integer> positions=null;
                boolean content=false;

                for(Row candidate:sheet) {
                    Map<String,Integer> found=new HashMap<>();
                    for(Cell cell:candidate) {
                        String text=formatter.formatCellValue(cell);
                        if(!text.isBlank()) content=true;
                        if(!text.isBlank())
                            found.put(headerKey(text),cell.getColumnIndex());
                    }
                    if(HEADERS.stream().allMatch(h->found.containsKey(headerKey(h)))) {
                        header=candidate;positions=found;break;
                    }
                    if(candidate.getRowNum()>=49) break;
                }

                if(header==null) {
                    if(content) throw new IllegalArgumentException(
                        "La hoja '"+sheet.getSheetName()+"' no contiene los 22 encabezados esperados.");
                    continue;
                }

                for(Row row:sheet) {
                    if(row.getRowNum()<=header.getRowNum()) continue;

                    List<String> values=new ArrayList<>();
                    boolean nonempty=false;
                    for(String name:HEADERS) {
                        Cell cell=row.getCell(
                            positions.get(headerKey(name)),
                            Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                        String value=cell==null?"":
                            cell.getCellType()==CellType.STRING?
                                cell.getStringCellValue():formatter.formatCellValue(cell);
                        values.add(value);
                        if(!value.isBlank()) nonempty=true;
                    }

                    if(nonempty) {
                        result.add(new RawRow(
                            sheet.getSheetName(),row.getRowNum()+1,List.copyOf(values)));
                        if(result.size()>50000)
                            throw new IllegalArgumentException("Limite: 50000 filas de inventario.");
                    }
                }
            }
        }

        if(result.isEmpty())
            throw new IllegalArgumentException("El archivo no contiene registros de inventario.");
        return result;
    }

    public Map<String,Object> apply(Path file,String expectedHash) throws Exception {
        String actualHash=HexFormat.of().formatHex(
            MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
        if(!actualHash.equalsIgnoreCase(expectedHash))
            throw new IllegalArgumentException("El Excel cambio despues de preparar la actualizacion.");

        // Leer por completo antes de modificar PostgreSQL.
        List<RawRow> rows=readWorkbook(file);
        String importStep="inventory:"+actualHash;

        return tx.execute(transaction->{
            query("SELECT 1 FROM pg_advisory_xact_lock(194726,1)").getSingleResult();

            boolean importedNow=false;
            if(!done(importStep)) {
                // Esta tabla es exclusivamente el catalogo importado de esta funcion.
                query("DELETE FROM offboarding_asset_catalog").executeUpdate();

                String fields="source_hash,source_sheet,source_row,"+
                    String.join(",",COLUMNS)+",asset_key,user_key,name_key,search_text";
                List<String> placeholders=new ArrayList<>();
                for(int i=0;i<29;i++) placeholders.add(":p"+i);
                String insert="INSERT INTO offboarding_asset_catalog("+fields+") VALUES("+
                    String.join(",",placeholders)+")";

                for(RawRow row:rows) {
                    List<Object> data=new ArrayList<>();
                    data.add(actualHash);data.add(row.sheet());data.add(row.row());
                    data.addAll(row.values());
                    data.add(fold(row.values().get(0)));
                    data.add(fold(row.values().get(9)));
                    data.add(fold(row.values().get(2)));
                    data.add(fold(String.join(" ",row.values())));

                    Query statement=em.createNativeQuery(insert);
                    for(int i=0;i<data.size();i++)
                        statement.setParameter("p"+i,data.get(i));
                    statement.executeUpdate();
                }
                mark(importStep);
                importedNow=true;
            }

            int deleted=0;
            if(!done(AUDIT_RESET)) {
                query("SET LOCAL lock_timeout='15s'").executeUpdate();
                query("LOCK TABLE audit_event IN ACCESS EXCLUSIVE MODE").executeUpdate();

                List<?> triggers=query("""
                    SELECT tgname,CAST(tgenabled AS TEXT)
                    FROM pg_trigger
                    WHERE tgrelid=to_regclass('audit_event')
                      AND NOT tgisinternal
                    """).getResultList();

                for(Object value:triggers) {
                    Object[] trigger=(Object[])value;
                    if(!"D".equals(trigger[1]))
                        query("ALTER TABLE audit_event DISABLE TRIGGER "+
                            identifier(trigger[0].toString())).executeUpdate();
                }

                deleted=query("DELETE FROM audit_event").executeUpdate();

                for(Object value:triggers) {
                    Object[] trigger=(Object[])value;
                    String mode=trigger[1].toString();
                    if("D".equals(mode)) continue;
                    String command=switch(mode) {
                        case "R" -> "ENABLE REPLICA TRIGGER ";
                        case "A" -> "ENABLE ALWAYS TRIGGER ";
                        default -> "ENABLE TRIGGER ";
                    };
                    query("ALTER TABLE audit_event "+command+
                        identifier(trigger[0].toString())).executeUpdate();
                }

                mark(AUDIT_RESET);
            }

            long count=((Number)query(
                "SELECT COUNT(*) FROM offboarding_asset_catalog")
                .getSingleResult()).longValue();

            return Map.<String,Object>of(
                "completed",true,"sourceHash",actualHash,
                "assetRows",count,"auditDeleted",deleted,
                "importedNow",importedNow);
        });
    }

    private static String identifier(String value) {
        return "\""+value.replace("\"","\"\"")+"\"";
    }

    private static String like(String value) {
        return value.replace("!","!!").replace("%","!%").replace("_","!_");
    }

    /* R6_TYPED_ASSET_SEARCH */
    public Page search(String term,int offset) {
        return search(term,offset,"all");
    }

    public Page search(String term,int offset,String kind) {
        String typeClause=r6TypeClause(kind);
        String text=fold(term);
        if(text.length()>256 || offset<0 || offset>50000)
            throw new IllegalArgumentException("Busqueda fuera de los limites.");
        if(text.isBlank()) return new Page(List.of(),false);

        List<String> tokens=Arrays.stream(text.split(" "))
            .filter(s->!s.isBlank()).limit(12).toList();

        return tx.execute(transaction->{
            StringBuilder where=new StringBuilder();
            for(int i=0;i<tokens.size();i++) {
                if(i>0) where.append(" AND ");
                where.append("search_text LIKE :t").append(i).append(" ESCAPE '!'");
            }

            String sql="SELECT id,"+String.join(",",COLUMNS)+
                " FROM offboarding_asset_catalog WHERE "+where+typeClause+
                """
                 ORDER BY CASE
                    WHEN asset_key=:exact THEN 0
                    WHEN user_key=:exact THEN 1
                    WHEN user_key LIKE :prefix ESCAPE '!' THEN 2
                    WHEN asset_key LIKE :prefix ESCAPE '!' THEN 3
                    WHEN name_key LIKE :prefix ESCAPE '!' THEN 4
                    ELSE 5 END,id
                 LIMIT 51 OFFSET :offset
                """;

            Query request=em.createNativeQuery(sql);
            request.setParameter("exact",text);
            request.setParameter("prefix",like(text)+"%");
            request.setParameter("offset",offset);
            for(int i=0;i<tokens.size();i++)
                request.setParameter("t"+i,"%"+like(tokens.get(i))+"%");

            List<?> found=request.getResultList();
            List<Hit> hits=new ArrayList<>();
            for(Object value:found.subList(0,Math.min(50,found.size()))) {
                Object[] row=(Object[])value;
                Map<String,String> fields=new LinkedHashMap<>();
                for(int i=0;i<HEADERS.size();i++)
                    fields.put(HEADERS.get(i),Objects.toString(row[i+1],""));
                hits.add(new Hit(((Number)row[0]).longValue(),fields));
            }
            return new Page(hits,found.size()>50);
        });
    }

    /* R6_CATALOG_MAINTENANCE */
    @org.springframework.beans.factory.annotation.Autowired
    private com.empresa.offboarding.service.AuditService r6Audit;

    public record CatalogInfo(long rows,String revision,String sourceHash,String updatedAt) {}
    public record UpdateResult(
        CatalogInfo current,int appliedRows,boolean replayed,boolean superseded
    ) {}

    public static String r6TypeClause(String kind) {
        return switch(Objects.toString(kind,"all").strip().toLowerCase(Locale.ROOT)) {
            case "all" -> "";
            case "computer" ->
                " AND lower(trim(dev_type)) IN ('desktop','laptop','mini pc','mini tower','workstation') ";
            case "phone" ->
                " AND lower(trim(dev_type)) IN ('phone','telephone') ";
            default -> throw new IllegalArgumentException("Unknown equipment type.");
        };
    }

    private CatalogInfo r6InfoDirect() {
        Object[] row=(Object[])query("""
            SELECT COUNT(*),
                md5(COALESCE(
                    string_agg(md5(CAST(to_jsonb(a) AS text)),'' ORDER BY a.id),''
                )),
                COALESCE(MIN(source_hash),''),
                COALESCE(MAX(source_hash),'')
            FROM offboarding_asset_catalog a
            """).getSingleResult();

        String hash=Objects.equals(row[2],row[3])?
            Objects.toString(row[2],""):"mixed";
        List<?> dates=query("""
            SELECT applied_at FROM offboarding_setup_done
            WHERE step_id='inventory-current-r6' OR step_id=:initial
            ORDER BY applied_at DESC LIMIT 1
            ""","initial","inventory:"+hash).getResultList();

        return new CatalogInfo(
            ((Number)row[0]).longValue(),
            row[1].toString(),
            hash,
            dates.isEmpty()?"":dates.get(0).toString()
        );
    }

    public CatalogInfo r6Info() {
        return tx.execute(status->r6InfoDirect());
    }

    public Hit r6Record(long id) {
        if(id<=0) throw new IllegalArgumentException("Invalid inventory record.");
        return tx.execute(status->{
            List<?> rows=query(
                "SELECT id,"+String.join(",",COLUMNS)+
                " FROM offboarding_asset_catalog WHERE id=:id","id",id).getResultList();

            if(rows.isEmpty()) throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND,
                "The inventory changed. Search again before selecting equipment.");

            Object[] data=(Object[])rows.get(0);
            Map<String,String> fields=new LinkedHashMap<>();
            for(int i=0;i<HEADERS.size();i++)
                fields.put(HEADERS.get(i),Objects.toString(data[i+1],""));
            return new Hit(((Number)data[0]).longValue(),fields);
        });
    }

    public UpdateResult r6Replace(
        List<RawRow> rows,String fileHash,String expectedRevision,
        long ownerId,String operation,String fileName,boolean completeInventory
    ) {
        if(!completeInventory)
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST,
                "Confirm that this file contains the COMPLETE inventory.");

        if(rows==null||rows.isEmpty()||rows.size()>50000||
            rows.stream().anyMatch(r->r.values()==null||r.values().size()!=HEADERS.size()))
            throw new IllegalArgumentException("Invalid inventory content.");

        return tx.execute(status->{
            query("SET LOCAL lock_timeout='20s'").executeUpdate();
            query("SELECT 1 FROM pg_advisory_xact_lock(194726,1)").getSingleResult();
            query("LOCK TABLE offboarding_asset_catalog IN SHARE ROW EXCLUSIVE MODE").executeUpdate();

            // Revalidar ADMIN directamente en PostgreSQL, sin confiar en el navegador.
            List<?> administrators=query("""
                SELECT u.username FROM app_user u
                WHERE u.id=:owner AND u.enabled=true
                  AND EXISTS (
                    SELECT 1 FROM app_user_role r
                    WHERE r.user_id=u.id AND r.role IN ('ADMIN','IT_ENGINEER_VALIDATOR')
                  )
                ""","owner",ownerId).getResultList();

            if(administrators.isEmpty())
                throw new org.springframework.security.access.AccessDeniedException(
                    "Only an enabled ADMIN can update inventory.");

            CatalogInfo current=r6InfoDirect();
            String operationKey="inventory-r6:"+operation;

            if(done(operationKey))
                return new UpdateResult(
                    current,rows.size(),true,!fileHash.equals(current.sourceHash()));

            if(!Objects.equals(current.revision(),expectedRevision))
                throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT,
                    "The inventory changed after preview. Analyze the Excel again.");

            // Reemplazo exclusivo de este catalogo; no modificar casos ni tareas.
            query("DELETE FROM offboarding_asset_catalog").executeUpdate();

            String columns="source_hash,source_sheet,source_row,"+
                String.join(",",COLUMNS)+",asset_key,user_key,name_key,search_text";
            List<String> placeholders=new ArrayList<>();
            for(int i=0;i<29;i++) placeholders.add(":v"+i);
            String sql="INSERT INTO offboarding_asset_catalog("+columns+") VALUES("+
                String.join(",",placeholders)+")";

            for(RawRow row:rows) {
                List<Object> values=new ArrayList<>();
                values.add(fileHash);
                values.add(row.sheet());
                values.add(row.row());
                values.addAll(row.values());
                values.add(fold(row.values().get(0)));
                values.add(fold(row.values().get(9)));
                values.add(fold(row.values().get(2)));
                values.add(fold(String.join(" ",row.values())));

                Query insert=em.createNativeQuery(sql);
                for(int i=0;i<values.size();i++)
                    insert.setParameter("v"+i,values.get(i));
                insert.executeUpdate();
            }

            mark(operationKey);
            query("""
                INSERT INTO offboarding_setup_done(step_id)
                VALUES('inventory-current-r6')
                ON CONFLICT(step_id)
                DO UPDATE SET applied_at=CURRENT_TIMESTAMP
                """).executeUpdate();

            r6Audit.record(
                administrators.get(0).toString(),
                "ASSET_CATALOG_UPDATED","AssetCatalog",null,
                "archivo="+fileName+
                "; registrosAnteriores="+current.rows()+
                "; registrosImportados="+rows.size()+
                "; sha256="+fileHash);

            return new UpdateResult(r6InfoDirect(),rows.size(),false,false);
        });
    }
}
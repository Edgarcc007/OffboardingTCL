package com.empresa.offboarding.bulk;

import com.empresa.offboarding.service.AuditService;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import jakarta.persistence.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Service
public class AdminMaintenance {
    @PersistenceContext private EntityManager em;
    private final BulkWorkflow workflow;
    private final AuditService audit;
    private final ObjectMapper json;
    private final TransactionTemplate readTx,writeTx;
    private final Map<String,Permit> permits=new ConcurrentHashMap<>();

    public record View(
        String kind,long id,String label,String revision,
        Map<String,String> fields,long tasks,long events,long bulkRows,String confirmation
    ) {}
    public record Change(
        String revision,Map<String,String> fields,String reason,
        String confirmation,boolean dryRun,String proof
    ) {}
    public record Result(
        boolean saved,boolean dryRun,String proof,
        long cases,long tasks,long events,String caseNumber
    ) {}
    private record Permit(long owner,String kind,long id,String revision,Instant expires) {}
    private record Data(View view,JsonNode raw,List<Object[]> links) {}

    public AdminMaintenance(
        BulkWorkflow workflow,AuditService audit,ObjectMapper json,
        PlatformTransactionManager manager
    ) {
        this.workflow=workflow;this.audit=audit;this.json=json;
        readTx=new TransactionTemplate(manager);
        readTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        readTx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        readTx.setReadOnly(true);readTx.setTimeout(45);
        writeTx=new TransactionTemplate(manager);
        writeTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        writeTx.setTimeout(45);
    }

    private Query q(String sql,Object... args) {
        Query query=em.createNativeQuery(sql);
        for(int i=0;i<args.length;i+=2)query.setParameter((String)args[i],args[i+1]);
        return query;
    }

    private BulkWorkflow.Actor owner(Authentication auth) {
        BulkWorkflow.Actor owner=workflow.actor(auth);
        return readTx.execute(s->{checkAdmin(owner);return owner;});
    }

    private void checkAdmin(BulkWorkflow.Actor owner) {
        List<?> found=q("""
            SELECT u.username FROM app_user u
            WHERE u.id=:id AND u.enabled=true
              AND EXISTS (
                SELECT 1 FROM app_user_role r
                WHERE r.user_id=u.id AND r.role='ADMIN'
              )
            ""","id",owner.id()).getResultList();
        if(found.isEmpty()||!owner.username().equals(found.get(0).toString()))
            throw error(403,"An enabled ADMIN account is required.");
    }

    public void authorize(Authentication auth) { owner(auth); }

    public View get(String kind,long id,Authentication auth) {
        BulkWorkflow.Actor owner=owner(auth);
        return readTx.execute(s->{checkAdmin(owner);return data(kind,id).view();});
    }

    private void lock() {
        q("SET LOCAL lock_timeout='10s'").executeUpdate();
        q("SELECT 1 FROM pg_advisory_xact_lock(194727,1)").getSingleResult();
        q("""
            LOCK TABLE bulk_import_batch,bulk_import_delivery,bulk_import_row,
                bulk_import_omission,offboarding_case,offboarding_task,
                offboarding_case_access IN SHARE ROW EXCLUSIVE MODE
            """).executeUpdate();
        q("LOCK TABLE audit_event IN ACCESS EXCLUSIVE MODE").executeUpdate();
    }

    public Result change(
        String kind,long id,String action,Change request,Authentication auth
    ) {
        BulkWorkflow.Actor owner=owner(auth);
        if(request==null||request.revision()==null)
            throw error(400,"Preview the current record first.");
        boolean deleting="delete".equals(action);
        if(!deleting&&!"edit".equals(action))throw error(400,"Unknown action.");
        if(deleting&&"task".equals(kind))
            throw error(400,"Tasks are removed with their case. Individual task deletion is not enabled.");
        if(!deleting&&"all".equals(kind))throw error(400,"This operation only supports deletion.");

        permits.entrySet().removeIf(e->e.getValue().expires().isBefore(Instant.now()));

        if(deleting&&!request.dryRun()) {
            Permit permit=permits.get(Objects.toString(request.proof(),""));
            if(permit==null||permit.owner()!=owner.id()||!permit.kind().equals(kind)||
                permit.id()!=id||!permit.revision().equals(request.revision()))
                throw error(409,"Run the rollback validation before confirming deletion.");
        }

        Result result=writeTx.execute(status->{
            lock();checkAdmin(owner);
            Data data=data(kind,id);
            View view=data.view();

            if(!view.revision().equals(request.revision()))
                throw error(409,"The record changed. Close this window and preview it again.");

            if(deleting) {
                if(!view.confirmation().equals(request.confirmation()))
                    throw error(400,"The confirmation text does not match.");

                String number="case".equals(kind)?data.raw().path("case_number").asText():"";
                if("case".equals(kind)) {
                    scrubBulk(data);
                    withWritableAudit(()->{
                        deleteAudit(kind,id);
                        return null;
                    });
                    q("DELETE FROM offboarding_case WHERE id=:id","id",id).executeUpdate();
                } else {
                    withWritableAudit(()->{deleteAudit(kind,id);return null;});
                }

                // Constancia general, sin empleado, folio ni ID del caso borrado.
                audit.record(owner.username(),"ADMIN_RECORDS_DELETED","Maintenance",null,
                    "casos="+("case".equals(kind)?1:0)+
                    "; tareas="+view.tasks()+"; eventos="+view.events());

                if(request.dryRun())status.setRollbackOnly();
                return new Result(!request.dryRun(),request.dryRun(),"",
                    "case".equals(kind)?1:0,view.tasks(),view.events(),number);
            }

            edit(data,request,owner);
            return new Result(true,false,"",0,0,0,"");
        });

        if(result.dryRun()) {
            if(permits.size()>=128)throw error(429,"Too many pending confirmations. Wait and retry.");
            String proof=UUID.randomUUID().toString();
            permits.put(proof,new Permit(owner.id(),kind,id,request.revision(),
                Instant.now().plusSeconds(300)));
            return new Result(false,true,proof,result.cases(),result.tasks(),result.events(),result.caseNumber());
        }
        if(deleting)permits.remove(Objects.toString(request.proof(),""));
        return result;
    }

    private static Map<String,Integer> editable(String kind) {
        return switch(kind) {
            case "case" -> Map.of(
                "employee_name",150,"corporate_email",150,"department",100,
                "building",100,"work_area",150,"manager_name",150,"observations",4000);
            case "task" -> Map.of(
                "comments",4000,"evidence_reference",200,
                "validation_comments",4000,"inventory_reference",200);
            case "audit" -> Map.of("details",4000);
            default -> Map.of();
        };
    }

    private Data data(String kind,long requestedId) {
        if(!Set.of("case","task","audit","all").contains(kind))
            throw error(400,"Unknown record type.");
        long id=requestedId;
        if("all".equals(kind)&&id==0)
            id=((Number)q("SELECT COALESCE(MAX(id),0) FROM audit_event").getSingleResult()).longValue();
        if(id<0||(!"all".equals(kind)&&id==0))throw error(400,"Invalid record ID.");

        String table=switch(kind) {
            case "case" -> "offboarding_case";
            case "task" -> "offboarding_task";
            case "audit" -> "audit_event";
            default -> null;
        };

        JsonNode raw=json.createObjectNode();
        List<String> signature=new ArrayList<>();
        Map<String,String> fields=new LinkedHashMap<>();
        List<Object[]> links=new ArrayList<>();
        long tasks=0;
        String label="Audit events through #"+id;
        String confirmation="BORRAR AUDITORIA";

        if(table!=null) {
            List<?> rows=q("SELECT CAST(to_jsonb(t) AS TEXT) FROM "+table+" t WHERE id=:id",
                "id",id).getResultList();
            if(rows.isEmpty())throw error(404,"Record no longer exists.");
            String encoded=rows.get(0).toString();
            raw=parse(encoded);signature.add(encoded);

            List<String> keys=new ArrayList<>(editable(kind).keySet());
            Collections.sort(keys);
            for(String key:keys)fields.put(key,raw.path(key).isNull()?"":raw.path(key).asText(""));

            if("case".equals(kind)) {
                label=raw.path("case_number").asText()+" | "+
                    raw.path("employee_identifier").asText()+" | "+raw.path("employee_name").asText();
                confirmation=raw.path("case_number").asText();

                List<?> taskRows=q("""
                    SELECT CAST(to_jsonb(t) AS TEXT) FROM offboarding_task t
                    WHERE offboarding_case_id=:id ORDER BY id
                    ""","id",id).getResultList();
                tasks=taskRows.size();
                taskRows.forEach(v->signature.add(v.toString()));

                q("""
                    SELECT access_type FROM offboarding_case_access
                    WHERE offboarding_case_id=:id ORDER BY access_type
                    ""","id",id).getResultList().forEach(v->signature.add(v.toString()));

                for(Object value:q("""
                    SELECT r.batch_id,r.row_no,b.document,b.name,b.version,b.last_hash,
                           r.payload,d.state,d.payload,d.subject
                    FROM bulk_import_row r JOIN bulk_import_batch b ON b.id=r.batch_id
                    LEFT JOIN bulk_import_delivery d ON d.batch_id=b.id
                    WHERE r.case_id=:id ORDER BY r.batch_id,r.row_no
                    ""","id",id).getResultList()) {
                    Object[] row=(Object[])value;
                    links.add(row);
                    for(Object field:row)signature.add(Objects.toString(field,""));
                }
            } else if("task".equals(kind)) {
                label="Task #"+id+" | "+raw.path("system_name").asText()+
                    " | "+raw.path("task_name").asText();
                confirmation="TASK "+id;
            } else {
                label="Audit #"+id+" | "+raw.path("action").asText()+
                    " | "+raw.path("actor").asText();
                confirmation="AUDIT "+id;
            }
        }

        Object[] auditState=(Object[])q(auditCte(kind)+"""
            SELECT COUNT(*),md5(COALESCE(
                string_agg(md5(CAST(to_jsonb(a) AS TEXT)),'' ORDER BY a.id),''))
            FROM audit_event a JOIN chosen x ON x.id=a.id
            ""","id",id).getSingleResult();
        signature.add(auditState[0].toString());signature.add(auditState[1].toString());

        return new Data(new View(kind,id,label,hash(String.join("\n",signature)),
            fields,tasks,((Number)auditState[0]).longValue(),links.size(),confirmation),
            raw,links);
    }

    private String auditCte(String kind) {
        String condition=switch(kind) {
            case "case" -> """
                (a.entity_type='OffboardingCase' AND a.entity_id=:id)
                OR (a.entity_type='OffboardingTask' AND a.entity_id IN (
                    SELECT id FROM offboarding_task WHERE offboarding_case_id=:id))
                """;
            case "task" -> "a.entity_type='OffboardingTask' AND a.entity_id=:id";
            case "audit" -> "a.id=:id";
            case "all" -> "a.id<=:id";
            default -> throw error(400,"Unknown type.");
        };
        if("all".equals(kind))
            return "WITH chosen AS (SELECT a.id FROM audit_event a WHERE "+condition+") ";
        return """
            WITH RECURSIVE chosen(id) AS (
                SELECT a.id FROM audit_event a WHERE
            """+condition+"""
                UNION
                SELECT a.id FROM audit_event a JOIN chosen p
                  ON a.entity_type='AuditEvent' AND a.entity_id=p.id
            )
            """;
    }

    private void deleteAudit(String kind,long id) {
        q(auditCte(kind)+" DELETE FROM audit_event WHERE id IN (SELECT id FROM chosen)",
            "id",id).executeUpdate();
    }

    private <T> T withWritableAudit(Supplier<T> action) {
        List<?> states=q("""
            SELECT CAST(tgenabled AS TEXT) FROM pg_trigger
            WHERE tgrelid=CAST('audit_event' AS regclass)
              AND tgname='trg_audit_event_immutable' AND NOT tgisinternal
            """).getResultList();
        if(states.size()!=1||!Set.of("O","A").contains(states.get(0).toString()))
            throw error(409,"Audit protection requires review before maintenance.");

        String mode=states.get(0).toString();
        q("ALTER TABLE audit_event DISABLE TRIGGER trg_audit_event_immutable").executeUpdate();
        T result=action.get();
        q("ALTER TABLE audit_event "+
            ("A".equals(mode)?"ENABLE ALWAYS":"ENABLE")+
            " TRIGGER trg_audit_event_immutable").executeUpdate();
        return result;
        // Ante excepcion, la transaccion completa revierte, incluido el ALTER.
    }

    public static String scrubDocument(ObjectMapper mapper,String source,int rowNumber) {
        try {
            JsonNode root=mapper.readTree(source);
            if(!(root instanceof ObjectNode)||!(root.path("rows") instanceof ArrayNode rows))
                throw new IllegalStateException("Unsupported batch document.");
            int removed=0;
            for(int i=rows.size()-1;i>=0;i--)
                if(rows.get(i).path("excelRow").asInt(-1)==rowNumber){rows.remove(i);removed++;}
            if(removed!=1)throw new IllegalStateException("Batch row could not be isolated.");
            return root.toString();
        } catch(Exception e) { throw new IllegalStateException("Unable to isolate the batch row.",e); }
    }

    public static String scrubDelivery(ObjectMapper mapper,String source,String number) {
        try {
            JsonNode root=mapper.readTree(source);
            if(!(root instanceof ArrayNode rows))
                throw new IllegalStateException("Unsupported delivery document.");
            int removed=0;
            for(int i=rows.size()-1;i>=0;i--)
                if(number.equals(rows.get(i).path("Folio").asText())){rows.remove(i);removed++;}
            if(removed!=1)throw new IllegalStateException("Delivery row could not be isolated.");
            return rows.toString();
        } catch(Exception e) { throw new IllegalStateException("Unable to isolate the delivery row.",e); }
    }

    private void scrubBulk(Data data) {
        String number=data.raw().path("case_number").asText();
        for(Object[] link:data.links()) {
            if("SENDING".equals(link[7]))
                throw error(409,"A notification is being sent. Resolve that delivery before deletion.");

            String batch=link[0].toString();
            int row=((Number)link[1]).intValue();
            String document=scrubDocument(json,link[2].toString(),row);

            q("""
                UPDATE bulk_import_row
                SET state='DELETED',case_id=NULL,case_number=NULL,
                    payload='{}',error=NULL,updated_at=CURRENT_TIMESTAMP
                WHERE batch_id=:batch AND row_no=:row
                ""","batch",batch,"row",row).executeUpdate();

            q("DELETE FROM bulk_import_omission WHERE batch_id=:batch AND row_no=:row",
                "batch",batch,"row",row).executeUpdate();

            boolean empty=parse(document).path("rows").isEmpty();
            q("""
                UPDATE bulk_import_batch SET document=:document,last_hash=:hash,
                    name=:name,version=version+1,updated_at=CURRENT_TIMESTAMP
                WHERE id=:batch
                ""","document",document,"hash",hash(document),
                "name",empty?"Lote sin registros":link[3].toString(),"batch",batch).executeUpdate();

            if(link[8]!=null) {
                String payload=scrubDelivery(json,link[8].toString(),number);
                int remaining=parse(payload).size();
                String state=remaining==0?"NOT_NEEDED":Objects.toString(link[7],"UNKNOWN");
                String subject=remaining==0?"Bulk Offboarding - sin registros":
                    "Bulk Offboarding | "+remaining+" registros | "+batch;

                q("""
                    UPDATE bulk_import_delivery SET payload=:payload,state=:state,
                        subject=:subject,updated_at=CURRENT_TIMESTAMP
                    WHERE batch_id=:batch
                    ""","payload",payload,"state",state,"subject",subject,"batch",batch).executeUpdate();
            }
        }
    }

    private void edit(Data data,Change request,BulkWorkflow.Actor owner) {
        String kind=data.view().kind();
        Map<String,Integer> limits=editable(kind);
        if(request.fields()==null||request.fields().isEmpty()||
            !limits.keySet().containsAll(request.fields().keySet()))
            throw error(400,"Unknown or missing editable fields.");
        String reason=Objects.toString(request.reason(),"").strip();
        if(reason.length()<5||reason.length()>500)
            throw error(400,"Enter a reason between 5 and 500 characters.");

        Map<String,String> values=new LinkedHashMap<>();
        for(var entry:request.fields().entrySet()) {
            String value=Objects.toString(entry.getValue(),"").strip();
            if(value.length()>limits.get(entry.getKey()))
                throw error(400,"A field exceeds its maximum length.");
            if("case".equals(kind)&&Set.of("employee_name","department","work_area").contains(entry.getKey())&&value.isEmpty())
                throw error(400,"Employee name, department and work area cannot be empty.");
            if("corporate_email".equals(entry.getKey())&&!value.isEmpty()&&
                !value.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))
                throw error(400,"Invalid email format.");
            values.put(entry.getKey(),value.isEmpty()?null:value);
        }

        for(Object[] link:data.links())
            if("SENDING".equals(link[7]))
                throw error(409,"A notification is being sent. Wait before editing.");

        String table=switch(kind) {
            case "case" -> "offboarding_case";
            case "task" -> "offboarding_task";
            case "audit" -> "audit_event";
            default -> throw error(400,"Unsupported edit.");
        };

        Supplier<Void> update=()->{
            List<String> assignments=new ArrayList<>();
            int n=0;
            for(String key:values.keySet())assignments.add(key+"=CAST(:v"+n+++" AS TEXT)");
            Query statement=q("UPDATE "+table+" SET "+String.join(",",assignments)+" WHERE id=:id",
                "id",data.view().id());
            n=0;
            for(String value:values.values())statement.setParameter("v"+n++,value);
            statement.executeUpdate();return null;
        };
        if("audit".equals(kind))withWritableAudit(update);else update.get();

        if("case".equals(kind)) {
            JsonNode current=parse(q("""
                SELECT CAST(to_jsonb(c) AS TEXT) FROM offboarding_case c WHERE id=:id
                ""","id",data.view().id()).getSingleResult().toString());
            for(Object[] link:data.links()) {
                if(link[8]==null||Set.of("SENT","UNKNOWN","NOT_NEEDED").contains(Objects.toString(link[7],"")))
                    continue;
                JsonNode payload=parse(link[8].toString());
                if(!(payload instanceof ArrayNode))
                    throw error(409,"Unsupported pending delivery.");
                int changed=0;
                for(JsonNode row:payload) {
                    if(current.path("case_number").asText().equals(row.path("Folio").asText())) {
                        ((ObjectNode)row).put("Empleado",current.path("employee_name").asText());
                        ((ObjectNode)row).put("Departamento",current.path("department").asText());
                        ((ObjectNode)row).put("Area",current.path("work_area").asText());
                        changed++;
                    }
                }
                if(changed!=1)throw error(409,"Pending delivery could not be synchronized.");
                q("UPDATE bulk_import_delivery SET payload=:payload,updated_at=CURRENT_TIMESTAMP WHERE batch_id=:id",
                    "payload",payload.toString(),"id",link[0]).executeUpdate();
            }
        }

        audit.record(owner.username(),
            "case".equals(kind)?"CASE_DETAILS_EDITED":
            "task".equals(kind)?"TASK_DETAILS_EDITED":"AUDIT_DETAILS_EDITED",
            "case".equals(kind)?"OffboardingCase":
            "task".equals(kind)?"OffboardingTask":"AuditEvent",
            data.view().id(),"motivo="+reason+"; campos="+values.keySet());
    }

    private JsonNode parse(String value) {
        try{return json.readTree(value);}
        catch(Exception e){throw new IllegalStateException("Stored document requires review.",e);}
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch(Exception e){throw new IllegalStateException(e);}
    }

    private static ResponseStatusException error(int status,String text) {
        return new ResponseStatusException(HttpStatus.valueOf(status),text);
    }
}
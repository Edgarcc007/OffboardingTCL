package com.empresa.offboarding.bulk;

import com.empresa.offboarding.dto.*;
import com.empresa.offboarding.enums.AppRole;
import com.empresa.offboarding.service.*;
import com.fasterxml.jackson.databind.*;
import jakarta.persistence.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

import static com.empresa.offboarding.bulk.AdminTaskPolicy.error;

@Service
public class AdminTaskOperations {
    private static final Set<AppRole> ADMIN=Set.of(AppRole.ADMIN);
    private static final Set<AppRole> EXECUTORS=Set.of(
        AppRole.ADMIN,AppRole.IT_ENGINEER,AppRole.IT_ENGINEER_VALIDATOR,AppRole.CONTROL_ACCESOS);
    private static final Set<AppRole> VALIDATORS=Set.of(AppRole.ADMIN,AppRole.CONTROL_ACCESOS);

    @PersistenceContext private EntityManager em;
    private final OffboardingService original;
    private final AuditService audit;
    private final ObjectMapper json;
    private final TransactionTemplate tx;

    public record Preview(
        long id,String status,String caseNumber,String employeeName,
        String taskName,String systemName,String completedBy,
        String revision,String operationId
    ) {}
    public record CombinedRequest(
        UUID operationId,String revision,boolean confirmed,String comments
    ) {}
    public record CombinedResult(TaskResponse task,boolean replayed) {}
    private record Locked(JsonNode task,JsonNode employeeCase,String revision) {}

    public AdminTaskOperations(
        OffboardingService original,AuditService audit,
        ObjectMapper json,PlatformTransactionManager manager
    ) {
        this.original=original;this.audit=audit;this.json=json;
        tx=new TransactionTemplate(manager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.setTimeout(45);
    }

    private Query q(String sql,Object... args) {
        Query query=em.createNativeQuery(sql);
        for(int i=0;i<args.length;i+=2)query.setParameter((String)args[i],args[i+1]);
        return query;
    }

    private AdminTaskPolicy.Actor actor(Authentication authentication,Set<AppRole> required) {
        var principal=AdminTaskPolicy.principal(authentication);

        q("SET LOCAL lock_timeout='10s'").executeUpdate();

        List<?> rows=q("""
            SELECT u.username,r.role
            FROM app_user u JOIN app_user_role r ON r.user_id=u.id
            WHERE u.id=:id AND u.enabled=TRUE
            FOR SHARE OF u,r
            ""","id",principal.getUserId()).getResultList();

        if(rows.isEmpty())throw error(403,"The account is disabled or unavailable.");
        String username=((Object[])rows.get(0))[0].toString();
        Set<AppRole> roles=EnumSet.noneOf(AppRole.class);
        for(Object value:rows)
            roles.add(AppRole.valueOf(((Object[])value)[1].toString()));

        return AdminTaskPolicy.verify(authentication,username,roles,required);
    }

    private Authentication authentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    public void authorizeAdmin(Authentication authentication) {
        tx.execute(status->{actor(authentication,ADMIN);return null;});
    }

    private Locked locked(long taskId) {
        if(taskId<=0)throw error(400,"Invalid task ID.");

        // Compatible con otras tareas; impide que mantenimiento borre
        // las tablas mientras esta operacion esta escribiendo.
        q("LOCK TABLE offboarding_case,offboarding_task IN ROW EXCLUSIVE MODE").executeUpdate();

        List<?> parents=q("""
            SELECT c.id FROM offboarding_case c
            JOIN offboarding_task t ON t.offboarding_case_id=c.id
            WHERE t.id=:id
            FOR UPDATE OF c
            ""","id",taskId).getResultList();
        if(parents.isEmpty())throw error(404,"The task no longer exists.");

        q("SELECT id FROM offboarding_task WHERE id=:id FOR UPDATE",
            "id",taskId).getSingleResult();

        Object[] row=(Object[])q("""
            SELECT CAST(to_jsonb(t) AS TEXT),CAST(to_jsonb(c) AS TEXT)
            FROM offboarding_task t
            JOIN offboarding_case c ON c.id=t.offboarding_case_id
            WHERE t.id=:id
            ""","id",taskId).getSingleResult();

        JsonNode task=parse(row[0].toString());
        JsonNode employeeCase=parse(row[1].toString());
        if("CANCELADA".equals(employeeCase.path("status").asText()))
            throw error(409,"The case is cancelled.");

        return new Locked(task,employeeCase,
            hash(row[0].toString()+"\n"+row[1].toString()));
    }

    public Preview preview(long taskId,Authentication authentication) {
        return tx.execute(status->{
            actor(authentication,ADMIN);
            Locked data=locked(taskId);
            return new Preview(taskId,data.task().path("status").asText(),
                data.employeeCase().path("case_number").asText(),
                data.employeeCase().path("employee_name").asText(),
                data.task().path("task_name").asText(),
                data.task().path("system_name").asText(),
                data.task().path("completed_by").asText(""),
                data.revision(),UUID.randomUUID().toString());
        });
    }

    public TaskResponse completeTask(
        Long taskId,CompleteTaskRequest request,String claimedUsername
    ) {
        return tx.execute(status->{
            var actor=actor(authentication(),EXECUTORS);
            sameName(actor,claimedUsername);
            locked(taskId);
            return original.completeTask(taskId,request,actor.username());
        });
    }

    public TaskResponse reopenTask(
        Long taskId,ReopenTaskRequest request,String claimedUsername
    ) {
        return tx.execute(status->{
            var actor=actor(authentication(),ADMIN);
            sameName(actor,claimedUsername);
            locked(taskId);
            // El trigger elimina el certificado al limpiar validated_at/by.
            return original.reopenTask(taskId,request,actor.username());
        });
    }

    public TaskResponse validateTask(
        Long taskId,ValidateAssetTaskRequest request,String claimedUsername
    ) {
        return tx.execute(status->{
            var actor=actor(authentication(),VALIDATORS);
            sameName(actor,claimedUsername);
            Locked data=locked(taskId);

            if(!"COMPLETADA".equals(data.task().path("status").asText()))
                throw error(409,"Only a completed task can be validated.");

            boolean self=actor.username().equalsIgnoreCase(
                data.task().path("completed_by").asText(""));

            if(!actor.admin()) {
                if(self)throw error(403,"Another authorized account must validate this task.");
                return original.validateTask(taskId,request,actor.username());
            }

            if(!request.assetReceived()||!request.inventoryUpdated())
                throw error(400,"Confirm completion/receipt and the corresponding record update.");

            String reference=clean(request.inventoryReference(),200);
            String comments=clean(request.validationComments(),2000);
            UUID operation=UUID.randomUUID();
            String requestHash=hash("validate\n"+taskId+"\n"+data.revision()+"\n"+
                Objects.toString(reference,"")+"\n"+Objects.toString(comments,""));

            if(self)administrativeContext(actor.id(),taskId);

            q("""
                UPDATE offboarding_task
                SET status='VALIDADA',
                    validated_by=:username,
                    validated_at=CAST(:stamp AS TIMESTAMPTZ),
                    asset_received=TRUE,inventory_updated=TRUE,
                    inventory_reference=CAST(:reference AS TEXT),
                    validation_comments=CAST(:comments AS TEXT),
                    admin_self_validation=:self,
                    admin_validation_user_id=CAST(:owner AS BIGINT),
                    admin_validation_operation=CAST(:operation AS UUID),
                    admin_validation_request_hash=CAST(:hash AS VARCHAR)
                WHERE id=:id
                """,
                "username",actor.username(),"stamp",Instant.now().toString(),
                "reference",reference,"comments",comments,"self",self,
                "owner",self?Long.toString(actor.id()):null,
                "operation",self?operation.toString():null,
                "hash",self?requestHash:null,"id",taskId).executeUpdate();

            audit.record(actor.username(),"TASK_VALIDATED","OffboardingTask",taskId,
                "sistema="+data.task().path("system_name").asText()+
                "; adminSelfValidation="+self+
                "; ejecutadaPor="+data.task().path("completed_by").asText()+
                "; comentarios="+Objects.toString(comments,""));

            return refresh(data,taskId);
        });
    }

    public CombinedResult combine(
        long taskId,CombinedRequest request,Authentication authentication
    ) {
        if(request==null||request.operationId()==null||
            request.revision()==null||!request.revision().matches("[0-9a-f]{64}")||
            !request.confirmed())
            throw error(400,"Preview the task and explicitly confirm completion and validation.");

        String comments=clean(request.comments(),2000);
        String requestHash=hash("combined\n"+taskId+"\n"+request.revision()+"\n"+
            Objects.toString(comments,""));

        return tx.execute(status->{
            var actor=actor(authentication,ADMIN);
            Locked data=locked(taskId);
            JsonNode task=data.task();

            if("VALIDADA".equals(task.path("status").asText())&&
                request.operationId().toString().equals(
                    task.path("admin_validation_operation").asText())&&
                task.path("admin_validation_user_id").asLong(-1)==actor.id()) {

                if(!requestHash.equals(task.path("admin_validation_request_hash").asText()))
                    throw error(409,"This operation ID was used with different data.");

                em.clear();
                TaskResponse existing=original.findById(
                    data.employeeCase().path("id").asLong()).tasks().stream()
                    .filter(t->t.getId()==taskId).findFirst()
                    .orElseThrow(()->error(404,"Task unavailable."));
                return new CombinedResult(existing,true);
            }

            if(!data.revision().equals(request.revision()))
                throw error(409,"The case or task changed. Close this window and preview again.");
            if(!AdminTaskPolicy.canCombine(task.path("status").asText()))
                throw error(409,"Use Validate for completed tasks, or reopen a processed task first.");

            administrativeContext(actor.id(),taskId);
            String stamp=Instant.now().toString();

            q("""
                UPDATE offboarding_task
                SET status='VALIDADA',
                    completed_by=:username,
                    completed_at=CAST(:stamp AS TIMESTAMPTZ),
                    validated_by=:username,
                    validated_at=CAST(:stamp AS TIMESTAMPTZ),
                    comments=COALESCE(CAST(:comments AS TEXT),comments),
                    validation_comments=CAST(:comments AS TEXT),
                    asset_received=TRUE,inventory_updated=TRUE,
                    admin_self_validation=TRUE,
                    admin_validation_user_id=:owner,
                    admin_validation_operation=CAST(:operation AS UUID),
                    admin_validation_request_hash=:hash
                WHERE id=:id
                """,
                "username",actor.username(),"stamp",stamp,"comments",comments,
                "owner",actor.id(),"operation",request.operationId().toString(),
                "hash",requestHash,"id",taskId).executeUpdate();

            String details="sistema="+task.path("system_name").asText()+
                "; adminCombined=true; operacion="+request.operationId()+
                "; comentarios="+Objects.toString(comments,"");

            audit.record(actor.username(),"TASK_COMPLETED","OffboardingTask",taskId,details);
            audit.record(actor.username(),"TASK_VALIDATED","OffboardingTask",taskId,details);

            return new CombinedResult(refresh(data,taskId),false);
        });
    }

    private void administrativeContext(long owner,long taskId) {
        q("""
            SELECT set_config('offboarding.admin_validation_actor',:owner,TRUE),
                   set_config('offboarding.admin_validation_task',:task,TRUE)
            ""","owner",Long.toString(owner),"task",Long.toString(taskId)).getSingleResult();
    }

    private TaskResponse refresh(Locked data,long taskId) {
        em.clear();
        return original.refreshAfterAdministrativeTask(
            data.employeeCase().path("id").asLong(),taskId);
    }

    private static void sameName(AdminTaskPolicy.Actor actor,String claimed) {
        if(!actor.username().equals(claimed))
            throw error(401,"The authenticated account changed.");
    }

    private static String clean(String value,int max) {
        if(value==null||value.isBlank())return null;
        String text=value.strip();
        if(text.length()>max)throw error(400,"A text field is too long.");
        return text;
    }

    private JsonNode parse(String text) {
        try{return json.readTree(text);}
        catch(Exception error){throw new IllegalStateException("Unable to read task data.",error);}
    }

    private static String hash(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(text.getBytes(StandardCharsets.UTF_8)));
        }catch(Exception error){throw new IllegalStateException(error);}
    }
}
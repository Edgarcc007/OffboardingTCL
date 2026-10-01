package com.empresa.offboarding.reporting;

import com.empresa.offboarding.entity.AuditEvent;
import jakarta.persistence.*;
import jakarta.persistence.metamodel.*;
import org.apache.poi.util.DefaultTempFileCreationStrategy;
import org.apache.poi.util.TempFile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.regex.*;

@Service
@Order(Ordered.HIGHEST_PRECEDENCE + 200)
public class AuditExplorer implements ApplicationRunner {
    public static final int PAGE_SIZE=50;
    public static final int EXPORT_LIMIT=20000;

    public enum SearchField { ALL, EMPLOYEE, NAME, ASSET }

    public record Filter(
        LocalDate from, LocalDate to, String q, SearchField field
    ) {}

    public record EventRow(
        String id, String timestamp, String actor, String code, String action,
        String entityType, String entityId, String caseNumber,
        String employeeNumber, String employeeName, String assets,
        String system, String task, String details
    ) {}

    public record Result(
        List<EventRow> items, long total, long cases, long actors,
        int offset, int pageSize, String snapshot, String zone
    ) {}

    private record SqlPart(String text,Map<String,Object> parameters) {}

    private static final Pattern ASSET_PATTERN=Pattern.compile(
        "\\bAsset\\s*ID\\s*:\\s*([^|;\\r\\n]+)",Pattern.CASE_INSENSITIVE);

    @PersistenceContext private EntityManager em;
    private final TransactionTemplate tx;
    private final Semaphore exportSlot=new Semaphore(1);
    private EntityType<AuditEvent> model;
    private String entityName;
    private String timeAttribute;
    private Class<?> timeType;

    @Value("${app.audit-report.zone:America/Los_Angeles}")
    private String zoneName;

    public AuditExplorer(PlatformTransactionManager manager) {
        tx=new TransactionTemplate(manager);
        tx.setReadOnly(true);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.setTimeout(60);
    }

    @Override
    public void run(ApplicationArguments arguments) throws Exception {
        Path temporary=Path.of("C:/OffboardingTCL/private-temp/poi");
        if(!Files.isDirectory(temporary)||!Files.isWritable(temporary))
            throw new IllegalStateException("El directorio temporal privado de reportes no esta disponible.");

        TempFile.setTempFileCreationStrategy(
            new DefaultTempFileCreationStrategy(temporary.toFile()));

        tx.execute(status->{
            initialize();
            Filter filter=new Filter(null,null,"",SearchField.ALL);
            SqlPart part=build(filter,0);
            bind(em.createQuery(select()+part.text()+" order by a.id desc",Object[].class),
                part.parameters()).setMaxResults(1).getResultList();
            return null;
        });
    }

    private void initialize() {
        if(model!=null)return;
        EntityType<AuditEvent> candidate=em.getMetamodel().entity(AuditEvent.class);
        for(String attribute:List.of("id","actor","action","entityType","entityId","details"))
            candidate.getAttribute(attribute);

        List<SingularAttribute<? super AuditEvent,?>> temporal=new ArrayList<>();
        for(var attribute:candidate.getSingularAttributes()) {
            Class<?> type=attribute.getJavaType();
            if(type==OffsetDateTime.class||type==Instant.class||
               type==LocalDateTime.class||type==ZonedDateTime.class||
               java.util.Date.class.isAssignableFrom(type)) {
                temporal.add(attribute);
            }
        }

        SingularAttribute<? super AuditEvent,?> selected=null;
        for(String preferred:List.of("timestamp","occurredAt","createdAt")) {
            for(var attribute:temporal)
                if(attribute.getName().equals(preferred)){selected=attribute;break;}
            if(selected!=null)break;
        }
        if(selected==null&&temporal.size()==1)selected=temporal.get(0);
        if(selected==null)
            throw new IllegalStateException("No se pudo identificar la fecha persistente de AuditEvent.");

        entityName=candidate.getName();
        timeAttribute=selected.getName();
        timeType=selected.getJavaType();
        ZoneId.of(zoneName);
        model=candidate;
    }

    private String base() {
        return " from "+entityName+" a "+
            " left join OffboardingTask t on "+
            " (a.entityType='OffboardingTask' and t.id=a.entityId) "+
            " left join OffboardingCase c on "+
            " ((a.entityType='OffboardingCase' and c.id=a.entityId) "+
            " or (a.entityType='OffboardingTask' and c.id=t.offboardingCase.id)) ";
    }

    private String select() {
        return "select a,c.caseNumber,c.employeeIdentifier,c.employeeName,"+
            "c.computerDetails,c.phoneDetails,t.systemName,t.taskName";
    }

    private static String safeLike(String value) {
        return value.replace("!","!!").replace("%","!%").replace("_","!_");
    }

    private Filter validate(Filter filter) {
        String q=Objects.toString(filter.q(),"").strip();
        if(q.length()>256)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Search text is limited to 256 characters.");
        if(filter.from()!=null&&filter.to()!=null&&filter.from().isAfter(filter.to()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Start date must not be after end date.");
        return new Filter(filter.from(),filter.to(),q,
            filter.field()==null?SearchField.ALL:filter.field());
    }

    private Object boundary(LocalDate day) {
        ZonedDateTime value=day.atStartOfDay(ZoneId.of(zoneName));
        if(timeType==OffsetDateTime.class)return value.toOffsetDateTime();
        if(timeType==Instant.class)return value.toInstant();
        if(timeType==ZonedDateTime.class)return value;
        if(timeType==LocalDateTime.class)return day.atStartOfDay();
        if(timeType==java.sql.Timestamp.class)
            return java.sql.Timestamp.from(value.toInstant());
        if(timeType==java.sql.Date.class)return java.sql.Date.valueOf(day);
        return java.util.Date.from(value.toInstant());
    }

    private SqlPart build(Filter filter,long snapshot) {
        StringBuilder where=new StringBuilder(base()+" where a.id<=:snapshot ");
        Map<String,Object> values=new LinkedHashMap<>();
        values.put("snapshot",snapshot);

        if(filter.from()!=null) {
            where.append(" and a.").append(timeAttribute).append(">=:fromDate ");
            values.put("fromDate",boundary(filter.from()));
        }
        if(filter.to()!=null) {
            where.append(" and a.").append(timeAttribute).append("<:untilDate ");
            values.put("untilDate",boundary(filter.to().plusDays(1)));
        }

        if(!filter.q().isBlank()) {
            String q=filter.q().toLowerCase(Locale.ROOT);

            if(filter.field()==SearchField.ASSET) {
                // Referencias capturadas en el caso/evento. No inferir por propietario actual.
                where.append("""
                    and (
                        lower(coalesce(c.computerDetails,'')) like :asset escape '!'
                        or lower(coalesce(c.phoneDetails,'')) like :asset escape '!'
                        or lower(coalesce(a.details,'')) like :asset escape '!'
                    )
                    """);
                values.put("asset","%asset id: "+safeLike(q)+"%");
            } else {
                String expression=switch(filter.field()) {
                    case EMPLOYEE -> "lower(coalesce(c.employeeIdentifier,''))";
                    case NAME -> "lower(coalesce(c.employeeName,''))";
                    default -> """
                        lower(concat(
                            coalesce(a.actor,''),' ',coalesce(a.action,''),' ',
                            coalesce(a.details,''),' ',coalesce(c.caseNumber,''),' ',
                            coalesce(c.employeeIdentifier,''),' ',coalesce(c.employeeName,''),' ',
                            coalesce(c.computerDetails,''),' ',coalesce(c.phoneDetails,''),' ',
                            coalesce(t.systemName,''),' ',coalesce(t.taskName,'')
                        ))
                        """;
                };

                String[] tokens=q.split("\\s+");
                if(tokens.length>12)
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Use at most 12 search words.");

                for(int i=0;i<tokens.length;i++) {
                    where.append(" and ").append(expression)
                        .append(" like :word").append(i).append(" escape '!' ");
                    values.put("word"+i,"%"+safeLike(tokens[i])+"%");
                }
            }
        }
        return new SqlPart(where.toString(),values);
    }

    private <T> TypedQuery<T> bind(TypedQuery<T> query,Map<String,Object> values) {
        values.forEach(query::setParameter);
        return query;
    }

    private long snapshot(Long requested) {
        Number maximum=(Number)em.createQuery(
            "select coalesce(max(a.id),0) from "+entityName+" a").getSingleResult();
        long current=maximum.longValue();
        if(requested!=null&&requested<0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid report snapshot.");
        return requested==null?current:Math.min(current,requested);
    }

    private Object attribute(AuditEvent event,String name) {
        try {
            Member member=model.getAttribute(name).getJavaMember();
            if(member instanceof Field field) {
                field.setAccessible(true);
                return field.get(event);
            }
            if(member instanceof Method method) {
                method.setAccessible(true);
                return method.invoke(event);
            }
            throw new IllegalStateException("Unsupported persistent member.");
        } catch(ReflectiveOperationException ex) {
            throw new IllegalStateException("Unable to read audit event.",ex);
        }
    }

    private static String text(Object value) {
        return Objects.toString(value,"");
    }

    private String timestamp(Object value) {
        ZoneId zone=ZoneId.of(zoneName);
        if(value instanceof OffsetDateTime v)return v.toInstant().atZone(zone).toOffsetDateTime().toString();
        if(value instanceof Instant v)return v.atZone(zone).toOffsetDateTime().toString();
        if(value instanceof ZonedDateTime v)return v.withZoneSameInstant(zone).toOffsetDateTime().toString();
        if(value instanceof LocalDateTime v)return v.atZone(zone).toOffsetDateTime().toString();
        if(value instanceof java.sql.Date v)return v.toLocalDate().atStartOfDay(zone).toOffsetDateTime().toString();
        if(value instanceof java.util.Date v)return v.toInstant().atZone(zone).toOffsetDateTime().toString();
        return text(value);
    }

    public static String assets(String... references) {
        Set<String> result=new LinkedHashSet<>();
        for(String reference:references) {
            Matcher matcher=ASSET_PATTERN.matcher(Objects.toString(reference,""));
            while(matcher.find())result.add(matcher.group(1).strip());
        }
        return String.join(" | ",result);
    }

    public static String actionLabel(String code) {
        return switch(Objects.toString(code,"")) {
            case "ASSET_CATALOG_UPDATED" -> "Inventory updated";
            case "CASE_CREATED" -> "Offboarding registered";
            case "CASE_STATUS_CHANGED" -> "Case status updated";
            case "TASK_COMPLETED" -> "Task completed";
            case "TASK_VALIDATED" -> "Task validated";
            case "TASK_REOPENED" -> "Task reopened";
            case "BULK_CASE_LINKED" -> "Offboarding registered through Bulk";
            case "USER_CREATED" -> "User created";
            case "USER_UPDATED" -> "User updated";
            case "PASSWORD_RESET","USER_PASSWORD_RESET" -> "Password reset";
            default -> Objects.toString(code,"Event").replace('_',' ');
        };
    }

    private EventRow row(Object[] data) {
        AuditEvent event=(AuditEvent)data[0];
        String code=text(attribute(event,"action"));
        String details=text(attribute(event,"details"));

        return new EventRow(
            text(attribute(event,"id")),timestamp(attribute(event,timeAttribute)),
            text(attribute(event,"actor")),code,actionLabel(code),
            text(attribute(event,"entityType")),text(attribute(event,"entityId")),
            text(data[1]),text(data[2]),text(data[3]),
            assets(text(data[4]),text(data[5]),details),
            text(data[6]),text(data[7]),details
        );
    }

    private List<EventRow> rows(SqlPart part,int offset,int limit) {
        return bind(em.createQuery(
            select()+part.text()+" order by a.id desc",Object[].class),part.parameters())
            .setFirstResult(offset).setMaxResults(limit).getResultList()
            .stream().map(this::row).toList();
    }

    public Result search(Filter original,Long requestedSnapshot,int offset) {
        Filter filter=validate(original);
        if(offset<0||offset>10000000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid page.");

        return tx.execute(status->{
            initialize();
            long snapshot=snapshot(requestedSnapshot);
            SqlPart part=build(filter,snapshot);
            Object[] counts=bind(em.createQuery(
                "select count(a),count(distinct c.id),count(distinct a.actor)"+
                    part.text(),Object[].class),part.parameters()).getSingleResult();

            return new Result(
                rows(part,offset,PAGE_SIZE),
                ((Number)counts[0]).longValue(),
                ((Number)counts[1]).longValue(),
                ((Number)counts[2]).longValue(),
                offset,PAGE_SIZE,Long.toString(snapshot),zoneName
            );
        });
    }

    public byte[] export(Filter original,Long requestedSnapshot) throws Exception {
        if(!exportSlot.tryAcquire())
            throw new ResponseStatusException(
                HttpStatus.TOO_MANY_REQUESTS,"Another export is running. Try again shortly.");

        try {
            Filter filter=validate(original);
            Result first=search(filter,requestedSnapshot,0);
            if(first.total()>EXPORT_LIMIT)
                throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "More than 20000 matching events. Narrow the dates or search.");

            long snapshot=Long.parseLong(first.snapshot());
            return AuditXlsx.generate(
                filter,first.zone(),first.total(),first.snapshot(),
                offset->tx.execute(status->rows(build(filter,snapshot),offset,500)));
        } finally {
            exportSlot.release();
        }
    }
}
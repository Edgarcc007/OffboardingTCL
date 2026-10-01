package com.empresa.offboarding.reporting;

import jakarta.persistence.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.*;
import java.time.temporal.*;
import java.util.*;

@Service
@Order(Ordered.HIGHEST_PRECEDENCE+250)
public class OffboardingDashboard implements ApplicationRunner {
    public enum Period { DAY,WEEK,MONTH,YEAR }
    public enum Dimension { DEPARTMENT,AREA }

    public record Point(
        String start,String label,long count,boolean partial
    ) {}

    public record Group(String name,long count) {}

    public record Trend(
        String previous,String current,long delta,Double percent,
        String direction,boolean partial
    ) {}

    public record Report(
        String from,String to,String zone,String generatedAt,
        Period period,Dimension dimension,long total,
        List<Point> series,List<Group> groups,List<Group> statuses,
        List<String> departments,List<String> areas,Trend trend
    ) {}

    @PersistenceContext private EntityManager em;
    private final TransactionTemplate tx;

    @Value("${app.audit-report.zone:America/Los_Angeles}")
    private String zoneName;

    public OffboardingDashboard(PlatformTransactionManager manager) {
        tx=new TransactionTemplate(manager);
        tx.setReadOnly(true);
        tx.setTimeout(45);
    }

    @Override
    public void run(ApplicationArguments args) {
        ZoneId.of(zoneName);
        tx.execute(status->{
            Object type=em.createNativeQuery("""
                SELECT format_type(a.atttypid,NULL)
                FROM pg_attribute a
                WHERE a.attrelid=CAST('offboarding_case' AS regclass)
                  AND a.attname='created_at' AND NOT a.attisdropped
                """).getSingleResult();

            if(!"timestamp with time zone".equals(type.toString()))
                throw new IllegalStateException(
                    "Review offboarding_case.created_at timezone mapping before enabling the dashboard.");
            return null;
        });
    }

    public static LocalDate bucket(LocalDate day,Period period) {
        return switch(period) {
            case DAY -> day;
            case WEEK -> day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTH -> day.withDayOfMonth(1);
            case YEAR -> day.withDayOfYear(1);
        };
    }

    public static LocalDate end(LocalDate start,Period period) {
        return switch(period) {
            case DAY -> start.plusDays(1);
            case WEEK -> start.plusWeeks(1);
            case MONTH -> start.plusMonths(1);
            case YEAR -> start.plusYears(1);
        };
    }

    public static String label(LocalDate start,Period period) {
        return switch(period) {
            case DAY -> start.toString();
            case WEEK -> String.format(Locale.ROOT,"%d-W%02d",
                start.get(WeekFields.ISO.weekBasedYear()),
                start.get(WeekFields.ISO.weekOfWeekBasedYear()));
            case MONTH -> start.toString().substring(0,7);
            case YEAR -> Integer.toString(start.getYear());
        };
    }

    public static Trend trend(List<Point> points) {
        // UX2_COMPLETE_PERIOD_COMPARISON
        // FIX_DASHBOARD_NULL_PERCENT
        List<Point> complete=points.stream().filter(p->!p.partial()).toList();
        if(complete.size()<2)
            return new Trend("","",0,null,"NONE",true);

        Point previous=complete.get(complete.size()-2);
        Point current=complete.get(complete.size()-1);
        long delta=current.count()-previous.count();
        Double percent=null;
        if(previous.count()>0)
            percent=Math.round(delta*1000.0/previous.count())/10.0;

        return new Trend(previous.label(),current.label(),delta,percent,
            delta>0?"UP":delta<0?"DOWN":"FLAT",false);
    }


    private Query query(String sql,Map<String,Object> parameters) {
        Query query=em.createNativeQuery(sql);
        parameters.forEach(query::setParameter);
        return query;
    }

    private String clean(String value,int max) {
        String result=Objects.toString(value,"").strip();
        if(result.length()>max)
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,"Filter text is too long.");
        return result;
    }

    public Report report(
        LocalDate requestedFrom,LocalDate requestedTo,
        Period period,Dimension dimension,String department,String area
    ) {
        ZoneId zone=ZoneId.of(zoneName);
        Instant generated=Instant.now();
        LocalDate today=generated.atZone(zone).toLocalDate();
        /* UX2_EXPLICIT_DATES */
        validateRange(requestedFrom,requestedTo,today);
        LocalDate from=requestedFrom;
        LocalDate to=requestedTo;

        if(from.isAfter(to)||to.isAfter(today)||
           ChronoUnit.DAYS.between(from,to)>3660)
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Use a valid date range ending no later than today, up to ten years.");

        String departmentFilter=clean(department,100);
        String areaFilter=clean(area,150);

        return tx.execute(status->{
            String dep="COALESCE(NULLIF(btrim(c.department),''),'Sin departamento')";
            String workArea="COALESCE(NULLIF(btrim(c.work_area),''),'Sin area')";

            StringBuilder where=new StringBuilder("""
                FROM offboarding_case c
                WHERE c.created_at>=CAST(:start AS timestamptz)
                  AND c.created_at<CAST(:finish AS timestamptz)
                """);

            Map<String,Object> parameters=new LinkedHashMap<>();
            parameters.put("start",from.atStartOfDay(zone).toOffsetDateTime().toString());
            parameters.put("finish",to.plusDays(1).atStartOfDay(zone).toOffsetDateTime().toString());

            if(!departmentFilter.isBlank()) {
                where.append(" AND ").append(dep).append("=:department ");
                parameters.put("department",departmentFilter);
            }
            if(!areaFilter.isBlank()) {
                where.append(" AND ").append(workArea).append("=:area ");
                parameters.put("area",areaFilter);
            }

            Map<String,Object> dateParameters=new LinkedHashMap<>(parameters);
            dateParameters.put("zone",zoneName);

            List<?> daily=query("""
                SELECT to_char(c.created_at AT TIME ZONE :zone,'YYYY-MM-DD'),
                       COUNT(*)
                """+where+"""
                GROUP BY 1 ORDER BY 1
                """,dateParameters).getResultList();

            Map<LocalDate,Long> counts=new HashMap<>();
            long total=0;
            for(Object value:daily) {
                Object[] row=(Object[])value;
                long count=((Number)row[1]).longValue();
                counts.put(LocalDate.parse(row[0].toString()),count);
                total+=count;
            }

            Map<LocalDate,Long> grouped=new TreeMap<>();
            for(LocalDate day=from;!day.isAfter(to);day=day.plusDays(1))
                grouped.merge(bucket(day,period),counts.getOrDefault(day,0L),Long::sum);

            List<Point> points=new ArrayList<>();
            for(var entry:grouped.entrySet()) {
                LocalDate start=entry.getKey();
                LocalDate until=end(start,period);
                boolean partial=start.isBefore(from)||until.isAfter(to.plusDays(1))||
                    until.atStartOfDay(zone).toInstant().isAfter(generated);
                points.add(new Point(
                    start.toString(),label(start,period),entry.getValue(),partial));
            }

            String groupExpression=dimension==Dimension.AREA?workArea:dep;
            List<Group> groups=groups(
                "SELECT "+groupExpression+",COUNT(*) "+where+
                " GROUP BY 1 ORDER BY 2 DESC,1",parameters);

            List<Group> statuses=groups(
                "SELECT c.status,COUNT(*) "+where+
                " GROUP BY 1 ORDER BY 1",parameters);

            List<String> departments=options(dep);
            List<String> areas=options(workArea);

            return new Report(
                from.toString(),to.toString(),zoneName,generated.toString(),
                period,dimension,total,List.copyOf(points),groups,statuses,
                departments,areas,trend(points));
        });
    }

    private List<Group> groups(String sql,Map<String,Object> parameters) {
        List<Group> result=new ArrayList<>();
        for(Object value:query(sql,parameters).getResultList()) {
            Object[] row=(Object[])value;
            result.add(new Group(
                Objects.toString(row[0],"Sin dato"),((Number)row[1]).longValue()));
        }
        return result;
    }

    private List<String> options(String expression) {
        List<String> result=new ArrayList<>();
        for(Object value:em.createNativeQuery(
            "SELECT DISTINCT "+expression+
            " FROM offboarding_case c ORDER BY 1").getResultList())
            result.add(value.toString());
        return result;
    }

    /* UX2_FILTER_OPTIONS */
    public static void validateRange(LocalDate from,LocalDate to,LocalDate today) {
        if(from==null||to==null)
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,"Select both From and To dates.");
        if(from.isAfter(to))
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,"From cannot be later than To.");
        if(to.isAfter(today))
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,"The final date cannot be in the future.");
        if(ChronoUnit.DAYS.between(from,to)>3660)
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,"Use a range of up to ten years.");
    }

    public Map<String,Object> filterOptions() {
        ZoneId zone=ZoneId.of(zoneName);
        return tx.execute(status->Map.<String,Object>of(
            "zone",zoneName,
            "today",LocalDate.now(zone).toString(),
            "departments",options(
                "COALESCE(NULLIF(btrim(c.department),''),'Sin departamento')"),
            "areas",options(
                "COALESCE(NULLIF(btrim(c.work_area),''),'Sin area')")
        ));
    }
}
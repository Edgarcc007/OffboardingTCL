package com.empresa.offboarding.bulk;

import jakarta.persistence.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@RestController
@RequestMapping("/api/bulk/session-records")
public class SessionRecordCheck {
    @PersistenceContext private EntityManager em;
    private final BulkWorkflow workflow;
    public SessionRecordCheck(BulkWorkflow workflow){this.workflow=workflow;}

    @GetMapping
    public ResponseEntity<List<String>> check(
        @RequestParam(defaultValue="") String numbers,Authentication auth
    ) {
        var owner=workflow.actor(auth);
        if(numbers.length()>4000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Too many session records.");
        List<String> requested=Arrays.stream(numbers.split(",")).filter(
            v->v.matches("OB-[0-9]{4}-[0-9]{2}-[0-9]{2}-[0-9]{6}"))
            .distinct().toList();
        if(requested.size()>100)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Too many session records.");

        List<String> result=new ArrayList<>();
        if(!requested.isEmpty()){
            for(Object value:em.createNativeQuery("""
                SELECT c.case_number FROM offboarding_case c JOIN app_user u ON u.id=:owner
                WHERE c.case_number IN (:numbers) AND c.requested_by=u.username
                  AND c.created_at>=u.created_at
                """).setParameter("owner",owner.id())
                .setParameter("numbers",requested).getResultList())
                result.add(value.toString());
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result);
    }
}
package com.empresa.offboarding.bulk;

import com.empresa.offboarding.enums.AppRole;
import com.empresa.offboarding.repository.AppUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import java.util.Set;

@Component
public class R7Access {
    private final BulkWorkflow workflow;
    private final AppUserRepository users;

    public R7Access(BulkWorkflow workflow,AppUserRepository users) {
        this.workflow=workflow;this.users=users;
    }

    public BulkWorkflow.Actor assets(Authentication auth) {
        if(!workflow.isReady())throw error(503,"Application is still starting.");
        if(auth==null||!auth.isAuthenticated()||
            !(auth.getPrincipal() instanceof BulkPrincipal principal)||
            principal.getUserId()==null)
            throw error(401,"Sign out and sign in again.");

        boolean granted=auth.getAuthorities().stream().anyMatch(a->
            Set.of("ROLE_ADMIN","ROLE_RECURSOS_HUMANOS","ROLE_IT_ENGINEER_VALIDATOR")
                .contains(a.getAuthority()));
        if(!granted)throw error(403,"Inventory access is not allowed.");

        var user=users.findById(principal.getUserId())
            .orElseThrow(()->error(401,"Account is unavailable."));
        if(!user.isEnabled()||user.getRoles().stream().noneMatch(
            Set.of(AppRole.ADMIN,AppRole.RECURSOS_HUMANOS,
                AppRole.IT_ENGINEER_VALIDATOR)::contains))
            throw error(403,"The account no longer has inventory access.");
        if(!user.getUsername().equals(principal.getUsername()))
            throw error(401,"The account changed. Sign in again.");
        return new BulkWorkflow.Actor(user.getId(),user.getUsername());
    }

    private static ResponseStatusException error(int code,String text) {
        return new ResponseStatusException(HttpStatus.valueOf(code),text);
    }
}
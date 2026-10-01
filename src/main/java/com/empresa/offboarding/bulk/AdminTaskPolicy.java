package com.empresa.offboarding.bulk;

import com.empresa.offboarding.enums.AppRole;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import java.util.stream.Collectors;

public final class AdminTaskPolicy {
    private AdminTaskPolicy() {}

    public record Actor(long id,String username,Set<AppRole> roles) {
        public boolean admin(){return roles.contains(AppRole.ADMIN);}
    }

    public static BulkPrincipal principal(Authentication authentication) {
        if(authentication==null||!authentication.isAuthenticated()||
            !(authentication.getPrincipal() instanceof BulkPrincipal principal)||
            principal.getUserId()==null||principal.getUserId()<=0)
            throw error(401,"Sign out and sign in again.");
        return principal;
    }

    public static Actor verify(
        Authentication authentication,String username,
        Set<AppRole> currentRoles,Set<AppRole> required
    ) {
        BulkPrincipal principal=principal(authentication);
        if(!principal.getUsername().equals(username))
            throw error(401,"The account changed. Sign in again.");

        Set<String> expected=currentRoles.stream()
            .flatMap(role->R7RolePolicy.authorities(role).stream())
            .map(a->a.getAuthority()).collect(Collectors.toSet());
        Set<String> granted=authentication.getAuthorities().stream()
            .map(a->a.getAuthority()).collect(Collectors.toSet());

        if(!expected.equals(granted))
            throw error(401,"Your profiles changed. Sign out and sign in again.");
        if(Collections.disjoint(currentRoles,required))
            throw error(403,"Your profile cannot perform this action.");

        return new Actor(principal.getUserId(),username,Set.copyOf(currentRoles));
    }

    public static boolean canCombine(String status) {
        return "PENDIENTE".equals(status)||"EN_PROCESO".equals(status);
    }

    public static ResponseStatusException error(int status,String message) {
        return new ResponseStatusException(HttpStatus.valueOf(status),message);
    }
}
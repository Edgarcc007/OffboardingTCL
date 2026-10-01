package com.empresa.offboarding.bulk;

import com.empresa.offboarding.enums.AppRole;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.*;

public final class R7RolePolicy {
    private R7RolePolicy() {}

    public static List<SimpleGrantedAuthority> authorities(AppRole role) {
        if(role==AppRole.IT_ENGINEER_VALIDATOR)
            return List.of(
                new SimpleGrantedAuthority("ROLE_IT_ENGINEER_VALIDATOR"),
                new SimpleGrantedAuthority("ROLE_IT_ENGINEER"));
        return List.of(new SimpleGrantedAuthority("ROLE_"+role.name()));
    }
}
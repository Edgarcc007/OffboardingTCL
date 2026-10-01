package com.empresa.offboarding.bulk;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;
import java.util.Collection;

public class BulkPrincipal extends User {
    private static final long serialVersionUID = 1L;
    private final Long userId;

    public BulkPrincipal(
        Long userId, String username, String password,
        boolean enabled, Collection<? extends GrantedAuthority> authorities
    ) {
        super(username, password, enabled, true, true, true, authorities);
        this.userId = userId;
    }

    public Long getUserId() {
        return userId;
    }
}
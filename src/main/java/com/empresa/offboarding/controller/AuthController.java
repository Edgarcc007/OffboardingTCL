package com.empresa.offboarding.controller;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @GetMapping("/me")
    public Map<String, Object> currentUser(
            Authentication authentication
    ) {
        List<String> roles =
                authentication.getAuthorities()
                        .stream()
                        .map(
                            GrantedAuthority::getAuthority)
                        .map(role ->
                            role.replaceFirst(
                                "^ROLE_",
                                ""
                            )
                        )
                        .toList();

        Map<String, Object> response =
                new LinkedHashMap<>();

        response.put(
                "username",
                authentication.getName()
        );

        response.put("roles", roles);

        return response;
    }
}

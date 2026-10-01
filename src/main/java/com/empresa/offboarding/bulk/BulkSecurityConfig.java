package com.empresa.offboarding.bulk;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

@Configuration
public class BulkSecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain bulkSecurityFilterChain(HttpSecurity http)
        throws Exception {

        http
            .securityMatcher("/api/bulk/**")
            .csrf(Customizer.withDefaults())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .authorizeHttpRequests(requests ->
                requests.requestMatchers(org.springframework.http.HttpMethod.GET, "/api/bulk/health").permitAll()
                    // R7_SCOPED_ROUTES
                    .requestMatchers("/api/bulk/admin-tasks/**").hasRole("ADMIN")
                    .requestMatchers("/api/bulk/admin-maintenance/**").hasRole("ADMIN")
                    .requestMatchers("/api/bulk/assets/**")
                    .hasAnyRole("ADMIN","RECURSOS_HUMANOS","IT_ENGINEER_VALIDATOR")
                    .anyRequest()
                    .hasAnyRole("ADMIN", "RECURSOS_HUMANOS"))
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint(
                    new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable);

        return http.build();
    }
}
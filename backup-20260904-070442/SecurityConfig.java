package com.empresa.offboarding.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/error").permitAll()

                        .requestMatchers("/api/users/**")
                            .hasRole("ADMIN")

                        .requestMatchers(HttpMethod.POST, "/api/offboardings")
                            .hasAnyRole("ADMIN", "RECURSOS_HUMANOS")

                        .requestMatchers(HttpMethod.GET, "/api/offboardings/**")
                            .authenticated()

                        .requestMatchers(HttpMethod.PATCH, "/api/tasks/*/complete")
                            .hasAnyRole("ADMIN", "EJECUTOR", "RECURSOS_HUMANOS", "SEGURIDAD")

                        .requestMatchers(HttpMethod.PATCH, "/api/tasks/*/validate")
                            .hasAnyRole("ADMIN", "SEGURIDAD")

                        .requestMatchers("/api/audit/**")
                            .hasAnyRole("ADMIN", "AUDITOR")

                        .anyRequest().authenticated()
                )
                .httpBasic(Customizer.withDefaults())
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
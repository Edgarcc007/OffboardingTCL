package com.empresa.offboarding.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                .csrf(AbstractHttpConfigurer::disable)

                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))

                .authorizeHttpRequests(authorize -> authorize

                        .requestMatchers(
                                "/login",
                                "/login.html",
                                "/error",
                                "/favicon.ico"
                        ).permitAll()

                        /*
                         * Administracion de usuarios locales.
                         */
                        .requestMatchers(
                                "/api/notification-recipients",
                                "/api/notification-recipients/**"
                        ).hasRole("ADMIN")

                        .requestMatchers("/api/users/**")
                            .hasRole("ADMIN")

                        /*
                         * Recursos Humanos solamente puede registrar bajas.
                         */
                        .requestMatchers(HttpMethod.POST, "/api/offboardings")
                            .hasAnyRole("ADMIN", "RECURSOS_HUMANOS")

                        /*
                         * Consulta de casos.
                         */
                        .requestMatchers(HttpMethod.GET, "/api/offboardings/**")
                            .hasAnyRole("ADMIN", "IT_ENGINEER", "AUDITOR", "CONTROL_ACCESOS")

                        /*
                         * Procesamiento de tareas.
                         */
                        .requestMatchers(HttpMethod.PATCH, "/api/tasks/*/complete")
                            .hasAnyRole("ADMIN", "IT_ENGINEER", "CONTROL_ACCESOS")

                        /*
                         * Validacion administrativa de activos.
                         */
                        .requestMatchers(HttpMethod.PATCH, "/api/tasks/*/validate")
                            .hasAnyRole("ADMIN", "CONTROL_ACCESOS")

                        /*
                         * Reabrir tarea procesada.
                         */
                        .requestMatchers(HttpMethod.PATCH, "/api/tasks/*/reopen")
                            .hasRole("ADMIN")

                        /*
                         * Bloquear cualquier otra operacion sobre tareas.
                         */
                        .requestMatchers("/api/tasks/**")
                            .denyAll()

                        /*
                         * Bitacora de lectura.
                         */
                        .requestMatchers(HttpMethod.GET, "/api/audit/**")
                            .hasAnyRole("ADMIN", "AUDITOR")

                        .requestMatchers("/api/audit/**")
                            .denyAll()

                        /*
                         * Notificaciones por correo.
                         */
                        .requestMatchers(HttpMethod.GET, "/api/notifications/test-smtp", "/api/notifications/test-flow")
                            .hasRole("ADMIN")

                        .requestMatchers(HttpMethod.POST, "/api/notifications/send")
                            .hasAnyRole("ADMIN", "IT_ENGINEER")

                        /*
                         * Informacion del usuario autenticado.
                         */
                        .requestMatchers("/api/auth/**")
                            .authenticated()

                        .anyRequest()
                            .authenticated()
                )

                .exceptionHandling(exception -> exception
                        .defaultAuthenticationEntryPointFor(
                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                                new AntPathRequestMatcher("/api/**")
                        )
                )

                .formLogin(form -> form
                        .loginPage("/login.html")
                        .loginProcessingUrl("/login")
                        .defaultSuccessUrl("/", true)
                        .failureUrl("/login.html?error")
                        .permitAll()
                )

                .logout(logout -> logout
                        .logoutRequestMatcher(
                                new AntPathRequestMatcher("/logout", "POST")
                        )
                        .logoutSuccessUrl("/login.html?logout")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID")
                        .permitAll()
                )

                .httpBasic(AbstractHttpConfigurer::disable);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
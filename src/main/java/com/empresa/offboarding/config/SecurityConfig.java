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
                         * Administración de usuarios locales.
                         */
                        .requestMatchers("/api/users/**")
                            .hasRole("ADMIN")

                        /*
                         * Recursos Humanos solamente puede registrar bajas.
                         */
                        .requestMatchers(HttpMethod.POST, "/api/offboardings")
                            .hasAnyRole("ADMIN", "RECURSOS_HUMANOS")

                        /*
                         * Consulta de casos:
                         * - Administrador
                         * - IT Engineer
                         * - Auditor de solo lectura
                         * - Control de Accesos (biométricos y accesos físicos)
                         */
                        .requestMatchers(HttpMethod.GET, "/api/offboardings/**")
                            .hasAnyRole("ADMIN", "IT_ENGINEER", "AUDITOR", "CONTROL_ACCESOS")

                        /*
                         * Procesamiento de tareas.
                         * Control de Accesos puede completar tareas de biométrico.
                         */
                        .requestMatchers(HttpMethod.PATCH, "/api/tasks/*/complete")
                            .hasAnyRole("ADMIN", "IT_ENGINEER", "CONTROL_ACCESOS")

                        /*
                         * Validación administrativa de activos.
                         * Control de Accesos puede validar tareas de biométrico.
                         */
                        .requestMatchers(HttpMethod.PATCH, "/api/tasks/*/validate")
                            .hasAnyRole("ADMIN", "CONTROL_ACCESOS")

                        /*
                         * Solamente un administrador puede reabrir
                         * una tarea que ya fue procesada.
                         */
                        .requestMatchers(HttpMethod.PATCH, "/api/tasks/*/reopen")
                            .hasRole("ADMIN")

                        /*
                         * Bloquear cualquier otra operación sobre tareas.
                         */
                        .requestMatchers("/api/tasks/**")
                            .denyAll()

                        /*
                         * Bitácora estrictamente de lectura.
                         */
                        .requestMatchers(HttpMethod.GET, "/api/audit/**")
                            .hasAnyRole("ADMIN", "AUDITOR")

                        .requestMatchers("/api/audit/**")
                            .denyAll()

                        /*
                         * Información del usuario autenticado.
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

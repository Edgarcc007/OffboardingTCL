package com.empresa.offboarding.config;

import com.empresa.offboarding.entity.AppUser;
import com.empresa.offboarding.enums.AppRole;
import com.empresa.offboarding.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.EnumSet;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrap implements CommandLineRunner {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.security.admin-username:admin}")
    private String adminUsername;

    @Value("${app.security.admin-password:admin123}")
    private String adminPassword;

    @Value("${app.security.admin-fullname:Administrator}")
    private String adminFullName;

    @Value("${app.security.admin-email:admin@localhost}")
    private String adminEmail;

    @Override
    public void run(String... args) {
        if (adminUsername == null || adminUsername.isBlank()) {
            log.warn("No se configuró un usuario administrador válido. Se omite la creación.");
            return;
        }

        if (appUserRepository.existsByUsername(adminUsername)) {
            return;
        }

        AppUser admin = new AppUser();
        admin.setUsername(adminUsername.trim());
        admin.setFullName(adminFullName);
        admin.setEmail(adminEmail);
        admin.setPasswordHash(passwordEncoder.encode(adminPassword));
        admin.setEnabled(true);
        admin.setRoles(EnumSet.of(AppRole.ADMIN));

        appUserRepository.save(admin);

        log.warn("Usuario administrador '{}' creado. Cambia la contrasena inicial.",
                adminUsername);
    }
}
package com.empresa.offboarding.controller;

import com.empresa.offboarding.service.EmailService;
import com.empresa.offboarding.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Slf4j
public class NotificationController {

    private final NotificationService notificationService;
    private final EmailService emailService;

    @PostMapping("/send")
    public ResponseEntity<Map<String, String>> sendNotification(@RequestBody Map<String, Object> payload) {

        String subject = (String) payload.getOrDefault("subject", "Offboarding - Notificacion");
        String to = (String) payload.get("to");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> data = (List<Map<String, Object>>) payload.get("data");

        if (data == null || data.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "El campo 'data' es requerido y no puede estar vacio"
            ));
        }

        notificationService.sendOffboardingNotification(data, subject, to);

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Notificacion enviada con " + data.size() + " registros"
        ));
    }

    @GetMapping("/test-smtp")
    public ResponseEntity<Map<String, String>> testSmtp() {
        try {
            String htmlBody = "<div style=\"font-family: Segoe UI, Arial; padding: 20px;\">"
                    + "<h2 style=\"color: #1a3c6e;\">Prueba SMTP Exitosa</h2>"
                    + "<p>Este correo confirma que la conexion SMTP desde <strong>OffboardingTCL</strong> "
                    + "hacia <code>mail.tcl.com:587</code> funciona correctamente.</p>"
                    + "<p style=\"color: #888; font-size: 12px;\">Generado automaticamente - No responder</p>"
                    + "</div>";

            emailService.sendHtmlEmail("ecarrasco@tcl.com", "OffboardingTCL - Test SMTP", htmlBody);

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Correo de prueba enviado a ecarrasco@tcl.com"
            ));
        } catch (Exception e) {
            log.error("Error en test SMTP: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "message", e.getMessage()
            ));
        }
    }

    @GetMapping("/test-flow")
    public ResponseEntity<Map<String, String>> testFlow() {
        try {
            List<Map<String, Object>> testData = List.of(
                    Map.of("Empleado", "USUARIO PRUEBA", "NumEmpleado", "00000",
                            "Departamento", "IT", "FechaBaja", "2026-09-15",
                            "Motivo", "Test del sistema"),
                    Map.of("Empleado", "SEGUNDO PRUEBA", "NumEmpleado", "00001",
                            "Departamento", "Produccion", "FechaBaja", "2026-09-15",
                            "Motivo", "Test del sistema")
            );

            notificationService.sendOffboardingNotification(testData, "OffboardingTCL - Test Flow + Email");

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Test completo ejecutado (Flow + Email) con 2 registros de prueba"
            ));
        } catch (Exception e) {
            log.error("Error en test Flow: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "message", e.getMessage()
            ));
        }
    }
}
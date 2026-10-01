package com.empresa.offboarding.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final FlowService flowService;
    private final EmailService emailService;

    @Value("${notification.default-to}")
    private String defaultRecipient;

    public void sendOffboardingNotification(
            List<Map<String, Object>> data,
            String subject,
            String recipient
    ) {
        if (data == null || data.isEmpty()) {
            log.info("No hay datos para notificar");
            return;
        }

        try {
            // Intentar generar tabla via Flow, si falla usar generador local
            String htmlTable = null;

            try {
                htmlTable = flowService.getHtmlTableFromFlow(data);
            } catch (Exception flowEx) {
                log.warn("Flow no disponible, usando generador local: {}",
                        flowEx.getMessage());
            }

            if (htmlTable == null || htmlTable.isBlank()) {
                log.info("Generando tabla HTML localmente ({} registros)", data.size());
                htmlTable = buildHtmlTableLocally(data);
            }

            String fecha = LocalDate.now()
                    .format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));

            String to = (recipient != null && !recipient.isBlank())
                    ? recipient
                    : defaultRecipient;

            String emailSubject = (subject != null && !subject.isBlank())
                    ? subject
                    : "Offboarding - Notificacion " + fecha;

            String htmlBody = buildEmailBody(
                    htmlTable, emailSubject, fecha, data.size());

            emailService.sendHtmlEmail(to, emailSubject, htmlBody);

            log.info("Notificacion enviada: {} registros a {}", data.size(), to);

        } catch (Exception e) {
            log.error("Error en el proceso de notificacion: {}",
                    e.getMessage(), e);
        }
    }

    public void sendOffboardingNotification(
            List<Map<String, Object>> data,
            String subject
    ) {
        sendOffboardingNotification(data, subject, null);
    }

    /**
     * Genera una tabla HTML a partir de los datos sin depender
     * de ningun servicio externo.
     */
    private String buildHtmlTableLocally(
            List<Map<String, Object>> data
    ) {
        if (data.isEmpty()) {
            return "<p>Sin datos</p>";
        }

        // Obtener columnas del primer registro
        List<String> columns = data.get(0).keySet()
                .stream().toList();

        StringBuilder sb = new StringBuilder();
        sb.append("<table style=\"width:100%;border-collapse:collapse;font-size:13px;\">\n");

        // Header
        sb.append("<thead><tr>");
        for (String col : columns) {
            sb.append("<th style=\"background:#2c3e50;color:#fff;")
              .append("padding:10px 12px;text-align:left;")
              .append("border:1px solid #34495e;\">")
              .append(escapeHtml(col))
              .append("</th>");
        }
        sb.append("</tr></thead>\n");

        // Body
        sb.append("<tbody>");
        int rowIndex = 0;
        for (Map<String, Object> row : data) {
            String bgColor = (rowIndex % 2 == 0) ? "#ffffff" : "#f9f9f9";
            sb.append("<tr style=\"background:").append(bgColor).append(";\">");

            for (String col : columns) {
                Object value = row.get(col);
                String cellValue = value != null
                        ? value.toString()
                        : "-";

                sb.append("<td style=\"padding:8px 12px;")
                  .append("border:1px solid #e0e0e0;")
                  .append("vertical-align:top;\">")
                  .append(escapeHtml(cellValue))
                  .append("</td>");
            }

            sb.append("</tr>\n");
            rowIndex++;
        }
        sb.append("</tbody></table>");

        return sb.toString();
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private String buildEmailBody(
            String htmlTable,
            String title,
            String fecha,
            int totalRegistros
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n");
        sb.append("<html>\n");
        sb.append("<head>\n");
        sb.append("  <meta charset=\"UTF-8\">\n");
        sb.append("  <style>\n");
        sb.append("    body { font-family: Segoe UI, Arial, sans-serif; ");
        sb.append("margin: 0; padding: 20px; background: #f5f5f5; }\n");
        sb.append("    .container { max-width: 950px; margin: 0 auto; ");
        sb.append("background: white; border-radius: 8px; overflow: hidden; ");
        sb.append("box-shadow: 0 2px 8px rgba(0,0,0,0.1); }\n");
        sb.append("    .header { background: #1a3c6e; color: white; ");
        sb.append("padding: 20px 30px; }\n");
        sb.append("    .header h2 { margin: 0; font-size: 20px; }\n");
        sb.append("    .header p { margin: 5px 0 0; opacity: 0.85; ");
        sb.append("font-size: 13px; }\n");
        sb.append("    .content { padding: 20px 30px; }\n");
        sb.append("    .summary { background: #eef3fb; ");
        sb.append("border-left: 4px solid #1a3c6e; ");
        sb.append("padding: 12px 16px; margin-bottom: 20px; ");
        sb.append("border-radius: 0 4px 4px 0; font-size: 14px; }\n");
        sb.append("    .summary strong { color: #1a3c6e; }\n");
        sb.append("    table { width: 100%; border-collapse: collapse; ");
        sb.append("font-size: 13px; }\n");
        sb.append("    table th { background: #2c3e50; color: white; ");
        sb.append("padding: 10px 12px; text-align: left; }\n");
        sb.append("    table td { padding: 8px 12px; ");
        sb.append("border-bottom: 1px solid #e0e0e0; }\n");
        sb.append("    table tr:nth-child(even) { background: #f9f9f9; }\n");
        sb.append("    table tr:hover { background: #eef2f7; }\n");
        sb.append("    .footer { padding: 15px 30px; background: #f8f8f8; ");
        sb.append("font-size: 11px; color: #999; ");
        sb.append("border-top: 1px solid #e0e0e0; }\n");
        sb.append("  </style>\n");
        sb.append("</head>\n");
        sb.append("<body>\n");
        sb.append("  <div class=\"container\">\n");
        sb.append("    <div class=\"header\">\n");
        sb.append("      <h2>").append(escapeHtml(title)).append("</h2>\n");
        sb.append("      <p>Fecha: ").append(fecha).append("</p>\n");
        sb.append("    </div>\n");
        sb.append("    <div class=\"content\">\n");
        sb.append("      <div class=\"summary\">\n");
        sb.append("        Total de registros: <strong>")
          .append(totalRegistros).append("</strong>\n");
        sb.append("      </div>\n");
        sb.append("      ").append(htmlTable).append("\n");
        sb.append("      <p style=\"margin-top:20px;font-size:14px;\">")
          .append("<a href=\"http://offboarding-control/login.html\" ")
          .append("style=\"color:#1a3c6e;font-weight:bold;\">")
          .append("Ingresar al portal de Offboarding</a></p>\n");
        sb.append("    </div>\n");
        sb.append("    <div class=\"footer\">\n");
        sb.append("      Generado automaticamente por OffboardingTCL ");
        sb.append("&bull; No responder a este correo\n");
        sb.append("    </div>\n");
        sb.append("  </div>\n");
        sb.append("</body>\n");
        sb.append("</html>");
        return sb.toString();
    }

    /* BULK_LOCAL_EMAIL_BODY_R3 */
    public String buildBulkOffboardingBody(
            List<Map<String,Object>> data, String subject
    ) {
        if (data == null || data.isEmpty()) {
            throw new IllegalArgumentException("No hay registros para el resumen.");
        }
        String fecha = LocalDate.now()
                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        return buildEmailBody(
                buildHtmlTableLocally(data), subject, fecha, data.size());
    }
}

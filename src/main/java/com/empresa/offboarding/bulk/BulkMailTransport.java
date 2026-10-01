package com.empresa.offboarding.bulk;

import com.empresa.offboarding.service.NotificationService;
import jakarta.mail.internet.InternetAddress;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.*;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class BulkMailTransport {
    private final ObjectProvider<JavaMailSender> provider;
    private final NotificationService notification;

    @Value("${APP_NOTIFICATIONS_ENABLED:${app.notifications.enabled:true}}")
    private boolean enabled;

    @Value("${NOTIFICATION_FROM:${notification.from:${spring.mail.username:}}}")
    private String from;

    public BulkMailTransport(
        ObjectProvider<JavaMailSender> provider, NotificationService notification
    ) {
        this.provider=provider;
        this.notification=notification;
    }

    public String send(
        List<Map<String,Object>> rows, String subject, String recipients, String id
    ) throws Exception {
        if (!enabled) return "DISABLED";
        if (recipients == null || recipients.isBlank()) return "NO_RECIPIENTS";

        JavaMailSender sender=provider.getIfAvailable();
        if (sender == null || from == null || from.isBlank()) return "CONFIG_ERROR";

        // Limites de tiempo solo para Bulk; conserva el resto de propiedades SMTP.
        if (sender instanceof JavaMailSenderImpl original &&
            original.getHost() != null && !original.getHost().isBlank()) {
            JavaMailSenderImpl isolated=new JavaMailSenderImpl();
            isolated.setHost(original.getHost());
            isolated.setPort(original.getPort());
            isolated.setProtocol(original.getProtocol());
            isolated.setUsername(original.getUsername());
            isolated.setPassword(original.getPassword());
            Properties properties=new Properties();
            properties.putAll(original.getSession().getProperties());
            properties.putIfAbsent("mail.smtp.connectiontimeout","10000");
            properties.putIfAbsent("mail.smtp.timeout","20000");
            properties.putIfAbsent("mail.smtp.writetimeout","20000");
            isolated.setJavaMailProperties(properties);
            sender=isolated;
        }

        InternetAddress[] addresses=InternetAddress.parse(
            recipients.replace(';',','), true);
        if (addresses.length == 0) return "NO_RECIPIENTS";
        for (InternetAddress address:addresses) address.validate();

        var message=sender.createMimeMessage();
        var helper=new MimeMessageHelper(message,false,"UTF-8");
        helper.setFrom(from);
        helper.setTo(addresses);
        helper.setSubject(subject);
        helper.setText(notification.buildBulkOffboardingBody(rows,subject),true);
        message.setHeader("X-Offboarding-Batch",id);

        // No se oculta el error del transporte.
        sender.send(message);
        return "SENT";
    }
}
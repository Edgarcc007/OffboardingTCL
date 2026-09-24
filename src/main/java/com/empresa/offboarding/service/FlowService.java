package com.empresa.offboarding.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class FlowService {

    @Value("${flow.html-table.url}")
    private String flowUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String getHtmlTableFromFlow(List<Map<String, Object>> data) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            String jsonBody = objectMapper.writeValueAsString(data);
            HttpEntity<String> request = new HttpEntity<>(jsonBody, headers);

            log.info("Enviando {} registros al Flow de Power Automate...", data.size());

            ResponseEntity<String> response = restTemplate.exchange(
                    flowUrl, HttpMethod.POST, request, String.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                String body = response.getBody();
                log.info("Tabla HTML recibida del Flow ({} caracteres)", body.length());
                return body;
            } else {
                log.error("Flow respondio con status: {}", response.getStatusCode());
                return null;
            }
        } catch (Exception e) {
            log.error("Error al llamar al Flow: {}", e.getMessage(), e);
            return null;
        }
    }
}
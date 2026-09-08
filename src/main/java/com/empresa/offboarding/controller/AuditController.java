package com.empresa.offboarding.controller;

import com.empresa.offboarding.dto.AuditEventResponse;
import com.empresa.offboarding.repository.AuditEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditEventRepository auditEventRepository;

    @GetMapping
    public List<AuditEventResponse> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        int safePage = Math.max(page, 0);
        int safeSize = Math.max(1, Math.min(size, 200));

        return auditEventRepository
                .findAllByOrderByOccurredAtDesc(PageRequest.of(safePage, safeSize))
                .map(e -> new AuditEventResponse(
                        e.getId(), e.getOccurredAt(), e.getActor(), e.getAction(),
                        e.getEntityType(), e.getEntityId(), e.getDetails()))
                .getContent();
    }
}
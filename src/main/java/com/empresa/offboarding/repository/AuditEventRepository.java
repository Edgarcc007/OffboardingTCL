package com.empresa.offboarding.repository;

import com.empresa.offboarding.entity.AuditEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    Page<AuditEvent> findAllByOrderByOccurredAtDesc(Pageable pageable);

    List<AuditEvent> findByEntityTypeAndEntityIdOrderByOccurredAtAsc(
            String entityType, Long entityId);

    List<AuditEvent> findByEntityTypeAndEntityIdOrderByOccurredAtDesc(
            String entityType, Long entityId);
}
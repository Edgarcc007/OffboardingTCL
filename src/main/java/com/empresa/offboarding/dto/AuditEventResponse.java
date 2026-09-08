package com.empresa.offboarding.dto;

import java.time.OffsetDateTime;
import java.util.Objects;

public class AuditEventResponse {
    private Long id;
    private OffsetDateTime occurredAt;
    private String actor;
    private String action;
    private String entityType;
    private Long entityId;
    private String details;

    public AuditEventResponse() {
    }

    public AuditEventResponse(Long id, OffsetDateTime occurredAt, String actor, String action,
                              String entityType, Long entityId, String details) {
        this.id = id;
        this.occurredAt = occurredAt;
        this.actor = actor;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.details = details;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public OffsetDateTime getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(OffsetDateTime occurredAt) {
        this.occurredAt = occurredAt;
    }

    public String getActor() {
        return actor;
    }

    public void setActor(String actor) {
        this.actor = actor;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public Long getEntityId() {
        return entityId;
    }

    public void setEntityId(Long entityId) {
        this.entityId = entityId;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AuditEventResponse that = (AuditEventResponse) o;
        return Objects.equals(id, that.id)
                && Objects.equals(occurredAt, that.occurredAt)
                && Objects.equals(actor, that.actor)
                && Objects.equals(action, that.action)
                && Objects.equals(entityType, that.entityType)
                && Objects.equals(entityId, that.entityId)
                && Objects.equals(details, that.details);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, occurredAt, actor, action, entityType, entityId, details);
    }

    @Override
    public String toString() {
        return "AuditEventResponse{" +
                "id=" + id +
                ", occurredAt=" + occurredAt +
                ", actor='" + actor + '\'' +
                ", action='" + action + '\'' +
                ", entityType='" + entityType + '\'' +
                ", entityId=" + entityId +
                ", details='" + details + '\'' +
                '}';
    }
}
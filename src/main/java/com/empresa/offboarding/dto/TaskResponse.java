package com.empresa.offboarding.dto;

import com.empresa.offboarding.enums.TaskStatus;

import java.time.OffsetDateTime;
import java.util.Objects;

public class TaskResponse {
    private final Long id;
    private final String systemName;
    private final String taskName;
    private final boolean critical;
    private final boolean automatic;
    private final OffsetDateTime dueAt;
    private final TaskStatus status;
    private final String evidenceReference;
    private final String comments;
    private final OffsetDateTime completedAt;
    private final String completedBy;
    private final OffsetDateTime validatedAt;
    private final String validatedBy;

    public TaskResponse(
            Long id,
            String systemName,
            String taskName,
            boolean critical,
            boolean automatic,
            OffsetDateTime dueAt,
            TaskStatus status,
            String evidenceReference,
            String comments,
            OffsetDateTime completedAt,
            String completedBy,
            OffsetDateTime validatedAt,
            String validatedBy
    ) {
        this.id = id;
        this.systemName = systemName;
        this.taskName = taskName;
        this.critical = critical;
        this.automatic = automatic;
        this.dueAt = dueAt;
        this.status = status;
        this.evidenceReference = evidenceReference;
        this.comments = comments;
        this.completedAt = completedAt;
        this.completedBy = completedBy;
        this.validatedAt = validatedAt;
        this.validatedBy = validatedBy;
    }

    public Long getId() {
        return id;
    }

    public String getSystemName() {
        return systemName;
    }

    public String getTaskName() {
        return taskName;
    }

    public boolean isCritical() {
        return critical;
    }

    public boolean isAutomatic() {
        return automatic;
    }

    public OffsetDateTime getDueAt() {
        return dueAt;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public String getEvidenceReference() {
        return evidenceReference;
    }

    public String getComments() {
        return comments;
    }

    public OffsetDateTime getCompletedAt() {
        return completedAt;
    }

    public String getCompletedBy() {
        return completedBy;
    }

    public OffsetDateTime getValidatedAt() {
        return validatedAt;
    }

    public String getValidatedBy() {
        return validatedBy;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TaskResponse that = (TaskResponse) o;
        return critical == that.critical
                && automatic == that.automatic
                && Objects.equals(id, that.id)
                && Objects.equals(systemName, that.systemName)
                && Objects.equals(taskName, that.taskName)
                && Objects.equals(dueAt, that.dueAt)
                && status == that.status
                && Objects.equals(evidenceReference, that.evidenceReference)
                && Objects.equals(comments, that.comments)
                && Objects.equals(completedAt, that.completedAt)
                && Objects.equals(completedBy, that.completedBy)
                && Objects.equals(validatedAt, that.validatedAt)
                && Objects.equals(validatedBy, that.validatedBy);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, systemName, taskName, critical, automatic, dueAt, status,
                evidenceReference, comments, completedAt, completedBy, validatedAt, validatedBy);
    }

    @Override
    public String toString() {
        return "TaskResponse{" +
                "id=" + id +
                ", systemName='" + systemName + '\'' +
                ", taskName='" + taskName + '\'' +
                ", critical=" + critical +
                ", automatic=" + automatic +
                ", dueAt=" + dueAt +
                ", status=" + status +
                ", evidenceReference='" + evidenceReference + '\'' +
                ", comments='" + comments + '\'' +
                ", completedAt=" + completedAt +
                ", completedBy='" + completedBy + '\'' +
                ", validatedAt=" + validatedAt +
                ", validatedBy='" + validatedBy + '\'' +
                '}';
    }
}

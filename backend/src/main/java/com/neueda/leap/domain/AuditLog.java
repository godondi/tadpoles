package com.neueda.leap.domain;

import java.time.LocalDateTime;

/**
 * Domain model for the `audit_logs` table.
 */
public class AuditLog {
    private Integer auditLogId;
    private Integer userId;
    private String entityName;
    private String entityId;
    private String actionType;
    private String oldValues;
    private String newValues;
    private LocalDateTime createdAt;
    public Integer getAuditLogId() {
        return auditLogId;
    }
    public void setAuditLogId(Integer auditLogId) {
        this.auditLogId = auditLogId;
    }
    public Integer getUserId() {
        return userId;
    }
    public void setUserId(Integer userId) {
        this.userId = userId;
    }
    public String getEntityName() {
        return entityName;
    }
    public void setEntityName(String entityName) {
        this.entityName = entityName;
    }
    public String getEntityId() {
        return entityId;
    }
    public void setEntityId(String entityId) {
        this.entityId = entityId;
    }
    public String getActionType() {
        return actionType;
    }
    public void setActionType(String actionType) {
        this.actionType = actionType;
    }
    public String getOldValues() {
        return oldValues;
    }
    public void setOldValues(String oldValues) {
        this.oldValues = oldValues;
    }
    public String getNewValues() {
        return newValues;
    }
    public void setNewValues(String newValues) {
        this.newValues = newValues;
    }
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

package com.yourdomain.model;

/** Row of the audit_logs table (with the acting user's username). */
public record AuditLog(int id, String username, String action, String tableName,
                       int recordId, String timestamp) {
}

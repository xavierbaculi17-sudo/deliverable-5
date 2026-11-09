package com.yourdomain.dao;

import com.yourdomain.model.AuditLog;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Writes and reads audit_logs (User Activity Log + Booking Audit Log). */
public class AuditLogDao {
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public void log(int userId, String action, String tableName, int recordId) {
        String sql = "INSERT INTO audit_logs (user_id, action, table_name, record_id, log_timestamp) "
                   + "VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, action);
            ps.setString(3, tableName);
            ps.setInt(4, recordId);
            ps.setString(5, LocalDateTime.now().format(TS));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Could not write audit log: " + e.getMessage(), e);
        }
    }

    /** Login / logout / registration events. */
    public List<AuditLog> getUserActivity(int limit) {
        String sql = "SELECT l.log_id, u.username, l.action, l.table_name, l.record_id, l.log_timestamp "
                   + "FROM audit_logs l JOIN users u ON u.user_id = l.user_id "
                   + "WHERE l.table_name = 'users' ORDER BY l.log_id DESC LIMIT ?";
        return query(sql, limit);
    }

    /** Booking creation, status changes, cancellations and payment entries. */
    public List<AuditLog> getBookingAudit(int limit) {
        String sql = "SELECT l.log_id, u.username, l.action, l.table_name, l.record_id, l.log_timestamp "
                   + "FROM audit_logs l JOIN users u ON u.user_id = l.user_id "
                   + "WHERE l.table_name IN ('bookings', 'payments') ORDER BY l.log_id DESC LIMIT ?";
        return query(sql, limit);
    }

    private List<AuditLog> query(String sql, int limit) {
        List<AuditLog> list = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new AuditLog(rs.getInt(1), rs.getString(2), rs.getString(3),
                            rs.getString(4), rs.getInt(5), rs.getString(6)));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not read audit logs: " + e.getMessage(), e);
        }
        return list;
    }
}

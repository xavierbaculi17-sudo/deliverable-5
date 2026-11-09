package com.yourdomain.dao;

import com.yourdomain.model.CateringPackage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class PackageDao {

    public int add(String name, String description, String eventType, double pricePerHead, int minGuests) {
        String sql = "INSERT INTO packages (package_name, description, event_type, price_per_head, min_guests) "
                   + "VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, description);
            ps.setString(3, eventType);
            ps.setDouble(4, pricePerHead);
            ps.setInt(5, minGuests);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getInt(1) : -1;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not add package: " + e.getMessage(), e);
        }
    }

    public boolean update(CateringPackage p) {
        String sql = "UPDATE packages SET package_name = ?, description = ?, event_type = ?, "
                   + "price_per_head = ?, min_guests = ? WHERE package_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, p.name());
            ps.setString(2, p.description());
            ps.setString(3, p.eventType());
            ps.setDouble(4, p.pricePerHead());
            ps.setInt(5, p.minGuests());
            ps.setInt(6, p.id());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DataAccessException("Could not update package: " + e.getMessage(), e);
        }
    }

    /** Soft delete: the row stays so old bookings keep their package reference. */
    public boolean discontinue(int packageId) {
        String sql = "UPDATE packages SET status = 'DISCONTINUED' WHERE package_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, packageId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DataAccessException("Could not discontinue package: " + e.getMessage(), e);
        }
    }

    public List<CateringPackage> getAll() {
        String sql = "SELECT package_id, package_name, description, event_type, price_per_head, min_guests, status "
                   + "FROM packages ORDER BY package_id";
        return query(sql, "", "", Double.MAX_VALUE, false);
    }

    /** Active packages filtered by event type (blank = any) and maximum price per head. */
    public List<CateringPackage> searchActive(String eventType, double maxPrice) {
        String sql = "SELECT package_id, package_name, description, event_type, price_per_head, min_guests, status "
                   + "FROM packages WHERE status = 'ACTIVE' AND (? = '' OR LOWER(event_type) LIKE ?) "
                   + "AND price_per_head <= ? ORDER BY package_id";
        String type = eventType == null ? "" : eventType.trim().toLowerCase();
        return query(sql, type, "%" + type + "%", maxPrice, true);
    }

    public Optional<CateringPackage> getById(int id) {
        String sql = "SELECT package_id, package_name, description, event_type, price_per_head, min_guests, status "
                   + "FROM packages WHERE package_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not read package: " + e.getMessage(), e);
        }
    }

    private List<CateringPackage> query(String sql, String type, String like, double max, boolean filtered) {
        List<CateringPackage> list = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (filtered) {
                ps.setString(1, type);
                ps.setString(2, like);
                ps.setDouble(3, max);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not read packages: " + e.getMessage(), e);
        }
        return list;
    }

    private CateringPackage map(ResultSet rs) throws SQLException {
        return new CateringPackage(rs.getInt("package_id"), rs.getString("package_name"),
                rs.getString("description"), rs.getString("event_type"),
                rs.getDouble("price_per_head"), rs.getInt("min_guests"), rs.getString("status"));
    }
}

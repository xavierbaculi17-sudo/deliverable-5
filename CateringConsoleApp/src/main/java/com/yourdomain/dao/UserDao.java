package com.yourdomain.dao;

import com.yourdomain.model.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** All users-table access. Every query is a parameterized PreparedStatement. */
public class UserDao {

    /** Returns every registered account regardless of role or status. */
    public List<User> getAllUsers() {
        String sql = "SELECT user_id, username, password_hash, full_name, contact_no, email, role, status "
                   + "FROM users ORDER BY user_id";
        return queryList(sql, null);
    }

    /** Returns all accounts (any role) whose status is PENDING. */
    public List<User> getPendingUsers() {
        String sql = "SELECT user_id, username, password_hash, full_name, contact_no, email, role, status "
                   + "FROM users WHERE status = ? ORDER BY user_id";
        return queryList(sql, "PENDING");
    }

    /** Changes a user's status from PENDING to APPROVED. Returns true if a row was updated. */
    public boolean approveUser(int userId) {
        String sql = "UPDATE users SET status = 'APPROVED' WHERE user_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DataAccessException("Could not approve user: " + e.getMessage(), e);
        }
    }

    /** Soft delete / reactivate: sets status to INACTIVE or APPROVED. */
    public boolean updateStatus(int userId, String status) {
        String sql = "UPDATE users SET status = ? WHERE user_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setInt(2, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DataAccessException("Could not update user status: " + e.getMessage(), e);
        }
    }

    public Optional<User> findByUsername(String username) {
        String sql = "SELECT user_id, username, password_hash, full_name, contact_no, email, role, status "
                   + "FROM users WHERE username = ?";
        List<User> result = queryList(sql, username);
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    /** Inserts a new account and returns its generated user_id. */
    public int createUser(String username, String passwordHash, String fullName,
                          String contactNo, String email, String role, String status) {
        String sql = "INSERT INTO users (username, password_hash, full_name, contact_no, email, role, status) "
                   + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, username);
            ps.setString(2, passwordHash);
            ps.setString(3, fullName);
            ps.setString(4, contactNo);
            ps.setString(5, email);
            ps.setString(6, role);
            ps.setString(7, status);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getInt(1) : -1;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not create user: " + e.getMessage(), e);
        }
    }

    // ---- helpers ---------------------------------------------------------------------------

    private List<User> queryList(String sql, String param) {
        List<User> users = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (param != null) ps.setString(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) users.add(map(rs));
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not read users: " + e.getMessage(), e);
        }
        return users;
    }

    private User map(ResultSet rs) throws SQLException {
        return new User(rs.getInt("user_id"), rs.getString("username"), rs.getString("password_hash"),
                rs.getString("full_name"), rs.getString("contact_no"), rs.getString("email"),
                rs.getString("role"), rs.getString("status"));
    }
}

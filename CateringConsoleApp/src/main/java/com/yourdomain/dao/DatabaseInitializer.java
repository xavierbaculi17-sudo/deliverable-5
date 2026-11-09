package com.yourdomain.dao;

import com.yourdomain.util.PasswordUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/** Creates the schema (5 tables + 1 view) and seeds demo data on first run. */
public final class DatabaseInitializer {

    private DatabaseInitializer() {}

    public static void initialize() {
        try (Connection conn = DatabaseConnection.getConnection();
             Statement st = conn.createStatement()) {

            st.execute("""
                CREATE TABLE IF NOT EXISTS users (
                    user_id       INTEGER PRIMARY KEY AUTOINCREMENT,
                    username      TEXT NOT NULL UNIQUE,
                    password_hash TEXT NOT NULL,
                    full_name     TEXT NOT NULL,
                    contact_no    TEXT,
                    email         TEXT,
                    role          TEXT NOT NULL CHECK (role   IN ('ADMIN','STAFF','CUSTOMER')),
                    status        TEXT NOT NULL DEFAULT 'PENDING'
                                  CHECK (status IN ('PENDING','APPROVED','INACTIVE'))
                )""");

            st.execute("""
                CREATE TABLE IF NOT EXISTS packages (
                    package_id     INTEGER PRIMARY KEY AUTOINCREMENT,
                    package_name   TEXT NOT NULL UNIQUE,
                    description    TEXT,
                    event_type     TEXT NOT NULL,
                    price_per_head REAL NOT NULL CHECK (price_per_head > 0),
                    min_guests     INTEGER NOT NULL DEFAULT 1 CHECK (min_guests > 0),
                    status         TEXT NOT NULL DEFAULT 'ACTIVE'
                                   CHECK (status IN ('ACTIVE','DISCONTINUED'))
                )""");

            st.execute("""
                CREATE TABLE IF NOT EXISTS bookings (
                    booking_id     INTEGER PRIMARY KEY AUTOINCREMENT,
                    customer_id    INTEGER NOT NULL,
                    package_id     INTEGER NOT NULL,
                    event_type     TEXT NOT NULL,
                    event_date     TEXT NOT NULL,
                    venue          TEXT NOT NULL,
                    guest_count    INTEGER NOT NULL CHECK (guest_count > 0),
                    total_amount   REAL NOT NULL,
                    booking_status TEXT NOT NULL DEFAULT 'PENDING'
                                   CHECK (booking_status IN ('PENDING','CONFIRMED','COMPLETED','CANCELLED')),
                    date_created   TEXT NOT NULL,
                    FOREIGN KEY (customer_id) REFERENCES users(user_id),
                    FOREIGN KEY (package_id)  REFERENCES packages(package_id)
                )""");

            st.execute("""
                CREATE TABLE IF NOT EXISTS payments (
                    payment_id     INTEGER PRIMARY KEY AUTOINCREMENT,
                    booking_id     INTEGER NOT NULL,
                    amount         REAL NOT NULL CHECK (amount > 0),
                    payment_method TEXT NOT NULL,
                    payment_type   TEXT NOT NULL CHECK (payment_type IN ('DOWN_PAYMENT','FULL_PAYMENT')),
                    payment_date   TEXT NOT NULL,
                    received_by    INTEGER NOT NULL,
                    FOREIGN KEY (booking_id)  REFERENCES bookings(booking_id),
                    FOREIGN KEY (received_by) REFERENCES users(user_id)
                )""");

            st.execute("""
                CREATE TABLE IF NOT EXISTS audit_logs (
                    log_id        INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_id       INTEGER NOT NULL,
                    action        TEXT NOT NULL,
                    table_name    TEXT NOT NULL,
                    record_id     INTEGER NOT NULL,
                    log_timestamp TEXT NOT NULL,
                    FOREIGN KEY (user_id) REFERENCES users(user_id)
                )""");

            // View used by BookingDao so queries stay short and fully parameterized.
            st.execute("""
                CREATE VIEW IF NOT EXISTS booking_details AS
                SELECT b.booking_id, b.customer_id, u.full_name AS customer_name,
                       b.package_id, p.package_name, b.event_type, b.event_date, b.venue,
                       b.guest_count, b.total_amount, b.booking_status, b.date_created,
                       (SELECT COALESCE(SUM(pay.amount), 0)
                          FROM payments pay WHERE pay.booking_id = b.booking_id) AS paid_amount
                FROM bookings b
                JOIN users u    ON u.user_id    = b.customer_id
                JOIN packages p ON p.package_id = b.package_id
                """);

            seed(conn);
        } catch (SQLException e) {
            throw new DataAccessException("Database initialization failed: " + e.getMessage(), e);
        }
    }

    private static void seed(Connection conn) throws SQLException {
        if (isEmpty(conn, "users")) {
            insertUser(conn, "admin",           "admin123", "System Administrator", "09170000001", "admin@catering.com",  "ADMIN",    "APPROVED");
            insertUser(conn, "staff1",          "staff123", "Carla Mendoza",        "09170000002", "carla@catering.com",  "STAFF",    "APPROVED");
            insertUser(conn, "customer1",       "cust123",  "Pedro Ramirez",        "09170000003", "pedro@mail.com",      "CUSTOMER", "APPROVED");
            // Demo accounts waiting for the administrator's approval:
            insertUser(conn, "maria.santos",    "pass1234", "Maria Santos",         "09170000004", "maria@mail.com",      "CUSTOMER", "PENDING");
            insertUser(conn, "juan.delacruz",   "pass1234", "Juan Dela Cruz",       "09170000005", "juan@mail.com",       "CUSTOMER", "PENDING");
            insertUser(conn, "ana.reyes",       "pass1234", "Ana Reyes",            "09170000006", "ana@catering.com",    "STAFF",    "PENDING");
        }
        if (isEmpty(conn, "packages")) {
            insertPackage(conn, "Classic Wedding Feast", "5-course plated menu, drinks and dessert bar", "Wedding",  650.00, 50);
            insertPackage(conn, "Birthday Fiesta",       "Buffet: 2 mains, pasta, rice, dessert, drinks", "Birthday", 350.00, 20);
            insertPackage(conn, "Corporate Lunch Box",   "Boxed meals with drink for company events",     "Corporate",280.00, 30);
        }
    }

    private static boolean isEmpty(Connection conn, String table) throws SQLException {
        // Table name comes from our own constants (never user input).
        String sql = "users".equals(table) ? "SELECT COUNT(*) FROM users" : "SELECT COUNT(*) FROM packages";
        try (PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            return rs.next() && rs.getInt(1) == 0;
        }
    }

    private static void insertUser(Connection conn, String username, String password, String fullName,
                                   String contact, String email, String role, String status) throws SQLException {
        String sql = "INSERT INTO users (username, password_hash, full_name, contact_no, email, role, status) "
                   + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, PasswordUtil.hash(password));
            ps.setString(3, fullName);
            ps.setString(4, contact);
            ps.setString(5, email);
            ps.setString(6, role);
            ps.setString(7, status);
            ps.executeUpdate();
        }
    }

    private static void insertPackage(Connection conn, String name, String desc, String eventType,
                                      double price, int minGuests) throws SQLException {
        String sql = "INSERT INTO packages (package_name, description, event_type, price_per_head, min_guests) "
                   + "VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, desc);
            ps.setString(3, eventType);
            ps.setDouble(4, price);
            ps.setInt(5, minGuests);
            ps.executeUpdate();
        }
    }
}

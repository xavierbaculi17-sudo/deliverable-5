package com.yourdomain.dao;

import com.yourdomain.model.Booking;
import com.yourdomain.model.Payment;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Bookings, payments and report queries. Reads go through the booking_details view. */
public class BookingDao {
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String SELECT_VIEW =
            "SELECT booking_id, customer_id, customer_name, package_id, package_name, event_type, event_date, "
          + "venue, guest_count, total_amount, booking_status, date_created, paid_amount FROM booking_details ";

    // ---- CREATE ------------------------------------------------------------------------------

    public int create(int customerId, int packageId, String eventType, LocalDate eventDate,
                      String venue, int guestCount, double totalAmount) {
        String sql = "INSERT INTO bookings (customer_id, package_id, event_type, event_date, venue, "
                   + "guest_count, total_amount, booking_status, date_created) "
                   + "VALUES (?, ?, ?, ?, ?, ?, ?, 'PENDING', ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, customerId);
            ps.setInt(2, packageId);
            ps.setString(3, eventType);
            ps.setString(4, eventDate.toString());
            ps.setString(5, venue);
            ps.setInt(6, guestCount);
            ps.setDouble(7, totalAmount);
            ps.setString(8, LocalDateTime.now().format(TS));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getInt(1) : -1;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not create booking: " + e.getMessage(), e);
        }
    }

    public int addPayment(int bookingId, double amount, String method, String type, int receivedBy) {
        String sql = "INSERT INTO payments (booking_id, amount, payment_method, payment_type, payment_date, received_by) "
                   + "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, bookingId);
            ps.setDouble(2, amount);
            ps.setString(3, method);
            ps.setString(4, type);
            ps.setString(5, LocalDateTime.now().format(TS));
            ps.setInt(6, receivedBy);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getInt(1) : -1;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not record payment: " + e.getMessage(), e);
        }
    }

    // ---- READ --------------------------------------------------------------------------------

    public List<Booking> getAll() {
        return queryBookings(SELECT_VIEW + "ORDER BY event_date, booking_id");
    }

    public List<Booking> getByCustomer(int customerId) {
        return queryBookings(SELECT_VIEW + "WHERE customer_id = ? ORDER BY event_date, booking_id", customerId);
    }

    public List<Booking> getByStatus(String status) {
        return queryBookings(SELECT_VIEW + "WHERE booking_status = ? ORDER BY event_date, booking_id", status);
    }

    public Optional<Booking> getById(int bookingId) {
        List<Booking> list = queryBookings(SELECT_VIEW + "WHERE booking_id = ?", bookingId);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    /** Confirmed or completed bookings that still have an unpaid balance. */
    public List<Booking> getWithBalance() {
        return queryBookings(SELECT_VIEW
                + "WHERE booking_status IN ('CONFIRMED', 'COMPLETED') AND total_amount - paid_amount > 0.005 "
                + "ORDER BY event_date, booking_id");
    }

    /** Confirmed events between two dates (inclusive). */
    public List<Booking> getConfirmedBetween(LocalDate from, LocalDate to) {
        return queryBookings(SELECT_VIEW
                + "WHERE booking_status = 'CONFIRMED' AND event_date BETWEEN ? AND ? ORDER BY event_date",
                from.toString(), to.toString());
    }

    /** Confirmed bookings in a month (yyyy-MM) - used by the booking calendar. */
    public List<Booking> getConfirmedInMonth(String yearMonth) {
        return queryBookings(SELECT_VIEW
                + "WHERE booking_status = 'CONFIRMED' AND substr(event_date, 1, 7) = ? ORDER BY event_date",
                yearMonth);
    }

    /** True if another CONFIRMED booking already holds that date. */
    public boolean isDateTaken(LocalDate date, int excludeBookingId) {
        String sql = "SELECT COUNT(*) FROM bookings WHERE event_date = ? "
                   + "AND booking_status = 'CONFIRMED' AND booking_id <> ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, date.toString());
            ps.setInt(2, excludeBookingId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not check date: " + e.getMessage(), e);
        }
    }

    public List<Payment> getPayments(int bookingId) {
        String sql = "SELECT p.payment_id, p.booking_id, p.amount, p.payment_method, p.payment_type, "
                   + "p.payment_date, u.full_name FROM payments p JOIN users u ON u.user_id = p.received_by "
                   + "WHERE p.booking_id = ? ORDER BY p.payment_id";
        List<Payment> list = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, bookingId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new Payment(rs.getInt(1), rs.getInt(2), rs.getDouble(3), rs.getString(4),
                            rs.getString(5), rs.getString(6), rs.getString(7)));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not read payments: " + e.getMessage(), e);
        }
        return list;
    }

    // ---- UPDATE / DELETE ---------------------------------------------------------------------

    public boolean updateStatus(int bookingId, String newStatus) {
        return execute("UPDATE bookings SET booking_status = ? WHERE booking_id = ?", newStatus, bookingId);
    }

    public boolean updateEventDetails(int bookingId, LocalDate date, String venue, int guests, double total) {
        return execute("UPDATE bookings SET event_date = ?, venue = ?, guest_count = ?, total_amount = ? "
                     + "WHERE booking_id = ?", date.toString(), venue, guests, total, bookingId);
    }

    /** Customer cancels their own booking, only while it is still PENDING. */
    public boolean cancelPendingByCustomer(int bookingId, int customerId) {
        return execute("UPDATE bookings SET booking_status = 'CANCELLED' "
                     + "WHERE booking_id = ? AND customer_id = ? AND booking_status = 'PENDING'",
                bookingId, customerId);
    }

    /** Hard-deletes an unconfirmed (PENDING/CANCELLED) booking that has no payments. */
    public boolean deleteUnconfirmed(int bookingId) {
        return execute("DELETE FROM bookings WHERE booking_id = ? "
                     + "AND booking_status IN ('PENDING', 'CANCELLED') "
                     + "AND NOT EXISTS (SELECT 1 FROM payments WHERE payments.booking_id = bookings.booking_id)",
                bookingId);
    }

    // ---- REPORTS -----------------------------------------------------------------------------

    /** Bookings per status for events in the month (yyyy-MM). */
    public Map<String, Integer> countByStatusForMonth(String yearMonth) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String s : new String[]{"PENDING", "CONFIRMED", "COMPLETED", "CANCELLED"}) counts.put(s, 0);
        String sql = "SELECT booking_status, COUNT(*) FROM bookings WHERE substr(event_date, 1, 7) = ? "
                   + "GROUP BY booking_status";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, yearMonth);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) counts.put(rs.getString(1), rs.getInt(2));
            }
        } catch (SQLException e) {
            throw new DataAccessException("Report failed: " + e.getMessage(), e);
        }
        return counts;
    }

    /** Total value of non-cancelled bookings whose event is in the month. */
    public double totalSalesForMonth(String yearMonth) {
        return scalar("SELECT COALESCE(SUM(total_amount), 0) FROM bookings "
                    + "WHERE substr(event_date, 1, 7) = ? AND booking_status <> 'CANCELLED'", yearMonth);
    }

    /** Sum of payments received during the month. */
    public double paymentsCollectedForMonth(String yearMonth) {
        return scalar("SELECT COALESCE(SUM(amount), 0) FROM payments WHERE substr(payment_date, 1, 7) = ?",
                yearMonth);
    }

    // ---- helpers -----------------------------------------------------------------------------

    private double scalar(String sql, String param) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getDouble(1) : 0;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Report failed: " + e.getMessage(), e);
        }
    }

    private boolean execute(String sql, Object... params) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, params);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DataAccessException("Database update failed: " + e.getMessage(), e);
        }
    }

    private List<Booking> queryBookings(String sql, Object... params) {
        List<Booking> list = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new Booking(rs.getInt("booking_id"), rs.getInt("customer_id"),
                            rs.getString("customer_name"), rs.getInt("package_id"), rs.getString("package_name"),
                            rs.getString("event_type"), rs.getString("event_date"), rs.getString("venue"),
                            rs.getInt("guest_count"), rs.getDouble("total_amount"), rs.getString("booking_status"),
                            rs.getString("date_created"), rs.getDouble("paid_amount")));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not read bookings: " + e.getMessage(), e);
        }
        return list;
    }

    private void bind(PreparedStatement ps, Object[] params) throws SQLException {
        for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
    }
}

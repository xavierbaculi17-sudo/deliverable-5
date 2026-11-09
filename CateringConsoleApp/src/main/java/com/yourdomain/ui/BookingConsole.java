package com.yourdomain.ui;

import com.yourdomain.dao.AuditLogDao;
import com.yourdomain.dao.BookingDao;
import com.yourdomain.dao.DataAccessException;
import com.yourdomain.dao.PackageDao;
import com.yourdomain.model.Booking;
import com.yourdomain.model.CateringPackage;
import com.yourdomain.model.Payment;
import com.yourdomain.model.User;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/** Booking and payment screens shared by Admin, Staff and Customer. */
public class BookingConsole {
    private final User actor;
    private final BookingDao bookingDao = new BookingDao();
    private final PackageDao packageDao = new PackageDao();
    private final AuditLogDao audit = new AuditLogDao();

    public BookingConsole(User actor) {
        this.actor = actor;
    }

    // ---- Admin / Staff -----------------------------------------------------------------------

    public void manageMenu(boolean isAdmin) {
        boolean back = false;
        while (!back) {
            System.out.println("\n--- BOOKINGS & PAYMENTS ---");
            System.out.println(" 1. View all bookings");
            System.out.println(" 2. View pending booking requests");
            System.out.println(" 3. Update booking status");
            System.out.println(" 4. Edit event details");
            System.out.println(" 5. Record payment");
            System.out.println(" 6. View booking calendar");
            if (isAdmin) System.out.println(" 7. Delete unconfirmed booking");
            System.out.println(" 0. Back");
            int c = ConsoleUtil.readInt("Choose an option: ");
            try {
                switch (c) {
                    case 1 -> printBookings(bookingDao.getAll());
                    case 2 -> printBookings(bookingDao.getByStatus("PENDING"));
                    case 3 -> updateStatus();
                    case 4 -> editEventDetails();
                    case 5 -> recordPayment();
                    case 6 -> calendar();
                    case 7 -> {
                        if (isAdmin) deleteUnconfirmed(); else System.out.println("  [!] Invalid option.");
                    }
                    case 0 -> back = true;
                    default -> System.out.println("  [!] Invalid option.");
                }
            } catch (DataAccessException e) {
                System.out.println("  [ERROR] " + e.getMessage());
            }
        }
    }

    private void updateStatus() {
        printBookings(bookingDao.getAll());
        int id = ConsoleUtil.readInt("Booking ID to update (0 to cancel): ");
        if (id == 0) return;
        Booking b = bookingDao.getById(id).orElse(null);
        if (b == null) {
            System.out.println("  [!] Booking not found.");
            return;
        }
        if (b.status().equals("COMPLETED") || b.status().equals("CANCELLED")) {
            System.out.println("  [!] A " + b.status() + " booking can no longer be changed.");
            return;
        }
        System.out.println("Current status: " + b.status());
        System.out.println(" 1. CONFIRMED   2. COMPLETED   3. CANCELLED   0. Cancel");
        int c = ConsoleUtil.readInt("New status: ");
        String next = switch (c) {
            case 1 -> "CONFIRMED";
            case 2 -> "COMPLETED";
            case 3 -> "CANCELLED";
            default -> null;
        };
        if (next == null) return;
        if (next.equals(b.status())) {
            System.out.println("  [!] Booking is already " + next + ".");
            return;
        }
        if (next.equals("COMPLETED") && !b.status().equals("CONFIRMED")) {
            System.out.println("  [!] Only CONFIRMED bookings can be marked COMPLETED.");
            return;
        }
        if (next.equals("CONFIRMED") && bookingDao.isDateTaken(LocalDate.parse(b.eventDate()), b.id())) {
            System.out.println("  [!] Another booking is already confirmed on " + b.eventDate() + ".");
            return;
        }
        if (bookingDao.updateStatus(id, next)) {
            audit.log(actor.id(), "STATUS_" + next, "bookings", id);
            System.out.println("[SUCCESS] Booking #" + id + " is now " + next + ".");
        }
    }

    private void editEventDetails() {
        printBookings(bookingDao.getAll());
        int id = ConsoleUtil.readInt("Booking ID to edit (0 to cancel): ");
        if (id == 0) return;
        Booking b = bookingDao.getById(id).orElse(null);
        if (b == null) {
            System.out.println("  [!] Booking not found.");
            return;
        }
        if (!b.status().equals("PENDING") && !b.status().equals("CONFIRMED")) {
            System.out.println("  [!] Only PENDING or CONFIRMED bookings can be edited.");
            return;
        }
        CateringPackage pkg = packageDao.getById(b.packageId()).orElseThrow();
        System.out.println("Press Enter to keep the current value.");

        LocalDate date = LocalDate.parse(b.eventDate());
        String ds = ConsoleUtil.readLine("Event date [" + b.eventDate() + "]: ");
        if (!ds.isEmpty()) {
            try {
                LocalDate parsed = LocalDate.parse(ds);
                if (!parsed.isAfter(LocalDate.now())) {
                    System.out.println("  [!] Date must be in the future. Kept old date.");
                } else {
                    date = parsed;
                }
            } catch (DateTimeParseException e) {
                System.out.println("  [!] Invalid date. Kept old date.");
            }
        }
        String vs = ConsoleUtil.readLine("Venue [" + b.venue() + "]: ");
        String venue = vs.isEmpty() ? b.venue() : vs;
        int guests = b.guestCount();
        String gs = ConsoleUtil.readLine("Guest count [" + b.guestCount() + "]: ");
        if (!gs.isEmpty()) {
            try {
                int g = Integer.parseInt(gs);
                if (g >= pkg.minGuests()) guests = g;
                else System.out.println("  [!] Minimum is " + pkg.minGuests() + ". Kept old value.");
            } catch (NumberFormatException e) {
                System.out.println("  [!] Invalid number. Kept old value.");
            }
        }
        if (b.status().equals("CONFIRMED") && bookingDao.isDateTaken(date, b.id())) {
            System.out.println("  [!] Another booking is already confirmed on " + date + ". No changes saved.");
            return;
        }
        double total = guests * pkg.pricePerHead();
        if (bookingDao.updateEventDetails(id, date, venue, guests, total)) {
            audit.log(actor.id(), "EDITED_EVENT_DETAILS", "bookings", id);
            System.out.println("[SUCCESS] Booking updated. New total: PHP " + ConsoleUtil.money(total));
        }
    }

    private void recordPayment() {
        ConsoleUtil.title("RECORD PAYMENT");
        List<Booking> due = bookingDao.getWithBalance();
        if (due.isEmpty()) {
            System.out.println("No confirmed bookings with an unpaid balance.");
            return;
        }
        printBookings(due);
        while (true) {
            int id = ConsoleUtil.readInt("Booking ID (0 to cancel): ");
            if (id == 0) {
                System.out.println("Cancelled.");
                return;
            }
            Booking b = due.stream().filter(x -> x.id() == id).findFirst().orElse(null);
            if (b == null) {
                System.out.println("  [!] Booking ID is not in the list above.");
                continue;
            }
            System.out.println("Balance due: PHP " + ConsoleUtil.money(b.balance()));
            double amount;
            while (true) {
                amount = ConsoleUtil.readPositiveDouble("Amount received: ");
                if (amount <= b.balance() + 0.005) break;
                System.out.println("  [!] Amount exceeds the balance.");
            }
            System.out.println(" 1. Cash   2. GCash (manual)   3. Bank Transfer");
            int m = ConsoleUtil.readInt("Payment method: ");
            String method = m == 2 ? "GCash" : m == 3 ? "Bank Transfer" : "Cash";
            String type = amount >= b.balance() - 0.005 ? "FULL_PAYMENT" : "DOWN_PAYMENT";
            int payId = bookingDao.addPayment(id, amount, method, type, actor.id());
            audit.log(actor.id(), "PAYMENT_" + type, "payments", payId);
            System.out.println("[SUCCESS] Payment #" + payId + " recorded (" + type + ").");
            return;
        }
    }

    private void deleteUnconfirmed() {
        printBookings(bookingDao.getAll());
        int id = ConsoleUtil.readInt("Booking ID to delete (0 to cancel): ");
        if (id == 0) return;
        if (ConsoleUtil.confirm("Permanently delete unconfirmed booking #" + id + "?")) {
            if (bookingDao.deleteUnconfirmed(id)) {
                audit.log(actor.id(), "DELETED_BOOKING", "bookings", id);
                System.out.println("[SUCCESS] Booking deleted.");
            } else {
                System.out.println("  [!] Only PENDING/CANCELLED bookings without payments can be deleted.");
            }
        }
    }

    // ---- Shared ------------------------------------------------------------------------------

    public void calendar() {
        ConsoleUtil.title("BOOKING CALENDAR");
        String input = ConsoleUtil.readLine("Month to view (yyyy-MM, Enter = this month): ");
        YearMonth ym;
        try {
            ym = input.isEmpty() ? YearMonth.now() : YearMonth.parse(input);
        } catch (DateTimeParseException e) {
            System.out.println("  [!] Use the format yyyy-MM (e.g. 2026-12).");
            return;
        }
        List<Booking> taken = bookingDao.getConfirmedInMonth(ym.toString());
        System.out.println("Dates already CONFIRMED in " + ym + ":");
        if (taken.isEmpty()) {
            System.out.println("  None - every date this month is available.");
            return;
        }
        List<String[]> rows = new ArrayList<>();
        for (Booking b : taken) {
            rows.add(new String[]{b.eventDate(), b.eventType(), b.venue()});
        }
        ConsoleUtil.printTable(new String[]{"Date", "Event", "Venue"}, new int[]{10, 12, 25}, rows);
        System.out.println("All other dates in " + ym + " are available.");
    }

    public static void printBookings(List<Booking> list) {
        if (list.isEmpty()) {
            System.out.println("No bookings found.");
            return;
        }
        List<String[]> rows = new ArrayList<>();
        for (Booking b : list) {
            rows.add(new String[]{String.valueOf(b.id()), b.customerName(), b.packageName(), b.eventDate(),
                    String.valueOf(b.guestCount()), ConsoleUtil.money(b.totalAmount()),
                    ConsoleUtil.money(b.paidAmount()), b.status()});
        }
        ConsoleUtil.printTable(
                new String[]{"ID", "Customer", "Package", "Date", "Guests", "Total", "Paid", "Status"},
                new int[]{4, 15, 20, 10, 6, 11, 11, 10}, rows);
    }

    public void printPaymentHistory(int bookingId) {
        List<Payment> payments = bookingDao.getPayments(bookingId);
        if (payments.isEmpty()) {
            System.out.println("  No payments recorded for booking #" + bookingId + ".");
            return;
        }
        List<String[]> rows = new ArrayList<>();
        for (Payment p : payments) {
            rows.add(new String[]{String.valueOf(p.id()), ConsoleUtil.money(p.amount()), p.method(),
                    p.type(), p.paymentDate(), p.receivedBy()});
        }
        ConsoleUtil.printTable(new String[]{"Pay#", "Amount", "Method", "Type", "Date", "Received By"},
                new int[]{5, 10, 13, 12, 19, 15}, rows);
    }
}

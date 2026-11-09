package com.yourdomain.ui;

import com.yourdomain.dao.AuditLogDao;
import com.yourdomain.dao.BookingDao;
import com.yourdomain.dao.DataAccessException;
import com.yourdomain.dao.PackageDao;
import com.yourdomain.model.Booking;
import com.yourdomain.model.CateringPackage;
import com.yourdomain.model.User;

import java.time.LocalDate;
import java.util.List;

/** Customer: browse packages, submit / track / cancel own bookings only. */
public class CustomerMenu {
    private final User customer;
    private final BookingDao bookingDao = new BookingDao();
    private final PackageDao packageDao = new PackageDao();
    private final AuditLogDao audit = new AuditLogDao();
    private final PackageConsole packages;
    private final BookingConsole bookings;

    public CustomerMenu(User customer) {
        this.customer = customer;
        this.packages = new PackageConsole(customer);
        this.bookings = new BookingConsole(customer);
    }

    public void show() {
        boolean running = true;
        while (running) {
            System.out.println();
            System.out.println("==========================================");
            System.out.println("  CUSTOMER MENU  -  " + customer.fullName());
            System.out.println("==========================================");
            System.out.println(" 1. Browse / Search Catering Packages");
            System.out.println(" 2. Submit Booking Request");
            System.out.println(" 3. My Bookings & Payments");
            System.out.println(" 4. Cancel a Pending Booking");
            System.out.println(" 5. View Booking Calendar");
            System.out.println(" 0. Logout");
            int choice = ConsoleUtil.readInt("Choose an option: ");
            try {
                switch (choice) {
                    case 1 -> packages.browse();
                    case 2 -> submitBooking();
                    case 3 -> myBookings();
                    case 4 -> cancelPending();
                    case 5 -> bookings.calendar();
                    case 0 -> running = false;
                    default -> System.out.println("  [!] Invalid option.");
                }
            } catch (DataAccessException e) {
                System.out.println("  [ERROR] " + e.getMessage());
            }
        }
    }

    private void submitBooking() {
        ConsoleUtil.title("SUBMIT BOOKING REQUEST");
        List<CateringPackage> active = packageDao.searchActive("", Double.MAX_VALUE);
        if (active.isEmpty()) {
            System.out.println("No packages are available right now.");
            return;
        }
        PackageConsole.printPackages(active);
        CateringPackage pkg = null;
        while (pkg == null) {
            int id = ConsoleUtil.readInt("Package ID (0 to cancel): ");
            if (id == 0) {
                System.out.println("Cancelled.");
                return;
            }
            pkg = active.stream().filter(p -> p.id() == id).findFirst().orElse(null);
            if (pkg == null) System.out.println("  [!] Choose an ID from the list.");
        }
        LocalDate date;
        while (true) {
            date = ConsoleUtil.readDate("Event date (yyyy-MM-dd): ", true);
            if (!bookingDao.isDateTaken(date, 0)) break;
            System.out.println("  [!] That date is already confirmed for another event. Pick another date.");
        }
        String venue = ConsoleUtil.readNonEmpty("Venue: ");
        int guests;
        while (true) {
            guests = ConsoleUtil.readInt("Guest count (min " + pkg.minGuests() + "): ");
            if (guests >= pkg.minGuests()) break;
            System.out.println("  [!] This package needs at least " + pkg.minGuests() + " guests.");
        }
        double total = guests * pkg.pricePerHead();
        System.out.println("Estimated total: PHP " + ConsoleUtil.money(total));
        if (!ConsoleUtil.confirm("Submit this booking request?")) {
            System.out.println("Cancelled.");
            return;
        }
        int id = bookingDao.create(customer.id(), pkg.id(), pkg.eventType(), date, venue, guests, total);
        audit.log(customer.id(), "CREATED_BOOKING", "bookings", id);
        System.out.println("[SUCCESS] Booking #" + id + " submitted. Status: PENDING (awaiting confirmation).");
    }

    private void myBookings() {
        ConsoleUtil.title("MY BOOKINGS");
        List<Booking> mine = bookingDao.getByCustomer(customer.id());
        BookingConsole.printBookings(mine);
        if (mine.isEmpty()) return;
        int id = ConsoleUtil.readInt("Enter a Booking ID to see its payments (0 to go back): ");
        if (id == 0) return;
        if (mine.stream().noneMatch(b -> b.id() == id)) {
            System.out.println("  [!] That booking is not yours.");
            return;
        }
        bookings.printPaymentHistory(id);
    }

    private void cancelPending() {
        ConsoleUtil.title("CANCEL PENDING BOOKING");
        List<Booking> pending = bookingDao.getByCustomer(customer.id()).stream()
                .filter(b -> b.status().equals("PENDING")).toList();
        if (pending.isEmpty()) {
            System.out.println("You have no pending bookings.");
            return;
        }
        BookingConsole.printBookings(pending);
        int id = ConsoleUtil.readInt("Booking ID to cancel (0 to go back): ");
        if (id == 0) return;
        if (pending.stream().noneMatch(b -> b.id() == id)) {
            System.out.println("  [!] Choose an ID from your pending list.");
            return;
        }
        if (ConsoleUtil.confirm("Cancel booking #" + id + "?")
                && bookingDao.cancelPendingByCustomer(id, customer.id())) {
            audit.log(customer.id(), "CANCELLED_BOOKING", "bookings", id);
            System.out.println("[SUCCESS] Booking cancelled.");
        }
    }
}

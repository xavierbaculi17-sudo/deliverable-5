package com.yourdomain.ui;

import com.yourdomain.dao.DataAccessException;
import com.yourdomain.model.User;

/** Catering Staff: confirms bookings and records payments. Cannot manage users or delete packages. */
public class StaffMenu {
    private final User staff;
    private final BookingConsole bookings;
    private final PackageConsole packages;

    public StaffMenu(User staff) {
        this.staff = staff;
        this.bookings = new BookingConsole(staff);
        this.packages = new PackageConsole(staff);
    }

    public void show() {
        boolean running = true;
        while (running) {
            System.out.println();
            System.out.println("==========================================");
            System.out.println("  STAFF MENU  -  " + staff.fullName());
            System.out.println("==========================================");
            System.out.println(" 1. Manage Bookings & Payments");
            System.out.println(" 2. Browse Catering Packages");
            System.out.println(" 0. Logout");
            int choice = ConsoleUtil.readInt("Choose an option: ");
            try {
                switch (choice) {
                    case 1 -> bookings.manageMenu(false);
                    case 2 -> packages.browse();
                    case 0 -> running = false;
                    default -> System.out.println("  [!] Invalid option.");
                }
            } catch (DataAccessException e) {
                System.out.println("  [ERROR] " + e.getMessage());
            }
        }
    }
}

package com.yourdomain.ui;

import com.yourdomain.dao.AuditLogDao;
import com.yourdomain.dao.DataAccessException;
import com.yourdomain.dao.UserDao;
import com.yourdomain.model.AuditLog;
import com.yourdomain.model.User;

import java.util.ArrayList;
import java.util.List;

/** Administrator / Caterer Owner console: full access. */
public class AdminMenu {
    private final User admin;
    private final UserDao userDao = new UserDao();
    private final AuditLogDao auditDao = new AuditLogDao();
    private final PackageConsole packages;
    private final BookingConsole bookings;

    public AdminMenu(User admin) {
        this.admin = admin;
        this.packages = new PackageConsole(admin);
        this.bookings = new BookingConsole(admin);
    }

    public void show() {
        boolean running = true;
        while (running) {
            System.out.println();
            System.out.println("==========================================");
            System.out.println("  ADMIN MENU  -  " + admin.fullName());
            System.out.println("==========================================");
            System.out.println(" 1. View All System Users");
            System.out.println(" 2. View Pending Registrations");
            System.out.println(" 3. Approve User Account");
            System.out.println(" 4. Deactivate / Reactivate User Account");
            System.out.println(" 5. Manage Catering Packages");
            System.out.println(" 6. Manage Bookings & Payments");
            System.out.println(" 7. Reports");
            System.out.println(" 8. Activity & Audit Logs");
            System.out.println(" 0. Logout");
            int choice = ConsoleUtil.readInt("Choose an option: ");
            try {
                switch (choice) {
                    case 1 -> viewAllUsers();
                    case 2 -> viewPendingRegistrations();
                    case 3 -> approveUserAccount();
                    case 4 -> toggleUserActive();
                    case 5 -> packages.manageMenu();
                    case 6 -> bookings.manageMenu(true);
                    case 7 -> new ReportConsole().menu();
                    case 8 -> logsMenu();
                    case 0 -> running = false;
                    default -> System.out.println("  [!] Invalid option.");
                }
            } catch (DataAccessException e) {
                System.out.println("  [ERROR] " + e.getMessage());
            }
        }
    }

    // ---- Required admin features -------------------------------------------------------------

    private void viewAllUsers() {
        ConsoleUtil.title("ALL SYSTEM USERS");
        List<User> users = userDao.getAllUsers();
        if (users.isEmpty()) {
            System.out.println("No users found.");
            return;
        }
        printUserTable(users);
        System.out.println("Total accounts: " + users.size());
    }

    private void viewPendingRegistrations() {
        ConsoleUtil.title("PENDING REGISTRATIONS");
        List<User> pending = userDao.getPendingUsers();
        if (pending.isEmpty()) {
            System.out.println("No accounts are awaiting approval.");
            return;
        }
        printUserTable(pending);
        System.out.println("Pending accounts: " + pending.size());
    }

    private void approveUserAccount() {
        ConsoleUtil.title("APPROVE USER ACCOUNT");
        List<User> pending = userDao.getPendingUsers();
        if (pending.isEmpty()) {
            System.out.println("No accounts are awaiting approval.");
            return;
        }
        printUserTable(pending);

        while (true) {
            int targetId = ConsoleUtil.readInt("Enter Target User ID to approve (0 to cancel): ");
            if (targetId == 0) {
                System.out.println("Approval cancelled.");
                return;
            }
            User target = pending.stream().filter(u -> u.id() == targetId).findFirst().orElse(null);
            if (target == null) {
                System.out.println("  [!] User ID " + targetId + " is not in the pending list. Try again.");
                continue;
            }
            if (userDao.approveUser(targetId)) {
                auditDao.log(admin.id(), "APPROVED_USER", "users", targetId);
                System.out.println();
                System.out.println("[SUCCESS] Account approved: ID " + target.id() + " - "
                        + target.username() + " (" + target.role() + ") is now APPROVED.");
            } else {
                System.out.println("  [ERROR] Approval failed. No record was updated.");
            }
            return;
        }
    }

    /** Soft delete of inactive accounts (status = INACTIVE) and reactivation. */
    private void toggleUserActive() {
        ConsoleUtil.title("DEACTIVATE / REACTIVATE ACCOUNT");
        printUserTable(userDao.getAllUsers());
        int id = ConsoleUtil.readInt("Enter User ID (0 to cancel): ");
        if (id == 0) {
            System.out.println("Cancelled.");
            return;
        }
        User target = userDao.getAllUsers().stream().filter(u -> u.id() == id).findFirst().orElse(null);
        if (target == null) {
            System.out.println("  [!] User ID not found.");
        } else if (target.id() == admin.id()) {
            System.out.println("  [!] You cannot deactivate your own account.");
        } else if (target.status().equals("PENDING")) {
            System.out.println("  [!] Pending accounts must be approved first (option 3).");
        } else if (target.status().equals("APPROVED")) {
            if (ConsoleUtil.confirm("Deactivate " + target.username() + "?")
                    && userDao.updateStatus(id, "INACTIVE")) {
                auditDao.log(admin.id(), "DEACTIVATED_USER", "users", id);
                System.out.println("[SUCCESS] Account deactivated.");
            }
        } else {
            if (ConsoleUtil.confirm("Reactivate " + target.username() + "?")
                    && userDao.updateStatus(id, "APPROVED")) {
                auditDao.log(admin.id(), "REACTIVATED_USER", "users", id);
                System.out.println("[SUCCESS] Account reactivated.");
            }
        }
    }

    // ---- Logs --------------------------------------------------------------------------------

    private void logsMenu() {
        System.out.println("\n 1. User Activity Log (login / logout / registration)");
        System.out.println(" 2. Booking Audit Log (bookings & payments)");
        System.out.println(" 0. Back");
        int c = ConsoleUtil.readInt("Choose an option: ");
        if (c == 1) {
            ConsoleUtil.title("USER ACTIVITY LOG (latest 50)");
            printLogs(auditDao.getUserActivity(50));
        } else if (c == 2) {
            ConsoleUtil.title("BOOKING AUDIT LOG (latest 50)");
            printLogs(auditDao.getBookingAudit(50));
        }
    }

    private void printLogs(List<AuditLog> logs) {
        if (logs.isEmpty()) {
            System.out.println("No log entries yet.");
            return;
        }
        List<String[]> rows = new ArrayList<>();
        for (AuditLog l : logs) {
            rows.add(new String[]{String.valueOf(l.id()), l.username(), l.action(), l.tableName(),
                    String.valueOf(l.recordId()), l.timestamp()});
        }
        ConsoleUtil.printTable(new String[]{"Log", "User", "Action", "Table", "Rec", "Timestamp"},
                new int[]{5, 15, 18, 9, 5, 19}, rows);
    }

    // ---- Formatted table helper (required) ---------------------------------------------------

    /**
     * Renders any list of users as an aligned, bordered table using
     * %-5s (ID), %-15s (Username), %-12s (Role) and %-10s (Status).
     */
    public static void printUserTable(List<User> users) {
        String border = "+-------+-----------------+--------------+------------+";
        String rowFormat = "| %-5s | %-15s | %-12s | %-10s |%n";

        System.out.println(border);
        System.out.printf(rowFormat, "ID", "Username", "Role", "Status");
        System.out.println(border);
        for (User u : users) {
            System.out.printf(rowFormat,
                    String.valueOf(u.id()),
                    ConsoleUtil.truncate(u.username(), 15),
                    u.role(),
                    u.status());
        }
        System.out.println(border);
    }
}

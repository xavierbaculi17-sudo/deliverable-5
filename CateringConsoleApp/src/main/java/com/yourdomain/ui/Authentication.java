package com.yourdomain.ui;

import com.yourdomain.dao.AuditLogDao;
import com.yourdomain.dao.DataAccessException;
import com.yourdomain.dao.UserDao;
import com.yourdomain.model.User;
import com.yourdomain.util.PasswordUtil;

import java.util.Optional;

/** Login, registration and role-based routing to the correct menu. */
public class Authentication {
    private final UserDao userDao = new UserDao();
    private final AuditLogDao auditDao = new AuditLogDao();

    /** Main entry loop shown after the database is ready. */
    public void start() {
        boolean running = true;
        while (running) {
            System.out.println();
            System.out.println("==========================================");
            System.out.println("  ONLINE EVENT CATERING & BOOKING SYSTEM");
            System.out.println("==========================================");
            System.out.println(" 1. Login");
            System.out.println(" 2. Register");
            System.out.println(" 0. Exit");
            int choice = ConsoleUtil.readInt("Choose an option: ");
            try {
                switch (choice) {
                    case 1 -> login();
                    case 2 -> register();
                    case 0 -> running = false;
                    default -> System.out.println("  [!] Invalid option.");
                }
            } catch (DataAccessException e) {
                System.out.println("  [ERROR] " + e.getMessage());
            }
        }
        System.out.println("Goodbye!");
    }

    private void login() {
        ConsoleUtil.title("LOGIN");
        String username = ConsoleUtil.readNonEmpty("Username: ");
        String password = ConsoleUtil.readNonEmpty("Password: ");

        Optional<User> found = userDao.findByUsername(username);
        if (found.isEmpty() || !PasswordUtil.verify(password, found.get().passwordHash())) {
            System.out.println("  [!] Invalid username or password.");
            return;
        }
        User user = found.get();
        switch (user.status()) {
            case "PENDING" -> {
                System.out.println("  [!] Your account is still awaiting administrator approval.");
                return;
            }
            case "INACTIVE" -> {
                System.out.println("  [!] Your account has been deactivated. Contact the administrator.");
                return;
            }
            default -> { /* APPROVED: continue */ }
        }

        auditDao.log(user.id(), "LOGIN", "users", user.id());
        System.out.println("Welcome, " + user.fullName() + "!");
        route(user);
        auditDao.log(user.id(), "LOGOUT", "users", user.id());
        System.out.println("You have been logged out.");
    }

    /** Sends the user to the menu that matches their role. */
    private void route(User user) {
        switch (user.role()) {
            case "ADMIN" -> new AdminMenu(user).show();
            case "STAFF" -> new StaffMenu(user).show();
            case "CUSTOMER" -> new CustomerMenu(user).show();
            default -> System.out.println("  [!] Unknown role: " + user.role());
        }
    }

    private void register() {
        ConsoleUtil.title("REGISTER NEW ACCOUNT");
        System.out.println(" 1. Customer   2. Catering Staff   0. Cancel");
        int r = ConsoleUtil.readInt("Register as: ");
        if (r == 0) return;
        String role = r == 2 ? "STAFF" : "CUSTOMER";

        String username;
        while (true) {
            username = ConsoleUtil.readNonEmpty("Username: ");
            if (userDao.findByUsername(username).isEmpty()) break;
            System.out.println("  [!] That username is already taken.");
        }
        String password;
        while (true) {
            password = ConsoleUtil.readNonEmpty("Password (min 6 chars): ");
            if (password.length() >= 6) break;
            System.out.println("  [!] Password is too short.");
        }
        String fullName = ConsoleUtil.readNonEmpty("Full name: ");
        String contact = ConsoleUtil.readNonEmpty("Contact no.: ");
        String email = ConsoleUtil.readNonEmpty("Email: ");

        int id = userDao.createUser(username, PasswordUtil.hash(password), fullName, contact, email, role, "PENDING");
        auditDao.log(id, "REGISTERED", "users", id);
        System.out.println("[SUCCESS] Registration submitted. An administrator must approve your account before you can log in.");
    }
}

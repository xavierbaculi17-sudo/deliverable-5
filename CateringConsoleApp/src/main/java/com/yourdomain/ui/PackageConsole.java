package com.yourdomain.ui;

import com.yourdomain.dao.AuditLogDao;
import com.yourdomain.dao.DataAccessException;
import com.yourdomain.dao.PackageDao;
import com.yourdomain.model.CateringPackage;
import com.yourdomain.model.User;

import java.util.ArrayList;
import java.util.List;

/** Package browsing (everyone) and package management (admin only). */
public class PackageConsole {
    private final User actor;
    private final PackageDao dao = new PackageDao();
    private final AuditLogDao audit = new AuditLogDao();

    public PackageConsole(User actor) {
        this.actor = actor;
    }

    // ---- Browse / search (READ) -------------------------------------------------------------

    public void browse() {
        ConsoleUtil.title("BROWSE CATERING PACKAGES");
        String type = ConsoleUtil.readLine("Event type filter (Enter = any): ");
        double max = Double.MAX_VALUE;
        String price = ConsoleUtil.readLine("Max price per head (Enter = any): ");
        if (!price.isEmpty()) {
            try {
                max = Double.parseDouble(price);
            } catch (NumberFormatException e) {
                System.out.println("  [!] Invalid price - ignoring the price filter.");
            }
        }
        List<CateringPackage> found = dao.searchActive(type, max);
        if (found.isEmpty()) {
            System.out.println("No packages match your search.");
            return;
        }
        printPackages(found);
        System.out.println("\nDetails:");
        for (CateringPackage p : found) {
            System.out.println("  [" + p.id() + "] " + p.name() + " - " + p.description());
        }
    }

    // ---- Admin management (CREATE / UPDATE / soft DELETE) -----------------------------------

    public void manageMenu() {
        boolean back = false;
        while (!back) {
            System.out.println("\n--- MANAGE PACKAGES ---");
            System.out.println(" 1. View all packages (incl. discontinued)");
            System.out.println(" 2. Add new package");
            System.out.println(" 3. Edit package");
            System.out.println(" 4. Discontinue package (soft delete)");
            System.out.println(" 0. Back");
            int c = ConsoleUtil.readInt("Choose an option: ");
            try {
                switch (c) {
                    case 1 -> printPackages(dao.getAll());
                    case 2 -> add();
                    case 3 -> edit();
                    case 4 -> discontinue();
                    case 0 -> back = true;
                    default -> System.out.println("  [!] Invalid option.");
                }
            } catch (DataAccessException e) {
                System.out.println("  [ERROR] " + e.getMessage());
            }
        }
    }

    private void add() {
        ConsoleUtil.title("ADD PACKAGE");
        String name = ConsoleUtil.readNonEmpty("Package name: ");
        String desc = ConsoleUtil.readNonEmpty("Description / menu items: ");
        String type = ConsoleUtil.readNonEmpty("Event type (Wedding, Birthday, ...): ");
        double price = ConsoleUtil.readPositiveDouble("Price per head: ");
        int min = (int) ConsoleUtil.readPositiveDouble("Minimum guests: ");
        int id = dao.add(name, desc, type, price, min);
        audit.log(actor.id(), "ADDED_PACKAGE", "packages", id);
        System.out.println("[SUCCESS] Package #" + id + " added.");
    }

    private void edit() {
        printPackages(dao.getAll());
        int id = ConsoleUtil.readInt("Package ID to edit (0 to cancel): ");
        if (id == 0) return;
        CateringPackage p = dao.getById(id).orElse(null);
        if (p == null) {
            System.out.println("  [!] Package not found.");
            return;
        }
        System.out.println("Press Enter to keep the current value.");
        String name = keep(ConsoleUtil.readLine("Name [" + p.name() + "]: "), p.name());
        String desc = keep(ConsoleUtil.readLine("Description [" + p.description() + "]: "), p.description());
        String type = keep(ConsoleUtil.readLine("Event type [" + p.eventType() + "]: "), p.eventType());
        double price = p.pricePerHead();
        String ps = ConsoleUtil.readLine("Price per head [" + p.pricePerHead() + "]: ");
        if (!ps.isEmpty()) {
            try {
                double v = Double.parseDouble(ps);
                if (v > 0) price = v; else System.out.println("  [!] Price must be > 0. Kept old price.");
            } catch (NumberFormatException e) {
                System.out.println("  [!] Invalid price. Kept old price.");
            }
        }
        int min = p.minGuests();
        String ms = ConsoleUtil.readLine("Min guests [" + p.minGuests() + "]: ");
        if (!ms.isEmpty()) {
            try {
                int v = Integer.parseInt(ms);
                if (v > 0) min = v; else System.out.println("  [!] Must be > 0. Kept old value.");
            } catch (NumberFormatException e) {
                System.out.println("  [!] Invalid number. Kept old value.");
            }
        }
        if (dao.update(new CateringPackage(id, name, desc, type, price, min, p.status()))) {
            audit.log(actor.id(), "EDITED_PACKAGE", "packages", id);
            System.out.println("[SUCCESS] Package updated.");
        }
    }

    private void discontinue() {
        printPackages(dao.getAll());
        int id = ConsoleUtil.readInt("Package ID to discontinue (0 to cancel): ");
        if (id == 0) return;
        if (dao.getById(id).isEmpty()) {
            System.out.println("  [!] Package not found.");
        } else if (ConsoleUtil.confirm("Discontinue package #" + id + "?") && dao.discontinue(id)) {
            audit.log(actor.id(), "DISCONTINUED_PACKAGE", "packages", id);
            System.out.println("[SUCCESS] Package discontinued (kept in database for booking history).");
        }
    }

    // ---- helpers -----------------------------------------------------------------------------

    private String keep(String input, String current) {
        return input.isEmpty() ? current : input;
    }

    public static void printPackages(List<CateringPackage> list) {
        if (list.isEmpty()) {
            System.out.println("No packages found.");
            return;
        }
        List<String[]> rows = new ArrayList<>();
        for (CateringPackage p : list) {
            rows.add(new String[]{String.valueOf(p.id()), p.name(), p.eventType(),
                    ConsoleUtil.money(p.pricePerHead()), String.valueOf(p.minGuests()), p.status()});
        }
        ConsoleUtil.printTable(new String[]{"ID", "Package", "Event Type", "Price/Head", "Min", "Status"},
                new int[]{4, 24, 12, 11, 4, 12}, rows);
    }
}

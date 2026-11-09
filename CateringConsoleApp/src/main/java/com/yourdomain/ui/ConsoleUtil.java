package com.yourdomain.ui;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Scanner;

/** Shared console input helpers and a generic table renderer. */
public final class ConsoleUtil {
    private static final Scanner IN = new Scanner(System.in);

    private ConsoleUtil() {}

    public static String readLine(String prompt) {
        System.out.print(prompt);
        if (!IN.hasNextLine()) {          // stdin closed (e.g. Ctrl+D)
            System.out.println("\nInput closed. Goodbye!");
            System.exit(0);
        }
        return IN.nextLine().trim();
    }

    public static String readNonEmpty(String prompt) {
        while (true) {
            String s = readLine(prompt);
            if (!s.isEmpty()) return s;
            System.out.println("  [!] This field cannot be empty.");
        }
    }

    public static int readInt(String prompt) {
        while (true) {
            String s = readLine(prompt);
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException e) {
                System.out.println("  [!] Please enter a whole number.");
            }
        }
    }

    public static double readPositiveDouble(String prompt) {
        while (true) {
            String s = readLine(prompt);
            try {
                double d = Double.parseDouble(s);
                if (d > 0) return d;
            } catch (NumberFormatException ignored) {
                // fall through to the message below
            }
            System.out.println("  [!] Please enter a number greater than 0.");
        }
    }

    /** Reads a yyyy-MM-dd date; if futureOnly, the date must be after today. */
    public static LocalDate readDate(String prompt, boolean futureOnly) {
        while (true) {
            String s = readLine(prompt);
            try {
                LocalDate d = LocalDate.parse(s);
                if (futureOnly && !d.isAfter(LocalDate.now())) {
                    System.out.println("  [!] The date must be in the future.");
                    continue;
                }
                return d;
            } catch (DateTimeParseException e) {
                System.out.println("  [!] Use the format yyyy-MM-dd (e.g. 2026-12-25).");
            }
        }
    }

    public static boolean confirm(String prompt) {
        while (true) {
            String s = readLine(prompt + " (Y/N): ").toLowerCase();
            if (s.equals("y") || s.equals("yes")) return true;
            if (s.equals("n") || s.equals("no")) return false;
            System.out.println("  [!] Please answer Y or N.");
        }
    }

    public static String money(double v) {
        return String.format("%,.2f", v);
    }

    public static String truncate(String s, int width) {
        if (s == null) return "";
        return s.length() <= width ? s : s.substring(0, width - 1) + "~";
    }

    public static void title(String text) {
        System.out.println();
        System.out.println("=== " + text + " ===");
    }

    /** Prints a bordered table; widths[i] is the column width for headers[i]. */
    public static void printTable(String[] headers, int[] widths, List<String[]> rows) {
        StringBuilder border = new StringBuilder("+");
        StringBuilder fmt = new StringBuilder("|");
        for (int w : widths) {
            border.append("-".repeat(w + 2)).append("+");
            fmt.append(" %-").append(w).append("s |");
        }
        fmt.append("%n");
        String format = fmt.toString();

        System.out.println(border);
        System.out.printf(format, (Object[]) headers);
        System.out.println(border);
        for (String[] row : rows) {
            Object[] cells = new Object[row.length];
            for (int i = 0; i < row.length; i++) cells[i] = truncate(row[i], widths[i]);
            System.out.printf(format, cells);
        }
        System.out.println(border);
    }
}

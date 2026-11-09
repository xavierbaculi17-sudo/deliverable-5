package com.yourdomain.ui;

import com.yourdomain.dao.BookingDao;
import com.yourdomain.model.Booking;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

/** Summary / analytical reports required by the proposal. */
public class ReportConsole {
    private final BookingDao dao = new BookingDao();

    public void menu() {
        System.out.println("\n 1. Monthly Bookings & Income Report");
        System.out.println(" 2. Upcoming Events & Unpaid Balance Report");
        System.out.println(" 0. Back");
        int c = ConsoleUtil.readInt("Choose an option: ");
        if (c == 1) monthly();
        else if (c == 2) upcomingAndUnpaid();
    }

    private void monthly() {
        String input = ConsoleUtil.readLine("Month (yyyy-MM, Enter = this month): ");
        YearMonth ym;
        try {
            ym = input.isEmpty() ? YearMonth.now() : YearMonth.parse(input);
        } catch (DateTimeParseException e) {
            System.out.println("  [!] Use the format yyyy-MM (e.g. 2026-12).");
            return;
        }
        String key = ym.toString();
        ConsoleUtil.title("MONTHLY BOOKINGS & INCOME REPORT - " + key);
        Map<String, Integer> counts = dao.countByStatusForMonth(key);
        int total = 0;
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            System.out.printf("  %-12s : %d%n", e.getKey(), e.getValue());
            total += e.getValue();
        }
        System.out.printf("  %-12s : %d%n", "TOTAL", total);
        System.out.println("  ------------------------------");
        System.out.printf("  Total sales (non-cancelled events this month) : PHP %s%n",
                ConsoleUtil.money(dao.totalSalesForMonth(key)));
        System.out.printf("  Payments collected this month                 : PHP %s%n",
                ConsoleUtil.money(dao.paymentsCollectedForMonth(key)));
    }

    private void upcomingAndUnpaid() {
        LocalDate today = LocalDate.now();
        ConsoleUtil.title("UPCOMING CONFIRMED EVENTS (next 30 days)");
        BookingConsole.printBookings(dao.getConfirmedBetween(today, today.plusDays(30)));

        ConsoleUtil.title("BOOKINGS WITH UNPAID BALANCE");
        List<Booking> due = dao.getWithBalance();
        if (due.isEmpty()) {
            System.out.println("No outstanding balances.");
            return;
        }
        BookingConsole.printBookings(due);
        double sum = due.stream().mapToDouble(Booking::balance).sum();
        System.out.println("Total outstanding balance: PHP " + ConsoleUtil.money(sum));
    }
}

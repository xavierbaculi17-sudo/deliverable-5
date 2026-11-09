package com.yourdomain.model;

/** Row of the booking_details view (bookings joined with customer, package and amount paid). */
public record Booking(int id, int customerId, String customerName, int packageId, String packageName,
                      String eventType, String eventDate, String venue, int guestCount,
                      double totalAmount, String status, String dateCreated, double paidAmount) {

    public double balance() {
        return totalAmount - paidAmount;
    }
}

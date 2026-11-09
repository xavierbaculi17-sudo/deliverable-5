package com.yourdomain.model;

/** Row of the payments table (with the receiving user's name). */
public record Payment(int id, int bookingId, double amount, String method, String type,
                      String paymentDate, String receivedBy) {
}

package com.yourdomain.model;

/** Row of the packages table. */
public record CateringPackage(int id, String name, String description, String eventType,
                              double pricePerHead, int minGuests, String status) {
}

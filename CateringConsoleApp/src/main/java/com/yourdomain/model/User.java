package com.yourdomain.model;

/** Row of the users table (immutable Java Record). */
public record User(int id, String username, String passwordHash, String fullName,
                   String contactNo, String email, String role, String status) {
}

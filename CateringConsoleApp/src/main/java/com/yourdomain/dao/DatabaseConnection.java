package com.yourdomain.dao;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/** Single place that opens SQLite connections. Foreign keys are enforced on every connection. */
public final class DatabaseConnection {
    private static final String URL = "jdbc:sqlite:catering.db";

    private DatabaseConnection() {}

    public static Connection getConnection() throws SQLException {
        Connection conn = DriverManager.getConnection(URL);
        // SQLite ignores FOREIGN KEY constraints unless this PRAGMA is set per connection.
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON");
        }
        return conn;
    }
}

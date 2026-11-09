package com.yourdomain;

import com.yourdomain.dao.DataAccessException;
import com.yourdomain.dao.DatabaseInitializer;
import com.yourdomain.ui.Authentication;

/**
 * Single entry point of the application.
 * The database is created / verified first; only then is the login screen shown.
 */
public class Main {
    public static void main(String[] args) {
        try {
            DatabaseInitializer.initialize();
        } catch (DataAccessException e) {
            System.err.println("Cannot start: " + e.getMessage());
            return;
        }
        new Authentication().start();
    }
}

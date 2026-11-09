package com.yourdomain.dao;

/** Unchecked wrapper so UI code does not need to handle SQLException everywhere. */
public class DataAccessException extends RuntimeException {
    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}

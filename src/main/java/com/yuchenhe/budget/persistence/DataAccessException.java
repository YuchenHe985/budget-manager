package com.yuchenhe.budget.persistence;

/** A database operation failed; the message is safe to show to the user. */
public class DataAccessException extends RuntimeException {
  public DataAccessException(String message, Throwable cause) {
    super(message, cause);
  }

  public DataAccessException(String message) {
    super(message);
  }
}

package com.yuchenhe.budget.domain;

import java.time.LocalDate;
import java.util.Objects;

/**
 * One ledger entry. {@code categoryId} is required for expenses and refunds and must be null for
 * income.
 */
public record Transaction(
    long id, LocalDate date, Kind kind, Money amount, Long categoryId, String description) {
  public static final int MAX_DESCRIPTION = 200;

  public Transaction {
    Objects.requireNonNull(date, "date");
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(amount, "amount");
    if (!amount.isPositive()) {
      throw new IllegalArgumentException("amount must be greater than zero");
    }
    if (kind == Kind.INCOME && categoryId != null) {
      throw new IllegalArgumentException("income has no category");
    }
    if (kind != Kind.INCOME && categoryId == null) {
      throw new IllegalArgumentException("a category is required for " + kind.name().toLowerCase());
    }
    description = description == null ? "" : description.strip();
    if (description.length() > MAX_DESCRIPTION) {
      throw new IllegalArgumentException(
          "description longer than " + MAX_DESCRIPTION + " characters");
    }
  }

  public Transaction withId(long newId) {
    return new Transaction(newId, date, kind, amount, categoryId, description);
  }

  /** Signed effect on its category's spending: expense +, refund -, income 0. */
  public Money spending() {
    return switch (kind) {
      case EXPENSE -> amount;
      case REFUND -> amount.negate();
      case INCOME -> Money.ZERO;
    };
  }
}

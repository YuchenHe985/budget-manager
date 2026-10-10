package com.yuchenhe.budget.ui;

import com.yuchenhe.budget.domain.Category;
import com.yuchenhe.budget.domain.Kind;
import com.yuchenhe.budget.domain.Money;
import com.yuchenhe.budget.domain.Transaction;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Turns the raw text of the entry form into a transaction, or into one message per bad field so the
 * screen can mark each field. Kept free of JavaFX so it can be unit tested.
 */
public final class TransactionForm {
  public enum Field {
    DATE,
    KIND,
    AMOUNT,
    CATEGORY,
    DESCRIPTION
  }

  /** Exactly one of {@code transaction} and a non-empty {@code errors} is set. */
  public record Result(Transaction transaction, Map<Field, String> errors) {
    public boolean ok() {
      return transaction != null;
    }
  }

  private TransactionForm() {}

  public static Result read(
      long id, LocalDate date, Kind kind, String amount, Category category, String description) {
    Map<Field, String> errors = new LinkedHashMap<>();
    if (date == null) {
      errors.put(Field.DATE, "pick a date");
    }
    if (kind == null) {
      errors.put(Field.KIND, "choose expense, refund or income");
    }
    Money money = null;
    try {
      money = Money.parse(amount);
      if (!money.isPositive()) {
        errors.put(Field.AMOUNT, "enter an amount greater than zero");
      }
    } catch (IllegalArgumentException e) {
      errors.put(Field.AMOUNT, e.getMessage());
    }
    if (kind != null && kind != Kind.INCOME && category == null) {
      errors.put(Field.CATEGORY, "choose a category");
    }
    String text = description == null ? "" : description.strip();
    if (text.length() > Transaction.MAX_DESCRIPTION) {
      errors.put(Field.DESCRIPTION, "at most " + Transaction.MAX_DESCRIPTION + " characters");
    }
    if (!errors.isEmpty()) {
      return new Result(null, Map.copyOf(errors));
    }
    Long categoryId = kind == Kind.INCOME ? null : category.id();
    return new Result(new Transaction(id, date, kind, money, categoryId, text), Map.of());
  }
}

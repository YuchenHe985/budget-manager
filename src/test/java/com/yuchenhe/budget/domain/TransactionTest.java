package com.yuchenhe.budget.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class TransactionTest {
  private static final LocalDate D = LocalDate.of(2026, 3, 15);

  @Test
  void kindGivesTheDirectionOfSpending() {
    assertEquals(
        Money.parse("5"), new Transaction(0, D, Kind.EXPENSE, Money.parse("5"), 1L, "").spending());
    assertEquals(
        Money.parse("-5"), new Transaction(0, D, Kind.REFUND, Money.parse("5"), 1L, "").spending());
    assertEquals(
        Money.ZERO, new Transaction(0, D, Kind.INCOME, Money.parse("5"), null, "").spending());
  }

  @Test
  void invalidCombinationsAreRejected() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new Transaction(0, D, Kind.EXPENSE, Money.parse("-5"), 1L, ""));
    assertThrows(
        IllegalArgumentException.class,
        () -> new Transaction(0, D, Kind.EXPENSE, Money.ZERO, 1L, ""));
    assertThrows(
        IllegalArgumentException.class,
        () -> new Transaction(0, D, Kind.EXPENSE, Money.parse("5"), null, ""));
    assertThrows(
        IllegalArgumentException.class,
        () -> new Transaction(0, D, Kind.INCOME, Money.parse("5"), 1L, ""));
    assertThrows(
        IllegalArgumentException.class,
        () -> new Transaction(0, D, Kind.EXPENSE, Money.parse("5"), 1L, "x".repeat(201)));
  }

  @Test
  void descriptionIsTrimmedAndNullBecomesEmpty() {
    assertEquals(
        "rent", new Transaction(0, D, Kind.EXPENSE, Money.parse("5"), 1L, "  rent ").description());
    assertEquals("", new Transaction(0, D, Kind.EXPENSE, Money.parse("5"), 1L, null).description());
  }
}

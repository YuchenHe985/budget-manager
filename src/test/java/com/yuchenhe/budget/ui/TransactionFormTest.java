package com.yuchenhe.budget.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yuchenhe.budget.domain.Category;
import com.yuchenhe.budget.domain.Group;
import com.yuchenhe.budget.domain.Kind;
import com.yuchenhe.budget.domain.Money;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TransactionFormTest {
  static final Category FOOD = new Category(2, "Food", Group.NEEDS);
  static final LocalDate D = LocalDate.of(2026, 2, 1);

  @Test
  void validInputBecomesATransaction() {
    var r = TransactionForm.read(0, D, Kind.EXPENSE, "1,250.5", FOOD, "  groceries ");
    assertTrue(r.ok());
    assertEquals(Money.parse("1250.50"), r.transaction().amount());
    assertEquals("groceries", r.transaction().description());
  }

  @Test
  void everyBadFieldGetsItsOwnMessageInsteadOfACrash() {
    var r = TransactionForm.read(0, null, Kind.EXPENSE, "abc", null, "x".repeat(300));
    assertEquals(
        Set.of(
            TransactionForm.Field.DATE,
            TransactionForm.Field.AMOUNT,
            TransactionForm.Field.CATEGORY,
            TransactionForm.Field.DESCRIPTION),
        r.errors().keySet());
    assertEquals(
        Set.of(TransactionForm.Field.AMOUNT),
        TransactionForm.read(0, D, Kind.EXPENSE, "0", FOOD, "").errors().keySet());
  }

  @Test
  void incomeIgnoresAStaleCategoryChoice() {
    var r = TransactionForm.read(0, D, Kind.INCOME, "3000", FOOD, "");
    assertTrue(r.ok());
    assertNull(r.transaction().categoryId());
  }
}

package com.yuchenhe.budget.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yuchenhe.budget.TestDb;
import com.yuchenhe.budget.domain.AllocationRule;
import com.yuchenhe.budget.domain.Category;
import com.yuchenhe.budget.domain.Group;
import com.yuchenhe.budget.domain.Kind;
import com.yuchenhe.budget.domain.Money;
import com.yuchenhe.budget.domain.Transaction;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RepositoriesTest {
  @TempDir Path dir;
  Database db;
  CategoryRepository cats;
  TransactionRepository txns;
  BudgetRepository budgets;
  long food;

  @BeforeEach
  void open() {
    db = TestDb.open(dir);
    cats = new CategoryRepository(db);
    txns = new TransactionRepository(db);
    budgets = new BudgetRepository(db);
    food = TestDb.category(db, "Food").id();
  }

  @AfterEach
  void close() {
    db.close();
  }

  private Transaction expense(String date, String amount) {
    return new Transaction(0, LocalDate.parse(date), Kind.EXPENSE, Money.parse(amount), food, "");
  }

  @Test
  void dataSurvivesClosingAndReopeningTheApp() {
    txns.insert(expense("2026-02-03", "12.34"));
    db.close();
    db = TestDb.open(dir);
    txns = new TransactionRepository(db);
    assertEquals(Money.parse("12.34"), txns.findAll().get(0).amount());
  }

  @Test
  void monthQueriesIncludeBothEndsAndNothingOutside() {
    txns.insert(expense("2026-01-31", "1"));
    txns.insert(expense("2026-02-01", "2"));
    txns.insert(expense("2026-02-28", "3"));
    txns.insert(expense("2026-03-01", "4"));
    assertEquals(2, txns.findByMonth(YearMonth.of(2026, 2)).size());
    txns.insert(expense("2028-02-29", "5")); // leap day
    assertEquals(1, txns.findByMonth(YearMonth.of(2028, 2)).size());
  }

  @Test
  void updateAndDeleteChangeExactlyOneRow() {
    Transaction t = txns.insert(expense("2026-02-03", "1"));
    txns.insert(expense("2026-02-04", "2"));
    txns.update(new Transaction(t.id(), t.date(), Kind.REFUND, Money.parse("9"), food, "back"));
    assertEquals(Kind.REFUND, txns.findById(t.id()).orElseThrow().kind());
    assertTrue(txns.delete(t.id()));
    assertFalse(txns.delete(t.id()));
    assertEquals(1, txns.findAll().size());
    assertThrows(DataAccessException.class, () -> txns.update(t));
  }

  @Test
  void incomeIsStoredWithoutACategory() {
    Transaction t =
        txns.insert(
            new Transaction(
                0, LocalDate.of(2026, 2, 1), Kind.INCOME, Money.parse("3000"), null, "pay"));
    assertNull(txns.findById(t.id()).orElseThrow().categoryId());
  }

  @Test
  void aCategoryInUseCannotBeDeletedAndNamesAreUniqueIgnoringCase() {
    txns.insert(expense("2026-02-03", "1"));
    DataAccessException e = assertThrows(DataAccessException.class, () -> cats.delete(food));
    assertTrue(e.getMessage().contains("still has transactions"));
    assertThrows(
        DataAccessException.class, () -> cats.insert(new Category(0, "FOOD", Group.NEEDS)));
    Category gym = cats.insert(new Category(0, "  Gym   membership ", Group.WANTS));
    assertEquals("Gym membership", gym.name());
    cats.delete(gym.id());
    assertTrue(cats.findById(gym.id()).isEmpty());
  }

  @Test
  void theDatabaseItselfRejectsBadRowsEvenIfCodeIsBypassed() throws Exception {
    try (var s = db.connection().createStatement()) {
      assertThrows(
          java.sql.SQLException.class,
          () ->
              s.execute(
                  "INSERT INTO txn (date, kind, amount_cents, category_id) VALUES ('2026-02-01', 'EXPENSE', -5, 1)"));
      assertThrows(
          java.sql.SQLException.class,
          () ->
              s.execute(
                  "INSERT INTO txn (date, kind, amount_cents, category_id) VALUES ('2026-02-01', 'INCOME', 5, 1)"));
      assertThrows(
          java.sql.SQLException.class,
          () ->
              s.execute(
                  "INSERT INTO txn (date, kind, amount_cents, category_id) VALUES ('2026-02-01', 'EXPENSE', 5, 999)"));
      assertThrows(
          java.sql.SQLException.class,
          () ->
              s.execute(
                  "INSERT INTO txn (date, kind, amount_cents, category_id) VALUES ('Feb 1', 'EXPENSE', 5, 1)"));
    }
  }

  @Test
  void limitsAndTheAllocationRuleAreStored() {
    YearMonth m = YearMonth.of(2026, 2);
    budgets.setLimit(m, food, Money.parse("400"));
    budgets.setLimit(m, food, Money.parse("450"));
    assertEquals(Money.parse("450"), budgets.limits(m).get(food));
    budgets.setLimit(m, food, null);
    assertTrue(budgets.limits(m).isEmpty());
    assertEquals(AllocationRule.DEFAULT, budgets.allocationRule());
    budgets.saveAllocationRule(new AllocationRule(60, 20, 20));
    assertEquals(new AllocationRule(60, 20, 20), budgets.allocationRule());
  }

  @Test
  void aFailedTransactionWritesNothing() {
    assertThrows(
        IllegalStateException.class,
        () ->
            db.inTransaction(
                c -> {
                  txns.insert(expense("2026-02-03", "1"));
                  throw new IllegalStateException("boom");
                }));
    assertTrue(txns.findAll().isEmpty());
  }
}

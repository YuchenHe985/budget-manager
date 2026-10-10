package com.yuchenhe.budget.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yuchenhe.budget.TestDb;
import com.yuchenhe.budget.domain.Group;
import com.yuchenhe.budget.domain.Kind;
import com.yuchenhe.budget.domain.Money;
import com.yuchenhe.budget.domain.Transaction;
import com.yuchenhe.budget.persistence.BudgetRepository;
import com.yuchenhe.budget.persistence.DataAccessException;
import com.yuchenhe.budget.persistence.Database;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ServicesTest {
  static final YearMonth FEB = YearMonth.of(2026, 2);
  @TempDir Path dir;
  Database db;
  LedgerService ledger;
  long food;
  long housing;
  long fun;

  @BeforeEach
  void open() {
    db = TestDb.open(dir);
    ledger = new LedgerService(db);
    food = TestDb.category(db, "Food").id();
    housing = TestDb.category(db, "Housing").id();
    fun = TestDb.category(db, "Entertainment").id();
  }

  @AfterEach
  void close() {
    db.close();
  }

  private void add(String date, Kind kind, String amount, Long cat) {
    ledger.record(new Transaction(0, LocalDate.parse(date), kind, Money.parse(amount), cat, ""));
  }

  @Test
  void theMonthlySummaryNetsRefundsAndComparesToThePlan() {
    add("2026-02-01", Kind.INCOME, "3000.01", null);
    add("2026-02-02", Kind.EXPENSE, "1200", housing);
    add("2026-02-05", Kind.EXPENSE, "80.10", food);
    add("2026-02-06", Kind.REFUND, "20.05", food);
    add("2026-02-07", Kind.EXPENSE, "1000", fun);
    add("2026-03-01", Kind.EXPENSE, "999", food); // other month
    new BudgetRepository(db).setLimit(FEB, fun, Money.parse("500"));

    MonthlySummary s = new SummaryService(db).summarize(FEB);
    assertEquals(Money.parse("3000.01"), s.income());
    assertEquals(Money.parse("2260.05"), s.spent());
    assertEquals(Money.parse("739.96"), s.balance());
    assertEquals(Money.parse("1260.05"), s.spentByGroup().get(Group.NEEDS));
    assertEquals(Money.parse("1500.01"), s.plan().get(Group.NEEDS));
    var funLine =
        s.lines().stream().filter(l -> l.category().id() == fun).findFirst().orElseThrow();
    assertTrue(funLine.over());
    assertEquals(Money.parse("-500"), funLine.remaining());
    var foodLine =
        s.lines().stream().filter(l -> l.category().id() == food).findFirst().orElseThrow();
    assertEquals(Money.parse("60.05"), foodLine.spent());
    assertNull(foodLine.remaining());
  }

  @Test
  void typosFarInTheFutureAndMissingCategoriesAreRefused() {
    assertThrows(IllegalArgumentException.class, () -> add("2062-02-01", Kind.EXPENSE, "1", food));
    assertThrows(IllegalArgumentException.class, () -> add("2026-02-01", Kind.EXPENSE, "1", 999L));
  }

  @Test
  void mergingMovesEveryTransactionThenRemovesTheCategory() {
    var coffee = ledger.addCategory("Coffee", Group.WANTS);
    add("2026-02-01", Kind.EXPENSE, "4.5", coffee.id());
    add("2026-02-02", Kind.EXPENSE, "5.5", coffee.id());
    assertEquals(2, ledger.mergeCategory(coffee.id(), food));
    assertTrue(ledger.categories().stream().noneMatch(c -> c.name().equals("Coffee")));
    assertEquals(
        Money.parse("10"), new SummaryService(db).summarize(FEB).spentByGroup().get(Group.NEEDS));
  }

  // ---- CSV ----

  @Test
  void importReportsEveryBadRowByLineAndWritesNothing() {
    CsvService csv = new CsvService(db);
    String file =
        String.join(
            "\n",
            "date,kind,amount,category,description",
            "2026-02-01,expense,12.50,Food,lunch",
            "2026-13-01,expense,1,Food,",
            "2026-02-02,spend,1,Food,",
            "2026-02-03,expense,1.005,Food,",
            "2026-02-04,expense,5,Pets,",
            "2026-02-05,income,5,Food,",
            "2026-02-06,expense,5");
    CsvService.Preview p = csv.preview(file);
    assertEquals(6, p.errorCount());
    assertEquals(
        java.util.List.of(3, 4, 5, 6, 7, 8),
        p.rows().stream().filter(r -> !r.ok()).map(CsvService.Row::line).toList());
    assertThrows(IllegalStateException.class, () -> csv.commit(p, CsvService.DuplicatePolicy.SKIP));
    assertTrue(ledger.between(LocalDate.MIN, LocalDate.MAX).isEmpty());
  }

  @Test
  void anImportThatFailsPartWayIsRolledBackCompletely() throws Exception {
    CsvService csv = new CsvService(db);
    CsvService.Preview p =
        csv.preview(
            "date,kind,amount,category,description\n2026-02-01,expense,1,Food,a\n2026-02-02,expense,2,Food,b\n");
    // the category disappears between preview and commit: the second insert fails on the foreign
    // key
    try (var s = db.connection().createStatement()) {
      s.execute("PRAGMA foreign_keys = OFF");
      s.execute("UPDATE category SET id = 777 WHERE name = 'Food'");
      s.execute("PRAGMA foreign_keys = ON");
    }
    var bad =
        new CsvService.Preview(
            java.util.List.of(
                p.rows().get(0),
                new CsvService.Row(
                    3,
                    new Transaction(
                        0, LocalDate.of(2026, 2, 2), Kind.EXPENSE, Money.parse("2"), food, "b"),
                    java.util.List.of(),
                    false)));
    assertThrows(
        DataAccessException.class, () -> csv.commit(bad, CsvService.DuplicatePolicy.IMPORT));
    assertTrue(
        ledger.between(LocalDate.MIN, LocalDate.MAX).isEmpty(), "first row must be rolled back");
  }

  @Test
  void duplicatesInTheFileOrAlreadyStoredAreSkippedOnRequest() {
    add("2026-02-01", Kind.EXPENSE, "12.5", food);
    CsvService csv = new CsvService(db);
    String file =
        "date,kind,amount,category,description\n"
            + "2026-02-01,expense,12.50,food,\n"
            + "2026-02-02,expense,3,Food,\"coffee, large\"\n"
            + "2026-02-02,expense,3,Food,\"coffee, large\"\n";
    CsvService.Preview p = csv.preview(file);
    assertEquals(0, p.errorCount());
    assertEquals(2, p.duplicateCount());
    assertEquals(new CsvService.Result(1, 2), csv.commit(p, CsvService.DuplicatePolicy.SKIP));
    assertEquals(
        new CsvService.Result(3, 0),
        csv.commit(csv.preview(file), CsvService.DuplicatePolicy.IMPORT));
  }

  @Test
  void exportThenImportGivesBackTheSameLedger() {
    add("2026-02-01", Kind.INCOME, "3000", null);
    ledger.record(
        new Transaction(
            0,
            LocalDate.of(2026, 2, 3),
            Kind.EXPENSE,
            Money.parse("7.05"),
            food,
            "tea, \"oolong\"\nsecond line"));
    add("2026-02-04", Kind.REFUND, "1.10", food);
    CsvService csv = new CsvService(db);
    String out = csv.export(FEB.atDay(1), FEB.atEndOfMonth());
    db.close();
    db = Database.open(dir.resolve("other.db"));
    ledger = new LedgerService(db);
    CsvService other = new CsvService(db);
    CsvService.Preview p = other.preview("\uFEFF" + out.replace("\n", "\r\n"));
    assertEquals(0, p.errorCount(), p.rows().toString());
    other.commit(p, CsvService.DuplicatePolicy.SKIP);
    assertEquals(out, other.export(FEB.atDay(1), FEB.atEndOfMonth()));
  }

  @Test
  void aWrongHeaderOrAnEmptyFileIsExplained() {
    CsvService csv = new CsvService(db);
    assertTrue(csv.preview("").rows().get(0).errors().get(0).contains("empty"));
    assertTrue(csv.preview("when,what\n").rows().get(0).errors().get(0).contains("first line"));
    assertTrue(
        csv.preview("date,kind,amount,category,description\n\"open")
            .rows()
            .get(0)
            .errors()
            .get(0)
            .contains("never closed"));
  }

  // ---- backup ----

  @Test
  void aBackupRestoresTheDataItWasTakenFrom() throws Exception {
    add("2026-02-01", Kind.EXPENSE, "10", food);
    Path backup = dir.resolve("backup.db");
    BackupService b = new BackupService(db);
    b.backup(backup);
    add("2026-02-02", Kind.EXPENSE, "99", food);
    b.restore(backup);
    var all = ledger.between(LocalDate.MIN, LocalDate.MAX);
    assertEquals(1, all.size());
    assertEquals(Money.parse("10"), all.get(0).amount());
  }

  @Test
  void aDamagedOrForeignFileIsNeverRestored() throws Exception {
    add("2026-02-01", Kind.EXPENSE, "10", food);
    Path junk = Files.writeString(dir.resolve("junk.db"), "not a database");
    BackupService b = new BackupService(db);
    assertThrows(DataAccessException.class, () -> b.restore(junk));
    assertThrows(DataAccessException.class, () -> b.restore(dir.resolve("missing.db")));
    Path empty = dir.resolve("empty.db");
    try (var c = java.sql.DriverManager.getConnection("jdbc:sqlite:" + empty);
        var s = c.createStatement()) {
      s.execute("CREATE TABLE other (x)");
    }
    assertThrows(DataAccessException.class, () -> b.restore(empty));
    assertEquals(1, ledger.between(LocalDate.MIN, LocalDate.MAX).size(), "live data untouched");
  }
}

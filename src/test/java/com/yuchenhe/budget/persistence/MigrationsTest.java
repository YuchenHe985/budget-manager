package com.yuchenhe.budget.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MigrationsTest {
  @TempDir Path dir;

  @Test
  void aNewDatabaseIsBroughtToTheLatestVersionOnce() throws Exception {
    try (Database db = Database.open(dir.resolve("a.db"))) {
      assertEquals(Migrations.LATEST, Migrations.currentVersion(db.connection()));
      assertEquals(0, Migrations.migrate(db.connection()));
    }
  }

  @Test
  void upgradingAnOlderDatabaseKeepsItsRecords() throws Exception {
    Path file = dir.resolve("old.db");
    try (Connection c = Database.connect(file);
        Statement s = c.createStatement()) {
      Migrations.migrate(c, 1); // the schema of the first release
      s.execute(
          "INSERT INTO txn (date, kind, amount_cents, category_id, description)"
              + " VALUES ('2025-12-01', 'EXPENSE', 123456, 1, 'rent')");
    }
    try (Database db = Database.open(file);
        Statement s = db.connection().createStatement()) {
      assertEquals(Migrations.LATEST, Migrations.currentVersion(db.connection()));
      try (ResultSet r = s.executeQuery("SELECT amount_cents, description FROM txn")) {
        assertTrue(r.next());
        assertEquals(123456, r.getLong(1));
        assertEquals("rent", r.getString(2));
      }
      try (ResultSet r =
          s.executeQuery("SELECT value FROM setting WHERE key = 'allocation.needs'")) {
        assertEquals("50", r.getString(1));
      }
    }
  }

  @Test
  void aFailingMigrationLeavesThePreviousVersionIntact() throws Exception {
    Path file = dir.resolve("broken.db");
    try (Connection c = Database.connect(file);
        Statement s = c.createStatement()) {
      Migrations.migrate(c, 1);
      s.execute("CREATE TABLE setting (key TEXT)"); // V2 will now fail half-way
      assertThrows(DataAccessException.class, () -> Migrations.migrate(c));
      assertEquals(1, Migrations.currentVersion(c));
      try (ResultSet r =
          s.executeQuery("SELECT count(*) FROM sqlite_master WHERE name = 'monthly_budget'")) {
        assertEquals(0, r.getInt(1), "V2's first table must be rolled back too");
      }
    }
  }

  @Test
  void aDatabaseFromANewerAppIsRefused() throws Exception {
    Path file = dir.resolve("future.db");
    try (Connection c = Database.connect(file);
        Statement s = c.createStatement()) {
      Migrations.migrate(c);
      s.execute("INSERT INTO schema_version VALUES (99, 'V99__future.sql', 'x')");
    }
    assertThrows(DataAccessException.class, () -> Database.open(file));
  }

  @Test
  void scriptsSplitIntoStatementsWithoutComments() {
    assertEquals(
        java.util.List.of("CREATE TABLE a (x)", "INSERT INTO a VALUES (1),\n(2)"),
        Migrations.statements("-- note\nCREATE TABLE a (x);\nINSERT INTO a VALUES (1),\n(2);\n"));
  }
}

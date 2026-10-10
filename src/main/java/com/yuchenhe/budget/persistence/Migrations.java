package com.yuchenhe.budget.persistence;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Versioned schema migrations. Each script runs in its own transaction together with its row in
 * {@code schema_version}, so a failing script leaves the database exactly at the previous version.
 * Scripts are append-only: a released script is never edited, a change is a new version.
 */
public final class Migrations {
  private static final Logger LOG = Logger.getLogger(Migrations.class.getName());

  /** In order; the number in the name is the version. */
  static final List<String> SCRIPTS =
      List.of("V1__initial_schema.sql", "V2__monthly_budgets_and_settings.sql");

  public static final int LATEST = SCRIPTS.size();

  private Migrations() {}

  /** Brings the database to {@link #LATEST}. Returns the number of scripts applied. */
  public static int migrate(Connection c) {
    return migrate(c, LATEST);
  }

  /** Brings the database to {@code target} (tests use this to build an older schema). */
  public static int migrate(Connection c, int target) {
    try {
      try (Statement s = c.createStatement()) {
        s.execute(
            "CREATE TABLE IF NOT EXISTS schema_version (version INTEGER PRIMARY KEY,"
                + " name TEXT NOT NULL, applied_at TEXT NOT NULL)");
      }
      int current = currentVersion(c);
      if (current > LATEST) {
        throw new DataAccessException(
            "this database was written by a newer version of the app (schema "
                + current
                + "); please update the app");
      }
      int applied = 0;
      for (int v = current + 1; v <= target; v++) {
        apply(c, v, SCRIPTS.get(v - 1));
        applied++;
      }
      return applied;
    } catch (SQLException e) {
      throw new DataAccessException("could not prepare the database: " + e.getMessage(), e);
    }
  }

  public static int currentVersion(Connection c) throws SQLException {
    try (Statement s = c.createStatement();
        ResultSet r = s.executeQuery("SELECT COALESCE(MAX(version), 0) FROM schema_version")) {
      return r.getInt(1);
    }
  }

  private static void apply(Connection c, int version, String name) throws SQLException {
    String sql = read(name);
    boolean auto = c.getAutoCommit();
    c.setAutoCommit(false);
    try (Statement s = c.createStatement()) {
      for (String stmt : statements(sql)) {
        s.execute(stmt);
      }
      try (PreparedStatement p =
          c.prepareStatement(
              "INSERT INTO schema_version (version, name, applied_at) VALUES (?, ?, ?)")) {
        p.setInt(1, version);
        p.setString(2, name);
        p.setString(3, Instant.now().toString());
        p.executeUpdate();
      }
      c.commit();
      LOG.info(() -> "applied migration " + name);
    } catch (SQLException | RuntimeException e) {
      c.rollback();
      throw new DataAccessException("migration " + name + " failed: " + e.getMessage(), e);
    } finally {
      c.setAutoCommit(auto);
    }
  }

  private static String read(String name) {
    try (InputStream in = Migrations.class.getResourceAsStream("/db/migration/" + name)) {
      if (in == null) {
        throw new DataAccessException("missing migration script " + name);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new DataAccessException("cannot read migration script " + name, e);
    }
  }

  /** Splits a script on semicolons at line ends; comment lines are dropped. */
  static List<String> statements(String sql) {
    List<String> out = new ArrayList<>();
    StringBuilder cur = new StringBuilder();
    for (String line : sql.split("\n")) {
      if (line.strip().startsWith("--")) {
        continue;
      }
      cur.append(line).append('\n');
      if (line.strip().endsWith(";")) {
        String stmt = cur.toString().strip();
        out.add(stmt.substring(0, stmt.length() - 1));
        cur.setLength(0);
      }
    }
    if (!cur.toString().isBlank()) {
      out.add(cur.toString().strip());
    }
    return out;
  }
}

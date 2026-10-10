package com.yuchenhe.budget.persistence;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * The single SQLite connection of the desktop app. Opening it turns on foreign keys and WAL
 * journaling and migrates the schema to the latest version.
 */
public final class Database implements AutoCloseable {
  /** Work that runs inside one transaction. */
  @FunctionalInterface
  public interface Work<T> {
    T run(Connection c) throws SQLException;
  }

  private final Path file;
  private Connection connection;

  private Database(Path file, Connection connection) {
    this.file = file;
    this.connection = connection;
  }

  public static Database open(Path file) {
    try {
      Path parent = file.toAbsolutePath().getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      Database db = new Database(file, connect(file));
      Migrations.migrate(db.connection);
      return db;
    } catch (java.io.IOException e) {
      throw new DataAccessException("cannot create the data folder for " + file, e);
    }
  }

  static Connection connect(Path file) {
    try {
      Connection c = DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath());
      try (Statement s = c.createStatement()) {
        s.execute("PRAGMA foreign_keys = ON");
        s.execute("PRAGMA journal_mode = WAL");
        s.execute("PRAGMA busy_timeout = 5000");
      }
      return c;
    } catch (SQLException e) {
      throw new DataAccessException("cannot open the database " + file + ": " + e.getMessage(), e);
    }
  }

  public Path file() {
    return file;
  }

  public Connection connection() {
    return connection;
  }

  /** Runs {@code work} atomically: everything it writes is committed together or not at all. */
  public <T> T inTransaction(Work<T> work) {
    try {
      boolean auto = connection.getAutoCommit();
      connection.setAutoCommit(false);
      try {
        T result = work.run(connection);
        connection.commit();
        return result;
      } catch (SQLException | RuntimeException e) {
        connection.rollback();
        if (e instanceof RuntimeException re) {
          throw re;
        }
        throw new DataAccessException(e.getMessage(), e);
      } finally {
        connection.setAutoCommit(auto);
      }
    } catch (SQLException e) {
      throw new DataAccessException("transaction failed: " + e.getMessage(), e);
    }
  }

  @Override
  public void close() {
    try {
      if (connection != null && !connection.isClosed()) {
        connection.close();
      }
    } catch (SQLException e) {
      throw new DataAccessException("cannot close the database", e);
    }
  }
}

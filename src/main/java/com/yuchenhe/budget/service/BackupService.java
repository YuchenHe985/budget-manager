package com.yuchenhe.budget.service;

import com.yuchenhe.budget.persistence.DataAccessException;
import com.yuchenhe.budget.persistence.Database;
import com.yuchenhe.budget.persistence.Migrations;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Logger;
import org.sqlite.SQLiteConfig;

/**
 * Consistent backups of the live database and checked restores.
 *
 * <p>A backup is SQLite's {@code VACUUM INTO}: a compact copy taken inside one read transaction,
 * safe while the app is running. A restore first opens the file on its own and checks that it is an
 * intact budget database no newer than this app; only then is it copied over the live data, and
 * older backups are migrated forward.
 */
public final class BackupService {
  private static final Logger LOG = Logger.getLogger(BackupService.class.getName());
  private final Database db;

  public BackupService(Database db) {
    this.db = db;
  }

  public void backup(Path target) {
    try {
      Files.deleteIfExists(target);
      try (PreparedStatement p = db.connection().prepareStatement("VACUUM INTO ?")) {
        p.setString(1, target.toAbsolutePath().toString());
        p.executeUpdate();
      }
      LOG.info("backup written");
    } catch (SQLException | java.io.IOException e) {
      throw new DataAccessException("backup failed: " + e.getMessage(), e);
    }
  }

  public void restore(Path source) {
    verify(source);
    try (Statement s = db.connection().createStatement()) {
      s.executeUpdate(
          "restore from '" + source.toAbsolutePath().toString().replace("'", "''") + "'");
    } catch (SQLException e) {
      throw new DataAccessException("restore failed: " + e.getMessage(), e);
    }
    Migrations.migrate(db.connection());
    LOG.info("backup restored");
  }

  /** Throws unless {@code file} is an intact budget database this app can open. */
  static void verify(Path file) {
    if (!Files.isRegularFile(file)) {
      throw new DataAccessException("no such backup file: " + file);
    }
    SQLiteConfig readOnly = new SQLiteConfig();
    readOnly.setReadOnly(true);
    try (Connection c = readOnly.createConnection("jdbc:sqlite:" + file.toAbsolutePath());
        Statement s = c.createStatement()) {
      try (ResultSet r = s.executeQuery("PRAGMA integrity_check")) {
        if (!r.next() || !"ok".equals(r.getString(1))) {
          throw new DataAccessException("the backup is damaged and was not restored");
        }
      }
      try (ResultSet r =
          s.executeQuery(
              "SELECT count(*) FROM sqlite_master WHERE type = 'table'"
                  + " AND name IN ('schema_version', 'category', 'txn')")) {
        if (r.getInt(1) != 3) {
          throw new DataAccessException("this file is not a budget backup");
        }
      }
      int v = Migrations.currentVersion(c);
      if (v > Migrations.LATEST) {
        throw new DataAccessException("the backup comes from a newer version of the app");
      }
    } catch (SQLException e) {
      throw new DataAccessException("this file is not a budget backup: " + e.getMessage(), e);
    }
  }
}

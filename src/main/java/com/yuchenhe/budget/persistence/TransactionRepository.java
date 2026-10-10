package com.yuchenhe.budget.persistence;

import com.yuchenhe.budget.domain.Kind;
import com.yuchenhe.budget.domain.Money;
import com.yuchenhe.budget.domain.Transaction;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** The ledger table. Dates are ISO strings, so a month is a half-open string range. */
public final class TransactionRepository {
  private static final String COLS = "id, date, kind, amount_cents, category_id, description";
  private final Database db;

  public TransactionRepository(Database db) {
    this.db = db;
  }

  public Transaction insert(Transaction t) {
    try (PreparedStatement p =
        db.connection()
            .prepareStatement(
                "INSERT INTO txn (date, kind, amount_cents, category_id, description)"
                    + " VALUES (?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
      bind(p, t);
      p.executeUpdate();
      try (ResultSet k = p.getGeneratedKeys()) {
        k.next();
        return t.withId(k.getLong(1));
      }
    } catch (SQLException e) {
      throw translate(e);
    }
  }

  public void update(Transaction t) {
    try (PreparedStatement p =
        db.connection()
            .prepareStatement(
                "UPDATE txn SET date = ?, kind = ?, amount_cents = ?, category_id = ?,"
                    + " description = ? WHERE id = ?")) {
      bind(p, t);
      p.setLong(6, t.id());
      if (p.executeUpdate() == 0) {
        throw new DataAccessException("transaction " + t.id() + " no longer exists");
      }
    } catch (SQLException e) {
      throw translate(e);
    }
  }

  public boolean delete(long id) {
    try (PreparedStatement p = db.connection().prepareStatement("DELETE FROM txn WHERE id = ?")) {
      p.setLong(1, id);
      return p.executeUpdate() > 0;
    } catch (SQLException e) {
      throw new DataAccessException("cannot delete the transaction: " + e.getMessage(), e);
    }
  }

  public Optional<Transaction> findById(long id) {
    return query("SELECT " + COLS + " FROM txn WHERE id = ?", id).stream().findFirst();
  }

  /** Every transaction dated inside {@code month}, oldest first. */
  public List<Transaction> findByMonth(YearMonth month) {
    return findBetween(month.atDay(1), month.atEndOfMonth());
  }

  /**
   * Inclusive date range, oldest first. Bounds are clamped to years 0000-9999: dates are compared
   * as ISO strings, and LocalDate.MIN/MAX print as "-999999999-..."/"+999999999-...", which sort
   * wrongly against "2026-...".
   */
  public List<Transaction> findBetween(LocalDate from, LocalDate to) {
    return query(
        "SELECT " + COLS + " FROM txn WHERE date >= ? AND date <= ? ORDER BY date, id",
        clamp(from).toString(),
        clamp(to).toString());
  }

  private static final LocalDate FIRST = LocalDate.of(0, 1, 1);
  private static final LocalDate LAST = LocalDate.of(9999, 12, 31);

  private static LocalDate clamp(LocalDate d) {
    return d.isBefore(FIRST) ? FIRST : d.isAfter(LAST) ? LAST : d;
  }

  public List<Transaction> findAll() {
    return query("SELECT " + COLS + " FROM txn ORDER BY date, id");
  }

  /** True if an identical entry (same date, kind, amount, category, description) exists. */
  public boolean existsSame(Transaction t) {
    try (PreparedStatement p =
        db.connection()
            .prepareStatement(
                "SELECT 1 FROM txn WHERE date = ? AND kind = ? AND amount_cents = ?"
                    + " AND category_id IS ? AND description = ? LIMIT 1")) {
      bind(p, t);
      try (ResultSet r = p.executeQuery()) {
        return r.next();
      }
    } catch (SQLException e) {
      throw new DataAccessException("cannot check for duplicates: " + e.getMessage(), e);
    }
  }

  private static void bind(PreparedStatement p, Transaction t) throws SQLException {
    p.setString(1, t.date().toString());
    p.setString(2, t.kind().name());
    p.setLong(3, t.amount().cents());
    if (t.categoryId() == null) {
      p.setNull(4, Types.INTEGER);
    } else {
      p.setLong(4, t.categoryId());
    }
    p.setString(5, t.description());
  }

  private static DataAccessException translate(SQLException e) {
    String m = e.getMessage() == null ? "" : e.getMessage();
    if (m.contains("FOREIGN KEY")) {
      return new DataAccessException("the chosen category no longer exists", e);
    }
    return new DataAccessException("cannot save the transaction: " + m, e);
  }

  private List<Transaction> query(String sql, Object... args) {
    try (PreparedStatement p = db.connection().prepareStatement(sql)) {
      for (int i = 0; i < args.length; i++) {
        p.setObject(i + 1, args[i]);
      }
      List<Transaction> out = new ArrayList<>();
      try (ResultSet r = p.executeQuery()) {
        while (r.next()) {
          long cat = r.getLong(5);
          Long categoryId = r.wasNull() ? null : cat; // wasNull() refers to the LAST column read
          out.add(
              new Transaction(
                  r.getLong(1),
                  LocalDate.parse(r.getString(2)),
                  Kind.valueOf(r.getString(3)),
                  Money.ofCents(r.getLong(4)),
                  categoryId,
                  r.getString(6)));
        }
      }
      return out;
    } catch (SQLException e) {
      throw new DataAccessException("cannot read transactions: " + e.getMessage(), e);
    }
  }
}

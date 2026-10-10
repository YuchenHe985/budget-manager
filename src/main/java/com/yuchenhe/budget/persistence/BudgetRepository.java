package com.yuchenhe.budget.persistence;

import com.yuchenhe.budget.domain.AllocationRule;
import com.yuchenhe.budget.domain.Money;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.Map;

/** Monthly category limits and the allocation rule setting. */
public final class BudgetRepository {
  private final Database db;

  public BudgetRepository(Database db) {
    this.db = db;
  }

  /** Sets (or with {@code null}, removes) the limit of one category for one month. */
  public void setLimit(YearMonth month, long categoryId, Money limit) {
    String sql =
        limit == null
            ? "DELETE FROM monthly_budget WHERE month = ? AND category_id = ?"
            : "INSERT INTO monthly_budget (month, category_id, limit_cents) VALUES (?, ?, ?)"
                + " ON CONFLICT (month, category_id) DO UPDATE SET limit_cents = excluded.limit_cents";
    try (PreparedStatement p = db.connection().prepareStatement(sql)) {
      p.setString(1, month.toString());
      p.setLong(2, categoryId);
      if (limit != null) {
        if (limit.isNegative()) {
          throw new IllegalArgumentException("a limit cannot be negative");
        }
        p.setLong(3, limit.cents());
      }
      p.executeUpdate();
    } catch (SQLException e) {
      throw new DataAccessException("cannot save the budget: " + e.getMessage(), e);
    }
  }

  /** categoryId -> limit for the month (categories without a limit are absent). */
  public Map<Long, Money> limits(YearMonth month) {
    try (PreparedStatement p =
        db.connection()
            .prepareStatement(
                "SELECT category_id, limit_cents FROM monthly_budget WHERE month = ?")) {
      p.setString(1, month.toString());
      Map<Long, Money> out = new HashMap<>();
      try (ResultSet r = p.executeQuery()) {
        while (r.next()) {
          out.put(r.getLong(1), Money.ofCents(r.getLong(2)));
        }
      }
      return out;
    } catch (SQLException e) {
      throw new DataAccessException("cannot read budgets: " + e.getMessage(), e);
    }
  }

  public AllocationRule allocationRule() {
    try (PreparedStatement p =
        db.connection()
            .prepareStatement("SELECT key, value FROM setting WHERE key LIKE 'allocation.%'")) {
      Map<String, Integer> v = new HashMap<>();
      try (ResultSet r = p.executeQuery()) {
        while (r.next()) {
          v.put(r.getString(1), Integer.parseInt(r.getString(2)));
        }
      }
      return new AllocationRule(
          v.getOrDefault("allocation.needs", 50),
          v.getOrDefault("allocation.wants", 30),
          v.getOrDefault("allocation.savings", 20));
    } catch (SQLException e) {
      throw new DataAccessException("cannot read settings: " + e.getMessage(), e);
    }
  }

  public void saveAllocationRule(AllocationRule rule) {
    db.inTransaction(
        c -> {
          try (PreparedStatement p =
              c.prepareStatement(
                  "INSERT INTO setting (key, value) VALUES (?, ?)"
                      + " ON CONFLICT (key) DO UPDATE SET value = excluded.value")) {
            for (var e :
                Map.of(
                        "allocation.needs", rule.needsPercent(),
                        "allocation.wants", rule.wantsPercent(),
                        "allocation.savings", rule.savingsPercent())
                    .entrySet()) {
              p.setString(1, e.getKey());
              p.setString(2, Integer.toString(e.getValue()));
              p.addBatch();
            }
            p.executeBatch();
          }
          return null;
        });
  }
}

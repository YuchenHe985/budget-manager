package com.yuchenhe.budget.service;

import com.yuchenhe.budget.domain.Category;
import com.yuchenhe.budget.domain.Group;
import com.yuchenhe.budget.domain.Transaction;
import com.yuchenhe.budget.persistence.CategoryRepository;
import com.yuchenhe.budget.persistence.DataAccessException;
import com.yuchenhe.budget.persistence.Database;
import com.yuchenhe.budget.persistence.TransactionRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.logging.Logger;

/** Use cases for entering and changing the ledger. Logs counts only, never amounts or text. */
public final class LedgerService {
  private static final Logger LOG = Logger.getLogger(LedgerService.class.getName());

  /** Entries more than this far in the future are almost always typos (2062 for 2026). */
  static final int MAX_DAYS_AHEAD = 366;

  private final Database db;
  private final CategoryRepository categories;
  private final TransactionRepository transactions;

  public LedgerService(Database db) {
    this.db = db;
    this.categories = new CategoryRepository(db);
    this.transactions = new TransactionRepository(db);
  }

  public List<Category> categories() {
    return categories.findAll();
  }

  public Category addCategory(String name, Group group) {
    Category c = categories.insert(new Category(0, name, group));
    LOG.info("category added");
    return c;
  }

  public void renameCategory(long id, String name, Group group) {
    categories.update(new Category(id, name, group));
  }

  public void deleteCategory(long id) {
    categories.delete(id);
  }

  /** Moves every transaction of {@code fromId} to {@code toId} and deletes {@code fromId}. */
  public int mergeCategory(long fromId, long toId) {
    if (fromId == toId) {
      throw new IllegalArgumentException("choose a different category to merge into");
    }
    categories.findById(toId).orElseThrow(() -> new DataAccessException("target not found"));
    return db.inTransaction(
        c -> {
          int moved;
          try (var p = c.prepareStatement("UPDATE txn SET category_id = ? WHERE category_id = ?")) {
            p.setLong(1, toId);
            p.setLong(2, fromId);
            moved = p.executeUpdate();
          }
          try (var p = c.prepareStatement("DELETE FROM category WHERE id = ?")) {
            p.setLong(1, fromId);
            p.executeUpdate();
          }
          return moved;
        });
  }

  public Transaction record(Transaction t) {
    check(t);
    Transaction saved = transactions.insert(t);
    LOG.info("transaction recorded");
    return saved;
  }

  public void change(Transaction t) {
    check(t);
    transactions.update(t);
  }

  public boolean remove(long id) {
    return transactions.delete(id);
  }

  public List<Transaction> between(LocalDate from, LocalDate to) {
    return transactions.findBetween(from, to);
  }

  private void check(Transaction t) {
    if (t.date().isAfter(LocalDate.now().plusDays(MAX_DAYS_AHEAD))) {
      throw new IllegalArgumentException("the date is more than a year in the future");
    }
    if (t.categoryId() != null && categories.findById(t.categoryId()).isEmpty()) {
      throw new IllegalArgumentException("the chosen category no longer exists");
    }
  }
}

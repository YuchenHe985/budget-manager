package com.yuchenhe.budget.service;

import com.yuchenhe.budget.domain.Category;
import com.yuchenhe.budget.domain.Kind;
import com.yuchenhe.budget.domain.Money;
import com.yuchenhe.budget.domain.Transaction;
import com.yuchenhe.budget.persistence.CategoryRepository;
import com.yuchenhe.budget.persistence.Database;
import com.yuchenhe.budget.persistence.TransactionRepository;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

/**
 * CSV import and export with the columns {@code date,kind,amount,category,description}.
 *
 * <p>Import is two steps: {@link #preview} checks every row and reports problems by line number
 * without touching the database; {@link #commit} writes all accepted rows in ONE transaction, so a
 * failure part-way leaves the ledger exactly as it was.
 */
public final class CsvService {
  private static final Logger LOG = Logger.getLogger(CsvService.class.getName());
  public static final List<String> HEADER =
      List.of("date", "kind", "amount", "category", "description");

  /** Larger files are refused before parsing (a desktop ledger never needs more). */
  public static final int MAX_ROWS = 50_000;

  /** What to do with a row identical to an existing entry or an earlier row of the same file. */
  public enum DuplicatePolicy {
    SKIP,
    IMPORT
  }

  /** One data row: either a transaction or the reasons it was rejected. */
  public record Row(int line, Transaction transaction, List<String> errors, boolean duplicate) {
    public boolean ok() {
      return errors.isEmpty();
    }
  }

  public record Preview(List<Row> rows) {
    public long errorCount() {
      return rows.stream().filter(r -> !r.ok()).count();
    }

    public long duplicateCount() {
      return rows.stream().filter(r -> r.ok() && r.duplicate()).count();
    }
  }

  public record Result(int imported, int skippedDuplicates) {}

  private final Database db;
  private final CategoryRepository categories;
  private final TransactionRepository transactions;

  public CsvService(Database db) {
    this.db = db;
    this.categories = new CategoryRepository(db);
    this.transactions = new TransactionRepository(db);
  }

  public Preview preview(String csv) {
    String text = csv.startsWith("﻿") ? csv.substring(1) : csv; // Excel's byte-order mark
    List<CsvCodec.Record> records;
    try {
      records = CsvCodec.parse(text);
    } catch (IllegalArgumentException e) {
      return new Preview(List.of(new Row(0, null, List.of(e.getMessage()), false)));
    }
    if (records.isEmpty()) {
      return new Preview(List.of(new Row(0, null, List.of("the file is empty"), false)));
    }
    List<String> header =
        records.get(0).fields().stream().map(h -> h.strip().toLowerCase(Locale.ROOT)).toList();
    if (!header.equals(HEADER)) {
      return new Preview(
          List.of(
              new Row(
                  1, null, List.of("the first line must be: " + String.join(",", HEADER)), false)));
    }
    if (records.size() - 1 > MAX_ROWS) {
      return new Preview(
          List.of(new Row(0, null, List.of("more than " + MAX_ROWS + " rows"), false)));
    }
    Map<String, Category> byName = new HashMap<>();
    for (Category c : categories.findAll()) {
      byName.put(c.name().toLowerCase(Locale.ROOT), c);
    }
    Set<String> seen = new HashSet<>();
    List<Row> rows = new ArrayList<>();
    for (CsvCodec.Record r : records.subList(1, records.size())) {
      rows.add(row(r, byName, seen));
    }
    return new Preview(rows);
  }

  private Row row(CsvCodec.Record r, Map<String, Category> byName, Set<String> seen) {
    List<String> errors = new ArrayList<>();
    List<String> f = r.fields();
    if (f.size() != HEADER.size()) {
      return new Row(
          r.line(),
          null,
          List.of("expected " + HEADER.size() + " columns, found " + f.size()),
          false);
    }
    LocalDate date = null;
    try {
      date = LocalDate.parse(f.get(0).strip());
    } catch (DateTimeParseException e) {
      errors.add("date must look like 2026-01-31");
    }
    Kind kind = null;
    try {
      kind = Kind.valueOf(f.get(1).strip().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      errors.add("kind must be expense, refund or income");
    }
    Money amount = null;
    try {
      amount = Money.parse(f.get(2));
      if (!amount.isPositive()) {
        errors.add("amount must be greater than zero (use kind=refund for money back)");
      }
    } catch (IllegalArgumentException e) {
      errors.add(e.getMessage());
    }
    Long categoryId = null;
    String catName = f.get(3).strip();
    if (kind == Kind.INCOME) {
      if (!catName.isEmpty()) {
        errors.add("income has no category; leave the column empty");
      }
    } else if (catName.isEmpty()) {
      errors.add("category is required");
    } else {
      Category c = byName.get(catName.toLowerCase(Locale.ROOT).replaceAll("\\s+", " "));
      if (c == null) {
        errors.add("unknown category \"" + catName + "\"");
      } else {
        categoryId = c.id();
      }
    }
    if (!errors.isEmpty()) {
      return new Row(r.line(), null, List.copyOf(errors), false);
    }
    Transaction t;
    try {
      t = new Transaction(0, date, kind, amount, categoryId, f.get(4));
    } catch (IllegalArgumentException e) {
      return new Row(r.line(), null, List.of(e.getMessage()), false);
    }
    String key =
        t.date()
            + "|"
            + t.kind()
            + "|"
            + t.amount().cents()
            + "|"
            + t.categoryId()
            + "|"
            + t.description();
    boolean dup = !seen.add(key) || transactions.existsSame(t);
    return new Row(r.line(), t, List.of(), dup);
  }

  /**
   * Writes the accepted rows atomically. Refuses a preview that still has errors: importing "the
   * good half" of a bank export silently would make the month totals wrong.
   */
  public Result commit(Preview preview, DuplicatePolicy policy) {
    if (preview.errorCount() > 0) {
      throw new IllegalStateException(
          preview.errorCount() + " row(s) have problems; fix the file and preview again");
    }
    Result result =
        db.inTransaction(
            c -> {
              int imported = 0;
              int skipped = 0;
              for (Row r : preview.rows()) {
                if (r.duplicate() && policy == DuplicatePolicy.SKIP) {
                  skipped++;
                  continue;
                }
                transactions.insert(r.transaction());
                imported++;
              }
              return new Result(imported, skipped);
            });
    LOG.info(
        () -> "imported " + result.imported() + " rows, skipped " + result.skippedDuplicates());
    return result;
  }

  public String export(LocalDate from, LocalDate to) {
    Map<Long, String> names = new HashMap<>();
    for (Category c : categories.findAll()) {
      names.put(c.id(), c.name());
    }
    StringBuilder sb = new StringBuilder(String.join(",", HEADER)).append('\n');
    for (Transaction t : transactions.findBetween(from, to)) {
      sb.append(t.date())
          .append(',')
          .append(t.kind().name().toLowerCase(Locale.ROOT))
          .append(',')
          .append(t.amount().toPlainString())
          .append(',')
          .append(CsvCodec.quote(Optional.ofNullable(names.get(t.categoryId())).orElse("")))
          .append(',')
          .append(CsvCodec.quote(t.description()))
          .append('\n');
    }
    return sb.toString();
  }
}

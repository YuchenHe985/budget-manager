package com.yuchenhe.budget;

import com.yuchenhe.budget.domain.Category;
import com.yuchenhe.budget.persistence.CategoryRepository;
import com.yuchenhe.budget.persistence.Database;
import java.nio.file.Path;

/** A fresh database file in a JUnit temp folder (removed by JUnit after the test). */
public final class TestDb {
  private TestDb() {}

  public static Database open(Path dir) {
    return Database.open(dir.resolve("budget.db"));
  }

  public static Category category(Database db, String name) {
    return new CategoryRepository(db).findByName(name).orElseThrow();
  }
}

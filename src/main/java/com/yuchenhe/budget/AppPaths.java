package com.yuchenhe.budget;

import java.nio.file.Path;
import java.util.Locale;

/** Where the data file lives: {@code -Dbudget.db=...} or {@code BUDGET_DB}, else per OS. */
public final class AppPaths {
  private AppPaths() {}

  public static Path database() {
    String override = System.getProperty("budget.db", System.getenv("BUDGET_DB"));
    if (override != null && !override.isBlank()) {
      return Path.of(override);
    }
    return dataDir(
            System.getProperty("os.name", ""), System.getProperty("user.home"), System.getenv())
        .resolve("budget.db");
  }

  static Path dataDir(String os, String home, java.util.Map<String, String> env) {
    String o = os.toLowerCase(Locale.ROOT);
    if (o.contains("mac")) {
      return Path.of(home, "Library", "Application Support", "BudgetManager");
    }
    if (o.contains("win")) {
      String appData = env.get("APPDATA");
      return appData != null ? Path.of(appData, "BudgetManager") : Path.of(home, "BudgetManager");
    }
    String xdg = env.get("XDG_DATA_HOME");
    return xdg != null && !xdg.isBlank()
        ? Path.of(xdg, "budget-manager")
        : Path.of(home, ".local", "share", "budget-manager");
  }
}

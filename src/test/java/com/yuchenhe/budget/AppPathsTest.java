package com.yuchenhe.budget;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AppPathsTest {
  @Test
  void eachOperatingSystemGetsItsUsualDataFolder() {
    assertEquals(
        Path.of("/u", "Library", "Application Support", "BudgetManager"),
        AppPaths.dataDir("Mac OS X", "/u", Map.of()));
    assertEquals(
        Path.of("C:/a", "BudgetManager"),
        AppPaths.dataDir("Windows 11", "C:/u", Map.of("APPDATA", "C:/a")));
    assertEquals(
        Path.of("/u", ".local", "share", "budget-manager"),
        AppPaths.dataDir("Linux", "/u", Map.of()));
    assertEquals(
        Path.of("/x", "budget-manager"),
        AppPaths.dataDir("Linux", "/u", Map.of("XDG_DATA_HOME", "/x")));
  }
}

package com.yuchenhe.budget;

import com.yuchenhe.budget.ui.BudgetApp;
import javafx.application.Application;

/**
 * Plain entry point. A main class that does not extend {@code Application} lets the app start from
 * a classpath jar or a jpackage image without the JavaFX launcher checks.
 */
public final class Launcher {
  private Launcher() {}

  public static void main(String[] args) {
    Application.launch(BudgetApp.class, args);
  }
}

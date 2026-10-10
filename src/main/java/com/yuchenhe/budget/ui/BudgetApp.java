package com.yuchenhe.budget.ui;

import com.yuchenhe.budget.AppPaths;
import com.yuchenhe.budget.persistence.Database;
import java.io.IOException;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

/** Opens the database, then the main window; closes the database when the window closes. */
public final class BudgetApp extends Application {
  private Database db;

  @Override
  public void start(Stage stage) {
    try {
      db = Database.open(AppPaths.database());
      stage.setTitle("Budget Manager");
      stage.setScene(createScene(db));
      stage.show();
    } catch (Exception e) {
      new Alert(Alert.AlertType.ERROR, "Budget Manager could not start: " + e.getMessage())
          .showAndWait();
      stop();
      javafx.application.Platform.exit();
    }
  }

  /** The main window's content; also used by the UI smoke test, so it renders what users see. */
  static Scene createScene(Database db) throws IOException {
    FXMLLoader loader = new FXMLLoader(BudgetApp.class.getResource("main.fxml"));
    loader.setController(new MainController(db));
    Parent root = loader.load();
    Scene scene = new Scene(root, 960, 620);
    scene.getStylesheets().add(BudgetApp.class.getResource("app.css").toExternalForm());
    return scene;
  }

  @Override
  public void stop() {
    if (db != null) {
      db.close();
      db = null;
    }
  }
}

package com.yuchenhe.budget.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yuchenhe.budget.TestDb;
import com.yuchenhe.budget.domain.Kind;
import com.yuchenhe.budget.domain.Money;
import com.yuchenhe.budget.domain.Transaction;
import com.yuchenhe.budget.persistence.Database;
import com.yuchenhe.budget.service.LedgerService;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableView;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Smoke test of the real window: loads main.fxml with its controller on a seeded database, checks
 * what the user would see on each tab, and renders every tab. With -Dscreenshots=DIR the renders
 * are written there as PNG (used for the README). Needs a display; CI runs it under xvfb.
 */
@Tag("ui")
class MainWindowTest {
  private static final String[] TABS = {"transactions", "summary", "settings", "data"};

  @TempDir Path dir;
  private Database db;

  @BeforeAll
  static void startToolkit() throws Exception {
    CompletableFuture<Void> up = new CompletableFuture<>();
    try {
      Platform.startup(() -> up.complete(null));
    } catch (IllegalStateException alreadyRunning) {
      up.complete(null);
    }
    up.get(30, TimeUnit.SECONDS);
    Platform.setImplicitExit(false);
  }

  @AfterEach
  void closeDb() {
    if (db != null) {
      db.close();
    }
  }

  @Test
  void theWindowShowsTheCurrentMonthOnEveryTab() throws Exception {
    db = TestDb.open(dir);
    seed(db);
    onFx(
        () -> {
          Scene scene = BudgetApp.createScene(db);
          TabPane tabs = (TabPane) scene.lookup("#tabs");
          assertEquals(TABS.length, tabs.getTabs().size());
          for (int i = 0; i < TABS.length; i++) {
            tabs.getSelectionModel().select(i);
            // A tab's content joins the scene graph only once it is shown and rendered.
            WritableImage shot = scene.snapshot(null);
            assertTrue(shot.getWidth() >= 900 && shot.getHeight() >= 600);
            if (i == 0) {
              TableView<?> table = (TableView<?>) scene.lookup("#txnTable");
              assertEquals(6, table.getItems().size(), "this month's transactions are listed");
            } else if (i == 1) {
              assertEquals("$1,312.80", ((Label) scene.lookup("#sBalance")).getText());
            }
            save(shot, TABS[i]);
          }
          return null;
        });
  }

  private static void seed(Database db) {
    LedgerService ledger = new LedgerService(db);
    YearMonth m = YearMonth.now();
    long food = TestDb.category(db, "Food").id();
    long housing = TestDb.category(db, "Housing").id();
    long fun = TestDb.category(db, "Entertainment").id();
    record(ledger, m, 1, Kind.INCOME, "2400.00", null, "Teaching assistant stipend");
    record(ledger, m, 2, Kind.EXPENSE, "950.00", housing, "Rent share");
    record(ledger, m, 5, Kind.EXPENSE, "86.40", food, "Groceries");
    record(ledger, m, 9, Kind.EXPENSE, "32.75", food, "Lunch, team meeting");
    record(ledger, m, 12, Kind.EXPENSE, "24.99", fun, "Concert ticket");
    record(ledger, m, 14, Kind.REFUND, "6.94", food, "Returned item");
  }

  private static void record(
      LedgerService ledger, YearMonth m, int day, Kind kind, String amount, Long cat, String what) {
    ledger.record(new Transaction(0, m.atDay(day), kind, Money.parse(amount), cat, what));
  }

  private static void save(WritableImage shot, String name) throws Exception {
    String out = System.getProperty("screenshots");
    if (out == null || out.isBlank()) {
      return;
    }
    int w = (int) shot.getWidth();
    int h = (int) shot.getHeight();
    BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
    PixelReader px = shot.getPixelReader();
    for (int y = 0; y < h; y++) {
      for (int x = 0; x < w; x++) {
        img.setRGB(x, y, px.getArgb(x, y));
      }
    }
    Path dirOut = Files.createDirectories(Path.of(out));
    ImageIO.write(img, "png", dirOut.resolve(name + ".png").toFile());
  }

  private interface FxWork {
    Object run() throws Exception;
  }

  private static void onFx(FxWork work) throws Exception {
    CompletableFuture<Object> done = new CompletableFuture<>();
    Platform.runLater(
        () -> {
          try {
            done.complete(work.run());
          } catch (Throwable t) {
            done.completeExceptionally(t);
          }
        });
    try {
      done.get(60, TimeUnit.SECONDS);
    } catch (java.util.concurrent.ExecutionException e) {
      if (e.getCause() instanceof Exception ex) {
        throw ex;
      }
      throw (Error) e.getCause();
    }
  }
}

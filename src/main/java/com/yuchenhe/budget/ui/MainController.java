package com.yuchenhe.budget.ui;

import com.yuchenhe.budget.domain.AllocationRule;
import com.yuchenhe.budget.domain.Category;
import com.yuchenhe.budget.domain.Group;
import com.yuchenhe.budget.domain.Kind;
import com.yuchenhe.budget.domain.Money;
import com.yuchenhe.budget.domain.Transaction;
import com.yuchenhe.budget.persistence.BudgetRepository;
import com.yuchenhe.budget.persistence.Database;
import com.yuchenhe.budget.service.BackupService;
import com.yuchenhe.budget.service.CsvService;
import com.yuchenhe.budget.service.LedgerService;
import com.yuchenhe.budget.service.MonthlySummary;
import com.yuchenhe.budget.service.SummaryService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.chart.PieChart;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;

/** Wires the screens to the services. Holds no business rules of its own. */
public final class MainController {
  private static final DateTimeFormatter MONTH =
      DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US);

  private final Database db;
  private final LedgerService ledger;
  private final SummaryService summaries;
  private final CsvService csv;
  private final BackupService backups;
  private final BudgetRepository budgets;
  private YearMonth month = YearMonth.now();
  private Map<Long, Category> categoriesById = new HashMap<>();
  private CsvService.Preview pendingImport;

  @FXML private Label monthLabel;
  @FXML private TableView<Transaction> txnTable;
  @FXML private TableColumn<Transaction, String> colDate;
  @FXML private TableColumn<Transaction, String> colKind;
  @FXML private TableColumn<Transaction, String> colCategory;
  @FXML private TableColumn<Transaction, String> colAmount;
  @FXML private TableColumn<Transaction, String> colDescription;
  @FXML private DatePicker fDate;
  @FXML private ComboBox<Kind> fKind;
  @FXML private TextField fAmount;
  @FXML private ComboBox<Category> fCategory;
  @FXML private TextField fDescription;
  @FXML private Label formErrors;
  @FXML private Button deleteBtn;

  @FXML private Label sIncome;
  @FXML private Label sSpent;
  @FXML private Label sBalance;
  @FXML private TableView<MonthlySummary.Line> lineTable;
  @FXML private TableColumn<MonthlySummary.Line, String> lCategory;
  @FXML private TableColumn<MonthlySummary.Line, String> lGroup;
  @FXML private TableColumn<MonthlySummary.Line, String> lSpent;
  @FXML private TableColumn<MonthlySummary.Line, String> lLimit;
  @FXML private TableColumn<MonthlySummary.Line, String> lRemaining;
  @FXML private TextField limitField;
  @FXML private Label limitError;
  @FXML private GridPane planGrid;
  @FXML private PieChart pie;

  @FXML private Spinner<Integer> pNeeds;
  @FXML private Spinner<Integer> pWants;
  @FXML private Spinner<Integer> pSavings;
  @FXML private Label ruleMsg;
  @FXML private ListView<Category> catList;
  @FXML private TextField catName;
  @FXML private ComboBox<Group> catGroup;
  @FXML private ComboBox<Category> mergeTarget;
  @FXML private Label catMsg;

  @FXML private CheckBox skipDuplicates;
  @FXML private Button importBtn;
  @FXML private Label importMsg;
  @FXML private TableView<CsvService.Row> previewTable;
  @FXML private TableColumn<CsvService.Row, String> pLine;
  @FXML private TableColumn<CsvService.Row, String> pStatus;
  @FXML private TableColumn<CsvService.Row, String> pDetail;
  @FXML private Label dataMsg;
  @FXML private Label dbPath;

  public MainController(Database db) {
    this.db = db;
    this.ledger = new LedgerService(db);
    this.summaries = new SummaryService(db);
    this.csv = new CsvService(db);
    this.backups = new BackupService(db);
    this.budgets = new BudgetRepository(db);
  }

  @FXML
  private void initialize() {
    bind(colDate, t -> t.date().toString());
    bind(colKind, t -> t.kind().name().toLowerCase(Locale.ROOT));
    bind(colCategory, t -> t.categoryId() == null ? "" : nameOf(t.categoryId()));
    bind(colAmount, t -> t.amount().toString());
    bind(colDescription, Transaction::description);
    txnTable.getSelectionModel().selectedItemProperty().addListener((o, a, t) -> fill(t));

    bind(lCategory, l -> l.category().name());
    bind(lGroup, l -> l.category().group().name().toLowerCase(Locale.ROOT));
    bind(lSpent, l -> l.spent().toString());
    bind(lLimit, l -> l.limit() == null ? "" : l.limit().toString());
    bind(lRemaining, l -> l.remaining() == null ? "" : l.remaining().toString());
    lineTable.setRowFactory(
        tv ->
            new TableRow<>() {
              @Override
              protected void updateItem(MonthlySummary.Line l, boolean empty) {
                super.updateItem(l, empty);
                getStyleClass().remove("over");
                if (!empty && l != null && l.over()) {
                  getStyleClass().add("over");
                }
              }
            });

    fKind.setItems(FXCollections.observableArrayList(Kind.values()));
    fKind.valueProperty().addListener((o, a, k) -> fCategory.setDisable(k == Kind.INCOME));
    catGroup.setItems(FXCollections.observableArrayList(Group.values()));
    catGroup.setValue(Group.NEEDS);
    StringConverter<Category> byName =
        new StringConverter<>() {
          @Override
          public String toString(Category c) {
            return c == null ? "" : c.name();
          }

          @Override
          public Category fromString(String s) {
            return null;
          }
        };
    fCategory.setConverter(byName);
    mergeTarget.setConverter(byName);
    catList.setCellFactory(
        lv ->
            new ListCell<>() {
              @Override
              protected void updateItem(Category c, boolean empty) {
                super.updateItem(c, empty);
                setText(
                    empty || c == null
                        ? null
                        : c.name() + "  (" + c.group().name().toLowerCase(Locale.ROOT) + ")");
              }
            });
    catList
        .getSelectionModel()
        .selectedItemProperty()
        .addListener(
            (o, a, c) -> {
              if (c != null) {
                catName.setText(c.name());
                catGroup.setValue(c.group());
              }
            });

    AllocationRule rule = budgets.allocationRule();
    pNeeds.setValueFactory(
        new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 100, rule.needsPercent()));
    pWants.setValueFactory(
        new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 100, rule.wantsPercent()));
    pSavings.setValueFactory(
        new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 100, rule.savingsPercent()));

    bind(pLine, r -> r.line() == 0 ? "" : Integer.toString(r.line()));
    bind(pStatus, r -> !r.ok() ? "error" : r.duplicate() ? "duplicate" : "ok");
    bind(
        pDetail,
        r ->
            !r.ok()
                ? String.join("; ", r.errors())
                : r.transaction().date()
                    + "  "
                    + r.transaction().kind().name().toLowerCase(Locale.ROOT)
                    + "  "
                    + r.transaction().amount()
                    + "  "
                    + (r.transaction().categoryId() == null
                        ? ""
                        : nameOf(r.transaction().categoryId()))
                    + "  "
                    + r.transaction().description());
    dbPath.setText("Data file: " + db.file().toAbsolutePath());
    onNew();
    refresh();
  }

  // ---- month navigation ----

  @FXML
  private void onPrevMonth() {
    month = month.minusMonths(1);
    refresh();
  }

  @FXML
  private void onNextMonth() {
    month = month.plusMonths(1);
    refresh();
  }

  private void refresh() {
    monthLabel.setText(month.format(MONTH));
    List<Category> cats = ledger.categories();
    categoriesById = new HashMap<>();
    cats.forEach(c -> categoriesById.put(c.id(), c));
    Category chosen = fCategory.getValue();
    fCategory.setItems(FXCollections.observableArrayList(cats));
    if (chosen != null) {
      fCategory.setValue(categoriesById.get(chosen.id()));
    }
    catList.setItems(FXCollections.observableArrayList(cats));
    mergeTarget.setItems(FXCollections.observableArrayList(cats));
    txnTable.setItems(
        FXCollections.observableArrayList(ledger.between(month.atDay(1), month.atEndOfMonth())));
    showSummary(summaries.summarize(month));
  }

  private void showSummary(MonthlySummary s) {
    sIncome.setText(s.income().toString());
    sSpent.setText(s.spent().toString());
    sBalance.setText(s.balance().toString());
    sBalance.getStyleClass().remove("over");
    if (s.balance().isNegative()) {
      sBalance.getStyleClass().add("over");
    }
    lineTable.setItems(FXCollections.observableArrayList(s.lines()));
    pie.setData(
        FXCollections.observableArrayList(
            s.lines().stream()
                .filter(l -> l.spent().isPositive())
                .map(l -> new PieChart.Data(l.category().name(), l.spent().cents() / 100.0))
                .toList()));
    planGrid.getChildren().clear();
    planGrid.addRow(
        0, new Label("Group"), new Label("Plan"), new Label("Actual"), new Label("Difference"));
    int row = 1;
    for (Group g : Group.values()) {
      Money plan = s.plan().get(g);
      Money actual = s.spentByGroup().get(g);
      Label diff = new Label(plan.minus(actual).toString());
      if (g != Group.SAVINGS && actual.compareTo(plan) > 0) {
        diff.getStyleClass().add("over");
      }
      planGrid.addRow(
          row++,
          new Label(g.name().toLowerCase(Locale.ROOT) + " (" + s.rule().percent(g) + "%)"),
          new Label(plan.toString()),
          new Label(actual.toString()),
          diff);
    }
  }

  // ---- transactions ----

  private void fill(Transaction t) {
    if (t == null) {
      return;
    }
    fDate.setValue(t.date());
    fKind.setValue(t.kind());
    fAmount.setText(t.amount().toPlainString());
    fCategory.setValue(t.categoryId() == null ? null : categoriesById.get(t.categoryId()));
    fDescription.setText(t.description());
    deleteBtn.setDisable(false);
    clearErrors();
  }

  @FXML
  private void onNew() {
    txnTable.getSelectionModel().clearSelection();
    LocalDate today = LocalDate.now();
    fDate.setValue(YearMonth.from(today).equals(month) ? today : month.atDay(1));
    fKind.setValue(Kind.EXPENSE);
    fAmount.clear();
    fDescription.clear();
    deleteBtn.setDisable(true);
    clearErrors();
  }

  @FXML
  private void onSave() {
    Transaction selected = txnTable.getSelectionModel().getSelectedItem();
    long id = selected == null ? 0 : selected.id();
    TransactionForm.Result r =
        TransactionForm.read(
            id,
            fDate.getValue(),
            fKind.getValue(),
            fAmount.getText(),
            fCategory.getValue(),
            fDescription.getText());
    clearErrors();
    if (!r.ok()) {
      Map<TransactionForm.Field, Control> controls =
          Map.of(
              TransactionForm.Field.DATE, fDate,
              TransactionForm.Field.KIND, fKind,
              TransactionForm.Field.AMOUNT, fAmount,
              TransactionForm.Field.CATEGORY, fCategory,
              TransactionForm.Field.DESCRIPTION, fDescription);
      r.errors().keySet().forEach(f -> controls.get(f).getStyleClass().add("invalid"));
      formErrors.setText(String.join("\n", r.errors().values()));
      return;
    }
    try {
      if (id == 0) {
        ledger.record(r.transaction());
      } else {
        ledger.change(r.transaction());
      }
      month = YearMonth.from(r.transaction().date());
      refresh();
      onNew();
    } catch (RuntimeException e) {
      formErrors.setText(e.getMessage());
    }
  }

  @FXML
  private void onDelete() {
    Transaction t = txnTable.getSelectionModel().getSelectedItem();
    if (t != null && confirm("Delete this transaction?")) {
      ledger.remove(t.id());
      refresh();
      onNew();
    }
  }

  private void clearErrors() {
    formErrors.setText("");
    for (Control c : List.<Control>of(fDate, fKind, fAmount, fCategory, fDescription)) {
      c.getStyleClass().remove("invalid");
    }
  }

  // ---- budgets and settings ----

  @FXML
  private void onSetLimit() {
    MonthlySummary.Line line = lineTable.getSelectionModel().getSelectedItem();
    limitError.setText("");
    if (line == null) {
      limitError.setText("select a category first");
      return;
    }
    try {
      String text = limitField.getText();
      budgets.setLimit(
          month, line.category().id(), text == null || text.isBlank() ? null : Money.parse(text));
      limitField.clear();
      refresh();
    } catch (RuntimeException e) {
      limitError.setText(e.getMessage());
    }
  }

  @FXML
  private void onSaveRule() {
    try {
      budgets.saveAllocationRule(
          new AllocationRule(pNeeds.getValue(), pWants.getValue(), pSavings.getValue()));
      ruleMsg.setText("Saved.");
      refresh();
    } catch (RuntimeException e) {
      ruleMsg.setText(e.getMessage());
    }
  }

  @FXML
  private void onAddCategory() {
    run(catMsg, () -> ledger.addCategory(catName.getText(), catGroup.getValue()), "Added.");
  }

  @FXML
  private void onRenameCategory() {
    Category c = catList.getSelectionModel().getSelectedItem();
    if (c == null) {
      catMsg.setText("select a category first");
      return;
    }
    run(
        catMsg,
        () -> ledger.renameCategory(c.id(), catName.getText(), catGroup.getValue()),
        "Saved.");
  }

  @FXML
  private void onDeleteCategory() {
    Category c = catList.getSelectionModel().getSelectedItem();
    if (c != null && confirm("Delete category \"" + c.name() + "\"?")) {
      run(catMsg, () -> ledger.deleteCategory(c.id()), "Deleted.");
    }
  }

  @FXML
  private void onMergeCategory() {
    Category from = catList.getSelectionModel().getSelectedItem();
    Category to = mergeTarget.getValue();
    if (from == null || to == null) {
      catMsg.setText("select a category and a target");
      return;
    }
    if (confirm(
        "Move every \""
            + from.name()
            + "\" transaction to \""
            + to.name()
            + "\" and delete \""
            + from.name()
            + "\"?")) {
      run(catMsg, () -> ledger.mergeCategory(from.id(), to.id()), "Merged.");
    }
  }

  // ---- data ----

  @FXML
  private void onPreview() {
    FileChooser fc = chooser("CSV files", "*.csv");
    var file = fc.showOpenDialog(window());
    if (file == null) {
      return;
    }
    try {
      pendingImport = csv.preview(Files.readString(file.toPath(), StandardCharsets.UTF_8));
      previewTable.setItems(FXCollections.observableArrayList(pendingImport.rows()));
      long errors = pendingImport.errorCount();
      importBtn.setDisable(errors > 0 || pendingImport.rows().isEmpty());
      importMsg.setText(
          errors > 0
              ? errors + " row(s) have problems. Nothing will be imported until the file is fixed."
              : pendingImport.rows().size()
                  + " rows ready, "
                  + pendingImport.duplicateCount()
                  + " duplicate(s).");
    } catch (IOException e) {
      importMsg.setText("cannot read the file: " + e.getMessage());
    }
  }

  @FXML
  private void onImport() {
    if (pendingImport == null) {
      return;
    }
    try {
      CsvService.Result r =
          csv.commit(
              pendingImport,
              skipDuplicates.isSelected()
                  ? CsvService.DuplicatePolicy.SKIP
                  : CsvService.DuplicatePolicy.IMPORT);
      importMsg.setText(
          "Imported " + r.imported() + ", skipped " + r.skippedDuplicates() + " duplicate(s).");
      pendingImport = null;
      importBtn.setDisable(true);
      refresh();
    } catch (RuntimeException e) {
      importMsg.setText("Nothing was imported: " + e.getMessage());
    }
  }

  @FXML
  private void onExport() {
    FileChooser fc = chooser("CSV files", "*.csv");
    fc.setInitialFileName("budget-" + month + ".csv");
    var file = fc.showSaveDialog(window());
    if (file != null) {
      run(
          dataMsg,
          () -> {
            try {
              Files.writeString(
                  file.toPath(),
                  csv.export(month.atDay(1), month.atEndOfMonth()),
                  StandardCharsets.UTF_8);
            } catch (IOException e) {
              throw new IllegalStateException("cannot write the file: " + e.getMessage(), e);
            }
          },
          "Exported to " + file.getName() + ".");
    }
  }

  @FXML
  private void onBackup() {
    FileChooser fc = chooser("Budget backups", "*.db");
    fc.setInitialFileName("budget-backup-" + LocalDate.now() + ".db");
    var file = fc.showSaveDialog(window());
    if (file != null) {
      run(
          dataMsg,
          () -> backups.backup(file.toPath()),
          "Backup written to " + file.getName() + ".");
    }
  }

  @FXML
  private void onRestore() {
    var file = chooser("Budget backups", "*.db").showOpenDialog(window());
    if (file != null && confirm("Replace ALL current data with this backup?")) {
      Path p = file.toPath();
      run(dataMsg, () -> backups.restore(p), "Restored from " + file.getName() + ".");
    }
  }

  // ---- helpers ----

  private String nameOf(long categoryId) {
    Category c = categoriesById.get(categoryId);
    return c == null ? "?" : c.name();
  }

  private void run(Label msg, Runnable action, String done) {
    try {
      action.run();
      msg.setText(done);
      refresh();
    } catch (RuntimeException e) {
      msg.setText(e.getMessage());
    }
  }

  private boolean confirm(String question) {
    return new Alert(Alert.AlertType.CONFIRMATION, question, ButtonType.OK, ButtonType.CANCEL)
        .showAndWait()
        .filter(b -> b == ButtonType.OK)
        .isPresent();
  }

  private FileChooser chooser(String what, String pattern) {
    FileChooser fc = new FileChooser();
    fc.getExtensionFilters().add(new FileChooser.ExtensionFilter(what, pattern));
    return fc;
  }

  private javafx.stage.Window window() {
    return monthLabel.getScene().getWindow();
  }

  private static <S> void bind(TableColumn<S, String> col, Function<S, String> value) {
    col.setCellValueFactory(c -> new ReadOnlyStringWrapper(value.apply(c.getValue())));
  }
}

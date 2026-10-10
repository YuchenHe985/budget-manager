package com.yuchenhe.budget.service;

import com.yuchenhe.budget.domain.Category;
import com.yuchenhe.budget.domain.Group;
import com.yuchenhe.budget.domain.Kind;
import com.yuchenhe.budget.domain.Money;
import com.yuchenhe.budget.domain.Transaction;
import com.yuchenhe.budget.persistence.BudgetRepository;
import com.yuchenhe.budget.persistence.CategoryRepository;
import com.yuchenhe.budget.persistence.Database;
import com.yuchenhe.budget.persistence.TransactionRepository;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Month totals: income, net spending per category (refunds subtract), limits and the plan. */
public final class SummaryService {
  private final CategoryRepository categories;
  private final TransactionRepository transactions;
  private final BudgetRepository budgets;

  public SummaryService(Database db) {
    this.categories = new CategoryRepository(db);
    this.transactions = new TransactionRepository(db);
    this.budgets = new BudgetRepository(db);
  }

  public MonthlySummary summarize(YearMonth month) {
    Money income = Money.ZERO;
    Map<Long, Money> byCategory = new HashMap<>();
    for (Transaction t : transactions.findByMonth(month)) {
      if (t.kind() == Kind.INCOME) {
        income = income.plus(t.amount());
      } else {
        byCategory.merge(t.categoryId(), t.spending(), Money::plus);
      }
    }
    Map<Long, Money> limits = budgets.limits(month);
    List<MonthlySummary.Line> lines = new ArrayList<>();
    Map<Group, Money> byGroup = new EnumMap<>(Group.class);
    for (Group g : Group.values()) {
      byGroup.put(g, Money.ZERO);
    }
    Money spent = Money.ZERO;
    for (Category c : categories.findAll()) {
      Money s = byCategory.getOrDefault(c.id(), Money.ZERO);
      lines.add(new MonthlySummary.Line(c, s, limits.get(c.id())));
      byGroup.merge(c.group(), s, Money::plus);
      spent = spent.plus(s);
    }
    var rule = budgets.allocationRule();
    return new MonthlySummary(month, income, spent, lines, byGroup, rule, rule.allocate(income));
  }
}

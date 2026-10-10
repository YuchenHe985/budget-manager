package com.yuchenhe.budget.service;

import com.yuchenhe.budget.domain.AllocationRule;
import com.yuchenhe.budget.domain.Category;
import com.yuchenhe.budget.domain.Group;
import com.yuchenhe.budget.domain.Money;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

/** Everything the summary screen shows for one month. */
public record MonthlySummary(
    YearMonth month,
    Money income,
    Money spent,
    List<Line> lines,
    Map<Group, Money> spentByGroup,
    AllocationRule rule,
    Map<Group, Money> plan) {

  /** One category: limit is null when no budget was set for the month. */
  public record Line(Category category, Money spent, Money limit) {
    public Money remaining() {
      return limit == null ? null : limit.minus(spent);
    }

    public boolean over() {
      return limit != null && spent.compareTo(limit) > 0;
    }
  }

  /** Income left after net spending (negative when the month overspent). */
  public Money balance() {
    return income.minus(spent);
  }
}

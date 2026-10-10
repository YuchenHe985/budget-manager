package com.yuchenhe.budget.domain;

import java.util.EnumMap;
import java.util.Map;

/**
 * Splits income across needs / wants / savings by whole-number percentages that add up to 100.
 *
 * <p>The split uses the largest-remainder method, so the three parts always add up to exactly the
 * income: $100.01 at 50/30/20 gives 50.01 + 30.00 + 20.00, never a cent more or less.
 */
public record AllocationRule(int needsPercent, int wantsPercent, int savingsPercent) {
  public static final AllocationRule DEFAULT = new AllocationRule(50, 30, 20);

  public AllocationRule {
    for (int p : new int[] {needsPercent, wantsPercent, savingsPercent}) {
      if (p < 0 || p > 100) {
        throw new IllegalArgumentException("each percentage must be between 0 and 100");
      }
    }
    if (needsPercent + wantsPercent + savingsPercent != 100) {
      throw new IllegalArgumentException(
          "percentages must add up to 100, not " + (needsPercent + wantsPercent + savingsPercent));
    }
  }

  public int percent(Group g) {
    return switch (g) {
      case NEEDS -> needsPercent;
      case WANTS -> wantsPercent;
      case SAVINGS -> savingsPercent;
    };
  }

  public Map<Group, Money> allocate(Money income) {
    if (income.isNegative()) {
      throw new IllegalArgumentException("income cannot be negative");
    }
    long total = income.cents();
    Map<Group, Long> parts = new EnumMap<>(Group.class);
    Map<Group, Long> remainders = new EnumMap<>(Group.class);
    long assigned = 0;
    for (Group g : Group.values()) {
      long scaled = total * percent(g); // <= 1e11 * 100: fits in a long
      parts.put(g, scaled / 100);
      remainders.put(g, scaled % 100);
      assigned += scaled / 100;
    }
    long left = total - assigned; // 0..2 cents
    while (left > 0) {
      Group best = null;
      for (Group g : Group.values()) {
        if (best == null || remainders.get(g) > remainders.get(best)) {
          best = g;
        }
      }
      parts.merge(best, 1L, Long::sum);
      remainders.put(best, -1L);
      left--;
    }
    Map<Group, Money> out = new EnumMap<>(Group.class);
    parts.forEach((g, c) -> out.put(g, Money.ofCents(c)));
    return out;
  }
}

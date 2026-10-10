package com.yuchenhe.budget.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;

class AllocationRuleTest {
  @Test
  void splitsAlwaysAddUpToTheIncomeExactly() {
    for (long cents = 0; cents < 5_000; cents += 7) {
      for (AllocationRule r :
          new AllocationRule[] {
            AllocationRule.DEFAULT, new AllocationRule(33, 33, 34), new AllocationRule(1, 1, 98)
          }) {
        Map<Group, Money> parts = r.allocate(Money.ofCents(cents));
        long sum = parts.values().stream().mapToLong(Money::cents).sum();
        assertEquals(cents, sum, r + " on " + cents);
      }
    }
  }

  @Test
  void theLeftoverCentGoesToTheLargestRemainder() {
    Map<Group, Money> p = AllocationRule.DEFAULT.allocate(Money.parse("100.01"));
    assertEquals(Money.parse("50.01"), p.get(Group.NEEDS)); // 5000.5 -> remainder .5 is largest
    assertEquals(Money.parse("30.00"), p.get(Group.WANTS));
    assertEquals(Money.parse("20.00"), p.get(Group.SAVINGS));
  }

  @Test
  void percentagesMustBeValidAndTotalOneHundred() {
    assertThrows(IllegalArgumentException.class, () -> new AllocationRule(50, 30, 30));
    assertThrows(IllegalArgumentException.class, () -> new AllocationRule(-10, 60, 50));
    assertThrows(
        IllegalArgumentException.class, () -> AllocationRule.DEFAULT.allocate(Money.parse("-1")));
  }
}

package com.yuchenhe.budget.domain;

/**
 * What a transaction does to the month. Amounts are always stored positive; the kind gives the
 * direction, so a refund can never be entered as a negative expense by mistake.
 */
public enum Kind {
  /** Money spent in a category. */
  EXPENSE,
  /** Money returned for an earlier expense; lowers the category's spending. */
  REFUND,
  /** Money earned; drives the allocation plan. */
  INCOME
}

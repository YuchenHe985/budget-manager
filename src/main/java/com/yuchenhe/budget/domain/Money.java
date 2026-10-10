package com.yuchenhe.budget.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * An exact amount of money in one currency, held as integer cents.
 *
 * <p>Floating point is never used: the 2023 version summed {@code double}s, so 0.1 + 0.2 printed
 * 0.30000000000000004 once more than two values were involved. Amounts are limited to +/- {@link
 * #MAX_CENTS} so that sums of a realistic ledger can never overflow a {@code long}.
 */
public final class Money implements Comparable<Money> {
  /** One billion dollars, in cents. */
  public static final long MAX_CENTS = 100_000_000_000L;

  public static final Money ZERO = new Money(0);

  private final long cents;

  private Money(long cents) {
    if (Math.abs(cents) > MAX_CENTS) {
      throw new IllegalArgumentException("amount out of supported range: " + cents + " cents");
    }
    this.cents = cents;
  }

  public static Money ofCents(long cents) {
    return new Money(cents);
  }

  /**
   * Parses user input such as {@code "12"}, {@code "12.5"}, {@code "1,234.56"} or {@code "$7.05"}.
   *
   * @throws IllegalArgumentException if the text is not a number or has more than two decimals
   */
  public static Money parse(String text) {
    if (text == null || text.isBlank()) {
      throw new IllegalArgumentException("amount is required");
    }
    String cleaned = text.trim().replace(",", "").replace("$", "");
    BigDecimal value;
    try {
      value = new BigDecimal(cleaned);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("not a number: " + text.trim());
    }
    if (value.stripTrailingZeros().scale() > 2) {
      throw new IllegalArgumentException("at most two decimal places: " + text.trim());
    }
    BigDecimal centsValue = value.movePointRight(2).setScale(0, RoundingMode.UNNECESSARY);
    if (centsValue.abs().compareTo(BigDecimal.valueOf(MAX_CENTS)) > 0) {
      throw new IllegalArgumentException("amount out of supported range: " + text.trim());
    }
    return new Money(centsValue.longValueExact());
  }

  public long cents() {
    return cents;
  }

  public BigDecimal toBigDecimal() {
    return BigDecimal.valueOf(cents, 2);
  }

  public Money plus(Money other) {
    return new Money(Math.addExact(cents, other.cents));
  }

  public Money minus(Money other) {
    return new Money(Math.subtractExact(cents, other.cents));
  }

  public Money negate() {
    return new Money(-cents);
  }

  public boolean isNegative() {
    return cents < 0;
  }

  public boolean isPositive() {
    return cents > 0;
  }

  /** This amount as a percentage of {@code whole}, rounded half-up to two decimals. */
  public BigDecimal percentOf(Money whole) {
    if (whole.cents == 0) {
      return BigDecimal.ZERO.setScale(2);
    }
    return BigDecimal.valueOf(cents)
        .multiply(BigDecimal.valueOf(100))
        .divide(BigDecimal.valueOf(whole.cents), 2, RoundingMode.HALF_UP);
  }

  /** Plain form for files and storage: {@code -1234.50}. */
  public String toPlainString() {
    return toBigDecimal().toPlainString();
  }

  @Override
  public String toString() {
    return (cents < 0 ? "-$" : "$") + String.format("%,.2f", toBigDecimal().abs());
  }

  @Override
  public int compareTo(Money o) {
    return Long.compare(cents, o.cents);
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof Money m && m.cents == cents;
  }

  @Override
  public int hashCode() {
    return Objects.hash(cents);
  }
}

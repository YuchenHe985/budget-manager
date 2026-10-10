package com.yuchenhe.budget.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTest {
  @Test
  void parsesTheWaysPeopleTypeAmounts() {
    assertEquals(1200, Money.parse("12").cents());
    assertEquals(1250, Money.parse("12.5").cents());
    assertEquals(123456, Money.parse(" 1,234.56 ").cents());
    assertEquals(705, Money.parse("$7.05").cents());
    assertEquals(-1999, Money.parse("-19.99").cents());
    assertEquals(1000, Money.parse("10.000").cents()); // trailing zeros are not extra precision
  }

  @Test
  void rejectsInputThatWouldLoseCentsOrIsNotANumber() {
    assertThrows(IllegalArgumentException.class, () -> Money.parse("1.005"));
    assertThrows(IllegalArgumentException.class, () -> Money.parse("abc"));
    assertThrows(IllegalArgumentException.class, () -> Money.parse(""));
    assertThrows(IllegalArgumentException.class, () -> Money.parse(null));
    assertThrows(IllegalArgumentException.class, () -> Money.parse("1e400"));
  }

  @Test
  void sumsStayExactWhereDoublesDrift() {
    Money sum = Money.ZERO;
    for (int i = 0; i < 10; i++) {
      sum = sum.plus(Money.parse("0.10"));
    }
    assertEquals(Money.parse("1.00"), sum); // 0.1 added ten times as double is 0.9999999999999999
  }

  @Test
  void rangeIsCheckedSoSumsCannotOverflow() {
    assertThrows(IllegalArgumentException.class, () -> Money.ofCents(Money.MAX_CENTS + 1));
    assertThrows(
        IllegalArgumentException.class,
        () -> Money.ofCents(Money.MAX_CENTS).plus(Money.ofCents(1)));
  }

  @Test
  void percentagesRoundHalfUpToTwoDecimals() {
    assertEquals(new BigDecimal("33.33"), Money.parse("1").percentOf(Money.parse("3")));
    assertEquals(new BigDecimal("66.67"), Money.parse("2").percentOf(Money.parse("3")));
    assertEquals(new BigDecimal("0.00"), Money.parse("2").percentOf(Money.ZERO));
  }

  @Test
  void displaysWithSignAndGrouping() {
    assertEquals("$1,234.50", Money.parse("1234.5").toString());
    assertEquals("-$0.05", Money.parse("-0.05").toString());
    assertEquals("-1234.50", Money.parse("-1234.5").toPlainString());
  }
}

package com.yuchenhe.budget.domain;

import java.util.Objects;

/** A spending category (Housing, Food, ...) that belongs to one allocation group. */
public record Category(long id, String name, Group group) {
  public static final int MAX_NAME = 40;

  public Category {
    Objects.requireNonNull(group, "group");
    name = normalizeName(name);
  }

  /** Trimmed, single-spaced name; rejects blank or over-long names. */
  public static String normalizeName(String name) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("category name is required");
    }
    String n = name.trim().replaceAll("\\s+", " ");
    if (n.length() > MAX_NAME) {
      throw new IllegalArgumentException("category name longer than " + MAX_NAME + " characters");
    }
    return n;
  }

  public Category withId(long newId) {
    return new Category(newId, name, group);
  }
}

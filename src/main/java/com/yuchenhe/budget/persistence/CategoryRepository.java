package com.yuchenhe.budget.persistence;

import com.yuchenhe.budget.domain.Category;
import com.yuchenhe.budget.domain.Group;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Categories table. All SQL uses bound parameters. */
public final class CategoryRepository {
  private final Database db;

  public CategoryRepository(Database db) {
    this.db = db;
  }

  public List<Category> findAll() {
    return query("SELECT id, name, grp FROM category ORDER BY grp, name COLLATE NOCASE");
  }

  public Optional<Category> findById(long id) {
    return query("SELECT id, name, grp FROM category WHERE id = ?", id).stream().findFirst();
  }

  public Optional<Category> findByName(String name) {
    return query("SELECT id, name, grp FROM category WHERE name = ? COLLATE NOCASE", name.strip())
        .stream()
        .findFirst();
  }

  public Category insert(Category c) {
    try (PreparedStatement p =
        conn()
            .prepareStatement(
                "INSERT INTO category (name, grp) VALUES (?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
      p.setString(1, c.name());
      p.setString(2, c.group().name());
      p.executeUpdate();
      try (ResultSet k = p.getGeneratedKeys()) {
        k.next();
        return c.withId(k.getLong(1));
      }
    } catch (SQLException e) {
      throw translate(e, c.name());
    }
  }

  public void update(Category c) {
    try (PreparedStatement p =
        conn().prepareStatement("UPDATE category SET name = ?, grp = ? WHERE id = ?")) {
      p.setString(1, c.name());
      p.setString(2, c.group().name());
      p.setLong(3, c.id());
      if (p.executeUpdate() == 0) {
        throw new DataAccessException("category " + c.id() + " no longer exists");
      }
    } catch (SQLException e) {
      throw translate(e, c.name());
    }
  }

  /** Deletes a category that has no transactions; otherwise explains why it cannot. */
  public void delete(long id) {
    try (PreparedStatement p = conn().prepareStatement("DELETE FROM category WHERE id = ?")) {
      p.setLong(1, id);
      p.executeUpdate();
    } catch (SQLException e) {
      if (e.getMessage() != null && e.getMessage().contains("FOREIGN KEY")) {
        throw new DataAccessException(
            "this category still has transactions; move them to another category first", e);
      }
      throw new DataAccessException("cannot delete the category: " + e.getMessage(), e);
    }
  }

  private DataAccessException translate(SQLException e, String name) {
    if (e.getMessage() != null && e.getMessage().contains("UNIQUE")) {
      return new DataAccessException("a category named \"" + name + "\" already exists", e);
    }
    return new DataAccessException("cannot save the category: " + e.getMessage(), e);
  }

  private List<Category> query(String sql, Object... args) {
    try (PreparedStatement p = conn().prepareStatement(sql)) {
      for (int i = 0; i < args.length; i++) {
        p.setObject(i + 1, args[i]);
      }
      List<Category> out = new ArrayList<>();
      try (ResultSet r = p.executeQuery()) {
        while (r.next()) {
          out.add(new Category(r.getLong(1), r.getString(2), Group.valueOf(r.getString(3))));
        }
      }
      return out;
    } catch (SQLException e) {
      throw new DataAccessException("cannot read categories: " + e.getMessage(), e);
    }
  }

  private Connection conn() {
    return db.connection();
  }
}

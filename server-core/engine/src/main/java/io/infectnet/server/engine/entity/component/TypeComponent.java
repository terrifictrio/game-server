package io.infectnet.server.engine.entity.component;

import io.infectnet.server.engine.entity.Category;
import io.infectnet.server.engine.entity.Entity;

import org.apache.commons.lang3.ObjectUtils;

import java.util.Objects;
import java.util.Optional;

/**
 * Abstract base class for entity types. The actual type of an
 * {@link io.infectnet.server.engine.entity.Entity} instance is determined by its
 * {@code TypeComponent}.
 */
public abstract class TypeComponent {
  private final TypeComponent parent;

  private final Category category;

  private final String name;

  /**
   * Constructs a new instance with the specified category and name and parent set to {@code null}.
   * @param category the category
   * @param name the name
   * @throws NullPointerException if any of the parameters is {@code null}
   */
  public TypeComponent(Category category, String name) {
    this(null, category, name);
  }

  /**
   * Constructs a new instance with the specified attributes.
   * @param parent the parent type
   * @param category the category
   * @param name the name
   * @throws NullPointerException if any of the parameters is {@code null}
   */
  public TypeComponent(TypeComponent parent, Category category, String name) {
    this.parent = parent;

    this.category = Objects.requireNonNull(category);

    this.name = Objects.requireNonNull(name);
  }

  public abstract Entity createEntityOfType();

  public Optional<TypeComponent> getParent() {
    return Optional.ofNullable(parent);
  }

  public Category getCategory() {
    return category;
  }

  public String getName() {
    return name;
  }

  public boolean isDescendantOf(TypeComponent baseType) {
    Optional<TypeComponent> parentOptional = this.getParent();

    while (parentOptional.isPresent()) {
      if (parentOptional.get().equals(baseType)) {
        return true;
      }

      parentOptional = parentOptional.get().getParent();
    }

    return false;
  }
}
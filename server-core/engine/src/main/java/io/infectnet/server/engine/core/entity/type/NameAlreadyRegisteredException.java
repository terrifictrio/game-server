package io.infectnet.server.engine.core.entity.type;

public class NameAlreadyRegisteredException extends RuntimeException {
  private final String name;

  public NameAlreadyRegisteredException(String name) {
    this.name = name;
  }

  public String getName() {
    return name;
  }
}

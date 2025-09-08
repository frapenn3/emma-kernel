package it.emma.kernel.model;

import java.util.List;

public final class Constitution {
  public int version;
  public List<Principle> principles;

  public static final class Principle {
    public String name;
    public boolean must;
  }
}

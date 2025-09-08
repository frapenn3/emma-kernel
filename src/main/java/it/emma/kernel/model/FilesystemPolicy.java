package it.emma.kernel.model;

import java.util.List;

public final class FilesystemPolicy {
  public int version;
  public String defaultPolicy;             // "ALLOW" | "DENY" | "ASK"
  public List<Rule> allow;
  public List<Rule> ask;
  public List<Rule> deny;
  public List<String> operations;          // READ / WRITE / DELETE

  public static final class Rule {
    public String path;                    // glob (es: "./work/**")
  }
}

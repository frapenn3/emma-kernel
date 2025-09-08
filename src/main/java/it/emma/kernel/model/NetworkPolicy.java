package it.emma.kernel.model;

import java.util.List;

public final class NetworkPolicy {
  public int version;
  public String defaultPolicy;               // "ALLOW" | "DENY" | "ASK"
  public List<NetRule> allow;
  public List<NetRule> ask;
  public List<NetRule> deny;
  public List<String> operations;            // CONNECT / DNS

  public static final class NetRule {
    public String host;                      // hostname | "*" | "*.example.com"
    public List<Integer> ports;              // opzionale
  }
}

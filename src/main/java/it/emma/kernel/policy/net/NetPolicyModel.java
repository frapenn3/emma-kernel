package it.emma.kernel.policy.net;

import java.util.List;

public class NetPolicyModel {
  public List<Rule> rules;

  public static final class Rule {
    // op: CONNECT oppure DNS; se assente -> vale per entrambi
    public List<String> op;
    // glob host (es: "localhost", "*.example.com", "*")
    public String host;
    // porta consentita (singola) o range "8000-8090"; opzionale
    public String port;
    // ALLOW | DENY
    public String effect;
    public String reason;
  }
}

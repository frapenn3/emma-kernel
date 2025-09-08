package it.emma.kernel.policy.quota;

public class QuotaPolicyModel {
  public Integer window_seconds;  // default in engine se null
  public Limits limits;
  public String reason_prefix;    // opzionale

  public static final class Limits {
    public Integer net_requests;
    public Integer cpu_cores;
    public Integer time_min;
  }
}

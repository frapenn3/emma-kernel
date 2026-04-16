package it.emma.kernel.policy.quota;

import it.emma.kernel.policy.Action;
import it.emma.kernel.policy.Decision;

public class QuotaPolicyEngine {

  private volatile QuotaPolicyModel model = new QuotaPolicyModel();
  private final QuotaTracker tracker = new QuotaTracker();

  public void load(QuotaPolicyModel m) {
    this.model = (m != null) ? m : new QuotaPolicyModel();
    tracker.configure(this.model.window_seconds);
  }

  public QuotaTracker getTracker() {
    return tracker;
  }

  /**
   * Valuta una richiesta QUOTA_CONSUME.
   * Parametri letti dall'Action:
   *  - "subject" (String) opzionale; default "global"
   *  - "net_requests" (Integer) incremento
   *  - "cpu_cores"   (Integer) incremento (interpretabile come core-minuti)
   *  - "time_min"    (Integer) incremento
   */
  public Decision decide(Action a) {
    return evaluate(a, true);
  }

  public Decision inspect(Action a) {
    return evaluate(a, false);
  }

  private Decision evaluate(Action a, boolean consume) {
    if (a == null) return new Decision(Decision.Effect.DENY, "invalid action");

    final String subject = a.getString("subject", "global");

    final int incNet  = a.getInt("net_requests", 0);
    final int incCpu  = a.getInt("cpu_cores", 0);
    final int incTime = a.getInt("time_min", 0);

    // limiti attivi
    final var limits = (model != null && model.limits != null) ? model.limits : new QuotaPolicyModel.Limits();

    // net_requests
    if (incNet > 0) {
      long total = consume ? tracker.addAndGet(subject, "net_requests", incNet)
          : tracker.current(subject, "net_requests") + incNet;
      if (limits.net_requests != null && total > limits.net_requests) {
        return deny("net_requests", total, limits.net_requests);
      }
    }

    // cpu_cores
    if (incCpu > 0) {
      long total = consume ? tracker.addAndGet(subject, "cpu_cores", incCpu)
          : tracker.current(subject, "cpu_cores") + incCpu;
      if (limits.cpu_cores != null && total > limits.cpu_cores) {
        return deny("cpu_cores", total, limits.cpu_cores);
      }
    }

    // time_min
    if (incTime > 0) {
      long total = consume ? tracker.addAndGet(subject, "time_min", incTime)
          : tracker.current(subject, "time_min") + incTime;
      if (limits.time_min != null && total > limits.time_min) {
        return deny("time_min", total, limits.time_min);
      }
    }

    return new Decision(Decision.Effect.ALLOW, consume ? "quota ok" : "quota check ok");
  }

  private Decision deny(String metric, long value, long limit) {
    String prefix = (model != null && model.reason_prefix != null && !model.reason_prefix.isBlank())
        ? model.reason_prefix
        : "quota exceeded";
    return new Decision(Decision.Effect.DENY,
        String.format("%s: %s (%d/%d)", prefix, metric, value, limit));
  }
}

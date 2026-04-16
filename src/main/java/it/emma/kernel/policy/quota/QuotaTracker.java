package it.emma.kernel.policy.quota;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import it.emma.kernel.policy.QuotaSnapshot;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Quota tracker a finestra fissa (fixed window). Per ogni (subject, metric) tiene un contatore
 * che si azzera quando la finestra scade.
 */
@ApplicationScoped
public class QuotaTracker {

  static final class Counter {
    final AtomicLong value = new AtomicLong(0);
    volatile long windowStartEpochSec;

    Counter(long start) { this.windowStartEpochSec = start; }
  }

  private final ConcurrentHashMap<String, Counter> map = new ConcurrentHashMap<>();
  private volatile int windowSeconds = 60;

  public void configure(Integer windowSeconds) {
    this.windowSeconds = (windowSeconds == null || windowSeconds <= 0) ? 60 : windowSeconds;
  }

  /**
   * Incrementa e ritorna il valore corrente nella finestra.
   *
   * @param subjectKey es. "global" o l'utente/chiamante
   * @param metric     es. "net_requests", "cpu_cores", "time_min"
   * @param delta      incremento positivo
   */
  public long addAndGet(String subjectKey, String metric, long delta) {
    if (delta <= 0) return current(subjectKey, metric);

    String key = key(subjectKey, metric);
    long now = Instant.now().getEpochSecond();

    Counter c = map.compute(key, (k, old) -> {
      if (old == null) return new Counter(now);

      // reset finestra se scaduta
      if (now - old.windowStartEpochSec >= windowSeconds) {
        old.value.set(0);
        old.windowStartEpochSec = now;
      }
      return old;
    });

    return c.value.addAndGet(delta);
  }

  public long current(String subjectKey, String metric) {
    String key = key(subjectKey, metric);
    Counter c = map.get(key);
    if (c == null) return 0L;

    long now = Instant.now().getEpochSecond();
    if (now - c.windowStartEpochSec >= windowSeconds) {
      // finestra scaduta -> considera 0
      return 0L;
    }
    return c.value.get();
  }

  public QuotaSnapshot snapshot() {
    QuotaSnapshot s = new QuotaSnapshot();
    s.net_requests = safeInt(current("global", "net_requests"));
    s.cpu_cores = safeInt(current("global", "cpu_cores"));
    s.time_min = current("global", "time_min");
    s.uptime_sec = 0L;
    return s;
  }

  public void reset() {
    map.clear();
  }

  private static int safeInt(long value) {
    if (value > Integer.MAX_VALUE) return Integer.MAX_VALUE;
    if (value < Integer.MIN_VALUE) return Integer.MIN_VALUE;
    return (int) value;
  }

  private static String key(String subject, String metric) {
    return Objects.toString(subject, "global") + "|" + Objects.toString(metric, "");
  }
}

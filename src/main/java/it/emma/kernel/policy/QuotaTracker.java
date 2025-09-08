package it.emma.kernel.policy;


import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class QuotaTracker {

  private final AtomicInteger netRequests = new AtomicInteger(0);
  private final AtomicInteger cpuCores    = new AtomicInteger(0);    // se vuoi modellare “core riservati”
  private final AtomicLong    startEpoch  = new AtomicLong(Instant.now().getEpochSecond());
  private final AtomicLong    consumedMinutes = new AtomicLong(0L);  // budget tempo consumato

  public void incrNetRequests(int delta) {
    if (delta > 0) netRequests.addAndGet(delta);
  }

  public int getNetRequests() {
    return netRequests.get();
  }

  public void consumeMinutes(long minutes) {
    if (minutes > 0L) consumedMinutes.addAndGet(minutes);
  }

  public long getConsumedMinutes() {
    return consumedMinutes.get();
  }

  public void setCpuCores(int cores) {
    cpuCores.set(Math.max(0, cores));
  }

  public int getCpuCores() {
    return cpuCores.get();
  }

  public long uptimeSeconds() {
    long now = Instant.now().getEpochSecond();
    return Math.max(0L, now - startEpoch.get());
  }

  public QuotaSnapshot snapshot() {
    QuotaSnapshot s = new QuotaSnapshot();
    s.net_requests = getNetRequests();
    s.cpu_cores    = getCpuCores();
    s.time_min     = getConsumedMinutes();
    s.uptime_sec   = uptimeSeconds();
    return s;
  }

  public void reset() {
    netRequests.set(0);
    cpuCores.set(0);
    consumedMinutes.set(0L);
    startEpoch.set(Instant.now().getEpochSecond());
  }

  // utilità comoda per misurare blocchi di codice
  public AutoCloseable timeBlockMinutes() {
    Instant start = Instant.now();
    return new AutoCloseable() {
      @Override public void close() {
        long mins = Duration.between(start, Instant.now()).toMinutes();
        if (mins < 1L) mins = 1L; // arrotonda a 1 min minimo
        consumeMinutes(mins);
      }
    };
  }
}
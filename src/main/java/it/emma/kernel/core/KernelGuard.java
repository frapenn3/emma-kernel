package it.emma.kernel.core;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.concurrent.atomic.AtomicBoolean;

@ApplicationScoped
public class KernelGuard {
  private final AtomicBoolean stopped = new AtomicBoolean(false);

  public boolean isStopped() { return stopped.get(); }

  public void stop() { stopped.set(true); }

  public void resume() { stopped.set(false); }
}

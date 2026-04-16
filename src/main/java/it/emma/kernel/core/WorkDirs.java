package it.emma.kernel.core;

import org.jboss.logging.Logger;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class WorkDirs {
  private static final Logger LOG = Logger.getLogger(WorkDirs.class);

  private final java.nio.file.Path work = java.nio.file.Path.of("./work").normalize();
  private final java.nio.file.Path deploy = java.nio.file.Path.of("./deploy").normalize();

  public java.nio.file.Path work() { return work; }
  public java.nio.file.Path deploy() { return deploy; }

  @PostConstruct
  void init() {
    try {
      if (!java.nio.file.Files.exists(work))   java.nio.file.Files.createDirectories(work);
      if (!java.nio.file.Files.exists(deploy)) java.nio.file.Files.createDirectories(deploy);
    } catch (Exception e) {
      LOG.errorf(e, "WorkDirs init error");
    }
  }
}

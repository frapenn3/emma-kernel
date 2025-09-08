package it.emma.kernel.policy;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import it.emma.kernel.model.Constitution;
import it.emma.kernel.model.FilesystemPolicy;
import it.emma.kernel.model.NetworkPolicy;
import it.emma.kernel.model.QuotasPolicy;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class PolicyLoader {

  private final AtomicReference<Constitution> constitutionRef = new AtomicReference<Constitution>();
  private final AtomicReference<FilesystemPolicy> fsRef = new AtomicReference<FilesystemPolicy>();
  private final AtomicReference<NetworkPolicy> netRef = new AtomicReference<NetworkPolicy>();
  private final AtomicReference<QuotasPolicy> quotasRef = new AtomicReference<QuotasPolicy>();

  public Constitution constitution() {
    return loadOnce("constitution.yml", Constitution.class, constitutionRef);
  }
  public FilesystemPolicy fs() {
    return loadOnce("policy.filesystem.yml", FilesystemPolicy.class, fsRef);
  }
  public NetworkPolicy net() {
    return loadOnce("policy.network.yml", NetworkPolicy.class, netRef);
  }
  public QuotasPolicy quotas() {
    return loadOnce("policy.quotas.yml", QuotasPolicy.class, quotasRef);
  }

  public void reloadAll() {
    constitutionRef.set(null);
    fsRef.set(null);
    netRef.set(null);
    quotasRef.set(null);
  }

  private <T> T loadOnce(String resource, Class<T> type, AtomicReference<T> ref) {
    T cur = ref.get();
    if (cur != null) return cur;
    T parsed = parseYaml(resource, type);
    ref.compareAndSet(null, parsed);
    return ref.get();
  }

  private <T> T parseYaml(String resource, Class<T> type) {
    Exception last = null;

    // 1) prova dal classpath
    try {
      ClassLoader cl = Thread.currentThread().getContextClassLoader();
      try (InputStream is = cl.getResourceAsStream(resource)) {
        if (is != null) {
          return loadYaml(is, type);
        }
      }
    } catch (Exception e) { last = e; }

    // 2) prova da filesystem (dev): ./src/main/resources/<resource>
    try {
      Path devPath = Path.of("src", "main", "resources", resource);
      if (Files.exists(devPath)) {
        try (InputStream is = Files.newInputStream(devPath)) {
          return loadYaml(is, type);
        }
      }
    } catch (Exception e) { last = e; }

    // 3) prova da filesystem: ./<resource> (se qualcuno li mette a fianco del jar)
    try {
      Path local = Path.of(resource);
      if (Files.exists(local)) {
        try (InputStream is = Files.newInputStream(local)) {
          return loadYaml(is, type);
        }
      }
    } catch (Exception e) { last = e; }

    String msg = "Missing resource: " + resource + (last != null ? " (" + last.getMessage() + ")" : "");
    throw new IllegalStateException("Failed to parse " + resource + ": " + msg);
  }

  private static <T> T loadYaml(InputStream is, Class<T> type) {
    LoaderOptions opts = new LoaderOptions();
    Constructor cons = new Constructor(type, opts);
    Yaml yaml = new Yaml(cons);
    Object o = yaml.load(is);
    return type.cast(o);
  }
}

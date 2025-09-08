package it.emma.kernel.policy;

import java.io.InputStream;
import java.time.Instant;

import org.yaml.snakeyaml.Yaml;

import it.emma.kernel.policy.fs.FsPolicyEngine;
import it.emma.kernel.policy.fs.FsPolicyModel;
import it.emma.kernel.policy.net.NetPolicyEngine;
import it.emma.kernel.policy.net.NetPolicyModel;
import it.emma.kernel.policy.quota.QuotaPolicyEngine;
import it.emma.kernel.policy.quota.QuotaPolicyModel;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class PolicyEnforcer {

  private volatile PolicyModel model;
  private volatile Instant lastReload;
  private final QuotaTracker quotas = new QuotaTracker();

  // Engines (inizializzati una volta e ricaricati)
  private final FsPolicyEngine fsEngine = new FsPolicyEngine();
  private final NetPolicyEngine netEngine = new NetPolicyEngine();
  private final QuotaPolicyEngine quotaEngine = new QuotaPolicyEngine();

  @PostConstruct
  void init() {
    reload();
  }

  public synchronized void reload() {
    var constitution = loadYaml("constitution.yml", Object.class);
    var fsPolicy     = loadYaml("policy.filesystem.yml", FsPolicyModel.class);
    var netPolicy    = loadYaml("policy.network.yml", NetPolicyModel.class);
    var quotasPolicy = loadYaml("policy.quotas.yml", QuotaPolicyModel.class);

    // carica nei motori
    if (fsPolicy != null)  fsEngine.load(fsPolicy);
    if (netPolicy != null) netEngine.load(netPolicy);
    if (quotasPolicy != null) quotaEngine.load(quotasPolicy);

    this.model = new PolicyModel(constitution, fsPolicy, netPolicy, quotasPolicy);
    this.lastReload = Instant.now();
  }

  private <T> T loadYaml(String resourceName, Class<T> type) {
    ClassLoader cl = Thread.currentThread().getContextClassLoader();
    try (InputStream is = cl.getResourceAsStream(resourceName)) {
      if (is == null) return null;
      return new Yaml().loadAs(is, type);
    } catch (Exception e) {
      throw new RuntimeException("Failed loading " + resourceName + ": " + e.getMessage(), e);
    }
  }

  public Decision check(Action action) {
    if (action == null || action.type == null) {
      return new Decision(Decision.Effect.DENY, "invalid action");
    }

    switch (action.type) {
      case FS_READ:
      case FS_WRITE:
      case FS_DELETE: {
        String path = action.getString("path");
        return fsEngine.decide(action.type, path);
      }
      case NET_CONNECT:
      case NET_DNS: {
        String host = action.getString("host");
        Integer port = action.getInt("port", null);
        return netEngine.decide(action.type, host, port);
      }
      case QUOTA_CONSUME: {
        return quotaEngine.decide(action);
      }
      default:
        return new Decision(Decision.Effect.DENY, "unsupported action");
    }
  }

  public Instant getLastReload() { return lastReload; }
  public PolicyModel getModel() { return model; }

  public static final class PolicyModel {
    public final Object constitution;
    public final Object fs;
    public final Object net;
    public final Object quotas;
    public PolicyModel(Object c, Object f, Object n, Object q) {
      this.constitution = c; this.fs = f; this.net = n; this.quotas = q;
    }
  }
  
//in PolicyEnforcer
public QuotaTracker getQuotaTracker() { return quotas; }

}

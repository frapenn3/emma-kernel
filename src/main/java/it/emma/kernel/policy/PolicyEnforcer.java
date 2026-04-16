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
import it.emma.kernel.core.ConsentGate;
import it.emma.kernel.dto.Approval;
import it.emma.kernel.dto.ProposalSummary;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class PolicyEnforcer {
  @Inject ConsentGate consentGate;

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

    Decision raw;
    switch (action.type) {
      case FS_READ:
      case FS_WRITE:
      case FS_DELETE: {
        String path = action.getString("path");
        raw = fsEngine.decide(action.type, path);
        break;
      }
      case NET_CONNECT:
      case NET_DNS: {
        String host = action.getString("host");
        Integer port = action.getInt("port", null);
        raw = netEngine.decide(action.type, host, port);
        break;
      }
      case QUOTA_CONSUME: {
        raw = quotaEngine.decide(action);
        break;
      }
      default:
        return new Decision(Decision.Effect.DENY, "unsupported action");
    }
    return maybeResolveAsk(action, raw);
  }

  private Decision maybeResolveAsk(Action action, Decision raw) {
    if (raw == null || raw.effect != Decision.Effect.ASK) return raw;

    boolean requireExplicit = Boolean.parseBoolean(action.getString("require_explicit_approval", "false"));
    Approval requested = parseApproval(action.getString("approval", null));
    if (requireExplicit && requested == null) {
      return new Decision(Decision.Effect.DENY, "explicit approval required");
    }

    ProposalSummary summary = new ProposalSummary();
    summary.id = action.getString("subject", "policy:" + action.type.name());
    summary.objective = buildObjective(action);

    Approval finalApproval = consentGate.request(summary.id, summary, requested);
    if (finalApproval == Approval.YES) {
      return new Decision(Decision.Effect.ALLOW, "approved by consent gate");
    }
    return new Decision(Decision.Effect.DENY, "rejected by consent gate");
  }

  private Approval parseApproval(String raw) {
    if (raw == null || raw.isBlank()) return null;
    try {
      return Approval.valueOf(raw.trim().toUpperCase());
    } catch (Exception ignored) {
      return null;
    }
  }

  private String buildObjective(Action action) {
    return switch (action.type) {
      case FS_READ, FS_WRITE, FS_DELETE ->
          "filesystem " + action.type.name() + " path=" + action.getString("path", "");
      case NET_CONNECT, NET_DNS ->
          "network " + action.type.name() + " host=" + action.getString("host", "") +
              " port=" + action.getString("port", "");
      case QUOTA_CONSUME -> "quota consume";
    };
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

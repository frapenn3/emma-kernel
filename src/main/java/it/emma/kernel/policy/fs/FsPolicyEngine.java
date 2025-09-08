package it.emma.kernel.policy.fs;

import it.emma.kernel.policy.Action;
import it.emma.kernel.policy.Decision;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;

import org.yaml.snakeyaml.Yaml;

public class FsPolicyEngine {

  /* ----------------- Modello compilato ----------------- */

  static final class CompiledRule {
    final List<String> ops;    // in UPPERCASE (es. READ/WRITE/DELETE o "*")
    final PathMatcher matcher; // glob matcher
    final Decision.Effect effect;
    final String reason;

    CompiledRule(List<String> ops, String glob, Decision.Effect e, String reason){
      this.ops = ops;
      this.matcher = FileSystems.getDefault().getPathMatcher("glob:" + glob);
      this.effect = e;
      this.reason = reason;
    }

    boolean applies(Action.Type t, Path normalized){
      String op = fsOpOf(t);
      if (op == null) return false;
      boolean opOk = ops.contains("*") || ops.contains(op);
      return opOk && matcher.matches(normalized);
    }
  }

  private volatile List<CompiledRule> rules = List.of();

  /* ----------------- API “core” ----------------- */

  static String fsOpOf(Action.Type t){
    return switch (t) {
      case FS_READ   -> "READ";
      case FS_WRITE  -> "WRITE";
      case FS_DELETE -> "DELETE";
      default        -> null;
    };
  }

  /** Carica le regole da modello deserializzato. */
  public void load(FsPolicyModel model){
    List<CompiledRule> list = new ArrayList<>();
    if (model != null && model.rules != null){
      for (FsPolicyModel.Rule r : model.rules){
        var ops = (r.op == null || r.op.isEmpty())
            ? List.of("*")
            : r.op.stream().map(s -> s == null ? "" : s.toUpperCase()).toList();

        var eff = Decision.Effect.valueOf(r.effect.toUpperCase());
        list.add(new CompiledRule(ops, normalizeGlob(r.path), eff, r.reason));
      }
    }
    this.rules = List.copyOf(list);
  }

  /** Esegue la decisione dati tipo operazione FS e path “raw”. */
  public Decision decide(Action.Type t, String rawPath){
    Path p = normalizePath(rawPath);
    for (CompiledRule r : rules){
      if (r.applies(t, p)) {
        return new Decision(r.effect, r.reason != null ? r.reason : "fs rule");
      }
    }
    return new Decision(Decision.Effect.DENY, "no matching fs rule");
  }

  /* ----------------- Adapter per PolicyEnforcer ----------------- */

  /** Carica regole da YAML testuale (usa SnakeYAML). */
  public void load(String yamlText){
    if (yamlText == null || yamlText.isBlank()) {
      this.rules = List.of();
      return;
    }
    Yaml yaml = new Yaml();
    FsPolicyModel model = yaml.loadAs(yamlText, FsPolicyModel.class);
    this.load(model);
  }

  /** Accetta un'Action e delega a {@link #decide(Action.Type, String)}. */
  public Decision check(Action action){
    if (action == null || action.type == null) {
      return new Decision(Decision.Effect.DENY, "invalid action");
    }
    String path = null;
    if (action.params != null) {
      Object v = action.params.get("path");
      if (v != null) path = String.valueOf(v);
    }
    return this.decide(action.type, path);
  }

  /* ----------------- Helpers ----------------- */

  private static Path normalizePath(String raw){
    if (raw == null || raw.isBlank()) return Paths.get("");
    String s = raw.replace('\\','/');   // normalizza slash
    if (s.startsWith("./")) s = s.substring(2);
    if (s.startsWith("/"))  s = s.substring(1);
    return Paths.get(s);
  }

  private static String normalizeGlob(String g){
    if (g == null || g.isBlank()) return "**";
    String s = g.replace('\\','/');
    if (s.startsWith("./")) s = s.substring(2);
    if (s.startsWith("/"))  s = s.substring(1);
    return s;
  }
}

package it.emma.kernel.policy.net;

import it.emma.kernel.policy.Action;
import it.emma.kernel.policy.Decision;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class NetPolicyEngine {

  static final class CompiledRule {
    final List<String> ops; // upper; "*" = tutti
    final Pattern hostRegex; // da glob a regex
    final PortSpec portSpec; // può essere null
    final Decision.Effect effect;
    final String reason;

    CompiledRule(List<String> ops, String hostGlob, String port, Decision.Effect eff, String reason){
      this.ops = ops == null || ops.isEmpty() ? List.of("*")
              : ops.stream().map(String::toUpperCase).toList();
      this.hostRegex = Pattern.compile(globToRegex(hostGlob == null ? "*" : hostGlob), Pattern.CASE_INSENSITIVE);
      this.portSpec = PortSpec.parse(port);
      this.effect = eff;
      this.reason = reason;
    }

    boolean applies(Action.Type t, String host, Integer port){
      String op = switch (t) {
        case NET_CONNECT -> "CONNECT";
        case NET_DNS     -> "DNS";
        default          -> null;
      };
      if (op == null) return false;

      boolean opOk = ops.contains("*") || ops.contains(op);
      if (!opOk) return false;

      String h = host == null ? "" : host;
      if (!hostRegex.matcher(h).matches()) return false;

      if (portSpec == null) return true; // nessun vincolo di porta
      return port != null && portSpec.matches(port);
    }
  }

  // singola porta o range
  static final class PortSpec {
    final int start;
    final int end;

    PortSpec(int s, int e){ this.start = s; this.end = e; }

    static PortSpec parse(String s){
      if (s == null || s.isBlank()) return null;
      String str = s.trim();
      if (str.contains("-")){
        String[] p = str.split("-", 2);
        try {
          int a = Integer.parseInt(p[0].trim());
          int b = Integer.parseInt(p[1].trim());
          return new PortSpec(Math.min(a,b), Math.max(a,b));
        } catch (Exception ignored) { return null; }
      } else {
        try {
          int p = Integer.parseInt(str);
          return new PortSpec(p, p);
        } catch (Exception ignored) { return null; }
      }
    }

    boolean matches(int p){ return p >= start && p <= end; }
  }

  private volatile List<CompiledRule> rules = List.of();

  public void load(NetPolicyModel model){
    List<CompiledRule> list = new ArrayList<>();
    if (model != null && model.rules != null){
      for (NetPolicyModel.Rule r : model.rules){
        Decision.Effect eff = Decision.Effect.valueOf(
            (r.effect == null ? "DENY" : r.effect).toUpperCase());
        list.add(new CompiledRule(r.op, r.host, r.port, eff, r.reason));
      }
    }
    this.rules = List.copyOf(list);
  }

  public Decision decide(Action.Type t, String host, Integer port){
    for (CompiledRule r : rules){
      if (r.applies(t, host, port)){
        return new Decision(r.effect, r.reason != null ? r.reason : "net rule");
      }
    }
    return new Decision(Decision.Effect.DENY, "no matching net rule");
  }

  // ---- helper glob -> regex
  private static String globToRegex(String glob){
    StringBuilder sb = new StringBuilder();
    sb.append('^');
    for (int i = 0; i < glob.length(); i++){
      char c = glob.charAt(i);
      switch (c){
        case '*': sb.append(".*"); break;
        case '?': sb.append('.');  break;
        case '.': sb.append("\\."); break;
        case '\\': sb.append("\\\\"); break;
        default: sb.append(c);
      }
    }
    sb.append('$');
    return sb.toString();
  }
}

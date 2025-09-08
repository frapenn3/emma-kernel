package it.emma.kernel.policy;

import java.util.Map;

public final class Action {

  public enum Type {
    FS_READ, FS_WRITE, FS_DELETE,
    NET_CONNECT, NET_DNS,
    QUOTA_CONSUME
  }

  public Type type;
  public Map<String, Object> params;

  public Action() {}

  public Action(Type type, Map<String, Object> params) {
    this.type = type;
    this.params = params;
  }

  // ---------- helpers di lettura ----------

  public String getString(String key) { return getString(key, null); }

  public String getString(String key, String def) {
    if (params == null) return def;
    Object v = params.get(key);
    if (v == null) return def;
    return String.valueOf(v);
  }

  public Integer getInt(String key) { return getInt(key, null); }

  public Integer getInt(String key, Integer def) {
    if (params == null) return def;
    Object v = params.get(key);
    if (v == null) return def;
    if (v instanceof Number n) return n.intValue();
    try { return Integer.parseInt(String.valueOf(v)); }
    catch (Exception e) { return def; }
  }

  // opzionale: set
  public Action put(String key, Object value) {
    if (params != null) params.put(key, value);
    return this;
  }
}

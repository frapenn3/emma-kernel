package it.emma.kernel.dto;
import com.fasterxml.jackson.databind.ObjectMapper;
public class AuditEntry {
  public String ts; public String phase; public String improvement_id; public String detail;
  public static AuditEntry simple(String phase, String id, String detail){
    var a = new AuditEntry(); a.ts = java.time.Instant.now().toString(); a.phase = phase; a.improvement_id = id; a.detail = detail; return a;
  }
  public String toJson(){ try { return new ObjectMapper().writeValueAsString(this);} catch(Exception e){ return "{}"; } }
}

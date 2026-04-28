package it.emma.kernel.api;

import java.util.List;

import it.emma.kernel.core.AuditService;
import it.emma.kernel.persist.AuditDoc;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

@Path("/kernel/audit")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class AuditResource {

  @Inject AuditService auditService;

  @GET
  public AuditEntries search(
      @QueryParam("subject") String subject,
      @QueryParam("event") String event,
      @QueryParam("limit") @DefaultValue("50") int limit) {
    return new AuditEntries(auditService.search(subject, event, limit));
  }

  @GET
  @Path("/tail")
  public AuditTail tail(@QueryParam("n") @DefaultValue("20") int n) {
    return new AuditTail(auditService.tailLines(n));
  }

  public static final class AuditEntries {
    public List<AuditDoc> entries;
    public AuditEntries() {}
    public AuditEntries(List<AuditDoc> entries) { this.entries = entries; }
  }

  public static final class AuditTail {
    public List<String> lines;
    public AuditTail() {}
    public AuditTail(List<String> lines) { this.lines = lines; }
  }
}

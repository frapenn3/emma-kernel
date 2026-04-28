package it.emma.kernel.api;

import it.emma.kernel.core.AuditService;
import it.emma.kernel.core.KernelGuard;
import it.emma.kernel.core.ProposalService;
import it.emma.kernel.dto.KernelStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/kernel")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class KernelResource {

  @Inject ProposalService proposals;
  @Inject AuditService auditService;
  @Inject KernelGuard guard;

  @GET
  @Path("/status")
  public KernelStatus status() {
    return proposals.status();
  }

  @POST
  @Path("/killswitch")
  @Consumes(MediaType.WILDCARD)
  public SimpleStatus killswitch() {
    guard.stop();
    auditService.record("KILL", "global", "manual");
    return new SimpleStatus("STOPPED");
  }

  @POST
  @Path("/resume")
  @Consumes(MediaType.WILDCARD)
  public SimpleStatus resume() {
    guard.resume();
    auditService.record("RESUME", "global", "manual");
    return new SimpleStatus("RESUMED");
  }

  public static final class SimpleStatus {
    public String status;
    public SimpleStatus() {}
    public SimpleStatus(String status) { this.status = status; }
  }
}

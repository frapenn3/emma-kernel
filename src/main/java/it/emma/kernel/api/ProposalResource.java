package it.emma.kernel.api;

import it.emma.kernel.core.AuditService;
import it.emma.kernel.core.ProposalService;
import it.emma.kernel.dto.Proposal;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/kernel/proposals")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class ProposalResource {

  @Inject ProposalService proposals;
  @Inject AuditService auditService;

  @POST
  public ProposalService.SubmitResult submit(Proposal proposal) {
    return proposals.submit(proposal);
  }

  @GET
  public ProposalService.ProposalList list(@QueryParam("state") String state) {
    return proposals.list(state);
  }

  @GET
  @Path("/{id}")
  public Proposal get(@PathParam("id") String id) {
    return proposals.get(id);
  }

  @POST
  @Path("/{id}/approve")
  public ProposalService.ApproveResult approve(@PathParam("id") String id, String body) {
    return proposals.approve(id, body);
  }

  @POST
  @Path("/{id}/advance")
  public ProposalService.StateChangeResult advance(@PathParam("id") String id, ProposalService.StateChangeRequest req) {
    return proposals.advance(id, req);
  }

  @POST
  @Path("/{id}/revert")
  @Consumes(MediaType.WILDCARD)
  public ProposalService.StateChangeResult revert(@PathParam("id") String id) {
    return proposals.revert(id);
  }

  @POST
  @Path("/{id}/complete")
  @Consumes(MediaType.WILDCARD)
  public ProposalService.StateChangeResult complete(@PathParam("id") String id) {
    return proposals.complete(id);
  }

  @GET
  @Path("/{id}/audit")
  public AuditResource.AuditEntries audit(@PathParam("id") String id, @QueryParam("limit") @DefaultValue("50") int limit) {
    if (!proposals.exists(id)) {
      throw new ApiException(404, "PROPOSAL_NOT_FOUND", "Unknown proposal id: " + id);
    }
    return new AuditResource.AuditEntries(auditService.search(id, null, limit));
  }
}

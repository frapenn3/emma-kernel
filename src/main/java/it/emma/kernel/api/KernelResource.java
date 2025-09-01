package it.emma.kernel.api;
import it.emma.kernel.core.*; import it.emma.kernel.dto.*;
import jakarta.enterprise.context.ApplicationScoped; import jakarta.inject.Inject;
import jakarta.ws.rs.*; import jakarta.ws.rs.core.MediaType;
import java.util.*;
@Path("/kernel") @Produces(MediaType.APPLICATION_JSON) @Consumes(MediaType.APPLICATION_JSON) @ApplicationScoped
public class KernelResource {
  @Inject ImprovementFSM fsm; @Inject ConsentGate consent; @Inject AuditLog audit;
  private final Map<String, Proposal> proposals = Collections.synchronizedMap(new LinkedHashMap<>());
  @GET @Path("/status") public KernelStatus status(){
    var ks = new KernelStatus(); ks.state = fsm.current("global"); ks.openProposals = new ArrayList<>(proposals.values()); return ks; }
  @POST @Path("/proposals") public Map<String,String> submit(Proposal p){
    proposals.put(p.id, p); fsm.advance(p.id, ImprovementFSM.State.PROPOSE);
    audit.record(AuditEntry.simple("PROPOSE", p.id, "submitted")); return Map.of("status","OK","id",p.id); }
  @POST @Path("/proposals/{id}/approve") public Map<String,String> approve(@PathParam("id") String id, Approval a){
    var summary = new ProposalSummary(); summary.id = id; summary.objective = proposals.get(id).objective;
    var ap = consent.request(id, summary); if(ap == Approval.YES){ fsm.advance(id, ImprovementFSM.State.RESEARCH); }
    audit.record(AuditEntry.simple("APPROVAL", id, ap.name())); return Map.of("status", ap.name()); }
  @POST @Path("/killswitch") public Map<String,String> killswitch(){
    audit.record(AuditEntry.simple("KILL","global","manual")); return Map.of("status","STOPPED"); }
}

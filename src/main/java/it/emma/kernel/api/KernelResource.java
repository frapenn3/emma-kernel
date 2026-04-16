package it.emma.kernel.api;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.quarkus.panache.common.Sort;
import it.emma.kernel.core.AuditLog;
import it.emma.kernel.core.ConsentGate;
import it.emma.kernel.core.ImprovementFSM;
import it.emma.kernel.core.KernelGuard;
import it.emma.kernel.dto.Approval;
import it.emma.kernel.dto.AuditEntry;
import it.emma.kernel.dto.KernelStatus;
import it.emma.kernel.dto.Proposal;
import it.emma.kernel.dto.ProposalSummary;
import it.emma.kernel.persist.AuditRepo;
import it.emma.kernel.persist.ProposalDoc;
import it.emma.kernel.persist.ProposalRepo;
import it.emma.kernel.policy.PolicyLoader;
import it.emma.kernel.policy.PolicyEnforcer;
import it.emma.kernel.policy.Action;
import it.emma.kernel.policy.Decision;
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
import jakarta.xml.bind.DatatypeConverter;

/**
 * Kernel REST API (persistenza su Mongo) — tipi espliciti ovunque.
 */
@Path("/kernel")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class KernelResource {

  @Inject ImprovementFSM fsm;
  @Inject ConsentGate   consent;
  @Inject AuditLog      audit;
  @Inject KernelGuard   guard;
  @Inject ProposalRepo  repo;
  @Inject AuditRepo     auditRepo;
  @Inject PolicyLoader  loader;
  @Inject PolicyEnforcer enforcer;



  // -------------------- STATUS --------------------

  @GET
  @Path("/status")
  public KernelStatus status() {
    KernelStatus ks = new KernelStatus();
    LinkedHashMap<String, ImprovementFSM.State> map =
        new LinkedHashMap<String, ImprovementFSM.State>();

    List<ProposalDoc> docs = repo.findAll(Sort.ascending("_id")).list();
    ks.openProposals = new ArrayList<Proposal>(docs.size());

    for (ProposalDoc d : docs) {
      Proposal p = toDto(d);
      ks.openProposals.add(p);

      ImprovementFSM.State st = (d.state != null)
          ? ImprovementFSM.State.valueOf(d.state)
          : fsm.current(p.id);
      map.put(p.id, st);
    }

    ks.proposalStates = map;
    ks.stopped = guard.isStopped();
    ks.state   = aggregate(map);
    return ks;
  }

  private ImprovementFSM.State aggregate(Map<String, ImprovementFSM.State> m){
    ImprovementFSM.State[] order = new ImprovementFSM.State[] {
        ImprovementFSM.State.APPLY, ImprovementFSM.State.APPLY_WAIT,
        ImprovementFSM.State.MONITOR, ImprovementFSM.State.REVIEW,
        ImprovementFSM.State.TEST, ImprovementFSM.State.BUILD,
        ImprovementFSM.State.RESEARCH, ImprovementFSM.State.PROPOSE
    };
    HashSet<ImprovementFSM.State> values = new HashSet<ImprovementFSM.State>(m.values());
    for (ImprovementFSM.State s : order) {
      if (values.contains(s)) return s;
    }
    return ImprovementFSM.State.IDLE;
  }

  // -------------------- PROPOSALS --------------------

  @POST
  @Path("/proposals")
  public Response submit(Proposal p) {
    if (guard.isStopped()) {
      return error(423, "Kernel stopped by killswitch");
    }
    if (p == null || p.id == null || p.id.isBlank()) {
      return error(400, "Proposal must have a non-empty 'id'");
    }
    ProposalDoc existing = repo.findById(p.id);
    if (existing != null) {
      return error(409, "Duplicate id: " + p.id);
    }

    ProposalDoc d = toDoc(p);
    d.state = "PROPOSE";
    repo.persist(d);

    fsm.advance(p.id, ImprovementFSM.State.PROPOSE);
    audit.record(AuditEntry.simple("PROPOSE", p.id, "submitted"));
    auditRepo.persist(it.emma.kernel.persist.AuditDoc.of("PROPOSE", p.id, "submitted"));

    return ok(new SubmitResult("OK", p.id));
  }

  @POST
  @Path("/proposals/{id}/approve")
  public Response approve(@PathParam("id") String id, String body) {
    if (guard.isStopped()) {
      return error(423, "Kernel stopped by killswitch");
    }

    ProposalDoc d = repo.findById(id);
    if (d == null) {
      return error(404, "Unknown proposal id: " + id);
    }

    String b = (body == null) ? "" : body.trim().replace("\"", "");
    Approval requested;
    try {
      requested = Approval.valueOf(b);
    } catch (Exception e) {
      return error(400, "Body must be YES or NO");
    }

    Proposal p = toDto(d);
    ProposalSummary summary = new ProposalSummary();
    summary.id = id;
    summary.objective = p.objective;

    Approval decision = consent.request(id, summary, requested);

    if (decision == Approval.YES) {
      fsm.advance(id, ImprovementFSM.State.RESEARCH);
      d.state = "RESEARCH";
    } else {
      try {
        fsm.advance(id, ImprovementFSM.State.REVERT);
      } catch (Exception ignored) {}
      d.state = "REVERT";
    }
    repo.persistOrUpdate(d);

    audit.record(AuditEntry.simple("APPROVAL", id, decision.name()));
    auditRepo.persist(it.emma.kernel.persist.AuditDoc.of("APPROVAL", id, decision.name()));

    return ok(new ApproveResult(decision.name()));
  }

  // -------------------- KILL / RESUME --------------------

  @POST
  @Path("/killswitch")
  @Consumes(MediaType.WILDCARD)
  public Response killswitch() {
    guard.stop();
    audit.record(AuditEntry.simple("KILL","global","manual"));
    auditRepo.persist(it.emma.kernel.persist.AuditDoc.of("KILL", "global", "manual"));
    return ok(new SimpleStatus("STOPPED"));
  }

  @POST
  @Path("/resume")
  @Consumes(MediaType.WILDCARD)
  public Response resume(){
    guard.resume();
    audit.record(AuditEntry.simple("RESUME","global","manual"));
    auditRepo.persist(it.emma.kernel.persist.AuditDoc.of("RESUME", "global", "manual"));
    return ok(new SimpleStatus("RESUMED"));
  }

  // -------------------- AUDIT --------------------

  @GET
  @Path("/audit/tail")
  public Response auditTail(@QueryParam("n") @DefaultValue("20") int n){
    try {
      int safeN = Math.max(1, n);

      // 1) Prova da Mongo (più recente prima)
      java.util.List<it.emma.kernel.persist.AuditDoc> docs =
          auditRepo.findAll(io.quarkus.panache.common.Sort.descending("ts"))
                   .page(io.quarkus.panache.common.Page.of(0, safeN))
                   .list();

      java.util.ArrayList<String> lines = new java.util.ArrayList<String>(docs.size());
      for (it.emma.kernel.persist.AuditDoc d : docs) {
        String when = (d.ts != null) ? DatatypeConverter.printDateTime(toCalendar(d.ts)) : "";
        String ev  = (d.event   != null) ? d.event   : "";
        String sub = (d.subject != null) ? d.subject : "";
        String det = (d.detail  != null) ? d.detail  : "";
        lines.add(when + " [" + ev + "] " + sub + " - " + det);
      }
      java.util.Collections.reverse(lines); // naturale ascendente

      // 2) Se Mongo è vuoto, fallback su file locale "audit.log" protetto da policy FS_READ
      if (lines.isEmpty()) {
        java.util.HashMap<String,Object> p = new java.util.HashMap<String,Object>();
        p.put("path", "audit.log");
        Decision dec = enforcer.check(new Action(Action.Type.FS_READ, p));
        if (dec.effect == Decision.Effect.DENY) {
          return error(423, "Policy DENY: " + dec.reason);
        }
        // ASK è gestito dentro l'enforcer via ConsentGate → qui arriva già ALLOW/DENY finale

        java.nio.file.Path f = java.nio.file.Path.of("audit.log");
        if (java.nio.file.Files.exists(f)) {
          java.util.List<String> all = java.nio.file.Files.readAllLines(f);
          int from = Math.max(0, all.size() - safeN);
          lines = new java.util.ArrayList<String>(all.subList(from, all.size()));
        }
      }

      return ok(new AuditTail(lines));
    } catch(Exception e){
      return error(500, e.toString());
    }
  }

  private static java.util.Calendar toCalendar(java.util.Date date) {
    java.util.Calendar c = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"));
    c.setTime(date);
    return c;
  }

  // -------------------- MAPPERS (DTO <-> DOC) --------------------

  private Proposal toDto(ProposalDoc d){
    Proposal p = new Proposal();
    p.id = d.id;
    p.objective = d.objective;
    p.hypothesis = d.hypothesis;
    p.scope = d.scope;
    p.risks = d.risks;

    if (d.plan != null) {
      ArrayList<Proposal.PlanItem> list = new ArrayList<Proposal.PlanItem>(d.plan.size());
      for (Map<String,String> m : d.plan) {
        Proposal.PlanItem it = new Proposal.PlanItem();
        it.key = m.getOrDefault("key", null);
        it.value = m.getOrDefault("value", null);
        list.add(it);
      }
      p.plan = list;
    }

    if (d.success_metrics != null) {
      Proposal.SuccessMetrics sm = new Proposal.SuccessMetrics();
      sm.quality_gain = d.success_metrics.getOrDefault("quality_gain", null);
      sm.latency_p95  = d.success_metrics.getOrDefault("latency_p95", null);
      sm.errors       = d.success_metrics.getOrDefault("errors", null);
      p.success_metrics = sm;
    }

    if (d.cost_caps != null) {
      Proposal.CostCaps cc = new Proposal.CostCaps();
      cc.time_min     = getInt(d.cost_caps.get("time_min"));
      cc.cpu_cores    = getInt(d.cost_caps.get("cpu_cores"));
      cc.net_requests = getInt(d.cost_caps.get("net_requests"));
      p.cost_caps = cc;
    }

    p.rollback_plan = d.rollback_plan;
    p.evidence_requirements = d.evidence_requirements;
    return p;
  }

  private ProposalDoc toDoc(Proposal p){
    ProposalDoc d = new ProposalDoc();
    d.id = p.id;
    d.objective = p.objective;
    d.hypothesis = p.hypothesis;
    d.scope = p.scope;
    d.risks = p.risks;

    if (p.plan != null) {
      ArrayList<Map<String,String>> list = new ArrayList<Map<String,String>>(p.plan.size());
      for (Proposal.PlanItem it : p.plan) {
        LinkedHashMap<String,String> m = new LinkedHashMap<String,String>();
        m.put("key",   it.key);
        m.put("value", it.value);
        list.add(m);
      }
      d.plan = list;
    }

    if (p.success_metrics != null) {
      LinkedHashMap<String,String> sm = new LinkedHashMap<String,String>();
      sm.put("quality_gain", p.success_metrics.quality_gain);
      sm.put("latency_p95",  p.success_metrics.latency_p95);
      sm.put("errors",       p.success_metrics.errors);
      d.success_metrics = sm;
    }

    if (p.cost_caps != null) {
      LinkedHashMap<String,Number> cc = new LinkedHashMap<String,Number>();
      cc.put("time_min",     p.cost_caps.time_min);
      cc.put("cpu_cores",    p.cost_caps.cpu_cores);
      cc.put("net_requests", p.cost_caps.net_requests);
      d.cost_caps = cc;
    }

    d.rollback_plan = p.rollback_plan;
    d.evidence_requirements = p.evidence_requirements;
    return d;
  }

  private int getInt(Number n){
    return (n == null) ? 0 : n.intValue();
  }

  // -------------------- DTO di risposta tipizzati --------------------

  public static final class SimpleStatus {
    public String status;
    public SimpleStatus() {}
    public SimpleStatus(String status) { this.status = status; }
  }

  public static final class SubmitResult {
    public String status;
    public String id;
    public SubmitResult() {}
    public SubmitResult(String status, String id) {
      this.status = status;
      this.id = id;
    }
  }

  public static final class ApproveResult {
    public String status;
    public ApproveResult() {}
    public ApproveResult(String status) { this.status = status; }
  }

  public static final class AuditTail {
    public List<String> lines;
    public AuditTail() {}
    public AuditTail(List<String> lines) { this.lines = lines; }
  }

  public static final class ErrorResponse {
    public String error;
    public ErrorResponse() {}
    public ErrorResponse(String error) { this.error = error; }
  }

  // -------------------- Response helpers --------------------

  private Response ok(Object entity){
    return Response.ok(entity).build();
  }

  private Response error(int code, String msg){
    return Response.status(code).entity(new ErrorResponse(msg)).build();
  }
}

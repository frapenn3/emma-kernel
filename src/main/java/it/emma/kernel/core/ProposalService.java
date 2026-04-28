package it.emma.kernel.core;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.quarkus.panache.common.Sort;
import it.emma.kernel.api.ApiException;
import it.emma.kernel.dto.Approval;
import it.emma.kernel.dto.KernelStatus;
import it.emma.kernel.dto.Proposal;
import it.emma.kernel.dto.ProposalSummary;
import it.emma.kernel.persist.ProposalDoc;
import it.emma.kernel.persist.ProposalRepo;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class ProposalService {

  @Inject ImprovementFSM fsm;
  @Inject ConsentGate consent;
  @Inject KernelGuard guard;
  @Inject ProposalRepo repo;
  @Inject AuditService auditService;

  private static final Map<ImprovementFSM.State, Set<ImprovementFSM.State>> ALLOWED_TRANSITIONS = Map.of(
      ImprovementFSM.State.IDLE, Set.of(ImprovementFSM.State.PROPOSE),
      ImprovementFSM.State.PROPOSE, Set.of(ImprovementFSM.State.RESEARCH, ImprovementFSM.State.REVERT),
      ImprovementFSM.State.RESEARCH, Set.of(ImprovementFSM.State.BUILD, ImprovementFSM.State.REVIEW, ImprovementFSM.State.REVERT),
      ImprovementFSM.State.BUILD, Set.of(ImprovementFSM.State.TEST, ImprovementFSM.State.REVIEW, ImprovementFSM.State.REVERT),
      ImprovementFSM.State.TEST, Set.of(ImprovementFSM.State.REVIEW, ImprovementFSM.State.REVERT),
      ImprovementFSM.State.REVIEW, Set.of(ImprovementFSM.State.APPLY_WAIT, ImprovementFSM.State.REVERT),
      ImprovementFSM.State.APPLY_WAIT, Set.of(ImprovementFSM.State.APPLY, ImprovementFSM.State.REVERT),
      ImprovementFSM.State.APPLY, Set.of(ImprovementFSM.State.MONITOR, ImprovementFSM.State.REVERT),
      ImprovementFSM.State.MONITOR, Set.of(ImprovementFSM.State.DONE, ImprovementFSM.State.REVERT),
      ImprovementFSM.State.REVERT, Set.of(ImprovementFSM.State.DONE)
  );

  public KernelStatus status() {
    KernelStatus ks = new KernelStatus();
    LinkedHashMap<String, ImprovementFSM.State> map = new LinkedHashMap<String, ImprovementFSM.State>();
    List<ProposalDoc> docs = repo.findAll(Sort.ascending("_id")).list();
    ks.openProposals = new ArrayList<Proposal>(docs.size());
    ks.allProposals = new ArrayList<ProposalSummary>(docs.size());

    for (ProposalDoc d : docs) {
      ImprovementFSM.State st = stateOf(d);
      if (st == ImprovementFSM.State.PROPOSE) {
        ks.openProposals.add(toDto(d, st));
      }
      ks.allProposals.add(toSummary(d, st));
      map.put(d.id, st);
    }

    ks.proposalStates = map;
    ks.stopped = guard.isStopped();
    ks.state = aggregate(map);
    return ks;
  }

  public SubmitResult submit(Proposal p) {
    requireRunning();
    validateProposalForSubmit(p);
    p.id = p.id.trim();
    p.objective = p.objective.trim();

    if (repo.findById(p.id) != null) {
      throw new ApiException(409, "DUPLICATE_PROPOSAL_ID", "Duplicate id: " + p.id);
    }

    Date now = new Date();
    ProposalDoc d = toDoc(p);
    d.state = ImprovementFSM.State.PROPOSE.name();
    d.createdBy = (p.createdBy == null || p.createdBy.isBlank()) ? "console" : p.createdBy.trim();
    d.createdAt = now;
    d.updatedAt = now;
    d.version = 1L;
    repo.persist(d);

    fsm.advance(p.id, ImprovementFSM.State.PROPOSE);
    auditService.record("PROPOSE", p.id, "submitted");
    return new SubmitResult("OK", p.id);
  }

  public ProposalList list(String state) {
    ImprovementFSM.State requested = parseOptionalState(state);
    List<ProposalDoc> docs = repo.findAll(Sort.ascending("_id")).list();
    ArrayList<Proposal> result = new ArrayList<Proposal>(docs.size());
    for (ProposalDoc d : docs) {
      ImprovementFSM.State st = stateOf(d);
      if (requested == null || requested == st) {
        result.add(toDto(d, st));
      }
    }
    return new ProposalList(result);
  }

  public Proposal get(String id) {
    ProposalDoc d = findRequired(id);
    return toDto(d, stateOf(d));
  }

  public ApproveResult approve(String id, String body) {
    requireRunning();
    ProposalDoc d = findRequired(id);
    Approval requested = parseApproval(body);
    ProposalSummary summary = toSummary(d, stateOf(d));
    Approval decision = consent.request(id, summary, requested);
    transition(d, decision == Approval.YES ? ImprovementFSM.State.RESEARCH : ImprovementFSM.State.REVERT, "APPROVAL", decision.name());
    return new ApproveResult(decision.name());
  }

  public StateChangeResult advance(String id, StateChangeRequest req) {
    requireRunning();
    ProposalDoc d = findRequired(id);
    if (req == null || req.target == null || req.target.isBlank()) {
      throw new ApiException(400, "MISSING_TARGET_STATE", "Body must contain a non-empty 'target'");
    }
    return transition(d, parseState(req.target), "ADVANCE", "manual");
  }

  public StateChangeResult revert(String id) {
    requireRunning();
    return transition(findRequired(id), ImprovementFSM.State.REVERT, "REVERT", "manual");
  }

  public StateChangeResult complete(String id) {
    requireRunning();
    return transition(findRequired(id), ImprovementFSM.State.DONE, "COMPLETE", "manual");
  }

  public boolean exists(String id) {
    return repo.findById(id) != null;
  }

  private ProposalDoc findRequired(String id) {
    ProposalDoc d = repo.findById(id);
    if (d == null) {
      throw new ApiException(404, "PROPOSAL_NOT_FOUND", "Unknown proposal id: " + id);
    }
    return d;
  }

  private void requireRunning() {
    if (guard.isStopped()) {
      throw new ApiException(423, "KERNEL_STOPPED", "Kernel stopped by killswitch");
    }
  }

  private StateChangeResult transition(ProposalDoc d, ImprovementFSM.State target, String event, String detail) {
    ImprovementFSM.State from = stateOf(d);
    if (from == target || !ALLOWED_TRANSITIONS.getOrDefault(from, Set.of()).contains(target)) {
      throw new ApiException(409, "INVALID_TRANSITION", "Invalid transition: " + from + " -> " + target);
    }
    try {
      fsm.advance(d.id, target);
    } catch (IllegalStateException ignored) {
      // Mongo state is authoritative across dev reloads.
    }
    d.state = target.name();
    d.lastTransitionReason = detail;
    d.updatedAt = new Date();
    if (target == ImprovementFSM.State.DONE) {
      d.closedAt = d.updatedAt;
    }
    d.version++;
    repo.persistOrUpdate(d);

    String auditDetail = detail + " " + from.name() + " -> " + target.name();
    auditService.recordTransition(event, d.id, auditDetail, from.name(), target.name());
    return new StateChangeResult(d.id, from.name(), target.name());
  }

  private ImprovementFSM.State aggregate(Map<String, ImprovementFSM.State> m) {
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

  private ImprovementFSM.State stateOf(ProposalDoc d) {
    if (d.state != null && !d.state.isBlank()) {
      return ImprovementFSM.State.valueOf(d.state);
    }
    return fsm.current(d.id);
  }

  private ImprovementFSM.State parseState(String state) {
    try {
      return ImprovementFSM.State.valueOf(state.trim().toUpperCase());
    } catch (Exception e) {
      throw new ApiException(400, "INVALID_STATE", "Unknown proposal state: " + state);
    }
  }

  private ImprovementFSM.State parseOptionalState(String state) {
    if (state == null || state.isBlank()) return null;
    return parseState(state);
  }

  private Approval parseApproval(String body) {
    String b = (body == null) ? "" : body.trim().replace("\"", "");
    try {
      return Approval.valueOf(b);
    } catch (Exception e) {
      throw new ApiException(400, "INVALID_APPROVAL", "Body must be YES or NO");
    }
  }

  private void validateProposalForSubmit(Proposal p) {
    if (p == null) throw new ApiException(400, "MISSING_PROPOSAL", "Proposal body is required");
    if (p.id == null || p.id.isBlank()) throw new ApiException(400, "MISSING_PROPOSAL_ID", "Proposal must have a non-empty 'id'");
    if (!p.id.trim().matches("[A-Za-z0-9._:-]+")) {
      throw new ApiException(400, "INVALID_PROPOSAL_ID", "Proposal id may contain only letters, numbers, dot, underscore, colon and dash");
    }
    if (p.objective == null || p.objective.isBlank()) throw new ApiException(400, "MISSING_OBJECTIVE", "Proposal must have a non-empty 'objective'");
  }

  private Proposal toDto(ProposalDoc d, ImprovementFSM.State state) {
    Proposal p = new Proposal();
    p.id = d.id;
    p.objective = d.objective;
    p.hypothesis = d.hypothesis;
    p.state = state.name();
    p.createdBy = d.createdBy;
    p.lastTransitionReason = d.lastTransitionReason;
    p.createdAt = d.createdAt;
    p.updatedAt = d.updatedAt;
    p.closedAt = d.closedAt;
    p.version = d.version;
    p.tags = d.tags;
    p.priority = d.priority;
    p.scope = d.scope;
    p.risks = d.risks;
    p.rollback_plan = d.rollback_plan;
    p.evidence_requirements = d.evidence_requirements;

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
      sm.latency_p95 = d.success_metrics.getOrDefault("latency_p95", null);
      sm.errors = d.success_metrics.getOrDefault("errors", null);
      p.success_metrics = sm;
    }
    if (d.cost_caps != null) {
      Proposal.CostCaps cc = new Proposal.CostCaps();
      cc.time_min = getInt(d.cost_caps.get("time_min"));
      cc.cpu_cores = getInt(d.cost_caps.get("cpu_cores"));
      cc.net_requests = getInt(d.cost_caps.get("net_requests"));
      p.cost_caps = cc;
    }
    return p;
  }

  private ProposalDoc toDoc(Proposal p) {
    ProposalDoc d = new ProposalDoc();
    d.id = p.id;
    d.objective = p.objective;
    d.hypothesis = p.hypothesis;
    d.createdBy = p.createdBy;
    d.tags = p.tags;
    d.priority = p.priority;
    d.scope = p.scope;
    d.risks = p.risks;
    d.rollback_plan = p.rollback_plan;
    d.evidence_requirements = p.evidence_requirements;

    if (p.plan != null) {
      ArrayList<Map<String,String>> list = new ArrayList<Map<String,String>>(p.plan.size());
      for (Proposal.PlanItem it : p.plan) {
        LinkedHashMap<String,String> m = new LinkedHashMap<String,String>();
        m.put("key", it.key);
        m.put("value", it.value);
        list.add(m);
      }
      d.plan = list;
    }
    if (p.success_metrics != null) {
      LinkedHashMap<String,String> sm = new LinkedHashMap<String,String>();
      sm.put("quality_gain", p.success_metrics.quality_gain);
      sm.put("latency_p95", p.success_metrics.latency_p95);
      sm.put("errors", p.success_metrics.errors);
      d.success_metrics = sm;
    }
    if (p.cost_caps != null) {
      LinkedHashMap<String,Number> cc = new LinkedHashMap<String,Number>();
      cc.put("time_min", p.cost_caps.time_min);
      cc.put("cpu_cores", p.cost_caps.cpu_cores);
      cc.put("net_requests", p.cost_caps.net_requests);
      d.cost_caps = cc;
    }
    return d;
  }

  private ProposalSummary toSummary(ProposalDoc d, ImprovementFSM.State state) {
    ProposalSummary s = new ProposalSummary();
    s.id = d.id;
    s.objective = d.objective;
    s.state = state.name();
    s.createdBy = d.createdBy;
    s.priority = d.priority;
    s.createdAt = d.createdAt;
    s.updatedAt = d.updatedAt;
    s.closedAt = d.closedAt;
    return s;
  }

  private int getInt(Number n) {
    return (n == null) ? 0 : n.intValue();
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

  public static final class ProposalList {
    public List<Proposal> proposals;
    public ProposalList() {}
    public ProposalList(List<Proposal> proposals) { this.proposals = proposals; }
  }

  public static final class StateChangeRequest {
    public String target;
  }

  public static final class StateChangeResult {
    public String id;
    public String from;
    public String state;
    public StateChangeResult() {}
    public StateChangeResult(String id, String from, String state) {
      this.id = id;
      this.from = from;
      this.state = state;
    }
  }

  public static final class ApproveResult {
    public String status;
    public ApproveResult() {}
    public ApproveResult(String status) { this.status = status; }
  }
}

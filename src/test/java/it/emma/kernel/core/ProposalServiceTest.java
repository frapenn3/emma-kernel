package it.emma.kernel.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.mongodb.panache.PanacheQuery;
import io.quarkus.panache.common.Sort;
import it.emma.kernel.api.ApiException;
import it.emma.kernel.dto.Approval;
import it.emma.kernel.dto.AuditEntry;
import it.emma.kernel.dto.KernelStatus;
import it.emma.kernel.dto.Proposal;
import it.emma.kernel.dto.ProposalSummary;
import it.emma.kernel.persist.AuditDoc;
import it.emma.kernel.persist.AuditRepo;
import it.emma.kernel.persist.ProposalDoc;
import it.emma.kernel.persist.ProposalRepo;
import it.emma.kernel.policy.Action;
import it.emma.kernel.policy.Decision;
import it.emma.kernel.policy.PolicyEnforcer;

class ProposalServiceTest {

  private ProposalService service;
  private RecordingFsm fsm;
  private RecordingConsentGate consent;
  private KernelGuard guard;
  private StubProposalRepo proposalRepo;
  private StubAuditRepo auditRepo;
  private RecordingAuditLog audit;

  @BeforeEach
  void setUp() throws Exception {
    service = new ProposalService();
    fsm = new RecordingFsm();
    consent = new RecordingConsentGate();
    guard = new KernelGuard();
    proposalRepo = new StubProposalRepo();
    auditRepo = new StubAuditRepo();
    audit = new RecordingAuditLog();

    AuditService auditService = new AuditService();
    inject(auditService, "audit", audit);
    inject(auditService, "auditRepo", auditRepo);
    inject(auditService, "enforcer", new StubPolicyEnforcer(new Decision(Decision.Effect.ALLOW, "ok")));

    inject(service, "fsm", fsm);
    inject(service, "consent", consent);
    inject(service, "guard", guard);
    inject(service, "repo", proposalRepo);
    inject(service, "auditService", auditService);
  }

  @Test
  void submitPersistsProposalAndWritesAudit() {
    Proposal proposal = new Proposal();
    proposal.id = "imp-001";
    proposal.objective = "Improve reliability";

    ProposalService.SubmitResult result = service.submit(proposal);

    assertEquals("OK", result.status);
    assertEquals("imp-001", result.id);
    assertEquals("PROPOSE", proposalRepo.store.get("imp-001").state);
    assertNotNull(proposalRepo.store.get("imp-001").createdAt);
    assertEquals(1, audit.entries.size());
    assertEquals(1, auditRepo.docs.size());
  }

  @Test
  void submitRejectsInvalidId() {
    Proposal proposal = new Proposal();
    proposal.id = "bad id";
    proposal.objective = "Invalid id";

    ApiException error = assertInstanceOf(ApiException.class, catchThrowable(() -> service.submit(proposal)));

    assertEquals(400, error.status);
    assertEquals("INVALID_PROPOSAL_ID", error.code);
  }

  @Test
  void approveYesAdvancesProposalToResearch() {
    ProposalDoc doc = doc("imp-002", "Ship change", "PROPOSE");
    proposalRepo.store.put(doc.id, doc);
    fsm.states.put(doc.id, ImprovementFSM.State.PROPOSE);

    ProposalService.ApproveResult result = service.approve("imp-002", "\"YES\"");

    assertEquals("YES", result.status);
    assertEquals("RESEARCH", proposalRepo.store.get("imp-002").state);
    assertEquals(Approval.YES, consent.lastRequested);
  }

  @Test
  void advanceRejectsInvalidTransition() {
    ProposalDoc doc = doc("imp-invalid", "Invalid", "PROPOSE");
    proposalRepo.store.put(doc.id, doc);

    ProposalService.StateChangeRequest req = new ProposalService.StateChangeRequest();
    req.target = "DONE";

    ApiException error = assertInstanceOf(ApiException.class, catchThrowable(() -> service.advance(doc.id, req)));

    assertEquals(409, error.status);
    assertEquals("INVALID_TRANSITION", error.code);
  }

  @Test
  void statusAggregatesStatesFromStoredProposals() {
    ProposalDoc review = doc("imp-review", "Review", "REVIEW");
    ProposalDoc propose = doc("imp-propose", "Propose", "PROPOSE");
    proposalRepo.store.put(review.id, review);
    proposalRepo.store.put(propose.id, propose);

    KernelStatus status = service.status();

    assertEquals(1, status.openProposals.size());
    assertEquals(2, status.allProposals.size());
    assertEquals(ImprovementFSM.State.REVIEW, status.state);
  }

  private static ProposalDoc doc(String id, String objective, String state) {
    ProposalDoc doc = new ProposalDoc();
    doc.id = id;
    doc.objective = objective;
    doc.state = state;
    return doc;
  }

  private static Throwable catchThrowable(Runnable action) {
    try {
      action.run();
      return null;
    } catch (Throwable t) {
      return t;
    }
  }

  private static void inject(Object target, String fieldName, Object value) throws Exception {
    Field field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(target, value);
  }

  static final class RecordingFsm implements ImprovementFSM {
    final Map<String, State> states = new LinkedHashMap<String, State>();
    @Override public State current(String improvementId) { return states.getOrDefault(improvementId, State.IDLE); }
    @Override public void advance(String improvementId, State target) { states.put(improvementId, target); }
  }

  static final class RecordingConsentGate extends ConsentGate {
    Approval lastRequested;
    @Override public Approval request(String id, ProposalSummary summary, Approval requested) {
      lastRequested = requested;
      return requested != null ? requested : Approval.NO;
    }
  }

  static final class RecordingAuditLog implements AuditLog {
    final List<AuditEntry> entries = new ArrayList<AuditEntry>();
    @Override public void record(AuditEntry e) { entries.add(e); }
  }

  static final class StubProposalRepo extends ProposalRepo {
    final Map<String, ProposalDoc> store = new LinkedHashMap<String, ProposalDoc>();
    @Override public ProposalDoc findById(String id) { return store.get(id); }
    @Override public void persist(ProposalDoc entity) { store.put(entity.id, entity); }
    @Override public void persistOrUpdate(ProposalDoc entity) { store.put(entity.id, entity); }
    @Override public PanacheQuery<ProposalDoc> findAll(Sort sort) { return queryFor(new ArrayList<ProposalDoc>(store.values())); }
  }

  static final class StubAuditRepo extends AuditRepo {
    final List<AuditDoc> docs = new ArrayList<AuditDoc>();
    @Override public void persist(AuditDoc entity) { docs.add(entity); }
    @Override public PanacheQuery<AuditDoc> findAll(Sort sort) { return queryFor(docs); }
  }

  static final class StubPolicyEnforcer extends PolicyEnforcer {
    Decision nextDecision;
    StubPolicyEnforcer(Decision nextDecision) { this.nextDecision = nextDecision; }
    @Override public Decision check(Action action) { return nextDecision; }
  }

  @SuppressWarnings("unchecked")
  private static <T> PanacheQuery<T> queryFor(List<T> items) {
    return (PanacheQuery<T>) Proxy.newProxyInstance(
        PanacheQuery.class.getClassLoader(),
        new Class<?>[] { PanacheQuery.class },
        (proxy, method, args) -> {
          String name = method.getName();
          if ("list".equals(name)) return items;
          if ("page".equals(name)) return proxy;
          if ("firstResultOptional".equals(name)) return items.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(items.get(0));
          if ("firstResult".equals(name)) return items.isEmpty() ? null : items.get(0);
          if ("count".equals(name)) return Long.valueOf(items.size());
          if ("hasNextPage".equals(name) || "hasPreviousPage".equals(name)) return Boolean.FALSE;
          if ("pageCount".equals(name)) return Integer.valueOf(1);
          if ("nextPage".equals(name) || "previousPage".equals(name) || "firstPage".equals(name) || "lastPage".equals(name)) return proxy;
          if ("singleResult".equals(name)) return items.get(0);
          if ("singleResultOptional".equals(name)) return items.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(items.get(0));
          return null;
        });
  }
}

package it.emma.kernel.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import it.emma.kernel.core.AuditLog;
import it.emma.kernel.core.ConsentGate;
import it.emma.kernel.core.ImprovementFSM;
import it.emma.kernel.core.KernelGuard;
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
import jakarta.ws.rs.core.Response;

class KernelResourceTest {

  private KernelResource resource;
  private RecordingFsm fsm;
  private RecordingConsentGate consent;
  private RecordingAuditLog audit;
  private KernelGuard guard;
  private StubProposalRepo proposalRepo;
  private StubAuditRepo auditRepo;
  private StubPolicyEnforcer enforcer;

  @BeforeEach
  void setUp() throws Exception {
    resource = new KernelResource();
    fsm = new RecordingFsm();
    consent = new RecordingConsentGate();
    audit = new RecordingAuditLog();
    guard = new KernelGuard();
    proposalRepo = new StubProposalRepo();
    auditRepo = new StubAuditRepo();
    enforcer = new StubPolicyEnforcer(new Decision(Decision.Effect.ALLOW, "ok"));

    inject(resource, "fsm", fsm);
    inject(resource, "consent", consent);
    inject(resource, "audit", audit);
    inject(resource, "guard", guard);
    inject(resource, "repo", proposalRepo);
    inject(resource, "auditRepo", auditRepo);
    inject(resource, "enforcer", enforcer);
  }

  @Test
  void submitReturns400WhenProposalIdIsMissing() {
    Proposal proposal = new Proposal();
    proposal.objective = "missing id";

    Response response = resource.submit(proposal);

    assertEquals(400, response.getStatus());
    KernelResource.ErrorResponse error = assertInstanceOf(KernelResource.ErrorResponse.class, response.getEntity());
    assertEquals("MISSING_PROPOSAL_ID", error.code);
    assertEquals("Proposal must have a non-empty 'id'", error.error);
  }

  @Test
  void submitPersistsProposalAndWritesAudit() {
    Proposal proposal = new Proposal();
    proposal.id = "imp-001";
    proposal.objective = "Improve reliability";

    Response response = resource.submit(proposal);

    assertEquals(200, response.getStatus());
    KernelResource.SubmitResult result = assertInstanceOf(KernelResource.SubmitResult.class, response.getEntity());
    assertEquals("OK", result.status);
    assertEquals("imp-001", result.id);
    assertNotNull(proposalRepo.store.get("imp-001"));
    assertEquals("PROPOSE", proposalRepo.store.get("imp-001").state);
    assertEquals(ImprovementFSM.State.PROPOSE, fsm.current("imp-001"));
    assertEquals(1, audit.entries.size());
    assertEquals(1, auditRepo.docs.size());
  }

  @Test
  void approveReturns404WhenProposalDoesNotExist() {
    Response response = resource.approve("missing", "\"YES\"");

    assertEquals(404, response.getStatus());
    KernelResource.ErrorResponse error = assertInstanceOf(KernelResource.ErrorResponse.class, response.getEntity());
    assertEquals("PROPOSAL_NOT_FOUND", error.code);
    assertEquals("Unknown proposal id: missing", error.error);
  }

  @Test
  void approveYesAdvancesProposalToResearch() {
    ProposalDoc doc = new ProposalDoc();
    doc.id = "imp-002";
    doc.objective = "Ship change";
    doc.state = "PROPOSE";
    proposalRepo.store.put(doc.id, doc);
    fsm.states.put(doc.id, ImprovementFSM.State.PROPOSE);

    Response response = resource.approve("imp-002", "\"YES\"");

    assertEquals(200, response.getStatus());
    KernelResource.ApproveResult result = assertInstanceOf(KernelResource.ApproveResult.class, response.getEntity());
    assertEquals("YES", result.status);
    assertEquals("RESEARCH", proposalRepo.store.get("imp-002").state);
    assertEquals(ImprovementFSM.State.RESEARCH, fsm.current("imp-002"));
    assertEquals(Approval.YES, consent.lastRequested);
  }

  @Test
  void statusAggregatesStatesFromStoredProposals() {
    ProposalDoc review = new ProposalDoc();
    review.id = "imp-review";
    review.objective = "Review";
    review.state = "REVIEW";

    ProposalDoc propose = new ProposalDoc();
    propose.id = "imp-propose";
    propose.objective = "Propose";
    propose.state = "PROPOSE";

    proposalRepo.store.put(review.id, review);
    proposalRepo.store.put(propose.id, propose);

    KernelStatus status = resource.status();

    assertEquals(2, status.openProposals.size());
    assertEquals(ImprovementFSM.State.REVIEW, status.state);
    assertEquals(ImprovementFSM.State.REVIEW, status.proposalStates.get("imp-review"));
    assertEquals(ImprovementFSM.State.PROPOSE, status.proposalStates.get("imp-propose"));
    assertFalse(status.stopped);
  }

  @Test
  void auditTailReturns423WhenFileFallbackIsDeniedByPolicy() {
    enforcer.nextDecision = new Decision(Decision.Effect.DENY, "default deny");

    Response response = resource.auditTail(5);

    assertEquals(423, response.getStatus());
    KernelResource.ErrorResponse error = assertInstanceOf(KernelResource.ErrorResponse.class, response.getEntity());
    assertEquals("POLICY_DENIED", error.code);
    assertEquals("Policy DENY: default deny", error.error);
  }

  @Test
  void killswitchAndResumeToggleGuardState() {
    Response stop = resource.killswitch();
    assertEquals(200, stop.getStatus());
    assertTrue(guard.isStopped());

    Response resume = resource.resume();
    assertEquals(200, resume.getStatus());
    assertFalse(guard.isStopped());
    assertEquals(2, auditRepo.docs.size());
  }

  private static void inject(Object target, String fieldName, Object value) throws Exception {
    Field field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(target, value);
  }

  static final class RecordingFsm implements ImprovementFSM {
    final Map<String, State> states = new LinkedHashMap<String, State>();

    @Override
    public State current(String improvementId) {
      return states.getOrDefault(improvementId, State.IDLE);
    }

    @Override
    public void advance(String improvementId, State target) {
      states.put(improvementId, target);
    }
  }

  static final class RecordingConsentGate extends ConsentGate {
    Approval lastRequested;

    @Override
    public Approval request(String id, ProposalSummary summary, Approval requested) {
      lastRequested = requested;
      return requested != null ? requested : Approval.NO;
    }
  }

  static final class RecordingAuditLog implements AuditLog {
    final List<AuditEntry> entries = new ArrayList<AuditEntry>();

    @Override
    public void record(AuditEntry e) {
      entries.add(e);
    }
  }

  static final class StubProposalRepo extends ProposalRepo {
    final Map<String, ProposalDoc> store = new LinkedHashMap<String, ProposalDoc>();

    @Override
    public ProposalDoc findById(String id) {
      return store.get(id);
    }

    @Override
    public void persist(ProposalDoc entity) {
      store.put(entity.id, entity);
    }

    @Override
    public void persistOrUpdate(ProposalDoc entity) {
      store.put(entity.id, entity);
    }

    @Override
    public PanacheQuery<ProposalDoc> findAll(Sort sort) {
      return queryFor(new ArrayList<ProposalDoc>(store.values()));
    }
  }

  static final class StubAuditRepo extends AuditRepo {
    final List<AuditDoc> docs = new ArrayList<AuditDoc>();

    @Override
    public void persist(AuditDoc entity) {
      docs.add(entity);
    }

    @Override
    public PanacheQuery<AuditDoc> findAll(Sort sort) {
      return queryFor(docs);
    }
  }

  static final class StubPolicyEnforcer extends PolicyEnforcer {
    Decision nextDecision;

    StubPolicyEnforcer(Decision nextDecision) {
      this.nextDecision = nextDecision;
    }

    @Override
    public Decision check(Action action) {
      return nextDecision;
    }
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

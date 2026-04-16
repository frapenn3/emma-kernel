package it.emma.kernel.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import it.emma.kernel.core.ConsentGate;
import it.emma.kernel.dto.Approval;
import it.emma.kernel.dto.ProposalSummary;

class PolicyEnforcerApprovalTest {

  private PolicyEnforcer enforcer;

  @BeforeEach
  void setUp() throws Exception {
    enforcer = new PolicyEnforcer();
    inject(enforcer, "consentGate", new RecordingConsentGate());
    invokeNoArg(enforcer, "init");
  }

  @Test
  void askWriteRequiresExplicitApprovalWhenConfigured() {
    HashMap<String, Object> params = new HashMap<String, Object>();
    params.put("path", "work/test-explicit.txt");
    params.put("require_explicit_approval", true);
    params.put("subject", "test:write");

    Decision decision = enforcer.check(new Action(Action.Type.FS_WRITE, params));

    assertEquals(Decision.Effect.DENY, decision.effect);
    assertEquals("explicit approval required", decision.reason);
  }

  @Test
  void askWriteBecomesAllowWhenApprovalIsYes() {
    HashMap<String, Object> params = new HashMap<String, Object>();
    params.put("path", "work/test-approved.txt");
    params.put("require_explicit_approval", true);
    params.put("approval", "YES");
    params.put("subject", "test:write");

    Decision decision = enforcer.check(new Action(Action.Type.FS_WRITE, params));

    assertEquals(Decision.Effect.ALLOW, decision.effect);
    assertEquals("approved by consent gate", decision.reason);
  }

  @Test
  void askWriteBecomesDenyWhenApprovalIsNo() {
    HashMap<String, Object> params = new HashMap<String, Object>();
    params.put("path", "work/test-rejected.txt");
    params.put("require_explicit_approval", true);
    params.put("approval", "NO");
    params.put("subject", "test:write");

    Decision decision = enforcer.check(new Action(Action.Type.FS_WRITE, params));

    assertEquals(Decision.Effect.DENY, decision.effect);
    assertEquals("rejected by consent gate", decision.reason);
  }

  private static void inject(Object target, String fieldName, Object value) throws Exception {
    Field field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(target, value);
  }

  private static void invokeNoArg(Object target, String methodName) throws Exception {
    Method method = target.getClass().getDeclaredMethod(methodName);
    method.setAccessible(true);
    method.invoke(target);
  }

  static final class RecordingConsentGate extends ConsentGate {
    @Override
    public Approval request(String id, ProposalSummary summary, Approval requested) {
      return requested != null ? requested : Approval.YES;
    }
  }
}

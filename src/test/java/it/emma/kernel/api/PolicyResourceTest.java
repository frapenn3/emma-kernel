package it.emma.kernel.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import it.emma.kernel.core.ConsentGate;
import it.emma.kernel.policy.Action;
import it.emma.kernel.policy.Decision;
import it.emma.kernel.policy.PolicyEnforcer;
import it.emma.kernel.policy.QuotaSnapshot;
import jakarta.ws.rs.core.Response;

class PolicyResourceTest {

  private PolicyResource resource;
  private PolicyEnforcer enforcer;

  @BeforeEach
  void setUp() throws Exception {
    enforcer = new PolicyEnforcer();
    inject(enforcer, "consentGate", new ConsentGate());
    invokeNoArg(enforcer, "init");

    resource = new PolicyResource();
    inject(resource, "enforcer", enforcer);
    enforcer.getQuotaTracker().reset();
  }

  @Test
  void checkFsReturns400WhenPathIsMissing() {
    Response response = resource.checkFs("READ", " ");

    assertEquals(400, response.getStatus());
    PolicyResource.ErrorResponse error = assertInstanceOf(PolicyResource.ErrorResponse.class, response.getEntity());
    assertEquals("Missing 'path'", error.error);
  }

  @Test
  void checkNetReturns400WhenOpIsInvalid() {
    Response response = resource.checkNet("POST", "localhost", 8080);

    assertEquals(400, response.getStatus());
    PolicyResource.ErrorResponse error = assertInstanceOf(PolicyResource.ErrorResponse.class, response.getEntity());
    assertEquals("Invalid 'op' (CONNECT|DNS)", error.error);
  }

  @Test
  void checkQuotaDoesNotConsumeTrackerState() {
    PolicyResource.QuotaConsume consume = new PolicyResource.QuotaConsume();
    consume.net_requests = Integer.valueOf(5);

    Response response = resource.checkQuota(consume);

    assertEquals(200, response.getStatus());
    PolicyResource.CheckResult result = assertInstanceOf(PolicyResource.CheckResult.class, response.getEntity());
    assertEquals("ALLOW", result.effect);
    assertEquals("quota check ok", result.reason);

    QuotaSnapshot snapshot = assertInstanceOf(QuotaSnapshot.class, resource.quotasSnapshot().getEntity());
    assertEquals(0, snapshot.net_requests);
  }

  @Test
  void quotasResetClearsConsumedState() {
    HashMap<String, Object> params = new HashMap<String, Object>();
    params.put("net_requests", Integer.valueOf(7));
    Decision decision = enforcer.check(new Action(Action.Type.QUOTA_CONSUME, params));
    assertEquals(Decision.Effect.ALLOW, decision.effect);

    QuotaSnapshot beforeReset = assertInstanceOf(QuotaSnapshot.class, resource.quotasSnapshot().getEntity());
    assertEquals(7, beforeReset.net_requests);

    Response resetResponse = resource.quotasReset();
    assertEquals(200, resetResponse.getStatus());

    QuotaSnapshot afterReset = assertInstanceOf(QuotaSnapshot.class, resource.quotasSnapshot().getEntity());
    assertEquals(0, afterReset.net_requests);
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
}
